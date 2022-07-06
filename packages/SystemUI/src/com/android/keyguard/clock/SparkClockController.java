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

public class SparkClockController implements ClockPlugin {
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
        return "Spark";
    }

    
    public String getTitle() {
        return "Spark";
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

    
    public SparkClockController(Resources resources, LayoutInflater layoutInflater, SysuiColorExtractor sysuiColorExtractor) {
        mResources = resources;
        mLayoutInflater = layoutInflater;
        mColorExtractor = sysuiColorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_sparklsclock1, (ViewGroup) null);
        mBigClockView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_sparklsclock1_big, (ViewGroup) null);
        mDateClock = (TextClock) mView.findViewById(R.id.date);
        mTimeClock1 = (TextClock) mView.findViewById(R.id.timeclock1);
        mTimeClock2 = (TextClock) mView.findViewById(R.id.timeclock2);
        mTimeClock3 = (TextClock) mView.findViewById(R.id.timeclock3);

    }

    
    public void onDestroyView() {
        mView = null;
        mTimeClock1 = null;
        mTimeClock2 = null;
        mTimeClock3 = null;
        mDateClock = null;
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
        return totalheight / 6;
    }

    
    public void setTextColor(int color) {
        TextClock textClock = mTimeClock1;
        int i2 = -1;
        textClock.setTextColor(mDarkAmount < 0.5f ? Utils.getColorAttrDefaultColor(textClock.getContext(), R.attr.wallpaperTextColorAccent) : -1);
        TextClock textClock2 = mDateClock;
        if (mDarkAmount < 0.5f) {
            i2 = Utils.getColorAttrDefaultColor(textClock2.getContext(), R.attr.wallpaperTextColorAccent);
        }
        textClock2.setTextColor(i2);
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
        TextClock textClock = mTimeClock1;
        int i = (darkAmount > 0.5f ? 1 : (darkAmount == 0.5f ? 0 : -1));
        int i2 = -1;
        textClock.setTextColor(i < 0 ? Utils.getColorAttrDefaultColor(textClock.getContext(), R.attr.wallpaperTextColorAccent) : -1);
        TextClock textClock2 = mDateClock;
        if (i < 0) {
            i2 = Utils.getColorAttrDefaultColor(textClock2.getContext(), R.attr.wallpaperTextColorAccent);
        }
        textClock2.setTextColor(i2);
    }

    
    public void onTimeZoneChanged(TimeZone timeZone) {
        onTimeTick();
    }
}
