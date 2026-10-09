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

package com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter

/** Models a per-app volume slider. Lives here because [SliderState] is sealed. */
data class AppVolumeSliderState(
    val packageName: String,
    val appIcon: Icon.Loaded,
    override val value: Float,
    override val icon: Icon.Loaded,
    override val label: String,
    override val a11yStateDescription: String,
    override val a11yContentDescription: String,
) : SliderState {
    override val valueRange: ClosedFloatingPointRange<Float> = 0f..1f
    override val step: Float = A11Y_STEP
    override val hapticFilter = SliderHapticFeedbackFilter()
    override val isEnabled: Boolean = true
    override val a11yClickDescription: String? = null
    override val disabledMessage: String? = null
    override val isMutable: Boolean = false

    private companion object {
        const val A11Y_STEP = 0.05f
    }
}
