/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.bliss.display;

import com.bliss.display.IRefreshRateListener;

/** @hide */
interface IRefreshRateManagerService {

    /* Request MEMC required refresh rate in video apps */
    void requestMemcRefreshRate(in int refreshRate);

    /* Restore refresh rate after exiting MEMC mode */
    void clearRequestedMemcRefreshRate();

    /* Get user preferred refresh rate for specific package, return -1 if not set */
    int getRefreshRateForPackage(in String packageName);

    /* Request user preferred refresh rate for specific package */
    void setRefreshRateForPackage(in String packageName, in int refreshRate);

    /* Reset user preferred refresh rate for specific package (Follow system) */
    void unsetRefreshRateForPackage(in String packageName);

    /* Force highest refresh rate in every apps, unless MEMC is running */
    void setExtremeRefreshRateEnabled(in boolean enabled);

    /* Get user preferred refresh in current app, return -1 if not set */
    int getRequestedRefreshRate();

    /* Get current MEMC requested refresh rate, return -1 if not set */
    int getRequestedMemcRefreshRate();

    /* Register listener to listen requested refresh rate change */
    boolean registerRefreshRateListener(in IRefreshRateListener listener);

    /* Unregister listener to listen requested refresh rate change */
    boolean unregisterRefreshRateListener(in IRefreshRateListener listener);
}
