/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.keyguard.CarrierText;
import com.android.systemui.Dependency;
import com.android.systemui.statusbar.DataUsageView;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.phone.SettingsButton;

public class OPQSFooter extends LinearLayout {

    protected View mEdit;
    private LinearLayout mMediaSpacer;
    private View mBrightnessView;
    protected TouchAnimator mFooterAnimator;
    protected TouchAnimator mCarrierTextAnimator;
    private Boolean mExpanded;
    private Boolean mIsLandscape = false;
    private boolean mIsQQSPanel = false;

    private SettingsButton mSettingsButton;
    private ActivityStarter mActivityStarter;
    private FrameLayout mFooterActions;
    private DataUsageView mDataUsageView;
    private CarrierText mCarrierText;

    public OPQSFooter(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBrightnessView = findViewById(R.id.brightness_view);
        mMediaSpacer = findViewById(R.id.qs_footer_media_spacer);
        mEdit = findViewById(R.id.edit);
        mSettingsButton = findViewById(R.id.settings_button);
        mFooterActions = findViewById(R.id.op_qs_footer_actions);
        mCarrierText = findViewById(R.id.qs_carrier_text);
        mDataUsageView = findViewById(R.id.data_usage_view);
        mDataUsageView.setVisibility(View.GONE);
        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        mFooterAnimator = createFooterAnimator();
        mCarrierTextAnimator = createCarrierTextAnimator();
    }

    public void setExpansion(float headerExpansionFraction) {
        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }
        if (mCarrierTextAnimator != null) {
            mCarrierTextAnimator.setPosition(headerExpansionFraction);
        }
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
        if (mDataUsageView != null) {
            mDataUsageView.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
            if (mExpanded) {
                mDataUsageView.updateUsage();
            }
        }
        if (mEdit != null) {
            mEdit.setVisibility(mExpanded ? View.VISIBLE : View.GONE);
        }
        mIsQQSPanel = !mExpanded;
        setOrientation(mIsLandscape);
    }

    public void setIsQQSPanel() {
        mIsQQSPanel = true;
        mEdit.setVisibility(View.GONE);
        mDataUsageView.setVisibility(View.GONE);
        setOrientation(mIsLandscape);
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mEdit, "alpha", 0, 0, 1);
        if (mIsLandscape) {
            builder = builder.addFloat(mBrightnessView, "alpha", 0, 0, 1)
                    .setStartDelay(0.5f);
            builder = builder.addFloat(mSettingsButton, "alpha", 0, 0, 1)
                    .setStartDelay(0.5f);
        }
        return builder.build();
    }

    @Nullable
    private TouchAnimator createCarrierTextAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mDataUsageView, "alpha", 0, 0, 1);
        if (mIsLandscape) {
            builder = builder.addFloat(mCarrierText, "alpha", 0, 0, 0)
                    .setStartDelay(0.5f);
        } else {
            builder = builder.addFloat(mCarrierText, "alpha", 1, 0, 0);
        }
        return builder.build();
    }

    public View getFooterActions() {
        return mFooterActions;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setOrientation(newConfig.orientation
                == Configuration.ORIENTATION_LANDSCAPE);
    }

    public void setOrientation(boolean isLandscape) {
        if (mIsLandscape != isLandscape) {
            mIsLandscape = isLandscape;
            mBrightnessView.setAlpha(1.0f);
            mSettingsButton.setAlpha(1.0f);
            mFooterAnimator = createFooterAnimator();
            mCarrierTextAnimator = createCarrierTextAnimator();
        }
        if (mIsLandscape && mIsQQSPanel) {
            mMediaSpacer.setVisibility(View.VISIBLE);
            mBrightnessView.setVisibility(View.GONE);
            mFooterActions.setVisibility(View.GONE);
        } else {
            mMediaSpacer.setVisibility(View.GONE);
            mBrightnessView.setVisibility(View.VISIBLE);
            mFooterActions.setVisibility(View.VISIBLE);
        }
    }

    public View getSettingsButton() {
        return mSettingsButton;
    }

    public View getEditButton() {
        return mEdit;
    }
}
