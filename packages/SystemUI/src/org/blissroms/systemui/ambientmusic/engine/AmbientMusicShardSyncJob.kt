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

package org.blissroms.systemui.ambientmusic.engine

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AmbientMusicShardSyncJob : JobService() {

    companion object {
        private const val TAG = "AmbientShardSync"
        private const val JOB_ID = 882049
        private const val SHARDS_SYNC_URL = "https://raw.githubusercontent.com/BlissRoms/platform_vendor_bliss/main/prebuilt/common/ambientmusic/shards"
        private const val SYNC_INTERVAL_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 days

        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val component = ComponentName(context, AmbientMusicShardSyncJob::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setPeriodic(SYNC_INTERVAL_MILLIS)
                .setPersisted(true)
                .build()

            jobScheduler.schedule(jobInfo)
            Log.d(TAG, "Scheduled weekly shard sync job")
        }
    }

    private var syncJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                syncRegionalShards()
                jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Shard sync failed", e)
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        syncJob?.cancel()
        return true
    }

    private fun syncRegionalShards() {
        val telephonyManager = getSystemService(TelephonyManager::class.java)
        val countryCode = telephonyManager?.networkCountryIso?.ifEmpty { null }
            ?: telephonyManager?.simCountryIso?.ifEmpty { null }
            ?: Locale.getDefault().country.lowercase()

        Log.d(TAG, "Syncing ambient song shards for region: $countryCode")
        val targetDir = File(filesDir, "ambient_shards").apply { mkdirs() }
        val shardFileName = "shards_${countryCode}.db"
        val targetFile = File(targetDir, shardFileName)

        try {
            val url = URL("$SHARDS_SYNC_URL/$shardFileName")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Successfully downloaded updated shard: $shardFileName (${targetFile.length()} bytes)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch shard for region $countryCode: ${e.message}")
        }
    }
}
