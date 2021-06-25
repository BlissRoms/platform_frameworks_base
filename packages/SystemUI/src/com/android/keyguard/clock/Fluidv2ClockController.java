/*
 * Copyright (C) 2019 The Android Open Source Project
 * Copyright (C) 2020 ProjectFluid
 * Copyright (C) 2021 NezukoOS 
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

/**
 * Plugin for the default clock face used only to provide a preview.
 */
public class Fluidv2ClockController implements ClockPlugin {

    /**
     * Resources used to get title and thumbnail.
     */
    private final Resources mResources;

    /**
     * LayoutInflater used to inflate custom clock views.
     */
    private final LayoutInflater mLayoutInflater;

    /**
     * Extracts accent color from wallpaper.
     */
    private final SysuiColorExtractor mColorExtractor;

    /**
     * Renders preview from clock view.
     */
    private final ViewPreviewer mRenderer = new ViewPreviewer();

    /**
     * Root view of clock.
     */
    private ClockLayout mBigClockView;

    /**
     * Text clock in preview view hierarchy.
     */
    private TextClock mTimeClock;
    private TextClock mMinutesClock;
    private TextClock mDay;
    private TextClock mDate;
    private TextClock mYear;

    /**
     * Create a DefaultClockController instance.
     *
     * @param res Resources contains title and thumbnail.
     * @param inflater Inflater used to inflate custom clock views.
     * @param colorExtractor Extracts accent color from wallpaper.
     */
    public Fluidv2ClockController(Resources res, LayoutInflater inflater,
            SysuiColorExtractor colorExtractor) {
        mResources = res;
        mLayoutInflater = inflater;
        mColorExtractor = colorExtractor;
    }

    private void createViews() {
        mBigClockView = (ClockLayout) mLayoutInflater
                .inflate(R.layout.digital_clock_fluidv2, null);
        mTimeClock = mBigClockView.findViewById(R.id.time_clock);
        mMinutesClock = mBigClockView.findViewById(R.id.minutes_clock);
        mDay = mBigClockView.findViewById(R.id.clock_day);
        mDate = mBigClockView.findViewById(R.id.clock_date);
        mYear = mBigClockView.findViewById(R.id.clock_year);
    }

    @Override
    public void onDestroyView() {
        mBigClockView = null;
        mTimeClock = null;
        mMinutesClock = null;
        mDay = null;
        mDate = null;
        mYear = null;
    }

    @Override
    public String getName() {
        return "fluid v2";
    }

    @Override
    public String getTitle() {
        return "Fluid V2";
    }

    @Override
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.fluidv2_thumbnail);
    }

    @Override
    public Bitmap getPreview(int width, int height) {

        View previewView = mLayoutInflater.inflate(R.layout.digital_fluidv2_preview, null);
        TextClock previewTime = previewView.findViewById(R.id.time_clock);
        TextClock previewMinutes = previewView.findViewById(R.id.minutes_clock);
        TextClock previewDay = previewView.findViewById(R.id.clock_day);
        TextClock previewDate = previewView.findViewById(R.id.clock_date);
        TextClock previewYear = previewView.findViewById(R.id.clock_year);

        // Initialize state of plugin before generating preview.
        previewTime.setTextColor(Color.WHITE);
        previewDay.setTextColor(Color.WHITE);
        previewYear.setTextColor(Color.WHITE);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        int[] colorPalette = colors.getColorPalette();
        int accentColor = mResources.getColor(R.color.typeClockAccentColor, null);
        if (colorPalette != null) {
            accentColor = colorPalette[Math.max(0, colorPalette.length - 5)];
        }
        previewMinutes.setTextColor(accentColor);
        previewDate.setTextColor(accentColor);
        onTimeTick();

        return mRenderer.createPreview(previewView, width, height);
    }

    @Override
    public View getView() {
        return null;
    }

    @Override
    public View getBigClockView() {
        if (mBigClockView == null) {
            createViews();
        }
        return mBigClockView;
    }

    @Override
    public int getPreferredY(int totalHeight) {
        return totalHeight / 2;
    }

    @Override
    public void setStyle(Style style) {}

    @Override
    public void setTextColor(int color) {
        mTimeClock.setTextColor(color);
        mDay.setTextColor(color);
        mYear.setTextColor(color);
    }

    @Override
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        if (colorPalette == null || colorPalette.length == 0) {
            return;
        }
        final int accentColor = colorPalette[Math.max(0, colorPalette.length - 5)];
        mMinutesClock.setTextColor(accentColor);
        mDate.setTextColor(accentColor);
    }

    @Override
    public void onTimeTick() {
        mBigClockView.onTimeChanged();
        mTimeClock.refreshTime();
        mMinutesClock.refreshTime();
        mDay.refreshTime();
        mDate.refreshTime();
        mYear.refreshTime();
    }

    @Override
    public void setDarkAmount(float darkAmount) {
        mBigClockView.setDarkAmount(darkAmount);
    }

    @Override
    public void onTimeZoneChanged(TimeZone timeZone) {}

    @Override
    public boolean shouldShowStatusArea() {
        return false;
    }
}
