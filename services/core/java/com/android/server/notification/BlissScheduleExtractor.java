/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.notification;

import android.content.Context;

public class BlissScheduleExtractor implements NotificationSignalExtractor {

    public void initialize(Context ctx, NotificationUsageStats usageStats) {
    }

    public RankingReconsideration process(NotificationRecord record) {
        if (record == null || record.getNotification() == null) return null;
        BlissNotificationScheduleHelper.applyScheduleLocked(record);
        return null;
    }

    @Override
    public void setConfig(RankingConfig config) {
    }

    @Override
    public void setZenHelper(ZenModeHelper helper) {
    }
}
