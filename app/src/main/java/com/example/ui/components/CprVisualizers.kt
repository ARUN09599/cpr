package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BioCyan
import com.example.ui.theme.CautionAmber
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.TelemetryBorder
import com.example.ui.theme.TelemetryNavyBg
import com.example.ui.theme.VitalEmerald
import kotlin.math.min

/**
 * Real-time clinical oscilloscope canvas plotting live CPR compression depth (0.0 to 8.0 cm)
 * with the AHA 5.0–6.0 cm target zone highlighted in emerald.
 */
@Composable
fun CompressionWaveCanvas(
    depthWaveform: List<Float>,
    forceWaveform: List<Float>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TelemetryNavyBg)
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            .padding(8.dp)
            .semantics {
                contentDescription = "Real-time CPR compression depth oscilloscope with 5 to 6 centimeter AHA target zone"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val maxDepthScaleCm = 7.5f

            fun depthToY(cm: Float): Float {
                val ratio = (cm / maxDepthScaleCm).coerceIn(0f, 1f)
                return h - (ratio * h) // 0 cm at bottom, 7.5 cm at top (or inverted if preferred; here peak compression rises clearly)
            }

            // 1. Draw AHA Target Zone (5.0 cm to 6.0 cm)
            val y6cm = depthToY(6.0f)
            val y5cm = depthToY(5.0f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        VitalEmerald.copy(alpha = 0.24f),
                        VitalEmerald.copy(alpha = 0.12f)
                    ),
                    startY = y6cm,
                    endY = y5cm
                ),
                topLeft = Offset(0f, y6cm),
                size = Size(w, y5cm - y6cm)
            )

            // 2. Horizontal Grid Lines (every 1.5 cm)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for (cm in listOf(1.5f, 3.0f, 5.0f, 6.0f)) {
                val y = depthToY(cm)
                val lineColor = when (cm) {
                    6.0f -> CriticalCrimson.copy(alpha = 0.65f)
                    5.0f -> VitalEmerald.copy(alpha = 0.75f)
                    else -> TelemetryBorder.copy(alpha = 0.45f)
                }
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (cm >= 5.0f) 2f else 1f,
                    pathEffect = if (cm >= 5.0f) dashEffect else null
                )
            }

            // Vertical subtle grid lines
            val cols = 7
            for (i in 1 until cols) {
                val x = (w / cols) * i
                drawLine(
                    color = TelemetryBorder.copy(alpha = 0.25f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }

            // 3. Plot secondary total FSR Force waveform (scaled 0..650N)
            if (forceWaveform.size >= 2) {
                val forcePath = Path()
                val stepX = w / (forceWaveform.size - 1).coerceAtLeast(1)
                forceWaveform.forEachIndexed { idx, forceN ->
                    val x = idx * stepX
                    val y = h - ((forceN / 650f).coerceIn(0f, 1f) * h)
                    if (idx == 0) forcePath.moveTo(x, y) else forcePath.lineTo(x, y)
                }
                drawPath(
                    path = forcePath,
                    color = CautionAmber.copy(alpha = 0.38f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 4. Plot primary Compression Depth waveform (cm)
            if (depthWaveform.size >= 2) {
                val depthPath = Path()
                val fillPath = Path()
                val stepX = w / (depthWaveform.size - 1).coerceAtLeast(1)
                depthWaveform.forEachIndexed { idx, cm ->
                    val x = idx * stepX
                    val y = depthToY(cm)
                    if (idx == 0) {
                        depthPath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        depthPath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BioCyan.copy(alpha = 0.30f),
                            BioCyan.copy(alpha = 0.02f)
                        )
                    )
                )

                val latestCm = depthWaveform.lastOrNull() ?: 0f
                val traceColor = when {
                    latestCm > 6.05f -> CriticalCrimson
                    latestCm in 5.0f..6.05f -> VitalEmerald
                    else -> BioCyan
                }

                drawPath(
                    path = depthPath,
                    color = traceColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Draw glowing leading dot
                val lastY = depthToY(latestCm)
                drawCircle(
                    color = traceColor.copy(alpha = 0.35f),
                    radius = 14f,
                    center = Offset(w - 2f, lastY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(w - 2f, lastY)
                )
            }
        }
    }
}

/**
 * Anatomical Smart CPR Glove 3-FSR Pressure Distribution & Center-of-Pressure (CoP) Visualizer.
 * Shows:
 * - FSR 1 (Palm Heel / Sternum Center)
 * - FSR 2 (Left Palm / Thenar Pad)
 * - FSR 3 (Right Palm / Hypothenar Pad)
 * - Real-time Center of Pressure vector reticle
 */
@Composable
fun GlovePalmFsrVisualizer(
    fsr1Pct: Float,
    fsr2Pct: Float,
    fsr3Pct: Float,
    copX: Float,
    copY: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TelemetryNavyBg)
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics {
                contentDescription = "Smart CPR Glove 3 FSR palm pressure heatmap and center of pressure reticle"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.54f
            val radius = min(w, h) * 0.42f

            // Outer palm boundary & finger silhouettes
            drawRoundRect(
                color = TelemetryBorder.copy(alpha = 0.35f),
                topLeft = Offset(cx - radius * 0.88f, cy - radius * 0.95f),
                size = Size(radius * 1.76f, radius * 1.88f),
                cornerRadius = CornerRadius(radius * 0.55f, radius * 0.55f),
                style = Stroke(width = 3f)
            )

            // Draw 4 subtle finger tips at the top of the glove
            val fingerOffsets = listOf(-0.52f, -0.18f, 0.18f, 0.52f)
            fingerOffsets.forEach { fx ->
                drawRoundRect(
                    color = TelemetryBorder.copy(alpha = 0.28f),
                    topLeft = Offset(cx + fx * radius - radius * 0.12f, cy - radius * 1.22f),
                    size = Size(radius * 0.24f, radius * 0.36f),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = 2f)
                )
            }

            // Sensor coordinates on palm:
            // FSR 1 (Heel - bottom center): (cx, cy + radius * 0.45f)
            // FSR 2 (Left Palm - upper left): (cx - radius * 0.44f, cy - radius * 0.25f)
            // FSR 3 (Right Palm - upper right): (cx + radius * 0.44f, cy - radius * 0.25f)
            val fsr1Center = Offset(cx, cy + radius * 0.45f)
            val fsr2Center = Offset(cx - radius * 0.44f, cy - radius * 0.25f)
            val fsr3Center = Offset(cx + radius * 0.44f, cy - radius * 0.25f)

            // Connecting triangle between the 3 FSR nodes
            val trianglePath = Path().apply {
                moveTo(fsr1Center.x, fsr1Center.y)
                lineTo(fsr2Center.x, fsr2Center.y)
                lineTo(fsr3Center.x, fsr3Center.y)
                close()
            }
            drawPath(
                path = trianglePath,
                color = TelemetryBorder.copy(alpha = 0.45f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )

            // Helper to draw each FSR pad with dynamic pressure glow
            fun drawFsrPad(center: Offset, pct: Float, baseColor: Color, maxR: Float) {
                val activePct = pct.coerceIn(0f, 1f)
                if (activePct > 0.02f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                baseColor.copy(alpha = 0.65f * activePct + 0.15f),
                                baseColor.copy(alpha = 0.0f)
                            ),
                            center = center,
                            radius = maxR * (0.8f + activePct * 0.65f)
                        ),
                        radius = maxR * (0.8f + activePct * 0.65f),
                        center = center
                    )
                }
                drawCircle(
                    color = baseColor.copy(alpha = 0.25f + 0.55f * activePct),
                    radius = maxR * 0.48f,
                    center = center
                )
                drawCircle(
                    color = baseColor,
                    radius = maxR * 0.48f,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }

            // Draw FSR 2 (Left Palm) & FSR 3 (Right Palm)
            drawFsrPad(
                center = fsr2Center,
                pct = fsr2Pct,
                baseColor = if (fsr2Pct > 0.65f) CautionAmber else BioCyan,
                maxR = radius * 0.42f
            )
            drawFsrPad(
                center = fsr3Center,
                pct = fsr3Pct,
                baseColor = if (fsr3Pct > 0.65f) CautionAmber else BioCyan,
                maxR = radius * 0.42f
            )

            // Draw FSR 1 (Heel - Primary Sternum Target)
            drawFsrPad(
                center = fsr1Center,
                pct = fsr1Pct,
                baseColor = VitalEmerald,
                maxR = radius * 0.54f
            )

            // Draw Optimal Heel Target Zone ring around FSR 1
            drawCircle(
                color = VitalEmerald.copy(alpha = 0.5f),
                radius = radius * 0.34f,
                center = fsr1Center,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            )

            // Draw Live 2D Center of Pressure (CoP) Reticle
            // copX in -1..1, copY in -1..1 (where -0.65 is FSR1 heel)
            val copScreenX = (cx + copX * radius * 0.55f).coerceIn(cx - radius * 0.7f, cx + radius * 0.7f)
            val copScreenY = (cy - copY * radius * 0.55f).coerceIn(cy - radius * 0.7f, cy + radius * 0.7f)
            val copPoint = Offset(copScreenX, copScreenY)

            val isHeelCentered = copY < -0.1f && kotlin.math.abs(copX) < 0.35f
            val reticleColor = if (isHeelCentered) Color.White else CautionAmber

            drawCircle(
                color = reticleColor,
                radius = 9f,
                center = copPoint
            )
            drawCircle(
                color = TelemetryNavyBg,
                radius = 4f,
                center = copPoint
            )
        }
    }
}

/**
 * MPU6050 6-Axis IMU Wrist Verticality / Tilt Bullseye Level.
 * Shows Pitch & Roll degrees relative to vertical compression axis.
 */
@Composable
fun Mpu6050TiltBullseye(
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TelemetryNavyBg)
            .border(1.dp, TelemetryBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics {
                contentDescription = "MPU6050 Pitch and Roll arm verticality bullseye level"
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val maxR = min(w, h) * 0.43f

            // Outer 30-degree ring
            drawCircle(
                color = TelemetryBorder.copy(alpha = 0.5f),
                radius = maxR,
                center = center,
                style = Stroke(width = 2f)
            )
            // Middle 15-degree warning ring
            drawCircle(
                color = CautionAmber.copy(alpha = 0.45f),
                radius = maxR * 0.6f,
                center = center,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )
            // Inner 8-degree optimal verticality ring
            drawCircle(
                color = VitalEmerald.copy(alpha = 0.6f),
                radius = maxR * 0.32f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Crosshairs
            drawLine(
                color = TelemetryBorder.copy(alpha = 0.4f),
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = TelemetryBorder.copy(alpha = 0.4f),
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1.5f
            )

            // Map pitch/roll (-30..+30 deg) to bullseye position
            val normX = (rollDeg / 30f).coerceIn(-1f, 1f)
            val normY = (pitchDeg / 30f).coerceIn(-1f, 1f)
            val bubblePos = Offset(center.x + normX * maxR, center.y + normY * maxR)

            val tiltMag = kotlin.math.sqrt(pitchDeg * pitchDeg + rollDeg * rollDeg)
            val bubbleColor = when {
                tiltMag <= 10f -> VitalEmerald
                tiltMag <= 16f -> CautionAmber
                else -> CriticalCrimson
            }

            drawCircle(
                color = bubbleColor.copy(alpha = 0.3f),
                radius = 20f,
                center = bubblePos
            )
            drawCircle(
                color = bubbleColor,
                radius = 9f,
                center = bubblePos
            )
        }
    }
}
