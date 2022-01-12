package com.android.keyguard.clock;

import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;
import com.android.internal.colorextraction.ColorExtractor;
import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;

import java.util.TimeZone;

public class SparkCircleClockController implements ClockPlugin {
    private ClockLayout mBigClockView;
    private final SysuiColorExtractor mColorExtractor;
    private float mDarkAmount;
    private TextClock mDateClock;
    private final LayoutInflater mLayoutInflater;
    private TextClock mTimeClock1;
    private TextClock mTimeClock2;
    private TextClock mTimeClock3;
    private final ViewPreviewer mRenderer = new ViewPreviewer();
    private final Resources mResources;
    private ClockLayout mView;

    
    public String getName() {
        return "SparkCircle";
    }

    
    public String getTitle() {
        return "SparkCircle";
    }

    
    public void setColorPalette(boolean z, int[] iArr) {
    }

    
    public void setStyle(Paint.Style style) {
    }

    
    public boolean shouldShowStatusArea() {
        return false;
    }

    
    public boolean usesPreferredY() {
        return true;
    }

    
    public SparkCircleClockController(Resources resources, LayoutInflater layoutInflater, SysuiColorExtractor sysuiColorExtractor) {
        mResources = resources;
        mLayoutInflater = layoutInflater;
        mColorExtractor = sysuiColorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_SparkCircleClock, (ViewGroup) null);
        mBigClockView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_SparkCircleClock_big, (ViewGroup) null);

    }

    
    public void onDestroyView() {
        mView = null;
    }

    
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_thumbnail);
    }

    public Bitmap getPreview(int width, int height) {

        // Use the big clock view for the preview
        View view = getBigClockView();

        // Initialize state of plugin before generating preview.
        setDarkAmount(1f);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();

        return mRenderer.createPreview(view, width, height);
    }
    
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    
    public View getBigClockView() {
        return mBigClockView;
    }

    
    public int getPreferredY(int totalheight) {
        return totalheight / 4;
    }

    
    public void setTextColor(int color) {
    }

    
    public void onTimeTick() {
        ClockLayout clockLayout = mView;
        if (clockLayout != null) {
            clockLayout.onTimeChanged();
        }
        ClockLayout clockLayout2 = mBigClockView;
        if (clockLayout2 != null) {
            clockLayout2.onTimeChanged();
        }
    }

    
    public void setDarkAmount(float darkAmount) {
        mView.setDarkAmount(darkAmount);
        mDarkAmount = darkAmount;
        int i = (darkAmount > 0.5f ? 1 : (darkAmount == 0.5f ? 0 : -1));
        int i2 = -1;
    }

    
    public void onTimeZoneChanged(TimeZone timeZone) {
        onTimeTick();
    }
}
