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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

interface RingerSliderTheme {
    @get:Composable
    val activeBg: Color
    
    @get:Composable
    val neutralBg: Color
    
    @get:Composable
    val activeIcon: Color
    
    @get:Composable
    val neutralIcon: Color
    
    @get:Composable
    val dndBg: Color
    
    @get:Composable
    val dndIcon: Color

    val dozeStroke: Dp
}

interface RingerSliderDimens {
    val totalWidth: Dp?
    val thumbSize: Dp
    val iconSize: Dp
    val thumbPadding: Dp
    val dotSize: Dp
}
