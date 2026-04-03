/*
 * Copyright (C) 2014-2026 The BlissRoms Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.bliss.charging

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.android.settingslib.Utils
import kotlin.math.min
import kotlin.math.sin

class BlissChargingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var style = STYLE_FLASH
    private var batteryLevel = 0
    private var animationProgress = 0f
    private var onAnimationEnd: Runnable? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentColor = Utils.getColorAttr(context, android.R.attr.colorAccent).defaultColor
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val batteryRect = RectF()
    private val fillPath = Path()
    private val cornerRadius
        get() = min(width, height) * 0.04f

    fun configure(style: Int, batteryLevel: Int, onEnd: Runnable) {
        this.style = style
        this.batteryLevel = batteryLevel
        this.onAnimationEnd = onEnd
    }

    fun startAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (style) {
                STYLE_FLASH -> 500L
                STYLE_GLOW_PULSE -> 1500L
                STYLE_BATTERY_FILL -> 1400L
                else -> 500L
            }
            interpolator = if (style == STYLE_GLOW_PULSE) LinearInterpolator()
                           else DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedFraction
                invalidate()
            }
        }

        val fadeOut = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f).apply {
            duration = 300L
            startDelay = animator.duration
        }

        AnimatorSet().apply {
            playSequentially(animator, fadeOut)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onAnimationEnd?.run()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        when (style) {
            STYLE_FLASH -> drawFlash(canvas)
            STYLE_GLOW_PULSE -> drawGlowPulse(canvas)
            STYLE_BATTERY_FILL -> drawBatteryFill(canvas)
        }
    }

    private fun drawFlash(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = kotlin.math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()
        val flashAlpha = ((1f - animationProgress) * 0.5f).coerceIn(0f, 1f)

        val gradient = RadialGradient(
            cx, cy, maxRadius * (0.4f + animationProgress * 0.6f),
            Color.argb((flashAlpha * 255).toInt(), 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawGlowPulse(canvas: Canvas) {
        val pulseCount = 3
        val pulsePhase = (animationProgress * pulseCount) % 1f
        val pulseAlpha = (sin(pulsePhase * Math.PI).toFloat() * 0.45f * 255).toInt()
            .coerceIn(0, 255)

        for (i in 0 until 3) {
            val layerWidth = cornerRadius * 0.6f * (3 - i)
            val layerAlpha = pulseAlpha * (3 - i) / 4

            paint.color = accentColor
            paint.alpha = layerAlpha.coerceIn(0, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = layerWidth

            val inset = layerWidth / 2f
            canvas.drawRoundRect(
                inset, inset, width - inset, height - inset,
                cornerRadius, cornerRadius, paint
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawBatteryFill(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val batteryW = width * 0.3f
        val batteryH = height * 0.25f
        val capW = batteryW * 0.3f
        val capH = batteryH * 0.06f
        val bCorner = batteryW * 0.08f
        val strokeW = batteryW * 0.04f

        paint.color = Color.WHITE
        paint.alpha = 200
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW

        batteryRect.set(cx - batteryW / 2, cy - batteryH / 2, cx + batteryW / 2, cy + batteryH / 2)
        canvas.drawRoundRect(batteryRect, bCorner, bCorner, paint)

        val capRect = RectF(cx - capW / 2, cy - batteryH / 2 - capH, cx + capW / 2, cy - batteryH / 2)
        canvas.drawRoundRect(capRect, bCorner / 2, bCorner / 2, paint)

        val fillProgress = animationProgress.coerceAtMost(0.65f) / 0.65f
        val fillLevel = fillProgress * batteryLevel / 100f
        val inset = strokeW * 1.5f
        val innerLeft = batteryRect.left + inset
        val innerRight = batteryRect.right - inset
        val innerBottom = batteryRect.bottom - inset
        val innerTop = batteryRect.top + inset
        val innerH = innerBottom - innerTop
        val fillTop = innerBottom - innerH * fillLevel

        paint.style = Paint.Style.FILL
        paint.color = accentColor
        paint.alpha = 220

        fillPath.reset()
        val waveAmp = batteryW * 0.04f
        val waveFreq = 2.5f
        val wavePhase = animationProgress * 12f

        fillPath.moveTo(innerLeft, innerBottom)
        fillPath.lineTo(innerLeft, fillTop)

        var x = innerLeft
        val step = 2f
        while (x <= innerRight) {
            val waveY = fillTop + sin((x / batteryW * waveFreq * Math.PI + wavePhase).toDouble()).toFloat() * waveAmp
            fillPath.lineTo(x, waveY)
            x += step
        }

        fillPath.lineTo(innerRight, fillTop)
        fillPath.lineTo(innerRight, innerBottom)
        fillPath.close()

        canvas.save()
        canvas.clipRect(innerLeft, innerTop, innerRight, innerBottom)
        canvas.drawPath(fillPath, paint)
        canvas.restore()

        if (animationProgress > 0.3f) {
            val textAlpha = ((animationProgress - 0.3f) / 0.35f).coerceIn(0f, 1f)
            textPaint.alpha = (textAlpha * 255).toInt()
            textPaint.textSize = batteryH * 0.3f
            canvas.drawText("${batteryLevel}%", cx, cy + textPaint.textSize / 3, textPaint)
        }
    }

    companion object {
        const val STYLE_FLASH = 2
        const val STYLE_GLOW_PULSE = 3
        const val STYLE_BATTERY_FILL = 4
    }
}
