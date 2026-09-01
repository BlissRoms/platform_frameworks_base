package com.google.android.systemui.keyguard

import android.app.AlarmManager
import android.content.Context
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.google.android.systemui.ambientmusic.AmbientIndicationService
import com.google.android.systemui.keyguard.domain.interactor.AmbientIndicationInteractor
import javax.inject.Inject

@SysUISingleton
class AmbientIndicationCoreStartable
@Inject
constructor(
    private val alarmManager: AlarmManager,
    @Application private val context: Context,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val ambientIndicationInteractor: AmbientIndicationInteractor,
) : CoreStartable {

    private fun isGoogleAmbientSupported(): Boolean {
        val isPixel =
            "google".equals(android.os.Build.BRAND, ignoreCase = true) ||
                "google".equals(android.os.Build.MANUFACTURER, ignoreCase = true)
        if (!isPixel) return false
        return try {
            context.packageManager.getPackageInfo("com.google.android.as", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun start() {
        if (!isGoogleAmbientSupported()) {
            return
        }

        val ambientIndicationService =
            AmbientIndicationService(
                alarmManager = alarmManager,
                context = context,
                keyguardUpdateMonitor = keyguardUpdateMonitor,
                selectedUserInteractor = selectedUserInteractor,
                ambientIndicationInteractor = ambientIndicationInteractor,
            )
        if (ambientIndicationService.mStarted) {
            return
        }
        ambientIndicationService.mStarted = true
        val intentFilter =
            IntentFilter().apply {
                addAction("com.google.android.ambientindication.action.AMBIENT_INDICATION_SHOW")
                addAction("com.google.android.ambientindication.action.AMBIENT_INDICATION_EXPAND")
                addAction("com.google.android.ambientindication.action.AMBIENT_INDICATION_HIDE")
                addAction(
                    "com.google.android.ambientindication.action.UPDATE_QUICK_AFFORDANCE_STATE"
                )
            }
        context.registerReceiverAsUser(
            ambientIndicationService,
            UserHandle.ALL,
            intentFilter,
            "com.google.android.ambientindication.permission.AMBIENT_INDICATION",
            null,
            Context.RECEIVER_EXPORTED,
        )
        keyguardUpdateMonitor.registerCallback(ambientIndicationService.mCallback)

        val settingsObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    ambientIndicationService.onSettingsChanged()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("now_playing_on_demand"),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("now_playing_on_demand"),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        ambientIndicationService.onSettingsChanged()
    }
}
