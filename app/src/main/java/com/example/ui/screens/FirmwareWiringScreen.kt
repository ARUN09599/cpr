package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hardware.Esp32FirmwareTemplates
import com.example.model.ConnectionMode
import com.example.model.PinoutConfig
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.IceWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald

@Composable
fun FirmwareWiringScreen(
    pinoutConfig: PinoutConfig,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    var selectedFirmwareType by remember { mutableStateOf(ConnectionMode.CLOUD_BRIDGE) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val generatedCode = remember(selectedFirmwareType, pinoutConfig) {
        Esp32FirmwareTemplates.generateSketch(selectedFirmwareType, pinoutConfig)
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
            // 1. Hardware Wiring Table (ESP32 + 3x FSR + MPU6050)
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("hardware_pinout_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DeveloperBoard,
                            contentDescription = "ESP32 Wiring Pinout",
                            tint = VitalEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SMART CPR GLOVE WIRING SCHEMATIC",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IceWhite
                            )
                            Text(
                                text = "Use ADC1 pins (GPIO 32–39) for the 3 FSRs so Wi-Fi/BT ADC2 conflicts never occur.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    WiringPinRow(
                        component = "FSR 1 (Palm Heel - Sternum)",
                        espPin = pinoutConfig.fsr1Pin,
                        notes = "3.3V → FSR1 → GPIO 34 + 10kΩ Pull-Down to GND",
                        accent = VitalEmerald
                    )
                    WiringPinRow(
                        component = "FSR 2 (Left Palm - Thenar)",
                        espPin = pinoutConfig.fsr2Pin,
                        notes = "3.3V → FSR2 → GPIO 35 + 10kΩ Pull-Down to GND",
                        accent = BioCyan
                    )
                    WiringPinRow(
                        component = "FSR 3 (Right Palm - Fingers)",
                        espPin = pinoutConfig.fsr3Pin,
                        notes = "3.3V → FSR3 → GPIO 32 + 10kΩ Pull-Down to GND",
                        accent = BioCyan
                    )
                    WiringPinRow(
                        component = "MPU6050 I2C Data (SDA)",
                        espPin = pinoutConfig.mpuSdaPin,
                        notes = "Connect MPU6050 VCC → 3.3V, GND → GND, AD0 → GND (0x68)",
                        accent = CautionAmber
                    )
                    WiringPinRow(
                        component = "MPU6050 I2C Clock (SCL)",
                        espPin = pinoutConfig.mpuSclPin,
                        notes = "400kHz Fast I2C Bus for 6-DOF Accel + Gyro",
                        accent = CautionAmber
                    )
                    WiringPinRow(
                        component = "Pacing Buzzer / Haptic Motor",
                        espPin = pinoutConfig.buzzerPin,
                        notes = "Optional active buzzer for 110 BPM wrist pacing",
                        accent = IceWhite
                    )
                }
            }

            // 2. Packet Protocol Specification Card
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
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "Telemetry Packet Format",
                            tint = BioCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTO-DETECTED TELEMETRY FORMATS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IceWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The app automatically decodes newline-terminated JSON, CSV, or Key:Value strings from your ESP32:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TelemetryNavyBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "JSON: {\"fsr1\":2450,\"fsr2\":510,\"fsr3\":490,\"ax\":0.03,\"ay\":-0.02,\"az\":2.15,\"gx\":1.2,\"gy\":-0.4,\"gz\":0.1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VitalEmerald
                        )
                        Text(
                            text = "CSV:  2450,510,490,0.03,-0.02,2.15,1.2,-0.4,0.1",
                            style = MaterialTheme.typography.bodySmall,
                            color = BioCyan
                        )
                    }
                }
            }

            // 3. Arduino IDE (.ino) Firmware Generator & One-Tap Copy
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("arduino_sketch_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = "Arduino ESP32 Sketch",
                                tint = BioCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESP32 ARDUINO (.INO) FIRMWARE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IceWhite
                            )
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("ESP32_CPR_Glove.ino", generatedCode)
                                )
                                copiedFeedback = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedFeedback) VitalEmerald else BioCyan
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_arduino_sketch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Sketch",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copiedFeedback) "COPIED!" else "COPY .INO",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ConnectionMode.CLOUD_BRIDGE to "Cloud Bridge (Works in Browser!)",
                            ConnectionMode.BT_CLASSIC_SPP to "Bluetooth Classic (SerialBT)",
                            ConnectionMode.BLE_GATT to "BLE GATT Notify",
                            ConnectionMode.WIFI_STREAM to "Wi-Fi SoftAP Server"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = (selectedFirmwareType == mode),
                                onClick = {
                                    selectedFirmwareType = mode
                                    copiedFeedback = false
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BioCyan,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("firmware_tab_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TelemetryNavyBg)
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = generatedCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = IceWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WiringPinRow(
    component: String,
    espPin: String,
    notes: String,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TelemetryNavyBg)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = component,
                style = MaterialTheme.typography.titleSmall,
                color = IceWhite
            )
            Text(
                text = espPin,
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = notes,
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted
        )
    }
}
