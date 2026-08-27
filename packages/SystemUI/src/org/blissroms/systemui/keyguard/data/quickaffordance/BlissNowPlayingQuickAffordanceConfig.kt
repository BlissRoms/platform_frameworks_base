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

package org.blissroms.systemui.keyguard.data.quickaffordance

import android.content.Context
import android.content.Intent
import com.android.systemui.animation.Expandable
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.blissroms.systemui.ambientmusic.NonPixelAmbientIndicationService

@SysUISingleton
class BlissNowPlayingQuickAffordanceConfig
@Inject
constructor(
    @Application private val context: Context,
    private val broadcastSender: BroadcastSender,
) : KeyguardQuickAffordanceConfig {

    override val key: String = "now_playing"

    override val lockScreenState: Flow<KeyguardQuickAffordanceConfig.LockScreenState> =
        flowOf(
            KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                icon =
                    Icon.Resource(
                        R.drawable.ic_now_playing_lockscreen,
                        ContentDescription.Resource(R.string.now_playing_label),
                    ),
                activationState = ActivationState.Inactive,
            )
        )

    override fun pickerName(): String = context.getString(R.string.now_playing_label)

    override val pickerIconResourceId: Int = R.drawable.ic_now_playing_lockscreen

    override suspend fun getPickerScreenState(): KeyguardQuickAffordanceConfig.PickerScreenState =
        KeyguardQuickAffordanceConfig.PickerScreenState.Default()

    override fun onTriggered(
        expandable: Expandable?
    ): KeyguardQuickAffordanceConfig.OnTriggeredResult {
        val blissIntent = Intent(NonPixelAmbientIndicationService.ACTION_BLISS_ON_DEMAND_SEARCH)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        broadcastSender.sendBroadcast(blissIntent)
        return KeyguardQuickAffordanceConfig.OnTriggeredResult.Handled(false)
    }
}
