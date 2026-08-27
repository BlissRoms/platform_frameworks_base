/*
 * Copyright (C) 2014-2026 The BlissRoms Project
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

package org.blissroms.systemui.ambientmusic.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HybridMusicRecognitionEngine(private val context: Context) {

    companion object {
        private const val TAG = "HybridMusicEngine"
    }

    private val recorder = AmbientAudioRecorder()
    private val offlineMatcher = OfflineShardMatcher(context)
    private val onlineEngine = OnlineSoundSearchEngine(context)
    private val mutex = Mutex()

    suspend fun recognize(durationSeconds: Int = 8): SongResult? = withContext(Dispatchers.Default) {
        mutex.withLock {
            Log.d(TAG, "Starting audio capture for recognition (${durationSeconds}s)...")
            val audioBytes = recorder.recordSample(durationSeconds) ?: run {
                Log.w(TAG, "No audio sample captured")
                return@withLock null
            }

            // 1. Attempt offline acoustic shard match first
            val offlineResult = offlineMatcher.matchAudio(audioBytes)
            if (offlineResult != null) {
                Log.d(TAG, "Matched song offline: ${offlineResult.title} - ${offlineResult.artist}")
                return@withLock offlineResult
            }

            // 2. Fall back to online sound search if offline match not found
            val onlineResult = onlineEngine.recognizeOnline(audioBytes)
            if (onlineResult != null) {
                Log.d(TAG, "Matched song online: ${onlineResult.title} - ${onlineResult.artist}")
                return@withLock onlineResult
            }

            Log.d(TAG, "No match found for ambient audio sample")
            return@withLock null
        }
    }
}
