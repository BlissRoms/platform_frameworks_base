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

public class OnGoingActionProgressController implements NotificationListener.NotificationHandler, KeyguardStateController.Callback {
    private static final String TAG = "OngoingActionProgressController";
    private static final String ONGOING_ACTION_CHIP_ENABLED = "ongoing_action_chip";
    private static final String SHOW_MEDIA_PROGRESS = "show_media_progress";
    private static final String PROGRESS_BAR_OPACITY = "progress_bar_opacity";
    private static final String COMPACT_MODE_ENABLED = "compact_progress_mode";
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
    private final ProgressBar mCircularProgressBar;
    private final View mProgressRootView;
    private final View mCompactRootView;
    private final ImageView mIconView;
    private final ImageView mCompactIconView;

    private final HashMap<String, IconFetcher.AdaptiveDrawableResult> mIconCache = new HashMap<>();
    
    private boolean mShowMediaProgress = true;
    private boolean mIsTrackingProgress = false;
    private boolean mIsForceHidden = false;
    private boolean mIsEnabled;
    private boolean mIsCompactModeEnabled = false;
    private int mCurrentProgress = 0;
    private int mCurrentProgressMax = 0;
    private int mProgressBarOpacity = DEFAULT_OPACITY;
    private String mTrackedNotificationKey;
    private String mTrackedPackageName;
    private PopupWindow mMediaPopup;
    private boolean mIsPopupActive = false;
    private boolean mNeedsFullUiUpdate = true;
    private boolean mIsViewAttached = false;
    private boolean mIsExpanded = false;
    
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
                    mNeedsFullUiUpdate = true;
                    requestUiUpdate();
                }

                @Override
                public void onPlaybackStateChanged() {
                    mNeedsFullUiUpdate = true;
                    requestUiUpdate();
                }
            };

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
        mCircularProgressBar = progressGroup.circularProgressBarView;
        mProgressRootView = progressGroup.rootView;
        mCompactRootView = progressGroup.compactRootView;
        mIconView = progressGroup.iconView;
        mCompactIconView = progressGroup.compactIconView;

        mIconFetcher = new IconFetcher(context);
        mMediaSessionHelper = MediaSessionManagerHelper.Companion.getInstance(context);

        mGestureDetector = new GestureDetector(mContext, new MediaGestureListener());

        mKeyguardStateController.addCallback(this);
        mNotificationListener.addNotificationHandler(this);
        mSettingsObserver.register();
        
        mProgressRootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
        mCompactRootView.setOnTouchListener((v, event) -> mGestureDetector.onTouchEvent(event));
        
        mCompactRootView.setOnClickListener(v -> {
            if (mIsCompactModeEnabled && !mIsExpanded) {
                expandCompactView();
            } else if (mShowMediaProgress && mMediaSessionHelper.isMediaPlaying()) {
                showMediaPopup(mProgressRootView);
            } else {
                openTrackedApp();
            }
            VibrationUtils.triggerVibration(mContext, 3);
        });
        
        mMediaSessionHelper.addMediaMetadataListener(mMediaMetadataListener);
        
        mIsViewAttached = true;
        updateSettings();
    }

    private void expandCompactView() {
        mIsExpanded = true;
        mCompactRootView.setVisibility(View.GONE);
        mProgressRootView.setVisibility(View.VISIBLE);
        
        mHandler.postDelayed(() -> {
            if (mIsCompactModeEnabled && mIsExpanded) {
                mIsExpanded = false;
                requestUiUpdate();
            }
        }, 5000);
    }

    private class MediaGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mIsCompactModeEnabled && !mIsExpanded) {
                expandCompactView();
                return true;
            }
            
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

    private void requestUiUpdate() {
        long currentTime = System.currentTimeMillis();
        if (!mUpdatePending && (currentTime - mLastUpdateTime > DEBOUNCE_DELAY_MS)) {
            mUpdatePending = false;
            mLastUpdateTime = currentTime;
            updateViews();
        } else if (!mUpdatePending) {
            mUpdatePending = true;
            mHandler.postDelayed(() -> {
                mUpdatePending = false;
                mLastUpdateTime = System.currentTimeMillis();
                updateViews();
            }, DEBOUNCE_DELAY_MS);
        }
    }

    private void updateViews() {
        if (!mIsViewAttached) return;
        
        float opacity = mProgressBarOpacity / 255f;
        mProgressRootView.setAlpha(opacity);
        mCompactRootView.setAlpha(opacity);
        
        if (mIsForceHidden) {
            mProgressRootView.setVisibility(View.GONE);
            mCompactRootView.setVisibility(View.GONE);
            return;
        }

        boolean isMediaPlaying = mShowMediaProgress && mMediaSessionHelper.isMediaPlaying();
        
        if (mIsCompactModeEnabled && !mIsExpanded) {
            mProgressRootView.setVisibility(View.GONE);
            
            if (!mIsEnabled && !isMediaPlaying) {
                mCompactRootView.setVisibility(View.GONE);
                return;
            }
            
            mCompactRootView.setVisibility(View.VISIBLE);
            
            if (isMediaPlaying) {
                updateMediaProgressCompact();
            } else {
                updateNotificationProgressCompact();
            }
        } else {
            mCompactRootView.setVisibility(View.GONE);
            
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
    }

private void updateMediaProgressOnly() {
    if (!mIsViewAttached) return;
    
    long totalDuration = mMediaSessionHelper.getTotalDuration();
    long currentProgress = mMediaSessionHelper.getMediaControllerPlaybackState() != null
            ? mMediaSessionHelper.getMediaControllerPlaybackState().getPosition() : 0;
            
    // Update the standard progress bar if visible
    if (mProgressRootView.getVisibility() == View.VISIBLE && mProgressBar != null && totalDuration > 0) {
        mProgressBar.setMax((int) totalDuration);
        mProgressBar.setProgress((int) currentProgress);
    }
    
    // Also update the circular progress bar for compact mode
    if (mCompactRootView.getVisibility() == View.VISIBLE && mCircularProgressBar != null && totalDuration > 0) {
        mCircularProgressBar.setMax((int) totalDuration);
        mCircularProgressBar.setProgress((int) currentProgress);
    }
}

    private void updateMediaProgressFull() {
        if (!mIsViewAttached) return;
        
        mProgressRootView.setVisibility(View.VISIBLE);
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mMediaProgressHandler.post(mMediaProgressRunnable);

        Drawable mediaAppIcon = mMediaSessionHelper.getMediaAppIcon();
        
        if (mediaAppIcon != null) {
            mIconView.setImageDrawable(mediaAppIcon);
        } else {
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
    
    private void updateMediaProgressCompact() {
        if (!mIsViewAttached) return;
        
        mCompactRootView.setVisibility(View.VISIBLE);
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mMediaProgressHandler.post(mMediaProgressRunnable);

        long totalDuration = mMediaSessionHelper.getTotalDuration();
        long currentProgress = mMediaSessionHelper.getMediaControllerPlaybackState() != null
                ? mMediaSessionHelper.getMediaControllerPlaybackState().getPosition() : 0;
                
        if (totalDuration > 0 && mCircularProgressBar != null) {
            mCircularProgressBar.setMax((int) totalDuration);
            mCircularProgressBar.setProgress((int) currentProgress);
        }

        Drawable mediaAppIcon = mMediaSessionHelper.getMediaAppIcon();
        
        if (mediaAppIcon != null) {
            mCompactIconView.setImageDrawable(mediaAppIcon);
        } else {
            String packageName = null;
            if (mMediaSessionHelper.getMediaControllerPlaybackState() != null &&
                mMediaSessionHelper.getMediaControllerPlaybackState().getExtras() != null) {
                packageName = mMediaSessionHelper.getMediaControllerPlaybackState().getExtras().getString("package");
            }
            
            if (packageName != null) {
                loadIconInBackground(packageName, drawable -> {
                    if (mCompactIconView != null && drawable != null) {
                        mCompactIconView.setImageDrawable(drawable);
                    } else if (mCompactIconView != null) {
                        mCompactIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_default_music_icon));
                    }
                });
            } else if (mCompactIconView != null) {
                mCompactIconView.setImageDrawable(mContext.getResources().getDrawable(R.drawable.ic_default_music_icon));
            }
        }
    }

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

        if (mTrackedPackageName != null) {
            loadIconInBackground(mTrackedPackageName, drawable -> {
                if (mIconView != null && drawable != null) {
                    mIconView.setImageDrawable(drawable);
                }
            });
        }
    }
    
    private void updateNotificationProgressCompact() {
        if (!mIsViewAttached) return;
        
        if (!mIsEnabled || !mIsTrackingProgress) {
            mCompactRootView.setVisibility(View.GONE);
            mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
            return;
        }

        mCompactRootView.setVisibility(View.VISIBLE);
        if (mCurrentProgressMax <= 0) {
            Log.w(TAG, "updateViews: invalid max progress " + mCurrentProgressMax + ", using 100");
            mCurrentProgressMax = 100;
        }

        if (mCircularProgressBar != null) {
            mCircularProgressBar.setMax(mCurrentProgressMax);
            mCircularProgressBar.setProgress(mCurrentProgress);
        }

        if (mTrackedPackageName != null) {
            loadIconInBackground(mTrackedPackageName, drawable -> {
                if (mCompactIconView != null && drawable != null) {
                    mCompactIconView.setImageDrawable(drawable);
                }
            });
        }
    }

    private void loadIconInBackground(String packageName, IconCallback callback) {
        if (packageName == null) return;
        
        if (mIconCache.containsKey(packageName)) {
            IconFetcher.AdaptiveDrawableResult cachedResult = mIconCache.get(packageName);
            if (cachedResult != null && cachedResult.drawable != null) {
                callback.onIconLoaded(cachedResult.drawable);
                return;
            }
        }
        
        mBackgroundExecutor.execute(() -> {
            final IconFetcher.AdaptiveDrawableResult iconResult = 
                    mIconFetcher.getMonotonicPackageIcon(packageName);
            
            if (iconResult != null && iconResult.drawable != null) {
                mIconCache.put(packageName, iconResult);
                
                mHandler.post(() -> {
                    callback.onIconLoaded(iconResult.drawable);
                });
            }
        });
    }
    
    private interface IconCallback {
        void onIconLoaded(@Nullable Drawable drawable);
    }

    private void extractProgress(Notification notification) {
        Bundle extras = notification.extras;
        mCurrentProgressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 100);
        mCurrentProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0);
    }

    private void trackProgress(final StatusBarNotification sbn) {
        mIsTrackingProgress = true;
        mTrackedNotificationKey = sbn.getKey();
        mTrackedPackageName = sbn.getPackageName();
        extractProgress(sbn.getNotification());
        requestUiUpdate();
    }

    private void updateProgressIfNeeded(final StatusBarNotification sbn) {
        if (!mIsTrackingProgress) {
            return;
        }
        if (sbn.getKey().equals(mTrackedNotificationKey)) {
            extractProgress(sbn.getNotification());
            requestUiUpdate();
        }
    }

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

    private static boolean hasProgress(@NonNull final Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return false;
        
        boolean indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false);
        boolean maxProgressValid = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;
        return extras.containsKey(Notification.EXTRA_PROGRESS) &&
               extras.containsKey(Notification.EXTRA_PROGRESS_MAX) &&
               !indeterminate && maxProgressValid;
    }

    private void showMediaPopup(View anchorView) {
        if (mIsPopupActive) {
            if (mMediaPopup != null) {
                mMediaPopup.dismiss();
            }
            mIsPopupActive = false;
            return;
        }

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

    private void onNotificationPosted(final StatusBarNotification sbn) {
        if (sbn == null) return;
        
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        
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
                    mHandler.post(() -> trackProgress(sbn));
                } else {
                    mHandler.post(() -> updateProgressIfNeeded(sbn));
                }
            }
        });
    }

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

    public void setForceHidden(final boolean forceHidden) {
        if (mIsForceHidden != forceHidden) {
            Log.d(TAG, "setForceHidden " + forceHidden);
            mIsForceHidden = forceHidden;
            requestUiUpdate();
        }
    }

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
    }
    
    @Override
    public void onNotificationsInitialized() {
    }

    @Override
    public void onKeyguardShowingChanged() {
        setForceHidden(mKeyguardStateController.isShowing());
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) { super(handler); }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(ONGOING_ACTION_CHIP_ENABLED)) ||
                    uri.equals(Settings.System.getUriFor(SHOW_MEDIA_PROGRESS)) ||
                    uri.equals(Settings.System.getUriFor(PROGRESS_BAR_OPACITY)) ||
                    uri.equals(Settings.System.getUriFor(COMPACT_MODE_ENABLED))) {
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
            mContentResolver.registerContentObserver(Settings.System.getUriFor(COMPACT_MODE_ENABLED), 
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        public void unregister() { 
            mContentResolver.unregisterContentObserver(this); 
        }
    }

    private void updateSettings() {
        boolean wasEnabled = mIsEnabled;
        boolean wasShowingMedia = mShowMediaProgress;
        boolean wasCompactMode = mIsCompactModeEnabled;
        
        mIsEnabled = Settings.System.getIntForUser(mContentResolver, 
                ONGOING_ACTION_CHIP_ENABLED, 1, UserHandle.USER_CURRENT) == 1;
        mShowMediaProgress = Settings.System.getIntForUser(mContentResolver, 
                SHOW_MEDIA_PROGRESS, 0, UserHandle.USER_CURRENT) == 1;
        mIsCompactModeEnabled = Settings.System.getIntForUser(mContentResolver, 
                COMPACT_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        
        int opacityPercentage = Settings.System.getIntForUser(mContentResolver, 
                PROGRESS_BAR_OPACITY, DEFAULT_OPACITY_PERCENTAGE, UserHandle.USER_CURRENT);
        
        opacityPercentage = Math.max(0, Math.min(100, opacityPercentage));
        
        mProgressBarOpacity = (int)(opacityPercentage * 2.55f);
        
        if (wasEnabled != mIsEnabled || wasShowingMedia != mShowMediaProgress || wasCompactMode != mIsCompactModeEnabled) {
            mNeedsFullUiUpdate = true;
            mIsExpanded = false;
        }
        
        requestUiUpdate();
    }

    public void destroy() {
        mIsViewAttached = false;
        
        mSettingsObserver.unregister();
        mKeyguardStateController.removeCallback(this);
        mMediaSessionHelper.removeMediaMetadataListener(mMediaMetadataListener);
        
        mMediaProgressHandler.removeCallbacks(mMediaProgressRunnable);
        mHandler.removeCallbacksAndMessages(null);
        
        if (mMediaPopup != null && mMediaPopup.isShowing()) {
            mMediaPopup.dismiss();
        }
        
        mIsTrackingProgress = false;
        mTrackedNotificationKey = null;
        mTrackedPackageName = null;
        
        mIconCache.clear();
        
        if (mIconView != null) {
            mIconView.setImageDrawable(null);
        }
        
        if (mCompactIconView != null) {
            mCompactIconView.setImageDrawable(null);
        }
    }

    private static int getThemeColor(Context context, int attrResId) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrResId, typedValue, true);
        return typedValue.data;
    }
}
