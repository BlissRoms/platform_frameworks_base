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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.volume.dialog.samsung.ui.viewmodel.SamsungVolumePanelViewModel

@Composable
fun SamsungVolumePanel(
    viewModel: SamsungVolumePanelViewModel,
    isOnLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    val isExpanded by viewModel.isExpanded.collectAsStateWithLifecycle()
    val activeComponent by viewModel.activeSliderComponent.collectAsStateWithLifecycle(null)
    val activeLabel by viewModel.activeStreamLabel.collectAsStateWithLifecycle("")
    val ringerMode by viewModel.ringerMode.collectAsStateWithLifecycle()

    BackHandler(enabled = isExpanded) {
        viewModel.onBackPressed()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.95f))
                    .togetherWith(fadeOut() + scaleOut(targetScale = 0.95f))
            },
            label = "samsung_volume_panel",
        ) { expanded ->
            if (expanded) {
                SamsungExpandedPanel(
                    sliderComponents = viewModel.expandedSliderComponents,
                    activeLabel = activeLabel,
                    ringerMode = ringerMode,
                    onMuteClicked = { viewModel.onMuteClicked() },
                    onSettingsClicked = { viewModel.onSettingsClicked() },
                    onDismiss = { viewModel.onCollapseRequested() },
                )
            } else {
                val component = activeComponent
                if (component != null) {
                    Box(
                        contentAlignment = if (isOnLeft) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.onDismissRequested() },
                    ) {
                        SamsungCollapsedSlider(
                            viewModel = component.sliderViewModel(),
                            onExpandClicked = { viewModel.onExpandClicked() },
                            isOnLeft = isOnLeft,
                        )
                    }
                }
            }
        }
    }
}
