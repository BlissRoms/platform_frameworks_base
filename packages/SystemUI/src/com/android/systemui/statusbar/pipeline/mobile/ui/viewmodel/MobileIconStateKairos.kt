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

import androidx.compose.runtime.getValue
import com.android.systemui.activateIn
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription
import com.android.systemui.util.composable.kairos.hydratedComposeStateOf
import com.android.systemui.util.lifecycle.kairos.kairosBuilder

class MobileIconStateKairos(
    viewModel: MobileIconViewModelKairos,
    private val kairosNetwork: KairosNetwork,
) : MobileIconState, ExclusiveActivatable() {

    private val builder = kairosBuilder()

    override val isVisible: Boolean by
        builder.hydratedComposeStateOf("isVisible", viewModel.isVisible, false)
    override val icon: SignalIconModel by
        builder.hydratedComposeStateOf("icon", viewModel.icon, SignalIconModel.DEFAULT)
    override val contentDescription: MobileContentDescription? by
        builder.hydratedComposeStateOf("contentDescriptions", viewModel.contentDescription, null)
    override val roaming: Boolean by
        builder.hydratedComposeStateOf("roaming", viewModel.roaming, false)
    override val networkTypeIcon: Icon.Resource? by
        builder.hydratedComposeStateOf("networkTypeIcon", viewModel.networkTypeIcon, null)
    override val networkTypeBackground: Icon.Resource? by
        builder.hydratedComposeStateOf(
            "networkTypeBackground",
            viewModel.networkTypeBackground,
            null,
        )
    override val activityInVisible: Boolean by
        builder.hydratedComposeStateOf("activityInVisible", viewModel.activityInVisible, false)
    override val activityOutVisible: Boolean by
        builder.hydratedComposeStateOf("activityOutVisible", viewModel.activityOutVisible, false)

    override val activityContainerVisible: Boolean by
        builder.hydratedComposeStateOf(
            "activityContainerVisible",
            viewModel.activityContainerVisible,
            false,
        )
    override val showHd: Boolean by
        builder.hydratedComposeStateOf("showHd", viewModel.showHd, false)

    override suspend fun onActivated() {
        builder.activateIn(kairosNetwork)
    }
}
