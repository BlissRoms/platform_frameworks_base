/*
 * Copyright (C) 2022 The LineageOS Project
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

package com.android.systemui.biometrics

import android.content.Context
import android.view.Surface
import android.os.FileUtils
import android.util.Log
import android.util.Slog
import java.io.IOException

class AsusUdfpsHbmProvider constructor(
    private val context: Context
): UdfpsHbmProvider {

    override fun enableHbm(hbmType: Int, surface: Surface?, onHbmEnabled: Runnable?) {

        // Enable LocalHBM
        try {
            FileUtils.stringToFile(ASUS_LOCAL_HBM_MODE, "1")
        } catch (e: IOException) {
            //Slog.e(TAG, "failed to write to " + ASUS_LOCAL_HBM_MODE)
        }
        onHbmEnabled?.run()
    }

    override fun disableHbm(onHbmDisabled: Runnable?) {

        // Disable LocalHBM
        try {
            FileUtils.stringToFile(ASUS_LOCAL_HBM_MODE, "0")
        } catch (e: IOException) {
            //Slog.e(TAG, "failed to write to " + ASUS_LOCAL_HBM_MODE)
        }

        onHbmDisabled?.run()
    }

    companion object {
	    // UDFPS Local HBM Node
	    private const val ASUS_LOCAL_HBM_MODE = "/proc/localHbm"
    }
}
