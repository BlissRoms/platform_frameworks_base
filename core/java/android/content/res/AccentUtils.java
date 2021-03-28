package android.content.res;

import android.annotation.Nullable;
import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;
import android.provider.Settings;
import android.content.Context;
import android.app.ActivityThread;

import java.util.ArrayList;
import java.util.Arrays;

public final class AccentUtils {
    private AccentUtils() {}

    private static final String TAG = "AccentUtils";

    private static final String ACCENT_COLOR_SETTING = "accent_color";

    public static boolean isResourceColorAccent(@Nullable String resName) {
        return resName == null
                ? false
                :  resName.contains("accent_device_default_dark")
                || resName.contains("colorAccent")
                || resName.contains("accent_device_default_light");
    }

    public static int getAccent(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_SETTING);
    }


    private static int getAccentColor(int defaultColor, String setting) {
        final Context context = ActivityThread.currentApplication();
        try {
            String colorValue = Settings.Secure.getString(context.getContentResolver(), setting);
            return (colorValue == null || "-1".equals(colorValue))
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
