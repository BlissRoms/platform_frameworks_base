/*
 * SPDX-FileCopyrightText: The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.view.layout

import android.os.UserHandle
import android.provider.Settings
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.ui.view.layout.blueprints.transitions.IntraBlueprintTransition.Config
import com.android.systemui.keyguard.ui.view.layout.blueprints.transitions.IntraBlueprintTransition.Type
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSliceViewSection
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardWeatherViewSection
import com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import com.android.systemui.util.settings.SystemSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

@SysUISingleton
class KeyguardWeatherLayoutRefresher
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val secureSettings: SecureSettings,
    private val systemSettings: SystemSettings,
    private val smartspaceController: LockscreenSmartspaceController,
    private val blueprintInteractor: KeyguardBlueprintInteractor,
    private val smartspaceSection: SmartspaceSection,
    private val sliceViewSection: KeyguardSliceViewSection,
    private val weatherViewSection: KeyguardWeatherViewSection,
) : CoreStartable {

    private data class WeatherSource(val isSmartspace: Boolean, val isOmniWeather: Boolean)

    override fun start() {
        val rebuildConfig =
            Config(
                Type.NoTransition,
                rebuildSections = listOf(smartspaceSection, sliceViewSection, weatherViewSection),
            )
        applicationScope.launch("$TAG#weatherSource") {
            merge(
                    secureSettings.observerFlow(
                        UserHandle.USER_ALL,
                        Settings.Secure.LOCKSCREEN_SMARTSPACE_ENABLED,
                    ),
                    systemSettings.observerFlow(
                        UserHandle.USER_ALL,
                        Settings.System.LOCKSCREEN_WEATHER_ENABLED,
                    ),
                )
                .onStart { emit(Unit) }
                .map { currentWeatherSource() }
                .distinctUntilChanged()
                .drop(1)
                .flowOn(backgroundDispatcher)
                .collect { blueprintInteractor.refreshBlueprint(rebuildConfig) }
        }
    }

    private fun currentWeatherSource() =
        WeatherSource(
            isSmartspace = smartspaceController.isEnabled,
            isOmniWeather = smartspaceController.isOmniWeatherEnabled,
        )

    private companion object {
        const val TAG = "KeyguardWeatherLayoutRefresher"
    }
}
