package com.example.bridge

import android.app.Activity
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.Record
import com.example.bridge.mappers.ActivityAndStepsMappers
import com.example.bridge.mappers.BodyAndIntakeMappers
import com.example.bridge.mappers.SleepAndVitalsMappers
import com.example.data.BridgePreferencesRepository
import com.example.model.ConnectionState
import com.example.model.EndpointConnectionStatus
import com.example.model.SamsungExclusiveMetrics
import com.example.model.SamsungSleepRecord
import com.example.model.SyncCategory
import com.example.model.SyncLogEntry
import com.example.model.SyncProgress
import com.example.model.SyncSummary
import com.example.model.SyncWindow
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modular Samsung Health Data SDK -> Android Health Connect direct sync bridge.
 *
 * Reads live telemetry via `HealthDataService.getStore(context)` (no CSV or intermediate files)
 * and writes strictly deduplicated Health Connect records using deterministic `clientRecordId`
 * upsert metadata (`Metadata.autoRecorded(device = Device(type = Device.TYPE_WATCH), ...)`).
 */
class SamsungToHealthConnectBridge(
    private val context: Context,
    private val preferencesRepository: BridgePreferencesRepository = BridgePreferencesRepository(context),
    private val samsungSdkAdapter: SamsungSdkAdapter = SamsungSdkAdapter(context)
) {

    companion object {
        private const val INSERT_BATCH_SIZE = 200
        private const val HEALTH_CONNECT_PROVIDER = "com.google.android.apps.healthdata"
    }

    private fun getHealthConnectClientOrNull(): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER)
        return if (status == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    fun isSkinTemperatureSupported(client: HealthConnectClient?): Boolean {
        if (client == null) return false
        return runCatching {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
    }

    suspend fun getRequiredHealthConnectPermissions(
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): Set<String> {
        val client = getHealthConnectClientOrNull()
        return HealthConnectPermissions.allRequiredPermissions(
            categories = categories,
            skinTemperatureSupported = isSkinTemperatureSupported(client)
        )
    }

    suspend fun checkSamsungStatus(
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): EndpointConnectionStatus = withContext(Dispatchers.IO) {
        samsungSdkAdapter.checkConnectionStatus(categories)
    }

    suspend fun requestSamsungPermissions(
        activity: Activity,
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): EndpointConnectionStatus = samsungSdkAdapter.requestPermissions(activity, categories)

    suspend fun checkHealthConnectStatus(
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): EndpointConnectionStatus = withContext(Dispatchers.IO) {
        val sdkStatus = HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER)
        when (sdkStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> EndpointConnectionStatus(
                state = ConnectionState.UNAVAILABLE,
                headline = "Unavailable",
                detail = "Health Connect is not supported on this Android build."
            )
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> EndpointConnectionStatus(
                state = ConnectionState.APP_NOT_INSTALLED,
                headline = "Update Required",
                detail = "Install or update Health Connect from Google Play."
            )
            else -> {
                val client = HealthConnectClient.getOrCreate(context)
                val required = HealthConnectPermissions.allRequiredPermissions(
                    categories = categories,
                    skinTemperatureSupported = isSkinTemperatureSupported(client)
                )
                val granted = runCatching {
                    client.permissionController.getGrantedPermissions()
                }.getOrDefault(emptySet())
                val matchedCount = required.count { it in granted }
                if (required.isNotEmpty() && matchedCount >= required.size) {
                    EndpointConnectionStatus(
                        state = ConnectionState.CONNECTED,
                        headline = "Connected",
                        detail = "Write access granted ($matchedCount/${required.size} record types)",
                        grantedCount = matchedCount,
                        totalCount = required.size
                    )
                } else {
                    EndpointConnectionStatus(
                        state = ConnectionState.PERMISSIONS_NEEDED,
                        headline = "Permissions Needed",
                        detail = "$matchedCount/${required.size} Health Connect write permissions granted",
                        grantedCount = matchedCount,
                        totalCount = required.size
                    )
                }
            }
        }
    }

    suspend fun refreshSamsungExclusiveMetrics(): SamsungExclusiveMetrics? = withContext(Dispatchers.IO) {
        if (!samsungSdkAdapter.isSdkAarBundled() || !samsungSdkAdapter.isSamsungHealthInstalled()) {
            return@withContext null
        }
        runCatching {
            val metrics = samsungSdkAdapter.readSamsungExclusiveMetrics()
            preferencesRepository.saveSamsungExclusiveMetrics(metrics)
            metrics
        }.getOrNull()
    }

    suspend fun runSync(
        window: SyncWindow,
        triggerSource: String = "Manual",
        onProgress: (SyncProgress) -> Unit = {}
    ): SyncSummary = withContext(Dispatchers.IO) {
        val startedAtMs = System.currentTimeMillis()
        val enabledCategories = preferencesRepository.getEnabledCategories()

        if (enabledCategories.isEmpty()) {
            val summary = SyncSummary(
                timestampEpochMs = startedAtMs,
                windowLabel = window.label,
                success = false,
                errorMessage = "No sync categories are enabled. Enable at least one category in the Categories tab."
            )
            persistOutcome(summary, triggerSource)
            return@withContext summary
        }

        if (!samsungSdkAdapter.isSdkAarBundled()) {
            val summary = SyncSummary(
                timestampEpochMs = startedAtMs,
                windowLabel = window.label,
                success = false,
                errorMessage = "Samsung Health Data SDK AAR not bundled. Add samsung-health-data-api-1.1.0.aar to app/libs/ and rebuild."
            )
            persistOutcome(summary, triggerSource)
            return@withContext summary
        }

        if (!samsungSdkAdapter.isSamsungHealthInstalled()) {
            val summary = SyncSummary(
                timestampEpochMs = startedAtMs,
                windowLabel = window.label,
                success = false,
                errorMessage = "Samsung Health (com.sec.android.app.shealth) is not installed on this device."
            )
            persistOutcome(summary, triggerSource)
            return@withContext summary
        }

        val hcClient = getHealthConnectClientOrNull()
        if (hcClient == null) {
            val summary = SyncSummary(
                timestampEpochMs = startedAtMs,
                windowLabel = window.label,
                success = false,
                errorMessage = "Android Health Connect is unavailable on this device."
            )
            persistOutcome(summary, triggerSource)
            return@withContext summary
        }

        val grantedHcPermissions = runCatching {
            hcClient.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

        if (grantedHcPermissions.isEmpty()) {
            val summary = SyncSummary(
                timestampEpochMs = startedAtMs,
                windowLabel = window.label,
                success = false,
                errorMessage = "No Health Connect write permissions granted. Tap Grant Access on the Health Connect card."
            )
            persistOutcome(summary, triggerSource)
            return@withContext summary
        }

        val endLocal = LocalDateTime.now()
        val startLocal = endLocal.minusHours(window.hours)
        val versionMs = System.currentTimeMillis()
        val categoryCounts = linkedMapOf<String, Int>()
        val categoryWarnings = mutableListOf<String>()

        // Order ensures Sleep is read before Heart Rate so overnight RHR derivation uses actual sleep windows
        val orderedCategories = listOf(
            SyncCategory.STEPS,
            SyncCategory.SLEEP,
            SyncCategory.SKIN_TEMPERATURE,
            SyncCategory.HEART_RATE,
            SyncCategory.WORKOUTS_GPS,
            SyncCategory.VITALS,
            SyncCategory.BODY_COMPOSITION,
            SyncCategory.NUTRITION_HYDRATION
        ).filter { it in enabledCategories }

        val totalStages = orderedCategories.size + 1 // +1 for Samsung exclusive dashboard metrics
        var completedStages = 0
        var cachedSleepSessions: List<SamsungSleepRecord> = emptyList()

        try {
            for (category in orderedCategories) {
                onProgress(
                    SyncProgress(
                        isRunning = true,
                        windowLabel = window.label,
                        currentStage = "Syncing ${category.title}...",
                        completedSteps = completedStages,
                        totalSteps = totalStages
                    )
                )

                val writtenCount = when (category) {
                    SyncCategory.STEPS -> syncHourlySteps(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                    SyncCategory.SLEEP -> {
                        val (count, sessions) = syncSleep(
                            hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                        )
                        cachedSleepSessions = sessions
                        count
                    }
                    SyncCategory.SKIN_TEMPERATURE -> syncSkinTemperature(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                    SyncCategory.HEART_RATE -> {
                        if (cachedSleepSessions.isEmpty()) {
                            cachedSleepSessions = runCatching {
                                samsungSdkAdapter.readSleepSessions(startLocal, endLocal)
                            }.getOrDefault(emptyList())
                        }
                        syncHeartRateAndRestingHr(
                            hcClient,
                            grantedHcPermissions,
                            startLocal,
                            endLocal,
                            cachedSleepSessions,
                            versionMs
                        )
                    }
                    SyncCategory.WORKOUTS_GPS -> syncWorkoutsAndActivity(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                    SyncCategory.VITALS -> syncVitals(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                    SyncCategory.BODY_COMPOSITION -> syncBodyComposition(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                    SyncCategory.NUTRITION_HYDRATION -> syncNutritionAndHydration(
                        hcClient, grantedHcPermissions, startLocal, endLocal, versionMs
                    )
                }

                categoryCounts[category.id] = writtenCount
                completedStages++
            }

            // Refresh Samsung-only dashboard metrics (Energy Score, Sleep Apnea, IHRN)
            onProgress(
                SyncProgress(
                    isRunning = true,
                    windowLabel = window.label,
                    currentStage = "Refreshing Samsung exclusive insights...",
                    completedSteps = completedStages,
                    totalSteps = totalStages
                )
            )
            runCatching {
                val exclusive = samsungSdkAdapter.readSamsungExclusiveMetrics()
                preferencesRepository.saveSamsungExclusiveMetrics(exclusive)
            }.onFailure { err ->
                categoryWarnings += "Samsung exclusive metrics skipped: ${err.message}"
            }

            completedStages = totalStages
            onProgress(
                SyncProgress(
                    isRunning = false,
                    windowLabel = window.label,
                    currentStage = "Sync complete",
                    completedSteps = completedStages,
                    totalSteps = totalStages
                )
            )

            val totalWritten = categoryCounts.values.sum()
            val summary = SyncSummary(
                timestampEpochMs = System.currentTimeMillis(),
                windowLabel = window.label,
                success = true,
                totalRecordsWritten = totalWritten,
                categoryCounts = categoryCounts,
                errorMessage = categoryWarnings.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
            persistOutcome(summary, triggerSource)
            summary
        } catch (devModeErr: SamsungDeveloperModeException) {
            onProgress(SyncProgress(isRunning = false))
            val summary = SyncSummary(
                timestampEpochMs = System.currentTimeMillis(),
                windowLabel = window.label,
                success = false,
                totalRecordsWritten = categoryCounts.values.sum(),
                categoryCounts = categoryCounts,
                errorMessage = devModeErr.message,
                isDeveloperModeError = true
            )
            persistOutcome(summary, triggerSource)
            summary
        } catch (t: Throwable) {
            onProgress(SyncProgress(isRunning = false))
            val summary = SyncSummary(
                timestampEpochMs = System.currentTimeMillis(),
                windowLabel = window.label,
                success = false,
                totalRecordsWritten = categoryCounts.values.sum(),
                categoryCounts = categoryCounts,
                errorMessage = t.message ?: "Sync failed unexpectedly."
            )
            persistOutcome(summary, triggerSource)
            summary
        }
    }

    private suspend fun syncHourlySteps(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        if (HealthConnectPermissions.WRITE_STEPS !in granted) return 0
        val buckets = samsungSdkAdapter.readHourlySteps(startLocal, endLocal)
        val records = ActivityAndStepsMappers.mapHourlySteps(buckets, versionMs)
        return insertInBatches(client, records)
    }

    private suspend fun syncSleep(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Pair<Int, List<SamsungSleepRecord>> {
        val rawSleep = samsungSdkAdapter.readSleepSessions(startLocal, endLocal)
        if (HealthConnectPermissions.WRITE_SLEEP !in granted) return 0 to rawSleep
        val records = SleepAndVitalsMappers.mapSleepSessions(rawSleep, versionMs)
        return insertInBatches(client, records) to rawSleep
    }

    private suspend fun syncSkinTemperature(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        if (!isSkinTemperatureSupported(client)) return 0
        if (HealthConnectPermissions.WRITE_SKIN_TEMPERATURE !in granted) return 0
        val raw = samsungSdkAdapter.readSkinTemperature(startLocal, endLocal)
        val records = SleepAndVitalsMappers.mapSkinTemperature(raw, versionMs)
        return insertInBatches(client, records)
    }

    private suspend fun syncHeartRateAndRestingHr(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        sleepSessions: List<SamsungSleepRecord>,
        versionMs: Long
    ): Int {
        val canWriteHr = HealthConnectPermissions.WRITE_HEART_RATE in granted
        val canWriteRhr = HealthConnectPermissions.WRITE_RESTING_HEART_RATE in granted
        if (!canWriteHr && !canWriteRhr) return 0

        val samples = samsungSdkAdapter.readHeartRateSamples(startLocal, endLocal)
        val records = mutableListOf<Record>()
        if (canWriteHr) {
            records += SleepAndVitalsMappers.mapHeartRateWindows(samples, versionMs)
        }
        if (canWriteRhr) {
            records += SleepAndVitalsMappers.deriveDailyRestingHeartRate(
                heartRateSamples = samples,
                sleepSessions = sleepSessions,
                zoneId = ZoneId.systemDefault(),
                versionMs = versionMs
            )
        }
        return insertInBatches(client, records)
    }

    private suspend fun syncWorkoutsAndActivity(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        val records = mutableListOf<Record>()

        val summaries = runCatching {
            samsungSdkAdapter.readActivitySummaries(startLocal, endLocal)
        }.getOrDefault(emptyList())
        records += ActivityAndStepsMappers.mapActivitySummaries(summaries, granted, versionMs)

        val exercises = samsungSdkAdapter.readExercisesWithRoutes(startLocal, endLocal)
        records += ActivityAndStepsMappers.mapExercises(exercises, granted, versionMs)

        if (HealthConnectPermissions.WRITE_FLOORS in granted) {
            val floors = runCatching {
                samsungSdkAdapter.readFloorsClimbed(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += ActivityAndStepsMappers.mapFloorsClimbed(floors, versionMs)
        }

        return insertInBatches(client, records)
    }

    private suspend fun syncVitals(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        val records = mutableListOf<Record>()

        if (HealthConnectPermissions.WRITE_OXYGEN_SATURATION in granted) {
            val spo2 = runCatching {
                samsungSdkAdapter.readBloodOxygen(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += SleepAndVitalsMappers.mapBloodOxygen(spo2, versionMs)
        }
        if (HealthConnectPermissions.WRITE_BODY_TEMPERATURE in granted) {
            val bodyTemp = runCatching {
                samsungSdkAdapter.readBodyTemperature(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += SleepAndVitalsMappers.mapBodyTemperature(bodyTemp, versionMs)
        }
        if (HealthConnectPermissions.WRITE_BLOOD_PRESSURE in granted) {
            val bp = runCatching {
                samsungSdkAdapter.readBloodPressure(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += SleepAndVitalsMappers.mapBloodPressure(bp, versionMs)
        }
        if (HealthConnectPermissions.WRITE_BLOOD_GLUCOSE in granted) {
            val bg = runCatching {
                samsungSdkAdapter.readBloodGlucose(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += SleepAndVitalsMappers.mapBloodGlucose(bg, versionMs)
        }

        return insertInBatches(client, records)
    }

    private suspend fun syncBodyComposition(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        val bodyRecords = samsungSdkAdapter.readBodyComposition(startLocal, endLocal)
        val profile = runCatching { samsungSdkAdapter.readUserProfile() }.getOrNull()
        val records = BodyAndIntakeMappers.mapBodyCompositionAndProfile(
            bodyRecords = bodyRecords,
            profile = profile,
            grantedPermissions = granted,
            versionMs = versionMs
        )
        return insertInBatches(client, records)
    }

    private suspend fun syncNutritionAndHydration(
        client: HealthConnectClient,
        granted: Set<String>,
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        versionMs: Long
    ): Int {
        val records = mutableListOf<Record>()
        if (HealthConnectPermissions.WRITE_HYDRATION in granted) {
            val water = runCatching {
                samsungSdkAdapter.readWaterIntake(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += BodyAndIntakeMappers.mapHydration(water, versionMs)
        }
        if (HealthConnectPermissions.WRITE_NUTRITION in granted) {
            val nutrition = runCatching {
                samsungSdkAdapter.readNutrition(startLocal, endLocal)
            }.getOrDefault(emptyList())
            records += BodyAndIntakeMappers.mapNutrition(nutrition, versionMs)
        }
        return insertInBatches(client, records)
    }

    private suspend fun insertInBatches(
        client: HealthConnectClient,
        records: List<Record>
    ): Int {
        if (records.isEmpty()) return 0
        var written = 0
        records.chunked(INSERT_BATCH_SIZE).forEach { batch ->
            client.insertRecords(batch)
            written += batch.size
        }
        return written
    }

    private suspend fun persistOutcome(summary: SyncSummary, triggerSource: String) {
        val msg = when {
            summary.success && summary.errorMessage.isNullOrBlank() ->
                "Upserted ${summary.totalRecordsWritten} deduplicated record(s) across ${summary.categoryCounts.size} categories."
            summary.success ->
                "Upserted ${summary.totalRecordsWritten} record(s). Note: ${summary.errorMessage}"
            else ->
                summary.errorMessage ?: "Sync did not complete."
        }
        val entry = SyncLogEntry(
            timestampEpochMs = summary.timestampEpochMs,
            windowLabel = summary.windowLabel,
            triggerSource = triggerSource,
            success = summary.success,
            totalRecordsWritten = summary.totalRecordsWritten,
            categoryCounts = summary.categoryCounts,
            message = msg,
            isDeveloperModeError = summary.isDeveloperModeError
        )
        preferencesRepository.recordSyncResult(summary, entry)
    }
}
