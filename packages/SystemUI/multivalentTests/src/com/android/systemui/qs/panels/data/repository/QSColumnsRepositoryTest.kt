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

package com.android.systemui.qs.panels.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class QSColumnsRepositoryTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_dual_shade_num_columns,
                2,
            )
            testCase.context.orCreateTestableResources.addOverride(
                com.android.internal.R.integer.config_qsTileColumnsMax,
                6,
            )
        }
    private lateinit var underTest: QSColumnsRepository

    @Before
    fun setUp() {
        underTest = with(kosmos) { qsColumnsRepository }
    }

    @Test
    fun userColumns_unset_returnsDefault() =
        with(kosmos) {
            testScope.runTest {
                val latest by collectLastValue(underTest.userColumns)

                assertThat(latest).isEqualTo(QSColumnsRepository.DEFAULT_USER_COLUMNS)
            }
        }

    @Test
    fun userColumns_tracksSetting() =
        with(kosmos) {
            testScope.runTest {
                val latest by collectLastValue(underTest.userColumns)

                setUserColumns(5)
                assertThat(latest).isEqualTo(5)

                setUserColumns(3)
                assertThat(latest).isEqualTo(3)
            }
        }

    @Test
    fun userColumns_outOfRange_isClamped() =
        with(kosmos) {
            testScope.runTest {
                val latest by collectLastValue(underTest.userColumns)

                setUserColumns(1)
                assertThat(latest).isEqualTo(QSColumnsRepository.MIN_USER_COLUMNS)

                setUserColumns(9)
                assertThat(latest).isEqualTo(6)
            }
        }

    @Test
    fun userColumns_configChange_reappliesMaxColumns() =
        with(kosmos) {
            testScope.runTest {
                val latest by collectLastValue(underTest.userColumns)
                setUserColumns(6)

                testCase.context.orCreateTestableResources.addOverride(
                    com.android.internal.R.integer.config_qsTileColumnsMax,
                    5,
                )
                fakeConfigurationRepository.onConfigurationChange()

                assertThat(latest).isEqualTo(5)
            }
        }

    @Test
    fun withDualShade_returnsCorrectValue() =
        with(kosmos) {
            testScope.runTest {
                val latest by collectLastValue(underTest.dualShadeColumns)

                assertThat(latest).isEqualTo(2)
            }
        }

    private suspend fun setUserColumns(columns: Int) =
        kosmos.fakeSecureSettingsRepository.setInt(
            QSColumnsRepository.USER_COLUMNS_SETTING,
            columns,
        )
}
