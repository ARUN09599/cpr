package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CprDatabase
import com.example.data.CprSessionEntity
import com.example.data.CprSessionRepository
import com.example.hardware.CprMetronomeEngine
import com.example.hardware.CprPacketParser
import com.example.hardware.CprSignalProcessor
import com.example.hardware.Esp32HardwareManager
import com.example.model.BenchScenario
import com.example.model.ConnectionMode
import com.example.model.ConnectionStatus
import com.example.model.DiscoveredEspDevice
import com.example.model.PinoutConfig
import com.example.model.ProcessedCprState
import com.example.model.RawGlovePacket
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CprGloveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CprDatabase.getInstance(application)
    private val repository = CprSessionRepository(database.cprSessionDao())

    private val signalProcessor = CprSignalProcessor()
    private val metronomeEngine = CprMetronomeEngine(application)

    private val _connectionMode = MutableStateFlow(ConnectionMode.CLOUD_BRIDGE)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private val _cloudChannelId = MutableStateFlow("cpr_glove_2026")
    val cloudChannelId: StateFlow<String> = _cloudChannelId.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Select ESP32 transport (BT Classic, BLE, Wi-Fi) or On-Device IMU.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredEspDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredEspDevice>> = _discoveredDevices.asStateFlow()

    private val _wifiEndpoint = MutableStateFlow("http://192.168.4.1/telemetry")
    val wifiEndpoint: StateFlow<String> = _wifiEndpoint.asStateFlow()

    private val _cprState = MutableStateFlow(ProcessedCprState())
    val cprState: StateFlow<ProcessedCprState> = _cprState.asStateFlow()

    private val _benchScenario = MutableStateFlow(BenchScenario.AHA_OPTIMAL)
    val benchScenario: StateFlow<BenchScenario> = _benchScenario.asStateFlow()

    private val _isMetronomeActive = MutableStateFlow(false)
    val isMetronomeActive: StateFlow<Boolean> = _isMetronomeActive.asStateFlow()

    private val _metronomeBpm = MutableStateFlow(110)
    val metronomeBpm: StateFlow<Int> = _metronomeBpm.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _metronomeBeat = MutableStateFlow(0)
    val metronomeBeat: StateFlow<Int> = _metronomeBeat.asStateFlow()

    private val _isRecordingSession = MutableStateFlow(false)
    val isRecordingSession: StateFlow<Boolean> = _isRecordingSession.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _serialTerminalLines = MutableStateFlow<List<String>>(
        listOf(
            "[SYS] Smart CPR Glove Telemetry Engine Initialized",
            "[SYS] Configured Sensors: 3x FSR (12-bit ADC) + MPU6050 6-Axis IMU",
            "[SYS] Supported Formats: JSON, CSV (fsr1,fsr2,fsr3,ax,ay,az,gx,gy,gz), Key:Value"
        )
    )
    val serialTerminalLines: StateFlow<List<String>> = _serialTerminalLines.asStateFlow()

    private val _pinoutConfig = MutableStateFlow(PinoutConfig())
    val pinoutConfig: StateFlow<PinoutConfig> = _pinoutConfig.asStateFlow()

    val sessionsHistory: StateFlow<List<CprSessionEntity>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var latestRawPacket: RawGlovePacket? = null
    private var recordingTimerJob: Job? = null

    // Accumulators for active session recording
    private var sessionDepthSum = 0f
    private var sessionRateSum = 0
    private var sessionFsr1Sum = 0f
    private var sessionFsr2Sum = 0f
    private var sessionFsr3Sum = 0f
    private var sessionTiltSum = 0f
    private var sessionSamples = 0

    private val hardwareManager = Esp32HardwareManager(
        context = application,
        scope = viewModelScope,
        onStatusChange = { status, msg ->
            _connectionStatus.value = status
            _statusMessage.value = msg
            appendTerminalLog("[LINK] ${status.name}: $msg")
        },
        onDevicesUpdated = { devices ->
            _discoveredDevices.value = devices
        },
        onRawLineReceived = { rawLine ->
            // Throttle terminal log updates slightly so UI stays silky smooth
            if (System.currentTimeMillis() % 4L == 0L) {
                appendTerminalLog("RX < $rawLine")
            }
        },
        onPacketReceived = { packet ->
            latestRawPacket = packet
            val newState = signalProcessor.processPacket(packet)
            _cprState.value = newState
            if (_isRecordingSession.value && newState.depthCm > 0.2f) {
                sessionDepthSum += newState.depthCm
                sessionRateSum += newState.compressionRateCpm
                sessionFsr1Sum += newState.fsr1Newtons
                sessionFsr2Sum += newState.fsr2Newtons
                sessionFsr3Sum += newState.fsr3Newtons
                sessionTiltSum += newState.totalTiltDeg
                sessionSamples++
            }
        }
    )

    fun selectConnectionMode(mode: ConnectionMode) {
        if (_connectionMode.value == mode && _connectionStatus.value == ConnectionStatus.CONNECTED) return
        hardwareManager.disconnectAll()
        _connectionMode.value = mode
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        when (mode) {
            ConnectionMode.CLOUD_BRIDGE -> {
                _statusMessage.value = "Cloud Bridge selected. Tap 'Connect Cloud Bridge' to receive ESP32 data in this browser emulator!"
                hardwareManager.connectCloudBridge(_cloudChannelId.value)
            }
            ConnectionMode.BT_CLASSIC_SPP -> {
                _statusMessage.value = "Bluetooth Classic SPP (SerialBT) selected. Scan or pick your paired ESP32."
                hardwareManager.refreshPairedAndScanDevices(mode)
            }
            ConnectionMode.BLE_GATT -> {
                _statusMessage.value = "BLE GATT mode selected. Tap Scan to discover ESP32 BLE telemetry service."
                hardwareManager.refreshPairedAndScanDevices(mode)
            }
            ConnectionMode.WIFI_STREAM -> {
                _statusMessage.value = "Wi-Fi mode selected. Enter ESP32 IP/WebSocket URL and tap Connect."
            }
            ConnectionMode.PHONE_IMU -> {
                hardwareManager.startPhoneImuMode()
            }
            ConnectionMode.SAFETY_BENCH_SIM -> {
                hardwareManager.startSafetyBenchSimulation { _benchScenario.value }
            }
        }
    }

    fun updateCloudChannelId(channel: String) {
        _cloudChannelId.value = channel
    }

    fun connectCloudBridge() {
        hardwareManager.connectCloudBridge(_cloudChannelId.value)
    }

    fun scanBluetoothDevices() {
        hardwareManager.refreshPairedAndScanDevices(_connectionMode.value)
    }

    fun connectToBluetoothDevice(device: DiscoveredEspDevice) {
        if (_connectionMode.value == ConnectionMode.BLE_GATT || device.isBle) {
            hardwareManager.connectBleGatt(device.address)
        } else {
            hardwareManager.connectClassicSpp(device.address)
        }
    }

    fun updateWifiEndpoint(endpoint: String) {
        _wifiEndpoint.value = endpoint
    }

    fun connectWifi() {
        hardwareManager.connectWifiEndpoint(_wifiEndpoint.value)
    }

    fun disconnectHardware() {
        hardwareManager.disconnectAll()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _statusMessage.value = "Disconnected from hardware stream."
    }

    fun selectBenchScenario(scenario: BenchScenario) {
        _benchScenario.value = scenario
        if (_connectionMode.value != ConnectionMode.SAFETY_BENCH_SIM ||
            _connectionStatus.value != ConnectionStatus.CONNECTED
        ) {
            _connectionMode.value = ConnectionMode.SAFETY_BENCH_SIM
            hardwareManager.startSafetyBenchSimulation { _benchScenario.value }
        }
    }

    fun tareAndZeroSensors() {
        signalProcessor.tareSensors(latestRawPacket)
        hardwareManager.sendCommandToEsp32("ZERO")
        appendTerminalLog("TX > ZERO (Tared 3x FSR baseline & MPU6050 gravity vector)")
    }

    fun sendCustomSerialCommand(cmd: String) {
        val clean = cmd.trim()
        if (clean.isEmpty()) return
        // If the user pastes a telemetry JSON or CSV line into the terminal, parse it directly for instant testing!
        val testPacket = CprPacketParser.parseLine(clean)
        if (testPacket != null) {
            appendTerminalLog("INJECT < $clean")
            latestRawPacket = testPacket
            _cprState.value = signalProcessor.processPacket(testPacket)
        } else {
            val sent = hardwareManager.sendCommandToEsp32(clean)
            appendTerminalLog(if (sent) "TX > $clean" else "TX (Queued/Offline) > $clean")
        }
    }

    fun toggleMetronome() {
        val next = !_isMetronomeActive.value
        _isMetronomeActive.value = next
        if (next) {
            metronomeEngine.start(
                scope = viewModelScope,
                bpmProvider = { _metronomeBpm.value },
                hapticEnabledProvider = { _hapticEnabled.value },
                onBeat = { beat -> _metronomeBeat.value = beat }
            )
        } else {
            metronomeEngine.stop()
            _metronomeBeat.value = 0
        }
    }

    fun setMetronomeBpm(bpm: Int) {
        _metronomeBpm.value = bpm.coerceIn(90, 140)
    }

    fun toggleHapticFeedback() {
        _hapticEnabled.value = !_hapticEnabled.value
    }

    fun toggleSessionRecording() {
        if (_isRecordingSession.value) {
            stopAndSaveSession()
        } else {
            startSessionRecording()
        }
    }

    private fun startSessionRecording() {
        signalProcessor.resetSessionCounters()
        sessionDepthSum = 0f
        sessionRateSum = 0
        sessionFsr1Sum = 0f
        sessionFsr2Sum = 0f
        sessionFsr3Sum = 0f
        sessionTiltSum = 0f
        sessionSamples = 0
        _recordingSeconds.value = 0
        _isRecordingSession.value = true

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive && _isRecordingSession.value) {
                delay(1000L)
                _recordingSeconds.value += 1
            }
        }
        appendTerminalLog("[REC] Started recording CPR telemetry session.")
    }

    private fun stopAndSaveSession() {
        _isRecordingSession.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val state = _cprState.value
        val duration = _recordingSeconds.value.coerceAtLeast(1)
        val n = sessionSamples.coerceAtLeast(1)

        val avgDepth = if (sessionSamples > 0) (sessionDepthSum / n) else state.depthCm
        val avgRate = if (sessionSamples > 0) (sessionRateSum / n) else state.compressionRateCpm
        val avgF1 = if (sessionSamples > 0) (sessionFsr1Sum / n) else state.fsr1Newtons
        val avgF2 = if (sessionSamples > 0) (sessionFsr2Sum / n) else state.fsr2Newtons
        val avgF3 = if (sessionSamples > 0) (sessionFsr3Sum / n) else state.fsr3Newtons
        val avgTilt = if (sessionSamples > 0) (sessionTiltSum / n) else state.totalTiltDeg

        val session = CprSessionEntity(
            startTimeMs = System.currentTimeMillis() - (duration * 1000L),
            durationSec = duration,
            connectionMode = _connectionMode.value.shortTag,
            totalCompressions = state.compressionCount,
            avgDepthCm = (avgDepth * 10f).roundToInt() / 10f,
            avgRateCpm = avgRate,
            recoilCompliancePct = state.recoilCompliancePct,
            handBalanceScorePct = state.handPlacementScore,
            avgFsr1N = (avgF1 * 10f).roundToInt() / 10f,
            avgFsr2N = (avgF2 * 10f).roundToInt() / 10f,
            avgFsr3N = (avgF3 * 10f).roundToInt() / 10f,
            avgTiltDeg = (avgTilt * 10f).roundToInt() / 10f,
            ahaOverallScore = state.valahaComplianceScore,
            notes = state.coachingDirective.title
        )

        viewModelScope.launch {
            repository.saveSession(session)
            appendTerminalLog("[REC] Saved CPR Session (${state.compressionCount} compressions, AHA Score: ${state.valahaComplianceScore}%)")
        }
    }

    fun saveInstantSnapshot() {
        val state = _cprState.value
        val session = CprSessionEntity(
            startTimeMs = System.currentTimeMillis(),
            durationSec = (_recordingSeconds.value).coerceAtLeast(15),
            connectionMode = _connectionMode.value.shortTag,
            totalCompressions = state.compressionCount.coerceAtLeast(1),
            avgDepthCm = (state.depthCm * 10f).roundToInt() / 10f,
            avgRateCpm = state.compressionRateCpm,
            recoilCompliancePct = state.recoilCompliancePct,
            handBalanceScorePct = state.handPlacementScore,
            avgFsr1N = (state.fsr1Newtons * 10f).roundToInt() / 10f,
            avgFsr2N = (state.fsr2Newtons * 10f).roundToInt() / 10f,
            avgFsr3N = (state.fsr3Newtons * 10f).roundToInt() / 10f,
            avgTiltDeg = (state.totalTiltDeg * 10f).roundToInt() / 10f,
            ahaOverallScore = state.valahaComplianceScore,
            notes = "Snapshot • ${state.coachingDirective.title}"
        )
        viewModelScope.launch {
            repository.saveSession(session)
        }
    }

    fun deleteSession(id: Int) {
        viewModelScope.launch {
            repository.deleteSession(id)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun updatePinoutConfig(newConfig: PinoutConfig) {
        _pinoutConfig.value = newConfig
    }

    fun clearTerminal() {
        _serialTerminalLines.value = listOf("[SYS] Serial terminal cleared.")
    }

    private fun appendTerminalLog(line: String) {
        val current = _serialTerminalLines.value
        val updated = if (current.size >= 60) current.drop(current.size - 59) + line else current + line
        _serialTerminalLines.value = updated
    }

    override fun onCleared() {
        super.onCleared()
        hardwareManager.disconnectAll()
        metronomeEngine.release()
    }
}
