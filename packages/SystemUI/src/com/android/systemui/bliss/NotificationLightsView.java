/*
* Copyright (C) 2019 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.bliss;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class NotificationLightsView extends RelativeLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "NotificationLightsView";
    private ValueAnimator mLightAnimator;

    public NotificationLightsView(Context context) {
        this(context, null);
    }

    public NotificationLightsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NotificationLightsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void stopAnimateNotification() {
        if (mLightAnimator != null) {
            mLightAnimator.end();
            mLightAnimator = null;
        }
    }

    public void animateNotification() {
        int usercolor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_COLOR, 0xFF3980FF,
                UserHandle.USER_CURRENT);
        int duration = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_DURATION, 2,
                UserHandle.USER_CURRENT) * 1000;
        boolean useAccent = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_PULSE_ACCENT,
                0, UserHandle.USER_CURRENT) != 0;
        int repeat = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_LIGHT_REPEAT_COUNT, 0,
                UserHandle.USER_CURRENT);
        int color = useAccent ?
                Utils.getColorAccentDefaultColor(getContext()) : usercolor;
        if (DEBUG) Log.d(TAG, "color = " + Integer.toHexString(color));
        int layout = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.PULSE_AMBIENT_LIGHT_LAYOUT, 0,
                UserHandle.USER_CURRENT);
        ImageView leftViewSolid = (ImageView) findViewById(R.id.notification_animation_left_solid);
        ImageView leftViewFaded = (ImageView) findViewById(R.id.notification_animation_left_faded);
        leftViewSolid.setColorFilter(color);
        leftViewFaded.setColorFilter(color);
        leftViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        leftViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        ImageView rightViewSolid = (ImageView) findViewById(R.id.notification_animation_right_solid);
        ImageView rightViewFaded = (ImageView) findViewById(R.id.notification_animation_right_faded);
        rightViewSolid.setColorFilter(color);
        rightViewFaded.setColorFilter(color);
        rightViewSolid.setVisibility(layout == 0 ? View.VISIBLE : View.GONE);
        rightViewFaded.setVisibility(layout == 1 ? View.VISIBLE : View.GONE);
        mLightAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
        mLightAnimator.setDuration(duration);
            if (repeat == 0) {
                mLightAnimator.setRepeatCount(ValueAnimator.INFINITE);
            } else {
                mLightAnimator.setRepeatCount(repeat - 1);
            }
        mLightAnimator.setRepeatMode(ValueAnimator.RESTART);
        mLightAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (DEBUG) Log.d(TAG, "onAnimationUpdate");
                float progress = ((Float) animation.getAnimatedValue()).floatValue();
                leftViewSolid.setScaleY(progress);
                leftViewFaded.setScaleY(progress);
                rightViewSolid.setScaleY(progress);
                rightViewFaded.setScaleY(progress);
                float alpha = 1.0f;
                if (progress <= 0.3f) {
                    alpha = progress / 0.3f;
                } else if (progress >= 1.0f) {
                    alpha = 2.0f - progress;
                }
                leftViewSolid.setAlpha(alpha);
                leftViewFaded.setAlpha(alpha);
                rightViewSolid.setAlpha(alpha);
                rightViewFaded.setAlpha(alpha);
            }
        });
        if (DEBUG) Log.d(TAG, "start");
        mLightAnimator.start();
    }
}
