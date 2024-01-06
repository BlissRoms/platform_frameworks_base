/*
 * Copyright (C) 2022 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.view.Display.DEFAULT_DISPLAY;

import static android.content.Context.DISPLAY_RESOLUTION_MANAGER_SERVICE;
import static android.provider.Settings.Global.DISPLAY_WIDTH_CUSTOM;
import static com.bliss.view.DisplayResolutionManager.FHD_WIDTH;
import static com.bliss.view.DisplayResolutionManager.QHD_WIDTH;
import static com.bliss.view.DisplayResolutionManager.RESTART_SYSTEMUI_ON_SWITCH;
import static com.bliss.view.DisplayResolutionManager.SCALE;
import static com.bliss.view.DisplayResolutionManager.TYPE_AOSP_FHD_DEFAULT;
import static com.bliss.view.DisplayResolutionManager.TYPE_DISABLED;
import static com.bliss.view.DisplayResolutionManager.TYPE_FORCED;

import android.content.ContentResolver;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Display;

import com.android.internal.R;
import com.android.internal.util.bliss.BlissUtils;

import com.android.server.ServiceThread;
import com.android.server.wm.WindowManagerService;

import java.util.ArrayList;
import java.util.Set;

import com.android.server.BlissSystemExService;
import com.bliss.view.DisplayResolutionManager;
import com.bliss.view.IDisplayResolutionListener;
import com.bliss.view.IDisplayResolutionManagerService;

public class DisplayResolutionController {

    private static final String TAG = "DisplayResolutionController";

    private static final String PROP_SCALE_BOOTANIMATION =
            "persist.sys.bliss.bootanimation.scale";

    private final Handler mHandler;
    private final ServiceThread mServiceThread;

    private ContentResolver mContentResolver;
    private Context mContext;
    private WindowManagerService mWms;

    private final ArrayList<String> mFhdOverlays = new ArrayList<>();

    private final Object mListenerLock = new Object();

    private static class InstanceHolder {
        private static DisplayResolutionController INSTANCE = new DisplayResolutionController();
    }

    public static DisplayResolutionController getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final class DisplayResolutionListener {
        final IDisplayResolutionListener mListener;
        final IBinder.DeathRecipient mDeathRecipient;

        DisplayResolutionListener(IDisplayResolutionListener listener,
                IBinder.DeathRecipient deathRecipient) {
            mListener = listener;
            mDeathRecipient = deathRecipient;
        }
    }

    private final ArrayList<DisplayResolutionListener> mListeners = new ArrayList<>();

    private final class DisplayResolutionManagerService extends IDisplayResolutionManagerService.Stub {
        @Override
        public Point getDisplayResolution() {
            updateHeightIfNeeded();
            final Point p = new Point(mWidth, mHeight);
            logD("getDisplayResolution, ret=" + p.x + "x" + p.y);
            return p;
        }

        @Override
        public void setDisplayResolution(int width) {
            setResolutionInternal(width, false);
        }

        @Override
        public boolean registerDisplayResolutionListener(IDisplayResolutionListener listener) {
            final IBinder listenerBinder = listener.asBinder();
            IBinder.DeathRecipient dr = new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    synchronized (mListenerLock) {
                        for (int i = 0; i < mListeners.size(); i++) {
                            if (listenerBinder == mListeners.get(i).mListener.asBinder()) {
                                DisplayResolutionListener removed = mListeners.remove(i);
                                IBinder binder = removed.mListener.asBinder();
                                if (binder != null) {
                                    binder.unlinkToDeath(this, 0);
                                }
                                i--;
                            }
                        }
                    }
                }
            };

            synchronized (mListenerLock) {
                try {
                    listener.asBinder().linkToDeath(dr, 0);
                    mListeners.add(new DisplayResolutionListener(listener, dr));
                    updateHeightIfNeeded();
                    mHandler.post(() -> notifyDisplayResolutionChanged(listener));
                } catch (RemoteException e) {
                    // Client died, no cleanup needed.
                    return false;
                }
                return true;
            }
        }

        @Override
        public boolean unregisterDisplayResolutionListener(IDisplayResolutionListener listener) {
            boolean found = false;
            final IBinder listenerBinder = listener.asBinder();
            synchronized (mListenerLock) {
                for (int i = 0; i < mListeners.size(); i++) {
                    found = true;
                    DisplayResolutionListener drListener = mListeners.get(i);
                    if (listenerBinder == drListener.mListener.asBinder()) {
                        DisplayResolutionListener removed = mListeners.remove(i);
                        IBinder binder = removed.mListener.asBinder();
                        if (binder != null) {
                            binder.unlinkToDeath(removed.mDeathRecipient, 0);
                        }
                        i--;
                    }
                }
            }
            return found;
        }
    }

    private Display mDisplay;
    private IOverlayManager mOverlayManager;

    private boolean mSystemReady = false;

    private int mWidth = -1;
    private int mHeight = -1;

    private DisplayResolutionController() {
        mServiceThread = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mServiceThread.start();
        mHandler = new Handler(mServiceThread.getLooper());
    }

    public void init(Context context, WindowManagerService wms) {
        mContentResolver = context.getContentResolver();
        mContext = context;
        mWms = wms;
    }

    public void initSystemExService(BlissSystemExService service) {
        service.publishBinderService(DISPLAY_RESOLUTION_MANAGER_SERVICE, new DisplayResolutionManagerService());
    }

    public void systemReady() {
        mDisplay = mContext.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));

        final String[] overlayPkgList = mContext.getResources().getStringArray(
                R.array.config_fhdResolutionOverlays);
        if (overlayPkgList != null && overlayPkgList.length != 0) {
            for (String pkg : overlayPkgList) {
                if (BlissUtils.isPackageInstalled(mContext, pkg)) {
                    mFhdOverlays.add(pkg);
                }
            }
        }

        mSystemReady = true;

        mWidth = getStoredDisplayWidth();
        setResolutionInternal(mWidth, true);
    }

    public Point getResolution() {
        return new Point(mWidth, mHeight);
    }

    private void setResolutionInternal(int width, boolean onBoot) {
        if (DisplayResolutionManager.getDeviceType() == TYPE_DISABLED) {
            return;
        }
        if (width <= 0) {
            return;
        }

        logD("setResolutionInternal, width=" + width);
        mWidth = width;

        if (onBoot) {
            if (!isUserSetuped()) {
                applyOverlay(width == FHD_WIDTH);
            }
            return;
        }

        final int currentSwDp = getDisplaySwDp();
        logD("setResolutionInternal, currentSwDp=" + currentSwDp);

        if (DisplayResolutionManager.getDeviceType() == TYPE_FORCED) {
            final Display.Mode mode = getPreferMode(mDisplay, QHD_WIDTH);
            final float height = (float) mode.getPhysicalHeight();
            final int newHeight = (int) (width == FHD_WIDTH ? height * SCALE : height);
            mHeight = newHeight;
            logD("setResolutionInternal, newResolution=" + width + "x" + newHeight);
            mWms.setForcedDisplaySize(Display.DEFAULT_DISPLAY, width, newHeight);
            SystemProperties.set(PROP_SCALE_BOOTANIMATION, width == FHD_WIDTH ? "1" : "0");
        } else {
            final Display.Mode mode = getPreferMode(mDisplay, width);
            mHeight = mode.getPhysicalHeight();
            logD("setResolutionInternal, preferMode=" + mode);
            mDisplay.setUserPreferredDisplayMode(mode);
        }

        setDisplaySwDp(currentSwDp, width);
        storeDisplayWidth(width);
        applyOverlay(width == FHD_WIDTH);
        BlissUtils.forceStopDefaultLauncher(mContext);
        if (RESTART_SYSTEMUI_ON_SWITCH) {
            BlissUtils.restartSystemUi(mContext);
        }
        notifyDisplayResolutionChanged();
    }

    private void notifyDisplayResolutionChanged() {
        updateHeightIfNeeded();
        logD("notifyDisplayResolutionChanged, resolution=" + mWidth + "x" + mHeight);
        mHandler.post(() -> {
            synchronized (mListenerLock) {
                for (DisplayResolutionListener listener : mListeners) {
                    notifyDisplayResolutionChanged(listener.mListener);
                }
            }
        });
    }

    private void notifyDisplayResolutionChanged(IDisplayResolutionListener listener) {
        try {
            listener.onDisplayResolutionChanged(mWidth, mHeight);
        } catch (RemoteException | RuntimeException e) {
            logE("Failed to notify display resolution changed");
        }
    }

    private void applyOverlay(boolean enabled) {
        for (String overlay: mFhdOverlays) {
            try {
                mOverlayManager.setEnabled(overlay, enabled, UserHandle.USER_SYSTEM);
            } catch (RemoteException re) {
                logE("Failed to apply overlay: " + overlay);
            }
        }
    }

    private int getDisplaySwDp() {
        final DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        final float density = metrics.density;
        final int minDimensionPx = Math.min(metrics.widthPixels, metrics.heightPixels);
        return (int) (minDimensionPx / density);
    }

    private void setDisplaySwDp(int newSwDp, int width) {
        final DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        final float scale = (float) width / metrics.widthPixels;
        final int minDimensionPx = (int) Math.min(metrics.widthPixels * scale, metrics.heightPixels * scale);
        final int newDensity = DisplayMetrics.DENSITY_MEDIUM * minDimensionPx / newSwDp;
        final int densityDpi = Math.max(newDensity, 120);
        logD("setDisplaySwDp, density=" + densityDpi);
        mWms.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, densityDpi, UserHandle.USER_CURRENT);
    }

    private boolean isUserSetuped() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.DEVICE_PROVISIONED, 0) != 0 &&
            Settings.Secure.getInt(mContentResolver,
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }

    private int getStoredDisplayWidth() {
        final int deviceType = DisplayResolutionManager.getDeviceType();
        if (deviceType == TYPE_DISABLED) {
            return -1;
        }
        return Settings.Global.getInt(mContentResolver, DISPLAY_WIDTH_CUSTOM,
                deviceType == TYPE_AOSP_FHD_DEFAULT ? FHD_WIDTH : QHD_WIDTH);
    }

    private void storeDisplayWidth(int width) {
        Settings.Global.putInt(mContentResolver, DISPLAY_WIDTH_CUSTOM, width);
    }

    private void updateHeightIfNeeded() {
        if ((mWidth < 0 || mHeight < 0) && mSystemReady) {
            if (DisplayResolutionManager.getDeviceType() == TYPE_FORCED) {
                final Display.Mode mode = getPreferMode(mDisplay, QHD_WIDTH);
                final float height = (float) mode.getPhysicalHeight();
                mHeight = (int) (mWidth == FHD_WIDTH ? height * SCALE : height);
            } else if (mWidth > 0) {
                final Display.Mode mode = getPreferMode(mDisplay, mWidth);
                mHeight = mode.getPhysicalHeight();
            } else {
                final Display.Mode mode = mDisplay.getMode();
                mWidth = mode.getPhysicalWidth();
                mHeight = mode.getPhysicalHeight();
            }
            logD("updateHeightIfNeeded, resolution=" + mWidth + "x" + mHeight);
        }
    }

    private static Display.Mode getPreferMode(Display display, int width) {
        for (Point resolution : getAllSupportedResolution(display)) {
            if (resolution.x == width) {
                return new Display.Mode(
                        resolution.x, resolution.y, display.getMode().getRefreshRate());
            }
        }

        return display.getMode();
    }

    private static Set<Point> getAllSupportedResolution(Display display) {
        final Set<Point> resolutions = new ArraySet<>();
        for (Display.Mode mode : display.getSupportedModes()) {
            resolutions.add(new Point(mode.getPhysicalWidth(), mode.getPhysicalHeight()));
        }

        return resolutions;
    }

    private static void logD(String msg) {
        Slog.d(TAG, msg);
    }

    private static void logE(String msg) {
        Slog.e(TAG, msg);
    }
}
