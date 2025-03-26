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
import android.content.Intent;
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
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;

import com.android.systemui.res.R;
import com.android.systemui.util.IconFetcher;
import com.android.systemui.statusbar.OnGoingActionProgressGroup;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.MediaSessionManagerHelper;

/** Controls the ongoing progress chip based on notifications @LineageExtension */
public class OnGoingActionProgressController implements NotificationListener.NotificationHandler, KeyguardStateController.Callback {
    private static final String TAG = "OngoingActionProgressController";
    private static final String ONGOING_ACTION_CHIP_ENABLED = "ongoing_action_chip";
    private static final String SHOW_MEDIA_PROGRESS = "show_media_progress";
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private Context mContext;
    private ContentResolver mContentResolver;
    private final Handler mHandler;
    private final SettingsObserver mSettingsObserver;
    private final KeyguardStateController mKeyguardStateController;
    private final NotificationListener mNotificationListener;
    private final IconFetcher mIconFetcher;
    private final MediaSessionManagerHelper mMediaSessionHelper;

    private final ProgressBar mProgressBar;
    private final View mProgressRootView;
    private final ImageView mIconView;

    private boolean mShowMediaProgress = true;
    private boolean mIsTrackingProgress = false;
    private boolean mIsForceHidden = false;
    private boolean mIsEnabled;
    private int mCurrentProgress = 0;
    private int mCurrentProgressMax = 0;
    private Drawable mCurrentDrawable = null;
    private String mTrackedNotificationKey;
    private PopupWindow mMediaPopup;

    private final GestureDetector mGestureDetector;
    private final Handler mMediaProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable mMediaProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                updateViews();
                mMediaProgressHandler.postDelayed(this, 1000);
            }
        }
    };

    /** Constructor */
    public OnGoingActionProgressController(
            Context context, OnGoingActionProgressGroup progressGroup,
            NotificationListener notificationListener,
            KeyguardStateController keyguardStateController) {
        if (progressGroup == null) {
            Log.wtf(TAG, "progressGroup is null");
        }
        mNotificationListener = notificationListener;
        if (mNotificationListener == null) {
            Log.wtf(TAG, "mNotificationListener is null");
        }

        mKeyguardStateController = keyguardStateController;
        keyguardStateController.addCallback(this);
        mContext = context;
        mContentResolver = context.getContentResolver();
        mHandler = new Handler(Looper.getMainLooper());
        mSettingsObserver = new SettingsObserver(mHandler);

        mProgressBar = progressGroup.progressBarView;
        mProgressRootView = progressGroup.rootView;
        mIconView = progressGroup.iconView;

        mIconFetcher = new IconFetcher(context);
        mNotificationListener.addNotificationHandler(this);
        mMediaSessionHelper = MediaSessionManagerHelper.Companion.getInstance(context);

        mGestureDetector = new GestureDetector(mContext, new MediaGestureListener());

        mSettingsObserver.register();
        mProgressRootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
        mMediaSessionHelper.addMediaMetadataListener(new MediaSessionManagerHelper.MediaMetadataListener() {
            @Override
            public void onMediaMetadataChanged() {
                updateViews();
            }

            @Override
            public void onPlaybackStateChanged() {
                updateViews();
            }
        });

        updateViews();
    }

    /** Gesture listener for media controls */
    private class MediaGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                showMediaPopup(mProgressRootView);
            } else {
                openTrackedApp();
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                toggleMediaPlaybackState();
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                openMediaApp();
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!(mShowMediaProgress && mMediaSessionHelper.isMediaPlaying())) {
                return false;
            }
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY()) &&
                    Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    skipToNextTrack();
                } else {
                    skipToPreviousTrack();
                }
                return true;
            }
            return false;
        }
    }

    /** Updates the UI based on current state */
    private void updateViews() {
        if (mIsForceHidden) {
            mProgressRootView.setVisibility(View.GONE);
            return;
        }

        boolean isMediaPlaying = mShowMediaProgress && mMediaSessionHelper.isMediaPlaying();
        if (isMediaPlaying) {
            updateMediaProgress();
        } else {
            updateNotificationProgress();
        }
    }

    /** Updates UI for media playback progress */
    private void updateMediaProgress() {
        mProgressRootView.setVisibility(View.VISIBLE);
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mMediaProgressHandler.post(mMediaProgressRunnable);

        Drawable mediaAppIcon = mMediaSessionHelper.getMediaAppIcon();
        mIconView.setImageDrawable(mediaAppIcon != null ? mediaAppIcon : mContext.getResources().getDrawable(R.drawable.ic_default_music_icon));

        long totalDuration = mMediaSessionHelper.getTotalDuration();
        long currentProgress = mMediaSessionHelper.getMediaControllerPlaybackState() != null
                ? mMediaSessionHelper.getMediaControllerPlaybackState().getPosition() : 0;
        if (totalDuration > 0) {
            mProgressBar.setMax((int) totalDuration);
            mProgressBar.setProgress((int) currentProgress);
        }

        mProgressRootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
    }

    /** Updates UI for notification progress */
    private void updateNotificationProgress() {
        if (!mIsEnabled || !mIsTrackingProgress) {
            mProgressRootView.setVisibility(View.GONE);
            mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
            if (!mMediaSessionHelper.isMediaPlaying()) {
                mIconView.setImageDrawable(null);
            }
            return;
        }

        mProgressRootView.setVisibility(View.VISIBLE);
        if (mCurrentProgressMax <= 0) {
            Log.w(TAG, "updateViews: invalid max progress " + mCurrentProgressMax + ", using 100");
            mCurrentProgressMax = 100;
        }

        Log.d(TAG, "updateViews: " + mCurrentProgress + "/" + mCurrentProgressMax);
        mProgressBar.setMax(mCurrentProgressMax);
        mProgressBar.setProgress(mCurrentProgress);

        if (mTrackedNotificationKey != null) {
            StatusBarNotification sbn = findNotificationByKey(mTrackedNotificationKey);
            if (sbn != null) {
                Drawable downloadAppIcon = mIconFetcher.getMonotonicPackageIcon(sbn.getPackageName()).drawable;
                if (downloadAppIcon != null) {
                    mIconView.setImageDrawable(downloadAppIcon);
                }
            }
        }
    }

    /** Helper to extract progress from a notification */
    private void extractProgress(Notification notification) {
        mCurrentProgressMax = notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 100);
        mCurrentProgress = notification.extras.getInt(Notification.EXTRA_PROGRESS, 0);
    }

    /** Tracks progress of a notification */
    private void trackProgress(final StatusBarNotification sbn) {
        mIsTrackingProgress = true;
        mTrackedNotificationKey = sbn.getKey();
        extractProgress(sbn.getNotification());
        IconFetcher.AdaptiveDrawableResult drawable = mIconFetcher.getMonotonicPackageIcon(sbn.getPackageName());
        mCurrentDrawable = drawable.drawable;
        updateIconImageView(drawable);
        updateViews();
    }

    /** Updates progress if the notification matches the tracked key */
    private void updateProgressIfNeeded(final StatusBarNotification sbn) {
        if (!mIsTrackingProgress) {
            Log.wtf(TAG, "Called updateProgress if needed, but we are not tracking anything");
            return;
        }
        if (sbn.getKey().equals(mTrackedNotificationKey)) {
            extractProgress(sbn.getNotification());
            updateViews();
        }
    }

    /** Finds a notification by its key */
    private StatusBarNotification findNotificationByKey(String key) {
        for (StatusBarNotification notification : mNotificationListener.getActiveNotifications()) {
            if (notification.getKey().equals(key)) {
                return notification;
            }
        }
        return null;
    }

    /** Checks if a notification has progress */
    private static boolean hasProgress(final Notification notification) {
        Bundle extras = notification.extras;
        boolean indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        boolean maxProgressValid = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;
        return extras.containsKey(Notification.EXTRA_PROGRESS) &&
                extras.containsKey(Notification.EXTRA_PROGRESS_MAX) &&
                !indeterminate && maxProgressValid;
    }

    /** Updates the icon view based on drawable properties */
    private void updateIconImageView(IconFetcher.AdaptiveDrawableResult drawable) {
        mIconView.setImageTintList(drawable.isAdaptive ?
                ColorStateList.valueOf(getThemeColor(mContext, android.R.attr.colorForeground)) : null);
        mIconView.setImageDrawable(drawable.drawable);
    }

    /** Shows a media control popup */
    private void showMediaPopup(View anchorView) {
        if (mMediaPopup != null && mMediaPopup.isShowing()) {
            mMediaPopup.dismiss();
            return;
        }

        View popupView = LayoutInflater.from(mContext).inflate(R.layout.media_control_popup, null);
        mMediaPopup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mMediaPopup.setOutsideTouchable(true);
        mMediaPopup.setFocusable(true);

        ImageButton btnPrevious = popupView.findViewById(R.id.btn_previous);
        ImageButton btnNext = popupView.findViewById(R.id.btn_next);
        btnPrevious.setOnClickListener(v -> {
            skipToPreviousTrack();
            mMediaPopup.dismiss();
        });
        btnNext.setOnClickListener(v -> {
            skipToNextTrack();
            mMediaPopup.dismiss();
        });

        anchorView.post(() -> {
            int offsetX = -popupView.getWidth() / 3;
            int offsetY = -anchorView.getHeight();
            mMediaPopup.showAsDropDown(anchorView, offsetX, offsetY);
        });
    }

    /** Opens the app associated with the tracked notification */
    private void openTrackedApp() {
        if (mTrackedNotificationKey == null || mNotificationListener == null) {
            Log.w(TAG, "No tracked notification available");
            return;
        }

        StatusBarNotification sbn = findNotificationByKey(mTrackedNotificationKey);
        if (sbn == null) {
            Log.w(TAG, "Tracked notification not found");
            return;
        }

        String packageName = sbn.getPackageName();
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(launchIntent);
        } else {
            Log.w(TAG, "No launch intent for package: " + packageName);
        }
    }

    /** Handles notification posted event */
    private void onNotificationPosted(final StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (!hasProgress(notification)) {
            if (mTrackedNotificationKey != null && mTrackedNotificationKey.equals(sbn.getKey())) {
                Log.d(TAG, "Tracked notification has lost progress");
                synchronized (this) {
                    mIsTrackingProgress = false;
                    mCurrentDrawable = null;
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

    /** Handles notification removed event */
    private void onNotificationRemoved(final StatusBarNotification sbn) {
        synchronized (this) {
            if (!mIsTrackingProgress || !sbn.getKey().equals(mTrackedNotificationKey)) {
                return;
            }
            mIsTrackingProgress = false;
            mCurrentDrawable = null;
            updateViews();
        }
    }

    /** Sets force hidden state */
    public void setForceHidden(final boolean forceHidden) {
        Log.d(TAG, "setForceHidden " + forceHidden);
        mIsForceHidden = forceHidden;
        updateViews();
    }

    private void toggleMediaPlaybackState() { mMediaSessionHelper.toggleMediaPlaybackState(); }
    private void skipToNextTrack() { mMediaSessionHelper.nextSong(); }
    private void skipToPreviousTrack() { mMediaSessionHelper.prevSong(); }
    private void openMediaApp() { mMediaSessionHelper.launchMediaApp(); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, NotificationListenerService.RankingMap _rankingMap) {
        onNotificationPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, NotificationListenerService.RankingMap _rankingMap) {
        onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, NotificationListenerService.RankingMap _rankingMap, int _reason) {
        onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRankingUpdate(NotificationListenerService.RankingMap _rankingMap) { /* stub */ }
    @Override
    public void onNotificationsInitialized() { /* stub */ }

    @Override
    public void onKeyguardShowingChanged() {
        setForceHidden(mKeyguardStateController.isShowing());
    }

    /** Settings observer for system settings */
    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) { super(handler); }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED)) ||
                    uri.equals(Settings.System.getUriFor(SHOW_MEDIA_PROGRESS))) {
                updateSettings();
            }
        }

        public void register() {
            mContentResolver.registerContentObserver(Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED), false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(SHOW_MEDIA_PROGRESS), false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        public void unregister() { mContentResolver.unregisterContentObserver(this); }
    }

    /** Updates settings from system preferences */
    private void updateSettings() {
        mIsEnabled = Settings.System.getIntForUser(mContentResolver, ONGOING_ACTION_CHIP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mShowMediaProgress = Settings.System.getIntForUser(mContentResolver, SHOW_MEDIA_PROGRESS, 1, UserHandle.USER_CURRENT) == 1;
        updateViews();
    }

    /** Cleans up resources */
    public void destroy() {
        mSettingsObserver.unregister();
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mIsTrackingProgress = false;
        mCurrentDrawable = null;
        mCurrentProgress = 0;
        mCurrentProgressMax = 0;
        mTrackedNotificationKey = null;
        mIconView.setImageDrawable(null);
    }

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
