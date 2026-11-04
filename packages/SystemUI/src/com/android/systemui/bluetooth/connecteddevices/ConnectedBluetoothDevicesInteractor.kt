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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.hardware.input.InputManager
import android.os.Handler
import android.view.InputDevice
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.flags.Flags.refactorBatteryLevelDisplay
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class ConnectedBluetoothDevicesInteractor
@Inject
constructor(
    private val bluetoothController: BluetoothController,
    private val localBluetoothManager: LocalBluetoothManager?,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val inputManager: InputManager,
    @Application applicationScope: CoroutineScope,
    @Background private val backgroundExecutor: Executor,
    @Background private val backgroundHandler: Handler,
    @Background backgroundDispatcher: CoroutineDispatcher,
) {
    private val connectedCachedDevices: Flow<List<CachedBluetoothDevice>> =
        merge(bluetoothControllerUpdates(), inputDeviceUpdates())
            .onStart { emit(Unit) }
            .map { fetchConnectedCachedDevices() }
            .flowOn(backgroundDispatcher)
            .shareIn(applicationScope, WHILE_SUBSCRIBED, replay = 1)

    private val metadataBatteryUpdates: Flow<List<CachedBluetoothDevice>> =
        connectedCachedDevices.distinctUntilChanged().flatMapLatest { devices ->
            devices.map { batteryMetadataUpdates(it.device) }.merge().map { devices }
        }

    val devices: StateFlow<List<ConnectedBluetoothDevice>> =
        merge(connectedCachedDevices, metadataBatteryUpdates)
            .map { cachedDevices -> toConnectedDevices(cachedDevices) }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(applicationScope, WHILE_SUBSCRIBED, emptyList())

    private fun fetchConnectedCachedDevices(): List<CachedBluetoothDevice> {
        if (bluetoothAdapter?.isEnabled != true) return emptyList()
        return localBluetoothManager
            ?.cachedDeviceManager
            ?.cachedDevicesCopy
            .orEmpty()
            .filter { it.isConnected }
    }

    private fun toConnectedDevices(
        cachedDevices: List<CachedBluetoothDevice>
    ): List<ConnectedBluetoothDevice> {
        val inputDevices = inputDevicesByAddress()
        return cachedDevices.map { cachedDevice ->
            ConnectedBluetoothDevice(
                address = cachedDevice.address,
                name = cachedDevice.name,
                category = cachedDevice.traits(inputDevices[cachedDevice.address]).toCategory(),
                batteryLevel = cachedDevice.overallBatteryLevel(),
            )
        }
    }

    private fun CachedBluetoothDevice.traits(inputDevices: List<InputDevice>?) =
        BluetoothDeviceTraits(
            metadataDeviceType =
                BluetoothUtils.getStringMetaData(device, BluetoothDevice.METADATA_DEVICE_TYPE),
            inputSources = inputDevices.orEmpty().fold(0) { sources, it -> sources or it.sources },
            hasAlphabeticKeyboard =
                inputDevices.orEmpty().any {
                    it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                },
            isHearingDevice = isHearingDevice,
            deviceClass = btClass?.deviceClass,
            majorDeviceClass = btClass?.majorDeviceClass,
        )

    private fun inputDevicesByAddress(): Map<String, List<InputDevice>> =
        inputManager.inputDeviceIds
            .toList()
            .mapNotNull { id ->
                val address = inputManager.getInputDeviceBluetoothAddress(id)
                val inputDevice = inputManager.getInputDevice(id)
                if (address != null && inputDevice != null) address to inputDevice else null
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    private fun bluetoothControllerUpdates(): Flow<Unit> = conflatedCallbackFlow {
        val callback =
            object : BluetoothController.Callback {
                override fun onBluetoothStateChange(enabled: Boolean) {
                    trySendWithFailureLogging(Unit, TAG, "onBluetoothStateChange")
                }

                override fun onBluetoothDevicesChanged() {
                    trySendWithFailureLogging(Unit, TAG, "onBluetoothDevicesChanged")
                }
            }
        bluetoothController.addCallback(callback)
        awaitClose { bluetoothController.removeCallback(callback) }
    }

    private fun inputDeviceUpdates(): Flow<Unit> = conflatedCallbackFlow {
        val listener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    trySendWithFailureLogging(Unit, TAG, "onInputDeviceAdded")
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    trySendWithFailureLogging(Unit, TAG, "onInputDeviceRemoved")
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    trySendWithFailureLogging(Unit, TAG, "onInputDeviceChanged")
                }
            }
        inputManager.registerInputDeviceListener(listener, backgroundHandler)
        awaitClose { inputManager.unregisterInputDeviceListener(listener) }
    }

    // The refactored CachedBluetoothDevice already relays metadata battery changes.
    private fun batteryMetadataUpdates(device: BluetoothDevice): Flow<Unit> {
        val adapter = bluetoothAdapter
        if (refactorBatteryLevelDisplay() || adapter == null) return emptyFlow()
        return conflatedCallbackFlow {
            val listener =
                BluetoothAdapter.OnMetadataChangedListener { _, key, _ ->
                    if (key in BATTERY_METADATA_KEYS) {
                        trySendWithFailureLogging(Unit, TAG, "onMetadataChanged")
                    }
                }
            val isRegistered =
                adapter.addOnMetadataChangedListener(device, backgroundExecutor, listener)
            awaitClose {
                if (isRegistered) adapter.removeOnMetadataChangedListener(device, listener)
            }
        }
    }

    private companion object {
        const val TAG = "ConnectedBluetoothDevicesInteractor"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val WHILE_SUBSCRIBED = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
        val BATTERY_METADATA_KEYS =
            setOf(
                BluetoothDevice.METADATA_MAIN_BATTERY,
                BluetoothDevice.METADATA_UNTETHERED_LEFT_BATTERY,
                BluetoothDevice.METADATA_UNTETHERED_RIGHT_BATTERY,
            )
    }
}
