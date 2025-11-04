/*
 * Copyright (C) 2025 AxionOS
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
package com.android.systemui.common.ringer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import android.os.Vibrator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

interface RingerModeInteractor {
    val ringerMode: Flow<Int>
    val targetPositionFlow: Flow<Float>
    val dndMode: Flow<Boolean>
    fun isDndEnabled(): Boolean
    fun toggleDnd()

    fun getCurrentMode(): Int
    fun setRingerMode(mode: Int)
    fun getAvailableRingerModes(): List<RingerModeOption>
    fun getNumberOfModes(): Int
    fun getMaxOffset(): Float
    fun getTargetPosition(currentMode: Int): Float
    fun snapMode(offset: Float): Int
}

data class RingerModeOption(
    val mode: Int,
    val icon: ImageVector,
    val label: String
)

class RingerModeInteractorImpl(
    private val context: Context,
    private val audioManager: AudioManager,
    private val notificationManager: NotificationManager
) : RingerModeInteractor {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    override val ringerMode: Flow<Int> = callbackFlow {
        trySend(audioManager.ringerMode)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    trySend(audioManager.ringerMode)
                }
            }
        }

        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override val dndMode: Flow<Boolean> = callbackFlow {
        trySend(isDndEnabled())
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                    trySend(isDndEnabled())
                }
            }
        }
        
        val filter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        context.registerReceiver(receiver, filter)
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()

    override fun getCurrentMode(): Int = audioManager.ringerModeInternal

    override fun setRingerMode(mode: Int) {
        audioManager.ringerModeInternal = mode
    }

    override fun getAvailableRingerModes(): List<RingerModeOption> {
        val modes = mutableListOf(
            RingerModeOption(AudioManager.RINGER_MODE_SILENT, Icons.Filled.VolumeOff, "Silent"),
            RingerModeOption(AudioManager.RINGER_MODE_NORMAL, Icons.Filled.VolumeUp, "Normal")
        )

        if (hasVibrator) {
            modes.add(1, RingerModeOption(AudioManager.RINGER_MODE_VIBRATE, Icons.Filled.Vibration, "Vibrate"))
        }

        return modes
    }

    override fun getNumberOfModes(): Int = getAvailableRingerModes().size

    override fun getMaxOffset(): Float = (getNumberOfModes() - 1).coerceAtLeast(1).toFloat()

    override fun getTargetPosition(currentMode: Int): Float {
        val modes = getAvailableRingerModes()
        val idx = modes.indexOfFirst { it.mode == currentMode }.takeIf { it >= 0 } ?: 0
        return idx.toFloat()
    }

    override fun snapMode(offset: Float): Int {
        val modes = getAvailableRingerModes()
        val maxIndex = (modes.size - 1).coerceAtLeast(0)
        val snappedIndex = offset.roundToInt().coerceIn(0, maxIndex)
        return modes[snappedIndex].mode
    }

    override val targetPositionFlow: Flow<Float> =
        ringerMode.map { getTargetPosition(it) }.distinctUntilChanged()

    override fun isDndEnabled(): Boolean = 
        notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    
    override fun toggleDnd() {
        val newFilter = if (isDndEnabled()) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        notificationManager.setInterruptionFilter(newFilter)
    }
}
