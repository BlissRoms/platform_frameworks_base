/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.panels.ui.compose

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.bluetooth.connecteddevices.connectedBluetoothDevicesInteractor
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.haptics.msdl.tileHapticsViewModelFactory
import com.android.systemui.integration.SystemUiIntegrationTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.flags.QsSplitInternetTile
import com.android.systemui.qs.panels.domain.interactor.iconTilesInteractor
import com.android.systemui.qs.panels.ui.compose.infinitegrid.InfiniteGridLayout
import com.android.systemui.qs.panels.ui.viewmodel.InfiniteGridViewModel
import com.android.systemui.qs.panels.ui.viewmodel.detailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.editModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.iconTilesViewModel
import com.android.systemui.qs.panels.ui.viewmodel.infiniteGridViewModelFactory
import com.android.systemui.qs.panels.ui.viewmodel.textFeedbackContentViewModelFactory
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@SystemUiIntegrationTest
@RunWith(AndroidJUnit4::class)
class InfiniteGridLayoutEditTileGridTest : SysuiTestCase() {

    @get:Rule val composeRule = createComposeRule()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.viewModelUnderTest by
        Kosmos.Fixture { infiniteGridViewModelFactory.create() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            InfiniteGridLayout(
                detailsViewModel,
                iconTilesViewModel,
                viewModelFactory =
                    object : InfiniteGridViewModel.Factory {
                        override fun create(): InfiniteGridViewModel {
                            return viewModelUnderTest
                        }
                    },
                textFeedbackContentViewModelFactory,
                tileHapticsViewModelFactory,
                connectedBluetoothDevicesInteractor,
            )
        }

    @Before
    fun setUp() {
        kosmos.apply {
            currentTilesInteractor.setTiles(TestEditTiles)
            editModeViewModel.startEditing()
            usingMediaInComposeFragment = false
        }
        kosmos.run { iconTilesInteractor.setLargeTiles(initialLargeTiles) }
    }

    @Composable
    private fun TestEditTileGrid() {
        PlatformTheme {
            with(kosmos) {
                val tiles by editModeViewModel.tiles.collectAsState(emptyList())
                underTest.EditTileGrid(
                    tiles = tiles,
                    modifier = Modifier.fillMaxSize(),
                    onAddTile = editModeViewModel::addTile,
                    onRemoveTile = editModeViewModel::removeTile,
                    onSetTiles = editModeViewModel::setTiles,
                    onStopEditing = {},
                )
            }
        }
    }

    @Test
    fun moveTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            val stateOnFirstMove =
                listOf(
                    "bt",
                    internetTileSpec,
                    "flashlight",
                    "dnd",
                    "alarm",
                    "airplane",
                    "controls",
                    "wallet",
                    "battery",
                    "cast",
                )
            val stateOnSecondMove =
                listOf(
                    "bt",
                    internetTileSpec,
                    "alarm",
                    "flashlight",
                    "dnd",
                    "airplane",
                    "controls",
                    "wallet",
                    "battery",
                    "cast",
                )

            // Perform first move
            // Double tap internet
            composeRule.onNodeWithContentDescription(internetTileSpec).performTouchInput {
                doubleClick()
            }
            // Tap on bt to position internet in its spot
            composeRule.onNodeWithContentDescription("bt").performClick()
            composeRule.waitForIdle()

            // Assert the move happened
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnFirstMove)
                .inOrder()

            // Perform second move
            // Double tap alarm
            composeRule.onNodeWithContentDescription("alarm").performTouchInput { doubleClick() }
            // Tap on flashlight to position alarm in its spot
            composeRule.onNodeWithContentDescription("flashlight").performClick()
            composeRule.waitForIdle()

            // Assert the second move happened
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnSecondMove)
                .inOrder()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert we're back to the first move state
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(stateOnFirstMove)
                .inOrder()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert we're back to the initial state
            assertThat(currentTilesInteractor.currentTilesSpecs.map { it.spec })
                .containsExactlyElementsIn(TestEditTiles.map { it.spec })
                .inOrder()
        }

    @Test
    fun addTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            val latest by collectLastValue(currentTilesInteractor.currentTiles)
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Perform first addition, rotation is not current
            composeRule
                .onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG)
                .performScrollToNode(hasText("rotation"))
                .performTouchInput {
                    // Perform additional scroll to make sure the tile is above the nav bar
                    swipeUp(startY = centerY, endY = top)
                }
            composeRule.onNodeWithText("rotation").performClick()
            composeRule.waitForIdle()

            // Assert the addition happened
            assertThat(latest!!.find { it.tile.tileSpec == "rotation" }).isNotNull()

            // Perform second addition, mictoggle is not current
            composeRule
                .onNodeWithTag(AVAILABLE_TILES_GRID_TEST_TAG)
                .performScrollToNode(hasText("mictoggle"))
            composeRule.onNodeWithText("mictoggle").performClick()
            composeRule.waitForIdle()

            // Assert the addition happened
            assertThat(latest!!.find { it.tile.tileSpec == "mictoggle" }).isNotNull()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that mictoggle is no longer current
            assertThat(latest!!.find { it.tile.tileSpec == "mictoggle" }).isNull()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that rotation is no longer current
            assertThat(latest!!.find { it.tile.tileSpec == "rotation" }).isNull()
        }

    @Test
    fun removeTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            val latest by collectLastValue(currentTilesInteractor.currentTiles)
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Perform first removal.
            composeRule.removeTile(internetTileSpec)
            composeRule.waitForIdle()

            // Assert the removal happened
            assertThat(latest!!.find { it.tile.tileSpec == internetTileSpec }).isNull()

            // Perform second removal
            composeRule.removeTile("bt")
            composeRule.waitForIdle()

            // Assert the removal happened
            assertThat(latest!!.find { it.tile.tileSpec == "bt" }).isNull()

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that bluetooth is current
            assertThat(latest!!.find { it.tile.tileSpec == "bt" }).isNotNull()

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            // Assert that internet is current
            assertThat(latest!!.find { it.tile.tileSpec == internetTileSpec }).isNotNull()
        }

    @Test
    fun resizeTiles_correctlySavesSnapshot() =
        kosmos.runTest {
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            // Resize tileA to large
            composeRule
                .onNodeWithContentDescription(internetTileSpec)
                .performClick() // Select
                .performTouchInput { // Tap on resizing handle
                    click(centerRight)
                }
            composeRule.waitForIdle()

            // Assert the internet is no longer large
            assertLargeTiles(setOf("bt", "dnd", "cast"))

            // Resize flashlight to large
            composeRule
                .onNodeWithContentDescription("flashlight")
                .performClick() // Select
                .performTouchInput { // Tap on resizing handle
                    click(centerRight)
                }
            composeRule.waitForIdle()

            // Assert the resizing happened
            assertLargeTiles(setOf("bt", "dnd", "cast", "flashlight"))

            // Perform first undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            assertLargeTiles(setOf("bt", "dnd", "cast"))

            // Perform second undo
            composeRule.onNodeWithContentDescription("Undo").performClick()
            assertLargeTiles(setOf(internetTileSpec, "bt", "dnd", "cast"))
        }

    @Test
    @DisableSceneContainer
    fun bothFlagsDisabled_noExtraOptions() =
        kosmos.runTest {
            composeRule.setContent { TestEditTileGrid() }
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Settings").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Reset").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Options").assertDoesNotExist()
        }

    @Test
    @EnableFlags(Flags.FLAG_SCENE_CONTAINER, Flags.FLAG_DUAL_SHADE)
    fun onlySceneContainer_onlySettingsOption() =
        kosmos.runTest {
            enableDualShade(enabledBySetting = true)
            composeRule.setContent {
                CompositionLocalProvider(LocalResources provides testableContext.resources) {
                    TestEditTileGrid()
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithContentDescription("Settings").assertExists()
            composeRule.onNodeWithContentDescription("Reset").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("Options").assertDoesNotExist()
        }

    private fun ComposeContentTestRule.removeTile(label: String) {
        onNodeWithContentDescription(label).performClick()
        onNodeWithText("Remove").performClick()
    }

    private fun assertLargeTiles(largeSpecs: Set<String>) =
        kosmos.run {
            assertThat(viewModelUnderTest.iconTilesViewModel.largeTilesState.value.map { it.spec })
                .containsExactlyElementsIn(largeSpecs)
        }

    companion object {
        private val AVAILABLE_TILES_GRID_TEST_TAG = resIdToTestTag("AvailableTilesGrid")
        private const val OPTIONS_DROP_DOWN_TEST_TAG = "OptionsDropdown"
        private val internetTileSpec = if (QsSplitInternetTile.isEnabled) "wifi" else "internet"
        private val initialLargeTiles =
            setOf(
                TileSpec.create(internetTileSpec),
                TileSpec.create("bt"),
                TileSpec.create("dnd"),
                TileSpec.create("cast"),
            )
        private val TestEditTiles =
            listOf(
                TileSpec.create(internetTileSpec),
                TileSpec.create("bt"),
                TileSpec.create("flashlight"),
                TileSpec.create("dnd"),
                TileSpec.create("alarm"),
                TileSpec.create("airplane"),
                TileSpec.create("controls"),
                TileSpec.create("wallet"),
                TileSpec.create("battery"),
                TileSpec.create("cast"),
            )
    }
}
