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

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.systemui.common.ringer.RingerSliderWidget
import com.android.systemui.common.ringer.RingerModeInteractorImpl
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSPanelStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileHeight

@Composable
fun QSTileRingerSlider(
    modifier: Modifier = Modifier,
    border: Modifier = Modifier,
    panelStyle: Int = LocalQSPanelStyle.current,
) {
    val context = LocalContext.current
    val isNothing = panelStyle == 5

    val interactor = remember {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        RingerModeInteractorImpl(context, audioManager, notificationManager)
    }
    
    val containerShape = if (isNothing) RoundedCornerShape(50) else RoundedCornerShape(24.dp)
    val thumbShape = if (isNothing) CircleShape else RoundedCornerShape(16.dp)

    RingerSliderWidget(
        interactor = interactor,
        theme = QSTileRingerTheme(isNothing = isNothing),
        dimens = QSTileRingerDimens(TileHeight, isNothing = isNothing),
        modifier = modifier,
        isDozing = false,
        border = border,
        containerShape = containerShape,
        thumbShape = thumbShape,
        isNothing = isNothing,
        onLongClick = {
            interactor.toggleDnd()
        }
    )
}
