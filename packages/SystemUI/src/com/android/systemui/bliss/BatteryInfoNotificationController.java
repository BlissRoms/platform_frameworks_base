/*
 * Copyright (C) 2026 VoltageOS
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

package com.android.systemui.bliss;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.BatterySummaryStats;
import android.os.BatteryStatsManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.settingslib.Utils;
import com.android.settingslib.fuelgauge.BatteryInfoFormatter;
import com.android.systemui.CoreStartable;
import com.android.systemui.res.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.policy.BatteryController;

import javax.inject.Inject;

@SysUISingleton
public class BatteryInfoNotificationController implements CoreStartable {

    private static final String CHANNEL_ID = "battery_info_stats";
    private static final int NOTIF_ID = 0x560000BA;
    private static final long POLL_INTERVAL_MS = 6_000;
    private static final long STATS_THROTTLE_MS = 10_000;
    private static final long MAX_PLAUSIBLE_MA = 30_000L;
    private static final long CURRENT_ROUND_MA = 25L;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int RATE_CURRENT_SAMPLES_MAX = 24;

    private final Context mContext;
    private final Handler mMainHandler;
    private final Handler mBgHandler;
    private final NotificationManager mNotifManager;
    private final BatteryManager mBatteryManager;
    private final BatteryStatsManager mBatteryStatsManager;
    private final PowerManager mPowerManager;
    private final BatteryController mBatteryController;
    private final ContentObserver mSettingsObserver;

    private final int mCurrentSign;
    private final int mCurrentDivisor;

    private volatile int mVoltageMv;
    private volatile int mTemperature;
    private volatile long mCurrentMa;
    private volatile int mLevel = -1;
    private volatile int mStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private volatile int mPlugged = 0;
    private volatile boolean mEnabled;
    private volatile boolean mScreenOn;
    private volatile BatterySummaryStats mCachedStats;
    private volatile String mCachedEstimate = "";
    private volatile String mLastNotifText = "";

    private boolean mReceiverRegistered;
    private long mLastStatsFetch;
    private volatile long mStatsSnapshotElapsed;
    private volatile String mActiveRateText = "\u2013";
    private volatile String mIdleRateText = "\u2013";
    private final java.util.ArrayDeque<CurrentSample> mCurrentSamples = new java.util.ArrayDeque<>();

    private static final class CurrentSample {
        final long t;
        final long mA;
        CurrentSample(long t, long mA) {
            this.t = t;
            this.mA = mA;
        }
    }

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mEnabled || !mScreenOn) return;
            mCurrentMa = readCurrentMa();
            updateActiveRateFromCurrent();
            final long now = SystemClock.uptimeMillis();
            if (now - mLastStatsFetch > STATS_THROTTLE_MS) {
                mLastStatsFetch = now;
                fetchStatsAsync();
            } else {
                refreshNotification();
            }
            mBgHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case Intent.ACTION_BATTERY_CHANGED:
                    mVoltageMv   = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, mVoltageMv);
                    mTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, mTemperature);
                    mLevel       = readLevel(intent);
                    mStatus      = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
                    mPlugged     = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    mBatteryController.getEstimatedTimeRemainingString(est -> {
                        mCachedEstimate = est != null ? est : "";
                        refreshNotification();
                    });
                    final long now = SystemClock.uptimeMillis();
                    if (now - mLastStatsFetch > STATS_THROTTLE_MS) {
                        mLastStatsFetch = now;
                        fetchStatsAsync();
                    }
                    break;
                case Intent.ACTION_SCREEN_ON:
                    mScreenOn = true;
                    mLastStatsFetch = 0;
                    startPolling();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    mScreenOn = false;
                    stopPolling();
                    refreshNotification();
                    break;
            }
        }
    };

    @Inject
    public BatteryInfoNotificationController(
            Context context,
            @Main Handler mainHandler,
            @Background Handler bgHandler,
            BatteryController batteryController,
            PowerManager powerManager) {
        mContext = context;
        mMainHandler = mainHandler;
        mBgHandler = bgHandler;
        mBatteryController = batteryController;
        mPowerManager = powerManager;
        mNotifManager = context.getSystemService(NotificationManager.class);
        mBatteryManager = context.getSystemService(BatteryManager.class);
        mBatteryStatsManager = context.getSystemService(BatteryStatsManager.class);
        mCurrentSign    = context.getResources().getInteger(R.integer.config_batteryCurrentNowSign);
        mCurrentDivisor = context.getResources().getInteger(R.integer.config_currentInfoDivider);
        mSettingsObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                final boolean enabled = isEnabledInSettings();
                if (enabled == mEnabled) return;
                mEnabled = enabled;
                if (mEnabled) enable(); else disable();
            }
        };
    }

    @Override
    public void start() {
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.BATTERY_INFO_NOTIFICATION),
                false, mSettingsObserver, UserHandle.USER_ALL);
        mEnabled = isEnabledInSettings();
        if (mEnabled) enable();
    }

    private void enable() {
        if (mReceiverRegistered) return;
        createNotificationChannel();
        mScreenOn = mPowerManager.isInteractive();
        final Intent sticky = mContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            mVoltageMv   = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            mTemperature = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            mLevel       = readLevel(sticky);
            mStatus      = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            mPlugged     = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mReceiverRegistered = true;
        mLastStatsFetch = 0;
        startPolling();
        fetchStatsAsync();
    }

    private void disable() {
        stopPolling();
        if (mReceiverRegistered) {
            mContext.unregisterReceiver(mReceiver);
            mReceiverRegistered = false;
        }
        mBgHandler.post(() -> {
            mNotifManager.cancel(NOTIF_ID);
            mLastNotifText = "";
        });
    }

    private void startPolling() {
        mBgHandler.removeCallbacks(mPollRunnable);
        if (mEnabled && mScreenOn) mBgHandler.post(mPollRunnable);
    }

    private void stopPolling() {
        mBgHandler.removeCallbacks(mPollRunnable);
    }

    private void fetchStatsAsync() {
        mBgHandler.post(() -> {
            BatterySummaryStats stats = null;
            try {
                stats = mBatteryStatsManager.getBatterySummaryStats();
            } catch (Exception ignored) {}
            if (stats != null) {
                mCachedStats = stats;
                mStatsSnapshotElapsed = SystemClock.elapsedRealtime();
                updateWindowedRates(stats);
            }
            refreshNotification();
        });
    }

    private void updateWindowedRates(BatterySummaryStats s) {
        final double capMah = batteryCapacityMah(s);
        if (capMah <= 0 || s.screenOffDischargeMah <= 0 || s.screenOffTimeMs <= 0) {
            return;
        }
        mIdleRateText = BatteryInfoFormatter.formatDischargeRatePct(
                s.screenOffDischargeMah, s.screenOffTimeMs, capMah);
    }

    private void updateActiveRateFromCurrent() {
        if (mPlugged != 0) {
            mCurrentSamples.clear();
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        mCurrentSamples.addLast(new CurrentSample(now, Math.abs(mCurrentMa)));
        while (mCurrentSamples.size() > RATE_CURRENT_SAMPLES_MAX
                || (!mCurrentSamples.isEmpty()
                        && now - mCurrentSamples.peekFirst().t > RATE_WINDOW_MS)) {
            mCurrentSamples.removeFirst();
        }
        final BatterySummaryStats s = mCachedStats;
        final double capMah = s != null ? batteryCapacityMah(s) : 0;
        if (capMah <= 0) return;
        long sum = 0;
        int n = 0;
        for (CurrentSample c : mCurrentSamples) { sum += c.mA; n++; }
        if (n == 0) return;
        final double avgMa = (double) sum / n;
        if (avgMa <= 0) return;
        final double pctPerH = (avgMa / capMah) * 100.0;
        mActiveRateText = String.format(java.util.Locale.ROOT, "%.1f%%/h", pctPerH);
    }

    private double batteryCapacityMah(BatterySummaryStats s) {
        final long totalMah = s.screenOnDischargeMah + s.screenOffDischargeMah;
        final int totalPct = s.screenOnDischargePercent + s.screenOffDischargePercent;
        if (totalMah > 0 && totalPct > 0) {
            return totalMah / (totalPct / 100.0);
        }
        if (mBatteryManager != null && mLevel > 0) {
            final long chargeUah = mBatteryManager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (chargeUah > 0) {
                return (chargeUah / 1000.0) / (mLevel / 100.0);
            }
        }
        if (s.learnedBatteryCapacityUah > 0) {
            return s.learnedBatteryCapacityUah / 1000.0;
        }
        return s.estimatedBatteryCapacityMah;
    }

    private long liveDuration(long snapshotMs, boolean screenOnBucket) {
        if (mStatsSnapshotElapsed <= 0 || screenOnBucket != mScreenOn) return snapshotMs;
        final long delta = SystemClock.elapsedRealtime() - mStatsSnapshotElapsed;
        if (delta <= 0 || delta > 5 * STATS_THROTTLE_MS) return snapshotMs;
        return snapshotMs + delta;
    }

    private void createNotificationChannel() {
        final NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                mContext.getString(R.string.battery_info_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableLights(false);
        ch.enableVibration(false);
        mNotifManager.createNotificationChannel(ch);
    }

    private void refreshNotification() {
        mBgHandler.post(this::doRefreshNotification);
    }

    private void doRefreshNotification() {
        if (!mEnabled) return;
        final String nowLine = buildNowLine();
        final String body = buildNotifText();
        final boolean hasBody = !body.isEmpty();
        final String dedupe = hasBody ? nowLine + "\n" + body : nowLine;
        if (dedupe.equals(mLastNotifText)) return;
        mLastNotifText = dedupe;
        final int level = mLevel;
        String collapsed = null;
        if (hasBody) {
            final int nl = body.indexOf('\n');
            collapsed = nl < 0 ? body : body.substring(0, nl);
        }
        final Notification.Builder builder = new Notification.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_battery_info)
                .setContentTitle(nowLine)
                .setContentText(collapsed)
                .setSubText(level >= 0 ? BatteryInfoFormatter.formatPercent(level) : null)
                .setColor(Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorAccent))
                .setColorized(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (hasBody) {
            builder.setStyle(new Notification.BigTextStyle().bigText(body));
        }
        mNotifManager.notify(NOTIF_ID, builder.build());
    }

    private String buildNowLine() {
        String nowLine = BatteryInfoFormatter.formatCurrent(mCurrentMa)
                + "  \u00b7  " + BatteryInfoFormatter.formatTemp(mTemperature);
        final String status = statusLabel();
        if (!status.isEmpty()) {
            nowLine = status + "  \u00b7  " + nowLine;
        }
        final String estimate = mCachedEstimate;
        if (!estimate.isEmpty()) {
            nowLine += " \u00b7 " + estimate;
        }
        return nowLine;
    }

    private String statusLabel() {
        if (mPlugged == 0) {
            return "Discharging";
        }
        if (mStatus == BatteryManager.BATTERY_STATUS_FULL) {
            return "Full";
        }
        return "Charging";
    }

    private String buildNotifText() {
        final StringBuilder sb = new StringBuilder();
        final BatterySummaryStats s = mCachedStats;
        if (s != null) {
            final long liveScreenOn = liveDuration(s.screenOnTimeMs, true);
            final long liveScreenOff = liveDuration(s.screenOffTimeMs, false);
            sb.append(mContext.getString(R.string.battery_info_notif_rates,
                    mActiveRateText, mIdleRateText));
            sb.append('\n');
            sb.append(mContext.getString(R.string.battery_info_notif_screen_on,
                    BatteryInfoFormatter.formatDuration(liveScreenOn),
                    BatteryInfoFormatter.formatPercent(s.screenOnDischargePercent),
                    BatteryInfoFormatter.formatMah(s.screenOnDischargeMah)));
            sb.append('\n');
            sb.append(mContext.getString(R.string.battery_info_notif_screen_off,
                    BatteryInfoFormatter.formatDuration(liveScreenOff),
                    BatteryInfoFormatter.formatPercent(s.screenOffDischargePercent),
                    BatteryInfoFormatter.formatMah(s.screenOffDischargeMah)));
            sb.append('\n');
            sb.append(mContext.getString(R.string.battery_info_notif_deep_sleep,
                    BatteryInfoFormatter.formatDuration(s.deepSleepTimeMs),
                    screenOffFraction(s.deepSleepTimeMs, liveScreenOff)));
            sb.append('\n');
            sb.append(mContext.getString(R.string.battery_info_notif_awake,
                    BatteryInfoFormatter.formatDuration(s.screenOffAwakeTimeMs),
                    screenOffFraction(s.screenOffAwakeTimeMs, liveScreenOff)));
        }
        return sb.toString().trim();
    }

    private long readCurrentMa() {
        if (mBatteryManager == null) return mCurrentMa;
        final long raw = mBatteryManager.getLongProperty(
                BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        if (raw == Long.MIN_VALUE || raw == 0) return mCurrentMa;
        final long divisor = mCurrentDivisor != 0 ? mCurrentDivisor : 1;
        final long mA = (raw * mCurrentSign) / divisor;
        if (Math.abs(mA) > MAX_PLAUSIBLE_MA) return mCurrentMa;
        final long rounded = Math.round((double) mA / CURRENT_ROUND_MA) * CURRENT_ROUND_MA;
        return rounded == 0 ? mCurrentMa : rounded;
    }

    private int readLevel(Intent intent) {
        if (intent == null) return mLevel;
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return mLevel;
        return Math.round(level * 100f / scale);
    }

    private static String screenOffFraction(long partMs, long totalMs) {
        if (totalMs <= 0) return "0%";
        return Math.round(100.0 * partMs / totalMs) + "%";
    }

    private boolean isEnabledInSettings() {
        return Settings.System.getIntForUser(
                mContext.getContentResolver(),
                Settings.System.BATTERY_INFO_NOTIFICATION,
                0, UserHandle.USER_CURRENT) == 1;
    }
}
