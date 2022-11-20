/*
 * Copyright (C) 2022 ReloadedOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.bliss;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GamesPropsUtils extends PixelPropsUtils {

    private static final String TAG = "GamesPropsUtils";
    private static final boolean DEBUG = true;
    private static final boolean DEBUG_PACKAGES = false;

    private static final String MODEL_ROG1 = "ASUS_Z01QD";
    private static final String MODEL_XP5 = "SO-52A";
    private static final String MODEL_OP8P = "IN2020";
    private static final String MODEL_OP9P = "LE2123";
    private static final String MODEL_MI11 = "M2102K1G";

    private static final Set<String> sRog1Packages = Set.of(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.madfingergames.legends"
    );

    private static final Set<String> sXp5Packages = Set.of(
        "com.activision.callofduty.shooter",
        "com.tencent.tmgp.kr.codm",
        "com.garena.game.codm",
        "com.vng.codmvn"
    );

    private static final Set<String> sOp8pPackages = Set.of(
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.pubg.krmobile",
        "com.vng.pubgmobile",
        "com.rekoo.pubgm",
        "com.tencent.tmgp.pubgmhd",
        "com.riotgames.league.wildrift",
        "com.riotgames.league.wildrifttw",
        "com.riotgames.league.wildriftvn",
        "com.netease.lztgglobal"
    );

    private static final Set<String> sOp9pPackages = Set.of(
        "com.epicgames.fortnite",
        "com.epicgames.portal"
    );

    private static final Set<String> sMi11Packages = Set.of(
        "com.ea.gp.apexlegendsmobilefps",
        "com.levelinfinite.hotta.gp",
        "com.mobile.legends",
        "com.tencent.tmgp.sgame"
    );

    private static final Map<String, String> sPackagesModelMap = new HashMap<String, String>();

    static {
        Map.of(
            sRog1Packages, MODEL_ROG1,
            sXp5Packages,  MODEL_XP5,
            sOp8pPackages, MODEL_OP8P,
            sOp9pPackages, MODEL_OP9P,
            sMi11Packages, MODEL_MI11
        ).forEach((k, v) -> k.forEach(p -> sPackagesModelMap.put(p, v)));
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();

        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        if (sPackagesModelMap.containsKey(packageName) && Secure.getInt(
                context.getContentResolver(), Secure.GAMES_DEVICE_SPOOF, 0) == 1) {
            String model = sPackagesModelMap.get(packageName);
            dlog("Spoofing model to " + model + " for package " + packageName);
            setPropValue("MODEL", model);
        }
    }
}
