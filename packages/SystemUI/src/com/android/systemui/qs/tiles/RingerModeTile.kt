/* 
 * SPDX-FileCopyrightText: 2021 The Android Open Source Project
 * SPDX-FileCopyrightText: 2025 Adithya R <gh0strider.2k18.reborn@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_VIBRATE
import android.media.AudioManager.RINGER_MODE_SILENT
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.service.quicksettings.Tile
import com.android.internal.logging.MetricsLogger
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import javax.inject.Inject

class RingerModeTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main private val mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger
) : QSTileImpl<BooleanState>(
    host,
    uiEventLogger,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger
) {
    private val audioManager = mContext.getSystemService(AudioManager::class.java)

    private val ringerModeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshState()
        }
    }

    private val ringerModeToRes = mapOf(
        RINGER_MODE_NORMAL to
            Pair(R.drawable.ic_speaker_on, R.string.volume_ringer_status_normal),
        RINGER_MODE_VIBRATE to
            Pair(R.drawable.ic_volume_ringer_vibrate, R.string.accessibility_ringer_vibrate),
        RINGER_MODE_SILENT to
            Pair(R.drawable.ic_speaker_mute, R.string.accessibility_ringer_silent)
    )

    override fun newTileState() = BooleanState().apply {
        label = tileLabel
        handlesLongClick = false
        state = Tile.STATE_ACTIVE
    }

    override fun handleSetListening(listening: Boolean) {
        if (listening) {
            mContext.registerReceiver(
                ringerModeChangeReceiver,
                IntentFilter(INTERNAL_RINGER_MODE_CHANGED_ACTION)
            )
        } else {
            mContext.unregisterReceiver(ringerModeChangeReceiver)
        }
    }

    override fun handleClick(expandable: Expandable?) {
        // noop, handled by tile view
    }

    override fun getLongClickIntent() = null

    override fun getTileLabel() = mContext.getString(R.string.qs_tile_ringer_mode_label)

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val ringerMode = audioManager?.getRingerModeInternal() ?: return
        ringerModeToRes[ringerMode]?.let { (iconRes, cdRes) ->
            state.apply {
                icon = ResourceIcon.get(iconRes)
                contentDescription = mContext.getString(cdRes)
            }
        }
    }

    companion object {
        const val TILE_SPEC = "ringer_sound"
    }
}
