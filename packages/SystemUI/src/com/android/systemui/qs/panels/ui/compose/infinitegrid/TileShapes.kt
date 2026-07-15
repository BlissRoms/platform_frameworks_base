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

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val CircleShape = RoundedCornerShape(50)
private val SquircleShape = RoundedCornerShape(35)
private val RoundedSquareShape = RoundedCornerShape(25)

private val DiamondShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    moveTo(cx, 0f)
    lineTo(size.width, cy)
    lineTo(cx, size.height)
    lineTo(0f, cy)
    close()
}

private val HexagonShape = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val start = -Math.PI / 2.0
    moveTo(cx + r * cos(start).toFloat(), cy + r * sin(start).toFloat())
    for (i in 1..5) {
        val a = start + i * Math.PI / 3.0
        lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    close()
}

private val CloverShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(size.width, size.height) * 0.28f
    val offsets = arrayOf(
        cx to cy - r,
        cx + r to cy,
        cx to cy + r,
        cx - r to cy,
    )
    for ((ox, oy) in offsets) {
        addOval(Rect(ox - r, oy - r, ox + r, oy + r))
    }
}

private val StarShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outerR = min(size.width, size.height) / 2f
    val innerR = outerR * 0.45f
    val start = -Math.PI / 2.0
    val points = 5
    moveTo(cx + outerR * cos(start).toFloat(), cy + outerR * sin(start).toFloat())
    for (i in 1 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val a = start + i * Math.PI / points
        lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    close()
}

private val PentagonShape = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val start = -Math.PI / 2.0
    moveTo(cx + r * cos(start).toFloat(), cy + r * sin(start).toFloat())
    for (i in 1..4) {
        val a = start + i * 2.0 * Math.PI / 5.0
        lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    close()
}

private val OctagonShape = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val start = -Math.PI / 8.0
    moveTo(cx + r * cos(start).toFloat(), cy + r * sin(start).toFloat())
    for (i in 1..7) {
        val a = start + i * Math.PI / 4.0
        lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    close()
}

private val FlowerShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(size.width, size.height) * 0.25f
    val petalDist = r * 0.9f
    for (i in 0 until 6) {
        val a = i * Math.PI / 3.0 - Math.PI / 2.0
        val ox = cx + petalDist * cos(a).toFloat()
        val oy = cy + petalDist * sin(a).toFloat()
        addOval(Rect(ox - r, oy - r, ox + r, oy + r))
    }
}

private val BlissShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val outerR = min(w, h) / 2f
    val innerR = outerR * 0.55f
    val points = 8
    val start = -Math.PI / 2.0
    moveTo(cx + outerR * cos(start).toFloat(), cy + outerR * sin(start).toFloat())
    for (i in 1 until points * 2) {
        val r = if (i % 2 == 0) outerR else innerR
        val a = start + i * Math.PI / points
        lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
    }
    close()
}

val CyberPunkTileShape = GenericShape { size, _ ->
    val cut = size.height * 0.2f
    moveTo(cut, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(0f, size.height)
    lineTo(0f, cut)
    close()
}

fun classicTileShape(style: Int): Shape = when (style) {
    1 -> SquircleShape
    2 -> RoundedSquareShape
    3 -> DiamondShape
    4 -> HexagonShape
    5 -> CloverShape
    6 -> StarShape
    7 -> PentagonShape
    8 -> OctagonShape
    9 -> FlowerShape
    10 -> BlissShape
    else -> CircleShape
}