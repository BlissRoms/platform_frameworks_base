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

package com.android.systemui.volume.appvolume.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.PlatformSliderDefaults
import com.android.systemui.common.ui.compose.Icon as SysUiIcon
import com.android.systemui.res.R
import com.android.systemui.volume.appvolume.ui.viewmodel.AppVolumePanelViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AppVolumeSliderState
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider

private val PanelPadding = 24.dp
private val OuterCornerRadius = 24.dp
private val InnerCornerRadius = 4.dp
private val SegmentGap = 2.dp
private val AppIconSize = 40.dp
private val ListMaxHeight = 420.dp

@Composable
fun AppVolumePanel(viewModel: AppVolumePanelViewModel, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.app_volume_panel_title)
    val sliders by viewModel.sliders.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .semantics { paneTitle = title }
                .padding(start = PanelPadding, top = PanelPadding, end = PanelPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(title = title, activeCount = sliders.size)
        if (sliders.isEmpty()) {
            EmptyState()
        } else {
            AppSliders(
                sliders = sliders,
                viewModel = viewModel,
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
        }
        BottomBar(viewModel = viewModel)
    }
}

@Composable
private fun Header(title: String, activeCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                if (activeCount == 0) {
                    stringResource(R.string.app_volume_panel_subtitle_idle)
                } else {
                    pluralStringResource(
                        R.plurals.app_volume_panel_subtitle_active,
                        activeCount,
                        activeCount,
                    )
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppSliders(
    sliders: List<AppVolumeSliderState>,
    viewModel: AppVolumePanelViewModel,
    modifier: Modifier = Modifier,
) {
    val sliderColors = appVolumeSliderColors()
    LazyColumn(
        modifier = modifier.heightIn(max = ListMaxHeight),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        itemsIndexed(items = sliders, key = { _, state -> state.packageName }) { index, state ->
            AppSliderRow(
                state = state,
                shape = segmentShape(index = index, count = sliders.size),
                sliderColors = sliderColors,
                viewModel = viewModel,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun AppSliderRow(
    state: AppVolumeSliderState,
    shape: Shape,
    sliderColors: SliderColors,
    viewModel: AppVolumePanelViewModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SysUiIcon(
            icon = state.appIcon,
            tint = Color.Unspecified,
            modifier = Modifier.size(AppIconSize),
        )
        VolumeSlider(
            state = state,
            onValueChange = { newValue -> viewModel.onValueChanged(state, newValue) },
            onIconTapped = {},
            sliderColors = PlatformSliderDefaults.defaultPlatformSliderColors(),
            hapticsViewModelFactory = viewModel.getSliderHapticsViewModelFactory(),
            materialSliderColors = sliderColors,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EmptyState() {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(OuterCornerRadius),
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_music_note_off),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(R.string.app_volume_panel_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BottomBar(viewModel: AppVolumePanelViewModel) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformOutlinedButton(onClick = viewModel::onSettingsClicked) {
            Text(text = stringResource(R.string.volume_panel_dialog_settings_button))
        }
        PlatformButton(onClick = viewModel::onDoneClicked) {
            Text(text = stringResource(R.string.inline_done_button))
        }
    }
}

@Composable
private fun appVolumeSliderColors(): SliderColors {
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    return SliderDefaults.colors()
        .copy(
            activeTickColor = inactive,
            inactiveTrackColor = inactive,
            disabledActiveTickColor = inactive,
            disabledInactiveTrackColor = inactive,
        )
}

private fun segmentShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OuterCornerRadius else InnerCornerRadius
    val bottom = if (index == count - 1) OuterCornerRadius else InnerCornerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
