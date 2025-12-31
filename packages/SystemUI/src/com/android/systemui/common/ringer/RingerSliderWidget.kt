/*
 * Copyright (C) 2025 AxionOS
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
package com.android.systemui.common.ringer

import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.gestures.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun RingerSliderWidget(
    interactor: RingerModeInteractor,
    theme: RingerSliderTheme,
    dimens: RingerSliderDimens,
    modifier: Modifier = Modifier,
    isDozing: Boolean = false,
    border: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val mode by interactor.ringerMode.collectAsState(initial = interactor.getCurrentMode())
    val isDndEnabled by interactor.dndMode.collectAsState(initial = interactor.isDndEnabled())
    
    val targetPosition = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> 0f
        AudioManager.RINGER_MODE_VIBRATE -> 1f
        AudioManager.RINGER_MODE_SILENT -> 2f
        else -> 0f
    }

    var dragOffset by remember { mutableStateOf(targetPosition) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedPosition by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else targetPosition,
        animationSpec = tween(
            durationMillis = 250,
            easing = LinearOutSlowInEasing
        ),
        label = "ringer_position"
    )

    LaunchedEffect(targetPosition) {
        if (!isDragging) dragOffset = targetPosition
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(dimens.thumbSize)
            .background(
                when {
                    isDozing -> Color.Transparent
                    isDndEnabled -> theme.dndBg
                    else -> theme.neutralBg
                },
                RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isDozing)
                    Modifier.border(theme.dozeStroke, Color.White, RoundedCornerShape(24.dp))
                else border
            )
.pointerInput(Unit) {
    detectTapGestures(
        onTap = { tapOffset ->
            if (isDndEnabled) return@detectTapGestures
            val sectionWidth = size.width / 3f

            val snappedIndex = when {
                tapOffset.x < sectionWidth -> 0
                tapOffset.x < sectionWidth * 2 -> 1
                else -> 2
            }

            dragOffset = snappedIndex.toFloat()

            val snappedMode = when (snappedIndex) {
                0 -> AudioManager.RINGER_MODE_NORMAL
                1 -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_SILENT
            }

            interactor.setRingerMode(snappedMode)
        },
        onLongPress = {
            onLongClick?.invoke()
        }
    )
}
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        val snappedMode = when {
                            dragOffset < 0.5f -> AudioManager.RINGER_MODE_NORMAL
                            dragOffset < 1.5f -> AudioManager.RINGER_MODE_VIBRATE
                            else -> AudioManager.RINGER_MODE_SILENT
                        }
                        if (isDndEnabled) return@detectDragGestures

                        interactor.setRingerMode(snappedMode)
                    },
                    onDragCancel = { 
                        isDragging = false
                    }
                ) { change, dragAmount ->
                    if (isDndEnabled) {
                        change.consume()
                        return@detectDragGestures
                    }
                    change.consume()
                    val trackWidth = size.width - dimens.thumbSize.toPx()
                    val maxOffset = 2f
                    val pixelPerUnit = trackWidth / maxOffset
                    dragOffset = (dragOffset + (dragAmount.x / pixelPerUnit))
                        .coerceIn(0f, maxOffset)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentIndex = animatedPosition.roundToInt()
            repeat(3) { index ->
                val dotAlpha by animateFloatAsState(
                    targetValue = if (currentIndex == index) 0f else 0.4f,
                    animationSpec = tween(durationMillis = 200),
                    label = "dot_alpha"
                )
                Box(
                    modifier = Modifier
                        .size(dimens.dotSize)
                        .graphicsLayer { alpha = dotAlpha }
                        .background(
                            if (isDozing) Color.White else if (isDndEnabled) theme.dndIcon else theme.neutralIcon,
                            RoundedCornerShape(50)
                        )
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalWidth = maxWidth
            val thumbOffset = if (isDndEnabled) {
                (totalWidth - dimens.thumbSize) / 2f
            } else {
                ((totalWidth - dimens.thumbSize) / 2f) * animatedPosition
            }
            
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(dimens.thumbSize)
                    .padding(dimens.thumbPadding)
                    .background(
                        when {
                            isDozing -> Color.Transparent
                            isDndEnabled -> theme.dndBg
                            else -> theme.activeBg
                        },
                        RoundedCornerShape(16.dp)
                    )
                    .then(
                        when {
                            isDozing ->
                            Modifier.border(theme.dozeStroke, Color.White, RoundedCornerShape(16.dp))
                            isDndEnabled ->
                                Modifier.border(2.dp, theme.dndBg, RoundedCornerShape(16.dp))
                            else ->
                                Modifier.border(2.dp, theme.activeBg, RoundedCornerShape(16.dp))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isDndEnabled -> Icons.Filled.DoNotDisturb
                        mode == AudioManager.RINGER_MODE_VIBRATE -> Icons.Filled.Vibration
                        mode == AudioManager.RINGER_MODE_SILENT -> Icons.Filled.VolumeOff
                        else -> Icons.Filled.VolumeUp
                    },
                    contentDescription = null,
                    tint = when {
                        isDozing -> Color.White
                        isDndEnabled -> theme.dndIcon
                        else -> theme.activeIcon
                    },
                    modifier = Modifier.size(dimens.iconSize)
                )
            }
        }
    }
}
