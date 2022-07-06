package com.android.keyguard.clock;

import android.content.res.Resources;
import android.graphics.Bitmap;
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
import android.graphics.BitmapFactory;

public class OOSClockController implements ClockPlugin {
    private ClockLayout mBigView;
    private int mColor;
    private final SysuiColorExtractor mColorExtractor;
    private TextClock mDate;
    private TextClock mDay;
    private final LayoutInflater mLayoutInflater;
    private final ViewPreviewer mRenderer = new ViewPreviewer();
    private final Resources mResources;
    private TextClock mTimeClock;
    private TextClock mTimeClockAccented;
    private ClockLayout mView;

    
    public String getName() {
        return "oos";
    }

    
    public String getTitle() {
        return "OxygenOS";
    }

    
    public void onTimeZoneChanged(TimeZone timeZone) {
    }

    
    public boolean usesPreferredY() {
        return false;
    }

    
    public void setStyle(Paint.Style style) {
    }

    
    public boolean shouldShowStatusArea() {
        return false;
    }

    public OOSClockController(Resources resources, LayoutInflater layoutInflater, SysuiColorExtractor sysuiColorExtractor) {
        mResources = resources;
        mLayoutInflater = layoutInflater;
        mColorExtractor = sysuiColorExtractor;
    }

    private void createViews() {
        mBigView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_oos_big, (ViewGroup) null);
        ClockLayout clockLayout = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_oos, (ViewGroup) null);
        mView = clockLayout;
        setViews(clockLayout);
    }

    private void setViews(View view) {
        mTimeClock = (TextClock) view.findViewById(R.id.time_clock);
        mTimeClockAccented = (TextClock) view.findViewById(R.id.time_clock_accented);
        mDay = (TextClock) view.findViewById(R.id.clock_day);
        mDate = (TextClock) view.findViewById(R.id.timedate);
    }

    
    public void onDestroyView() {
        mView = null;
        mTimeClock = null;
        mDay = null;
        mDate = null;
        mTimeClockAccented = null;
    }


    
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_thumbnail);
    }

    
    public Bitmap getPreview(int width, int height) {

        View inflate = mLayoutInflater.inflate(R.layout.digital_clock_oos_preview, (ViewGroup) null);
        setViews(inflate);
        ColorExtractor.GradientColors colors = this.mColorExtractor.getColors(2);
        setColorPalette(colors.supportsDarkText(), colors.getColorPalette());
        onTimeTick();
        return mRenderer.createPreview(inflate, width, height);
    }

    
    public View getView() {
        if (mView == null) {
            createViews();
        }
        return mView;
    }

    
    public View getBigClockView() {
        return mBigView;
    }

    
    public int getPreferredY(int totalHeight) {
        return totalHeight / 6;
    }

    
    public void setTextColor(int color) {
        mTimeClock.setTextColor(color);
        mDay.setTextColor(color);
        mDate.setTextColor(color);
        mColor = color;
    }

    
    public void setColorPalette(boolean supportsDarkText, int[] colorPalette) {
        if (colorPalette == null || colorPalette.length == 0) {
            return;
        }
        final int accentColor = colorPalette[Math.max(0, colorPalette.length - 5)];
        mTimeClockAccented.setTextColor(accentColor);
    }

    
    public void onTimeTick() {
        ClockLayout clockLayout = mView;
        if (clockLayout != null) {
            clockLayout.onTimeChanged();
        }
        ClockLayout clockLayout2 = mBigView;
        if (clockLayout2 != null) {
            clockLayout2.onTimeChanged();
        }
        mTimeClock.refreshTime();
        mTimeClockAccented.refreshTime();
        mDay.refreshTime();
        mDate.refreshTime();
        setTextColor(mColor);
    }

    
    public void setDarkAmount(float darkAmount) {
        ClockLayout clockLayout = mView;
        if (clockLayout != null) {
            clockLayout.setDarkAmount(darkAmount);
        }
        int i = (darkAmount > 0.5f ? 1 : (darkAmount == 0.5f ? 0 : -1));
        int i2 = -1;
        mTimeClockAccented.setTextColor(i < 0 ? Utils.getColorAttrDefaultColor(mTimeClock.getContext(), R.attr.wallpaperTextColorAccent) : -1);
        if (i < 0) {
            i2 = Utils.getColorAttrDefaultColor(mTimeClock.getContext(), R.attr.wallpaperTextColorAccent);
        }
        mColor = i2;
    }
}
