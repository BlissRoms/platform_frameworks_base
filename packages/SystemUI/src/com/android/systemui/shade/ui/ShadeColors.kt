/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.shade.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.content.Context
import android.graphics.Color
import android.provider.Settings
import com.android.internal.graphics.ColorUtils
import com.android.systemui.res.R

object ShadeColors {
    @JvmStatic
    fun Resources.shadePanel(blurSupported: Boolean, context: Context): Int {
        return if (blurSupported) {
            shadePanelStandard(context)
        } else {
            shadePanelFallback()
        }
    }

    @JvmStatic
    fun Resources.notificationScrim(blurSupported: Boolean, context: Context): Int {
        return if (blurSupported) {
            notificationScrimStandard()
        } else {
            notificationScrimFallback()
        }
    }

    private fun Resources.isNightModeActive(): Boolean {
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    private fun Resources.shadePanelStandard(context: Context): Int {
        return if (isNightModeActive()) {
            shadePanelStandardDark(context)
        } else {
            shadePanelStandardLight(context)
        }
    }

    private fun Resources.shadePanelStandardLight(context: Context): Int {
        val useDualTone = if (context != null) {
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.QS_DUAL_TONE, 1) == 1
            } catch (e: Exception) {
                true // fallback to default
            }
        } else {
            true // fallback to default when context is null
        }

        val topLayerAlpha = 0.75f

        val layerAbove = ColorUtils.setAlphaComponent(
            getColor(R.color.shade_panel_base, null),
            (topLayerAlpha * 255).toInt()
        )

        val layerBelow = if (useDualTone) {
            ColorUtils.setAlphaComponent(Color.WHITE, (0.1f * 255).toInt())
        } else {
            val colorBase = getColor(R.color.shade_panel_base_color, null)
            ColorUtils.setAlphaComponent(colorBase, (0.1f * 255).toInt())
        }

        return ColorUtils.compositeColors(layerAbove, layerBelow)
    }

    private fun Resources.shadePanelStandardDark(context: Context): Int {
        val useDualTone = if (context != null) {
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.QS_DUAL_TONE, 1) == 1
            } catch (e: Exception) {
                true // fallback to default
            }
        } else {
            true // fallback to default when context is null
        }

        val topLayerAlpha = 0.8f

        val layerAbove = ColorUtils.setAlphaComponent(
            getColor(R.color.shade_panel_base, null),
            (topLayerAlpha * 255).toInt()
        )

        val layerBelow = if (useDualTone) {
            ColorUtils.setAlphaComponent(Color.WHITE, (0.05f * 255).toInt())
        } else {
            val colorBase = getColor(R.color.shade_panel_base_color, null)
            ColorUtils.setAlphaComponent(colorBase, (0.1f * 255).toInt())
        }

        return ColorUtils.compositeColors(layerAbove, layerBelow)
    }

    @JvmStatic
    private fun Resources.shadePanelFallback(): Int {
        return ColorUtils.blendARGB(getColor(R.color.nt_scrim_behind_1), getColor(R.color.nt_scrim_behind_2), 0.5f)
    }

    @JvmStatic
    private fun Resources.notificationScrimStandard(): Int {
        return if (isNightModeActive()) {
            notificationScrimStandardDark()
        } else {
            notificationScrimStandardLight()
        }
    }

    private fun Resources.notificationScrimStandardLight(): Int {
        return ColorUtils.setAlphaComponent(
            getColor(R.color.notification_scrim_base, null),
            (0.6f * 255).toInt(),
        )
    }

    private fun Resources.notificationScrimStandardDark(): Int {
        return ColorUtils.setAlphaComponent(
            getColor(R.color.notification_scrim_base, null),
            (0.65f * 255).toInt(),
        )
    }

    @JvmStatic
    private fun Resources.notificationScrimFallback(): Int {
        return getColor(R.color.notification_scrim_fallback, null)
    }
}
