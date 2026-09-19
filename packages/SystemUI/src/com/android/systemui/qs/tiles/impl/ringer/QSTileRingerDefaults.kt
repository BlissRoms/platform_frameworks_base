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
package com.android.systemui.qs.tiles.impl.ringer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.common.ringer.RingerSliderDimens
import com.android.systemui.common.ringer.RingerSliderTheme

class QSTileRingerTheme(
    private val isNothing: Boolean = false,
) : RingerSliderTheme {
    override val activeBg: Color
        @Composable get() = if (isNothing) Color.White else MaterialTheme.colorScheme.primary
    
    override val neutralBg: Color
        @Composable get() = if (isNothing) Color(0xFF262626) else LocalAndroidColorScheme.current.surfaceEffect1
    
    override val activeIcon: Color
        @Composable get() = if (isNothing) Color.Black else MaterialTheme.colorScheme.onPrimary
    
    override val neutralIcon: Color
        @Composable get() = if (isNothing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface

    override val dndBg: Color
        @Composable get() = if (isNothing) Color.White else MaterialTheme.colorScheme.primary
    
    override val dndIcon: Color
        @Composable get() = if (isNothing) Color(0xFFD71921) else MaterialTheme.colorScheme.onPrimary
    
    override val dozeStroke: Dp = 2.dp
}

class QSTileRingerDimens(
    private val tileHeight: Dp,
    private val isNothing: Boolean = false,
) : RingerSliderDimens {
    override val totalWidth: Dp? = null
    override val thumbSize: Dp = tileHeight
    override val iconSize: Dp = if (isNothing) 26.dp else 24.dp
    override val thumbPadding: Dp = 8.dp
    override val dotSize: Dp = 6.dp
}
