/*
 * Copyright (C) 2026 BlissRoms
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

package com.android.systemui.volume.appvolume.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.provider.Settings
import android.util.Log
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AppVolumeSliderState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Models the per-app volume panel: one slider for each app that is currently playing audio. */
class AppVolumePanelViewModel
@AssistedInject
constructor(
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onDismiss: () -> Unit,
    @Application private val context: Context,
    @Background private val backgroundContext: CoroutineContext,
    private val audioManager: AudioManager,
    private val activityStarter: ActivityStarter,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {

    private val volumeOnIcon: Icon.Loaded by lazy { loadIcon(R.drawable.ic_volume_media) }
    private val volumeOffIcon: Icon.Loaded by lazy { loadIcon(R.drawable.ic_volume_media_mute) }

    private val volumeOverrides = MutableStateFlow<Map<String, Float>>(emptyMap())

    private val activeApps: StateFlow<List<AppModel>> =
        conflatedCallbackFlow {
                val callback =
                    object : AudioManager.AudioPlaybackCallback() {
                        override fun onPlaybackConfigChanged(
                            configs: List<AudioPlaybackConfiguration>
                        ) {
                            trySend(Unit)
                        }
                    }
                audioManager.registerAudioPlaybackCallback(callback, null)
                trySend(Unit)
                awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
            }
            .map { loadActiveApps() }
            .flowOn(backgroundContext)
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val sliders: StateFlow<List<AppVolumeSliderState>> =
        combine(activeApps, volumeOverrides) { apps, overrides ->
                apps.map { app -> app.toSliderState(overrides[app.packageName] ?: app.volume) }
            }
            .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    init {
        coroutineScope.launch(backgroundContext) {
            var applied = emptyMap<String, Float>()
            volumeOverrides.collect { overrides ->
                overrides
                    .filter { (packageName, volume) -> applied[packageName] != volume }
                    .forEach { (packageName, volume) ->
                        audioManager.setAppVolume(packageName, volume)
                    }
                applied = overrides
            }
        }
    }

    fun onValueChanged(state: AppVolumeSliderState, newValue: Float) {
        volumeOverrides.update { it + (state.packageName to newValue) }
    }

    fun getSliderHapticsViewModelFactory(): SliderHapticsViewModel.Factory =
        hapticsViewModelFactory

    fun onSettingsClicked() {
        activityStarter.startActivityDismissingKeyguard(
            /* intent = */ Intent(Settings.ACTION_SOUND_SETTINGS),
            /* onlyProvisioned = */ false,
            /* dismissShade = */ true,
            /* disallowEnterPictureInPictureWhileLaunching = */ false,
            /* callback = */ { onDismiss() },
            /* flags = */ Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            /* animationController = */ null,
            /* userHandle = */ null,
        )
    }

    fun onDoneClicked() {
        onDismiss()
    }

    private fun loadActiveApps(): List<AppModel> =
        audioManager
            .listAppVolumes()
            .filter { it.isActive }
            .distinctBy { it.packageName }
            .mapNotNull { loadApp(it.packageName, it.volume) }

    private fun loadApp(packageName: String, volume: Float): AppModel? {
        val packageManager = context.packageManager
        return try {
            val appInfo =
                packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ANY_USER)
            val label = appInfo.loadLabel(packageManager).toString()
            AppModel(
                packageName = packageName,
                label = label,
                icon =
                    packageManager
                        .getApplicationIcon(appInfo)
                        .asIcon(ContentDescription.Loaded(label)),
                volume = volume,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to load app info for $packageName", e)
            null
        }
    }

    private fun AppModel.toSliderState(value: Float): AppVolumeSliderState =
        AppVolumeSliderState(
            packageName = packageName,
            appIcon = icon,
            value = value,
            icon = trackIcon(value),
            label = label,
            a11yStateDescription =
                context.getString(R.string.app_volume_panel_percent, (value * 100).roundToInt()),
            a11yContentDescription = label,
        )

    private fun trackIcon(value: Float): Icon.Loaded =
        if (value == 0f) volumeOffIcon else volumeOnIcon

    private fun loadIcon(resId: Int): Icon.Loaded =
        requireNotNull(context.getDrawable(resId)).asIcon(resId = resId)

    private data class AppModel(
        val packageName: String,
        val label: String,
        val icon: Icon.Loaded,
        val volume: Float,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onDismiss: () -> Unit,
        ): AppVolumePanelViewModel
    }

    private companion object {
        const val TAG = "AppVolumePanelViewModel"
    }
}
