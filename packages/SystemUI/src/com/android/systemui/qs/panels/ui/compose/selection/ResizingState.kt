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

package com.android.systemui.qs.panels.ui.compose.selection

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.systemui.qs.panels.ui.compose.selection.ResizingState.ResizeOperation.FinalResizeOperation
import com.android.systemui.qs.panels.ui.compose.selection.ResizingState.ResizeOperation.TemporaryResizeOperation
import com.android.systemui.qs.pipeline.shared.TileSpec

@Composable
fun rememberResizingState(
    tileSpec: TileSpec,
    startsAsIcon: Boolean,
    startsAsQuad: Boolean = false,
    allowQuad: Boolean = false,
): ResizingState {
    return remember(tileSpec) {
        ResizingState(tileSpec, startsAsIcon, startsAsQuad, allowQuad)
    }
}

enum class QSDragAnchor {
    Icon,
    Large,
    Quad,
}

/**
 * Holds the data required to calculate the anchors of a resizable tile.
 *
 * @property isIcon whether or not the tile is currently an icon or a large tile
 * @property width the current width in pixel of the tile
 */
data class QSDragAnchorsData(
    val isIcon: Boolean,
    val isQuad: Boolean = false,
    val width: Int,
)

class ResizingState(
    tileSpec: TileSpec,
    startsAsIcon: Boolean,
    startsAsQuad: Boolean = false,
    val allowQuad: Boolean = false,
) {
    val anchoredDraggableState =
        AnchoredDraggableState(
            when {
                allowQuad && startsAsQuad -> QSDragAnchor.Quad
                startsAsIcon -> QSDragAnchor.Icon
                else -> QSDragAnchor.Large
            }
        )

    val bounds by derivedStateOf {
        anchoredDraggableState.anchors.minPosition().takeIf { !it.isNaN() } to
            anchoredDraggableState.anchors.maxPosition().takeIf { !it.isNaN() }
    }

    /** Whether or not the resizing state is idle at one of the anchors */
    val isIdle by derivedStateOf { progress().let { it == 0f || it == 1f } }

    /** Whether or not the handle is currently being dragged. */
    var isDragActive by mutableStateOf(false)
        private set

    val temporaryResizeOperation by derivedStateOf {
        TemporaryResizeOperation(
            tileSpec,
            toIcon = anchoredDraggableState.currentValue == QSDragAnchor.Icon,
            toQuad = anchoredDraggableState.currentValue == QSDragAnchor.Quad,
        )
    }

    val finalResizeOperation by derivedStateOf {
        FinalResizeOperation(
            tileSpec,
            toIcon = anchoredDraggableState.settledValue == QSDragAnchor.Icon,
            toQuad = anchoredDraggableState.settledValue == QSDragAnchor.Quad,
        )
    }

    fun dragStarted() {
        isDragActive = true
    }

    fun dragEnded() {
        isDragActive = false
    }

    /** Calculates and updates the drag anchors based on the size and maximum span. */
    fun updateAnchors(data: QSDragAnchorsData, maxSpan: Int, padding: Int) {
        val totalPadding = (maxSpan - 1) * padding
        val min = if (data.isIcon) data.width else (data.width - totalPadding) / maxSpan
        val max = if (data.isIcon) (data.width * maxSpan) + totalPadding else data.width
        if (allowQuad) {
            val quadPos = max + (min * 0.75f)
            anchoredDraggableState.updateAnchors(
                DraggableAnchors {
                    QSDragAnchor.Icon at min.toFloat()
                    QSDragAnchor.Large at max.toFloat()
                    QSDragAnchor.Quad at quadPos
                }
            )
        } else {
            anchoredDraggableState.updateAnchors(
                DraggableAnchors {
                    QSDragAnchor.Icon at min.toFloat()
                    QSDragAnchor.Large at max.toFloat()
                }
            )
        }
    }

    suspend fun updateCurrentValue(anchor: QSDragAnchor) {
        anchoredDraggableState.animateTo(anchor)
    }

    suspend fun updateCurrentValue(isIcon: Boolean, isQuad: Boolean = false) {
        val anchor = when {
            allowQuad && isQuad -> QSDragAnchor.Quad
            isIcon -> QSDragAnchor.Icon
            else -> QSDragAnchor.Large
        }
        anchoredDraggableState.animateTo(anchor)
    }

    suspend fun toggleCurrentValue() {
        val current = anchoredDraggableState.settledValue
        val nextAnchor = if (allowQuad) {
            when (current) {
                QSDragAnchor.Icon -> QSDragAnchor.Large
                QSDragAnchor.Large -> QSDragAnchor.Quad
                QSDragAnchor.Quad -> QSDragAnchor.Icon
            }
        } else {
            val isIcon = current == QSDragAnchor.Icon
            if (isIcon) QSDragAnchor.Large else QSDragAnchor.Icon
        }
        anchoredDraggableState.animateTo(nextAnchor)
    }

    fun progress(): Float {
        // Check if anchors are defined before calculating progress since it defaults to 1f
        // otherwise.
        val iconPos = anchoredDraggableState.anchors.positionOf(QSDragAnchor.Icon)
        val largePos = anchoredDraggableState.anchors.positionOf(QSDragAnchor.Large)
        return if (iconPos.isNaN() || largePos.isNaN()) {
            if (anchoredDraggableState.currentValue == QSDragAnchor.Icon) 0f else 1f
        } else {
            if (anchoredDraggableState.currentValue == QSDragAnchor.Quad) {
                1f
            } else {
                anchoredDraggableState.progress(QSDragAnchor.Icon, QSDragAnchor.Large)
            }
        }
    }

    /**
     * Represents a resizing operation for a tile.
     *
     * @property spec The tile's [TileSpec]
     * @property toIcon The new size for the tile.
     * @property toQuad Whether the tile is resizing to 2x2 quad size.
     */
    sealed class ResizeOperation(
        val spec: TileSpec,
        val toIcon: Boolean,
        val toQuad: Boolean = false,
    ) {
        /** A temporary resizing operation, used while a resizing movement is in motion. */
        class TemporaryResizeOperation(spec: TileSpec, toIcon: Boolean, toQuad: Boolean = false) :
            ResizeOperation(spec, toIcon, toQuad)

        /** A final resizing operation, used while a resizing movement is done. */
        class FinalResizeOperation(spec: TileSpec, toIcon: Boolean, toQuad: Boolean = false) :
            ResizeOperation(spec, toIcon, toQuad)
    }
}
