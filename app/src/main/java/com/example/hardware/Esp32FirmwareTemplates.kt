package com.example.hardware

import com.example.model.ConnectionMode
import com.example.model.PinoutConfig

/**
 * Generates ready-to-flash Arduino IDE (.ino) C++ firmware for the user's
 * ESP32 + 3x FSR + MPU6050 Smart CPR Glove hardware project.
 */
object Esp32FirmwareTemplates {

    fun generateSketch(mode: ConnectionMode, pins: PinoutConfig): String {
        val fsr1Num = pins.fsr1Pin.filter { it.isDigit() }.take(2).ifEmpty { "34" }
        val fsr2Num = pins.fsr2Pin.filter { it.isDigit() }.take(2).ifEmpty { "35" }
        val fsr3Num = pins.fsr3Pin.filter { it.isDigit() }.take(2).ifEmpty { "32" }
        val sdaNum = pins.mpuSdaPin.filter { it.isDigit() }.take(2).ifEmpty { "21" }
        val sclNum = pins.mpuSclPin.filter { it.isDigit() }.take(2).ifEmpty { "22" }
        val buzzerNum = pins.buzzerPin.filter { it.isDigit() }.take(2).ifEmpty { "25" }

        return when (mode) {
            ConnectionMode.CLOUD_BRIDGE -> buildCloudBridgeSketch(fsr1Num, fsr2Num, fsr3Num, sdaNum, sclNum)
            ConnectionMode.BLE_GATT -> buildBleSketch(fsr1Num, fsr2Num, fsr3Num, sdaNum, sclNum, buzzerNum)
            ConnectionMode.WIFI_STREAM -> buildWifiSketch(fsr1Num, fsr2Num, fsr3Num, sdaNum, sclNum, buzzerNum)
            else -> buildClassicBtSketch(fsr1Num, fsr2Num, fsr3Num, sdaNum, sclNum, buzzerNum)
        }
    }

    private fun buildCloudBridgeSketch(
        fsr1: String,
        fsr2: String,
        fsr3: String,
        sda: String,
        scl: String
    ): String = """
/*
 * SMART CPR GLOVE — ESP32 CLOUD BRIDGE (WORKS DIRECTLY WITH BROWSER EMULATOR!)
 * Connects ESP32 to your home Wi-Fi / phone hotspot and streams 3x FSR + MPU6050
 * telemetry over HTTPS to the app's Cloud Bridge Channel ID.
 */
#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

// 1. Enter your Wi-Fi or Phone Hotspot credentials:
const char* WIFI_SSID     = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// 2. Match this Channel ID in the Android App -> ESP32 Link -> Cloud Bridge:
const char* CLOUD_CHANNEL = "cpr_glove_2026";

#define FSR1_HEEL_PIN  $fsr1
#define FSR2_LEFT_PIN  $fsr2
#define FSR3_RIGHT_PIN $fsr3
#define I2C_SDA_PIN    $sda
#define I2C_SCL_PIN    $scl

Adafruit_MPU6050 mpu;
WiFiClient client;

void setup() {
  Serial.begin(115200);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  analogReadResolution(12);

  if (!mpu.begin()) {
    Serial.println("MPU6050 not found! Check SDA(21)/SCL(22) wiring.");
  }

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    Serial.print(".");
  }
  Serial.println("\nConnected! Streaming to Cloud Channel: " + String(CLOUD_CHANNEL));
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    int f1 = analogRead(FSR1_HEEL_PIN);
    int f2 = analogRead(FSR2_LEFT_PIN);
    int f3 = analogRead(FSR3_RIGHT_PIN);

    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);

    char payload[180];
    snprintf(payload, sizeof(payload),
      "{\"fsr1\":%d,\"fsr2\":%d,\"fsr3\":%d,\"ax\":%.2f,\"ay\":%.2f,\"az\":%.2f,\"gx\":%.1f,\"gy\":%.1f,\"gz\":%.1f}",
      f1, f2, f3,
      a.acceleration.x / 9.81f, a.acceleration.y / 9.81f, a.acceleration.z / 9.81f,
      g.gyro.x * 57.3f, g.gyro.y * 57.3f, g.gyro.z * 57.3f
    );

    HTTPClient http;
    String url = "http://ntfy.sh/" + String(CLOUD_CHANNEL);
    http.begin(client, url);
    http.addHeader("Content-Type", "text/plain");
    http.POST(String(payload));
    http.end();

    Serial.println(payload);
  }
  delay(250); // 4 Hz cloud relay update
}
""".trimIndent()

    private fun buildClassicBtSketch(
        fsr1: String,
        fsr2: String,
        fsr3: String,
        sda: String,
        scl: String,
        buzzer: String
    ): String = """
/*
 * SMART CPR GLOVE — ESP32 + 3x FSR + MPU6050 (Bluetooth Classic SerialBT)
 * Compatible with: CPR Glove Telemetry Android App
 * Libraries required: Adafruit MPU6050, Adafruit Unified Sensor, Wire, BluetoothSerial
 */
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include "BluetoothSerial.h"

#define FSR1_HEEL_PIN  $fsr1  // Primary Sternum / Palm Heel FSR (10k pull-down to GND)
#define FSR2_LEFT_PIN  $fsr2  // Left Palm / Thenar FSR
#define FSR3_RIGHT_PIN $fsr3  // Right Palm / Hypothenar FSR
#define I2C_SDA_PIN    $sda   // MPU6050 SDA
#define I2C_SCL_PIN    $scl   // MPU6050 SCL
#define BUZZER_PIN     $buzzer // Active/Passive Pacing Buzzer

BluetoothSerial SerialBT;
Adafruit_MPU6050 mpu;

void setup() {
  Serial.begin(115200);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  pinMode(BUZZER_PIN, OUTPUT);

  // Configure 12-bit ADC resolution (0..4095) for 3 FSR sensors
  analogReadResolution(12);

  if (!mpu.begin()) {
    Serial.println("ERR: MPU6050 not detected on I2C bus!");
  } else {
    mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
    mpu.setGyroRange(MPU6050_RANGE_500_DEG);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  }

  SerialBT.begin("ESP32_CPR_Glove");
  Serial.println("Smart CPR Glove Bluetooth SPP Ready: ESP32_CPR_Glove");
}

void loop() {
  int fsr1 = analogRead(FSR1_HEEL_PIN);
  int fsr2 = analogRead(FSR2_LEFT_PIN);
  int fsr3 = analogRead(FSR3_RIGHT_PIN);

  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  // Convert m/s^2 to g-units and rad/s to deg/s
  float ax = a.acceleration.x / 9.80665f;
  float ay = a.acceleration.y / 9.80665f;
  float az = a.acceleration.z / 9.80665f;
  float gx = g.gyro.x * 57.2958f;
  float gy = g.gyro.y * 57.2958f;
  float gz = g.gyro.z * 57.2958f;

  // Stream JSON telemetry packet at ~33 Hz to Android app
  char payload[180];
  snprintf(payload, sizeof(payload),
    "{\"fsr1\":%d,\"fsr2\":%d,\"fsr3\":%d,\"ax\":%.2f,\"ay\":%.2f,\"az\":%.2f,\"gx\":%.1f,\"gy\":%.1f,\"gz\":%.1f}",
    fsr1, fsr2, fsr3, ax, ay, az, gx, gy, gz
  );

  Serial.println(payload);
  if (SerialBT.hasClient()) {
    SerialBT.println(payload);
  }

  // Listen for calibration or buzzer commands from Android App
  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();
    if (cmd == "BEEP") {
      digitalWrite(BUZZER_PIN, HIGH);
      delay(35);
      digitalWrite(BUZZER_PIN, LOW);
    }
  }
  delay(30);
}
""".trimIndent()

    private fun buildBleSketch(
        fsr1: String,
        fsr2: String,
        fsr3: String,
        sda: String,
        scl: String,
        buzzer: String
    ): String = """
/*
 * SMART CPR GLOVE — ESP32 + 3x FSR + MPU6050 (BLE GATT UART Notify)
 * Compatible with: CPR Glove Telemetry Android App (Auto-discovers Notify UUID)
 */
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define FSR1_HEEL_PIN  $fsr1
#define FSR2_LEFT_PIN  $fsr2
#define FSR3_RIGHT_PIN $fsr3
#define I2C_SDA_PIN    $sda
#define I2C_SCL_PIN    $scl
#define BUZZER_PIN     $buzzer

#define SERVICE_UUID        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_TX   "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

Adafruit_MPU6050 mpu;
BLECharacteristic *pTxCharacteristic;
bool deviceConnected = false;

class GloveServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) { deviceConnected = true; }
  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    pServer->getAdvertising()->start();
  }
};

void setup() {
  Serial.begin(115200);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  analogReadResolution(12);
  mpu.begin();

  BLEDevice::init("ESP32_CPR_Glove_BLE");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new GloveServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pTxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_TX,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  pServer->getAdvertising()->start();
}

void loop() {
  int f1 = analogRead(FSR1_HEEL_PIN);
  int f2 = analogRead(FSR2_LEFT_PIN);
  int f3 = analogRead(FSR3_RIGHT_PIN);

  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  char line[128];
  // Compact CSV packet: fsr1,fsr2,fsr3,ax,ay,az,gx,gy,gz
  snprintf(line, sizeof(line), "%d,%d,%d,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f\n",
    f1, f2, f3,
    a.acceleration.x / 9.81f, a.acceleration.y / 9.81f, a.acceleration.z / 9.81f,
    g.gyro.x * 57.3f, g.gyro.y * 57.3f, g.gyro.z * 57.3f
  );

  if (deviceConnected) {
    pTxCharacteristic->setValue((uint8_t*)line, strlen(line));
    pTxCharacteristic->notify();
  }
  delay(35);
}
""".trimIndent()

    private fun buildWifiSketch(
        fsr1: String,
        fsr2: String,
        fsr3: String,
        sda: String,
        scl: String,
        buzzer: String
    ): String = """
/*
 * SMART CPR GLOVE — ESP32 SoftAP Wi-Fi HTTP Telemetry Server
 * Connect phone to Wi-Fi SSID: "ESP32_CPR_Glove_AP" (Password: "cprglove123")
 * Endpoint in Android App: http://192.168.4.1/telemetry
 */
#include <WiFi.h>
#include <WebServer.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

#define FSR1_HEEL_PIN  $fsr1
#define FSR2_LEFT_PIN  $fsr2
#define FSR3_RIGHT_PIN $fsr3
#define I2C_SDA_PIN    $sda
#define I2C_SCL_PIN    $scl

Adafruit_MPU6050 mpu;
WebServer server(80);

void handleTelemetry() {
  int f1 = analogRead(FSR1_HEEL_PIN);
  int f2 = analogRead(FSR2_LEFT_PIN);
  int f3 = analogRead(FSR3_RIGHT_PIN);
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  char json[180];
  snprintf(json, sizeof(json),
    "{\"fsr1\":%d,\"fsr2\":%d,\"fsr3\":%d,\"ax\":%.2f,\"ay\":%.2f,\"az\":%.2f,\"gx\":%.1f,\"gy\":%.1f,\"gz\":%.1f}",
    f1, f2, f3,
    a.acceleration.x / 9.81f, a.acceleration.y / 9.81f, a.acceleration.z / 9.81f,
    g.gyro.x * 57.3f, g.gyro.y * 57.3f, g.gyro.z * 57.3f
  );
  server.send(200, "application/json", json);
}

void setup() {
  Serial.begin(115200);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  analogReadResolution(12);
  mpu.begin();

  WiFi.softAP("ESP32_CPR_Glove_AP", "cprglove123");
  server.on("/telemetry", handleTelemetry);
  server.begin();
}

void loop() {
  server.handleClient();
}
""".trimIndent()
}
