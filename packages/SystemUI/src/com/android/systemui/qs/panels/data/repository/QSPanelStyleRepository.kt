/*
 * Copyright (C) 2014-2026 The BlissRoms Project
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

package com.android.systemui.qs.panels.data.repository

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

@SysUISingleton
class QSPanelStyleRepository
@Inject
constructor(
    private val contentResolver: ContentResolver,
) {
    val panelStyle: Flow<Int> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(getCurrentStyle())
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(QS_PANEL_STYLE_KEY),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        trySend(getCurrentStyle())
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.conflate().distinctUntilChanged()

    private fun getCurrentStyle(): Int {
        return Settings.Secure.getIntForUser(
            contentResolver,
            QS_PANEL_STYLE_KEY,
            STYLE_CARD,
            UserHandle.USER_CURRENT,
        )
    }

    companion object {
        const val QS_PANEL_STYLE_KEY = "qs_panel_style"
        const val STYLE_CARD = 0
        const val STYLE_CLASSIC_CIRCULAR = 1
    }
}
