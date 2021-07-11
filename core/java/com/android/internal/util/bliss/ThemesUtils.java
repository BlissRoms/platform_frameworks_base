/*
 * Copyright (C) 2014-2021 BlissRoms Project
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

package com.android.internal.util.bliss;

import static android.os.UserHandle.USER_SYSTEM;

import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

public class ThemesUtils {

    public static final String[] QS_TILE_THEMES = {
            "com.android.systemui.qstile.default",
            "com.android.systemui.qstile.circletrim",
            "com.android.systemui.qstile.dualtonecircletrim",
            "com.android.systemui.qstile.squircletrim",
		    "com.android.systemui.qstile.wavey", // 4
		    "com.android.systemui.qstile.pokesign", // 5
		    "com.android.systemui.qstile.ninja", // 6
		    "com.android.systemui.qstile.dottedcircle", // 7
		    "com.android.systemui.qstile.attemptmountain", // 8
		    "com.android.systemui.qstile.squaremedo", // 9
		    "com.android.systemui.qstile.inkdrop", // 10
		    "com.android.systemui.qstile.cookie", // 11
		    "com.android.systemui.qstile.circleoutline", //12
		    "com.android.systemui.qstile.neonlike", // 13
		    "com.android.systemui.qstile.oos", // 14
		    "com.android.systemui.qstile.triangles", // 15
		    "com.android.systemui.qstile.divided", // 16
		    "com.android.systemui.qstile.cosmos", // 17
		    "com.android.systemui.qstile.squircle", // 18
		    "com.android.systemui.qstile.teardrop", // 19
		    "com.android.systemui.qstile.deletround", //20
		    "com.android.systemui.qstile.hexagon", // 21
		    "com.android.systemui.qstile.diamond", // 22
		    "com.android.systemui.qstile.star", // 23
		    "com.android.systemui.qstile.gear", // 24
		    "com.android.systemui.qstile.badge", // 25
		    "com.android.systemui.qstile.badgetwo", // 26
    };

    public static final String[] STATUSBAR_HEIGHT = {
            "com.gnonymous.gvisualmod.sbh_m", // 1
            "com.gnonymous.gvisualmod.sbh_l", // 2
            "com.gnonymous.gvisualmod.sbh_xl", // 3
    };

    public static final String[] UI_RADIUS = {
            "com.gnonymous.gvisualmod.urm_r", // 1
            "com.gnonymous.gvisualmod.urm_m", // 2
            "com.gnonymous.gvisualmod.urm_l", // 3
    };

    public static final String NAVBAR_COLOR_PURP = "com.gnonymous.gvisualmod.pgm_purp";

    public static final String NAVBAR_COLOR_ORCD = "com.gnonymous.gvisualmod.pgm_orcd";

    public static final String NAVBAR_COLOR_OPRD = "com.gnonymous.gvisualmod.pgm_oprd";

    public static final String NAVBAR_COLOR_ACCENT = "com.gnonymous.gvisualmod.pgm_accent";

    public static final String[] STOCK = {
            "com.android.theme.stock.system",
    };

    public static final String[] SOLARIZED_DARK = {
            "com.android.theme.solarizeddark.system",
            "com.android.theme.solarizeddark.systemui",
    };

    public static final String[] PITCH_BLACK = {
            "com.android.theme.pitchblack.system",
            "com.android.theme.pitchblack.systemui",
    };

    public static final String[] DARK_GREY = {
            "com.android.theme.darkgrey.system",
            "com.android.theme.darkgrey.systemui",
    };

    public static final String[] MATERIAL_OCEAN = {
            "com.android.theme.materialocean.system",
            "com.android.theme.materialocean.systemui",
    };

    public static final String[] XTENDED_CLEAR = {
            "com.android.theme.xtendedclear.system",
            "com.android.theme.xtendedclear.systemui",
    };

    // Switch themes
    private static final String[] SWITCH_THEMES = {
        "com.android.system.switch.oneplus", // 0
        "com.android.system.switch.aosp", // 1
        "com.android.system.switch.narrow", // 2
        "com.android.system.switch.contained", // 3
        "com.android.system.switch.telegram", // 4
        "com.android.system.switch.md2", // 5
        "com.android.system.switch.retro", // 6
    };

    public static void updateSwitchStyle(IOverlayManager om, int userId, int switchStyle) {
        if (switchStyle == 1) {
            stockSwitchStyle(om, userId);
        } else {
            try {
                om.setEnabled(SWITCH_THEMES[switchStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change switch theme", e);
            }
        }
    }

    public static void stockSwitchStyle(IOverlayManager om, int userId) {
        for (int i = 0; i < SWITCH_THEMES.length; i++) {
            String switchtheme = SWITCH_THEMES[i];
            try {
                om.setEnabled(switchtheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
