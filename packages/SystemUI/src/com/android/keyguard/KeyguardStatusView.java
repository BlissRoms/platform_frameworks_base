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

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.Dependency;
import com.android.systemui.omni.CurrentWeatherView;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tuner.TunerService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.TimeZone;

public class KeyguardStatusView extends GridLayout implements
        ConfigurationController.ConfigurationListener,
        TunerService.Tunable {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";
    private static final int MARQUEE_DELAY_MS = 2000;

    private static final int FONT_NORMAL = 0;
    private static final int FONT_ITALIC = 1;
    private static final int FONT_BOLD = 2;
    private static final int FONT_BOLD_ITALIC = 3;
    private static final int FONT_LIGHT = 4;
    private static final int FONT_LIGHT_ITALIC = 5;
    private static final int FONT_THIN = 6;
    private static final int FONT_THIN_ITALIC = 7;
    private static final int FONT_CONDENSED = 8;
    private static final int FONT_CONDENSED_ITALIC = 9;
    private static final int FONT_CONDENSED_LIGHT = 10;
    private static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    private static final int FONT_CONDENSED_BOLD = 12;
    private static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    private static final int FONT_MEDIUM = 14;
    private static final int FONT_MEDIUM_ITALIC = 15;
    private static final int FONT_BLACK = 16;
    private static final int FONT_BLACK_ITALIC = 17;
    private static final int FONT_DANCINGSCRIPT = 18;
    private static final int FONT_DANCINGSCRIPT_BOLD = 19;
    private static final int FONT_COMINGSOON = 20;
    private static final int FONT_NOTOSERIF = 21;
    private static final int FONT_NOTOSERIF_ITALIC = 22;
    private static final int FONT_NOTOSERIF_BOLD = 23;
    private static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    private static final int FONT_ACLONICA = 25;
    private static final int FONT_AMARANTE = 26;
    private static final int FONT_BARIOL = 27;
    private static final int FONT_CAGLIOSTRO = 28;
    private static final int FONT_COOLSTORY = 29;
    private static final int FONT_LGSMARTGOTHIC = 30;
    private static final int FONT_ROSEMARY = 31;
    private static final int FONT_SONYSKETCH = 32;
    private static final int FONT_SURFER = 33;
    private static final int FONT_COMICSANS = 34;
    private static final int FONT_GOOGLESANS = 35;
    private static final int FONT_ONEPLUSSLATE = 36;
    private static final int FONT_SAMSUNGONE = 37;
    private static final int FONT_COMFORTAA = 38;
    private static final int FONT_EXOTWO = 39;
    private static final int FONT_STOROPIA = 40;
    private static final int FONT_UBUNTU = 41;
    private static final int FONT_NOKIAPURE = 42;
    private static final int FONT_FIFA2018 = 43;
    private static final int FONT_ROADRAGE = 44;
    private static final int FONT_20SEVEN = 45;
    private static final int FONT_COCON = 46;
    private static final int FONT_QUANDO = 47;
    private static final int FONT_GRANDHOTEL = 48;
    private static final int FONT_REDRESSED = 49;
    private static final int FONT_SANFRANSISCO = 50;

    private final LockPatternUtils mLockPatternUtils;
    private final IActivityManager mIActivityManager;

    private LinearLayout mStatusViewContainer;
    private TextView mLogoutView;
    private KeyguardClockSwitch mClockView;
    private View mSmallClockView;
    private TextView mOwnerInfo;
    private TextClock mDefaultClockView;
    private KeyguardSliceView mKeyguardSlice;
    private View mNotificationIcons;
    private Runnable mPendingMarqueeStart;
    private Handler mHandler;

    private boolean mPulsing;
    private float mDarkAmount = 0;
    private int mTextColor;
    private CurrentWeatherView mWeatherView;
    private boolean mShowWeather;
    private boolean mOmniStyle;
    private boolean mLockDateHide;

    /**
     * Bottom margin that defines the margin between bottom of smart space and top of notification
     * icons on AOD.
     */
    private int mIconTopMargin;
    private int mIconTopMarginWithHeader;
    private boolean mShowingHeader;

    private int mClockSelection;
    private int mLockClockFontStyle;
    private int mLockDateFontStyle;
    private int mDateSelection;

    // Date styles paddings
    private int mDateVerPadding;
    private int mDateHorPadding;
    private int mLockClockFontSize;
    private int mLockDateFontSize;
    private int mOwnerInfoSize;
    private int mOwnerInfoFontStyle;

    private static final String LOCK_CLOCK_FONT_STYLE =
            "system:" + Settings.System.LOCK_CLOCK_FONT_STYLE;
    private static final String LOCK_DATE_FONT_STYLE =
            "system:" + Settings.System.LOCK_DATE_FONT_STYLE;
    private static final String LOCKSCREEN_CLOCK_SELECTION =
            "system:" + Settings.System.LOCKSCREEN_CLOCK_SELECTION;
    private static final String LOCKSCREEN_DATE_HIDE =
            "system:" + Settings.System.LOCKSCREEN_DATE_HIDE;
    private static final String LOCKSCREEN_DATE_SELECTION =
            "system:" + Settings.System.LOCKSCREEN_DATE_SELECTION;
    private static final String LOCK_CLOCK_FONT_SIZE =
            "system:" + Settings.System.LOCK_CLOCK_FONT_SIZE;
    private static final String LOCK_DATE_FONT_SIZE =
            "system:" + Settings.System.LOCK_DATE_FONT_SIZE;
    private static final String LOCKOWNER_FONT_SIZE =
            "system:" + Settings.System.LOCKOWNER_FONT_SIZE;
    private static final String LOCK_OWNERINFO_FONTS =
            "system:" + Settings.System.LOCK_OWNERINFO_FONTS;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refreshTime();
        }

        @Override
        public void onTimeZoneChanged(TimeZone timeZone) {
            updateTimeZone(timeZone);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refreshTime();
                updateOwnerInfo();
                updateLogoutView();
                updateSettings();
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
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, LOCK_CLOCK_FONT_STYLE);
        tunerService.addTunable(this, LOCK_DATE_FONT_STYLE);
        tunerService.addTunable(this, LOCKSCREEN_CLOCK_SELECTION);
        tunerService.addTunable(this, LOCKSCREEN_DATE_HIDE);
        tunerService.addTunable(this, LOCKSCREEN_DATE_SELECTION);
        tunerService.addTunable(this, LOCK_CLOCK_FONT_SIZE);
        tunerService.addTunable(this, LOCK_DATE_FONT_SIZE);
        tunerService.addTunable(this, LOCKOWNER_FONT_SIZE);
        tunerService.addTunable(this, LOCK_OWNERINFO_FONTS);
        onDensityOrFontScaleChanged();
    }

    /**
     * If we're presenting a custom clock of just the default one.
     */
    public boolean hasCustomClock() {
        return mClockView.hasCustomClock();
    }

    public boolean hasCustomClockInBigContainer() {
        return mClockView.hasCustomClockInBigContainer();
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    public void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        mClockView.setHasVisibleNotifications(hasVisibleNotifications);
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
        mStatusViewContainer = findViewById(R.id.status_view_container);
        mLogoutView = findViewById(R.id.logout);
        mNotificationIcons = findViewById(R.id.clock_notification_icon_container);
        if (mLogoutView != null) {
            mLogoutView.setOnClickListener(this::onLogoutClicked);
        }

        mClockView = findViewById(R.id.keyguard_clock_container);
        mDefaultClockView = findViewById(R.id.default_clock_view);
        mClockView.setShowCurrentUserTime(true);
        mSmallClockView  = findViewById(R.id.clock_view);
        mOwnerInfo = findViewById(R.id.owner_info);
        mKeyguardSlice = findViewById(R.id.keyguard_status_area);

        mWeatherView = (CurrentWeatherView) findViewById(R.id.weather_container);
        updateSettings();

        mTextColor = mClockView.getCurrentTextColor();

        mKeyguardSlice.setContentChangeListener(this::onSliceContentChanged);
        onSliceContentChanged();

        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext).isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refreshFormat();
        updateOwnerInfo();
        updateLogoutView();
        updateDark();
        updateSettings();
    }

    public KeyguardSliceView getKeyguardSliceView() {
        return mKeyguardSlice;
    }

    /**
     * Moves clock, adjusting margins when slice content changes.
     */
    private void onSliceContentChanged() {
        final boolean hasHeader = mKeyguardSlice.hasHeader();
        mClockView.setKeyguardShowingHeader(hasHeader);
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
        if (mNotificationIcons != null) {
            // Update top margin since header has appeared/disappeared.
            MarginLayoutParams params = (MarginLayoutParams) mNotificationIcons.getLayoutParams();
            params.setMargins(params.leftMargin,
                    hasHeader ? mIconTopMarginWithHeader : mIconTopMargin,
                    params.rightMargin,
                    params.bottomMargin);
            mNotificationIcons.setLayoutParams(params);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        layoutOwnerInfo();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        if (mClockView != null) {
	    if (mClockSelection == 4) {
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimensionPixelSize(R.dimen.widget_clock_small_font_size));
            } else {
                setFontSize(mClockView, mLockClockFontSize);
            }

            if (mClockSelection >= 8 && mClockSelection <= 12)
                mDefaultClockView.setLineSpacing(0, 0.8f);

            switch (mClockSelection) {
                case 1: // hidden
                    mSmallClockView.setVisibility(mDarkAmount != 1 ? View.GONE : View.VISIBLE);
                    break;
                case 2: // default
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 3: // default (bold)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 4: // default (small font)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 5: // default (accent)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 6: // default (accent hr)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 7: // default (accent min)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 8: // sammy
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 9: // sammy (bold)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 10: // sammy (accent)
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 11: // sammy accent hour
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                case 12: // sammy accent darker hour
                    mSmallClockView.setVisibility(View.VISIBLE);
                    break;
                }
            refreshFormat();
            setFontStyle(mClockView, mLockClockFontStyle);
        }

        if (mOwnerInfo != null) {
            setOwnerInfoSize(mOwnerInfoSize);
            setOwnerInfoFontStyle(mOwnerInfoFontStyle);
        }
        if (mWeatherView != null) {
            mWeatherView.onDensityOrFontScaleChanged();
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.setFontStyle(mLockDateFontStyle);
            mKeyguardSlice.setDateSize(mLockDateFontSize);

            // Dont hide slice view in doze
            mKeyguardSlice.setVisibility(mDarkAmount != 1 ? (mLockDateHide ? View.GONE : View.VISIBLE) : View.VISIBLE);
        }

        switch (mDateSelection) {
            case 0: // default
            default:
                try {
                    mKeyguardSlice.setViewBackgroundResource(0);
                    mDateVerPadding = 0;
                    mDateHorPadding = 0;
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.05f, false);
                } catch (Exception e) {
                }
                break;
            case 1: // semi-transparent box
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_box_str_border));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.05f, false);
                } catch (Exception e) {
                }
                break;
            case 2: // semi-transparent box (round)
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_border));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.05f, false);
                } catch (Exception e) {
                }
                break;
            case 3: // Q-Now Playing background
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.ambient_indication_pill_background));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.q_nowplay_pill_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.05f, false);
                } catch (Exception e) {
                }
                break;
            case 4: // accent box
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.15f, true);
                } catch (Exception e) {
                }
                break;
            case 5: // accent box transparent
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_accent), 160);
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.15f, true);
                } catch (Exception e) {
                }
                break;
            case 6: // gradient box
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_gradient));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.15f, true);
                } catch (Exception e) {
                }
                break;
            case 7: // Dark Accent border
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_borderacc));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.08f, true);
                } catch (Exception e) {
                }
                break;
            case 8: // Dark Gradient border
                try {
                    mKeyguardSlice.setViewBackground(getResources().getDrawable(R.drawable.date_str_bordergrad));
                    mDateHorPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_hor),getResources().getDisplayMetrics()));
                    mDateVerPadding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.widget_date_accent_box_padding_ver),getResources().getDisplayMetrics()));
                    mKeyguardSlice.setViewPadding(mDateHorPadding,mDateVerPadding,mDateHorPadding,mDateVerPadding);
                    mKeyguardSlice.setViewsTextStyles(0.08f, true);
                } catch (Exception e) {
                }
                break;
        }

        loadBottomMargin();
    }

    public void dozeTimeTick() {
        refreshTime();
        mKeyguardSlice.refresh();
    }

    private void refreshTime() {
        mClockView.refresh();

        if (mClockSelection == 2) {
            mClockView.setFormat12Hour(Patterns.clockView12);
            mClockView.setFormat24Hour(Patterns.clockView24);
        } else if (mClockSelection == 3) {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>h</strong>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong>:mm"));
        } else if (mClockSelection == 4) {
	        mClockView.setFormat12Hour(Html.fromHtml("<strong>h:mm</strong>"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk:mm</strong>"));
        } else if (mClockSelection == 5) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">h:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk:m</font>"));
        } else if (mClockSelection == 6) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">h</font>:mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk</font>:mm"));
        } else if (mClockSelection == 7) {
            mClockView.setFormat12Hour(Html.fromHtml("h<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">:mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">:mm</font>"));
        } else if (mClockSelection == 8) {
            mClockView.setFormat12Hour("hh\nmm");
            mClockView.setFormat24Hour("kk\nmm");
        } else if (mClockSelection == 10) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">hh<br>mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color=" + getResources().getColor(R.color.accent_device_default_light) + ">kk<br>mm</font>"));
        } else if (mClockSelection == 11) {
            mClockView.setFormat12Hour(Html.fromHtml("hh<br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("kk<br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
        } else if (mClockSelection == 12) {
            mClockView.setFormat12Hour(Html.fromHtml("<font color='#454545'>hh</font><br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
            mClockView.setFormat24Hour(Html.fromHtml("<font color='#454545'>kk</font><br><font color=" + getResources().getColor(R.color.accent_device_default_light) + ">mm</font>"));
        } else {
            mClockView.setFormat12Hour(Html.fromHtml("<strong>hh</strong><br>mm"));
            mClockView.setFormat24Hour(Html.fromHtml("<strong>kk</strong><br>mm"));
        }
    }

    private void updateTimeZone(TimeZone timeZone) {
        mClockView.onTimeZoneChanged(timeZone);
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

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight The height available to position the clock.
     * @return Y position of clock.
     */
    public int getClockPreferredY(int totalHeight) {
        return mClockView.getPreferredY(totalHeight);
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
            // Use the current user owner information if enabled.
            final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                    KeyguardUpdateMonitor.getCurrentUser());
            if (ownerInfoEnabled) {
                info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
            }
        }
        mOwnerInfo.setText(info);
        updateDark();
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
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCK_CLOCK_FONT_STYLE:
                    mLockClockFontStyle = TunerService.parseInteger(newValue, 4);
                onDensityOrFontScaleChanged();
                break;
            case LOCK_DATE_FONT_STYLE:
                    mLockDateFontStyle = TunerService.parseInteger(newValue, 14);
                onDensityOrFontScaleChanged();
                break;
            case LOCKSCREEN_CLOCK_SELECTION:
                    mClockSelection = TunerService.parseInteger(newValue, 2);
                onDensityOrFontScaleChanged();
                break;
            case LOCKSCREEN_DATE_HIDE:
                    mLockDateHide = TunerService.parseIntegerSwitch(newValue, false);
                onDensityOrFontScaleChanged();
                break;
            case LOCKSCREEN_DATE_SELECTION:
                    mDateSelection = TunerService.parseInteger(newValue, 0);
                onDensityOrFontScaleChanged();
                break;
            case LOCK_CLOCK_FONT_SIZE:
                    mLockClockFontSize = TunerService.parseInteger(newValue, 50);
                onDensityOrFontScaleChanged();
                break;
            case LOCK_DATE_FONT_SIZE:
                    mLockDateFontSize = TunerService.parseInteger(newValue, 18);
                onDensityOrFontScaleChanged();
                break;
            case LOCKOWNER_FONT_SIZE:
                    mOwnerInfoSize = TunerService.parseInteger(newValue, 18);
                onDensityOrFontScaleChanged();
                break;
            case LOCK_OWNERINFO_FONTS:
                    mOwnerInfoFontStyle = TunerService.parseInteger(newValue, 4);
                onDensityOrFontScaleChanged();
                break;
            default:
                break;
        }
    }

    private void setFontStyle(KeyguardClockSwitch view, int fontstyle) {
        switch (fontstyle) {
            case FONT_NORMAL:
                view.setTextFont(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                view.setTextFont(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                view.setTextFont(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                view.setTextFont(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                view.setTextFont(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                view.setTextFont(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                view.setTextFont(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                view.setTextFont(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                view.setTextFont(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                view.setTextFont(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                view.setTextFont(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                view.setTextFont(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                view.setTextFont(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                view.setTextFont(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                view.setTextFont(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                view.setTextFont(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                view.setTextFont(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_ACLONICA:
                view.setTextFont(Typeface.create("aclonica", Typeface.NORMAL));
                break;
            case FONT_AMARANTE:
                view.setTextFont(Typeface.create("amarante", Typeface.NORMAL));
                break;
            case FONT_BARIOL:
                view.setTextFont(Typeface.create("bariol", Typeface.NORMAL));
                break;
            case FONT_CAGLIOSTRO:
                view.setTextFont(Typeface.create("cagliostro", Typeface.NORMAL));
                break;
            case FONT_COOLSTORY:
                view.setTextFont(Typeface.create("coolstory", Typeface.NORMAL));
                break;
            case FONT_LGSMARTGOTHIC:
                view.setTextFont(Typeface.create("lgsmartgothic", Typeface.NORMAL));
                break;
            case FONT_ROSEMARY:
                view.setTextFont(Typeface.create("rosemary", Typeface.NORMAL));
                break;
            case FONT_SONYSKETCH:
                view.setTextFont(Typeface.create("sonysketch", Typeface.NORMAL));
                break;
            case FONT_SURFER:
                view.setTextFont(Typeface.create("surfer", Typeface.NORMAL));
                break;
            case FONT_COMICSANS:
                view.setTextFont(Typeface.create("comicsans", Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                view.setTextFont(Typeface.create("googlesans", Typeface.NORMAL));
                break;
            case FONT_ONEPLUSSLATE:
                view.setTextFont(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                view.setTextFont(Typeface.create("samsungone", Typeface.NORMAL));
                break;
            case FONT_COMFORTAA:
                view.setTextFont(Typeface.create("comfortaa", Typeface.NORMAL));
                break;
            case FONT_EXOTWO:
                view.setTextFont(Typeface.create("exotwo", Typeface.NORMAL));
                break;
            case FONT_STOROPIA:
                view.setTextFont(Typeface.create("storopia", Typeface.NORMAL));
                break;
            case FONT_UBUNTU:
                view.setTextFont(Typeface.create("ubuntu", Typeface.NORMAL));
                break;
            case FONT_NOKIAPURE:
                view.setTextFont(Typeface.create("nokiapure", Typeface.NORMAL));
                break;
            case FONT_FIFA2018:
                view.setTextFont(Typeface.create("fifa2018", Typeface.NORMAL));
                break;
            case FONT_ROADRAGE:
                view.setTextFont(Typeface.create("roadrage", Typeface.NORMAL));
                break;
            case FONT_20SEVEN:
                view.setTextFont(Typeface.create("20seven", Typeface.NORMAL));
                break;
            case FONT_COCON:
                view.setTextFont(Typeface.create("cocon", Typeface.NORMAL));
                break;
            case FONT_QUANDO:
                view.setTextFont(Typeface.create("quando", Typeface.NORMAL));
                break;
            case FONT_GRANDHOTEL:
                view.setTextFont(Typeface.create("grandhotel", Typeface.NORMAL));
                break;
            case FONT_REDRESSED:
                view.setTextFont(Typeface.create("redressed", Typeface.NORMAL));
                break;
            case FONT_SANFRANSISCO:
                view.setTextFont(Typeface.create("sanfransisco", Typeface.NORMAL));
                break;
            default:
                break;
        }
    }

    private void setFontSize(KeyguardClockSwitch view, int size) {
        switch (size) {
            case 20:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
                break;
            case 21:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
                break;
            case 22:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
                break;
            case 23:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
                break;
            case 24:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
                break;
            case 25:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
                break;
            case 26:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26));
                break;
            case 27:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27));
                break;
            case 28:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28));
                break;
            case 29:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29));
                break;
            case 30:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30));
                break;
            case 31:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31));
                break;
            case 32:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32));
                break;
            case 33:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33));
                break;
            case 34:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34));
                break;
            case 35:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35));
                break;
            case 36:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36));
                break;
            case 37:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37));
                break;
            case 38:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38));
                break;
            case 39:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39));
                break;
            case 40:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40));
                break;
            case 41:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41));
                break;
            case 42:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42));
                break;
            case 43:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43));
                break;
            case 44:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44));
                break;
            case 45:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45));
                break;
            case 46:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46));
                break;
            case 47:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47));
                break;
            case 48:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48));
                break;
            case 49:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49));
                break;
            case 50:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50));
                break;
            case 51:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
                break;
            case 52:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
                break;
            case 53:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
                break;
            case 54:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
                break;
            case 55:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
                break;
            case 56:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56));
                break;
            case 57:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57));
                break;
            case 58:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58));
                break;
            case 59:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59));
                break;
            case 60:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60));
                break;
            case 61:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61));
                break;
            case 62:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62));
                break;
            case 63:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63));
                break;
            case 64:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64));
                break;
            case 65:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
                break;
            case 66:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
                break;
            case 67:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
                break;
            case 68:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
                break;
            case 69:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
                break;
            case 70:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
                break;
            case 71:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
                break;
            case 72:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
                break;
            case 73:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
                break;
            case 74:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
                break;
            case 75:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
                break;
            case 76:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
                break;
            case 77:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
                break;
            case 78:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
                break;
            case 79:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
                break;
            case 80:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
                break;
            case 81:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
                break;
            case 82:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
                break;
            case 83:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
                break;
            case 84:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
                break;
            case 85:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
                break;
            case 86:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
                break;
            case 87:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
                break;
            case 88:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
                break;
            case 89:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
                break;
            case 90:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
                break;
            case 91:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
                break;
            case 92:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
                break;
            case 93:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
                break;
            case 94:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
                break;
            case 95:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
                break;
            case 96:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
                break;
            case 97:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
                break;
            case 98:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
                break;
            case 99:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
                break;
            case 100:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
                break;
            case 101:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
                break;
            case 102:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_102));
                break;
            case 103:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_103));
                break;
            case 104:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_104));
                break;
            case 105:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_105));
                break;
            case 106:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_106));
                break;
            case 107:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_107));
                break;
            case 108:
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_108));
                break;
            default:
                break;
        }
    }

    private void setOwnerInfoSize(int size) {
        switch (size) {
            case 10:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
                break;
            case 11:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
                break;
            case 12:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
                break;
            case 13:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
                break;
            case 14:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
                break;
            case 15:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
                break;
            case 16:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
                break;
            case 17:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
                break;
            case 18:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
                break;
            case 19:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
                break;
            case 20:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
                break;
            case 21:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
                break;
            case 22:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
                break;
            case 23:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
                break;
            case 24:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
                break;
            case 25:
                mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
                break;
        }
    }

    private void setOwnerInfoFontStyle(int fontstyle) {
        switch (fontstyle) {
            case FONT_NORMAL:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                mOwnerInfo.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                mOwnerInfo.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                mOwnerInfo.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_ACLONICA:
                mOwnerInfo.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
                break;
            case FONT_AMARANTE:
                mOwnerInfo.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
                break;
            case FONT_BARIOL:
                mOwnerInfo.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
                break;
            case FONT_CAGLIOSTRO:
                mOwnerInfo.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
                break;
            case FONT_COOLSTORY:
                mOwnerInfo.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
                break;
            case FONT_LGSMARTGOTHIC:
                mOwnerInfo.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
                break;
            case FONT_ROSEMARY:
                mOwnerInfo.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
                break;
            case FONT_SONYSKETCH:
                mOwnerInfo.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
                break;
            case FONT_SURFER:
                mOwnerInfo.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
                break;
            case FONT_COMICSANS:
                mOwnerInfo.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                mOwnerInfo.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
                break;
            case FONT_ONEPLUSSLATE:
                mOwnerInfo.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                mOwnerInfo.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
                break;
            case FONT_COMFORTAA:
                mOwnerInfo.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
                break;
            case FONT_EXOTWO:
                mOwnerInfo.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
                break;
            case FONT_STOROPIA:
                mOwnerInfo.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
                break;
            case FONT_UBUNTU:
                mOwnerInfo.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
                break;
            case FONT_NOKIAPURE:
                mOwnerInfo.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
                break;
            case FONT_FIFA2018:
                mOwnerInfo.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
                break;
            case FONT_ROADRAGE:
                mOwnerInfo.setTypeface(Typeface.create("roadrage", Typeface.NORMAL));
                break;
            case FONT_20SEVEN:
                mOwnerInfo.setTypeface(Typeface.create("20seven", Typeface.NORMAL));
                break;
            case FONT_COCON:
                mOwnerInfo.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
                break;
            case FONT_QUANDO:
                mOwnerInfo.setTypeface(Typeface.create("quando", Typeface.NORMAL));
                break;
            case FONT_GRANDHOTEL:
                mOwnerInfo.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
                break;
            case FONT_REDRESSED:
                mOwnerInfo.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
                break;
            case FONT_SANFRANSISCO:
                mOwnerInfo.setTypeface(Typeface.create("sanfransisco", Typeface.NORMAL));
                break;
            default:
                break;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mOwnerInfo: " + (mOwnerInfo == null
                ? "null" : mOwnerInfo.getVisibility() == VISIBLE));
        pw.println("  mPulsing: " + mPulsing);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mLogoutView != null) {
            pw.println("  logout visible: " + (mLogoutView.getVisibility() == VISIBLE));
        }
        if (mClockView != null) {
            mClockView.dump(fd, pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(fd, pw, args);
        }
    }

    private void loadBottomMargin() {
        mIconTopMargin = getResources().getDimensionPixelSize(R.dimen.widget_vertical_padding);
        mIconTopMarginWithHeader = getResources().getDimensionPixelSize(
                R.dimen.widget_vertical_padding_with_header);
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

            cacheKey = key;
        }
    }

    public void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
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
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        onDensityOrFontScaleChanged();
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
            if (mNotificationIcons != null) {
                // We're using scrolling in order not to overload the translation which is used
                // when appearing the icons
                mNotificationIcons.setScrollY(toRemove);
            }
        } else if (mNotificationIcons != null){
            mNotificationIcons.setScrollY(0);
        }
    }

    public void setPulsing(boolean pulsing) {
        if (mPulsing == pulsing) {
            return;
        }
        mPulsing = pulsing;
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

    private void updateSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        mShowWeather = Settings.System.getIntForUser(resolver,
                Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        mOmniStyle = Settings.System.getIntForUser(resolver,
                Settings.System.LOCKSCREEN_WEATHER_STYLE, 0,
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
    }
}
