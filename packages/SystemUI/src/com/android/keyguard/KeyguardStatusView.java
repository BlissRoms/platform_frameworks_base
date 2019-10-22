/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import androidx.core.graphics.ColorUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.CrossFadeHelper;

import java.io.PrintWriter;
import java.util.Set;

/**
 * View consisting of:
 * - keyguard clock
 * - media player (split shade mode only)
 */
public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private ViewGroup mStatusViewContainer;
    private KeyguardClockSwitch mClockView;
    private KeyguardSliceView mKeyguardSlice;
    private View mMediaHostContainer;
    private KeyguardSliceView mKeyguardSliceView;

    private float mDarkAmount = 0;
    private int mTextColor;

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mStatusViewContainer = findViewById(R.id.status_view_container);

        mClockView = findViewById(R.id.keyguard_clock_container);
        mKeyguardSlice = findViewById(R.id.keyguard_slice_view);
        mKeyguardSliceView = findViewById(R.id.keyguard_slice_view);

        mClockView.refreshLockFont();
	refreshLockDateFont();

        mTextColor = mClockView.getCurrentTextColor();

        mMediaHostContainer = findViewById(R.id.status_view_media_container);

        updateDark();
    }

    private int getLockDateFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_DATE_FONTS, 28);
    }

    public void refreshLockDateFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockDateFont = isPrimary ? getLockDateFont() : 28;

        switch (lockDateFont) {
        case 0:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            break;
        case 1:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            break;
        case 2:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            break;
        case 3:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            break;
        case 4:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            break;
        case 5:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            break;
        case 6:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            break;
        case 7:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            break;
        case 8:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            break;
        case 9:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            break;
        case 10:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            break;
        case 11:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            break;
        case 12:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            break;
        case 13:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            break;
        case 14:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            break;
        case 15:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            break;
        case 16:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            break;
        case 17:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            break;
        case 18:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("cursive", Typeface.NORMAL));
            break;
        case 19:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("cursive", Typeface.BOLD));
            break;
        case 20:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("casual", Typeface.NORMAL));
            break;
        case 21:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("serif", Typeface.NORMAL));
            break;
        case 22:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("serif", Typeface.ITALIC));
            break;
        case 23:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("serif", Typeface.BOLD));
            break;
        case 24:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            break;
        case 25:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
            break;
        case 26:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
            break;
        case 27:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
            break;
        case 28:
        default:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("googlesansmedium-sys", Typeface.NORMAL));
            break;
        case 29:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
            break;
        case 30:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
            break;
        case 31:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
            break;
        case 32:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
            break;
        case 33:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
            break;
        case 34:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
            break;
        case 35:
            mKeyguardSliceView.setViewsTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
            break;
        }
    }

    void setDarkAmount(float darkAmount) {
        if (mDarkAmount == darkAmount) {
            return;
        }
        mDarkAmount = darkAmount;
        mClockView.setDarkAmount(darkAmount);
        CrossFadeHelper.fadeOut(mMediaHostContainer, darkAmount);
        updateDark();
    }

    void updateDark() {
        final int blendedTextColor = ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
        mKeyguardSlice.setDarkAmount(mDarkAmount);
        mClockView.setTextColor(blendedTextColor);
        mClockView.refreshLockFont();
        refreshLockDateFont();
    }

    /** Sets a translationY value on every child view except for the media view. */
    public void setChildrenTranslationYExcludingMediaView(float translationY) {
        setChildrenTranslationYExcluding(translationY, Set.of(mMediaHostContainer));
    }

    /** Sets a translationY value on every view except for the views in the provided set. */
    private void setChildrenTranslationYExcluding(float translationY, Set<View> excludedViews) {
        for (int i = 0; i < mStatusViewContainer.getChildCount(); i++) {
            final View child = mStatusViewContainer.getChildAt(i);

            if (!excludedViews.contains(child)) {
                child.setTranslationY(translationY);
            }
        }
    }

    public void dump(PrintWriter pw, String[] args) {
        pw.println("KeyguardStatusView:");
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        if (mClockView != null) {
            mClockView.dump(pw, args);
        }
        if (mKeyguardSlice != null) {
            mKeyguardSlice.dump(pw, args);
        }
    }
}
