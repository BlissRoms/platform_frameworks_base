/*
 * Copyright (C) 2022-2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles;

import static android.provider.Settings.System.MIN_REFRESH_RATE;
import static android.provider.Settings.System.PEAK_REFRESH_RATE;

import android.content.ComponentName;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.util.bliss.DisplayRefreshRateHelper;

import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.util.settings.SystemSettings;

import java.util.ArrayList;

import javax.inject.Inject;

import com.bliss.display.IRefreshRateListener;
import com.bliss.display.RefreshRateManager;

public class RefreshRateTile extends QSTileImpl<State> {

    public static final String TILE_SPEC = "refresh_rate";

    private static final Intent SCREEN_REFRESH_RATE_SETTINGS =
            new Intent().setComponent(new ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings$ScreenRefreshRateActivity"));

    private final ArrayList<Integer> mSupportedList;
    private final DisplayRefreshRateHelper mHelper;
    private final RefreshRateManager mManager;
    private final SettingsObserver mObserver;

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_refresh_rate);

    private int mMinRefreshRate;
    private int mPeakRefreshRate;

    private boolean mUpdateRefreshRate = true;

    private int mRequestedRefreshRate = -1;
    private int mRequestedMemcRefreshRate = -1;

    private boolean mRegistered = false;

    private final IRefreshRateListener.Stub mRefreshRateListener =
            new IRefreshRateListener.Stub() {
        @Override
        public void onRequestedRefreshRate(int refreshRate) {
            mRequestedRefreshRate = refreshRate;
            refreshState();
        }

        @Override
        public void onRequestedMemcRefreshRate(int refreshRate) {
            mRequestedMemcRefreshRate = refreshRate;
            refreshState();
        }
    };

    @Inject
    public RefreshRateTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SystemSettings systemSettings) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager,
                metricsLogger, statusBarStateController, activityStarter, qsLogger);

        mHelper = DisplayRefreshRateHelper.getInstance(mContext);
        mManager = mContext.getSystemService(RefreshRateManager.class);
        mSupportedList = mHelper.getSupportedRefreshRateList();

        mObserver = new SettingsObserver(mHandler, systemSettings);

        mMinRefreshRate = mHelper.getMinimumRefreshRate();
        mPeakRefreshRate = mHelper.getPeakRefreshRate();
    }

    @Override
    public boolean isAvailable() {
        return mSupportedList.size() > 1;
    }

    @Override
    public State newTileState() {
        return new State();
    }

    @Override
    protected void handleInitialize() {
        mObserver.observe();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mObserver.unobserve();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (mRequestedRefreshRate > 0 || mRequestedMemcRefreshRate > 0) {
            return;
        }
        if (!isRefreshRateValid()) {
            mMinRefreshRate = mSupportedList.get(mSupportedList.size() - 1);
            mPeakRefreshRate = mMinRefreshRate;
        } else if (mSupportedList.indexOf(mPeakRefreshRate) == mSupportedList.size() - 1) {
            if (mMinRefreshRate == mPeakRefreshRate) {
                mMinRefreshRate = mSupportedList.get(0);
            } else {
                mMinRefreshRate = mSupportedList.get(mSupportedList.indexOf(mMinRefreshRate) + 1);
            }
            mPeakRefreshRate = mMinRefreshRate;
        } else {
            mPeakRefreshRate = mSupportedList.get(mSupportedList.indexOf(mPeakRefreshRate) + 1);
        }
        mUpdateRefreshRate = false;
        mHelper.setRefreshRate(mMinRefreshRate, mPeakRefreshRate);
        mUpdateRefreshRate = true;
    }

    @Override
    public Intent getLongClickIntent() {
        return SCREEN_REFRESH_RATE_SETTINGS;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.refresh_rate_tile_label);
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        if (!isAvailable()) {
            return;
        }

        state.state = mRequestedRefreshRate > 0 || mRequestedMemcRefreshRate > 0
                ? Tile.STATE_UNAVAILABLE : Tile.STATE_ACTIVE;
        state.icon = mIcon;
        state.label = mContext.getString(R.string.refresh_rate_tile_label);
        state.contentDescription = mContext.getString(R.string.refresh_rate_tile_label);
        state.secondaryLabel = getRefreshRateLabel();
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);

        if (!isAvailable()) {
            return;
        }

        if (listening && !mRegistered) {
            mManager.registerRefreshRateListener(mRefreshRateListener);
            mRegistered = true;
        } else if (!listening && mRegistered) {
            mManager.unregisterRefreshRateListener(mRefreshRateListener);
            mRegistered = false;
        }
    }

    private boolean isRefreshRateValid() {
        return mHelper.isRefreshRateValid(mMinRefreshRate) &&
                mHelper.isRefreshRateValid(mPeakRefreshRate) &&
                mMinRefreshRate <= mPeakRefreshRate;
    }

    private String getRefreshRateLabel() {
        if (mRequestedMemcRefreshRate > 0) {
            return String.valueOf((int) mRequestedMemcRefreshRate) + " Hz";
        }
        if (mRequestedRefreshRate > 0) {
            return String.valueOf((int) mRequestedRefreshRate) + " Hz";
        }
        if (!isRefreshRateValid()) {
            return mContext.getString(R.string.refresh_rate_unknown);
        }
        if (mMinRefreshRate == mPeakRefreshRate) {
            return String.valueOf(mPeakRefreshRate) + " Hz";
        }
        return String.valueOf(mMinRefreshRate) + " ~ " + String.valueOf(mPeakRefreshRate) + " Hz";
    }

    private final class SettingsObserver extends ContentObserver {

        private final SystemSettings mSystemSettings;

        private boolean mObserving = false;

        SettingsObserver(Handler handler, SystemSettings systemSettings) {
            super(handler);
            mSystemSettings = systemSettings;
        }

        void observe() {
            if (mObserving) {
                return;
            }
            mSystemSettings.registerContentObserverSync(MIN_REFRESH_RATE, this);
            mSystemSettings.registerContentObserverSync(PEAK_REFRESH_RATE, this);
            mObserving = true;
        }

        void unobserve() {
            if (!mObserving) {
                return;
            }
            mSystemSettings.unregisterContentObserverSync(this);
            mObserving = false;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mUpdateRefreshRate) {
                mMinRefreshRate = mHelper.getMinimumRefreshRate();
                mPeakRefreshRate = mHelper.getPeakRefreshRate();
            }
            refreshState();
        }
    }
}
