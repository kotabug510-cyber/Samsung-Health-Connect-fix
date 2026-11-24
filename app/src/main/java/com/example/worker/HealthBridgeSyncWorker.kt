package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.bridge.SamsungToHealthConnectBridge
import com.example.data.BridgePreferencesRepository
import com.example.model.SyncWindow
import java.util.concurrent.TimeUnit

/**
 * Automatic background synchronization worker scheduled every 15 minutes via WorkManager.
 * Syncs the trailing 24-hour window with battery-aware constraints and strict upsert deduplication.
 */
class HealthBridgeSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = BridgePreferencesRepository(applicationContext)
        if (!prefs.isAutoSyncEnabled()) {
            return Result.success()
        }

        val bridge = SamsungToHealthConnectBridge(
            context = applicationContext,
            preferencesRepository = prefs
        )
        val summary = bridge.runSync(
            window = SyncWindow.LAST_24_HOURS,
            triggerSource = "Auto (15m)"
        )

        return when {
            summary.success -> Result.success()
            summary.isDeveloperModeError -> Result.failure()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "healthbridge_periodic_sync_15m"
        private const val INTERVAL_MINUTES = 15L

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthBridgeSyncWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    5,
                    TimeUnit.MINUTES
                )
                .addTag(UNIQUE_WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
