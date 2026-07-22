/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app;

/** @hide */
public final class AppLockExtras {

    public static final String SETTING_AUTO_BIOMETRIC_PROMPT =
            "app_lock_auto_biometric_prompt";
    public static final String SETTING_RELOCK_BEHAVIOR =
            "app_lock_relock_behavior";
    public static final String SETTING_GRACE_PERIOD_MS =
            "app_lock_grace_period_ms";
    public static final String SETTING_SEPARATE_CREDENTIAL_TYPE =
            "app_lock_separate_credential_type";

    public static final int RELOCK_BEHAVIOR_GRACE_PERIOD = 0;
    public static final int RELOCK_BEHAVIOR_SCREEN_OFF = 1;

    public static final int CREDENTIAL_TYPE_NONE = 0;
    public static final int CREDENTIAL_TYPE_PIN = 1;
    public static final int CREDENTIAL_TYPE_PATTERN = 2;

    public static final int DEFAULT_AUTO_BIOMETRIC_PROMPT = 1;
    public static final int DEFAULT_RELOCK_BEHAVIOR = RELOCK_BEHAVIOR_GRACE_PERIOD;
    public static final long DEFAULT_GRACE_PERIOD_MS = 5000L;

    public static final int MIN_CREDENTIAL_LENGTH = 4;

    public static final String ACTION_VERIFY_CREDENTIAL =
            "com.android.internal.app.applock.action.VERIFY_CREDENTIAL";
    public static final String ACTION_SET_CREDENTIAL =
            "com.android.internal.app.applock.action.SET_CREDENTIAL";
    public static final String ACTION_CLEAR_CREDENTIAL =
            "com.android.internal.app.applock.action.CLEAR_CREDENTIAL";
    public static final String EXTRA_CREDENTIAL_TYPE =
            "com.android.internal.app.applock.extra.CREDENTIAL_TYPE";

    private AppLockExtras() {
    }
}
