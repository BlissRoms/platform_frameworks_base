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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
import com.android.systemui.grid.ui.compose.VerticalSpannedGrid
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.QSMaterialExpressiveTiles
import androidx.compose.runtime.CompositionLocalProvider
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSPanelStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileColumns
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileLabelHide
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileQqsRows
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileShape
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileAnimationStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.LocalQSTileOpacity
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileAnimationStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.Tile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSPanelStyle
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileColumns
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileLabelHide
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileQqsRows
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileShape
import com.android.systemui.qs.panels.ui.compose.infinitegrid.rememberQSTileOpacity
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
    val panelStyle = rememberQSPanelStyle()
    val isClassicStyle = panelStyle == 1
    val hideTileLabels = rememberQSTileLabelHide()
    val customColumns = rememberQSTileColumns()
    val customQqsRows = rememberQSTileQqsRows()
    val tileShape = rememberQSTileShape()
    val tileOpacity = rememberQSTileOpacity()
    val tileAnimationStyle = rememberQSTileAnimationStyle()
    val columns = if (isClassicStyle) customColumns else viewModel.columns
    val sizedTiles = if (isClassicStyle) {
        val maxTiles = columns * customQqsRows
        viewModel.allTileViewModels
            .take(maxTiles)
            .fastMap { SizedTileImpl(it, 1) }
    } else {
        viewModel.tileViewModels
    }
    val tiles = sizedTiles.fastMap { it.tile }
    val squishiness by viewModel.squishinessViewModel.squishiness.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalQSPanelStyle provides panelStyle,
        LocalQSTileLabelHide provides hideTileLabels,
        LocalQSTileColumns provides customColumns,
        LocalQSTileQqsRows provides customQqsRows,
        LocalQSTileShape provides tileShape,
        LocalQSTileOpacity provides tileOpacity,
        LocalQSTileAnimationStyle provides tileAnimationStyle,
    ) {
    Box(modifier = modifier) {
        GridAnchor()

        if (QSMaterialExpressiveTiles.isEnabled) {
            ButtonGroupGrid(
                sizedTiles = sizedTiles,
                columns = columns,
                keys = { it.spec },
                elementKey = { it.spec.toElementKey() },
                horizontalPadding = dimensionResource(R.dimen.qs_tile_margin_horizontal),
                modifier = Modifier.sysuiResTag("qqs_tile_layout"),
            ) { sizedTile, interactionSource ->
                Tile(
                    tile = sizedTile.tile,
                    iconOnly = isClassicStyle || sizedTile.isIcon,
                    squishiness = { squishiness },
                    coroutineScope = scope,
                    tileHapticsViewModelFactoryProvider =
                        viewModel.tileHapticsViewModelFactoryProvider,
                    // There should be no QuickQuickSettings when the details
                    // view is enabled.
                    detailsViewModel = null,
                    isVisible = listening,
                    bounceableInfo = null,
                    interactionSource = interactionSource,
                )
            }
        } else {
            val bounceables =
                remember(sizedTiles) { List(sizedTiles.size) { BounceableTileViewModel() } }
            val spans by remember(sizedTiles) { derivedStateOf { sizedTiles.fastMap { it.width } } }
            VerticalSpannedGrid(
                columns = columns,
                columnSpacing = dimensionResource(R.dimen.qs_tile_margin_horizontal),
                rowSpacing = dimensionResource(R.dimen.qs_tile_margin_vertical),
                spans = spans,
                modifier = Modifier.sysuiResTag("qqs_tile_layout"),
                keys = { sizedTiles[it].tile.spec },
            ) { spanIndex, column, isFirstInColumn, isLastInColumn ->
                val it = sizedTiles[spanIndex]
                Element(it.tile.spec.toElementKey(), Modifier) {
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
                        tileHapticsViewModelFactoryProvider =
                            viewModel.tileHapticsViewModelFactoryProvider,
                        // There should be no QuickQuickSettings when the details view is enabled.
                        detailsViewModel = null,
                        isVisible = listening,
                        interactionSource = null,
                    )
                }
            }
        }
    }
    }

    TileListener(tiles, listening)
}
