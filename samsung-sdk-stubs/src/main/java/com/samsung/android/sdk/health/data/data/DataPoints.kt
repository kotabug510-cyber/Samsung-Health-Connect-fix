package com.samsung.android.sdk.health.data.data

import com.samsung.android.sdk.health.data.request.DataType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

open class Field<T>(val name: String)

interface DataPoint

class HealthDataPoint(
    val uid: String = "",
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant? = null,
    val zoneOffset: ZoneOffset? = null,
    val startLocalDateTime: LocalDateTime = LocalDateTime.MIN,
    val endLocalDateTime: LocalDateTime? = null,
    val dataSource: DataSource? = null
) : DataPoint {
    fun <T> getValue(field: Field<T>): T? = null
    fun <T> getValueOrDefault(field: Field<T>, defaultValue: T): T = defaultValue
}

class DataSource(
    val appId: String = "",
    val deviceId: String = ""
)

class AggregatedData<T>(
    val value: T? = null,
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val startLocalDateTime: LocalDateTime = LocalDateTime.MIN,
    val endLocalDateTime: LocalDateTime = LocalDateTime.MIN,
    val zoneOffset: ZoneOffset? = null
)

class AssociatedDataPoints(
    val id: String = ""
) {
    fun <T : DataPoint> getDataPointOf(dataType: DataType.Readable<T, *>): List<T>? = emptyList()
}

class DataResponse<T>(
    val dataList: List<T> = emptyList(),
    val pageToken: String? = null
)

class UserDataPoint(
    val uid: String = ""
) : DataPoint {
    fun <T> getValue(field: Field<T>): T? = null
}

// Nested entry classes in Samsung Health Data SDK 1.1.0

data class SleepSession(
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val stages: List<SleepStage>? = null
)

data class SleepStage(
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val stage: StageType = StageType.UNDEFINED
) {
    enum class StageType {
        UNDEFINED,
        AWAKE,
        LIGHT,
        DEEP,
        REM
    }
}

data class ExerciseSession(
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val exerciseType: com.samsung.android.sdk.health.data.request.DataType.ExerciseType.PredefinedExerciseType =
        com.samsung.android.sdk.health.data.request.DataType.ExerciseType.PredefinedExerciseType.CUSTOM,
    val calories: Float = 0f,
    val distance: Float = 0f,
    val meanSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val altitudeGain: Float = 0f,
    val vo2Max: Float = 0f,
    val customTitle: String? = null,
    val route: List<ExerciseLocation>? = null
)

data class ExerciseLocation(
    val timestamp: Instant = Instant.EPOCH,
    val latitude: Float = 0f,
    val longitude: Float = 0f,
    val altitude: Float = 0f,
    val accuracy: Float = 0f
)

data class SkinTemperatureSeries(
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val skinTemperature: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f
)

data class HeartRateSeries(
    val startTime: Instant = Instant.EPOCH,
    val endTime: Instant = Instant.EPOCH,
    val heartRate: Float = 0f,
    val min: Float = 0f,
    val max: Float = 0f
)
