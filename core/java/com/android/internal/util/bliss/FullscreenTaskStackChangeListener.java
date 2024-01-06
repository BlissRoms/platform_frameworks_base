/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.bliss;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

/**
 * TaskStackListener that ignores freeform / mini-window focuses change
 *
 * @hide
 */
public abstract class FullscreenTaskStackChangeListener extends TaskStackListener {

    private static final String DEFAULT_TAG = "FullscreenTaskStackChangeListener";

    private final ActivityManager mActivityManager;
    private final ActivityTaskManager mActivityTaskManager;
    private final IActivityTaskManager mIActivityTaskManager;

    private final boolean mObserveActivityChange;

    private boolean mDebug = false;
    private String mDebugTag = DEFAULT_TAG;

    private boolean mListening = false;

    private String mTopPackage = "";
    private String mTopActivity = "";
    private int mTopTaskId = INVALID_TASK_ID;

    public FullscreenTaskStackChangeListener(Context context) {
        this(context, false);
    }

    public FullscreenTaskStackChangeListener(Context context, boolean observeActivity) {
        mActivityManager = context.getSystemService(ActivityManager.class);
        mActivityTaskManager = ActivityTaskManager.getInstance();
        mIActivityTaskManager = ActivityTaskManager.getService();
        mObserveActivityChange = observeActivity;
    }

    public void setDebug(boolean debug) {
        mDebug = debug;
    }

    public void setDebugTag(String tag) {
        mDebugTag = tag;
    }

    public void setListening(boolean listening) {
        if (mListening != listening) {
            if (listening) {
                mActivityTaskManager.registerTaskStackListener(this);
                mListening = true;
            } else {
                mActivityTaskManager.unregisterTaskStackListener(this);
                mListening = false;
            }
        }
    }

    private void handleChange(String newPackageName, String newActivityName, int newTaskId) {
        if (mTopPackage.equals(newPackageName) && mTopActivity.equals(newActivityName)) {
            return;
        }
        mTopActivity = newActivityName;
        if (!mObserveActivityChange && mTopPackage.equals(newPackageName)) {
            return;
        }
        mTopPackage = newPackageName;
        mTopTaskId = newTaskId;
        if (mDebug) {
            Log.d(mDebugTag, "Change: mTopPackage=" + mTopPackage
                    + ", mTopActivity=" + mTopActivity
                    + ", mTopTaskId=" + mTopTaskId);
        }
        onFullscreenTaskChanged(mTopPackage, mTopActivity, mTopTaskId);
    }

    @Override
    public void onTaskStackChanged() {
        forceCheck();
    }

    @Override
    public void onTaskFocusChanged(int taskId, boolean focused) {
        if (focused) {
            forceCheck();
        }
    }

    public void forceCheck() {
        try {
            final ActivityTaskManager.RootTaskInfo info = mIActivityTaskManager.getFocusedRootTaskInfo();
            if (info == null) {
                return;
            }
            if (info.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                return;
            }
            final ComponentName topActivity = info.topActivity;
            if (topActivity == null) {
                return;
            }
            handleChange(topActivity.getPackageName(), topActivity.getClassName(), info.taskId);
        } catch (RemoteException e) {}
    }

    public String getTopPackageName() {
        return mTopPackage;
    }

    public String getTopActivityName() {
        return mTopActivity;
    }

    public int getTopTaskId() {
        return mTopTaskId;
    }

    public abstract void onFullscreenTaskChanged(
            String packageName, String activityName, int taskId);
}
