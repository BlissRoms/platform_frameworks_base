/*
 * Copyright (C) 2018 The LineageOS Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.tuner.TunerService;

public class ClockController implements TunerService.Tunable {

    private static final String TAG = "ClockController";

    private static final String CLOCK_POSITION = Settings.Secure.STATUS_BAR_CLOCK;

    private static final int CLOCK_POSITION_LEFT = 0;
    private static final int CLOCK_POSITION_CENTER = 1;
    private static final int CLOCK_POSITION_RIGHT = 2;

    private Context mContext;
    private Clock mActiveClock, mCenterClock, mLeftClock, mRightClock;

    private int mClockPosition = CLOCK_POSITION_LEFT;
    private boolean mBlackListed = false;

    private final CustomSettingsObserver mCustomSettingsObserver;
    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(CLOCK_POSITION),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        void update() {
            mClockPosition = Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.STATUS_BAR_CLOCK, CLOCK_POSITION_LEFT);
            updateActiveClock();
        }
    }

    public ClockController(Context context, View statusBar) {
        mContext = context;

        mCenterClock = statusBar.findViewById(R.id.clock_center);
        mLeftClock = statusBar.findViewById(R.id.clock);
        mRightClock = statusBar.findViewById(R.id.clock_right);

        mActiveClock = mLeftClock;

        mCustomSettingsObserver = new CustomSettingsObserver();
        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();

        Dependency.get(TunerService.class).addTunable(this,
                StatusBarIconController.ICON_HIDE_LIST);
    }

    public Clock getClock() {
        switch (mClockPosition) {
            default:
            case CLOCK_POSITION_LEFT:
                return mLeftClock;
            case CLOCK_POSITION_CENTER:
                return mCenterClock;
            case CLOCK_POSITION_RIGHT:
                return mRightClock;
        }
    }

    private void updateActiveClock() {
        mActiveClock.setClockVisibleByUser(false);
        removeDarkReceiver();
        mActiveClock = getClock();
        mActiveClock.setClockVisibleByUser(true);
        addDarkReceiver();

        // Override any previous setting
        mActiveClock.setClockVisibleByUser(!mBlackListed);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        Log.d(TAG, "onTuningChanged key=" + key + " value=" + newValue);
        mBlackListed = StatusBarIconController.getIconHideList(
                mContext, newValue).contains("clock");
        updateActiveClock();
    }

    public void addDarkReceiver() {
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(mActiveClock);
    }

    public void removeDarkReceiver() {
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(mActiveClock);
    }
}
