package com.example.bridge

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import com.example.model.SyncCategory

object HealthConnectPermissions {

    val WRITE_STEPS = HealthPermission.getWritePermission(StepsRecord::class)
    val WRITE_DISTANCE = HealthPermission.getWritePermission(DistanceRecord::class)
    val WRITE_ACTIVE_CALORIES = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    val WRITE_TOTAL_CALORIES = HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
    val WRITE_SPEED = HealthPermission.getWritePermission(SpeedRecord::class)
    val WRITE_ELEVATION = HealthPermission.getWritePermission(ElevationGainedRecord::class)
    val WRITE_VO2_MAX = HealthPermission.getWritePermission(Vo2MaxRecord::class)
    val WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    val WRITE_EXERCISE_ROUTE = HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE
    val WRITE_FLOORS = HealthPermission.getWritePermission(FloorsClimbedRecord::class)

    val WRITE_SLEEP = HealthPermission.getWritePermission(SleepSessionRecord::class)
    val WRITE_SKIN_TEMPERATURE = HealthPermission.getWritePermission(SkinTemperatureRecord::class)

    val WRITE_HEART_RATE = HealthPermission.getWritePermission(HeartRateRecord::class)
    val WRITE_RESTING_HEART_RATE = HealthPermission.getWritePermission(RestingHeartRateRecord::class)
    val WRITE_OXYGEN_SATURATION = HealthPermission.getWritePermission(OxygenSaturationRecord::class)
    val WRITE_BODY_TEMPERATURE = HealthPermission.getWritePermission(BodyTemperatureRecord::class)
    val WRITE_BLOOD_PRESSURE = HealthPermission.getWritePermission(BloodPressureRecord::class)
    val WRITE_BLOOD_GLUCOSE = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

    val WRITE_WEIGHT = HealthPermission.getWritePermission(WeightRecord::class)
    val WRITE_BODY_FAT = HealthPermission.getWritePermission(BodyFatRecord::class)
    val WRITE_LEAN_BODY_MASS = HealthPermission.getWritePermission(LeanBodyMassRecord::class)
    val WRITE_BMR = HealthPermission.getWritePermission(BasalMetabolicRateRecord::class)
    val WRITE_HEIGHT = HealthPermission.getWritePermission(HeightRecord::class)

    val WRITE_HYDRATION = HealthPermission.getWritePermission(HydrationRecord::class)
    val WRITE_NUTRITION = HealthPermission.getWritePermission(NutritionRecord::class)

    fun permissionsForCategory(
        category: SyncCategory,
        skinTemperatureSupported: Boolean = true
    ): Set<String> = when (category) {
        SyncCategory.STEPS -> setOf(WRITE_STEPS)
        SyncCategory.SLEEP -> setOf(WRITE_SLEEP)
        SyncCategory.HEART_RATE -> setOf(WRITE_HEART_RATE, WRITE_RESTING_HEART_RATE)
        SyncCategory.SKIN_TEMPERATURE ->
            if (skinTemperatureSupported) setOf(WRITE_SKIN_TEMPERATURE) else emptySet()
        SyncCategory.WORKOUTS_GPS -> setOf(
            WRITE_DISTANCE,
            WRITE_ACTIVE_CALORIES,
            WRITE_TOTAL_CALORIES,
            WRITE_SPEED,
            WRITE_ELEVATION,
            WRITE_VO2_MAX,
            WRITE_EXERCISE,
            WRITE_EXERCISE_ROUTE,
            WRITE_FLOORS
        )
        SyncCategory.BODY_COMPOSITION -> setOf(
            WRITE_WEIGHT,
            WRITE_BODY_FAT,
            WRITE_LEAN_BODY_MASS,
            WRITE_BMR,
            WRITE_HEIGHT
        )
        SyncCategory.VITALS -> setOf(
            WRITE_OXYGEN_SATURATION,
            WRITE_BODY_TEMPERATURE,
            WRITE_BLOOD_PRESSURE,
            WRITE_BLOOD_GLUCOSE
        )
        SyncCategory.NUTRITION_HYDRATION -> setOf(
            WRITE_HYDRATION,
            WRITE_NUTRITION
        )
    }

    fun allRequiredPermissions(
        categories: Set<SyncCategory> = SyncCategory.entries.toSet(),
        skinTemperatureSupported: Boolean = true
    ): Set<String> = categories.flatMapTo(mutableSetOf()) {
        permissionsForCategory(it, skinTemperatureSupported)
    }
}
