package com.excavplayer.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.excavplayer.core.logging.AppLogger
import com.excavplayer.core.result.ExcavResult
import com.excavplayer.media.discovery.MediaSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MediaSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaSyncManager: MediaSyncManager,
    private val logger: AppLogger
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MediaSyncWorker"
        private const val PERIODIC_WORK_NAME = "excav_media_sync_periodic"
        private const val ONE_TIME_WORK_NAME = "excav_media_sync_immediate"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                oneTimeRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        logger.i(TAG, "Executing background MediaSyncWorker")
        return when (val result = mediaSyncManager.syncMediaStore()) {
            is ExcavResult.Success -> {
                logger.i(TAG, "Background media scan completed successfully. Items: ${result.data}")
                Result.success()
            }
            is ExcavResult.Error -> {
                logger.e(TAG, "Background media scan failed", result.exception)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            is ExcavResult.Loading -> Result.retry()
        }
    }
}
