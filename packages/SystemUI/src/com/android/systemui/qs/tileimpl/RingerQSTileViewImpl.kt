/* 
 * SPDX-FileCopyrightText: 2025 Adithya R <gh0strider.2k18.reborn@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tileimpl

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources.ID_NULL
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import com.android.settingslib.Utils
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.qs.QSTileView
import com.android.systemui.res.R
import kotlin.math.abs

class RingerQSTileViewImpl @JvmOverloads constructor(
    private val context: Context
) : QSTileView(context), HeightOverrideable {

    private val activeRingerIconLayout: FrameLayout
    private val activeRingerIconContainer: FrameLayout
    private val activeRingerIcon: QSIconViewImpl
    private var activeRingerMode: Int = RINGER_MODE_SILENT
    private lateinit var tileState: QSTile.State
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val stateChangeRunnable = Runnable { handleStateChanged() }
    private val ringerModeChangeRunnable = Runnable {
        updateRingerIconPosition(animate = true)
        audioManager?.setRingerModeInternal(activeRingerMode)
    }

    private val touchListener = object : View.OnTouchListener {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f
        var downY = 0f
        var isMoving = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isMoving = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.x - downX)
                    val deltaY = abs(event.y - downY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isMoving = true
                        return false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isMoving) {
                        return false
                    }
                    val spacing = measuredWidth / 3f
                    dlog("touch UP x=${event.x} spacing=$spacing")
                    val mode = when {
                        (event.x < spacing) -> RINGER_MODE_SILENT
                        (event.x < spacing * 2f) -> RINGER_MODE_VIBRATE
                        else -> RINGER_MODE_NORMAL
                    }
                    if (activeRingerMode != mode) {
                        activeRingerMode = mode
                        removeCallbacks(ringerModeChangeRunnable)
                        post(ringerModeChangeRunnable)
                    }
                    return true
                }
            }
            return false
        }
    }

    override var heightOverride: Int = HeightOverrideable.NO_OVERRIDE
        set(value) {
            if (field == value) return
            field = value
            updateHeight()
        }

    override var squishinessFraction: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            updateHeight()
        }

    init {
        id = generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        background = RingerModesBgDrawable()
        setOnTouchListener(touchListener)

        activeRingerIconLayout = LayoutInflater.from(context)
            .inflate(R.layout.qs_ringer_icon_layout, this, false) as FrameLayout
        addView(activeRingerIconLayout)
        // We need an inner container for our own translation animation since QSAnimator
        // performs its own translation animations on all tile views.
        activeRingerIconContainer =
            activeRingerIconLayout.requireViewById(R.id.qs_ringer_active_icon_container)
                as FrameLayout

        activeRingerIcon = QSIconViewImpl(context)
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.qs_icon_size)
        activeRingerIconContainer.addView(
            activeRingerIcon,
            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateResources()
    }

    // QSAnimator sets these both to true that breaks our icon translation.
    override fun setClipChildren(clipChildren: Boolean) {
        super.setClipChildren(false)
    }

    override fun setClipToPadding(clipToPadding: Boolean) {
        super.setClipToPadding(false)
    }

    override fun updateAccessibilityOrder(previousView: View?): View {
        accessibilityTraversalAfter = previousView?.id ?: ID_NULL
        return this
    }

    override fun getIcon() = activeRingerIcon

    override fun getIconWithBackground() = activeRingerIconLayout

    override fun init(tile: QSTile) {
        dlog("init: $tile")
        onStateChanged(tile.state)
    }

    override fun onStateChanged(state: QSTile.State) {
        tileState = state
        removeCallbacks(stateChangeRunnable)
        post(stateChangeRunnable)
    }

    override fun getDetailY() = top + height / 2

    override fun setPosition(position: Int) {
        // noop
    }

    override fun resetOverride() {
        heightOverride = HeightOverrideable.NO_OVERRIDE
    }

    private fun updateResources() {
        background = RingerModesBgDrawable()
    }

    private fun handleStateChanged() {
        dlog("handleStateChanged: $tileState")
        contentDescription = tileState.contentDescription
        icon.setIcon(tileState, /*allowAnimations=*/ false)
        activeRingerMode = audioManager?.getRingerModeInternal() ?: RINGER_MODE_SILENT
        updateRingerIconPosition(animate = false)
    }

    private fun updateRingerIconPosition(animate: Boolean) {
        val spacing = measuredWidth / 3f
        val extra = (measuredWidth - activeRingerIconContainer.measuredWidth * 3) / 4f
        val translationX = (activeRingerMode * spacing + extra).coerceAtLeast(0f)
        dlog("updateRingerIconPosition: animate=$animate mode=$activeRingerMode " +
                "spacing=$spacing extra=$extra translationX=$translationX")
        if (!animate) {
            activeRingerIconContainer.translationX = translationX
            return
        }
        activeRingerIconContainer.animate()
            .translationX(translationX)
            .setDuration(RINGER_ICON_TRANSLATION_DURATION)
            .start()
    }

    private fun updateHeight() {
        val actualHeight =
            if (heightOverride != HeightOverrideable.NO_OVERRIDE) {
                heightOverride
            } else {
                measuredHeight
            }
        // Limit how much we affect the height, so we don't have rounding artifacts when the tile
        // is too short.
        val constrainedSquishiness = constrainSquishiness(squishinessFraction)
        bottom = top + (actualHeight * constrainedSquishiness).toInt()
        scrollY = (actualHeight - height) / 2
    }

    // Tile background showing 3 evenly spaced dots
    inner class RingerModesBgDrawable : GradientDrawable() {

        private val dotRadius = context.resources.getDimensionPixelSize(
            R.dimen.qs_ringer_tile_dot_radius).toFloat()

        private val dotPaint = Paint().apply {
            color = Utils.getColorAttrDefaultColor(context, R.attr.onShadeInactive)
            style = Paint.Style.FILL
        }

        init {
            // Replicate @drawable/qs_tile_background_shape
            shape = RECTANGLE
            cornerRadius =
                context.resources.getDimensionPixelSize(R.dimen.qs_ringer_corner_radius).toFloat()
            setColor(Utils.getColorAttrDefaultColor(context, R.attr.shadeInactive))
        }

        override fun draw(canvas: Canvas) {
            super.draw(canvas)

            val spacing = (bounds.right - bounds.left) / 3f
            val y = (bounds.bottom - bounds.top) / 2f
            val startX = bounds.left

            for (i in 0..2) {
                canvas.drawCircle(
                    startX + spacing * (i + 0.5f),
                    y,
                    dotRadius,
                    dotPaint
                )
            }
        }
    }

    companion object {
        private const val TAG = "RingerQSTileViewImpl"
        private const val RINGER_ICON_TRANSLATION_DURATION = 200L // ms

        private inline fun dlog(msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, msg)
            }
        }
    }
}
