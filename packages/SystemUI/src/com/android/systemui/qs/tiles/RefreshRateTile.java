/*
 * Copyright (C) 2020 The Android Open Source Project
 *               2021 AOSP-Krypton Project
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

package com.android.systemui.qs.tiles;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.view.Display;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.QSHost;
import com.android.systemui.R;

import javax.inject.Inject;

public class RefreshRateTile extends QSTileImpl<State> {
    private static final String TAG = "RefreshRateTile";
    private static final boolean DEBUG = false;

    private static final Icon sIcon = ResourceIcon.get(R.drawable.ic_refresh_rate);
    private static final Intent sDisplaySettingsIntent = new Intent().setComponent(
        new ComponentName("com.android.settings",
            "com.android.settings.Settings$DisplaySettingsActivity"));

    private static final float DEFAULT_REFRESH_RATE = 60f;
    private static final float NO_CONFIG = 0f;
    private static final float INVALIDATE_REFRESH_RATE = -1f;

    private final Handler mHandler;
    private final SettingsObserver mSettingsObserver;
    private final String mTileLabel, mAutoModeLabel;
    private final float mDefaultRefreshRate;

    private Mode mRefreshRateMode = Mode.MIN;
    private float mPeakRefreshRate = DEFAULT_REFRESH_RATE;

    @Inject
    public RefreshRateTile(QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
            statusBarStateController, activityStarter, qsLogger);

        mHandler = mainHandler;
        final Resources res = mContext.getResources();
        mTileLabel = res.getString(R.string.refresh_rate_tile_label);
        mAutoModeLabel = res.getString(R.string.auto_mode_label);
        mDefaultRefreshRate = getDefaultPeakRefreshRate((float) res.getInteger(
            com.android.internal.R.integer.config_defaultRefreshRate));

        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);

        if (display == null) {
            Log.w(TAG, "No valid default display");
        } else {
            for (Display.Mode mode : display.getSupportedModes()) {
                if (Math.round(mode.getRefreshRate()) > DEFAULT_REFRESH_RATE) {
                    mPeakRefreshRate = mode.getRefreshRate();
                }
            }
        }
        logD("mPeakRefreshRate = " + mPeakRefreshRate);
        mSettingsObserver = new SettingsObserver(mHandler);
    }

    @Override
    public State newTileState() {
        logD("newTileState");
        final State state = new State();
        state.icon = sIcon;
        state.state = Tile.STATE_ACTIVE;
        return state;
    }

    @Override
    public Intent getLongClickIntent() {
        return sDisplaySettingsIntent;
    }

    @Override
    public boolean isAvailable() {
        return mPeakRefreshRate > DEFAULT_REFRESH_RATE;
    }

    @Override
    protected void handleInitialize() {
        logD("handleInitialize");
        updateMode();
        mSettingsObserver.observe();
    }

    @Override
    protected void handleClick(@Nullable View view) {
        logD("handleClick");
        mRefreshRateMode = getNextMode(mRefreshRateMode);
        logD("mRefreshRateMode = " + mRefreshRateMode);
        updateRefreshRateForMode(mRefreshRateMode);
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mTileLabel;
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (state.label == null) {
            state.label = mTileLabel;
            state.contentDescription = mTileLabel;
        }
        logD("handleUpdateState, state = " + state + ", arg = " + arg);
        state.secondaryLabel = modeToString(mRefreshRateMode);
        logD("secondaryLabel = " + state.secondaryLabel);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    @Override
    public void destroy() {
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
    }

    private void updateMode() {
        final ContentResolver contentResolver = mContext.getContentResolver();
        final float minRate = Settings.System.getFloat(contentResolver,
            MIN_REFRESH_RATE, NO_CONFIG);
        final float maxRate = Settings.System.getFloat(contentResolver,
            PEAK_REFRESH_RATE, mDefaultRefreshRate);
        logD("minRate = " + minRate + ", maxRate = " + maxRate);

        if (minRate >= mPeakRefreshRate) {
            mRefreshRateMode = Mode.MAX;
        } else if (minRate <= DEFAULT_REFRESH_RATE) {
            if (maxRate == mPeakRefreshRate) {
                mRefreshRateMode = Mode.AUTO;
            } else {
                mRefreshRateMode = Mode.MIN;
            }
        }
        logD("mRefreshRateMode = " + mRefreshRateMode);
    }

    private float getDefaultPeakRefreshRate(float def) {
        float defaultPeakRefreshRate = DeviceConfig.getFloat(
                DeviceConfig.NAMESPACE_DISPLAY_MANAGER,
                DisplayManager.DeviceConfig.KEY_PEAK_REFRESH_RATE_DEFAULT,
                INVALIDATE_REFRESH_RATE);
        if (defaultPeakRefreshRate == INVALIDATE_REFRESH_RATE) {
            defaultPeakRefreshRate = def;
        }
        logD("getDefaultPeakRefreshRate(), defaultPeakRefreshRate = " + defaultPeakRefreshRate);
        return defaultPeakRefreshRate;
    }

    private Mode getNextMode(Mode mode) {
        switch (mode) {
            case MIN: return Mode.MAX;
            case MAX: return Mode.AUTO;
            case AUTO:
            default: return Mode.MIN;
        }
    }

    private void updateRefreshRateForMode(Mode mode) {
        logD("updateRefreshRateForMode, mode = " + mode);
        float minRate, maxRate;
        switch (mode) {
            case MAX:
                minRate = mPeakRefreshRate;
                maxRate = DEFAULT_REFRESH_RATE;
                break;
            case AUTO:
                minRate = NO_CONFIG;
                maxRate = mPeakRefreshRate;
                break;
            case MIN:
            default:
                minRate = NO_CONFIG;
                maxRate = DEFAULT_REFRESH_RATE;
        }
        final ContentResolver contentResolver = mContext.getContentResolver();
        mSettingsObserver.unobserve(); // Hack to prevent infinite loops
        Settings.System.putFloat(contentResolver, MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(contentResolver, PEAK_REFRESH_RATE, maxRate);
        mSettingsObserver.observe();
    }

    private String modeToString(Mode mode) {
        switch (mode) {
            case MAX: return String.valueOf((int) mPeakRefreshRate).concat("Hz");
            case AUTO: return mAutoModeLabel;
            case MIN:
            default: return String.valueOf((int) DEFAULT_REFRESH_RATE).concat("Hz");
        }
    }

    private static void logD(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

    private enum Mode {
        MIN,
        MAX,
        AUTO
    }

    private class SettingsObserver extends ContentObserver {
        private boolean mIsObserving;

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateMode();
        }

        void observe() {
            if (!mIsObserving) {
                mIsObserving = true;
            } else {
                return;
            }
            final ContentResolver contentResolver = mContext.getContentResolver();
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(MIN_REFRESH_RATE), false,
                this, UserHandle.USER_ALL);
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(PEAK_REFRESH_RATE), false,
                this, UserHandle.USER_ALL);
        }

        void unobserve() {
            if (mIsObserving) {
                mIsObserving = false;
                mContext.getContentResolver().unregisterContentObserver(this);
            }
        }
    }
}
