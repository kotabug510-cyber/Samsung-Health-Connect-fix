package com.example.model

enum class SyncCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val samsungSource: String,
    val healthConnectTarget: String,
    val idPattern: String
) {
    STEPS(
        id = "steps",
        title = "Steps (Hourly)",
        subtitle = "1-hour truncated buckets — no daily batching",
        samsungSource = "DataTypes.STEPS.TOTAL",
        healthConnectTarget = "StepsRecord",
        idPattern = "shealth_steps_hour_{hourEpoch}"
    ),
    SLEEP(
        id = "sleep",
        title = "Sleep & Stages",
        subtitle = "Full sessions with Awake, Light, Deep & REM stages",
        samsungSource = "DataTypes.SLEEP",
        healthConnectTarget = "SleepSessionRecord",
        idPattern = "shealth_sleep_{uid}"
    ),
    HEART_RATE(
        id = "heart_rate",
        title = "Heart Rate & Resting HR",
        subtitle = "15-min HR windows + overnight derived daily RHR",
        samsungSource = "DataTypes.HEART_RATE",
        healthConnectTarget = "HeartRateRecord, RestingHeartRateRecord",
        idPattern = "shealth_hr_win_{bucket} / shealth_rhr_{date}"
    ),
    SKIN_TEMPERATURE(
        id = "skin_temperature",
        title = "Skin Temperature",
        subtitle = "Continuous wrist skin temp deltas during sleep",
        samsungSource = "DataTypes.SKIN_TEMPERATURE",
        healthConnectTarget = "SkinTemperatureRecord",
        idPattern = "shealth_skin_temp_{uid}"
    ),
    WORKOUTS_GPS(
        id = "workouts_gps",
        title = "Workouts, Activity & GPS",
        subtitle = "Sessions, GPS routes, distance, calories, VO2 max & floors",
        samsungSource = "ACTIVITY_SUMMARY, EXERCISE, EXERCISE_LOCATION, FLOORS_CLIMBED",
        healthConnectTarget = "ExerciseSession, ExerciseRoute, Distance, Calories, Speed, Elevation, VO2Max, Floors",
        idPattern = "shealth_exercise_{uid}"
    ),
    BODY_COMPOSITION(
        id = "body_composition",
        title = "Body Composition",
        subtitle = "Weight, body fat, lean mass, BMR & profile height",
        samsungSource = "DataTypes.BODY_COMPOSITION, USER_PROFILE",
        healthConnectTarget = "Weight, BodyFat, LeanBodyMass, BMR, Height",
        idPattern = "shealth_body_{metric}_{uid}"
    ),
    VITALS(
        id = "vitals",
        title = "Vitals",
        subtitle = "SpO2, body temperature, blood pressure & blood glucose",
        samsungSource = "BLOOD_OXYGEN, BODY_TEMPERATURE, BLOOD_PRESSURE, BLOOD_GLUCOSE",
        healthConnectTarget = "OxygenSaturation, BodyTemp, BloodPressure, BloodGlucose",
        idPattern = "shealth_{vital}_{uid}"
    ),
    NUTRITION_HYDRATION(
        id = "nutrition_hydration",
        title = "Nutrition & Hydration",
        subtitle = "Meal macros, calories & water intake volume",
        samsungSource = "DataTypes.NUTRITION, DataTypes.WATER_INTAKE",
        healthConnectTarget = "NutritionRecord, HydrationRecord",
        idPattern = "shealth_nutrition_{uid} / shealth_water_{uid}"
    )
}

enum class SyncWindow(val label: String, val hours: Long) {
    LAST_24_HOURS("Last 24 Hours", 24L),
    LAST_7_DAYS("Last 7 Days", 24L * 7L),
    LAST_30_DAYS("Last 30 Days", 24L * 30L)
}

enum class ConnectionState {
    CONNECTED,
    PERMISSIONS_NEEDED,
    DEVELOPER_MODE_REQUIRED,
    SDK_NOT_BUNDLED,
    APP_NOT_INSTALLED,
    UNAVAILABLE
}

data class EndpointConnectionStatus(
    val state: ConnectionState = ConnectionState.UNAVAILABLE,
    val headline: String = "Checking...",
    val detail: String = "",
    val grantedCount: Int = 0,
    val totalCount: Int = 0
)

data class SamsungExclusiveMetrics(
    val energyScore: Int? = null,
    val energyScoreTimestampEpochMs: Long? = null,
    val sleepApneaStatus: String? = null,
    val sleepApneaTimestampEpochMs: Long? = null,
    val ihrnStatus: String? = null,
    val ihrnEventCount: Int = 0,
    val ihrnTimestampEpochMs: Long? = null
)

data class SyncProgress(
    val isRunning: Boolean = false,
    val windowLabel: String = "",
    val currentStage: String = "",
    val completedSteps: Int = 0,
    val totalSteps: Int = 1
) {
    val fraction: Float
        get() = if (totalSteps <= 0) 0f else (completedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}

data class SyncSummary(
    val timestampEpochMs: Long = 0L,
    val windowLabel: String = "",
    val success: Boolean = false,
    val totalRecordsWritten: Int = 0,
    val categoryCounts: Map<String, Int> = emptyMap(),
    val errorMessage: String? = null,
    val isDeveloperModeError: Boolean = false
)

data class SyncLogEntry(
    val timestampEpochMs: Long,
    val windowLabel: String,
    val triggerSource: String,
    val success: Boolean,
    val totalRecordsWritten: Int,
    val categoryCounts: Map<String, Int>,
    val message: String,
    val isDeveloperModeError: Boolean = false
)
