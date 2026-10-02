package com.example.model

data class RawGlovePacket(
    val fsr1Raw: Int, // 0..4095 (Heel / Center Sternum primary force sensor)
    val fsr2Raw: Int, // 0..4095 (Thenar / Left Palm lateral sensor)
    val fsr3Raw: Int, // 0..4095 (Hypothenar / Right Palm & Finger sensor)
    val ax: Float,    // MPU6050 Accel X (g)
    val ay: Float,    // MPU6050 Accel Y (g)
    val az: Float,    // MPU6050 Accel Z (g) - vertical compression axis
    val gx: Float,    // MPU6050 Gyro X (deg/s)
    val gy: Float,    // MPU6050 Gyro Y (deg/s)
    val gz: Float,    // MPU6050 Gyro Z (deg/s)
    val overrideDepthCm: Float? = null,
    val overrideRateCpm: Int? = null,
    val rawLine: String = "",
    val timestampMs: Long = System.currentTimeMillis()
)

enum class CoachingSeverity {
    OPTIMAL,
    WARNING,
    CRITICAL,
    INFO
}

enum class CoachingDirective(
    val title: String,
    val subtitle: String,
    val severity: CoachingSeverity
) {
    STANDBY(
        title = "READY FOR COMPRESSIONS",
        subtitle = "Place glove palm heel on center of sternum. Target 5.0–6.0 cm at 100–120 CPM.",
        severity = CoachingSeverity.INFO
    ),
    OPTIMAL(
        title = "OPTIMAL AHA COMPRESSIONS",
        subtitle = "Depth 5.0–6.0 cm, Rate 100–120 CPM, Full Recoil & Heel-Centered Force.",
        severity = CoachingSeverity.OPTIMAL
    ),
    PUSH_HARDER(
        title = "PUSH HARDER (SHALLOW DEPTH)",
        subtitle = "Compression depth is below 5.0 cm. Increase downward sternum force on FSR 1.",
        severity = CoachingSeverity.WARNING
    ),
    TOO_DEEP(
        title = "TOO DEEP — REDUCE FORCE",
        subtitle = "Depth exceeds 6.0 cm! Risk of internal injury. Moderate peak compression force.",
        severity = CoachingSeverity.CRITICAL
    ),
    PUSH_FASTER(
        title = "PUSH FASTER (< 100 CPM)",
        subtitle = "Compression rate is too slow. Match the 110 BPM metronome pulse.",
        severity = CoachingSeverity.WARNING
    ),
    SLOW_DOWN(
        title = "SLOW DOWN (> 120 CPM)",
        subtitle = "Compressing too fast reduces cardiac filling time. Follow the 110 BPM rhythm.",
        severity = CoachingSeverity.WARNING
    ),
    INCOMPLETE_RECOIL(
        title = "RELEASE COMPLETELY (NO RECOIL)",
        subtitle = "Residual force detected on FSR pads between strokes. Let chest fully recoil.",
        severity = CoachingSeverity.CRITICAL
    ),
    CENTER_ON_HEEL(
        title = "SHIFT FORCE TO PALM HEEL (FSR 1)",
        subtitle = "Excessive lateral force on FSR 2 / FSR 3 or wrist tilt > 15°. Keep arms vertical.",
        severity = CoachingSeverity.WARNING
    ),
    BREATH_PROMPT(
        title = "30 COMPRESSIONS REACHED — 2 BREATHS",
        subtitle = "Deliver 2 rescue breaths (1 sec each), then immediately resume compressions.",
        severity = CoachingSeverity.INFO
    )
}

data class ProcessedCprState(
    val depthCm: Float = 0f,
    val instantDisplacementCm: Float = 0f,
    val compressionRateCpm: Int = 0,
    val compressionCount: Int = 0,
    val cycleCompressionCount: Int = 0, // 0..30 for 30:2 cycle
    val completedCycles: Int = 0,
    val fsr1Raw: Int = 0,
    val fsr2Raw: Int = 0,
    val fsr3Raw: Int = 0,
    val fsr1Newtons: Float = 0f,
    val fsr2Newtons: Float = 0f,
    val fsr3Newtons: Float = 0f,
    val fsr1Pct: Float = 0f,
    val fsr2Pct: Float = 0f,
    val fsr3Pct: Float = 0f,
    val totalForceNewtons: Float = 0f,
    val centerOfPressureX: Float = 0f, // -1.0 (Left/FSR2) to +1.0 (Right/FSR3)
    val centerOfPressureY: Float = -0.35f, // -1.0 (Heel/FSR1) to +1.0 (Fingers)
    val handPlacementScore: Int = 100,
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 1.0f,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val totalTiltDeg: Float = 0f,
    val fullRecoilAchieved: Boolean = true,
    val recoilCompliancePct: Int = 100,
    val valahaComplianceScore: Int = 100,
    val coachingDirective: CoachingDirective = CoachingDirective.STANDBY,
    val depthWaveform: List<Float> = List(70) { 0f },
    val forceWaveform: List<Float> = List(70) { 0f },
    val packetRateHz: Int = 0,
    val lastPacketLine: String = "Waiting for ESP32 stream..."
)

enum class ConnectionMode(val label: String, val shortTag: String, val description: String) {
    CLOUD_BRIDGE(
        label = "ESP32 Cloud Bridge (Works in Browser Emulator!)",
        shortTag = "Cloud Bridge",
        description = "Streams ESP32 3-FSR + MPU6050 over Wi-Fi Internet Relay (ntfy.sh pub/sub) directly into this Cloud Emulator!"
    ),
    BT_CLASSIC_SPP(
        label = "ESP32 Bluetooth Classic (SerialBT)",
        shortTag = "BT Classic SPP",
        description = "Standard RFCOMM Serial Port Profile (UUID 00001101...) for ESP32 #include <BluetoothSerial.h> on a real phone"
    ),
    BLE_GATT(
        label = "ESP32 Bluetooth Low Energy (BLE GATT)",
        shortTag = "BLE GATT",
        description = "Low-power BLE UART / Custom GATT Notify characteristic streaming from ESP32"
    ),
    WIFI_STREAM(
        label = "ESP32 Wi-Fi (WebSocket / HTTP / TCP)",
        shortTag = "Wi-Fi Stream",
        description = "Connects over Wi-Fi to ESP32 SoftAP (192.168.4.1) or local LAN IP via WebSocket or HTTP"
    ),
    PHONE_IMU(
        label = "On-Device Phone IMU (Real Hardware Sensors)",
        shortTag = "Phone IMU",
        description = "Uses this Android device's real physical Accelerometer & Gyroscope strapped to wrist/hand"
    ),
    SAFETY_BENCH_SIM(
        label = "Safety Bench Simulation (Hazard-Free Test)",
        shortTag = "Safety Sim",
        description = "Synthetic 3-FSR + MPU6050 biomechanical generator for safe bench testing without chest force hazard"
    )
}

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class DiscoveredEspDevice(
    val name: String,
    val address: String,
    val rssi: Int? = null,
    val isBle: Boolean = false,
    val isPaired: Boolean = false
)

enum class BenchScenario(
    val title: String,
    val badge: String,
    val targetDepthCm: Float,
    val targetRateCpm: Int,
    val fsrBalanceBias: Float, // 0f = ideal heel, 1f = heavy lateral/finger imbalance
    val incompleteRecoil: Boolean
) {
    AHA_OPTIMAL(
        title = "AHA Guideline Ideal",
        badge = "5.5 cm • 110 CPM • Heel Centered",
        targetDepthCm = 5.5f,
        targetRateCpm = 110,
        fsrBalanceBias = 0.0f,
        incompleteRecoil = false
    ),
    SHALLOW_FAST(
        title = "Shallow & Rapid Fatigue",
        badge = "3.7 cm • 132 CPM • Push Harder",
        targetDepthCm = 3.7f,
        targetRateCpm = 132,
        fsrBalanceBias = 0.15f,
        incompleteRecoil = false
    ),
    TOO_DEEP_NO_RECOIL(
        title = "Excessive Depth + Leaning",
        badge = "6.5 cm • 108 CPM • Incomplete Recoil",
        targetDepthCm = 6.5f,
        targetRateCpm = 108,
        fsrBalanceBias = 0.2f,
        incompleteRecoil = true
    ),
    OFF_CENTER_RIB_LOAD(
        title = "Off-Center Palm / Rib Pressure",
        badge = "5.2 cm • High FSR2/FSR3 • 19° Tilt",
        targetDepthCm = 5.2f,
        targetRateCpm = 112,
        fsrBalanceBias = 0.85f,
        incompleteRecoil = false
    )
}

data class PinoutConfig(
    val fsr1Pin: String = "GPIO 34 (ADC1_CH6)",
    val fsr2Pin: String = "GPIO 35 (ADC1_CH7)",
    val fsr3Pin: String = "GPIO 32 (ADC1_CH4)",
    val mpuSdaPin: String = "GPIO 21 (I2C SDA)",
    val mpuSclPin: String = "GPIO 22 (I2C SCL)",
    val mpuIntPin: String = "GPIO 19 (Optional INT)",
    val buzzerPin: String = "GPIO 25 (PWM Metronome)",
    val statusLedPin: String = "GPIO 2 (Onboard LED)"
)
