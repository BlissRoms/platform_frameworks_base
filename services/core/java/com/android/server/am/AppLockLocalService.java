/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.app.ActivityManager.PROCESS_STATE_TOP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.ProcessState;
import android.app.AppLockExtras;
import android.app.AppLockInternal;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.Trace;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Service that manages the locked state of applications for the App Lock feature.
 *
 * <p>This service is responsible for determining whether an app should be considered locked or
 * unlocked based on its visibility and recent user authentications. It listens to UID process state
 * changes to decide when to lock an application. To prevent an abrupt user experience when
 * switching apps, a grace period is provided before an app is actually locked after it moves to the
 * background. If the app returns to the foreground within this grace period, the pending lock is
 * canceled.
 *
 * <p>The locked state changes are communicated to registered listeners via
 * {@link PackageLockedStateListener}.
 *
 * <p>This class deals with two locks: the {@link ActivityManagerService} (AMS) lock and one
 * specific to this class. To avoid deadlocks, the AMS lock should always be the outer lock, and
 * should never be acquired while holding onto the AppLockLocalService lock.
 *
 * <p>To prevent deadlocks and avoid blocking critical system execution paths, state change updates
 * are dispatched asynchronously on a dedicated handler thread. These updates are always sent
 * without holding {@link #mLock}, ensuring that any other code can safely call back into this
 * service if needed.
 */
// TODO(b/465408530): Move class its own directory
public final class AppLockLocalService implements AppLockInternal,
        KeyguardManager.DeviceLockedStateListener {
    private static final String TAG = "AppLockLocalService";
    private static final boolean DEBUG = Build.isDebuggable() && Log.isLoggable(TAG, Log.DEBUG);

    private final Object mLock = new Object();

    // Array where indices are userId and values are (map of package name -> AppLockLockedState)
    //
    // To keep this up to date and remove obsolete entries, the following events will be monitored:
    // - User removal
    // - Package removal
    // - Package App Lock enablement status
    @GuardedBy("mLock")
    private final SparseArray<ArrayMap<String, AppLockLockedState>> mAppLockLockedStatesForUser =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final ArrayList<PackageLockedStateListener> mPackageLockedStateListeners =
            new ArrayList<>();
    private final ActivityManagerService mAmService;
    private final ActivityTaskManagerInternal mAtmInternal;
    private final Injector mInjector;
    @VisibleForTesting
    final PackageMonitor mPackageMonitor = new AppLockPackageMonitor(this);
    // Note: PackageManagerInternal is not available at the time of construction. Use
    // getPackageManagerInternal() to access it.
    private PackageManagerInternal mPmInternal;

    @GuardedBy("mLock")
    private AppLockCredentialStore mCredentialStore;

    private final Object mSettingsCacheLock = new Object();
    @GuardedBy("mSettingsCacheLock")
    private final SparseLongArray mGracePeriodCache = new SparseLongArray();
    @GuardedBy("mSettingsCacheLock")
    private final SparseIntArray mRelockBehaviorCache = new SparseIntArray();

    private static final long MIN_SCHEDULED_GRACE_PERIOD_MS = 250L;

    AppLockLocalService(final ActivityManagerService service) {
        this(service, new InjectorImpl(service));
    }

    @VisibleForTesting
    AppLockLocalService(final ActivityManagerService service, Injector injector) {
        mAmService = service;
        mAtmInternal = service.mAtmInternal;
        mInjector = injector;
    }

    /**
     * Called by the {@link ActivityManagerService} once the system services are ready.
     */
    public void systemServicesReady() {
        Trace.beginSection(TAG + ".systemServicesReady");
        try {
            initAppLockLockedStates();
            mPackageMonitor.register(mAmService.mContext, UserHandle.ALL,
                    BackgroundThread.getHandler());
            Context context = mAmService.mContext;
            final KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
            keyguardManager.addDeviceLockedStateListener(BackgroundThread.getExecutor(),
                    /* listener= */ this);
            final ContentResolver resolver = context.getContentResolver();
            final ContentObserver settingsObserver = new ContentObserver(mInjector.getHandler()) {
                @Override
                public void onChange(boolean selfChange, Uri uri, int userId) {
                    reloadSettingsForUser(userId);
                }
            };
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(AppLockExtras.SETTING_GRACE_PERIOD_MS),
                    /* notifyForDescendants= */ false, settingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(AppLockExtras.SETTING_RELOCK_BEHAVIOR),
                    /* notifyForDescendants= */ false, settingsObserver, UserHandle.USER_ALL);
            mInjector.getHandler().post(() -> {
                reloadSettingsForUser(UserHandle.myUserId());
                getCredentialStore().getCredentialType(UserHandle.myUserId());
            });
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    handleScreenOff();
                }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF), /* broadcastPermission= */ null,
                    mInjector.getHandler());
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Initializes {@link #mAppLockLockedStatesForUser} by querying all installed applications for
     * all full users and populating the map with packages that have App Lock enabled.
     */
    private void initAppLockLockedStates() {
        Trace.beginSection(TAG + ".initAppLockLockedStates");
        try {
            final UserManagerInternal umi = LocalServices.getService(UserManagerInternal.class);
            if (umi == null) {
                Slog.w(TAG, "Unable to retrieve UserManagerInternal");
                return;
            }
            final List<UserInfo> allUsers = umi.getUsers(
                    UserManagerInternal.USER_FILTER_WITH_ALL_COMPLETE_USERS);

            for (UserInfo userInfo : allUsers) {
                if (!userInfo.isFull()) {
                    continue;
                }

                final int userId = userInfo.id;
                final List<String> appLockEnabledPackages =
                        getPackageManagerInternal().getAppLockEnabledPackagesForUser(userId);
                synchronized (mLock) {
                    ArrayMap<String, AppLockLockedState> map = mAppLockLockedStatesForUser.get(
                            userId);

                    for (int i = appLockEnabledPackages.size() - 1; i >= 0; i--) {
                        if (map == null) {
                            map = new ArrayMap<>();
                            mAppLockLockedStatesForUser.put(userId, map);
                        }
                        map.put(appLockEnabledPackages.get(i), new AppLockLockedState());
                    }
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    private PackageManagerInternal getPackageManagerInternal() {
        if (mPmInternal == null) {
            mPmInternal = mAmService.getPackageManagerInternal();
        }
        return mPmInternal;
    }

    @Override
    public @NonNull SparseArray<Set<String>> getAppLockEnabledPackages() {
        final SparseArray<Set<String>> appLockEnabledPackages = new SparseArray<>();
        synchronized (mLock) {
            for (int i = 0; i < mAppLockLockedStatesForUser.size(); i++) {
                final int userId = mAppLockLockedStatesForUser.keyAt(i);
                final ArrayMap<String, AppLockLockedState> userPackages =
                        mAppLockLockedStatesForUser.valueAt(i);

                if (userPackages != null && !userPackages.isEmpty()) {
                    // Packages that are currently locked must have App Lock enabled.
                    appLockEnabledPackages.put(userId, new ArraySet<>(userPackages.keySet()));
                }
            }
        }
        return appLockEnabledPackages;
    }

    @Override
    public void registerPackageLockedStateListener(@NonNull PackageLockedStateListener listener) {
        Objects.requireNonNull(listener);

        synchronized (mLock) {
            mPackageLockedStateListeners.add(listener);
        }
    }

    @Override
    public void unregisterPackageLockedStateListener(@NonNull PackageLockedStateListener listener) {
        Objects.requireNonNull(listener);

        synchronized (mLock) {
            mPackageLockedStateListeners.remove(listener);
        }
    }

    @Override
    public boolean isPackageAppLockEnabled(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        synchronized (mLock) {
            return isPackageAppLockEnabledLocked(packageName, userId);
        }
    }

    @GuardedBy("mLock")
    private boolean isPackageAppLockEnabledLocked(@NonNull String packageName, int userId) {
        Objects.requireNonNull(packageName);

        return mAppLockLockedStatesForUser.contains(userId) && mAppLockLockedStatesForUser.get(
                userId).containsKey(packageName);
    }

    // NOTE: This method will hold the ActivityManagerService lock. Callers should be mindful of not
    // causing a deadlock.
    @Override
    public boolean isPackageLocked(@NonNull String packageName, int userId) {
        return isPackageLocked(packageName, userId, /* respectGracePeriod= */ true);
    }

    private boolean isPackageLocked(@NonNull String packageName, int userId,
            boolean respectGracePeriod) {
        Trace.beginSection(TAG + ".isPackageLocked");
        if (DEBUG) {
            Slog.d(TAG, "isPackageLocked for " + packageName + " and user: " + userId
                    + ", respectGracePeriod: " + respectGracePeriod);
        }
        try {
            Objects.requireNonNull(packageName);

            // 1. Check if App Lock is enabled.
            if (!isPackageAppLockEnabled(packageName, userId)) {
                if (DEBUG) {
                    Slog.d(TAG, "isPackageLocked: App Lock is disabled, returning false");
                }
                return false;
            }

            synchronized (mLock) {
                final ArrayMap<String, AppLockLockedState> userStates =
                        mAppLockLockedStatesForUser.get(userId);
                final AppLockLockedState forceState =
                        userStates == null ? null : userStates.get(packageName);
                if (forceState != null && forceState.mForceLocked) {
                    if (DEBUG) {
                        Slog.d(TAG, "isPackageLocked: package is force-locked, returning true");
                    }
                    return true;
                }
            }

            // 2. Check if the last successful authentication is within the grace period. Note that
            //    the grace period associated with the package's last visibility in the foreground
            //    is checked below with pending locked jobs.
            if (respectGracePeriod && isLastAuthWithinGracePeriod(packageName, userId)) {
                if (DEBUG) {
                    Slog.d(TAG, "isPackageLocked: last successful auth is within grace period,"
                            + " returning false");
                }
                return false;
            }

            // 3. Check pending jobs. If there is a pending job to lock the package, the package
            //    is currently in a grace period and is thus technically unlocked.
            if (respectGracePeriod && packageHasQueuedAppLockedJob(userId, packageName)) {
                if (DEBUG) {
                    Slog.d(TAG, "isPackageLocked: there is a pending job to lock the package,"
                            + " returning false");
                }
                return false;
            }

            // 4. Check if the package has any visible tasks.
            // TODO(b/462423789): Move the visible tasks logic in isPackageLocked into
            //  WindowManager's AppLockController.
            final List<ActivityAssistInfo> visibleAaInfoList =
                    getVisibleActivityAssistInfosForPackage(packageName, userId);
            if (!visibleAaInfoList.isEmpty()) {
                // If there are visible tasks and any of them have showWhenLocked=false, the package
                // is unlocked. showWhenLocked indicates that the activity should be shown even if
                // the device is locked, so those activities should still be shown if the app is
                // locked.
                final int callingUid = mInjector.getCallingUid();
                for (ActivityAssistInfo aaInfo : visibleAaInfoList) {
                    final ActivityInfo info = getPackageManagerInternal().getActivityInfo(
                            aaInfo.getComponentName(), PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, callingUid, userId);
                    if (info != null && (info.flags & ActivityInfo.FLAG_SHOW_WHEN_LOCKED) == 0) {
                        if (DEBUG) {
                            Slog.d(TAG, "isPackageLocked: there are visible tasks with"
                                    + " showWhenLocked=false, returning false");
                        }
                        return false;
                    }
                }
                if (DEBUG) {
                    Slog.d(TAG, "isPackageLocked: there are no visible tasks with"
                            + " showWhenLocked=false, returning true");
                }
                // If all visible tasks have showWhenLocked=true, the package is locked.
                return true;
            }

            if (Thread.holdsLock(mLock)) {
                Slog.wtf(TAG, "isPackageLocked: Attempting to acquire AMS lock while holding"
                        + " AppLockLocalService lock!");
            }

            // 5. Check if the package is in the foreground: at least one process belonging to the
            //    package should have a PROCESS_STATE_TOP state (includes the notification shade
            //    being pulled down while the app is in the foreground).
            final boolean isPackageInBackground;
            synchronized (mAmService) {
                isPackageInBackground = !anyProcessInPackageIsInForegroundLocked(packageName,
                        userId);
            }
            if (DEBUG) {
                Slog.d(TAG, "isPackageLocked: returning isPackageInBackground="
                        + isPackageInBackground);
            }
            return isPackageInBackground;
        } finally {
            Trace.endSection();
        }
    }

    private boolean isLastAuthWithinGracePeriod(String packageName, int userId) {
        final long lastSuccessfulAuth = getLastSuccessfulAuthTimeForLockedPackage(packageName,
                userId);
        return lastSuccessfulAuth + getGracePeriodMs(userId) > System.currentTimeMillis()
                && lastSuccessfulAuth != AppLockLockedState.INVALID_AUTH_TIME_MS;
    }

    private long getGracePeriodMs(int userId) {
        synchronized (mSettingsCacheLock) {
            final int index = mGracePeriodCache.indexOfKey(userId);
            if (index >= 0) {
                return mGracePeriodCache.valueAt(index);
            }
        }
        mInjector.getHandler().post(() -> reloadSettingsForUser(userId));
        return AppLockExtras.DEFAULT_GRACE_PERIOD_MS;
    }

    private int getRelockBehavior(int userId) {
        synchronized (mSettingsCacheLock) {
            final int index = mRelockBehaviorCache.indexOfKey(userId);
            if (index >= 0) {
                return mRelockBehaviorCache.valueAt(index);
            }
        }
        mInjector.getHandler().post(() -> reloadSettingsForUser(userId));
        return AppLockExtras.DEFAULT_RELOCK_BEHAVIOR;
    }

    /** Must never be called with mLock or the ActivityManagerService lock held. */
    private void reloadSettingsForUser(int userId) {
        if (userId < 0) {
            return;
        }
        final ContentResolver resolver = mAmService.mContext.getContentResolver();
        final long gracePeriodMs = Settings.Secure.getLongForUser(resolver,
                AppLockExtras.SETTING_GRACE_PERIOD_MS,
                AppLockExtras.DEFAULT_GRACE_PERIOD_MS, userId);
        final int relockBehavior = Settings.Secure.getIntForUser(resolver,
                AppLockExtras.SETTING_RELOCK_BEHAVIOR,
                AppLockExtras.DEFAULT_RELOCK_BEHAVIOR, userId);
        final boolean clearForceLocked;
        synchronized (mSettingsCacheLock) {
            final int index = mRelockBehaviorCache.indexOfKey(userId);
            clearForceLocked = index >= 0
                    && mRelockBehaviorCache.valueAt(index)
                            == AppLockExtras.RELOCK_BEHAVIOR_SCREEN_OFF
                    && relockBehavior != AppLockExtras.RELOCK_BEHAVIOR_SCREEN_OFF;
            mGracePeriodCache.put(userId, gracePeriodMs);
            mRelockBehaviorCache.put(userId, relockBehavior);
        }
        if (clearForceLocked) {
            clearForceLockedStateForUser(userId);
        }
    }

    private void clearForceLockedStateForUser(int userId) {
        synchronized (mLock) {
            final ArrayMap<String, AppLockLockedState> userPackages =
                    mAppLockLockedStatesForUser.get(userId);
            if (userPackages == null) {
                return;
            }
            for (int i = 0; i < userPackages.size(); i++) {
                final AppLockLockedState state = userPackages.valueAt(i);
                if (state != null) {
                    state.mForceLocked = false;
                }
            }
        }
    }

    @GuardedBy("mAmService")
    private boolean anyProcessInPackageIsInForegroundLocked(String packageName, int userId) {
        final int uid = getPackageManagerInternal().getPackageUid(packageName, /* flags= */ 0,
                userId);
        final UidRecord uidRecord = mAmService.mProcessList.getUidRecordLOSP(uid);

        return uidRecord != null && uidRecord.anyProcessInPackageMatches(packageName,
                process -> process.getProcState() <= PROCESS_STATE_TOP);
    }

    private ArrayList<ActivityAssistInfo> getVisibleActivityAssistInfosForPackage(
            String packageName, int userId) {
        final List<ActivityAssistInfo> aaInfoList = mAtmInternal.getTopVisibleActivities();
        final ArrayList<ActivityAssistInfo> visibleAaInfoList = new ArrayList<>();
        for (int i = 0; i < aaInfoList.size(); i++) {
            final ActivityAssistInfo aaInfo = aaInfoList.get(i);
            if (aaInfo.getUserId() == userId && aaInfo.getComponentName().getPackageName().equals(
                    packageName)) {
                visibleAaInfoList.add(aaInfo);
            }
        }
        return visibleAaInfoList;
    }

    /**
     * Updates the App Lock locked state for packages when a UID's process state changes.
     *
     * <p>This method is called when a UID's process state changes (e.g., from foreground to
     * background). It checks all packages associated with that UID and, if any of them have App
     * Lock enabled, it updates their locked state.
     *
     * <ul>
     *   <li>If a package becomes locked (i.e., it moves to the background), a delayed job is queued
     *       to update the state. This provides a grace period for the user to switch back to the
     *       app before it actually locks.
     *   <li>If a package becomes unlocked (i.e., it moves to the foreground), the state is updated
     *       immediately.
     *   <li>If a package moves to the background and then back to the foreground within the grace
     *       period, the queued lock update is canceled.
     * </ul>
     *
     * <p>Callbacks are sent to registered {@link PackageLockedStateListener}s to notify them of any
     * changes to the locked state.
     *
     * @param uidRec         object for the uid
     * @param uid            uid
     * @param enqueuedChange what kind of change
     * @param procState      the process state the uid is moving to
     */
    @GuardedBy("mAmService")
    public void handleUidChangeLocked(@Nullable UidRecord uidRec, int uid,
            int enqueuedChange, @ProcessState int procState) {
        Trace.beginSection(TAG + ".handleUidChangeLocked");
        try {
            if (uidRec == null) {
                return;
            }
            if ((enqueuedChange & UidRecord.CHANGE_PROCSTATE) == 0) {
                // Only handles process state changes, e.g. TOP <-> NOT_TOP
                return;
            }
            final int userId = UserHandle.getUserId(uid);

            // 1. Collect packages involved in the UID change.
            final ArraySet<String> packageSet = new ArraySet<>();
            for (int i = 0; i < uidRec.getNumOfProcs(); i++) {
                final ProcessRecord proc = uidRec.getProcessRecordByIndex(i);
                final String[] packages = proc.getProcessPackageNames();
                packageSet.addAll(Set.of(packages));
            }

            // 2. Remove packages that have App Lock disabled, as only App Lock enabled packages are
            //    relevant.
            packageSet.removeIf(pkg -> !isPackageAppLockEnabled(pkg, userId));

            if (packageSet.isEmpty()) {
                // No App Lock enabled packages involved in the UID change, nothing to do.
                return;
            }

            // 3. Iterate over App Lock enabled packages and schedule listener updates.
            for (int i = 0; i < packageSet.size(); i++) {
                final String packageName = packageSet.valueAt(i);
                if (DEBUG) {
                    Slog.d(TAG, "handleUidChangeLocked: packageName: " + packageName);
                }
                // isPackageLocked should be called outside of this synchronized block because it
                // acquires the outer lock (mAmService).
                //
                // Note: respectGracePeriod is set to false here. If the user recently authenticated
                // and then immediately left the app, isPackageLocked would normally return false
                // (unlocked) due to the authentication grace period. By ignoring it, we ensure
                // that moving to the background correctly triggers handleLockedStateLocked to
                // schedule the grace period lock.
                final boolean isCurrentlyLocked = procState != PROCESS_STATE_TOP && isPackageLocked(
                        packageName, userId, /* respectGracePeriod= */ false);
                final ArrayList<PackageLockedStateListener> listenersToNotify;
                synchronized (mLock) {
                    final Boolean lastSentLockedState = getOrCreateAppLockLockedStateLocked(
                            packageName, userId).mLastSentLockedState;

                    if (lastSentLockedState != null && lastSentLockedState == isCurrentlyLocked) {
                        if (DEBUG) {
                            Slog.d(TAG, "handleUidChangeLocked: lastSentLockedState is the same");
                        }
                        // No change since last listener update.
                        if (!isCurrentlyLocked) {
                            // If the update that the package is locked hasn't been sent yet, but
                            // the package was moved off of the top (and now back to the top),
                            // cancel the queued lock update
                            cancelPackageAppLockedJobLocked(packageName, userId);
                        }
                        continue;
                    }

                    if (isCurrentlyLocked) {
                        scheduleLockedStateLocked(packageName, userId);
                        continue;
                    }

                    // Package is no longer locked.
                    if (!updatePackageLockedStateLocked(packageName, userId, /* locked= */ false)) {
                        continue;
                    }
                    // Package was updated to unlocked, collect listeners to notify and update them
                    // outside the synchronized block.
                    listenersToNotify = copyPackageLockedStateListenersLocked();
                }

                // As mentioned above, reaching this point means the package is no longer locked, so
                // send listener updates outside the synchronized block to prevent deadlocks.
                dispatchPackageLockedStateChanged(listenersToNotify, packageName, userId,
                        /* locked= */ false);
            }
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Updates the internal locked state for the given package and returns whether listeners should
     * be notified of a change. In the unlocked state, pending locked jobs are always canceled.
     *
     * @return {@code true} if the package's locked state has changed and listeners should be
     * notified, {@code false} otherwise.
     */
    @GuardedBy("mLock")
    private boolean updatePackageLockedStateLocked(@NonNull String packageName, int userId,
            boolean locked) {
        Objects.requireNonNull(packageName);

        if (DEBUG) {
            Slog.d(TAG, "updatePackageLockedStateLocked: " + packageName
                    + " (user " + userId + ") state changing to locked=" + locked);
        }

        final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName, userId);

        // Transitioning to unlocked always cancels any pending lock job.
        if (!locked) {
            cancelPackageAppLockedJobLocked(packageName, userId);
        }

        if (Objects.equals(locked, state.mLastSentLockedState)) {
            if (DEBUG) {
                Slog.d(TAG, "updatePackageLockedStateLocked: already sent an update with locked="
                        + locked + ", returning false");
            }
            return false;
        }

        state.mLastSentLockedState = locked;

        // If we are sending an update that the package is locked, any pending delayed job is now
        // obsolete.
        if (locked) {
            cancelPackageAppLockedJobLocked(packageName, userId);
        }

        return true;
    }

    @GuardedBy("mLock")
    @NonNull
    private ArrayList<PackageLockedStateListener> copyPackageLockedStateListenersLocked() {
        return new ArrayList<>(mPackageLockedStateListeners);
    }

    /**
     * Dispatches a package locked state change to the provided listeners. Each listener is notified
     * on the service's handler thread.
     *
     * <p>This method must be called without holding {@link #mLock} to prevent deadlocks, as
     * listeners might perform operations that call back into this service and attempt to acquire
     * the same lock.
     */
    private void dispatchPackageLockedStateChanged(
            @NonNull ArrayList<PackageLockedStateListener> listeners, @NonNull String packageName,
            int userId, boolean locked) {
        Objects.requireNonNull(listeners);
        Objects.requireNonNull(packageName);

        if (listeners.isEmpty()) {
            if (DEBUG) {
                Slog.d(TAG, "dispatchPackageLockedStateChanged: no listeners");
            }
            return;
        }

        if (DEBUG) {
            Slog.d(TAG, "dispatchPackageLockedStateChanged: " + packageName + " (user " + userId
                    + ") locked=" + locked + " for " + listeners.size() + " listeners");
        }

        if (Thread.holdsLock(mLock)) {
            Slog.wtf(TAG, "dispatchPackageLockedStateChanged: Should not be holding the internal"
                    + " lock!");
        }

        mInjector.getHandler().post(() -> {
            if (DEBUG) {
                Slog.d(TAG, "dispatchPackageLockedStateChanged's runnable: Notifying listener"
                        + " onPackageLockedStateChanged for " + packageName + " (user " + userId
                        + ") locked=" + locked);
            }
            for (int i = 0; i < listeners.size(); i++) {
                final PackageLockedStateListener listener = listeners.get(i);
                if (listener != null) {
                    listener.onPackageLockedStateChanged(packageName, userId, locked);
                }
            }
        });
    }

    /**
     * Handles the event when a package becomes App Lock enabled. This involves adding the package
     * to the internal locked states map, and notifying listeners if the package is now locked. The
     * package may not be immediately locked if it had open windows during the enablement flow.
     *
     * @param packageName The package that had App Lock enabled.
     * @param userId      The user ID for which App Lock was enabled.
     */
    @VisibleForTesting
    void handleAppLockEnabled(String packageName, int userId) {
        synchronized (mLock) {
            getOrCreateAppLockLockedStateLocked(packageName, userId);
        }

        if (isPackageLocked(packageName, userId)) {
            final ArrayList<PackageLockedStateListener> listenersToNotify;
            synchronized (mLock) {
                if (!updatePackageLockedStateLocked(packageName, userId, /* locked= */ true)) {
                    return;
                }
                listenersToNotify = copyPackageLockedStateListenersLocked();
            }
            dispatchPackageLockedStateChanged(listenersToNotify, packageName, userId,
                    /* locked= */ true);
        }
    }

    /**
     * Schedules a delayed update to notify listeners that a package has become locked.
     *
     * <p>Scheduling the locked state change while holding {@link #mLock} ensures that the check for
     * existing pending jobs and the update of the package's state are performed atomically. This
     * prevents duplicate jobs from being queued for the same package.
     */
    @VisibleForTesting
    @GuardedBy("mLock")
    void scheduleLockedStateLocked(String packageName, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "handleLockedState for " + packageName + " and user: " + userId);
        }

        if (getOrCreateAppLockLockedStateLocked(packageName, userId).mLockedUpdateRunnable
                != null) {
            if (DEBUG) {
                Slog.d(TAG, "handleLockedState: there is already a job to lock the package,"
                        + " returning early");
            }
            // This shouldn't happen, but if there's already a job to lock the package, don't
            // schedule a new one and let the existing one run.
            return;
        }

        final Runnable setPackageLocked = new SetPackageLockedRunnable(this, packageName, userId);

        getOrCreateAppLockLockedStateLocked(packageName, userId).mLockedUpdateRunnable =
                setPackageLocked;
        mInjector.getHandler().postDelayed(setPackageLocked,
                Math.max(MIN_SCHEDULED_GRACE_PERIOD_MS, getGracePeriodMs(userId)));
    }

    @Override
    public void onDeviceLockedStateChanged(boolean isDeviceLocked) {
        Trace.beginSection(TAG + ".onDeviceLockedStateChanged");
        try {
            if (!isDeviceLocked) {
                return;
            }
            final ArrayList<PackageLockedStateListener> listeners;
            final SparseArray<ArraySet<String>> packagesByUserIdToNotify = new SparseArray<>();
            synchronized (mLock) {
                for (int i = 0; i < mAppLockLockedStatesForUser.size(); i++) {
                    final int userId = mAppLockLockedStatesForUser.keyAt(i);
                    final ArrayMap<String, AppLockLockedState> userPackages =
                            mAppLockLockedStatesForUser.valueAt(i);

                    if (userPackages == null) {
                        continue;
                    }
                    for (int j = 0; j < userPackages.size(); j++) {
                        final String packageName = userPackages.keyAt(j);
                        final AppLockLockedState state = userPackages.valueAt(j);
                        // When the device locks, all apps should be immediately locked.
                        if (state != null && updatePackageLockedStateLocked(packageName, userId,
                                /* locked= */ true)) {
                            if (DEBUG) {
                                Slog.d(TAG, packageName
                                        + " has become locked due to the device locking");
                            }
                            state.mLastAuthTimeSinceDeviceUnlock =
                                    AppLockLockedState.INVALID_AUTH_TIME_MS;
                            if (!packagesByUserIdToNotify.contains(userId)) {
                                packagesByUserIdToNotify.put(userId, new ArraySet<>());
                            }
                            packagesByUserIdToNotify.get(userId).add(packageName);
                        }
                    }
                }
                if (packagesByUserIdToNotify.size() == 0) {
                    return;
                }
                listeners = copyPackageLockedStateListenersLocked();
            }

            // Listener updates are dispatched outside the synchronized block to prevent deadlocks.
            for (int i = 0; i < packagesByUserIdToNotify.size(); i++) {
                final int userId = packagesByUserIdToNotify.keyAt(i);
                final ArraySet<String> packages = packagesByUserIdToNotify.valueAt(i);
                for (int j = 0; j < packages.size(); j++) {
                    dispatchPackageLockedStateChanged(listeners, packages.valueAt(j), userId,
                            /* locked= */ true);
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    private void handleScreenOff() {
        final SparseArray<ArraySet<String>> targets = new SparseArray<>();
        synchronized (mLock) {
            for (int i = 0; i < mAppLockLockedStatesForUser.size(); i++) {
                final int userId = mAppLockLockedStatesForUser.keyAt(i);
                if (getRelockBehavior(userId) != AppLockExtras.RELOCK_BEHAVIOR_SCREEN_OFF) {
                    continue;
                }
                final ArrayMap<String, AppLockLockedState> userPackages =
                        mAppLockLockedStatesForUser.valueAt(i);
                if (userPackages == null || userPackages.isEmpty()) {
                    continue;
                }
                targets.put(userId, new ArraySet<>(userPackages.keySet()));
            }
        }
        if (targets.size() == 0) {
            return;
        }
        mInjector.getHandler().post(() -> forceLockPackages(targets));
    }

    private void forceLockPackages(SparseArray<ArraySet<String>> targets) {
        final ArrayList<PackageLockedStateListener> listeners;
        final SparseArray<ArraySet<String>> packagesByUserIdToNotify = new SparseArray<>();
        synchronized (mAmService) {
            synchronized (mLock) {
                for (int i = 0; i < targets.size(); i++) {
                    final int userId = targets.keyAt(i);
                    final ArraySet<String> packages = targets.valueAt(i);
                    for (int j = 0; j < packages.size(); j++) {
                        final String packageName = packages.valueAt(j);
                        final AppLockLockedState state =
                                getOrCreateAppLockLockedStateLocked(packageName, userId);
                        state.mForceLocked = true;
                        state.mLastAuthTimeSinceDeviceUnlock =
                                AppLockLockedState.INVALID_AUTH_TIME_MS;
                        if (updatePackageLockedStateLocked(packageName, userId,
                                /* locked= */ true)) {
                            if (!packagesByUserIdToNotify.contains(userId)) {
                                packagesByUserIdToNotify.put(userId, new ArraySet<>());
                            }
                            packagesByUserIdToNotify.get(userId).add(packageName);
                        }
                    }
                }
                if (packagesByUserIdToNotify.size() == 0) {
                    return;
                }
                listeners = copyPackageLockedStateListenersLocked();
            }
        }

        for (int i = 0; i < packagesByUserIdToNotify.size(); i++) {
            final int userId = packagesByUserIdToNotify.keyAt(i);
            final ArraySet<String> packages = packagesByUserIdToNotify.valueAt(i);
            for (int j = 0; j < packages.size(); j++) {
                dispatchPackageLockedStateChanged(listeners, packages.valueAt(j), userId,
                        /* locked= */ true);
            }
        }
    }

    @Override
    public void setAppLockEnabledPackageSuccessfullyAuthenticated(@NonNull String packageName,
            int userId) {
        Trace.beginSection(TAG + ".setAppLockEnabledPackageSuccessfullyAuthenticated");
        try {
            Objects.requireNonNull(packageName);
            if (!UserHandle.isSameApp(mInjector.getCallingUid(), Process.SYSTEM_UID)
                    && !UserHandle.isSameApp(mInjector.getCallingUid(), Process.ROOT_UID)) {
                throw new SecurityException(
                        "setAppLockEnabledPackageSuccessfullyAuthenticated can only be called by "
                                + "the system");
            }

            if (DEBUG) {
                Slog.d(TAG, "setAppLockEnabledPackageSuccessfullyAuthenticated for " + packageName
                        + " and user: " + userId);
            }

            final ArraySet<String> packagesToAuthenticate = new ArraySet<>();
            packagesToAuthenticate.add(packageName);
            // In a multi-window scenario, e.g. split screen and freeform windows, multiple packages
            // can be visible with an App Lock overlay simultaneously. To improve user experience,
            // retrieve all such packages for the current user and authenticate them along with the
            // primary package.
            final Set<String> packagesWithVisibleAppLockOverlay = mAtmInternal
                    .getPackagesWithVisibleAppLockOverlay(userId);
            if (packagesWithVisibleAppLockOverlay != null
                    && !packagesWithVisibleAppLockOverlay.isEmpty()) {
                packagesToAuthenticate.addAll(packagesWithVisibleAppLockOverlay);
            }

            final ArrayList<PackageLockedStateListener> listeners;
            final ArraySet<String> packagesToNotify = new ArraySet<>();
            synchronized (mLock) {
                // All packages receive the same 'authTime' to accurately reflect a single user
                // authentication event. Even if processing multiple packages takes longer than a
                // very short grace period, the functional impact is practically negligible due to
                // the microsecond scale of loop operations vs. the millisecond or second scale of
                // typical grace periods.
                final long authTime = System.currentTimeMillis();

                for (int i = 0; i < packagesToAuthenticate.size(); i++) {
                    final String packageToAuthenticate = packagesToAuthenticate.valueAt(i);
                    if (packageToAuthenticate == null
                            || !isPackageAppLockEnabledLocked(packageToAuthenticate, userId)) {
                        // Ensure the package still has App Lock enabled.
                        continue;
                    }
                    if (DEBUG) {
                        Slog.d(TAG, "setAppLockEnabledPackageSuccessfullyAuthenticated:"
                                + " authenticating package " + packageToAuthenticate);
                    }
                    final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(
                            packageToAuthenticate, userId);
                    state.mLastAuthTimeSinceDeviceUnlock = authTime;
                    state.mForceLocked = false;
                    if (updatePackageLockedStateLocked(packageToAuthenticate, userId,
                            /* locked= */ false)) {
                        packagesToNotify.add(packageToAuthenticate);
                    }
                }
                if (packagesToNotify.isEmpty()) {
                    return;
                }
                listeners = copyPackageLockedStateListenersLocked();
            }

            // Listener updates are dispatched outside the synchronized block to prevent deadlocks.
            for (int i = 0; i < packagesToNotify.size(); i++) {
                dispatchPackageLockedStateChanged(listeners, packagesToNotify.valueAt(i), userId,
                        /* locked= */ false);
            }
        } finally {
            Trace.endSection();
        }
    }

    private AppLockCredentialStore getCredentialStore() {
        synchronized (mLock) {
            if (mCredentialStore == null) {
                mCredentialStore = new AppLockCredentialStore(mAmService.mContext);
            }
            return mCredentialStore;
        }
    }

    @Override
    public boolean hasSeparateCredential(int userId) {
        return getCredentialStore().getCredentialType(userId)
                != AppLockExtras.CREDENTIAL_TYPE_NONE;
    }

    @Override
    public int getSeparateCredentialType(int userId) {
        return getCredentialStore().getCredentialType(userId);
    }

    @Override
    public long verifySeparateCredential(@NonNull byte[] credential, int userId) {
        return getCredentialStore().verifyCredential(credential, userId);
    }

    @Override
    public boolean setSeparateCredential(int type, @NonNull byte[] credential, int userId) {
        final boolean success = getCredentialStore().setCredential(type, credential, userId);
        if (success) {
            Settings.Secure.putIntForUser(mAmService.mContext.getContentResolver(),
                    AppLockExtras.SETTING_SEPARATE_CREDENTIAL_TYPE, type, userId);
        }
        return success;
    }

    @Override
    public boolean clearSeparateCredential(int userId) {
        final boolean success = getCredentialStore().clearCredential(userId);
        if (success) {
            Settings.Secure.putIntForUser(mAmService.mContext.getContentResolver(),
                    AppLockExtras.SETTING_SEPARATE_CREDENTIAL_TYPE,
                    AppLockExtras.CREDENTIAL_TYPE_NONE, userId);
        }
        return success;
    }

    @VisibleForTesting
    long getLastSuccessfulAuthTimeForLockedPackage(String packageName, int userId) {
        synchronized (mLock) {
            final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName,
                    userId);
            return state.mLastAuthTimeSinceDeviceUnlock;
        }
    }

    @NonNull
    @GuardedBy("mLock")
    private AppLockLockedState getOrCreateAppLockLockedStateLocked(String packageName, int userId) {
        ArrayMap<String, AppLockLockedState> userStates = mAppLockLockedStatesForUser.get(userId);
        if (userStates == null) {
            userStates = new ArrayMap<>();
            mAppLockLockedStatesForUser.put(userId, userStates);
        }
        return userStates.computeIfAbsent(packageName, pkgName -> new AppLockLockedState());
    }

    @GuardedBy("mLock")
    private void cancelPackageAppLockedJobLocked(String packageName, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "cancelPackageAppLockedJobLocked for " + packageName + " and user: "
                    + userId);
        }
        final AppLockLockedState state = getOrCreateAppLockLockedStateLocked(packageName, userId);
        if (state.mLockedUpdateRunnable == null) {
            if (DEBUG) {
                Slog.d(TAG, "cancelPackageAppLockedJobLocked: no job to cancel");
            }
            return;
        }
        mInjector.getHandler().removeCallbacks(state.mLockedUpdateRunnable);
        state.mLockedUpdateRunnable = null;
    }

    boolean packageHasQueuedAppLockedJob(int userId, String packageName) {
        synchronized (mLock) {
            return getOrCreateAppLockLockedStateLocked(packageName, userId).mLockedUpdateRunnable
                    != null;
        }
    }

    @VisibleForTesting
    interface Injector {
        Handler getHandler();

        int getCallingUid();
    }

    /**
     * Represents the App Lock state for a specific package and user combination.
     *
     * <p>This class tracks the last successful authentication time, any pending runnables to
     * lock the app, and the last state change that was communicated to listeners. This allows the
     * service to manage the grace period before an app is locked and to avoid sending redundant
     * state change updates.
     */
    static final class AppLockLockedState {
        private static final long INVALID_AUTH_TIME_MS = -1L;
        // Last successful authentication timestamp, which is required to calculate grace period
        // expiration. This gets reset to {@link #INVALID_AUTH_TIME_MS} when the device becomes
        // locked.
        private long mLastAuthTimeSinceDeviceUnlock;
        // A runnable to inform listeners that the package has become locked after the grace period
        // has expired. If the package moves back to PROCESS_STATE_TOP, the runnable should be
        // canceled. If this runnable exists for this package and user combination, the package
        // should be considered unlocked.
        private Runnable mLockedUpdateRunnable;
        // Last sent locked state to locked state listeners.
        private Boolean mLastSentLockedState;
        private boolean mForceLocked;

        AppLockLockedState() {
            mLastAuthTimeSinceDeviceUnlock = INVALID_AUTH_TIME_MS;
            mLockedUpdateRunnable = null;
            mLastSentLockedState = null;
            mForceLocked = false;
        }
    }

    private static final class SetPackageLockedRunnable implements Runnable {
        private final AppLockLocalService mService;
        private final String mPackageName;
        private final int mUserId;

        SetPackageLockedRunnable(AppLockLocalService service, String packageName, int userId) {
            mService = service;
            this.mPackageName = packageName;
            this.mUserId = userId;
        }

        @Override
        public void run() {
            if (DEBUG) {
                Slog.d(TAG, "SetPackageLockedRunnable for " + mPackageName + " and user: "
                        + mUserId);
            }
            synchronized (mService.mLock) {
                if (!mService.isPackageAppLockEnabledLocked(mPackageName, mUserId)) {
                    // Ensure the package still has App Lock enabled.
                    if (DEBUG) {
                        Slog.d(TAG, "SetPackageLockedRunnable: package has App Lock disabled,"
                                + " returning");
                    }
                    return;
                }
                final AppLockLockedState state = mService.getOrCreateAppLockLockedStateLocked(
                        mPackageName, mUserId);
                if (Boolean.TRUE.equals(state.mLastSentLockedState)) {
                    // Already sent an update that the package is locked.
                    if (DEBUG) {
                        Slog.d(TAG, "SetPackageLockedRunnable: already sent a locked update,"
                                + " updating state and returning");
                    }
                    state.mLockedUpdateRunnable = null;
                    return;
                }
                if (state.mLockedUpdateRunnable != this) {
                    // Job has been canceled or replaced.
                    Slog.w(TAG, "SetPackageLockedRunnable: job was canceled or replaced,"
                            + " returning");
                    return;
                }
            }

            // Check if the package is still locked before sending the update.
            // isPackageLocked acquires the AMS lock, so it must be called without holding mLock to
            // avoid deadlocks.
            //
            // Note: We should ignore the grace period here. Otherwise, isPackageLocked would see
            // this current execution as a "pending lock job" and return false (unlocked), causing
            // this runnable to incorrectly abort.
            final boolean isPackageLocked = mService.isPackageLocked(mPackageName, mUserId,
                    /* respectGracePeriod= */ false);

            final ArrayList<PackageLockedStateListener> listenersToNotify;
            synchronized (mService.mLock) {
                final AppLockLockedState state = mService.getOrCreateAppLockLockedStateLocked(
                        mPackageName, mUserId);
                if (!isPackageLocked) {
                    if (DEBUG) {
                        Slog.d(TAG, "SetPackageLockedRunnable: package is no longer locked,"
                                + " updating state and returning");
                    }
                    state.mLockedUpdateRunnable = null;
                    return;
                }
                // Check again in case state changed while we weren't holding the lock.
                if (!mService.updatePackageLockedStateLocked(mPackageName, mUserId,
                        /* locked= */ true)) {
                    return;
                }
                listenersToNotify = mService.copyPackageLockedStateListenersLocked();
            }
            mService.dispatchPackageLockedStateChanged(listenersToNotify, mPackageName, mUserId,
                    /* locked= */ true);
            if (DEBUG) {
                Slog.d(TAG, "SetPackageLockedRunnable: completed");
            }
        }
    }

    private static final class AppLockPackageMonitor extends PackageMonitor {
        private final AppLockLocalService mService;

        AppLockPackageMonitor(AppLockLocalService service) {
            mService = service;
        }

        @Override
        public void onPackageAppLockEnabled(String packageName) {
            Trace.beginSection(TAG + ".onPackageAppLockEnabled");
            try {
                super.onPackageAppLockEnabled(packageName);
                if (DEBUG) {
                    Slog.d(TAG, "onPackageAppLockEnabled for " + packageName);
                }
                final int userId = getChangingUserId();

                mService.handleAppLockEnabled(packageName, userId);
            } finally {
                Trace.endSection();
            }
        }

        @Override
        public void onPackageAppLockDisabled(String packageName) {
            Trace.beginSection(TAG + ".onPackageAppLockDisabled");
            try {
                super.onPackageAppLockDisabled(packageName);
                if (DEBUG) {
                    Slog.d(TAG, "onPackageAppLockDisabled for " + packageName);
                }

                updateMapAndListenersPackageNoLongerAppLockEnabled(packageName);
            } finally {
                Trace.endSection();
            }
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            Trace.beginSection(TAG + ".onPackageRemoved");
            try {
                super.onPackageRemoved(packageName, uid);
                if (DEBUG) {
                    Slog.d(TAG, "onPackageRemoved for " + packageName);
                }

                updateMapAndListenersPackageNoLongerAppLockEnabled(packageName);
            } finally {
                Trace.endSection();
            }
        }

        private void updateMapAndListenersPackageNoLongerAppLockEnabled(String packageName) {
            final int userId = getChangingUserId();

            mService.mInjector.getHandler().post(() -> {
                if (DEBUG) {
                    Slog.d(TAG, "updateMapAndListenersPackageNoLongerAppLockEnabled's runnable for "
                            + packageName);
                }
                final ArrayList<PackageLockedStateListener> listenersToNotify;
                synchronized (mService.mLock) {
                    final ArrayMap<String, AppLockLockedState> map =
                            mService.mAppLockLockedStatesForUser.get(userId);
                    if (map == null || !map.containsKey(packageName)) {
                        return;
                    }
                    final boolean shouldNotify = mService.updatePackageLockedStateLocked(
                            packageName, userId, /* locked= */ false);

                    map.remove(packageName);
                    if (map.isEmpty()) {
                        mService.mAppLockLockedStatesForUser.remove(userId);
                    }

                    if (!shouldNotify) {
                        return;
                    }
                    listenersToNotify = mService.copyPackageLockedStateListenersLocked();
                }

                // Listener updates are dispatched outside the synchronized block to prevent
                // deadlocks.
                mService.dispatchPackageLockedStateChanged(listenersToNotify, packageName, userId,
                        /* locked= */ false);
            });
        }
    }

    /**
     * Default implementation of {@link Injector}.
     */
    private static final class InjectorImpl implements Injector {
        final ActivityManagerService mAms;

        InjectorImpl(ActivityManagerService service) {
            this.mAms = service;
        }

        @Override
        public Handler getHandler() {
            return BackgroundThread.getHandler();
        }

        @Override
        public int getCallingUid() {
            return Binder.getCallingUid();
        }
    }
}
