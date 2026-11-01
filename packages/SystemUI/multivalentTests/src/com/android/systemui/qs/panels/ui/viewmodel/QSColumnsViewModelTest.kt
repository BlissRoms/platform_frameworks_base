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

package com.android.systemui.qs.panels.ui.viewmodel

import android.content.res.Configuration
import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.configurationRepository
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QQS
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager.Companion.LOCATION_QS
import com.android.systemui.media.controls.ui.controller.MediaLocation
import com.android.systemui.media.controls.ui.controller.mediaHostStatesManager
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.remedia.data.repository.setHasMedia
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.panels.data.repository.QSColumnsRepository
import com.android.systemui.qs.panels.data.repository.qsColumnsRepository
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shared.settings.data.repository.fakeSecureSettingsRepository
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class QSColumnsViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = true
            testCase.context.orCreateTestableResources.addOverride(
                com.android.internal.R.integer.config_qsTileColumnsMax,
                MAX_USER_COLUMNS,
            )
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_dual_shade_num_columns,
                DUAL_SHADE_COLUMNS,
            )
            testCase.context.orCreateTestableResources.addOverride(
                R.integer.quick_settings_infinite_grid_tile_max_width,
                4,
            )
            qsColumnsRepository =
                QSColumnsRepository(
                    mainResources,
                    configurationRepository,
                    secureSettingsRepository,
                )
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun mediaLocationNull_singleOrSplit_alwaysSingleShadeColumns() =
        kosmos.runTest {
            val underTest = qsColumnsViewModelFactory.create(null, null)
            underTest.activateIn(testScope)
            disableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            makeMediaVisible(LOCATION_QQS, visible = true)
            makeMediaVisible(LOCATION_QS, visible = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_DUAL_SHADE)
    fun mediaLocationNull_dualShade_alwaysDualShadeColumns() =
        kosmos.runTest {
            val underTest = qsColumnsViewModelFactory.create(null, null)
            underTest.activateIn(testScope)
            enableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            makeMediaVisible(LOCATION_QQS, visible = true)
            makeMediaVisible(LOCATION_QS, visible = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_DUAL_SHADE)
    fun mediaLocationQS_dualShade_alwaysDualShadeColumns() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QS,
                    QuickSettingsContainerViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)
            enableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            makeMediaVisible(LOCATION_QS, visible = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_DUAL_SHADE)
    fun mediaLocationQQS_dualShade_alwaysDualShadeColumns() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)
            enableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            makeMediaVisible(LOCATION_QQS, visible = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(DUAL_SHADE_COLUMNS)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun mediaLocationQS_singleOrSplit_halfColumnsOnCorrectConfigurationAndVisible() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QS,
                    QuickSettingsContainerViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)
            disableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)

            makeMediaVisible(LOCATION_QS, visible = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS / 2)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun mediaLocationQQS_singleOrSplit_halfColumnsOnCorrectConfigurationAndVisible() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)
            disableDualShade()

            setConfigurationForMediaInRow(mediaInRow = false)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)

            setConfigurationForMediaInRow(mediaInRow = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS)

            makeMediaVisible(LOCATION_QQS, visible = true)

            assertThat(underTest.columns).isEqualTo(SINGLE_SPLIT_SHADE_COLUMNS / 2)
        }

    @Test
    fun largeSpan_normalTiles_ignoreColumns() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)

            // Set extra large tiles to false
            val configuration = Configuration().apply { this.fontScale = 1f }
            context.orCreateTestableResources.overrideConfiguration(configuration)
            fakeConfigurationRepository.onConfigurationChange()

            // Not using extra large tiles means that we stay to the default width of 2, regardless
            // of columns
            assertThat(underTest.largeSpan).isEqualTo(2)

            setColumns(10)
            assertThat(underTest.largeSpan).isEqualTo(2)
        }

    @Test
    fun largeSpan_extraLargeTiles_tracksColumns() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)

            // Set extra large tiles to true
            val configuration = Configuration().apply { this.fontScale = 2f }
            context.orCreateTestableResources.overrideConfiguration(configuration)
            fakeConfigurationRepository.onConfigurationChange()

            // Using extra large tiles with a max width of 4 means that we change the width to the
            // same as the columns if equal or under 4, otherwise we divide it in half
            assertThat(underTest.largeSpan).isEqualTo(4)

            setColumns(3)
            assertThat(underTest.largeSpan).isEqualTo(3)

            setColumns(6)
            assertThat(underTest.largeSpan).isEqualTo(3)

            setColumns(8)
            assertThat(underTest.largeSpan).isEqualTo(4)
        }

    @Test
    fun largeSpan_extraLargeTiles_tracksMaxWidth() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)

            // Set extra large tiles to true
            val configuration = Configuration().apply { this.fontScale = 2f }
            context.orCreateTestableResources.overrideConfiguration(configuration)
            fakeConfigurationRepository.onConfigurationChange()

            // Using extra large tiles with 4 columns means that we change the width to be 4, unless
            // we're using a max width lower than 4 in which case divide it in half
            assertThat(underTest.largeSpan).isEqualTo(4)

            setMaxWidth(3)
            assertThat(underTest.largeSpan).isEqualTo(2)

            setMaxWidth(6)
            assertThat(underTest.largeSpan).isEqualTo(4)

            setMaxWidth(8)
            assertThat(underTest.largeSpan).isEqualTo(4)
        }

    @Test
    fun largeSpan_tracksExtraLargeTiles() =
        kosmos.runTest {
            val underTest =
                qsColumnsViewModelFactory.create(
                    LOCATION_QQS,
                    QuickQuickSettingsViewModel.mediaUiBehavior,
                )
            underTest.activateIn(testScope)

            // Set extra large tiles to false
            val configuration = Configuration().apply { this.fontScale = 1f }
            context.orCreateTestableResources.overrideConfiguration(configuration)
            fakeConfigurationRepository.onConfigurationChange()

            assertThat(underTest.largeSpan).isEqualTo(2)

            // Set extra large tiles to true
            configuration.fontScale = 2f
            fakeConfigurationRepository.onConfigurationChange()
            assertThat(underTest.largeSpan).isEqualTo(4)
        }

    private suspend fun setColumns(columns: Int) =
        kosmos.fakeSecureSettingsRepository.setInt(
            QSColumnsRepository.USER_COLUMNS_SETTING,
            columns,
        )

    private fun setMaxWidth(width: Int) =
        setValueInConfig(width, R.integer.quick_settings_infinite_grid_tile_max_width)

    private fun setValueInConfig(value: Int, id: Int) =
        with(kosmos) {
            testCase.context.orCreateTestableResources.addOverride(id, value)
            fakeConfigurationRepository.onConfigurationChange()
        }

    companion object {
        private const val SINGLE_SPLIT_SHADE_COLUMNS = QSColumnsRepository.DEFAULT_USER_COLUMNS
        private const val MAX_USER_COLUMNS = 10
        private const val DUAL_SHADE_COLUMNS = 2

        private fun Kosmos.makeMediaVisible(@MediaLocation location: Int, visible: Boolean) {
            mediaHostStatesManager.updateHostState(
                location,
                MediaHost.MediaHostStateHolder().apply { this.visible = visible },
            )

            // Active media will appear either in QQS or QS.
            setHasMedia(visible)
        }
    }
}
