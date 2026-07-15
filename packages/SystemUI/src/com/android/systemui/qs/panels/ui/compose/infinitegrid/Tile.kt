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

@file:OptIn(ExperimentalFoundationApi::class)

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import android.content.Context
import android.content.res.Resources
import android.os.Trace
import android.provider.Settings
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import com.android.app.tracing.coroutines.launchTraced as launch
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.Expandable
import com.android.compose.animation.bounceable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.mechanics.compose.modifier.verticalFadeContentReveal
import com.android.mechanics.compose.modifier.verticalTactileSurfaceReveal
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.BounceableInfo
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CyberPunkTileShape
import com.android.systemui.qs.panels.ui.compose.infinitegrid.classicTileShape
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveTileCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.StartPadding
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.TileHeight
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelMoreDetails
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.BounceableTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

val LocalQSPanelStyle = compositionLocalOf { 0 }
val LocalQSTileLabelHide = compositionLocalOf { false }
val LocalQSTileColumns = compositionLocalOf { 4 }
val LocalQSTileQqsRows = compositionLocalOf { 2 }
val LocalQSTileQsRows = compositionLocalOf { 4 }
val LocalQSTileShape = compositionLocalOf { 0 }
val LocalQSTileOpacity = compositionLocalOf { 100 }
val LocalQSTileAnimationStyle = compositionLocalOf { 0 }

@Composable
private fun rememberSecureIntSetting(key: String, defaultValue: Int = 0): Int {
    val context = LocalContext.current
    val value by produceState(
        initialValue = Settings.Secure.getIntForUser(
            context.contentResolver, key, defaultValue,
            android.os.UserHandle.USER_CURRENT,
        )
    ) {
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                value = Settings.Secure.getIntForUser(
                    context.contentResolver, key, defaultValue,
                    android.os.UserHandle.USER_CURRENT,
                )
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false, observer, android.os.UserHandle.USER_ALL,
        )
        kotlinx.coroutines.awaitCancellation()
    }
    return value
}

@Composable
fun rememberQSPanelStyle(): Int = rememberSecureIntSetting("qs_panel_style")

@Composable
fun rememberQSTileLabelHide(): Boolean = rememberSecureIntSetting("qs_tile_label_hide") == 1

@Composable
fun rememberQSTileColumns(): Int {
    val max = LocalContext.current.resources.getInteger(
        com.android.internal.R.integer.config_qsTileColumnsMax)
    return rememberSecureIntSetting("qs_tile_columns", 4).coerceIn(3, max)
}

@Composable
fun rememberQSTileColumnsClassic(): Int {
    val max = LocalContext.current.resources.getInteger(
        R.integer.quick_settings_num_columns_classic)
    return rememberSecureIntSetting("qs_tile_columns", 4).coerceIn(3, max)
}

@Composable
fun rememberQSTileQqsRowsClassic(): Int {
    val max = LocalContext.current.resources.getInteger(
        R.integer.quick_qs_panel_max_tiles_classic)
    return rememberSecureIntSetting("qs_tile_qqs_rows", 2).coerceIn(1, max)
}

@Composable
fun rememberQSTileQqsRows(): Int {
    val max = LocalContext.current.resources.getInteger(
        com.android.internal.R.integer.config_qsTileQqsRowsMax)
    return rememberSecureIntSetting("qs_tile_qqs_rows", 2).coerceIn(1, max)
}

@Composable
fun rememberQSTileQsRows(): Int {
    val max = LocalContext.current.resources.getInteger(
        com.android.internal.R.integer.config_qsTileQsRowsMax)
    return rememberSecureIntSetting("qs_tile_qs_rows", 4).coerceIn(2, max)
}

@Composable
fun rememberQSTileShape(): Int = rememberSecureIntSetting("qs_tile_shape")

@Composable
fun rememberQSTileOpacity(): Int = rememberSecureIntSetting("qs_tile_opacity", 100).coerceIn(0, 100)

@Composable
fun rememberQSTileAnimationStyle(): Int = rememberSecureIntSetting("qs_tile_animation_style")

@Composable
fun TileLazyGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = columns,
        verticalArrangement = spacedBy(CommonTileDefaults.TileArrangementPadding),
        horizontalArrangement = spacedBy(CommonTileDefaults.TileArrangementPadding),
        contentPadding = contentPadding,
        modifier = modifier,
        content = content,
    )
}

private val TileViewModel.traceName
    get() = spec.toString().takeLast(Trace.MAX_SECTION_NAME_LEN)

/**
 * This composable function is responsible for rendering a tile based on the provided
 * [TileViewModel]. It handles different states of the tile (e.g., available, unavailable),
 * interactions (click, long click), and visual styles (icon only or large tile).
 *
 * @param tile The [TileViewModel] containing the data and logic for the tile.
 * @param iconOnly A boolean indicating whether to display only the icon of the tile or the full
 *   tile content (false for large tiles).
 * @param squishiness The float value representing the current squishiness factor of the tile, used
 *   for animations.
 * @param coroutineScope The [CoroutineScope] to launch coroutines for animations.
 * @param tileHapticsViewModelFactoryProvider A provider for creating a [TileHapticsViewModel]
 *   instance, used for haptic feedback.
 * @param interactionSource An optional [MutableInteractionSource] to track user interactions with
 *   the tile, used by the parent composable to animate a bounce effect. Tiles may or may not use
 *   this interaction source to control whether they should bounce or not.
 * @param modifier An optional [Modifier] to be applied to the root composable of the tile.
 * @param isVisible Whether the tile is currently visible. Defaults to true.
 * @param requestToggleTextFeedback A lambda function that is invoked when a toggleable icon only
 *   tile is clicked, used to request the feedback text.
 * @param detailsViewModel An optional [DetailsViewModel] used to handle navigation to a detailed
 *   view when a tile is clicked, if applicable.
 * @param enableRevealEffect If `true`, the tiles will animate using the reveal animation.
 */
@Composable
fun ContentScope.Tile(
    tile: TileViewModel,
    iconOnly: Boolean,
    squishiness: () -> Float,
    coroutineScope: CoroutineScope,
    bounceableInfo: BounceableInfo?,
    tileHapticsViewModelFactory: TileHapticsViewModel.Factory,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    requestToggleTextFeedback: (TileSpec) -> Unit = {},
    detailsViewModel: DetailsViewModel?,
    enableRevealEffect: Boolean = false,
) {
    trace(tile.traceName) {
        val currentBounceableInfo by rememberUpdatedState(bounceableInfo)
        val resources = resources()

        /*
         * Use produce state because [QSTile.State] doesn't have well defined equals (due to
         * inheritance). This way, even if tile.state changes, uiState may not change and lead to
         * recomposition.
         */
        val uiState by
            produceState(tile.currentState.toUiState(resources), tile, resources) {
                tile.state.collect { value = it.toUiState(resources) }
            }
        val isClickable = uiState.visualState != STATE_UNAVAILABLE

        val icon by
            produceState(tile.currentState.toIconProvider(), tile) {
                tile.state.collect { value = it.toIconProvider() }
            }

        val panelStyle = LocalQSPanelStyle.current
        val tileAnimationStyle = LocalQSTileAnimationStyle.current
        val density = LocalDensity.current

        val baseColors = TileDefaults.getColorForState(uiState, iconOnly)
        val colors = when (panelStyle) {
            2, 3 -> if (uiState.visualState == STATE_ACTIVE) {
                baseColors.copy(
                    icon = MaterialTheme.colorScheme.onSurface,
                    label = MaterialTheme.colorScheme.onSurface,
                    secondaryLabel = MaterialTheme.colorScheme.onSurface,
                )
            } else baseColors
            else -> baseColors
        }
        val hapticsViewModel: TileHapticsViewModel? =
            rememberViewModel(traceName = "TileHapticsViewModel") {
                tileHapticsViewModelFactory.create(tile)
            }

        // TODO(b/361789146): Draw the shapes instead of clipping
        val animatedTileShape by TileDefaults.animateTileShapeAsState(uiState.visualState)
        val classicTileShape = classicTileShape(LocalQSTileShape.current)
        val animatedColor by animateColorAsState(colors.background, label = "QSTileBackgroundColor")
        val isDualTarget = uiState.handlesToggleClick

        val surfaceRevealModifier: Modifier
        val contentRevealModifier: Modifier
        if (enableRevealEffect) {
            val marginBottom =
                with(LocalDensity.current) { QuickSettingsShade.Dimensions.VerticalPadding.toPx() }
            surfaceRevealModifier =
                Modifier.verticalTactileSurfaceReveal(deltaY = marginBottom, label = tile.traceName)
            contentRevealModifier =
                Modifier.verticalFadeContentReveal(deltaY = marginBottom, label = tile.traceName)
        } else {
            surfaceRevealModifier = Modifier
            contentRevealModifier = Modifier
        }

        val effectiveColor: Color
        val effectiveShape: Shape
        val effectiveCornerSize: androidx.compose.foundation.shape.CornerSize
        val styleModifier: Modifier
        val opacity = LocalQSTileOpacity.current / 100f

        when (panelStyle) {
            1 -> {
                // Classic panel style - ClassicCircleTileContent handles its own background/shape
                effectiveColor = Color.Transparent
                effectiveShape = RoundedCornerShape(0.dp)
                effectiveCornerSize = androidx.compose.foundation.shape.CornerSize(0.dp)
                styleModifier = Modifier
            }
            2 -> {
                // Outline style - transparent background with border
                effectiveColor = Color.Transparent
                effectiveShape = animatedTileShape
                effectiveCornerSize = animatedTileShape.topEnd
                styleModifier = Modifier.border(2.dp, animatedColor.copy(alpha = animatedColor.alpha * opacity), animatedTileShape)
            }
            3 -> {
                // Neumorph style - transparent background with shadow effect
                effectiveColor = Color.Transparent
                effectiveShape = animatedTileShape
                effectiveCornerSize = animatedTileShape.topEnd
                val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
                val isActive = uiState.visualState == STATE_ACTIVE
                val shadowOffset = with(density) { 3.dp.toPx() }
                val cornerRadius = with(density) { 24.dp.toPx() }
                styleModifier = Modifier.drawBehind {
                    val cr = CornerRadius(cornerRadius, cornerRadius)
                    drawRoundRect(color = surfaceColor, cornerRadius = cr)
                    for (i in 1..3) {
                        val offset = shadowOffset * i / 3f
                        val lightOff = if (isActive) offset else -offset
                        val darkOff = if (isActive) -offset else offset
                        translate(left = lightOff, top = lightOff) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.25f / i),
                                cornerRadius = cr,
                            )
                        }
                        translate(left = darkOff, top = darkOff) {
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.08f / i),
                                cornerRadius = cr,
                            )
                        }
                    }
                    drawRoundRect(color = surfaceColor, cornerRadius = cr)
                }
            }
            4 -> {
                // Cyberpunk style - filled background with cyberpunk shape and accent border
                effectiveColor = animatedColor.copy(alpha = animatedColor.alpha * opacity)
                effectiveShape = CyberPunkTileShape
                // CyberPunkTileShape is a GenericShape, use a fixed corner size for focus ring
                effectiveCornerSize = androidx.compose.foundation.shape.CornerSize(24.dp)
                val accentColor = MaterialTheme.colorScheme.tertiary
                styleModifier = Modifier.border(1.5.dp, accentColor, CyberPunkTileShape)
            }
            else -> {
                // Default style
                effectiveColor = animatedColor.copy(alpha = animatedColor.alpha * opacity)
                effectiveShape = animatedTileShape
                effectiveCornerSize = animatedTileShape.topEnd
                styleModifier = Modifier
            }
        }

        TileExpandable(
            color = { effectiveColor },
            shape = effectiveShape,
            animationShape = if (effectiveShape is RoundedCornerShape) effectiveShape else animatedTileShape,
            squishiness = squishiness,
            hapticsViewModel = hapticsViewModel,
            modifier =
                modifier
                    .then(surfaceRevealModifier)
                    .borderOnFocus(color = MaterialTheme.colorScheme.secondary, effectiveCornerSize)
                    .fillMaxWidth()
                    .then(styleModifier)
                    .tileToggleAnimation(uiState.visualState, tileAnimationStyle)
                    .thenIf(currentBounceableInfo != null) {
                        Modifier.bounceable(
                            currentBounceableInfo!!.bounceable,
                            currentBounceableInfo!!.previousTile,
                            currentBounceableInfo!!.nextTile,
                            orientation = Orientation.Horizontal,
                            bounceEnd = currentBounceableInfo!!.bounceEnd,
                        )
                    },
        ) { expandable ->
            // Use main click on long press for small, available dual target tiles.
            // Open settings otherwise.
            val useLongClickToSettings = !(iconOnly && isDualTarget && isClickable)
            val longClick: (() -> Unit)? =
                {
                        hapticsViewModel?.setTileInteractionState(
                            TileHapticsViewModel.TileInteractionState.LONG_CLICKED
                        )

                        if (useLongClickToSettings) {
                            tile.settingsClick(expandable)
                        } else {
                            tile.mainClick(expandable)
                        }
                    }
                    .takeIf { !useLongClickToSettings || uiState.handlesSettingsClick }

            // Bounce the tile's container if it is toggleable and is not a large
            // dual target tile. These don't toggle on main click.
            val bounceContainer = uiState.isToggleable && (iconOnly || !isDualTarget)
            val contentBounceable =
                remember(currentBounceableInfo) {
                    currentBounceableInfo?.bounceable ?: BounceableTileViewModel()
                }
            TileContainer(
                interactionSource = interactionSource.takeIf { bounceContainer },
                onClick = onClick@{
                        if (!isClickable) return@onClick

                        val hasDetails =
                            QsDetailedView.isEnabled &&
                                detailsViewModel?.onTileClicked(tile.spec) == true
                        if (hasDetails) return@onClick

                        // For those tile's who doesn't have a detailed view, process with
                        // their `onClick` behavior.
                        if (iconOnly && isDualTarget) {
                            tile.toggleClick()
                        } else {
                            tile.mainClick(expandable)
                        }

                        // Side effects of the click
                        hapticsViewModel?.setTileInteractionState(
                            TileHapticsViewModel.TileInteractionState.CLICKED
                        )

                        coroutineScope.launch {
                            // Bounce the tile's container if it is toggleable and is not a large
                            // dual target tile. These don't toggle on main click. Otherwise bounce
                            // the content of the tile.
                            if (bounceContainer) {
                                // Only bounce the container ourselves if a BounceableInfo was given
                                currentBounceableInfo?.bounceable?.animateContentBounce(true)
                            } else {
                                contentBounceable.animateContentBounce(iconOnly)
                            }
                        }
                        if (uiState.isToggleable && iconOnly) {
                            // And show footer text feedback for icons
                            requestToggleTextFeedback(tile.spec)
                        }
                    },
                onLongClick = longClick,
                accessibilityUiState = uiState.accessibilityUiState,
                iconOnly = iconOnly,
                isDualTarget = isDualTarget,
                modifier = contentRevealModifier,
            ) {
                val iconProvider: Context.() -> Icon = { getTileIcon(icon = icon) }
                val isClassicStyle = LocalQSPanelStyle.current == 1
                if (isClassicStyle) {
                    ClassicCircleTileContent(
                        label = uiState.label,
                        secondaryLabel = uiState.secondaryLabel,
                        iconProvider = iconProvider,
                        colors = colors,
                        hideLabel = LocalQSTileLabelHide.current,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (iconOnly) {
                    SmallTileContent(
                        iconProvider = iconProvider,
                        color = colors.icon,
                        modifier =
                            Modifier.align(Alignment.Center).bounceScale {
                                contentBounceable.iconBounceScale
                            },
                    )
                } else {
                    val iconShape by TileDefaults.animateIconShapeAsState(uiState.visualState)
                    val secondaryClick: (() -> Unit)? =
                        {
                                hapticsViewModel?.setTileInteractionState(
                                    TileHapticsViewModel.TileInteractionState.CLICKED
                                )
                                tile.toggleClick()
                            }
                            .takeIf { isDualTarget }
                    LargeTileContent(
                        label = uiState.label,
                        secondaryLabel = uiState.secondaryLabel,
                        iconProvider = iconProvider,
                        sideDrawable = uiState.sideDrawable,
                        colors = colors,
                        iconShape = iconShape,
                        toggleClick = secondaryClick,
                        onLongClick = longClick,
                        accessibilityUiState = uiState.accessibilityUiState,
                        squishiness = squishiness,
                        isVisible = isVisible,
                        textScale = { contentBounceable.textBounceScale },
                        modifier =
                            Modifier.largeTilePadding(isDualTarget = uiState.handlesSettingsClick),
                    )
                }
            }
        }
    }
}

@Composable
private fun TileExpandable(
    color: () -> Color,
    shape: Shape,
    squishiness: () -> Float,
    hapticsViewModel: TileHapticsViewModel?,
    modifier: Modifier = Modifier,
    animationShape: Shape = shape,
    content: @Composable (Expandable) -> Unit,
) {
    Expandable(
        controller = rememberExpandableController(color = color, shape = animationShape),
        modifier = modifier.clip(shape).verticalSquish(squishiness),
        useModifierBasedImplementation = true,
    ) {
        content(hapticsViewModel?.createStateAwareExpandable(it) ?: it)
    }
}

@Composable
fun TileContainer(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isClassic = LocalQSPanelStyle.current == 1
    val tileHeight = if (isClassic && LocalQSTileLabelHide.current)
        CommonTileDefaults.ClassicCircleSize + 8.dp
    else if (isClassic)
        CommonTileDefaults.ClassicTileHeight
    else
        TileHeight
    Box(
        modifier =
            modifier
                .height(tileHeight)
                .fillMaxWidth()
                .tileCombinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                    accessibilityUiState = accessibilityUiState,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                    interactionSource = interactionSource,
                )
                .tileTestTag(iconOnly),
        content = content,
    )
}

@Composable
fun LargeStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
) {
    val baseColors = TileDefaults.getColorForState(uiState = uiState, iconOnly = false)
    val panelStyle = LocalQSPanelStyle.current
    val colors = when (panelStyle) {
        2, 3 -> if (uiState.visualState == STATE_ACTIVE) {
            baseColors.copy(
                icon = MaterialTheme.colorScheme.onSurface,
                label = MaterialTheme.colorScheme.onSurface,
                secondaryLabel = MaterialTheme.colorScheme.onSurface,
            )
        } else baseColors
        else -> baseColors
    }
    val animatedTileShape = TileDefaults.animateTileShapeAsState(state = uiState.visualState).value
    val classicTileShape = classicTileShape(LocalQSTileShape.current)
    val density = LocalDensity.current

    val bgColor: Color
    val clipShape: Shape
    val extraModifier: Modifier

    when (panelStyle) {
        1 -> {
            // Classic panel style - filled background with classic tile shape
            bgColor = colors.background
            clipShape = classicTileShape
            extraModifier = Modifier
        }
        2 -> {
            bgColor = Color.Transparent
            clipShape = animatedTileShape
            extraModifier = Modifier.border(2.dp, colors.background, animatedTileShape)
        }
        3 -> {
            bgColor = Color.Transparent
            clipShape = animatedTileShape
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val shadowOffset = with(density) { 3.dp.toPx() }
            val cornerRadius = with(density) { 24.dp.toPx() }
            extraModifier = Modifier.drawBehind {
                val cr = CornerRadius(cornerRadius, cornerRadius)
                drawRoundRect(color = surfaceColor, cornerRadius = cr)
                for (i in 1..3) {
                    val offset = shadowOffset * i / 3f
                    translate(left = -offset, top = -offset) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.25f / i),
                            cornerRadius = cr,
                        )
                    }
                    translate(left = offset, top = offset) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.08f / i),
                            cornerRadius = cr,
                        )
                    }
                }
                drawRoundRect(color = surfaceColor, cornerRadius = cr)
            }
        }
        4 -> {
            bgColor = colors.background
            clipShape = CyberPunkTileShape
            val accentColor = MaterialTheme.colorScheme.tertiary
            extraModifier = Modifier.border(1.5.dp, accentColor, CyberPunkTileShape)
        }
        else -> {
            bgColor = colors.background
            clipShape = animatedTileShape
            extraModifier = Modifier
        }
    }

    Box(
        modifier
            .then(extraModifier)
            .clip(clipShape)
            .background(bgColor)
            .height(TileHeight)
            .largeTilePadding()
    ) {
        LargeTileContent(
            label = uiState.label,
            secondaryLabel = "",
            iconProvider = { getTileIcon(icon = iconProvider) },
            sideDrawable = null,
            colors = colors,
            squishiness = { 1f },
        )
    }
}

@Composable
fun SmallStaticTile(
    uiState: TileUiState,
    iconProvider: IconProvider,
    modifier: Modifier = Modifier,
) {
    val baseColors = TileDefaults.getColorForState(uiState = uiState, iconOnly = true)
    val panelStyle = LocalQSPanelStyle.current
    val colors = when (panelStyle) {
        2, 3 -> if (uiState.visualState == STATE_ACTIVE) {
            baseColors.copy(
                icon = MaterialTheme.colorScheme.onSurface,
                label = MaterialTheme.colorScheme.onSurface,
                secondaryLabel = MaterialTheme.colorScheme.onSurface,
            )
        } else baseColors
        else -> baseColors
    }
    val classicTileShape = classicTileShape(LocalQSTileShape.current)
    val animatedTileShape = TileDefaults.animateTileShapeAsState(state = uiState.visualState).value
    val density = LocalDensity.current

    val bgColor: Color
    val clipShape: Shape
    val extraModifier: Modifier

    when (panelStyle) {
        1 -> {
            // Classic panel style - filled background with classic tile shape
            bgColor = colors.background
            clipShape = classicTileShape
            extraModifier = Modifier
        }
        2 -> {
            bgColor = Color.Transparent
            clipShape = animatedTileShape
            extraModifier = Modifier.border(2.dp, colors.background, animatedTileShape)
        }
        3 -> {
            bgColor = Color.Transparent
            clipShape = animatedTileShape
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val shadowOffset = with(density) { 3.dp.toPx() }
            val cornerRadius = with(density) { 24.dp.toPx() }
            extraModifier = Modifier.drawBehind {
                val cr = CornerRadius(cornerRadius, cornerRadius)
                drawRoundRect(color = surfaceColor, cornerRadius = cr)
                for (i in 1..3) {
                    val offset = shadowOffset * i / 3f
                    translate(left = -offset, top = -offset) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.25f / i),
                            cornerRadius = cr,
                        )
                    }
                    translate(left = offset, top = offset) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.08f / i),
                            cornerRadius = cr,
                        )
                    }
                }
                drawRoundRect(color = surfaceColor, cornerRadius = cr)
            }
        }
        4 -> {
            bgColor = colors.background
            clipShape = CyberPunkTileShape
            val accentColor = MaterialTheme.colorScheme.tertiary
            extraModifier = Modifier.border(1.5.dp, accentColor, CyberPunkTileShape)
        }
        else -> {
            bgColor = colors.background
            clipShape = animatedTileShape
            extraModifier = Modifier
        }
    }

    val iconSize = CommonTileDefaults.SmallTileIconSize

    Box(
        modifier
            .then(extraModifier)
            .clip(clipShape)
            .background(bgColor)
            .size(iconSize)
            .padding(CommonTileDefaults.StartPadding)
    ) {
        SmallTileContent(
            iconProvider = { getTileIcon(icon = iconProvider) },
            color = colors.icon,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun Context.getTileIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

fun tileHorizontalArrangement(): Arrangement.Horizontal {
    return spacedBy(space = CommonTileDefaults.TileArrangementPadding, alignment = Alignment.Start)
}

@Composable
fun Modifier.tileCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    interactionSource: MutableInteractionSource?,
    iconOnly: Boolean,
    isDualTarget: Boolean,
): Modifier {
    val longPressLabel =
        if (iconOnly && isDualTarget) longPressLabelMoreDetails() else longPressLabelSettings()
    return combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = !Flags.msdlFeedback(),
            interactionSource = interactionSource,
        )
        .semantics {
            val accessibilityRole =
                if (iconOnly && isDualTarget) {
                    Role.Switch
                } else {
                    accessibilityUiState.accessibilityRole
                }
            if (accessibilityRole == Role.Switch) {
                accessibilityUiState.toggleableState?.let { toggleableState = it }
            }
            role = accessibilityRole
            stateDescription = accessibilityUiState.stateDescription
        }
        .thenIf(iconOnly) {
            Modifier.semantics { contentDescription = accessibilityUiState.contentDescription }
        }
}

data class TileColors(
    val background: Color,
    val iconBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val icon: Color,
)

private object TileDefaults {
    val ActiveIconCornerRadius = 16.dp
    val ActiveTileCornerRadius = 24.dp

    /** An active tile uses the active color as background */
    @Composable
    @ReadOnlyComposable
    fun activeTileColors(): TileColors =
        TileColors(
            background = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onPrimary,
            secondaryLabel = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    /** An active tile with dual target only show the active color on the icon */
    @Composable
    @ReadOnlyComposable
    fun activeDualTargetTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onPrimary,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = LocalAndroidColorScheme.current.surfaceEffect2,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(): TileColors =
        TileColors(
            background = LocalAndroidColorScheme.current.surfaceEffect1,
            iconBackground = Color.Transparent,
            label = MaterialTheme.colorScheme.onSurface,
            secondaryLabel = MaterialTheme.colorScheme.onSurface,
            icon = MaterialTheme.colorScheme.onSurface,
        )

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(): TileColors {
        val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .18f)
        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
        return TileColors(
            background = surfaceColor,
            iconBackground = surfaceColor,
            label = onSurfaceVariantColor,
            secondaryLabel = onSurfaceVariantColor,
            icon = onSurfaceVariantColor,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun getColorForState(uiState: TileUiState, iconOnly: Boolean): TileColors {
        return when (uiState.visualState) {
            STATE_ACTIVE -> {
                if (uiState.handlesToggleClick && !iconOnly) {
                    activeDualTargetTileColors()
                } else {
                    activeTileColors()
                }
            }

            STATE_INACTIVE -> {
                if (uiState.handlesToggleClick && !iconOnly) {
                    inactiveDualTargetTileColors()
                } else {
                    inactiveTileColors()
                }
            }

            else -> unavailableTileColors()
        }
    }

    @Composable
    fun animateIconShapeAsState(state: Int): State<RoundedCornerShape> {
        return animateShapeAsState(
            state = state,
            activeCornerRadius = ActiveIconCornerRadius,
            label = "QSTileCornerRadius",
        )
    }

    @Composable
    fun animateTileShapeAsState(state: Int): State<RoundedCornerShape> {
        return animateShapeAsState(
            state = state,
            activeCornerRadius = ActiveTileCornerRadius,
            label = "QSTileIconCornerRadius",
        )
    }

    @Composable
    fun animateShapeAsState(
        state: Int,
        activeCornerRadius: Dp,
        label: String,
    ): State<RoundedCornerShape> {
        val animatedCornerRadius by
            animateDpAsState(
                targetValue =
                    if (state == STATE_ACTIVE) {
                        activeCornerRadius
                    } else {
                        InactiveTileCornerRadius
                    },
                label = label,
            )

        return remember {
            val corner =
                object : CornerSize {
                    override fun toPx(shapeSize: Size, density: Density): Float {
                        return with(density) { animatedCornerRadius.toPx() }
                    }
                }
            mutableStateOf(RoundedCornerShape(corner))
        }
    }
}
@Composable
@ReadOnlyComposable
private fun resources(): Resources {
    LocalConfiguration.current
    return LocalResources.current
}

@VisibleForTesting
object TileMotionTestKeys {
    val Squishness = MotionTestValueKey<Float>("tile_squishiness")
}
