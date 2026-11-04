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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settingslib.Utils
import com.android.systemui.bluetooth.connecteddevices.BluetoothDeviceCategory
import com.android.systemui.bluetooth.connecteddevices.ConnectedBluetoothDevice
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val NoConnectedBluetoothDevices: StateFlow<List<ConnectedBluetoothDevice>> =
    MutableStateFlow(emptyList<ConnectedBluetoothDevice>()).asStateFlow()

val LocalQSBluetoothDevices = compositionLocalOf { NoConnectedBluetoothDevices }

private val QuadIconButtonSize = 54.dp
private val QuadIconSize = 28.dp
private val QuadContentPadding = 16.dp
private val PagerDotSize = 5.dp
private val PagerDotSpacing = 4.dp
private val QuadInactiveButtonColor = Color(0xFF383838)
private const val SECONDARY_TEXT_ALPHA = 0.65f
private const val INACTIVE_DOT_ALPHA = 0.35f

@Composable
fun NothingBluetoothQuadTileContent(
    iconProvider: Context.() -> Icon,
    isActive: Boolean,
    label: String,
    secondaryLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by LocalQSBluetoothDevices.current.collectAsStateWithLifecycle()
    val connectedDevices = if (isActive) devices else emptyList()
    val pagerState = rememberPagerState { connectedDevices.size.coerceAtLeast(1) }
    val currentDevice = connectedDevices.getOrNull(pagerState.currentPage)
    val deviceIconRes = currentDevice?.category?.iconRes()
    val buttonIconProvider: Context.() -> Icon =
        if (deviceIconRes != null) ({ Icon.Resource(deviceIconRes, null) }) else iconProvider

    Column(
        modifier = modifier.fillMaxSize().padding(QuadContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        NothingQuadIconButton(
            iconProvider = buttonIconProvider,
            isActive = isActive,
            onClick = onClick,
        )
        if (connectedDevices.isEmpty()) {
            NothingQuadTileLabels(label, secondaryLabel, isActive)
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val device = connectedDevices[page]
                NothingQuadLabels(primary = device.name, secondary = device.statusText())
            }
        }
        if (connectedDevices.size > 1) {
            PagerDots(count = connectedDevices.size, currentPage = pagerState.currentPage)
        } else {
            Spacer(modifier = Modifier.height(PagerDotSize))
        }
    }
}

@Composable
fun NothingQuadIconButton(
    iconProvider: Context.() -> Icon,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(QuadIconButtonSize)
                .clip(CircleShape)
                .background(if (isActive) Color.White else QuadInactiveButtonColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SmallTileContent(
            iconProvider = iconProvider,
            color = if (isActive) Color.Black else Color.White,
            size = { QuadIconSize },
        )
    }
}

@Composable
fun NothingQuadLabels(primary: String, secondary: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = primary,
            color = Color.White,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondary,
            color = Color.White.copy(alpha = SECONDARY_TEXT_ALPHA),
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NothingQuadTileLabels(label: String, secondaryLabel: String?, isActive: Boolean) {
    val distinctSecondaryLabel = secondaryLabel?.takeIf { it.isNotEmpty() && it != label }
    val stateRes = if (isActive) R.string.switch_bar_on else R.string.switch_bar_off
    NothingQuadLabels(
        primary = distinctSecondaryLabel ?: label,
        secondary = if (distinctSecondaryLabel != null) label else stringResource(stateRes),
    )
}

@Composable
private fun PagerDots(count: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PagerDotSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val alpha = if (index == currentPage) 1f else INACTIVE_DOT_ALPHA
            Box(
                modifier =
                    Modifier.size(PagerDotSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun ConnectedBluetoothDevice.statusText(): String =
    batteryLevel?.let { Utils.formatPercentage(it) }
        ?: stringResource(R.string.quick_settings_connected)

@DrawableRes
private fun BluetoothDeviceCategory.iconRes(): Int? =
    when (this) {
        BluetoothDeviceCategory.WATCH -> R.drawable.ic_watch
        BluetoothDeviceCategory.EARBUDS ->
            com.android.settingslib.R.drawable.ic_bt_untethered_earbuds
        BluetoothDeviceCategory.HEADPHONES -> com.android.internal.R.drawable.ic_bt_headphones_a2dp
        BluetoothDeviceCategory.HEADSET -> com.android.internal.R.drawable.ic_bt_headset_hfp
        BluetoothDeviceCategory.HEARING_AID -> com.android.internal.R.drawable.ic_bt_hearing_aid
        BluetoothDeviceCategory.SPEAKER ->
            com.android.settingslib.R.drawable.ic_bt_le_audio_speakers
        BluetoothDeviceCategory.CAR -> com.android.settingslib.R.drawable.ic_media_car
        BluetoothDeviceCategory.STYLUS -> R.drawable.ic_bt_stylus
        BluetoothDeviceCategory.GAMEPAD -> com.android.settingslib.R.drawable.ic_media_game_console
        BluetoothDeviceCategory.KEYBOARD -> com.android.internal.R.drawable.ic_lockscreen_ime
        BluetoothDeviceCategory.MOUSE -> com.android.internal.R.drawable.ic_bt_pointing_hid
        BluetoothDeviceCategory.COMPUTER -> com.android.internal.R.drawable.ic_bt_laptop
        BluetoothDeviceCategory.PHONE -> com.android.internal.R.drawable.ic_phone
        BluetoothDeviceCategory.OTHER -> null
    }
