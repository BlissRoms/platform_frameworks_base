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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BluetoothBatteryLevelTest : SysuiTestCase() {

    @Test
    fun mainMetadataLevel_isPreferred() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = 80,
                leftLevel = 40,
                rightLevel = 50,
                bluetoothLevel = 10,
            )

        assertThat(level).isEqualTo(80)
    }

    @Test
    fun missingMainMetadata_usesLowestEarbud() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = BluetoothUtils.META_INT_ERROR,
                leftLevel = 40,
                rightLevel = 55,
                bluetoothLevel = 10,
            )

        assertThat(level).isEqualTo(40)
    }

    @Test
    fun singleEarbudLevel_isUsed() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = BluetoothUtils.META_INT_ERROR,
                leftLevel = BluetoothUtils.META_INT_ERROR,
                rightLevel = 65,
                bluetoothLevel = 10,
            )

        assertThat(level).isEqualTo(65)
    }

    @Test
    fun noMetadata_fallsBackToBluetoothLevel() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = BluetoothUtils.META_INT_ERROR,
                leftLevel = BluetoothUtils.META_INT_ERROR,
                rightLevel = BluetoothUtils.META_INT_ERROR,
                bluetoothLevel = 30,
            )

        assertThat(level).isEqualTo(30)
    }

    @Test
    fun unknownEverywhere_isNull() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = BluetoothUtils.META_INT_ERROR,
                leftLevel = BluetoothUtils.META_INT_ERROR,
                rightLevel = BluetoothUtils.META_INT_ERROR,
                bluetoothLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN,
            )

        assertThat(level).isNull()
    }

    @Test
    fun bluetoothOffLevel_isNull() {
        val level =
            legacyOverallBatteryLevel(
                mainLevel = BluetoothUtils.META_INT_ERROR,
                leftLevel = BluetoothUtils.META_INT_ERROR,
                rightLevel = BluetoothUtils.META_INT_ERROR,
                bluetoothLevel = BluetoothDevice.BATTERY_LEVEL_BLUETOOTH_OFF,
            )

        assertThat(level).isNull()
    }
}
