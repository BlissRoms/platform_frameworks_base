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

package com.android.systemui.qs.panels.shared.model

/** Represents a tile of type [T] associated with a width */
interface SizedTile<T> {
    val tile: T
    val width: Int
    val isIcon: Boolean
        get() = width == 1
}

data class SizedTileImpl<T>(
    override val tile: T,
    override val width: Int,
) : SizedTile<T>

/** Represents a row of [SizedTile] with a maximum width of [columns] */
class TileRow<T>(private val columns: Int) {
    private var availableColumns = columns
    private val _tiles: MutableList<SizedTile<T>> = mutableListOf()
    val tiles: List<SizedTile<T>>
        get() = _tiles.toList()

    fun maybeAddTile(tile: SizedTile<T>): Boolean {
        if (availableColumns - tile.width >= 0) {
            _tiles.add(tile)
            availableColumns -= tile.width
            return true
        }
        return false
    }

    fun findLastIconTile(): SizedTile<T>? {
        return _tiles.findLast { it.width == 1 }
    }

    fun removeTile(tile: SizedTile<T>) {
        _tiles.remove(tile)
        availableColumns += tile.width
    }

    fun clear() {
        _tiles.clear()
        availableColumns = columns
    }

    fun isFull(): Boolean = availableColumns == 0
}

/**
 * Converts a list of [SizedTile] to a sequence of rows based on the number of columns of the grid
 */
fun <T> splitInRowsSequence(
    tiles: List<SizedTile<T>>,
    columns: Int,
): Sequence<List<SizedTile<T>>> = sequence {
    val row = TileRow<T>(columns)
    for (tile in tiles) {
        check(tile.width <= columns)
        if (!row.maybeAddTile(tile)) {
            // Couldn't add tile to previous row, create a row with the current tiles
            // and start a new one
            yield(row.tiles)
            row.clear()
            row.maybeAddTile(tile)
        }
    }
    if (row.tiles.isNotEmpty()) {
        yield(row.tiles)
    }
}

/**
 * Auto-arranges sized tiles for a standard grid so that rows are cleanly filled to [columns]
 * without leaving single-column holes/empty spaces.
 */
fun <T> autoArrangeTiles(
    tiles: List<SizedTile<T>>,
    columns: Int,
    largeSpan: Int = 2,
): List<SizedTile<T>> {
    if (columns <= 0 || tiles.isEmpty() || largeSpan <= 1) return tiles

    val remaining = tiles.toMutableList()
    val result = mutableListOf<SizedTile<T>>()

    while (remaining.isNotEmpty()) {
        val rowTiles = mutableListOf<SizedTile<T>>()
        var rowSpace = columns

        while (rowSpace > 0 && remaining.isNotEmpty()) {
            if (rowSpace >= largeSpan) {
                val tile = remaining.removeAt(0)
                rowTiles.add(tile)
                rowSpace -= tile.width
            } else {
                // Exactly 1 space left in this row (rowSpace == 1)
                val iconIdx = remaining.indexOfFirst { it.width == 1 }
                if (iconIdx != -1) {
                    val iconTile = remaining.removeAt(iconIdx)
                    rowTiles.add(iconTile)
                    rowSpace -= 1
                } else {
                    // No more width 1 tiles in remaining.
                    // To prevent an incomplete row with an empty hole,
                    // expand an icon tile already placed in this row to largeSpan if possible.
                    val iconInRowIdx = rowTiles.indexOfLast { it.width == 1 }
                    if (iconInRowIdx != -1) {
                        val iconToExpand = rowTiles.removeAt(iconInRowIdx)
                        rowTiles.add(iconInRowIdx, SizedTileImpl(iconToExpand.tile, largeSpan))
                        rowSpace -= (largeSpan - 1)
                    } else {
                        break
                    }
                }
            }
        }
        result.addAll(rowTiles)
    }
    return result
}
