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
import kotlinx.coroutines.withContext
import java.io.File

class OfflineShardMatcher(private val context: Context) {

    companion object {
        private const val TAG = "OfflineShardMatcher"
        private const val SYSTEM_SHARDS_PATH = "/product/etc/ambientmusic/shards"
        private const val DATA_SHARDS_PATH = "/data/misc/ambientmusic/shards"
    }

    private val localShardsDir: File by lazy {
        val dataDir = File(DATA_SHARDS_PATH)
        if (dataDir.exists() && dataDir.listFiles()?.isNotEmpty() == true) {
            dataDir
        } else {
            val systemDir = File(SYSTEM_SHARDS_PATH)
            if (systemDir.exists()) systemDir else File(context.filesDir, "ambient_shards")
        }
    }

    fun hasShards(): Boolean {
        return localShardsDir.exists() && localShardsDir.listFiles()?.isNotEmpty() == true
    }

    suspend fun matchAudio(audioBytes: ByteArray): SongResult? = withContext(Dispatchers.Default) {
        if (!hasShards()) {
            return@withContext null
        }

        try {
            // Check local acoustic shard index
            // Shard search maps the audio sample to embedding and checks the nearest vector
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error matching against local shards", e)
            return@withContext null
        }
    }
}
