#ifndef LIRA_CONFIG_H
#define LIRA_CONFIG_H

// Local Wi-Fi SoftAP Configuration
#define AP_SSID  LIRA-ESP32-SETUP
#define AP_PASS lira12345

// Device Metadata
#define DEVICE_ID lira-esp32-01
#define FIRMWARE_VERSION 0.1.0

// Pin Definitions - TFT ILI9341 (Hardware SPI)
#define TFT_CS   5
#define TFT_DC   2
#define TFT_RST  4
#define TFT_MOSI 23
#define TFT_SCLK 18
#define TFT_MISO 19

// Pin Definitions - RC522 RFID (Shared SPI, Dedicated CS/RST)
#define RFID_SS_PIN   21
#define RFID_RST_PIN  22

// Pin Definitions - Sensors
#define PIR_PIN       27
#define VIBRATION_PIN 34  // Input-only on Classic ESP32

// Optional Relay Pins (Disabled by default)
#define RELAY_1_PIN   25
#define RELAY_2_PIN   26
#define RELAY_3_PIN   32
#define RELAY_4_PIN   33

// Timing
#define DEMO_ACK_TIMEOUT_MS 20000
#define SENSOR_DEBOUNCE_MS  500

#endif // LIRA_CONFIG_H
