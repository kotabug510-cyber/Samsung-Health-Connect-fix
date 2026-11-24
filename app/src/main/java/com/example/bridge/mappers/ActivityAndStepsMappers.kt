package com.example.bridge.mappers

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import com.example.bridge.HealthConnectPermissions
import com.example.bridge.RecordMetadataFactory
import com.example.model.SamsungActivitySummaryBucket
import com.example.model.SamsungExerciseRecord
import com.example.model.SamsungFloorsRecord
import com.example.model.SamsungHourlyStepBucket
import java.time.temporal.ChronoUnit

object ActivityAndStepsMappers {

    /**
     * Maps hourly Samsung step buckets (`DataTypes.STEPS.TOTAL` grouped by 1-hour windows)
     * into individual truncated-hour `StepsRecord` entries — never daily batched.
     */
    fun mapHourlySteps(
        buckets: List<SamsungHourlyStepBucket>,
        versionMs: Long = System.currentTimeMillis()
    ): List<StepsRecord> {
        return buckets.mapNotNull { bucket ->
            if (bucket.steps <= 0L) return@mapNotNull null
            val truncatedStart = bucket.startTime.truncatedTo(ChronoUnit.HOURS)
            val truncatedEnd = truncatedStart.plus(1, ChronoUnit.HOURS)
            StepsRecord(
                count = bucket.steps.coerceIn(1L, 1_000_000L),
                startTime = truncatedStart,
                startZoneOffset = bucket.zoneOffset,
                endTime = truncatedEnd,
                endZoneOffset = bucket.zoneOffset,
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.hourlyStepsId(truncatedStart),
                    versionMs = versionMs
                )
            )
        }
    }

    /**
     * Maps hourly/windowed `ACTIVITY_SUMMARY` buckets into Distance, ActiveCaloriesBurned,
     * and TotalCaloriesBurned records gated by granted permissions.
     */
    fun mapActivitySummaries(
        summaries: List<SamsungActivitySummaryBucket>,
        grantedPermissions: Set<String>,
        versionMs: Long = System.currentTimeMillis()
    ): List<Record> {
        val output = mutableListOf<Record>()
        val canWriteDistance = HealthConnectPermissions.WRITE_DISTANCE in grantedPermissions
        val canWriteActiveCal = HealthConnectPermissions.WRITE_ACTIVE_CALORIES in grantedPermissions
        val canWriteTotalCal = HealthConnectPermissions.WRITE_TOTAL_CALORIES in grantedPermissions

        summaries.forEach { summary ->
            val start = summary.startTime
            val end = if (summary.endTime.isAfter(start)) summary.endTime else start.plusSeconds(60)
            val bucketEpoch = start.truncatedTo(ChronoUnit.HOURS).epochSecond

            summary.distanceMeters?.takeIf { it > 0.0 && canWriteDistance }?.let { meters ->
                output += DistanceRecord(
                    startTime = start,
                    startZoneOffset = summary.zoneOffset,
                    endTime = end,
                    endZoneOffset = summary.zoneOffset,
                    distance = Length.meters(meters.coerceAtMost(1_000_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.activitySummaryId("distance", bucketEpoch),
                        versionMs = versionMs
                    )
                )
            }

            summary.activeCaloriesKcal?.takeIf { it > 0.0 && canWriteActiveCal }?.let { kcal ->
                output += ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = summary.zoneOffset,
                    endTime = end,
                    endZoneOffset = summary.zoneOffset,
                    energy = Energy.kilocalories(kcal.coerceAtMost(100_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.activitySummaryId("active_cal", bucketEpoch),
                        versionMs = versionMs
                    )
                )
            }

            summary.totalCaloriesKcal?.takeIf { it > 0.0 && canWriteTotalCal }?.let { kcal ->
                output += TotalCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = summary.zoneOffset,
                    endTime = end,
                    endZoneOffset = summary.zoneOffset,
                    energy = Energy.kilocalories(kcal.coerceAtMost(100_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.activitySummaryId("total_cal", bucketEpoch),
                        versionMs = versionMs
                    )
                )
            }
        }
        return output
    }

    /**
     * Maps Samsung `EXERCISE` + `EXERCISE_LOCATION` records into:
     * - `ExerciseSessionRecord` (with embedded GPS `ExerciseRoute` when route permission is granted)
     * - Associated workout `DistanceRecord`, `ActiveCaloriesBurnedRecord`, `SpeedRecord`,
     *   `ElevationGainedRecord`, and `Vo2MaxRecord` (when present).
     */
    fun mapExercises(
        exercises: List<SamsungExerciseRecord>,
        grantedPermissions: Set<String>,
        versionMs: Long = System.currentTimeMillis()
    ): List<Record> {
        val output = mutableListOf<Record>()
        val canWriteExercise = HealthConnectPermissions.WRITE_EXERCISE in grantedPermissions
        val canWriteRoute = HealthConnectPermissions.WRITE_EXERCISE_ROUTE in grantedPermissions
        val canWriteDistance = HealthConnectPermissions.WRITE_DISTANCE in grantedPermissions
        val canWriteActiveCal = HealthConnectPermissions.WRITE_ACTIVE_CALORIES in grantedPermissions
        val canWriteSpeed = HealthConnectPermissions.WRITE_SPEED in grantedPermissions
        val canWriteElevation = HealthConnectPermissions.WRITE_ELEVATION in grantedPermissions
        val canWriteVo2 = HealthConnectPermissions.WRITE_VO2_MAX in grantedPermissions

        exercises.forEach { ex ->
            val start = ex.startTime
            val end = if (ex.endTime.isAfter(start)) ex.endTime else start.plusSeconds(1)

            if (canWriteExercise) {
                val route = if (canWriteRoute && ex.routePoints.isNotEmpty()) {
                    val validLocations = ex.routePoints
                        .filter { pt ->
                            !pt.timestamp.isBefore(start) &&
                                !pt.timestamp.isAfter(end) &&
                                pt.latitude in -90.0..90.0 &&
                                pt.longitude in -180.0..180.0
                        }
                        .sortedBy { it.timestamp }
                        .map { pt ->
                            ExerciseRoute.Location(
                                time = pt.timestamp,
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                horizontalAccuracy = pt.accuracyMeters
                                    ?.takeIf { it >= 0.0 }
                                    ?.let { Length.meters(it) },
                                verticalAccuracy = pt.accuracyMeters
                                    ?.takeIf { it >= 0.0 }
                                    ?.let { Length.meters(it) },
                                altitude = pt.altitudeMeters?.let { Length.meters(it) }
                            )
                        }
                    if (validLocations.isNotEmpty()) ExerciseRoute(validLocations) else null
                } else {
                    null
                }

                output += ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = ex.zoneOffset,
                    endTime = end,
                    endZoneOffset = ex.zoneOffset,
                    exerciseType = mapExerciseType(ex.samsungExerciseTypeName),
                    title = ex.title?.takeIf { it.isNotBlank() }
                        ?: formatExerciseTitle(ex.samsungExerciseTypeName),
                    exerciseRoute = route,
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("exercise", ex.uid),
                        versionMs = versionMs
                    )
                )
            }

            ex.distanceMeters?.takeIf { it > 0.0 && canWriteDistance }?.let { dist ->
                output += DistanceRecord(
                    startTime = start,
                    startZoneOffset = ex.zoneOffset,
                    endTime = end,
                    endZoneOffset = ex.zoneOffset,
                    distance = Length.meters(dist.coerceAtMost(1_000_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("exercise_dist", ex.uid),
                        versionMs = versionMs
                    )
                )
            }

            ex.caloriesKcal?.takeIf { it > 0.0 && canWriteActiveCal }?.let { kcal ->
                output += ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = ex.zoneOffset,
                    endTime = end,
                    endZoneOffset = ex.zoneOffset,
                    energy = Energy.kilocalories(kcal.coerceAtMost(50_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("exercise_cal", ex.uid),
                        versionMs = versionMs
                    )
                )
            }

            if (canWriteSpeed) {
                val speedSamples = buildList {
                    ex.meanSpeedMetersPerSec?.takeIf { it > 0.0 }?.let { mean ->
                        add(SpeedRecord.Sample(start, Velocity.metersPerSecond(mean.coerceAtMost(100.0))))
                    }
                    ex.maxSpeedMetersPerSec?.takeIf { it > 0.0 }?.let { max ->
                        val midTime = start.plusMillis((end.toEpochMilli() - start.toEpochMilli()) / 2)
                        add(SpeedRecord.Sample(midTime, Velocity.metersPerSecond(max.coerceAtMost(100.0))))
                    }
                }
                if (speedSamples.isNotEmpty()) {
                    output += SpeedRecord(
                        startTime = start,
                        startZoneOffset = ex.zoneOffset,
                        endTime = end,
                        endZoneOffset = ex.zoneOffset,
                        samples = speedSamples.sortedBy { it.time },
                        metadata = RecordMetadataFactory.autoRecorded(
                            clientRecordId = RecordMetadataFactory.discreteRecordId("exercise_speed", ex.uid),
                            versionMs = versionMs
                        )
                    )
                }
            }

            ex.elevationGainMeters?.takeIf { it > 0.0 && canWriteElevation }?.let { elev ->
                output += ElevationGainedRecord(
                    startTime = start,
                    startZoneOffset = ex.zoneOffset,
                    endTime = end,
                    endZoneOffset = ex.zoneOffset,
                    elevation = Length.meters(elev.coerceAtMost(50_000.0)),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("exercise_elev", ex.uid),
                        versionMs = versionMs
                    )
                )
            }

            ex.vo2Max?.takeIf { it in 1.0..100.0 && canWriteVo2 }?.let { vo2 ->
                output += Vo2MaxRecord(
                    time = end,
                    zoneOffset = ex.zoneOffset,
                    vo2MillilitersPerMinuteKilogram = vo2,
                    measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("exercise_vo2", ex.uid),
                        versionMs = versionMs
                    )
                )
            }
        }

        return output
    }

    fun mapFloorsClimbed(
        floors: List<SamsungFloorsRecord>,
        versionMs: Long = System.currentTimeMillis()
    ): List<FloorsClimbedRecord> {
        return floors.mapNotNull { item ->
            if (item.floors <= 0.0) return@mapNotNull null
            val start = item.startTime
            val end = if (item.endTime.isAfter(start)) item.endTime else start.plusSeconds(60)
            FloorsClimbedRecord(
                startTime = start,
                startZoneOffset = item.zoneOffset,
                endTime = end,
                endZoneOffset = item.zoneOffset,
                floors = item.floors.coerceAtMost(10_000.0),
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("floors", item.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    private fun mapExerciseType(samsungName: String): Int {
        return when (samsungName.uppercase()) {
            "WALKING" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            "RUNNING" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            "TREADMILL" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            "CYCLING" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            "SWIMMING" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            "HIKING" -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
            "YOGA" -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            "WEIGHT_TRAINING" -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
            "ELLIPTICAL" -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
            "ROWING_MACHINE" -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
            "PILATES" -> ExerciseSessionRecord.EXERCISE_TYPE_PILATES
            "TENNIS" -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
            "BASKETBALL" -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
            "SOCCER" -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
            "HIGH_INTENSITY_INTERVAL_TRAINING" ->
                ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    private fun formatExerciseTitle(raw: String): String {
        return raw.lowercase()
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }
}
