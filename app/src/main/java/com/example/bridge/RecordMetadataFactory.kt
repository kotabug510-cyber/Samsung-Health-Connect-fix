package com.example.bridge

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Centralized deterministic Health Connect Metadata factory enforcing strict deduplication
 * (in-place upsert semantics) across repeated syncs and multi-day backfills.
 */
object RecordMetadataFactory {

    private val watchDevice = Device(
        type = Device.TYPE_WATCH,
        manufacturer = "Samsung",
        model = "Galaxy Watch"
    )

    fun autoRecorded(
        clientRecordId: String,
        versionMs: Long = System.currentTimeMillis()
    ): Metadata = Metadata.autoRecorded(
        device = watchDevice,
        clientRecordId = clientRecordId,
        clientRecordVersion = versionMs
    )

    fun hourlyStepsId(hourStart: Instant): String {
        val hourEpoch = hourStart.truncatedTo(ChronoUnit.HOURS).epochSecond
        return "shealth_steps_hour_$hourEpoch"
    }

    fun dailyRestingHeartRateId(date: LocalDate): String = "shealth_rhr_$date"

    fun heartRateWindowId(windowStartEpochSec: Long): String = "shealth_hr_win_$windowStartEpochSec"

    fun activitySummaryId(metric: String, windowStartEpochSec: Long): String =
        "shealth_activity_${metric}_$windowStartEpochSec"

    fun discreteRecordId(type: String, samsungUid: String): String {
        val sanitizedUid = samsungUid.trim().ifBlank { "unknown" }
        return "shealth_${type}_$sanitizedUid"
    }
}
