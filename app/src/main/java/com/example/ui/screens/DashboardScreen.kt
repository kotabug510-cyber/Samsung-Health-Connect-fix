package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ConnectionState
import com.example.model.EndpointConnectionStatus
import com.example.model.SamsungExclusiveMetrics
import com.example.model.SyncCategory
import com.example.model.SyncProgress
import com.example.model.SyncSummary
import com.example.model.SyncWindow
import com.example.ui.HealthBridgeUiState
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    uiState: HealthBridgeUiState,
    onRequestSamsungPermissions: () -> Unit,
    onRequestHealthConnectPermissions: () -> Unit,
    onRefreshConnections: () -> Unit,
    onTriggerSync: (SyncWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroBridgeHeader(
                    samsungStatus = uiState.samsungStatus,
                    hcStatus = uiState.healthConnectStatus,
                    autoSyncEnabled = uiState.autoSyncEnabled,
                    onRefresh = onRefreshConnections
                )
            }

            if (uiState.samsungStatus.state == ConnectionState.DEVELOPER_MODE_REQUIRED ||
                uiState.lastSyncSummary?.isDeveloperModeError == true
            ) {
                item {
                    DeveloperModeAlertCard()
                }
            }

            item {
                SectionTitle(
                    title = "Bridge Endpoints",
                    subtitle = "Direct SDK-to-Health Connect pipeline (zero CSV files)"
                )
            }

            item {
                ConnectionEndpointCard(
                    title = "Samsung Health Data SDK",
                    subtitle = "HealthDataService.getStore(context)",
                    icon = Icons.Filled.Watch,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    status = uiState.samsungStatus,
                    actionLabel = when (uiState.samsungStatus.state) {
                        ConnectionState.CONNECTED -> "Re-verify"
                        ConnectionState.SDK_NOT_BUNDLED -> "Check AAR"
                        else -> stringResource(R.string.action_grant_permissions)
                    },
                    testTag = "samsung_permission_button",
                    onActionClick = onRequestSamsungPermissions
                )
            }

            item {
                ConnectionEndpointCard(
                    title = "Android Health Connect",
                    subtitle = "Deduplicated upsert clientRecordId writes",
                    icon = Icons.Filled.HealthAndSafety,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    status = uiState.healthConnectStatus,
                    actionLabel = if (uiState.healthConnectStatus.state == ConnectionState.CONNECTED) {
                        "Manage Access"
                    } else {
                        stringResource(R.string.action_grant_permissions)
                    },
                    testTag = "health_connect_permission_button",
                    onActionClick = onRequestHealthConnectPermissions
                )
            }

            item {
                SyncControlCard(
                    progress = uiState.syncProgress,
                    enabledCategoryCount = uiState.enabledCategories.size,
                    onTriggerSync = onTriggerSync
                )
            }

            item {
                SectionTitle(
                    title = "Samsung Exclusive Insights",
                    subtitle = "Read-only Galaxy Watch & Ring metrics not yet in Health Connect"
                )
            }

            item {
                SamsungExclusiveMetricsGrid(metrics = uiState.exclusiveMetrics)
            }

            item {
                SectionTitle(
                    title = "Last Sync Summary",
                    subtitle = "Exact record counts written per category"
                )
            }

            item {
                LastSyncSummaryCard(summary = uiState.lastSyncSummary)
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HeroBridgeHeader(
    samsungStatus: EndpointConnectionStatus,
    hcStatus: EndpointConnectionStatus,
    autoSyncEnabled: Boolean,
    onRefresh: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f)
        )
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HEALTHBRIDGE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Direct Telemetry Bridge",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .testTag("refresh_connections_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh endpoint status",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    label = "Samsung: ${samsungStatus.headline}",
                    isHealthy = samsungStatus.state == ConnectionState.CONNECTED
                )
                StatusChip(
                    label = "HC: ${hcStatus.headline}",
                    isHealthy = hcStatus.state == ConnectionState.CONNECTED
                )
            }

            Text(
                text = if (autoSyncEnabled) {
                    "Auto-sync scheduled every 15 min via WorkManager · Hourly step granularity · Deterministic upserts"
                } else {
                    "Auto-sync paused · Manual & historical backfill ready"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, isHealthy: Boolean) {
    val dotColor = if (isHealthy) MaterialTheme.colorScheme.tertiary else StatusWarning
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeveloperModeAlertCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("developer_mode_alert_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Developer mode warning",
                    tint = StatusError
                )
                Text(
                    text = stringResource(R.string.dev_mode_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.dev_mode_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun ConnectionEndpointCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    status: EndpointConnectionStatus,
    actionLabel: String,
    testTag: String,
    onActionClick: () -> Unit
) {
    val badgeColor = when (status.state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.tertiary
        ConnectionState.PERMISSIONS_NEEDED, ConnectionState.SDK_NOT_BUNDLED -> StatusWarning
        ConnectionState.DEVELOPER_MODE_REQUIRED,
        ConnectionState.APP_NOT_INSTALLED,
        ConnectionState.UNAVAILABLE -> StatusError
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = status.headline,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = status.detail.ifBlank { "Checking endpoint permissions..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status.totalCount > 0) {
                    Text(
                        text = "${status.grantedCount} / ${status.totalCount} granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                FilledTonalButton(
                    onClick = onActionClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(testTag)
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun SyncControlCard(
    progress: SyncProgress,
    enabledCategoryCount: Int,
    onTriggerSync: (SyncWindow) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual Sync & Backfill",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$enabledCategoryCount/8 active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = { onTriggerSync(SyncWindow.LAST_24_HOURS) },
                enabled = !progress.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sync_now_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync now",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_sync_now),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onTriggerSync(SyncWindow.LAST_7_DAYS) },
                    enabled = !progress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("backfill_7d_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Backfill 7 days",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_backfill_7d))
                }

                OutlinedButton(
                    onClick = { onTriggerSync(SyncWindow.LAST_30_DAYS) },
                    enabled = !progress.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("backfill_30d_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Backfill 30 days",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_backfill_30d))
                }
            }

            AnimatedVisibility(
                visible = progress.isRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("sync_progress_container")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = progress.currentStage.ifBlank { "Syncing..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${progress.completedSteps}/${progress.totalSteps}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SamsungExclusiveMetricsGrid(metrics: SamsungExclusiveMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Energy Score Feature Card with Circular Arc Gauge
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.large
                )
                .testTag("energy_score_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EnergyScoreRing(score = metrics.energyScore)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "Energy Score",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Samsung Energy Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (metrics.energyScore != null) {
                            "Daily readiness read from DataTypes.ENERGY_SCORE"
                        } else {
                            "Awaiting Energy Score from Samsung Health Data SDK"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    metrics.energyScoreTimestampEpochMs?.let { ts ->
                        Text(
                            text = "Updated ${formatEpoch(ts)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExclusiveStatusTile(
                title = "Sleep Apnea",
                sourceType = "DataTypes.SLEEP_APNEA",
                value = metrics.sleepApneaStatus ?: "No reading yet",
                timestampMs = metrics.sleepApneaTimestampEpochMs,
                icon = Icons.Filled.NightlightRound,
                accentColor = MaterialTheme.colorScheme.secondary,
                testTag = "sleep_apnea_card",
                modifier = Modifier.weight(1f)
            )
            ExclusiveStatusTile(
                title = "Irregular Rhythm",
                sourceType = "DataTypes.IHRN",
                value = metrics.ihrnStatus ?: "No reading yet",
                timestampMs = metrics.ihrnTimestampEpochMs,
                icon = Icons.Filled.Favorite,
                accentColor = if (metrics.ihrnEventCount > 0) StatusError else MaterialTheme.colorScheme.tertiary,
                testTag = "ihrn_status_card",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EnergyScoreRing(score: Int?) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val sweep = ((score ?: 0).coerceIn(0, 100) / 100f) * 360f

    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            if (score != null && score > 0) {
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        Text(
            text = score?.toString() ?: "--",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExclusiveStatusTile(
    title: String,
    sourceType: String,
    value: String,
    timestampMs: Long?,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large
            )
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timestampMs?.let { formatEpoch(it) } ?: sourceType,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastSyncSummaryCard(summary: SyncSummary?) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.large
            )
            .testTag("last_sync_summary_card")
    ) {
        if (summary == null) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "No sync completed yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant Samsung Health & Health Connect permissions above, then tap Sync Now (24h) or run a 7-day / 30-day backfill.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (summary.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = if (summary.success) "Sync succeeded" else "Sync failed",
                            tint = if (summary.success) MaterialTheme.colorScheme.tertiary else StatusError
                        )
                        Column {
                            Text(
                                text = if (summary.success) {
                                    "${summary.totalRecordsWritten} Records Upserted"
                                } else {
                                    "Sync Attention Required"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${summary.windowLabel} · ${formatEpoch(summary.timestampEpochMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!summary.errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = summary.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (summary.categoryCounts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        summary.categoryCounts.forEach { (catId, count) ->
                            val label = SyncCategory.entries.find { it.id == catId }?.title ?: catId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "$label: $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val logFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")

internal fun formatEpoch(epochMs: Long): String = runCatching {
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(logFormatter)
}.getOrDefault("--")
