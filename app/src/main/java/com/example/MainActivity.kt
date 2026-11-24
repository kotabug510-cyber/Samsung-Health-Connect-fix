package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HealthBridgeViewModel
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SyncLogScreen
import com.example.ui.theme.HealthBridgeTheme
import kotlinx.coroutines.launch

private enum class BridgeTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    CATEGORIES("categories", R.string.nav_categories, Icons.Filled.Tune, Icons.Outlined.Tune),
    LOGS("sync_log", R.string.nav_logs, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
}

class MainActivity : ComponentActivity() {

    private var launchedForPermissionsRationale by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchedForPermissionsRationale = isRationaleIntent(intent)

        setContent {
            HealthBridgeTheme {
                HealthBridgeApp(
                    activity = this,
                    showPrivacyRationaleInitially = launchedForPermissionsRationale,
                    onDismissPrivacyRationale = { launchedForPermissionsRationale = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isRationaleIntent(intent)) {
            launchedForPermissionsRationale = true
        }
    }

    private fun isRationaleIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        return action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            action == "android.intent.action.VIEW_PERMISSION_USAGE"
    }
}

@Composable
private fun HealthBridgeApp(
    activity: ComponentActivity,
    showPrivacyRationaleInitially: Boolean,
    onDismissPrivacyRationale: () -> Unit,
    viewModel: HealthBridgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (showPrivacyRationaleInitially) BridgeTab.LOGS else BridgeTab.DASHBOARD
        )
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.onHealthConnectPermissionsResult(grantedPermissions)
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    if (selectedTab != BridgeTab.DASHBOARD) {
        BackHandler {
            selectedTab = BridgeTab.DASHBOARD
        }
    }

    if (showPrivacyRationaleInitially) {
        AlertDialog(
            onDismissRequest = onDismissPrivacyRationale,
            title = { Text(stringResource(R.string.privacy_rationale_title)) },
            text = { Text(stringResource(R.string.privacy_rationale_body)) },
            confirmButton = {
                TextButton(
                    onClick = onDismissPrivacyRationale,
                    modifier = Modifier.testTag("dismiss_rationale_button")
                ) {
                    Text("Got it")
                }
            }
        )
    }

    val triggerHealthConnectPermissionRequest: () -> Unit = {
        coroutineScope.launch {
            val permissions = viewModel.getRequiredHealthConnectPermissions()
            if (permissions.isNotEmpty()) {
                runCatching {
                    healthConnectPermissionLauncher.launch(permissions)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        "Unable to open Health Connect permissions: ${err.message ?: "unavailable"}"
                    )
                }
            } else {
                snackbarHostState.showSnackbar("Enable at least one sync category first.")
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        BridgeTab.entries.forEach { tab ->
                            val selected = tab == selectedTab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        BridgeTab.entries.forEach { tab ->
                            val selected = tab == selectedTab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        BridgeTab.DASHBOARD -> DashboardScreen(
                            uiState = uiState,
                            onRequestSamsungPermissions = {
                                viewModel.requestSamsungHealthPermissions(activity)
                            },
                            onRequestHealthConnectPermissions = triggerHealthConnectPermissionRequest,
                            onRefreshConnections = viewModel::refreshConnections,
                            onTriggerSync = viewModel::triggerSync
                        )
                        BridgeTab.CATEGORIES -> CategoriesScreen(
                            uiState = uiState,
                            onToggleCategory = viewModel::toggleCategory,
                            onToggleAutoSync = viewModel::toggleAutoSync
                        )
                        BridgeTab.LOGS -> SyncLogScreen(
                            uiState = uiState,
                            onClearLogs = viewModel::clearLogs
                        )
                    }
                }
            }
        }
    }
}
