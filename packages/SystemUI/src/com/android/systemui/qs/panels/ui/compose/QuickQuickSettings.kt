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

package com.android.systemui.qs.panels.ui.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.grid.ui.compose.GridSpan
import com.android.systemui.grid.ui.compose.TwoDimensionalSpannedGrid
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.grid.ui.compose.calculate2DPlacements
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSPanelStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSQuadTiles
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileAnimationStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileLabelHide
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileOpacity
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileShape
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberNothingQSMaxCellSize
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSPanelStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileAnimationStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileColumns
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileLabelHide
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileOpacity
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileQqsRows
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileQsRows
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileShape
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQuadTiles
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileLayout
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.QuickQuickSettingsViewModel
import com.android.systemui.qs.shared.ui.QuickSettings.Elements.toElementKey
import com.android.systemui.res.R

@Composable
fun ContentScope.QuickQuickSettings(
    viewModel: QuickQuickSettingsViewModel,
    modifier: Modifier = Modifier,
    listening: () -> Boolean,
) {
    val customColumns = rememberQSTileColumns()
    val customQqsRows = rememberQSTileQqsRows()
    val customQsRows = rememberQSTileQsRows() // expanded QS rows
    val tileShape = rememberQSTileShape()
    val tileOpacity = rememberQSTileOpacity()
    val tileAnimationStyle = rememberQSTileAnimationStyle()
    val panelStyle = rememberQSPanelStyle()
    val isClassicStyle = panelStyle == 1
    val isNothingStyle = panelStyle == 5
    val quadTiles = rememberQuadTiles()
    val columns = if (isNothingStyle) 4 else if (isClassicStyle) customColumns else viewModel.columns
    val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val largeTiles by viewModel.iconTilesViewModel.largeTiles.collectAsStateWithLifecycle()

    if (isNothingStyle) {
        val qqsMaxRows = 2
        val tileLayout = rememberQSTileLayout()
        val allTiles = viewModel.allTileViewModels.map { it.tile }
        val allSpans = remember(allTiles, largeTiles, quadTiles) {
            allTiles.map {
                if (quadTiles.contains(it.spec)) GridSpan(colSpan = 2, rowSpan = 2)
                else if (largeTiles.contains(it.spec)) GridSpan(colSpan = 2, rowSpan = 1)
                else GridSpan(colSpan = 1, rowSpan = 1)
            }
        }
        val preferredPlacements = remember(allTiles, tileLayout) {
            allTiles.mapIndexedNotNull { index, tile ->
                tileLayout[tile.spec]?.let { index to it }
            }.toMap()
        }
        val qqsPlacements = remember(allSpans, columns, qqsMaxRows, preferredPlacements) {
            calculate2DPlacements(
                allSpans,
                columns,
                preferredPlacements = preferredPlacements,
                maxRows = qqsMaxRows,
            )
        }
        val qqsTiles = remember(qqsPlacements, allTiles) {
            qqsPlacements.map { allTiles[it.index] }
        }
        val qqsSpans = remember(qqsPlacements, allSpans) {
            qqsPlacements.map { allSpans[it.index] }
        }

        Box(modifier = modifier) {
            GridAnchor()

            TwoDimensionalSpannedGrid(
                columns = columns,
                columnSpacing = 16.dp,
                rowSpacing = 16.dp,
                spans = qqsSpans,
                customPlacements = qqsPlacements.mapIndexed { idx, p -> p.copy(index = idx) },
                useSquareCells = true,
                maxCellSize = rememberNothingQSMaxCellSize(),
                keys = { qqsTiles[it].spec },
                modifier = Modifier.sysuiResTag("qqs_tile_layout"),
            ) { spanIndex, placement ->
                val it = qqsTiles[spanIndex]
                val interactionSource = remember(it.spec) { MutableInteractionSource() }
                Element(it.spec.toElementKey(), Modifier) {
                    CompositionLocalProvider(
                        LocalQSPanelStyle provides panelStyle,
                        LocalQSTileLabelHide provides rememberQSTileLabelHide(),
                        LocalQSTileShape provides tileShape,
                        LocalQSTileOpacity provides tileOpacity,
                        LocalQSTileAnimationStyle provides tileAnimationStyle,
                        LocalQSQuadTiles provides quadTiles,
                    ) {
                        Tile(
                            tile = it,
                            iconOnly = !largeTiles.contains(it.spec) && !quadTiles.contains(it.spec),
                            squishiness = { squishiness },
                            coroutineScope = scope,
                            bounceableInfo = null,
                            tileHapticsViewModelFactory = viewModel.tileHapticsViewModelFactory,
                            interactionSource = interactionSource,
                            detailsViewModel = null,
                            isVisible = listening,
                        )
                    }
                }
            }
        }

        TileListener(qqsTiles, listening)
    } else {
        val sizedTiles = if (isClassicStyle) {
            val maxTiles = customColumns * customQqsRows
            viewModel.allTileViewModels.take(maxTiles)
        } else {
            viewModel.tileViewModels
        }
        
        val classicMaxTiles = customColumns * customQsRows
        val nonClassicMaxTiles = columns * customQqsRows

        val filteredSizedTiles = if (isClassicStyle) {
            sizedTiles.take(classicMaxTiles)
        } else {
            sizedTiles.take(nonClassicMaxTiles)
        }

        Box(modifier = modifier) {
            GridAnchor()

            val bounceables =
                remember(filteredSizedTiles) { List(filteredSizedTiles.size) { BounceableTileViewModel() } }
            val spans by remember(filteredSizedTiles) { derivedStateOf { filteredSizedTiles.fastMap { it.width } } }

            VerticalSpannedGrid(
                columns = columns,
                columnSpacing = dimensionResource(R.dimen.qs_tile_margin_horizontal),
                rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
                spans = spans,
                modifier = Modifier.sysuiResTag("qqs_tile_layout"),
                keys = { filteredSizedTiles[it].tile.spec },
            ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
                val it = filteredSizedTiles[spanIndex]
                val interactionSource = remember(it.tile.spec) { MutableInteractionSource() }
                Element(it.tile.spec.toElementKey(), Modifier) {
                    CompositionLocalProvider(
                        LocalQSPanelStyle provides rememberQSPanelStyle(),
                        LocalQSTileLabelHide provides rememberQSTileLabelHide(),
                        LocalQSTileShape provides tileShape,
                        LocalQSTileOpacity provides tileOpacity,
                        LocalQSTileAnimationStyle provides tileAnimationStyle,
                    ) {
                        Tile(
                            tile = it.tile,
                            iconOnly = isClassicStyle || it.isIcon,
                            squishiness = { squishiness },
                            coroutineScope = scope,
                            bounceableInfo =
                                bounceables.bounceableInfo(
                                    it,
                                    index = spanIndex,
                                    column = column,
                                    columns = columns,
                                    isFirstInRow = isFirstInColumn,
                                    isLastInRow = isLastInColumn,
                                ),
                            tileHapticsViewModelFactory = viewModel.tileHapticsViewModelFactory,
                            interactionSource = interactionSource,
                            detailsViewModel = null,
                            isVisible = listening,
                        )
                    }
                }
            }
        }

        val tilesOnly = filteredSizedTiles.fastMap { it.tile }
        TileListener(tilesOnly, listening)
    }
}