package com.example.bridge.mappers

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Volume
import com.example.bridge.HealthConnectPermissions
import com.example.bridge.RecordMetadataFactory
import com.example.model.SamsungBodyCompositionRecord
import com.example.model.SamsungNutritionRecord
import com.example.model.SamsungUserProfile
import com.example.model.SamsungWaterIntakeSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object BodyAndIntakeMappers {

    fun mapBodyCompositionAndProfile(
        bodyRecords: List<SamsungBodyCompositionRecord>,
        profile: SamsungUserProfile?,
        grantedPermissions: Set<String>,
        versionMs: Long = System.currentTimeMillis()
    ): List<Record> {
        val output = mutableListOf<Record>()
        val canWriteWeight = HealthConnectPermissions.WRITE_WEIGHT in grantedPermissions
        val canWriteBodyFat = HealthConnectPermissions.WRITE_BODY_FAT in grantedPermissions
        val canWriteLeanMass = HealthConnectPermissions.WRITE_LEAN_BODY_MASS in grantedPermissions
        val canWriteBmr = HealthConnectPermissions.WRITE_BMR in grantedPermissions
        val canWriteHeight = HealthConnectPermissions.WRITE_HEIGHT in grantedPermissions

        var wroteHeightFromBodyComp = false

        bodyRecords.forEach { rec ->
            rec.weightKg?.takeIf { it in 5.0..500.0 && canWriteWeight }?.let { kg ->
                output += WeightRecord(
                    time = rec.time,
                    zoneOffset = rec.zoneOffset,
                    weight = Mass.kilograms(kg),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("weight", rec.uid),
                        versionMs = versionMs
                    )
                )
            }

            rec.bodyFatPercentage?.takeIf { it in 1.0..85.0 && canWriteBodyFat }?.let { pct ->
                output += BodyFatRecord(
                    time = rec.time,
                    zoneOffset = rec.zoneOffset,
                    percentage = Percentage(pct),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("body_fat", rec.uid),
                        versionMs = versionMs
                    )
                )
            }

            rec.leanBodyMassKg?.takeIf { it in 5.0..300.0 && canWriteLeanMass }?.let { kg ->
                output += LeanBodyMassRecord(
                    time = rec.time,
                    zoneOffset = rec.zoneOffset,
                    mass = Mass.kilograms(kg),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("lean_mass", rec.uid),
                        versionMs = versionMs
                    )
                )
            }

            rec.basalMetabolicRateKcalPerDay?.takeIf { it in 300.0..10_000.0 && canWriteBmr }?.let { bmr ->
                output += BasalMetabolicRateRecord(
                    time = rec.time,
                    zoneOffset = rec.zoneOffset,
                    basalMetabolicRate = Power.kilocaloriesPerDay(bmr),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("bmr", rec.uid),
                        versionMs = versionMs
                    )
                )
            }

            rec.heightCm?.takeIf { it in 40.0..260.0 && canWriteHeight }?.let { cm ->
                wroteHeightFromBodyComp = true
                output += HeightRecord(
                    time = rec.time,
                    zoneOffset = rec.zoneOffset,
                    height = Length.meters(cm / 100.0),
                    metadata = RecordMetadataFactory.autoRecorded(
                        clientRecordId = RecordMetadataFactory.discreteRecordId("height", rec.uid),
                        versionMs = versionMs
                    )
                )
            }
        }

        // Fall back to Samsung USER_PROFILE height & weight if no recent body composition height exists
        if (profile != null) {
            val now = Instant.now()
            val offset = ZoneOffset.systemDefault().rules.getOffset(now)
            val today = LocalDate.now()

            if (!wroteHeightFromBodyComp && canWriteHeight) {
                profile.heightCm?.takeIf { it in 40.0..260.0 }?.let { cm ->
                    output += HeightRecord(
                        time = now,
                        zoneOffset = offset,
                        height = Length.meters(cm / 100.0),
                        metadata = RecordMetadataFactory.autoRecorded(
                            clientRecordId = "shealth_profile_height_$today",
                            versionMs = versionMs
                        )
                    )
                }
            }

            if (bodyRecords.none { it.weightKg != null } && canWriteWeight) {
                profile.weightKg?.takeIf { it in 5.0..500.0 }?.let { kg ->
                    output += WeightRecord(
                        time = now,
                        zoneOffset = offset,
                        weight = Mass.kilograms(kg),
                        metadata = RecordMetadataFactory.autoRecorded(
                            clientRecordId = "shealth_profile_weight_$today",
                            versionMs = versionMs
                        )
                    )
                }
            }
        }

        return output
    }

    fun mapHydration(
        samples: List<SamsungWaterIntakeSample>,
        versionMs: Long = System.currentTimeMillis()
    ): List<HydrationRecord> {
        return samples.mapNotNull { water ->
            if (water.volumeMl <= 0.0 || water.volumeMl > 20_000.0) return@mapNotNull null
            val start = water.startTime
            val end = if (water.endTime.isAfter(start)) water.endTime else start.plusSeconds(60)
            HydrationRecord(
                startTime = start,
                startZoneOffset = water.zoneOffset,
                endTime = end,
                endZoneOffset = water.zoneOffset,
                volume = Volume.milliliters(water.volumeMl),
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("water", water.uid),
                    versionMs = versionMs
                )
            )
        }
    }

    fun mapNutrition(
        records: List<SamsungNutritionRecord>,
        versionMs: Long = System.currentTimeMillis()
    ): List<NutritionRecord> {
        return records.mapNotNull { meal ->
            val start = meal.startTime
            val end = if (meal.endTime.isAfter(start)) meal.endTime else start.plusSeconds(60)
            NutritionRecord(
                startTime = start,
                startZoneOffset = meal.zoneOffset,
                endTime = end,
                endZoneOffset = meal.zoneOffset,
                name = meal.title?.takeIf { it.isNotBlank() },
                mealType = when (meal.mealType?.uppercase()) {
                    "BREAKFAST" -> MealType.MEAL_TYPE_BREAKFAST
                    "LUNCH" -> MealType.MEAL_TYPE_LUNCH
                    "DINNER" -> MealType.MEAL_TYPE_DINNER
                    "MORNING_SNACK", "AFTERNOON_SNACK", "EVENING_SNACK" -> MealType.MEAL_TYPE_SNACK
                    else -> MealType.MEAL_TYPE_UNKNOWN
                },
                energy = meal.caloriesKcal?.takeIf { it > 0.0 }?.let { Energy.kilocalories(it) },
                protein = meal.proteinGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                totalCarbohydrate = meal.carbsGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                totalFat = meal.totalFatGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                saturatedFat = meal.saturatedFatGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                dietaryFiber = meal.fiberGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                sugar = meal.sugarGrams?.takeIf { it >= 0.0 }?.let { Mass.grams(it) },
                sodium = meal.sodiumMg?.takeIf { it >= 0.0 }?.let { Mass.milligrams(it) },
                cholesterol = meal.cholesterolMg?.takeIf { it >= 0.0 }?.let { Mass.milligrams(it) },
                caffeine = meal.caffeineMg?.takeIf { it >= 0.0 }?.let { Mass.milligrams(it) },
                metadata = RecordMetadataFactory.autoRecorded(
                    clientRecordId = RecordMetadataFactory.discreteRecordId("nutrition", meal.uid),
                    versionMs = versionMs
                )
            )
        }
    }
}
