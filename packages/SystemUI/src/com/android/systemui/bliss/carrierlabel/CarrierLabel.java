/*
 * Copyright (C) 2014-2015 The MoKee OpenSource Project
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

package com.android.systemui.bliss.carrierlabel;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.internal.util.bliss.BlissUtils;
import com.android.internal.telephony.TelephonyIntents;

import com.android.systemui.Dependency;
import com.android.systemui.bliss.carrierlabel.SpnOverride;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.tuner.TunerService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import com.android.systemui.R;

public class CarrierLabel extends TextView implements DarkReceiver, TunerService.Tunable {

    private Context mContext;
    private boolean mAttached;
    private static boolean isCN;

    private int mShowCarrierLabel;
    private int mCarrierLabelFontStyle = FONT_NORMAL;
    private int mCarrierColor = 0xffffffff;
    private int mTintColor = Color.WHITE;

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

    private static final String STATUS_BAR_SHOW_CARRIER =
            "system:" + Settings.System.STATUS_BAR_SHOW_CARRIER;
    private static final String STATUS_BAR_CARRIER_FONT_STYLE =
            "system:" + Settings.System.STATUS_BAR_CARRIER_FONT_STYLE;
    private static final String STATUS_BAR_CARRIER_COLOR =
            "system:" + Settings.System.STATUS_BAR_CARRIER_COLOR;

    public CarrierLabel(Context context) {
        this(context, null);
    }

    public CarrierLabel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        updateNetworkName(true, null, false, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
            filter.addAction(Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
            Dependency.get(TunerService.class).addTunable(this,
                STATUS_BAR_SHOW_CARRIER,
                STATUS_BAR_CARRIER_FONT_STYLE,
                STATUS_BAR_CARRIER_COLOR);

        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Dependency.get(DarkIconDispatcher.class).removeDarkReceiver(this);
        if (mAttached) {
            Dependency.get(TunerService.class).removeTunable(this);
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_SHOW_CARRIER:
                mShowCarrierLabel =
                        TunerService.parseInteger(newValue, 0);
                setCarrierLabel();
                break;
            case STATUS_BAR_CARRIER_FONT_STYLE:
                mCarrierLabelFontStyle =
                        TunerService.parseInteger(newValue, 14);
                setCarrierLabel();
                break;
            case STATUS_BAR_CARRIER_COLOR:
                mCarrierColor =
                        TunerService.parseInteger(newValue, 0xFFFFFFFF);
                updateColor();
                break;
            default:
                break;
        }
    }

    public void getFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
            default:
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case FONT_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case FONT_BOLD:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case FONT_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case FONT_LIGHT:
                setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case FONT_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case FONT_THIN:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case FONT_THIN_ITALIC:
                setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case FONT_CONDENSED:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_LIGHT:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case FONT_CONDENSED_LIGHT_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case FONT_CONDENSED_BOLD:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case FONT_CONDENSED_BOLD_ITALIC:
                setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case FONT_MEDIUM:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case FONT_MEDIUM_ITALIC:
                setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case FONT_BLACK:
                setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case FONT_BLACK_ITALIC:
                setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case FONT_ACLONICA:
                setTypeface(Typeface.create("aclonica", Typeface.NORMAL));
                break;
            case FONT_AMARANTE:
                setTypeface(Typeface.create("amarante", Typeface.NORMAL));
                break;
            case FONT_BARIOL:
                setTypeface(Typeface.create("bariol", Typeface.NORMAL));
                break;
            case FONT_CAGLIOSTRO:
                setTypeface(Typeface.create("cagliostro", Typeface.NORMAL));
                break;
            case FONT_COOLSTORY:
                setTypeface(Typeface.create("coolstory", Typeface.NORMAL));
                break;
            case FONT_LGSMARTGOTHIC:
                setTypeface(Typeface.create("lgsmartgothic", Typeface.NORMAL));
                break;
            case FONT_ROSEMARY:
                setTypeface(Typeface.create("rosemary", Typeface.NORMAL));
                break;
            case FONT_SONYSKETCH:
                setTypeface(Typeface.create("sonysketch", Typeface.NORMAL));
                break;
            case FONT_SURFER:
                setTypeface(Typeface.create("surfer", Typeface.NORMAL));
                break;
            case FONT_COMICSANS:
                setTypeface(Typeface.create("comicsans", Typeface.NORMAL));
                break;
            case FONT_GOOGLESANS:
                setTypeface(Typeface.create("googlesans", Typeface.NORMAL));
                break;
            case FONT_ONEPLUSSLATE:
                setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
                break;
        }
    }

    private void setCarrierLabel() {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            setVisibility(View.VISIBLE);
            getFontStyle(mCarrierLabelFontStyle);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.SPN_STRINGS_UPDATED_ACTION.equals(action)
                    || Intent.ACTION_CUSTOM_CARRIER_LABEL_CHANGED.equals(action)) {
                        updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, true),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN));
                isCN = BlissUtils.isChineseLanguage();
            }
        }
    };

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        final String str;
        final boolean plmnValid = showPlmn && !TextUtils.isEmpty(plmn);
        final boolean spnValid = showSpn && !TextUtils.isEmpty(spn);
        if (spnValid) {
            str = spn;
        } else if (plmnValid) {
            str = plmn;
        } else {
            str = "";
        }
        String customCarrierLabel = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.CUSTOM_CARRIER_LABEL, UserHandle.USER_CURRENT);
        if (!TextUtils.isEmpty(customCarrierLabel)) {
            setText(customCarrierLabel);
        } else {
            setText(TextUtils.isEmpty(str) ? getOperatorName() : str);
        }
        setCarrierLabel();
    }

    private String getOperatorName() {
        String operatorName = getContext().getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (isCN) {
            String operator = telephonyManager.getNetworkOperator();
            if (TextUtils.isEmpty(operator)) {
                operator = telephonyManager.getSimOperator();
            }
            SpnOverride mSpnOverride = new SpnOverride();
            operatorName = mSpnOverride.getSpn(operator);
        } else {
            operatorName = telephonyManager.getNetworkOperatorName();
        }
        if (TextUtils.isEmpty(operatorName)) {
            operatorName = telephonyManager.getSimOperatorName();
        }
        return operatorName;
    }

    private void updateColor() {
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
        }
    }
}
