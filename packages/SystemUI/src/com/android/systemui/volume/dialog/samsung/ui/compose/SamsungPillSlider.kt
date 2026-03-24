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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

@Composable
fun SamsungPillSlider(
    viewModel: VolumeDialogSliderViewModel,
    sliderWidth: Dp = 56.dp,
    sliderHeight: Dp = 200.dp,
    showIcon: Boolean = true,
    onExpandClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val collectedState by viewModel.state.collectAsStateWithLifecycle(null)
    val state = collectedState ?: return

    val range = state.valueRange
    val fraction = if (range.endInclusive > range.start) {
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else 0f

    var isDragging by remember { mutableStateOf(false) }
    var currentFraction by remember { mutableFloatStateOf(fraction) }
    if (!isDragging) {
        currentFraction = fraction
    }

    val animatedFraction by animateFloatAsState(
        targetValue = currentFraction,
        animationSpec = tween(durationMillis = if (isDragging) 0 else 250),
        label = "sliderFill",
    )

    val trackColor = Color.White.copy(alpha = 0.15f)
    val fillColor = Color.White.copy(alpha = 0.65f)
    val pillShape = RoundedCornerShape(50)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(sliderWidth)
                .height(sliderHeight)
                .clip(pillShape)
                .background(trackColor)
                .pointerInput(range) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            viewModel.onSliderDragStarted()
                        },
                        onDragEnd = {
                            isDragging = false
                            viewModel.onSliderDragFinished()
                        },
                        onDragCancel = {
                            isDragging = false
                            viewModel.onSliderDragFinished()
                        },
                    ) { _, dragAmount ->
                        val delta = -dragAmount / size.height.toFloat()
                        currentFraction = (currentFraction + delta).coerceIn(0f, 1f)
                        val newValue = range.start +
                            currentFraction * (range.endInclusive - range.start)
                        viewModel.setStreamVolume(
                            newValue.coerceIn(range.start, range.endInclusive),
                            true,
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val fillTop = size.height * (1f - animatedFraction)
                        clipRect(
                            left = 0f,
                            top = fillTop,
                            right = size.width,
                            bottom = size.height,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .background(fillColor),
            )

            if (onExpandClicked != null) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .size(20.dp)
                        .clickable { onExpandClicked() },
                )
            }

            if (showIcon) {
                Icon(
                    icon = state.icon,
                    tint = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(22.dp),
                )
            }
        }
    }
}
