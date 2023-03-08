/*
 * Copyright (C) 2022 GrapheneOS
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

package android.content.pm;

import android.Manifest;

/** @hide */
public class AppPermissionUtils {

    // If the list of spoofed permissions changes at runtime, make sure to invalidate the permission
    // check cache, it's keyed on the PermissionManager.CACHE_KEY_PACKAGE_INFO system property.
    // Updates of GosPackageState invalidate this cache automatically.
    //
    // android.permission.PermissionManager#checkPermissionUncached
    public static boolean shouldSpoofSelfCheck(String permName) {
        return false;
    }

    // android.app.AppOpsManager#checkOpNoThrow
    // android.app.AppOpsManager#noteOpNoThrow
    // android.app.AppOpsManager#noteProxyOpNoThrow
    // android.app.AppOpsManager#unsafeCheckOpRawNoThrow
    public static boolean shouldSpoofSelfAppOpCheck(int op) {
        return false;
    }
}
