package com.example.model

import java.time.Instant
import java.time.ZoneOffset

data class SamsungHourlyStepBucket(
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val steps: Long
)

data class SamsungActivitySummaryBucket(
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val distanceMeters: Double?,
    val activeCaloriesKcal: Double?,
    val totalCaloriesKcal: Double?
)

data class SamsungGpsPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Double?
)

data class SamsungExerciseRecord(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val samsungExerciseTypeName: String,
    val title: String?,
    val caloriesKcal: Double?,
    val distanceMeters: Double?,
    val meanSpeedMetersPerSec: Double?,
    val maxSpeedMetersPerSec: Double?,
    val elevationGainMeters: Double?,
    val vo2Max: Double?,
    val routePoints: List<SamsungGpsPoint>
)

data class SamsungFloorsRecord(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val floors: Double
)

enum class SamsungSleepStageKind {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    UNKNOWN
}

data class SamsungSleepStageSample(
    val startTime: Instant,
    val endTime: Instant,
    val kind: SamsungSleepStageKind
)

data class SamsungSleepRecord(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val sleepScore: Int?,
    val stages: List<SamsungSleepStageSample>
)

data class SamsungSkinTempPoint(
    val time: Instant,
    val celsius: Double
)

data class SamsungSkinTempRecord(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val averageCelsius: Double?,
    val samples: List<SamsungSkinTempPoint>
)

data class SamsungHeartRateSample(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val bpm: Long
)

data class SamsungBloodOxygenSample(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val percentage: Double
)

data class SamsungBodyTempSample(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val celsius: Double
)

data class SamsungBloodPressureSample(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val systolicMmHg: Double,
    val diastolicMmHg: Double
)

data class SamsungBloodGlucoseSample(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val mmolPerLiter: Double,
    val mealStatus: String?
)

data class SamsungBodyCompositionRecord(
    val uid: String,
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val weightKg: Double?,
    val heightCm: Double?,
    val bodyFatPercentage: Double?,
    val leanBodyMassKg: Double?,
    val basalMetabolicRateKcalPerDay: Double?
)

data class SamsungUserProfile(
    val uid: String,
    val heightCm: Double?,
    val weightKg: Double?,
    val nickname: String?
)

data class SamsungWaterIntakeSample(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val volumeMl: Double
)

data class SamsungNutritionRecord(
    val uid: String,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val title: String?,
    val mealType: String?,
    val caloriesKcal: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val totalFatGrams: Double?,
    val saturatedFatGrams: Double?,
    val fiberGrams: Double?,
    val sugarGrams: Double?,
    val sodiumMg: Double?,
    val cholesterolMg: Double?,
    val caffeineMg: Double?
)
