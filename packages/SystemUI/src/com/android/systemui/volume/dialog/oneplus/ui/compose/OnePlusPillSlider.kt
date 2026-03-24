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

package com.android.systemui.volume.dialog.oneplus.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel

@Composable
fun OnePlusPillSlider(
    viewModel: VolumeDialogSliderViewModel,
    sliderWidth: Dp = 64.dp,
    sliderHeight: Dp = 200.dp,
    modifier: Modifier = Modifier,
) {
    val collectedState by viewModel.state.collectAsStateWithLifecycle(null)
    val state = collectedState ?: return

    val range = state.valueRange
    val fraction = if (range.endInclusive > range.start) {
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else 0f

    var currentFraction by remember { mutableFloatStateOf(fraction) }
    if (!state.isDisabled) {
        currentFraction = fraction
    }

    val sliderShape = RoundedCornerShape(24.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .width(sliderWidth)
                .height(sliderHeight)
                .clip(sliderShape)
                .background(Color.White.copy(alpha = 0.15f))
                .pointerInput(range) {
                    var dragStartFraction = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragStartFraction = currentFraction
                            viewModel.onSliderDragStarted()
                        },
                        onDragEnd = { viewModel.onSliderDragFinished() },
                        onDragCancel = { viewModel.onSliderDragFinished() },
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
                    .fillMaxWidth()
                    .height(sliderHeight * currentFraction)
                    .background(Color.White),
            )

            Icon(
                icon = state.icon,
                tint = null,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
