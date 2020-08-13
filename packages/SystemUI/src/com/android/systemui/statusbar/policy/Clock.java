/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.systemui.DemoMode;
import com.android.systemui.Dependency;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.SysUiServiceProvider;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;

import libcore.icu.LocaleData;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import lineageos.providers.LineageSettings;

/**
 * Digital clock for the status bar.
 */
public class Clock extends TextView implements DemoMode, Tunable, CommandQueue.Callbacks,
        DarkReceiver, ConfigurationListener {

    public static final String CLOCK_SECONDS = "clock_seconds";
    private static final String TAG = "StatusBarClock";
    private static final String CLOCK_SUPER_PARCELABLE = "clock_super_parcelable";
    private static final String CURRENT_USER_ID = "current_user_id";
    private static final String VISIBLE_BY_POLICY = "visible_by_policy";
    private static final String VISIBLE_BY_USER = "visible_by_user";
    private static final String SHOW_SECONDS = "show_seconds";
    private static final String VISIBILITY = "visibility";
    private static final String QSHEADER = "qsheader";

    private final CurrentUserTracker mCurrentUserTracker;
    private int mCurrentUserId;

    private boolean mClockVisibleByPolicy = true;
    private boolean mClockVisibleByUser = getVisibility() == View.VISIBLE;

    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString;
    private SimpleDateFormat mClockFormat;
    private SimpleDateFormat mContentDescriptionFormat;
    private Locale mLocale;
    private boolean mScreenOn = true;
    private Handler autoHideHandler = new Handler();

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;

    private static final int CLOCK_DATE_DISPLAY_GONE = 0;
    private static final int CLOCK_DATE_DISPLAY_SMALL = 1;
    private static final int CLOCK_DATE_DISPLAY_NORMAL = 2;

    private static final int CLOCK_DATE_STYLE_REGULAR = 0;
    private static final int CLOCK_DATE_STYLE_LOWERCASE = 1;
    private static final int CLOCK_DATE_STYLE_UPPERCASE = 2;

    private static final int STYLE_DATE_LEFT = 0;
    private static final int STYLE_DATE_RIGHT = 1;
    private static final int HIDE_DURATION = 60; // 1 minute
    private static final int SHOW_DURATION = 5; // 5 seconds

    private int mAmPmStyle = AM_PM_STYLE_GONE;

    private int mClockFontStyle = FONT_NORMAL;
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
    private static final int FONT_BIGNOODLE_ITALIC = 51;
    private static final int FONT_BIGNOODLE_REGULAR = 52;
    private static final int FONT_HANKEN = 53;
    private static final int FONT_MITTELSCHRIFT = 54;
    private static final int FONT_REEMKUFI = 55;
    private static final int FONT_COMIC_NEUE_BOLD = 56;
    private static final int FONT_COMIC_NEUE = 57;
    private static final int FONT_EXO2_REGULAR = 58;
    private static final int FONT_EXO2_SEMIBOLD = 59;
    private static final int FONT_FINLANDICA = 60;
    private static final int FONT_GOODLIGHT = 61;
    private static final int FONT_GRAVITY_REGULAR = 62;
    private static final int FONT_INTER_REGULAR = 63;
    private static final int FONT_INTER_MEDIUM_ITALIC = 64;
    private static final int FONT_LEAGUE_MONO_N_REGULAR = 65;
    private static final int FONT_LEAGUE_MONO_N_MEDIUM = 66;
    private static final int FONT_LEAGUE_MONO_N_BOLD = 67;
    private static final int FONT_LEAGUE_MONO_N_SEMIBOLD = 68;
    private static final int FONT_LEONSANS_REGULAR = 69;
    private static final int FONT_MESCLA_REGULAR = 70;
    private static final int FONT_ODIBEE_SANS = 71;
    private static final int FONT_PANAMERICANA = 72;
    private static final int FONT_PT_SANS = 73;
    private static final int FONT_PT_MONO = 74;
    private static final int FONT_ROUNDED_GOTHIC_NARROW = 75;
    private static final int FONT_ROUNDED_GOTHIC_NARROW_HALF_ITALIC = 76;
    private static final int FONT_SOFIA_SANS_REGULAR = 77;
    private static final int FONT_SOFIA_SANS_MEDIUM = 78;
    private static final int FONT_SOFIA_SEMICONDENSED_REGULAR = 79;
    private static final int FONT_SOFIA_SEMICONDENSED_MEDIUM = 80;
    private static final int FONT_SAMSUNG = 81;
    private static final int FONT_MEXCELLENT = 82;
    private static final int FONT_BURNSTOWN = 83;
    private static final int FONT_DUMBLEDOR = 84;
    private static final int FONT_PHANTOMBOLD = 85;
    private static final int FONT_SNOWSTORM = 86;
    private static final int FONT_NEONEON = 87;
    private static final int FONT_CIRCULARSTD = 88;
    public int DEFAULT_CLOCK_SIZE = 14;
    public int DEFAULT_CLOCK_COLOR = 0xffffffff;

    private final boolean mShowDark;
    private boolean mShowSeconds;
    private Handler mSecondsHandler;
    private int mClockDateDisplay = CLOCK_DATE_DISPLAY_GONE;
    private int mClockDateStyle = CLOCK_DATE_STYLE_REGULAR;
    private int mClockDatePosition;
    private String mClockDateFormat = null;
    private boolean mClockAutoHide;
    private int mHideDuration = HIDE_DURATION, mShowDuration = SHOW_DURATION;
    private boolean mQsHeader;
    private boolean mQsClockStyle;
    private int mClockColor = 0xffffffff;
    private int mClockSize = 14;
    private int mQsClockSize = 24;

    public static final String STATUS_BAR_CLOCK_SECONDS =
            "system:" + Settings.System.STATUS_BAR_CLOCK_SECONDS;
    private static final String STATUS_BAR_AM_PM =
            "lineagesystem:" + LineageSettings.System.STATUS_BAR_AM_PM;
    public static final String STATUS_BAR_CLOCK_DATE_DISPLAY =
            "system:" + Settings.System.STATUS_BAR_CLOCK_DATE_DISPLAY;
    public static final String STATUS_BAR_CLOCK_DATE_STYLE =
            "system:" + Settings.System.STATUS_BAR_CLOCK_DATE_STYLE;
    public static final String STATUS_BAR_CLOCK_DATE_POSITION =
            "system:" + Settings.System.STATUS_BAR_CLOCK_DATE_POSITION;
    public static final String STATUS_BAR_CLOCK_DATE_FORMAT =
            "system:" + Settings.System.STATUS_BAR_CLOCK_DATE_FORMAT;
    public static final String STATUS_BAR_CLOCK_AUTO_HIDE =
            "system:" + Settings.System.STATUS_BAR_CLOCK_AUTO_HIDE;
    public static final String STATUS_BAR_CLOCK_AUTO_HIDE_HDURATION =
            "system:" + Settings.System.STATUS_BAR_CLOCK_AUTO_HIDE_HDURATION;
    public static final String STATUS_BAR_CLOCK_AUTO_HIDE_SDURATION =
            "system:" + Settings.System.STATUS_BAR_CLOCK_AUTO_HIDE_SDURATION;
    public static final String STATUS_BAR_CLOCK_SIZE =
            "system:" + Settings.System.STATUS_BAR_CLOCK_SIZE;
    public static final String STATUS_BAR_CLOCK_COLOR =
            "system:" + Settings.System.STATUS_BAR_CLOCK_COLOR;
    public static final String STATUS_BAR_CLOCK_FONT_STYLE =
            "system:" + Settings.System.STATUS_BAR_CLOCK_FONT_STYLE;
    public static final String QS_CLOCK_STYLE =
            "system:" + Settings.System.QS_CLOCK_STYLE;

    /**
     * Whether we should use colors that adapt based on wallpaper/the scrim behind quick settings
     * for text.
     */
    private boolean mUseWallpaperTextColor;

    /**
     * Color to be set on this {@link TextView}, when wallpaperTextColor is <b>not</b> utilized.
     */
    private int mNonAdaptedColor;

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.Clock,
                0, 0);
        try {
            mAmPmStyle = a.getInt(R.styleable.Clock_amPmStyle, mAmPmStyle);
            mShowDark = a.getBoolean(R.styleable.Clock_showDark, true);
            mNonAdaptedColor = getCurrentTextColor();
        } finally {
            a.recycle();
        }
        mCurrentUserTracker = new CurrentUserTracker(context) {
            @Override
            public void onUserSwitched(int newUserId) {
                mCurrentUserId = newUserId;
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(CLOCK_SUPER_PARCELABLE, super.onSaveInstanceState());
        bundle.putInt(CURRENT_USER_ID, mCurrentUserId);
        bundle.putBoolean(VISIBLE_BY_POLICY, mClockVisibleByPolicy);
        bundle.putBoolean(VISIBLE_BY_USER, mClockVisibleByUser);
        bundle.putBoolean(SHOW_SECONDS, mShowSeconds);
        bundle.putInt(VISIBILITY, getVisibility());
        bundle.putBoolean(QSHEADER, mQsHeader);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state == null || !(state instanceof Bundle)) {
            super.onRestoreInstanceState(state);
            return;
        }

        Bundle bundle = (Bundle) state;
        Parcelable superState = bundle.getParcelable(CLOCK_SUPER_PARCELABLE);
        super.onRestoreInstanceState(superState);
        if (bundle.containsKey(CURRENT_USER_ID)) {
            mCurrentUserId = bundle.getInt(CURRENT_USER_ID);
        }
        mClockVisibleByPolicy = bundle.getBoolean(VISIBLE_BY_POLICY, true);
        mClockVisibleByUser = bundle.getBoolean(VISIBLE_BY_USER, true);
        mShowSeconds = bundle.getBoolean(SHOW_SECONDS, false);
        if (bundle.containsKey(VISIBILITY)) {
            super.setVisibility(bundle.getInt(VISIBILITY));
        }
        mQsHeader = bundle.getBoolean(QSHEADER, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);

            getContext().registerReceiverAsUser(mIntentReceiver, UserHandle.ALL, filter,
                    null, Dependency.get(Dependency.TIME_TICK_HANDLER));
            Dependency.get(TunerService.class).addTunable(this,
                    STATUS_BAR_CLOCK_SECONDS,
                    STATUS_BAR_AM_PM,
                    STATUS_BAR_CLOCK_DATE_DISPLAY,
                    STATUS_BAR_CLOCK_DATE_STYLE,
                    STATUS_BAR_CLOCK_DATE_POSITION,
                    STATUS_BAR_CLOCK_DATE_FORMAT,
                    STATUS_BAR_CLOCK_AUTO_HIDE,
                    STATUS_BAR_CLOCK_AUTO_HIDE_HDURATION,
                    STATUS_BAR_CLOCK_AUTO_HIDE_SDURATION,
                    STATUS_BAR_CLOCK_SIZE,
                    STATUS_BAR_CLOCK_COLOR,
                    STATUS_BAR_CLOCK_FONT_STYLE,
                    QS_CLOCK_STYLE);
            SysUiServiceProvider.getComponent(getContext(), CommandQueue.class).addCallback(this);
            if (mShowDark) {
                Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
            }
            mCurrentUserTracker.startTracking();
            mCurrentUserId = mCurrentUserTracker.getCurrentUserId();
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        // Make sure we update to the current time
        mClockFormatString = ""; // force refresh
        updateClock();
        updateClockVisibility();
        updateShowSeconds();
        updateClockVisibility();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
            Dependency.get(TunerService.class).removeTunable(this);
            SysUiServiceProvider.getComponent(getContext(), CommandQueue.class)
                    .removeCallback(this);
            if (mShowDark) {
                Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
            }
            mCurrentUserTracker.stopTracking();
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Handler handler = getHandler();
            if (handler == null) {
                Log.e(TAG,
                        "Received intent, but handler is null - still attached to window? Window "
                                + "token: "
                                + getWindowToken());
                return;
            }

            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                handler.post(() -> {
                    mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                    if (mClockFormat != null) {
                        mClockFormat.setTimeZone(mCalendar.getTimeZone());
                    }
                });
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                final Locale newLocale = getResources().getConfiguration().locale;
                handler.post(() -> {
                    if (!newLocale.equals(mLocale)) {
                        mLocale = newLocale;
                    }
                    mClockFormatString = ""; // force refresh
                    updateClockVisibility();
                    updateShowSeconds();
                    updateClock();
                    return;
                });
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                mScreenOn = true;
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                mScreenOn = false;
            }
            if (mScreenOn) {
                handler.post(() -> updateClock());
                if (mClockAutoHide) autoHideHandler.post(() -> updateClockVisibility());
            }
        }
    };

    @Override
    public void setVisibility(int visibility) {
        if (visibility == View.VISIBLE && !shouldBeVisible()) {
            return;
        }

        super.setVisibility(visibility);
    }

    public void setQsHeader() {
        mQsHeader = true;
    }

    public void setClockVisibleByUser(boolean visible) {
        mClockVisibleByUser = visible;
        updateClockVisibility();
    }

    public void setClockVisibilityByPolicy(boolean visible) {
        mClockVisibleByPolicy = visible;
        updateClockVisibility();
    }

    public boolean shouldBeVisible() {
        return mClockVisibleByPolicy && mClockVisibleByUser;
    }

    private void updateClockVisibility() {
        boolean visible = mClockVisibleByPolicy && mClockVisibleByUser;
        int visibility = visible ? View.VISIBLE : View.GONE;
        try {
            autoHideHandler.removeCallbacksAndMessages(null);
        } catch (NullPointerException e) {
            // Do nothing
        }
        setVisibility(visibility);
        if (!mQsHeader && mClockAutoHide && visible && mScreenOn) {
            autoHideHandler.postDelayed(()->autoHideClock(), mShowDuration * 1000);
        }
    }

    private void autoHideClock() {
        setVisibility(View.GONE);
        autoHideHandler.postDelayed(()->updateClockVisibility(), mHideDuration * 1000);
    }

    private void updateClockStyle() {
        if (!mQsClockStyle) {
            if (mQsHeader) {
                setTextSize(mQsClockSize);
            } else {
                setTextSize(mClockSize);
            }
        } else {
            setTextSize(mClockSize);
        }
        if (mClockColor == 0xFFFFFFFF) {
            setTextColor(mNonAdaptedColor);
        } else {
            setTextColor(mClockColor);
        }
        getFontStyle(mClockFontStyle);
        updateClock();
    }

    final void updateClock() {
        if (mDemoMode || mCalendar == null) return;
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
        setContentDescription(mContentDescriptionFormat.format(mCalendar.getTime()));
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_CLOCK_SECONDS:
                mShowSeconds =
                        TunerService.parseIntegerSwitch(newValue, false);
                updateShowSeconds();
                break;
            case STATUS_BAR_AM_PM:
                mAmPmStyle =
                        TunerService.parseInteger(newValue, AM_PM_STYLE_GONE);
                break;
            case STATUS_BAR_CLOCK_DATE_DISPLAY:
                mClockDateDisplay =
                        TunerService.parseInteger(newValue, CLOCK_DATE_DISPLAY_GONE);
                break;
            case STATUS_BAR_CLOCK_DATE_STYLE:
                mClockDateStyle =
                        TunerService.parseInteger(newValue, CLOCK_DATE_STYLE_REGULAR);
                break;
            case STATUS_BAR_CLOCK_DATE_POSITION:
                mClockDatePosition =
                        TunerService.parseInteger(newValue, STYLE_DATE_LEFT);
                break;
            case STATUS_BAR_CLOCK_DATE_FORMAT:
                mClockDateFormat = newValue;
                break;
            case STATUS_BAR_CLOCK_AUTO_HIDE:
                mClockAutoHide =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            case STATUS_BAR_CLOCK_AUTO_HIDE_HDURATION:
                mHideDuration =
                        TunerService.parseInteger(newValue, HIDE_DURATION);
                break;
            case STATUS_BAR_CLOCK_AUTO_HIDE_SDURATION:
                mShowDuration =
                        TunerService.parseInteger(newValue, SHOW_DURATION);
                break;
            case STATUS_BAR_CLOCK_SIZE:
                mClockSize =
                        TunerService.parseInteger(newValue, 14);
                break;
            case STATUS_BAR_CLOCK_COLOR:
                mClockColor =
                        TunerService.parseInteger(newValue, DEFAULT_CLOCK_COLOR);
                break;
            case STATUS_BAR_CLOCK_FONT_STYLE:
                mClockFontStyle =
                        TunerService.parseInteger(newValue, 36);
                break;
            case QS_CLOCK_STYLE:
                mQsClockStyle =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            default:
                break;
        }
        mClockFormatString = ""; // force refresh
        updateClockStyle();
        updateClock();
        updateClockVisibility();
    }

    @Override
    public void disable(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getDisplay().getDisplayId()) {
            return;
        }
        boolean clockVisibleByPolicy = (state1 & StatusBarManager.DISABLE_CLOCK) == 0;
        if (clockVisibleByPolicy != mClockVisibleByPolicy) {
            setClockVisibilityByPolicy(clockVisibleByPolicy);
        }
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mNonAdaptedColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mClockColor == DEFAULT_CLOCK_COLOR) {
            setTextColor(mNonAdaptedColor);
        } else {
            setTextColor(mClockColor);
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        FontSizeUtils.updateFontSize(this, R.dimen.status_bar_clock_size);
        setPaddingRelative(
                mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_clock_starting_padding),
                0,
                mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_clock_end_padding),
                0);
    }

    /**
     * Sets whether the clock uses the wallpaperTextColor. If we're not using it, we'll revert back
     * to dark-mode-based/tinted colors.
     *
     * @param shouldUseWallpaperTextColor whether we should use wallpaperTextColor for text color
     */
    public void useWallpaperTextColor(boolean shouldUseWallpaperTextColor) {
        setTextColor(mClockColor);
    }

    private void updateShowSeconds() {
        if (mShowSeconds) {
            // Wait until we have a display to start trying to show seconds.
            if (mSecondsHandler == null && getDisplay() != null) {
                mSecondsHandler = new Handler();
                if (getDisplay().getState() == Display.STATE_ON) {
                    mSecondsHandler.postAtTime(mSecondTick,
                            SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                }
                IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
                filter.addAction(Intent.ACTION_SCREEN_ON);
                mContext.registerReceiver(mScreenReceiver, filter);
            }
        } else {
            if (mSecondsHandler != null) {
                mContext.unregisterReceiver(mScreenReceiver);
                mSecondsHandler.removeCallbacks(mSecondTick);
                mSecondsHandler = null;
            }
        }
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean is24 = DateFormat.is24HourFormat(context, mCurrentUserId);
        LocaleData d = LocaleData.get(context.getResources().getConfiguration().locale);

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = mShowSeconds
                ? is24 ? d.timeFormat_Hms : d.timeFormat_hms
                : is24 ? d.timeFormat_Hm : d.timeFormat_hm;
        if (!format.equals(mClockFormatString)) {
            mContentDescriptionFormat = new SimpleDateFormat(format);
            /*
             * Search for an unquoted "a" in the format string, so we can
             * add dummy characters around it to let us find it again after
             * formatting and change its size.
             */
            if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
                int a = -1;
                boolean quoted = false;
                for (int i = 0; i < format.length(); i++) {
                    char c = format.charAt(i);

                    if (c == '\'') {
                        quoted = !quoted;
                    }
                    if (!quoted && c == 'a') {
                        a = i;
                        break;
                    }
                }

                if (a >= 0) {
                    // Move a back so any whitespace before AM/PM is also in the alternate size.
                    final int b = a;
                    while (a > 0 && Character.isWhitespace(format.charAt(a-1))) {
                        a--;
                    }
                    format = format.substring(0, a) + MAGIC1 + format.substring(a, b)
                        + "a" + MAGIC2 + format.substring(b + 1);
                }
            }
            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }

        CharSequence dateString = null;

        String result = "";
        String timeResult = sdf.format(mCalendar.getTime());
        String dateResult = "";

        if (!mQsHeader && mClockDateDisplay != CLOCK_DATE_DISPLAY_GONE) {
            Date now = new Date();

            if (mClockDateFormat == null || mClockDateFormat.isEmpty()) {
                // Set dateString to short uppercase Weekday if empty
                dateString = DateFormat.format("EEE", now);
            } else {
                dateString = DateFormat.format(mClockDateFormat, now);
            }
            if (mClockDateStyle == CLOCK_DATE_STYLE_LOWERCASE) {
                // When Date style is small, convert date to uppercase
                dateResult = dateString.toString().toLowerCase();
            } else if (mClockDateStyle == CLOCK_DATE_STYLE_UPPERCASE) {
                dateResult = dateString.toString().toUpperCase();
            } else {
                dateResult = dateString.toString();
            }
            result = (mClockDatePosition == STYLE_DATE_LEFT) ? dateResult + " " + timeResult
                    : timeResult + " " + dateResult;
        } else {
            // No date, just show time
            result = timeResult;
        }

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);

        if (mClockDateDisplay != CLOCK_DATE_DISPLAY_NORMAL) {
            if (dateString != null) {
                int dateStringLen = dateString.length();
                int timeStringOffset = (mClockDatePosition == STYLE_DATE_RIGHT)
                        ? timeResult.length() + 1 : 0;
                if (mClockDateDisplay == CLOCK_DATE_DISPLAY_GONE) {
                   formatted.delete(0, dateStringLen);
                } else {
                    if (mClockDateDisplay == CLOCK_DATE_DISPLAY_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, timeStringOffset,
                                timeStringOffset + dateStringLen,
                                Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                }
            }
        }

        if (mAmPmStyle != AM_PM_STYLE_NORMAL) {
            int magic1 = result.indexOf(MAGIC1);
            int magic2 = result.indexOf(MAGIC2);
            if (magic1 >= 0 && magic2 > magic1) {
                if (mAmPmStyle == AM_PM_STYLE_GONE) {
                    formatted.delete(magic1, magic2+1);
                } else {
                    if (mAmPmStyle == AM_PM_STYLE_SMALL) {
                        CharacterStyle style = new RelativeSizeSpan(0.7f);
                        formatted.setSpan(style, magic1, magic2,
                                          Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                    formatted.delete(magic2, magic2 + 1);
                    formatted.delete(magic1, magic1 + 1);
                }
            }
        }

        return formatted;
    }

    private boolean mDemoMode;

    @Override
    public void dispatchDemoCommand(String command, Bundle args) {
        if (!mDemoMode && command.equals(COMMAND_ENTER)) {
            mDemoMode = true;
        } else if (mDemoMode && command.equals(COMMAND_EXIT)) {
            mDemoMode = false;
            updateClock();
        } else if (mDemoMode && command.equals(COMMAND_CLOCK)) {
            String millis = args.getString("millis");
            String hhmm = args.getString("hhmm");
            if (millis != null) {
                mCalendar.setTimeInMillis(Long.parseLong(millis));
            } else if (hhmm != null && hhmm.length() == 4) {
                int hh = Integer.parseInt(hhmm.substring(0, 2));
                int mm = Integer.parseInt(hhmm.substring(2));
                boolean is24 = DateFormat.is24HourFormat(getContext(), mCurrentUserId);
                if (is24) {
                    mCalendar.set(Calendar.HOUR_OF_DAY, hh);
                } else {
                    mCalendar.set(Calendar.HOUR, hh);
                }
                mCalendar.set(Calendar.MINUTE, mm);
            }
            setText(getSmallTime());
            setContentDescription(mContentDescriptionFormat.format(mCalendar.getTime()));
        }
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mSecondsHandler != null) {
                    mSecondsHandler.removeCallbacks(mSecondTick);
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mSecondsHandler != null) {
                    mSecondsHandler.postAtTime(mSecondTick,
                            SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
                }
            }
        }
    };

    private final Runnable mSecondTick = new Runnable() {
        @Override
        public void run() {
            if (mCalendar != null) {
                updateClock();
            }
            mSecondsHandler.postAtTime(this, SystemClock.uptimeMillis() / 1000 * 1000 + 1000);
        }
    };

    public void getFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_ACLONICA:
                setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
                break;
            case FONT_AMARANTE:
                setTypeface(Typeface.create("amarante", Typeface.NORMAL));
                break;
            case FONT_BARIOL:
                setTypeface(Typeface.create("bariol", Typeface.NORMAL));
                break;
            case FONT_CAGLIOSTRO:
                setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
                break;
            case FONT_COOLSTORY:
                setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
                break;
            case FONT_LGSMARTGOTHIC:
                setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
                break;
            case FONT_ROSEMARY:
                setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
                break;
            case FONT_SONYSKETCH:
                setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
                break;
            case FONT_SURFER:
                setTypeface(Typeface.create("surfer", Typeface.NORMAL));
                break;
            case FONT_COMICSANS:
                setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
                break;
            case FONT_ONEPLUSSLATE:
            default:
                setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
                break;
            case FONT_COMFORTAA:
                setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
                break;
            case FONT_EXOTWO:
                setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
                break;
            case FONT_STOROPIA:
                setTypeface(Typeface.create("storopia", Typeface.NORMAL));
                break;
            case FONT_UBUNTU:
                setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
                break;
            case FONT_NOKIAPURE:
                setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
                break;
            case FONT_FIFA2018:
                setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
                break;
            case FONT_ROADRAGE:
                setTypeface(Typeface.create("roadrage", Typeface.NORMAL));
                break;
            case FONT_20SEVEN:
                setTypeface(Typeface.create("20seven", Typeface.NORMAL));
                break;
            case FONT_COCON:
                setTypeface(Typeface.create("cocon", Typeface.NORMAL));
                break;
            case FONT_QUANDO:
                setTypeface(Typeface.create("quando", Typeface.NORMAL));
                break;
            case FONT_GRANDHOTEL:
                setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
                break;
            case FONT_REDRESSED:
                setTypeface(Typeface.create("redressed", Typeface.NORMAL));
                break;
            case FONT_SANFRANSISCO:
                setTypeface(Typeface.create("sanfransisco", Typeface.NORMAL));
                break;
            case FONT_BIGNOODLE_ITALIC:
                setTypeface(Typeface.create("bignoodle-italic", Typeface.NORMAL));
                break;
            case FONT_BIGNOODLE_REGULAR:
                setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
                break;
            case FONT_HANKEN:
                setTypeface(Typeface.create("hanken", Typeface.NORMAL));
                break;
            case FONT_MITTELSCHRIFT:
                setTypeface(Typeface.create("mittelschrift", Typeface.NORMAL));
                break;
            case FONT_REEMKUFI:
                setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
                break;
            case FONT_COMIC_NEUE_BOLD:
                setTypeface(Typeface.create("comic-neue-bold", Typeface.NORMAL));
                break;
            case FONT_COMIC_NEUE:
                setTypeface(Typeface.create("comic-neue", Typeface.NORMAL));
                break;
            case FONT_EXO2_REGULAR:
                setTypeface(Typeface.create("exo2-regular", Typeface.NORMAL));
                break;
            case FONT_EXO2_SEMIBOLD:
                setTypeface(Typeface.create("exo2-semibold", Typeface.NORMAL));
                break;
            case FONT_FINLANDICA:
                setTypeface(Typeface.create("finlandica", Typeface.NORMAL));
                break;
            case FONT_GOODLIGHT:
                setTypeface(Typeface.create("goodlight", Typeface.NORMAL));
                break;
            case FONT_GRAVITY_REGULAR:
                setTypeface(Typeface.create("gravity-regular", Typeface.NORMAL));
                break;
            case FONT_INTER_REGULAR:
                setTypeface(Typeface.create("inter-regular", Typeface.NORMAL));
                break;
            case FONT_INTER_MEDIUM_ITALIC:
                setTypeface(Typeface.create("inter-medium-italic", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_REGULAR:
                setTypeface(Typeface.create("league-mono-n-regular", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_MEDIUM:
                setTypeface(Typeface.create("league-mono-n-medium", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_BOLD:
                setTypeface(Typeface.create("league-mono-n-bold", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_SEMIBOLD:
                setTypeface(Typeface.create("league-mono-n-semibold", Typeface.NORMAL));
                break;
            case FONT_LEONSANS_REGULAR:
                setTypeface(Typeface.create("leonsans-regular", Typeface.NORMAL));
                break;
            case FONT_MESCLA_REGULAR:
                setTypeface(Typeface.create("mescla-regular", Typeface.NORMAL));
                break;
            case FONT_ODIBEE_SANS:
                setTypeface(Typeface.create("odibee-sans", Typeface.NORMAL));
                break;
            case FONT_PANAMERICANA:
                setTypeface(Typeface.create("panamericana", Typeface.NORMAL));
                break;
            case FONT_PT_SANS:
                setTypeface(Typeface.create("pt-sans", Typeface.NORMAL));
                break;
            case FONT_PT_MONO:
                setTypeface(Typeface.create("pt-mono", Typeface.NORMAL));
                break;
            case FONT_ROUNDED_GOTHIC_NARROW:
                setTypeface(Typeface.create("rounded-gothic-narrow", Typeface.NORMAL));
                break;
            case FONT_ROUNDED_GOTHIC_NARROW_HALF_ITALIC:
                setTypeface(Typeface.create("rounded-gothic-narrow-half-italic", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SANS_REGULAR:
                setTypeface(Typeface.create("sofia-sans-regular", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SANS_MEDIUM:
                setTypeface(Typeface.create("sofia-sans-medium", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SEMICONDENSED_REGULAR:
                setTypeface(Typeface.create("sofia-semicondensed-regular", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SEMICONDENSED_MEDIUM:
                setTypeface(Typeface.create("sofia-semicondensed-medium", Typeface.NORMAL));
                break;
            case FONT_SAMSUNG:
                setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case FONT_MEXCELLENT:
                setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case FONT_BURNSTOWN:
                setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case FONT_DUMBLEDOR:
                setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case FONT_PHANTOMBOLD:
                setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case FONT_SNOWSTORM:
                setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case FONT_NEONEON:
                setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case FONT_CIRCULARSTD:
                setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
        }
    }
}

