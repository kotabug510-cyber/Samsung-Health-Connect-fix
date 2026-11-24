package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SamsungExclusiveMetrics
import com.example.model.SyncCategory
import com.example.model.SyncLogEntry
import com.example.model.SyncSummary
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bridgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "health_bridge_prefs"
)

class BridgePreferencesRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val LAST_SYNC_SUMMARY_JSON = stringPreferencesKey("last_sync_summary_json")
        val SYNC_LOGS_JSON = stringPreferencesKey("sync_logs_json")
        val SAMSUNG_EXCLUSIVE_JSON = stringPreferencesKey("samsung_exclusive_json")

        fun categoryKey(category: SyncCategory) = booleanPreferencesKey("cat_enabled_${category.id}")
    }

    val enabledCategoriesFlow: Flow<Set<SyncCategory>> = context.bridgeDataStore.data.map { prefs ->
        SyncCategory.entries.filterTo(mutableSetOf()) { category ->
            prefs[Keys.categoryKey(category)] ?: true
        }
    }

    val autoSyncEnabledFlow: Flow<Boolean> = context.bridgeDataStore.data.map { prefs ->
        prefs[Keys.AUTO_SYNC_ENABLED] ?: true
    }

    val lastSyncSummaryFlow: Flow<SyncSummary?> = context.bridgeDataStore.data.map { prefs ->
        val raw = prefs[Keys.LAST_SYNC_SUMMARY_JSON] ?: return@map null
        runCatching { gson.fromJson(raw, SyncSummary::class.java) }.getOrNull()
    }

    val syncLogsFlow: Flow<List<SyncLogEntry>> = context.bridgeDataStore.data.map { prefs ->
        val raw = prefs[Keys.SYNC_LOGS_JSON] ?: return@map emptyList()
        val listType = object : TypeToken<List<SyncLogEntry>>() {}.type
        runCatching { gson.fromJson<List<SyncLogEntry>>(raw, listType) }.getOrDefault(emptyList())
    }

    val samsungExclusiveMetricsFlow: Flow<SamsungExclusiveMetrics> = context.bridgeDataStore.data.map { prefs ->
        val raw = prefs[Keys.SAMSUNG_EXCLUSIVE_JSON] ?: return@map SamsungExclusiveMetrics()
        runCatching { gson.fromJson(raw, SamsungExclusiveMetrics::class.java) }
            .getOrDefault(SamsungExclusiveMetrics())
    }

    suspend fun getEnabledCategories(): Set<SyncCategory> = enabledCategoriesFlow.first()

    suspend fun isAutoSyncEnabled(): Boolean = autoSyncEnabledFlow.first()

    suspend fun setCategoryEnabled(category: SyncCategory, enabled: Boolean) {
        context.bridgeDataStore.edit { prefs ->
            prefs[Keys.categoryKey(category)] = enabled
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.bridgeDataStore.edit { prefs ->
            prefs[Keys.AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun recordSyncResult(summary: SyncSummary, logEntry: SyncLogEntry) {
        context.bridgeDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_SUMMARY_JSON] = gson.toJson(summary)

            val existingRaw = prefs[Keys.SYNC_LOGS_JSON]
            val listType = object : TypeToken<List<SyncLogEntry>>() {}.type
            val existing: List<SyncLogEntry> = if (existingRaw != null) {
                runCatching { gson.fromJson<List<SyncLogEntry>>(existingRaw, listType) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val updated = (listOf(logEntry) + existing).take(MAX_LOG_ENTRIES)
            prefs[Keys.SYNC_LOGS_JSON] = gson.toJson(updated)
        }
    }

    suspend fun saveSamsungExclusiveMetrics(metrics: SamsungExclusiveMetrics) {
        context.bridgeDataStore.edit { prefs ->
            prefs[Keys.SAMSUNG_EXCLUSIVE_JSON] = gson.toJson(metrics)
        }
    }

    suspend fun clearSyncLogs() {
        context.bridgeDataStore.edit { prefs ->
            prefs.remove(Keys.SYNC_LOGS_JSON)
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 35
    }
}
