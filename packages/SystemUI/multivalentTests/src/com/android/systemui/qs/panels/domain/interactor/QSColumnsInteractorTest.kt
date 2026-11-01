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

package com.android.systemui.qs.panels.domain.interactor

import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.configurationRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.panels.data.repository.QSColumnsRepository
import com.android.systemui.qs.panels.data.repository.qsColumnsRepository
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QSColumnsInteractorTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_dual_shade_num_columns,
                2,
            )
            qsColumnsRepository =
                QSColumnsRepository(
                    mainResources,
                    configurationRepository,
                    secureSettingsRepository,
                )
        }
    private lateinit var underTest: QSColumnsInteractor

    @Before
    fun setUp() {
        underTest = kosmos.qsColumnsInteractor
    }

    @Test
    fun withSingleShade_returnsCorrectValue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.columns)
            fakeSecureSettingsRepository.setInt(QSColumnsRepository.USER_COLUMNS_SETTING, 5)

            enableSingleShade()

            assertThat(latest).isEqualTo(5)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_DUAL_SHADE)
    fun withDualShade_returnsCorrectValue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.columns)

            enableDualShade()

            assertThat(latest).isEqualTo(2)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun withSplitShade_returnsCorrectValue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.columns)
            fakeSecureSettingsRepository.setInt(QSColumnsRepository.USER_COLUMNS_SETTING, 3)

            enableSplitShade()

            assertThat(latest).isEqualTo(3)
        }
}
