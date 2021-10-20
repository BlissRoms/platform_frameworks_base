/*
 * Copyright (C) 2020 The Pixel Experience Project
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
package com.android.internal.util.custom;

import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PixelPropsUtils {

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Map<String, Object> propsToChange;

    private static final String[] packagesToChange = {
            "com.android.vending",
            "com.breel.wallpapers20",
            "com.google.android.configupdater",
            "com.google.android.apps.customization.pixel",
            "com.google.android.apps.fitness",
            "com.google.android.apps.gcs",
            "com.google.android.apps.googleassistant",
            "com.google.android.apps.maps",
            "com.google.android.apps.messaging",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.pixelmigrate",
            "com.google.android.apps.recorder",
            "com.google.android.apps.safetyhub",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.turbo",
            "com.google.android.apps.turboadapter",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.apps.wellbeing",
            "com.google.android.as",
            "com.google.android.dialer",
            "com.google.android.ext.services",
            "com.google.android.gms",
            "com.google.android.deskclock",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.podcasts",
            "com.google.android.apps.tachyon",
            "com.google.android.apps.nbu.files",
            "com.google.android.android.contacts",
            "com.google.android.android.apps.messaging",
            "com.google.android.gms.location.history",
            "com.google.android.gsf",
            "com.google.android.inputmethod.latin",
            "com.google.android.soundpicker",
            "com.google.intelligence.sense",
            "com.google.pixel.dynamicwallpapers",
            "com.google.pixel.livewallpaper",
            "com.google.android.apps.photos",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gm"
    };

    static {
        propsToChange = new HashMap<>();
        propsToChange.put("BRAND", "google");
        propsToChange.put("MANUFACTURER", "Google");
        propsToChange.put("DEVICE", "redfin");
        propsToChange.put("PRODUCT", "redfin");
        propsToChange.put("MODEL", "Pixel 5");
        propsToChange.put("FINGERPRINT", "google/redfin/redfin:12/SPB5.210812.002/7671067:user/release-keys");
        propsToChange.put("ID", "SPB5.210812.002");
        propsToChange.put("SECURITY_PATCH", "2021-09-05");        
        propsToChange.put("IS_DEBUGGABLE", false);
        propsToChange.put("IS_ENG", false);
        propsToChange.put("IS_USERDEBUG", false);
        propsToChange.put("IS_USER", true);
        propsToChange.put("TYPE", "user");
    }

    public static void setProps(String packageName) {
        if (packageName == null){
            return;
        }
        if (Arrays.asList(packagesToChange).contains(packageName)){
            if (DEBUG){
                Log.d(TAG, "Defining props for: " + packageName);
            }
            for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                // Don't set model if gms
                if (packageName.equals("com.google.android.gms") && key.equals("MODEL")){
                    continue;
                }
                setPropValue(key, value);
            }
        }
    }

    private static void setPropValue(String key, Object value){
        try {
            if (DEBUG){
                Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            }
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }
}
