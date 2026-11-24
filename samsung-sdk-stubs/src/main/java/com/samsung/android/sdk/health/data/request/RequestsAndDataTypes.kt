package com.samsung.android.sdk.health.data.request

import com.samsung.android.sdk.health.data.data.DataPoint
import com.samsung.android.sdk.health.data.data.ExerciseLocation
import com.samsung.android.sdk.health.data.data.ExerciseSession
import com.samsung.android.sdk.health.data.data.Field
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.HeartRateSeries
import com.samsung.android.sdk.health.data.data.SkinTemperatureSeries
import com.samsung.android.sdk.health.data.data.SleepSession
import com.samsung.android.sdk.health.data.data.UserDataPoint
import java.time.Instant
import java.time.LocalDateTime

enum class Ordering {
    ASC,
    DESC
}

enum class LocalTimeGroupUnit {
    MINUTELY,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

class LocalTimeGroup private constructor(
    val unit: LocalTimeGroupUnit,
    val multiplier: Int
) {
    companion object {
        @JvmStatic
        fun of(unit: LocalTimeGroupUnit, multiplier: Int): LocalTimeGroup =
            LocalTimeGroup(unit, multiplier)
    }
}

class LocalTimeFilter private constructor(
    val startTime: LocalDateTime?,
    val endTime: LocalDateTime?
) {
    companion object {
        @JvmStatic
        fun of(startTime: LocalDateTime, endTime: LocalDateTime): LocalTimeFilter =
            LocalTimeFilter(startTime, endTime)

        @JvmStatic
        fun since(startTime: LocalDateTime): LocalTimeFilter =
            LocalTimeFilter(startTime, null)
    }
}

class InstantTimeFilter private constructor(
    val startTime: Instant?,
    val endTime: Instant?
) {
    companion object {
        @JvmStatic
        fun of(startTime: Instant, endTime: Instant): InstantTimeFilter =
            InstantTimeFilter(startTime, endTime)
    }
}

class IdFilter private constructor(val uids: List<String>) {
    companion object {
        @JvmStatic
        fun fromDataUid(uid: String): IdFilter = IdFilter(listOf(uid))
    }
}

class ReadDataRequest<T : DataPoint> internal constructor() {
    open class DualTimeBuilder<T : DataPoint> {
        fun setLocalTimeFilter(filter: LocalTimeFilter): DualTimeBuilder<T> = this
        fun setInstantTimeFilter(filter: InstantTimeFilter): DualTimeBuilder<T> = this
        fun setOrdering(ordering: Ordering): DualTimeBuilder<T> = this
        fun setPageSize(pageSize: Int): DualTimeBuilder<T> = this
        fun setPageToken(token: String?): DualTimeBuilder<T> = this
        fun build(): ReadDataRequest<T> = ReadDataRequest()
    }

    open class BasicBuilder<T : DataPoint> {
        fun setOrdering(ordering: Ordering): BasicBuilder<T> = this
        fun build(): ReadDataRequest<T> = ReadDataRequest()
    }
}

class AggregateRequest<T : Any> internal constructor() {
    open class DualTimeBuilder<T : Any> {
        fun setLocalTimeFilter(filter: LocalTimeFilter): DualTimeBuilder<T> = this
        fun setLocalTimeFilterWithGroup(
            filter: LocalTimeFilter,
            group: LocalTimeGroup
        ): DualTimeBuilder<T> = this
        fun setInstantTimeFilter(filter: InstantTimeFilter): DualTimeBuilder<T> = this
        fun setOrdering(ordering: Ordering): DualTimeBuilder<T> = this
        fun build(): AggregateRequest<T> = AggregateRequest()
    }

    open class LocalDateBuilder<T : Any> {
        fun setLocalTimeFilter(filter: LocalTimeFilter): LocalDateBuilder<T> = this
        fun setLocalTimeFilterWithGroup(
            filter: LocalTimeFilter,
            group: LocalTimeGroup
        ): LocalDateBuilder<T> = this
        fun setOrdering(ordering: Ordering): LocalDateBuilder<T> = this
        fun build(): AggregateRequest<T> = AggregateRequest()
    }
}

class AssociatedReadRequest internal constructor() {
    class Builder {
        fun setIdFilter(filter: IdFilter): Builder = this
        fun addAssociatedDataType(associate: DataType.AssociateType): Builder = this
        fun build(): AssociatedReadRequest = AssociatedReadRequest()
    }
}

class AggregateOperation<T : Any, B : Any>(val name: String, private val builderFactory: () -> B) {
    val requestBuilder: B get() = builderFactory()
}

sealed class DataType(val name: String) {
    open class AssociateType(val name: String)

    open class Readable<T : DataPoint, B : Any>(
        name: String,
        private val builderFactory: () -> B
    ) : DataType(name) {
        val readDataRequestBuilder: B get() = builderFactory()
    }

    class StepsType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "steps",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField
            val TOTAL = AggregateOperation<Long, AggregateRequest.DualTimeBuilder<Long>>(
                "steps_total",
                { AggregateRequest.DualTimeBuilder() }
            )
        }
    }

    class ActivitySummaryType : DataType("activity_summary") {
        companion object {
            @JvmField
            val TOTAL_DISTANCE = AggregateOperation<Float, AggregateRequest.DualTimeBuilder<Float>>(
                "total_distance",
                { AggregateRequest.DualTimeBuilder() }
            )
            @JvmField
            val TOTAL_CALORIES_BURNED = AggregateOperation<Float, AggregateRequest.DualTimeBuilder<Float>>(
                "total_calories_burned",
                { AggregateRequest.DualTimeBuilder() }
            )
            @JvmField
            val ACTIVE_CALORIES_BURNED = AggregateOperation<Float, AggregateRequest.DualTimeBuilder<Float>>(
                "active_calories_burned",
                { AggregateRequest.DualTimeBuilder() }
            )
        }
    }

    class ExerciseType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "exercise",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        enum class PredefinedExerciseType {
            CUSTOM,
            WALKING,
            RUNNING,
            CYCLING,
            SWIMMING,
            HIKING,
            TREADMILL,
            YOGA,
            WEIGHT_TRAINING,
            ELLIPTICAL,
            ROWING_MACHINE,
            PILATES,
            TENNIS,
            BASKETBALL,
            SOCCER,
            HIGH_INTENSITY_INTERVAL_TRAINING
        }

        val associatedReadRequestBuilder: AssociatedReadRequest.Builder
            get() = AssociatedReadRequest.Builder()

        object Associates {
            @JvmField val EXERCISE_LOCATION = AssociateType("exercise_location")
        }

        companion object {
            @JvmField val EXERCISE_TYPE = Field<PredefinedExerciseType>("exercise_type")
            @JvmField val SESSIONS = Field<List<ExerciseSession>>("sessions")
            @JvmField val CUSTOM_TITLE = Field<String>("custom_title")
            @JvmField val CALORIES = Field<Float>("calories")
            @JvmField val DISTANCE = Field<Float>("distance")
            @JvmField val MEAN_SPEED = Field<Float>("mean_speed")
            @JvmField val MAX_SPEED = Field<Float>("max_speed")
            @JvmField val ALTITUDE_GAIN = Field<Float>("altitude_gain")
            @JvmField val VO2_MAX = Field<Float>("vo2_max")
        }
    }

    class ExerciseLocationType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "exercise_location",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val LOCATION_DATA = Field<List<ExerciseLocation>>("location_data")
            @JvmField val LATITUDE = Field<Float>("latitude")
            @JvmField val LONGITUDE = Field<Float>("longitude")
            @JvmField val ALTITUDE = Field<Float>("altitude")
            @JvmField val ACCURACY = Field<Float>("accuracy")
        }
    }

    class FloorsClimbedType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "floors_climbed",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val FLOOR = Field<Float>("floor")
        }
    }

    class SleepType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "sleep",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        val associatedReadRequestBuilder: AssociatedReadRequest.Builder
            get() = AssociatedReadRequest.Builder()

        object Associates {
            @JvmField val SKIN_TEMPERATURE = AssociateType("skin_temperature")
            @JvmField val BLOOD_OXYGEN = AssociateType("blood_oxygen")
        }

        companion object {
            @JvmField val SESSIONS = Field<List<SleepSession>>("sessions")
            @JvmField val SLEEP_SCORE = Field<Int>("sleep_score")
        }
    }

    class SkinTemperatureType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "skin_temperature",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val SKIN_TEMPERATURE = Field<Float>("skin_temperature")
            @JvmField val MIN_SKIN_TEMPERATURE = Field<Float>("min_skin_temperature")
            @JvmField val MAX_SKIN_TEMPERATURE = Field<Float>("max_skin_temperature")
            @JvmField val SERIES_DATA = Field<List<SkinTemperatureSeries>>("series_data")
        }
    }

    class HeartRateType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "heart_rate",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val HEART_RATE = Field<Float>("heart_rate")
            @JvmField val MIN_HEART_RATE = Field<Float>("min_heart_rate")
            @JvmField val MAX_HEART_RATE = Field<Float>("max_heart_rate")
            @JvmField val SERIES_DATA = Field<List<HeartRateSeries>>("series_data")
        }
    }

    class BloodOxygenType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "blood_oxygen",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val OXYGEN_SATURATION = Field<Float>("oxygen_saturation")
        }
    }

    class BodyTemperatureType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "body_temperature",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val BODY_TEMPERATURE = Field<Float>("body_temperature")
        }
    }

    class BloodPressureType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "blood_pressure",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val SYSTOLIC = Field<Float>("systolic")
            @JvmField val DIASTOLIC = Field<Float>("diastolic")
            @JvmField val MEAN = Field<Float>("mean")
            @JvmField val PULSE_RATE = Field<Int>("pulse_rate")
        }
    }

    class BloodGlucoseType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "blood_glucose",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        enum class MealStatus {
            UNDEFINED,
            FASTING,
            BEFORE_MEAL,
            AFTER_MEAL,
            GENERAL
        }

        companion object {
            @JvmField val GLUCOSE_LEVEL = Field<Float>("glucose_level")
            @JvmField val MEAL_STATUS = Field<MealStatus>("meal_status")
        }
    }

    class BodyCompositionType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "body_composition",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val WEIGHT = Field<Float>("weight")
            @JvmField val HEIGHT = Field<Float>("height")
            @JvmField val BODY_FAT = Field<Float>("body_fat")
            @JvmField val FAT_FREE_MASS = Field<Float>("fat_free_mass")
            @JvmField val SKELETAL_MUSCLE_MASS = Field<Float>("skeletal_muscle_mass")
            @JvmField val BASAL_METABOLIC_RATE = Field<Int>("basal_metabolic_rate")
        }
    }

    class UserProfileType : Readable<UserDataPoint, ReadDataRequest.BasicBuilder<UserDataPoint>>(
        "user_profile",
        { ReadDataRequest.BasicBuilder() }
    ) {
        companion object {
            @JvmField val HEIGHT = Field<Float>("height")
            @JvmField val WEIGHT = Field<Float>("weight")
            @JvmField val NICKNAME = Field<String>("nickname")
        }
    }

    class WaterIntakeType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "water_intake",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val AMOUNT = Field<Float>("amount")
        }
    }

    class NutritionType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "nutrition",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        enum class MealType {
            UNDEFINED,
            BREAKFAST,
            LUNCH,
            DINNER,
            MORNING_SNACK,
            AFTERNOON_SNACK,
            EVENING_SNACK
        }

        companion object {
            @JvmField val TITLE = Field<String>("title")
            @JvmField val MEAL_TYPE = Field<MealType>("meal_type")
            @JvmField val CALORIES = Field<Float>("calories")
            @JvmField val PROTEIN = Field<Float>("protein")
            @JvmField val CARBOHYDRATE = Field<Float>("carbohydrate")
            @JvmField val TOTAL_FAT = Field<Float>("total_fat")
            @JvmField val SATURATED_FAT = Field<Float>("saturated_fat")
            @JvmField val DIETARY_FIBER = Field<Float>("dietary_fiber")
            @JvmField val SUGAR = Field<Float>("sugar")
            @JvmField val SODIUM = Field<Float>("sodium")
            @JvmField val CHOLESTEROL = Field<Float>("cholesterol")
            @JvmField val CAFFEINE = Field<Float>("caffeine")
        }
    }

    class EnergyScoreType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "energy_score",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        companion object {
            @JvmField val ENERGY_SCORE = Field<Int>("energy_score")
        }
    }

    class SleepApneaType : Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
        "sleep_apnea",
        { ReadDataRequest.DualTimeBuilder() }
    ) {
        enum class DetectionResult {
            UNDEFINED,
            NO_SIGNS,
            SIGNS_DETECTED,
            INCONCLUSIVE
        }

        companion object {
            @JvmField val DETECTION_RESULT = Field<DetectionResult>("detection_result")
            @JvmField val STATUS = Field<String>("status")
        }
    }

    class IrregularHeartRhythmNotificationType :
        Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>(
            "irregular_heart_rhythm_notification",
            { ReadDataRequest.DualTimeBuilder() }
        ) {
        companion object {
            @JvmField val EVENT_COUNT = Field<Int>("event_count")
            @JvmField val MIN_HEART_RATE = Field<Float>("min_heart_rate")
            @JvmField val MAX_HEART_RATE = Field<Float>("max_heart_rate")
            @JvmField val MESSAGE = Field<String>("message")
        }
    }
}

object DataTypes {
    @JvmField val STEPS = DataType.StepsType()
    @JvmField val ACTIVITY_SUMMARY = DataType.ActivitySummaryType()
    @JvmField val EXERCISE = DataType.ExerciseType()
    @JvmField val EXERCISE_LOCATION = DataType.ExerciseLocationType()
    @JvmField val FLOORS_CLIMBED = DataType.FloorsClimbedType()
    @JvmField val SLEEP = DataType.SleepType()
    @JvmField val SKIN_TEMPERATURE = DataType.SkinTemperatureType()
    @JvmField val HEART_RATE = DataType.HeartRateType()
    @JvmField val BLOOD_OXYGEN = DataType.BloodOxygenType()
    @JvmField val BODY_TEMPERATURE = DataType.BodyTemperatureType()
    @JvmField val BLOOD_PRESSURE = DataType.BloodPressureType()
    @JvmField val BLOOD_GLUCOSE = DataType.BloodGlucoseType()
    @JvmField val BODY_COMPOSITION = DataType.BodyCompositionType()
    @JvmField val USER_PROFILE = DataType.UserProfileType()
    @JvmField val WATER_INTAKE = DataType.WaterIntakeType()
    @JvmField val NUTRITION = DataType.NutritionType()
    @JvmField val ENERGY_SCORE = DataType.EnergyScoreType()
    @JvmField val SLEEP_APNEA = DataType.SleepApneaType()
    @JvmField val IRREGULAR_HEART_RHYTHM_NOTIFICATION = DataType.IrregularHeartRhythmNotificationType()
}
