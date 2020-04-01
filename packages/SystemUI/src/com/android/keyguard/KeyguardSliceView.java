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

package com.android.keyguard;

import static android.app.slice.Slice.HINT_LIST_ITEM;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.ColorInt;
import android.annotation.StyleRes;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Trace;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.SliceViewManager;
import androidx.slice.core.SliceQuery;
import androidx.slice.widget.ListContent;
import androidx.slice.widget.RowContent;
import androidx.slice.widget.SliceContent;
import androidx.slice.widget.SliceLiveData;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.Utils;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import com.google.android.systemui.keyguard.KeyguardSliceProviderGoogle;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * View visible under the clock on the lock screen and AoD.
 */
public class KeyguardSliceView extends LinearLayout implements View.OnClickListener,
        Observer<Slice>, TunerService.Tunable, ConfigurationController.ConfigurationListener {

    private static final String TAG = "KeyguardSliceView";

    private static final int FONT_NORMAL = 0;
    private static final int FONT_ITALIC = 1;
    private static final int FONT_BOLD = 2;
    private static final int FONT_BOLD_ITALIC = 3;
    private static final int FONT_LIGHT = 4;
    private static final int FONT_LIGHT_ITALIC = 5;
    private static final int FONT_THIN = 6;
    private static final int FONT_THIN_ITALIC = 7;
    private static final int FONT_CONDENSED = 8;
    private static final int FONT_CONDENSED_ITALIC = 9;
    private static final int FONT_CONDENSED_LIGHT = 10;
    private static final int FONT_CONDENSED_LIGHT_ITALIC = 11;
    private static final int FONT_CONDENSED_BOLD = 12;
    private static final int FONT_CONDENSED_BOLD_ITALIC = 13;
    private static final int FONT_MEDIUM = 14;
    private static final int FONT_MEDIUM_ITALIC = 15;
    private static final int FONT_BLACK = 16;
    private static final int FONT_BLACK_ITALIC = 17;
    private static final int FONT_DANCINGSCRIPT = 18;
    private static final int FONT_DANCINGSCRIPT_BOLD = 19;
    private static final int FONT_COMINGSOON = 20;
    private static final int FONT_NOTOSERIF = 21;
    private static final int FONT_NOTOSERIF_ITALIC = 22;
    private static final int FONT_NOTOSERIF_BOLD = 23;
    private static final int FONT_NOTOSERIF_BOLD_ITALIC = 24;
    private static final int FONT_ACLONICA = 25;
    private static final int FONT_AMARANTE = 26;
    private static final int FONT_BARIOL = 27;
    private static final int FONT_CAGLIOSTRO = 28;
    private static final int FONT_COOLSTORY = 29;
    private static final int FONT_LGSMARTGOTHIC = 30;
    private static final int FONT_ROSEMARY = 31;
    private static final int FONT_SONYSKETCH = 32;
    private static final int FONT_SURFER = 33;
    private static final int FONT_COMICSANS = 34;
    private static final int FONT_GOOGLESANS = 35;
    private static final int FONT_ONEPLUSSLATE = 36;
    private static final int FONT_SAMSUNGONE = 37;
    private static final int FONT_COMFORTAA = 38;
    private static final int FONT_EXOTWO = 39;
    private static final int FONT_STOROPIA = 40;
    private static final int FONT_UBUNTU = 41;
    private static final int FONT_NOKIAPURE = 42;
    private static final int FONT_FIFA2018 = 43;
    private static final int FONT_ROADRAGE = 44;
    private static final int FONT_20SEVEN = 45;
    private static final int FONT_COCON = 46;
    private static final int FONT_QUANDO = 47;
    private static final int FONT_GRANDHOTEL = 48;
    private static final int FONT_REDRESSED = 49;
    private static final int FONT_SANFRANSISCO = 50;

    public static final int DEFAULT_ANIM_DURATION = 550;

    private final HashMap<View, PendingIntent> mClickActions;
    private final ActivityStarter mActivityStarter;
    private final ConfigurationController mConfigurationController;
    private final LayoutTransition mLayoutTransition;
    private Uri mKeyguardSliceUri;
    @VisibleForTesting
    TextView mTitle;
    private RelativeLayout mRowContainer;
    private Row mRow;
    private int mTextColor;
    private float mDarkAmount = 0;

    private LiveData<Slice> mLiveData;
    private int mDisplayId = INVALID_DISPLAY;
    private int mIconSize;
    private int mIconSizeWithHeader;
    private int mWeatherIconSize;
    /**
     * Runnable called whenever the view contents change.
     */
    private Runnable mContentChangeListener;
    private Slice mSlice;
    private boolean mHasHeader;
    private final int mRowWithHeaderPadding;
    private final int mRowPadding;
    private int mRowTextSize;
    private float mRowWithHeaderTextSize;

    private int mLockDateFontStyle = 14;
    private int mLockDateSize = 10;

    @Inject
    public KeyguardSliceView(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            ActivityStarter activityStarter, ConfigurationController configurationController) {
        super(context, attrs);

        TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, Settings.Secure.KEYGUARD_SLICE_URI);

        mClickActions = new HashMap<>();
        mRowPadding = context.getResources().getDimensionPixelSize(R.dimen.subtitle_clock_padding);
        mRowWithHeaderPadding = context.getResources()
                .getDimensionPixelSize(R.dimen.header_subtitle_padding);
        mActivityStarter = activityStarter;
        mConfigurationController = configurationController;

        mLayoutTransition = new LayoutTransition();
        mLayoutTransition.setStagger(LayoutTransition.CHANGE_APPEARING, DEFAULT_ANIM_DURATION / 2);
        mLayoutTransition.setDuration(LayoutTransition.APPEARING, DEFAULT_ANIM_DURATION);
        mLayoutTransition.setDuration(LayoutTransition.DISAPPEARING, DEFAULT_ANIM_DURATION / 2);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        mLayoutTransition.setInterpolator(LayoutTransition.APPEARING,
                Interpolators.FAST_OUT_SLOW_IN);
        mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING, Interpolators.ALPHA_OUT);
        mLayoutTransition.setAnimateParentHierarchy(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitle = findViewById(R.id.title);
        mRowContainer = findViewById(R.id.row_maincenter);
        mRow = findViewById(R.id.row);
        mTextColor = Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor);
        mIconSize = mRowTextSize;
        mIconSizeWithHeader = (int) mContext.getResources().getDimension(R.dimen.header_icon_size);
        mRowTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.lock_date_font_size_18);
        mRowWithHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_row_font_size);
        mTitle.setOnClickListener(this);
    }

    public View getTitleView() {
        return mTitle;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mDisplayId = getDisplay().getDisplayId();
        // Make sure we always have the most current slice
        mLiveData.observeForever(this);
        mConfigurationController.addCallback(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // TODO(b/117344873) Remove below work around after this issue be fixed.
        if (mDisplayId == DEFAULT_DISPLAY) {
            mLiveData.removeObserver(this);
        }
        mConfigurationController.removeCallback(this);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        setLayoutTransition(null);
    }

    public void setRowGravity(int gravity) {
        mRow.setGravity(gravity);
    }

    public void setRowPadding(int left, int top, int right, int bottom) {
        mRow.setPadding(left, top, right, bottom);
    }

    /**
     * Returns whether the current visible slice has a title/header.
     */
    public boolean hasHeader() {
        return mHasHeader;
    }

    private void showSlice() {
        Trace.beginSection("KeyguardSliceView#showSlice");
        if (mSlice == null) {
            mTitle.setVisibility(GONE);
            mRow.setVisibility(GONE);
            mHasHeader = false;
            if (mContentChangeListener != null) {
                mContentChangeListener.run();
            }
            Trace.endSection();
            return;
        }
        mClickActions.clear();

        ListContent lc = new ListContent(getContext(), mSlice);
        SliceContent headerContent = lc.getHeader();
        mHasHeader = headerContent != null && !headerContent.getSliceItem().hasHint(HINT_LIST_ITEM);
        List<SliceContent> subItems = new ArrayList<>();
        for (int i = 0; i < lc.getRowItems().size(); i++) {
            SliceContent subItem = lc.getRowItems().get(i);
            String itemUri = subItem.getSliceItem().getSlice().getUri().toString();
            // Filter out the action row
            if (!KeyguardSliceProvider.KEYGUARD_ACTION_URI.equals(itemUri)) {
                subItems.add(subItem);
            }
        }
        if (!mHasHeader) {
            mTitle.setVisibility(GONE);
        } else {
            mTitle.setVisibility(VISIBLE);

            RowContent header = lc.getHeader();
            SliceItem mainTitle = header.getTitleItem();
            CharSequence title = mainTitle != null ? mainTitle.getText() : null;
            mTitle.setText(title);
            setFontStyle(mTitle, mLockDateFontStyle);
            if (header.getPrimaryAction() != null
                    && header.getPrimaryAction().getAction() != null) {
                mClickActions.put(mTitle, header.getPrimaryAction().getAction());
            }
        }

        final int subItemsCount = subItems.size();
        final int blendedColor = getTextColor();
        final int startIndex = mHasHeader ? 1 : 0; // First item is header; skip it
        mRow.setVisibility(subItemsCount > 0 ? VISIBLE : GONE);

        for (int i = startIndex; i < subItemsCount; i++) {
            RowContent rc = (RowContent) subItems.get(i);
            SliceItem item = rc.getSliceItem();
            final Uri itemTag = item.getSlice().getUri();
            final boolean isWeatherSlice = itemTag.toString().equals(KeyguardSliceProvider.KEYGUARD_WEATHER_URI);
            // Try to reuse the view if already exists in the layout
            KeyguardSliceButton button = mRow.findViewWithTag(itemTag);
            if (button == null) {
                button = new KeyguardSliceButton(mContext);
                button.setShouldTintDrawable(!isWeatherSlice);
                button.setTextColor(blendedColor);
                button.setTag(itemTag);
                final int viewIndex = i - (mHasHeader ? 1 : 0);
                mRow.addView(button, viewIndex);
            } else {
                button.setShouldTintDrawable(!isWeatherSlice);
            }

            PendingIntent pendingIntent = null;
            if (rc.getPrimaryAction() != null) {
                pendingIntent = rc.getPrimaryAction().getAction();
            }
            mClickActions.put(button, pendingIntent);

            final SliceItem titleItem = rc.getTitleItem();
            button.setText(titleItem == null ? null : titleItem.getText());
            setFontStyle(button, mLockDateFontStyle);
            button.setContentDescription(rc.getContentDescription());
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mHasHeader ? mRowWithHeaderTextSize : mRowTextSize);

            Drawable iconDrawable = null;
            SliceItem icon = SliceQuery.find(item.getSlice(),
                    android.app.slice.SliceItem.FORMAT_IMAGE);
            if (icon != null) {
                final int iconSize = mHasHeader ? mIconSizeWithHeader : mIconSize;
                iconDrawable = icon.getIcon().loadDrawable(mContext);
                if (iconDrawable != null) {
                    final int width = (int) (iconDrawable.getIntrinsicWidth()
                        / (float) iconDrawable.getIntrinsicHeight() * (isWeatherSlice ? mWeatherIconSize : iconSize));
                    iconDrawable.setBounds(0, 0, Math.max(width, 1), (isWeatherSlice ? mWeatherIconSize : iconSize));
                }
            }
            button.setCompoundDrawables(iconDrawable, null, null, null);
            button.setOnClickListener(this);
            button.setClickable(pendingIntent != null);
        }

        // Removing old views
        for (int i = 0; i < mRow.getChildCount(); i++) {
            View child = mRow.getChildAt(i);
            if (!mClickActions.containsKey(child)) {
                mRow.removeView(child);
                i--;
            }
        }

        if (mContentChangeListener != null) {
            mContentChangeListener.run();
        }
        Trace.endSection();
    }

    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        mRow.setDarkAmount(darkAmount);
        updateTextColors();
    }

    private void updateTextColors() {
        final int blendedColor = getTextColor();
        mTitle.setTextColor(blendedColor);
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof Button) {
                ((Button) v).setTextColor(blendedColor);
            }
        }
    }

    public void setViewsTextStyles(float textSp, boolean textAllCaps) {
        int childCount = mRow.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = mRow.getChildAt(i);
            if (v instanceof Button) {
                ((Button) v).setLetterSpacing(textSp);
                ((Button) v).setAllCaps(textAllCaps);
            }
        }
    }

    public void setViewBackground(Drawable drawRes) {
        setViewBackground(drawRes, 255);
    }

    public void setViewBackground(Drawable drawRes, int bgAlpha) {
        mRow.setBackground(drawRes);
        mRow.getBackground().setAlpha(bgAlpha);
    }

    public void setViewBackgroundResource(int drawRes) {
        mRow.setBackgroundResource(drawRes);
    }

    public void setViewPadding(int left, int top, int right, int bottom) {
        mRow.setPadding(left,top,right,bottom);
    }

    @Override
    public void onClick(View v) {
        /*final PendingIntent action = mClickActions.get(v);
        if (action != null) {
            mActivityStarter.startPendingIntentDismissingKeyguard(action);
        }*/ //not being used by aosp actually for any slice - see KeyguardSliceProvider.addPrimaryActionLocked
    }

    /**
     * Runnable that gets invoked every time the title or the row visibility changes.
     * @param contentChangeListener The listener.
     */
    public void setContentChangeListener(Runnable contentChangeListener) {
        mContentChangeListener = contentChangeListener;
    }

    /**
     * LiveData observer lifecycle.
     * @param slice the new slice content.
     */
    @Override
    public void onChanged(Slice slice) {
        mSlice = slice;
        showSlice();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        setupUri(newValue);
    }

    /**
     * Sets the slice provider Uri.
     */
    public void setupUri(String uriString) {
        if (uriString == null) {
            uriString = KeyguardSliceProvider.KEYGUARD_SLICE_URI;
        }

        boolean wasObserving = false;
        if (mLiveData != null && mLiveData.hasActiveObservers()) {
            wasObserving = true;
            mLiveData.removeObserver(this);
        }

        mKeyguardSliceUri = Uri.parse(uriString);
        mLiveData = SliceLiveData.fromUri(mContext, mKeyguardSliceUri);

        if (wasObserving) {
            mLiveData.observeForever(this);
        }
    }

    private void setFontStyle(TextView view, int fontstyle) {
        switch (fontstyle) {
            case FONT_NORMAL:
                view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                view.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                view.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
            default:
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                view.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_DANCINGSCRIPT:
                view.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case FONT_DANCINGSCRIPT_BOLD:
                view.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case FONT_COMINGSOON:
                view.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF:
                view.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case FONT_NOTOSERIF_ITALIC:
                view.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case FONT_NOTOSERIF_BOLD:
                view.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case FONT_NOTOSERIF_BOLD_ITALIC:
                view.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;

            case FONT_ACLONICA:
                view.setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
                break;
            case FONT_AMARANTE:
                view.setTypeface(Typeface.create("amarante", Typeface.NORMAL));
                break;
            case FONT_BARIOL:
                view.setTypeface(Typeface.create("bariol", Typeface.NORMAL));
                break;
            case FONT_CAGLIOSTRO:
                view.setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
                break;
            case FONT_COOLSTORY:
                view.setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
                break;
            case FONT_LGSMARTGOTHIC:
                view.setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
                break;
            case FONT_ROSEMARY:
                view.setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
                break;
            case FONT_SONYSKETCH:
                view.setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
                break;
            case FONT_SURFER:
                view.setTypeface(Typeface.create("surfer", Typeface.NORMAL));
                break;
            case FONT_COMICSANS:
                view.setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                view.setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
                break;
            case FONT_ONEPLUSSLATE:
                view.setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                view.setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
                break;
            case FONT_COMFORTAA:
                view.setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
                break;
            case FONT_EXOTWO:
                view.setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
                break;
            case FONT_STOROPIA:
                view.setTypeface(Typeface.create("storopia", Typeface.NORMAL));
                break;
            case FONT_UBUNTU:
                view.setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
                break;
            case FONT_NOKIAPURE:
                view.setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
                break;
            case FONT_FIFA2018:
                view.setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
                break;
            case FONT_ROADRAGE:
                view.setTypeface(Typeface.create("roadrage", Typeface.NORMAL));
                break;
            case FONT_20SEVEN:
                view.setTypeface(Typeface.create("20seven", Typeface.NORMAL));
                break;
            case FONT_COCON:
                view.setTypeface(Typeface.create("cocon", Typeface.NORMAL));
                break;
            case FONT_QUANDO:
                view.setTypeface(Typeface.create("quando", Typeface.NORMAL));
                break;
            case FONT_GRANDHOTEL:
                view.setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
                break;
            case FONT_REDRESSED:
                view.setTypeface(Typeface.create("redressed", Typeface.NORMAL));
                break;
            case FONT_SANFRANSISCO:
                view.setTypeface(Typeface.create("sanfransisco", Typeface.NORMAL));
                break;
        }
    }

    public void setDateSize(int size) {
        mLockDateSize = size;

        switch (size) {
            case 10:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10);
                break;
            case 11:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11);
                break;
            case 12:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12);
                break;
            case 13:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13);
                break;
            case 14:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14);
                break;
            case 15:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15);
                break;
            case 16:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16);
                break;
            case 17:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17);
                break;
            case 18:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18);
                break;
            case 19:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19);
                break;
            case 20:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20);
                break;
            case 21:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21);
                break;
            case 22:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22);
                break;
            case 23:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23);
                break;
            case 24:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24);
                break;
            case 25:
                mRowTextSize = mContext.getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25);
                break;
        }
    }

    @VisibleForTesting
    int getTextColor() {
        return ColorUtils.blendARGB(mTextColor, Color.WHITE, mDarkAmount);
    }

    @VisibleForTesting
    void setTextColor(@ColorInt int textColor) {
        mTextColor = textColor;
        updateTextColors();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        mIconSize = mRowTextSize;
        mIconSizeWithHeader = (int) mContext.getResources().getDimension(R.dimen.header_icon_size);
        mRowTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.lock_date_font_size_18);
        mRowWithHeaderTextSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.header_row_font_size);
        mWeatherIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.weather_icon_size);
    }

    public void setFontStyle(int fontstyle) {
        mLockDateFontStyle = fontstyle;
    }

    public void refresh() {
        Slice slice;
        Trace.beginSection("KeyguardSliceView#refresh");
        // We can optimize performance and avoid binder calls when we know that we're bound
        // to a Slice on the same process.
        if (KeyguardSliceProvider.KEYGUARD_SLICE_URI.equals(mKeyguardSliceUri.toString())) {
            KeyguardSliceProvider instance = KeyguardSliceProviderGoogle.getAttachedInstance();
            if (instance != null) {
                slice = instance.onBindSlice(mKeyguardSliceUri);
            } else {
                Log.w(TAG, "Keyguard slice not bound yet?");
                slice = null;
            }
        } else {
            slice = SliceViewManager.getInstance(getContext()).bindSlice(mKeyguardSliceUri);
        }
        onChanged(slice);
        Trace.endSection();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardSliceView:");
        pw.println("  mClickActions: " + mClickActions);
        pw.println("  mTitle: " + (mTitle == null ? "null" : mTitle.getVisibility() == VISIBLE));
        pw.println("  mRow: " + (mRow == null ? "null" : mRow.getVisibility() == VISIBLE));
        pw.println("  mTextColor: " + Integer.toHexString(mTextColor));
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mSlice: " + mSlice);
        pw.println("  mHasHeader: " + mHasHeader);
    }

    public static class Row extends LinearLayout {

        /**
         * This view is visible in AOD, which means that the device will sleep if we
         * don't hold a wake lock. We want to enter doze only after all views have reached
         * their desired positions.
         */
        private final Animation.AnimationListener mKeepAwakeListener;
        private LayoutTransition mLayoutTransition;
        private float mDarkAmount;

        public Row(Context context) {
            this(context, null);
        }

        public Row(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public Row(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public Row(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
            mKeepAwakeListener = new KeepAwakeAnimationListener(mContext);
        }

        @Override
        protected void onFinishInflate() {
            mLayoutTransition = new LayoutTransition();
            mLayoutTransition.setDuration(DEFAULT_ANIM_DURATION);

            PropertyValuesHolder left = PropertyValuesHolder.ofInt("left", 0, 1);
            PropertyValuesHolder right = PropertyValuesHolder.ofInt("right", 0, 1);
            ObjectAnimator changeAnimator = ObjectAnimator.ofPropertyValuesHolder((Object) null,
                    left, right);
            mLayoutTransition.setAnimator(LayoutTransition.CHANGE_APPEARING, changeAnimator);
            mLayoutTransition.setAnimator(LayoutTransition.CHANGE_DISAPPEARING, changeAnimator);
            mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_APPEARING,
                    Interpolators.ACCELERATE_DECELERATE);
            mLayoutTransition.setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
                    Interpolators.ACCELERATE_DECELERATE);
            mLayoutTransition.setStartDelay(LayoutTransition.CHANGE_APPEARING,
                    DEFAULT_ANIM_DURATION);
            mLayoutTransition.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING,
                    DEFAULT_ANIM_DURATION);

            ObjectAnimator appearAnimator = ObjectAnimator.ofFloat(null, "alpha", 0f, 1f);
            mLayoutTransition.setAnimator(LayoutTransition.APPEARING, appearAnimator);
            mLayoutTransition.setInterpolator(LayoutTransition.APPEARING, Interpolators.ALPHA_IN);

            ObjectAnimator disappearAnimator = ObjectAnimator.ofFloat(null, "alpha", 1f, 0f);
            mLayoutTransition.setInterpolator(LayoutTransition.DISAPPEARING,
                    Interpolators.ALPHA_OUT);
            mLayoutTransition.setDuration(LayoutTransition.DISAPPEARING, DEFAULT_ANIM_DURATION / 4);
            mLayoutTransition.setAnimator(LayoutTransition.DISAPPEARING, disappearAnimator);

            mLayoutTransition.setAnimateParentHierarchy(false);
        }

        @Override
        public void onVisibilityAggregated(boolean isVisible) {
            super.onVisibilityAggregated(isVisible);
            setLayoutTransition(null);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child instanceof KeyguardSliceButton && childCount > 3) {
                    ((KeyguardSliceButton) child).setMaxWidth(width / childCount);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void setDarkAmount(float darkAmount) {
            boolean isAwake = darkAmount != 0;
            boolean wasAwake = mDarkAmount != 0;
            if (isAwake == wasAwake) {
                return;
            }
            mDarkAmount = darkAmount;
            setLayoutAnimationListener(isAwake ? null : mKeepAwakeListener);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /**
     * Representation of an item that appears under the clock on main keyguard message.
     */
    @VisibleForTesting
    static class KeyguardSliceButton extends Button implements
            ConfigurationController.ConfigurationListener {

        @StyleRes
        private static int sStyleId = R.style.TextAppearance_Keyguard_Secondary;

        private boolean shouldTintDrawable = true;
        public KeyguardSliceButton(Context context) {
            super(context, null /* attrs */, 0 /* styleAttr */, sStyleId);
            onDensityOrFontScaleChanged();
            setEllipsize(TruncateAt.END);
        }

        public void setShouldTintDrawable(boolean shouldTintDrawable){
            this.shouldTintDrawable = shouldTintDrawable;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Dependency.get(ConfigurationController.class).addCallback(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Dependency.get(ConfigurationController.class).removeCallback(this);
        }

        @Override
        public void onDensityOrFontScaleChanged() {
            updatePadding();
        }

        @Override
        public void onOverlayChanged() {
            setTextAppearance(sStyleId);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            updatePadding();
        }

        private void updatePadding() {
            boolean hasText = !TextUtils.isEmpty(getText());
            int horizontalPadding = (int) getContext().getResources()
                    .getDimension(R.dimen.widget_horizontal_padding) / 2;
            setPadding(horizontalPadding, 0, horizontalPadding * (hasText ? 1 : -1), 0);
            setCompoundDrawablePadding((int) mContext.getResources()
                    .getDimension(R.dimen.widget_icon_padding));
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(color);
            updateDrawableColors();
        }

        @Override
        public void setCompoundDrawables(Drawable left, Drawable top, Drawable right,
                Drawable bottom) {
            super.setCompoundDrawables(left, top, right, bottom);
            updateDrawableColors();
            updatePadding();
        }

        private void updateDrawableColors() {
            if (!shouldTintDrawable){
                return;
            }
            final int color = getCurrentTextColor();
            for (Drawable drawable : getCompoundDrawables()) {
                if (drawable != null) {
                    drawable.setTint(color);
                }
            }
        }
    }
}
