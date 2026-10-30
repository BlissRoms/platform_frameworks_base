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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.testing.ViewUtils
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardSmartspaceInteractor
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardSmartspaceViewModel
import com.android.systemui.res.R
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.shared.R as sharedR
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class SmartspaceSectionTest : SysuiTestCase() {
    private lateinit var underTest: SmartspaceSection
    @Mock private lateinit var keyguardClockViewModel: KeyguardClockViewModel
    @Mock private lateinit var keyguardSmartspaceViewModel: KeyguardSmartspaceViewModel
    @Mock private lateinit var lockscreenSmartspaceController: LockscreenSmartspaceController
    @Mock private lateinit var keyguardUnlockAnimationController: KeyguardUnlockAnimationController
    @Mock private lateinit var keyguardSmartspaceInteractor: KeyguardSmartspaceInteractor
    @Mock private lateinit var blueprintInteractor: Lazy<KeyguardBlueprintInteractor>

    private val smartspaceView = View(mContext).also { it.id = sharedR.id.bc_smartspace_view }
    private val weatherView = View(mContext).also { it.id = sharedR.id.weather_smartspace_view }
    private val weatherViewLarge =
        View(mContext).also { it.id = sharedR.id.weather_smartspace_view_large }
    private val dateView = LinearLayout(mContext).also { it.id = sharedR.id.date_smartspace_view }
    private val dateViewLarge =
        LinearLayout(mContext).also { it.id = sharedR.id.date_smartspace_view_large }
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var constraintSet: ConstraintSet

    private val clockShouldBeCentered = MutableStateFlow(false)
    private val hasCustomWeatherDataDisplay = MutableStateFlow(false)
    private val shouldDateWeatherBeBelowSmallClock = MutableStateFlow(true)
    private val shouldDateWeatherBeBelowLargeClock = MutableStateFlow(true)
    private val isWeatherVisibleFlow = MutableStateFlow(false)
    private val isFullWidthShade = MutableStateFlow(true)
    private val isLargeClockVisible = MutableStateFlow(true)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest =
            SmartspaceSection(
                mContext,
                keyguardClockViewModel,
                keyguardSmartspaceViewModel,
                keyguardSmartspaceInteractor,
                lockscreenSmartspaceController,
                keyguardUnlockAnimationController,
                blueprintInteractor,
            )
        constraintLayout = ConstraintLayout(mContext)
        whenever(lockscreenSmartspaceController.buildAndConnectView(any()))
            .thenReturn(smartspaceView)
        whenever(lockscreenSmartspaceController.buildAndConnectWeatherView(any(), eq(false)))
            .thenReturn(weatherView)
        whenever(lockscreenSmartspaceController.buildAndConnectWeatherView(any(), eq(true)))
            .thenReturn(weatherViewLarge)
        whenever(lockscreenSmartspaceController.buildAndConnectDateView(any(), eq(false)))
            .thenReturn(dateView)
        whenever(lockscreenSmartspaceController.buildAndConnectDateView(any(), eq(true)))
            .thenReturn(dateViewLarge)
        whenever(keyguardClockViewModel.hasCustomWeatherDataDisplay)
            .thenReturn(hasCustomWeatherDataDisplay)
        whenever(keyguardClockViewModel.isLargeClockVisible).thenReturn(isLargeClockVisible)
        whenever(keyguardClockViewModel.shouldDateWeatherBeBelowSmallClock)
            .thenReturn(shouldDateWeatherBeBelowSmallClock)
        whenever(keyguardClockViewModel.shouldDateWeatherBeBelowLargeClock)
            .thenReturn(shouldDateWeatherBeBelowLargeClock)
        whenever(keyguardClockViewModel.clockShouldBeCentered).thenReturn(clockShouldBeCentered)
        whenever(keyguardSmartspaceViewModel.isSmartspaceEnabled).thenReturn(true)
        whenever(keyguardSmartspaceViewModel.isWeatherVisible).thenReturn(isWeatherVisibleFlow)
        whenever(keyguardSmartspaceViewModel.isFullWidthShade).thenReturn(isFullWidthShade)
        constraintSet = ConstraintSet()
    }

    @Test
    fun testAddViews_smartspaceNotEnabled() {
        whenever(keyguardSmartspaceViewModel.isSmartspaceEnabled).thenReturn(false)

        underTest.addViews(constraintLayout)

        assertThat(smartspaceView.parent).isNull()
        assertThat(weatherView.parent).isNull()
        assertThat(dateView.parent).isNull()
    }

    @Test
    fun testAddViews_smartspaceEnabled() {
        underTest.addViews(constraintLayout)

        assertThat(smartspaceView.parent).isEqualTo(constraintLayout)
        assertThat(weatherView.parent).isEqualTo(dateView)
        assertThat(dateView.parent).isEqualTo(constraintLayout)
    }

    @Test
    fun testRemoveViews_removesViews_whenSmartspaceDisabledAfterAdd() {
        underTest.addViews(constraintLayout)
        whenever(keyguardSmartspaceViewModel.isSmartspaceEnabled).thenReturn(false)

        underTest.removeViews(constraintLayout)

        assertThat(smartspaceView.parent).isNull()
        assertThat(dateView.parent).isNull()
    }

    @Test
    fun testConstraintsWhenShadeLayoutIsNotWide() {
        underTest.addViews(constraintLayout)
        underTest.applyConstraints(constraintSet)

        val smartspaceConstraints = constraintSet.getConstraint(smartspaceView.id)
        assertThat(smartspaceConstraints.layout.endToEnd).isEqualTo(ConstraintSet.PARENT_ID)
    }

    @Test
    fun testConstraintsWhenShadeLayoutIsWide() {
        isFullWidthShade.value = false

        underTest.addViews(constraintLayout)
        underTest.applyConstraints(constraintSet)

        val smartspaceConstraints = constraintSet.getConstraint(smartspaceView.id)
        assertThat(smartspaceConstraints.layout.endToEnd).isEqualTo(R.id.split_shade_guideline)
    }

    @Test
    fun testConstraintsWhenHasCustomWeatherDataDisplay() {
        hasCustomWeatherDataDisplay.value = true
        underTest.addViews(constraintLayout)
        underTest.applyConstraints(constraintSet)

        val dateConstraints = constraintSet.getConstraint(dateView.id)
        assertThat(dateConstraints.layout.bottomToTop).isEqualTo(smartspaceView.id)
    }

    @Test
    fun testRemoveViews_unRegistersListener_fromAttachInfoTreeObserver() {
        val ssViewWithSpiedTreeObserver =
            ViewWithSpiedTreeObserver(mContext).also { it.id = sharedR.id.bc_smartspace_view }
        whenever(lockscreenSmartspaceController.buildAndConnectView(any()))
            .thenReturn(ssViewWithSpiedTreeObserver)

        runOnMainThreadAndWaitForIdleSync { ViewUtils.attachView(constraintLayout) }
        assertThat(constraintLayout.isAttachedToWindow).isTrue()

        runOnMainThreadAndWaitForIdleSync { underTest.addViews(constraintLayout) }

        assertThat(ssViewWithSpiedTreeObserver.isAttachedToWindow).isTrue()
        assertThat(ssViewWithSpiedTreeObserver.getSuperViewTreeObserver())
            .isEqualTo(constraintLayout.viewTreeObserver)
        val viewTreeObserver1 = ssViewWithSpiedTreeObserver.viewTreeObserver

        verify(viewTreeObserver1).addOnGlobalLayoutListener(any())
        clearInvocations(viewTreeObserver1)

        runOnMainThreadAndWaitForIdleSync { underTest.removeViews(constraintLayout) }

        assertThat(ssViewWithSpiedTreeObserver.isAttachedToWindow).isFalse()
        assertThat(ssViewWithSpiedTreeObserver.getSuperViewTreeObserver())
            .isNotEqualTo(constraintLayout.viewTreeObserver)
        val viewTreeObserver2 = ssViewWithSpiedTreeObserver.viewTreeObserver
        verify(viewTreeObserver1).removeOnGlobalLayoutListener(any())
        verify(viewTreeObserver2, never()).removeOnGlobalLayoutListener(any())
    }

    class ViewWithSpiedTreeObserver(context: Context) : View(context) {
        private var realTreeObserver: ViewTreeObserver? = null
        private var spiedTreeObserver: ViewTreeObserver? = null

        override fun getViewTreeObserver(): ViewTreeObserver {
            val viewViewTreeObserver = getSuperViewTreeObserver()
            if (realTreeObserver == null || realTreeObserver !== viewViewTreeObserver) {
                realTreeObserver = viewViewTreeObserver
                spiedTreeObserver = spy(realTreeObserver)
            }
            return spiedTreeObserver!!
        }

        fun getSuperViewTreeObserver(): ViewTreeObserver {
            return super.getViewTreeObserver()
        }
    }
}
