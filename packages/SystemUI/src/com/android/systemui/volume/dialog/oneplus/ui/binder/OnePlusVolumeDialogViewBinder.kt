/*
 * Copyright (C) 2014-2026 The BlissRoms Project
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

package com.android.systemui.volume.dialog.oneplus.ui.binder

import android.app.Dialog
import android.util.MathUtils.lerp
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.app.tracing.coroutines.launchInTraced
import com.android.app.tracing.coroutines.launchTraced
import com.android.compose.theme.PlatformTheme
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.dagger.scope.VolumeDialogScope
import com.android.systemui.volume.dialog.oneplus.ui.compose.OnePlusVolumePanel
import com.android.systemui.volume.dialog.oneplus.ui.viewmodel.OnePlusVolumePanelViewModel
import com.android.systemui.volume.dialog.shared.model.VolumeDialogVisibilityModel
import com.android.systemui.volume.dialog.ui.utils.suspendAnimate
import com.android.systemui.volume.dialog.ui.viewmodel.VolumeDialogViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

private const val SPRING_STIFFNESS = 700f
private const val SPRING_DAMPING_RATIO = 0.9f
private const val FRACTION_HIDE = 0f
private const val FRACTION_SHOW = 1f
private const val ANIMATION_MINIMUM_VISIBLE_CHANGE = 0.01f

@VolumeDialogScope
class OnePlusVolumeDialogViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogViewModel,
    private val onePlusViewModel: OnePlusVolumePanelViewModel,
) {

    fun CoroutineScope.bind(dialog: Dialog, isOnLeft: Boolean) {
        val root: ViewGroup = dialog.requireViewById(R.id.volume_dialog)
        val composeView: ComposeView =
            root.findViewById(R.id.volume_dialog_oneplus_content)

        composeView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        composeView.setContent {
            PlatformTheme {
                OnePlusVolumePanel(
                    viewModel = onePlusViewModel,
                    isOnLeft = isOnLeft,
                )
            }
        }

        animateVisibility(root, dialog, isOnLeft, viewModel.dialogVisibilityModel)

        launchTraced("OnePlusVDB#expandState") {
            onePlusViewModel.isExpanded.collect { expanded ->
                val window = dialog.window ?: return@collect
                if (expanded) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = 80
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = 0
                    }
                }
            }
        }
    }

    private fun CoroutineScope.animateVisibility(
        view: View,
        dialog: Dialog,
        isOnLeft: Boolean,
        visibilityModel: Flow<VolumeDialogVisibilityModel>,
    ) {
        view.applyAnimationProgress(FRACTION_HIDE, isOnLeft)
        val animationValueHolder = FloatValueHolder(FRACTION_HIDE)
        val animation: SpringAnimation =
            SpringAnimation(animationValueHolder)
                .setSpring(
                    SpringForce()
                        .setStiffness(SPRING_STIFFNESS)
                        .setDampingRatio(SPRING_DAMPING_RATIO)
                )
                .setMinimumVisibleChange(ANIMATION_MINIMUM_VISIBLE_CHANGE)
                .addUpdateListener { _, value, _ -> view.applyAnimationProgress(value, isOnLeft) }

        visibilityModel
            .conflate()
            .onEach {
                when (it) {
                    is VolumeDialogVisibilityModel.Visible -> {
                        onePlusViewModel.isExpanded.value = false
                        animation.suspendAnimate(FRACTION_SHOW)
                    }
                    is VolumeDialogVisibilityModel.Dismissed -> {
                        animation.suspendAnimate(FRACTION_HIDE)
                        dialog.dismiss()
                    }
                    is VolumeDialogVisibilityModel.Invisible -> {}
                }
            }
            .launchInTraced("OnePlusVDB#visibility", this)
    }

    private fun View.applyAnimationProgress(fraction: Float, isOnLeft: Boolean) {
        alpha = ceil(fraction)
        val startTranslationX = if (isOnLeft) {
            -width.toFloat()
        } else {
            width.toFloat()
        }
        translationX = lerp(startTranslationX, 0f, fraction)
    }
}
