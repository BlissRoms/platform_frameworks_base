package com.android.systemui.ambientmusic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.view.View;
import android.widget.TextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.Dependency;
import com.android.systemui.Interpolators;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.doze.util.BurnInHelperKt;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.util.wakelock.DelayedWakeLock;
import com.android.systemui.util.wakelock.WakeLock;

public class AmbientIndicationContainer extends AutoReinflateContainer implements DozeReceiver, View.OnClickListener, StatusBarStateController.StateListener, NotificationMediaManager.MediaListener {
    private int mAmbientIndicationIconSize;
    private Drawable mAmbientMusicAnimation;
    private PendingIntent mAmbientMusicIntent;
    private CharSequence mAmbientMusicText;
    private boolean mAmbientSkipUnlock;
    private int mBurnInPreventionOffset;
    private float mDozeAmount;
    private boolean mDozing;
    private int mDrawablePadding;
    private final Handler mHandler;
    private final Rect mIconBounds = new Rect();
    private int mMediaPlaybackState;
    private boolean mNotificationsHidden;
    private CharSequence mReverseChargingMessage;
    private StatusBar mStatusBar;
    private TextView mText;
    private int mTextColor;
    private ValueAnimator mTextColorAnimator;
    private final WakeLock mWakeLock;

    public void onStateChanged(int state) {
    }

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        Handler handler = new Handler(Looper.getMainLooper());
        mHandler = handler;
        mWakeLock = createWakeLock(mContext, handler);
    }

    @VisibleForTesting
    private WakeLock createWakeLock(Context context, Handler handler) {
        return new DelayedWakeLock(handler, WakeLock.createPartial(context, "AmbientIndication"));
    }

    public void initializeView(StatusBar statusBar) {
        mStatusBar = statusBar;
        addInflateListener(new AutoReinflateContainer.InflateListener() {
            public final void onInflated(View view) {
                mText = (TextView) findViewById(R.id.ambient_indication_text);
                mAmbientMusicAnimation = getResources().getDrawable(R.anim.audioanim_animation, mContext.getTheme());
                mTextColor = mText.getCurrentTextColor();
                mAmbientIndicationIconSize = getResources().getDimensionPixelSize(R.dimen.ambient_indication_icon_size);
                mBurnInPreventionOffset = getResources().getDimensionPixelSize(R.dimen.default_burn_in_prevention_offset);
                mDrawablePadding = mText.getCompoundDrawablePadding();
                updateColors();
                updatePill();
                mText.setOnClickListener(this);
            }
        });
        addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updateBottomPadding();
            }
        });
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ((StatusBarStateController) Dependency.get(StatusBarStateController.class)).addCallback(this);
        ((NotificationMediaManager) Dependency.get(NotificationMediaManager.class)).addCallback(this);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ((StatusBarStateController) Dependency.get(StatusBarStateController.class)).removeCallback(this);
        ((NotificationMediaManager) Dependency.get(NotificationMediaManager.class)).removeCallback(this);
        mMediaPlaybackState = 0;
    }

    public void setAmbientMusic(CharSequence charSequence, PendingIntent pendingIntent, boolean skipUnlock) {
        mAmbientMusicText = charSequence;
        mAmbientMusicIntent = pendingIntent;
        mAmbientSkipUnlock = skipUnlock;
        updatePill();
    }

    public void setReverseChargingMessage(CharSequence charSequence) {
        mReverseChargingMessage = charSequence;
        updatePill();
    }

    private void updatePill() {
        int i = 0;
        if (!TextUtils.isEmpty(mReverseChargingMessage)) {
            Drawable drawable = getResources().getDrawable(R.drawable.ic_qs_reverse_charging, mContext.getTheme());
            mText.setClickable(false);
            mText.setText(mReverseChargingMessage);
            mText.setContentDescription(mReverseChargingMessage);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            Drawable drawable2 = isLayoutRtl() ? null : drawable;
            if (drawable2 != null) {
                drawable = null;
            }
            mText.setCompoundDrawables(drawable2, (Drawable) null, drawable, (Drawable) null);
            mText.setCompoundDrawablePadding(mDrawablePadding);
            mText.setVisibility(0);
            updateBottomPadding();
            return;
        }
        CharSequence charSequence = mAmbientMusicText;
        Drawable drawable3 = mAmbientMusicAnimation;
        boolean z = true;
        boolean z2 = charSequence != null && charSequence.length() == 0;
        mText.setClickable(mAmbientMusicIntent != null);
        mText.setText(charSequence);
        mText.setContentDescription(charSequence);
        if (drawable3 != null) {
            mIconBounds.set(0, 0, drawable3.getIntrinsicWidth(), drawable3.getIntrinsicHeight());
            MathUtils.fitRect(mIconBounds, mAmbientIndicationIconSize);
            drawable3.setBounds(mIconBounds);
        }
        Drawable drawable4 = isLayoutRtl() ? null : drawable3;
        mText.setCompoundDrawables(drawable4, (Drawable) null, drawable4 == null ? drawable3 : null, (Drawable) null);
        mText.setCompoundDrawablePadding(z2 ? 0 : mDrawablePadding);
        boolean z3 = (!TextUtils.isEmpty(charSequence) || z2) && !mNotificationsHidden;
        if (mText.getVisibility() != 0) {
            z = false;
        }
        TextView textView = mText;
        if (!z3) {
            i = 8;
        }
        textView.setVisibility(i);
        if (!z3) {
            mText.animate().cancel();
            if (drawable3 instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) drawable3).reset();
            }
            mHandler.post(mWakeLock.wrap(() -> AmbientIndicationContainer.updatePill()));
        } else if (!z) {
            mWakeLock.acquire("AmbientIndication");
            if (drawable3 instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) drawable3).start();
            }
            TextView textView2 = mText;
            textView2.setTranslationY((float) (textView2.getHeight() / 2));
            mText.setAlpha(0.0f);
            mText.animate().withLayer().alpha(1.0f).translationY(0.0f).setStartDelay(150).setDuration(100).setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animator) {
                    AmbientIndicationContainer.mWakeLock.release("AmbientIndication");
                }
            }).setInterpolator(Interpolators.DECELERATE_QUINT).start();
        } else {
            mHandler.post(mWakeLock.wrap(() -> AmbientIndicationContainer.updatePill()));
        }
        updateBottomPadding();
    }

    private void updateBottomPadding() {
        mStatusBar.getPanelController().setAmbientIndicationBottomPadding(mText.getVisibility() == 0 ? mStatusBar.getNotificationScrollLayout().getBottom() - getTop() : 0);
    }

    public void hideAmbientMusic() {
        setAmbientMusic((CharSequence) null, (PendingIntent) null, false);
    }

    public void onClick(View view) {
        if (mAmbientMusicIntent != null) {
            mStatusBar.wakeUpIfDozing(SystemClock.uptimeMillis(), mText, "AMBIENT_MUSIC_CLICK");
            if (mAmbientSkipUnlock) {
                sendBroadcastWithoutDismissingKeyguard(mAmbientMusicIntent);
            } else {
                mStatusBar.postStartActivityDismissingKeyguard(mAmbientMusicIntent);
            }
        }
    }

    public void onDozingChanged(boolean isDozing) {
        mDozing = isDozing;
        mText.setEnabled(!isDozing);
        updateColors();
        updateBurnInOffsets();
    }

    public void dozeTimeTick() {
        updatePill();
        updateBurnInOffsets();
    }

    private void updateBurnInOffsets() {
        int burnInOffset = BurnInHelperKt.getBurnInOffset(mBurnInPreventionOffset * 2, true);
        int i = mBurnInPreventionOffset;
        setTranslationX(((float) (burnInOffset - i)) * mDozeAmount);
        setTranslationY(((float) (BurnInHelperKt.getBurnInOffset(i * 2, false) - mBurnInPreventionOffset)) * mDozeAmount);
    }

    private void updateColors() {
        ValueAnimator valueAnimator = mTextColorAnimator;
        if (valueAnimator != null && valueAnimator.isRunning()) {
            mTextColorAnimator.cancel();
        }
        int defaultColor = mText.getTextColors().getDefaultColor();
        int color = mDozing ? -1 : mTextColor;
        if (defaultColor != color) {
            ValueAnimator ofArgb = ValueAnimator.ofArgb(new int[]{defaultColor, color});
            mTextColorAnimator = ofArgb;
            ofArgb.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
            mTextColorAnimator.setDuration(500);
            mTextColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public final void onAnimationUpdate(ValueAnimator valueAnimator) {
                    int intValue = ((Integer) valueAnimator.getAnimatedValue()).intValue();
                    mText.setTextColor(intValue);
                    mText.setCompoundDrawableTintList(ColorStateList.valueOf(intValue));

                }
            });
            mTextColorAnimator.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animator) {
                    ValueAnimator unused = AmbientIndicationContainer.mTextColorAnimator = null;
                }
            });
            mTextColorAnimator.start();
        }
    }

    public void onDozeAmountChanged(float linear, float eased) {
        mDozeAmount = eased;
        updateBurnInOffsets();
    }

    private void sendBroadcastWithoutDismissingKeyguard(PendingIntent pendingIntent) {
        if (!pendingIntent.isActivity()) {
            try {
                pendingIntent.send();
            } catch (PendingIntent.CanceledException e) {
                Log.w("AmbientIndication", "Sending intent failed: " + e);
            }
        }
    }

    public void onPrimaryMetadataOrStateChanged(MediaMetadata mediaMetadata, int state) {
        if (mMediaPlaybackState != state) {
            mMediaPlaybackState = state;
            if (isMediaPlaying()) {
                hideAmbientMusic();
            }
        }
    }

    public boolean isMediaPlaying() {
        return NotificationMediaManager.isPlayingState(mMediaPlaybackState);
    }
}
