package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Build;
import android.transition.Fade;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.keyguard.dagger.KeyguardStatusViewScope;
import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;
import com.android.systemui.plugins.ClockPlugin;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.TimeZone;

/**
 * Switch to show plugin clock when plugin is connected, otherwise it will show default clock.
 */
@KeyguardStatusViewScope
public class KeyguardClockSwitch extends RelativeLayout {

    private static final String TAG = "KeyguardClockSwitch";

    private static final long CLOCK_OUT_MILLIS = 150;
    private static final long CLOCK_IN_MILLIS = 200;
    private static final long STATUS_AREA_MOVE_MILLIS = 350;

    @IntDef({LARGE, SMALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ClockSize { }

    public static final int LARGE = 0;
    public static final int SMALL = 1;

    /**
     * Optional/alternative clock injected via plugin.
     */
    private ClockPlugin mClockPlugin;

    /**
     * Frame for small/large clocks
     */
    private FrameLayout mClockFrame;
    private FrameLayout mLargeClockFrame;
    private AnimatableClockView mClockView;
    private AnimatableClockView mLargeClockView;

    private View mStatusArea;
    private int mSmartspaceTopOffset;

    /**
     * Maintain state so that a newly connected plugin can be initialized.
     */
    private float mDarkAmount;

    /**
     * Indicates which clock is currently displayed - should be one of {@link ClockSize}.
     * Use null to signify it is uninitialized.
     */
    @ClockSize private Integer mDisplayedClockSize = null;

    @VisibleForTesting AnimatorSet mClockInAnim = null;
    @VisibleForTesting AnimatorSet mClockOutAnim = null;
    private ObjectAnimator mStatusAreaAnim = null;

    /**
     * If the Keyguard Slice has a header (big center-aligned text.)
     */
    private boolean mSupportsDarkText;
    private int[] mColorPalette;

    private int mClockSwitchYAmount;
    @VisibleForTesting boolean mChildrenAreLaidOut = false;

    public KeyguardClockSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mDisplayedClockSize != null) {
            boolean landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
            boolean useLargeClock = mDisplayedClockSize == LARGE && !landscape;
            updateClockViews(useLargeClock, /* animate */ true);
        }
    }

    /**
     * Apply dp changes on font/scale change
     */
    public void onDensityOrFontScaleChanged() {
        mLargeClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mContext.getResources()
                .getDimensionPixelSize(R.dimen.large_clock_text_size));
        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mContext.getResources()
                .getDimensionPixelSize(R.dimen.clock_text_size));

        mClockSwitchYAmount = mContext.getResources().getDimensionPixelSize(
                R.dimen.keyguard_clock_switch_y_shift);

        mSmartspaceTopOffset = mContext.getResources().getDimensionPixelSize(
                R.dimen.keyguard_smartspace_top_offset);
    }

    public void onThemeChanged() {
        refreshLockFont();
    }

    /**
     * Returns if this view is presenting a custom clock, or the default implementation.
     */
    public boolean hasCustomClock() {
        return mClockPlugin != null;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mClockFrame = findViewById(R.id.lockscreen_clock_view);
        mClockView = findViewById(R.id.animatable_clock_view);
        mLargeClockFrame = findViewById(R.id.lockscreen_clock_view_large);
        mLargeClockView = findViewById(R.id.animatable_clock_view_large);
        mStatusArea = findViewById(R.id.keyguard_status_area);

        onDensityOrFontScaleChanged();
        onThemeChanged();
        refreshLockFont();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 28);
    }

    void setClockPlugin(ClockPlugin plugin, int statusBarState) {
        // Disconnect from existing plugin.
        if (mClockPlugin != null) {
            View smallClockView = mClockPlugin.getView();
            if (smallClockView != null && smallClockView.getParent() == mClockFrame) {
                mClockFrame.removeView(smallClockView);
            }
            View bigClockView = mClockPlugin.getBigClockView();
            if (bigClockView != null && bigClockView.getParent() == mLargeClockFrame) {
                mLargeClockFrame.removeView(bigClockView);
            }
            mClockPlugin.onDestroyView();
            mClockPlugin = null;
        }
        if (plugin == null) {
            mClockView.setVisibility(View.VISIBLE);
            mLargeClockView.setVisibility(View.VISIBLE);
            refreshLockFont();
            return;
        }
        // Attach small and big clock views to hierarchy.
        View smallClockView = plugin.getView();
        if (smallClockView != null) {
            mClockFrame.addView(smallClockView, -1,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            mClockView.setVisibility(View.GONE);
        }
        View bigClockView = plugin.getBigClockView();
        if (bigClockView != null) {
            mLargeClockFrame.addView(bigClockView);
            mLargeClockView.setVisibility(View.GONE);
        }

        // Initialize plugin parameters.
        mClockPlugin = plugin;
        mClockPlugin.setStyle(getPaint().getStyle());
        mClockPlugin.setTextColor(getCurrentTextColor());
        mClockPlugin.setDarkAmount(mDarkAmount);
        if (mColorPalette != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    /**
     * It will also update plugin setStyle if plugin is connected.
     */
    public void setStyle(Style style) {
        if (mClockPlugin != null) {
            mClockPlugin.setStyle(style);
        }
    }

    /**
     * It will also update plugin setTextColor if plugin is connected.
     */
    public void setTextColor(int color) {
        if (mClockPlugin != null) {
            mClockPlugin.setTextColor(color);
        }
    }

    private void updateClockViews(boolean useLargeClock, boolean animate) {
        refreshLockFont();
        if (mClockInAnim != null) mClockInAnim.cancel();
        if (mClockOutAnim != null) mClockOutAnim.cancel();
        if (mStatusAreaAnim != null) mStatusAreaAnim.cancel();

        mClockInAnim = null;
        mClockOutAnim = null;
        mStatusAreaAnim = null;

        View in, out;
        int direction = 1;
        float statusAreaYTranslation;
        if (useLargeClock) {
            out = mClockFrame;
            in = mLargeClockFrame;
            if (indexOfChild(in) == -1) addView(in);
            direction = -1;
            statusAreaYTranslation = mClockFrame.getTop() - mStatusArea.getTop()
                    + mSmartspaceTopOffset;
        } else {
            in = mClockFrame;
            out = mLargeClockFrame;
            statusAreaYTranslation = 0f;

            // Must remove in order for notifications to appear in the proper place
            removeView(out);
        }

        if (!animate) {
            out.setAlpha(0f);
            in.setAlpha(1f);
            in.setVisibility(VISIBLE);
            mStatusArea.setTranslationY(statusAreaYTranslation);
            return;
        }

        mClockOutAnim = new AnimatorSet();
        mClockOutAnim.setDuration(CLOCK_OUT_MILLIS);
        mClockOutAnim.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
        mClockOutAnim.playTogether(
                ObjectAnimator.ofFloat(out, View.ALPHA, 0f),
                ObjectAnimator.ofFloat(out, View.TRANSLATION_Y, 0,
                        direction * -mClockSwitchYAmount));
        mClockOutAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mClockOutAnim = null;
            }
        });

        in.setAlpha(0);
        in.setVisibility(View.VISIBLE);
        mClockInAnim = new AnimatorSet();
        mClockInAnim.setDuration(CLOCK_IN_MILLIS);
        mClockInAnim.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
        mClockInAnim.playTogether(ObjectAnimator.ofFloat(in, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(in, View.TRANSLATION_Y, direction * mClockSwitchYAmount, 0));
        mClockInAnim.setStartDelay(CLOCK_OUT_MILLIS / 2);
        mClockInAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mClockInAnim = null;
            }
        });

        mClockInAnim.start();
        mClockOutAnim.start();

        mStatusAreaAnim = ObjectAnimator.ofFloat(mStatusArea, View.TRANSLATION_Y,
                statusAreaYTranslation);
        mStatusAreaAnim.setDuration(STATUS_AREA_MOVE_MILLIS);
        mStatusAreaAnim.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mStatusAreaAnim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                mStatusAreaAnim = null;
            }
        });
        mStatusAreaAnim.start();
    }

    /**
     * Set the amount (ratio) that the device has transitioned to doze.
     *
     * @param darkAmount Amount of transition to doze: 1f for doze and 0f for awake.
     */
    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        if (mClockPlugin != null) {
            mClockPlugin.setDarkAmount(darkAmount);
        }
    }

    /**
     * Display the desired clock and hide the other one
     *
     * @return true if desired clock appeared and false if it was already visible
     */
    boolean switchToClock(@ClockSize int clockSize, boolean animate) {
        if (mDisplayedClockSize != null && clockSize == mDisplayedClockSize) {
            return false;
        }
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean useLargeClock = clockSize == LARGE && !landscape;

        // let's make sure clock is changed only after all views were laid out so we can
        // translate them properly
        if (mChildrenAreLaidOut) {
            updateClockViews(useLargeClock, animate);
        }

        mDisplayedClockSize = clockSize;
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (mDisplayedClockSize != null && !mChildrenAreLaidOut) {
            post(() -> updateClockViews(mDisplayedClockSize == LARGE, /* animate */ true));
        }

        mChildrenAreLaidOut = true;
    }

    public Paint getPaint() {
        return mClockView.getPaint();
    }

    public int getCurrentTextColor() {
        return mClockView.getCurrentTextColor();
    }

    public float getTextSize() {
        return mClockView.getTextSize();
    }

    /**
     * Refresh the time of the clock, due to either time tick broadcast or doze time tick alarm.
     */
    public void refresh() {
        if (mClockPlugin != null) {
            mClockPlugin.onTimeTick();
        }
        refreshLockFont();
    }

    /**
     * Notifies that the time zone has changed.
     */
    public void onTimeZoneChanged(TimeZone timeZone) {
        if (mClockPlugin != null) {
            mClockPlugin.onTimeZoneChanged(timeZone);
        }
        refreshLockFont();
    }

    public void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 28;

        switch (lockClockFont) {
        case 0:
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            break;
        case 1:
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            mLargeClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            break;
        case 2:
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            break;
        case 3:
            mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
            break;
        case 4:
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
            break;
        case 5:
            mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            break;
        case 6:
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
            break;
        case 7:
            mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            break;
        case 8:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            break;
        case 9:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
            break;
        case 10:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            break;
        case 11:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
            break;
        case 12:
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            break;
        case 13:
            mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
            break;
        case 14:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
            break;
        case 15:
            mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
            break;
        case 16:
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            break;
        case 17:
            mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
            break;
        case 18:
            mClockView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
            break;
        case 19:
            mClockView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
            mLargeClockView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
            break;
        case 20:
            mClockView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
            break;
        case 21:
            mClockView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            break;
        case 22:
            mClockView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            mLargeClockView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
            break;
        case 23:
            mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD));
            mLargeClockView.setTypeface(Typeface.create("serif", Typeface.BOLD));
            break;
        case 24:
            mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            mLargeClockView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
            break;
        case 25:
            mClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
            break;
        case 26:
            mClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
            break;
        case 27:
            mClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
            break;
        case 28:
		default:
            mClockView.setTypeface(Typeface.create("googlesansmedium-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("googlesansmedium-sys", Typeface.NORMAL));
            break;
        case 29:
            mClockView.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
            break;
        case 30:
            mClockView.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
            break;
        case 31:
            mClockView.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
            break;
        case 32:
            mClockView.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
            break;
        case 33:
            mClockView.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
            break;
        case 34:
            mClockView.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
            break;
        case 35:
            mClockView.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
            mLargeClockView.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
            break;
        }
    }

    /**
     * Notifies that the time format has changed.
     *
     * @param timeFormat "12" for 12-hour format, "24" for 24-hour format
     */
    public void onTimeFormatChanged(String timeFormat) {
        if (mClockPlugin != null) {
            mClockPlugin.onTimeFormatChanged(timeFormat);
        }
    }

    void updateColors(ColorExtractor.GradientColors colors) {
        mSupportsDarkText = colors.supportsDarkText();
        mColorPalette = colors.getColorPalette();
        if (mClockPlugin != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    public void dump(PrintWriter pw, String[] args) {
        pw.println("KeyguardClockSwitch:");
        pw.println("  mClockPlugin: " + mClockPlugin);
        pw.println("  mClockFrame: " + mClockFrame);
        pw.println("  mLargeClockFrame: " + mLargeClockFrame);
        pw.println("  mStatusArea: " + mStatusArea);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mSupportsDarkText: " + mSupportsDarkText);
        pw.println("  mColorPalette: " + Arrays.toString(mColorPalette));
        pw.println("  mDisplayedClockSize: " + mDisplayedClockSize);
    }
}
