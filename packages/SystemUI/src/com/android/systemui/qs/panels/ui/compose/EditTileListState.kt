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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import com.android.systemui.qs.panels.shared.model.SizedTile
import com.android.systemui.qs.panels.shared.model.SizedTileImpl
import com.android.systemui.qs.panels.ui.compose.selection.PlacementEvent
import com.android.systemui.qs.panels.ui.model.GridCell
import com.android.systemui.qs.panels.ui.model.SpacerGridCell
import com.android.systemui.qs.panels.ui.model.TileGridCell
import com.android.systemui.qs.panels.ui.model.toGridCells
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec

/**
 * Holds the state for the tiles to display and builds a grid using their sizes and the available
 * columns.
 */
class EditTileListState(
    initialTiles: List<EditTileViewModel>,
    initialLargeTiles: Set<TileSpec>,
    val columns: Int,
    val largeTilesSpan: Int,
    val isNothingStyle: Boolean = false,
    initialQuadTiles: Set<TileSpec> = emptySet(),
    initialPositions: Map<TileSpec, Pair<Int, Int>> = emptyMap(),
) : DragAndDropState {
    override var draggedCell by mutableStateOf<SizedTile<EditTileViewModel>?>(null)
        private set

    override var draggedPosition by mutableStateOf(Offset.Unspecified)
        private set

    override var dragType by mutableStateOf<DragType?>(null)
        private set

    // A dragged cell can be removed if it was added in the drag movement OR if it's marked as
    // removable
    override val isDraggedCellRemovable: Boolean
        get() = dragType == DragType.Add || draggedCell?.tile?.isRemovable ?: false

    override val dragInProgress: Boolean
        get() = draggedCell != null

    var largeTilesSpecs: Set<TileSpec> = initialLargeTiles
        private set

    var quadTilesSpecs: Set<TileSpec> = if (isNothingStyle) initialQuadTiles else emptySet()
        private set

    var tilePositions: MutableMap<TileSpec, Pair<Int, Int>> =
        (if (isNothingStyle) initialPositions else emptyMap()).toMutableMap()
        private set

    private val _tiles: SnapshotStateList<GridCell> =
        initialTiles.toGridCells(initialLargeTiles, quadTilesSpecs, tilePositions).toMutableStateList()
    val tiles: List<GridCell> = _tiles

    init {
        if (isNothingStyle) {
            _tiles.filterIsInstance<TileGridCell>().forEach { cell ->
                tilePositions[cell.tile.tileSpec] = cell.row to cell.column
            }
        }
    }

    /** Update the grid with this new list of tiles and new set of large tileSpecs. */
    fun updateTiles(
        tiles: List<EditTileViewModel>,
        largeTiles: Set<TileSpec>,
        quadTiles: Set<TileSpec> = if (isNothingStyle) quadTilesSpecs else emptySet(),
        positions: Map<TileSpec, Pair<Int, Int>> = if (isNothingStyle) tilePositions else emptyMap(),
    ) {
        largeTilesSpecs = largeTiles
        quadTilesSpecs = if (isNothingStyle) quadTiles else emptySet()
        tilePositions = (if (isNothingStyle) positions else emptyMap()).toMutableMap()
        tiles.toGridCells(largeTiles, quadTilesSpecs, tilePositions).let {
            _tiles.apply {
                clear()
                addAll(it)
            }
        }
        if (isNothingStyle) {
            _tiles.filterIsInstance<TileGridCell>().forEach { cell ->
                tilePositions[cell.tile.tileSpec] = cell.row to cell.column
            }
        }
    }

    fun tileSpecs(): List<TileSpec> {
        return _tiles.filterIsInstance<TileGridCell>().map { it.tile.tileSpec }
    }

    /**
     * Finds the closest tile to the given tileSpec.
     *
     * If the given tileSpec is found in the list of tileSpecs, this function returns the next tile
     * in the list. If the given tileSpec is the last tile in the list, this function returns the
     * previous tile in the list. If the given tileSpec is not found in the list of tileSpecs, this
     * function returns null.
     *
     * @param tileSpec The tileSpec to find the closest tile to.
     * @return The closest tile to the given tileSpec, or null if the given tileSpec is not found.
     */
    fun findNeighboringTile(tileSpec: TileSpec): TileSpec? {
        val specs = tileSpecs()
        return when (val index = specs.indexOf(tileSpec)) {
            -1 -> null
            specs.size - 1 -> { // Last element
                if (specs.size > 1) {
                    specs[index - 1]
                } else {
                    null
                }
            }
            else -> specs[index + 1] // Return next element
        }
    }

    private fun indexOf(tileSpec: TileSpec): Int {
        return _tiles.indexOfFirst { it is TileGridCell && it.tile.tileSpec == tileSpec }
    }

    /** Resize the tile corresponding to the [TileSpec] to [toIcon] or [toQuad] */
    fun resizeTile(tileSpec: TileSpec, toIcon: Boolean, toQuad: Boolean = false) {
        val fromIndex = indexOf(tileSpec)
        if (fromIndex != INVALID_INDEX) {
            val cell = _tiles[fromIndex] as TileGridCell
            val newWidth = if (toIcon) 1 else largeTilesSpan

            if (cell.width == newWidth && cell.isQuad == toQuad) return

            quadTilesSpecs = if (toQuad) quadTilesSpecs + tileSpec else quadTilesSpecs - tileSpec
            largeTilesSpecs = if (toIcon) largeTilesSpecs - tileSpec else largeTilesSpecs + tileSpec

            val colSpan = newWidth.coerceIn(1, columns)
            val currentPos = tilePositions[tileSpec]
            if (currentPos != null) {
                val (r, c) = currentPos
                val safeCol = c.coerceIn(0, columns - colSpan)
                tilePositions[tileSpec] = r to safeCol
            }

            _tiles[fromIndex] = cell.copy(width = newWidth, isQuad = toQuad)
            regenerateGrid(prioritySpec = tileSpec)
        }
    }

    override fun isMoving(tileSpec: TileSpec): Boolean {
        return draggedCell?.let { it.tile.tileSpec == tileSpec } ?: false
    }

    override fun onStarted(cell: SizedTile<EditTileViewModel>, dragType: DragType) {
        draggedCell = cell
        this.dragType = dragType
    }

    override fun onTargeting(target: Int, insertAfter: Boolean) {
        val draggedTile = draggedCell ?: return

        val fromIndex = indexOf(draggedTile.tile.tileSpec)
        if (fromIndex == target) {
            return
        }

        val insertionIndex = if (insertAfter) target + 1 else target
        if (fromIndex != INVALID_INDEX) {
            val cell = _tiles.removeAt(fromIndex)
            _tiles.add(insertionIndex.coerceIn(0, _tiles.size), cell)
        } else {
            // Add the tile with a temporary row/col which will get reassigned when
            // regenerating spacers
            _tiles.add(
                insertionIndex.coerceIn(0, _tiles.size),
                TileGridCell(draggedTile, 0, 0, isQuad = isNothingStyle && quadTilesSpecs.contains(draggedTile.tile.tileSpec)),
            )
        }

        regenerateGrid()
    }

    override fun onMoved(offset: Offset) {
        draggedPosition = offset
    }

    override fun movedOutOfBounds() {
        val draggedTile = draggedCell ?: return

        _tiles.removeIf { cell ->
            cell is TileGridCell && cell.tile.tileSpec == draggedTile.tile.tileSpec
        }
        draggedPosition = Offset.Unspecified

        // Regenerate spacers without the dragged tile
        regenerateGrid()
    }

    override fun onDrop() {
        draggedCell = null
        draggedPosition = Offset.Unspecified
        dragType = null

        // Remove the spacers
        regenerateGrid()
    }

    /**
     * Return the appropriate index to move the tile to for the placement [event]
     *
     * The grid includes spacers. As a result, indexes from the grid need to be translated to the
     * corresponding index from [currentTileSpecs].
     */
    fun targetIndexForPlacement(event: PlacementEvent): Int {
        val currentTileSpecs = tileSpecs()
        return when (event) {
            is PlacementEvent.PlaceToTileSpec -> {
                currentTileSpecs.indexOf(event.targetSpec)
            }
            is PlacementEvent.PlaceToIndex -> {
                if (event.targetIndex >= _tiles.size) {
                    currentTileSpecs.size
                } else if (event.targetIndex <= 0) {
                    0
                } else {
                    // The index may point to a spacer, so first find the first tile located
                    // after index, then use its position as a target
                    val targetTile =
                        _tiles.subList(event.targetIndex, _tiles.size).firstOrNull {
                            it is TileGridCell
                        } as? TileGridCell

                    if (targetTile == null) {
                        currentTileSpecs.size
                    } else {
                        val targetIndex = currentTileSpecs.indexOf(targetTile.tile.tileSpec)
                        val fromIndex = currentTileSpecs.indexOf(event.movingSpec)
                        if (fromIndex < targetIndex) targetIndex - 1 else targetIndex
                    }
                }
            }
        }
    }

    fun tilePositions(): Map<TileSpec, Pair<Int, Int>> {
        return _tiles.filterIsInstance<TileGridCell>().associate { it.tile.tileSpec to (it.row to it.column) }
    }

    private fun List<EditTileViewModel>.toGridCells(
        largeTiles: Set<TileSpec>,
        quadTiles: Set<TileSpec> = quadTilesSpecs,
        positions: Map<TileSpec, Pair<Int, Int>> = tilePositions,
    ): List<GridCell> {
        return map {
                val isQuad = if (isNothingStyle) quadTiles.contains(it.tileSpec) else false
                val width = if (isQuad || largeTiles.contains(it.tileSpec)) largeTilesSpan else 1
                SizedTileImpl(it, width)
            }
            .toGridCells(
                columns,
                quadTiles = if (isNothingStyle) quadTiles else emptySet(),
                preferredPositions = if (isNothingStyle) positions else emptyMap(),
                isNothingStyle = isNothingStyle,
            )
    }

    fun onTargeting2D(targetRow: Int, targetCol: Int) {
        val draggedTile = draggedCell ?: return
        val spec = draggedTile.tile.tileSpec
        val isQuad = quadTilesSpecs.contains(spec)
        val colSpan = if (isQuad) 2 else draggedTile.width.coerceIn(1, columns)
        val rowSpan = if (isQuad) 2 else 1
        val safeCol = targetCol.coerceIn(0, columns - colSpan)
        val safeRow = targetRow.coerceAtLeast(0)

        val currentPos = tilePositions[spec]
        if (currentPos == (safeRow to safeCol)) {
            return
        }

        val fromIndex = indexOf(spec)
        if (fromIndex == INVALID_INDEX) {
            tilePositions[spec] = safeRow to safeCol
            _tiles.add(
                TileGridCell(draggedTile, safeRow, safeCol, isQuad = isQuad)
            )
        }

        val overlappingTiles = _tiles.filterIsInstance<TileGridCell>().filter { other ->
            other.tile.tileSpec != spec &&
            other.row < safeRow + rowSpan &&
            (other.row + if (other.isQuad) 2 else 1) > safeRow &&
            other.column < safeCol + colSpan &&
            (other.column + other.width) > safeCol
        }

        if (overlappingTiles.isEmpty()) {
            tilePositions[spec] = safeRow to safeCol
        } else {
            // When placing a tile on top of other tile(s), shift them to the right or left
            tilePositions[spec] = safeRow to safeCol

            val placedRects = mutableListOf<Triple<TileSpec, Pair<Int, Int>, Pair<Int, Int>>>()
            _tiles.filterIsInstance<TileGridCell>().forEach { cell ->
                if (cell.tile.tileSpec != spec && cell !in overlappingTiles) {
                    val r = cell.row
                    val c = cell.column
                    val w = cell.width
                    val h = if (cell.isQuad) 2 else 1
                    placedRects.add(Triple(cell.tile.tileSpec, r to c, h to w))
                }
            }
            placedRects.add(Triple(spec, safeRow to safeCol, rowSpan to colSpan))

            fun isCellOccupied(r: Int, c: Int, h: Int, w: Int): Boolean {
                if (c < 0 || c + w > columns || r < 0) return true
                return placedRects.any { (_, pos, size) ->
                    val pr = pos.first
                    val pc = pos.second
                    val ph = size.first
                    val pw = size.second
                    r < pr + ph && r + h > pr && c < pc + pw && c + w > pc
                }
            }

            for (other in overlappingTiles) {
                val oSpec = other.tile.tileSpec
                val oWidth = other.width.coerceIn(1, columns)
                val oHeight = if (other.isQuad) 2 else 1

                var newPos: Pair<Int, Int>? = null

                // 1. Try moving to the right on the same row
                var tryCol = safeCol + colSpan
                while (tryCol + oWidth <= columns) {
                    if (!isCellOccupied(other.row, tryCol, oHeight, oWidth)) {
                        newPos = other.row to tryCol
                        break
                    }
                    tryCol++
                }

                // 2. If no space on right, try moving to the left on the same row
                if (newPos == null) {
                    tryCol = safeCol - oWidth
                    while (tryCol >= 0) {
                        if (!isCellOccupied(other.row, tryCol, oHeight, oWidth)) {
                            newPos = other.row to tryCol
                            break
                        }
                        tryCol--
                    }
                }

                // 3. Try the vacated spot where dragged tile came from
                if (newPos == null && currentPos != null) {
                    val (cr, cc) = currentPos
                    val safeCc = cc.coerceIn(0, columns - oWidth)
                    if (!isCellOccupied(cr, safeCc, oHeight, oWidth)) {
                        newPos = cr to safeCc
                    }
                }

                // 4. Find the nearest empty slot (search right and down)
                if (newPos == null) {
                    var r = safeRow
                    while (newPos == null) {
                        for (c in 0..(columns - oWidth)) {
                            if (!isCellOccupied(r, c, oHeight, oWidth)) {
                                newPos = r to c
                                break
                            }
                        }
                        r++
                    }
                }

                tilePositions[oSpec] = newPos
                placedRects.add(Triple(oSpec, newPos, oHeight to oWidth))
            }
        }
        regenerateGrid(prioritySpec = spec)
    }

    /** Regenerate the list of [GridCell] with their new potential rows */
    private fun regenerateGrid(prioritySpec: TileSpec? = null) {
        if (!isNothingStyle) {
            _tiles.filterIsInstance<TileGridCell>().toGridCells(columns, isNothingStyle = false).let {
                _tiles.clear()
                _tiles.addAll(it)
            }
            return
        }
        val currentQuads = quadTilesSpecs
        _tiles.filterIsInstance<TileGridCell>().toGridCells(
            columns,
            quadTiles = currentQuads,
            preferredPositions = tilePositions,
            prioritySpec = prioritySpec,
            isNothingStyle = true,
        ).let {
            _tiles.clear()
            _tiles.addAll(it)
        }
        _tiles.filterIsInstance<TileGridCell>().forEach { cell ->
            tilePositions[cell.tile.tileSpec] = cell.row to cell.column
        }
    }

    /**
     * Regenerate the list of [GridCell] with their new potential rows from [fromIndex], leaving
     * cells before that untouched.
     */
    private fun regenerateGrid(fromIndex: Int) {
        val fromRow = _tiles[fromIndex].row
        val (pre, post) = _tiles.partition { it.row < fromRow }
        if (!isNothingStyle) {
            post.filterIsInstance<TileGridCell>().toGridCells(columns, startingRow = fromRow, isNothingStyle = false).let {
                _tiles.clear()
                _tiles.addAll(pre)
                _tiles.addAll(it)
            }
            return
        }
        val currentQuads = quadTilesSpecs
        post.filterIsInstance<TileGridCell>().toGridCells(
            columns,
            startingRow = fromRow,
            quadTiles = currentQuads,
            preferredPositions = tilePositions,
            isNothingStyle = true,
        ).let {
            _tiles.clear()
            _tiles.addAll(pre)
            _tiles.addAll(it)
        }
        _tiles.filterIsInstance<TileGridCell>().forEach { cell ->
            tilePositions[cell.tile.tileSpec] = cell.row to cell.column
        }
    }

    companion object {
        const val INVALID_INDEX = -1
    }
}
