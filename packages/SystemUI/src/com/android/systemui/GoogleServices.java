package com.android.systemui;

import android.content.Context;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.Dumpable;
import com.android.systemui.VendorServices;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.ambientmusic.AmbientIndicationContainer;
import com.android.systemui.ambientmusic.AmbientIndicationService;
import dagger.Lazy;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class GoogleServices extends VendorServices {
    private ArrayList<Object> mServices = new ArrayList<>();
    private final StatusBar mStatusBar;
    private final UiEventLogger mUiEventLogger;

    public GoogleServices(Context context, StatusBar statusBar, UiEventLogger uiEventLogger) {
        super(context);
        mStatusBar = statusBar;
        mUiEventLogger = uiEventLogger;
    }

    public void start() {
        AmbientIndicationContainer ambientIndicationContainer = (AmbientIndicationContainer) mStatusBar.getNotificationShadeWindowView().findViewById(R.id.ambient_indication_container);
        ambientIndicationContainer.initializeView(mStatusBar);
        addService(new AmbientIndicationService(mContext, ambientIndicationContainer));
    }

    public void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        for (int i = 0; i < mServices.size(); i++) {
            if (mServices.get(i) instanceof Dumpable) {
                ((Dumpable) mServices.get(i)).dump(fileDescriptor, printWriter, strArr);
            }
        }
    }

    private void addService(Object obj) {
        if (obj != null) {
            mServices.add(obj);
        }
    }
}

