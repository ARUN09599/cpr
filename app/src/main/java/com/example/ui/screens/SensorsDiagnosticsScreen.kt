package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ProcessedCprState
import com.example.ui.components.GlovePalmFsrVisualizer
import com.example.ui.components.Mpu6050TiltBullseye
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald
import java.util.Locale

@Composable
fun SensorsDiagnosticsScreen(
    cprState: ProcessedCprState,
    terminalLines: List<String>,
    onZeroSensors: () -> Unit,
    onSendSerialCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var commandInput by remember { mutableStateOf("") }

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
            // 1. 3x FSR Array & Center of Pressure Palm Heatmap Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("fsr_palm_matrix_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Compress,
                                contentDescription = "3 FSR Force Matrix",
                                tint = VitalEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "3x FSR PALM FORCE & CoP VECTOR",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IceWhite
                                )
                                Text(
                                    text = "Heel (GPIO 34) • Left Thenar (GPIO 35) • Right Palm (GPIO 32)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onZeroSensors,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("diagnostics_zero_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MyLocation,
                                contentDescription = "Zero FSR & IMU",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TARE", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlovePalmFsrVisualizer(
                            fsr1Pct = cprState.fsr1Pct,
                            fsr2Pct = cprState.fsr2Pct,
                            fsr3Pct = cprState.fsr3Pct,
                            copX = cprState.centerOfPressureX,
                            copY = cprState.centerOfPressureY,
                            modifier = Modifier
                                .weight(1f)
                                .height(220.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FsrChannelDetailRow(
                                title = "FSR 1 • Palm Heel (Sternum)",
                                adcRaw = cprState.fsr1Raw,
                                newtons = cprState.fsr1Newtons,
                                pct = cprState.fsr1Pct,
                                color = VitalEmerald
                            )
                            FsrChannelDetailRow(
                                title = "FSR 2 • Left Palm (Thenar)",
                                adcRaw = cprState.fsr2Raw,
                                newtons = cprState.fsr2Newtons,
                                pct = cprState.fsr2Pct,
                                color = if (cprState.fsr2Pct > 0.65f) CautionAmber else BioCyan
                            )
                            FsrChannelDetailRow(
                                title = "FSR 3 • Right Palm (Fingers)",
                                adcRaw = cprState.fsr3Raw,
                                newtons = cprState.fsr3Newtons,
                                pct = cprState.fsr3Pct,
                                color = if (cprState.fsr3Pct > 0.65f) CautionAmber else BioCyan
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "CoP Vector: (X: %+.2f, Y: %+.2f) • Score: %d%%",
                                    cprState.centerOfPressureX,
                                    cprState.centerOfPressureY,
                                    cprState.handPlacementScore
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = IceWhite
                            )
                        }
                    }
                }
            }

            // 2. MPU6050 6-Axis IMU (3-Axis Accel + 3-Axis Gyro + Pitch/Roll Bullseye)
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("mpu6050_imu_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = "MPU6050 6-Axis IMU",
                            tint = BioCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "MPU6050 6-AXIS IMU KINEMATICS (I2C 0x68)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IceWhite
                            )
                            Text(
                                text = "3-Axis Accelerometer (±8g) + 3-Axis Gyroscope (±500°/s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Mpu6050TiltBullseye(
                            pitchDeg = cprState.pitchDeg,
                            rollDeg = cprState.rollDeg,
                            modifier = Modifier
                                .weight(1f)
                                .height(190.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImuMetricBox(
                                label = "ACCELEROMETER (g)",
                                line1 = String.format(Locale.US, "AX: %+.2f g   AY: %+.2f g", cprState.ax, cprState.ay),
                                line2 = String.format(Locale.US, "AZ (Vertical): %+.2f g", cprState.az),
                                accent = BioCyan
                            )
                            ImuMetricBox(
                                label = "GYROSCOPE (°/s)",
                                line1 = String.format(Locale.US, "GX: %+.1f°/s  GY: %+.1f°/s", cprState.gx, cprState.gy),
                                line2 = String.format(Locale.US, "GZ (Yaw): %+.1f °/s", cprState.gz),
                                accent = VitalEmerald
                            )
                            ImuMetricBox(
                                label = "ARM VERTICALITY (TILT)",
                                line1 = String.format(Locale.US, "Pitch: %+.1f°  Roll: %+.1f°", cprState.pitchDeg, cprState.rollDeg),
                                line2 = String.format(Locale.US, "Total Tilt: %.1f° (Max 12°)", cprState.totalTiltDeg),
                                accent = if (cprState.totalTiltDeg <= 12f) VitalEmerald else CriticalCrimson
                            )
                        }
                    }
                }
            }

            // 3. Live ESP32 Serial Telemetry Terminal & Packet Injector
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("serial_terminal_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Terminal,
                                contentDescription = "ESP32 Serial Monitor",
                                tint = BioCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESP32 SERIAL STREAM & PACKET TESTER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IceWhite
                            )
                        }
                        IconButton(
                            onClick = onClearTerminal,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_terminal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear Terminal Logs",
                                tint = SlateMuted
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TelemetryNavyBg)
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        terminalLines.takeLast(18).forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    line.startsWith("ERR") -> CriticalCrimson
                                    line.startsWith("TX") || line.startsWith("INJECT") -> CautionAmber
                                    line.startsWith("[") -> BioCyan
                                    else -> VitalEmerald
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commandInput,
                            onValueChange = { commandInput = it },
                            placeholder = {
                                Text(
                                    text = "Send command (ZERO, BEEP) or test CSV: 2800,450,410,0.02,-0.01,2.6",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("serial_command_input")
                        )
                        Button(
                            onClick = {
                                onSendSerialCommand(commandInput)
                                commandInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BioCyan),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("send_serial_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Serial Command",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FsrChannelDetailRow(
    title: String,
    adcRaw: Int,
    newtons: Float,
    pct: Float,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TelemetryNavyBg)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = IceWhite)
            Text(
                text = String.format(Locale.US, "%.0f N (%d)", newtons, adcRaw),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = TelemetryCard
        )
    }
}

@Composable
private fun ImuMetricBox(
    label: String,
    line1: String,
    line2: String,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TelemetryNavyBg)
            .padding(8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent)
        Text(text = line1, style = MaterialTheme.typography.bodySmall, color = IceWhite)
        Text(text = line2, style = MaterialTheme.typography.bodySmall, color = SlateMuted)
    }
}
