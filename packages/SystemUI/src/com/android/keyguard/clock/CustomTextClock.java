/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.GradientColors;
import com.android.internal.graphics.ColorUtils;
import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.colorextraction.SysuiColorExtractor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

public class CustomTextClock extends TextView implements ColorExtractor.OnColorsChangedListener {

    private String mDescFormat;
    private final String[] mHours;
    private final String[] mMinutes;
    private final Resources mResources;
    private final Calendar mTime;
    private TimeZone mTimeZone;
    private SysuiColorExtractor mColorExtractor;

    private int mPrimaryColor;
    private int mAmbientColor;
    private int mSystemAccent;
    private int mFallbackColor;
    private int mCurrentAccent;
    private float mDarkAmount;
    private float[] mHslOut = new float[3];

    private int mClockSize = 40;
    private SettingsObserver mSettingsObserver;

    private final BroadcastReceiver mTimeZoneChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                onTimeZoneChanged(TimeZone.getTimeZone(tz));
                onTimeChanged();
            }
        }
    };

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public CustomTextClock(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        mColorExtractor = Dependency.get(SysuiColorExtractor.class);
        mColorExtractor.addOnColorsChangedListener(this);
        mTime = Calendar.getInstance(TimeZone.getDefault());
        mDescFormat = ((SimpleDateFormat) DateFormat.getTimeFormat(context)).toLocalizedPattern();
        mResources = context.getResources();
        mHours = mResources.getStringArray(R.array.type_clock_hours);
        mMinutes = mResources.getStringArray(R.array.type_clock_minutes);
        mSystemAccent = mResources.getColor(R.color.accent_device_default_light, null);
        mFallbackColor = mResources.getColor(R.color.custom_text_clock_top_fallback_color, null);
        onColorsChanged(mColorExtractor, 0);
    }

    public void onTimeChanged() {
        mTime.setTimeInMillis(System.currentTimeMillis());
        setContentDescription(DateFormat.format(mDescFormat, mTime));
        int hours = mTime.get(Calendar.HOUR) % 12;
        int minutes = mTime.get(Calendar.MINUTE) % 60;
        SpannedString rawFormat = (SpannedString) mResources.getQuantityText(R.plurals.type_clock_header, hours);
        Annotation[] annotationArr = (Annotation[]) rawFormat.getSpans(0, rawFormat.length(), Annotation.class);
        SpannableString colored = new SpannableString(rawFormat);
        for (Annotation annotation : annotationArr) {
            if ("color".equals(annotation.getValue())) {
                colored.setSpan(new ForegroundColorSpan(mCurrentAccent),
                        colored.getSpanStart(annotation),
                        colored.getSpanEnd(annotation),
                        Spanned.SPAN_POINT_POINT);
            }
        }
        setText(TextUtils.expandTemplate(colored, new CharSequence[]{mHours[hours], mMinutes[minutes]}));
    }

    public void onTimeZoneChanged(TimeZone timeZone) {
        mTimeZone = timeZone;
        mTime.setTimeZone(timeZone);
    }

    @Override
    public void onColorsChanged(ColorExtractor extractor, int which) {
        GradientColors colors = extractor.getColors(WallpaperManager.FLAG_LOCK);
        setWallpaperColors(colors.getMainColor(), colors.supportsDarkText(), colors.getColorPalette());
    }

    private void setWallpaperColors(int mainColor, boolean supportsDarkText, int[] colorPalette) {
        int scrimColor = supportsDarkText ? Color.WHITE : Color.BLACK;
        int scrimTinted = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(scrimColor, mainColor, 0.5f), 64);
        int bgColor = ColorUtils.compositeColors(scrimTinted, mainColor);

        int paletteColor = getColorFromPalette(colorPalette);
        bgColor = ColorUtils.compositeColors(bgColor, Color.BLACK);
        mPrimaryColor = findColor(paletteColor, bgColor, !supportsDarkText, mSystemAccent, mFallbackColor);
        mAmbientColor = findColor(paletteColor, Color.BLACK, true, mSystemAccent, mFallbackColor);

        setDarkAmount(mDarkAmount);
    }

    private int getColorFromPalette(int[] palette) {
        if (palette != null && palette.length != 0) {
            return palette[Math.max(0, palette.length - 5)];
        } else {
            return mSystemAccent;
        }
    }

    private int findColor(int color, int background, boolean againstDark, int accent, int fallback) {
        if (!isGreyscale(color)) {
            return color;
        } else {
            return fallback;
        }
    }

    private boolean isGreyscale(int color) {
        ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), mHslOut);
        return mHslOut[1] < 0.1f || mHslOut[2] < 0.1f;
    }

    public void setDarkAmount(float dark) {
        mDarkAmount = dark;
        mCurrentAccent = ColorUtils.blendARGB(mPrimaryColor, mAmbientColor, mDarkAmount);
        onTimeChanged();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Calendar calendar = mTime;
        TimeZone timeZone = mTimeZone;
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }
        calendar.setTimeZone(timeZone);
        onTimeChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeZoneChangedReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getContext().unregisterReceiver(mTimeZoneChangedReceiver);
        mColorExtractor.removeOnColorsChangedListener(this);
    }
}
