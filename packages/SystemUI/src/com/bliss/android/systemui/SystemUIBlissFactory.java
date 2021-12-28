package com.bliss.android.systemui;

import android.content.Context;

import com.bliss.android.systemui.dagger.DaggerGlobalRootComponentBliss;
import com.bliss.android.systemui.dagger.GlobalRootComponentBliss;

import com.android.systemui.SystemUIFactory;
import com.android.systemui.dagger.GlobalRootComponent;

public class SystemUIBlissFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent buildGlobalRootComponent(Context context) {
        return DaggerGlobalRootComponentBliss.builder()
                .context(context)
                .build();
    }
}
