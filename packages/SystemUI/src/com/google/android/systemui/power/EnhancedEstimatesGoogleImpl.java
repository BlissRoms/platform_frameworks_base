package com.google.android.systemui.power;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.BatteryStats;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import android.util.KeyValueListParser;
import android.util.Log;
import com.android.internal.os.BatteryStatsHelper;
import com.android.settingslib.fuelgauge.Estimate;
import com.android.settingslib.fuelgauge.EstimateKt;
import com.android.settingslib.utils.PowerUtil;
import com.android.systemui.power.EnhancedEstimates;
import java.time.Duration;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EnhancedEstimatesGoogleImpl implements EnhancedEstimates {
    private Context mContext;
    private final KeyValueListParser mParser = new KeyValueListParser(',');

    BatteryStatsHelper mBatteryStatsHelper;
    UserManager mUserManager;

    @Inject
    public EnhancedEstimatesGoogleImpl(Context context) {
        mContext = context;
        mBatteryStatsHelper = new BatteryStatsHelper(context,
                true /* collectBatteryBroadcast */);
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public boolean isHybridNotificationEnabled() {
        if (getAospEstimate() != null) {
            return true;
        }

        try {
            if (!mContext.getPackageManager().getPackageInfo("com.google.android.apps.turbo", PackageManager.MATCH_DISABLED_COMPONENTS).applicationInfo.enabled) {
                return false;
            }
            updateFlags();
            return mParser.getBoolean("hybrid_enabled", true);
        } catch (PackageManager.NameNotFoundException unused) {
            return false;
        }
    }

    @Override
    public Estimate getEstimate() {
        Builder appendPath = new Builder().scheme("content").authority("com.google.android.apps.turbo.estimated_time_remaining").appendPath("time_remaining").appendPath("id");
        try {
           Cursor query = mContext.getContentResolver().query(appendPath.build(), null, null, null, null);
            if (query != null) {
                if (query.moveToFirst()) {
                    boolean z = true;
                    if (query.getColumnIndex("is_based_on_usage") != -1) {
                        if (query.getInt(query.getColumnIndex("is_based_on_usage")) == 0) {
                            z = false;
                        }
                    }
                    boolean z2 = z;
                    int columnIndex = query.getColumnIndex("average_battery_life");
                    long j = -1;
                    if (columnIndex != -1) {
                        long j2 = query.getLong(columnIndex);
                        if (j2 != -1) {
                            long millis = Duration.ofMinutes(15).toMillis();
                            if (Duration.ofMillis(j2).compareTo(Duration.ofDays(1)) >= 0) {
                                millis = Duration.ofHours(1).toMillis();
                            }
                            j = PowerUtil.roundTimeToNearestThreshold(j2, millis);
                        }
                    }
                    Estimate estimate = new Estimate(query.getLong(query.getColumnIndex("battery_estimate")), z2, j);
                    if (query != null) {
                        query.close();
                    }
                    return estimate;
                }
            }
            if (query == null) {
                return getAospEstimate();
            }
            query.close();
            return getAospEstimate();
        } catch (Exception e) {
            Log.d("EnhancedEstimates", "Something went wrong when getting battery estimate", e);
        }
        return getAospEstimate();
    }

    @Override
    public long getLowWarningThreshold() {
        updateFlags();
        return mParser.getLong("low_threshold", Duration.ofHours(3).toMillis());
    }

    @Override
    public long getSevereWarningThreshold() {
        updateFlags();
        return mParser.getLong("severe_threshold", Duration.ofHours(1).toMillis());
    }

    @Override
    public boolean getLowWarningEnabled() {
        updateFlags();
        return mParser.getBoolean("low_warning_enabled", false);
    }

    protected void updateFlags() {
        try {
            mParser.setString(Settings.Global.getString(mContext.getContentResolver(), "hybrid_sysui_battery_warning_flags"));
        } catch (IllegalArgumentException unused) {
            Log.e("EnhancedEstimates", "Bad hybrid sysui warning flags");
        }
    }

    public Estimate getAospEstimate() {
        try {
            mBatteryStatsHelper.create((Bundle) null);
            mBatteryStatsHelper.clearStats();
            mBatteryStatsHelper.refreshStats(BatteryStats.STATS_SINCE_CHARGED, mUserManager.getUserProfiles());
            BatteryStats stats = mBatteryStatsHelper.getStats();
            if (stats != null) {
                long remaining = stats.computeBatteryTimeRemaining(PowerUtil.convertMsToUs(
                        SystemClock.elapsedRealtime()));
                if (remaining != -1) {
                    return new Estimate(PowerUtil.convertUsToMs(remaining), false,
                            EstimateKt.AVERAGE_TIME_TO_DISCHARGE_UNKNOWN);
                }
            }
            if (stats == null)
                return null;
        } catch (NullPointerException | IndexOutOfBoundsException e) {
            Log.d("EnhancedAospEstimates", "Something went wrong when getting battery estimate", e);
        }
        return null;
    }
}
