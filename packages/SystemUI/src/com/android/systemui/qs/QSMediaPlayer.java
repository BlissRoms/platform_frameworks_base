package com.android.systemui.qs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaDescription;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.android.settingslib.media.LocalMediaManager;
import com.android.systemui.C0008R$id;
import com.android.systemui.C0010R$layout;
import com.android.systemui.media.MediaControlPanel;
import com.android.systemui.media.SeekBarObserver;
import com.android.systemui.media.SeekBarViewModel;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.util.SysuiLifecycle;
import com.android.systemui.util.concurrency.DelayableExecutor;
import java.util.concurrent.Executor;

public class QSMediaPlayer extends MediaControlPanel {
    static final int[] QS_ACTION_IDS = {C0008R$id.action0, C0008R$id.action1, C0008R$id.action2, C0008R$id.action3, C0008R$id.action4};
    private final DelayableExecutor mBackgroundExecutor;
    private final Executor mForegroundExecutor;
    private String mPackageName;
    private final QSPanel mParent;
    private final SeekBarObserver mSeekBarObserver = new SeekBarObserver(getView());
    private final SeekBarViewModel mSeekBarViewModel;

    public QSMediaPlayer(Context context, ViewGroup viewGroup, LocalMediaManager localMediaManager, Executor executor, DelayableExecutor delayableExecutor, ActivityStarter activityStarter) {
        super(context, viewGroup, localMediaManager, C0010R$layout.qs_media_panel, QS_ACTION_IDS, executor, delayableExecutor, activityStarter);
        this.mParent = (QSPanel) viewGroup;
        this.mForegroundExecutor = executor;
        this.mBackgroundExecutor = delayableExecutor;
        this.mSeekBarViewModel = new SeekBarViewModel(delayableExecutor);
        this.mSeekBarViewModel.getProgress().observe(SysuiLifecycle.viewAttachLifecycle(viewGroup), this.mSeekBarObserver);
        SeekBar seekBar = (SeekBar) getView().findViewById(C0008R$id.media_progress_bar);
        seekBar.setOnSeekBarChangeListener(this.mSeekBarViewModel.getSeekBarListener());
        seekBar.setOnTouchListener(this.mSeekBarViewModel.getSeekBarTouchListener());
    }

    /* JADX WARNING: Removed duplicated region for block: B:12:0x004d  */
    /* JADX WARNING: Removed duplicated region for block: B:13:0x0059  */
    /* JADX WARNING: Removed duplicated region for block: B:16:0x007a  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void setMediaSession(android.media.session.MediaSession.Token r13, android.media.MediaDescription r14, int r15, int r16, android.app.PendingIntent r17, java.lang.String r18) {
        /*
            r12 = this;
            r10 = r12
            r11 = r15
            r0 = r18
            r10.mPackageName = r0
            android.content.Context r1 = r12.getContext()
            android.content.pm.PackageManager r1 = r1.getPackageManager()
            java.lang.String r2 = "."
            int r2 = r0.lastIndexOf(r2)
            java.lang.String r2 = r0.substring(r2)
            android.graphics.drawable.Drawable r3 = r1.getApplicationIcon(r0)     // Catch:{ NameNotFoundException -> 0x0028 }
            r4 = 0
            android.content.pm.ApplicationInfo r0 = r1.getApplicationInfo(r0, r4)     // Catch:{ NameNotFoundException -> 0x0026 }
            java.lang.CharSequence r2 = r1.getApplicationLabel(r0)     // Catch:{ NameNotFoundException -> 0x0026 }
            goto L_0x0031
        L_0x0026:
            r0 = move-exception
            goto L_0x002a
        L_0x0028:
            r0 = move-exception
            r3 = 0
        L_0x002a:
            java.lang.String r1 = "QSMediaPlayer"
            java.lang.String r4 = "Error getting package information"
            android.util.Log.e(r1, r4, r0)
        L_0x0031:
            r4 = 0
            java.lang.String r8 = r2.toString()
            r9 = 0
            r1 = r12
            r2 = r13
            r5 = r15
            r6 = r16
            r7 = r17
            super.setMediaSession(r2, r3, r4, r5, r6, r7, r8, r9)
            android.widget.LinearLayout r0 = r10.mMediaNotifView
            int r1 = com.android.systemui.C0008R$id.album_art
            android.view.View r0 = r0.findViewById(r1)
            android.widget.ImageView r0 = (android.widget.ImageView) r0
            if (r0 == 0) goto L_0x0059
            com.android.systemui.util.concurrency.DelayableExecutor r1 = r10.mBackgroundExecutor
            com.android.systemui.qs.-$$Lambda$QSMediaPlayer$cpgBeMnZATn4gWEtMed6yyA0KI4 r2 = new com.android.systemui.qs.-$$Lambda$QSMediaPlayer$cpgBeMnZATn4gWEtMed6yyA0KI4
            r3 = r14
            r2.<init>(r14, r0)
            r1.execute(r2)
            goto L_0x005a
        L_0x0059:
            r3 = r14
        L_0x005a:
            android.widget.LinearLayout r0 = r10.mMediaNotifView
            int r1 = com.android.systemui.C0008R$id.header_title
            android.view.View r0 = r0.findViewById(r1)
            android.widget.TextView r0 = (android.widget.TextView) r0
            java.lang.CharSequence r1 = r14.getTitle()
            r0.setText(r1)
            r0.setTextColor(r15)
            android.widget.LinearLayout r0 = r10.mMediaNotifView
            int r1 = com.android.systemui.C0008R$id.header_artist
            android.view.View r0 = r0.findViewById(r1)
            android.widget.TextView r0 = (android.widget.TextView) r0
            if (r0 == 0) goto L_0x0084
            java.lang.CharSequence r1 = r14.getSubtitle()
            r0.setText(r1)
            r0.setTextColor(r15)
        L_0x0084:
            r12.initLongPressMenu(r15)
            r12.resetButtons()
            return
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.systemui.qs.QSMediaPlayer.setMediaSession(android.media.session.MediaSession$Token, android.media.MediaDescription, int, int, android.app.PendingIntent, java.lang.String):void");
    }

    /* access modifiers changed from: private */
    /* renamed from: lambda$setMediaSession$0 */
    public /* synthetic */ void lambda$setMediaSession$0$QSMediaPlayer(MediaDescription mediaDescription, ImageView imageView) {
        processAlbumArt(mediaDescription, imageView);
    }

    public void setMediaSession(MediaSession.Token token, Drawable drawable, Icon icon, int i, int i2, View view, PendingIntent pendingIntent, String str, String str2) {
        int i3 = i;
        int[] iArr = QS_ACTION_IDS;
        super.setMediaSession(token, drawable, icon, i, i2, pendingIntent, str, str2);
        if (view != null) {
            LinearLayout linearLayout = (LinearLayout) view;
            int i4 = 0;
            while (i4 < linearLayout.getChildCount() && i4 < iArr.length) {
                ImageButton imageButton = (ImageButton) this.mMediaNotifView.findViewById(iArr[i4]);
                ImageButton imageButton2 = (ImageButton) linearLayout.findViewById(MediaControlPanel.NOTIF_ACTION_IDS[i4]);
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
                            QSMediaPlayer.lambda$setMediaSession$1(this.f$0, view);
                        }
                    });
                }
                i4++;
            }
            while (i4 < iArr.length) {
                ((ImageButton) this.mMediaNotifView.findViewById(iArr[i4])).setVisibility(8);
                i4++;
            }
        }
        MediaSession.Token token2 = token;
        this.mBackgroundExecutor.execute(new Runnable(new MediaController(getContext(), token), i3) {
            public final /* synthetic */ MediaController f$1;
            public final /* synthetic */ int f$2;

            {
                this.f$1 = r2;
                this.f$2 = r3;
            }

            public final void run() {
                QSMediaPlayer.this.lambda$setMediaSession$2$QSMediaPlayer(this.f$1, this.f$2);
            }
        });
        initLongPressMenu(i3);
    }

    static /* synthetic */ void lambda$setMediaSession$1(ImageButton imageButton, View view) {
        Log.d("QSMediaPlayer", "clicking on other button");
        imageButton.performClick();
    }

    /* access modifiers changed from: private */
    /* renamed from: lambda$setMediaSession$2 */
    public /* synthetic */ void lambda$setMediaSession$2$QSMediaPlayer(MediaController mediaController, int i) {
        this.mSeekBarViewModel.updateController(mediaController, i);
    }

    private void initLongPressMenu(int i) {
        View findViewById = this.mMediaNotifView.findViewById(C0008R$id.media_guts);
        View findViewById2 = this.mMediaNotifView.findViewById(C0008R$id.qs_media_controls_options);
        findViewById2.setMinimumHeight(findViewById.getHeight());
        findViewById2.findViewById(C0008R$id.remove).setOnClickListener(new View.OnClickListener() {
            public final void onClick(View view) {
                QSMediaPlayer.this.lambda$initLongPressMenu$3$QSMediaPlayer(view);
            }
        });
        ((ImageView) findViewById2.findViewById(C0008R$id.remove_icon)).setImageTintList(ColorStateList.valueOf(i));
        ((TextView) findViewById2.findViewById(C0008R$id.remove_text)).setTextColor(i);
        TextView textView = (TextView) findViewById2.findViewById(C0008R$id.cancel);
        textView.setTextColor(i);
        textView.setOnClickListener(new View.OnClickListener(findViewById2, findViewById) {
            public final /* synthetic */ View f$0;
            public final /* synthetic */ View f$1;

            {
                this.f$0 = r1;
                this.f$1 = r2;
            }

            public final void onClick(View view) {
                QSMediaPlayer.lambda$initLongPressMenu$4(this.f$0, this.f$1, view);
            }
        });
        this.mMediaNotifView.setOnLongClickListener((View.OnLongClickListener) null);
        findViewById2.setVisibility(8);
        findViewById.setVisibility(0);
    }

    /* access modifiers changed from: private */
    /* renamed from: lambda$initLongPressMenu$3 */
    public /* synthetic */ void lambda$initLongPressMenu$3$QSMediaPlayer(View view) {
        removePlayer();
    }

    static /* synthetic */ void lambda$initLongPressMenu$4(View view, View view2, View view3) {
        view.setVisibility(8);
        view2.setVisibility(0);
    }

    /* access modifiers changed from: protected */
    public void resetButtons() {
        super.resetButtons();
        this.mSeekBarViewModel.clearController();
        this.mMediaNotifView.setOnLongClickListener(new View.OnLongClickListener(this.mMediaNotifView.findViewById(C0008R$id.media_guts), this.mMediaNotifView.findViewById(C0008R$id.qs_media_controls_options)) {
            public final /* synthetic */ View f$0;
            public final /* synthetic */ View f$1;

            {
                this.f$0 = r1;
                this.f$1 = r2;
            }

            public final boolean onLongClick(View view) {
                return QSMediaPlayer.lambda$resetButtons$5(this.f$0, this.f$1, view);
            }
        });
    }

    static /* synthetic */ boolean lambda$resetButtons$5(View view, View view2, View view3) {
        view.setVisibility(8);
        view2.setVisibility(0);
        return true;
    }

    public void setListening(boolean z) {
        this.mSeekBarViewModel.setListening(z);
    }

    public void removePlayer() {
        Log.d("QSMediaPlayer", "removing player from parent: " + this.mParent);
        this.mForegroundExecutor.execute(new Runnable() {
            public final void run() {
                QSMediaPlayer.this.lambda$removePlayer$6$QSMediaPlayer();
            }
        });
    }

    /* access modifiers changed from: private */
    /* renamed from: lambda$removePlayer$6 */
    public /* synthetic */ void lambda$removePlayer$6$QSMediaPlayer() {
        this.mParent.removeMediaPlayer(this);
    }

    public String getMediaPlayerPackage() {
        if (getController() == null) {
            return this.mPackageName;
        }
        return super.getMediaPlayerPackage();
    }
}
