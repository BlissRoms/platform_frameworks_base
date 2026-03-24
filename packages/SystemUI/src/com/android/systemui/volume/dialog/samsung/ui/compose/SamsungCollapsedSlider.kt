/*
 * Copyright (C) 2014-2026 The BlissRoms Project
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

package com.android.systemui.volume.dialog.samsung.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

@Composable
fun SamsungCollapsedSlider(
    viewModel: VolumeDialogSliderViewModel,
    onExpandClicked: () -> Unit,
    isOnLeft: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sliderWidth = dimensionResource(R.dimen.volume_dialog_samsung_collapsed_slider_width)
    val sliderHeight = dimensionResource(R.dimen.volume_dialog_samsung_collapsed_slider_height)
    val verticalPadding = dimensionResource(R.dimen.volume_dialog_samsung_collapsed_vertical_padding)

    val edgePadding = if (isOnLeft) {
        Modifier.padding(start = 16.dp, top = verticalPadding, bottom = verticalPadding)
    } else {
        Modifier.padding(end = 16.dp, top = verticalPadding, bottom = verticalPadding)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(edgePadding),
    ) {
        SamsungPillSlider(
            viewModel = viewModel,
            sliderWidth = sliderWidth,
            sliderHeight = sliderHeight,
            showIcon = true,
            onExpandClicked = onExpandClicked,
        )
    }
}
