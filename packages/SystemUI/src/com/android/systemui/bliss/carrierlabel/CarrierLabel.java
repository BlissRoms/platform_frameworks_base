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
    private static final int FONT_BIGNOODLE_ITALIC = 51;
    private static final int FONT_BIGNOODLE_REGULAR = 52;
    private static final int FONT_HANKEN = 53;
    private static final int FONT_MITTELSCHRIFT = 54;
    private static final int FONT_REEMKUFI = 55;
    private static final int FONT_COMIC_NEUE_BOLD = 56;
    private static final int FONT_COMIC_NEUE = 57;
    private static final int FONT_EXO2_REGULAR = 58;
    private static final int FONT_EXO2_SEMIBOLD = 59;
    private static final int FONT_FINLANDICA = 60;
    private static final int FONT_GOODLIGHT = 61;
    private static final int FONT_GRAVITY_REGULAR = 62;
    private static final int FONT_INTER_REGULAR = 63;
    private static final int FONT_INTER_MEDIUM_ITALIC = 64;
    private static final int FONT_LEAGUE_MONO_N_REGULAR = 65;
    private static final int FONT_LEAGUE_MONO_N_MEDIUM = 66;
    private static final int FONT_LEAGUE_MONO_N_BOLD = 67;
    private static final int FONT_LEAGUE_MONO_N_SEMIBOLD = 68;
    private static final int FONT_LEONSANS_REGULAR = 69;
    private static final int FONT_MESCLA_REGULAR = 70;
    private static final int FONT_ODIBEE_SANS = 71;
    private static final int FONT_PANAMERICANA = 72;
    private static final int FONT_PT_SANS = 73;
    private static final int FONT_PT_MONO = 74;
    private static final int FONT_ROUNDED_GOTHIC_NARROW = 75;
    private static final int FONT_ROUNDED_GOTHIC_NARROW_HALF_ITALIC = 76;
    private static final int FONT_SOFIA_SANS_REGULAR = 77;
    private static final int FONT_SOFIA_SANS_MEDIUM = 78;
    private static final int FONT_SOFIA_SEMICONDENSED_REGULAR = 79;
    private static final int FONT_SOFIA_SEMICONDENSED_MEDIUM = 80;
    private static final int FONT_SAMSUNG = 81;
    private static final int FONT_MEXCELLENT = 82;
    private static final int FONT_BURNSTOWN = 83;
    private static final int FONT_DUMBLEDOR = 84;
    private static final int FONT_PHANTOMBOLD = 85;
    private static final int FONT_SNOWSTORM = 86;
    private static final int FONT_NEONEON = 87;
    private static final int FONT_CIRCULARSTD = 88;

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
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mTintColor = DarkIconDispatcher.getTint(area, this, tint);
        if (mCarrierColor == 0xFFFFFFFF) {
            setTextColor(mTintColor);
        } else {
            setTextColor(mCarrierColor);
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
                        TunerService.parseInteger(newValue, 36);
                setCarrierLabel();
                break;
            case STATUS_BAR_CARRIER_COLOR:
                mCarrierColor =
                        TunerService.parseInteger(newValue, 0xFFFFFFFF);
                setCarrierLabel();
                break;
            default:
                break;
        }
    }

    public void getFontStyle(int font) {
        switch (font) {
            case FONT_NORMAL:
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
            default:
                setTypeface(Typeface.create("oneplusslate", Typeface.NORMAL));
                break;
            case FONT_SAMSUNGONE:
                setTypeface(Typeface.create("samsungone", Typeface.NORMAL));
                break;
            case FONT_COMFORTAA:
                setTypeface(Typeface.create("comfortaa", Typeface.NORMAL));
                break;
            case FONT_EXOTWO:
                setTypeface(Typeface.create("exotwo", Typeface.NORMAL));
                break;
            case FONT_STOROPIA:
                setTypeface(Typeface.create("storopia", Typeface.NORMAL));
                break;
            case FONT_UBUNTU:
                setTypeface(Typeface.create("ubuntu", Typeface.NORMAL));
                break;
            case FONT_NOKIAPURE:
                setTypeface(Typeface.create("nokiapure", Typeface.NORMAL));
                break;
            case FONT_FIFA2018:
                setTypeface(Typeface.create("fifa2018", Typeface.NORMAL));
                break;
            case FONT_ROADRAGE:
                setTypeface(Typeface.create("roadrage", Typeface.NORMAL));
                break;
            case FONT_20SEVEN:
                setTypeface(Typeface.create("20seven", Typeface.NORMAL));
                break;
            case FONT_COCON:
                setTypeface(Typeface.create("cocon", Typeface.NORMAL));
                break;
            case FONT_QUANDO:
                setTypeface(Typeface.create("quando", Typeface.NORMAL));
                break;
            case FONT_GRANDHOTEL:
                setTypeface(Typeface.create("grandhotel", Typeface.NORMAL));
                break;
            case FONT_REDRESSED:
                setTypeface(Typeface.create("redressed", Typeface.NORMAL));
                break;
            case FONT_SANFRANSISCO:
                setTypeface(Typeface.create("sanfransisco", Typeface.NORMAL));
                break;
            case FONT_BIGNOODLE_ITALIC:
                setTypeface(Typeface.create("bignoodle-italic", Typeface.NORMAL));
                break;
            case FONT_BIGNOODLE_REGULAR:
                setTypeface(Typeface.create("bignoodle-regular", Typeface.NORMAL));
                break;
            case FONT_HANKEN:
                setTypeface(Typeface.create("hanken", Typeface.NORMAL));
                break;
            case FONT_MITTELSCHRIFT:
                setTypeface(Typeface.create("mittelschrift", Typeface.NORMAL));
                break;
            case FONT_REEMKUFI:
                setTypeface(Typeface.create("reemkufi", Typeface.NORMAL));
                break;
            case FONT_COMIC_NEUE_BOLD:
                setTypeface(Typeface.create("comic-neue-bold", Typeface.NORMAL));
                break;
            case FONT_COMIC_NEUE:
                setTypeface(Typeface.create("comic-neue", Typeface.NORMAL));
                break;
            case FONT_EXO2_REGULAR:
                setTypeface(Typeface.create("exo2-regular", Typeface.NORMAL));
                break;
            case FONT_EXO2_SEMIBOLD:
                setTypeface(Typeface.create("exo2-semibold", Typeface.NORMAL));
                break;
            case FONT_FINLANDICA:
                setTypeface(Typeface.create("finlandica", Typeface.NORMAL));
                break;
            case FONT_GOODLIGHT:
                setTypeface(Typeface.create("goodlight", Typeface.NORMAL));
                break;
            case FONT_GRAVITY_REGULAR:
                setTypeface(Typeface.create("gravity-regular", Typeface.NORMAL));
                break;
            case FONT_INTER_REGULAR:
                setTypeface(Typeface.create("inter-regular", Typeface.NORMAL));
                break;
            case FONT_INTER_MEDIUM_ITALIC:
                setTypeface(Typeface.create("inter-medium-italic", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_REGULAR:
                setTypeface(Typeface.create("league-mono-n-regular", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_MEDIUM:
                setTypeface(Typeface.create("league-mono-n-medium", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_BOLD:
                setTypeface(Typeface.create("league-mono-n-bold", Typeface.NORMAL));
                break;
            case FONT_LEAGUE_MONO_N_SEMIBOLD:
                setTypeface(Typeface.create("league-mono-n-semibold", Typeface.NORMAL));
                break;
            case FONT_LEONSANS_REGULAR:
                setTypeface(Typeface.create("leonsans-regular", Typeface.NORMAL));
                break;
            case FONT_MESCLA_REGULAR:
                setTypeface(Typeface.create("mescla-regular", Typeface.NORMAL));
                break;
            case FONT_ODIBEE_SANS:
                setTypeface(Typeface.create("odibee-sans", Typeface.NORMAL));
                break;
            case FONT_PANAMERICANA:
                setTypeface(Typeface.create("panamericana", Typeface.NORMAL));
                break;
            case FONT_PT_SANS:
                setTypeface(Typeface.create("pt-sans", Typeface.NORMAL));
                break;
            case FONT_PT_MONO:
                setTypeface(Typeface.create("pt-mono", Typeface.NORMAL));
                break;
            case FONT_ROUNDED_GOTHIC_NARROW:
                setTypeface(Typeface.create("rounded-gothic-narrow", Typeface.NORMAL));
                break;
            case FONT_ROUNDED_GOTHIC_NARROW_HALF_ITALIC:
                setTypeface(Typeface.create("rounded-gothic-narrow-half-italic", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SANS_REGULAR:
                setTypeface(Typeface.create("sofia-sans-regular", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SANS_MEDIUM:
                setTypeface(Typeface.create("sofia-sans-medium", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SEMICONDENSED_REGULAR:
                setTypeface(Typeface.create("sofia-semicondensed-regular", Typeface.NORMAL));
                break;
            case FONT_SOFIA_SEMICONDENSED_MEDIUM:
                setTypeface(Typeface.create("sofia-semicondensed-medium", Typeface.NORMAL));
                break;
            case FONT_SAMSUNG:
                setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case FONT_MEXCELLENT:
                setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case FONT_BURNSTOWN:
                setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case FONT_DUMBLEDOR:
                setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case FONT_PHANTOMBOLD:
                setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case FONT_SNOWSTORM:
                setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case FONT_NEONEON:
                setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case FONT_CIRCULARSTD:
                setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
        }
    }

    private void setCarrierLabel() {
        if (mShowCarrierLabel == 2 || mShowCarrierLabel == 3) {
            setVisibility(View.VISIBLE);
            getFontStyle(mCarrierLabelFontStyle);
            if (mCarrierColor == 0xFFFFFFFF) {
                setTextColor(mTintColor);
            } else {
                setTextColor(mCarrierColor);
            }
        } else {
            setVisibility(View.GONE);
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
}
