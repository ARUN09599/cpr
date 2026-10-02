package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CprSessionEntity
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.IceWhite
import com.example.ui.theme.SlateMuted
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryCard
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHistoryScreen(
    sessions: List<CprSessionEntity>,
    onSaveSnapshotNow: () -> Unit,
    onDeleteSession: (Int) -> Unit,
    onClearAllSessions: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current

    fun shareSessionReport(session: CprSessionEntity) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(session.startTimeMs))
        val report = """
            SMART CPR GLOVE — BIOTELEMETRY DEBRIEF REPORT
            Timestamp: $dateStr
            Hardware Mode: ${session.connectionMode}
            Duration: ${session.durationSec} sec | Compressions: ${session.totalCompressions}
            Avg Depth: ${session.avgDepthCm} cm (AHA Target: 5.0–6.0 cm)
            Avg Rate: ${session.avgRateCpm} CPM (AHA Target: 100–120 CPM)
            Full Chest Recoil Compliance: ${session.recoilCompliancePct}%
            3-FSR Hand Balance Score: ${session.handBalanceScorePct}%
            Mean Force — FSR1(Heel): ${session.avgFsr1N}N, FSR2(Left): ${session.avgFsr2N}N, FSR3(Right): ${session.avgFsr3N}N
            MPU6050 Mean Arm Tilt: ${session.avgTiltDeg}°
            Overall AHA Guideline Score: ${session.ahaOverallScore}%
        """.trimIndent()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Smart CPR Glove Telemetry Report ($dateStr)")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export CPR Telemetry Report"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
        ) {
            // Top Action & Summary Header
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
                                imageVector = Icons.Filled.Assessment,
                                contentDescription = "Session Logs",
                                tint = BioCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "RESUSCITATION DEBRIEF LOGS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = IceWhite
                                )
                                Text(
                                    text = "${sessions.size} recorded session(s) stored in local Room database",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                        }
                        if (sessions.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAllSessions,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_all_sessions_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "Clear All Sessions",
                                    tint = CriticalCrimson
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onSaveSnapshotNow,
                        colors = ButtonDefaults.buttonColors(containerColor = VitalEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_snapshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddTask,
                            contentDescription = "Log Current Telemetry Snapshot",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LOG CURRENT GLOVE TELEMETRY SNAPSHOT",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sessions.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TelemetryCard),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TelemetryBorder, RoundedCornerShape(18.dp))
                        .testTag("empty_sessions_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Assessment,
                            contentDescription = "No Sessions",
                            tint = BioCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Recorded CPR Sessions Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = IceWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use the 'REC LOG' button on the Live CPR Cockpit or tap 'LOG CURRENT GLOVE TELEMETRY SNAPSHOT' above to save 3-FSR + MPU6050 session metrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionDebriefCard(
                            session = session,
                            onShare = { shareSessionReport(session) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDebriefCard(
    session: CprSessionEntity,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatted = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US).format(Date(session.startTimeMs))
    val scoreColor = when {
        session.ahaOverallScore >= 85 -> VitalEmerald
        session.ahaOverallScore >= 65 -> CautionAmber
        else -> CriticalCrimson
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = TelemetryCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            .testTag("session_item_card_${session.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$dateFormatted • ${session.connectionMode}",
                        style = MaterialTheme.typography.titleSmall,
                        color = IceWhite
                    )
                    Text(
                        text = session.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = BioCyan
                    )
                }
                Text(
                    text = "AHA ${session.ahaOverallScore}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TelemetryNavyBg)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DebriefStatColumn("DEPTH", "${session.avgDepthCm} cm")
                DebriefStatColumn("RATE", "${session.avgRateCpm} CPM")
                DebriefStatColumn("RECOIL", "${session.recoilCompliancePct}%")
                DebriefStatColumn("BALANCE", "${session.handBalanceScorePct}%")
                DebriefStatColumn("TILT", "${session.avgTiltDeg}°")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3-FSR Mean: F1 ${session.avgFsr1N}N | F2 ${session.avgFsr2N}N | F3 ${session.avgFsr3N}N",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
                Row {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("share_session_button_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share Session Report",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_session_button_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Session",
                            tint = SlateMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebriefStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SlateMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = IceWhite
        )
    }
}
