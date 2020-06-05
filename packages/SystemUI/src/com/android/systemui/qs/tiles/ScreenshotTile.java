/*
 * Copyright (C) 2017 ABC rom
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.internal.util.bliss.BlissUtils;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/** Quick settings tile: Screenshot **/
public class ScreenshotTile extends QSTileImpl<BooleanState> {

    private int mType;

    @Inject
    public ScreenshotTile(QSHost host) {
        super(host);
        mType = getScreenShotType();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleSetListening(boolean listening) {}

    @Override
    public void handleClick() {
        switchMode();
    }

    private void switchMode() {
        mType++;
        if (mType == 4) {
            mType = 0;
        }
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DEFAULT_MODE, mType,
                UserHandle.USER_CURRENT);
        refreshState();
    }

    private int getScreenShotType() {
        mType = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SCREENSHOT_DEFAULT_MODE, 0, UserHandle.USER_CURRENT);
        return mType;
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();

        //finish collapsing the panel
        try {
             Thread.sleep(1000); //1s
        } catch (InterruptedException ie) {}
        BlissUtils.takeScreenshot(mType);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_screenshot_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        switch (mType) {
           case 0:
              state.label = mContext.getString(R.string.quick_settings_screenshot_off);
              state.icon = ResourceIcon.get(R.drawable.ic_qs_screenshot);
              state.contentDescription =  mContext.getString(
                      R.string.quick_settings_long_screenshot_label);
              state.state = Tile.STATE_INACTIVE;
              break;
           case 1:
              state.label = mContext.getString(R.string.quick_settings_screenshot_label);
              state.icon = ResourceIcon.get(R.drawable.ic_qs_screenshot);
              state.contentDescription =  mContext.getString(
                      R.string.quick_settings_screenshot_label);
              state.state = Tile.STATE_ACTIVE;
              break;
           case 2:
              state.label = mContext.getString(R.string.quick_settings_region_screenshot_label);
              state.icon = ResourceIcon.get(R.drawable.ic_qs_region_screenshot);
              state.contentDescription =  mContext.getString(
                      R.string.quick_settings_region_screenshot_label);
              state.state = Tile.STATE_ACTIVE;
              break;
           case 3:
              state.label = mContext.getString(R.string.quick_settings_long_screenshot_label);
              state.icon = ResourceIcon.get(R.drawable.ic_qs_screenshot);
              state.contentDescription =  mContext.getString(
                      R.string.quick_settings_long_screenshot_label);
              state.state = Tile.STATE_ACTIVE;
              break;
        }
    }
}
