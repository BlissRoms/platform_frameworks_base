/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.display.mode;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.SurfaceControl;
import android.view.SurfaceControl.WorkDuration;

import com.android.server.display.config.SupportedModeData;
import com.android.server.display.feature.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

interface Vote {
    // DEFAULT_RENDER_FRAME_RATE votes for render frame rate [0, DEFAULT]. As the lowest
    // priority vote, it's overridden by all other considerations. It acts to set a default
    // frame rate for a device.
    int PRIORITY_DEFAULT_RENDER_FRAME_RATE = 0;

    // PRIORITY_FLICKER_REFRESH_RATE votes for a single refresh rate like [60,60], [90,90] or
    // null. It is used to set a preferred refresh rate value in case the higher priority votes
    // result is a range.
    static final int PRIORITY_FLICKER_REFRESH_RATE = 1;

    // High-brightness-mode may need a specific range of refresh-rates to function properly.
    int PRIORITY_HIGH_BRIGHTNESS_MODE = 2;

    // SETTING_MIN_RENDER_FRAME_RATE is used to propose a lower bound of the render frame rate.
    // It votes [minRefreshRate, Float.POSITIVE_INFINITY]
    int PRIORITY_USER_SETTING_MIN_RENDER_FRAME_RATE = 3;

    // Restrict displays allowed HDR mode (HDR+SDR / SDR-only).
    int PRIORITY_USER_SETTING_HDR_MODE = 4;

    // User setting preferred display mode
    int PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS = 5;

    // APP_REQUEST_RENDER_FRAME_RATE_RANGE is used to for internal apps to limit the render
    // frame rate in certain cases, mostly to preserve power.
    // @see android.view.WindowManager.LayoutParams#preferredMinRefreshRate
    // @see android.view.WindowManager.LayoutParams#preferredMaxRefreshRate
    // It votes to [preferredMinRefreshRate, preferredMaxRefreshRate].
    int PRIORITY_APP_REQUEST_RENDER_FRAME_RATE_RANGE = 6;

    // We split the app request into different priorities in case we can satisfy one desire
    // without the other.

    // Application can specify preferred refresh rate with below attrs.
    // @see android.view.WindowManager.LayoutParams#preferredRefreshRate
    // @see android.view.WindowManager.LayoutParams#preferredDisplayModeId
    //
    // When the app specifies a LayoutParams#preferredDisplayModeId, in addition to the
    // refresh rate, it also chooses a preferred size (resolution) as part of the selected
    // mode id. The app preference is then translated to APP_REQUEST_BASE_MODE_REFRESH_RATE and
    // optionally to APP_REQUEST_SIZE as well, if a mode id was selected.
    // The system also forces some apps like denylisted app to run at a lower refresh rate.
    // @see android.R.array#config_highRefreshRateBlacklist
    //
    // When summarizing the votes and filtering the allowed display modes, these votes determine
    // which mode id should be the base mode id to be sent to SurfaceFlinger:
    // - APP_REQUEST_BASE_MODE_REFRESH_RATE is used to validate the vote summary. If a summary
    //   includes a base mode refresh rate, but it is not in the refresh rate range, then the
    //   summary is considered invalid so we could drop a lower priority vote and try again.
    // - APP_REQUEST_SIZE is used to filter out display modes of a different size.
    //
    // The preferred refresh rate is set on the main surface of the app outside of
    // DisplayModeDirector.
    // @see com.android.server.wm.WindowState#updateFrameRateSelectionPriorityIfNeeded
    int PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE = 7;

    int PRIORITY_APP_REQUEST_SIZE = 8;

    // PRIORITY_REJECTED_MODES rejects the modes for which the mode config failed
    // so that the modeset can be retried for next available mode after filtering
    // out the rejected modes for the connected display
    int PRIORITY_REJECTED_MODES = 9;

    // PRIORITY_USER_SETTING_PEAK_REFRESH_RATE restricts physical refresh rate to
    // [0, max(PEAK, MIN)], depending on user settings peakRR/minRR values
    int PRIORITY_USER_SETTING_PEAK_REFRESH_RATE = 10;

    // PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE has a higher priority than
    // PRIORITY_USER_SETTING_PEAK_REFRESH_RATE and will limit render rate to [0, max(PEAK, MIN)]
    // in case physical refresh rate vote is discarded (due to other high priority votes),
    // render rate vote can still apply
    int PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE = 11;

    // Restrict all displays physical refresh rate to 60Hz when external display is connected.
    // It votes [59Hz, 61Hz].
    int PRIORITY_SYNCHRONIZED_REFRESH_RATE = 12;

    // PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE has a higher priority than
    // PRIORITY_SYNCHRONIZED_REFRESH_RATE and will limit render rate to [59Hz, 61Hz].
    // In case physical refresh rate vote discarded (due to physical refresh rate not supported),
    // render rate vote can still apply.
    int PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE = 13;

    // Restrict displays max available resolution and refresh rates. It votes [0, LIMIT]
    int PRIORITY_LIMIT_MODE = 14;

    // To avoid delay in switching between 60HZ -> 90HZ when activating LHBM, set refresh
    // rate to max value (same as for PRIORITY_UDFPS) on lock screen
    int PRIORITY_AUTH_OPTIMIZER_RENDER_FRAME_RATE = 15;

    // For concurrent displays we want to limit refresh rate on all displays
    int PRIORITY_LAYOUT_LIMITED_REFRESH_RATE = 16;

    // For concurrent displays we want to limit refresh rate on all displays
    int PRIORITY_LAYOUT_LIMITED_FRAME_RATE = 17;

    // For internal application to limit display modes to specific ids
    int PRIORITY_SYSTEM_REQUESTED_MODES = 18;

    // PRIORITY_LOW_POWER_MODE_MODES limits display modes to specific refreshRate-vsync pairs if
    // Settings.Global.LOW_POWER_MODE is on.
    // Lower priority that PRIORITY_LOW_POWER_MODE_RENDER_RATE and if discarded (due to other
    // higher priority votes), render rate limit can still apply
    int PRIORITY_LOW_POWER_MODE_MODES = 19;

    // PRIORITY_LOW_POWER_MODE_RENDER_RATE force the render frame rate to [0, 60HZ] if
    // Settings.Global.LOW_POWER_MODE is on.
    int PRIORITY_LOW_POWER_MODE_RENDER_RATE = 20;

    // PRIORITY_FLICKER_REFRESH_RATE_SWITCH votes for disabling refresh rate switching. If the
    // higher priority voters' result is a range, it will fix the rate to a single choice.
    // It's used to avoid refresh rate switches in certain conditions which may result in the
    // user seeing the display flickering when the switches occur.
    int PRIORITY_FLICKER_REFRESH_RATE_SWITCH = 21;

    // Force display to [0, 60HZ] if skin temperature is at or above CRITICAL.
    int PRIORITY_SKIN_TEMPERATURE = 22;

    // The proximity sensor needs the refresh rate to be locked in order to function, so this is
    // set to a high priority.
    int PRIORITY_PROXIMITY = 23;

    // User preferred refresh rate for specific apps
    int PRIORITY_USER_PREFERRED = 24;

    // Force display to requested refresh rate in MEMC mode
    int PRIORITY_MEMC = 25;

    // The Under-Display Fingerprint Sensor (UDFPS) needs the refresh rate to be locked in order
    // to function, so this needs to be the highest priority of all votes.
    int PRIORITY_UDFPS = 26;

    @IntDef(prefix = { "PRIORITY_" }, value = {
            PRIORITY_DEFAULT_RENDER_FRAME_RATE,
            PRIORITY_FLICKER_REFRESH_RATE,
            PRIORITY_HIGH_BRIGHTNESS_MODE,
            PRIORITY_USER_SETTING_MIN_RENDER_FRAME_RATE,
            PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS,
            PRIORITY_APP_REQUEST_RENDER_FRAME_RATE_RANGE,
            PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE,
            PRIORITY_APP_REQUEST_SIZE,
            PRIORITY_REJECTED_MODES,
            PRIORITY_USER_SETTING_PEAK_REFRESH_RATE,
            PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE,
            PRIORITY_SYNCHRONIZED_REFRESH_RATE,
            PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE,
            PRIORITY_LIMIT_MODE,
            PRIORITY_AUTH_OPTIMIZER_RENDER_FRAME_RATE,
            PRIORITY_LAYOUT_LIMITED_REFRESH_RATE,
            PRIORITY_LAYOUT_LIMITED_FRAME_RATE,
            PRIORITY_USER_SETTING_HDR_MODE,
            PRIORITY_SYSTEM_REQUESTED_MODES,
            PRIORITY_LOW_POWER_MODE_MODES,
            PRIORITY_LOW_POWER_MODE_RENDER_RATE,
            PRIORITY_FLICKER_REFRESH_RATE_SWITCH,
            PRIORITY_SKIN_TEMPERATURE,
            PRIORITY_PROXIMITY,
            PRIORITY_USER_PREFERRED,
            PRIORITY_MEMC,
            PRIORITY_UDFPS
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Priority {}

    // Whenever a new priority is added, remember to update MIN_PRIORITY, MAX_PRIORITY, and
    // APP_REQUEST_REFRESH_RATE_RANGE_PRIORITY_CUTOFF, as well as priorityToString.
    @Priority int MIN_PRIORITY = PRIORITY_DEFAULT_RENDER_FRAME_RATE;
    @Priority int MAX_PRIORITY = PRIORITY_UDFPS;

    // The cutoff for the app request refresh rate range. Votes with priorities lower than this
    // value will not be considered when constructing the app request refresh rate range.
    @Priority int APP_REQUEST_REFRESH_RATE_RANGE_PRIORITY_CUTOFF =
            PRIORITY_APP_REQUEST_RENDER_FRAME_RATE_RANGE;

    /**
     * A value signifying an invalid width or height in a vote.
     */
    int INVALID_SIZE = -1;

    void updateSummary(@NonNull VoteSummary summary);

    static Vote forPhysicalRefreshRates(float refreshRate) {
        return forPhysicalRefreshRates(refreshRate, refreshRate);
    }

    static Vote forPhysicalRefreshRates(float minRefreshRate, float maxRefreshRate) {
        return new CombinedVote(
                List.of(
                        new RefreshRateVote.PhysicalVote(minRefreshRate, maxRefreshRate),
                        new DisableRefreshRateSwitchingVote(minRefreshRate == maxRefreshRate)
                )
        );
    }

    static Vote forRenderFrameRates(float renderRate) {
        return forRenderFrameRates(renderRate, renderRate);
    }

    static Vote forRenderFrameRates(float minFrameRate, float maxFrameRate) {
        return new RefreshRateVote.RenderVote(minFrameRate, maxFrameRate);
    }

    static Vote forRenderFrameRates(@Nullable SurfaceControl.RefreshRateRange range) {
        return range == null ? null : forRenderFrameRates(range.min, range.max);
    }

    static Vote forSize(int width, int height) {
        return new SizeVote(width, height, width, height);
    }

    static Vote forSizeAndPhysicalRefreshRatesRange(int minWidth, int minHeight,
            int width, int height, float minRefreshRate, float maxRefreshRate) {
        return new CombinedVote(
                List.of(
                        new SizeVote(width, height, minWidth, minHeight),
                        new RefreshRateVote.PhysicalVote(minRefreshRate, maxRefreshRate),
                        new DisableRefreshRateSwitchingVote(minRefreshRate == maxRefreshRate)
                )
        );
    }

    static Vote forDisableRefreshRateSwitching() {
        return new DisableRefreshRateSwitchingVote(true);
    }

    static Vote forBaseModeRefreshRate(float baseModeRefreshRate) {
        return new BaseModeRefreshRateVote(baseModeRefreshRate);
    }

    static Vote forRequestedRefreshRate(float refreshRate) {
        return new RequestedRefreshRateVote(refreshRate);
    }

    static Vote forSupportedRefreshRates(List<SupportedModeData> supportedModes) {
        if (supportedModes.isEmpty()) {
            return null;
        }
        List<SupportedRefreshRatesVote.RefreshRates> rates = new ArrayList<>();
        for (SupportedModeData data : supportedModes) {
            rates.add(new SupportedRefreshRatesVote.RefreshRates(data.refreshRate, data.vsyncRate));
        }
        return new SupportedRefreshRatesVote(rates);
    }

    static Vote forHdrPreference(boolean allowHdr) {
        return new HdrPreferenceVote(allowHdr);
    }

    static Vote forWorkDurations(@Nullable WorkDuration workDurationsData) {
        return (!Flags.enableWorkDurations() || workDurationsData == null) ? null
                : new WorkDurationsVote(workDurationsData);
    }

    static Vote forSupportedModes(List<Integer> modeIds) {
        return new SupportedModesVote(modeIds);
    }

    static Vote forRejectedModes(Set<Integer> modeIds) {
        return new RejectedModesVote(modeIds);
    }

    static Vote forVotes(List<Vote> votes) {
        List<Vote> combinedVotes = new ArrayList<>();
        for (Vote vote : votes) {
            if (vote != null) {
                combinedVotes.add(vote);
            }
        }
        if (combinedVotes.isEmpty()) {
            return null;
        }
        if (combinedVotes.size() == 1) {
            return combinedVotes.getFirst();
        }

        return new CombinedVote(combinedVotes);
    }

    static String priorityToString(int priority) {
        switch (priority) {
            case PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE:
                return "PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE";
            case PRIORITY_APP_REQUEST_RENDER_FRAME_RATE_RANGE:
                return "PRIORITY_APP_REQUEST_RENDER_FRAME_RATE_RANGE";
            case PRIORITY_APP_REQUEST_SIZE:
                return "PRIORITY_APP_REQUEST_SIZE";
            case PRIORITY_REJECTED_MODES:
                return "PRIORITY_REJECTED_MODES";
            case PRIORITY_DEFAULT_RENDER_FRAME_RATE:
                return "PRIORITY_DEFAULT_REFRESH_RATE";
            case PRIORITY_FLICKER_REFRESH_RATE:
                return "PRIORITY_FLICKER_REFRESH_RATE";
            case PRIORITY_FLICKER_REFRESH_RATE_SWITCH:
                return "PRIORITY_FLICKER_REFRESH_RATE_SWITCH";
            case PRIORITY_HIGH_BRIGHTNESS_MODE:
                return "PRIORITY_HIGH_BRIGHTNESS_MODE";
            case PRIORITY_PROXIMITY:
                return "PRIORITY_PROXIMITY";
            case PRIORITY_LOW_POWER_MODE_MODES:
                return "PRIORITY_LOW_POWER_MODE_MODES";
            case PRIORITY_LOW_POWER_MODE_RENDER_RATE:
                return "PRIORITY_LOW_POWER_MODE_RENDER_RATE";
            case PRIORITY_SKIN_TEMPERATURE:
                return "PRIORITY_SKIN_TEMPERATURE";
            case PRIORITY_USER_PREFERRED:
                return "PRIORITY_USER_PREFERRED";
            case PRIORITY_MEMC:
                return "PRIORITY_MEMC";
            case PRIORITY_UDFPS:
                return "PRIORITY_UDFPS";
            case PRIORITY_USER_SETTING_MIN_RENDER_FRAME_RATE:
                return "PRIORITY_USER_SETTING_MIN_RENDER_FRAME_RATE";
            case PRIORITY_USER_SETTING_DISPLAY_PREFERRED_OPTIONS:
                return "PRIORITY_USER_SETTING_DISPLAY_PREFERRED_MODE";
            case PRIORITY_LIMIT_MODE:
                return "PRIORITY_LIMIT_MODE";
            case PRIORITY_SYNCHRONIZED_REFRESH_RATE:
                return "PRIORITY_SYNCHRONIZED_REFRESH_RATE";
            case PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE:
                return "PRIORITY_SYNCHRONIZED_RENDER_FRAME_RATE";
            case PRIORITY_USER_SETTING_PEAK_REFRESH_RATE:
                return "PRIORITY_USER_SETTING_PEAK_REFRESH_RATE";
            case PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE:
                return "PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE";
            case PRIORITY_AUTH_OPTIMIZER_RENDER_FRAME_RATE:
                return "PRIORITY_AUTH_OPTIMIZER_RENDER_FRAME_RATE";
            case PRIORITY_LAYOUT_LIMITED_REFRESH_RATE:
                return "PRIORITY_LAYOUT_LIMITED_REFRESH_RATE";
            case PRIORITY_LAYOUT_LIMITED_FRAME_RATE:
                return "PRIORITY_LAYOUT_LIMITED_FRAME_RATE";
            case PRIORITY_USER_SETTING_HDR_MODE:
                return "PRIORITY_USER_SETTING_HDR_MODE";
            case PRIORITY_SYSTEM_REQUESTED_MODES:
                return "PRIORITY_SYSTEM_REQUESTED_MODES";
            default:
                return Integer.toString(priority);
        }
    }
}
