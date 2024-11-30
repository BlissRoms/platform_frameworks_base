/*
 * Copyright (C) 2023-2024 the risingOS Android Project
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

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

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
import com.android.systemui.qs.tileimpl.TouchableQSTile;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.ConfigurationController;

import javax.inject.Inject;

public class VolumeControlTile extends QSTileImpl<BooleanState> 
        implements TouchableQSTile, ConfigurationController.ConfigurationListener {

    public static final String TILE_SPEC = "volume_control";

    private static final String VOLUME_LEVEL_SETTING = "volume_level";

    private final AudioManager mAudioManager;
    private float mCurrentVolumePercent;
    private int mCurrentVolumeLevel;
    
    private boolean mListening = false;

    private final View.OnTouchListener mTouchListener =
            new View.OnTouchListener() {
                float initX = 0;
                float initPct = 0;
                boolean moved = false;

                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    switch (motionEvent.getAction()) {
                        case MotionEvent.ACTION_DOWN -> {
                            initX = motionEvent.getX();
                            initPct = initX / view.getWidth();
                            return true;
                        }
                        case MotionEvent.ACTION_MOVE -> {
                            float newPct = motionEvent.getX() / view.getWidth();
                            float deltaPct = Math.abs(newPct - initPct);
                            if (deltaPct > .03f) {
                                view.getParent().requestDisallowInterceptTouchEvent(true);
                                moved = true;
                                mCurrentVolumePercent = Math.max(0f, Math.min(newPct, 1));
                                updateVolumeFromUser();
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP -> {
                            if (moved) {
                                moved = false;
                                updateVolumeFromUser();
                            } else {
                                refreshState(true);
                            }
                            return true;
                        }
                    }
                    return true;
                }
            };

    private final BroadcastReceiver mVolumeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.VOLUME_CHANGED_ACTION.equals(intent.getAction())) {
                updateVolumeFromSystem();
            }
        }
    };

    @Inject
    public VolumeControlTile(
            QSHost host,
            QsEventLogger qsEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            ConfigurationController configurationController) {
        super(
                host,
                qsEventLogger,
                backgroundLooper,
                mainHandler,
                falsingManager,
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        configurationController.observe(getLifecycle(), this);
        updateVolumeFromSystem();
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            IntentFilter filter = new IntentFilter(AudioManager.VOLUME_CHANGED_ACTION);
            mContext.registerReceiver(mVolumeChangeReceiver, filter);
        } else {
            mContext.unregisterReceiver(mVolumeChangeReceiver);
        }
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public View.OnTouchListener getTouchListener() {
        return mTouchListener;
    }

    @Override
    public String getSettingsSystemKey() {
        return VOLUME_LEVEL_SETTING;
    }

    @Override
    public float getSettingsDefaultValue() {
        return mCurrentVolumePercent;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_volume_tile_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }
    
    @Override
    public void onUiModeChanged() {
        updateVolumeFromSystem();
    }
    
    private void updateVolumeFromSystem() {
        mCurrentVolumeLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mCurrentVolumePercent = (float) mCurrentVolumeLevel / mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        Settings.System.putFloat(
                mContext.getContentResolver(),
                VOLUME_LEVEL_SETTING,
                mCurrentVolumePercent);
        refreshState(true);
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {}

    private void updateVolumeFromUser() {
        int newLevel = (int) (mCurrentVolumePercent * mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newLevel, 0);
        mCurrentVolumeLevel = newLevel;
        Settings.System.putFloat(
                mContext.getContentResolver(),
                VOLUME_LEVEL_SETTING,
                mCurrentVolumePercent);
        refreshState(true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.state = Tile.STATE_ACTIVE;
        state.label = mHost.getContext().getString(R.string.quick_settings_volume_tile_label) 
            + " - " + Math.round(mCurrentVolumePercent * 100f) + "%";
        float volumePercent = mCurrentVolumePercent * 100f;
        if (volumePercent <= 1) {
            state.icon = ResourceIcon.get(R.drawable.ic_volume_media_mute);
            state.secondaryLabel = mHost.getContext().getString(R.string.quick_settings_volume_tile_muted);
        } else if (volumePercent > 0 && volumePercent < 50) {
            state.icon = ResourceIcon.get(R.drawable.ic_volume_media_low);
            state.secondaryLabel = mHost.getContext().getString(R.string.quick_settings_volume_tile_low);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_volume_media);
            state.secondaryLabel = mHost.getContext().getString(R.string.quick_settings_volume_tile_high);
        }
    }
}
