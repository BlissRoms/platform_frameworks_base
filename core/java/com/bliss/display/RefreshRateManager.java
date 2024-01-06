/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.bliss.display;

import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.util.bliss.BlissUtils;
import com.android.internal.util.bliss.DisplayRefreshRateHelper;

/** @hide */
@SystemService(Context.REFRESH_RATE_MANAGER_SERVICE)
public class RefreshRateManager {

    private static final String TAG = "RefreshRateManager";

    private final Context mContext;
    private final DisplayRefreshRateHelper mDisplayRefreshRateHelper;
    private final IRefreshRateManagerService mService;

    public RefreshRateManager(Context context, IRefreshRateManagerService service) {
        mContext = context;
        mService = service;
        mDisplayRefreshRateHelper = DisplayRefreshRateHelper.getInstance(context);
    }

    public void requestMemcRefreshRate(int refreshRate) {
        if (mService == null) {
            Slog.e(TAG, "Failed to request memc refresh rate. Service is null");
            return;
        }
        if (refreshRate > 0f &&
                !mDisplayRefreshRateHelper.isRefreshRateValid(refreshRate)) {
            Slog.e(TAG, "Failed to request memc refresh rate. Invalid refresh rate");
            return;
        }
        try {
            mService.requestMemcRefreshRate(refreshRate);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void clearRequestedMemcRefreshRate() {
        if (mService == null) {
            Slog.e(TAG, "Failed to clear memc refresh rate. Service is null");
            return;
        }
        try {
            mService.clearRequestedMemcRefreshRate();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getRefreshRateForPackage(String packageName) {
        if (mService == null) {
            Slog.e(TAG, "Failed to get refresh rate for package. Service is null");
            return -1;
        }
        if (!BlissUtils.isPackageInstalled(mContext, packageName)) {
            Slog.e(TAG, "Failed to get refresh rate for package. Package " + packageName + " is unavailable");
            return -1;
        }
        try {
            return mService.getRefreshRateForPackage(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setRefreshRateForPackage(String packageName, int refreshRate) {
        if (mService == null) {
            Slog.e(TAG, "Failed to set refresh rate for package. Service is null");
            return;
        }
        if (!BlissUtils.isPackageInstalled(mContext, packageName)) {
            Slog.e(TAG, "Failed to set refresh rate for package. Package " + packageName + " is unavailable");
            return;
        }
        if (refreshRate > 0f &&
                !mDisplayRefreshRateHelper.isRefreshRateValid(refreshRate)) {
            Slog.e(TAG, "Failed to set refresh rate for package. Invalid refresh rate");
            return;
        }
        try {
            mService.setRefreshRateForPackage(packageName, refreshRate);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void unsetRefreshRateForPackage(String packageName) {
        if (mService == null) {
            Slog.e(TAG, "Failed to unset refresh rate for package. Service is null");
            return;
        }
        if (!BlissUtils.isPackageInstalled(mContext, packageName)) {
            Slog.e(TAG, "Failed to unset refresh rate for package. Package " + packageName + " is unavailable");
            return;
        }
        try {
            mService.unsetRefreshRateForPackage(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public void setExtremeRefreshRateEnabled(boolean enabled) {
        if (mService == null) {
            Slog.e(TAG, "Failed to set extreme refresh rate. Service is null");
            return;
        }
        try {
            mService.setExtremeRefreshRateEnabled(enabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getRequestedRefreshRate() {
        if (mService == null) {
            Slog.e(TAG, "Failed to get requested refresh rate. Service is null");
            return -1;
        }
        try {
            return mService.getRequestedRefreshRate();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public int getRequestedMemcRefreshRate() {
        if (mService == null) {
            Slog.e(TAG, "Failed to get requested memc refresh rate. Service is null");
            return -1;
        }
        try {
            return mService.getRequestedMemcRefreshRate();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean registerRefreshRateListener(IRefreshRateListener.Stub listener) {
        if (mService == null) {
            Slog.e(TAG, "Failed to register refresh rate listener. Service is null");
            return false;
        }
        try {
            return mService.registerRefreshRateListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public boolean unregisterRefreshRateListener(IRefreshRateListener.Stub listener) {
        if (mService == null) {
            Slog.e(TAG, "Failed to unregister refresh rate listener. Service is null");
            return false;
        }
        try {
            return mService.unregisterRefreshRateListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
