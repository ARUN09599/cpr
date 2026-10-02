package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BenchScenario
import com.example.model.CoachingSeverity
import com.example.model.ConnectionMode
import com.example.model.ConnectionStatus
import com.example.model.ProcessedCprState
import com.example.ui.components.CompressionWaveCanvas
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CautionAmberSoft
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonSoft
import com.example.ui.theme.IceWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald
import com.example.ui.theme.VitalEmeraldSoft
import java.util.Locale

@Composable
fun LiveCprCockpitScreen(
    cprState: ProcessedCprState,
    connectionMode: ConnectionMode,
    connectionStatus: ConnectionStatus,
    statusMessage: String,
    benchScenario: BenchScenario,
    isMetronomeActive: Boolean,
    metronomeBpm: Int,
    metronomeBeat: Int,
    hapticEnabled: Boolean,
    isRecording: Boolean,
    recordingSeconds: Int,
    onOpenConnectionTab: () -> Unit,
    onSelectBenchScenario: (BenchScenario) -> Unit,
    onSwitchToPhoneImu: () -> Unit,
    onToggleMetronome: () -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onToggleHaptic: () -> Unit,
    onZeroSensors: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hardware Link Status Bar
            HardwareStatusHeaderBar(
                connectionMode = connectionMode,
                connectionStatus = connectionStatus,
                packetRateHz = cprState.packetRateHz,
                statusMessage = statusMessage,
                onOpenConnectionTab = onOpenConnectionTab,
                onSwitchToPhoneImu = onSwitchToPhoneImu,
                onActivateBenchSim = { onSelectBenchScenario(benchScenario) }
            )

            // 2. Prominent Safety Simulation Mode Indicator (Required when running hazard-free bench test)
            if (connectionMode == ConnectionMode.SAFETY_BENCH_SIM && connectionStatus == ConnectionStatus.CONNECTED) {
                SafetySimulationModeBanner(
                    selectedScenario = benchScenario,
                    onSelectScenario = onSelectBenchScenario,
                    onSwitchToRealHardware = onOpenConnectionTab
                )
            }

            // 3. Clinical AHA Coaching Directive Banner + 30:2 Cycle Progress
            CoachingDirectiveBanner(cprState = cprState)

            // 4. Primary Telemetry Readout Cards: Depth (cm) & Rate (CPM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryDepthCard(
                    depthCm = cprState.depthCm,
                    recoilAchieved = cprState.fullRecoilAchieved,
                    modifier = Modifier.weight(1f)
                )
                PrimaryRateCard(
                    rateCpm = cprState.compressionRateCpm,
                    compressionCount = cprState.compressionCount,
                    ahaScore = cprState.valahaComplianceScore,
                    modifier = Modifier.weight(1f)
                )
            }

            // 5. Real-Time Compression Depth & Force Oscilloscope
            Card(
                colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                    .testTag("oscilloscope_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MonitorHeart,
                                contentDescription = "Live Depth Waveform",
                                tint = BioCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MPU6050 + FSR DEPTH WAVEFORM",
                                style = MaterialTheme.typography.labelLarge,
                                color = IceWhite
                            )
                        }
                        Text(
                            text = "TARGET: 5.0–6.0 cm",
                            style = MaterialTheme.typography.labelSmall,
                            color = VitalEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CompressionWaveCanvas(
                        depthWaveform = cprState.depthWaveform,
                        forceWaveform = cprState.forceWaveform
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Total Force: %.0f N", cprState.totalForceNewtons),
                            style = MaterialTheme.typography.labelSmall,
                            color = CautionAmber
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Arm Tilt: %.1f° (Target <12°)",
                                cprState.totalTiltDeg
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cprState.totalTiltDeg <= 12f) VitalEmerald else CautionAmber
                        )
                        Text(
                            text = "Recoil: ${cprState.recoilCompliancePct}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (cprState.fullRecoilAchieved) VitalEmerald else CriticalCrimson
                        )
                    }
                }
            }

            // 6. 3-FSR Quick Force Balance Strip (FSR1 Heel, FSR2 Left, FSR3 Right)
            QuickThreeFsrSummaryCard(cprState = cprState)

            // 7. 110 BPM Resuscitation Metronome & Session Recording Controls
            MetronomeAndActionDeck(
                isMetronomeActive = isMetronomeActive,
                metronomeBpm = metronomeBpm,
                metronomeBeat = metronomeBeat,
                hapticEnabled = hapticEnabled,
                isRecording = isRecording,
                recordingSeconds = recordingSeconds,
                onToggleMetronome = onToggleMetronome,
                onAdjustBpm = onAdjustBpm,
                onToggleHaptic = onToggleHaptic,
                onZeroSensors = onZeroSensors,
                onToggleRecording = onToggleRecording
            )
        }
    }
}

@Composable
private fun HardwareStatusHeaderBar(
    connectionMode: ConnectionMode,
    connectionStatus: ConnectionStatus,
    packetRateHz: Int,
    statusMessage: String,
    onOpenConnectionTab: () -> Unit,
    onSwitchToPhoneImu: () -> Unit,
    onActivateBenchSim: () -> Unit
) {
    val badgeColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> VitalEmerald
        ConnectionStatus.SCANNING, ConnectionStatus.CONNECTING -> CautionAmber
        ConnectionStatus.ERROR -> CriticalCrimson
        ConnectionStatus.DISCONNECTED -> SlateMuted
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenConnectionTab() }
                        .testTag("hardware_status_banner")
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${connectionMode.shortTag} • ${connectionStatus.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite
                        )
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted,
                            maxLines = 1
                        )
                    }
                }
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TelemetryNavyBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$packetRateHz Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = BioCyan
                        )
                    }
                }
            }

            if (connectionStatus != ConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenConnectionTab,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("connect_esp32_quick_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bluetooth,
                            contentDescription = "Connect ESP32",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link ESP32", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onSwitchToPhoneImu,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("use_phone_imu_quick_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Use Phone IMU",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Phone IMU", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onActivateBenchSim,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("bench_sim_quick_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Science,
                            contentDescription = "Safety Bench Test",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bench Sim", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetySimulationModeBanner(
    selectedScenario: BenchScenario,
    onSelectScenario: (BenchScenario) -> Unit,
    onSwitchToRealHardware: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CautionAmberSoft.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CautionAmber, RoundedCornerShape(14.dp))
            .testTag("safety_simulation_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Safety Simulation Mode Active",
                        tint = CautionAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SAFETY SIMULATION MODE (BENCH TEST)",
                        style = MaterialTheme.typography.labelLarge,
                        color = CautionAmber
                    )
                }
                Text(
                    text = "Switch to ESP32 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = IceWhite,
                    modifier = Modifier
                        .clickable { onSwitchToRealHardware() }
                        .padding(4.dp)
                        .testTag("switch_to_real_esp32_link")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BenchScenario.entries.forEach { scenario ->
                    FilterChip(
                        selected = (scenario == selectedScenario),
                        onClick = { onSelectScenario(scenario) },
                        label = { Text(scenario.title, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CautionAmber,
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.testTag("scenario_chip_${scenario.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun CoachingDirectiveBanner(cprState: ProcessedCprState) {
    val directive = cprState.coachingDirective
    val bgTarget = when (directive.severity) {
        CoachingSeverity.OPTIMAL -> VitalEmeraldSoft
        CoachingSeverity.WARNING -> CautionAmberSoft
        CoachingSeverity.CRITICAL -> CriticalCrimsonSoft
        CoachingSeverity.INFO -> TelemetryCard
    }
    val borderTarget = when (directive.severity) {
        CoachingSeverity.OPTIMAL -> VitalEmerald
        CoachingSeverity.WARNING -> CautionAmber
        CoachingSeverity.CRITICAL -> CriticalCrimson
        CoachingSeverity.INFO -> BioCyan
    }
    val bgColor by animateColorAsState(targetValue = bgTarget, label = "coachingBg")
    val borderColor by animateColorAsState(targetValue = borderTarget, label = "coachingBorder")

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag("coaching_directive_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (directive.severity == CoachingSeverity.OPTIMAL) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.Warning
                        },
                        contentDescription = directive.title,
                        tint = borderColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = directive.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IceWhite
                        )
                        Text(
                            text = directive.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = IceWhite.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 30:2 Compression Cycle Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "30:2 CYCLE PROGRESS: ${cprState.cycleCompressionCount} / 30",
                    style = MaterialTheme.typography.labelSmall,
                    color = IceWhite
                )
                Text(
                    text = "CYCLES: ${cprState.completedCycles} • AHA SCORE: ${cprState.valahaComplianceScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (cprState.cycleCompressionCount / 30f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = borderColor,
                trackColor = TelemetryNavyBg
            )
        }
    }
}

@Composable
private fun PrimaryDepthCard(
    depthCm: Float,
    recoilAchieved: Boolean,
    modifier: Modifier = Modifier
) {
    val depthColor = when {
        depthCm > 6.05f -> CriticalCrimson
        depthCm in 5.0f..6.05f -> VitalEmerald
        depthCm > 0.5f -> CautionAmber
        else -> BioCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
            .testTag("depth_metric_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DEPTH (cm)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateMuted
                )
                Text(
                    text = if (recoilAchieved) "RECOIL OK" else "LEANING!",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (recoilAchieved) VitalEmerald else CriticalCrimson
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", depthCm),
                    style = MaterialTheme.typography.displayMedium,
                    color = depthColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "cm",
                    style = MaterialTheme.typography.titleMedium,
                    color = SlateMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (depthCm / 7.0f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = depthColor,
                trackColor = TelemetryNavyBg
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AHA Target: 5.0 – 6.0 cm",
                style = MaterialTheme.typography.labelSmall,
                color = SlateMuted
            )
        }
    }
}

@Composable
private fun PrimaryRateCard(
    rateCpm: Int,
    compressionCount: Int,
    ahaScore: Int,
    modifier: Modifier = Modifier
) {
    val rateColor = when {
        rateCpm in 100..120 -> VitalEmerald
        rateCpm in 90..130 -> CautionAmber
        rateCpm > 0 -> CriticalCrimson
        else -> BioCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
            .testTag("rate_metric_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RATE (CPM)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateMuted
                )
                Text(
                    text = "#$compressionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = BioCyan
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$rateCpm",
                    style = MaterialTheme.typography.displayMedium,
                    color = rateColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "cpm",
                    style = MaterialTheme.typography.titleMedium,
                    color = SlateMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (rateCpm / 150f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = rateColor,
                trackColor = TelemetryNavyBg
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AHA Target: 100 – 120 CPM",
                style = MaterialTheme.typography.labelSmall,
                color = SlateMuted
            )
        }
    }
}

@Composable
private fun QuickThreeFsrSummaryCard(cprState: ProcessedCprState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            .testTag("quick_fsr_summary_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3x FSR PALM FORCE DISTRIBUTION",
                    style = MaterialTheme.typography.labelLarge,
                    color = IceWhite
                )
                Text(
                    text = "Balance Score: ${cprState.handPlacementScore}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cprState.handPlacementScore >= 75) VitalEmerald else CautionAmber
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FsrMiniPill(
                    label = "FSR 1 (Heel)",
                    newtons = cprState.fsr1Newtons,
                    adc = cprState.fsr1Raw,
                    pct = cprState.fsr1Pct,
                    accent = VitalEmerald,
                    modifier = Modifier.weight(1f)
                )
                FsrMiniPill(
                    label = "FSR 2 (Left)",
                    newtons = cprState.fsr2Newtons,
                    adc = cprState.fsr2Raw,
                    pct = cprState.fsr2Pct,
                    accent = BioCyan,
                    modifier = Modifier.weight(1f)
                )
                FsrMiniPill(
                    label = "FSR 3 (Right)",
                    newtons = cprState.fsr3Newtons,
                    adc = cprState.fsr3Raw,
                    pct = cprState.fsr3Pct,
                    accent = BioCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FsrMiniPill(
    label: String,
    newtons: Float,
    adc: Int,
    pct: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TelemetryNavyBg)
            .padding(10.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = String.format(Locale.US, "%.0f N", newtons),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = IceWhite
        )
        Text(
            text = "ADC: $adc",
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = accent,
            trackColor = TelemetryCard
        )
    }
}

@Composable
private fun MetronomeAndActionDeck(
    isMetronomeActive: Boolean,
    metronomeBpm: Int,
    metronomeBeat: Int,
    hapticEnabled: Boolean,
    isRecording: Boolean,
    recordingSeconds: Int,
    onToggleMetronome: () -> Unit,
    onAdjustBpm: (Int) -> Unit,
    onToggleHaptic: () -> Unit,
    onZeroSensors: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Metronome",
                        tint = if (isMetronomeActive) VitalEmerald else SlateMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "AHA PACING METRONOME ($metronomeBpm BPM)",
                            style = MaterialTheme.typography.labelLarge,
                            color = IceWhite
                        )
                        Text(
                            text = if (isMetronomeActive) "Beat $metronomeBeat / 30 • Active Audio/Pulse" else "100–120 BPM audio-haptic rhythm guide",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMetronomeActive) VitalEmerald else SlateMuted
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleHaptic,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_haptic_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = "Toggle Haptic Vibration",
                            tint = if (hapticEnabled) BioCyan else SlateMuted
                        )
                    }
                    Button(
                        onClick = onToggleMetronome,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMetronomeActive) CriticalCrimson else VitalEmerald,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_metronome_button")
                    ) {
                        Icon(
                            imageVector = if (isMetronomeActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Start or Stop Metronome",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMetronomeActive) "STOP" else "110 BPM",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // BPM Fine Tuning + Zero Sensors + Record Session Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjustBpm(metronomeBpm - 5) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bpm_minus_button")
                ) {
                    Text("-5 BPM", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onAdjustBpm(metronomeBpm + 5) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bpm_plus_button")
                ) {
                    Text("+5 BPM", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onZeroSensors,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("tare_zero_sensors_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Tare Sensors",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TARE / ZERO", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onToggleRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) CriticalCrimson else BioCyan
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .minimumInteractiveComponentSize()
                        .testTag("record_session_button")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = "Record CPR Session",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecording) "SAVE (${recordingSeconds}s)" else "REC LOG",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
