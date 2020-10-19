package com.oneplus.networkspeed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Slog;
import com.android.internal.os.BackgroundThread;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.tuner.TunerService;
import com.oneplus.networkspeed.NetworkSpeedController;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Timer;

public class NetworkSpeedControllerImpl extends BroadcastReceiver implements TunerService.Tunable, NetworkSpeedController {
    private static long ERTRY_POINT = 1024;
    private static int HANDRED = 100;
    public static String TAG = "NetworkSpeedController";
    private static int TEN = 10;
    private static int THOUSAND = 1000;
    private static String UNIT_GB = "GB";
    private static String UNIT_KB = "KB";
    private static String UNIT_MB = "MB";
    public static int UPDATE_INTERVAL = 3;
    public int MSG_MAYBE_STOP_NETWORTSPEED = 1001;
    public int MSG_UPDATE_NETWORTSPEED = 1000;
    public int MSG_UPDATE_SHOW = 1002;
    public int MSG_UPDATE_SPEED_ON_BG = 2001;
    public MyBackgroundHandler mBackgroundHandler = new MyBackgroundHandler(BackgroundThread.getHandler().getLooper());
    private boolean mBlockNetworkSpeed = true;
    private ConnectivityManager mConnectivityManager = null;
    private Context mContext;
    public MyHandler mHandler = new MyHandler(Looper.getMainLooper());
    private boolean mHotSpotEnable = false;
    private StatusBarIconController mIconController;
    public boolean mIsFirstLoad = true;
    private final ArrayList<NetworkSpeedController.INetworkSpeedStateCallBack> mNetworkSpeedStateCallBack = new ArrayList<>();
    private boolean mNetworkTraceState = false;
    private boolean mShow = false;
    public String mSpeed = "";
    public MySpeedMachine mSpeedMachine = new MySpeedMachine();

    public NetworkSpeedControllerImpl (Context context) {
        mContext = context;
        new Timer();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        intentFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        mContext.registerReceiver(this, intentFilter);
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService("connectivity");
        StatusBarIconController statusBarIconController = (StatusBarIconController) Dependency.get(StatusBarIconController.class);
        mIconController = statusBarIconController;
        statusBarIconController.setOPCustView("networkspeed", R.layout.status_bar_network_speed, mShow);
        mIconController.setIconVisibility("networkspeed", mShow);
        ((TunerService) Dependency.get(TunerService.class)).addTunable(this, "icon_blacklist");
    }

    public void updateConnectivity(BitSet bitSet, BitSet bitSet2) {
        updateState();
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == "android.intent.action.TIME_SET") {
            updateState();
        } else if (action == "android.intent.action.TIMEZONE_CHANGED") {
            updateState();
        } else if (action == "android.net.wifi.WIFI_AP_STATE_CHANGED") {
            mHotSpotEnable = intent.getIntExtra("wifi_state", 14) == 13;
            updateState();
        }
    }

    private String divToFractionDigits(long j, long j2, int i) {
        if (j2 == 0) {
            Log.i(TAG, "divisor shouldn't be 0");
            return "Error";
        }
        StringBuffer stringBuffer = new StringBuffer();
        long j3 = j / j2;
        long j4 = j % j2;
        stringBuffer.append(j3);
        if (i > 0) {
            stringBuffer.append(".");
            for (int i2 = 0; i2 < i; i2++) {
                long j5 = j4 * 10;
                long j6 = j5 / j2;
                j4 = j5 % j2;
                stringBuffer.append(j6);
            }
        }
        return stringBuffer.toString();
    }

    public String formateSpeed(long j) {
        long j2;
        StringBuffer stringBuffer = new StringBuffer();
        String str = UNIT_KB;
        long j3 = ERTRY_POINT;
        int i = 0;
        if (j >= j3) {
            if (j < j3 || j >= ((long) THOUSAND) * j3) {
                long j4 = ERTRY_POINT;
                int i2 = THOUSAND;
                if (j < ((long) i2) * j4 || j >= j4 * j4 * ((long) i2)) {
                    long j5 = ERTRY_POINT;
                    long j6 = j5 * j5 * j5;
                    String str2 = UNIT_GB;
                    if (j < j5 * j5 * ((long) THOUSAND) || j >= ((long) TEN) * j6) {
                        long j7 = ERTRY_POINT;
                        if (j < j7 * j7 * j7 * ((long) TEN) || j >= ((long) HANDRED) * j6) {
                            j2 = j6;
                            str = str2;
                            stringBuffer.append(divToFractionDigits(j, j2, i));
                            stringBuffer.append(":");
                            stringBuffer.append(str);
                            stringBuffer.append("/S");
                            return stringBuffer.toString();
                        }
                        i = 1;
                    } else {
                        i = 2;
                    }
                    str = str2;
                    j2 = j6;
                    stringBuffer.append(divToFractionDigits(j, j2, i));
                    stringBuffer.append(":");
                    stringBuffer.append(str);
                    stringBuffer.append("/S");
                    return stringBuffer.toString();
                }
                long j8 = j4 * j4;
                String str3 = UNIT_MB;
                if (j < j4 * ((long) i2) || j >= ((long) TEN) * j8) {
                    long j9 = ERTRY_POINT;
                    if (j < j9 * j9 * ((long) TEN) || j >= ((long) HANDRED) * j8) {
                        long j10 = ERTRY_POINT;
                        if (j >= j10 * j10 * ((long) HANDRED)) {
                            int i3 = (j > (((long) THOUSAND) * j8) ? 1 : (j == (((long) THOUSAND) * j8) ? 0 : -1));
                        }
                        str = str3;
                    } else {
                        str = str3;
                        i = 1;
                    }
                } else {
                    str = str3;
                    i = 2;
                }
                j2 = j8;
                stringBuffer.append(divToFractionDigits(j, j2, i));
                stringBuffer.append(":");
                stringBuffer.append(str);
                stringBuffer.append("/S");
                return stringBuffer.toString();
            } else if (j < j3 || j >= ((long) TEN) * j3) {
                long j11 = ERTRY_POINT;
                if (j < ((long) TEN) * j11 || j >= j11 * ((long) HANDRED)) {
                    long j12 = ERTRY_POINT;
                    if (j >= ((long) HANDRED) * j12) {
                        int i4 = (j > (j12 * ((long) THOUSAND)) ? 1 : (j == (j12 * ((long) THOUSAND)) ? 0 : -1));
                    }
                    j2 = j3;
                    stringBuffer.append(divToFractionDigits(j, j2, i));
                    stringBuffer.append(":");
                    stringBuffer.append(str);
                    stringBuffer.append("/S");
                    return stringBuffer.toString();
                }
                i = 1;
                j2 = j3;
                stringBuffer.append(divToFractionDigits(j, j2, i));
                stringBuffer.append(":");
                stringBuffer.append(str);
                stringBuffer.append("/S");
                return stringBuffer.toString();
            }
        }
        i = 2;
        j2 = j3;
        stringBuffer.append(divToFractionDigits(j, j2, i));
        stringBuffer.append(":");
        stringBuffer.append(str);
        stringBuffer.append("/S");
        return stringBuffer.toString();
    }

    public void updateState() {
        boolean isNetworkSpeedTracing = isNetworkSpeedTracing();
        if (mNetworkTraceState != isNetworkSpeedTracing) {
            mNetworkTraceState = isNetworkSpeedTracing;
            if (isNetworkSpeedTracing) {
                onStartTraceSpeed();
            } else {
                onStopTraceSpeed();
            }
            Message obtainMessage = mHandler.obtainMessage();
            obtainMessage.what = MSG_UPDATE_SHOW;
            mHandler.sendMessage(obtainMessage);
        }
    }

    public void onShowStateChange() {
        boolean z = mNetworkTraceState;
        if (mShow != z) {
            mShow = z;
            StatusBarIconController statusBarIconController = mIconController;
            if (statusBarIconController != null) {
                statusBarIconController.setIconVisibility("networkspeed", z);
            }
            Iterator<NetworkSpeedController.INetworkSpeedStateCallBack> it = mNetworkSpeedStateCallBack.iterator();
            while (it.hasNext()) {
                it.next().onSpeedShow(z);
            }
        }
    }

    private void onStartTraceSpeed() {
        updateSpeed();
    }

    private void onStopTraceSpeed() {
        mIsFirstLoad = true;
        stopSpeed();
        mSpeed = "";
    }

    private void updateSpeed() {
        mIsFirstLoad = true;
        mSpeed = "";
        Message obtainMessage = mHandler.obtainMessage();
        obtainMessage.what = MSG_UPDATE_NETWORTSPEED;
        obtainMessage.obj = mSpeed;
        mHandler.sendMessage(obtainMessage);
        MySpeedMachine mySpeedMachine = mSpeedMachine;
        if (mySpeedMachine != null) {
            mySpeedMachine.reset();
            mSpeedMachine.setTurnOn();
        }
        mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
        Message message = new Message();
        message.what = MSG_UPDATE_SPEED_ON_BG;
        mBackgroundHandler.sendMessage(message);
    }

    public void scheduleNextUpdate() {
        long uptimeMillis = SystemClock.uptimeMillis() + ((long) (UPDATE_INTERVAL * 1000));
        Message message = new Message();
        message.what = MSG_UPDATE_SPEED_ON_BG;
        mBackgroundHandler.sendMessageAtTime(message, uptimeMillis);
    }

    private void stopSpeed() {
        MySpeedMachine mySpeedMachine = mSpeedMachine;
        if (mySpeedMachine != null) {
            mySpeedMachine.reset();
            mSpeedMachine.setTurnOff();
        }
        mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
    }

    public void refreshSpeed() {
        Iterator<NetworkSpeedController.INetworkSpeedStateCallBack> it = mNetworkSpeedStateCallBack.iterator();
        while (it.hasNext()) {
            NetworkSpeedController.INetworkSpeedStateCallBack next = it.next();
            if (next != null) {
                next.onSpeedChange(mSpeed);
            }
        }
    }

    private class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            int i = message.what;
            if (i == MSG_UPDATE_NETWORTSPEED) {
                Object obj = message.obj;
                if (obj instanceof String) {
                    String unused = mSpeed = (String) obj;
                    refreshSpeed();
                }
            } else if (i == MSG_MAYBE_STOP_NETWORTSPEED) {
                    updateState();
            } else if (i == MSG_UPDATE_SHOW) {
                    onShowStateChange();
            }
        }
    }

    private class MyBackgroundHandler extends Handler {
        public MyBackgroundHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message message) {
            if (message.what == MSG_UPDATE_SPEED_ON_BG) {
                mBackgroundHandler.removeMessages(MSG_UPDATE_SPEED_ON_BG);
                if (mSpeedMachine.isTurnOn()) {
                    mSpeedMachine.updateSpeedonBG();
                    scheduleNextUpdate();
                }
            }
        }
    }

    private class MySpeedMachine {
        long incrementRxBytes = 0;
        long incrementTxBytes = 0;
        boolean isTurnOn = false;
        long oldRxBytes = 0;
        long oldTxBytes = 0;

        public MySpeedMachine() {
            reset();
        }

        public void updateSpeedonBG() {
            if (isNetworkSpeedTracing()) {
                long totalTxBytes = TrafficStats.getTotalTxBytes();
                long totalRxBytes = TrafficStats.getTotalRxBytes();
                incrementTxBytes = totalTxBytes - oldTxBytes;
                incrementRxBytes = totalRxBytes - oldRxBytes;
                oldTxBytes = totalTxBytes;
                oldRxBytes = totalRxBytes;
                if (mIsFirstLoad) {
                    incrementTxBytes = 0;
                    incrementRxBytes = 0;
                    boolean unused = mIsFirstLoad = false;
                }
                if (incrementTxBytes < 0) {
                    incrementTxBytes = 0;
                }
                if (incrementRxBytes < 0) {
                    incrementRxBytes = 0;
                }
                long j = incrementRxBytes;
                long j2 = incrementTxBytes;
                if (j <= j2) {
                    j = j2;
                }
                long access$1500 = j / ((long) UPDATE_INTERVAL);
                String access$1600 = formateSpeed(access$1500);
                Message obtainMessage = mHandler.obtainMessage();
                obtainMessage.what = MSG_UPDATE_NETWORTSPEED;
                obtainMessage.obj = access$1600;
                mHandler.sendMessage(obtainMessage);
                return;
            }
            Message obtainMessage2 = mHandler.obtainMessage();
            obtainMessage2.what = MSG_MAYBE_STOP_NETWORTSPEED;
            mHandler.sendMessage(obtainMessage2);
            Log.d(TAG, "send MSG_CLOSE_NETWORTSPEED");
        }

        public void reset() {
            oldTxBytes = 0;
            incrementTxBytes = 0;
            oldRxBytes = 0;
            incrementRxBytes = 0;
        }

        public void setTurnOn() {
            isTurnOn = true;
        }

        public void setTurnOff() {
            isTurnOn = false;
        }

        public boolean isTurnOn() {
            return isTurnOn;
        }
    }

    private boolean isNetworkConnected() {
        boolean z = false;
        if (mContext == null) {
            return false;
        }
        NetworkInfo networkInfo = null;
        ConnectivityManager connectivityManager = mConnectivityManager;
        if (connectivityManager != null) {
            networkInfo = connectivityManager.getActiveNetworkInfo();
        }
        if (networkInfo != null && networkInfo.isAvailable()) {
            z = true;
        }
        return z;
    }

    public boolean isNetworkSpeedTracing() {
        return isNetworkConnected() && !mBlockNetworkSpeed;
    }

    public void onTuningChanged(String str, String str2) {
        boolean contains;
        if ("icon_blacklist".equals(str) && (contains = StatusBarIconController.getIconBlacklist(mContext, str2).contains("networkspeed")) != mBlockNetworkSpeed) {
            String str3 = TAG;
            Log.i(str3, " onTuningChanged blocknetworkSpeed:" + contains);
            mBlockNetworkSpeed = contains;
            updateState();
        }
    }

    public void addCallback(NetworkSpeedController.INetworkSpeedStateCallBack iNetworkSpeedStateCallBack) {
        synchronized (this) {
            mNetworkSpeedStateCallBack.add(iNetworkSpeedStateCallBack);
            try {
                iNetworkSpeedStateCallBack.onSpeedChange(mSpeed);
                iNetworkSpeedStateCallBack.onSpeedShow(mShow);
            } catch (Exception e) {
                Slog.w(TAG, "Failed to call to IKeyguardStateCallback", e);
            }
        }
    }

    public void removeCallback(NetworkSpeedController.INetworkSpeedStateCallBack iNetworkSpeedStateCallBack) {
        synchronized (this) {
            mNetworkSpeedStateCallBack.remove(iNetworkSpeedStateCallBack);
        }
    }
}
