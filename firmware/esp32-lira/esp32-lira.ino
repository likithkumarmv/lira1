/*
 * LIRA — Linked Intelligence & Response Agent
 * ESP32 DevKit V1 Hardware Firmware
 * Physical Event Detection (PIR + Vibration) + RC522 + ILI9341 TFT + REST API
 */

#include <WiFi.h>
#include <WebServer.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ILI9341.h>
#include config.example.h

// Enums
enum IncidentState {
  STATE_DISARMED,
  STATE_ARMED_IDLE,
  STATE_INCIDENT_DETECTED,
  STATE_AWAITING_ACK,
  STATE_ACKNOWLEDGED,
  STATE_ESCALATION_PENDING
};

// Global Objects
WebServer server(80);
Adafruit_ILI9341 tft = Adafruit_ILI9341(TFT_CS, TFT_DC, TFT_RST);
MFRC522 rfid(RFID_SS_PIN, RFID_RST_PIN);

// State Variables
IncidentState currentState = STATE_DISARMED;
bool isArmed = false;
bool pirActive = false;
bool vibrationActive = false;
bool rfidReady = false;

unsigned long incidentStartTime = 0;
unsigned long ackDeadline = 0;
String currentIncidentId = ";
String currentIncidentType = ;
int incidentCounter = 0;

// Demo allowlist
String authorizedTags[] = {4A:5B:6C:7D, 9F:1E:2D:3C};
const int numAuthorizedTags = 2;

// Event ring buffer
struct EventItem {
 String id;
 String type;
 String state;
 unsigned long timestampMs;
};
const int EVENT_BUFFER_SIZE = 20;
EventItem eventBuffer[EVENT_BUFFER_SIZE];
int eventCount = 0;

// Function Prototypes
void setupHardware();
void setupWiFi();
void setupApi();
void updateSensors();
void checkRfid();
void updateDisplay();
void addEvent(String type, String state);

void setup() {
 Serial.begin(115200);
 delay(500);
 Serial.println([LIRA] Initializing ESP32 Core Node...);

 setupHardware();
 setupWiFi();
 setupApi();
 updateDisplay();

 Serial.println([LIRA] Ready. SoftAP active at 192.168.4.1);
}

void loop() {
 server.handleClient();
 updateSensors();
 checkRfid();

 // State machine timer for AWAITING_ACK
 if (currentState == STATE_AWAITING_ACK) {
 if (millis() >= ackDeadline) {
 Serial.println([LIRA] Acknowledgement timeout reached. Escalation pending.);
 currentState = STATE_ESCALATION_PENDING;
 addEvent(currentIncidentType, ESCALATION_PENDING);
 updateDisplay();
 }
 }

 static unsigned long lastDisplayUpdate = 0;
 if (millis() - lastDisplayUpdate > 1000) {
 lastDisplayUpdate = millis();
 updateDisplay();
 }
}

void setupHardware() {
 pinMode(PIR_PIN, INPUT);
 pinMode(VIBRATION_PIN, INPUT);

 // Relay outputs (optional, disabled/OFF by default)
 pinMode(RELAY_1_PIN, OUTPUT);
 pinMode(RELAY_2_PIN, OUTPUT);
 pinMode(RELAY_3_PIN, OUTPUT);
 pinMode(RELAY_4_PIN, OUTPUT);
 digitalWrite(RELAY_1_PIN, HIGH);
 digitalWrite(RELAY_2_PIN, HIGH);
 digitalWrite(RELAY_3_PIN, HIGH);
 digitalWrite(RELAY_4_PIN, HIGH);

 // Shared SPI bus setup: ensure CS pins high before init
 pinMode(TFT_CS, OUTPUT);
 pinMode(RFID_SS_PIN, OUTPUT);
 digitalWrite(TFT_CS, HIGH);
 digitalWrite(RFID_SS_PIN, HIGH);

 SPI.begin(TFT_SCLK, TFT_MISO, TFT_MOSI);

 // Init TFT
 digitalWrite(TFT_CS, LOW);
 tft.begin();
 tft.setRotation(1);
 tft.fillScreen(ILI9341_BLACK);
 digitalWrite(TFT_CS, HIGH);

 // Init RC522
 digitalWrite(RFID_SS_PIN, LOW);
 rfid.PCD_Init();
 byte v = rfid.PCD_ReadRegister(MFRC522::PCD_Register::VersionReg);
 rfidReady = (v != 0x00 && v != 0xFF);
 digitalWrite(RFID_SS_PIN, HIGH);

 Serial.printf([LIRA] RC522 version 0x%02X: %s\n, v, rfidReady ? READY : NOT DETECTED);
}

void setupWiFi() {
 WiFi.mode(WIFI_AP);
 IPAddress local_ip(192, 168, 4, 1);
 IPAddress gateway(192, 168, 4, 1);
 IPAddress subnet(255, 255, 255, 0);
 WiFi.softAPConfig(local_ip, gateway, subnet);
 WiFi.softAP(AP_SSID, AP_PASS);
}

void setupApi() {
 server.on(/api/v1/health, HTTP_GET, []() {
 String json = {"schemaVersion":1,"deviceId":" DEVICE_ID ","firmwareVersion":" FIRMWARE_VERSION ","uptimeSeconds":;
 json += String(millis() / 1000);
 json += ,"ipAddress":"192.168.4.1","rc522Ready":;
 json += rfidReady ? true : false;
 json += ,"armed":;
 json += isArmed ? true : false;
 json += };
 server.send(200, application/json, json);
 });

 server.on(/api/v1/status, HTTP_GET, []() {
 String stateStr = DISARMED;
 if (currentState == STATE_ARMED_IDLE) stateStr = ARMED_IDLE;
 else if (currentState == STATE_INCIDENT_DETECTED) stateStr = INCIDENT_DETECTED;
 else if (currentState == STATE_AWAITING_ACK) stateStr = AWAITING_ACK;
 else if (currentState == STATE_ACKNOWLEDGED) stateStr = ACKNOWLEDGED;
 else if (currentState == STATE_ESCALATION_PENDING) stateStr = ESCALATION_PENDING;

 String json = {"schemaVersion":1,"deviceId":" DEVICE_ID ","timestampUptimeMs":;
 json += String(millis());
 json += ,"armed":;
 json += isArmed ? true : false;
 json += ,"state":" + stateStr + ","sensors":{"pir":{"configured":true,"active":;
 json += pirActive ? true : false;
 json += },"vibration":{"configured":true,"active":;
 json += vibrationActive ? true : false;
 json += },"rc522":{"configured":true,"ready":;
 json += rfidReady ? true : false;
 json += },"relay":{"configured":false}};

 if (currentIncidentId != ) {
 json += ,"currentIncident":{"incidentId":" + currentIncidentId + ","type":" + currentIncidentType + ","state":" + stateStr + ","startedUptimeMs":;
 json += String(incidentStartTime);
 json += ,"ackDeadlineUptimeMs":;
 json += String(ackDeadline);
 json += };
 }

 json += ,"lastEventId":"evt-;
 json += String(eventCount);
 json += "};

 server.send(200, application/json, json);
 });

 server.on(/api/v1/arm, HTTP_POST, []() {
 if (server.hasArg(plain)) {
 String body = server.arg(plain);
 bool armRequest = body.indexOf("armed":true) >= 0;
 isArmed = armRequest;
 if (isArmed) {
 currentState = STATE_ARMED_IDLE;
 addEvent(system, ARMED);
 } else {
 currentState = STATE_DISARMED;
 currentIncidentId = ;
 addEvent(system, DISARMED);
 }
 updateDisplay();

 String resp = {"armed":;
 resp += isArmed ? true : false;
 resp += ,"state":";
 resp += isArmed ? ARMED_IDLE : DISARMED;
 resp += "};
 server.send(200, application/json, resp);
 return;
 }
 server.send(400, application/json, {"code":"INVALID_REQUEST"});
 });

 server.on(/api/v1/acknowledge, HTTP_POST, []() {
 if (currentState == STATE_AWAITING_ACK || currentState == STATE_ESCALATION_PENDING) {
 currentState = STATE_ACKNOWLEDGED;
 addEvent(currentIncidentType, ACKNOWLEDGED);
 updateDisplay();
 server.send(200, application/json, {"success":true,"state":"ACKNOWLEDGED"});
 return;
 }
 server.send(400, application/json, {"code":"NO_PENDING_INCIDENT"});
 });

 server.on(/api/v1/events, HTTP_GET, []() {
 String json = [;
 for (int i = 0; i < eventCount; i++) {
 if (i > 0) json += ,;
 json += {"incidentId":" + eventBuffer[i].id + ","type":" + eventBuffer[i].type + ","state":" + eventBuffer[i].state + ","startedUptimeMs":;
 json += String(eventBuffer[i].timestampMs);
 json += };
 }
 json += ];
 server.send(200, application/json, json);
 });

 server.begin();
}

void updateSensors() {
 pirActive = (digitalRead(PIR_PIN) == HIGH);
 vibrationActive = (digitalRead(VIBRATION_PIN) == HIGH);

 if (isArmed && (currentState == STATE_ARMED_IDLE || currentState == STATE_ACKNOWLEDGED)) {
 if (pirActive || vibrationActive) {
 currentState = STATE_AWAITING_ACK;
 incidentCounter++;
 currentIncidentId = inc- + String(incidentCounter);
 currentIncidentType = (pirActive && vibrationActive) ? motion_and_vibration : (pirActive ? motion : vibration);
 incidentStartTime = millis();
 ackDeadline = incidentStartTime + DEMO_ACK_TIMEOUT_MS;

 addEvent(currentIncidentType, INCIDENT_DETECTED);
 updateDisplay();
 Serial.printf([LIRA] Physical event: %s -> AWAITING_ACK\n, currentIncidentType.c_str());
 }
 }
}

void checkRfid() {
 digitalWrite(RFID_SS_PIN, LOW);
 if (!rfid.PICC_IsNewCardPresent() || !rfid.PICC_ReadCardSerial()) {
 digitalWrite(RFID_SS_PIN, HIGH);
 return;
 }

 String uidStr = ;
 for (byte i = 0; i < rfid.uid.size; i++) {
 if (i > 0) uidStr += : ;
 if (rfid.uid.uidByte[i] < 0x10) uidStr += 0;
 uidStr += String(rfid.uid.uidByte[i], HEX);
 }
 uidStr.toUpperCase();
 rfid.PICC_HaltA();
 digitalWrite(RFID_SS_PIN, HIGH);

 Serial.println([LIRA] Scanned RFID:  + uidStr);

 bool authorized = false;
 for (int i = 0; i < numAuthorizedTags; i++) {
 if (authorizedTags[i] == uidStr) {
 authorized = true;
 break;
 }
 }

 if (authorized) {
 if (currentState == STATE_AWAITING_ACK || currentState == STATE_ESCALATION_PENDING) {
 currentState = STATE_ACKNOWLEDGED;
 addEvent(rfid_ack, ACKNOWLEDGED);
 Serial.println([LIRA] Tag acknowledged incident.);
 } else {
 isArmed = !isArmed;
 currentState = isArmed ? STATE_ARMED_IDLE : STATE_DISARMED;
 addEvent(rfid_toggle, isArmed ? ARMED : DISARMED);
 Serial.println(isArmed ? [LIRA] Armed via tag. : [LIRA] Disarmed via tag.);
 }
 updateDisplay();
 } else {
 Serial.println([LIRA] Unknown tag:  + uidStr);
 addEvent(unknown_tag, UNAUTHORIZED);
 }
}

void updateDisplay() {
 digitalWrite(TFT_CS, LOW);
 tft.fillScreen(ILI9341_BLACK);

 // Title
 tft.setTextColor(ILI9341_CYAN);
 tft.setTextSize(2);
 tft.setCursor(10, 10);
 tft.print(LIRA Core Node);

 // Status line
 tft.setTextSize(1);
 tft.setCursor(10, 35);
 tft.setTextColor(ILI9341_WHITE);
 tft.print(AP: LIRA-ESP32-SETUP (192.168.4.1));

 // Guard State
 tft.setTextSize(2);
 tft.setCursor(10, 60);
 if (isArmed) {
 tft.setTextColor(ILI9341_GREEN);
 tft.print(GUARD: ARMED);
 } else {
 tft.setTextColor(ILI9341_DARKGREY);
 tft.print(GUARD: DISARMED);
 }

 // Incident & Countdown
 tft.setCursor(10, 95);
 tft.setTextSize(1);
 tft.setTextColor(ILI9341_YELLOW);
 if (currentState == STATE_AWAITING_ACK) {
 long remain = (ackDeadline - millis()) / 1000;
 if (remain < 0) remain = 0;
 tft.printf(EVENT: %s | ACK: %lds, currentIncidentType.c_str(), remain);
 } else if (currentState == STATE_ESCALATION_PENDING) {
 tft.setTextColor(ILI9341_RED);
 tft.printf(ESCALATING -> AI CALL DISPATCH);
 } else if (currentState == STATE_ACKNOWLEDGED) {
 tft.setTextColor(ILI9341_GREEN);
 tft.print(STATE: ACKNOWLEDGED (SAFE));
 } else {
 tft.setTextColor(ILI9341_WHITE);
 tft.print(STATE: ALL SENSORS QUIET);
 }

 // Sensor Telemetry Footer
 tft.setTextSize(1);
 tft.setTextColor(ILI9341_WHITE);
 tft.setCursor(10, 130);
 tft.printf(PIR:%s | VIB:%s | RC522:%s,
 pirActive ? TRIP : OK,
 vibrationActive ? TRIP : OK,
 rfidReady ? RDY : ERR);

 
  // Demo Mode Status Banner
  if (tftDemoMessage.length() > 0) {
    tft.setCursor(10, 165);
    tft.setTextSize(2);
    tft.setTextColor(ILI9341_YELLOW);
    tft.print("[SIM] ");
    tft.setTextColor(ILI9341_CYAN);
    tft.print(tftDemoMessage);
  }

  digitalWrite(TFT_CS, HIGH);
}

void addEvent(String type, String state) {
 if (eventCount < EVENT_BUFFER_SIZE) {
 eventBuffer[eventCount].id = evt- + String(eventCount + 1);
 eventBuffer[eventCount].type = type;
 eventBuffer[eventCount].state = state;
 eventBuffer[eventCount].timestampMs = millis();
 eventCount++;
 }
}
