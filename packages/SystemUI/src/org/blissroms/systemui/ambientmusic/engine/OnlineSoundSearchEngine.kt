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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class OnlineSoundSearchEngine(private val context: Context) {

    companion object {
        private const val TAG = "OnlineSoundSearch"
        private const val BASE_URL = "https://amp.shazam.com/discovery/v5/en-US/US/android/-/tag"
        private const val TIMEOUT_MILLIS = 8000
    }

    private val connectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }

    private val deviceUuid: String by lazy {
        val prefs = context.getSharedPreferences("ambient_music_engine", Context.MODE_PRIVATE)
        var id = prefs.getString("device_uuid", null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString().uppercase()
            prefs.edit().putString("device_uuid", id).apply()
        }
        id
    }

    fun isOnline(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun recognizeOnline(audioBytes: ByteArray): SongResult? = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            return@withContext null
        }

        var connection: HttpURLConnection? = null
        try {
            val signatureBase64 = ShazamSignatureGenerator.generateSignature(audioBytes, 16000)
            if (signatureBase64.isNullOrEmpty()) {
                return@withContext null
            }
            val sampleMs = (audioBytes.size * 1000) / (16000 * 2)
            val tagUuid = UUID.randomUUID().toString().uppercase()
            val requestUrl = "$BASE_URL/$deviceUuid/$tagUuid?sync=true"
            val timestamp = System.currentTimeMillis()

            val jsonBody = JSONObject().apply {
                val signatureObj = JSONObject().apply {
                    put("uri", "data:audio/vnd.shazam.sig;base64,$signatureBase64")
                    put("samplems", sampleMs)
                    put("timestamp", timestamp)
                }
                put("signature", signatureObj)
                put("timestamp", timestamp)
                put("timezone", "UTC")
                val geo = JSONObject().apply {
                    put("latitude", 0.0)
                    put("longitude", 0.0)
                    put("altitude", 0.0)
                }
                put("geolocation", geo)
            }

            val url = URL(requestUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "Shazam/14.0.0 (Android; en-US)")
            }

            connection.outputStream.use { out ->
                out.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseResponse(responseText)
            }
            Log.w(TAG, "Server returned response code: ${connection.responseCode}")
            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "Online sound search error: ${e.message}")
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseResponse(jsonString: String): SongResult? {
        return try {
            val json = JSONObject(jsonString)
            val track = json.optJSONObject("track")
                ?: json.optJSONArray("matches")?.optJSONObject(0)
                ?: return null
            val title = track.optString("title").ifEmpty { track.optString("heading") }
            val artist = track.optString("subtitle").ifEmpty { track.optString("subheading") }
            val images = track.optJSONObject("images")
            val albumArtUrl = images?.optString("coverarthq")?.ifEmpty { null }
                ?: images?.optString("coverart")?.ifEmpty { null }
                ?: images?.optString("background")?.ifEmpty { null }
                ?: track.optJSONObject("share")?.optString("image")?.ifEmpty { null }
                ?: track.optJSONObject("hub")?.optJSONObject("image")?.optString("default")?.ifEmpty { null }

            if (title.isNotEmpty() && artist.isNotEmpty()) {
                SongResult(
                    title = title,
                    artist = artist,
                    albumArtUri = if (!albumArtUrl.isNullOrEmpty()) Uri.parse(albumArtUrl) else null,
                    source = SongResult.RecognitionSource.ONLINE_FALLBACK,
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing track response", e)
            null
        }
    }
}
