/*
 * Copyright (C) 2022-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.content.Intent.ACTION_USER_PRESENT;
import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.os.UserManager.USER_TYPE_PROFILE_CLONE;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.LauncherApps;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;

import com.android.internal.util.bliss.BlissUtils;
import com.android.internal.util.bliss.FullscreenTaskStackChangeListener;

import com.android.server.LocalServices;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.pm.UserManagerInternal;

import java.util.List;

import com.android.server.DisplayRefreshRateController;
import com.android.server.DisplayResolutionController;

public class BlissSystemExService extends SystemService {

    private static final String TAG = "BlissSystemExService";

    private final ContentResolver mResolver;

    private Handler mHandler;
    private ServiceThread mWorker;

    private PackageManagerInternal mPackageManagerInternal;
    private UserManagerInternal mUserManagerInternal;

    private FullscreenTaskStackChangeListener mFullscreenTaskStackChangeListener;
    private PackageRemovedListener mPackageRemovedListener;
    private ScreenStateListener mScreenStateListener;

    public BlissSystemExService(Context context) {
        super(context);
        mResolver = context.getContentResolver();
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
            mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
            mFullscreenTaskStackChangeListener = new FullscreenTaskStackChangeListener(getContext()) {
                @Override
                public void onFullscreenTaskChanged(String packageName, String activityName, int taskId) {
                    onTopFullscreenPackageChanged(packageName, taskId);
                }
            };
            mPackageRemovedListener = new PackageRemovedListener();
            mScreenStateListener = new ScreenStateListener();
            return;
        }

        if (phase == PHASE_BOOT_COMPLETED) {
            DisplayRefreshRateController.getInstance().onBootCompleted();
            mFullscreenTaskStackChangeListener.setListening(true);
            mPackageRemovedListener.register();
            mScreenStateListener.register();
            return;
        }
    }

    @Override
    public void onStart() {
        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());

        DisplayRefreshRateController.getInstance().initSystemExService(this);
        DisplayResolutionController.getInstance().initSystemExService(this);
    }

    @Override
    public void onUserSwitching(TargetUser from, TargetUser to) {
        final int newUserId = to.getUserIdentifier();
        DisplayRefreshRateController.getInstance().onUserSwitching(newUserId);
    }

    private void onPackageRemoved(String packageName) {
        DisplayRefreshRateController.getInstance().onPackageRemoved(packageName);
    }

    private void onScreenOff() {
        DisplayRefreshRateController.getInstance().onScreenOff();
    }

    private void onScreenOn() {
        DisplayRefreshRateController.getInstance().onScreenOn();
    }

    private void onScreenUnlocked() {
        onTopFullscreenPackageChanged(
            mFullscreenTaskStackChangeListener.getTopPackageName(),
            mFullscreenTaskStackChangeListener.getTopTaskId()
        );
    }

    public void onTopFullscreenPackageChanged(String packageName, int taskId) {
        mHandler.post(() -> {
            DisplayRefreshRateController.getInstance().onTopFullscreenPackageChanged(packageName);
        });
    }

    public ContentResolver getContentResolver() {
        return mResolver;
    }

    public String getTopFullscreenPackage() {
        return mFullscreenTaskStackChangeListener.getTopPackageName();
    }

    public int getTopFullscreenTaskId() {
        return mFullscreenTaskStackChangeListener.getTopTaskId();
    }

    private int getCloneUserId() {
        for (UserInfo userInfo : mUserManagerInternal.getUsers(false)) {
            if (USER_TYPE_PROFILE_CLONE.equals(userInfo.userType)) {
                return userInfo.id;
            }
        }
        return -1;
    }

    private void maybeCleanClonedUser(int userId) {
        final List<ApplicationInfo> packages = mPackageManagerInternal.getInstalledApplicationsCrossUser(
                0, userId, Binder.getCallingUid());
        boolean hasUserApp = false;
        for (ApplicationInfo info : packages) {
            if ((info.flags &
                    (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0) {
                hasUserApp = true;
                break;
            }
        }
        if (!hasUserApp) {
            mUserManagerInternal.removeUserEvenWhenDisallowed(userId);
            BlissUtils.forceStopDefaultLauncher(getContext());
        }
    }

    private final class PackageRemovedListener extends LauncherApps.Callback {

        private final LauncherApps mLauncherApps;

        public PackageRemovedListener() {
            mLauncherApps = getContext().getSystemService(LauncherApps.class);
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            // Do nothing
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            // Do nothing
        }

        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            final UserInfo userInfo = mUserManagerInternal.getUserInfo(user.getIdentifier());
            if (userInfo != null && USER_TYPE_PROFILE_CLONE.equals(userInfo.userType)) {
               maybeCleanClonedUser(user.getIdentifier());
                return;
            }

            BlissSystemExService.this.onPackageRemoved(packageName);
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            // Do nothing
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            // Do nothing
        }

        public void register() {
            mLauncherApps.registerCallback(this, mHandler);
        }
    }

    private final class ScreenStateListener extends BroadcastReceiver {

        private final KeyguardManager mKeyguardManager;
        private final PowerManager mPowerManager;

        private boolean mHandledUnlock = false;

        public ScreenStateListener() {
            mKeyguardManager = getContext().getSystemService(KeyguardManager.class);
            mPowerManager = getContext().getSystemService(PowerManager.class);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_SCREEN_OFF:
                    mHandledUnlock = false;
                    onScreenOff();
                    break;
                case ACTION_SCREEN_ON:
                    if (!mHandledUnlock && !mKeyguardManager.isKeyguardLocked()) {
                        onScreenUnlocked();
                    } else {
                        onScreenOn();
                    }
                    break;
                case ACTION_USER_PRESENT:
                    mHandledUnlock = true;
                    onScreenUnlocked();
                    break;
            }
        }

        public void register() {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_SCREEN_OFF);
            filter.addAction(ACTION_SCREEN_ON);
            filter.addAction(ACTION_USER_PRESENT);
            getContext().registerReceiverForAllUsers(this, filter, null, mHandler);
        }
    }
}
