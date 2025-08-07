/*
 * Copyright (C) 2025 Neoteric OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.text

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CompatText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow? = null,
    onView: (TextView.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val resolved = remember(style, fontSize, letterSpacing) {
        resolveStyle(style, fontSize, letterSpacing, density)
    }
    val resolvedColor = rememberResolvedColor(color, style)
    val lineHeightPx = rememberLineHeightPx(resolved.lineHeight, density)
    val typeface = rememberTypeface(style.fontFamily, style.fontWeight, style.fontStyle)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                isSingleLine = maxLines == 1
                this.maxLines = maxLines
                this.text = text
                setTypeface(typeface)
                applyResolvedStyle(resolved, resolvedColor, textAlign, lineHeightPx)
                applyOverflow(overflow)
                onView?.invoke(this)
            }
        },
        update = { view ->
            view.text = text
            view.maxLines = maxLines
            view.setTypeface(typeface)
            view.applyResolvedStyle(resolved, resolvedColor, textAlign, lineHeightPx)
            view.applyOverflow(overflow)
            onView?.invoke(view)
        }
    )
}

@Composable
fun CompatMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = 1,
    iterations: Int = Int.MAX_VALUE,
    blurEdgeWidthPx: Float = 24f,
    onView: (TextView.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val resolved = remember(style) {
        resolveStyle(style, TextUnit.Unspecified, TextUnit.Unspecified, density)
    }
    val resolvedColor = rememberResolvedColor(null, style)
    val lineHeightPx = rememberLineHeightPx(resolved.lineHeight, density)
    val typeface = rememberTypeface(style.fontFamily, style.fontWeight, style.fontStyle)

    val measuredTextWidth = remember { mutableStateOf(0f) }
    val containerWidth = remember { mutableStateOf(0f) }

    val drawModifier = Modifier.drawWithContent {
        drawContent()
        if (measuredTextWidth.value > containerWidth.value) {
            drawEndBlur(blurEdgeWidthPx)
        }
    }

    AndroidView(
        modifier = modifier.then(drawModifier),
        factory = { context ->
            TextView(context).apply {
                isSingleLine = true
                this.maxLines = maxLines
                isSelected = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = iterations
                setHorizontallyScrolling(true)
                isFocusable = true
                isFocusableInTouchMode = true
                gravity = Gravity.START
                this.text = text
                setTypeface(typeface)
                applyResolvedStyle(resolved, resolvedColor, null, lineHeightPx)
                onView?.invoke(this)

                post {
                    measuredTextWidth.value = paint.measureText(text)
                    containerWidth.value = width.toFloat()
                }
            }
        },
        update = { view ->
            view.text = text
            view.setTypeface(typeface)
            view.applyResolvedStyle(resolved, resolvedColor, null, lineHeightPx)
            view.post {
                measuredTextWidth.value = view.paint.measureText(text)
                containerWidth.value = view.width.toFloat()
            }
            onView?.invoke(view)
        }
    )
}

private fun resolveStyle(
    style: TextStyle,
    overrideFontSize: TextUnit,
    overrideLetterSpacing: TextUnit,
    density: Density
): ResolvedTextStyle {
    val fontSize = when {
        overrideFontSize != TextUnit.Unspecified -> overrideFontSize
        style.fontSize != TextUnit.Unspecified -> style.fontSize
        else -> 14.sp
    }
    val fontSizePx = with(density) { fontSize.toPx() }

    val letterSpacing = (
        if (overrideLetterSpacing != TextUnit.Unspecified) overrideLetterSpacing
        else if (style.letterSpacing != TextUnit.Unspecified) style.letterSpacing
        else 0.em
    ).let { ls ->
        if (ls.type == TextUnitType.Em) ls.value else with(density) { ls.toPx() } / fontSizePx
    }

    return ResolvedTextStyle(
        fontSizePx = fontSizePx,
        letterSpacing = letterSpacing,
        lineHeight = style.lineHeight
    )
}

@Composable
private fun rememberTypeface(
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
    fontStyle: FontStyle?
): Typeface {
    return remember(fontFamily, fontWeight, fontStyle) {
        val weightInt = (fontWeight ?: FontWeight.Normal).weight
        val isItalic = (fontStyle ?: FontStyle.Normal) == FontStyle.Italic
        Typeface.create(Typeface.DEFAULT, weightInt, isItalic)
    }
}

@Composable
private fun rememberResolvedColor(
    color: Color?,
    style: TextStyle
): Color {
    val fallbackColor = LocalContentColor.current.copy(alpha = 1f)
    return remember(color, style.color, fallbackColor) {
        color?.takeIf { it != Color.Unspecified }
            ?: style.color.takeIf { it != Color.Unspecified }
            ?: fallbackColor
    }
}

@Composable
private fun rememberLineHeightPx(lineHeight: TextUnit, density: Density): Float? {
    return remember(lineHeight) {
        if (lineHeight != TextUnit.Unspecified) {
            with(density) { lineHeight.toPx() }
        } else null
    }
}

private fun TextView.applyResolvedStyle(
    resolved: ResolvedTextStyle,
    color: Color,
    align: TextAlign?,
    lineHeightPx: Float?,
) {
    textSize = resolved.fontSizePx / resources.displayMetrics.density
    letterSpacing = resolved.letterSpacing
    setTextColor(color.toArgb())

    gravity = when (align) {
        TextAlign.Center -> Gravity.CENTER_HORIZONTAL
        TextAlign.Right, TextAlign.End -> Gravity.END
        TextAlign.Left, TextAlign.Start -> Gravity.START
        else -> gravity
    }

    lineHeightPx?.let {
        val fontMetrics = paint.fontMetrics
        val actualLineHeight = fontMetrics.descent - fontMetrics.ascent
        val extraSpacing = (it - actualLineHeight).coerceAtLeast(0f)
        setLineSpacing(extraSpacing, 1f)
    }
}

private fun TextView.applyOverflow(overflow: TextOverflow?) {
    ellipsize = when (overflow) {
        TextOverflow.Ellipsis -> TextUtils.TruncateAt.END
        TextOverflow.Clip -> null
        else -> null
    }
}

private fun DrawScope.drawEndBlur(edgeWidthPx: Float) {
    drawRect(
        topLeft = Offset(size.width - edgeWidthPx, 0f),
        size = Size(edgeWidthPx, size.height),
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = size.width,
            endX = size.width - edgeWidthPx,
        ),
        blendMode = BlendMode.DstIn,
    )
}

private data class ResolvedTextStyle(
    val fontSizePx: Float,
    val letterSpacing: Float,
    val lineHeight: TextUnit,
)
