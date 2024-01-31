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
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.model.MobileContentDescription

class MobileIconStateImpl(viewModel: MobileIconViewModelCommon) :
    MobileIconState,
    HydratedActivatable(traceName = "MobileIconStateImpl[${viewModel.subscriptionId}].hydrator") {

    override val isVisible: Boolean by viewModel.isVisible.hydratedStateOf()
    override val icon: SignalIconModel by viewModel.icon.hydratedStateOf(SignalIconModel.DEFAULT)
    override val contentDescription: MobileContentDescription? by
        viewModel.contentDescription.hydratedStateOf(null)
    override val roaming: Boolean by viewModel.roaming.hydratedStateOf(false)
    override val networkTypeIcon: Icon.Resource? by viewModel.networkTypeIcon.hydratedStateOf(null)
    override val networkTypeBackground: Icon.Resource? by
        viewModel.networkTypeBackground.hydratedStateOf()
    override val activityInVisible: Boolean by viewModel.activityInVisible.hydratedStateOf(false)
    override val activityOutVisible: Boolean by viewModel.activityOutVisible.hydratedStateOf(false)
    override val activityContainerVisible: Boolean by
        viewModel.activityContainerVisible.hydratedStateOf(false)
    override val showHd: Boolean by viewModel.showHd.hydratedStateOf(false)
}
