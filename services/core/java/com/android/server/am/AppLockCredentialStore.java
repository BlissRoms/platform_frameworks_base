/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import android.annotation.NonNull;
import android.app.AppLockExtras;
import android.content.Context;
import android.os.Environment;
import android.os.FileUtils;
import android.os.SystemClock;
import android.provider.Settings;
import android.security.Scrypt;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

final class AppLockCredentialStore {

    private static final String TAG = "AppLockCredentialStore";

    private static final String DIR_NAME = "app_lock_extras";
    private static final int SCRYPT_N = 16384;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ATTEMPTS_BEFORE_LOCKOUT = 5;
    private static final long LOCKOUT_BASE_MS = 30_000L;
    private static final long LOCKOUT_MAX_MS = 300_000L;

    private static final String KEY_TYPE = "type";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";

    public static final long VERIFY_SUCCESS = 0L;
    public static final long VERIFY_FAILED = -1L;

    private final Object mLock = new Object();
    private final Context mContext;
    private final SecureRandom mSecureRandom = new SecureRandom();
    private final Scrypt mScrypt = new Scrypt();

    @GuardedBy("mLock")
    private final SparseArray<CredentialState> mStates = new SparseArray<>();

    AppLockCredentialStore(@NonNull Context context) {
        mContext = context;
    }

    int getCredentialType(int userId) {
        synchronized (mLock) {
            return loadLocked(userId).mType;
        }
    }

    boolean setCredential(int type, @NonNull byte[] credential, int userId) {
        if (type != AppLockExtras.CREDENTIAL_TYPE_PIN
                && type != AppLockExtras.CREDENTIAL_TYPE_PATTERN) {
            Slog.e(TAG, "setCredential: unsupported type " + type);
            return false;
        }
        final int minLength = type == AppLockExtras.CREDENTIAL_TYPE_PATTERN
                ? AppLockExtras.MIN_CREDENTIAL_LENGTH * 2
                : AppLockExtras.MIN_CREDENTIAL_LENGTH;
        if (credential.length < minLength) {
            Slog.e(TAG, "setCredential: credential too short");
            return false;
        }
        synchronized (mLock) {
            final CredentialState state = loadLocked(userId);
            final byte[] salt = new byte[SALT_LENGTH];
            mSecureRandom.nextBytes(salt);
            final byte[] hash = hashCredential(credential, salt);
            if (!writeLocked(userId, type, salt, hash)) {
                return false;
            }
            wipeLocked(state);
            state.mType = type;
            state.mSalt = salt;
            state.mHash = hash;
            state.mFailedAttempts = 0;
            state.mLockoutEndElapsed = 0L;
        }
        updateTypeMirror(userId);
        return true;
    }

    boolean clearCredential(int userId) {
        synchronized (mLock) {
            final CredentialState state = loadLocked(userId);
            getFile(userId).delete();
            wipeLocked(state);
            state.mType = AppLockExtras.CREDENTIAL_TYPE_NONE;
            state.mFailedAttempts = 0;
            state.mLockoutEndElapsed = 0L;
        }
        updateTypeMirror(userId);
        return true;
    }

    long verifyCredential(@NonNull byte[] credential, int userId) {
        final byte[] salt;
        final byte[] expected;
        synchronized (mLock) {
            final CredentialState state = loadLocked(userId);
            if (state.mType == AppLockExtras.CREDENTIAL_TYPE_NONE
                    || state.mSalt == null || state.mHash == null) {
                return VERIFY_FAILED;
            }
            final long now = SystemClock.elapsedRealtime();
            if (now < state.mLockoutEndElapsed) {
                return state.mLockoutEndElapsed - now;
            }
            salt = state.mSalt.clone();
            expected = state.mHash.clone();
        }

        byte[] computed = null;
        try {
            computed = hashCredential(credential, salt);
            final boolean matched = MessageDigest.isEqual(expected, computed);
            synchronized (mLock) {
                final CredentialState state = loadLocked(userId);
                if (state.mSalt == null || state.mHash == null
                        || !MessageDigest.isEqual(state.mSalt, salt)
                        || !MessageDigest.isEqual(state.mHash, expected)) {
                    return VERIFY_FAILED;
                }
                final long now = SystemClock.elapsedRealtime();
                if (matched) {
                    state.mFailedAttempts = 0;
                    state.mLockoutEndElapsed = 0L;
                    return VERIFY_SUCCESS;
                }
                state.mFailedAttempts++;
                if (state.mFailedAttempts >= ATTEMPTS_BEFORE_LOCKOUT) {
                    final int over = state.mFailedAttempts - ATTEMPTS_BEFORE_LOCKOUT;
                    final long timeout = Math.min(LOCKOUT_MAX_MS,
                            LOCKOUT_BASE_MS << Math.min(over, 4));
                    state.mLockoutEndElapsed = now + timeout;
                    return timeout;
                }
                return VERIFY_FAILED;
            }
        } finally {
            if (computed != null) {
                Arrays.fill(computed, (byte) 0);
            }
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(salt, (byte) 0);
        }
    }

    private byte[] hashCredential(byte[] credential, byte[] salt) {
        return mScrypt.scrypt(credential, salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, HASH_LENGTH);
    }

    private void updateTypeMirror(int userId) {
        final int type;
        synchronized (mLock) {
            type = loadLocked(userId).mType;
        }
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                AppLockExtras.SETTING_SEPARATE_CREDENTIAL_TYPE, type, userId);
    }

    @GuardedBy("mLock")
    private CredentialState loadLocked(int userId) {
        CredentialState state = mStates.get(userId);
        if (state != null) {
            return state;
        }
        state = new CredentialState();
        mStates.put(userId, state);
        final File file = getFile(userId);
        if (!file.isFile()) {
            return state;
        }
        try {
            final byte[] data = new AtomicFile(file).readFully();
            final JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
            final int type = root.optInt(KEY_TYPE, AppLockExtras.CREDENTIAL_TYPE_NONE);
            final byte[] salt = Base64.decode(root.optString(KEY_SALT), Base64.NO_WRAP);
            final byte[] hash = Base64.decode(root.optString(KEY_HASH), Base64.NO_WRAP);
            if ((type == AppLockExtras.CREDENTIAL_TYPE_PIN
                    || type == AppLockExtras.CREDENTIAL_TYPE_PATTERN)
                    && salt.length == SALT_LENGTH && hash.length == HASH_LENGTH) {
                state.mType = type;
                state.mSalt = salt;
                state.mHash = hash;
            } else {
                Slog.wtf(TAG, "loadLocked: invalid credential data for user " + userId);
            }
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Slog.e(TAG, "loadLocked: failed to read credential for user " + userId, e);
        }
        return state;
    }

    @GuardedBy("mLock")
    private boolean writeLocked(int userId, int type, byte[] salt, byte[] hash) {
        final File dir = getDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Slog.e(TAG, "writeLocked: cannot create " + dir);
            return false;
        }
        FileUtils.setPermissions(dir.getPath(), FileUtils.S_IRWXU, -1, -1);
        final JSONObject root = new JSONObject();
        try {
            root.put(KEY_TYPE, type);
            root.put(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            root.put(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP));
        } catch (JSONException e) {
            Slog.wtf(TAG, "writeLocked: serialization failed", e);
            return false;
        }
        final AtomicFile atomicFile = new AtomicFile(getFile(userId));
        FileOutputStream out = null;
        try {
            out = atomicFile.startWrite();
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(out);
        } catch (IOException e) {
            atomicFile.failWrite(out);
            Slog.e(TAG, "writeLocked: failed to persist credential for user " + userId, e);
            return false;
        }
        FileUtils.setPermissions(getFile(userId).getPath(),
                FileUtils.S_IRUSR | FileUtils.S_IWUSR, -1, -1);
        return true;
    }

    @GuardedBy("mLock")
    private void wipeLocked(CredentialState state) {
        if (state.mSalt != null) {
            Arrays.fill(state.mSalt, (byte) 0);
            state.mSalt = null;
        }
        if (state.mHash != null) {
            Arrays.fill(state.mHash, (byte) 0);
            state.mHash = null;
        }
    }

    private static File getDir() {
        return new File(Environment.getDataSystemDirectory(), DIR_NAME);
    }

    private static File getFile(int userId) {
        return new File(getDir(), userId + ".json");
    }

    private static final class CredentialState {
        int mType = AppLockExtras.CREDENTIAL_TYPE_NONE;
        byte[] mSalt;
        byte[] mHash;
        int mFailedAttempts;
        long mLockoutEndElapsed;
    }
}
