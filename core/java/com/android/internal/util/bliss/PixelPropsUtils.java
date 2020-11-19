/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2022 StatiXOS
 *               2021-2022 crDroid Android Project
 *               2019-2024 The Evolution X Project
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

import android.app.ActivityTaskManager;
import android.app.ActivityThread;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.util.bliss.BlissUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @hide
 */
public final class PixelPropsUtils {

    private static final String DISGUISE_PROPS_FOR_MUSIC_APP =
            "persist.sys.disguise_props_for_music_app";
    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_GOOGLE = "com.google";
    private static final String PACKAGE_NEXUS_LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String PACKAGE_SI = "com.google.android.settings.intelligence";
    private static final String SPOOF_PIXEL_PROPS = "persist.sys.pphooks.enable";

    private static final String PROP_HOOKS = "persist.sys.pihooks_";
    public static final String SPOOF_PIXEL_GMS = "persist.sys.pixelprops.gms";
    public static final String ENABLE_GAME_PROP_OPTIONS = "persist.sys.gameprops.enabled";

    private static final String TAG = PixelPropsUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String sDeviceModel =
            SystemProperties.get("ro.product.model", Build.MODEL);
    private static final String sDeviceFingerprint =
            SystemProperties.get("ro.product.fingerprint", Build.FINGERPRINT);

    private static final Map<String, Object> propsToChangeGeneric;
    private static final Map<String, Object> propsToChangeRecentPixel;
    private static final Map<String, Object> propsToChangePixelTablet;
    private static final Map<String, Object> propsToChangePixel5a;
    private static final Map<String, Object> propsToChangeMeizu;
    private static final Map<String, ArrayList<String>> propsToKeep;

    private static Set<String> mLauncherPkgs;
    private static Set<String> mExemptedUidPkgs;

    // Packages to Spoof as the most recent Pixel device
    private static final String[] packagesToChangeRecentPixel = {
            "com.amazon.avod.thirdpartyclient",
            "com.android.chrome",
            "com.breel.wallpapers20",
            "com.disney.disneyplus",
            "com.google.android.aicore",
            "com.google.android.apps.accessibility.magnifier",
            "com.google.android.apps.aiwallpapers",
            "com.google.android.apps.bard",
            "com.google.android.apps.customization.pixel",
            "com.google.android.apps.emojiwallpaper",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.apps.pixel.agent",
            "com.google.android.apps.pixel.creativeassistant",
            "com.google.android.apps.pixel.support",
            "com.google.android.apps.privacy.wildlife",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.wallpaper",
            "com.google.android.apps.wallpaper.pixel",
            "com.google.android.apps.weather",
            "com.google.android.gms",
            "com.google.android.googlequicksearchbox",
            "com.google.android.soundpicker",
            "com.google.android.wallpaper.effects",
            "com.google.pixel.livewallpaper",
            "com.microsoft.android.smsorganizer",
            "com.nhs.online.nhsonline",
            "com.nothing.smartcenter",
            "com.realme.link",
            "in.startv.hotstar",
            "jp.id_credit_sp2.android"
    };

    private static final String[] customGoogleCameraPackages = {
            "com.google.android.MTCL83",
            "com.google.android.UltraCVM",
            "com.google.android.apps.cameralite"
    };

    private static final String[] packagesToChangeMeizu = {
        "cmccwm.mobilemusic",
        "cn.kuwo.player",
        "com.hihonor.cloudmusic",
        "com.kugou.android.lite",
        "com.kugou.android",
        "com.meizu.media.music",
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
    };

    private static final String[] GMS_SPOOF_KEYS = {
        "BRAND", "DEVICE", "DEVICE_INITIAL_SDK_INT", "FINGERPRINT", "ID",
        "MANUFACTURER", "MODEL", "PRODUCT", "RELEASE", "SECURITY_PATCH",
        "TAGS", "TYPE"
    };

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static volatile boolean sIsGms, sIsExcluded;
    private static volatile String sProcessName;

    static {
        propsToKeep = new HashMap<>();
        propsToKeep.put(PACKAGE_SI, new ArrayList<>(Collections.singletonList("FINGERPRINT")));
        propsToChangeGeneric = new HashMap<>();
        propsToChangeGeneric.put("TYPE", "user");
        propsToChangeGeneric.put("TAGS", "release-keys");
        propsToChangeRecentPixel = new HashMap<>();
        propsToChangeRecentPixel.put("BRAND", "google");
        propsToChangeRecentPixel.put("BOARD", "mustang");
        propsToChangeRecentPixel.put("MANUFACTURER", "Google");
        propsToChangeRecentPixel.put("DEVICE", "mustang");
        propsToChangeRecentPixel.put("PRODUCT", "mustang");
        propsToChangeRecentPixel.put("HARDWARE", "mustang");
        propsToChangeRecentPixel.put("MODEL", "Pixel 10 Pro XL");
        propsToChangeRecentPixel.put("ID", "BP4A.260105.004.E1");
        propsToChangeRecentPixel.put("FINGERPRINT", "google/mustang/mustang:16/BP4A.260105.004.E1/14587043:user/release-keys");
        propsToChangePixelTablet = new HashMap<>();
        propsToChangePixelTablet.put("BRAND", "google");
        propsToChangePixelTablet.put("BOARD", "tangorpro");
        propsToChangePixelTablet.put("MANUFACTURER", "Google");
        propsToChangePixelTablet.put("DEVICE", "tangorpro");
        propsToChangePixelTablet.put("PRODUCT", "tangorpro");
        propsToChangePixelTablet.put("HARDWARE", "tangorpro");
        propsToChangePixelTablet.put("MODEL", "Pixel Tablet");
        propsToChangePixelTablet.put("ID", "BP4A.260105.004.E1");
        propsToChangePixelTablet.put("FINGERPRINT", "google/tangorpro/tangorpro:16/BP4A.260105.004.E1/14587043:user/release-keys");
        propsToChangePixel5a = new HashMap<>();
        propsToChangePixel5a.put("BRAND", "google");
        propsToChangePixel5a.put("MANUFACTURER", "Google");
        propsToChangePixel5a.put("DEVICE", "barbet");
        propsToChangePixel5a.put("PRODUCT", "barbet");
        propsToChangePixel5a.put("HARDWARE", "barbet");
        propsToChangePixel5a.put("MODEL", "Pixel 5a");
        propsToChangePixel5a.put("ID", "AP2A.240805.005.S4");
        propsToChangePixel5a.put("FINGERPRINT", "google/barbet/barbet:14/AP2A.240805.005.S4/12281092:user/release-keys");
        propsToChangeMeizu = new HashMap<>();
        propsToChangeMeizu.put("BRAND", "meizu");
        propsToChangeMeizu.put("MANUFACTURER", "Meizu");
        propsToChangeMeizu.put("DEVICE", "m1892");
        propsToChangeMeizu.put("DISPLAY", "Flyme");
        propsToChangeMeizu.put("PRODUCT", "meizu_16thPlus_CN");
        propsToChangeMeizu.put("MODEL", "meizu 16th Plus");
    }

    public static String getBuildID(String fingerprint) {
        Pattern pattern = Pattern.compile("([A-Za-z0-9]+\\.\\d+\\.\\d+\\.\\w+)");
        Matcher matcher = pattern.matcher(fingerprint);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String getDeviceName(String fingerprint) {
        String[] parts = fingerprint.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    private static boolean isGoogleCameraPackage(String packageName) {
        return packageName.contains("GoogleCamera")
                || Arrays.asList(customGoogleCameraPackages).contains(packageName);
    }

    private static boolean shouldTryToCertifyDevice() {
        if (!sIsGms) return false;

        final String processName = Application.getProcessName();
        if (!processName.toLowerCase().contains("unstable")) {
            return false;
        }

        final boolean was = isGmsAddAccountActivityOnTop();
        final String reason = "GmsAddAccountActivityOnTop";
        if (!was) {
            return true;
        }
        dlog("Skip spoofing build for GMS, because " + reason + "!");
        TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean isNow = isGmsAddAccountActivityOnTop();
                if (isNow ^ was) {
                    dlog(String.format("%s changed: isNow=%b, was=%b, killing myself!", reason, isNow, was));
                    Process.killProcess(Process.myPid());
                }
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
            return true;
        }
    }

    public static void spoofBuildGms() {
        if (!SystemProperties.getBoolean(SPOOF_PIXEL_GMS, true))
            return;
        for (String key : GMS_SPOOF_KEYS) {
            setPropValue(key, SystemProperties.get(PROP_HOOKS + key));
        }
    }

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();
        Map<String, Object> propsToChange = new HashMap<>();
        Context appContext = context.getApplicationContext();
        final boolean sIsTablet = isDeviceTablet(appContext);
        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsExcluded = isGoogleCameraPackage(packageName);
        String model = SystemProperties.get("ro.product.model");
        boolean isPixelDevice = SystemProperties.get("ro.soc.manufacturer").equalsIgnoreCase("Google");
        boolean isMainlineDevice = isPixelDevice && model.matches("Pixel (8|9|10)[a-zA-Z ]*");
        boolean isTensorDevice = isPixelDevice && model.matches("Pixel (6|7|8|9|10)[a-zA-Z ]*");
        boolean isPixelGmsEnabled = SystemProperties.getBoolean(SPOOF_PIXEL_GMS, true);
        propsToChangeGeneric.forEach((k, v) -> setPropValue(k, v));
        if (packageName == null || processName == null || packageName.isEmpty()) {
            return;
        }
        if (sIsExcluded) {
            return;
        }
        setGameProps(packageName);
        if (sIsGms) {
            if (shouldTryToCertifyDevice()) {
                if (!isPixelGmsEnabled) {
                    return;
                } else {
                    spoofBuildGms();
                }
            }
        } else if (Arrays.asList(packagesToChangeRecentPixel).contains(packageName)) {
            if (isMainlineDevice || !SystemProperties.getBoolean(SPOOF_PIXEL_PROPS, true)) {
                return;
            } else if (packageName.equals(PACKAGE_GMS) && !sIsGms) {
                setPropValue("TIME", System.currentTimeMillis());
                if (!isTensorDevice) {
                    if (processName.toLowerCase().contains("gapps")
                            || processName.toLowerCase().contains("gservice")
                            || processName.toLowerCase().contains("learning")
                            || processName.toLowerCase().contains("persistent")
                            || processName.toLowerCase().contains("search")
                            || processName.toLowerCase().contains("update")) {
                        propsToChange.putAll(propsToChangePixel5a);
                    }
                }
            } else if (SystemProperties.getBoolean(SPOOF_PIXEL_PROPS, true)) {
                if (sIsTablet) {
                    propsToChange.putAll(propsToChangePixelTablet);
                } else {
                    propsToChange.putAll(propsToChangeRecentPixel);
                }
            }
        } else if (Arrays.asList(packagesToChangeMeizu).contains(packageName)) {
            if (SystemProperties.getBoolean(DISGUISE_PROPS_FOR_MUSIC_APP, false)) {
                propsToChange.putAll(propsToChangeMeizu);
            }
        }
        dlog("Defining props for: " + packageName);
        for (Map.Entry<String, Object> prop : propsToChange.entrySet()) {
            String key = prop.getKey();
            Object value = prop.getValue();
            if (propsToKeep.containsKey(packageName) && propsToKeep.get(packageName).contains(key)) {
                dlog("Not defining " + key + " prop for: " + packageName);
                continue;
            }
            dlog("Defining " + key + " prop for: " + packageName);
            setPropValue(key, value);
        }
        // Set proper indexing fingerprint
        if (packageName.equals(PACKAGE_SI)) {
            setPropValue("FINGERPRINT", String.valueOf(Build.TIME));
            return;
        }
        if (packageName.equals(PACKAGE_ARCORE)) {
            setPropValue("FINGERPRINT", sDeviceFingerprint);
            return;
        }
        // Show correct model name on gms services
        if (packageName.toLowerCase().contains("com.google.android.gms")) {
            if (processName != null && processName.toLowerCase().contains("ui")) {
                setPropValue("MODEL", sDeviceModel);
                return;
            }
        }
    }

    public static void setGameProps(String packageName) {
        if (!SystemProperties.getBoolean(ENABLE_GAME_PROP_OPTIONS, false)) {
            return;
        }
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        Map<String, String> gamePropsToChange = new HashMap<>();
        String[] keys = {"BRAND", "DEVICE", "MANUFACTURER", "MODEL", "FINGERPRINT", "PRODUCT"};
        for (String key : keys) {
            String systemPropertyKey = "persist.sys.gameprops." + packageName + "." + key;
            String value = SystemProperties.get(systemPropertyKey);
            if (value != null && !value.isEmpty()) {
                gamePropsToChange.put(key, value);
                dlog("Got system property: " + systemPropertyKey + " = " + value);
            }
        }
        if (!gamePropsToChange.isEmpty()) {
            dlog("Defining props for: " + packageName);
            for (Map.Entry<String, String> prop : gamePropsToChange.entrySet()) {
                String key = prop.getKey();
                String value = prop.getValue();
                dlog("Defining " + key + " prop for: " + packageName);
                setPropValue(key, value);
            }
        }
    }

    private static boolean isDeviceTablet(Context context) {
        if (context == null) {
            return false;
        }
        Configuration configuration = context.getResources().getConfiguration();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        }
        return (configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXHIGH
                || displayMetrics.densityDpi == DisplayMetrics.DENSITY_XXXHIGH;
    }

    private static void setPropValue(String key, Object value) {
        try {
            Field field = getBuildClassField(key);
            if (field != null) {
                field.setAccessible(true);
                if (field.getType() == int.class) {
                    if (value instanceof String) {
                        field.set(null, Integer.parseInt((String) value));
                    } else if (value instanceof Integer) {
                        field.set(null, (Integer) value);
                    }
                } else if (field.getType() == long.class) {
                    if (value instanceof String) {
                        field.set(null, Long.parseLong((String) value));
                    } else if (value instanceof Long) {
                        field.set(null, (Long) value);
                    }
                } else {
                    field.set(null, value.toString());
                }
                field.setAccessible(false);
                dlog("Set prop " + key + " to " + value);
            } else {
                Log.e(TAG, "Field " + key + " not found in Build or Build.VERSION classes");
            }
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setVersionField(String key, Object value) {
        try {
            dlog("Defining version field " + key + " to " + value.toString());
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set version field " + key, e);
        }
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static void setVersionFieldInt(String key, int value) {
        try {
            dlog("Defining version field " + key + " to " + value);
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to spoof Build." + key, e);
        }
    }

    private static Field getBuildClassField(String key) throws NoSuchFieldException {
        try {
            Field field = Build.class.getDeclaredField(key);
            dlog("Field " + key + " found in Build.class");
            return field;
        } catch (NoSuchFieldException e) {
            Field field = Build.VERSION.class.getDeclaredField(key);
            dlog("Field " + key + " found in Build.VERSION.class");
            return field;
        }
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    private static String[] getStringArrayResSafely(int resId) {
        String[] strArr = Resources.getSystem().getStringArray(resId);
        if (strArr == null) strArr = new String[0];
        return strArr;
    }

    public static boolean isPackageGoogle(String pkg) {
        return pkg != null && pkg.toLowerCase().contains("google");
    }

    private static Set<String> getLauncherPkgs() {
        if (mLauncherPkgs == null || mLauncherPkgs.isEmpty()) {
            mLauncherPkgs =
                    new HashSet<>(
                            Arrays.asList(
                                    getStringArrayResSafely(R.array.config_launcherPackages)));
        }
        return mLauncherPkgs;
    }

    private static Set<String> getExemptedUidPkgs() {
        if (mExemptedUidPkgs == null || mExemptedUidPkgs.isEmpty()) {
            mExemptedUidPkgs = new HashSet<>();
            mExemptedUidPkgs.add(PACKAGE_GMS);
            mExemptedUidPkgs.addAll(getLauncherPkgs());
        }
        return mExemptedUidPkgs;
    }

    public static boolean isNexusLauncher(Context context) {
        try {
            return PACKAGE_NEXUS_LAUNCHER.equals(
                    context.getPackageManager().getNameForUid(android.os.Binder.getCallingUid()));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSystemLauncher(Context context) {
        try {
            return isSystemLauncherInternal(
                    context.getPackageManager().getNameForUid(android.os.Binder.getCallingUid()));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSystemLauncher(int callingUid) {
        try {
            return isSystemLauncherInternal(
                    ActivityThread.getPackageManager().getNameForUid(callingUid));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSystemLauncherInternal(String callerPackage) {
        return getLauncherPkgs().contains(callerPackage);
    }

    public static boolean shouldBypassTaskPermission(int callingUid) {
        for (String pkg : getExemptedUidPkgs()) {
            try {
                ApplicationInfo appInfo =
                        ActivityThread.getPackageManager()
                                .getApplicationInfo(pkg, 0, UserHandle.getUserId(callingUid));
                if (appInfo.uid == callingUid) {
                    return true;
                }
            } catch (Exception e) {
            }
        }
        return false;
    }

    public static boolean shouldBypassManageActivityTaskPermission(Context context) {
        final int callingUid = Binder.getCallingUid();
        return isSystemLauncher(callingUid)
                || isPackageGoogle(context.getPackageManager().getNameForUid(callingUid));
    }

    public static boolean shouldBypassMonitorInputPermission(Context context) {
        final int callingUid = Binder.getCallingUid();
        return shouldBypassTaskPermission(callingUid)
                || isPackageGoogle(context.getPackageManager().getNameForUid(callingUid));
    }

    // Whitelist of package names to bypass FGS type validation
    public static boolean shouldBypassFGSValidation(String packageName) {
        // Check if the app is whitelisted
        if (Arrays.asList(getStringArrayResSafely(R.array.config_fgsTypeValidationBypassPackages))
                .contains(packageName)) {
            dlog(
                    "shouldBypassFGSValidation: "
                            + "Bypassing FGS type validation for whitelisted app: "
                            + packageName);
            return true;
        }
        return false;
    }

    // Whitelist of package names to bypass alarm manager validation
    public static boolean shouldBypassAlarmManagerValidation(String packageName) {
        // Check if the app is whitelisted
        if (Arrays.asList(
                        getStringArrayResSafely(
                                R.array.config_alarmManagerValidationBypassPackages))
                .contains(packageName)) {
            dlog(
                    "shouldBypassAlarmManagerValidation: "
                            + "Bypassing alarm manager validation for whitelisted app: "
                            + packageName);
            return true;
        }
        return false;
    }

    // Whitelist of package names to bypass broadcast reciever validation
    public static boolean shouldBypassBroadcastReceiverValidation(String packageName) {
        // Check if the app is whitelisted
        if (Arrays.asList(
                        getStringArrayResSafely(
                                R.array.config_broadcaseReceiverValidationBypassPackages))
                .contains(packageName)) {
            dlog(
                    "shouldBypassBroadcastReceiverValidation: "
                            + "Bypassing broadcast receiver validation for whitelisted app: "
                            + packageName);
            return true;
        }
        return false;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
