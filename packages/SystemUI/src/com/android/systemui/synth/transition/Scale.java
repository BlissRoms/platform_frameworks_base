  
/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.synth.transition;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;

public class Scale extends Transition {

    private final static String PROPNAME_SCALE_X = "PROPNAME_SCALE_X";
    private final static String PROPNAME_SCALE_Y = "PROPNAME_SCALE_Y";

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    private void captureValues(TransitionValues values){
        values.values.put(PROPNAME_SCALE_X, values.view.getScaleX());
        values.values.put(PROPNAME_SCALE_Y, values.view.getScaleY());
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
        if (endValues == null || startValues == null) return null;    // no values

        float startX = (float) startValues.values.get(PROPNAME_SCALE_X);
        float startY = (float) startValues.values.get(PROPNAME_SCALE_Y);
        float endX = (float) endValues.values.get(PROPNAME_SCALE_X);
        float endY = (float) endValues.values.get(PROPNAME_SCALE_Y);

        if (startX == endX && startY == endY) return null;    // no scale to run

        final View view = startValues.view;
        PropertyValuesHolder propX = PropertyValuesHolder.ofFloat(PROPNAME_SCALE_X, startX, endX);
        PropertyValuesHolder propY = PropertyValuesHolder.ofFloat(PROPNAME_SCALE_Y, startY, endY);
        ValueAnimator valAnim = ValueAnimator.ofPropertyValuesHolder(propX, propY);
        valAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                view.setPivotX(view.getWidth()/2f);
                view.setPivotY(view.getHeight()/2f);
                view.setScaleX((float) valueAnimator.getAnimatedValue(PROPNAME_SCALE_X));
                view.setScaleY((float) valueAnimator.getAnimatedValue(PROPNAME_SCALE_Y));
            }
        });
        return valAnim;
    }

}