package com.android.keyguard.clock;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;
import androidx.core.graphics.ColorUtils;

import com.android.internal.colorextraction.ColorExtractor;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import java.util.TimeZone;

public class ClockertinoClockController implements ClockPlugin {
    private final SysuiColorExtractor mColorExtractor;
    private float mDarkAmount;
    private LinearLayout mDateWidgetBase;
    private LinearLayout mDateWidgetBaseBig;
    private final LayoutInflater mLayoutInflater;
    private final ClockPalette mPalette = new ClockPalette();
    private final ViewPreviewer mRenderer = new ViewPreviewer();
    private final Resources mResources;
    private LinearLayout mTimeWidgetBase;
    private LinearLayout mTimeWidgetBaseBig;
    private ViewGroup mTimeWidgetBaseBigClockDateViews;
    private ViewGroup mTimeWidgetBaseBigClockInternalTextViews;
    private ViewGroup mTimeWidgetBaseBigClockTextClocks;
    private ViewGroup mTimeWidgetBaseBigClockTextViews;
    private ViewGroup mTimeWidgetBaseDateViews;
    private ViewGroup mTimeWidgetBaseInternalTextViews;
    private ViewGroup mTimeWidgetBaseTextClocks;
    private ViewGroup mTimeWidgetBaseTextViews;
    private ClockLayout mView;
    private ClockLayout mViewBigClock;

    public String getName() {
        return "clockertino";
    }

    public String getTitle() {
        return "Clockertino";
    }

    public void setStyle(Paint.Style style) {
    }

    public boolean shouldShowStatusArea() {
        return false;
    }

    public boolean usesPreferredY() {
        return true;
    }

    public ClockertinoClockController(Resources resources, LayoutInflater layoutInflater, SysuiColorExtractor sysuiColorExtractor) {
        mResources = resources;
        mLayoutInflater = layoutInflater;
        mColorExtractor = sysuiColorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater.inflate(R.layout.clock_clockertino, (ViewGroup) null);
        mViewBigClock = (ClockLayout) mLayoutInflater.inflate(R.layout.clock_clockertino_big, (ViewGroup) null);
        ClockLayout clockLayout = mView;
        mTimeWidgetBase = mView.findViewById(R.id.timeWidget);
        mDateWidgetBase = mView.findViewById(R.id.dateWidget);
        mDateWidgetBaseBig = mViewBigClock.findViewById(R.id.dateWidget);
        mTimeWidgetBaseBig = mViewBigClock.findViewById(R.id.timeWidget);

    }

    public void onDestroyView() {
        mView = null;
        mTimeWidgetBase = null;
        mDateWidgetBase = null;
    }

    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.clockertino_thumbnail);
    }

    public Bitmap getPreview(int width, int height) {

        View previewView = getView();
        // Initialize state of plugin before generating preview.
        setDarkAmount(1f);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();

        return mRenderer.createPreview(previewView, width, height);
    }

    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    public View getBigClockView() {
        return mViewBigClock;
    }

    public int getPreferredY(int totalHeight) {
        return totalHeight / 5;
    }

    public void setTextColor(int color) {
    }

    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        mPalette.setColorPalette(supportsDarkText, colorPalette);
        updateColor();
    }

    private void updateColor() {
        final int primary = mPalette.getPrimaryColor();

        int[] gradColors = {primary, generateColorDesat(primary)};
        GradientDrawable bgTinted = new GradientDrawable(Orientation.TOP_BOTTOM, gradColors);
        bgTinted.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        mTimeWidgetBase.setBackground(bgTinted);
        mDateWidgetBase.setBackground(bgTinted);
        mTimeWidgetBaseBig.setBackground(bgTinted);
        mDateWidgetBaseBig.setBackground(bgTinted);

    }

    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        mPalette.setDarkAmount(darkAmount);
        updateColor();
    }

    public void onTimeTick() {
        ClockLayout clockLayout = mView;
        if (clockLayout != null) {
            clockLayout.onTimeChanged();
        }
        ClockLayout clockLayout2 = mViewBigClock;
        if (clockLayout2 != null) {
            clockLayout2.onTimeChanged();
        }
    }

    private int generateColorDesat(int color) {
        float[] hslParams = new float[3];
        ColorUtils.colorToHSL(color, hslParams);
        // Conversion to desature the color?
        hslParams[1] = hslParams[1]*0.64f;
        return ColorUtils.HSLToColor(hslParams);
    }

}
