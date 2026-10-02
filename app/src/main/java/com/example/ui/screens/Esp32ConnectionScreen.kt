package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BenchScenario
import com.example.model.ConnectionMode
import com.example.model.ConnectionStatus
import com.example.model.DiscoveredEspDevice
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald

@Composable
fun Esp32ConnectionScreen(
    connectionMode: ConnectionMode,
    connectionStatus: ConnectionStatus,
    statusMessage: String,
    discoveredDevices: List<DiscoveredEspDevice>,
    cloudChannelId: String,
    wifiEndpoint: String,
    benchScenario: BenchScenario,
    onSelectMode: (ConnectionMode) -> Unit,
    onUpdateCloudChannelId: (String) -> Unit,
    onConnectCloudBridge: () -> Unit,
    onScanBluetooth: () -> Unit,
    onConnectBluetoothDevice: (DiscoveredEspDevice) -> Unit,
    onUpdateWifiEndpoint: (String) -> Unit,
    onConnectWifi: () -> Unit,
    onSelectBenchScenario: (BenchScenario) -> Unit,
    onDisconnect: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onScanBluetooth()
    }

    fun requestBtPermissionsAndScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        btPermissionLauncher.launch(perms)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hardware Hero Banner Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(20.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cpr_glove_hero),
                        contentDescription = stringResource(id = R.string.hero_banner_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        TelemetryNavyBg.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SMART CPR GLOVE HARDWARE BRIDGE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IceWhite
                        )
                        Text(
                            text = "ESP32 Microcontroller • 3x FSR (12-Bit ADC) • MPU6050 6-DOF IMU",
                            style = MaterialTheme.typography.bodySmall,
                            color = BioCyan
                        )
                    }
                }
            }

            // 2. Current Link Status & Disconnect Row
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STATUS: ${connectionStatus.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> VitalEmerald
                                ConnectionStatus.ERROR -> CriticalCrimson
                                else -> CautionAmber
                            }
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                    if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("disconnect_hardware_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = "Disconnect",
                                tint = CriticalCrimson,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", color = CriticalCrimson, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // 3. Transport Mode Selector
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SELECT TELEMETRY TRANSPORT MODE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IceWhite
                    )

                    ConnectionMode.entries.forEach { mode ->
                        val selected = (mode == connectionMode)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) TelemetryNavyBg else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) BioCyan else TelemetryBorder.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectMode(mode)
                                    if (mode == ConnectionMode.BT_CLASSIC_SPP || mode == ConnectionMode.BLE_GATT) {
                                        requestBtPermissionsAndScan()
                                    }
                                }
                                .padding(10.dp)
                                .testTag("mode_option_${mode.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onSelectMode(mode)
                                    if (mode == ConnectionMode.BT_CLASSIC_SPP || mode == ConnectionMode.BLE_GATT) {
                                        requestBtPermissionsAndScan()
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = BioCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) BioCyan else IceWhite
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                        }
                    }
                }
            }

            // 4. Mode-Specific Configuration Panel
            when (connectionMode) {
                ConnectionMode.CLOUD_BRIDGE -> {
                    CloudBridgePanel(
                        cloudChannelId = cloudChannelId,
                        onUpdateChannel = onUpdateCloudChannelId,
                        onConnectCloudBridge = onConnectCloudBridge
                    )
                }
                ConnectionMode.BT_CLASSIC_SPP, ConnectionMode.BLE_GATT -> {
                    BluetoothDiscoveryPanel(
                        mode = connectionMode,
                        devices = discoveredDevices,
                        onScanClick = { requestBtPermissionsAndScan() },
                        onConnectDevice = onConnectBluetoothDevice
                    )
                }
                ConnectionMode.WIFI_STREAM -> {
                    WifiEndpointPanel(
                        wifiEndpoint = wifiEndpoint,
                        onUpdateEndpoint = onUpdateWifiEndpoint,
                        onConnectWifi = onConnectWifi
                    )
                }
                ConnectionMode.PHONE_IMU -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, VitalEmerald, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sensors,
                                contentDescription = "Phone IMU Active",
                                tint = VitalEmerald,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ON-DEVICE PHONE IMU ACTIVE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = VitalEmerald
                                )
                                Text(
                                    text = "Move or tilt your phone rhythmically to test live MPU6050 Z-axis acceleration, Pitch/Roll bullseye, and CPM rate detection using real phone sensors.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IceWhite
                                )
                            }
                        }
                    }
                }
                ConnectionMode.SAFETY_BENCH_SIM -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CautionAmber, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = "Safety Bench Simulation",
                                    tint = CautionAmber
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SAFETY BENCH SIMULATION PROFILES",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CautionAmber
                                )
                            }
                            BenchScenario.entries.forEach { scenario ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (scenario == benchScenario) TelemetryNavyBg else Color.Transparent)
                                        .clickable { onSelectBenchScenario(scenario) }
                                        .padding(10.dp)
                                        .testTag("bench_profile_${scenario.name.lowercase()}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = scenario.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = IceWhite
                                        )
                                        Text(
                                            text = scenario.badge,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BioCyan
                                        )
                                    }
                                    if (scenario == benchScenario) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VitalEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothDiscoveryPanel(
    mode: ConnectionMode,
    devices: List<DiscoveredEspDevice>,
    onScanClick: () -> Unit,
    onConnectDevice: (DiscoveredEspDevice) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothSearching,
                        contentDescription = "Bluetooth Devices",
                        tint = BioCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (mode == ConnectionMode.BLE_GATT) "ESP32 BLE PERIPHERALS" else "PAIRED ESP32 SPP DEVICES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IceWhite
                    )
                }
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BioCyan),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scan_bluetooth_devices_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Scan Bluetooth",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SCAN", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TelemetryNavyBg)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ESP32 Bluetooth devices detected yet. Tap SCAN to grant Bluetooth permissions and discover 'ESP32_CPR_Glove'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TelemetryNavyBg)
                                .clickable { onConnectDevice(dev) }
                                .padding(12.dp)
                                .testTag("bt_device_${dev.address}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BioCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bluetooth,
                                        contentDescription = dev.name,
                                        tint = BioCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = dev.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = IceWhite
                                    )
                                    Text(
                                        text = buildString {
                                            append(dev.address)
                                            if (dev.isPaired) append(" • Paired")
                                            if (dev.rssi != null) append(" • ${dev.rssi} dBm")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateMuted
                                    )
                                }
                            }
                            Text(
                                text = "CONNECT",
                                style = MaterialTheme.typography.labelSmall,
                                color = VitalEmerald
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBridgePanel(
    cloudChannelId: String,
    onUpdateChannel: (String) -> Unit,
    onConnectCloudBridge: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, VitalEmerald, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = "ESP32 Cloud Bridge",
                    tint = VitalEmerald
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESP32 CLOUD BRIDGE (LIVE IN BROWSER!)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VitalEmerald
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Because this preview runs in a Cloud Emulator, use Cloud Bridge to connect your real ESP32 over the Internet! Flash the 'Cloud Bridge' sketch from the Wiring & Code tab with this Channel ID:",
                style = MaterialTheme.typography.bodySmall,
                color = IceWhite
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = cloudChannelId,
                onValueChange = onUpdateChannel,
                label = { Text("Cloud Bridge Channel ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_channel_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onConnectCloudBridge,
                colors = ButtonDefaults.buttonColors(containerColor = VitalEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("connect_cloud_bridge_button")
            ) {
                Text(
                    text = "LISTEN TO ESP32 CLOUD CHANNEL",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun WifiEndpointPanel(
    wifiEndpoint: String,
    onUpdateEndpoint: (String) -> Unit,
    onConnectWifi: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = "ESP32 Wi-Fi Stream",
                    tint = BioCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESP32 WI-FI ENDPOINT (HTTP / WS / TCP)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = IceWhite
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = wifiEndpoint,
                onValueChange = onUpdateEndpoint,
                label = { Text("ESP32 Endpoint URL or IP:Port") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_endpoint_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    "http://192.168.4.1/telemetry",
                    "ws://192.168.4.1:81",
                    "192.168.4.1:8080"
                )
                presets.forEach { preset ->
                    FilterChip(
                        selected = (wifiEndpoint == preset),
                        onClick = { onUpdateEndpoint(preset) },
                        label = { Text(preset.removePrefix("http://"), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onConnectWifi,
                colors = ButtonDefaults.buttonColors(containerColor = VitalEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("connect_wifi_button")
            ) {
                Text("CONNECT TO ESP32 OVER WI-FI", color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
