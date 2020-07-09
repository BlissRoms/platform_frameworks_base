package com.android.systemui.qs;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import com.android.settingslib.media.LocalMediaManager;
import com.android.systemui.C0008R$id;
import com.android.systemui.C0010R$layout;
import com.android.systemui.media.MediaControlPanel;
import com.android.systemui.plugins.ActivityStarter;
import java.util.concurrent.Executor;

public class QuickQSMediaPlayer extends MediaControlPanel {
    private static final int[] QQS_ACTION_IDS = {C0008R$id.action0, C0008R$id.action1, C0008R$id.action2};

    public QuickQSMediaPlayer(Context context, ViewGroup viewGroup, Executor executor, Executor executor2, ActivityStarter activityStarter) {
        super(context, viewGroup, (LocalMediaManager) null, C0010R$layout.qqs_media_panel, QQS_ACTION_IDS, executor, executor2, activityStarter);
    }

    public void setMediaSession(MediaSession.Token token, Drawable drawable, Icon icon, int i, int i2, View view, int[] iArr, PendingIntent pendingIntent, String str) {
        MediaSession.Token token2 = token;
        int[] iArr2 = iArr;
        int[] iArr3 = QQS_ACTION_IDS;
        String packageName = getController() != null ? getController().getPackageName() : "";
        MediaController mediaController = new MediaController(getContext(), token);
        MediaSession.Token mediaSessionToken = getMediaSessionToken();
        int i3 = 0;
        boolean z = mediaSessionToken != null && mediaSessionToken.equals(token) && packageName.equals(mediaController.getPackageName());
        if (getController() == null || z || isPlaying(mediaController)) {
            super.setMediaSession(token, drawable, icon, i, i2, pendingIntent, (String) null, str);
            LinearLayout linearLayout = (LinearLayout) view;
            if (iArr2 != null) {
                int min = Math.min(Math.min(iArr2.length, linearLayout.getChildCount()), iArr3.length);
                int i4 = 0;
                while (i4 < min) {
                    ImageButton imageButton = (ImageButton) this.mMediaNotifView.findViewById(iArr3[i4]);
                    ImageButton imageButton2 = (ImageButton) linearLayout.findViewById(MediaControlPanel.NOTIF_ACTION_IDS[iArr2[i4]]);
                    if (imageButton2 == null || imageButton2.getDrawable() == null || imageButton2.getVisibility() != 0) {
                        imageButton.setVisibility(8);
                    } else {
                        imageButton.setImageDrawable(imageButton2.getDrawable().mutate());
                        imageButton.setVisibility(0);
                        imageButton.setOnClickListener(new View.OnClickListener(imageButton2) {
                            public final /* synthetic */ ImageButton f$0;

                            {
                                this.f$0 = r1;
                            }

                            public final void onClick(View view) {
                                this.f$0.performClick();
                            }
                        });
                    }
                    i4++;
                }
                i3 = i4;
            }
            while (i3 < iArr3.length) {
                ((ImageButton) this.mMediaNotifView.findViewById(iArr3[i3])).setVisibility(8);
                i3++;
            }
        }
    }
}
