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

package com.android.settingslib.fuelgauge;

import java.util.Locale;

public final class BatteryInfoFormatter {

    private BatteryInfoFormatter() {}

    public static String formatCurrent(long milliAmps) {
        long abs = Math.abs(milliAmps);
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1f A", abs / 1_000.0);
        }
        return abs + " mA";
    }

    public static String formatPower(long milliAmps, int milliVolts) {
        double watts = (Math.abs(milliAmps) / 1_000.0) * (milliVolts / 1_000.0);
        return String.format(Locale.ROOT, "%.1f W", watts);
    }

    public static String formatTemp(int tenthsOfCelsius) {
        return String.format(Locale.ROOT, "%.1f\u00b0", tenthsOfCelsius / 10.0);
    }

    public static String formatDuration(long ms) {
        long secs  = ms / 1_000;
        long hours = secs / 3600;
        long mins  = (secs % 3600) / 60;
        long s     = secs % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %dm %ds", hours, mins, s);
        if (mins  > 0) return String.format(Locale.ROOT, "%dm %ds", mins, s);
        return s + "s";
    }

    public static String formatDischargeRate(int dischargePercent, long periodMs) {
        if (periodMs <= 0) return "\u2013";
        double rph = dischargePercent / (periodMs / 3_600_000.0);
        return String.format(Locale.ROOT, "%.1f%%/h", rph);
    }

    public static String formatDischargeRatePct(long dischargeMah, long periodMs,
            double capacityMah) {
        if (periodMs <= 0 || capacityMah <= 0 || dischargeMah <= 0) return "\u2013";
        double hours = periodMs / 3_600_000.0;
        double pctPerH = (dischargeMah / capacityMah) * 100.0 / hours;
        return String.format(Locale.ROOT, "%.1f%%/h", pctPerH);
    }

    public static String formatMah(long mah) {
        return mah + " mAh";
    }

    public static String formatPercent(int percent) {
        return percent + "%";
    }
}
