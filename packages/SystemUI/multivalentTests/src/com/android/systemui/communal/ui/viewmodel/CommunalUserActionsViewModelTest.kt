/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.communal.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.input.pointer.PointerType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.domain.interactor.biometricUnlockInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockSource
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.SwipeUpToGone
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class CommunalUserActionsViewModelTest : SysuiTestCase() {

    @get:Rule val expect: Expect = Expect.create()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: CommunalUserActionsViewModel by lazy {
        kosmos.communalUserActionsViewModel
    }

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun actions_singleShade() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            enableSingleShade()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = false)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.Bouncer))
            expect.that(actions?.get(Swipe.Down)).isEqualTo(UserActionResult(Scenes.Shade))

            setUpState(isShadeTouchable = false, isDeviceUnlocked = false)
            assertThat(actions).isEmpty()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult(Scenes.Gone, transitionKey = SwipeUpToGone))
            expect.that(actions?.get(Swipe.Down)).isEqualTo(UserActionResult(Scenes.Shade))
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun actions_splitShade() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            enableSplitShade()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = false)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.Bouncer))
            expect
                .that(actions?.get(Swipe.Down))
                .isEqualTo(UserActionResult(Scenes.Shade, ToSplitShade))

            setUpState(isShadeTouchable = false, isDeviceUnlocked = false)
            assertThat(actions).isEmpty()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult(Scenes.Gone, transitionKey = SwipeUpToGone))
            expect
                .that(actions?.get(Swipe.Down))
                .isEqualTo(UserActionResult(Scenes.Shade, ToSplitShade))
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun actions_dualShade() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            enableDualShade()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = false)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.Bouncer))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Eraser)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Stylus)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Touch)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))

            setUpState(isShadeTouchable = false, isDeviceUnlocked = false)
            assertThat(actions).isEmpty()

            setUpState(isShadeTouchable = true, isDeviceUnlocked = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Start)).isEqualTo(UserActionResult(Scenes.Lockscreen))
            expect
                .that(actions?.get(Swipe.Up))
                .isEqualTo(UserActionResult(Scenes.Gone, transitionKey = SwipeUpToGone))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Eraser)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Stylus)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Touch)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
        }

    private fun Kosmos.setUpState(isShadeTouchable: Boolean, isDeviceUnlocked: Boolean) {
        if (isShadeTouchable) {
            powerInteractor.setAwakeForTest()
        } else {
            powerInteractor.setAsleepForTest()
        }

        if (isDeviceUnlocked) {
            unlockDevice()
        } else {
            lockDevice()
        }
    }

    private fun Kosmos.lockDevice() {
        val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)

        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
        assertThat(deviceUnlockStatus?.isUnlocked).isFalse()
        sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
    }

    private fun Kosmos.unlockDevice() {
        val deviceUnlockStatus by collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus)

        kosmos.biometricUnlockInteractor.setBiometricUnlockState(
            unlockStateInt = BiometricUnlockController.MODE_DISMISS,
            biometricUnlockSource = BiometricUnlockSource.FINGERPRINT_SENSOR,
        )
        assertThat(deviceUnlockStatus?.isUnlocked).isTrue()
        sceneInteractor.changeScene(Scenes.Gone, "reason")
    }
}
