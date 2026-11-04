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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BluetoothDeviceCategoryTest : SysuiTestCase() {

    @Test
    fun fastPairWatchMetadata_isWatch() {
        val traits = BluetoothDeviceTraits(metadataDeviceType = BluetoothDevice.DEVICE_TYPE_WATCH)

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.WATCH)
    }

    @Test
    fun fastPairUntetheredMetadata_winsOverHeadphoneClass() {
        val traits =
            BluetoothDeviceTraits(
                metadataDeviceType = BluetoothDevice.DEVICE_TYPE_UNTETHERED_HEADSET,
                deviceClass = BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            )

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.EARBUDS)
    }

    @Test
    fun stylusInputSourceWithoutMetadata_isStylus() {
        val traits =
            BluetoothDeviceTraits(
                inputSources = InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_KEYBOARD,
                deviceClass = BluetoothClass.Device.PERIPHERAL_POINTING,
            )

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.STYLUS)
    }

    @Test
    fun gamepadInputSource_isGamepad() {
        val traits =
            BluetoothDeviceTraits(
                inputSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD,
                hasAlphabeticKeyboard = true,
            )

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.GAMEPAD)
    }

    @Test
    fun nonAlphabeticKeyboardSource_fallsBackToDeviceClass() {
        val traits =
            BluetoothDeviceTraits(
                inputSources = InputDevice.SOURCE_KEYBOARD,
                deviceClass = BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            )

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.HEADPHONES)
    }

    @Test
    fun hearingDevice_isHearingAid() {
        val traits =
            BluetoothDeviceTraits(
                isHearingDevice = true,
                deviceClass = BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            )

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.HEARING_AID)
    }

    @Test
    fun wristWatchClass_isWatch() {
        val traits = BluetoothDeviceTraits(deviceClass = BluetoothClass.Device.WEARABLE_WRIST_WATCH)

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.WATCH)
    }

    @Test
    fun loudspeakerClass_isSpeaker() {
        val traits =
            BluetoothDeviceTraits(deviceClass = BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER)

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.SPEAKER)
    }

    @Test
    fun unknownAudioVideoClass_fallsBackToHeadphones() {
        val traits =
            BluetoothDeviceTraits(majorDeviceClass = BluetoothClass.Device.Major.AUDIO_VIDEO)

        assertThat(traits.toCategory()).isEqualTo(BluetoothDeviceCategory.HEADPHONES)
    }

    @Test
    fun noTraits_isOther() {
        assertThat(BluetoothDeviceTraits().toCategory()).isEqualTo(BluetoothDeviceCategory.OTHER)
    }
}
