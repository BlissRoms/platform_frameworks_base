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

package com.android.systemui.qs.panels.ui.model

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Immutable
import com.android.systemui.qs.panels.shared.model.SizedTile
import com.android.systemui.qs.panels.shared.model.autoArrangeTiles
import com.android.systemui.qs.panels.shared.model.splitInRowsSequence
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.shared.model.CategoryAndName

import com.android.systemui.qs.pipeline.shared.TileSpec

/** Represents an item from a grid associated with a row and a span */
sealed interface GridCell {
    val row: Int
    val span: GridItemSpan
}

/**
 * Represents a [EditTileViewModel] from a grid associated with a tile format and the row and column
 * it's positioned at
 */
@Immutable
data class TileGridCell(
    override val tile: EditTileViewModel,
    override val row: Int,
    override val width: Int,
    override val span: GridItemSpan = GridItemSpan(width),
    val column: Int,
    val isQuad: Boolean = false,
) : GridCell, SizedTile<EditTileViewModel>, CategoryAndName by tile {
    val key: String = "${tile.tileSpec.spec}-$row"

    constructor(
        sizedTile: SizedTile<EditTileViewModel>,
        row: Int,
        column: Int,
        isQuad: Boolean = false,
    ) : this(tile = sizedTile.tile, row = row, column = column, width = sizedTile.width, isQuad = isQuad)
}

/** Represents an empty space used to fill incomplete rows. Will always display as a 1x1 tile */
@Immutable
data class SpacerGridCell(
    override val row: Int,
    val column: Int = 0,
    override val span: GridItemSpan = GridItemSpan(1),
) : GridCell {
    val key: String = "spacer-$row-$column"
}

/**
 * Generates a list of [GridCell] from a list of [SizedTile]
 *
 * Builds rows based on the tiles' widths, and fill each hole with a [SpacerGridCell]
 *
 * @param startingRow The row index the grid is built from, used in cases where only end rows need
 *   to be regenerated
 */
fun List<SizedTile<EditTileViewModel>>.toGridCells(
    columns: Int,
    startingRow: Int = 0,
    quadTiles: Set<TileSpec> = emptySet(),
    preferredPositions: Map<TileSpec, Pair<Int, Int>> = emptyMap(),
    prioritySpec: TileSpec? = null,
    isNothingStyle: Boolean = true,
): List<GridCell> {
    if (!isNothingStyle || (quadTiles.isEmpty() && preferredPositions.isEmpty())) {
        val arrangedTiles = if (!isNothingStyle) autoArrangeTiles(this, columns) else this
        return splitInRowsSequence(arrangedTiles, columns)
            .flatMapIndexed { rowIndex, sizedTiles ->
                val correctedRowIndex = rowIndex + startingRow
                var column = 0
                val row: List<GridCell> =
                    sizedTiles.map {
                        val isQuad = if (isNothingStyle) quadTiles.contains(it.tile.tileSpec) else false
                        TileGridCell(it, correctedRowIndex, column, isQuad = isQuad).also { cell ->
                            column += cell.width
                        }
                    }

                // Fill incomplete rows with spacers ONLY if isNothingStyle is true
                if (isNothingStyle) {
                    val numSpacers = columns - sizedTiles.sumOf { it.width }
                    row.toMutableList().apply {
                        repeat(numSpacers) { c -> add(SpacerGridCell(correctedRowIndex, column + c)) }
                    }
                } else {
                    row
                }
            }
            .toList()
    }

    val occupied = mutableListOf<BooleanArray>()
    fun isOccupied(r: Int, c: Int): Boolean {
        if (r >= occupied.size) return false
        return occupied[r][c]
    }
    fun setOccupied(r: Int, c: Int) {
        while (r >= occupied.size) {
            occupied.add(BooleanArray(columns))
        }
        occupied[r][c] = true
    }

    val placedCells = mutableListOf<TileGridCell>()
    val sortedTiles = if (prioritySpec != null) {
        sortedByDescending { it.tile.tileSpec == prioritySpec }
    } else {
        this
    }
    for (sizedTile in sortedTiles) {
        val spec = sizedTile.tile.tileSpec
        val isQuad = quadTiles.contains(spec)
        val colSpan = sizedTile.width.coerceIn(1, columns)
        val rowSpan = if (isQuad) 2 else 1

        var foundRow = -1
        var foundCol = -1

        val preferred = preferredPositions[spec]
        if (preferred != null) {
            val pr = preferred.first - startingRow
            val pc = preferred.second
            if (pr >= 0 && pc in 0..(columns - colSpan)) {
                var canFit = true
                for (dr in 0 until rowSpan) {
                    for (dc in 0 until colSpan) {
                        if (isOccupied(pr + dr, pc + dc)) {
                            canFit = false
                            break
                        }
                    }
                    if (!canFit) break
                }
                if (canFit) {
                    foundRow = pr
                    foundCol = pc
                }
            }
        }

        if (foundRow == -1) {
            var r = 0
            while (foundRow == -1) {
                for (c in 0..(columns - colSpan)) {
                    var canFit = true
                    for (dr in 0 until rowSpan) {
                        for (dc in 0 until colSpan) {
                            if (isOccupied(r + dr, c + dc)) {
                                canFit = false
                                break
                            }
                        }
                        if (!canFit) break
                    }
                    if (canFit) {
                        foundRow = r
                        foundCol = c
                        break
                    }
                }
                if (foundRow == -1) {
                    r++
                }
            }
        }

        for (dr in 0 until rowSpan) {
            for (dc in 0 until colSpan) {
                setOccupied(foundRow + dr, foundCol + dc)
            }
        }

        placedCells.add(
            TileGridCell(sizedTile, foundRow + startingRow, foundCol, isQuad = isQuad)
        )
    }

    val totalRows = occupied.size
    val result = mutableListOf<GridCell>()
    for (r in 0 until totalRows) {
        var c = 0
        while (c < columns) {
            val tile = placedCells.find { it.row == (r + startingRow) && it.column == c }
            if (tile != null) {
                result.add(tile)
                c += tile.width
            } else if (isOccupied(r, c)) {
                c++
            } else {
                result.add(SpacerGridCell(r + startingRow, c))
                c++
            }
        }
    }

    if (isNothingStyle) {
        for (r in totalRows until maxOf(4, totalRows)) {
            for (c in 0 until columns) {
                result.add(SpacerGridCell(r + startingRow, c))
            }
        }
    }

    return result
}
