package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ConnectionMode
import com.example.model.ConnectionStatus
import com.example.ui.screens.Esp32ConnectionScreen
import com.example.ui.screens.FirmwareWiringScreen
import com.example.ui.screens.LiveCprCockpitScreen
import com.example.ui.screens.SensorsDiagnosticsScreen
import com.example.ui.screens.SessionHistoryScreen
import com.example.ui.theme.BioCyan
import com.example.ui.theme.BioCyanSoft
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.TelemetrySurface
import com.example.ui.theme.VitalEmerald
import com.example.viewmodel.CprGloveViewModel

enum class CprDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    COCKPIT(
        route = "live_cpr_cockpit",
        label = "Live CPR",
        selectedIcon = Icons.Filled.MonitorHeart,
        unselectedIcon = Icons.Outlined.MonitorHeart
    ),
    SENSORS(
        route = "sensors_diagnostics",
        label = "3-FSR & IMU",
        selectedIcon = Icons.Filled.Compress,
        unselectedIcon = Icons.Outlined.Compress
    ),
    ESP32_LINK(
        route = "esp32_connection",
        label = "ESP32 Link",
        selectedIcon = Icons.Filled.Bluetooth,
        unselectedIcon = Icons.Outlined.Bluetooth
    ),
    FIRMWARE(
        route = "firmware_wiring",
        label = "Wiring & Code",
        selectedIcon = Icons.Filled.Code,
        unselectedIcon = Icons.Outlined.Code
    ),
    HISTORY(
        route = "session_history",
        label = "Debrief",
        selectedIcon = Icons.Filled.Assessment,
        unselectedIcon = Icons.Outlined.Assessment
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                SmartCprGloveApp()
            }
        }
    }
}

@Composable
fun SmartCprGloveApp(
    viewModel: CprGloveViewModel = viewModel()
) {
    val cprState by viewModel.cprState.collectAsStateWithLifecycle()
    val connectionMode by viewModel.connectionMode.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val cloudChannelId by viewModel.cloudChannelId.collectAsStateWithLifecycle()
    val wifiEndpoint by viewModel.wifiEndpoint.collectAsStateWithLifecycle()
    val benchScenario by viewModel.benchScenario.collectAsStateWithLifecycle()
    val isMetronomeActive by viewModel.isMetronomeActive.collectAsStateWithLifecycle()
    val metronomeBpm by viewModel.metronomeBpm.collectAsStateWithLifecycle()
    val metronomeBeat by viewModel.metronomeBeat.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingSession.collectAsStateWithLifecycle()
    val recordingSeconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val terminalLines by viewModel.serialTerminalLines.collectAsStateWithLifecycle()
    val pinoutConfig by viewModel.pinoutConfig.collectAsStateWithLifecycle()
    val sessionsHistory by viewModel.sessionsHistory.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(CprDestination.COCKPIT) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(TelemetryNavyBg)
    ) {
        val isExpandedScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = TelemetryNavyBg,
            topBar = {
                CprTelemetryTopBar(
                    connectionMode = connectionMode,
                    connectionStatus = connectionStatus,
                    onClickConnectionBadge = { currentTab = CprDestination.ESP32_LINK }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        containerColor = TelemetrySurface,
                        tonalElevation = 8.dp
                    ) {
                        CprDestination.entries.forEach { dest ->
                            val selected = (currentTab == dest)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BioCyan,
                                    selectedTextColor = BioCyan,
                                    indicatorColor = BioCyanSoft,
                                    unselectedIconColor = SlateMuted,
                                    unselectedTextColor = SlateMuted
                                ),
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
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
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = TelemetrySurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        CprDestination.entries.forEach { dest ->
                            val selected = (currentTab == dest)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("rail_tab_${dest.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentTab) {
                        CprDestination.COCKPIT -> {
                            LiveCprCockpitScreen(
                                cprState = cprState,
                                connectionMode = connectionMode,
                                connectionStatus = connectionStatus,
                                statusMessage = statusMessage,
                                benchScenario = benchScenario,
                                isMetronomeActive = isMetronomeActive,
                                metronomeBpm = metronomeBpm,
                                metronomeBeat = metronomeBeat,
                                hapticEnabled = hapticEnabled,
                                isRecording = isRecording,
                                recordingSeconds = recordingSeconds,
                                onOpenConnectionTab = { currentTab = CprDestination.ESP32_LINK },
                                onSelectBenchScenario = { scenario ->
                                    viewModel.selectBenchScenario(scenario)
                                },
                                onSwitchToPhoneImu = {
                                    viewModel.selectConnectionMode(ConnectionMode.PHONE_IMU)
                                },
                                onToggleMetronome = { viewModel.toggleMetronome() },
                                onAdjustBpm = { newBpm -> viewModel.setMetronomeBpm(newBpm) },
                                onToggleHaptic = { viewModel.toggleHapticFeedback() },
                                onZeroSensors = { viewModel.tareAndZeroSensors() },
                                onToggleRecording = { viewModel.toggleSessionRecording() }
                            )
                        }
                        CprDestination.SENSORS -> {
                            SensorsDiagnosticsScreen(
                                cprState = cprState,
                                terminalLines = terminalLines,
                                onZeroSensors = { viewModel.tareAndZeroSensors() },
                                onSendSerialCommand = { cmd -> viewModel.sendCustomSerialCommand(cmd) },
                                onClearTerminal = { viewModel.clearTerminal() },
                                onNavigateBack = { currentTab = CprDestination.COCKPIT }
                            )
                        }
                        CprDestination.ESP32_LINK -> {
                            Esp32ConnectionScreen(
                                connectionMode = connectionMode,
                                connectionStatus = connectionStatus,
                                statusMessage = statusMessage,
                                discoveredDevices = discoveredDevices,
                                cloudChannelId = cloudChannelId,
                                wifiEndpoint = wifiEndpoint,
                                benchScenario = benchScenario,
                                onSelectMode = { mode -> viewModel.selectConnectionMode(mode) },
                                onUpdateCloudChannelId = { ch -> viewModel.updateCloudChannelId(ch) },
                                onConnectCloudBridge = { viewModel.connectCloudBridge() },
                                onScanBluetooth = { viewModel.scanBluetoothDevices() },
                                onConnectBluetoothDevice = { dev ->
                                    viewModel.connectToBluetoothDevice(dev)
                                },
                                onUpdateWifiEndpoint = { url -> viewModel.updateWifiEndpoint(url) },
                                onConnectWifi = { viewModel.connectWifi() },
                                onSelectBenchScenario = { scenario ->
                                    viewModel.selectBenchScenario(scenario)
                                },
                                onDisconnect = { viewModel.disconnectHardware() },
                                onNavigateBack = { currentTab = CprDestination.COCKPIT }
                            )
                        }
                        CprDestination.FIRMWARE -> {
                            FirmwareWiringScreen(
                                pinoutConfig = pinoutConfig,
                                onNavigateBack = { currentTab = CprDestination.COCKPIT }
                            )
                        }
                        CprDestination.HISTORY -> {
                            SessionHistoryScreen(
                                sessions = sessionsHistory,
                                onSaveSnapshotNow = { viewModel.saveInstantSnapshot() },
                                onDeleteSession = { id -> viewModel.deleteSession(id) },
                                onClearAllSessions = { viewModel.clearAllSessions() },
                                onNavigateBack = { currentTab = CprDestination.COCKPIT }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CprTelemetryTopBar(
    connectionMode: ConnectionMode,
    connectionStatus: ConnectionStatus,
    onClickConnectionBadge: () -> Unit
) {
    val dotColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> VitalEmerald
        ConnectionStatus.SCANNING, ConnectionStatus.CONNECTING -> CautionAmber
        ConnectionStatus.ERROR -> CriticalCrimson
        ConnectionStatus.DISCONNECTED -> SlateMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TelemetrySurface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SMART CPR GLOVE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IceWhite
                )
                Text(
                    text = "ESP32 • 3x FSR ARRAY • MPU6050 6-DOF IMU",
                    style = MaterialTheme.typography.labelSmall,
                    color = BioCyan
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(TelemetryCard)
                    .clickable { onClickConnectionBadge() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("top_bar_connection_pill")
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = connectionMode.shortTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = IceWhite
                )
            }
        }
        HorizontalDivider(color = TelemetryBorder.copy(alpha = 0.6f), thickness = 1.dp)
    }
}
