package com.android.keyguard.clock;

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

public class SfunnyClockController implements ClockPlugin {
    private ClockLayout mBigClockView;
    private final SysuiColorExtractor mColorExtractor;
    private float mDarkAmount;
    private TextClock mHourClock;
    private final LayoutInflater mLayoutInflater;
    private TextClock mMinuteClock;
    private final ViewPreviewer mRenderer = new ViewPreviewer();
    private final Resources mResources;
    private ClockLayout mView;

    
    public String getName() {
        return "Sony";
    }

    
    public String getTitle() {
        return "Sony";
    }

    
    public void setColorPalette(boolean z, int[] iArr) {
    }

    
    public void setStyle(Paint.Style style) {
    }

    
    public boolean shouldShowStatusArea() {
        return true;
    }

    
    public boolean usesPreferredY() {
        return true;
    }

    
    public Bitmap getThumbnail() {
        return BitmapFactory.decodeResource(mResources, R.drawable.default_thumbnail);
    }

    public SfunnyClockController(Resources resources, LayoutInflater layoutInflater, SysuiColorExtractor sysuiColorExtractor) {
        mResources = resources;
        mLayoutInflater = layoutInflater;
        mColorExtractor = sysuiColorExtractor;
    }

    private void createViews() {
        mView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_sfuny, (ViewGroup) null);
        mBigClockView = (ClockLayout) mLayoutInflater.inflate(R.layout.digital_clock_sfuny_big, (ViewGroup) null);
        mHourClock = (TextClock) mView.findViewById(R.id.clockHour);
        mMinuteClock = (TextClock) mView.findViewById(R.id.clockMinute);
    }

    
    public void onDestroyView() {
        mView = null;
        mHourClock = null;
        mMinuteClock = null;
    }

    
    public Bitmap getPreview(int width, int height) {
        View inflate = mLayoutInflater.inflate(R.layout.digital_sfuny_preview, (ViewGroup) null);
        ((TextClock) inflate.findViewById(R.id.clockHour)).setTextColor(-1);
        ((TextClock) inflate.findViewById(R.id.clockMinute)).setTextColor(-1);
        ((TextClock) inflate.findViewById(R.id.date)).setTextColor(-1);
        ColorExtractor.GradientColors colors = mColorExtractor.getColors(2);
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
        return mBigClockView;
    }

    
    public int getPreferredY(int totalheight) {
        return totalheight / 4;
    }

    
    public void setTextColor(int color) {
        TextClock textClock = mHourClock;
        int i2 = -1;
        textClock.setTextColor(mDarkAmount < 0.5f ? Utils.getColorAttrDefaultColor(textClock.getContext(), R.attr.wallpaperTextColorAccent) : -1);
        TextClock textClock2 = mMinuteClock;
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
        TextClock textClock = mHourClock;
        int i = (darkAmount > 0.5f ? 1 : (darkAmount == 0.5f ? 0 : -1));
        int i2 = -1;
        textClock.setTextColor(i < 0 ? Utils.getColorAttrDefaultColor(textClock.getContext(), R.attr.wallpaperTextColorAccent) : -1);
        TextClock textClock2 = mMinuteClock;
        if (i < 0) {
            i2 = Utils.getColorAttrDefaultColor(textClock2.getContext(), R.attr.wallpaperTextColorAccent);
        }
        textClock2.setTextColor(i2);
    }

    
    public void onTimeZoneChanged(TimeZone timeZone) {
        onTimeTick();
    }
}
