package com.android.systemui.communal.ui.compose

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserActionDistance
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.observableTransitionState
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags
import com.android.systemui.communal.shared.model.CommunalBackgroundType
import com.android.systemui.communal.shared.model.CommunalSceneDataSourceDelegator
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.shared.model.CommunalTransitionKeys
import com.android.systemui.communal.ui.compose.Dimensions.Companion.SlideOffsetY
import com.android.systemui.communal.ui.compose.extensions.allowGestures
import com.android.systemui.communal.ui.compose.section.AmbientStatusBarSection
import com.android.systemui.communal.ui.viewmodel.CommunalViewModel
import com.android.systemui.communal.util.CommunalColors
import com.android.systemui.keyguard.domain.interactor.FromAodTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.FromGlanceableHubTransitionInteractor.Companion.TO_LOCKSCREEN_DURATION
import com.android.systemui.keyguard.domain.interactor.FromPrimaryBouncerTransitionInteractor.Companion.TO_GONE_DURATION
import com.android.systemui.scene.ui.composable.SceneTransitionLayoutDataSource
import kotlin.time.DurationUnit

object Communal {
    object Elements {
        val Scrim = ElementKey("Scrim", contentPicker = LowestZIndexContentPicker)
        val Grid = ElementKey("CommunalContent")
        val LockIcon = ElementKey("CommunalLockIcon")
        val IndicationArea = ElementKey("CommunalIndicationArea")
        val StatusBar = ElementKey("StatusBar")
    }
}

object AllElements : ElementMatcher {
    override fun matches(key: ElementKey, content: ContentKey) = true
}

object TransitionDuration {
    const val BETWEEN_HUB_AND_EDIT_MODE_MS = 1000
    const val EDIT_MODE_TO_HUB_CONTENT_MS = 167
    const val EDIT_MODE_TO_HUB_GRID_DELAY_MS = 167
    const val EDIT_MODE_TO_HUB_GRID_END_MS =
        EDIT_MODE_TO_HUB_GRID_DELAY_MS + EDIT_MODE_TO_HUB_CONTENT_MS
    const val HUB_TO_EDIT_MODE_CONTENT_MS = 250
    const val TO_GLANCEABLE_HUB_DURATION_MS = 1000

    // The duration in milliseconds to animate in and our of the background for edit mode
    // transition.
    const val EDIT_MODE_BACKGROUND_ANIM_DURATION_MS = 300
}

val sceneTransitionsV2 = transitions {
    to(CommunalScenes.Communal) {
        spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)
        fade(AllElements)
    }
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.FromOccluded) {
        spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)
        timestampRange(startMillis = 250) { fade(AllElements) }
    }
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.FromAod) {
        spec =
            tween(
                durationMillis =
                    FromAodTransitionInteractor.TO_GLANCEABLE_HUB_DURATION.toInt(
                        DurationUnit.MILLISECONDS
                    )
            )
        timestampRange(startMillis = 167) { fade(AllElements) }
    }
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.Swipe) {
        spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)
        translate(Communal.Elements.Grid, Edge.Start)
        if (Flags.gestureBetweenHubAndLockscreenMotion()) {
            distance = UserActionDistance { fromContent, _, _ ->
                // fromContent size can be null if it hasn't been compose yet, in which case we
                // fall back to 0.
                fromContent.targetSize()?.width?.times(0.5f) ?: 0f
            }
            timestampRange(startMillis = 167, endMillis = 334) { fade(Communal.Elements.StatusBar) }
        } else {
            timestampRange(startMillis = 167, endMillis = 334) { fade(AllElements) }
        }
    }
    to(CommunalScenes.Blank) {
        spec = tween(durationMillis = TO_GONE_DURATION.toInt(DurationUnit.MILLISECONDS))
        fade(AllElements)
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.SwipeUp) {
        spec = tween(durationMillis = TO_LOCKSCREEN_DURATION.toInt(DurationUnit.MILLISECONDS))
        fade(AllElements)
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.SwipeInLandscape) {
        spec = tween(durationMillis = TO_LOCKSCREEN_DURATION.toInt(DurationUnit.MILLISECONDS))
        translate(Communal.Elements.Grid, Edge.Start)
        timestampRange(endMillis = 167) {
            fade(Communal.Elements.Grid)
            fade(Communal.Elements.IndicationArea)
            fade(Communal.Elements.LockIcon)
            fade(Communal.Elements.StatusBar)
        }
        timestampRange(startMillis = 167, endMillis = 500) { fade(Communal.Elements.Scrim) }
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.Swipe) {
        spec = tween(durationMillis = TransitionDuration.TO_GLANCEABLE_HUB_DURATION_MS)
        translate(Communal.Elements.Grid, Edge.Start)
        if (!Flags.gestureBetweenHubAndLockscreenMotion()) {
            timestampRange(endMillis = 167) {
                fade(Communal.Elements.Grid)
                fade(Communal.Elements.IndicationArea)
                fade(Communal.Elements.LockIcon)
                if (!Flags.glanceableHubV2()) {
                    fade(Communal.Elements.StatusBar)
                }
            }
            timestampRange(startMillis = 167, endMillis = 334) { fade(Communal.Elements.Scrim) }
        }
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.ToEditMode) {
        spec = tween(durationMillis = TransitionDuration.BETWEEN_HUB_AND_EDIT_MODE_MS)
        fade(AllElements)
    }
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.FromEditMode) {
        spec = tween(durationMillis = TransitionDuration.BETWEEN_HUB_AND_EDIT_MODE_MS)
        fade(AllElements)
    }
}

val sceneTransitions = transitions {
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.SimpleFade) {
        spec = tween(durationMillis = 250)
        fade(AllElements)
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.SimpleFade) {
        spec = tween(durationMillis = TO_GONE_DURATION.toInt(DurationUnit.MILLISECONDS))
        fade(AllElements)
    }
    to(CommunalScenes.Communal) {
        spec = tween(durationMillis = 1000)
        translate(Communal.Elements.Grid, Edge.Start)
        timestampRange(startMillis = 167, endMillis = 334) { fade(AllElements) }
    }
    to(CommunalScenes.Blank) {
        spec = tween(durationMillis = 1000)
        translate(Communal.Elements.Grid, Edge.Start)
        timestampRange(endMillis = 167) {
            fade(Communal.Elements.Grid)
            fade(Communal.Elements.IndicationArea)
            fade(Communal.Elements.LockIcon)
            if (!Flags.glanceableHubV2()) {
                fade(Communal.Elements.StatusBar)
            }
        }
        timestampRange(startMillis = 167, endMillis = 334) { fade(Communal.Elements.Scrim) }
    }
    to(CommunalScenes.Blank, key = CommunalTransitionKeys.ToEditMode) {
        spec = tween(durationMillis = TransitionDuration.BETWEEN_HUB_AND_EDIT_MODE_MS)
        timestampRange(endMillis = TransitionDuration.HUB_TO_EDIT_MODE_CONTENT_MS) {
            fade(Communal.Elements.Grid)
            fade(Communal.Elements.IndicationArea)
            fade(Communal.Elements.LockIcon)
        }
        fade(Communal.Elements.Scrim)
    }
    to(CommunalScenes.Communal, key = CommunalTransitionKeys.FromEditMode) {
        spec = tween(durationMillis = TransitionDuration.BETWEEN_HUB_AND_EDIT_MODE_MS)
        translate(Communal.Elements.Grid, y = SlideOffsetY)
        timestampRange(endMillis = TransitionDuration.EDIT_MODE_TO_HUB_CONTENT_MS) {
            fade(Communal.Elements.IndicationArea)
            fade(Communal.Elements.LockIcon)
            fade(Communal.Elements.Scrim)
        }
        timestampRange(
            startMillis = TransitionDuration.EDIT_MODE_TO_HUB_GRID_DELAY_MS,
            endMillis = TransitionDuration.EDIT_MODE_TO_HUB_GRID_END_MS,
        ) {
            fade(Communal.Elements.Grid)
        }
    }
}

/**
 * View containing a [SceneTransitionLayout] that shows the communal UI and handles transitions.
 *
 * This is a temporary container to allow the communal UI to use [SceneTransitionLayout] for gesture
 * handling and transitions before the full Flexiglass layout is ready.
 */
@Composable
fun CommunalContainer(
    modifier: Modifier = Modifier,
    viewModel: CommunalViewModel,
    dataSourceDelegator: CommunalSceneDataSourceDelegator,
    colors: CommunalColors,
    content: CommunalContent,
    ambientStatusBarSection: AmbientStatusBarSection,
) {
    val coroutineScope = rememberCoroutineScope()
    val currentSceneKey: SceneKey by viewModel.currentScene.collectAsStateWithLifecycle()
    val backgroundType by
        viewModel.communalBackground.collectAsStateWithLifecycle(
            initialValue = CommunalBackgroundType.ANIMATED
        )
    val swipeToHubEnabled by viewModel.swipeToHubEnabled.collectAsStateWithLifecycle(false)
    val state: MutableSceneTransitionLayoutState =
        rememberMutableSceneTransitionLayoutState(
            initialScene = currentSceneKey,
            canChangeScene = { toScene -> viewModel.canChangeScene(toScene) },
            transitions = if (viewModel.v2FlagEnabled()) sceneTransitionsV2 else sceneTransitions,
        )
    val detector = remember { CommunalSwipeDetector() }

    DisposableEffect(state) {
        val dataSource = SceneTransitionLayoutDataSource(state, coroutineScope)
        dataSourceDelegator.setDelegate(dataSource)
        onDispose { dataSourceDelegator.setDelegate(null) }
    }

    // This effect exposes the SceneTransitionLayout's observable transition state to the rest of
    // the system, and unsets it when the view is disposed to avoid a memory leak.
    DisposableEffect(viewModel, state) {
        viewModel.setTransitionState(state.observableTransitionState())
        onDispose { viewModel.setTransitionState(null) }
    }

    val swipeFromHubInLandscape by
        viewModel.swipeFromHubInLandscape.collectAsStateWithLifecycle(false)

    SceneTransitionLayout(
        state = state,
        modifier = modifier.fillMaxSize(),
        swipeSourceDetector = detector,
        swipeDetector = detector,
        debugName = "Communal UI",
    ) {
        scene(
            CommunalScenes.Blank,
            userActions =
                if (swipeToHubEnabled) {
                    mapOf(
                        Swipe.End(fromSource = Edge.Start) to
                            UserActionResult(CommunalScenes.Communal, CommunalTransitionKeys.Swipe)
                    )
                } else {
                    emptyMap()
                },
        ) {
            // This scene shows nothing only allowing for transitions to the communal scene.
            Box(modifier = Modifier.fillMaxSize())
        }

        scene(
            CommunalScenes.Communal,
            userActions =
                mapOf(
                    Swipe.Start to
                        UserActionResult(
                            CommunalScenes.Blank,
                            if (swipeFromHubInLandscape) {
                                CommunalTransitionKeys.SwipeInLandscape
                            } else {
                                CommunalTransitionKeys.Swipe
                            },
                        ),
                    Swipe.Up to
                        UserActionResult(CommunalScenes.Blank, CommunalTransitionKeys.SwipeUp),
                ),
        ) {
            val touchesAllowed by viewModel.touchesAllowed.collectAsStateWithLifecycle()

            CommunalScene(
                backgroundType = backgroundType,
                colors = colors,
                content = content,
                ambientStatusBarSection = ambientStatusBarSection,
                viewModel = viewModel,
            )

            // Touches on the notification shade in blank areas fall through to the glanceable hub.
            // When the shade is showing, we block all touches in order to prevent this unwanted
            // behavior.
            Box(modifier = Modifier.fillMaxSize().allowGestures(touchesAllowed))
        }
    }
}

/** Scene containing the glanceable hub UI. */
@Composable
fun ContentScope.CommunalScene(
    backgroundType: CommunalBackgroundType,
    colors: CommunalColors,
    content: CommunalContent,
    ambientStatusBarSection: AmbientStatusBarSection,
    viewModel: CommunalViewModel,
    modifier: Modifier = Modifier,
) {
    val isFocusable by viewModel.isFocusable.collectAsStateWithLifecycle(initialValue = false)
    val isBlurred by viewModel.isUiBlurred.collectAsStateWithLifecycle()
    val blurRadius = with(LocalDensity.current) { viewModel.blurRadiusPx.toDp() }

    Box(
        modifier =
            Modifier.element(Communal.Elements.Scrim)
                .fillMaxSize()
                .thenIf(isBlurred) { Modifier.blur(blurRadius) }
                .then(
                    if (isFocusable) {
                        Modifier.focusable()
                    } else {
                        Modifier.semantics { disabled() }.clearAndSetSemantics {}
                    }
                )
    ) {
        when (backgroundType) {
            CommunalBackgroundType.STATIC -> DefaultBackground(colors = colors)
            CommunalBackgroundType.STATIC_GRADIENT -> StaticLinearGradient()
            CommunalBackgroundType.ANIMATED -> AnimatedLinearGradient()
            CommunalBackgroundType.NONE -> BackgroundTopScrim()
            CommunalBackgroundType.BLUR -> Background()
            CommunalBackgroundType.SCRIM -> Scrimmed()
        }

        if (!Flags.glanceableHubV2()) {
            with(ambientStatusBarSection) {
                AmbientStatusBar(modifier = Modifier.fillMaxWidth().zIndex(1f))
            }
        }

        with(content) {
            Content(
                modifier =
                    modifier.focusable(isFocusable).semantics {
                        if (!isFocusable) {
                            disabled()
                        }
                    }
            )
        }
    }
}

/** Default background of the hub, a single color */
@Composable
private fun BoxScope.DefaultBackground(colors: CommunalColors) {
    val backgroundColor by colors.backgroundColor.collectAsStateWithLifecycle()
    Box(modifier = Modifier.matchParentSize().background(Color(backgroundColor.toArgb())))
}

@Composable
private fun BoxScope.Scrimmed() {
    Box(modifier = Modifier.matchParentSize().alpha(0.34f).background(Color.Black))
}

/** Experimental hub background, static linear gradient */
@Composable
private fun BoxScope.StaticLinearGradient() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.matchParentSize()
            .background(
                Brush.linearGradient(colors = listOf(colors.primary, colors.primaryContainer))
            )
    )
    BackgroundTopScrim()
}

/** Experimental hub background, animated linear gradient */
@Composable
private fun BoxScope.AnimatedLinearGradient() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.matchParentSize()
            .background(colors.primary)
            .animatedRadialGradientBackground(
                toColor = colors.primary,
                fromColor = colors.primaryContainer.copy(alpha = 0.6f),
            )
    )
    BackgroundTopScrim()
}

/** Scrim placed on top of the background in order to dim/bright colors */
@Composable
private fun BoxScope.BackgroundTopScrim() {
    val darkTheme = isSystemInDarkTheme()
    val scrimOnTopColor = if (darkTheme) Color.Black else Color.White
    Box(Modifier.matchParentSize().alpha(0.34f).background(scrimOnTopColor))
}

/** Transparent (nothing) composable for when the background is blurred. */
@Composable private fun BoxScope.Background() {}

/** The duration to use for the gradient background animation. */
private const val ANIMATION_DURATION_MS = 10_000

/** The offset to use in order to place the center of each gradient offscreen. */
private val ANIMATION_OFFSCREEN_OFFSET = 128.dp

/** Modifier which creates two radial gradients that animate up and down. */
@Composable
fun Modifier.animatedRadialGradientBackground(toColor: Color, fromColor: Color): Modifier {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "radial gradient transition")
    val centerFraction by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = ANIMATION_DURATION_MS,
                            easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f),
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "radial gradient center fraction",
        )

    // Offset to place the center of the gradients offscreen. This is applied to both the
    // x and y coordinates.
    val offsetPx = remember(density) { with(density) { ANIMATION_OFFSCREEN_OFFSET.toPx() } }

    return drawBehind {
        val gradientRadius = (size.width / 2) + offsetPx
        val totalHeight = size.height + 2 * offsetPx

        val leftCenter = Offset(x = -offsetPx, y = totalHeight * centerFraction - offsetPx)
        val rightCenter =
            Offset(x = offsetPx + size.width, y = totalHeight * (1f - centerFraction) - offsetPx)

        // Right gradient
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(fromColor, toColor),
                    center = rightCenter,
                    radius = gradientRadius,
                ),
            center = rightCenter,
            radius = gradientRadius,
            blendMode = BlendMode.SrcAtop,
        )

        // Left gradient
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(fromColor, toColor),
                    center = leftCenter,
                    radius = gradientRadius,
                ),
            center = leftCenter,
            radius = gradientRadius,
            blendMode = BlendMode.SrcAtop,
        )
    }
}
