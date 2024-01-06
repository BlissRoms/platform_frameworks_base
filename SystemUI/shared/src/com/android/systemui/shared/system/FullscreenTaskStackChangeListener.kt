/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.shared.system

import android.app.ActivityTaskManager
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.IActivityTaskManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.os.RemoteException
import android.util.Log

/**
 * TaskStackChangeListener that ignores freeform / mini-window focuses change
*/
open class FullscreenTaskStackChangeListener(
    private val context: Context,
    private val observeActivityChange: Boolean = false
) : TaskStackChangeListener {

    var debug = false
    var debugTag = DEFAULT_TAG

    private val iActivityTaskManager by lazy { ActivityTaskManager.getService() }

    var topPackageName = String()
        private set
    var topActivityName = String()
        private set
    var topTaskId = INVALID_TASK_ID
        private set

    private fun handleChange(newPackageName: String, newActivityName: String, newTaskId: Int) {
        if (topPackageName == newPackageName && topActivityName == newActivityName) {
            return
        }
        topActivityName = newActivityName
        if (!observeActivityChange && topPackageName == newPackageName) {
            return
        }
        topPackageName = newPackageName
        topTaskId = newTaskId
        if (debug) {
            Log.d(debugTag, "Change: mTopPackage=$topPackageName"
                    + ", mTopActivity=$topActivityName"
                    + ", mTopTaskId=$topTaskId")
        }
        onFullscreenTaskChanged(topPackageName, topActivityName, topTaskId)
    }

    override fun onTaskStackChanged() {
        forceCheck()
    }

    override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
        if (focused) {
            forceCheck()
        }
    }

    fun forceCheck() {
        try {
            iActivityTaskManager.focusedRootTaskInfo?.let { info ->
                info.windowingMode.let {
                    if (it == WINDOWING_MODE_FREEFORM) {
                        return
                    }
                }
                info.topActivity?.let {
                    handleChange(it.packageName, it.className, info.taskId)
                }
            }
        } catch (e: RemoteException) {}
    }

    open fun onFullscreenTaskChanged(packageName: String, activityName: String, taskId: Int) {}

    companion object {
        private const val DEFAULT_TAG = "FullscreenTaskStackChangeListener"
    }
}
