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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AmbientAudioRecorder {

    companion object {
        private const val TAG = "AmbientAudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SILENCE_RMS_THRESHOLD = 180.0
    }

    @SuppressLint("MissingPermission")
    suspend fun recordSample(durationSeconds: Int = 8): ByteArray? = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for audio recording: $minBufferSize")
            return@withContext null
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
        var audioRecord: AudioRecord? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return@withContext null
            }

            val totalBytesTarget = SAMPLE_RATE * 2 * durationSeconds
            val outputStream = ByteArrayOutputStream(totalBytesTarget)
            val buffer = ByteArray(bufferSize)

            audioRecord.startRecording()
            var bytesReadTotal = 0

            while (bytesReadTotal < totalBytesTarget && isActive) {
                val bytesToRead = (totalBytesTarget - bytesReadTotal).coerceAtMost(buffer.size)
                val read = audioRecord.read(buffer, 0, bytesToRead)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    bytesReadTotal += read
                } else if (read < 0) {
                    Log.e(TAG, "Error while reading audio buffer: $read")
                    break
                }
            }

            if (bytesReadTotal >= totalBytesTarget / 2) {
                val recordedBytes = outputStream.toByteArray()
                var sumSquare = 0.0
                val numShorts = recordedBytes.size / 2
                val byteBuffer = java.nio.ByteBuffer.wrap(recordedBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until numShorts) {
                    val sample = byteBuffer.short.toDouble()
                    sumSquare += sample * sample
                }
                val rms = Math.sqrt(sumSquare / numShorts)
                if (rms < SILENCE_RMS_THRESHOLD) {
                    Log.d(TAG, "Audio sample RMS ($rms) is below silence threshold ($SILENCE_RMS_THRESHOLD), ignoring silence")
                    return@withContext null
                }
                return@withContext recordedBytes
            } else {
                Log.w(TAG, "Insufficient audio recorded: $bytesReadTotal bytes")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed during audio recording", e)
            return@withContext null
        } finally {
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
                audioRecord?.release()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}
