/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.biometrics.BiometricSourceType;
import android.os.PowerManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.filters.SmallTest;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.testing.UiEventLoggerFake;
import com.android.internal.util.LatencyTracker;
import com.android.keyguard.KeyguardClockSwitch;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.classifier.FalsingManagerFake;
import com.android.systemui.doze.DozeLog;
import com.android.systemui.media.MediaHierarchyManager;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.FlingAnimationUtils;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.PulseExpansionHandler;
import com.android.systemui.statusbar.StatusBarStateControllerImpl;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.notification.ConversationNotificationManager;
import com.android.systemui.statusbar.notification.DynamicPrivacyController;
import com.android.systemui.statusbar.notification.NotificationEntryManager;
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator;
import com.android.systemui.statusbar.notification.stack.NotificationRoundnessManager;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.InjectionInflationController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NotificationPanelViewTest extends SysuiTestCase {

    @Mock
    private StatusBar mStatusBar;
    @Mock
    private SysuiStatusBarStateController mStatusBarStateController;
    @Mock
    private NotificationStackScrollLayout mNotificationStackScrollLayout;
    @Mock
    private KeyguardBottomAreaView mKeyguardBottomArea;
    @Mock
    private KeyguardBottomAreaView mQsFrame;
    @Mock
    private ViewGroup mBigClockContainer;
    @Mock
    private ScrimController mScrimController;
    @Mock
    private NotificationIconAreaController mNotificationAreaController;
    @Mock
    private HeadsUpManagerPhone mHeadsUpManager;
    @Mock
    private NotificationShelf mNotificationShelf;
    @Mock
    private NotificationGroupManager mGroupManager;
    @Mock
    private KeyguardStatusBarView mKeyguardStatusBar;
    @Mock
    private HeadsUpTouchHelper.Callback mHeadsUpCallback;
    @Mock
    private PanelBar mPanelBar;
    @Mock
    private KeyguardUpdateMonitor mUpdateMonitor;
    @Mock
    private FalsingManager mFalsingManager;
    @Mock
    private KeyguardBypassController mKeyguardBypassController;
    @Mock
    private DozeParameters mDozeParameters;
    @Mock
    private NotificationPanelView mView;
    @Mock
    private InjectionInflationController mInjectionInflationController;
    @Mock
    private DynamicPrivacyController mDynamicPrivacyController;
    @Mock
    private ShadeController mShadeController;
    @Mock
    private NotificationLockscreenUserManager mNotificationLockscreenUserManager;
    @Mock
    private NotificationEntryManager mNotificationEntryManager;
    @Mock
    private StatusBarTouchableRegionManager mStatusBarTouchableRegionManager;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private DozeLog mDozeLog;
    @Mock
    private CommandQueue mCommandQueue;
    @Mock
    private VibratorHelper mVibratorHelper;
    @Mock
    private LatencyTracker mLatencyTracker;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    private AccessibilityManager mAccessibilityManager;
    @Mock
    private MetricsLogger mMetricsLogger;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private Resources mResources;
    @Mock
    private Configuration mConfiguration;
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    @Mock
    private KeyguardClockSwitch mKeyguardClockSwitch;
    private PanelViewController.TouchHandler mTouchHandler;
    @Mock
    private ZenModeController mZenModeController;
    @Mock
    private ConfigurationController mConfigurationController;
    @Mock
    private MediaHierarchyManager mMediaHiearchyManager;
    @Mock
    private ConversationNotificationManager mConversationNotificationManager;
    @Mock
    private BiometricUnlockController mBiometricUnlockController;
    @Mock
    private StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private FlingAnimationUtils.Builder mFlingAnimationUtilsBuilder;

    private NotificationPanelViewController mNotificationPanelViewController;
    private View.AccessibilityDelegate mAccessibiltyDelegate;

    private TunerService mTunerService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mHeadsUpCallback.getContext()).thenReturn(mContext);
        when(mView.getResources()).thenReturn(mResources);
        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        mConfiguration.orientation = ORIENTATION_PORTRAIT;
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        mDisplayMetrics.density = 100;
        when(mResources.getBoolean(R.bool.config_enableNotificationShadeDrag)).thenReturn(true);
        when(mView.getContext()).thenReturn(getContext());
        when(mView.findViewById(R.id.keyguard_clock_container)).thenReturn(mKeyguardClockSwitch);
        when(mView.findViewById(R.id.notification_stack_scroller))
                .thenReturn(mNotificationStackScrollLayout);
        when(mNotificationStackScrollLayout.getHeight()).thenReturn(1000);
        when(mNotificationStackScrollLayout.getHeadsUpCallback()).thenReturn(mHeadsUpCallback);
        when(mView.findViewById(R.id.keyguard_bottom_area)).thenReturn(mKeyguardBottomArea);
        when(mKeyguardBottomArea.getLeftView()).thenReturn(mock(KeyguardAffordanceView.class));
        when(mKeyguardBottomArea.getRightView()).thenReturn(mock(KeyguardAffordanceView.class));
        when(mView.findViewById(R.id.big_clock_container)).thenReturn(mBigClockContainer);
        when(mView.findViewById(R.id.qs_frame)).thenReturn(mQsFrame);
        mFlingAnimationUtilsBuilder = new FlingAnimationUtils.Builder(mDisplayMetrics);

        doAnswer((Answer<Void>) invocation -> {
            mTouchHandler = invocation.getArgument(0);
            return null;
        }).when(mView).setOnTouchListener(any(PanelViewController.TouchHandler.class));

        NotificationWakeUpCoordinator coordinator =
                new NotificationWakeUpCoordinator(
                        mock(HeadsUpManagerPhone.class),
                        new StatusBarStateControllerImpl(new UiEventLoggerFake()),
                        mKeyguardBypassController,
                        mDozeParameters);
        PulseExpansionHandler expansionHandler = new PulseExpansionHandler(
                mContext,
                coordinator,
                mKeyguardBypassController, mHeadsUpManager,
                mock(NotificationRoundnessManager.class),
                mStatusBarStateController,
                new FalsingManagerFake());
        mNotificationPanelViewController = new NotificationPanelViewController(mView,
                mInjectionInflationController,
                coordinator, expansionHandler, mDynamicPrivacyController, mKeyguardBypassController,
                mFalsingManager, mShadeController,
                mNotificationLockscreenUserManager, mNotificationEntryManager,
                mKeyguardStateController, mStatusBarStateController, mDozeLog,
                mDozeParameters, mCommandQueue, mVibratorHelper,
                mLatencyTracker, mPowerManager, mAccessibilityManager, 0, mUpdateMonitor,
                mMetricsLogger, mActivityManager, mZenModeController, mConfigurationController,
                mFlingAnimationUtilsBuilder, mStatusBarTouchableRegionManager,
                mConversationNotificationManager, mMediaHiearchyManager,
                mBiometricUnlockController, mStatusBarKeyguardViewManager,
                mTunerService);
        mNotificationPanelViewController.initDependencies(mStatusBar, mGroupManager,
                mNotificationShelf, mNotificationAreaController, mScrimController);
        mNotificationPanelViewController.setHeadsUpManager(mHeadsUpManager);
        mNotificationPanelViewController.setBar(mPanelBar);

        ArgumentCaptor<View.AccessibilityDelegate> accessibilityDelegateArgumentCaptor =
                ArgumentCaptor.forClass(View.AccessibilityDelegate.class);
        verify(mView)
                .setAccessibilityDelegate(accessibilityDelegateArgumentCaptor.capture());
        mAccessibiltyDelegate = accessibilityDelegateArgumentCaptor.getValue();
    }

    @Test
    public void testSetDozing_notifiesNsslAndStateController() {
        mNotificationPanelViewController.setDozing(true /* dozing */, true /* animate */,
                null /* touch */);
        InOrder inOrder = inOrder(mNotificationStackScrollLayout, mStatusBarStateController);
        inOrder.verify(mNotificationStackScrollLayout).setDozing(eq(true), eq(true), eq(null));
        inOrder.verify(mStatusBarStateController).setDozeAmount(eq(1f), eq(true));
    }

    @Test
    public void testSetExpandedHeight() {
        mNotificationPanelViewController.setExpandedHeight(200);
        assertThat((int) mNotificationPanelViewController.getExpandedHeight()).isEqualTo(200);
    }

    @Test
    public void testAffordanceLaunchingListener() {
        Consumer<Boolean> listener = spy((showing) -> { });
        mNotificationPanelViewController.setExpandedFraction(1f);
        mNotificationPanelViewController.setLaunchAffordanceListener(listener);
        mNotificationPanelViewController.launchCamera(false /* animate */,
                StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP);
        verify(listener).accept(eq(true));

        mNotificationPanelViewController.onAffordanceLaunchEnded();
        verify(listener).accept(eq(false));
    }

    @Test
    public void testOnTouchEvent_expansionCanBeBlocked() {
        onTouchEvent(MotionEvent.obtain(0L /* downTime */,
                0L /* eventTime */, MotionEvent.ACTION_DOWN, 0f /* x */, 0f /* y */,
                0 /* metaState */));
        onTouchEvent(MotionEvent.obtain(0L /* downTime */,
                0L /* eventTime */, MotionEvent.ACTION_MOVE, 0f /* x */, 200f /* y */,
                0 /* metaState */));
        assertThat((int) mNotificationPanelViewController.getExpandedHeight()).isEqualTo(200);
        assertThat(mNotificationPanelViewController.isTrackingBlocked()).isFalse();

        mNotificationPanelViewController.blockExpansionForCurrentTouch();
        onTouchEvent(MotionEvent.obtain(0L /* downTime */,
                0L /* eventTime */, MotionEvent.ACTION_MOVE, 0f /* x */, 300f /* y */,
                0 /* metaState */));
        // Expansion should not have changed because it was blocked
        assertThat((int) mNotificationPanelViewController.getExpandedHeight()).isEqualTo(200);
        assertThat(mNotificationPanelViewController.isTrackingBlocked()).isTrue();

        onTouchEvent(MotionEvent.obtain(0L /* downTime */,
                0L /* eventTime */, MotionEvent.ACTION_UP, 0f /* x */, 300f /* y */,
                0 /* metaState */));
        assertThat(mNotificationPanelViewController.isTrackingBlocked()).isFalse();
    }

    @Test
    public void testKeyguardStatusBarVisibility_hiddenForBypass() {
        when(mUpdateMonitor.shouldListenForFace()).thenReturn(true);
        mNotificationPanelViewController.mKeyguardUpdateCallback.onBiometricRunningStateChanged(
                true, BiometricSourceType.FACE);
        verify(mKeyguardStatusBar, never()).setVisibility(View.VISIBLE);

        when(mKeyguardBypassController.getBypassEnabled()).thenReturn(true);
        mNotificationPanelViewController.mKeyguardUpdateCallback.onFinishedGoingToSleep(0);
        mNotificationPanelViewController.mKeyguardUpdateCallback.onBiometricRunningStateChanged(
                true, BiometricSourceType.FACE);
        verify(mKeyguardStatusBar, never()).setVisibility(View.VISIBLE);
    }

    @Test
    public void testA11y_initializeNode() {
        AccessibilityNodeInfo nodeInfo = new AccessibilityNodeInfo();
        mAccessibiltyDelegate.onInitializeAccessibilityNodeInfo(mView, nodeInfo);

        List<AccessibilityNodeInfo.AccessibilityAction> actionList = nodeInfo.getActionList();
        assertThat(actionList).containsAllIn(
                new AccessibilityNodeInfo.AccessibilityAction[] {
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD,
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP}
        );
    }

    @Test
    public void testA11y_scrollForward() {
        mAccessibiltyDelegate.performAccessibilityAction(
                mView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId(),
                null);

        verify(mStatusBarKeyguardViewManager).showBouncer(true);
    }

    @Test
    public void testA11y_scrollUp() {
        mAccessibiltyDelegate.performAccessibilityAction(
                mView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId(),
                null);

        verify(mStatusBarKeyguardViewManager).showBouncer(true);
    }

    private void onTouchEvent(MotionEvent ev) {
        mTouchHandler.onTouch(mView, ev);
    }
}
