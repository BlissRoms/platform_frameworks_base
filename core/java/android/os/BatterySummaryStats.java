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

package android.os;

import android.annotation.NonNull;

/**
 * Since-last-charge battery summary used by privileged battery detail UIs.
 *
 * @hide
 */
public final class BatterySummaryStats implements Parcelable {
    public final long screenOnTimeMs;
    public final long screenOffTimeMs;
    public final long batteryRealtimeMs;
    public final long batteryUptimeMs;
    public final long deepSleepTimeMs;
    public final long screenOffAwakeTimeMs;
    public final int screenOnDischargePercent;
    public final int screenOffDischargePercent;
    public final long screenOffDischargeMah;
    public final long screenOnDischargeMah;
    public final int learnedBatteryCapacityUah;
    public final int estimatedBatteryCapacityMah;

    /** @hide */
    public BatterySummaryStats(long screenOnTimeMs, long screenOffTimeMs,
            long batteryRealtimeMs, long batteryUptimeMs, long deepSleepTimeMs,
            long screenOffAwakeTimeMs, int screenOnDischargePercent,
            int screenOffDischargePercent, long screenOffDischargeMah, long screenOnDischargeMah,
            int learnedBatteryCapacityUah, int estimatedBatteryCapacityMah) {
        this.screenOnTimeMs = screenOnTimeMs;
        this.screenOffTimeMs = screenOffTimeMs;
        this.batteryRealtimeMs = batteryRealtimeMs;
        this.batteryUptimeMs = batteryUptimeMs;
        this.deepSleepTimeMs = deepSleepTimeMs;
        this.screenOffAwakeTimeMs = screenOffAwakeTimeMs;
        this.screenOnDischargePercent = screenOnDischargePercent;
        this.screenOffDischargePercent = screenOffDischargePercent;
        this.screenOffDischargeMah = screenOffDischargeMah;
        this.screenOnDischargeMah = screenOnDischargeMah;
        this.learnedBatteryCapacityUah = learnedBatteryCapacityUah;
        this.estimatedBatteryCapacityMah = estimatedBatteryCapacityMah;
    }

    private BatterySummaryStats(@NonNull Parcel in) {
        screenOnTimeMs = in.readLong();
        screenOffTimeMs = in.readLong();
        batteryRealtimeMs = in.readLong();
        batteryUptimeMs = in.readLong();
        deepSleepTimeMs = in.readLong();
        screenOffAwakeTimeMs = in.readLong();
        screenOnDischargePercent = in.readInt();
        screenOffDischargePercent = in.readInt();
        screenOffDischargeMah = in.readLong();
        screenOnDischargeMah = in.readLong();
        learnedBatteryCapacityUah = in.readInt();
        estimatedBatteryCapacityMah = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeLong(screenOnTimeMs);
        out.writeLong(screenOffTimeMs);
        out.writeLong(batteryRealtimeMs);
        out.writeLong(batteryUptimeMs);
        out.writeLong(deepSleepTimeMs);
        out.writeLong(screenOffAwakeTimeMs);
        out.writeInt(screenOnDischargePercent);
        out.writeInt(screenOffDischargePercent);
        out.writeLong(screenOffDischargeMah);
        out.writeLong(screenOnDischargeMah);
        out.writeInt(learnedBatteryCapacityUah);
        out.writeInt(estimatedBatteryCapacityMah);
    }

    @NonNull
    public static final Creator<BatterySummaryStats> CREATOR =
            new Creator<BatterySummaryStats>() {
                @Override
                public BatterySummaryStats createFromParcel(Parcel in) {
                    return new BatterySummaryStats(in);
                }

                @Override
                public BatterySummaryStats[] newArray(int size) {
                    return new BatterySummaryStats[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }
}
