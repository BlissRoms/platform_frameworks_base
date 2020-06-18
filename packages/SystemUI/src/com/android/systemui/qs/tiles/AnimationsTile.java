/*
 * Copyright (C) 2019-20 The BlissRoms Project
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
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

import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.service.quicksettings.Tile;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;

import javax.inject.Inject;

/** Quick settings tile: Animations **/
public class AnimationsTile extends QSTileImpl<BooleanState> {

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_qs_animations);

    private final GlobalSetting mSetting;

    @Inject
    public AnimationsTile(QSHost host) {
        super(host);

        mSetting = new GlobalSetting(mContext, mHandler, Global.DISABLE_TRANSITION_ANIMATIONS) {
            @Override
            protected void handleValueChanged(int value) {
                handleRefreshState(value);
            }
        };
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
    public Intent getLongClickIntent() {
        return null;
    }

    private void setEnabled(boolean enabled) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DISABLE_TRANSITION_ANIMATIONS,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean disableAnimations = value != 0;
        state.value = disableAnimations;
        state.label = mContext.getString(R.string.quick_settings_animation_label);
        state.icon = mIcon;
        if (disableAnimations) {
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_animations_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription =  mContext.getString(
                    R.string.quick_settings_animations_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_animation_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.BLISSIFY;
    }

    @Override
    public void handleSetListening(boolean listening) {
        // Do nothing
    }
}
