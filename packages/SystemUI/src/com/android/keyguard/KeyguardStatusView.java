/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import java.lang.Math;
import android.graphics.Typeface;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.ViewClippingUtil;
import com.android.keyguard.clocks.CustomAnalogClock;
import com.android.keyguard.clocks.TypographicClock;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import com.google.android.collect.Sets;

import java.util.Locale;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener, View.OnLayoutChangeListener {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;
    private static final String FONT_FAMILY_LIGHT = "sans-serif-light";
    private static final String FONT_FAMILY_MEDIUM = "sans-serif-medium";

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;
    private final float mSmallClockScale;

    private TextView mLogoutView;
    private CustomAnalogClock mCustomClockView;
    private CustomAnalogClock mCustomNumClockView;
    private CustomAnalogClock mDuClockView;
    private CustomAnalogClock mSpideyClockView;
    private CustomAnalogClock mSpectrumClockView;
    private CustomAnalogClock mSneekyClockView;
    private CustomAnalogClock mDotClockView;
    private TypographicClock mTextClock;
    private TextClock mClockView;
    private View mClockSeparator;
    private TextView mOwnerInfo;
    private KeyguardSliceView mKeyguardSlice;
    private View mKeyguardSliceView;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private ArraySet<View> mVisibleInDoze;
    private boolean mPulsing;
    private boolean mWasPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private float mWidgetPadding;
    private int mLastLayoutHeight;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;
    private boolean mOmniStyle;

    private boolean mShowClock;
    private boolean mShowInfo;
    private int mClockSelection;
    private int mDateSelection;

    // Date styles paddings
    private int mDateVerPadding;
    private int mDateHorPadding;

    private boolean mWasLatestViewSmall;

    private boolean mForcedMediaDoze;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
            refreshLockFont();
            updateDateStyles();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateSettings();
                refreshLockFont();
            }
        }

        @Override
        public void onStartedWakingUp() {
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refreshFormat();
            updateOwnerInfo();
            updateLogoutView();
            updateSettings();
            refreshLockFont();
        }

        @Override
        public void onLogoutEnabledChanged() {
            updateLogoutView();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mIActivityManager = ActivityManager.getService();
        mLockPatternUtils = new LockPatternUtils(getContext());
        mHandler = new Handler(Looper.myLooper());
        mSmallClockScale = getResources().getDimension(R.dimen.widget_small_font_size)
                / getResources().getDimension(R.dimen.widget_big_font_size);
        onDensityOrFontScaleChanged();
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, "Schedule setEnableMarquee: " + (enabled ? "Enable" : "Disable"));
        if (enabled) {
            if (mPendingMarqueeStart == null) {
                mPendingMarqueeStart = () -> {
                    setEnableMarqueeImpl(true);
                    mPendingMarqueeStart = null;
                };
                mHandler.postDelayed(mPendingMarqueeStart, MARQUEE_DELAY_MS);
            }
        } else {
            if (mPendingMarqueeStart != null) {
                mHandler.removeCallbacks(mPendingMarqueeStart);
                mPendingMarqueeStart = null;
            }
            setEnableMarqueeImpl(false);
        }
    }

    private void setEnableMarqueeImpl(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLogoutView = findViewById(R.id.logout);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.clock_view);
        mClockView.setShowCurrentUserTime(true);
        if (KeyguardClockAccessibilityDelegate.isNeeded(mContext)) {
            mClockView.setAccessibilityDelegate(new KeyguardClockAccessibilityDelegate(mContext));
        }
        mCustomClockView = findViewById(R.id.custom_clock_view);
        mCustomNumClockView = findViewById(R.id.custom_num_clock_view);
        mTextClock = findViewById(R.id.custom_textclock_view);
        mDuClockView = findViewById(R.id.du_clock_view);
        mSpideyClockView = findViewById(R.id.spidey_clock_view);
        mSpectrumClockView = findViewById(R.id.spectrum_clock_view);
        mSneekyClockView = findViewById(R.id.sneeky_clock_view);
	    mDotClockView = findViewById(R.id.dot_clock_view);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);
        mClockSeparator = findViewById(R.id.clock_separator);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);
        updateSettings();
        mVisibleInDoze = Sets.newArraySet();
        if (mWeatherView != null) {
            if (mShowWeather && mOmniStyle) mVisibleInDoze.add(mWeatherView);
        }
        if (mClockView != null) {
            mVisibleInDoze.add(mClockView);
        }
        if (mKeyguardSlice != null) {
            if (mShowWeather && !mOmniStyle) mVisibleInDoze.add(mKeyguardSlice);
        }
        if (mCustomClockView != null) {
            mVisibleInDoze.add(mCustomClockView);
        }
        if (mDuClockView != null) {
            mVisibleInDoze.add(mDuClockView);
        }
        if (mSpideyClockView != null) {
            mVisibleInDoze.add(mSpideyClockView);
        }
		if (mCustomNumClockView != null) {
            mVisibleInDoze.add(mCustomNumClockView);
        }
		if (mDotClockView != null) {
            mVisibleInDoze.add(mDotClockView);
        }
		if (mSpectrumClockView != null) {
            mVisibleInDoze.add(mSpectrumClockView);
        }

        mTextColor = mClockView.getCurrentTextColor();

        int clockStroke = getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke);
        mClockView.getPaint().setStrokeWidth(clockStroke);
        mClockView.addOnLayoutChangeListener(this);
        mClockSeparator.addOnLayoutChangeListener(this);
        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateSettings();
        refreshLockFont();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);

    }

    /**
     * Moves clock and separator, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        boolean smallClock = mKeyguardSlice.hasHeader() || mPulsing;
        prepareSmallView(smallClock);
        float clockScale = smallClock ? mSmallClockScale : 1;

        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) mClockView.getLayoutParams();
        int height = mClockView.getHeight();
        layoutParams.bottomMargin = (int) -(height - (clockScale * height));
        mClockView.setLayoutParams(layoutParams);

        // Custom analog clock
        RelativeLayout.LayoutParams customlayoutParams =
                (RelativeLayout.LayoutParams) mCustomClockView.getLayoutParams();
        customlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mCustomClockView.setLayoutParams(customlayoutParams);

        // Du analog clock
        RelativeLayout.LayoutParams dulayoutParams =
                (RelativeLayout.LayoutParams) mDuClockView.getLayoutParams();
        dulayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mDuClockView.setLayoutParams(dulayoutParams);

        // Spidey analog clock
        RelativeLayout.LayoutParams spideylayoutParams =
                (RelativeLayout.LayoutParams) mSpideyClockView.getLayoutParams();
        spideylayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mSpideyClockView.setLayoutParams(spideylayoutParams);

        // Custom analog clock
        RelativeLayout.LayoutParams customnumlayoutParams =
                (RelativeLayout.LayoutParams) mCustomNumClockView.getLayoutParams();
        customnumlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mCustomNumClockView.setLayoutParams(customnumlayoutParams);

        // Dot analog clock
        RelativeLayout.LayoutParams dotlayoutParams =
                (RelativeLayout.LayoutParams) mDotClockView.getLayoutParams();
        dotlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mDotClockView.setLayoutParams(dotlayoutParams);

        // Spectrum analog clock
        RelativeLayout.LayoutParams spectrumlayoutParams =
                (RelativeLayout.LayoutParams) mSpectrumClockView.getLayoutParams();
        spectrumlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mSpectrumClockView.setLayoutParams(spectrumlayoutParams);

        // Sneekyanalog clock
        RelativeLayout.LayoutParams sneekylayoutParams =
                (RelativeLayout.LayoutParams) mSneekyClockView.getLayoutParams();
        sneekylayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mSneekyClockView.setLayoutParams(sneekylayoutParams);

        //Custom Text clock
        RelativeLayout.LayoutParams textlayoutParams =
                (RelativeLayout.LayoutParams) mTextClock.getLayoutParams();
        textlayoutParams.bottomMargin = getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_view_top_margin);
        mTextClock.setLayoutParams(textlayoutParams);

        layoutParams = (RelativeLayout.LayoutParams) mClockSeparator.getLayoutParams();
        layoutParams.topMargin = smallClock ? (int) mWidgetPadding : 0;
        layoutParams.bottomMargin = layoutParams.topMargin;
        mClockSeparator.setLayoutParams(layoutParams);
    }

    /**
     * Animate clock and its separator when necessary.
     */
    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int heightOffset = mPulsing || mWasPulsing ? 0 : getHeight() - mLastLayoutHeight;
        boolean hasHeader = mKeyguardSlice.hasHeader();
        boolean smallClock = hasHeader || mPulsing;
        prepareSmallView(smallClock);
        long duration = KeyguardSliceView.DEFAULT_ANIM_DURATION;
        long delay = smallClock || mWasPulsing ? 0 : duration / 4;
        mWasPulsing = false;

        boolean shouldAnimate = mKeyguardSlice.getLayoutTransition() != null
                && mKeyguardSlice.getLayoutTransition().isRunning();
        if (view == mClockView) {
            float clockScale = smallClock ? mSmallClockScale : 1;
            Paint.Style style = smallClock ? Paint.Style.FILL_AND_STROKE : Paint.Style.FILL;
            mClockView.animate().cancel();
            if (shouldAnimate) {
                mClockView.setY(oldTop + heightOffset);
                mClockView.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(new ClipChildrenAnimationListener())
                        .setStartDelay(delay)
                        .y(top)
                        .scaleX(clockScale)
                        .scaleY(clockScale)
                        .withEndAction(() -> {
                            mClockView.getPaint().setStyle(style);
                            mClockView.invalidate();
                        })
                        .start();
            } else {
                mClockView.setY(top);
                mClockView.setScaleX(clockScale);
                mClockView.setScaleY(clockScale);
                mClockView.getPaint().setStyle(style);
                mClockView.invalidate();
            }
        } else if (view == mClockSeparator) {
            boolean hasSeparator = hasHeader && !mPulsing;
            float alpha = hasSeparator ? 1 : 0;
            mClockSeparator.animate().cancel();
            if (shouldAnimate) {
                boolean isAwake = mDarkAmount != 0;
                mClockSeparator.setY(oldTop + heightOffset);
                mClockSeparator.animate()
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .setDuration(duration)
                        .setListener(isAwake ? null : new KeepAwakeAnimationListener(getContext()))
                        .setStartDelay(delay)
                        .y(top)
                        .alpha(alpha)
                        .start();
            } else {
                mClockSeparator.setY(top);
                mClockSeparator.setAlpha(alpha);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mClockView.setPivotX(mClockView.getWidth() / 2);
        mClockView.setPivotY(0);
        mLastLayoutHeight = getHeight();
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mWidgetPadding = getResources().getDimension(R.dimen.widget_vertical_padding);
        Typeface tfLight = Typeface.create(FONT_FAMILY_LIGHT, Typeface.NORMAL);
        Typeface tfMedium = Typeface.create(FONT_FAMILY_MEDIUM, Typeface.NORMAL);
        if (mClockView != null) {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
            mClockView.setTypeface(tfLight);
            mClockView.getPaint().setStrokeWidth(
                    getResources().getDimensionPixelSize(R.dimen.widget_small_font_stroke));
        }
        if (mOwnerInfo != null) {
            mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
            mOwnerInfo.setTypeface(tfMedium);
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
        if (mLogoutView != null) {
            mLogoutView.setTypeface(tfMedium);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.refresh();
        }
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
        refreshLockFont();
        updateDateStyles();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 0 || mWasLatestViewSmall) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 1) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>:mm"));
        } else if (mClockSelection == 5) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        } else if (mClockSelection == 8) {
	        mClockView.setFormat12Hour(Html.fromHtml("<strong>h:mm</strong>"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk:mm</strong>"));
        } else if (mClockSelection == 9) {
            mClockView.setFormat12Hour(Html.fromHtml("hh<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 10) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color='#454545'>hh</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color='#454545'>kk</font><br><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">mm</font>"));
        } else if (mClockSelection == 11) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>h</strong>:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>kk</strong>:m</font>"));
        } else if (mClockSelection == 12) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>h</strong></font>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.sammy_minutes_accent) + "><strong>kk</strong></font>:mm"));
        } else if (mClockSelection == 13) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><font color=" + getResources().getColor(R.color.sammy_minutes_accent) + ">:mm</font>"));
        } else if (mClockSelection == 17) {
            mTextClock.onTimeChanged();
        } else {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        }
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 0);
    }

    private void refreshFormat() {
        Patterns.update(mContext);
        mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    public int getLogoutButtonHeight() {
        if (mLogoutView == null) {
            return 0;
        }
        return mLogoutView.getVisibility() == VISIBLE ? mLogoutView.getHeight() : 0;
    }

    public float getClockTextSize() {
        return mClockView.getTextSize();
    }

    private void updateLogoutView() {
        if (mLogoutView == null) {
            return;
        }
        mLogoutView.setVisibility(shouldShowLogout() ? VISIBLE : GONE);
        // Logout button will stay in language of user 0 if we don't set that manually.
        mLogoutView.setText(mContext.getResources().getString(
                com.android.internal.R.string.global_action_logout));
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String info = mLockPatternUtils.getDeviceOwnerInfo();
        if (info == null) {

            // If text style clock, align the textView to start else keep it center.
            if (mClockSelection == 17) {
                mOwnerInfo.setPaddingRelative((int) mContext.getResources()
                    .getDimension(R.dimen.custom_clock_left_padding) + 8, 0, 0, 0);
                mOwnerInfo.setGravity(Gravity.START);
            } else {
                mOwnerInfo.setPaddingRelative(0, 0, 0, 0);
                mOwnerInfo.setGravity(Gravity.CENTER);
            }
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        Dependency.get(ConfigurationController.class).removeCallback(this);
    }

    @Override
    public void onLocaleListChanged() {
        refreshFormat();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 0;

        if (lockClockFont == 0) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 2) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 3) {
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 5) {
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 6) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 7) {
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 8) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 11) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 12) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockClockFont == 14) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 16) {
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
    }


    private void updateVisibilities() {
        switch (mClockSelection) {
            case 0: // default digital
            default:
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_normal_clock_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 1: // digital (bold)
			case 8: // digital (small)
            case 11: // digital (accent full)
			case 12: // digital (accent hour)
			case 13: // digital (accent minutes)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_normal_clock_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 2: // custom analog
                mCustomClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_normal_clock_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 3: // du analog
                mDuClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 4: // sammy
            case 5: // sammy (bold)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 6: // spidey analog
                mSpideyClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 7: // custom analog with numbers
                mCustomNumClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                break;
            case 9: // sammy accent
            case 10: // sammy accent (alt)
                mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mCustomClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 14: // dot analog
                mDotClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 15: // spectrum analog
                mSpectrumClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 16: // sneeky analog
                mSneekyClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mTextClock.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
            case 17: // custom text clock
                mTextClock.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                       View.GONE) : View.VISIBLE);
				mKeyguardSlice.setVisibility(mShowInfo ? View.VISIBLE : View.GONE);
                mClockView.setVisibility(View.GONE);
                mDuClockView.setVisibility(View.GONE);
                mSpideyClockView.setVisibility(View.GONE);
                mCustomNumClockView.setVisibility(View.GONE);
                mDotClockView.setVisibility(View.GONE);
                mSpectrumClockView.setVisibility(View.GONE);
                mSneekyClockView.setVisibility(View.GONE);
                mKeyguardSlice.setPadding(0,(int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_shishu_date_padding), 
                getResources().getDisplayMetrics()),0,0);
                break;
        }
    }

    private void updateDateStyles() {
        switch (mDateSelection) {
            case 0: // default
            default:
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackgroundResource(0);
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT);
                mDateVerPadding = 0;
                mDateHorPadding = 0;
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 1: // semi-transparent box
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT_BOLD);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 2: // semi-transparent box (round)
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                mKeyguardSlice.setViewsTypeface(Typeface.DEFAULT_BOLD);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 3: // Q-Now Playing background
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.05f, false);
                break;
            case 4: // accent box
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 5: // accent box but just the day
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 6: // accent box transparent
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 7: // accent box transparent but just the day
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 8: // gradient box
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
            case 9: // Dark Accent border
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 10: // Dark Gradient border
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 11: // white outline
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_border_outline));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 12: // white outline (round)
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_border_outline));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 13: // black outline
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_border_outline_black));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 14: // // black outline (round)
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_border_outline_black));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 15: // accent outline
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_border_outline_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
            case 16: // accent outline (round)
                mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mShowInfo ? View.VISIBLE : View.GONE) : View.VISIBLE);
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_border_outline_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.08f, true);
                break;
           case 17: // gradient box with transparent end
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_half_gradient));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
           case 18: // Accent box with transparent end
                mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_half_accent));
                mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                mKeyguardSlice.setViewsTextStyles(0.15f, true);
                break;
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
	final Resources res = getContext().getResources();

        mShowWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        mOmniStyle = Settings.System.getIntForUser(resolver,
                Settings.System.AICP_LOCKSCREEN_WEATHER_STYLE, 0,
                UserHandle.USER_CURRENT) == 0;

        if (mWeatherView != null) {
            if (mShowWeather && mOmniStyle) {
                mWeatherView.setVisibility(View.VISIBLE);
                mWeatherView.enableUpdates();
            }
            if (!mShowWeather || !mOmniStyle) {
                mWeatherView.setVisibility(View.GONE);
                mWeatherView.disableUpdates();
            }
        }

        // Set smaller Clock, Date and OwnerInfo text size if the user selects the small clock type
	if (mClockSelection == 8) {
	    mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_clock_small_font_size));
	} else {
	    mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
	}

        mShowClock = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK, 1, UserHandle.USER_CURRENT) == 1;
        mShowInfo = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_INFO, 1, UserHandle.USER_CURRENT) == 1;
        mClockSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_CLOCK_SELECTION, 0, UserHandle.USER_CURRENT);
        mDateSelection = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_DATE_SELECTION, 0, UserHandle.USER_CURRENT);

        setStyle();
        updateDateStyles();
    }

    private void setStyle() {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mKeyguardSlice.getLayoutParams();
        switch (mClockSelection) {
            case 0: // default digital
            default:
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 1: // digital (bold)
			case 8: // digital small
			case 11: // digital (accent full)
            case 12: // digital (accent hour)
			case 13: // digital (accent minutes)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(true);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 2: // custom analog
                params.addRule(RelativeLayout.BELOW, R.id.custom_clock_view);
                break;
            case 3: // du analog
                params.addRule(RelativeLayout.BELOW, R.id.du_clock_view);
                break;
            case 4: // sammy
            case 5: // sammy (bold)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 6: // spidey analog
                params.addRule(RelativeLayout.BELOW, R.id.spidey_clock_view);
                break;
            case 7: // custom analog with numbers
                params.addRule(RelativeLayout.BELOW, R.id.custom_num_clock_view);
                break;
            case 9: // sammy accent
            case 10: // sammy accent (alt)
                params.addRule(RelativeLayout.BELOW, R.id.clock_view);
                mClockView.setSingleLine(false);
                mClockView.setGravity(Gravity.CENTER);
                break;
            case 14: // dot analog
                params.addRule(RelativeLayout.BELOW, R.id.dot_clock_view);
                break;
            case 15: // spectrum analog
                params.addRule(RelativeLayout.BELOW, R.id.spectrum_clock_view);
                break;
            case 16: // sneeky analog
                params.addRule(RelativeLayout.BELOW, R.id.sneeky_clock_view);
                break;
            case 17: // custom text clock
                params.addRule(RelativeLayout.BELOW, R.id.custom_textclock_view);
                break;
        }

        updateVisibilities();
        updateDozeVisibleViews();
    }

    private void prepareSmallView(boolean small) {
        if (mWasLatestViewSmall == small) return;
        mWasLatestViewSmall = small;
        if (small) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    mKeyguardSlice.getLayoutParams();
            params.addRule(RelativeLayout.BELOW, R.id.clock_view);
            mClockView.setSingleLine(true);
            mClockView.setGravity(Gravity.CENTER);
            mClockView.setVisibility(mDarkAmount != 1 ? (mShowClock ? View.VISIBLE :
                    View.GONE) : View.VISIBLE);
            mCustomClockView.setVisibility(View.GONE);
            mDuClockView.setVisibility(View.GONE);
            mSpideyClockView.setVisibility(View.GONE);
            mCustomNumClockView.setVisibility(View.GONE);
            mDotClockView.setVisibility(View.GONE);
            mSpectrumClockView.setVisibility(View.GONE);
            mSneekyClockView.setVisibility(View.GONE);
            mTextClock.setVisibility(View.GONE);
            mKeyguardSlice.setViewBackgroundResource(0);
        } else {
            setStyle();
			updateDateStyles();
            refreshTime();
        }
    }

    public void updateAll() {
        updateSettings();
        mKeyguardSlice.updateSettings();
        mKeyguardSlice.refresh();
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        updateDark();
    }

    private void updateDark() {
        boolean dark = mDarkAmount == 1;
        if (mLogoutView != null) {
            mLogoutView.setAlpha(dark ? 0 : 1);
        }

        if (mOwnerInfo != null) {
            boolean hasText = !TextUtils.isEmpty(mOwnerInfo.getText());
            mOwnerInfo.setVisibility(hasText ? VISIBLE : GONE);
            layoutOwnerInfo();
        }

        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        updateDozeVisibleViews();
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        mClockSeparator.setBackgroundColor(blendedTextColor);
        mCustomClockView.setDark(dark);
        mDuClockView.setDark(dark);
        mSpideyClockView.setDark(dark);
        mCustomNumClockView.setDark(dark);
        if (mClockSelection == 17) {
            mTextClock.setTextColor(blendedTextColor);
        }
        updateVisibilities();
    }

    private void layoutOwnerInfo() {
        if (mOwnerInfo != null && mOwnerInfo.getVisibility() != GONE) {
            // Animate owner info during wake-up transition
            mOwnerInfo.setAlpha(1f - mDarkAmount);

            float ratio = mDarkAmount;
            // Calculate how much of it we should crop in order to have a smooth transition
            int collapsed = mOwnerInfo.getTop() - mOwnerInfo.getPaddingTop();
            int expanded = mOwnerInfo.getBottom() + mOwnerInfo.getPaddingBottom();
            int toRemove = (int) ((expanded - collapsed) * ratio);
            setBottom(getMeasuredHeight() - toRemove);
        }
    }

    public void setPulsing(boolean pulsing, boolean animate) {
        if (mPulsing == pulsing) {
            return;
        }
        if (mPulsing) {
            mWasPulsing = true;
        }
        mPulsing = pulsing;
        // Animation can look really weird when the slice has a header, let's hide the views
        // immediately instead of fading them away.
        if (mKeyguardSlice.hasHeader()) {
            animate = false;
        }
        mKeyguardSlice.setPulsing(pulsing, animate);
        if (mWeatherView != null) {
            mWeatherView.setVisibility((mShowWeather && mOmniStyle && !mPulsing) ? View.VISIBLE : View.GONE);
        }
        updateDozeVisibleViews();
    }

    public void setCleanLayout(int reason) {
        mForcedMediaDoze =
                reason == DozeLog.PULSE_REASON_FORCED_MEDIA_NOTIFICATION;
        updateDozeVisibleViews();
    }

    private void updateDozeVisibleViews() {
        for (View child : mVisibleInDoze) {
            if (!mForcedMediaDoze) {
                child.setAlpha(mDarkAmount == 1 && mPulsing ? 0.8f : 1);
            } else {
                child.setAlpha(mDarkAmount == 1 ? 0 : 1);
            }
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.setVisibility(mForcedMediaDoze ? View.GONE : View.VISIBLE);
        }
        if (mWeatherView != null) {
            mWeatherView.setVisibility(mForcedMediaDoze ? View.GONE : (mShowWeather && mOmniStyle) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean shouldShowLogout() {
        return KeyguardUpdateMonitor.getInstance(mContext).isLogoutEnabled()
                && KeyguardUpdateMonitor.getCurrentUser() != UserHandle.USER_SYSTEM;
    }

    private void onLogoutClicked(View view) {
        int currentUserId = KeyguardUpdateMonitor.getCurrentUser();
        try {
            mIActivityManager.switchUser(UserHandle.USER_SYSTEM);
            mIActivityManager.stopUser(currentUserId, true /*force*/, null);
        } catch (RemoteException re) {
            Log.e(TAG, "Failed to logout user", re);
        }
    }

    private class ClipChildrenAnimationListener extends AnimatorListenerAdapter implements
            ViewClippingUtil.ClippingParameters {

        ClipChildrenAnimationListener() {
            ViewClippingUtil.setClippingDeactivated(mClockView, true /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            ViewClippingUtil.setClippingDeactivated(mClockView, false /* deactivated */,
                    this /* clippingParams */);
        }

        @Override
        public boolean shouldFinish(View view) {
            return view == getParent();
        }
    }
}
