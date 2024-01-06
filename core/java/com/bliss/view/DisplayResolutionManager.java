/*
 * Copyright (C) 2022-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.bliss.view;

import android.annotation.SystemService;
import android.content.Context;
import android.graphics.Point;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Slog;

/** @hide */
@SystemService(Context.DISPLAY_RESOLUTION_MANAGER_SERVICE)
public class DisplayResolutionManager {

    private static final String TAG = "DisplayResolutionManager";

    public static final int TYPE_DISABLED = 0;
    public static final int TYPE_AOSP_FHD_DEFAULT = 1;
    public static final int TYPE_AOSP_QHD_DEFAULT = 2;
    public static final int TYPE_FORCED = 3;

    private static final int DEVICE_TYPE =
            SystemProperties.getInt("ro.bliss.display.resolution_switch", TYPE_DISABLED);

    public static final boolean RESTART_SYSTEMUI_ON_SWITCH =
            SystemProperties.getBoolean("persist.sys.bliss.resolution.restart_sysui", false);

    public static final int FHD_WIDTH = 1080;
    public static final int QHD_WIDTH = 1440;
    public static final float SCALE = (float) FHD_WIDTH / QHD_WIDTH;

    private final Context mContext;
    private final IDisplayResolutionManagerService mService;

    public DisplayResolutionManager(Context context, IDisplayResolutionManagerService service) {
        mContext = context;
        mService = service;
    }

    /** @hide */
    public Point getDisplayResolution() {
        if (mService == null) {
            Slog.e(TAG, "Failed to get display resolution. Service is null");
            return new Point(-1, -1);
        }
        try {
            return mService.getDisplayResolution();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    public void setDisplayResolution(int width) {
        if (mService == null) {
            Slog.e(TAG, "Failed to set display resolution. Service is null");
            return;
        }
        if (width <= 0) {
            Slog.e(TAG, "Failed to set display resolution. Invalid width");
            return;
        }
        try {
            mService.setDisplayResolution(width);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    public boolean registerDisplayResolutionListener(IDisplayResolutionListener.Stub listener) {
        if (mService == null) {
            Slog.e(TAG, "Failed to register resolution listener. Service is null");
            return false;
        }
        try {
            return mService.registerDisplayResolutionListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** @hide */
    public boolean unregisterDisplayResolutionListener(IDisplayResolutionListener.Stub listener) {
        if (mService == null) {
            Slog.e(TAG, "Failed to unregister resolution listener. Service is null");
            return false;
        }
        try {
            return mService.unregisterDisplayResolutionListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public static int getDeviceType() {
        return DEVICE_TYPE >= TYPE_DISABLED &&
                DEVICE_TYPE <= TYPE_FORCED ?
                DEVICE_TYPE : TYPE_DISABLED;
    }

    public static float getDensityScale(int currentWidth) {
        if (DEVICE_TYPE != TYPE_AOSP_FHD_DEFAULT && DEVICE_TYPE != TYPE_AOSP_QHD_DEFAULT) {
            return 1f;
        }
        return (float) currentWidth / FHD_WIDTH;
    }

    public static boolean isDisplayWidthStrValid(String width) {
        return "1080".equals(width) || "1440".equals(width);
    }
}
