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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.res.R;
import com.android.systemui.util.IconFetcher;
import com.android.systemui.statusbar.OnGoingActionProgressGroup;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.MediaSessionManagerHelper;

import com.android.internal.util.android.VibrationUtils;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Controls the ongoing progress chip based on notifications @LineageExtension */
public class OnGoingActionProgressController implements NotificationListener.NotificationHandler, KeyguardStateController.Callback {
    private static final String TAG = "OngoingActionProgressController";
    private static final String ONGOING_ACTION_CHIP_ENABLED = "ongoing_action_chip";
    private static final String SHOW_MEDIA_PROGRESS = "show_media_progress";
    private static final String PROGRESS_BAR_OPACITY = "progress_bar_opacity";
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private static final int DEFAULT_OPACITY = 255;
    private static final int DEFAULT_OPACITY_PERCENTAGE = 100;
    private static final int MEDIA_UPDATE_INTERVAL_MS = 1000;
    private static final int DEBOUNCE_DELAY_MS = 150;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final Handler mHandler;
    private final SettingsObserver mSettingsObserver;
    private final KeyguardStateController mKeyguardStateController;
    private final NotificationListener mNotificationListener;
    private final IconFetcher mIconFetcher;
    private final MediaSessionManagerHelper mMediaSessionHelper;
    private final Executor mBackgroundExecutor;

    private final ProgressBar mProgressBar;
    private final View mProgressRootView;
    private final ImageView mIconView;

    // Cache for package icons to avoid repeated loading
    private final HashMap<String, IconFetcher.AdaptiveDrawableResult> mIconCache = new HashMap<>();
    
    private boolean mShowMediaProgress = true;
    private boolean mIsTrackingProgress = false;
    private boolean mIsForceHidden = false;
    private boolean mIsEnabled;
    private int mCurrentProgress = 0;
    private int mCurrentProgressMax = 0;
    private int mProgressBarOpacity = DEFAULT_OPACITY;
    private String mTrackedNotificationKey;
    private String mTrackedPackageName;
    private PopupWindow mMediaPopup;
    private boolean mIsPopupActive = false;
    private boolean mNeedsFullUiUpdate = true;
    private boolean mIsViewAttached = false;
    
    // Debounce UI updates
    private boolean mUpdatePending = false;
    private long mLastUpdateTime = 0;

    private final GestureDetector mGestureDetector;
    private final Handler mMediaProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable mMediaProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                updateMediaProgressOnly();
                mMediaProgressHandler.postDelayed(this, MEDIA_UPDATE_INTERVAL_MS);
            }
        }
    };

    private final MediaSessionManagerHelper.MediaMetadataListener mMediaMetadataListener = 
            new MediaSessionManagerHelper.MediaMetadataListener() {
                @Override
                public void onMediaMetadataChanged() {
                    // Force full UI update when metadata changes
                    mNeedsFullUiUpdate = true;
                    requestUiUpdate();
                }

                @Override
                public void onPlaybackStateChanged() {
                    // Force full UI update when playback state changes
                    mNeedsFullUiUpdate = true;
                    requestUiUpdate();
                }
            };

    /** Constructor */
    public OnGoingActionProgressController(
            Context context, OnGoingActionProgressGroup progressGroup,
            NotificationListener notificationListener,
            KeyguardStateController keyguardStateController) {
        if (progressGroup == null) {
            Log.wtf(TAG, "progressGroup is null");
            throw new IllegalArgumentException("progressGroup cannot be null");
        }
        
        mNotificationListener = notificationListener;
        if (mNotificationListener == null) {
            Log.wtf(TAG, "mNotificationListener is null");
            throw new IllegalArgumentException("notificationListener cannot be null");
        }

        mKeyguardStateController = keyguardStateController;
        mContext = context;
        mContentResolver = context.getContentResolver();
        mHandler = new Handler(Looper.getMainLooper());
        mSettingsObserver = new SettingsObserver(mHandler);
        mBackgroundExecutor = Executors.newSingleThreadExecutor();

        mProgressBar = progressGroup.progressBarView;
        mProgressRootView = progressGroup.rootView;
        mIconView = progressGroup.iconView;

        mIconFetcher = new IconFetcher(context);
        mMediaSessionHelper = MediaSessionManagerHelper.Companion.getInstance(context);

        mGestureDetector = new GestureDetector(mContext, new MediaGestureListener());

        // Initialize
        mKeyguardStateController.addCallback(this);
        mNotificationListener.addNotificationHandler(this);
        mSettingsObserver.register();
        
        // Optimize touch listeners - only set once
        mProgressRootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
        mMediaSessionHelper.addMediaMetadataListener(mMediaMetadataListener);
        
        mIsViewAttached = true;
        updateSettings();
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
            VibrationUtils.triggerVibration(mContext, 3);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                toggleMediaPlaybackState();
            }
            VibrationUtils.triggerVibration(mContext, 4);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                openMediaApp();
            }
            VibrationUtils.triggerVibration(mContext, 5);
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

    /**
     * Request a UI update with debouncing to prevent too many rapid updates
     */
    private void requestUiUpdate() {
        long currentTime = System.currentTimeMillis();
        if (!mUpdatePending && (currentTime - mLastUpdateTime > DEBOUNCE_DELAY_MS)) {
            // Update immediately if enough time has passed since last update
            mUpdatePending = false;
            mLastUpdateTime = currentTime;
            updateViews();
        } else if (!mUpdatePending) {
            // Schedule update for later
            mUpdatePending = true;
            mHandler.postDelayed(() -> {
                mUpdatePending = false;
                mLastUpdateTime = System.currentTimeMillis();
                updateViews();
            }, DEBOUNCE_DELAY_MS);
        }
    }

    /** Updates the UI based on current state */
    private void updateViews() {
        if (!mIsViewAttached) return;
        
        mProgressRootView.setAlpha(mProgressBarOpacity / 255f);
        
        if (mIsForceHidden) {
            mProgressRootView.setVisibility(View.GONE);
            return;
        }

        boolean isMediaPlaying = mShowMediaProgress && mMediaSessionHelper.isMediaPlaying();
        if (isMediaPlaying) {
            if (mNeedsFullUiUpdate) {
                updateMediaProgressFull();
                mNeedsFullUiUpdate = false;
            } else {
                updateMediaProgressOnly();
            }
        } else {
            updateNotificationProgress();
        }
    }

    /** Updates only the media progress value without changing other UI elements */
    private void updateMediaProgressOnly() {
        if (!mIsViewAttached) return;
        
        // Only update if visible to avoid unnecessary work
        if (mProgressRootView.getVisibility() != View.VISIBLE) return;
        
        long totalDuration = mMediaSessionHelper.getTotalDuration();
        long currentProgress = mMediaSessionHelper.getMediaControllerPlaybackState() != null
                ? mMediaSessionHelper.getMediaControllerPlaybackState().getPosition() : 0;
                
        if (totalDuration > 0 && mProgressBar != null) {
            mProgressBar.setMax((int) totalDuration);
            mProgressBar.setProgress((int) currentProgress);
        }
    }

    /** Updates complete media UI including icon and visibility */
    private void updateMediaProgressFull() {
        if (!mIsViewAttached) return;
        
        mProgressRootView.setVisibility(View.VISIBLE);
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mMediaProgressHandler.post(mMediaProgressRunnable);

        // Load icon in background if needed
        Drawable mediaAppIcon = mMediaSessionHelper.getMediaAppIcon();
        
        if (mediaAppIcon != null) {
            mIconView.setImageDrawable(mediaAppIcon);
        } else {
            // Get current media session package and load icon if available
            String packageName = null;
            if (mMediaSessionHelper.getMediaControllerPlaybackState() != null &&
                mMediaSessionHelper.getMediaControllerPlaybackState().getExtras() != null) {
                packageName = mMediaSessionHelper.getMediaControllerPlaybackState().getExtras().getString("package");
            }
            
            if (packageName != null) {
                loadIconInBackground(packageName, drawable -> {
                    if (mIconView != null && drawable != null) {
                        mIconView.setImageDrawable(drawable);
                    } else if (mIconView != null) {
                        mIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_default_music_icon));
                    }
                });
            } else if (mIconView != null) {
                mIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_default_music_icon));
            }
        }

        updateMediaProgressOnly();
    }

    /** Updates UI for notification progress */
    private void updateNotificationProgress() {
        if (!mIsViewAttached) return;
        
        if (!mIsEnabled || !mIsTrackingProgress) {
            mProgressRootView.setVisibility(View.GONE);
            mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
            return;
        }

        mProgressRootView.setVisibility(View.VISIBLE);
        if (mCurrentProgressMax <= 0) {
            Log.w(TAG, "updateViews: invalid max progress " + mCurrentProgressMax + ", using 100");
            mCurrentProgressMax = 100;
        }

        if (mProgressBar != null) {
            mProgressBar.setMax(mCurrentProgressMax);
            mProgressBar.setProgress(mCurrentProgress);
        }

        // Use cached icons or load in background
        if (mTrackedPackageName != null) {
            loadIconInBackground(mTrackedPackageName, drawable -> {
                if (mIconView != null && drawable != null) {
                    mIconView.setImageDrawable(drawable);
                }
            });
        }
    }

    /**
     * Load package icon in background thread and cache it
     */
    private void loadIconInBackground(String packageName, IconCallback callback) {
        if (packageName == null) return;
        
        // Check cache first
        if (mIconCache.containsKey(packageName)) {
            IconFetcher.AdaptiveDrawableResult cachedResult = mIconCache.get(packageName);
            if (cachedResult != null && cachedResult.drawable != null) {
                callback.onIconLoaded(cachedResult.drawable);
                return;
            }
        }
        
        // Load in background
        mBackgroundExecutor.execute(() -> {
            final IconFetcher.AdaptiveDrawableResult iconResult = 
                    mIconFetcher.getMonotonicPackageIcon(packageName);
            
            if (iconResult != null && iconResult.drawable != null) {
                // Cache the result
                mIconCache.put(packageName, iconResult);
                
                // Apply on main thread
                mHandler.post(() -> {
                    callback.onIconLoaded(iconResult.drawable);
                });
            }
        });
    }
    
    /** Interface for icon loading callbacks */
    private interface IconCallback {
        void onIconLoaded(@Nullable Drawable drawable);
    }

    /** Helper to extract progress from a notification */
    private void extractProgress(Notification notification) {
        Bundle extras = notification.extras;
        mCurrentProgressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 100);
        mCurrentProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0);
    }

    /** Tracks progress of a notification */
    private void trackProgress(final StatusBarNotification sbn) {
        mIsTrackingProgress = true;
        mTrackedNotificationKey = sbn.getKey();
        mTrackedPackageName = sbn.getPackageName();
        extractProgress(sbn.getNotification());
        requestUiUpdate();
    }

    /** Updates progress if the notification matches the tracked key */
    private void updateProgressIfNeeded(final StatusBarNotification sbn) {
        if (!mIsTrackingProgress) {
            return;
        }
        if (sbn.getKey().equals(mTrackedNotificationKey)) {
            extractProgress(sbn.getNotification());
            requestUiUpdate();
        }
    }

    /** Finds a notification by its key */
    @Nullable
    private StatusBarNotification findNotificationByKey(String key) {
        if (key == null || mNotificationListener == null) return null;
        
        for (StatusBarNotification notification : mNotificationListener.getActiveNotifications()) {
            if (notification.getKey().equals(key)) {
                return notification;
            }
        }
        return null;
    }

    /** Checks if a notification has progress */
    private static boolean hasProgress(@NonNull final Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return false;
        
        boolean indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        boolean maxProgressValid = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;
        return extras.containsKey(Notification.EXTRA_PROGRESS) &&
               extras.containsKey(Notification.EXTRA_PROGRESS_MAX) &&
               !indeterminate && maxProgressValid;
    }

    /** Shows a media control popup */
    private void showMediaPopup(View anchorView) {
        if (mIsPopupActive) {
            if (mMediaPopup != null) {
                mMediaPopup.dismiss();
            }
            mIsPopupActive = false;
            return;
        }

        // Use view to ensure context is still valid
        Context context = anchorView.getContext();
        View popupView = LayoutInflater.from(context).inflate(R.layout.media_control_popup, null);
        
        if (mMediaPopup != null && mMediaPopup.isShowing()) {
            mMediaPopup.dismiss();
        }
        
        mMediaPopup = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        mMediaPopup.setOutsideTouchable(true);
        mMediaPopup.setFocusable(true);
        mMediaPopup.setOnDismissListener(() -> mIsPopupActive = false);

        ImageButton btnPrevious = popupView.findViewById(R.id.btn_previous);
        ImageButton btnNext = popupView.findViewById(R.id.btn_next);
        
        if (btnPrevious != null) {
            btnPrevious.setOnClickListener(v -> {
                skipToPreviousTrack();
                mMediaPopup.dismiss();
            });
        }
        
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                skipToNextTrack();
                mMediaPopup.dismiss();
            });
        }

        anchorView.post(() -> {
            if (!mIsViewAttached) return;
            
            int offsetX = -popupView.getWidth() / 3;
            int offsetY = -anchorView.getHeight();
            mMediaPopup.showAsDropDown(anchorView, offsetX, offsetY);
            mIsPopupActive = true;
        });
    }

    /** Opens the app associated with the tracked notification */
    private void openTrackedApp() {
        if (mTrackedPackageName == null) {
            Log.w(TAG, "No tracked package available");
            return;
        }

        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(mTrackedPackageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(launchIntent);
        } else {
            Log.w(TAG, "No launch intent for package: " + mTrackedPackageName);
        }
    }

    /** Handles notification posted event */
    private void onNotificationPosted(final StatusBarNotification sbn) {
        if (sbn == null) return;
        
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        
        // Process in background to avoid UI jank
        mBackgroundExecutor.execute(() -> {
            boolean hasValidProgress = hasProgress(notification);
            
            if (!hasValidProgress) {
                if (mTrackedNotificationKey != null && mTrackedNotificationKey.equals(sbn.getKey())) {
                    Log.d(TAG, "Tracked notification has lost progress");
                    synchronized (this) {
                        mIsTrackingProgress = false;
                        mTrackedPackageName = null;
                        mHandler.post(this::requestUiUpdate);
                    }
                }
                return;
            }
            
            synchronized (this) {
                if (!mIsTrackingProgress) {
                    // New notification to track
                    mHandler.post(() -> trackProgress(sbn));
                } else {
                    // Update existing notification
                    mHandler.post(() -> updateProgressIfNeeded(sbn));
                }
            }
        });
    }

    /** Handles notification removed event */
    private void onNotificationRemoved(final StatusBarNotification sbn) {
        if (sbn == null) return;
        
        synchronized (this) {
            if (!mIsTrackingProgress || !sbn.getKey().equals(mTrackedNotificationKey)) {
                return;
            }
            mIsTrackingProgress = false;
            mTrackedPackageName = null;
            requestUiUpdate();
        }
    }

    /** Sets force hidden state */
    public void setForceHidden(final boolean forceHidden) {
        if (mIsForceHidden != forceHidden) {
            Log.d(TAG, "setForceHidden " + forceHidden);
            mIsForceHidden = forceHidden;
            requestUiUpdate();
        }
    }

    // Media playback control helpers
    private void toggleMediaPlaybackState() { 
        if (mMediaSessionHelper != null) {
            mMediaSessionHelper.toggleMediaPlaybackState(); 
        }
    }
    
    private void skipToNextTrack() { 
        if (mMediaSessionHelper != null) {
            mMediaSessionHelper.nextSong(); 
        }
    }
    
    private void skipToPreviousTrack() { 
        if (mMediaSessionHelper != null) {
            mMediaSessionHelper.prevSong(); 
        }
    }
    
    private void openMediaApp() { 
        if (mMediaSessionHelper != null) {
            mMediaSessionHelper.launchMediaApp(); 
        }
    }

    // NotificationHandler implementation
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
    public void onNotificationRankingUpdate(NotificationListenerService.RankingMap _rankingMap) {
        // No need to process ranking updates
    }
    
    @Override
    public void onNotificationsInitialized() {
        // Opportunity to handle initial notification set if needed
    }

    // KeyguardStateController.Callback implementation
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
                    uri.equals(Settings.System.getUriFor(SHOW_MEDIA_PROGRESS)) ||
                    uri.equals(Settings.System.getUriFor(PROGRESS_BAR_OPACITY))) {
                updateSettings();
            }
        }

        public void register() {
            mContentResolver.registerContentObserver(Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED), 
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(SHOW_MEDIA_PROGRESS), 
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(PROGRESS_BAR_OPACITY), 
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        public void unregister() { 
            mContentResolver.unregisterContentObserver(this); 
        }
    }

    /** Updates settings from system preferences */
    private void updateSettings() {
        boolean wasEnabled = mIsEnabled;
        boolean wasShowingMedia = mShowMediaProgress;
        
        mIsEnabled = Settings.System.getIntForUser(mContentResolver, 
                ONGOING_ACTION_CHIP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mShowMediaProgress = Settings.System.getIntForUser(mContentResolver, 
                SHOW_MEDIA_PROGRESS, 0, UserHandle.USER_CURRENT) == 1;
        
        // Read opacity as percentage (0-100)
        int opacityPercentage = Settings.System.getIntForUser(mContentResolver, 
                PROGRESS_BAR_OPACITY, DEFAULT_OPACITY_PERCENTAGE, UserHandle.USER_CURRENT);
        
        // Ensure percentage is within valid range
        opacityPercentage = Math.max(0, Math.min(100, opacityPercentage));
        
        // Convert percentage to alpha value (0-255)
        mProgressBarOpacity = (int)(opacityPercentage * 2.55f);
        
        // Only update if something actually changed
        if (wasEnabled != mIsEnabled || wasShowingMedia != mShowMediaProgress) {
            mNeedsFullUiUpdate = true;
        }
        
        requestUiUpdate();
    }

    /** Cleans up resources */
    public void destroy() {
        mIsViewAttached = false;
        
        // Unregister observers/callbacks
        mSettingsObserver.unregister();
        mKeyguardStateController.removeCallback(this);
        mMediaSessionHelper.removeMediaMetadataListener(mMediaMetadataListener);
        
        // Cancel any pending operations
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mHandler.removeCallbacksAndMessages(null);
        
        // Dismiss popup if showing
        if (mMediaPopup != null && mMediaPopup.isShowing()) {
            mMediaPopup.dismiss();
        }
        
        // Clear references
        mIsTrackingProgress = false;
        mTrackedNotificationKey = null;
        mTrackedPackageName = null;
        
        // Clear icon cache
        mIconCache.clear();
        
        // Clear views
        if (mIconView != null) {
            mIconView.setImageDrawable(null);
        }
    }

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
