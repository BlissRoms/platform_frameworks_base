/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.wm;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.server.DisplayResolutionController;
import com.android.server.DisplayThread;

import com.bliss.view.DisplayResolutionManager;

class WindowManagerServiceExt {

    private static class InstanceHolder {
        private static final WindowManagerServiceExt INSTANCE = new WindowManagerServiceExt();
    }

    static WindowManagerServiceExt getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private WindowManagerService mWms;

    void init(WindowManagerService wms) {
        mWms = wms;
        TopActivityRecorder.getInstance().initWms(wms);
        DisplayResolutionController.getInstance().init(wms.mContext, wms);
    }

    void systemReady() {
        DisplayResolutionController.getInstance().systemReady();
    }

    void registerContentObserver(ContentObserver observer) {
        final ContentResolver cr = mWms.mContext.getContentResolver();
    }

    void loadSettings() {
    }

    boolean onSettingsChanged(Uri uri) {
        return false;
    }

    void onUserSwitched() {
        loadSettings();
    }

    int getDensityWithScale(int density) {
        final int width = DisplayResolutionController.getInstance().getResolution().x;
        if (width > 0) {
            return (int) (density * DisplayResolutionManager.getDensityScale(width));
        }
        return density;
    }
}
