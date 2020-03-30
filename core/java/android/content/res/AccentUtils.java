/**
 * Copyright (C) 2020 ion-OS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public class AccentUtils {
    private static final String TAG = "AccentUtils";

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";
    private static final String GRADIENT_COLOR_PROP = "persist.sys.theme.gradientcolor";

    static boolean isResourceAccent(String resName) {
        return resName.contains("accent_device_default_light")
                || resName.contains("accent_device_default_dark")
                || resName.contains("accent_device_default")
                || resName.contains("material_pixel_blue_dark")
                || resName.contains("material_pixel_blue_bright")
                || resName.contains("gradient_start")
                || resName.contains("colorAccent")
                || resName.contains("holo_blue_light")
                || resName.contains("holo_blue_dark")
                || resName.contains("omni_color5")
                || resName.contains("omni_color4")
                || resName.contains("dialer_theme_color")
                || resName.contains("dialer_theme_color_dark")
                || resName.contains("dialer_theme_color_20pct");
    }

    static boolean isResourceGradient(String resName) {
        return resName.contains("gradient_end");
    }

    public static int getNewAccentColor(int defaultColor) {
        return getAccentColor(defaultColor, ACCENT_COLOR_PROP);
    }

    public static int getNewGradientColor(int defaultColor) {
        return getAccentColor(defaultColor, GRADIENT_COLOR_PROP);
    }

    private static int getAccentColor(int defaultColor, String property) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        if (cal.get(Calendar.MONTH) == 3 && cal.get(Calendar.DAY_OF_MONTH) == 1) {
            return ColorUtils.genRandomAccentColor(property == ACCENT_COLOR_PROP);
        }
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
