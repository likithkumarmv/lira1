# LIRA ESP32 DevKit V1 Firmware

Firmware for the physical LIRA core node executing on the classic ESP32 DevKit V1 (ESP-WROOM-32).

## Hardware Pinout
- **TFT ILI9341**: CS (GPIO 5), DC (GPIO 2), RST (GPIO 4), SCLK (GPIO 18), MOSI (GPIO 23), MISO (GPIO 19), VCC/LED (3V3), GND (GND).
- **RC522 RFID**: SS/CS (GPIO 21), RST (GPIO 22), SCLK (GPIO 18 shared), MOSI (GPIO 23 shared), MISO (GPIO 19 shared), 3V3, GND.
- **PIR Sensor**: OUT -> GPIO 27.
- **HW-072 Vibration**: DO -> GPIO 34 (Input-only pin).
- **Relay Outputs** (Optional): GPIO 25, 26, 32, 33.

## Wi-Fi SoftAP
- **SSID:** LIRA-ESP32-SETUP
- **Passphrase:** lira12345
- **IP Address:** 192.168.4.1

## REST API Endpoints
- GET /api/v1/health
- GET /api/v1/status
- GET /api/v1/events
- POST /api/v1/arm
- POST /api/v1/acknowledge
