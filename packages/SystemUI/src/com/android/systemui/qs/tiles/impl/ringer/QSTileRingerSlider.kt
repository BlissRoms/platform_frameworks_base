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

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.android.systemui.common.ringer.RingerSliderWidget
import com.android.systemui.common.ringer.RingerModeInteractorImpl
import com.android.systemui.res.R

@Composable
fun QSTileRingerSlider(
    border: Modifier = Modifier
) {
    val context = LocalContext.current

    val interactor = remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        RingerModeInteractorImpl(context, audioManager)
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = dimensionResource(R.dimen.qs_tile_margin_horizontal)
        val tileHeight = with(LocalDensity.current) {
            val heightPx = (maxWidth.toPx() / 2) - (spacing.toPx() / 2)
            heightPx.toDp()
        }
        
        RingerSliderWidget(
            interactor = interactor,
            theme = QSTileRingerTheme(),
            dimens = QSTileRingerDimens(tileHeight),
            modifier = Modifier.fillMaxWidth(),
            isDozing = false,
            border = border
        )
    }
}