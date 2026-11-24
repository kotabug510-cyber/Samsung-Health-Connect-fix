package com.example.bridge.mappers

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import com.example.bridge.RecordMetadataFactory
import com.example.model.SamsungBloodGlucoseSample
import com.example.model.SamsungBloodOxygenSample
import com.example.model.SamsungBloodPressureSample
import com.example.model.SamsungBodyTempSample
import com.example.model.SamsungHeartRateSample
import com.example.model.SamsungSkinTempRecord
import com.example.model.SamsungSleepRecord
import com.example.model.SamsungSleepStageKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

object SleepAndVitalsMappers {

    private const val HR_WINDOW_SECONDS = 15L * 60L // 15-minute granular windows

    fun mapSleepSessions(
        sessions: List<SamsungSleepRecord>,
        versionMs: Long = System.currentTimeMillis()
    ): List<SleepSessionRecord> {
        return sessions.mapNotNull { sleep ->
            if (!sleep.endTime.isAfter(sleep.startTime)) return@mapNotNull null

            val mappedStages = sleep.stages
                .filter { st ->
                    st.endTime.isAfter(st.startTime) &&
                        !st.startTime.isBefore(sleep.startTime) &&
                        !st.endTime.isAfter(sleep.endTime)
                }
                .sortedBy { it.startTime }
                .map { st ->
                    SleepSessionRecord.Stage(
                        startTime = st.startTime,
                        endTime = st.endTime,
                        stage = when (st.kind) {
                            SamsungSleepStageKind.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
                            SamsungSleepStageKind.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
                            SamsungSleepStageKind.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
                            SamsungSleepStageKind.REM -> SleepSessionRecord.STAGE_TYPE_REM
                            SamsungSleepStageKind.UNKNOWN -> SleepSessionRecord.STAGE_TYPE_SLEEPING
                        }
                    )
                }

            val notes = sleep.sleepScore?.let { "Samsung Sleep Score: $it" }

            SleepSessionRecord(
                startTime = sleep.startTime,
                startZoneOffset = sleep.zoneOffset,
                endTime = sleep.endTime,
                endZoneOffset = sleep.zoneOffset,
                title = "Samsung Health Sleep",
                notes = notes,
                stages = mappedStages,
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("sleep", sleep.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    /**
     * Bridges continuous Samsung wrist skin temperature readings during sleep into
     * Health Connect `SkinTemperatureRecord` (baseline + delta series).
     */
    fun mapSkinTemperature(
        records: List<SamsungSkinTempRecord>,
        versionMs: Long = System.currentTimeMillis()
    ): List<SkinTemperatureRecord> {
        return records.mapNotNull { rec ->
            val validSamples = rec.samples
                .filter { it.celsius in 15.0..45.0 }
                .sortedBy { it.time }

            val baselineCelsius = rec.averageCelsius?.takeIf { it in 15.0..45.0 }
                ?: validSamples.map { it.celsius }.average().takeIf { !it.isNaN() }
                ?: return@mapNotNull null

            val start = validSamples.firstOrNull()?.time?.let { minOf(it, rec.startTime) } ?: rec.startTime
            val lastSampleTime = validSamples.lastOrNull()?.time
            val minRequiredEnd = (lastSampleTime ?: start).plusSeconds(1)
            val end = maxOf(rec.endTime, minRequiredEnd)

            val deltas = if (validSamples.isNotEmpty()) {
                validSamples.map { pt ->
                    val maxAllowed = end.minusMillis(1)
                    val clampedTime = pt.time.coerceIn(start, maxAllowed)
                    val deltaC = (pt.celsius - baselineCelsius).coerceIn(-15.0, 15.0)
                    SkinTemperatureRecord.Delta(
                        time = clampedTime,
                        delta = TemperatureDelta.celsius(deltaC)
                    )
                }
            } else {
                listOf(
                    SkinTemperatureRecord.Delta(
                        time = start,
                        delta = TemperatureDelta.celsius(0.0)
                    )
                )
            }

            SkinTemperatureRecord(
                startTime = start,
                startZoneOffset = rec.zoneOffset,
                endTime = end,
                endZoneOffset = rec.zoneOffset,
                baseline = Temperature.celsius(baselineCelsius),
                measurementLocation = SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST,
                deltas = deltas,
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("skin_temp", rec.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    /**
     * Groups raw Samsung `HEART_RATE` samples into granular 15-minute `HeartRateRecord` windows
     * preserving every individual sample timestamp.
     */
    fun mapHeartRateWindows(
        samples: List<SamsungHeartRateSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<HeartRateRecord> {
        val valid = samples.filter { it.bpm in 25L..260L }
        if (valid.isEmpty()) return emptyList()

        val grouped = valid.groupBy { sample ->
            (sample.time.epochSecond / HR_WINDOW_SECONDS) * HR_WINDOW_SECONDS
        }

        return grouped.entries.sortedBy { it.key }.mapNotNull { (windowStartSec, bucketSamples) ->
            val sorted = bucketSamples.sortedBy { it.time }.distinctBy { it.time }
            if (sorted.isEmpty()) return@mapNotNull null

            val firstTime = sorted.first().time
            val lastTime = sorted.last().time
            val endTime = lastTime.plusSeconds(1)
            val offset = sorted.first().zoneOffset

            HeartRateRecord(
                startTime = firstTime,
                startZoneOffset = offset,
                endTime = endTime,
                endZoneOffset = sorted.last().zoneOffset,
                samples = sorted.map { s ->
                    HeartRateRecord.Sample(time = s.time, beatsPerMinute = s.bpm)
                },
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.heartRateWindowId(windowStartSec),
                    versionMs = versionMs
                )
            )
        }
    }

    /**
     * Derives daily `RestingHeartRateRecord` (`clientRecordId = "shealth_rhr_{date}"`) from
     * overnight / sleep-window minimum `HEART_RATE` samples.
     *
     * Prefers actual recorded sleep windows when available; otherwise uses the local overnight
     * rest window (22:00 - 08:00). Uses the 10th-percentile low HR (or minimum when < 10 samples)
     * to avoid single-sample optical sensor glitches while matching resting physiology.
     */
    fun deriveDailyRestingHeartRate(
        heartRateSamples: List<SamsungHeartRateSample>,
        sleepSessions: List<SamsungSleepRecord>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        versionMs: Long = System.currentTimeMillis()
    ): List<RestingHeartRateRecord> {
        val validHr = heartRateSamples.filter { it.bpm in 30L..160L }
        if (validHr.isEmpty()) return emptyList()

        val byDate = mutableMapOf<LocalDate, MutableList<SamsungHeartRateSample>>()

        validHr.forEach { sample ->
            val localDateTime = sample.time.atZone(zoneId).toLocalDateTime()
            val inSleepWindow = sleepSessions.any { sleep ->
                !sample.time.isBefore(sleep.startTime) && !sample.time.isAfter(sleep.endTime)
            }
            val inOvernightWindow = localDateTime.hour >= 22 || localDateTime.hour < 8

            if (inSleepWindow || inOvernightWindow) {
                // Attribute late-evening sleep (22:00-23:59) to the next morning's wake date
                val wakeDate = if (localDateTime.hour >= 22) {
                    localDateTime.toLocalDate().plusDays(1)
                } else {
                    localDateTime.toLocalDate()
                }
                byDate.getOrPut(wakeDate) { mutableListOf() }.add(sample)
            }
        }

        return byDate.entries.sortedBy { it.key }.mapNotNull { (date, overnightSamples) ->
            if (overnightSamples.isEmpty()) return@mapNotNull null
            val sortedByBpm = overnightSamples.sortedBy { it.bpm }
            val index = if (sortedByBpm.size >= 10) {
                (sortedByBpm.size * 0.10).roundToLong().toInt().coerceIn(0, sortedByBpm.lastIndex)
            } else {
                0
            }
            val chosen = sortedByBpm[index]

            RestingHeartRateRecord(
                time = chosen.time,
                zoneOffset = chosen.zoneOffset,
                beatsPerMinute = chosen.bpm.coerceIn(30L, 160L),
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.dailyRestingHeartRateId(date),
                    versionMs = versionMs
                )
            )
        }
    }

    fun mapBloodOxygen(
        samples: List<SamsungBloodOxygenSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<OxygenSaturationRecord> {
        return samples.mapNotNull { s ->
            if (s.percentage !in 50.0..100.0) return@mapNotNull null
            OxygenSaturationRecord(
                time = s.time,
                zoneOffset = s.zoneOffset,
                percentage = Percentage(s.percentage),
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("spo2", s.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    fun mapBodyTemperature(
        samples: List<SamsungBodyTempSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<BodyTemperatureRecord> {
        return samples.mapNotNull { s ->
            if (s.celsius !in 30.0..45.0) return@mapNotNull null
            BodyTemperatureRecord(
                time = s.time,
                zoneOffset = s.zoneOffset,
                temperature = Temperature.celsius(s.celsius),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_WRIST,
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("body_temp", s.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    fun mapBloodPressure(
        samples: List<SamsungBloodPressureSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<BloodPressureRecord> {
        return samples.mapNotNull { s ->
            if (s.systolicMmHg !in 40.0..250.0 || s.diastolicMmHg !in 30.0..180.0) {
                return@mapNotNull null
            }
            BloodPressureRecord(
                time = s.time,
                zoneOffset = s.zoneOffset,
                systolic = Pressure.millimetersOfMercury(s.systolicMmHg),
                diastolic = Pressure.millimetersOfMercury(s.diastolicMmHg),
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("bp", s.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    fun mapBloodGlucose(
        samples: List<SamsungBloodGlucoseSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<BloodGlucoseRecord> {
        return samples.mapNotNull { s ->
            if (s.mmolPerLiter !in 0.5..45.0) return@mapNotNull null
            val relation = when (s.mealStatus?.uppercase()) {
                "FASTING" -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                "BEFORE_MEAL" -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                "AFTER_MEAL" -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                "GENERAL" -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                else -> BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
            }
            BloodGlucoseRecord(
                time = s.time,
                zoneOffset = s.zoneOffset,
                level = BloodGlucose.millimolesPerLiter(s.mmolPerLiter),
                relationToMeal = relation,
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("glucose", s.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    private fun Instant.coerceIn(min: Instant, max: Instant): Instant = when {
        this.isBefore(min) -> min
        this.isAfter(max) -> max
        else -> this
    }
}
