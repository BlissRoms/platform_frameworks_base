/*
 * Copyright (C) 2024-2026 crDroid Android Project
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import javax.inject.Inject

import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.weather.WeatherInfoView

class KeyguardWeatherViewSection
@Inject
constructor(
    private val context: Context,
    val layoutInflater: LayoutInflater,
    val smartspaceController: LockscreenSmartspaceController,
    val keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel,
) : KeyguardSection() {
    private lateinit var weatherView: WeatherInfoView

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!smartspaceController.isOmniWeatherEnabled || smartspaceController.isEnabled) return

        weatherView =
            layoutInflater.inflate(R.layout.keyguard_weather_area, null, false) as WeatherInfoView
        constraintLayout.addView(weatherView)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        if (!smartspaceController.isOmniWeatherEnabled || smartspaceController.isEnabled) return

        weatherView.init()
    }

    private fun hasExtraWeatherOptions(): Boolean {
        val resolver = context.contentResolver
        val showLocation = Settings.System.getIntForUser(
            resolver, "lockscreen_weather_location", 0, UserHandle.USER_CURRENT
        ) != 0
        val showText = Settings.System.getIntForUser(
            resolver, "lockscreen_weather_text", 1, UserHandle.USER_CURRENT
        ) != 0
        val showWind = Settings.System.getIntForUser(
            resolver, "lockscreen_weather_wind_info", 0, UserHandle.USER_CURRENT
        ) != 0
        val showHumidity = Settings.System.getIntForUser(
            resolver, "lockscreen_weather_humidity_info", 0, UserHandle.USER_CURRENT
        ) != 0
        return showLocation || showText || showWind || showHumidity
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!smartspaceController.isOmniWeatherEnabled || smartspaceController.isEnabled) return

        val dateWeatherPaddingStart = KeyguardSmartspaceViewModel.getDateWeatherStartMargin(context)
        val endGuideline = if (keyguardSmartspaceViewModel.isFullWidthShade.value) {
            ConstraintSet.PARENT_ID
        } else {
            R.id.split_shade_guideline
        }

        val useOwnLine = hasExtraWeatherOptions()

        constraintSet.apply {
            constrainWidth(R.id.keyguard_weather_area, ConstraintSet.WRAP_CONTENT)
            constrainHeight(R.id.keyguard_weather_area, ConstraintSet.WRAP_CONTENT)

            if (useOwnLine) {
                connect(
                    R.id.keyguard_weather_area,
                    ConstraintSet.START,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.START,
                    dateWeatherPaddingStart,
                )
                connect(
                    R.id.keyguard_weather_area,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                )
            } else {
                connect(
                    R.id.keyguard_weather_area,
                    ConstraintSet.START,
                    R.id.keyguard_slice_view,
                    ConstraintSet.END,
                    context.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_base_action_icon_margin),
                )
                connect(
                    R.id.keyguard_weather_area,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.TOP
                )
                connect(
                    R.id.keyguard_weather_area,
                    ConstraintSet.BOTTOM,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM
                )
            }

            connect(
                R.id.keyguard_weather_area,
                ConstraintSet.END,
                endGuideline,
                ConstraintSet.END,
                dateWeatherPaddingStart,
            )
            setHorizontalBias(R.id.keyguard_weather_area, 0f)
            constrainedWidth(R.id.keyguard_weather_area, true)

            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(R.id.keyguard_slice_view, R.id.keyguard_weather_area)
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        if (!smartspaceController.isOmniWeatherEnabled || smartspaceController.isEnabled) return

        constraintLayout.findViewById<WeatherInfoView?>(R.id.keyguard_weather_area)?.let { weatherArea ->
            weatherArea.cleanup()
            constraintLayout.removeView(weatherArea)
        }
    }
}
