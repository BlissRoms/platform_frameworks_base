package com.android.internal.util.aicp;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class Helpers {
    // avoids hardcoding the tag
    private static final String TAG = Thread.currentThread().getStackTrace()[1].getClassName();

    public static void restartSystemUI(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            IActivityManager amn = ActivityManagerNative.getDefault();
            context.stopService(new Intent().setComponent(new ComponentName("com.android.systemui", "com.android.systemui.SystemUIService")));
            am.killBackgroundProcesses("com.android.systemui");
            for (ActivityManager.RunningAppProcessInfo app : am.getRunningAppProcesses()) {
                if ("com.android.systemui".equals(app.processName)) {
                    Toast toast = Toast.makeText(context, "Restarting Interface ...." , Toast.LENGTH_LONG);
                    toast.show();
                    amn.killApplicationProcess(app.processName, app.uid);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
