package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bridge.SamsungToHealthConnectBridge
import com.example.data.BridgePreferencesRepository
import com.example.model.EndpointConnectionStatus
import com.example.model.SamsungExclusiveMetrics
import com.example.model.SyncCategory
import com.example.model.SyncLogEntry
import com.example.model.SyncProgress
import com.example.model.SyncSummary
import com.example.model.SyncWindow
import com.example.worker.HealthBridgeSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthBridgeUiState(
    val samsungStatus: EndpointConnectionStatus = EndpointConnectionStatus(),
    val healthConnectStatus: EndpointConnectionStatus = EndpointConnectionStatus(),
    val enabledCategories: Set<SyncCategory> = SyncCategory.entries.toSet(),
    val autoSyncEnabled: Boolean = true,
    val syncProgress: SyncProgress = SyncProgress(),
    val lastSyncSummary: SyncSummary? = null,
    val syncLogs: List<SyncLogEntry> = emptyList(),
    val exclusiveMetrics: SamsungExclusiveMetrics = SamsungExclusiveMetrics(),
    val statusBannerMessage: String? = null
)

class HealthBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = BridgePreferencesRepository(application)
    private val bridge = SamsungToHealthConnectBridge(
        context = application,
        preferencesRepository = prefsRepo
    )

    private val samsungStatusFlow = MutableStateFlow(EndpointConnectionStatus())
    private val healthConnectStatusFlow = MutableStateFlow(EndpointConnectionStatus())
    private val syncProgressFlow = MutableStateFlow(SyncProgress())
    private val statusBannerFlow = MutableStateFlow<String?>(null)

    private val liveStatusBundle = combine(
        samsungStatusFlow,
        healthConnectStatusFlow,
        syncProgressFlow,
        statusBannerFlow
    ) { samsung, hc, progress, banner ->
        LiveBundle(samsung, hc, progress, banner)
    }

    private val persistedBundle = combine(
        prefsRepo.enabledCategoriesFlow,
        prefsRepo.autoSyncEnabledFlow,
        prefsRepo.lastSyncSummaryFlow,
        prefsRepo.syncLogsFlow,
        prefsRepo.samsungExclusiveMetricsFlow
    ) { categories, autoSync, summary, logs, exclusive ->
        PersistedBundle(categories, autoSync, summary, logs, exclusive)
    }

    val uiState: StateFlow<HealthBridgeUiState> = combine(
        liveStatusBundle,
        persistedBundle
    ) { live, persisted ->
        HealthBridgeUiState(
            samsungStatus = live.samsung,
            healthConnectStatus = live.hc,
            enabledCategories = persisted.categories,
            autoSyncEnabled = persisted.autoSync,
            syncProgress = live.progress,
            lastSyncSummary = persisted.summary,
            syncLogs = persisted.logs,
            exclusiveMetrics = persisted.exclusive,
            statusBannerMessage = live.banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HealthBridgeUiState()
    )

    init {
        refreshConnections()
        ensureBackgroundWorkerSchedule()
    }

    private fun ensureBackgroundWorkerSchedule() {
        viewModelScope.launch {
            if (prefsRepo.isAutoSyncEnabled()) {
                HealthBridgeSyncWorker.schedulePeriodicSync(getApplication())
            }
        }
    }

    fun refreshConnections() {
        viewModelScope.launch {
            val categories = prefsRepo.getEnabledCategories().ifEmpty { SyncCategory.entries.toSet() }
            samsungStatusFlow.value = bridge.checkSamsungStatus(categories)
            healthConnectStatusFlow.value = bridge.checkHealthConnectStatus(categories)
            bridge.refreshSamsungExclusiveMetrics()
        }
    }

    suspend fun getRequiredHealthConnectPermissions(): Set<String> {
        val categories = prefsRepo.getEnabledCategories().ifEmpty { SyncCategory.entries.toSet() }
        return bridge.getRequiredHealthConnectPermissions(categories)
    }

    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            val categories = prefsRepo.getEnabledCategories().ifEmpty { SyncCategory.entries.toSet() }
            val updated = bridge.checkHealthConnectStatus(categories)
            healthConnectStatusFlow.value = updated
            statusBannerFlow.value = if (granted.isNotEmpty()) {
                "Health Connect access updated (${updated.grantedCount}/${updated.totalCount} write types)."
            } else {
                "No Health Connect write permissions were granted."
            }
        }
    }

    fun requestSamsungHealthPermissions(activity: Activity) {
        viewModelScope.launch {
            val categories = prefsRepo.getEnabledCategories().ifEmpty { SyncCategory.entries.toSet() }
            val result = bridge.requestSamsungPermissions(activity, categories)
            samsungStatusFlow.value = result
            statusBannerFlow.value = "${result.headline}: ${result.detail}"
            bridge.refreshSamsungExclusiveMetrics()
        }
    }

    fun triggerSync(window: SyncWindow) {
        if (syncProgressFlow.value.isRunning) return
        viewModelScope.launch {
            statusBannerFlow.value = null
            syncProgressFlow.value = SyncProgress(
                isRunning = true,
                windowLabel = window.label,
                currentStage = "Preparing ${window.label} bridge sync...",
                completedSteps = 0,
                totalSteps = 1
            )
            val summary = bridge.runSync(
                window = window,
                triggerSource = "Manual (${window.label})",
                onProgress = { progress -> syncProgressFlow.value = progress }
            )
            syncProgressFlow.value = SyncProgress(isRunning = false)
            refreshConnections()
            statusBannerFlow.value = if (summary.success) {
                "Synced ${summary.totalRecordsWritten} record(s) for ${window.label}."
            } else {
                summary.errorMessage ?: "Sync failed."
            }
        }
    }

    fun toggleCategory(category: SyncCategory, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setCategoryEnabled(category, enabled)
            val updated = prefsRepo.getEnabledCategories().ifEmpty { SyncCategory.entries.toSet() }
            samsungStatusFlow.value = bridge.checkSamsungStatus(updated)
            healthConnectStatusFlow.value = bridge.checkHealthConnectStatus(updated)
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoSyncEnabled(enabled)
            if (enabled) {
                HealthBridgeSyncWorker.schedulePeriodicSync(getApplication())
                statusBannerFlow.value = "Automatic 15-minute background sync enabled."
            } else {
                HealthBridgeSyncWorker.cancelPeriodicSync(getApplication())
                statusBannerFlow.value = "Automatic background sync paused."
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            prefsRepo.clearSyncLogs()
            statusBannerFlow.value = "Sync log history cleared."
        }
    }

    fun dismissBanner() {
        statusBannerFlow.value = null
    }

    private data class LiveBundle(
        val samsung: EndpointConnectionStatus,
        val hc: EndpointConnectionStatus,
        val progress: SyncProgress,
        val banner: String?
    )

    private data class PersistedBundle(
        val categories: Set<SyncCategory>,
        val autoSync: Boolean,
        val summary: SyncSummary?,
        val logs: List<SyncLogEntry>,
        val exclusive: SamsungExclusiveMetrics
    )
}
