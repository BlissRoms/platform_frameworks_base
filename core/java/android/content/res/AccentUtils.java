package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";

    public static boolean isResourceDarkAccent(String resName) {
        return resName.contains("accent_device_default_dark");
    }

    public static boolean isResourceLightAccent(String resName) {
        return resName.contains("accent_device_default_light");
    }

    public static boolean isResourceGradientStart(String resName) {
        return resName.contains("gradient_start");
    }

    public static int getNewAccentColor(int defaultColor) {
        return getNewColor(defaultColor, ACCENT_COLOR_PROP);
    }

    private static int getNewColor(int defaultColor, String property) {
        try {
            String colorValue = SystemProperties.get(property, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
