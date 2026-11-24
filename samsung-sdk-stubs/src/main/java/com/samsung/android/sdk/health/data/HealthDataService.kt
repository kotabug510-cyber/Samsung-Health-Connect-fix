package com.samsung.android.sdk.health.data

import android.app.Activity
import android.content.Context
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.AssociatedDataPoints
import com.samsung.android.sdk.health.data.data.DataPoint
import com.samsung.android.sdk.health.data.data.DataResponse
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.AssociatedReadRequest
import com.samsung.android.sdk.health.data.request.ReadDataRequest

/**
 * Compile-only API stub matching Samsung Health Data SDK 1.1.0 (`samsung-health-data-api-1.1.0.aar`).
 * Excluded automatically when the official AAR is placed in `app/libs/`.
 */
object HealthDataService {
    @JvmStatic
    fun getStore(context: Context): HealthDataStore {
        throw UnsupportedOperationException(
            "Samsung Health Data SDK AAR is not bundled. Place samsung-health-data-api-1.1.0.aar in app/libs/."
        )
    }
}

interface HealthDataStore {
    suspend fun getGrantedPermissions(permissions: Set<Permission>): Set<Permission>
    suspend fun requestPermissions(permissions: Set<Permission>, activity: Activity): Set<Permission>
    suspend fun <T : DataPoint> readData(request: ReadDataRequest<T>): DataResponse<T>
    suspend fun <T : Any> aggregateData(request: AggregateRequest<T>): DataResponse<AggregatedData<T>>
    suspend fun readAssociatedData(request: AssociatedReadRequest): DataResponse<AssociatedDataPoints>
}
