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

import android.net.Uri

data class SongResult(
    val title: String,
    val artist: String,
    val albumArtUri: Uri? = null,
    val source: RecognitionSource = RecognitionSource.OFFLINE_NEURAL,
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    enum class RecognitionSource {
        OFFLINE_NEURAL,
        ONLINE_FALLBACK,
    }
}
