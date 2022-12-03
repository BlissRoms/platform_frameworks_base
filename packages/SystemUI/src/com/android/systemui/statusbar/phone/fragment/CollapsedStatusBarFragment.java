/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.statusbar.phone.fragment;

import static android.app.StatusBarManager.DISABLE2_SYSTEM_ICONS;
import static android.app.StatusBarManager.DISABLE_CLOCK;
import static android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS;
import static android.app.StatusBarManager.DISABLE_ONGOING_CALL_CHIP;
import static android.app.StatusBarManager.DISABLE_SYSTEM_INFO;

import static com.android.systemui.statusbar.events.SystemStatusAnimationSchedulerKt.IDLE;
import static com.android.systemui.statusbar.events.SystemStatusAnimationSchedulerKt.SHOWING_PERSISTENT_DOT;

import android.animation.Animator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;
import com.android.systemui.battery.BatteryMeterView;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.DisableFlagsLogger.DisableState;
import com.android.systemui.statusbar.OperatorNameView;
import com.android.systemui.statusbar.OperatorNameViewController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.events.SystemStatusAnimationCallback;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.phone.NotificationIconAreaController;
import com.android.systemui.statusbar.phone.NotificationPanelViewController;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.phone.StatusBarHideIconsForBouncerManager;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.phone.StatusBarIconController.DarkIconManager;
import com.android.systemui.statusbar.phone.StatusBarLocationPublisher;
import com.android.systemui.statusbar.phone.StatusIconContainer;
import com.android.systemui.statusbar.phone.fragment.dagger.StatusBarFragmentComponent;
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallController;
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallListener;
import com.android.systemui.statusbar.phone.panelstate.PanelExpansionStateManager;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.EncryptionHelper;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.CarrierConfigTracker;
import com.android.systemui.util.CarrierConfigTracker.CarrierConfigChangedListener;
import com.android.systemui.util.CarrierConfigTracker.DefaultDataSubscriptionChangedListener;
import com.android.systemui.util.settings.SecureSettings;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Contains the collapsed status bar and handles hiding/showing based on disable flags
 * and keyguard state. Also manages lifecycle to make sure the views it contains are being
 * updated by the StatusBarIconController and DarkIconManager while it is attached.
 */
@SuppressLint("ValidFragment")
public class CollapsedStatusBarFragment extends Fragment implements CommandQueue.Callbacks,
        StatusBarStateController.StateListener,
        SystemStatusAnimationCallback {

    public static final String TAG = "CollapsedStatusBarFragment";
    private static final String EXTRA_PANEL_STATE = "panel_state";
    public static final String STATUS_BAR_ICON_MANAGER_TAG = "status_bar_icon_manager";
    public static final int FADE_IN_DURATION = 320;
    public static final int FADE_IN_DELAY = 50;
    private StatusBarFragmentComponent mStatusBarFragmentComponent;
    private PhoneStatusBarView mStatusBar;
    private final StatusBarStateController mStatusBarStateController;
    private final KeyguardStateController mKeyguardStateController;
    private final NotificationPanelViewController mNotificationPanelViewController;
    private final NetworkController mNetworkController;
    private LinearLayout mSystemIconArea;
    private View mClockView;
    private View mOngoingCallChip;
    private View mNotificationIconAreaInner;
    private int mDisabled1;
    private int mDisabled2;
    private DarkIconManager mDarkIconManager;
    private final StatusBarFragmentComponent.Factory mStatusBarFragmentComponentFactory;
    private final CommandQueue mCommandQueue;
    private final CollapsedStatusBarFragmentLogger mCollapsedStatusBarFragmentLogger;
    private final OperatorNameViewController.Factory mOperatorNameViewControllerFactory;
    private final OngoingCallController mOngoingCallController;
    private final SystemStatusAnimationScheduler mAnimationScheduler;
    private final StatusBarLocationPublisher mLocationPublisher;
    private final FeatureFlags mFeatureFlags;
    private final NotificationIconAreaController mNotificationIconAreaController;
    private final PanelExpansionStateManager mPanelExpansionStateManager;
    private final StatusBarIconController mStatusBarIconController;
    private final CarrierConfigTracker mCarrierConfigTracker;
    private final StatusBarHideIconsForBouncerManager mStatusBarHideIconsForBouncerManager;
    private final SecureSettings mSecureSettings;
    private final Executor mMainExecutor;
    private BatteryMeterView mBatteryMeterView;
    private StatusIconContainer mStatusIcons;
    private int mSignalClusterEndPadding = 0;

    private int mClockStyle;

    private View mBatteryBars[] = new View[2];

    private ImageView mBlissLogo;
    private ImageView mBlissLogoRight;
    private int mTintColor = Color.WHITE;
    private int mLogoStyle;
    private int mShowLogo;
    private int mLogoColor;
    // Custom Image as SB LOGO
    private boolean mCustomSbLogoEnabled;

    private List<String> mBlockedIcons = new ArrayList<>();

    private LinearLayout mCenterClockLayout;
    private View mRightClock;
    private boolean mShowClock = true;
    private final Handler mHandler = new Handler();

    private class SettingsObserver extends ContentObserver {
       SettingsObserver(Handler handler) {
           super(handler);
       }

       void observe() {
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK),
                    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUSBAR_CLOCK_STYLE),
                    false, this, UserHandle.USER_ALL);
	 mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO),
                    false, this, UserHandle.USER_ALL);
	 mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_STYLE),
		    false, this, UserHandle.USER_ALL);
	 mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_LOGO_COLOR),
		    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CUSTOM_SB_LOGO_ENABLED),
                    false, this, UserHandle.USER_ALL);
         mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CUSTOM_SB_LOGO_IMAGE),
                    false, this, UserHandle.USER_ALL);
       }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if ((uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO_STYLE))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.STATUS_BAR_LOGO_COLOR))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.CUSTOM_SB_LOGO_ENABLED))) ||
                (uri.equals(Settings.System.getUriFor(Settings.System.CUSTOM_SB_LOGO_IMAGE)))){
                updateLogoSettings(true);
	    }
            updateSettings(true);
        }
    }

    private SettingsObserver mSettingsObserver;
    private ContentResolver mContentResolver;

    private SignalCallback mSignalCallback = new SignalCallback() {
        @Override
        public void setIsAirplaneMode(@NonNull IconState icon) {
            mCommandQueue.recomputeDisableFlags(getContext().getDisplayId(), true /* animate */);
        }
    };

    private final OngoingCallListener mOngoingCallListener = new OngoingCallListener() {
        @Override
        public void onOngoingCallStateChanged(boolean animate) {
            disable(getContext().getDisplayId(), mDisabled1, mDisabled2, animate);
        }
    };
    private OperatorNameViewController mOperatorNameViewController;

    private StatusBarSystemEventAnimator mSystemEventAnimator;

	private BatteryMeterView.BatteryMeterViewCallbacks mBatteryMeterViewCallback =
            new BatteryMeterView.BatteryMeterViewCallbacks() {
        @Override
        public void onHiddenBattery(boolean hidden) {
            mStatusIcons.setPadding(
                    mStatusIcons.getPaddingLeft(), mStatusIcons.getPaddingTop(),
                    (hidden ? 0 : mSignalClusterEndPadding), mStatusIcons.getPaddingBottom());
        }
    };

    private final CarrierConfigChangedListener mCarrierConfigCallback =
            new CarrierConfigChangedListener() {
                @Override
                public void onCarrierConfigChanged() {
                    if (mOperatorNameViewController == null) {
                        initOperatorName();
                    } else {
                        // Already initialized, KeyguardUpdateMonitorCallback will handle the update
                    }
                }
            };

    private final DefaultDataSubscriptionChangedListener mDefaultDataListener =
            new DefaultDataSubscriptionChangedListener() {
                @Override
                public void onDefaultSubscriptionChanged(int subId) {
                    if (mOperatorNameViewController == null) {
                        initOperatorName();
                    }
                }
            };

    @SuppressLint("ValidFragment")
    public CollapsedStatusBarFragment(
            StatusBarFragmentComponent.Factory statusBarFragmentComponentFactory,
            OngoingCallController ongoingCallController,
            SystemStatusAnimationScheduler animationScheduler,
            StatusBarLocationPublisher locationPublisher,
            NotificationIconAreaController notificationIconAreaController,
            PanelExpansionStateManager panelExpansionStateManager,
            FeatureFlags featureFlags,
            StatusBarIconController statusBarIconController,
            StatusBarHideIconsForBouncerManager statusBarHideIconsForBouncerManager,
            KeyguardStateController keyguardStateController,
            NotificationPanelViewController notificationPanelViewController,
            NetworkController networkController,
            StatusBarStateController statusBarStateController,
            CommandQueue commandQueue,
            CarrierConfigTracker carrierConfigTracker,
            CollapsedStatusBarFragmentLogger collapsedStatusBarFragmentLogger,
            OperatorNameViewController.Factory operatorNameViewControllerFactory,
            SecureSettings secureSettings,
            @Main Executor mainExecutor
    ) {
        mStatusBarFragmentComponentFactory = statusBarFragmentComponentFactory;
        mOngoingCallController = ongoingCallController;
        mAnimationScheduler = animationScheduler;
        mLocationPublisher = locationPublisher;
        mNotificationIconAreaController = notificationIconAreaController;
        mPanelExpansionStateManager = panelExpansionStateManager;
        mFeatureFlags = featureFlags;
        mStatusBarIconController = statusBarIconController;
        mStatusBarHideIconsForBouncerManager = statusBarHideIconsForBouncerManager;
        mKeyguardStateController = keyguardStateController;
        mNotificationPanelViewController = notificationPanelViewController;
        mNetworkController = networkController;
        mStatusBarStateController = statusBarStateController;
        mCommandQueue = commandQueue;
        mCarrierConfigTracker = carrierConfigTracker;
        mCollapsedStatusBarFragmentLogger = collapsedStatusBarFragmentLogger;
        mOperatorNameViewControllerFactory = operatorNameViewControllerFactory;
        mSecureSettings = secureSettings;
        mMainExecutor = mainExecutor;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.status_bar, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStatusBarFragmentComponent = mStatusBarFragmentComponentFactory.create(this);
        mStatusBarFragmentComponent.init();

        mStatusBar = (PhoneStatusBarView) view;
        View contents = mStatusBar.findViewById(R.id.status_bar_contents);
        contents.addOnLayoutChangeListener(mStatusBarLayoutListener);
        updateStatusBarLocation(contents.getLeft(), contents.getRight());
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_PANEL_STATE)) {
            mStatusBar.restoreHierarchyState(
                    savedInstanceState.getSparseParcelableArray(EXTRA_PANEL_STATE));
        }
        mDarkIconManager = new DarkIconManager(view.findViewById(R.id.statusIcons), mFeatureFlags);
        mContentResolver = getContext().getContentResolver();
        mSettingsObserver = new SettingsObserver(mHandler);
        mDarkIconManager.setShouldLog(true);
        updateBlockedIcons();
        mStatusBarIconController.addIconGroup(mDarkIconManager);
        mSystemIconArea = mStatusBar.findViewById(R.id.system_icon_area);
        mClockView = mStatusBar.findViewById(R.id.clock);
        mBatteryBars[0] = mStatusBar.findViewById(R.id.battery_bar);
        mBatteryBars[1] = mStatusBar.findViewById(R.id.battery_bar_1);
	mBlissLogo = mStatusBar.findViewById(R.id.status_bar_logo);
	mBlissLogoRight = mStatusBar.findViewById(R.id.status_bar_logo_right);
        updateSettings(false);
        mSignalClusterEndPadding = getResources().getDimensionPixelSize(R.dimen.signal_cluster_battery_padding);
        mStatusIcons = mStatusBar.findViewById(R.id.statusIcons);
        int batteryStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0);
        mStatusIcons.setPadding(mStatusIcons.getPaddingLeft(), mStatusIcons.getPaddingTop(),
               (batteryStyle == 5/*hidden*/ ? 0 : mSignalClusterEndPadding), mStatusIcons.getPaddingBottom());
        mBatteryMeterView = mStatusBar.findViewById(R.id.battery);
        mBatteryMeterView.addCallback(mBatteryMeterViewCallback);
        mOngoingCallChip = mStatusBar.findViewById(R.id.ongoing_call_chip);
        mCenterClockLayout = (LinearLayout) mStatusBar.findViewById(R.id.center_clock_layout);
        mRightClock = mStatusBar.findViewById(R.id.right_clock);
        showSystemIconArea(false);
        initEmergencyCryptkeeperText();
        initOperatorName();
        initNotificationIconArea();
        mSystemEventAnimator =
                new StatusBarSystemEventAnimator(mSystemIconArea, getResources());
        mCarrierConfigTracker.addCallback(mCarrierConfigCallback);
        mCarrierConfigTracker.addDefaultDataSubscriptionChangedListener(mDefaultDataListener);
        mSettingsObserver.observe();
    }

    @VisibleForTesting
    void updateBlockedIcons() {
        mBlockedIcons.clear();

        // Reload the blocklist from res
        List<String> blockList = Arrays.asList(getResources().getStringArray(
                R.array.config_collapsed_statusbar_icon_blocklist));
        String vibrateIconSlot = getString(com.android.internal.R.string.status_bar_volume);
        boolean showVibrateIcon =
                mSecureSettings.getIntForUser(
                        Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON,
                        1,
                        UserHandle.USER_CURRENT) == 0;

        // Filter out vibrate icon from the blocklist if the setting is on
        for (int i = 0; i < blockList.size(); i++) {
            if (blockList.get(i).equals(vibrateIconSlot)) {
                if (showVibrateIcon) {
                    mBlockedIcons.add(blockList.get(i));
                }
            } else {
                mBlockedIcons.add(blockList.get(i));
            }
        }

        mMainExecutor.execute(() -> mDarkIconManager.setBlockList(mBlockedIcons));
    }

    @VisibleForTesting
    List<String> getBlockedIcons() {
        return mBlockedIcons;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        SparseArray<Parcelable> states = new SparseArray<>();
        mStatusBar.saveHierarchyState(states);
        outState.putSparseParcelableArray(EXTRA_PANEL_STATE, states);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCommandQueue.addCallback(this);
        mStatusBarStateController.addCallback(this);
        initOngoingCallChip();
        mAnimationScheduler.addCallback(this);

        mSecureSettings.registerContentObserverForUser(
                Settings.Secure.getUriFor(Settings.Secure.STATUS_BAR_SHOW_VIBRATE_ICON),
                false,
                mVolumeSettingObserver,
                UserHandle.USER_ALL);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCommandQueue.removeCallback(this);
        mStatusBarStateController.removeCallback(this);
        mOngoingCallController.removeCallback(mOngoingCallListener);
        mAnimationScheduler.removeCallback(this);
        mSecureSettings.unregisterContentObserver(mVolumeSettingObserver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mStatusBarIconController.removeIconGroup(mDarkIconManager);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            mNetworkController.removeCallback(mSignalCallback);
        }
        if (mBatteryMeterView != null) {
            mBatteryMeterView.removeCallback(mBatteryMeterViewCallback);
        }
        mCarrierConfigTracker.removeCallback(mCarrierConfigCallback);
        mCarrierConfigTracker.removeDataSubscriptionChangedListener(mDefaultDataListener);
        if (mBatteryMeterView != null) {
            mBatteryMeterView.removeCallback(mBatteryMeterViewCallback);
        }
    }

    /** Initializes views related to the notification icon area. */
    public void initNotificationIconArea() {
        ViewGroup notificationIconArea = mStatusBar.findViewById(R.id.notification_icon_area);
        mNotificationIconAreaInner =
                mNotificationIconAreaController.getNotificationInnerAreaView();
        if (mNotificationIconAreaInner.getParent() != null) {
            ((ViewGroup) mNotificationIconAreaInner.getParent())
                    .removeView(mNotificationIconAreaInner);
        }
        notificationIconArea.addView(mNotificationIconAreaInner);

        // #disable should have already been called, so use the disable values to set visibility.
        updateNotificationIconAreaAndCallChip(mDisabled1, false);
    }

    /**
     * Returns the dagger component for this fragment.
     *
     * TODO(b/205609837): Eventually, the dagger component should encapsulate all status bar
     *   fragment functionality and we won't need to expose it here anymore.
     */
    @Nullable
    public StatusBarFragmentComponent getStatusBarFragmentComponent() {
        return mStatusBarFragmentComponent;
    }

    @Override
    public void disable(int displayId, int state1, int state2, boolean animate) {
        if (displayId != getContext().getDisplayId()) {
            return;
        }

        int state1BeforeAdjustment = state1;
        state1 = adjustDisableFlags(state1);

        mCollapsedStatusBarFragmentLogger.logDisableFlagChange(
                /* new= */ new DisableState(state1BeforeAdjustment, state2),
                /* newAfterLocalModification= */ new DisableState(state1, state2));

        final int old1 = mDisabled1;
        final int diff1 = state1 ^ old1;
        final int old2 = mDisabled2;
        final int diff2 = state2 ^ old2;
        mDisabled1 = state1;
        mDisabled2 = state2;
        if ((diff1 & DISABLE_SYSTEM_INFO) != 0 || ((diff2 & DISABLE2_SYSTEM_ICONS) != 0)) {
            if ((state1 & DISABLE_SYSTEM_INFO) != 0 || ((state2 & DISABLE2_SYSTEM_ICONS) != 0)) {
                hideSystemIconArea(animate);
                hideOperatorName(animate);
                hideSbLogoRight(animate);
            } else {
                showSystemIconArea(animate);
                showOperatorName(animate);
                showSbLogoRight(animate);
            }
        }

        // The ongoing call chip and notification icon visibilities are intertwined, so update both
        // if either change.
        if (((diff1 & DISABLE_ONGOING_CALL_CHIP) != 0)
                || ((diff1 & DISABLE_NOTIFICATION_ICONS) != 0)) {
            updateNotificationIconAreaAndCallChip(state1, animate);
        }
    }

    protected int adjustDisableFlags(int state) {
        boolean headsUpVisible =
                mStatusBarFragmentComponent.getHeadsUpAppearanceController().shouldBeVisible();
        if (headsUpVisible) {
            state |= DISABLE_CLOCK;
        }

        if (!mKeyguardStateController.isLaunchTransitionFadingAway()
                && !mKeyguardStateController.isKeyguardFadingAway()
                && shouldHideNotificationIcons()
                && !(mStatusBarStateController.getState() == StatusBarState.KEYGUARD
                        && headsUpVisible)) {
            state |= DISABLE_NOTIFICATION_ICONS;
            state |= DISABLE_SYSTEM_INFO;
            state |= DISABLE_CLOCK;
        }


        if (mNetworkController != null && EncryptionHelper.IS_DATA_ENCRYPTED) {
            if (mNetworkController.hasEmergencyCryptKeeperText()) {
                state |= DISABLE_NOTIFICATION_ICONS;
            }
            if (!mNetworkController.isRadioOn()) {
                state |= DISABLE_SYSTEM_INFO;
            }
        }

        // The shelf will be hidden when dozing with a custom clock, we must show notification
        // icons in this occasion.
        if (mStatusBarStateController.isDozing()
                && mNotificationPanelViewController.hasCustomClock()) {
            state |= DISABLE_CLOCK | DISABLE_SYSTEM_INFO;
        }

        if (mOngoingCallController.hasOngoingCall()) {
            state &= ~DISABLE_ONGOING_CALL_CHIP;
        } else {
            state |= DISABLE_ONGOING_CALL_CHIP;
        }

        return state;
    }

    /**
     * Updates the visibility of the notification icon area and ongoing call chip based on disabled1
     * state.
     */
    private void updateNotificationIconAreaAndCallChip(int state1, boolean animate) {
        boolean disableNotifications = (state1 & DISABLE_NOTIFICATION_ICONS) != 0;
        boolean hasOngoingCall = (state1 & DISABLE_ONGOING_CALL_CHIP) == 0;

        // Hide notifications if the disable flag is set or we have an ongoing call.
        if (disableNotifications || hasOngoingCall) {
            hideNotificationIconArea(animate);
            animateHide(mClockView, animate, mClockStyle == 0);
            hideSbLogoLeft(animate);
        } else {
            showNotificationIconArea(animate);
            updateClockStyle(animate);
            showSbLogoLeft(animate);
        }

        // Show the ongoing call chip only if there is an ongoing call *and* notification icons
        // are allowed. (The ongoing call chip occupies the same area as the notification icons,
        // so if the icons are disabled then the call chip should be, too.)
        boolean showOngoingCallChip = hasOngoingCall && !disableNotifications;
        if (showOngoingCallChip) {
            showOngoingCallChip(animate);
        } else {
            hideOngoingCallChip(animate);
        }
        mOngoingCallController.notifyChipVisibilityChanged(showOngoingCallChip);
    }

    private boolean shouldHideNotificationIcons() {
        if (!mPanelExpansionStateManager.isClosed()
                && mNotificationPanelViewController.hideStatusBarIconsWhenExpanded()) {
            return true;
        }
        return mStatusBarHideIconsForBouncerManager.getShouldHideStatusBarIconsForBouncer();
    }

    private void hideSystemIconArea(boolean animate) {
        animateHide(mCenterClockLayout, animate, true);
        if (mClockStyle == 2) {
            animateHide(mRightClock, animate, true);
        }
        animateHide(mSystemIconArea, animate, true);
        if (mShowLogo == 2) {
            animateHide(mBlissLogoRight, animate, false);
        }
        for (View batteryBar: mBatteryBars) {
            animateHide(batteryBar, animate, false);
        }
    }

    private void showSystemIconArea(boolean animate) {
        // Only show the system icon area if we are not currently animating
        int state = mAnimationScheduler.getAnimationState();
        if (state == IDLE || state == SHOWING_PERSISTENT_DOT) {
            animateShow(mCenterClockLayout, animate);
            if (mClockStyle == 2) {
                animateShow(mRightClock, animate);
            }
            animateShow(mSystemIconArea, animate);
            if (mShowLogo == 2) {
                animateShow(mBlissLogoRight, animate);
                updateLogoSettings(animate);
            }
            for (View batteryBar: mBatteryBars) {
                 animateShow(batteryBar, animate);
            }
        } else {
            // We are in the middle of a system status event animation, which will animate the
            // alpha (but not the visibility). Allow the view to become visible again
            mSystemIconArea.setVisibility(View.VISIBLE);
        }
    }

/**
    private void hideClock(boolean animate) {
        animateHiddenState(mClockView, clockHiddenMode(), animate);
    }

    private void showClock(boolean animate) {
        animateShow(mClockView, animate);
    }
*/

    /** Hides the ongoing call chip. */
    public void hideOngoingCallChip(boolean animate) {
        animateHide(mOngoingCallChip, animate, true);
    }

    /** Displays the ongoing call chip. */
    public void showOngoingCallChip(boolean animate) {
        animateShow(mOngoingCallChip, animate);
    }

    /**
     * If panel is expanded/expanding it usually means QS shade is opening, so
     * don't set the clock GONE otherwise it'll mess up the animation.
    private int clockHiddenMode() {
        if (!mPanelExpansionStateManager.isClosed() && !mKeyguardStateController.isShowing()
                && !mStatusBarStateController.isDozing()
                && mClockController.getClock().shouldBeVisible()) {
            return View.INVISIBLE;
        }
        return View.GONE;
    }
     */

    public void hideNotificationIconArea(boolean animate) {
        animateHide(mNotificationIconAreaInner, animate, true);
	animateHide(mCenterClockLayout, animate, true);
        if (mShowLogo == 1) {
            animateHide(mBlissLogo, animate, false);
            updateLogoSettings(animate);
        }
    }

    public void showNotificationIconArea(boolean animate) {
        animateShow(mNotificationIconAreaInner, animate);
        animateShow(mCenterClockLayout, animate);
         if (mShowLogo == 1) {
             animateShow(mBlissLogo, animate);
             updateLogoSettings(animate);
         }
    }

    public void hideOperatorName(boolean animate) {
        if (mOperatorNameViewController != null) {
            animateHide(mOperatorNameViewController.getView(), animate, true);
        }
    }

    public void showOperatorName(boolean animate) {
        if (mOperatorNameViewController != null) {
            animateShow(mOperatorNameViewController.getView(), animate);
        }
    }

    public void hideSbLogoLeft(boolean animate) {
        if (mShowLogo == 1) {
            animateHide(mBlissLogo, animate, false);
        }
    }

    public void hideSbLogoRight(boolean animate) {
        if (mShowLogo == 2) {
            animateHide(mBlissLogoRight, animate, false);
        }
    }

    public void showSbLogoLeft(boolean animate) {
        if (mShowLogo == 1) {
            animateShow(mBlissLogo, animate);
            updateLogoSettings(animate);
        }
    }

    public void showSbLogoRight(boolean animate) {
        if (mShowLogo == 2) {
            animateShow(mBlissLogoRight, animate);
            updateLogoSettings(animate);
        }
    }

    /**
     * Hides a View
     */
    private void animateHide(final View v, boolean animate, final boolean invisible) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(0f);
            v.setVisibility(invisible ? View.INVISIBLE : View.GONE);
            return;
        }

        v.animate()
                .alpha(0f)
                .setDuration(160)
                .setStartDelay(0)
                .setInterpolator(Interpolators.ALPHA_OUT)
                .withEndAction(() -> v.setVisibility(invisible ? View.INVISIBLE : View.GONE));
    }

    /**
     * Shows a view, and synchronizes the animation with Keyguard exit animations, if applicable.
     */
    private void animateShow(View v, boolean animate) {
        if (v instanceof Clock && !((Clock)v).isClockVisible()) {
            return;
        }
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (!animate) {
            v.setAlpha(1f);
            return;
        }
        v.animate()
                .alpha(1f)
                .setDuration(FADE_IN_DURATION)
                .setInterpolator(Interpolators.ALPHA_IN)
                .setStartDelay(FADE_IN_DELAY)

                // We need to clean up any pending end action from animateHide if we call
                // both hide and show in the same frame before the animation actually gets started.
                // cancel() doesn't really remove the end action.
                .withEndAction(null);

        // Synchronize the motion with the Keyguard fading if necessary.
        if (mKeyguardStateController.isKeyguardFadingAway()) {
            v.animate()
                    .setDuration(mKeyguardStateController.getKeyguardFadingAwayDuration())
                    .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN)
                    .setStartDelay(mKeyguardStateController.getKeyguardFadingAwayDelay())
                    .start();
        }
    }

    private void initEmergencyCryptkeeperText() {
        View emergencyViewStub = mStatusBar.findViewById(R.id.emergency_cryptkeeper_text);
        if (mNetworkController.hasEmergencyCryptKeeperText()) {
            if (emergencyViewStub != null) {
                ((ViewStub) emergencyViewStub).inflate();
            }
            mNetworkController.addCallback(mSignalCallback);
        } else if (emergencyViewStub != null) {
            ViewGroup parent = (ViewGroup) emergencyViewStub.getParent();
            parent.removeView(emergencyViewStub);
        }
    }

    private void initOperatorName() {
        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (mCarrierConfigTracker.getShowOperatorNameInStatusBarConfig(subId)) {
            ViewStub stub = mStatusBar.findViewById(R.id.operator_name);
            mOperatorNameViewController =
                    mOperatorNameViewControllerFactory.create((OperatorNameView) stub.inflate());
            mOperatorNameViewController.init();
            // This view should not be visible on lock-screen
            if (mKeyguardStateController.isShowing()) {
                hideOperatorName(false);
            }
        }
    }

    private void initOngoingCallChip() {
        mOngoingCallController.addCallback(mOngoingCallListener);
        mOngoingCallController.setChipView(mOngoingCallChip);
    }

    @Override
    public void onStateChanged(int newState) { }

    @Override
    public void onDozingChanged(boolean isDozing) {
        disable(getContext().getDisplayId(), mDisabled1, mDisabled2, false /* animate */);
    }

    @Nullable
    @Override
    public Animator onSystemEventAnimationBegin() {
        return mSystemEventAnimator.onSystemEventAnimationBegin();
    }

    @Nullable
    @Override
    public Animator onSystemEventAnimationFinish(boolean hasPersistentDot) {
        return mSystemEventAnimator.onSystemEventAnimationFinish(hasPersistentDot);
    }

    private boolean isSystemIconAreaDisabled() {
        return (mDisabled1 & DISABLE_SYSTEM_INFO) != 0 || (mDisabled2 & DISABLE2_SYSTEM_ICONS) != 0;
    }

    private void updateStatusBarLocation(int left, int right) {
        int leftMargin = left - mStatusBar.getLeft();
        int rightMargin = mStatusBar.getRight() - right;

        mLocationPublisher.updateStatusBarMargin(leftMargin, rightMargin);
    }

    public void updateSettings(boolean animate) {
        if (mStatusBar == null) return;

        if (getContext() == null) {
            return;
        }

        try {
           mShowClock = Settings.System.getIntForUser(mContentResolver,
                   Settings.System.STATUSBAR_CLOCK, 1,
                   UserHandle.USER_CURRENT) == 1;
           if (!mShowClock) {
               mClockStyle = 1; // internally switch to centered clock layout because
                             // left & right will show up again after QS pulldown
           } else {
               mClockStyle = Settings.System.getIntForUser(mContentResolver,
                       Settings.System.STATUSBAR_CLOCK_STYLE, 0,
                       UserHandle.USER_CURRENT);
           }
        } catch (Exception e) {
        }
	updateClockStyle(animate);
        updateLogoSettings(animate);
    }

    public void updateLogoSettings(boolean animate) {
        Drawable logo = null;

        if (mStatusBar == null) return;

        if (getContext() == null) {
            return;
        }

        mShowLogo = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO, 0,
                UserHandle.USER_CURRENT);
        mLogoColor = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO_COLOR, 0xffff8800,
                UserHandle.USER_CURRENT);
        mLogoStyle = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.STATUS_BAR_LOGO_STYLE, 0,
                UserHandle.USER_CURRENT);
        mCustomSbLogoEnabled = Settings.System.getIntForUser(
                getContext().getContentResolver(), Settings.System.CUSTOM_SB_LOGO_ENABLED, 0,
                UserHandle.USER_CURRENT) == 1;

        final String customSbLogoURI = Settings.System.getStringForUser(
                getContext().getContentResolver(), Settings.System.CUSTOM_SB_LOGO_IMAGE,
                UserHandle.USER_CURRENT);

        if (!TextUtils.isEmpty(customSbLogoURI) && mCustomSbLogoEnabled) {
            try {
                ParcelFileDescriptor parcelFileDescriptor =
                    getContext().getContentResolver().openFileDescriptor(Uri.parse(customSbLogoURI), "r");
                FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
                Bitmap imageSbLogo = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                parcelFileDescriptor.close();
                if (mShowLogo == 1) {
                    mBlissLogoRight.setImageDrawable(null);
                    mBlissLogoRight.setVisibility(View.GONE);
                    mBlissLogo.setImageDrawable(null);
                    mBlissLogo.setImageBitmap(imageSbLogo);
                    mLogoColor = 0x00000000;
                    mBlissLogo.setVisibility(View.VISIBLE);
                } else if (mShowLogo == 2) {
                    mBlissLogo.setImageDrawable(null);
                    mBlissLogo.setVisibility(View.GONE);
                    mBlissLogoRight.setImageDrawable(null);
                    mBlissLogoRight.setImageBitmap(imageSbLogo);
                    mLogoColor = 0x00000000;
                    mBlissLogoRight.setVisibility(View.VISIBLE);
                }
            }
            catch (Exception e) {
            }
        } else {
		switch(mLogoStyle) {
		        // Bliss logo
		    case 1:
		        logo = getContext().getDrawable(R.drawable.ic_bliss_logo);
		        break;
		        // GZR Skull
		    case 2:
		        logo = getContext().getDrawable(R.drawable.status_bar_gzr_skull_logo);
		        break;
		        // GZR Circle
		    case 3:
		        logo = getContext().getDrawable(R.drawable.status_bar_gzr_circle_logo);
		        break;
		        // Batman
		    case 4:
		        logo = getContext().getDrawable(R.drawable.ic_batman_logo);
		        break;
		        // Deadpool
		    case 5:
		        logo = getContext().getDrawable(R.drawable.ic_deadpool_logo);
		        break;
		        // Superman
		    case 6:
		        logo = getContext().getDrawable(R.drawable.ic_superman_logo);
		        break;
		        // Ironman
		    case 7:
		        logo = getContext().getDrawable(R.drawable.ic_ironman_logo);
		        break;
		        // Spiderman
		    case 8:
		        logo = getContext().getDrawable(R.drawable.ic_spiderman_logo);
		        break;
		        // Decepticons
		    case 9:
		        logo = getContext().getDrawable(R.drawable.ic_decpeticons_logo);
		        break;
		        // Minions
		    case 10:
		        logo = getContext().getDrawable(R.drawable.ic_minions_logo);
		        break;
		    case 11:
		        logo = getContext().getDrawable(R.drawable.ic_android_logo);
		        break;
		        // Shit
		    case 12:
		        logo = getContext().getDrawable(R.drawable.ic_apple_logo);
		        break;
		        // Shitty Logo
		    case 13:
		        logo = getContext().getDrawable(R.drawable.ic_ios_logo);
		        break;
		        // Others
		    case 14:
		        logo = getContext().getDrawable(R.drawable.ic_blackberry);
		        break;
		        // Cake
		    case 15:
		        logo = getContext().getDrawable(R.drawable.ic_cake_new);
		        break;
		    case 16:
		        logo = getContext().getDrawable(R.drawable.ic_blogger);
		        break;
		    case 17:
		        logo = getContext().getDrawable(R.drawable.ic_biohazard);
		        break;
		    case 18:
		        logo = getContext().getDrawable(R.drawable.ic_linux);
		        break;
		    case 19:
		        logo = getContext().getDrawable(R.drawable.ic_yin_yang);
		        break;
		    case 20:
		        logo = getContext().getDrawable(R.drawable.ic_windows);
		        break;
		    case 21:
		        logo = getContext().getDrawable(R.drawable.ic_robot);
		        break;
		    case 22:
		        logo = getContext().getDrawable(R.drawable.ic_ninja);
		        break;
		    case 23:
		        logo = getContext().getDrawable(R.drawable.ic_heart);
		        break;
		    case 24:
		        logo = getContext().getDrawable(R.drawable.ic_ghost);
		        break;
		    case 25:
		        logo = getContext().getDrawable(R.drawable.ic_google);
		        break;
		    case 26:
		        logo = getContext().getDrawable(R.drawable.ic_human_male);
		        break;
		    case 27:
		        logo = getContext().getDrawable(R.drawable.ic_human_female);
		        break;
		    case 28:
		        logo = getContext().getDrawable(R.drawable.ic_human_male_female);
		        break;
		    case 29:
		        logo = getContext().getDrawable(R.drawable.ic_gender_male);
		        break;
		    case 30:
		        logo = getContext().getDrawable(R.drawable.ic_gender_female);
		        break;
		    case 31:
		        logo = getContext().getDrawable(R.drawable.ic_gender_male_female);
		        break;
		    case 32:
		        logo = getContext().getDrawable(R.drawable.ic_guitar_electric);
		        break;
		    case 33:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon);
		        break;
		    case 34:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_neutral);
		        break;
		    case 35:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_happy);
		        break;
		    case 36:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_sad);
		        break;
		    case 37:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_tongue);
		        break;
		    case 38:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_dead);
		        break;
		    case 39:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_cool);
		        break;
		    case 40:
		        logo = getContext().getDrawable(R.drawable.ic_emoticon_devil);
		        break;
		        // Default (Bliss Main)
		    case 0:
		    default:
		        logo = getContext().getDrawable(R.drawable.ic_bliss_logo);
		        break;
		}
            if (mShowLogo == 1) {
                mBlissLogoRight.setImageDrawable(null);
                mBlissLogoRight.setVisibility(View.GONE);
                mBlissLogo.setVisibility(View.VISIBLE);
                mBlissLogo.setImageDrawable(logo);
                if (mLogoColor == 0xFFFFFFFF) {
                    mBlissLogo.setColorFilter(mTintColor, PorterDuff.Mode.MULTIPLY);
                } else {
                    mBlissLogo.setColorFilter(mLogoColor, PorterDuff.Mode.MULTIPLY);
                }
            } else if (mShowLogo == 2) {
                mBlissLogo.setImageDrawable(null);
                mBlissLogo.setVisibility(View.GONE);
                mBlissLogoRight.setVisibility(View.VISIBLE);
                mBlissLogoRight.setImageDrawable(logo);
                if (mLogoColor == 0xFFFFFFFF) {
                    mBlissLogoRight.setColorFilter(mTintColor, PorterDuff.Mode.MULTIPLY);
                } else {
                    mBlissLogoRight.setColorFilter(mLogoColor, PorterDuff.Mode.MULTIPLY);
                }
            }
        }
    }


    private void updateClockStyle(boolean animate) {
        if (mClockStyle == 1 || mClockStyle == 2) {
            animateHide(mClockView, animate, false);
        } else {
            if (((Clock)mClockView).isClockVisible()) {
                 animateShow(mClockView, animate);
            }
        }
    }

    private final ContentObserver mVolumeSettingObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            updateBlockedIcons();
        }
    };

    // Listen for view end changes of PhoneStatusBarView and publish that to the privacy dot
    private View.OnLayoutChangeListener mStatusBarLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (left != oldLeft || right != oldRight) {
                    updateStatusBarLocation(left, right);
                }
            };
}
