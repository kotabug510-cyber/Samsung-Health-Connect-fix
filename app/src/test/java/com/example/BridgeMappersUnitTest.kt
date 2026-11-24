package com.example

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.example.bridge.HealthConnectPermissions
import com.example.bridge.mappers.ActivityAndStepsMappers
import com.example.bridge.mappers.BodyAndIntakeMappers
import com.example.bridge.mappers.SleepAndVitalsMappers
import com.example.model.SamsungBodyCompositionRecord
import com.example.model.SamsungExerciseRecord
import com.example.model.SamsungGpsPoint
import com.example.model.SamsungHeartRateSample
import com.example.model.SamsungHourlyStepBucket
import com.example.model.SamsungSkinTempPoint
import com.example.model.SamsungSkinTempRecord
import com.example.model.SamsungSleepRecord
import com.example.model.SamsungSleepStageKind
import com.example.model.SamsungSleepStageSample
import com.example.model.SamsungUserProfile
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMappersUnitTest {

    @Test
    fun hourlyStepsTruncateHoursSkipZerosAndUseDeterministicClientIds() {
        val hour1 = Instant.parse("2026-11-24T08:15:30Z")
        val hour2 = Instant.parse("2026-11-24T09:00:00Z")
        val hour3 = Instant.parse("2026-11-24T10:45:00Z")

        val buckets = listOf(
            SamsungHourlyStepBucket(hour1, hour1.plusSeconds(1800), ZoneOffset.UTC, 1_450L),
            SamsungHourlyStepBucket(hour2, hour2.plusSeconds(3600), ZoneOffset.UTC, 0L),
            SamsungHourlyStepBucket(hour3, hour3.plusSeconds(900), ZoneOffset.UTC, 620L)
        )

        val records = ActivityAndStepsMappers.mapHourlySteps(buckets, versionMs = 123456L)

        assertEquals(2, records.size)
        assertEquals(Instant.parse("2026-11-24T08:00:00Z"), records[0].startTime)
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), records[0].endTime)
        assertEquals(1_450L, records[0].count)
        assertEquals(
            "shealth_steps_hour_${Instant.parse("2026-11-24T08:00:00Z").epochSecond}",
            records[0].metadata.clientRecordId
        )
        assertEquals(123456L, records[0].metadata.clientRecordVersion)
        assertEquals(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, records[0].metadata.recordingMethod)
        assertEquals(Device.TYPE_WATCH, records[0].metadata.device?.type)
    }

    @Test
    fun overnightRestingHeartRateDerivesDailyDeterministicRecordFromSleepWindow() {
        val sleepStart = Instant.parse("2026-11-23T23:00:00Z")
        val sleepEnd = Instant.parse("2026-11-24T06:30:00Z")
        val sleep = listOf(
            SamsungSleepRecord(
                uid = "sleep-1",
                startTime = sleepStart,
                endTime = sleepEnd,
                zoneOffset = ZoneOffset.UTC,
                sleepScore = 88,
                stages = listOf(
                    SamsungSleepStageSample(sleepStart, sleepEnd, SamsungSleepStageKind.DEEP)
                )
            )
        )

        val hrSamples = listOf(
            SamsungHeartRateSample("hr1", Instant.parse("2026-11-23T23:30:00Z"), ZoneOffset.UTC, 56L),
            SamsungHeartRateSample("hr2", Instant.parse("2026-11-24T02:15:00Z"), ZoneOffset.UTC, 49L),
            SamsungHeartRateSample("hr3", Instant.parse("2026-11-24T04:00:00Z"), ZoneOffset.UTC, 52L),
            SamsungHeartRateSample("hr4", Instant.parse("2026-11-24T14:00:00Z"), ZoneOffset.UTC, 118L)
        )

        val rhrRecords = SleepAndVitalsMappers.deriveDailyRestingHeartRate(
            heartRateSamples = hrSamples,
            sleepSessions = sleep,
            zoneId = ZoneId.of("UTC"),
            versionMs = 999L
        )

        assertEquals(1, rhrRecords.size)
        assertEquals(49L, rhrRecords[0].beatsPerMinute)
        assertEquals("shealth_rhr_2026-11-24", rhrRecords[0].metadata.clientRecordId)
    }

    @Test
    fun skinTemperatureMapsBaselineAndWristDeltas() {
        val t0 = Instant.parse("2026-11-24T01:00:00Z")
        val t1 = Instant.parse("2026-11-24T02:00:00Z")
        val raw = listOf(
            SamsungSkinTempRecord(
                uid = "skin-abc",
                startTime = t0,
                endTime = t1,
                zoneOffset = ZoneOffset.UTC,
                averageCelsius = 35.5,
                samples = listOf(
                    SamsungSkinTempPoint(t0, 35.3),
                    SamsungSkinTempPoint(t1, 35.7)
                )
            )
        )

        val mapped = SleepAndVitalsMappers.mapSkinTemperature(raw, versionMs = 500L)
        assertEquals(1, mapped.size)
        assertEquals("shealth_skin_temp_skin-abc", mapped[0].metadata.clientRecordId)
        assertEquals(2, mapped[0].deltas.size)
    }

    @Test
    fun exerciseMapsGpsRouteAndVo2MaxWhenPermitted() {
        val start = Instant.parse("2026-11-24T07:00:00Z")
        val end = Instant.parse("2026-11-24T07:35:00Z")
        val exercise = SamsungExerciseRecord(
            uid = "run-42",
            startTime = start,
            endTime = end,
            zoneOffset = ZoneOffset.UTC,
            samsungExerciseTypeName = "RUNNING",
            title = "Morning 5K",
            caloriesKcal = 340.0,
            distanceMeters = 5020.0,
            meanSpeedMetersPerSec = 2.8,
            maxSpeedMetersPerSec = 3.6,
            elevationGainMeters = 45.0,
            vo2Max = 51.2,
            routePoints = listOf(
                SamsungGpsPoint(start.plusSeconds(10), 37.422, -122.084, 15.0, 4.0),
                SamsungGpsPoint(end.minusSeconds(10), 37.430, -122.090, 22.0, 3.5)
            )
        )

        val granted = HealthConnectPermissions.allRequiredPermissions()
        val mapped = ActivityAndStepsMappers.mapExercises(listOf(exercise), granted, versionMs = 777L)

        val session = mapped.filterIsInstance<ExerciseSessionRecord>().single()
        assertEquals("shealth_exercise_run-42", session.metadata.clientRecordId)
        assertTrue(session.exerciseRouteResult is ExerciseRouteResult.Data)
        val vo2 = mapped.filterIsInstance<Vo2MaxRecord>().single()
        assertEquals(51.2, vo2.vo2MillilitersPerMinuteKilogram, 0.01)
    }

    @Test
    fun bodyCompositionFallsBackToUserProfileHeight() {
        val now = Instant.parse("2026-11-24T09:00:00Z")
        val body = listOf(
            SamsungBodyCompositionRecord(
                uid = "bc-1",
                time = now,
                zoneOffset = ZoneOffset.UTC,
                weightKg = 74.5,
                heightCm = null,
                bodyFatPercentage = 17.2,
                leanBodyMassKg = 61.7,
                basalMetabolicRateKcalPerDay = 1720.0
            )
        )
        val profile = SamsungUserProfile("prof-1", heightCm = 178.0, weightKg = 75.0, nickname = "Alex")
        val mapped = BodyAndIntakeMappers.mapBodyCompositionAndProfile(
            bodyRecords = body,
            profile = profile,
            grantedPermissions = HealthConnectPermissions.allRequiredPermissions(),
            versionMs = 888L
        )

        assertEquals(5, mapped.size)
    }
}
