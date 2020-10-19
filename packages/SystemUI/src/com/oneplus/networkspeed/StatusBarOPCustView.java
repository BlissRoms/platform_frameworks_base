package com.oneplus.networkspeed;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.R;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.StatusIconDisplayable;
import com.android.internal.util.bliss.BlissUtils;

public class StatusBarOPCustView extends LinearLayout implements DarkIconDispatcher.DarkReceiver, StatusIconDisplayable {
    private boolean mDirty = true;
    private StatusBarIconView mDotView;
    private OPCustView mOPCustView;
    private Rect mRect;
    private String mSlot;
    private int mTint;
    private boolean mVisible;
    private int mVisibleState = -1;

    public static StatusBarOPCustView fromResId(Context context, int i) {
        StatusBarOPCustView statusBarOPCustView = new StatusBarOPCustView(context);
        View inflate = LayoutInflater.from(context).inflate(i, (ViewGroup) null);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.gravity = 16;
        statusBarOPCustView.addView(inflate, layoutParams);
        statusBarOPCustView.setView(inflate, context);
        statusBarOPCustView.initDotView();
        return statusBarOPCustView;
    }

    public StatusBarOPCustView(Context context) {
        super(context);
    }

    public StatusBarOPCustView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public void setSlot(String str) {
        mSlot = str;
    }

    public void applyVisible(boolean z) {
        if (mVisible != z) {
            mVisible = z;
            setVisibility(z ? 0 : 8);
            updateState();
        }
    }

    public String getSlot() {
        return mSlot;
    }

    public void setStaticDrawableColor(int i) {
        mOPCustView.setColor(i);
        mDotView.setDecorColor(i);
    }

    public void setDecorColor(int i) {
        mDotView.setDecorColor(i);
    }

    public void setVisibleState(int i) {
        if (mVisibleState != i) {
            mVisibleState = i;
            updateState();
        }
    }

    public void setVisibleState(int i, boolean z) {
        setVisibleState(i);
    }

    public int getVisibleState() {
        return mVisibleState;
    }

    public boolean isIconVisible() {
        return mVisible;
    }

    public void onDarkChanged(Rect rect, float f, int i) {
        Rect rect2 = mRect;
        if (rect2 != null && DarkIconDispatcher.isInArea(rect2, this)) {
            BlissUtils.notifyStatusBarIconsDark(f == 1.0f);
        }
        mRect = rect;
        mTint = i;
        applyColors();
    }

    private void applyColors() {
        Rect rect = mRect;
        if (rect != null) {
            int i = mTint;
            mOPCustView.setColor(DarkIconDispatcher.getTint(rect, this, i));
            mDotView.setDecorColor(i);
            mDotView.setIconColor(i, false);
        }
    }

    private boolean setView(View view, Context context) {
        if (mOPCustView == null) {
            mOPCustView = new OPCustView(this, context);
        }
        return mOPCustView.setView(view);
    }

    private void initDotView() {
        StatusBarIconView statusBarIconView = new StatusBarIconView(mContext, mSlot, (StatusBarNotification) null);
        mDotView = statusBarIconView;
        statusBarIconView.setVisibleState(1);
        int dimensionPixelSize = mContext.getResources().getDimensionPixelSize(R.dimen.status_bar_icon_size);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(dimensionPixelSize, dimensionPixelSize);
        layoutParams.gravity = 8388627;
        addView(mDotView, layoutParams);
    }

    private void updateState() {
        if (mVisible) {
            int i = mVisibleState;
            if (i == 0) {
                mOPCustView.setVisibility(0);
                mDotView.setVisibility(8);
            } else if (i == 1) {
                mOPCustView.setVisibility(4);
                mDotView.setVisibility(0);
            } else if (i != 2) {
                mOPCustView.setVisibility(0);
                mDotView.setVisibility(8);
            } else {
                mOPCustView.setVisibility(8);
                mDotView.setVisibility(8);
            }
        }
    }

    public void onLayout(boolean z, int i, int i2, int i3, int i4) {
        super.onLayout(z, i, i2, i3, i4);
        if (mDirty && getWidth() > 0) {
            applyColors();
            mDirty = false;
        }
    }

    public void setVisibility(int i) {
        super.setVisibility(i);
        if (i == 0) {
            if (getWidth() > 0) {
                applyColors();
            }
            mDirty = true;
        }
    }

    private class OPCustView {
        private Context mContext;
        Class[] mValidArray = {TextView.class, NetworkSpeedView.class};
        private View mView;

        public OPCustView(StatusBarOPCustView statusBarOPCustView, Context context) {
            mContext = context;
        }

        public boolean setView(View view) {
            mView = view;
            boolean z = false;
            for (Class isInstance : mValidArray) {
                if (isInstance.isInstance(mView)) {
                    z = true;
                }
            }
            if (!z) {
                Log.w("StatusBarOPCustView", "Load StatusBarOPCustView error, the resource is not valid.");
                mView = new TextView(mContext);
            }
            return z;
        }

        public void setVisibility(int i) {
            mView.setVisibility(i);
        }

        public void setColor(int i) {
            if (mValidArray[0].isInstance(mView)) {
                ((TextView) mView).setTextColor(i);
            } else if (mValidArray[1].isInstance(mView)) {
                ((NetworkSpeedView) mView).setTextColor(i);
            }
        }
    }

    public void onConfigurationChanged(Configuration configuration) {
        mDirty = true;
    }

    public void onMeasure(int i, int i2) {
        super.onMeasure(0, 0);
    }
}
