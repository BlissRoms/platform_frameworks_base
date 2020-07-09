package com.android.systemui.qs;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

public class QSMediaBrowser {
    /* access modifiers changed from: private */
    public final Callback mCallback;
    /* access modifiers changed from: private */
    public ComponentName mComponentName;
    private final MediaBrowser.ConnectionCallback mConnectionCallback = new MediaBrowser.ConnectionCallback() {
        public void onConnected() {
            if (QSMediaBrowser.this.mMediaBrowser.isConnected()) {
                QSMediaBrowser.this.mCallback.onConnected();
                Log.d("QSMediaBrowser", "Service connected for " + QSMediaBrowser.this.mComponentName);
                QSMediaBrowser.this.mMediaBrowser.subscribe(QSMediaBrowser.this.mMediaBrowser.getRoot(), QSMediaBrowser.this.mSubscriptionCallback);
            }
        }

        public void onConnectionSuspended() {
            Log.d("QSMediaBrowser", "Connection suspended for " + QSMediaBrowser.this.mComponentName);
            QSMediaBrowser.this.mCallback.onError();
            QSMediaBrowser.this.disconnect();
        }

        public void onConnectionFailed() {
            Log.e("QSMediaBrowser", "Connection failed for " + QSMediaBrowser.this.mComponentName);
            QSMediaBrowser.this.mCallback.onError();
            QSMediaBrowser.this.disconnect();
        }
    };
    /* access modifiers changed from: private */
    public final Context mContext;
    /* access modifiers changed from: private */
    public MediaBrowser mMediaBrowser;
    /* access modifiers changed from: private */
    public final MediaBrowser.SubscriptionCallback mSubscriptionCallback = new MediaBrowser.SubscriptionCallback() {
        public void onChildrenLoaded(String str, List<MediaBrowser.MediaItem> list) {
            if (list.size() == 0) {
                Log.e("QSMediaBrowser", "No children found for " + QSMediaBrowser.this.mComponentName);
                return;
            }
            MediaBrowser.MediaItem mediaItem = list.get(0);
            MediaDescription description = mediaItem.getDescription();
            if (mediaItem.isPlayable()) {
                QSMediaBrowser.this.mCallback.addTrack(description, QSMediaBrowser.this.mMediaBrowser.getServiceComponent(), QSMediaBrowser.this);
            } else {
                Log.e("QSMediaBrowser", "Child found but not playable for " + QSMediaBrowser.this.mComponentName);
            }
            QSMediaBrowser.this.disconnect();
        }

        public void onError(String str) {
            Log.e("QSMediaBrowser", "Subscribe error for " + QSMediaBrowser.this.mComponentName + ": " + str);
            QSMediaBrowser.this.mCallback.onError();
            QSMediaBrowser.this.disconnect();
        }

        public void onError(String str, Bundle bundle) {
            Log.e("QSMediaBrowser", "Subscribe error for " + QSMediaBrowser.this.mComponentName + ": " + str + ", options: " + bundle);
            QSMediaBrowser.this.mCallback.onError();
            QSMediaBrowser.this.disconnect();
        }
    };

    public static class Callback {
        public void addTrack(MediaDescription mediaDescription, ComponentName componentName, QSMediaBrowser qSMediaBrowser) {
        }

        public void onConnected() {
        }

        public void onError() {
        }
    }

    public QSMediaBrowser(Context context, Callback callback, ComponentName componentName) {
        this.mContext = context;
        this.mCallback = callback;
        this.mComponentName = componentName;
    }

    public void findRecentMedia() {
        Log.d("QSMediaBrowser", "Connecting to " + this.mComponentName);
        disconnect();
        Bundle bundle = new Bundle();
        bundle.putBoolean("android.service.media.extra.RECENT", true);
        MediaBrowser mediaBrowser = new MediaBrowser(this.mContext, this.mComponentName, this.mConnectionCallback, bundle);
        this.mMediaBrowser = mediaBrowser;
        mediaBrowser.connect();
    }

    public void disconnect() {
        MediaBrowser mediaBrowser = this.mMediaBrowser;
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
        }
        this.mMediaBrowser = null;
    }

    public void restart() {
        disconnect();
        Bundle bundle = new Bundle();
        bundle.putBoolean("android.service.media.extra.RECENT", true);
        MediaBrowser mediaBrowser = new MediaBrowser(this.mContext, this.mComponentName, new MediaBrowser.ConnectionCallback() {
            public void onConnected() {
                Log.d("QSMediaBrowser", "Connected for restart " + QSMediaBrowser.this.mMediaBrowser.isConnected());
                MediaController mediaController = new MediaController(QSMediaBrowser.this.mContext, QSMediaBrowser.this.mMediaBrowser.getSessionToken());
                mediaController.getTransportControls();
                mediaController.getTransportControls().prepare();
                mediaController.getTransportControls().play();
                QSMediaBrowser.this.mCallback.onConnected();
            }

            public void onConnectionFailed() {
                QSMediaBrowser.this.mCallback.onError();
            }

            public void onConnectionSuspended() {
                QSMediaBrowser.this.mCallback.onError();
            }
        }, bundle);
        this.mMediaBrowser = mediaBrowser;
        mediaBrowser.connect();
    }

    public MediaSession.Token getToken() {
        MediaBrowser mediaBrowser = this.mMediaBrowser;
        if (mediaBrowser == null || !mediaBrowser.isConnected()) {
            return null;
        }
        return this.mMediaBrowser.getSessionToken();
    }

    public PendingIntent getAppIntent() {
        return PendingIntent.getActivity(this.mContext, 0, this.mContext.getPackageManager().getLaunchIntentForPackage(this.mComponentName.getPackageName()), 0);
    }

    public void testConnection() {
        disconnect();
        AnonymousClass4 r0 = new MediaBrowser.ConnectionCallback() {
            public void onConnected() {
                Log.d("QSMediaBrowser", "connected");
                if (QSMediaBrowser.this.mMediaBrowser.getRoot() == null) {
                    QSMediaBrowser.this.mCallback.onError();
                } else {
                    QSMediaBrowser.this.mCallback.onConnected();
                }
            }

            public void onConnectionSuspended() {
                Log.d("QSMediaBrowser", "suspended");
                QSMediaBrowser.this.mCallback.onError();
            }

            public void onConnectionFailed() {
                Log.d("QSMediaBrowser", "failed");
                QSMediaBrowser.this.mCallback.onError();
            }
        };
        Bundle bundle = new Bundle();
        bundle.putBoolean("android.service.media.extra.RECENT", true);
        MediaBrowser mediaBrowser = new MediaBrowser(this.mContext, this.mComponentName, r0, bundle);
        this.mMediaBrowser = mediaBrowser;
        mediaBrowser.connect();
    }
}
