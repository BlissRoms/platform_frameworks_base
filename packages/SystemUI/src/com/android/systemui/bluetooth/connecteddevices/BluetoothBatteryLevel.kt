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

package com.android.systemui.bluetooth.connecteddevices

import android.bluetooth.BluetoothDevice
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.flags.Flags.refactorBatteryLevelDisplay

private val VALID_BATTERY_LEVELS = 0..100

fun CachedBluetoothDevice.overallBatteryLevel(): Int? =
    if (refactorBatteryLevelDisplay()) {
        batteryLevelsInfo?.overallBatteryLevel?.asValidBatteryLevel()
    } else {
        legacyOverallBatteryLevel(
            mainLevel = intMetadata(BluetoothDevice.METADATA_MAIN_BATTERY),
            leftLevel = intMetadata(BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY),
            rightLevel = intMetadata(BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY),
            bluetoothLevel = minBatteryLevelWithMemberDevices,
        )
    }

fun legacyOverallBatteryLevel(
    mainLevel: Int,
    leftLevel: Int,
    rightLevel: Int,
    bluetoothLevel: Int,
): Int? =
    mainLevel.asValidBatteryLevel()
        ?: listOfNotNull(leftLevel.asValidBatteryLevel(), rightLevel.asValidBatteryLevel())
            .minOrNull()
        ?: bluetoothLevel.asValidBatteryLevel()

private fun CachedBluetoothDevice.intMetadata(key: Int): Int =
    BluetoothUtils.getIntMetaData(device, key)

private fun Int.asValidBatteryLevel(): Int? = takeIf { it in VALID_BATTERY_LEVELS }
