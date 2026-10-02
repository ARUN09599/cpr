package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.example.model.BenchScenario
import com.example.model.ConnectionMode
import com.example.model.ConnectionStatus
import com.example.model.DiscoveredEspDevice
import com.example.model.RawGlovePacket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages real hardware links to the ESP32 Smart CPR Glove (BT Classic SPP, BLE GATT, Wi-Fi),
 * On-Device Phone IMU (SensorManager Accelerometer + Gyroscope), and Hazard-Free Bench Simulation.
 */
@SuppressLint("MissingPermission")
class Esp32HardwareManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStatusChange: (ConnectionStatus, String) -> Unit,
    private val onDevicesUpdated: (List<DiscoveredEspDevice>) -> Unit,
    private val onRawLineReceived: (String) -> Unit,
    private val onPacketReceived: (RawGlovePacket) -> Unit
) {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val NUS_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val discoveredDevices = LinkedHashMap<String, DiscoveredEspDevice>()

    // Active transport handles
    private var btSocket: BluetoothSocket? = null
    private var btOutputStream: OutputStream? = null
    private var btReadJob: Job? = null

    private var activeGatt: BluetoothGatt? = null
    private var bleWriteCharacteristic: BluetoothGattCharacteristic? = null
    private var isBleScanning = false

    private val okHttpClient = OkHttpClient()
    private var activeWebSocket: WebSocket? = null
    private var wifiStreamJob: Job? = null

    private var sensorListener: SensorEventListener? = null
    private var simJob: Job? = null

    fun refreshPairedAndScanDevices(mode: ConnectionMode) {
        discoveredDevices.clear()
        if (bluetoothAdapter == null) {
            onStatusChange(ConnectionStatus.ERROR, "Bluetooth adapter unavailable on this device.")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            onStatusChange(ConnectionStatus.ERROR, "Bluetooth is turned OFF. Enable Bluetooth in settings.")
            return
        }

        try {
            val bonded = bluetoothAdapter.bondedDevices.orEmpty()
            for (device in bonded) {
                val addr = device.address ?: continue
                val name = device.name ?: "ESP32_CPR_Glove"
                discoveredDevices[addr] = DiscoveredEspDevice(
                    name = name,
                    address = addr,
                    rssi = null,
                    isBle = (mode == ConnectionMode.BLE_GATT),
                    isPaired = true
                )
            }
            onDevicesUpdated(discoveredDevices.values.toList())
        } catch (e: SecurityException) {
            onStatusChange(ConnectionStatus.ERROR, "Bluetooth permission required to list paired ESP32 devices.")
            return
        }

        if (mode == ConnectionMode.BLE_GATT) {
            startBleScan()
        } else if (mode == ConnectionMode.BT_CLASSIC_SPP) {
            onStatusChange(
                ConnectionStatus.DISCONNECTED,
                if (discoveredDevices.isEmpty()) "Pair your ESP32 ('ESP32_CPR_Glove') in Bluetooth Settings or scan via BLE."
                else "Found ${discoveredDevices.size} paired Bluetooth device(s). Tap to connect."
            )
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val addr = device.address ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: "BLE Device (${addr.takeLast(5)})"
            discoveredDevices[addr] = DiscoveredEspDevice(
                name = name,
                address = addr,
                rssi = result.rssi,
                isBle = true,
                isPaired = device.bondState == BluetoothDevice.BOND_BONDED
            )
            onDevicesUpdated(discoveredDevices.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
            isBleScanning = false
            onStatusChange(ConnectionStatus.ERROR, "BLE scan failed (code $errorCode)")
        }
    }

    fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            onStatusChange(ConnectionStatus.ERROR, "BLE Scanner not available.")
            return
        }
        try {
            if (isBleScanning) {
                scanner.stopScan(bleScanCallback)
            }
            isBleScanning = true
            onStatusChange(ConnectionStatus.SCANNING, "Scanning for ESP32 BLE GATT peripherals...")
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, bleScanCallback)

            scope.launch {
                delay(8000L)
                stopBleScan()
            }
        } catch (e: Exception) {
            isBleScanning = false
            onStatusChange(ConnectionStatus.ERROR, "BLE Scan error: ${e.localizedMessage}")
        }
    }

    fun stopBleScan() {
        if (!isBleScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (_: Exception) {
        }
        isBleScanning = false
    }

    fun connectClassicSpp(address: String) {
        disconnectAll()
        val adapter = bluetoothAdapter ?: run {
            onStatusChange(ConnectionStatus.ERROR, "Bluetooth not supported.")
            return
        }
        onStatusChange(ConnectionStatus.CONNECTING, "Connecting to ESP32 SPP ($address)...")
        btReadJob = scope.launch(Dispatchers.IO) {
            try {
                val device = adapter.getRemoteDevice(address)
                val socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                } catch (_: Exception) {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                }
                btSocket = socket
                socket.connect()
                btOutputStream = socket.outputStream
                onStatusChange(ConnectionStatus.CONNECTED, "Connected to ${device.name ?: address} (SPP SerialBT)")

                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (isActive && socket.isConnected) {
                    val line = reader.readLine() ?: break
                    handleIncomingRawLine(line)
                }
                onStatusChange(ConnectionStatus.DISCONNECTED, "ESP32 SPP stream closed.")
            } catch (e: Exception) {
                onStatusChange(ConnectionStatus.ERROR, "SPP Connection failed: ${e.localizedMessage ?: "Socket closed"}")
            }
        }
    }

    fun connectBleGatt(address: String) {
        disconnectAll()
        stopBleScan()
        val adapter = bluetoothAdapter ?: run {
            onStatusChange(ConnectionStatus.ERROR, "Bluetooth not supported.")
            return
        }
        try {
            val device = adapter.getRemoteDevice(address)
            onStatusChange(ConnectionStatus.CONNECTING, "Connecting BLE GATT to ${device.name ?: address}...")
            activeGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                private val lineBuffer = StringBuilder()

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        onStatusChange(ConnectionStatus.CONNECTING, "Discovering ESP32 GATT Services...")
                        gatt.requestMtu(185)
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        onStatusChange(ConnectionStatus.DISCONNECTED, "BLE GATT Disconnected.")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        onStatusChange(ConnectionStatus.ERROR, "GATT Service discovery failed ($status)")
                        return
                    }
                    var subscribed = false
                    for (service in gatt.services) {
                        for (characteristic in service.characteristics) {
                            val props = characteristic.properties
                            if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                            ) {
                                bleWriteCharacteristic = characteristic
                            }
                            if (!subscribed && (
                                    (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                                        (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                    )
                            ) {
                                gatt.setCharacteristicNotification(characteristic, true)
                                val descriptor = characteristic.getDescriptor(CCCD_UUID)
                                if (descriptor != null) {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                                subscribed = true
                            }
                        }
                    }
                    if (subscribed) {
                        onStatusChange(
                            ConnectionStatus.CONNECTED,
                            "BLE GATT Live (${gatt.device.name ?: address})"
                        )
                    } else {
                        onStatusChange(
                            ConnectionStatus.ERROR,
                            "Connected to BLE device, but no Notify telemetry characteristic found."
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    @Suppress("DEPRECATION")
                    val bytes = characteristic.value ?: return
                    val chunk = String(bytes, Charsets.UTF_8)
                    processBleChunk(chunk)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    val chunk = String(value, Charsets.UTF_8)
                    processBleChunk(chunk)
                }

                private fun processBleChunk(chunk: String) {
                    synchronized(lineBuffer) {
                        lineBuffer.append(chunk)
                        var newlineIdx = lineBuffer.indexOf("\n")
                        while (newlineIdx >= 0) {
                            val line = lineBuffer.substring(0, newlineIdx).trim()
                            lineBuffer.delete(0, newlineIdx + 1)
                            if (line.isNotEmpty()) {
                                handleIncomingRawLine(line)
                            }
                            newlineIdx = lineBuffer.indexOf("\n")
                        }
                        // Also handle single-packet notifications without trailing newline
                        if (lineBuffer.length > 10 && !chunk.contains("\n")) {
                            val snapshot = lineBuffer.toString().trim()
                            if (snapshot.startsWith("{") && snapshot.endsWith("}") || snapshot.count { it == ',' } >= 5) {
                                lineBuffer.clear()
                                handleIncomingRawLine(snapshot)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            onStatusChange(ConnectionStatus.ERROR, "BLE GATT error: ${e.localizedMessage}")
        }
    }

    fun connectCloudBridge(channelIdInput: String) {
        disconnectAll()
        val channel = channelIdInput.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (channel.length < 4) {
            onStatusChange(ConnectionStatus.ERROR, "Enter a Channel ID with at least 4 characters (e.g. cpr_glove_2026).")
            return
        }
        val streamUrl = "https://ntfy.sh/$channel/raw"
        onStatusChange(ConnectionStatus.CONNECTING, "Subscribing to Cloud Bridge channel '$channel'...")
        wifiStreamJob = scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("Accept", "text/plain")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onStatusChange(ConnectionStatus.ERROR, "Cloud Bridge HTTP ${response.code}")
                        return@use
                    }
                    onStatusChange(
                        ConnectionStatus.CONNECTED,
                        "Cloud Bridge LIVE (Channel: $channel) — Waiting for ESP32 POSTs"
                    )
                    val source = response.body?.byteStream() ?: return@use
                    val reader = BufferedReader(InputStreamReader(source))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            handleIncomingRawLine(trimmed)
                        }
                    }
                }
                if (isActive) {
                    onStatusChange(ConnectionStatus.DISCONNECTED, "Cloud Bridge stream closed.")
                }
            } catch (e: Exception) {
                if (isActive) {
                    onStatusChange(ConnectionStatus.ERROR, "Cloud Bridge error: ${e.localizedMessage}")
                }
            }
        }
    }

    fun connectWifiEndpoint(endpointInput: String) {
        disconnectAll()
        val clean = endpointInput.trim()
        if (clean.isEmpty()) {
            onStatusChange(ConnectionStatus.ERROR, "Enter a valid ESP32 IP or WebSocket URL.")
            return
        }

        if (clean.startsWith("ws://") || clean.startsWith("wss://")) {
            onStatusChange(ConnectionStatus.CONNECTING, "Connecting WebSocket to $clean...")
            val request = Request.Builder().url(clean).build()
            activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onStatusChange(ConnectionStatus.CONNECTED, "ESP32 WebSocket Connected ($clean)")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    text.lines().forEach { line ->
                        if (line.isNotBlank()) handleIncomingRawLine(line)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onStatusChange(ConnectionStatus.ERROR, "WebSocket failed: ${t.localizedMessage}")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onStatusChange(ConnectionStatus.DISCONNECTED, "WebSocket closed.")
                }
            })
        } else if (clean.startsWith("http://") || clean.startsWith("https://")) {
            onStatusChange(ConnectionStatus.CONNECTING, "Connecting HTTP Polling to $clean...")
            wifiStreamJob = scope.launch(Dispatchers.IO) {
                var connectedOnce = false
                while (isActive) {
                    try {
                        val req = Request.Builder().url(clean).build()
                        okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                if (!connectedOnce) {
                                    connectedOnce = true
                                    onStatusChange(ConnectionStatus.CONNECTED, "ESP32 HTTP Telemetry Active ($clean)")
                                }
                                val body = resp.body?.string().orEmpty()
                                body.lines().forEach { line ->
                                    if (line.isNotBlank()) handleIncomingRawLine(line)
                                }
                            } else {
                                onStatusChange(ConnectionStatus.ERROR, "HTTP ${resp.code} from ESP32")
                            }
                        }
                    } catch (e: Exception) {
                        onStatusChange(ConnectionStatus.ERROR, "HTTP unreachable: ${e.localizedMessage}")
                        break
                    }
                    delay(40L) // 25 Hz HTTP polling
                }
            }
        } else {
            // Raw TCP Socket e.g. 192.168.4.1:8080
            val hostPort = clean.split(":")
            val host = hostPort[0]
            val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 8080
            onStatusChange(ConnectionStatus.CONNECTING, "Connecting TCP Socket to $host:$port...")
            wifiStreamJob = scope.launch(Dispatchers.IO) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 4000)
                        onStatusChange(ConnectionStatus.CONNECTED, "ESP32 TCP Socket Connected ($host:$port)")
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        while (isActive && socket.isConnected) {
                            val line = reader.readLine() ?: break
                            handleIncomingRawLine(line)
                        }
                    }
                    onStatusChange(ConnectionStatus.DISCONNECTED, "ESP32 TCP Socket closed.")
                } catch (e: Exception) {
                    onStatusChange(ConnectionStatus.ERROR, "TCP connection failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun startPhoneImuMode() {
        disconnectAll()
        val sm = sensorManager ?: run {
            onStatusChange(ConnectionStatus.ERROR, "Android SensorManager unavailable.")
            return
        }
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accel == null) {
            onStatusChange(ConnectionStatus.ERROR, "No hardware accelerometer found on this phone.")
            return
        }

        var lastAx = 0f
        var lastAy = 0f
        var lastAz = 1f
        var lastGx = 0f
        var lastGy = 0f
        var lastGz = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val ev = event ?: return
                if (ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    // Convert m/s^2 to g (9.80665)
                    lastAx = ev.values[0] / 9.80665f
                    lastAy = ev.values[1] / 9.80665f
                    lastAz = ev.values[2] / 9.80665f

                    // Derive synthetic FSR pressure proportional to vertical acceleration pulse so
                    // the user can test full kinematics with phone strapped to wrist
                    val dynG = kotlin.math.abs(lastAz - 1.0f)
                    val estimatedAdc = (dynG * 2200f).roundToInt().coerceIn(0, 4095)
                    val line = String.format(
                        Locale.US,
                        "{\"fsr1\":%d,\"fsr2\":%d,\"fsr3\":%d,\"ax\":%.2f,\"ay\":%.2f,\"az\":%.2f,\"gx\":%.1f,\"gy\":%.1f,\"gz\":%.1f}",
                        estimatedAdc,
                        (estimatedAdc * 0.35f).roundToInt(),
                        (estimatedAdc * 0.32f).roundToInt(),
                        lastAx,
                        lastAy,
                        lastAz,
                        lastGx,
                        lastGy,
                        lastGz
                    )
                    handleIncomingRawLine(line)
                } else if (ev.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    // Convert rad/s to deg/s
                    lastGx = Math.toDegrees(ev.values[0].toDouble()).toFloat()
                    lastGy = Math.toDegrees(ev.values[1].toDouble()).toFloat()
                    lastGz = Math.toDegrees(ev.values[2].toDouble()).toFloat()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorListener = listener
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        if (gyro != null) {
            sm.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
        onStatusChange(
            ConnectionStatus.CONNECTED,
            "On-Device Phone IMU Active (Real Accelerometer + Gyroscope)"
        )
    }

    fun startSafetyBenchSimulation(scenarioProvider: () -> BenchScenario) {
        disconnectAll()
        onStatusChange(
            ConnectionStatus.CONNECTED,
            "Safety Simulation Mode Active (Hazard-Free Bench Stream)"
        )
        simJob = scope.launch(Dispatchers.Default) {
            val startMs = System.currentTimeMillis()
            while (isActive) {
                val scenario = scenarioProvider()
                val elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0
                val freqHz = scenario.targetRateCpm / 60.0
                val phase = (elapsedSec * freqHz * 2.0 * PI)

                // Half-wave rectified compression pulse
                val rawWave = max(0.0, sin(phase))
                val pulse = rawWave * rawWave

                val baseLeaningAdc = if (scenario.incompleteRecoil) 620 else 20
                val peakFsr1Adc = ((scenario.targetDepthCm / 5.5f) * 3100f).roundToInt().coerceIn(800, 4050)
                val fsr1 = (baseLeaningAdc + pulse * (peakFsr1Adc - baseLeaningAdc) + Random.nextInt(-18, 18))
                    .roundToInt().coerceIn(0, 4095)

                val lateralScale = if (scenario.fsrBalanceBias > 0.5f) 0.95f else 0.34f
                val fsr2 = (baseLeaningAdc * 0.6 + pulse * peakFsr1Adc * lateralScale + Random.nextInt(-15, 15))
                    .roundToInt().coerceIn(0, 4095)
                val fsr3 = (baseLeaningAdc * 0.6 + pulse * peakFsr1Adc * (lateralScale * 0.85f) + Random.nextInt(-15, 15))
                    .roundToInt().coerceIn(0, 4095)

                val tiltRad = if (scenario.fsrBalanceBias > 0.5f) 0.33f else 0.05f
                val ax = (sin(tiltRad.toDouble()) + cos(phase) * 0.06).toFloat()
                val ay = (sin(tiltRad.toDouble() * 0.7) + sin(phase) * 0.05).toFloat()
                val az = (1.0 + pulse * (scenario.targetDepthCm * 0.32)).toFloat()

                val gx = (cos(phase) * 14.0).toFloat()
                val gy = (sin(phase) * 9.0).toFloat()
                val gz = (cos(phase * 0.5) * 2.5).toFloat()

                val currentInstDepth = if (scenario.incompleteRecoil) {
                    (0.95f + pulse.toFloat() * (scenario.targetDepthCm - 0.95f))
                } else {
                    (pulse.toFloat() * scenario.targetDepthCm)
                }

                val jsonLine = String.format(
                    Locale.US,
                    "{\"fsr1\":%d,\"fsr2\":%d,\"fsr3\":%d,\"ax\":%.2f,\"ay\":%.2f,\"az\":%.2f,\"gx\":%.1f,\"gy\":%.1f,\"gz\":%.1f,\"depth\":%.2f,\"rate\":%d}",
                    fsr1, fsr2, fsr3, ax, ay, az, gx, gy, gz, currentInstDepth, scenario.targetRateCpm
                )
                handleIncomingRawLine(jsonLine)
                delay(33L) // ~30 Hz telemetry frame rate
            }
        }
    }

    fun sendCommandToEsp32(command: String): Boolean {
        val payload = if (command.endsWith("\n")) command else "$command\n"
        return try {
            when {
                btOutputStream != null -> {
                    scope.launch(Dispatchers.IO) {
                        try {
                            btOutputStream?.write(payload.toByteArray(Charsets.UTF_8))
                            btOutputStream?.flush()
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                activeGatt != null && bleWriteCharacteristic != null -> {
                    val char = bleWriteCharacteristic!!
                    @Suppress("DEPRECATION")
                    char.value = payload.toByteArray(Charsets.UTF_8)
                    @Suppress("DEPRECATION")
                    activeGatt?.writeCharacteristic(char) ?: false
                }
                activeWebSocket != null -> {
                    activeWebSocket?.send(payload.trim()) ?: false
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun handleIncomingRawLine(line: String) {
        onRawLineReceived(line)
        val packet = CprPacketParser.parseLine(line)
        if (packet != null) {
            onPacketReceived(packet)
        }
    }

    fun disconnectAll() {
        stopBleScan()

        btReadJob?.cancel()
        btReadJob = null
        try {
            btOutputStream?.close()
        } catch (_: Exception) {
        }
        btOutputStream = null
        try {
            btSocket?.close()
        } catch (_: Exception) {
        }
        btSocket = null

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (_: Exception) {
        }
        activeGatt = null
        bleWriteCharacteristic = null

        try {
            activeWebSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {
        }
        activeWebSocket = null

        wifiStreamJob?.cancel()
        wifiStreamJob = null

        sensorListener?.let {
            sensorManager?.unregisterListener(it)
        }
        sensorListener = null

        simJob?.cancel()
        simJob = null
    }
}
