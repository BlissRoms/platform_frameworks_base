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
import android.view.InputDevice

enum class BluetoothDeviceCategory {
    WATCH,
    EARBUDS,
    HEADPHONES,
    HEADSET,
    HEARING_AID,
    SPEAKER,
    CAR,
    STYLUS,
    GAMEPAD,
    KEYBOARD,
    MOUSE,
    COMPUTER,
    PHONE,
    OTHER,
}

data class BluetoothDeviceTraits(
    val metadataDeviceType: String? = null,
    val inputSources: Int = 0,
    val hasAlphabeticKeyboard: Boolean = false,
    val isHearingDevice: Boolean = false,
    val deviceClass: Int? = null,
    val majorDeviceClass: Int? = null,
)

fun BluetoothDeviceTraits.toCategory(): BluetoothDeviceCategory =
    categoryFromMetadata(metadataDeviceType)
        ?: categoryFromInput(inputSources, hasAlphabeticKeyboard)
        ?: BluetoothDeviceCategory.HEARING_AID.takeIf { isHearingDevice }
        ?: categoryFromDeviceClass(deviceClass)
        ?: categoryFromMajorClass(majorDeviceClass)
        ?: BluetoothDeviceCategory.OTHER

private fun categoryFromMetadata(deviceType: String?): BluetoothDeviceCategory? =
    when (deviceType) {
        BluetoothDevice.DEVICE_TYPE_WATCH -> BluetoothDeviceCategory.WATCH
        BluetoothDevice.DEVICE_TYPE_UNTETHERED_HEADSET -> BluetoothDeviceCategory.EARBUDS
        BluetoothDevice.DEVICE_TYPE_HEADSET -> BluetoothDeviceCategory.HEADSET
        BluetoothDevice.DEVICE_TYPE_STYLUS -> BluetoothDeviceCategory.STYLUS
        BluetoothDevice.DEVICE_TYPE_CARKIT -> BluetoothDeviceCategory.CAR
        else -> null
    }

private fun categoryFromInput(sources: Int, hasAlphabeticKeyboard: Boolean) =
    when {
        sources.hasSource(InputDevice.SOURCE_STYLUS) -> BluetoothDeviceCategory.STYLUS
        sources.hasSource(InputDevice.SOURCE_GAMEPAD) ||
            sources.hasSource(InputDevice.SOURCE_JOYSTICK) -> BluetoothDeviceCategory.GAMEPAD
        hasAlphabeticKeyboard -> BluetoothDeviceCategory.KEYBOARD
        sources.hasSource(InputDevice.SOURCE_MOUSE) -> BluetoothDeviceCategory.MOUSE
        else -> null
    }

private fun Int.hasSource(source: Int) = (this and source) == source

private fun categoryFromDeviceClass(deviceClass: Int?): BluetoothDeviceCategory? =
    when (deviceClass) {
        BluetoothClass.Device.WEARABLE_WRIST_WATCH -> BluetoothDeviceCategory.WATCH
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BluetoothDeviceCategory.HEADPHONES
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BluetoothDeviceCategory.HEADSET
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BluetoothDeviceCategory.SPEAKER
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> BluetoothDeviceCategory.CAR
        BluetoothClass.Device.PERIPHERAL_KEYBOARD,
        BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING -> BluetoothDeviceCategory.KEYBOARD
        BluetoothClass.Device.PERIPHERAL_POINTING -> BluetoothDeviceCategory.MOUSE
        else -> null
    }

private fun categoryFromMajorClass(majorDeviceClass: Int?): BluetoothDeviceCategory? =
    when (majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceCategory.HEADPHONES
        BluetoothClass.Device.Major.COMPUTER -> BluetoothDeviceCategory.COMPUTER
        BluetoothClass.Device.Major.PHONE -> BluetoothDeviceCategory.PHONE
        else -> null
    }
