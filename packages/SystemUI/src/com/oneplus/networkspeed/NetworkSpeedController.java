package com.oneplus.networkspeed;

import com.android.systemui.statusbar.policy.CallbackController;
import com.oneplus.networkspeed.NetworkSpeedController.INetworkSpeedStateCallBack;
import java.util.BitSet;

public interface NetworkSpeedController extends CallbackController<INetworkSpeedStateCallBack> {

    public interface INetworkSpeedStateCallBack {
        void onSpeedChange(String str);

        default void onSpeedShow(boolean z) {
        }
    }

    void updateConnectivity(BitSet bitSet, BitSet bitSet2);
}
