package com.example.bridge

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import com.example.model.ConnectionState
import com.example.model.EndpointConnectionStatus
import com.example.model.SamsungActivitySummaryBucket
import com.example.model.SamsungBloodGlucoseSample
import com.example.model.SamsungBloodOxygenSample
import com.example.model.SamsungBloodPressureSample
import com.example.model.SamsungBodyCompositionRecord
import com.example.model.SamsungBodyTempSample
import com.example.model.SamsungExclusiveMetrics
import com.example.model.SamsungExerciseRecord
import com.example.model.SamsungFloorsRecord
import com.example.model.SamsungGpsPoint
import com.example.model.SamsungHeartRateSample
import com.example.model.SamsungHourlyStepBucket
import com.example.model.SamsungNutritionRecord
import com.example.model.SamsungSkinTempPoint
import com.example.model.SamsungSkinTempRecord
import com.example.model.SamsungSleepRecord
import com.example.model.SamsungSleepStageKind
import com.example.model.SamsungSleepStageSample
import com.example.model.SamsungUserProfile
import com.example.model.SamsungWaterIntakeSample
import com.example.model.SyncCategory
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.SleepStage
import com.samsung.android.sdk.health.data.error.AuthorizationException
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.IdFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class SamsungDeveloperModeException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Direct adapter for the Samsung Health Data SDK (`HealthDataService.getStore(context)`).
 * Guards against missing AAR at runtime so the app operates safely even before
 * `samsung-health-data-api-1.1.0.aar` is dropped into `app/libs/`.
 */
class SamsungSdkAdapter(private val context: Context) {

    companion object {
        const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
        private const val SDK_ENTRY_CLASS = "com.samsung.android.sdk.health.data.HealthDataService"
    }

    fun isSdkAarBundled(): Boolean = try {
        Class.forName(SDK_ENTRY_CLASS)
        true
    } catch (_: Throwable) {
        false
    }

    fun isSamsungHealthInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    private fun requireStore(): HealthDataStore {
        if (!isSdkAarBundled()) {
            throw IllegalStateException(
                "Samsung Health Data SDK AAR not bundled. Place samsung-health-data-api-1.1.0.aar in app/libs/."
            )
        }
        if (!isSamsungHealthInstalled()) {
            throw IllegalStateException(
                "Samsung Health ($SAMSUNG_HEALTH_PACKAGE) is not installed on this device."
            )
        }
        return HealthDataService.getStore(context.applicationContext)
    }

    private fun requiredPermissionsFor(categories: Set<SyncCategory>): Set<Permission> {
        val perms = mutableSetOf<Permission>()
        if (SyncCategory.STEPS in categories) {
            perms += Permission.of(DataTypes.STEPS, AccessType.READ)
        }
        if (SyncCategory.WORKOUTS_GPS in categories) {
            perms += Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)
            perms += Permission.of(DataTypes.EXERCISE, AccessType.READ)
            perms += Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ)
            perms += Permission.of(DataTypes.FLOORS_CLIMBED, AccessType.READ)
        }
        if (SyncCategory.SLEEP in categories) {
            perms += Permission.of(DataTypes.SLEEP, AccessType.READ)
        }
        if (SyncCategory.SKIN_TEMPERATURE in categories) {
            perms += Permission.of(DataTypes.SKIN_TEMPERATURE, AccessType.READ)
        }
        if (SyncCategory.HEART_RATE in categories) {
            perms += Permission.of(DataTypes.HEART_RATE, AccessType.READ)
        }
        if (SyncCategory.VITALS in categories) {
            perms += Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ)
            perms += Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ)
            perms += Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ)
            perms += Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
        }
        if (SyncCategory.BODY_COMPOSITION in categories) {
            perms += Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)
            perms += Permission.of(DataTypes.USER_PROFILE, AccessType.READ)
        }
        if (SyncCategory.NUTRITION_HYDRATION in categories) {
            perms += Permission.of(DataTypes.WATER_INTAKE, AccessType.READ)
            perms += Permission.of(DataTypes.NUTRITION, AccessType.READ)
        }
        // Always include Samsung-only read-only dashboard insights
        perms += Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ)
        perms += Permission.of(DataTypes.SLEEP_APNEA, AccessType.READ)
        perms += Permission.of(DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, AccessType.READ)
        return perms
    }

    suspend fun checkConnectionStatus(
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): EndpointConnectionStatus {
        if (!isSdkAarBundled()) {
            return EndpointConnectionStatus(
                state = ConnectionState.SDK_NOT_BUNDLED,
                headline = "SDK AAR Needed",
                detail = "Place samsung-health-data-api-1.1.0.aar in app/libs/ to enable direct Samsung Health reads."
            )
        }
        if (!isSamsungHealthInstalled()) {
            return EndpointConnectionStatus(
                state = ConnectionState.APP_NOT_INSTALLED,
                headline = "Samsung Health Missing",
                detail = "Install Samsung Health (6.30.2+) and enable Developer Mode (Samsung Health Data SDK)."
            )
        }

        val required = requiredPermissionsFor(categories)
        return try {
            val store = requireStore()
            val granted = store.getGrantedPermissions(required)
            if (granted.size >= required.size) {
                EndpointConnectionStatus(
                    state = ConnectionState.CONNECTED,
                    headline = "Connected",
                    detail = "Direct SDK access active (${granted.size}/${required.size} data types)",
                    grantedCount = granted.size,
                    totalCount = required.size
                )
            } else {
                EndpointConnectionStatus(
                    state = ConnectionState.PERMISSIONS_NEEDED,
                    headline = "Permissions Needed",
                    detail = "${granted.size}/${required.size} Samsung Health permissions granted",
                    grantedCount = granted.size,
                    totalCount = required.size
                )
            }
        } catch (t: Throwable) {
            classifyConnectionError(t, required.size)
        }
    }

    suspend fun requestPermissions(
        activity: Activity,
        categories: Set<SyncCategory> = SyncCategory.entries.toSet()
    ): EndpointConnectionStatus {
        if (!isSdkAarBundled()) {
            return checkConnectionStatus(categories)
        }
        if (!isSamsungHealthInstalled()) {
            return checkConnectionStatus(categories)
        }
        val required = requiredPermissionsFor(categories)
        return try {
            val store = requireStore()
            val granted = store.requestPermissions(required, activity)
            if (granted.size >= required.size) {
                EndpointConnectionStatus(
                    state = ConnectionState.CONNECTED,
                    headline = "Connected",
                    detail = "All ${granted.size} Samsung Health permissions granted",
                    grantedCount = granted.size,
                    totalCount = required.size
                )
            } else {
                EndpointConnectionStatus(
                    state = ConnectionState.PERMISSIONS_NEEDED,
                    headline = "Partial Access",
                    detail = "${granted.size}/${required.size} Samsung Health permissions granted",
                    grantedCount = granted.size,
                    totalCount = required.size
                )
            }
        } catch (t: Throwable) {
            if (t is ResolvablePlatformException && t.hasResolution) {
                runCatching { t.resolve(activity) }
            }
            classifyConnectionError(t, required.size)
        }
    }

    /**
     * Queries `DataTypes.STEPS.TOTAL` grouped into strict 1-hour windows using
     * `LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)`.
     */
    suspend fun readHourlySteps(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungHourlyStepBucket> = wrapSdkCall {
        val store = requireStore()
        val request = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(startLocal, endLocal),
                LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
            )
            .setOrdering(Ordering.ASC)
            .build()

        val zone = ZoneId.systemDefault()
        store.aggregateData(request).dataList.mapNotNull { item ->
            val count = item.value ?: 0L
            if (count <= 0L) return@mapNotNull null
            val offset = item.zoneOffset ?: zone.rules.getOffset(item.startTime)
            SamsungHourlyStepBucket(
                startTime = item.startTime,
                endTime = item.endTime,
                zoneOffset = offset,
                steps = count
            )
        }
    }

    suspend fun readActivitySummaries(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungActivitySummaryBucket> = wrapSdkCall {
        val store = requireStore()
        val filter = LocalTimeFilter.of(startLocal, endLocal)
        val group = LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
        val zone = ZoneId.systemDefault()

        val distByStart = store.aggregateData(
            DataType.ActivitySummaryType.TOTAL_DISTANCE.requestBuilder
                .setLocalTimeFilterWithGroup(filter, group)
                .setOrdering(Ordering.ASC)
                .build()
        ).dataList.associateBy { it.startTime }

        val activeCalByStart = store.aggregateData(
            DataType.ActivitySummaryType.ACTIVE_CALORIES_BURNED.requestBuilder
                .setLocalTimeFilterWithGroup(filter, group)
                .setOrdering(Ordering.ASC)
                .build()
        ).dataList.associateBy { it.startTime }

        val totalCalByStart = store.aggregateData(
            DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
                .setLocalTimeFilterWithGroup(filter, group)
                .setOrdering(Ordering.ASC)
                .build()
        ).dataList.associateBy { it.startTime }

        val allStartTimes = (distByStart.keys + activeCalByStart.keys + totalCalByStart.keys).sorted()
        allStartTimes.mapNotNull { start ->
            val dist = distByStart[start]
            val active = activeCalByStart[start]
            val total = totalCalByStart[start]
            val end = dist?.endTime ?: active?.endTime ?: total?.endTime ?: start.plusSeconds(3600)
            val offset = dist?.zoneOffset ?: active?.zoneOffset ?: total?.zoneOffset
                ?: zone.rules.getOffset(start)
            val dVal = dist?.value?.toDouble()?.takeIf { it > 0.0 }
            val aVal = active?.value?.toDouble()?.takeIf { it > 0.0 }
            val tVal = total?.value?.toDouble()?.takeIf { it > 0.0 }
            if (dVal == null && aVal == null && tVal == null) return@mapNotNull null
            SamsungActivitySummaryBucket(
                startTime = start,
                endTime = end,
                zoneOffset = offset,
                distanceMeters = dVal,
                activeCaloriesKcal = aVal,
                totalCaloriesKcal = tVal
            )
        }
    }

    suspend fun readExercisesWithRoutes(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungExerciseRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.EXERCISE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        val exercisePoints = store.readData(request).dataList
        exercisePoints.flatMap { point ->
            val offset = point.zoneOffset ?: zone.rules.getOffset(point.startTime)
            val associatedGps = fetchAssociatedExerciseRoute(store, point.uid)
            val sessions = point.getValue(DataType.ExerciseType.SESSIONS)

            if (!sessions.isNullOrEmpty()) {
                sessions.mapIndexed { idx, session ->
                    val sessionRoute = session.route?.map { loc ->
                        SamsungGpsPoint(
                            timestamp = loc.timestamp,
                            latitude = loc.latitude.toDouble(),
                            longitude = loc.longitude.toDouble(),
                            altitudeMeters = loc.altitude.toDouble(),
                            accuracyMeters = loc.accuracy.toDouble()
                        )
                    }.orEmpty().ifEmpty { associatedGps }

                    SamsungExerciseRecord(
                        uid = if (sessions.size == 1) point.uid else "${point.uid}_$idx",
                        startTime = session.startTime,
                        endTime = session.endTime,
                        zoneOffset = offset,
                        samsungExerciseTypeName = session.exerciseType.name,
                        title = session.customTitle ?: point.getValue(DataType.ExerciseType.CUSTOM_TITLE),
                        caloriesKcal = session.calories.toDouble().takeIf { it > 0.0 },
                        distanceMeters = session.distance.toDouble().takeIf { it > 0.0 },
                        meanSpeedMetersPerSec = session.meanSpeed.toDouble().takeIf { it > 0.0 },
                        maxSpeedMetersPerSec = session.maxSpeed.toDouble().takeIf { it > 0.0 },
                        elevationGainMeters = session.altitudeGain.toDouble().takeIf { it > 0.0 },
                        vo2Max = session.vo2Max.toDouble().takeIf { it > 0.0 },
                        routePoints = sessionRoute
                    )
                }
            } else {
                val end = point.endTime ?: point.startTime.plusSeconds(60)
                listOf(
                    SamsungExerciseRecord(
                        uid = point.uid,
                        startTime = point.startTime,
                        endTime = end,
                        zoneOffset = offset,
                        samsungExerciseTypeName = point.getValue(DataType.ExerciseType.EXERCISE_TYPE)?.name
                            ?: "CUSTOM",
                        title = point.getValue(DataType.ExerciseType.CUSTOM_TITLE),
                        caloriesKcal = point.getValue(DataType.ExerciseType.CALORIES)?.toDouble(),
                        distanceMeters = point.getValue(DataType.ExerciseType.DISTANCE)?.toDouble(),
                        meanSpeedMetersPerSec = point.getValue(DataType.ExerciseType.MEAN_SPEED)?.toDouble(),
                        maxSpeedMetersPerSec = point.getValue(DataType.ExerciseType.MAX_SPEED)?.toDouble(),
                        elevationGainMeters = point.getValue(DataType.ExerciseType.ALTITUDE_GAIN)?.toDouble(),
                        vo2Max = point.getValue(DataType.ExerciseType.VO2_MAX)?.toDouble(),
                        routePoints = associatedGps
                    )
                )
            }
        }
    }

    private suspend fun fetchAssociatedExerciseRoute(
        store: HealthDataStore,
        exerciseUid: String
    ): List<SamsungGpsPoint> = runCatching {
        val assocReq = DataTypes.EXERCISE.associatedReadRequestBuilder
            .setIdFilter(IdFilter.fromDataUid(exerciseUid))
            .addAssociatedDataType(DataType.ExerciseType.Associates.EXERCISE_LOCATION)
            .build()

        store.readAssociatedData(assocReq).dataList.flatMap { assoc ->
            assoc.getDataPointOf(DataTypes.EXERCISE_LOCATION).orEmpty().flatMap { locPoint ->
                val list = locPoint.getValue(DataType.ExerciseLocationType.LOCATION_DATA)
                if (!list.isNullOrEmpty()) {
                    list.map { l ->
                        SamsungGpsPoint(
                            timestamp = l.timestamp,
                            latitude = l.latitude.toDouble(),
                            longitude = l.longitude.toDouble(),
                            altitudeMeters = l.altitude.toDouble(),
                            accuracyMeters = l.accuracy.toDouble()
                        )
                    }
                } else {
                    val lat = locPoint.getValue(DataType.ExerciseLocationType.LATITUDE)?.toDouble()
                    val lon = locPoint.getValue(DataType.ExerciseLocationType.LONGITUDE)?.toDouble()
                    if (lat != null && lon != null) {
                        listOf(
                            SamsungGpsPoint(
                                timestamp = locPoint.startTime,
                                latitude = lat,
                                longitude = lon,
                                altitudeMeters = locPoint.getValue(DataType.ExerciseLocationType.ALTITUDE)?.toDouble(),
                                accuracyMeters = locPoint.getValue(DataType.ExerciseLocationType.ACCURACY)?.toDouble()
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    suspend fun readFloorsClimbed(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungFloorsRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.FLOORS_CLIMBED.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val floors = pt.getValue(DataType.FloorsClimbedType.FLOOR)?.toDouble() ?: return@mapNotNull null
            SamsungFloorsRecord(
                uid = pt.uid,
                startTime = pt.startTime,
                endTime = pt.endTime ?: pt.startTime.plusSeconds(60),
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                floors = floors
            )
        }
    }

    suspend fun readSleepSessions(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungSleepRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.map { pt ->
            val offset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime)
            val sessions = pt.getValue(DataType.SleepType.SESSIONS).orEmpty()
            val stages = sessions.flatMap { session ->
                session.stages.orEmpty().map { st ->
                    SamsungSleepStageSample(
                        startTime = st.startTime,
                        endTime = st.endTime,
                        kind = when (st.stage) {
                            SleepStage.StageType.AWAKE -> SamsungSleepStageKind.AWAKE
                            SleepStage.StageType.LIGHT -> SamsungSleepStageKind.LIGHT
                            SleepStage.StageType.DEEP -> SamsungSleepStageKind.DEEP
                            SleepStage.StageType.REM -> SamsungSleepStageKind.REM
                            else -> SamsungSleepStageKind.UNKNOWN
                        }
                    )
                }
            }
            val start = sessions.minOfOrNull { it.startTime } ?: pt.startTime
            val end = sessions.maxOfOrNull { it.endTime } ?: pt.endTime ?: pt.startTime.plusSeconds(60)
            SamsungSleepRecord(
                uid = pt.uid,
                startTime = start,
                endTime = end,
                zoneOffset = offset,
                sleepScore = pt.getValue(DataType.SleepType.SLEEP_SCORE),
                stages = stages
            )
        }
    }

    suspend fun readSkinTemperature(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungSkinTempRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.map { pt ->
            val offset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime)
            val avg = pt.getValue(DataType.SkinTemperatureType.SKIN_TEMPERATURE)?.toDouble()
            val series = pt.getValue(DataType.SkinTemperatureType.SERIES_DATA).orEmpty().map { s ->
                SamsungSkinTempPoint(time = s.startTime, celsius = s.skinTemperature.toDouble())
            }
            SamsungSkinTempRecord(
                uid = pt.uid,
                startTime = pt.startTime,
                endTime = pt.endTime ?: pt.startTime.plusSeconds(60),
                zoneOffset = offset,
                averageCelsius = avg,
                samples = series
            )
        }
    }

    suspend fun readHeartRateSamples(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungHeartRateSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.HEART_RATE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.flatMap { pt ->
            val offset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime)
            val series = pt.getValue(DataType.HeartRateType.SERIES_DATA)
            if (!series.isNullOrEmpty()) {
                series.mapIndexedNotNull { idx, item ->
                    val bpm = item.heartRate.toLong()
                    if (bpm <= 0L) return@mapIndexedNotNull null
                    SamsungHeartRateSample(
                        uid = "${pt.uid}_$idx",
                        time = item.startTime,
                        zoneOffset = offset,
                        bpm = bpm
                    )
                }
            } else {
                val bpm = pt.getValue(DataType.HeartRateType.HEART_RATE)?.toLong()
                if (bpm != null && bpm > 0L) {
                    listOf(
                        SamsungHeartRateSample(
                            uid = pt.uid,
                            time = pt.startTime,
                            zoneOffset = offset,
                            bpm = bpm
                        )
                    )
                } else {
                    emptyList()
                }
            }
        }
    }

    suspend fun readBloodOxygen(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungBloodOxygenSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val pct = pt.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)?.toDouble()
                ?: return@mapNotNull null
            SamsungBloodOxygenSample(
                uid = pt.uid,
                time = pt.startTime,
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                percentage = pct
            )
        }
    }

    suspend fun readBodyTemperature(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungBodyTempSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.BODY_TEMPERATURE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val celsius = pt.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE)?.toDouble()
                ?: return@mapNotNull null
            SamsungBodyTempSample(
                uid = pt.uid,
                time = pt.startTime,
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                celsius = celsius
            )
        }
    }

    suspend fun readBloodPressure(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungBloodPressureSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val sys = pt.getValue(DataType.BloodPressureType.SYSTOLIC)?.toDouble() ?: return@mapNotNull null
            val dia = pt.getValue(DataType.BloodPressureType.DIASTOLIC)?.toDouble() ?: return@mapNotNull null
            SamsungBloodPressureSample(
                uid = pt.uid,
                time = pt.startTime,
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                systolicMmHg = sys,
                diastolicMmHg = dia
            )
        }
    }

    suspend fun readBloodGlucose(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungBloodGlucoseSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val mmol = pt.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)?.toDouble()
                ?: return@mapNotNull null
            SamsungBloodGlucoseSample(
                uid = pt.uid,
                time = pt.startTime,
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                mmolPerLiter = mmol,
                mealStatus = pt.getValue(DataType.BloodGlucoseType.MEAL_STATUS)?.name
            )
        }
    }

    suspend fun readBodyComposition(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungBodyCompositionRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.map { pt ->
            SamsungBodyCompositionRecord(
                uid = pt.uid,
                time = pt.startTime,
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                weightKg = pt.getValue(DataType.BodyCompositionType.WEIGHT)?.toDouble(),
                heightCm = pt.getValue(DataType.BodyCompositionType.HEIGHT)?.toDouble(),
                bodyFatPercentage = pt.getValue(DataType.BodyCompositionType.BODY_FAT)?.toDouble(),
                leanBodyMassKg = pt.getValue(DataType.BodyCompositionType.FAT_FREE_MASS)?.toDouble()
                    ?: pt.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS)?.toDouble(),
                basalMetabolicRateKcalPerDay = pt.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE)?.toDouble()
            )
        }
    }

    suspend fun readUserProfile(): SamsungUserProfile? = wrapSdkCall {
        val store = requireStore()
        val request = DataTypes.USER_PROFILE.readDataRequestBuilder.build()
        val profile = store.readData(request).dataList.firstOrNull() ?: return@wrapSdkCall null
        SamsungUserProfile(
            uid = profile.uid,
            heightCm = profile.getValue(DataType.UserProfileType.HEIGHT)?.toDouble(),
            weightKg = profile.getValue(DataType.UserProfileType.WEIGHT)?.toDouble(),
            nickname = profile.getValue(DataType.UserProfileType.NICKNAME)
        )
    }

    suspend fun readWaterIntake(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungWaterIntakeSample> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.WATER_INTAKE.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.mapNotNull { pt ->
            val ml = pt.getValue(DataType.WaterIntakeType.AMOUNT)?.toDouble() ?: return@mapNotNull null
            SamsungWaterIntakeSample(
                uid = pt.uid,
                startTime = pt.startTime,
                endTime = pt.endTime ?: pt.startTime.plusSeconds(60),
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                volumeMl = ml
            )
        }
    }

    suspend fun readNutrition(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime
    ): List<SamsungNutritionRecord> = wrapSdkCall {
        val store = requireStore()
        val zone = ZoneId.systemDefault()
        val request = DataTypes.NUTRITION.readDataRequestBuilder
            .setLocalTimeFilter(LocalTimeFilter.of(startLocal, endLocal))
            .setOrdering(Ordering.ASC)
            .build()

        store.readData(request).dataList.map { pt ->
            SamsungNutritionRecord(
                uid = pt.uid,
                startTime = pt.startTime,
                endTime = pt.endTime ?: pt.startTime.plusSeconds(60),
                zoneOffset = pt.zoneOffset ?: zone.rules.getOffset(pt.startTime),
                title = pt.getValue(DataType.NutritionType.TITLE),
                mealType = pt.getValue(DataType.NutritionType.MEAL_TYPE)?.name,
                caloriesKcal = pt.getValue(DataType.NutritionType.CALORIES)?.toDouble(),
                proteinGrams = pt.getValue(DataType.NutritionType.PROTEIN)?.toDouble(),
                carbsGrams = pt.getValue(DataType.NutritionType.CARBOHYDRATE)?.toDouble(),
                totalFatGrams = pt.getValue(DataType.NutritionType.TOTAL_FAT)?.toDouble(),
                saturatedFatGrams = pt.getValue(DataType.NutritionType.SATURATED_FAT)?.toDouble(),
                fiberGrams = pt.getValue(DataType.NutritionType.DIETARY_FIBER)?.toDouble(),
                sugarGrams = pt.getValue(DataType.NutritionType.SUGAR)?.toDouble(),
                sodiumMg = pt.getValue(DataType.NutritionType.SODIUM)?.toDouble(),
                cholesterolMg = pt.getValue(DataType.NutritionType.CHOLESTEROL)?.toDouble(),
                caffeineMg = pt.getValue(DataType.NutritionType.CAFFEINE)?.toDouble()
            )
        }
    }

    /**
     * Reads Samsung-only display metrics (`ENERGY_SCORE`, `SLEEP_APNEA`, and
     * `IRREGULAR_HEART_RHYTHM_NOTIFICATION`) for the HealthBridge UI dashboard.
     */
    suspend fun readSamsungExclusiveMetrics(): SamsungExclusiveMetrics = wrapSdkCall {
        val store = requireStore()
        val now = LocalDateTime.now()
        val last7Days = LocalTimeFilter.of(now.minusDays(7), now)
        val last30Days = LocalTimeFilter.of(now.minusDays(30), now)

        val latestEnergy = runCatching {
            store.readData(
                DataTypes.ENERGY_SCORE.readDataRequestBuilder
                    .setLocalTimeFilter(last7Days)
                    .setOrdering(Ordering.DESC)
                    .build()
            ).dataList.firstOrNull()
        }.getOrNull()

        val latestApnea = runCatching {
            store.readData(
                DataTypes.SLEEP_APNEA.readDataRequestBuilder
                    .setLocalTimeFilter(last30Days)
                    .setOrdering(Ordering.DESC)
                    .build()
            ).dataList.firstOrNull()
        }.getOrNull()

        val ihrnEvents = runCatching {
            store.readData(
                DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION.readDataRequestBuilder
                    .setLocalTimeFilter(last30Days)
                    .setOrdering(Ordering.DESC)
                    .build()
            ).dataList
        }.getOrDefault(emptyList())

        val apneaLabel = latestApnea?.let { pt ->
            pt.getValue(DataType.SleepApneaType.STATUS)
                ?: when (pt.getValue(DataType.SleepApneaType.DETECTION_RESULT)) {
                    DataType.SleepApneaType.DetectionResult.NO_SIGNS -> "No signs detected"
                    DataType.SleepApneaType.DetectionResult.SIGNS_DETECTED -> "Signs detected"
                    DataType.SleepApneaType.DetectionResult.INCONCLUSIVE -> "Inconclusive"
                    else -> "Monitoring active"
                }
        }

        val totalIhrnCount = ihrnEvents.sumOf { pt ->
            pt.getValue(DataType.IrregularHeartRhythmNotificationType.EVENT_COUNT) ?: 1
        }
        val latestIhrn = ihrnEvents.firstOrNull()
        val ihrnLabel = when {
            ihrnEvents.isEmpty() -> "No irregular rhythms (30d)"
            totalIhrnCount == 1 -> "1 alert detected (30d)"
            else -> "$totalIhrnCount alerts detected (30d)"
        }

        SamsungExclusiveMetrics(
            energyScore = latestEnergy?.getValue(DataType.EnergyScoreType.ENERGY_SCORE),
            energyScoreTimestampEpochMs = latestEnergy?.startTime?.toEpochMilli(),
            sleepApneaStatus = apneaLabel,
            sleepApneaTimestampEpochMs = latestApnea?.startTime?.toEpochMilli(),
            ihrnStatus = ihrnLabel,
            ihrnEventCount = totalIhrnCount,
            ihrnTimestampEpochMs = latestIhrn?.startTime?.toEpochMilli()
        )
    }

    private suspend fun <T> wrapSdkCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: AuthorizationException) {
            throw SamsungDeveloperModeException(
                "Samsung Health authorization denied (code ${e.errorCode}). Ensure Developer mode (Samsung Health Data SDK) is enabled in Samsung Health Settings → About Samsung Health.",
                e
            )
        } catch (e: HealthDataException) {
            if (isDeveloperModeErrorMessage(e.errorMessage, e.errorCode)) {
                throw SamsungDeveloperModeException(
                    "Samsung Health Developer Mode is turned off (code ${e.errorCode}). Enable it in Samsung Health → Settings → About Samsung Health.",
                    e
                )
            }
            throw e
        }
    }

    private fun classifyConnectionError(t: Throwable, totalCount: Int): EndpointConnectionStatus {
        val msg = t.message.orEmpty()
        val code = (t as? HealthDataException)?.errorCode ?: -1
        return if (t is AuthorizationException || t is SamsungDeveloperModeException ||
            isDeveloperModeErrorMessage(msg, code)
        ) {
            EndpointConnectionStatus(
                state = ConnectionState.DEVELOPER_MODE_REQUIRED,
                headline = "Developer Mode Off",
                detail = "Enable 'Developer mode (Samsung Health Data SDK)' inside Samsung Health → About Samsung Health.",
                grantedCount = 0,
                totalCount = totalCount
            )
        } else {
            EndpointConnectionStatus(
                state = ConnectionState.UNAVAILABLE,
                headline = "SDK Error",
                detail = msg.ifBlank { "Unable to connect to Samsung Health Data Store." },
                grantedCount = 0,
                totalCount = totalCount
            )
        }
    }

    private fun isDeveloperModeErrorMessage(message: String, errorCode: Int): Boolean {
        val lower = message.lowercase()
        return lower.contains("developer") ||
            lower.contains("not authorized") ||
            lower.contains("access denied") ||
            lower.contains("partnership") ||
            errorCode == 2000 ||
            errorCode == 2003
    }
}
