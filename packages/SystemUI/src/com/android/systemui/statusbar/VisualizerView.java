/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.systemui.statusbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.UiOffloadThread;

public class VisualizerView extends View
        implements Palette.PaletteAsyncListener {

    private static final String TAG = VisualizerView.class.getSimpleName();
    private static final boolean DEBUG = false;

    private Paint mPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private SettingsObserver mSettingObserver;
    private Context mContext;

    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;

    private int mStatusBarState;
    private boolean mVisualizerEnabled = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private boolean mDisplaying = false; // the state we're animating to
    private boolean mDozing = false;
    private boolean mOccluded = false;
    private boolean mAmbientVisualizerEnabled = false;

    private boolean mUseCustomColor;
    private int mColorToUse;
    private int mDefaultColor;
    private int mCustomColor;
    private Bitmap mCurrentBitmap;

    private final UiOffloadThread mUiOffloadThread;

    private Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
        byte rfk, ifk;
        int dbValue;
        float magnitude;

        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            for (int i = 0; i < 32; i++) {
                mValueAnimators[i].cancel();
                rfk = fft[i * 2 + 2];
                ifk = fft[i * 2 + 3];
                magnitude = rfk * rfk + ifk * ifk;
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * 16f));
                mValueAnimators[i].start();
            }
        }
    };

    public void dolink() {
        mUiOffloadThread.submit(() -> {
            if (mVisualizer != null) return;
            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                Log.e(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener,Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);
        });
    }

    private void unlink() {
        mUiOffloadThread.submit(() -> {
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
        });
    }

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;

        mDefaultColor = Color.TRANSPARENT;
        updateColorSettings();
        mColorToUse = mUseCustomColor ? mCustomColor : mDefaultColor;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mColorToUse);

        mFFTPoints = new float[128];
        mValueAnimators = new ValueAnimator[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }

        mUiOffloadThread = Dependency.get(UiOffloadThread.class);
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    private void updateViewVisibility() {
        final int curVis = getVisibility();
        final int newVis = mVisible && mStatusBarState != StatusBarState.SHADE
                && mVisualizerEnabled ? View.VISIBLE : View.GONE;
        if (curVis != newVis) {
            setVisibility(newVis);
            checkStateChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mCurrentBitmap == null)
            setColor(Color.TRANSPARENT);
        mSettingObserver = new SettingsObserver(new Handler());
        mSettingObserver.observe();
        mSettingObserver.update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingObserver.unobserve();
        mSettingObserver = null;
        mCurrentBitmap = null;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    private void setVisualizerEnabled() {
        mVisualizerEnabled = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED, 0) == 1;
        mAmbientVisualizerEnabled = Settings.Secure.getIntForUser(
                getContext().getContentResolver(), Settings.Secure.AMBIENT_VISUALIZER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    public void setVisible(boolean visible) {
        if (DEBUG) {
            Log.i(TAG, "setVisible() called with visible = [" + visible + "]");
        }
        mVisible = visible;
        updateViewVisibility();
    }

    public void setDozing(boolean dozing) {
        if (mDozing != dozing) {
            if (DEBUG) {
                Log.i(TAG, "setDozing() called with dozing = [" + dozing + "]");
            }
            mDozing = dozing;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            if (DEBUG) {
                Log.i(TAG, "setPlaying() called with playing = [" + playing + "]");
            }
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            if (DEBUG) {
                Log.i(TAG, "setPowerSaveMode() called with powerSaveMode = [" + powerSaveMode + "]");
            }
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setOccluded(boolean occluded) {
        if (mOccluded != occluded) {
            if (DEBUG) {
                Log.i(TAG, "setOccluded() called with occluded = [" + occluded + "]");
            }
            mOccluded = occluded;
            checkStateChanged();
        }
    }

    public void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updateViewVisibility();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mCurrentBitmap == bitmap) {
            return;
        }
        mCurrentBitmap = bitmap;
        if (bitmap != null) {
            Palette.generateAsync(bitmap, this);
        } else {
            mDefaultColor = Color.TRANSPARENT;
            if (!mUseCustomColor) {
                setColor(mDefaultColor);
            }
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = Color.TRANSPARENT;

        color = palette.getVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }
        mDefaultColor = color;
        if (!mUseCustomColor) {
            setColor(mDefaultColor);
        }
    }

    private void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = (140 << 24) | (color & 0x00ffffff);

        if (mColorToUse != color) {
            mColorToUse = color;

            if (mVisualizer != null) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColorToUse);
                mVisualizerColorAnimator.setStartDelay(600);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColorToUse);
            }
        }
    }

    private void checkStateChanged() {
        setColor(mUseCustomColor ? mCustomColor : mDefaultColor);
        if (getVisibility() == View.VISIBLE && mVisible && mPlaying && mDozing && mAmbientVisualizerEnabled && !mPowerSaveMode
                 && mVisualizerEnabled && !mOccluded) {
            if (!mUseCustomColor) setColor(Color.WHITE);
            if (!mDisplaying) {
                mDisplaying = true;
                dolink();
                animate()
                        .alpha(0.70f)
                        .setDuration(800);
            } else {
                animate()
                        .alpha(0.70f)
                        .withEndAction(null)
                        .setDuration(800);
            }
        } else if (getVisibility() == View.VISIBLE && mVisible && mPlaying && !mDozing && !mPowerSaveMode
                && mVisualizerEnabled && !mOccluded) {
            if (!mDisplaying) {
                mDisplaying = true;
                dolink();
                animate()
                        .alpha(1f)
                        .setDuration(800);
            } else {
                animate()
                        .alpha(1f)
                        .withEndAction(null)
                        .setDuration(800);
            }
        } else {
            if (mDisplaying) {
                unlink();
                mDisplaying = false;
                if (mVisible && !mAmbientVisualizerEnabled) {
                    animate()
                            .alpha(0f)
                            .setDuration(600);
                } else {
                    animate().
                            alpha(0f)
                            .setDuration(0);
                }
            }
        }
    }

    private void updateColorSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mUseCustomColor = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_VISUALIZER_USE_CUSTOM_COLOR, 0) == 1;
        final int color = Settings.System.getInt(resolver,
                Settings.System.LOCK_SCREEN_VISUALIZER_CUSTOM_COLOR, 0xff1976D2);

        // make sure custom color always has the right transparency
        mCustomColor = (140 << 24) | (color & 0x00ffffff);
    }

    private class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        protected void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_VISUALIZER_USE_CUSTOM_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_VISUALIZER_CUSTOM_COLOR),
                    false, this);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.AMBIENT_VISUALIZER_ENABLED),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        protected void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            ContentResolver resolver = mContext.getContentResolver();
            if (uri.equals(Settings.Secure.getUriFor(
                    Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED)) 
                || uri.equals(Settings.Secure.getUriFor( 
                    Settings.Secure.AMBIENT_VISUALIZER_ENABLED))) {
                setVisualizerEnabled();
                checkStateChanged();
                updateViewVisibility();
            } else if (uri.equals(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_VISUALIZER_USE_CUSTOM_COLOR))
                || uri.equals(Settings.System.getUriFor(
                    Settings.System.LOCK_SCREEN_VISUALIZER_CUSTOM_COLOR))) {
                updateColorSettings();
                setColor(mUseCustomColor ? mCustomColor : mDefaultColor);
            }
        }

        protected void update() {
            setVisualizerEnabled();
            updateColorSettings();
            checkStateChanged();
            updateViewVisibility();
        }
    }
}
