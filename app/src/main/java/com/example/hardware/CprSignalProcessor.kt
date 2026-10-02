package com.example.hardware

import com.example.model.CoachingDirective
import com.example.model.ProcessedCprState
import com.example.model.RawGlovePacket
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Parses incoming serial/BLE/Wi-Fi strings from the ESP32 Smart CPR Glove.
 * Supports:
 * 1. JSON format: {"fsr1":2400,"fsr2":600,"fsr3":550,"ax":0.02,"ay":-0.04,"az":-1.85,"gx":1.2,"gy":-0.5,"gz":0.1,"depth":5.4,"rate":110}
 * 2. Key-Value format: F1:2400,F2:600,F3:550,AX:0.02,AY:-0.04,AZ:-1.85,GX:1.2,GY:-0.5,GZ:0.1
 * 3. CSV format (6 or 9 or 11 columns): fsr1,fsr2,fsr3,ax,ay,az[,gx,gy,gz,depth,rate]
 */
object CprPacketParser {

    fun parseLine(rawLine: String): RawGlovePacket? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                trimmed.startsWith("{") && trimmed.endsWith("}") -> parseJson(trimmed)
                trimmed.contains(":") -> parseKeyValue(trimmed)
                else -> parseCsv(trimmed)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJson(jsonStr: String): RawGlovePacket? {
        val obj = JSONObject(jsonStr)
        val fsr1 = obj.optInt("fsr1", obj.optInt("f1", obj.optInt("heel", 0))).coerceIn(0, 4095)
        val fsr2 = obj.optInt("fsr2", obj.optInt("f2", obj.optInt("left", 0))).coerceIn(0, 4095)
        val fsr3 = obj.optInt("fsr3", obj.optInt("f3", obj.optInt("right", 0))).coerceIn(0, 4095)

        val ax = obj.optDouble("ax", 0.0).toFloat()
        val ay = obj.optDouble("ay", 0.0).toFloat()
        val az = obj.optDouble("az", 1.0).toFloat()

        val gx = obj.optDouble("gx", 0.0).toFloat()
        val gy = obj.optDouble("gy", 0.0).toFloat()
        val gz = obj.optDouble("gz", 0.0).toFloat()

        val depth = if (obj.has("depth")) obj.optDouble("depth", 0.0).toFloat() else null
        val rate = if (obj.has("rate")) obj.optInt("rate", 0) else null

        return RawGlovePacket(
            fsr1Raw = fsr1,
            fsr2Raw = fsr2,
            fsr3Raw = fsr3,
            ax = ax,
            ay = ay,
            az = az,
            gx = gx,
            gy = gy,
            gz = gz,
            overrideDepthCm = depth,
            overrideRateCpm = rate,
            rawLine = jsonStr
        )
    }

    private fun parseKeyValue(line: String): RawGlovePacket? {
        val map = mutableMapOf<String, Float>()
        val pairs = line.split(",", ";", " ")
        for (pair in pairs) {
            val kv = pair.split(":", "=")
            if (kv.size == 2) {
                val key = kv[0].trim().lowercase()
                val value = kv[1].trim().toFloatOrNull()
                if (value != null) {
                    map[key] = value
                }
            }
        }
        if (map.isEmpty()) return null

        val fsr1 = (map["fsr1"] ?: map["f1"] ?: 0f).toInt().coerceIn(0, 4095)
        val fsr2 = (map["fsr2"] ?: map["f2"] ?: 0f).toInt().coerceIn(0, 4095)
        val fsr3 = (map["fsr3"] ?: map["f3"] ?: 0f).toInt().coerceIn(0, 4095)
        val ax = map["ax"] ?: 0f
        val ay = map["ay"] ?: 0f
        val az = map["az"] ?: 1f
        val gx = map["gx"] ?: 0f
        val gy = map["gy"] ?: 0f
        val gz = map["gz"] ?: 0f
        val depth = map["depth"] ?: map["d"]
        val rate = (map["rate"] ?: map["bpm"] ?: map["cpm"])?.toInt()

        return RawGlovePacket(
            fsr1Raw = fsr1,
            fsr2Raw = fsr2,
            fsr3Raw = fsr3,
            ax = ax,
            ay = ay,
            az = az,
            gx = gx,
            gy = gy,
            gz = gz,
            overrideDepthCm = depth,
            overrideRateCpm = rate,
            rawLine = line
        )
    }

    private fun parseCsv(line: String): RawGlovePacket? {
        val parts = line.split(",", ";", "\t").mapNotNull { it.trim().toFloatOrNull() }
        if (parts.size < 3) return null

        val fsr1 = parts[0].toInt().coerceIn(0, 4095)
        val fsr2 = parts[1].toInt().coerceIn(0, 4095)
        val fsr3 = parts[2].toInt().coerceIn(0, 4095)
        val ax = if (parts.size > 3) parts[3] else 0f
        val ay = if (parts.size > 4) parts[4] else 0f
        val az = if (parts.size > 5) parts[5] else 1f
        val gx = if (parts.size > 6) parts[6] else 0f
        val gy = if (parts.size > 7) parts[7] else 0f
        val gz = if (parts.size > 8) parts[8] else 0f
        val depth = if (parts.size > 9) parts[9] else null
        val rate = if (parts.size > 10) parts[10].toInt() else null

        return RawGlovePacket(
            fsr1Raw = fsr1,
            fsr2Raw = fsr2,
            fsr3Raw = fsr3,
            ax = ax,
            ay = ay,
            az = az,
            gx = gx,
            gy = gy,
            gz = gz,
            overrideDepthCm = depth,
            overrideRateCpm = rate,
            rawLine = line
        )
    }
}

/**
 * Real-time Sensor Fusion & Biomechanical CPR Signal Processor
 * Fuses 3x FSR (12-bit ESP32 ADC 0..4095) + MPU6050 6-axis IMU (Accel + Gyro).
 */
class CprSignalProcessor {

    // Tare / calibration offsets
    private var fsr1Tare = 0
    private var fsr2Tare = 0
    private var fsr3Tare = 0
    private var gravityBaselineG = 1.0f
    private var pitchOffsetDeg = 0f
    private var rollOffsetDeg = 0f

    // Complementary filter states for orientation
    private var filteredPitch = 0f
    private var filteredRoll = 0f
    private var lastProcessTimeMs = 0L

    // Kinematic integration state for MPU6050 Z-axis displacement
    private var velocityZ = 0f
    private var displacementCm = 0f
    private var currentStrokeMaxDepthCm = 0f
    private var lastPeakDepthCm = 0f
    private var isInCompressionStroke = false

    // Recoil tracking
    private var minForceBetweenStrokesN = 0f
    private var strokeCountTotal = 0
    private var recoilCompliantStrokes = 0
    private var lastStrokeAchievedRecoil = true

    // Peak timestamps for CPM rate calculation
    private val peakTimestamps = ArrayDeque<Long>()
    private var currentRateCpm = 0

    // Rolling waveforms (70 samples)
    private val depthWaveBuffer = ArrayDeque<Float>(70)
    private val forceWaveBuffer = ArrayDeque<Float>(70)

    // Packet rate counter
    private val packetTimes = ArrayDeque<Long>()

    init {
        repeat(70) {
            depthWaveBuffer.addLast(0f)
            forceWaveBuffer.addLast(0f)
        }
    }

    fun tareSensors(latestPacket: RawGlovePacket?) {
        if (latestPacket != null) {
            fsr1Tare = latestPacket.fsr1Raw.coerceAtMost(800)
            fsr2Tare = latestPacket.fsr2Raw.coerceAtMost(800)
            fsr3Tare = latestPacket.fsr3Raw.coerceAtMost(800)
            val accelMag = sqrt(
                latestPacket.ax * latestPacket.ax +
                    latestPacket.ay * latestPacket.ay +
                    latestPacket.az * latestPacket.az
            )
            if (accelMag in 0.5f..1.5f) {
                gravityBaselineG = latestPacket.az
            }
            pitchOffsetDeg = computeRawPitch(latestPacket.ax, latestPacket.ay, latestPacket.az)
            rollOffsetDeg = computeRawRoll(latestPacket.ax, latestPacket.ay, latestPacket.az)
        }
        velocityZ = 0f
        displacementCm = 0f
    }

    fun resetSessionCounters() {
        strokeCountTotal = 0
        recoilCompliantStrokes = 0
        lastPeakDepthCm = 0f
        currentStrokeMaxDepthCm = 0f
        currentRateCpm = 0
        peakTimestamps.clear()
        velocityZ = 0f
        displacementCm = 0f
    }

    private fun computeRawPitch(ax: Float, ay: Float, az: Float): Float {
        return Math.toDegrees(atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
    }

    private fun computeRawRoll(ax: Float, ay: Float, az: Float): Float {
        return Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
    }

    fun processPacket(packet: RawGlovePacket): ProcessedCprState {
        val now = packet.timestampMs
        val dt = if (lastProcessTimeMs == 0L) 0.02f else ((now - lastProcessTimeMs) / 1000f).coerceIn(0.005f, 0.25f)
        lastProcessTimeMs = now

        // Track packet frequency (Hz)
        packetTimes.addLast(now)
        while (packetTimes.isNotEmpty() && now - packetTimes.first() > 1000L) {
            packetTimes.removeFirst()
        }
        val packetHz = packetTimes.size

        // 1. Process 3x FSR array (ADC 0..4095 -> calibrated Newtons)
        val cFsr1 = max(0, packet.fsr1Raw - fsr1Tare)
        val cFsr2 = max(0, packet.fsr2Raw - fsr2Tare)
        val cFsr3 = max(0, packet.fsr3Raw - fsr3Tare)

        val fsr1Pct = (cFsr1 / 4095f).coerceIn(0f, 1f)
        val fsr2Pct = (cFsr2 / 4095f).coerceIn(0f, 1f)
        val fsr3Pct = (cFsr3 / 4095f).coerceIn(0f, 1f)

        // Typical adult CPR requires ~300-550N total force; FSR1 heel carries main load
        val fsr1N = fsr1Pct * 420f
        val fsr2N = fsr2Pct * 220f
        val fsr3N = fsr3Pct * 220f
        val totalForceN = fsr1N + fsr2N + fsr3N

        // 2. Compute 2D Center of Pressure (CoP) across palm
        // FSR1 is at (x = 0.0, y = -0.65) [Heel]
        // FSR2 is at (x = -0.65, y = +0.45) [Left Palm / Thenar]
        // FSR3 is at (x = +0.65, y = +0.45) [Right Palm / Fingers]
        val sumForce = max(1f, totalForceN)
        val copX = if (totalForceN < 15f) 0f else ((fsr3N - fsr2N) / sumForce).coerceIn(-1f, 1f)
        val copY = if (totalForceN < 15f) -0.45f else (((fsr2N + fsr3N) * 0.55f - fsr1N * 0.65f) / sumForce).coerceIn(-1f, 1f)

        // 3. Process MPU6050 6-axis Orientation (Complementary Filter: 94% Gyro + 6% Accel)
        val rawPitch = computeRawPitch(packet.ax, packet.ay, packet.az) - pitchOffsetDeg
        val rawRoll = computeRawRoll(packet.ax, packet.ay, packet.az) - rollOffsetDeg
        filteredPitch = 0.92f * (filteredPitch + packet.gx * dt) + 0.08f * rawPitch
        filteredRoll = 0.92f * (filteredRoll + packet.gy * dt) + 0.08f * rawRoll
        val totalTilt = sqrt(filteredPitch * filteredPitch + filteredRoll * filteredRoll)

        // Hand placement score: penalize lateral FSR2/FSR3 dominance and arm tilt > 12 deg
        val heelRatio = if (totalForceN > 25f) (fsr1N / totalForceN).coerceIn(0f, 1f) else 0.75f
        val lateralImbalance = if (totalForceN > 25f) (abs(fsr2N - fsr3N) / totalForceN) else 0f
        val tiltPenalty = max(0f, (totalTilt - 8f) * 2.2f)
        val placementScore = (
            (heelRatio * 100f).coerceAtMost(85f) + 15f -
                (lateralImbalance * 35f) -
                tiltPenalty
            ).roundToInt().coerceIn(0, 100)

        // 4. Kinematic Double Integration + FSR Sensor Fusion for Compression Depth (cm)
        val dynAccelG = abs(packet.az - gravityBaselineG)
        if (packet.overrideDepthCm != null && packet.overrideDepthCm > 0f) {
            displacementCm = packet.overrideDepthCm
            lastPeakDepthCm = packet.overrideDepthCm
        } else {
            // Fuse MPU6050 vertical acceleration with FSR chest stiffness curve (approx 85 N/cm on adult sternum)
            val forceDerivedDepthCm = (totalForceN / 88f).coerceIn(0f, 7.8f)
            val accelDerivedDelta = dynAccelG * 981f * dt * dt * 12f
            velocityZ = (velocityZ + (packet.az - gravityBaselineG) * 981f * dt) * 0.86f
            val rawKinematicDepth = max(0f, displacementCm * 0.78f + accelDerivedDelta)

            // If FSR pads are connected & active, fuse 65% force-displacement + 35% IMU kinematics;
            // if On-Device Phone IMU mode (FSR = 0), use 100% IMU acceleration envelope!
            displacementCm = if (packet.fsr1Raw > 0 || packet.fsr2Raw > 0 || packet.fsr3Raw > 0) {
                (forceDerivedDepthCm * 0.72f + (dynAccelG * 3.1f + rawKinematicDepth) * 0.28f).coerceIn(0f, 8.0f)
            } else {
                // Phone IMU only mode: map dynamic G-force peak envelope to cm
                (dynAccelG * 3.6f).coerceIn(0f, 7.5f)
            }
        }

        // 5. Stroke & Peak Detection + Recoil Verification
        val activeThresholdCm = 1.6f
        val releaseThresholdCm = 0.85f

        if (!isInCompressionStroke) {
            minForceBetweenStrokesN = min(minForceBetweenStrokesN, totalForceN)
            if (displacementCm > activeThresholdCm) {
                isInCompressionStroke = true
                currentStrokeMaxDepthCm = displacementCm
                // Check if previous release achieved full recoil (< 45N residual force or < 0.85cm)
                lastStrokeAchievedRecoil = minForceBetweenStrokesN < 48f
            }
        } else {
            if (displacementCm > currentStrokeMaxDepthCm) {
                currentStrokeMaxDepthCm = displacementCm
            }
            if (displacementCm < releaseThresholdCm || (currentStrokeMaxDepthCm > 2.5f && displacementCm < currentStrokeMaxDepthCm * 0.42f)) {
                // Completed a compression stroke!
                if (now - (peakTimestamps.lastOrNull() ?: 0L) > 260L) { // Debounce >260ms (max ~230 CPM)
                    lastPeakDepthCm = currentStrokeMaxDepthCm
                    strokeCountTotal++
                    if (lastStrokeAchievedRecoil) {
                        recoilCompliantStrokes++
                    }
                    peakTimestamps.addLast(now)
                    while (peakTimestamps.size > 6) {
                        peakTimestamps.removeFirst()
                    }
                    if (peakTimestamps.size >= 2) {
                        val avgIntervalMs = (peakTimestamps.last() - peakTimestamps.first()).toFloat() / (peakTimestamps.size - 1)
                        if (avgIntervalMs > 200f) {
                            currentRateCpm = (60000f / avgIntervalMs).roundToInt().coerceIn(40, 220)
                        }
                    }
                }
                isInCompressionStroke = false
                currentStrokeMaxDepthCm = 0f
                minForceBetweenStrokesN = totalForceN
            }
        }

        if (packet.overrideRateCpm != null && packet.overrideRateCpm > 0) {
            currentRateCpm = packet.overrideRateCpm
        }

        // Decay rate to 0 if no compressions for > 3.5 seconds
        if (peakTimestamps.isNotEmpty() && now - peakTimestamps.last() > 3500L) {
            currentRateCpm = 0
        }

        val effectiveDepthCm = if (lastPeakDepthCm > 0.1f) lastPeakDepthCm else displacementCm
        val recoilPct = if (strokeCountTotal > 0) {
            ((recoilCompliantStrokes * 100f) / strokeCountTotal).roundToInt().coerceIn(0, 100)
        } else {
            100
        }

        // Update waveform buffers
        if (depthWaveBuffer.size >= 70) depthWaveBuffer.removeFirst()
        depthWaveBuffer.addLast(displacementCm)

        if (forceWaveBuffer.size >= 70) forceWaveBuffer.removeFirst()
        forceWaveBuffer.addLast(totalForceN)

        // 6. Compute AHA Compliance Score & Coaching Directive
        val depthScore = when {
            effectiveDepthCm in 5.0f..6.0f -> 100
            effectiveDepthCm in 4.5f..6.4f -> 75
            effectiveDepthCm > 0.5f -> 40
            else -> 100
        }
        val rateScore = when {
            currentRateCpm in 100..120 -> 100
            currentRateCpm in 90..130 -> 75
            currentRateCpm > 0 -> 40
            else -> 100
        }
        val overallAhaScore = (
            depthScore * 0.35f +
                rateScore * 0.25f +
                recoilPct * 0.20f +
                placementScore * 0.20f
            ).roundToInt().coerceIn(0, 100)

        val cycleComp = strokeCountTotal % 30
        val completedCycles = strokeCountTotal / 30

        val directive = when {
            currentRateCpm == 0 && displacementCm < 0.8f -> CoachingDirective.STANDBY
            strokeCountTotal > 0 && cycleComp == 0 && (now - (peakTimestamps.lastOrNull() ?: 0L)) < 2500L -> CoachingDirective.BREATH_PROMPT
            !lastStrokeAchievedRecoil -> CoachingDirective.INCOMPLETE_RECOIL
            effectiveDepthCm > 6.05f -> CoachingDirective.TOO_DEEP
            effectiveDepthCm in 0.8f..4.95f -> CoachingDirective.PUSH_HARDER
            placementScore < 68 || totalTilt > 16f -> CoachingDirective.CENTER_ON_HEEL
            currentRateCpm in 1..99 -> CoachingDirective.PUSH_FASTER
            currentRateCpm > 121 -> CoachingDirective.SLOW_DOWN
            else -> CoachingDirective.OPTIMAL
        }

        return ProcessedCprState(
            depthCm = effectiveDepthCm,
            instantDisplacementCm = displacementCm,
            compressionRateCpm = currentRateCpm,
            compressionCount = strokeCountTotal,
            cycleCompressionCount = if (strokeCountTotal > 0 && cycleComp == 0) 30 else cycleComp,
            completedCycles = completedCycles,
            fsr1Raw = cFsr1,
            fsr2Raw = cFsr2,
            fsr3Raw = cFsr3,
            fsr1Newtons = fsr1N,
            fsr2Newtons = fsr2N,
            fsr3Newtons = fsr3N,
            fsr1Pct = fsr1Pct,
            fsr2Pct = fsr2Pct,
            fsr3Pct = fsr3Pct,
            totalForceNewtons = totalForceN,
            centerOfPressureX = copX,
            centerOfPressureY = copY,
            handPlacementScore = placementScore,
            ax = packet.ax,
            ay = packet.ay,
            az = packet.az,
            gx = packet.gx,
            gy = packet.gy,
            gz = packet.gz,
            pitchDeg = filteredPitch,
            rollDeg = filteredRoll,
            totalTiltDeg = totalTilt,
            fullRecoilAchieved = lastStrokeAchievedRecoil,
            recoilCompliancePct = recoilPct,
            valahaComplianceScore = overallAhaScore,
            coachingDirective = directive,
            depthWaveform = depthWaveBuffer.toList(),
            forceWaveform = forceWaveBuffer.toList(),
            packetRateHz = packetHz,
            lastPacketLine = packet.rawLine
        )
    }
}
