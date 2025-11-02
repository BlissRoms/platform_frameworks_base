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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface RingerModeInteractor {
    val ringerMode: Flow<Int>
    fun getCurrentMode(): Int
    fun setRingerMode(mode: Int)
}

class RingerModeInteractorImpl(
    private val context: Context,
    private val audioManager: AudioManager
) : RingerModeInteractor {
    
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
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged()
    
    override fun getCurrentMode(): Int = audioManager.ringerMode
    
    override fun setRingerMode(mode: Int) {
        audioManager.ringerMode = mode
    }
}
