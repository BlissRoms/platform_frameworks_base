/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.systemui.util

import android.app.WallpaperColors
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionLegacyHelper
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View

import com.android.internal.jank.InteractionJankMonitor

import com.android.systemui.Dependency
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.monet.ColorScheme
import com.android.systemui.media.dialog.MediaOutputDialogManager

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main

class MediaSessionManagerHelper private constructor(private val context: Context) {

    interface MediaMetadataListener {
        fun onMediaMetadataChanged() {}
        fun onPlaybackStateChanged() {}
        fun onMediaColorsChanged() {}
    }

    private var lastSavedPackageName: String? = null
    private val mediaSessionManager: MediaSessionManager = context.getSystemService(MediaSessionManager::class.java)!!
    private var activeController: MediaController? = null
    private val listeners: MutableSet<MediaMetadataListener> = mutableSetOf()
    private var mediaMetadata: MediaMetadata? = null
    private var currMediaArtColor: Int = 0
    private var mWallpaperColors: WallpaperColors? = null
    private var mCurrentColorScheme: ColorScheme? = null

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (mediaMetadata != metadata) {
                mediaMetadata = metadata
                notifyListeners { it.onMediaMetadataChanged() }
            }
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            notifyListeners { it.onPlaybackStateChanged() }
        }
    }

    private var updateJob: Job? = null

    init {
        lastSavedPackageName = Settings.System.getString(
            context.contentResolver,
            "media_session_last_package_name"
        )
    }

    private fun startPeriodicUpdate() {
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateMediaController()
                updateMediaColors()
                delay(1000)
            }
        }
    }
    
    fun updateMediaColors() {
        val metadata = getMediaMetadata()
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        
        val wallpaperColors = bitmap?.let { WallpaperColors.fromBitmap(it) }
        if (wallpaperColors == null || wallpaperColors == mWallpaperColors) return

        val config = context.resources.configuration
        val currentNightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkThemeOn = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        val colorScheme = ColorScheme(wallpaperColors, isDarkThemeOn)
        val newMediaArtColor = if (isDarkThemeOn) {
            colorScheme.accent1.s100
        } else {
            colorScheme.accent1.s800
        }

        if (currMediaArtColor != newMediaArtColor) {
            currMediaArtColor = newMediaArtColor
            mCurrentColorScheme = colorScheme
            mWallpaperColors = wallpaperColors
            notifyListeners { it.onMediaColorsChanged() }
        }
    }

    fun getColorScheme(): ColorScheme? {
        return mCurrentColorScheme
    }

    fun addMediaMetadataListener(listener: MediaMetadataListener?) {
        listener?.let {
            val wasEmpty = listeners.isEmpty()
            listeners.add(it)
            if (wasEmpty) {
                startPeriodicUpdate()
            }
            notifyListeners()
        }
    }
    
    fun removeMediaMetadataListener(listener: MediaMetadataListener?) {
        listener?.let {
            listeners.remove(it)
            if (listeners.isEmpty()) {
                updateJob?.cancel()
                activeController?.unregisterCallback(mediaControllerCallback)
                activeController = null
                mediaMetadata = null
            }
        }
    }

    private fun notifyListeners(action: (MediaMetadataListener) -> Unit) {
        for (listener in listeners) {
            action(listener)
        }
    }

    private fun notifyListeners() {
        // Store the last used media package name
        saveLastNonNullPackageName()
        listeners.forEach {
            it.onMediaMetadataChanged()
            it.onPlaybackStateChanged()
            it.onMediaColorsChanged()
        }
    }

    fun seekTo(time: Long) {
        val controller = getActiveLocalMediaController()
        controller?.transportControls?.seekTo(time)
    }

    fun getTotalDuration(): Long {
        val metadata = getMediaMetadata()
        return metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    private fun saveLastNonNullPackageName() {
        val packageName = getActiveLocalMediaController()?.packageName
        if (!TextUtils.isEmpty(packageName) && packageName != lastSavedPackageName) {
            Settings.System.putString(
                context.contentResolver,
                "media_session_last_package_name",
                packageName
            )
            lastSavedPackageName = packageName
        }
    }

    fun updateMediaController() {
        val localController = getActiveLocalMediaController()
        if (localController != null && !sameSessions(activeController, localController)) {
            activeController?.unregisterCallback(mediaControllerCallback)
            activeController = localController
            activeController?.registerCallback(mediaControllerCallback)
            notifyListeners()
        }
    }

    private fun getActiveLocalMediaController(): MediaController? {
        var localController: MediaController? = null
        val remoteMediaSessionLists = mutableListOf<String>()
        if (mediaSessionManager != null) {
            for (controller in mediaSessionManager.getActiveSessions(null)) {
                val playbackInfo = controller.playbackInfo
                if (playbackInfo == null) {
                    // do nothing
                    continue
                }
                val playbackState = controller.playbackState
                if (playbackState == null) {
                    // do nothing
                    continue
                }
                if (playbackState.state != PlaybackState.STATE_PLAYING) {
                    // do nothing
                    continue
                }
                if (playbackInfo.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                    if (localController != null && localController.packageName == controller.packageName) {
                        localController = null
                    }
                    if (!remoteMediaSessionLists.contains(controller.packageName)) {
                        remoteMediaSessionLists.add(controller.packageName)
                    }
                    continue
                }
                if (playbackInfo.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
                    if (localController == null && !remoteMediaSessionLists.contains(controller.packageName)) {
                        localController = controller
                    }
                }
            }
        }
        return localController
    }
    
    fun getMediaBitmap(): Bitmap? {
        val metadata = getMediaMetadata()
        return metadata?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
    }

    fun getMediaMetadata(): MediaMetadata? {
        return getActiveLocalMediaController()?.metadata
    }
    
    fun getMediaColor(): Int {
        return currMediaArtColor
    }

    fun isMediaControllerAvailable(): Boolean {
        return getActiveLocalMediaController() != null &&
            !TextUtils.isEmpty(getActiveLocalMediaController()?.packageName)
    }

    fun isMediaPlaying(): Boolean {
        return isMediaControllerAvailable() &&
            getMediaControllerPlaybackState(getActiveLocalMediaController()) == PlaybackState.STATE_PLAYING
    }

    fun getMediaControllerPlaybackState(controller: MediaController?): Int {
        return controller?.playbackState?.state ?: PlaybackState.STATE_NONE
    }

    fun getMediaControllerPlaybackState(): PlaybackState? {
        val controller = getActiveLocalMediaController()
        return controller?.playbackState ?: null
    }

    private fun sameSessions(a: MediaController?, b: MediaController?): Boolean {
        if (a == b) return true
        if (a == null) return false
        return a.controlsSameSession(b)
    }

    private fun dispatchMediaKeyWithWakeLockToMediaSession(keycode: Int) {
        val helper = MediaSessionLegacyHelper.getHelper(context) ?: return
        var event = KeyEvent(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_DOWN,
            keycode,
            0
        )
        helper.sendMediaButtonEvent(event, true)
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP)
        helper.sendMediaButtonEvent(event, true)
    }

    fun prevSong() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun nextSong() {
        dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun toggleMediaPlaybackState() {
        if (isMediaPlaying()) {
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            dispatchMediaKeyWithWakeLockToMediaSession(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }
    
    fun showMediaDialog(view: View) {
        val packageName = lastSavedPackageName?.takeIf { it.isNotEmpty() } ?: return
        Dependency.get(MediaOutputDialogManager::class.java)
            .createAndShowWithController(
                packageName,
                true,
                Expandable.fromView(view).dialogController()
            )
    }
    
    private fun Expandable.dialogController(): DialogTransitionAnimator.Controller? {
        return dialogTransitionController(
            cuj =
                DialogCuj(
                    InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                    MediaOutputDialogManager.INTERACTION_JANK_TAG
                )
        )
    }

    companion object {
        @Volatile
        private var instance: MediaSessionManagerHelper? = null
        fun getInstance(context: Context): MediaSessionManagerHelper {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionManagerHelper(context).also { instance = it }
            }
        }
    }
}
