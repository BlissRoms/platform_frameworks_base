package com.android.systemui.ambientmusic;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.Dependency;
import java.util.Objects;

public class AmbientIndicationService extends BroadcastReceiver {
    private final AlarmManager mAlarmManager;
    private final AmbientIndicationContainer mAmbientIndicationContainer;
    private final KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {
        public void onUserSwitchComplete(int i) {
            onUserSwitched();
        }
    };
    private final Context mContext;
    private final AlarmManager.OnAlarmListener mHideIndicationListener;

    public AmbientIndicationService(Context context, AmbientIndicationContainer ambientIndicationContainer) {
        mContext = context;
        mAmbientIndicationContainer = ambientIndicationContainer;
        mAlarmManager = (AlarmManager) context.getSystemService(AlarmManager.class);
        AmbientIndicationContainer ambientIndicationContainer2 = mAmbientIndicationContainer;
        Objects.requireNonNull(ambientIndicationContainer2);
        mHideIndicationListener = new AlarmManager.OnAlarmListener() {
            public final void onAlarm() {
                mAmbientIndicationContainer.hideAmbientMusic();
            }
        };
        start();
    }

    public void start() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW");
        intentFilter.addAction("com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE");
        mContext.registerReceiverAsUser(this, UserHandle.ALL, intentFilter, "com.google.android.ambientindication.permission.AMBIENT_INDICATION", (Handler) null);
        ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).registerCallback(mCallback);
    }

    public void onReceive(Context context, Intent intent) {
        if (!isForCurrentUser()) {
            Log.i("AmbientIndication", "Suppressing ambient, not for this user.");
        } else if (verifyAmbientApiVersion(intent)) {
            if (mAmbientIndicationContainer.isMediaPlaying()) {
                Log.i("AmbientIndication", "Suppressing ambient intent due to media playback.");
                return;
            }
            String action = intent.getAction();
            char c = 65535;
            int hashCode = action.hashCode();
            if (hashCode != -1032272569) {
                if (hashCode == -1031945470 && action.equals("com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW")) {
                    c = 0;
                }
            } else if (action.equals("com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE")) {
                c = 1;
            }
            if (c == 0) {
                long min = Math.min(Math.max(intent.getLongExtra("com.google.android.ambientindication.extra.TTL_MILLIS", 180000), 0), 180000);
                mAmbientIndicationContainer.setAmbientMusic(intent.getCharSequenceExtra("com.google.android.ambientindication.extra.TEXT"), (PendingIntent) intent.getParcelableExtra("com.google.android.ambientindication.extra.OPEN_INTENT"), intent.getBooleanExtra("com.google.android.ambientindication.extra.SKIP_UNLOCK", false));
                mAlarmManager.setExact(2, SystemClock.elapsedRealtime() + min, "AmbientIndication", mHideIndicationListener, (Handler) null);
                Log.i("AmbientIndication", "Showing ambient indication.");
            } else if (c == 1) {
                mAlarmManager.cancel(mHideIndicationListener);
                mAmbientIndicationContainer.hideAmbientMusic();
                Log.i("AmbientIndication", "Hiding ambient indication.");
            }
        }
    }

    private boolean verifyAmbientApiVersion(Intent intent) {
        int intExtra = intent.getIntExtra("com.google.android.ambientindication.extra.VERSION", 0);
        if (intExtra == 1) {
            return true;
        }
        Log.e("AmbientIndication", "AmbientIndicationApi.EXTRA_VERSION is " + 1 + ", but received an intent with version " + intExtra + ", dropping intent.");
        return false;
    }

    public boolean isForCurrentUser() {
        return getSendingUserId() == getCurrentUser() || getSendingUserId() == -1;
    }

    public int getCurrentUser() {
        return KeyguardUpdateMonitor.getCurrentUser();
    }

    public void onUserSwitched() {
        mAmbientIndicationContainer.hideAmbientMusic();
    }
}
