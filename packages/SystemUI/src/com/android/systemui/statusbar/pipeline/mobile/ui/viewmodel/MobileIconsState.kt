/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription

/** View state for describing the system's current mobile cellular connections. */
interface MobileIconsState : Activatable {
    val isStackable: Boolean
    val mobileSubViewModels: List<MobileIconState.Factory>

    fun interface Factory {
        fun create(): MobileIconsState
    }
}

/** Common interface for all of the location-based mobile icon view states. */
interface MobileIconState : Activatable {
    /** True if this view should be visible at all. */
    val isVisible: Boolean
    val icon: SignalIconModel
    val contentDescription: MobileContentDescription?
    val roaming: Boolean
    /** The RAT icon (LTE, 3G, 5G, etc) to be displayed. Null if we shouldn't show anything */
    val networkTypeIcon: Icon.Resource?
    /** The slice attribution. Drawn as a background layer */
    val networkTypeBackground: Icon.Resource?
    val activityInVisible: Boolean
    val activityOutVisible: Boolean
    val activityContainerVisible: Boolean
    val showHd: Boolean

    interface Factory {
        val subscriptionId: Int

        fun create(): MobileIconState
    }
}
