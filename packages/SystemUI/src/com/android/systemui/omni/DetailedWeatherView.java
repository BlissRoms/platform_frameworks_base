/*
* Copyright (C) 2017 The OmniROM Project
* Copyright (C) 2019 The RevengeOS Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.omni;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.omni.OmniJawsClient;
import com.android.systemui.statusbar.phone.SettingsButton;
import com.android.systemui.plugins.ActivityStarter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DetailedWeatherView extends FrameLayout {

    static final String TAG = "DetailedWeatherView";
    static final boolean DEBUG = false;

    private LinearLayout weatheritemview;
    private LinearLayout weatherforecastitemcontainer;
    private Drawable forecastdrawable;
    private String dayShort;
    private String lowtemperature;
    private String hightemperature;
    private int forecastimagesize;
    private TextView mWeatherCity;
    private TextView mWeatherTimestamp;
    private TextView mWeatherData;
    private ActivityStarter mActivityStarter;
    private OmniJawsClient mWeatherClient;

    private View mProgressContainer;
    private TextView mStatusMsg;
    private View mEmptyView;
    private ImageView mEmptyViewImage;
    private View mWeatherLine;
    private TextView mProviderName;

    public DetailedWeatherView(Context context) {
        this(context, null);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setWeatherClient(OmniJawsClient client) {
        mWeatherClient = client;
    }

    public void setActivityStarter(ActivityStarter activityStarter) {
        mActivityStarter = activityStarter;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mProgressContainer = findViewById(R.id.progress_container);
        mWeatherCity  = (TextView) findViewById(R.id.current_weather_city);
        mWeatherTimestamp  = (TextView) findViewById(R.id.current_weather_timestamp);
        mWeatherData  = (TextView) findViewById(R.id.current_weather_data);
        mStatusMsg = (TextView) findViewById(R.id.status_msg);
        mEmptyView = findViewById(android.R.id.empty);
        mEmptyViewImage = (ImageView) findViewById(R.id.empty_weather_image);
        mWeatherLine = findViewById(R.id.current_weather);
        mProviderName = (TextView) findViewById(R.id.current_weather_provider);
        weatherforecastitemcontainer = findViewById(R.id.weather_forecast_items);
        forecastimagesize = getResources().getDimensionPixelSize(R.dimen.ForecastImageSize);

        mEmptyViewImage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mWeatherClient.isOmniJawsEnabled()) {
                    startProgress();
                    forceRefreshWeatherSettings();
                }
                return true;
            }
        });

        mWeatherLine.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mWeatherClient.isOmniJawsEnabled()) {
                    startProgress();
                    forceRefreshWeatherSettings();
                }
                return true;
            }
        });
    }

    public void updateWeatherData(OmniJawsClient.WeatherInfo weatherData) {
        if (DEBUG) Log.d(TAG, "updateWeatherData");
        weatherforecastitemcontainer.removeAllViews();
        mProgressContainer.setVisibility(View.GONE);

        if (weatherData == null || !mWeatherClient.isOmniJawsEnabled()) {
            setErrorView();
            if (mWeatherClient.isOmniJawsEnabled()) {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
                mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_unkown));
            } else {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
                mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_disabled));
            }
            return;
        }
        mEmptyView.setVisibility(View.GONE);
        mWeatherLine.setVisibility(View.VISIBLE);
        weatherforecastitemcontainer.setVisibility(View.VISIBLE);
        mWeatherCity.setText(weatherData.city);
        mProviderName.setText(weatherData.provider);
        mWeatherData.setText(weatherData.windSpeed + " " + weatherData.windUnits + " " + weatherData.pinWheel +" - " + weatherData.humidity);

        Long timeStamp = weatherData.timeStamp;
        String format = DateFormat.is24HourFormat(mContext) ? "HH:mm" : "hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        mWeatherTimestamp.setText(getResources().getString(R.string.omnijaws_service_last_update) + " " + sdf.format(timeStamp));
        for (int i= -1; i<5; i++) {
            View forecastitem = LayoutInflater.from(mContext).inflate(R.layout.detailed_weather_view_item, null);
            weatheritemview = forecastitem.findViewById(R.id.weather_item);
            LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
                1.0f
            );
            weatheritemview.setLayoutParams(param);

            if (i<0) {
                forecastdrawable = mWeatherClient.getWeatherConditionImage(weatherData.conditionCode);
                lowtemperature = weatherData.temp;
                hightemperature = null;
                dayShort = getResources().getString(R.string.omnijaws_current_text);
            } else {
                forecastdrawable = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(i).conditionCode);
                lowtemperature = weatherData.forecasts.get(i).low;
                hightemperature = weatherData.forecasts.get(i).high;

                sdf = new SimpleDateFormat("EE");
                Calendar cal = Calendar.getInstance();
                if (i != 0) {
                    cal.add(Calendar.DATE, i);
                }
                dayShort = sdf.format(new Date(cal.getTimeInMillis()));
            }

            ImageView ForecastImage = new ImageView(mContext);
            ForecastImage.setLayoutParams(new LayoutParams(forecastimagesize, forecastimagesize));
            ForecastImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            ForecastImage.setImageDrawable(forecastdrawable);

            weatheritemview.addView(ForecastImage);

            TextView temperaturetextview = new TextView(mContext);
            temperaturetextview.setTextAppearance(mContext, R.style.WeatherForecastTemperature);
            temperaturetextview.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams temperatureparams = new LayoutParams(
                LayoutParams.WRAP_CONTENT,      
                LayoutParams.WRAP_CONTENT
            );
            temperatureparams.setMargins(0, getResources().getDimensionPixelSize(R.dimen.TemperatureTextMarginTop), 0, 0);
            temperaturetextview.setLayoutParams(temperatureparams);
            String str = null;
            if (hightemperature != null) {
                str = lowtemperature + "/" + hightemperature + weatherData.tempUnits;
            } else {
                str = lowtemperature + weatherData.tempUnits;
            }
            temperaturetextview.setText(str);

            weatheritemview.addView(temperaturetextview);

            TextView forecastdayview = new TextView(mContext);
            forecastdayview.setTextAppearance(mContext, R.style.WeatherDayViewText);
            forecastdayview.setGravity(Gravity.CENTER_HORIZONTAL);
            forecastdayview.setText(dayShort);

            weatheritemview.addView(forecastdayview);
            weatherforecastitemcontainer.addView(weatheritemview);
        }
    }

    private Drawable applyTint(Drawable icon) {
        icon = icon.mutate();
        icon.setTint(getTintColor());
        return icon;
    }

    private int getTintColor() {
        TypedArray array = mContext.obtainStyledAttributes(new int[]{android.R.attr.colorControlNormal});
        int color = array.getColor(0, 0);
        array.recycle();
        return color;
    }

    private void forceRefreshWeatherSettings() {
        mWeatherClient.updateWeather();
    }

    private void setErrorView() {
        weatherforecastitemcontainer.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.VISIBLE);
        mWeatherLine.setVisibility(View.GONE);
    }

    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mProgressContainer.setVisibility(View.GONE);
        setErrorView();

        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_disabled));
        } else {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_error_long));
        }
    }

    public void startProgress() {
        weatherforecastitemcontainer.setVisibility(View.GONE);
        mEmptyView.setVisibility(View.GONE);
        mWeatherLine.setVisibility(View.GONE);
        mProgressContainer.setVisibility(View.VISIBLE);
    }

    public void stopProgress() {
        mProgressContainer.setVisibility(View.GONE);
    }
}
