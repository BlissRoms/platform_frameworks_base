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

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.platform.test.annotations.DisableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.flags.Flags.FLAG_REFACTOR_BATTERY_LEVEL_DISPLAY
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.bluetoothAdapter
import com.android.systemui.bluetooth.cachedBluetoothDeviceManager
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.fakeBluetoothController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@DisableFlags(FLAG_REFACTOR_BATTERY_LEVEL_DISPLAY)
class ConnectedBluetoothDevicesInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Test
    fun bluetoothOff_emitsNoDevices() =
        kosmos.runTest {
            whenever(bluetoothAdapter.isEnabled).thenReturn(false)
            givenCachedDevices(cachedDevice(WATCH_ADDRESS, isConnected = true))

            val devices by collectLastValue(connectedBluetoothDevicesInteractor.devices)

            assertThat(devices).isEmpty()
        }

    @Test
    fun connectedDevices_mapToNameCategoryAndBattery() =
        kosmos.runTest {
            whenever(bluetoothAdapter.isEnabled).thenReturn(true)
            givenCachedDevices(
                cachedDevice(
                    WATCH_ADDRESS,
                    isConnected = true,
                    deviceClass = BluetoothClass.Device.WEARABLE_WRIST_WATCH,
                    batteryLevel = 72,
                ),
                cachedDevice(HEADPHONES_ADDRESS, isConnected = false),
            )

            val devices by collectLastValue(connectedBluetoothDevicesInteractor.devices)

            assertThat(devices)
                .containsExactly(
                    ConnectedBluetoothDevice(
                        address = WATCH_ADDRESS,
                        name = WATCH_ADDRESS,
                        category = BluetoothDeviceCategory.WATCH,
                        batteryLevel = 72,
                    )
                )
        }

    @Test
    fun bluetoothControllerUpdate_refreshesBatteryLevel() =
        kosmos.runTest {
            whenever(bluetoothAdapter.isEnabled).thenReturn(true)
            val watch = cachedDevice(WATCH_ADDRESS, isConnected = true, batteryLevel = 72)
            givenCachedDevices(watch)
            val devices by collectLastValue(connectedBluetoothDevicesInteractor.devices)

            whenever(watch.minBatteryLevelWithMemberDevices).thenReturn(65)
            fakeBluetoothController.onBluetoothDevicesChanged()

            assertThat(devices?.single()?.batteryLevel).isEqualTo(65)
        }

    private fun Kosmos.givenCachedDevices(vararg devices: CachedBluetoothDevice) {
        whenever(cachedBluetoothDeviceManager.cachedDevicesCopy).thenReturn(devices.toList())
    }

    private fun cachedDevice(
        address: String,
        isConnected: Boolean,
        deviceClass: Int? = null,
        batteryLevel: Int = BluetoothDevice.BATTERY_LEVEL_UNKNOWN,
    ): CachedBluetoothDevice {
        val bluetoothClass =
            deviceClass?.let { cod ->
                mock<BluetoothClass> { on { this.deviceClass }.thenReturn(cod) }
            }
        val bluetoothDevice = mock<BluetoothDevice> { on { this.address }.thenReturn(address) }
        return mock {
            on { this.address }.thenReturn(address)
            on { name }.thenReturn(address)
            on { device }.thenReturn(bluetoothDevice)
            on { this.isConnected }.thenReturn(isConnected)
            on { btClass }.thenReturn(bluetoothClass)
            on { minBatteryLevelWithMemberDevices }.thenReturn(batteryLevel)
        }
    }

    private companion object {
        const val WATCH_ADDRESS = "00:11:22:33:44:55"
        const val HEADPHONES_ADDRESS = "66:77:88:99:AA:BB"
    }
}
