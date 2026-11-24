package com.samsung.android.sdk.health.data.error

import android.app.Activity

open class HealthDataException(
    val errorCode: Int? = 0,
    val errorMessage: String? = "",
    cause: Throwable? = null
) : RuntimeException(errorMessage, cause)

class AuthorizationException(
    errorCode: Int? = 2000,
    errorMessage: String? = "Authorization failed"
) : HealthDataException(errorCode, errorMessage)

class ResolvablePlatformException(
    errorCode: Int? = 3000,
    errorMessage: String? = "Resolvable Samsung Health platform error"
) : HealthDataException(errorCode, errorMessage) {
    val hasResolution: Boolean = false
    fun resolve(activity: Activity) {
        throw UnsupportedOperationException("Stub only")
    }
}

class PlatformInternalException(
    errorCode: Int? = 4000,
    errorMessage: String? = "Platform internal error"
) : HealthDataException(errorCode, errorMessage)

class InvalidRequestException(
    errorCode: Int? = 5000,
    errorMessage: String? = "Invalid request"
) : HealthDataException(errorCode, errorMessage)
