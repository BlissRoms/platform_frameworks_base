/**
 * Copyright (c) 2025, The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.app.Notification;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.android.systemui.res.R;
import com.android.systemui.util.IconFetcher;
import com.android.systemui.statusbar.OnGoingActionProgressGroup;

/** Controls the ongoing progress chip based on notifcations @LineageExtension */
public class OnGoingActionProgressController implements NotificationListener.NotificationHandler {
    private static final String TAG = "OngoingActionProgressController";

    private Context mContext;

    // Views of chip
    private final ProgressBar mProgressBar;
    private final View mProgressRootView;
    private final ImageView mIconView;

    // Progress tracking variables
    private boolean mIsTrackingProgress = false;
    private int mCurrentProgress = 0;
    private int mCurrentProgressMax = 0;
    private Drawable mCurrentDrawable = null;
    private String mTrackedNotificationKey;

    private final IconFetcher mIconFetcher;

    private final NotificationListener mNotificationListener;

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    /**
     * Creates controller for ongoing progress notifications
     *
     * @param View status bar View object to find progress chip
     */
    public OnGoingActionProgressController(
            Context context, OnGoingActionProgressGroup progressGroup,
            NotificationListener notificationListener) {
        if (progressGroup == null) {
            Log.wtf(TAG, "progressGroup is null");
        }
        mNotificationListener = notificationListener;
        if (mNotificationListener == null) {
            Log.wtf(TAG, "mNotificationListener is null");
        }
        mContext = context;
        mProgressBar = progressGroup.progressBarView;
        mProgressRootView = progressGroup.rootView;
        mIconView = progressGroup.iconView;
        mIconFetcher = new IconFetcher(context);
        mNotificationListener.addNotificationHandler(this);
    }

    /** Checks whether notification has progress */
    private static boolean hasProgress(final Notification notification) {
        Bundle extras = notification.extras;
        boolean indeterminate =
                notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);

        boolean maxProgressValid =
                notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;

        return extras.containsKey(Notification.EXTRA_PROGRESS)
                && extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
                && !indeterminate
                && maxProgressValid;
    }

    /** Starts tracking progress of certain notification @AsyncUnsafe */
    private void trackProgress(final StatusBarNotification sbn) {
        // Here we set progress tracking and update view if needed
        mIsTrackingProgress = true;
        mTrackedNotificationKey = sbn.getKey();
        Notification notification = sbn.getNotification();
        mCurrentProgressMax = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 100);
        mCurrentProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS, 0);
        IconFetcher.AdaptiveDrawableResult drawable =
                mIconFetcher.getMonotonicPackageIcon(sbn.getPackageName());
        updateIconImageView(drawable);
        updateViews();
    }

    /** Updates icon based on result from IconFetcher @AsyncUnsafe */
    private void updateIconImageView(IconFetcher.AdaptiveDrawableResult drawable) {
        if (drawable.isAdaptive) {
            mIconView.setImageTintList(
                    ColorStateList.valueOf(
                            getThemeColor(mContext, android.R.attr.colorForeground)));
        } else {
            mIconView.setImageTintList(null);
        }
        mIconView.setImageDrawable(drawable.drawable);
    }

    /** Updates progress if needed @AsyncUnsafe */
    private void updateProgressIfNeeded(final StatusBarNotification sbn) {
        if (!mIsTrackingProgress) {
            Log.wtf(TAG, "Called updateProgress if needed, but we do not tracking anything");
            return;
        }
        // Log.d(TAG, "updateProgressIfNeeded: got notification update");
        Notification notification = sbn.getNotification();
        if (sbn.getKey().equals(mTrackedNotificationKey)) {
            mCurrentProgressMax = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 100);
            mCurrentProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS, 0);
            Log.d(TAG, "updateProgressIfNeeded: about to updateViews()");
            updateViews();
        }
    }

    /** Updates progress views @AsyncUnsafe */
    private void updateViews() {
        if (!mIsTrackingProgress) {
            mProgressRootView.setVisibility(View.GONE);
        } else {
            // TODO: make it a bit faster by checking wether mIsTrackingProgress has changed between
            // calls
            mProgressRootView.setVisibility(View.VISIBLE);
            if (mCurrentProgressMax == 0) {
                Log.w(TAG, "updateViews: max progress is 0. Guessing it as 100");
                mCurrentProgressMax = 100;
            }
            Log.d(TAG, "updateViews: " + mCurrentProgress + "/" + mCurrentProgressMax);
            mProgressBar.setMax(mCurrentProgressMax);
            mProgressBar.setProgress(mCurrentProgress);
            if (mCurrentDrawable != null) {
                mIconView.setImageDrawable(mCurrentDrawable);
            }
        }
    }

    /**
     * Should be called when new notification is posted
     *
     * @param StatusBarNotification a notification which was posted
     */
    private void onNotificationPosted(final StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (!hasProgress(notification)) {
            // Log.d(TAG, "Got notification without progress");
            if (sbn.getKey() == mTrackedNotificationKey) {
                // The notification we track has no progress anymore
                Log.d(TAG, "Tracked notification has lost progress");
                synchronized (this) {
                    mIsTrackingProgress = false;
                    updateViews();
                }
            }
            return;
        }
        synchronized (this) {
            if (!mIsTrackingProgress) {
                trackProgress(sbn);
            } else {
                updateProgressIfNeeded(sbn);
            }
        }
    }

    /**
     * Should be call when notification is removed
     *
     * @param StatusBarNotification a notification which was removed
     */
    private void onNotificationRemoved(final StatusBarNotification sbn) {
        synchronized (this) {
            if (!mIsTrackingProgress) {
                return;
            }
            if (sbn.getKey().equals(mTrackedNotificationKey)) {
                mIsTrackingProgress = false;
                mCurrentDrawable = null;
                updateViews();
            }
        }
    }

    // Implementation of notification handler
    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn, NotificationListenerService.RankingMap _rankingMap) {
        onNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap _rankingMap,
            int _reason) {
        onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRankingUpdate(NotificationListenerService.RankingMap _rankingMap) {
        /*stub*/
    }

    @Override
    public void onNotificationsInitialized() {
        /*stub*/
    }
}
