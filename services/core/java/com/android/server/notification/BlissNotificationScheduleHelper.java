/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.notification;

import android.app.NotificationManager.Policy;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class BlissNotificationScheduleHelper {

    private static final String KEY_ENABLED = "bliss_notification_schedule_enabled";
    private static final String KEY_CONFIG = "bliss_notification_schedule_config";

    private static final int SUPPRESSED_EFFECTS =
            Policy.SUPPRESSED_EFFECT_SCREEN_OFF
            | Policy.SUPPRESSED_EFFECT_SCREEN_ON
            | Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
            | Policy.SUPPRESSED_EFFECT_LIGHTS
            | Policy.SUPPRESSED_EFFECT_PEEK
            | Policy.SUPPRESSED_EFFECT_AMBIENT;

    private static volatile boolean sEnabled;
    private static volatile int sStartHour;
    private static volatile int sStartMinute;
    private static volatile int sEndHour;
    private static volatile int sEndMinute;
    private static volatile Set<String> sPackages = Collections.emptySet();

    public static void init(Context context, Handler handler) {
        ContentResolver resolver = context.getContentResolver();
        ContentObserver observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                updateConfig(resolver);
            }
        };
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(KEY_ENABLED), false, observer, UserHandle.USER_ALL);
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(KEY_CONFIG), false, observer, UserHandle.USER_ALL);
        updateConfig(resolver);
    }

    public static void applyScheduleLocked(NotificationRecord record) {
        if (!sEnabled) return;
        if (!isWithinSchedule()) return;
        String pkg = record.getSbn().getPackageName();
        if (!sPackages.contains(pkg)) return;
        record.setIntercepted(true);
        record.setSuppressedVisualEffects(
                record.getSuppressedVisualEffects() | SUPPRESSED_EFFECTS);
    }

    private static void updateConfig(ContentResolver resolver) {
        sEnabled = Settings.Secure.getIntForUser(
                resolver, KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        if (!sEnabled) return;
        try {
            String json = Settings.Secure.getStringForUser(
                    resolver, KEY_CONFIG, UserHandle.USER_CURRENT);
            if (json == null) {
                sEnabled = false;
                return;
            }
            JSONObject config = new JSONObject(json);
            sStartHour = config.optInt("startHour", 22);
            sStartMinute = config.optInt("startMinute", 0);
            sEndHour = config.optInt("endHour", 8);
            sEndMinute = config.optInt("endMinute", 0);
            JSONArray pkgArray = config.optJSONArray("packages");
            if (pkgArray == null || pkgArray.length() == 0) {
                sPackages = Collections.emptySet();
                return;
            }
            Set<String> packages = new HashSet<>();
            for (int i = 0; i < pkgArray.length(); i++) {
                packages.add(pkgArray.getString(i));
            }
            sPackages = packages;
        } catch (Exception e) {
            sEnabled = false;
        }
    }

    private static boolean isWithinSchedule() {
        long millis = System.currentTimeMillis();
        int totalSeconds = (int) ((millis / 1000) % 86400);
        int tzOffsetSeconds = java.util.TimeZone.getDefault().getOffset(millis) / 1000;
        int localSeconds = (totalSeconds + tzOffsetSeconds) % 86400;
        if (localSeconds < 0) localSeconds += 86400;
        int currentMinutes = localSeconds / 60;
        int startMinutes = sStartHour * 60 + sStartMinute;
        int endMinutes = sEndHour * 60 + sEndMinute;
        if (startMinutes == endMinutes) return false;
        if (startMinutes > endMinutes) {
            return currentMinutes >= startMinutes || currentMinutes < endMinutes;
        }
        return currentMinutes >= startMinutes && currentMinutes < endMinutes;
    }
}
