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

package org.blissroms.systemui.ambientmusic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.musicrecognition.MusicRecognitionManager
import android.media.musicrecognition.RecognitionRequest
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.res.R
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.google.android.systemui.keyguard.domain.interactor.AmbientIndicationInteractor
import com.google.android.systemui.keyguard.shared.AmbientIndicationMusicStatus
import com.google.android.systemui.keyguard.shared.ExpandedIndicationData
import com.google.android.systemui.keyguard.shared.ExtendedIndication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.blissroms.systemui.ambientmusic.engine.HybridMusicRecognitionEngine
import org.blissroms.systemui.ambientmusic.engine.SongResult
import java.util.concurrent.Executor

class NonPixelAmbientIndicationService(
    private val alarmManager: AlarmManager,
    private val context: Context,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val ambientIndicationInteractor: AmbientIndicationInteractor,
    private val backgroundExecutor: Executor,
) : BroadcastReceiver(), MusicRecognitionManager.RecognitionCallback {

    var started: Boolean = false
    private var musicRecognitionManager: MusicRecognitionManager? = null
    private var isPeriodicSearch: Boolean = false
    private var activeRecognitionJob: Job? = null
    private val hybridEngine by lazy { HybridMusicRecognitionEngine(context) }
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var consecutiveSilentPasses: Int = 0

    internal val callback: KeyguardUpdateMonitorCallback =
        object : KeyguardUpdateMonitorCallback() {
            override fun onUserSwitchComplete(userId: Int) {
                onUserSwitched()
            }

            override fun onKeyguardVisibilityChanged(showing: Boolean) {
                if (showing && isContinuousRecognitionEnabled()) {
                    scheduleNextPeriodicRecognition(20000L)
                } else if (!showing) {
                    cancelPeriodicRecognition()
                }
            }

            override fun onDreamingStateChanged(dreaming: Boolean) {
                if (dreaming && isContinuousRecognitionEnabled()) {
                    scheduleNextPeriodicRecognition(20000L)
                }
            }
        }

    private val hideIndicationListener =
        AlarmManager.OnAlarmListener {
            if (isOnDemandEnabled() && isContinuousRecognitionEnabled()) {
                showOnDemandSearchIcon()
            } else {
                ambientIndicationInteractor.hideAmbientMusic()
            }
        }

    private val periodicRecognitionListener =
        AlarmManager.OnAlarmListener {
            performPeriodicRecognition()
        }

    fun getCurrentUser(): Int = selectedUserInteractor.getSelectedUserId()

    fun isForCurrentUser(): Boolean {
        return try {
            val current = getCurrentUser()
            val sender = sendingUserId
            sender == current || sender <= 0 || sender == -10000 || sender == -1 || sender == -2
        } catch (e: Exception) {
            true
        }
    }

    fun isContinuousRecognitionEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            "now_playing_enabled",
            0,
            UserHandle.USER_CURRENT,
        ) == 1
    }

    fun isOnDemandEnabled(): Boolean {
        return Settings.System.getIntForUser(
            context.contentResolver,
            "now_playing_on_demand",
            0,
            UserHandle.USER_CURRENT,
        ) == 1
    }

    fun showOnDemandSearchIcon() {
        if (!isOnDemandEnabled() || !isContinuousRecognitionEnabled()) {
            return
        }
        val onDemandPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_BLISS_ON_DEMAND_SEARCH).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ambientIndicationInteractor.setAmbientMusic(
            "",
            onDemandPendingIntent,
            null,
            1,
            true,
            context.getString(R.string.ambient_music_searching),
            ExtendedIndication(
                null,
                null,
                null,
                isRecognitionResult = false,
                isSongSearching = false,
                null,
            ),
        )
    }

    fun onSettingsChanged() {
        if (isContinuousRecognitionEnabled()) {
            if (isOnDemandEnabled()) {
                showOnDemandSearchIcon()
            }
            scheduleNextPeriodicRecognition(5000L)
        } else {
            cancelPeriodicRecognition()
            alarmManager.cancel(hideIndicationListener)
            ambientIndicationInteractor.hideAmbientMusic()
        }
    }

    private fun scheduleNextPeriodicRecognition(delayMillis: Long? = null) {
        cancelPeriodicRecognition()
        if (!isContinuousRecognitionEnabled()) {
            return
        }
        val actualDelay = delayMillis ?: when {
            consecutiveSilentPasses == 0 -> PERIODIC_INTERVAL_MILLIS
            consecutiveSilentPasses == 1 -> 180000L
            consecutiveSilentPasses == 2 -> 300000L
            consecutiveSilentPasses == 3 -> 600000L
            else -> 900000L
        }
        val isAodOrKeyguard = keyguardUpdateMonitor.isKeyguardVisible || keyguardUpdateMonitor.isDreaming
        val alarmType = if (isAodOrKeyguard) {
            AlarmManager.ELAPSED_REALTIME_WAKEUP
        } else {
            AlarmManager.ELAPSED_REALTIME
        }
        alarmManager.setWindow(
            alarmType,
            SystemClock.elapsedRealtime() + actualDelay,
            30000L,
            TAG_PERIODIC,
            periodicRecognitionListener,
            null,
        )
    }

    private fun cancelPeriodicRecognition() {
        alarmManager.cancel(periodicRecognitionListener)
    }

    private fun performPeriodicRecognition() {
        if (!isContinuousRecognitionEnabled()) {
            return
        }
        if (!keyguardUpdateMonitor.isKeyguardVisible && !keyguardUpdateMonitor.isDreaming) {
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            scheduleNextPeriodicRecognition(600000L)
            return
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val isCharging = batteryManager?.isCharging == true
        if (!isCharging && batteryLevel <= 15) {
            scheduleNextPeriodicRecognition(900000L)
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.isMusicActive == true || audioManager?.mode != AudioManager.MODE_NORMAL) {
            scheduleNextPeriodicRecognition()
            return
        }

        isPeriodicSearch = true
        triggerExternalRecognizers()

        activeRecognitionJob?.cancel()
        activeRecognitionJob = serviceScope.launch {
            try {
                val result = hybridEngine.recognize(durationSeconds = 6)
                if (result != null) {
                    consecutiveSilentPasses = 0
                    withContext(Dispatchers.Main) {
                        handleEngineResult(result)
                    }
                } else {
                    isPeriodicSearch = false
                    consecutiveSilentPasses++
                    scheduleNextPeriodicRecognition()
                }
            } catch (e: CancellationException) {
                // Cancelled for another recognition
            } catch (e: Exception) {
                isPeriodicSearch = false
                consecutiveSilentPasses++
                scheduleNextPeriodicRecognition()
            }
        }
    }

    private fun triggerExternalRecognizers() {
        try {
            val ammIntent = Intent(ACTION_AMM_RUN_RECOGNITION)
                .setPackage(PACKAGE_AMM)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context.sendBroadcastAsUser(ammIntent, UserHandle.ALL)
        } catch (e: Exception) {
            // Ignored if recognizers are not installed
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!isForCurrentUser()) {
            return
        }

        val action = intent.action ?: return
        when (action) {
            ACTION_AMBIENT_INDICATION_HIDE,
            ACTION_BLISS_AMBIENT_INDICATION_HIDE,
            ACTION_AMM_AMBIENT_INDICATION_HIDE -> {
                alarmManager.cancel(hideIndicationListener)
                ambientIndicationInteractor.hideAmbientMusic()
            }
            ACTION_AMBIENT_INDICATION_SHOW,
            ACTION_BLISS_AMBIENT_INDICATION_SHOW,
            ACTION_AMM_AMBIENT_INDICATION_SHOW -> {
                handleShow(intent)
            }
            ACTION_AMBIENT_INDICATION_EXPAND,
            ACTION_BLISS_AMBIENT_INDICATION_EXPAND -> {
                handleExpand(intent)
            }
            ACTION_UPDATE_QUICK_AFFORDANCE_STATE,
            ACTION_BLISS_UPDATE_QUICK_AFFORDANCE_STATE -> {
                handleQuickAffordanceState(intent)
            }
            ACTION_BLISS_ON_DEMAND_SEARCH,
            ACTION_GOOGLE_ON_DEMAND_CLICK -> {
                startOnDemandRecognition()
            }
        }
    }

    fun startOnDemandRecognition() {
        isPeriodicSearch = false
        triggerExternalRecognizers()

        ambientIndicationInteractor.setAmbientMusic(
            context.getString(R.string.ambient_music_searching),
            null,
            null,
            0,
            false,
            null,
            ExtendedIndication(
                null,
                null,
                null,
                isRecognitionResult = false,
                isSongSearching = true,
                null,
            ),
        )

        alarmManager.cancel(hideIndicationListener)
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + SEARCH_TIMEOUT_MILLIS,
            TAG,
            hideIndicationListener,
            null,
        )

        activeRecognitionJob?.cancel()
        activeRecognitionJob = serviceScope.launch {
            try {
                val result = hybridEngine.recognize(durationSeconds = 8)
                if (result != null) {
                    consecutiveSilentPasses = 0
                    withContext(Dispatchers.Main) {
                        handleEngineResult(result)
                    }
                } else if (isOnDemandEnabled() && isContinuousRecognitionEnabled()) {
                    withContext(Dispatchers.Main) {
                        showOnDemandSearchIcon()
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled for another recognition
            } catch (e: Exception) {
                if (isOnDemandEnabled() && isContinuousRecognitionEnabled()) {
                    withContext(Dispatchers.Main) {
                        showOnDemandSearchIcon()
                    }
                }
            }
        }
    }

    private fun handleEngineResult(result: SongResult) {
        val songTitle = result.title
        val artistName = result.artist
        val displayText = "$songTitle - $artistName"
        val expandedIndicationData = if (result.albumArtUri != null) {
            ExpandedIndicationData(
                dmpIntent = null,
                dmpPackageName = null,
                albumArtUri = result.albumArtUri,
                isFavorite = false,
            )
        } else null

        ambientIndicationInteractor.setAmbientMusic(
            displayText,
            null,
            null,
            0,
            false,
            null,
            ExtendedIndication(
                songTitle,
                artistName,
                null,
                isRecognitionResult = true,
                isSongSearching = false,
                expandedIndicationData,
            ),
        )

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + DEFAULT_TTL_MILLIS,
            TAG,
            hideIndicationListener,
            null,
        )

        saveToHistory(songTitle, artistName, result.albumArtUri)

        scheduleNextPeriodicRecognition(PERIODIC_INTERVAL_MILLIS)
    }

    private fun saveToHistory(title: String, artist: String, albumArtUri: Uri?) {
        try {
            val existingJson = Settings.System.getStringForUser(
                context.contentResolver,
                "now_playing_history",
                UserHandle.USER_CURRENT,
            )
            val jsonArray = if (!existingJson.isNullOrEmpty()) {
                org.json.JSONArray(existingJson)
            } else {
                org.json.JSONArray()
            }

            if (jsonArray.length() > 0) {
                val latest = jsonArray.getJSONObject(0)
                if (latest.optString("title") == title && latest.optString("artist") == artist) {
                    return
                }
            }

            val newEntry = org.json.JSONObject().apply {
                put("title", title)
                put("artist", artist)
                put("timestamp", System.currentTimeMillis())
                if (albumArtUri != null) {
                    put("albumArtUri", albumArtUri.toString())
                }
            }

            val newArray = org.json.JSONArray()
            newArray.put(newEntry)
            for (i in 0 until jsonArray.length().coerceAtMost(99)) {
                newArray.put(jsonArray.getJSONObject(i))
            }

            Settings.System.putStringForUser(
                context.contentResolver,
                "now_playing_history",
                newArray.toString(),
                UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save track to history", e)
        }
    }

    private fun handleShow(intent: Intent) {
        val text = getExtraCharSequence(intent, EXTRA_TEXT, EXTRA_BLISS_TEXT, "text", "track", "song")
        val songTitle = getExtraCharSequence(intent, EXTRA_SONG_TITLE, EXTRA_BLISS_SONG_TITLE, "song_title", "title", "track_name")
        val artistName = getExtraCharSequence(intent, EXTRA_ARTIST_NAME, EXTRA_BLISS_ARTIST_NAME, "artist_name", "artist")
        val openIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_OPEN_INTENT, EXTRA_BLISS_OPEN_INTENT, "open_intent")
        val favoritingIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_FAVORITING_INTENT, EXTRA_BLISS_FAVORITING_INTENT, "favoriting_intent")
        val iconOverride = getExtraInt(intent, EXTRA_ICON_OVERRIDE, EXTRA_BLISS_ICON_OVERRIDE, "icon_override", default = 0)
        val iconDescription = getExtraString(intent, EXTRA_ICON_DESCRIPTION, EXTRA_BLISS_ICON_DESCRIPTION, "icon_description")
        val skipUnlock = getExtraBoolean(intent, EXTRA_SKIP_UNLOCK, EXTRA_BLISS_SKIP_UNLOCK, "skip_unlock", default = false)
        val useExtendedInteraction = getExtraBoolean(intent, EXTRA_USE_EXTENDED_INTERACTION, EXTRA_BLISS_USE_EXTENDED_INTERACTION, "use_extended_interaction", default = false)
        val ttlMillis = getTtlMillis(intent)

        val displayText = if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(songTitle)) {
            if (!TextUtils.isEmpty(artistName)) "$songTitle - $artistName" else songTitle
        } else {
            text
        }

        val albumArtUriString = getExtraString(intent, EXTRA_ALBUM_ART_URI, EXTRA_BLISS_ALBUM_ART_URI, "album_art_uri")
        val albumArtUri = if (TextUtils.isEmpty(albumArtUriString)) null else Uri.parse(albumArtUriString)
        val expandedIndicationData = if (albumArtUri != null) {
            ExpandedIndicationData(
                dmpIntent = openIntent,
                dmpPackageName = null,
                albumArtUri = albumArtUri,
                isFavorite = false,
            )
        } else null

        val isRecognitionResult = getExtraBoolean(intent, EXTRA_IS_RECOGNITION_RESULT, EXTRA_BLISS_IS_RECOGNITION_RESULT, "is_recognition_result", default = true)
        val isSongSearching = getExtraBoolean(intent, EXTRA_IS_SONG_SEARCHING, EXTRA_BLISS_IS_SONG_SEARCHING, "is_song_searching", default = false)

        if (useExtendedInteraction || !TextUtils.isEmpty(songTitle)) {
            val expandIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_EXPAND_INTENT, EXTRA_BLISS_EXPAND_INTENT, "expand_intent")

            ambientIndicationInteractor.setAmbientMusic(
                displayText,
                openIntent,
                favoritingIntent,
                iconOverride,
                skipUnlock,
                iconDescription,
                ExtendedIndication(
                    songTitle,
                    artistName,
                    expandIntent,
                    isRecognitionResult,
                    isSongSearching,
                    expandedIndicationData,
                ),
            )
        } else {
            ambientIndicationInteractor.setAmbientMusic(
                displayText,
                openIntent,
                favoritingIntent,
                iconOverride,
                skipUnlock,
                iconDescription,
                null,
            )
        }

        if (isRecognitionResult && !isSongSearching && !songTitle.isNullOrEmpty()) {
            saveToHistory(songTitle.toString(), artistName?.toString() ?: "", albumArtUri)
        }

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + ttlMillis,
            TAG,
            hideIndicationListener,
            null,
        )
    }

    private fun handleExpand(intent: Intent) {
        val text = getExtraCharSequence(intent, EXTRA_TEXT, EXTRA_BLISS_TEXT, "text", "track", "song")
        val songTitle = getExtraCharSequence(intent, EXTRA_SONG_TITLE, EXTRA_BLISS_SONG_TITLE, "song_title", "title", "track_name")
        val artistName = getExtraCharSequence(intent, EXTRA_ARTIST_NAME, EXTRA_BLISS_ARTIST_NAME, "artist_name", "artist")
        val openIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_OPEN_INTENT, EXTRA_BLISS_OPEN_INTENT, "open_intent")
        val favoritingIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_FAVORITING_INTENT, EXTRA_BLISS_FAVORITING_INTENT, "favoriting_intent")
        val albumArtUriString = getExtraString(intent, EXTRA_ALBUM_ART_URI, EXTRA_BLISS_ALBUM_ART_URI, "album_art_uri")
        val albumArtUri = if (TextUtils.isEmpty(albumArtUriString)) null else Uri.parse(albumArtUriString)
        val isFavorite = getExtraBoolean(intent, EXTRA_IS_FAVORITE, EXTRA_BLISS_IS_FAVORITE, "is_favorite", default = false)
        val dmpIntent = getExtraParcelable<PendingIntent>(intent, EXTRA_DMP_INTENT, EXTRA_BLISS_DMP_INTENT, "dmp_intent")
        val dmpPackageName = getExtraString(intent, EXTRA_DMP_PACKAGE_NAME, EXTRA_BLISS_DMP_PACKAGE_NAME, "dmp_package_name")
        val ttlMillis = getTtlMillis(intent)

        val displayText = if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(songTitle)) {
            if (!TextUtils.isEmpty(artistName)) "$songTitle - $artistName" else songTitle
        } else {
            text
        }

        val expandedIndicationData =
            ExpandedIndicationData(
                dmpIntent = dmpIntent,
                dmpPackageName = dmpPackageName,
                albumArtUri = albumArtUri,
                isFavorite = isFavorite,
            )

        ambientIndicationInteractor.setAmbientMusic(
            displayText,
            openIntent,
            favoritingIntent,
            0,
            false,
            "",
            ExtendedIndication(
                songTitle,
                artistName,
                null,
                true,
                false,
                expandedIndicationData,
            ),
        )

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + ttlMillis,
            TAG,
            hideIndicationListener,
            null,
        )
    }

    private fun handleQuickAffordanceState(intent: Intent) {
        val isEnabled = getExtraBoolean(intent, EXTRA_IS_ENABLED, EXTRA_BLISS_IS_ENABLED, "is_enabled", default = false)
        val isActive = getExtraBoolean(intent, EXTRA_IS_ACTIVE, EXTRA_BLISS_IS_ACTIVE, "is_active", default = false)
        ambientIndicationInteractor.ambientIndicationRepository.ambientMusicStatus.value =
            AmbientIndicationMusicStatus(isEnabled, isActive)
    }

    override fun onRecognitionSucceeded(
        recognitionRequest: RecognitionRequest,
        result: MediaMetadata,
        extras: Bundle?,
    ) {
        isPeriodicSearch = false
        val songTitle = result.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artistName = result.getString(MediaMetadata.METADATA_KEY_ARTIST)
        if (TextUtils.isEmpty(songTitle) && TextUtils.isEmpty(artistName)) {
            scheduleNextPeriodicRecognition(PERIODIC_INTERVAL_MILLIS)
            return
        }
        val text = if (!TextUtils.isEmpty(songTitle) && !TextUtils.isEmpty(artistName)) {
            "$songTitle - $artistName"
        } else {
            songTitle ?: artistName
        }

        val albumArtUriString = result.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: result.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: extras?.getString("album_art_uri")
            ?: extras?.getString("com.google.android.ambientindication.extra.ALBUM_ART_URI")
        val albumArtUri = if (TextUtils.isEmpty(albumArtUriString)) null else Uri.parse(albumArtUriString)
        val expandedIndicationData = if (albumArtUri != null) {
            ExpandedIndicationData(
                dmpIntent = null,
                dmpPackageName = null,
                albumArtUri = albumArtUri,
                isFavorite = false,
            )
        } else null

        ambientIndicationInteractor.setAmbientMusic(
            text,
            null,
            null,
            0,
            false,
            null,
            ExtendedIndication(
                songTitle,
                artistName,
                null,
                isRecognitionResult = true,
                isSongSearching = false,
                expandedIndicationData,
            ),
        )

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + DEFAULT_TTL_MILLIS,
            TAG,
            hideIndicationListener,
            null,
        )

        scheduleNextPeriodicRecognition(PERIODIC_INTERVAL_MILLIS)
    }

    private fun handleRecognitionFailure() {
        ambientIndicationInteractor.setAmbientMusic(
            context.getString(R.string.ambient_music_no_match),
            null,
            null,
            0,
            false,
            null,
            ExtendedIndication(
                null,
                null,
                null,
                isRecognitionResult = false,
                isSongSearching = false,
                null,
            ),
        )

        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 5000L,
            TAG,
            hideIndicationListener,
            null,
        )
    }

    override fun onRecognitionFailed(
        recognitionRequest: RecognitionRequest,
        failureCode: Int,
    ) {
        isPeriodicSearch = false
        scheduleNextPeriodicRecognition(PERIODIC_INTERVAL_MILLIS)
    }

    override fun onAudioStreamClosed() {}

    fun onUserSwitched() {
        ambientIndicationInteractor.hideAmbientMusic()
        onSettingsChanged()
    }

    private fun getTtlMillis(intent: Intent): Long {
        val ttl = intent.getLongExtra(
            EXTRA_TTL_MILLIS,
            intent.getLongExtra(EXTRA_BLISS_TTL_MILLIS, intent.getLongExtra("ttl_millis", intent.getLongExtra("ttl", DEFAULT_TTL_MILLIS)))
        )
        return ttl.coerceIn(0L, DEFAULT_TTL_MILLIS)
    }

    private fun getExtraCharSequence(intent: Intent, vararg keys: String): CharSequence? {
        for (key in keys) {
            val value = intent.getCharSequenceExtra(key)
            if (!TextUtils.isEmpty(value)) return value
        }
        return null
    }

    private fun getExtraString(intent: Intent, vararg keys: String): String? {
        for (key in keys) {
            val value = intent.getStringExtra(key)
            if (!TextUtils.isEmpty(value)) return value
        }
        return null
    }

    private fun getExtraBoolean(intent: Intent, vararg keys: String, default: Boolean): Boolean {
        for (key in keys) {
            if (intent.hasExtra(key)) return intent.getBooleanExtra(key, default)
        }
        return default
    }

    private fun getExtraInt(intent: Intent, vararg keys: String, default: Int): Int {
        for (key in keys) {
            if (intent.hasExtra(key)) return intent.getIntExtra(key, default)
        }
        return default
    }

    private inline fun <reified T> getExtraParcelable(intent: Intent, vararg keys: String): T? {
        for (key in keys) {
            val value = intent.getParcelableExtra(key, T::class.java)
            if (value != null) return value
        }
        return null
    }

    companion object {
        private const val TAG = "NonPixelAmbientIndication"
        private const val TAG_PERIODIC = "NonPixelAmbientIndication_Periodic"
        private const val DEFAULT_TTL_MILLIS = 180000L
        private const val SEARCH_TIMEOUT_MILLIS = 20000L
        private const val PERIODIC_INTERVAL_MILLIS = 120000L

        const val ACTION_AMBIENT_INDICATION_SHOW = "com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW"
        const val ACTION_AMBIENT_INDICATION_EXPAND = "com.google.android.ambientindication.action.AMBIENT_INDICATION_EXPAND"
        const val ACTION_AMBIENT_INDICATION_HIDE = "com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE"
        const val ACTION_UPDATE_QUICK_AFFORDANCE_STATE = "com.google.android.ambientindication.action.UPDATE_QUICK_AFFORDANCE_STATE"

        const val ACTION_BLISS_AMBIENT_INDICATION_SHOW = "org.blissroms.ambientmusic.action.AMBIENT_INDICATION_SHOW"
        const val ACTION_BLISS_AMBIENT_INDICATION_EXPAND = "org.blissroms.ambientmusic.action.AMBIENT_INDICATION_EXPAND"
        const val ACTION_BLISS_AMBIENT_INDICATION_HIDE = "org.blissroms.ambientmusic.action.AMBIENT_INDICATION_HIDE"
        const val ACTION_BLISS_UPDATE_QUICK_AFFORDANCE_STATE = "org.blissroms.ambientmusic.action.UPDATE_QUICK_AFFORDANCE_STATE"
        const val ACTION_BLISS_ON_DEMAND_SEARCH = "org.blissroms.ambientmusic.action.ON_DEMAND_SEARCH"
        const val ACTION_GOOGLE_ON_DEMAND_CLICK = "com.google.intelligence.sense.ambientmusic.ondemand.AQA_CLICK"

        const val PACKAGE_AMM = "com.kieronquinn.app.ambientmusicmod"
        const val PACKAGE_PAM = "com.kieronquinn.app.pixelambientmusic"
        const val ACTION_AMM_AMBIENT_INDICATION_SHOW = "com.kieronquinn.app.ambientmusicmod.action.AMBIENT_INDICATION_SHOW"
        const val ACTION_AMM_AMBIENT_INDICATION_HIDE = "com.kieronquinn.app.ambientmusicmod.action.AMBIENT_INDICATION_HIDE"
        const val ACTION_AMM_RUN_RECOGNITION = "com.kieronquinn.app.ambientmusicmod.action.RUN_RECOGNITION"
        const val ACTION_AMM_RUN_ONLINE_RECOGNITION = "com.kieronquinn.app.ambientmusicmod.action.RUN_ONLINE_RECOGNITION"

        const val EXTRA_TEXT = "com.google.android.ambientindication.extra.TEXT"
        const val EXTRA_OPEN_INTENT = "com.google.android.ambientindication.extra.OPEN_INTENT"
        const val EXTRA_FAVORITING_INTENT = "com.google.android.ambientindication.extra.FAVORITING_INTENT"
        const val EXTRA_SONG_TITLE = "com.google.android.ambientindication.extra.SONG_TITLE"
        const val EXTRA_ARTIST_NAME = "com.google.android.ambientindication.extra.ARTIST_NAME"
        const val EXTRA_TTL_MILLIS = "com.google.android.ambientindication.extra.TTL_MILLIS"
        const val EXTRA_SKIP_UNLOCK = "com.google.android.ambientindication.extra.SKIP_UNLOCK"
        const val EXTRA_ICON_OVERRIDE = "com.google.android.ambientindication.extra.ICON_OVERRIDE"
        const val EXTRA_ICON_DESCRIPTION = "com.google.android.ambientindication.extra.ICON_DESCRIPTION"
        const val EXTRA_USE_EXTENDED_INTERACTION = "com.google.android.ambientindication.extra.USE_EXTENDED_INTERACTION"
        const val EXTRA_EXPAND_INTENT = "com.google.android.ambientindication.extra.EXPAND_INTENT"
        const val EXTRA_IS_RECOGNITION_RESULT = "com.google.android.ambientindication.extra.IS_RECOGNITION_RESULT"
        const val EXTRA_IS_SONG_SEARCHING = "com.google.android.ambientindication.extra.IS_SONG_SEARCHING"
        const val EXTRA_ALBUM_ART_URI = "com.google.android.ambientindication.extra.ALBUM_ART_URI"
        const val EXTRA_IS_FAVORITE = "com.google.android.ambientindication.extra.IS_FAVORITE"
        const val EXTRA_DMP_INTENT = "com.google.android.ambientindication.extra.DMP_INTENT"
        const val EXTRA_DMP_PACKAGE_NAME = "com.google.android.ambientindication.extra.DMP_PACKAGE_NAME"
        const val EXTRA_IS_ENABLED = "com.google.android.ambientindication.extra.IS_ENABLED"
        const val EXTRA_IS_ACTIVE = "com.google.android.ambientindication.extra.IS_ACTIVE"

        const val EXTRA_BLISS_TEXT = "org.blissroms.ambientmusic.extra.TEXT"
        const val EXTRA_BLISS_OPEN_INTENT = "org.blissroms.ambientmusic.extra.OPEN_INTENT"
        const val EXTRA_BLISS_FAVORITING_INTENT = "org.blissroms.ambientmusic.extra.FAVORITING_INTENT"
        const val EXTRA_BLISS_SONG_TITLE = "org.blissroms.ambientmusic.extra.SONG_TITLE"
        const val EXTRA_BLISS_ARTIST_NAME = "org.blissroms.ambientmusic.extra.ARTIST_NAME"
        const val EXTRA_BLISS_TTL_MILLIS = "org.blissroms.ambientmusic.extra.TTL_MILLIS"
        const val EXTRA_BLISS_SKIP_UNLOCK = "org.blissroms.ambientmusic.extra.SKIP_UNLOCK"
        const val EXTRA_BLISS_ICON_OVERRIDE = "org.blissroms.ambientmusic.extra.ICON_OVERRIDE"
        const val EXTRA_BLISS_ICON_DESCRIPTION = "org.blissroms.ambientmusic.extra.ICON_DESCRIPTION"
        const val EXTRA_BLISS_USE_EXTENDED_INTERACTION = "org.blissroms.ambientmusic.extra.USE_EXTENDED_INTERACTION"
        const val EXTRA_BLISS_EXPAND_INTENT = "org.blissroms.ambientmusic.extra.EXPAND_INTENT"
        const val EXTRA_BLISS_IS_RECOGNITION_RESULT = "org.blissroms.ambientmusic.extra.IS_RECOGNITION_RESULT"
        const val EXTRA_BLISS_IS_SONG_SEARCHING = "org.blissroms.ambientmusic.extra.IS_SONG_SEARCHING"
        const val EXTRA_BLISS_ALBUM_ART_URI = "org.blissroms.ambientmusic.extra.ALBUM_ART_URI"
        const val EXTRA_BLISS_IS_FAVORITE = "org.blissroms.ambientmusic.extra.IS_FAVORITE"
        const val EXTRA_BLISS_DMP_INTENT = "org.blissroms.ambientmusic.extra.DMP_INTENT"
        const val EXTRA_BLISS_DMP_PACKAGE_NAME = "org.blissroms.ambientmusic.extra.DMP_PACKAGE_NAME"
        const val EXTRA_BLISS_IS_ENABLED = "org.blissroms.ambientmusic.extra.IS_ENABLED"
        const val EXTRA_BLISS_IS_ACTIVE = "org.blissroms.ambientmusic.extra.IS_ACTIVE"
    }
}
