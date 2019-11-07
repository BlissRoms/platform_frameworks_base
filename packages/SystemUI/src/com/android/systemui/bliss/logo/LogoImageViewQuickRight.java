/*
 * Copyright (C) 2018 Bliss Android Project
 * Copyright (C) 2018 Crdroid Android Project
 * Copyright (C) 2018 AICP
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

package com.android.systemui.bliss.logo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.Dependency;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.tuner.TunerService;

public class LogoImageViewQuickRight extends ImageView implements
        TunerService.Tunable {

    private Context mContext;

    private boolean mAttached;
    private boolean mBlissLogo;
    private int mBlissLogoColor;
    private int mBlissLogoPosition;
    private int mBlissLogoStyle;
    private int mTintColor = Color.WHITE;

    private static final String STATUS_BAR_LOGO =
            "system:" + Settings.System.STATUS_BAR_LOGO;
    private static final String STATUS_BAR_LOGO_COLOR =
            "system:" + Settings.System.STATUS_BAR_LOGO_COLOR;
    private static final String STATUS_BAR_LOGO_POSITION =
            "system:" + Settings.System.STATUS_BAR_LOGO_POSITION;
    private static final String STATUS_BAR_LOGO_STYLE =
            "system:" + Settings.System.STATUS_BAR_LOGO_STYLE;

    public LogoImageViewQuickRight(Context context) {
        this(context, null);
    }

    public LogoImageViewQuickRight(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LogoImageViewQuickRight(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        final Resources resources = getResources();
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAttached)
            return;

        mAttached = true;

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);

        Dependency.get(TunerService.class).addTunable(this,
                STATUS_BAR_LOGO,
                STATUS_BAR_LOGO_COLOR,
                STATUS_BAR_LOGO_POSITION,
                STATUS_BAR_LOGO_STYLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!mAttached)
            return;

        mAttached = false;
        Dependency.get(TunerService.class).removeTunable(this);
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
    }

    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mBlissLogo && mBlissLogoPosition == 3 &&
                mBlissLogoColor == 0xFFFFFFFF) {
            updateBlissLogo();
        }
    }

    public void updateBlissLogo() {
        Drawable drawable = null;

        if (!mBlissLogo || mBlissLogoPosition != 3) {
            setImageDrawable(null);
            setVisibility(View.GONE);
            return;
        } else {
            setVisibility(View.VISIBLE);
        }

        if (mBlissLogoStyle == 0) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_bliss_logo);
        } else if (mBlissLogoStyle == 1) {
           drawable = mContext.getResources().getDrawable(R.drawable.ic_android_logo);
        } else if (mBlissLogoStyle == 2) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_apple_logo);
        } else if (mBlissLogoStyle == 3) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ios_logo);
        } else if (mBlissLogoStyle == 4) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon);
        } else if (mBlissLogoStyle == 5) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_cool);
        } else if (mBlissLogoStyle == 6) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_dead);
        } else if (mBlissLogoStyle == 7) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_devil);
        } else if (mBlissLogoStyle == 8) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_happy);
        } else if (mBlissLogoStyle == 9) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_neutral);
        } else if (mBlissLogoStyle == 10) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_poop);
        } else if (mBlissLogoStyle == 11) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_sad);
        } else if (mBlissLogoStyle == 12) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_emoticon_tongue);
        } else if (mBlissLogoStyle == 13) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_blackberry);
        } else if (mBlissLogoStyle == 14) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_cake);
        } else if (mBlissLogoStyle == 15) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_blogger);
        } else if (mBlissLogoStyle == 16) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_biohazard);
        } else if (mBlissLogoStyle == 17) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_linux);
        } else if (mBlissLogoStyle == 18) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_yin_yang);
        } else if (mBlissLogoStyle == 19) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_windows);
        } else if (mBlissLogoStyle == 20) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_robot);
        } else if (mBlissLogoStyle == 21) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ninja);
        } else if (mBlissLogoStyle == 22) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_heart);
        } else if (mBlissLogoStyle == 23) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_flower);
        } else if (mBlissLogoStyle == 24) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ghost);
        } else if (mBlissLogoStyle == 25) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_google);
        } else if (mBlissLogoStyle == 26) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_male);
        } else if (mBlissLogoStyle == 27) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_female);
        } else if (mBlissLogoStyle == 28) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_human_male_female);
        } else if (mBlissLogoStyle == 29) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_male);
        } else if (mBlissLogoStyle == 30) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_female);
        } else if (mBlissLogoStyle == 31) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_gender_male_female);
        } else if (mBlissLogoStyle == 32) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_guitar_electric);
        } else if (mBlissLogoStyle == 33) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_batman);
        } else if (mBlissLogoStyle == 34) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_deadpool);
        } else if (mBlissLogoStyle == 35) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_decepticons);
        } else if (mBlissLogoStyle == 36) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_ironman);
        } else if (mBlissLogoStyle == 37) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_minions);
        } else if (mBlissLogoStyle == 38) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_spiderman);
        } else if (mBlissLogoStyle == 39) {
            drawable = mContext.getResources().getDrawable(R.drawable.ic_superman);
        }

        setImageDrawable(null);

        clearColorFilter();

        if (mBlissLogoColor == 0xFFFFFFFF) {
            drawable.setTint(mTintColor);
        } else {
            setColorFilter(mBlissLogoColor, PorterDuff.Mode.SRC_IN);
        }
        setImageDrawable(drawable);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_LOGO:
                mBlissLogo = TunerService.parseIntegerSwitch(newValue, false);
                break;
            case STATUS_BAR_LOGO_COLOR:
                mBlissLogoColor =
                        newValue == null ? 0xFFFFFFFF : Integer.parseInt(newValue);
                break;
            case STATUS_BAR_LOGO_POSITION:
                mBlissLogoPosition =
                        newValue == null ? 0 : Integer.parseInt(newValue);
                break;
            case STATUS_BAR_LOGO_STYLE:
                mBlissLogoStyle =
                        newValue == null ? 0 : Integer.parseInt(newValue);
                break;
            default:
                break;
        }
        updateBlissLogo();
    }
}
