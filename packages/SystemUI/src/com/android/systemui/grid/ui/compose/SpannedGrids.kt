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
package com.android.systemui.grid.ui.compose

import androidx.collection.IntIntPair
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMapIndexed
import kotlin.math.max

/**
 * Horizontal (non lazy) grid that supports [spans] for its elements.
 *
 * The elements will be laid down vertically first, and then by columns. So assuming LTR layout, it
 * will be (for a span list `[2, 1, 2, 1, 1, 1, 1, 1]` and 4 rows):
 * ```
 * 0  2  5
 * 0  2  6
 * 1  3  7
 *    4
 * ```
 *
 * where repeated numbers show larger span. If an element doesn't fit in a column due to its span,
 * it will start a new column.
 *
 * Elements in [spans] must be in the interval `[1, rows]` ([rows] > 0), and the composables are
 * associated with the corresponding span based on their index.
 *
 * Due to the fact that elements are seen as a linear list that's laid out in a grid, the semantics
 * represent the collection as a list of elements.
 */
@Composable
fun HorizontalSpannedGrid(
    rows: Int,
    columnSpacing: Dp,
    rowSpacing: Dp,
    spans: List<Int>,
    modifier: Modifier = Modifier,
    keys: (spanIndex: Int) -> Any = { it },
    composables:
        @Composable
        BoxScope.(
            spanIndex: Int, row: Int, isFirstInColumn: Boolean, isLastInColumn: Boolean,
        ) -> Unit,
) {
    SpannedGrid(
        primarySpaces = rows,
        crossAxisSpacing = rowSpacing,
        mainAxisSpacing = columnSpacing,
        spans = spans,
        isVertical = false,
        modifier = modifier,
        keys = keys,
        composables = composables,
    )
}

/**
 * Vertical (non lazy) grid that supports [spans] for its elements.
 *
 * The elements will be laid down horizontally first, and then by rows. So assuming LTR layout, it
 * will be (for a span list `[2, 1, 2, 1, 1, 1, 1, 1]` and 4 columns):
 * ```
 * 0  0  1
 * 2  2  3  4
 * 5  6  7
 * ```
 *
 * where repeated numbers show larger span. If an element doesn't fit in a row due to its span, it
 * will start a new row.
 *
 * Elements in [spans] must be in the interval `[1, columns]` ([columns] > 0), and the composables
 * are associated with the corresponding span based on their index.
 *
 * Due to the fact that elements are seen as a linear list that's laid out in a grid, the semantics
 * represent the collection as a list of elements.
 */
@Composable
fun VerticalSpannedGrid(
    columns: Int,
    columnSpacing: Dp,
    rowSpacing: Dp,
    spans: List<Int>,
    modifier: Modifier = Modifier,
    keys: (spanIndex: Int) -> Any = { it },
    composables:
        @Composable
        BoxScope.(spanIndex: Int, column: Int, isFirstInRow: Boolean, isLastInRow: Boolean) -> Unit,
) {
    SpannedGrid(
        primarySpaces = columns,
        crossAxisSpacing = columnSpacing,
        mainAxisSpacing = rowSpacing,
        spans = spans,
        isVertical = true,
        modifier = modifier,
        keys = keys,
        composables = composables,
    )
}

@Composable
private fun SpannedGrid(
    primarySpaces: Int,
    crossAxisSpacing: Dp,
    mainAxisSpacing: Dp,
    spans: List<Int>,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
    keys: (spanIndex: Int) -> Any = { it },
    composables:
        @Composable
        BoxScope.(spanIndex: Int, secondaryAxis: Int, isFirst: Boolean, isLast: Boolean) -> Unit,
) {
    val crossAxisArrangement = Arrangement.spacedBy(crossAxisSpacing)
    spans.forEachIndexed { index, span ->
        check(span in 1..primarySpaces) {
            "Span out of bounds. Span at index $index has value of $span which is outside of the " +
                "expected rance of [1, $primarySpaces]"
        }
    }
    if (isVertical) {
        check(crossAxisSpacing >= 0.dp) { "Negative columnSpacing $crossAxisSpacing" }
        check(mainAxisSpacing >= 0.dp) { "Negative rowSpacing $mainAxisSpacing" }
    } else {
        check(mainAxisSpacing >= 0.dp) { "Negative columnSpacing $mainAxisSpacing" }
        check(crossAxisSpacing >= 0.dp) { "Negative rowSpacing $crossAxisSpacing" }
    }
    // List of primary axis index to secondary axis index
    // This is keyed to the size of the spans list for performance reasons as we don't expect the
    // spans value to change outside of edit mode.
    val positions = remember(spans.size) { Array(spans.size) { IntIntPair(0, 0) } }
    val totalMainAxisGroups =
        remember(primarySpaces, spans) {
            var mainAxisGroup = 0
            var currentSlot = 0
            spans.fastForEachIndexed { index, span ->
                if (currentSlot + span > primarySpaces) {
                    currentSlot = 0
                    mainAxisGroup += 1
                }
                positions[index] = IntIntPair(mainAxisGroup, currentSlot)
                currentSlot += span
            }
            mainAxisGroup + 1
        }
    val slotPositionsAndSizesCache = remember {
        object {
            var sizes = IntArray(0)
            var positions = IntArray(0)
        }
    }
    Layout(
        {
            (0 until spans.size).map { spanIndex ->
                key(keys(spanIndex)) {
                    Box(
                        Modifier.semantics {
                            collectionItemInfo =
                                if (isVertical) {
                                    CollectionItemInfo(spanIndex, 1, 0, 1)
                                } else {
                                    CollectionItemInfo(0, 1, spanIndex, 1)
                                }
                        }
                    ) {
                        val position = positions[spanIndex]
                        composables(
                            spanIndex,
                            position.second,
                            position.second == 0,
                            positions.getOrNull(spanIndex + 1)?.first != position.first,
                        )
                    }
                }
            }
        },
        modifier.semantics { collectionInfo = CollectionInfo(spans.size, 1) },
    ) { measurables, constraints ->
        check(measurables.size == spans.size)
        val crossAxisSize = if (isVertical) constraints.maxWidth else constraints.maxHeight
        check(crossAxisSize != Constraints.Infinity) { "Width must be constrained" }
        if (slotPositionsAndSizesCache.sizes.size != primarySpaces) {
            slotPositionsAndSizesCache.sizes = IntArray(primarySpaces)
            slotPositionsAndSizesCache.positions = IntArray(primarySpaces)
        }
        calculateCellsCrossAxisSize(
            crossAxisSize,
            primarySpaces,
            crossAxisSpacing.roundToPx(),
            slotPositionsAndSizesCache.sizes,
        )
        val cellSizesInCrossAxis = slotPositionsAndSizesCache.sizes
        // with is needed because of the double receiver (Density, Arrangement).
        with(crossAxisArrangement) {
            arrange(
                crossAxisSize,
                slotPositionsAndSizesCache.sizes,
                LayoutDirection.Ltr,
                slotPositionsAndSizesCache.positions,
            )
        }
        val startPositions = slotPositionsAndSizesCache.positions
        val mainAxisSpacingPx = mainAxisSpacing.roundToPx()
        val mainAxisTotalGaps = (totalMainAxisGroups - 1) * mainAxisSpacingPx
        val mainAxisMaxSize = if (isVertical) constraints.maxHeight else constraints.maxWidth
        val mainAxisElementConstraint =
            if (mainAxisMaxSize == Constraints.Infinity) {
                Constraints.Infinity
            } else {
                max(0, (mainAxisMaxSize - mainAxisTotalGaps) / totalMainAxisGroups)
            }

        var mainAxisTotalSize = mainAxisTotalGaps
        var currentMainAxis = 0
        var currentMainAxisMax = 0
        val placeables =
            measurables.fastMapIndexed { index, measurable ->
                val span = spans[index]
                val position = positions[index]
                val crossAxisConstraint =
                    calculateWidth(cellSizesInCrossAxis, startPositions, position.second, span)

                measurable
                    .measure(
                        makeConstraint(isVertical, mainAxisElementConstraint, crossAxisConstraint)
                    )
                    .also {
                        val placeableSize = if (isVertical) it.height else it.width
                        if (position.first != currentMainAxis) {
                            // New row -- Add the max size to the total and reset the max
                            mainAxisTotalSize += currentMainAxisMax
                            currentMainAxisMax = placeableSize
                            currentMainAxis = position.first
                        } else {
                            currentMainAxisMax = max(currentMainAxisMax, placeableSize)
                        }
                    }
            }
        mainAxisTotalSize += currentMainAxisMax

        val height = if (isVertical) mainAxisTotalSize else crossAxisSize
        val width = if (isVertical) crossAxisSize else mainAxisTotalSize

        layout(width, height) {
            var previousMainAxis = 0
            var currentMainAxisPosition = 0
            var currentMainAxisMax = 0
            placeables.forEachIndexed { index, placeable ->
                val slot = positions[index].second
                val mainAxisSize = if (isVertical) placeable.height else placeable.width

                if (positions[index].first != previousMainAxis) {
                    // Move up a row + padding
                    currentMainAxisPosition += currentMainAxisMax + mainAxisSpacingPx
                    currentMainAxisMax = mainAxisSize
                    previousMainAxis = positions[index].first
                } else {
                    currentMainAxisMax = max(currentMainAxisMax, mainAxisSize)
                }

                val x =
                    if (isVertical) {
                        startPositions[slot]
                    } else {
                        currentMainAxisPosition
                    }
                val y =
                    if (isVertical) {
                        currentMainAxisPosition
                    } else {
                        startPositions[slot]
                    }
                placeable.placeRelative(x, y)
            }
        }
    }
}

fun makeConstraint(isVertical: Boolean, mainAxisSize: Int, crossAxisSize: Int): Constraints {
    return if (isVertical) {
        Constraints(maxHeight = mainAxisSize, minWidth = crossAxisSize, maxWidth = crossAxisSize)
    } else {
        Constraints(maxWidth = mainAxisSize, minHeight = crossAxisSize, maxHeight = crossAxisSize)
    }
}

private fun calculateWidth(sizes: IntArray, positions: IntArray, startSlot: Int, span: Int): Int {
    val crossAxisSize =
        if (span == 1) {
                sizes[startSlot]
            } else {
                val endSlot = startSlot + span - 1
                positions[endSlot] + sizes[endSlot] - positions[startSlot]
            }
            .coerceAtLeast(0)
    return crossAxisSize
}

private fun calculateCellsCrossAxisSize(
    gridSize: Int,
    slotCount: Int,
    spacingPx: Int,
    outArray: IntArray,
) {
    check(outArray.size == slotCount)
    val gridSizeWithoutSpacing = gridSize - spacingPx * (slotCount - 1)
    val slotSize = gridSizeWithoutSpacing / slotCount
    val remainingPixels = gridSizeWithoutSpacing % slotCount
    outArray.indices.forEach { index ->
        outArray[index] = slotSize + if (index < remainingPixels) 1 else 0
    }
}

data class GridSpan(val colSpan: Int, val rowSpan: Int)

data class PlacedGridItem(
    val index: Int,
    val row: Int,
    val col: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

fun calculate2DPlacements(
    spans: List<GridSpan>,
    columns: Int,
    maxRowsPerPage: Int = 0,
    maxRows: Int = 0,
    preferredPlacements: Map<Int, Pair<Int, Int>>? = null,
): List<PlacedGridItem> {
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

    val result = ArrayList<PlacedGridItem>(spans.size)
    var minRow = 0
    for (i in spans.indices) {
        val span = spans[i]
        val colSpan = span.colSpan.coerceIn(1, columns)
        val rowSpan = span.rowSpan.coerceAtLeast(1)

        if (maxRows > 0 && rowSpan > maxRows) {
            continue
        }

        var foundRow = -1
        var foundCol = -1

        val preferred = preferredPlacements?.get(i)
        if (preferred != null) {
            val pr = preferred.first
            val pc = preferred.second
            if (pc in 0..(columns - colSpan) && (maxRows == 0 || pr + rowSpan <= maxRows)) {
                if (maxRowsPerPage == 0 || (pr % maxRowsPerPage) + rowSpan <= maxRowsPerPage) {
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
        }

        if (foundRow == -1) {
            var r = minRow
            while (foundRow == -1) {
                if (maxRows > 0 && r + rowSpan > maxRows) {
                    break
                }
                if (maxRowsPerPage > 0 && (r % maxRowsPerPage) + rowSpan > maxRowsPerPage) {
                    r = (r / maxRowsPerPage + 1) * maxRowsPerPage
                    if (maxRows > 0 && r + rowSpan > maxRows) {
                        break
                    }
                }
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

        if (foundRow != -1) {
            for (dr in 0 until rowSpan) {
                for (dc in 0 until colSpan) {
                    setOccupied(foundRow + dr, foundCol + dc)
                }
            }
            minRow = foundRow
            result.add(PlacedGridItem(i, foundRow, foundCol, colSpan, rowSpan))
        }
    }
    return result
}


/**
 * 2D Spanned Grid supporting both variable column spans and multi-row spans.
 */
@Composable
fun TwoDimensionalSpannedGrid(
    columns: Int,
    columnSpacing: Dp,
    rowSpacing: Dp,
    spans: List<GridSpan>,
    customPlacements: List<PlacedGridItem>? = null,
    preferredPlacements: Map<Int, Pair<Int, Int>>? = null,
    maxRows: Int = 0,
    rowHeight: Dp = Dp.Unspecified,
    useSquareCells: Boolean = false,
    horizontalPadding: Dp = 0.dp,
    targetCellSize: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
    keys: (index: Int) -> Any = { it },
    content: @Composable BoxScope.(index: Int, placement: PlacedGridItem) -> Unit,
) {
    val placements = customPlacements ?: remember(spans, columns, preferredPlacements, maxRows) {
        calculate2DPlacements(spans, columns, preferredPlacements = preferredPlacements, maxRows = maxRows)
    }
    val totalRows = remember(placements) {
        placements.maxOfOrNull { it.row + it.rowSpan } ?: 0
    }

    Layout(
        content = {
            placements.forEachIndexed { i, placement ->
                key(keys(placement.index)) {
                    Box(
                        Modifier.semantics {
                            collectionItemInfo = CollectionItemInfo(
                                placement.row, placement.rowSpan,
                                placement.col, placement.colSpan,
                            )
                        }
                    ) {
                        content(placement.index, placement)
                    }
                }
            }
        },
        modifier = modifier.semantics { collectionInfo = CollectionInfo(totalRows, columns) },
    ) { measurables, constraints ->
        val totalWidth = constraints.maxWidth
        check(totalWidth != Constraints.Infinity) { "Width must be constrained" }

        val colSpacingPx = columnSpacing.roundToPx()
        val rowSpacingPx = rowSpacing.roundToPx()

        val targetCellSizePx = if (targetCellSize != Dp.Unspecified) targetCellSize.roundToPx() else 0
        val baseHPaddingPx = horizontalPadding.roundToPx()
        val hPaddingPx = if (targetCellSizePx > 0) {
            val contentW = columns * targetCellSizePx + (columns - 1) * colSpacingPx
            maxOf(baseHPaddingPx, (totalWidth - contentW) / 2)
        } else {
            baseHPaddingPx
        }

        val availableWidth = totalWidth - 2 * hPaddingPx - (columns - 1) * colSpacingPx
        val baseColWidth = availableWidth / columns
        val remainder = availableWidth % columns

        val rowHeightPx = if (targetCellSizePx > 0) {
            targetCellSizePx
        } else if (useSquareCells || rowHeight == Dp.Unspecified) {
            baseColWidth
        } else {
            rowHeight.roundToPx()
        }

        val colWidths = IntArray(columns) { c ->
            baseColWidth + if (c < remainder) 1 else 0
        }
        val colPositions = IntArray(columns)
        var curX = hPaddingPx
        for (c in 0 until columns) {
            colPositions[c] = curX
            curX += colWidths[c] + colSpacingPx
        }

        val placeables = measurables.mapIndexed { i, measurable ->
            val placement = placements[i]
            var itemWidth = 0
            for (c in placement.col until (placement.col + placement.colSpan)) {
                itemWidth += colWidths[c]
            }
            itemWidth += (placement.colSpan - 1) * colSpacingPx
            val itemHeight = placement.rowSpan * rowHeightPx + (placement.rowSpan - 1) * rowSpacingPx

            val (finalWidth, finalHeight) = if (useSquareCells && placement.colSpan == 1 && placement.rowSpan == 1) {
                val s = minOf(itemWidth, itemHeight)
                s to s
            } else {
                itemWidth to itemHeight
            }

            measurable.measure(
                Constraints.fixed(finalWidth, finalHeight)
            )
        }

        val totalHeight = if (totalRows == 0) 0 else {
            totalRows * rowHeightPx + (totalRows - 1) * rowSpacingPx
        }

        layout(totalWidth, totalHeight) {
            placeables.forEachIndexed { i, placeable ->
                val placement = placements[i]
                var slotWidth = 0
                for (c in placement.col until (placement.col + placement.colSpan)) {
                    slotWidth += colWidths[c]
                }
                slotWidth += (placement.colSpan - 1) * colSpacingPx
                val slotHeight = placement.rowSpan * rowHeightPx + (placement.rowSpan - 1) * rowSpacingPx

                val x = colPositions[placement.col] + (slotWidth - placeable.width) / 2
                val y = placement.row * (rowHeightPx + rowSpacingPx) + (slotHeight - placeable.height) / 2
                placeable.placeRelative(x, y)
            }
        }
    }
}
