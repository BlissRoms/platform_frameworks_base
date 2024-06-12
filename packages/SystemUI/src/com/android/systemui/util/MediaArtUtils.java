/*
 * Copyright (C) 2023-2024 The risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.util;

import static com.android.systemui.statusbar.StatusBarState.KEYGUARD;

import android.annotation.NonNull;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.internal.graphics.ColorUtils;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.qs.QSImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaArtUtils {

    private static final String LS_MEDIA_ART_ENABLED = "ls_media_art_enabled";
    private static final String LS_MEDIA_ART_FILTER = "ls_media_art_filter";
    private static final String LS_MEDIA_ART_FADE_LEVEL = "ls_media_art_fade_level";

    private static final int GRADIENT_BLUR_LEVEL = 1000;

    private static MediaArtUtils instance;
    private FrameLayout mLsMediaScrim;
    private Drawable mDimmingOverlay;

    private final Context mContext;
    private final KeyguardStateController mKeyguardStateController;
    private final StatusBarStateController mStatusBarStateController;

    private boolean mLsMediaEnabled;
    private boolean mDozing;
    private boolean mPulsing;
    private boolean mAlbumArtShowing = false;
    
    private MediaMetadata mMediaMetadata = null;
    private LayerDrawable currLayeredDrawable = null;
    private MediaController mController;
    private int mLsMediaFilter = 1;
    private int mLsMediaFadeLevel = 40;
    private int mPreviousLsMediaFadeLevel = 40;
    private MediaMetadata mPreviousMediaMetadata = null;
    private QSImpl mQSImpl = null;
    
    private ContentObserver mMediaArtObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            updateSettings();
        }
    };

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final MediaController.Callback mMediaCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(@NonNull PlaybackState state) {
            updateMediaArt();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            super.onMetadataChanged(metadata);
            mMediaMetadata = metadata;
            updateMediaArt();
        }
    };

    private final KeyguardStateController.Callback mKeyguardStateCallback =
            new KeyguardStateController.Callback() {
                @Override
                public void onKeyguardFadingAwayChanged() {
                    hideMediaArt();
                }

                @Override
                public void onKeyguardGoingAwayChanged() {
                    hideMediaArt();
                }
            };

    private MediaArtUtils(Context context) {
        mContext = context;
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mKeyguardStateController = Dependency.get(KeyguardStateController.class);
        setUpLockscreenScrim();
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mKeyguardStateController.addCallback(mKeyguardStateCallback);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(LS_MEDIA_ART_ENABLED), 
                false, 
                mMediaArtObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(LS_MEDIA_ART_FILTER), 
                false, 
                mMediaArtObserver);
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(LS_MEDIA_ART_FADE_LEVEL), 
                false, 
                mMediaArtObserver);
        mMediaArtObserver.onChange(true);
        updateMediaController();
    }
    
    private void updateSettings() {
        mLsMediaEnabled = Settings.System.getInt(mContext.getContentResolver(), 
                         LS_MEDIA_ART_ENABLED, 0) == 1;
        mLsMediaFilter = Settings.System.getInt(mContext.getContentResolver(), 
                       LS_MEDIA_ART_FILTER, 1);
        mLsMediaFadeLevel = Settings.System.getInt(mContext.getContentResolver(), 
                           LS_MEDIA_ART_FADE_LEVEL, 40);
        setUpMediaFilter();
        updateMediaArtVisibility();
    }
    
    private void setUpLockscreenScrim() {
        mLsMediaScrim = new FrameLayout(mContext) {
            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                MediaArtUtils.this.onDetachedFromWindow();
            }
        };
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mLsMediaScrim.setLayoutParams(lp);
        setUpMediaFilter();
    }
    
    private void setUpMediaFilter() {
        // based from IOS lockscreen blur effect (based from glassblur ratio: 128px:4000, 68px(hdpi):2125)
        // this should not result to boxing on non-skia rendering devices since we are using MIRROR tile mode shader
        RenderEffect blurGradient = mLsMediaFilter == 1 ? 
            RenderEffect.createBlurEffect(GRADIENT_BLUR_LEVEL, GRADIENT_BLUR_LEVEL, Shader.TileMode.MIRROR)
            : null;
        if (mLsMediaScrim != null) {
            mLsMediaScrim.post(() -> {
                mLsMediaScrim.setRenderEffect(blurGradient);
            });
        }
    }

    public static MediaArtUtils getInstance(Context context) {
        if (instance == null) {
            instance = new MediaArtUtils(context);
        }
        return instance;
    }

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
            @Override
            public void onStateChanged(int newState) {}

            @Override
            public void onDozingChanged(boolean dozing) {
                if (mDozing == dozing) {
                    return;
                }
                mDozing = dozing;
                updateMediaArt();
            }
    };

    private boolean isMediaControllerAvailable() {
        return mController != null && !TextUtils.isEmpty(mController.getPackageName());
    }

    private boolean isMediaPlaying() {
        return isMediaControllerAvailable() && PlaybackState.STATE_PLAYING == getMediaControllerPlaybackState(mController);
    }

    private MediaController getActiveLocalMediaController() {
        MediaSessionManager mediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        MediaController localController = null;
        final List<String> remoteMediaSessionLists = new ArrayList<>();
        for (MediaController controller : mediaSessionManager.getActiveSessions(null)) {
            final MediaController.PlaybackInfo pi = controller.getPlaybackInfo();
            if (pi == null) continue;
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState == null || playbackState.getState() != PlaybackState.STATE_PLAYING) continue;
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                if (localController != null && TextUtils.equals(localController.getPackageName(), controller.getPackageName())) {
                    localController = null;
                }
                if (!remoteMediaSessionLists.contains(controller.getPackageName())) {
                    remoteMediaSessionLists.add(controller.getPackageName());
                }
                continue;
            }
            if (pi.getPlaybackType() == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                if (localController == null && !remoteMediaSessionLists.contains(controller.getPackageName())) {
                    localController = controller;
                }
            }
        }
        return localController;
    }

    private void updateMediaController() {
        MediaController localController = getActiveLocalMediaController();
        if (localController != null && !sameSessions(mController, localController)) {
            if (mController != null) {
                mController.unregisterCallback(mMediaCallback);
                mController = null;
            }
            mController = localController;
            mController.registerCallback(mMediaCallback);
        }
        mMediaMetadata = isMediaControllerAvailable() ? mController.getMetadata() : null;
    }

    public void updateMediaArt() {
        updateMedia();
        if (mLsMediaScrim != null) {
            mLsMediaScrim.postDelayed(() -> {
                updateMedia();
            }, 250);
        }
    }
    
    public void updateMedia() {
        updateMediaController();
        if (isMediaPlaying()) {
            updateMediaArt(mMediaMetadata);
        } else {
            hideMediaArt();
        }
    }

    public FrameLayout getMediaArtScrim() {
        return mLsMediaScrim;
    }
    
    public void setQSImpl(QSImpl qsImpl) {
        mQSImpl = qsImpl;
    }

    private boolean canShowLsMediaArt() {
        return (mLsMediaScrim != null && mLsMediaEnabled
                && mQSImpl != null && mQSImpl.isFullyCollapsed() 
                && mStatusBarStateController.getState() == KEYGUARD
                && mContext.getResources().getConfiguration().orientation 
                    != Configuration.ORIENTATION_LANDSCAPE 
                && isMediaPlaying()) && !mDozing;
    }

    public boolean albumArtVisible() {
        return mAlbumArtShowing;
    }

    public void updateMediaArtVisibility() {
        updateMediaController();
        if (canShowLsMediaArt()) {
            showMediaArt();
        } else {
            hideMediaArt();
        }
    }

    private void showMediaArt() {
        updateMediaController();
        if (mLsMediaScrim == null || mLsMediaScrim.getVisibility() == View.VISIBLE) return;
        mLsMediaScrim.post(() -> {
            mLsMediaScrim.setBackground(currLayeredDrawable);
            mLsMediaScrim.setAlpha(0f);
            mLsMediaScrim.setVisibility(View.VISIBLE);
            mLsMediaScrim.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);
        });
        mAlbumArtShowing = true;
    }

    public void hideMediaArt() {
        updateMediaController();
        if (mLsMediaScrim == null || mLsMediaScrim.getVisibility() == View.GONE) return;
        mLsMediaScrim.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLsMediaScrim.post(() -> {
                        mLsMediaScrim.setVisibility(View.GONE);
                        mLsMediaScrim.setBackground(null);
                        mAlbumArtShowing = false;
                    });
                }
            });
    }

    public Bitmap getResizedBitmap(Bitmap wallpaperBitmap) {
        Rect displayBounds = mContext.getSystemService(WindowManager.class).getCurrentWindowMetrics().getBounds();
        float ratioW = displayBounds.width() / (float) wallpaperBitmap.getWidth();
        float ratioH = displayBounds.height() / (float) wallpaperBitmap.getHeight();
        int desiredHeight = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getHeight());
        int desiredWidth = Math.round(Math.max(ratioH, ratioW) * wallpaperBitmap.getWidth());
        desiredHeight = Math.max(desiredHeight, 0);
        desiredWidth = Math.max(desiredWidth, 0);
        Bitmap scaledWallpaperBitmap = Bitmap.createScaledBitmap(wallpaperBitmap, desiredWidth, desiredHeight, true);
        int xPixelShift = Math.max((desiredWidth - displayBounds.width()) / 2, 0);
        int yPixelShift = Math.max((desiredHeight - displayBounds.height()) / 2, 0);
        int cropWidth = Math.min(displayBounds.width(), scaledWallpaperBitmap.getWidth() - xPixelShift);
        int cropHeight = Math.min(displayBounds.height(), scaledWallpaperBitmap.getHeight() - yPixelShift);
        return Bitmap.createBitmap(scaledWallpaperBitmap, Math.max(xPixelShift, 0), Math.max(yPixelShift, 0), cropWidth, cropHeight);
    }

    public void updateMediaArt(MediaMetadata metadata) {
        if (mLsMediaScrim == null) return;
        updateMediaController();
        if (metadata == null || !mLsMediaEnabled) {
            hideMediaArt();
            return;
        }
        if (metadata != null && metadata.equals(mPreviousMediaMetadata)) {
            mLsMediaScrim.post(() -> {
                updateMediaArtVisibility();
            });
            return;
        }
        Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (bitmap == null) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        if (bitmap != null) {
            final Bitmap albumArt = bitmap;
            mExecutor.execute(() -> {
                Bitmap resizedAlbumArt = getResizedBitmap(albumArt);
                Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), resizedAlbumArt);
                bitmapDrawable.setAlpha(255);
                final int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, Color.BLACK, mLsMediaFadeLevel / 100f);
                Bitmap overlayBitmap = Bitmap.createBitmap(albumArt.getWidth(), albumArt.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(overlayBitmap);
                canvas.drawColor(fadeFilter);
                Drawable overlay = new BitmapDrawable(mContext.getResources(), overlayBitmap);
                Drawable[] layers = new Drawable[2];
                layers[0] = bitmapDrawable;
                layers[1] = overlay;
                LayerDrawable layeredDrawable = new LayerDrawable(layers);
                currLayeredDrawable = layeredDrawable;
                mPreviousLsMediaFadeLevel = mLsMediaFadeLevel;
                mLsMediaScrim.post(() -> {
                    mLsMediaScrim.setBackground(currLayeredDrawable);
                });
            });
            mPreviousMediaMetadata = metadata;
        }
        mLsMediaScrim.post(() -> {
            updateMediaArtVisibility();
        });
    }

    private boolean sameSessions(MediaController a, MediaController b) {
        if (a == b) return true;
        if (a == null) return false;
        return a.controlsSameSession(b);
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller == null)  return PlaybackState.STATE_NONE;
        final PlaybackState playbackState = controller.getPlaybackState();
        if (playbackState != null) return playbackState.getState();
        return PlaybackState.STATE_NONE;
    }
    
    public void onDetachedFromWindow() {
        mStatusBarStateController.removeCallback(mStatusBarStateListener);
        mKeyguardStateController.removeCallback(mKeyguardStateCallback);
        mContext.getContentResolver().unregisterContentObserver(mMediaArtObserver);
        currLayeredDrawable = null;
        mMediaMetadata = null;
        mPreviousMediaMetadata = null;
        mExecutor.shutdown();
        if (mController == null) return;
        mController.unregisterCallback(mMediaCallback);
        mController = null;
    }
}
