/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.ui.input.pointer.PointerType
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.UserActionResult.ChangeScene
import com.android.compose.animation.scene.UserActionResult.HideOverlay
import com.android.compose.animation.scene.UserActionResult.ReplaceByOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.math.pow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.Parameter
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@RunWithLooper
@EnableSceneContainer
class LockscreenUserActionsViewModelTest : SysuiTestCase() {

    companion object {
        private const val parameterCount = 6

        @Parameters(
            name =
                "canSwipeToEnter={0}, downWithTwoPointers={1}, downFromEdge={2}," +
                    " isSingleShade={3}, isShadeTouchable={4}, isCommunalAvailable={5}"
        )
        @JvmStatic
        fun combinations() = buildList {
            repeat(2f.pow(parameterCount).toInt()) { combination ->
                add(
                    arrayOf(
                            /* canSwipeToEnter= */ combination and 1 != 0,
                            /* downWithTwoPointers= */ combination and 2 != 0,
                            /* downFromEdge= */ combination and 4 != 0,
                            /* isSingleShade= */ combination and 8 != 0,
                            /* isShadeTouchable= */ combination and 16 != 0,
                            /* isCommunalAvailable= */ combination and 32 != 0,
                        )
                        .also { check(it.size == parameterCount) }
                )
            }
        }

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val combinationStrings =
                combinations().map { array ->
                    check(array.size == parameterCount)
                    buildString {
                        ((parameterCount - 1) downTo 0).forEach { index ->
                            append("${array[index]}")
                            if (index > 0) {
                                append(",")
                            }
                        }
                    }
                }
            val uniqueCombinations = combinationStrings.toSet()
            assertThat(combinationStrings).hasSize(uniqueCombinations.size)
        }

        private fun expectedDownDestination(
            downFromEdge: Boolean,
            isNarrowScreen: Boolean,
            isShadeTouchable: Boolean,
        ): SceneKey? {
            return when {
                !isShadeTouchable -> null
                downFromEdge && isNarrowScreen -> Scenes.QuickSettings
                else -> Scenes.Shade
            }
        }

        private fun expectedDownTransitionKey(
            isSingleShade: Boolean,
            isShadeTouchable: Boolean,
        ): TransitionKey? {
            return when {
                !isShadeTouchable -> null
                !isSingleShade -> TransitionKeys.ToSplitShade
                else -> null
            }
        }

        private fun expectedUpDestination(
            canSwipeToEnter: Boolean,
            isShadeTouchable: Boolean,
        ): ContentKey? {
            return when {
                !isShadeTouchable -> null
                canSwipeToEnter -> Scenes.Gone
                else -> Overlays.Bouncer
            }
        }

        private fun expectedStartDestination(
            isCommunalAvailable: Boolean,
            isShadeTouchable: Boolean,
        ): SceneKey? {
            return when {
                !isShadeTouchable -> null
                isCommunalAvailable -> Scenes.Communal
                else -> null
            }
        }
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @JvmField @Parameter(0) var canSwipeToEnter: Boolean = false
    @JvmField @Parameter(1) var downWithTwoPointers: Boolean = false
    @JvmField @Parameter(2) var downFromEdge: Boolean = false
    @JvmField @Parameter(3) var isNarrowScreen: Boolean = true
    @JvmField @Parameter(4) var isShadeTouchable: Boolean = false
    @JvmField @Parameter(5) var isCommunalAvailable: Boolean = false

    private val Kosmos.underTest by Kosmos.Fixture { lockscreenUserActionsViewModel }

    @Before
    fun setup() {
        with(kosmos) { underTest.activateIn(testScope) }
    }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_HUB)
    @DisableFlags(Flags.FLAG_DUAL_SHADE)
    fun userActions_combinedShade() =
        kosmos.runTest {
            disableDualShade()
            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(
                if (canSwipeToEnter) {
                    AuthenticationMethodModel.None
                } else {
                    AuthenticationMethodModel.Pin
                }
            )
            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            if (isNarrowScreen) {
                enableSingleShade()
            } else {
                enableSplitShade()
            }
            setCommunalAvailable(isCommunalAvailable)
            fakePowerRepository.updateWakefulness(
                rawState = if (isShadeTouchable) WakefulnessState.AWAKE else WakefulnessState.ASLEEP
            )

            val userActions by collectLastValue(underTest.actions)
            val downDestination =
                userActions?.get(
                    Swipe.Down(
                        fromSource = Edge.Top.takeIf { downFromEdge },
                        pointerCount = if (downWithTwoPointers) 2 else 1,
                    )
                ) as? ChangeScene
            val downScene by
                collectLastValue(
                    downDestination?.let {
                        sceneInteractor.resolveSceneFamily(downDestination.toScene)
                    } ?: flowOf(null)
                )
            assertThat(downScene)
                .isEqualTo(
                    expectedDownDestination(
                        downFromEdge = downFromEdge,
                        isNarrowScreen = isNarrowScreen,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            assertThat(downDestination?.transitionKey)
                .isEqualTo(
                    expectedDownTransitionKey(
                        isSingleShade = isNarrowScreen,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            val upContent =
                userActions?.get(Swipe.Up)?.let { result ->
                    when (result) {
                        is ChangeScene -> result.toScene
                        is ShowOverlay -> result.overlay
                        is HideOverlay -> result.overlay
                        is ReplaceByOverlay -> result.overlay
                    }
                }

            assertThat(upContent)
                .isEqualTo(
                    expectedUpDestination(
                        canSwipeToEnter = canSwipeToEnter,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            val startScene by
                collectLastValue(
                    (userActions?.get(Swipe.End) as? ChangeScene)?.toScene?.let { scene ->
                        sceneInteractor.resolveSceneFamily(scene)
                    } ?: flowOf(null)
                )

            assertThat(startScene)
                .isEqualTo(
                    expectedStartDestination(
                        isCommunalAvailable = isCommunalAvailable,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            assertThat(userActions?.get(Swipe.End)).isNull()
        }

    @Test
    @EnableFlags(Flags.FLAG_COMMUNAL_HUB, Flags.FLAG_DUAL_SHADE)
    fun userActions_dualShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = !isNarrowScreen)
            fakeKeyguardRepository.setKeyguardEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(
                if (canSwipeToEnter) {
                    AuthenticationMethodModel.None
                } else {
                    AuthenticationMethodModel.Pin
                }
            )
            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            setCommunalAvailable(isCommunalAvailable)
            fakePowerRepository.updateWakefulness(
                rawState = if (isShadeTouchable) WakefulnessState.AWAKE else WakefulnessState.ASLEEP
            )

            val userActions by collectLastValue(underTest.actions)

            val downDestination =
                userActions?.get(
                    Swipe.Down(
                        fromSource = Edge.Top.takeIf { downFromEdge },
                        pointerCount = if (downWithTwoPointers) 2 else 1,
                        // ShadeUserActions filters one-finger swipes to "not mouse".
                        pointerType = if (downWithTwoPointers) null else PointerType.Touch,
                    )
                )

            // Assert that the correct action is returned for a down swipe.
            when {
                // Swiping is disabled if the shade is not touchable.
                !isShadeTouchable -> assertThat(downDestination).isNull()
                // Swiping from the top edge has no action in dual shade mode.
                downFromEdge -> assertThat(downDestination).isNull()
                // A two-finger swipe should open the quick settings shade.
                downWithTwoPointers ->
                    assertThat(downDestination).isEqualTo(ShowOverlay(Overlays.QuickSettingsShade))
                // A one-finger swipe should open the notifications shade.
                else ->
                    assertThat(downDestination).isEqualTo(ShowOverlay(Overlays.NotificationsShade))
            }

            val upContent =
                userActions?.get(Swipe.Up)?.let { result ->
                    when (result) {
                        is ChangeScene -> result.toScene
                        is ShowOverlay -> result.overlay
                        is HideOverlay -> result.overlay
                        is ReplaceByOverlay -> result.overlay
                    }
                }

            assertThat(upContent)
                .isEqualTo(
                    expectedUpDestination(
                        canSwipeToEnter = canSwipeToEnter,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            val startScene by
                collectLastValue(
                    (userActions?.get(Swipe.End) as? ChangeScene)?.toScene?.let { scene ->
                        sceneInteractor.resolveSceneFamily(scene)
                    } ?: flowOf(null)
                )

            assertThat(startScene)
                .isEqualTo(
                    expectedStartDestination(
                        isCommunalAvailable = isCommunalAvailable,
                        isShadeTouchable = isShadeTouchable,
                    )
                )

            assertThat(userActions?.get(Swipe.End)).isNull()
        }
}
