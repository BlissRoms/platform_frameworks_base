/*
 * Copyright (C) 2023 Rising OS Android Project
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

package com.android.internal.util.android;

import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import java.lang.ref.WeakReference;

import java.util.List;

public class SystemRestartUtils {

    private static final int RESTART_TIMEOUT = 1000;

    public static void showSystemRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.system_restart_title)
                .setMessage(R.string.system_restart_message)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    Handler handler = new Handler();
                    handler.postDelayed(() -> restartSystem(context), RESTART_TIMEOUT);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void powerOffSystem(Context context) {
        new PowerOffSystemTask(context).execute();
    }

    private static class PowerOffSystemTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContext;

        PowerOffSystemTask(Context context) {
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                IStatusBarService mBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE));
                if (mBarService != null) {
                    try {
                        Thread.sleep(RESTART_TIMEOUT);
                        mBarService.shutdown();
                    } catch (RemoteException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void restartSystem(Context context) {
        new RestartSystemTask(context).execute();
    }

    private static class RestartSystemTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContext;

        RestartSystemTask(Context context) {
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                IStatusBarService mBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE));
                if (mBarService != null) {
                    try {
                        Thread.sleep(RESTART_TIMEOUT);
                        mBarService.reboot(false, null);
                    } catch (RemoteException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static void showRestartDialog(Context context, int title, int message, Runnable action) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    Handler handler = new Handler();
                    handler.postDelayed(action, RESTART_TIMEOUT);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartProcess(Context context, String processName) {
        new RestartTask(context, processName).execute();
    }

    private static class RestartTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContext;
        private final String mPackageName;

        RestartTask(Context context, String packageName) {
            mContext = new WeakReference<>(context);
            mPackageName = packageName;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am = (ActivityManager) mContext.get().getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
                    for (ActivityManager.RunningAppProcessInfo appProcess : runningProcesses) {
                        if (appProcess.pkgList != null) {
                            for (String pkg : appProcess.pkgList) {
                                if (pkg.equals(mPackageName)) {
                                    Process.killProcess(appProcess.pid);
                                    return null;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {}
            return null;
        }
    }

    public static void showSettingsRestartDialog(Context context) {
        showRestartDialog(context, R.string.settings_restart_title, R.string.settings_restart_message, () -> restartProcess(context, "com.android.settings"));
    }

    public static void showSystemUIRestartDialog(Context context) {
        showRestartDialog(context, R.string.systemui_restart_title, R.string.systemui_restart_message, () -> restartProcess(context, "com.android.systemui"));
    }
}
