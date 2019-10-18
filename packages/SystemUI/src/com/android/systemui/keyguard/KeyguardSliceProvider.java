/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.keyguard;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.ZenModeConfig;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.NextAlarmController;
import com.android.systemui.statusbar.policy.NextAlarmControllerImpl;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.statusbar.policy.ZenModeControllerImpl;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.slice.Slice;
import androidx.slice.SliceProvider;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.ListBuilder.RowBuilder;
import androidx.slice.builders.SliceAction;

import com.android.systemui.omni.OmniJawsClient;

/**
 * Simple Slice provider that shows the current date.
 */
public class KeyguardSliceProvider extends SliceProvider implements
        NextAlarmController.NextAlarmChangeCallback, ZenModeController.Callback, OmniJawsClient.OmniJawsObserver {

    private String TAG = KeyguardSliceProvider.class.getSimpleName();
    private static final boolean DEBUG = false;

    public static final String KEYGUARD_SLICE_URI = "content://com.android.systemui.keyguard/main";
    public static final String KEYGUARD_DATE_URI = "content://com.android.systemui.keyguard/date";
    public static final String KEYGUARD_WEATHER_URI = "content://com.android.systemui.keyguard/weather";
    public static final String KEYGUARD_NEXT_ALARM_URI =
            "content://com.android.systemui.keyguard/alarm";
    public static final String KEYGUARD_DND_URI = "content://com.android.systemui.keyguard/dnd";
    public static final String KEYGUARD_ACTION_URI =
            "content://com.android.systemui.keyguard/action";

    /**
     * Only show alarms that will ring within N hours.
     */
    @VisibleForTesting
    static final int ALARM_VISIBILITY_HOURS = 12;

    protected final Uri mSliceUri;
    protected final Uri mDateUri;
    protected final Uri mWeatherUri;
    protected final Uri mAlarmUri;
    protected final Uri mDndUri;
    private final Date mCurrentTime = new Date();
    private final Handler mHandler;
    private final AlarmManager.OnAlarmListener mUpdateNextAlarm = this::updateNextAlarm;
    private ZenModeController mZenModeController;
    private String mDatePattern;
    private DateFormat mDateFormat;
    private String mLastText;
    private boolean mRegistered;
    private String mNextAlarm;
    private int mLsDateSel;
    private String mLsDateSPattern;
    private NextAlarmController mNextAlarmController;
    protected AlarmManager mAlarmManager;
    protected ContentResolver mContentResolver;
    private AlarmManager.AlarmClockInfo mNextAlarmInfo;

    private OmniJawsClient mWeatherClient;
    private OmniJawsClient.WeatherInfo mWeatherInfo;
    private OmniJawsClient.PackageInfo mPackageInfo;
    private boolean mWeatherEnabled;
    private boolean mShowWeatherSlice;

    /**
     * Receiver responsible for time ticking and updating the date format.
     */
    @VisibleForTesting
    final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_TIME_TICK.equals(action)
                    || Intent.ACTION_DATE_CHANGED.equals(action)
                    || Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                    || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                if (Intent.ACTION_LOCALE_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    // need to get a fresh date format
                    mHandler.post(KeyguardSliceProvider.this::cleanDateFormat);
                }
                mHandler.post(KeyguardSliceProvider.this::updateClock);
            }
        }
    };

    public KeyguardSliceProvider() {
        this(new Handler());
    }

    @VisibleForTesting
    KeyguardSliceProvider(Handler handler) {
        mHandler = handler;
        mSliceUri = Uri.parse(KEYGUARD_SLICE_URI);
        mDateUri = Uri.parse(KEYGUARD_DATE_URI);
        mWeatherUri = Uri.parse(KEYGUARD_WEATHER_URI);
        mAlarmUri = Uri.parse(KEYGUARD_NEXT_ALARM_URI);
        mDndUri = Uri.parse(KEYGUARD_DND_URI);
    }

    @Override
    public Slice onBindSlice(Uri sliceUri) {
        ListBuilder builder = new ListBuilder(getContext(), mSliceUri);
        builder.addRow(new RowBuilder(builder, mDateUri).setTitle(mLastText));
        addWeather(builder);
        addNextAlarm(builder);
        addZenMode(builder);
        addPrimaryAction(builder);
        return builder.build();
    }

    protected void addPrimaryAction(ListBuilder builder) {
        // Add simple action because API requires it; Keyguard handles presenting
        // its own slices so this action + icon are actually never used.
        PendingIntent pi = PendingIntent.getActivity(getContext(), 0, new Intent(), 0);
        Icon icon = Icon.createWithResource(getContext(), R.drawable.ic_access_alarms_big);
        SliceAction action = new SliceAction(pi, icon, mLastText);

        RowBuilder primaryActionRow = new RowBuilder(builder, Uri.parse(KEYGUARD_ACTION_URI))
            .setPrimaryAction(action);
        builder.addRow(primaryActionRow);
    }

    protected void addNextAlarm(ListBuilder builder) {
        if (TextUtils.isEmpty(mNextAlarm)) {
            return;
        }

        Icon alarmIcon = Icon.createWithResource(getContext(), R.drawable.ic_access_alarms_big);
        RowBuilder alarmRowBuilder = new RowBuilder(builder, mAlarmUri)
                .setTitle(mNextAlarm)
                .addEndItem(alarmIcon);
        builder.addRow(alarmRowBuilder);
    }

    /**
     * Add zen mode (DND) icon to slice if it's enabled.
     * @param builder The slice builder.
     */
    protected void addZenMode(ListBuilder builder) {
        if (!isDndSuppressingNotifications()) {
            return;
        }
        RowBuilder dndBuilder = new RowBuilder(builder, mDndUri)
                .setContentDescription(getContext().getResources()
                        .getString(R.string.accessibility_quick_settings_dnd))
                .addEndItem(Icon.createWithResource(getContext(), R.drawable.stat_sys_dnd));
        builder.addRow(dndBuilder);
    }

    /**
     * Return true if DND is enabled suppressing notifications.
     */
    protected boolean isDndSuppressingNotifications() {
        boolean suppressingNotifications = (mZenModeController.getConfig().suppressedVisualEffects
                & NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST) != 0;
        return mZenModeController.getZen() != Settings.Global.ZEN_MODE_OFF
                && suppressingNotifications;
    }

    protected void addWeather(ListBuilder builder) {
        if (!mWeatherClient.isOmniJawsEnabled()) return;
        if (!mWeatherEnabled || !mShowWeatherSlice || mWeatherInfo == null || mPackageInfo == null) {
            return;
        }
        String temperatureText = mWeatherInfo.temp + " " + mWeatherInfo.tempUnits;
        Icon conditionIcon = Icon.createWithResource(mPackageInfo.packageName, mPackageInfo.resourceID);
        RowBuilder weatherRowBuilder = new RowBuilder(builder, mWeatherUri)
                .setTitle(temperatureText)
                .addEndItem(conditionIcon);
        builder.addRow(weatherRowBuilder);
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
        mContentResolver.notifyChange(mSliceUri, null /* observer */);
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
    }

    private void queryAndUpdateWeather() {
        try {
            if (DEBUG) Log.d(TAG, "queryAndUpdateWeather.isOmniJawsEnabled " + mWeatherClient.isOmniJawsEnabled());
            mWeatherClient.queryWeather();
            mWeatherInfo = mWeatherClient.getWeatherInfo();
            setPackageInfo();
            if (DEBUG) Log.w(TAG, "queryAndUpdateWeather mPackageName: " + mPackageInfo.packageName);
            if (DEBUG) Log.w(TAG, "queryAndUpdateWeather mDrawableResID: " + mPackageInfo.resourceID);
        } catch(Exception e) {
            // Do nothing
        }
    }

    private void setPackageInfo() {
        mPackageInfo = null;
        if (mWeatherInfo != null){
              Drawable conditionImage = mWeatherClient.getWeatherConditionImage(mWeatherInfo.conditionCode);
              mPackageInfo = mWeatherClient.getPackageInfo();
        }
    }
    private SettingsObserver mSettingsObserver;

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_DATE_FORMAT),
                    false, this, UserHandle.USER_ALL);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED),
                    false, this, UserHandle.USER_ALL);

            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.OMNIJAWS_WEATHER_ICON_PACK),
                    false, this, UserHandle.USER_ALL);

            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.AICP_LOCKSCREEN_WEATHER_STYLE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri.equals(Settings.System.getUriFor(Settings.System.LOCKSCREEN_DATE_FORMAT))) {
                updateDateSkeleton();
                mContentResolver.notifyChange(mSliceUri, null /* observer */);
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED))) {
                updateLockscreenWeather();
                mContentResolver.notifyChange(mSliceUri, null /* observer */);
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.OMNIJAWS_WEATHER_ICON_PACK))) {
                queryAndUpdateWeather();
                mContentResolver.notifyChange(mSliceUri, null /* observer */);
            } else if (uri.equals(Settings.System.getUriFor(Settings.System.AICP_LOCKSCREEN_WEATHER_STYLE))) {
                updateLockscreenWeatherStyle();
                mContentResolver.notifyChange(mSliceUri, null /* observer */);
            }
        }

        public void updateLockscreenWeather() {
            mWeatherEnabled = Settings.System.getIntForUser(mContentResolver, Settings.System.OMNI_LOCKSCREEN_WEATHER_ENABLED, 0, UserHandle.USER_CURRENT) != 0;
        }

        public void updateLockscreenWeatherStyle() {
            mShowWeatherSlice = Settings.System.getIntForUser(mContentResolver, Settings.System.AICP_LOCKSCREEN_WEATHER_STYLE, 0, UserHandle.USER_CURRENT) != 0;
        }

        public void updateDateSkeleton() {
            mLsDateSel = Settings.System.getIntForUser(mContentResolver, Settings.System.LOCKSCREEN_DATE_FORMAT, 0, UserHandle.USER_CURRENT);
            switch (mLsDateSel) {
            case 0: // default
            default:
                mDatePattern = getContext().getString(R.string.system_ui_aod_date_pattern);
                break;
            case 1:
                mDatePattern = getContext().getString(R.string.lockdate_opt_1);
                break;
            case 2:
                mDatePattern = getContext().getString(R.string.lockdate_opt_2);
                break;
            case 3:
                mDatePattern = getContext().getString(R.string.lockdate_opt_3);
                break;
            case 4:
                mDatePattern = getContext().getString(R.string.lockdate_opt_4);
                break;
            case 5:
                mDatePattern = getContext().getString(R.string.lockdate_opt_5);
                break;
            case 6:
                mDatePattern = getContext().getString(R.string.lockdate_opt_6);
                break;
            case 7:
                mDatePattern = getContext().getString(R.string.lockdate_opt_7);
                break;
            case 8:
                mDatePattern = getContext().getString(R.string.lockdate_opt_8);
                break;
            case 9:
                mDatePattern = getContext().getString(R.string.lockdate_opt_9);
                break;
            case 10:
                mDatePattern = getContext().getString(R.string.lockdate_opt_10);
                break;
            case 11:
                mDatePattern = getContext().getString(R.string.lockdate_opt_11);
                break;
            case 12:
                mDatePattern = getContext().getString(R.string.lockdate_opt_12);
                break;
            case 13:
                mDatePattern = getContext().getString(R.string.lockdate_opt_13);
                break;
            case 14:
                mDatePattern = getContext().getString(R.string.lockdate_opt_14);
                break;
            case 15:
                mDatePattern = getContext().getString(R.string.lockdate_opt_15);
                break;
            case 16:
                mDatePattern = getContext().getString(R.string.lockdate_opt_16);
                break;
            case 17:
                mDatePattern = getContext().getString(R.string.lockdate_opt_17);
                break;
            case 18:
                mDatePattern = getContext().getString(R.string.lockdate_opt_18);
                break;
            }
            updateClock();
        }
    }

    @Override
    public boolean onCreateSliceProvider() {
        mAlarmManager = getContext().getSystemService(AlarmManager.class);
        mContentResolver = getContext().getContentResolver();
        mNextAlarmController = new NextAlarmControllerImpl(getContext());
        mNextAlarmController.addCallback(this);
        mZenModeController = new ZenModeControllerImpl(getContext(), mHandler);
        mZenModeController.addCallback(this);
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();
        mSettingsObserver.updateDateSkeleton();
        mSettingsObserver.updateLockscreenWeatherStyle();
        mSettingsObserver.updateLockscreenWeather();
        mWeatherClient = new OmniJawsClient(getContext());
        mWeatherClient.addSettingsObserver();
        mWeatherClient.addObserver(this);
        queryAndUpdateWeather();
        registerClockUpdate();
        updateClock();
        return true;
    }

    @Override
    public void onZenChanged(int zen) {
        mContentResolver.notifyChange(mSliceUri, null /* observer */);
    }

    @Override
    public void onConfigChanged(ZenModeConfig config) {
        mContentResolver.notifyChange(mSliceUri, null /* observer */);
    }

    private void updateNextAlarm() {
        if (withinNHours(mNextAlarmInfo, ALARM_VISIBILITY_HOURS)) {
            String pattern = android.text.format.DateFormat.is24HourFormat(getContext(),
                    ActivityManager.getCurrentUser()) ? "H:mm" : "h:mm";
            mNextAlarm = android.text.format.DateFormat.format(pattern,
                    mNextAlarmInfo.getTriggerTime()).toString();
        } else {
            mNextAlarm = "";
        }
        mContentResolver.notifyChange(mSliceUri, null /* observer */);
    }

    private boolean withinNHours(AlarmManager.AlarmClockInfo alarmClockInfo, int hours) {
        if (alarmClockInfo == null) {
            return false;
        }

        long limit = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hours);
        return mNextAlarmInfo.getTriggerTime() <= limit;
    }

    /**
     * Registers a broadcast receiver for clock updates, include date, time zone and manually
     * changing the date/time via the settings app.
     */
    private void registerClockUpdate() {
        if (mRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        getContext().registerReceiver(mIntentReceiver, filter, null /* permission*/,
                null /* scheduler */);
        mRegistered = true;
    }

    @VisibleForTesting
    boolean isRegistered() {
        return mRegistered;
    }

    protected void updateClock() {
        final String text = getFormattedDate();
        if (!text.equals(mLastText)) {
            mLastText = text;
            mContentResolver.notifyChange(mSliceUri, null /* observer */);
        }
    }

    protected String getFormattedDate() {
        final Locale l = Locale.getDefault();
        DateFormat format = DateFormat.getInstanceForSkeleton(mDatePattern, l);
        format.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        mDateFormat = format;
        mCurrentTime.setTime(System.currentTimeMillis());
        return mDateFormat.format(mCurrentTime);
    }

    @VisibleForTesting
    void cleanDateFormat() {
        mDateFormat = null;
    }

    @Override
    public void onNextAlarmChanged(AlarmManager.AlarmClockInfo nextAlarm) {
        mNextAlarmInfo = nextAlarm;
        mAlarmManager.cancel(mUpdateNextAlarm);

        long triggerAt = mNextAlarmInfo == null ? -1 : mNextAlarmInfo.getTriggerTime()
                - TimeUnit.HOURS.toMillis(ALARM_VISIBILITY_HOURS);
        if (triggerAt > 0) {
            mAlarmManager.setExact(AlarmManager.RTC, triggerAt, "lock_screen_next_alarm",
                    mUpdateNextAlarm, mHandler);
        }
        updateNextAlarm();
    }
}
