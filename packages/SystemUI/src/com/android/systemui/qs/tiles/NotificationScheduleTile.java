/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles;

import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.text.format.DateFormat;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import org.json.JSONObject;

import java.util.Calendar;

import javax.inject.Inject;

public class NotificationScheduleTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "notification_schedule";

    private static final String KEY_ENABLED = "bliss_notification_schedule_enabled";
    private static final String KEY_CONFIG = "bliss_notification_schedule_config";

    @Nullable
    private Icon mIcon = null;

    private final ContentObserver mObserver;

    @Inject
    public NotificationScheduleTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                refreshState();
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(KEY_ENABLED), false, mObserver, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(KEY_CONFIG), false, mObserver, UserHandle.USER_ALL);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                KEY_ENABLED, enabled ? 0 : 1, UserHandle.USER_CURRENT);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.settings",
                "org.blissroms.blissify.BlissifySettingsActivity");
        intent.putExtra(":settings:show_fragment",
                "org.blissroms.blissify.fragments.notifications.NotificationSchedule");
        return intent;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_notification_schedule_label);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mContext.getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mIcon == null) {
            mIcon = maybeLoadResourceIcon(R.drawable.ic_qs_notification_schedule);
        }

        boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                KEY_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        String config = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                KEY_CONFIG, UserHandle.USER_CURRENT);
        boolean hasConfig = config != null;

        state.value = enabled;
        state.icon = mIcon;
        state.label = mContext.getString(R.string.quick_settings_notification_schedule_label);

        if (enabled && !hasConfig) {
            state.state = Tile.STATE_INACTIVE;
            state.secondaryLabel = mContext.getString(R.string.quick_settings_notification_schedule_not_configured);
        } else if (enabled) {
            state.state = Tile.STATE_ACTIVE;
            state.secondaryLabel = getScheduleSummary();
        } else {
            state.state = Tile.STATE_INACTIVE;
            state.secondaryLabel = null;
        }
        state.contentDescription = state.label;
    }

    @Nullable
    private String getScheduleSummary() {
        try {
            String json = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                    KEY_CONFIG, UserHandle.USER_CURRENT);
            if (json == null) return null;
            JSONObject config = new JSONObject(json);
            int startHour = config.optInt("startHour", 22);
            int startMinute = config.optInt("startMinute", 0);
            int endHour = config.optInt("endHour", 8);
            int endMinute = config.optInt("endMinute", 0);
            return formatTime(startHour, startMinute) + " – " + formatTime(endHour, endMinute);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        return DateFormat.getTimeFormat(mContext).format(cal.getTime());
    }

    @Override
    public int getMetricsCategory() {
        return com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;
    }
}
