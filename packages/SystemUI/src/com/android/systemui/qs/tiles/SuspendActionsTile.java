/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.ComponentName;
import android.os.UserHandle;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/** Quick settings tile: Suspend Actions **/
public class SuspendActionsTile extends QSTileImpl<BooleanState> {

    private static final ComponentName SUSPEND_ACTIONS_SETTINGS_COMPONENT = new ComponentName(
            "com.blissroms.blissify", "com.blissroms.blissify.fragments.misc$ScreenStateServiceActivity");

    private static final Intent SUSPEND_ACTIONS_SETTINGS =
            new Intent().setComponent(SUSPEND_ACTIONS_SETTINGS_COMPONENT);

    @Inject
    public SuspendActionsTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_suspend_actions_label);
    }

    @Override
    public Intent getLongClickIntent() {
        return SUSPEND_ACTIONS_SETTINGS;
    }

    private void setEnabled(boolean enabled) {
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.START_SCREEN_STATE_SERVICE, enabled ? 1 : 0, UserHandle.USER_CURRENT);
        if (enabled) {
            Intent screenstate = new Intent(mContext, com.android.systemui.screenstate.ScreenStateService.class);
            mContext.startService(screenstate);
        }
    }

    private boolean isSuspendActionsEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.START_SCREEN_STATE_SERVICE, 0) == 1;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = isSuspendActionsEnabled();
        state.state = Tile.STATE_INACTIVE;
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_suspend_actions_on);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_suspend_actions_off);
            state.label =  mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_suspend_actions_off);
        }
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
