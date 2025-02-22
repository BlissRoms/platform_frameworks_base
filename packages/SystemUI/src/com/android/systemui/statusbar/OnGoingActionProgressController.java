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
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
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
    private static final String ONGOING_ACTION_CHIP_ENABLED = "ongoing_action_chip";

    private Context mContext;
    private ContentResolver mContentResolver;
    private final Handler mHandler;
    private final SettingsObserver mSettingsObserver;

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
    private boolean mIsEnabled;

    private boolean mPreviousTrackingProgress = false;

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED))) {
                updateSettings();
            }
        }

        public void register() {
            mContentResolver.registerContentObserver(
                Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED),
                false, this, UserHandle.USER_ALL);
            // Update initial state
            updateSettings();
        }

        public void unregister() {
            mContentResolver.unregisterContentObserver(this);
        }
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
        mContentResolver = context.getContentResolver();
        mHandler = new Handler(Looper.getMainLooper());
        mSettingsObserver = new SettingsObserver(mHandler);
        
        mProgressBar = progressGroup.progressBarView;
        mProgressRootView = progressGroup.rootView;
        mIconView = progressGroup.iconView;
        mIconFetcher = new IconFetcher(context);
        mNotificationListener.addNotificationHandler(this);
        
        // Register settings observer
        mSettingsObserver.register();
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
        // Check if tracking state changed or if we're not tracking/enabled
        if (!mIsEnabled || !mIsTrackingProgress) {
            mProgressRootView.setVisibility(View.GONE);
            mPreviousTrackingProgress = mIsTrackingProgress;
            return;
        }

        // Update previous tracking state
        mPreviousTrackingProgress = mIsTrackingProgress;

        // Show and update progress
        mProgressRootView.setVisibility(View.VISIBLE);
        if (mCurrentProgressMax == 0) {
            Log.w(TAG, "updateViews: max progress is 0. Guessing it as 100");
            mCurrentProgressMax = 100;
        }
        
        // Hide if progress is complete
        if (mCurrentProgress >= mCurrentProgressMax) {
            mIsTrackingProgress = false;
            mProgressRootView.setVisibility(View.GONE);
            return;
        }

        Log.d(TAG, "updateViews: " + mCurrentProgress + "/" + mCurrentProgressMax);
        mProgressBar.setMax(mCurrentProgressMax);
        mProgressBar.setProgress(mCurrentProgress);
        if (mCurrentDrawable != null) {
            mIconView.setImageDrawable(mCurrentDrawable);
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
                mPreviousTrackingProgress = false;
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

    private void updateSettings() {
        mIsEnabled = Settings.System.getIntForUser(mContentResolver,
            ONGOING_ACTION_CHIP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        updateViews();
    }

    public void destroy() {
        mSettingsObserver.unregister();
        mIsTrackingProgress = false;
        mPreviousTrackingProgress = false;
        mCurrentDrawable = null;
        mCurrentProgress = 0;
        mCurrentProgressMax = 0;
        mTrackedNotificationKey = null;
    }
}
