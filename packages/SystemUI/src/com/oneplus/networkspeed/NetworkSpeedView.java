package com.oneplus.networkspeed;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.oneplus.networkspeed.NetworkSpeedController;

public class NetworkSpeedView extends LinearLayout implements NetworkSpeedController.INetworkSpeedStateCallBack {
    private Context mContext;
    private boolean mIsVisible;
    private NetworkSpeedController mNetworkSpeedController;
    private ScreenLifecycle mScreenLifecycle;
    private final ScreenLifecycle.Observer mScreenObserver;
    private String mTextDown;
    private String mTextUp;
    private TextView mTextViewDown;
    private TextView mTextViewUp;

    public NetworkSpeedView(Context context) {
        this(context, (AttributeSet) null);
    }

    public NetworkSpeedView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public NetworkSpeedView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        mIsVisible = false;
        mScreenObserver = new ScreenLifecycle.Observer() {
            public void onScreenTurnedOff() {
            }

            public void onScreenTurnedOn() {
                updateText();
            }
        };
        mNetworkSpeedController = (NetworkSpeedController) Dependency.get(NetworkSpeedController.class);
        mScreenLifecycle = (ScreenLifecycle) Dependency.get(ScreenLifecycle.class);
        mContext = context;
    }

    public void onFinishInflate() {
        super.onFinishInflate();
        mTextViewUp = (TextView) findViewById(R.id.speed_word_up);
        mTextViewDown = (TextView) findViewById(R.id.speed_word_down);
        Log.i("NetworkSpeedView", "onFinishInflate");
        mContext.getResources().getConfiguration();
        refreshTextView();
    }

    public void onSpeedChange(String str) {
        String[] split = str.split(":");
        if (split.length == 2) {
            mTextUp = split[0];
            mTextDown = split[1];
            updateText();
        }
    }

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerReceiver();
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterReceiver();
    }

    public void registerReceiver() {
        mNetworkSpeedController.addCallback(this);
        mScreenLifecycle.addObserver(mScreenObserver);
    }

    public void unregisterReceiver() {
        mNetworkSpeedController.removeCallback(this);
        mScreenLifecycle.removeObserver(mScreenObserver);
    }

    public void setVisibility(int i) {
        super.setVisibility(i);
        boolean z = i == 0;
        if (mIsVisible != z) {
            mIsVisible = z;
            updateText();
        }
    }

    public void updateText() {
        TextView textView;
        boolean z = mScreenLifecycle.getScreenState() == 2;
        if (mIsVisible && z && (textView = mTextViewUp) != null && mTextViewDown != null) {
            textView.setText(mTextUp);
            mTextViewDown.setText(mTextDown);
        }
    }

    public void setTextColor(int i) {
        TextView textView = mTextViewUp;
        if (textView != null && mTextViewDown != null) {
            textView.setTextColor(i);
            mTextViewDown.setTextColor(i);
        }
    }

    private void refreshTextView() {
        TextView textView = mTextViewUp;
        if (textView != null && mTextViewDown != null) {
            textView.setLetterSpacing(-0.05f);
            mTextViewDown.setLetterSpacing(0.05f);
        }
    }
}
