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

package com.android.internal.app;

import android.app.Activity;
import android.app.AppLockExtras;
import android.app.AppLockInternal;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Slog;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.widget.LockPatternView;

import com.android.server.LocalServices;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class AppLockCredentialActivity extends Activity {

    private static final String TAG = "AppLockCredentialActivity";

    private static final int MODE_VERIFY = 0;
    private static final int MODE_SET = 1;
    private static final int MODE_CLEAR = 2;

    private static final int STAGE_VERIFY_CURRENT = 0;
    private static final int STAGE_ENTER_NEW = 1;
    private static final int STAGE_CONFIRM_NEW = 2;

    private AppLockInternal mAppLockInternal;
    private int mMode;
    private int mStage;
    private int mUserId;
    private String mPackageName;
    private int mActiveType;
    private int mNewType;
    private byte[] mFirstNewCredential;
    private long mLockoutEndElapsed;

    private TextView mHeader;
    private TextView mError;
    private EditText mPinEntry;
    private LockPatternView mPatternView;
    private Button mContinueButton;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mWorkerThread;
    private Handler mWorker;
    private final Runnable mLockoutTick = this::updateLockout;

    private final LockPatternView.OnPatternListener mPatternListener =
            new LockPatternView.OnPatternListener() {
                @Override
                public void onPatternStart() {
                    hideError();
                }

                @Override
                public void onPatternCleared() {
                }

                @Override
                public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {
                }

                public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                    final byte[] credential = new byte[pattern.size() * 2];
                    for (int i = 0; i < pattern.size(); i++) {
                        final LockPatternView.Cell cell = pattern.get(i);
                        credential[i * 2] = (byte) cell.getRow();
                        credential[i * 2 + 1] = (byte) cell.getColumn();
                    }
                    mPatternView.clearPattern();
                    handleInput(credential);
                }

                public void onPatternDetected(List<LockPatternView.Cell> pattern,
                        byte patternSize) {
                    onPatternDetected(pattern);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!android.security.Flags.appLockApis() || !android.security.Flags.appLockCore()) {
            Slog.w(TAG, "App Lock implementation is not enabled, finishing");
            finish();
            return;
        }

        mAppLockInternal = LocalServices.getService(AppLockInternal.class);
        if (mAppLockInternal == null) {
            Slog.wtf(TAG, "AppLockInternal service not found, finishing");
            finish();
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (AppLockExtras.ACTION_SET_CREDENTIAL.equals(action)) {
            mMode = MODE_SET;
        } else if (AppLockExtras.ACTION_CLEAR_CREDENTIAL.equals(action)) {
            mMode = MODE_CLEAR;
        } else {
            mMode = MODE_VERIFY;
        }
        mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        mUserId = intent.getIntExtra(Intent.EXTRA_USER_ID, UserHandle.myUserId());
        mNewType = intent.getIntExtra(AppLockExtras.EXTRA_CREDENTIAL_TYPE,
                AppLockExtras.CREDENTIAL_TYPE_PIN);

        final int currentType = mAppLockInternal.getSeparateCredentialType(mUserId);
        if (mMode != MODE_SET && currentType == AppLockExtras.CREDENTIAL_TYPE_NONE) {
            Slog.w(TAG, "No separate credential set for user " + mUserId + ", finishing");
            finish();
            return;
        }

        if (mMode == MODE_SET && currentType == AppLockExtras.CREDENTIAL_TYPE_NONE) {
            mStage = STAGE_ENTER_NEW;
            mActiveType = mNewType;
        } else {
            mStage = STAGE_VERIFY_CURRENT;
            mActiveType = currentType;
        }

        setContentView(R.layout.app_lock_credential_activity);
        mWorkerThread = new HandlerThread("AppLockCredential");
        mWorkerThread.start();
        mWorker = new Handler(mWorkerThread.getLooper());
        mHeader = findViewById(R.id.app_lock_credential_header);
        mError = findViewById(R.id.app_lock_credential_error);
        mPinEntry = findViewById(R.id.app_lock_credential_pin_entry);
        mPatternView = findViewById(R.id.app_lock_credential_pattern_view);
        mContinueButton = findViewById(R.id.app_lock_credential_continue_button);

        findViewById(R.id.app_lock_credential_cancel_button)
                .setOnClickListener(v -> finish());
        mContinueButton.setOnClickListener(v -> submitPin());
        mPinEntry.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPin();
                return true;
            }
            return false;
        });
        mPatternView.setOnPatternListener(mPatternListener);

        updateUi();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mLockoutTick);
        if (mWorkerThread != null) {
            mWorkerThread.quitSafely();
            mWorkerThread = null;
            mWorker = null;
        }
        if (mFirstNewCredential != null) {
            Arrays.fill(mFirstNewCredential, (byte) 0);
            mFirstNewCredential = null;
        }
    }

    private void submitPin() {
        if (mActiveType != AppLockExtras.CREDENTIAL_TYPE_PIN) {
            return;
        }
        final String text = mPinEntry.getText().toString();
        mPinEntry.setText("");
        handleInput(text.getBytes(StandardCharsets.UTF_8));
    }

    private void handleInput(byte[] credential) {
        if (SystemClock.elapsedRealtime() < mLockoutEndElapsed) {
            Arrays.fill(credential, (byte) 0);
            return;
        }
        switch (mStage) {
            case STAGE_VERIFY_CURRENT:
                handleVerifyCurrent(credential);
                break;
            case STAGE_ENTER_NEW:
                handleEnterNew(credential);
                break;
            case STAGE_CONFIRM_NEW:
                handleConfirmNew(credential);
                break;
        }
    }

    private void handleVerifyCurrent(byte[] credential) {
        setInputEnabled(false);
        mWorker.post(() -> {
            final long result = mAppLockInternal.verifySeparateCredential(credential, mUserId);
            Arrays.fill(credential, (byte) 0);
            mHandler.post(() -> {
                if (isDestroyed() || isFinishing()) {
                    return;
                }
                setInputEnabled(true);
                if (result == 0L) {
                    onVerifiedCurrent();
                } else if (result > 0L) {
                    startLockout(result);
                } else {
                    showError(getString(R.string.app_lock_credential_wrong));
                }
            });
        });
    }

    private void onVerifiedCurrent() {
        switch (mMode) {
            case MODE_VERIFY:
                if (mPackageName != null) {
                    mAppLockInternal.setAppLockEnabledPackageSuccessfullyAuthenticated(
                            mPackageName, mUserId);
                }
                setResult(RESULT_OK);
                finish();
                break;
            case MODE_CLEAR:
                mAppLockInternal.clearSeparateCredential(mUserId);
                Toast.makeText(this, R.string.app_lock_credential_cleared,
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
                break;
            case MODE_SET:
                mStage = STAGE_ENTER_NEW;
                mActiveType = mNewType;
                updateUi();
                break;
        }
    }

    private void handleEnterNew(byte[] credential) {
        final int minLength = mActiveType == AppLockExtras.CREDENTIAL_TYPE_PATTERN
                ? AppLockExtras.MIN_CREDENTIAL_LENGTH * 2
                : AppLockExtras.MIN_CREDENTIAL_LENGTH;
        if (credential.length < minLength) {
            Arrays.fill(credential, (byte) 0);
            showError(getString(R.string.app_lock_credential_too_short));
            return;
        }
        mFirstNewCredential = credential;
        mStage = STAGE_CONFIRM_NEW;
        updateUi();
    }

    private void handleConfirmNew(byte[] credential) {
        final boolean matched = mFirstNewCredential != null
                && Arrays.equals(mFirstNewCredential, credential);
        Arrays.fill(credential, (byte) 0);
        if (!matched) {
            if (mFirstNewCredential != null) {
                Arrays.fill(mFirstNewCredential, (byte) 0);
                mFirstNewCredential = null;
            }
            mStage = STAGE_ENTER_NEW;
            updateUi();
            showError(getString(R.string.app_lock_credential_mismatch));
            return;
        }
        final byte[] toStore = mFirstNewCredential;
        final int type = mActiveType;
        mFirstNewCredential = null;
        setInputEnabled(false);
        mWorker.post(() -> {
            final boolean success = mAppLockInternal.setSeparateCredential(type, toStore, mUserId);
            Arrays.fill(toStore, (byte) 0);
            mHandler.post(() -> {
                if (isDestroyed() || isFinishing()) {
                    return;
                }
                if (success) {
                    Toast.makeText(this, R.string.app_lock_credential_set_done,
                            Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    setInputEnabled(true);
                    mStage = STAGE_ENTER_NEW;
                    updateUi();
                    showError(getString(R.string.app_lock_credential_wrong));
                }
            });
        });
    }

    private void updateUi() {
        hideError();
        final boolean isPin = mActiveType == AppLockExtras.CREDENTIAL_TYPE_PIN;
        mPinEntry.setVisibility(isPin ? View.VISIBLE : View.GONE);
        mPatternView.setVisibility(isPin ? View.GONE : View.VISIBLE);
        mContinueButton.setVisibility(isPin ? View.VISIBLE : View.GONE);
        mPinEntry.setText("");
        mPatternView.clearPattern();
        final int headerRes;
        switch (mStage) {
            case STAGE_ENTER_NEW:
                headerRes = isPin ? R.string.app_lock_credential_choose_new_pin
                        : R.string.app_lock_credential_choose_new_pattern;
                break;
            case STAGE_CONFIRM_NEW:
                headerRes = isPin ? R.string.app_lock_credential_confirm_new_pin
                        : R.string.app_lock_credential_confirm_new_pattern;
                break;
            default:
                headerRes = isPin ? R.string.app_lock_credential_enter_pin
                        : R.string.app_lock_credential_enter_pattern;
                break;
        }
        mHeader.setText(headerRes);
        if (isPin) {
            mPinEntry.requestFocus();
        }
    }

    private void startLockout(long remainingMs) {
        mLockoutEndElapsed = SystemClock.elapsedRealtime() + remainingMs;
        setInputEnabled(false);
        updateLockout();
    }

    private void updateLockout() {
        final long remaining = mLockoutEndElapsed - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            setInputEnabled(true);
            hideError();
            return;
        }
        showError(getString(R.string.app_lock_credential_lockout,
                (int) ((remaining + 999) / 1000)));
        mHandler.postDelayed(mLockoutTick, 1000L);
    }

    private void setInputEnabled(boolean enabled) {
        mPinEntry.setEnabled(enabled);
        mPatternView.setEnabled(enabled);
        mContinueButton.setEnabled(enabled);
    }

    private void showError(String message) {
        mError.setText(message);
        mError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        mError.setVisibility(View.INVISIBLE);
    }
}
