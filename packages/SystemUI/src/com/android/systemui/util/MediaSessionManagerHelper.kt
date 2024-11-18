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

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.text.TextUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main

class MediaSessionManagerHelper private constructor(private val context: Context) {

    interface MediaMetadataListener {
        fun onMediaMetadataChanged()
        fun onPlaybackStateChanged()
    }

    private val mediaSessionManager: MediaSessionManager = context.getSystemService(MediaSessionManager::class.java)!!
    private var activeController: MediaController? = null
    private val listeners: MutableSet<MediaMetadataListener> = mutableSetOf()
    private var mediaMetadata: MediaMetadata? = null

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

    private fun startPeriodicUpdate() {
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateMediaController()
                delay(1000)
            }
        }
    }

    fun addMediaMetadataListener(listener: MediaMetadataListener?) {
        listener?.let {
            val wasEmpty = listeners.isEmpty()
            listeners.add(it)
            if (wasEmpty) {
                startPeriodicUpdate()
                notifyListeners()
            }
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
        listeners.forEach {
            it.onMediaMetadataChanged()
            it.onPlaybackStateChanged()
        }
    }

    fun updateMediaController() {
        val localController = getActiveLocalMediaController()
        if (localController != null && !sameSessions(activeController, localController)) {
            activeController?.unregisterCallback(mediaControllerCallback)
            activeController = localController
            activeController?.registerCallback(mediaControllerCallback)
            mediaMetadata = activeController?.metadata
            notifyListeners()
        }
    }

    fun getActiveLocalMediaController(): MediaController? {
        val controllers = mediaSessionManager.getActiveSessions(null) ?: return null
        val remoteMediaSessionLists = mutableListOf<String>()
        var localController: MediaController? = null
        for (controller in controllers) {
            val playbackInfo = controller.playbackInfo ?: continue
            val playbackState = controller.playbackState ?: continue
            if (playbackState.state != PlaybackState.STATE_PLAYING) continue
            if (playbackInfo.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                if (localController?.packageName == controller.packageName) {
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
        return localController
    }

    fun getMediaMetadata(): MediaMetadata? {
        return mediaMetadata
    }

    fun isMediaControllerAvailable(): Boolean {
        return getActiveLocalMediaController() != null &&
            !TextUtils.isEmpty(getActiveLocalMediaController()?.packageName)
    }

    fun isMediaPlaying(): Boolean {
        return isMediaControllerAvailable() &&
            getMediaControllerPlaybackState(activeController) == PlaybackState.STATE_PLAYING
    }

    private fun getMediaControllerPlaybackState(controller: MediaController?): Int {
        return controller?.playbackState?.state ?: PlaybackState.STATE_NONE
    }

    private fun sameSessions(a: MediaController?, b: MediaController?): Boolean {
        if (a == b) return true
        if (a == null || b == null) return false
        return a.controlsSameSession(b)
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
