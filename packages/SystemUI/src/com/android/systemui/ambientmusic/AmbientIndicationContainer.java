/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.ambientmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.ambientmusic.AmbientIndicationInflateListener;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.phone.StatusBar;

public class AmbientIndicationContainer extends AutoReinflateContainer implements
        NotificationMediaManager.MediaListener {

    //private final int mFODmargin;
    //private boolean mHasInDisplayFingerprint;
    private View mAmbientIndication;
    private boolean mDozing;
    private boolean mKeyguard;
    private StatusBar mStatusBar;
    private TextView mText;
    private Context mContext;

    private String mMediaTitle;
    private String mTrackInfoSeparator;
    private boolean mNpInfoAvailable;
    private boolean mVisible;
    private boolean mMediaIsVisible;

    protected NotificationMediaManager mMediaManager;

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaManager.addCallback(this);
        mTrackInfoSeparator = getResources().getString(R.string.ambientmusic_songinfo);
        /*mHasInDisplayFingerprint = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_needCustomFODView);*/
        /*mFODmargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_fod_view_margin);*/
    }

    public void initializeView(StatusBar statusBar) {
        mStatusBar = statusBar;
        addInflateListener(new AmbientIndicationInflateListener(this));
        updateAmbientIndicationView(AmbientIndicationContainer.this);
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
    }

    /*private void updatePosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) this.getLayoutParams();
        if (mHasInDisplayFingerprint) {
            lp.setMargins(0, 0, 0, mFODmargin);
        }
        this.setLayoutParams(lp);
    }*/

    public void updateKeyguardState(boolean keyguard) {
        if (mKeyguard != keyguard) {
            mKeyguard = keyguard;
            setVisibility(shouldShow());
        }
    }

    public void updateDozingState(boolean dozing) {
        if (mDozing != dozing) {
            mDozing = dozing;
            setVisibility(shouldShow());
        }
    }

    private void setVisibility(boolean shouldShow) {
        if (mVisible != shouldShow) {
            mVisible = shouldShow;
            mAmbientIndication.setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
            /*if (mHasInDisplayFingerprint && shouldShow) {
                updatePosition();
            }*/
        }
    }

    private boolean shouldShow() {
        return mKeyguard && !mDozing && mNpInfoAvailable;
    }

    /**
     * Called whenever new media metadata is available.
     * @param metadata New metadata.
     */
    @Override
    public void onPrimaryMetadataOrStateChanged(MediaMetadata metadata,
            @PlaybackState.State int state) {
        CharSequence npTitle = mMediaManager.getNowPlayingTrack();
        boolean nowPlayingAvailable = npTitle != null;

        if (nowPlayingAvailable == mMediaIsVisible && TextUtils.equals(npTitle, mMediaTitle)) {
            return;
        }
        mMediaTitle = nowPlayingAvailable ? npTitle.toString() : null;
        mMediaIsVisible = nowPlayingAvailable;

        if (nowPlayingAvailable) {
            mNpInfoAvailable = true;
            if (mMediaTitle.length() >= 40) {
                mMediaTitle = shortenMediaTitle(mMediaTitle);
            }
            mText.setText(mMediaTitle);
            setVisibility(shouldShow());
        } else {
            hideIndication();
        }
    }

    private String shortenMediaTitle(String input) {
        int cutPos = input.lastIndexOf("\"");
        if (cutPos > 25) { // only shorten the song title if it is too long
            String artist = input.substring(cutPos + 1, input.length());
            int artistLenght = 10;
            artistLenght = (artist.length() < artistLenght) ? artist.length() : artistLenght;
            cutPos = cutPos > 34 ? 30 - artistLenght : cutPos - artistLenght - 4;
            return input.substring(0, cutPos) + "...\"" + artist;
        } else { // otherwise the original string is returned
            return input;
        }
    }

    private void hideIndication() {
        mNpInfoAvailable = false;
        mText.setText(null);
        setVisibility(shouldShow());
    }
}
