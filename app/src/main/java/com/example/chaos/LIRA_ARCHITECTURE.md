# LIRA — Linked Intelligence & Response Agent

> **Project:** LIRA  
> **Android app:** LIRA  
> **Expansion:** **L**inked **I**ntelligence & **R**esponse **A**gent  
> **Tagline:** *From physical events to verified action.*  
> **Challenge:** Jarvis / Personal AI Agent  
> **Core:** ESP32 IoT + Android + Gemini + the existing AI calling-agent repository

---

## 1. Purpose and product definition

LIRA is an IoT-aware personal/workspace agent that observes physical events, checks authorization, runs a local response workflow, verifies whether the event was acknowledged, and escalates unresolved incidents through an existing AI voice-calling agent. Gemini coordinates allowed software tools and explains outcomes using recorded evidence.

LIRA must demonstrate a complete workflow rather than a collection of unrelated sensors:

1. A user arms the device with the RC522 RFID reader or, optionally, the phone's NFC reader.
2. The ESP32 detects motion or vibration using the sensors that are actually connected.
3. The ESP32 records an incident and displays the state on the existing TFT.
4. The user can acknowledge the event with an authorized tag or in the app.
5. If the incident remains unresolved past a configurable demo timeout, LIRA creates an escalation job.
6. The backend invokes the **already-built AI calling-agent repository** and contacts a pre-approved supervisor/technician.
7. LIRA records the real call outcome, transcript/summary, and whether a person acknowledged the incident.
8. If a call fails, LIRA marks the step failed and offers a bounded retry/fallback; it must never pretend that a call or task succeeded.

### Non-negotiable implementation rules

- Build a **native Android app**, not a browser-only dashboard, as the primary client.
- Use the user's **classic ESP32 DevKit V1**, not ESP32-C6.
- Reuse the supplied TFT-only pinout. **Do not add touchscreen wiring or touchscreen logic.**
- Connect the phone to the ESP32 over local Wi-Fi for device traffic, while using mobile/cellular data for cloud/backend traffic. Implement network-specific routing; do not globally bind the entire app to Wi-Fi.
- Integrate the existing calling-agent repository. Inspect it first and preserve its working call/telephony code, dependency manager, and configuration style.
- Never put Gemini or telephony API keys in the Android app or ESP32 firmware.
- Use only existing hardware listed in Section 4. Do not require purchased components, extra sensors, a second microcontroller, or a custom PCB.
- The DHT22 is not available and must not appear in code, pin maps, UI requirements, or the BOM.
- The relay is a switch, **not a voltage reducer**. Relay usage is optional and must not control mains equipment in the demo.
- Local sensor/state-machine handling must continue if Gemini, the backend, cellular data, or the Android app is unavailable.
- Never represent this prototype as a certified security, fire, gas, or life-safety system.

---

## 2. Primary scope versus optional scope

### MVP — build this first

| Feature | Hardware/software | Required for acceptance? |
|---|---|---:|
| Local physical-event detection | PIR HC-SR501 + HW-072 vibration sensor | Yes |
| Physical arm/disarm/acknowledgement | RC522 reader + supplied RFID tag | Yes |
| Local state and workflow display | Existing ILI9341 TFT display | Yes |
| Local Wi-Fi device API | Classic ESP32 DevKit V1 | Yes |
| Android dashboard and incident history | Native Android app | Yes |
| Wi-Fi/cellular split networking | Android `ConnectivityManager` + per-network connections | Yes |
| Gemini task orchestration | Backend only | Yes |
| Existing AI calling-agent integration | Existing repository + small adapter | Yes |
| Failure detection and truthful status | ESP32/backend/app state machine | Yes |
| Phone NFC feature switch | Optional Android NFC reader when supported | Yes as an app option; NFC-capable phone is not required |

### Optional — only after the full MVP works

| Feature | Existing component | Rules |
|---|---|---|
| Relay output control | Existing 4-channel relay module | Optional. Demonstrate channel state only unless a suitable, already-owned low-voltage load is available. Never use mains for the hackathon demo. |
| Distance event | HC-SR04 ultrasonic sensor | Optional. Echo is typically 5 V; use a suitable divider/level shifter before the ESP32 GPIO. Do not wire Echo directly to an ESP32 input. |
| Obstacle event | IR obstacle sensor | Optional; keep it out of the MVP if extra wiring slows the main flow. |
| Sound event | KY-037 sound sensor | Optional; noisy triggers need filtering and add little to the first demo. |
| Motion/orientation context | MPU6050 | Optional; not needed to verify PIR/vibration events. |
| Timestamp support | DS3231 RTC | Optional; use ESP32 uptime and backend timestamps for the MVP. |
| Gas/smoke sensor display | MQ-2 | Do not use it as a real safety trigger or emergency shutoff. It requires suitable warm-up/calibration and is not certified safety equipment. If shown at all, label it experimental and non-safety-critical. |
| Arduino UNO | Existing board | Not needed; do not introduce a second controller. |

The app may expose optional modules as disabled cards/settings, but do not show a sensor as connected unless firmware reports it as configured and its readings are valid.

---

## 3. System architecture

```text
                         LIRA ANDROID APP
                  Native Kotlin + Jetpack Compose
        ┌─────────────────────────────────────────────┐
        │ Dashboard / Incidents / NFC / Call outcomes │
        │ Wi-Fi connection manager (ESP32-only)       │
        │ Cellular connection manager (cloud-only)    │
        └───────────────┬──────────────────┬──────────┘
                        │                  │
           Local Wi-Fi  │                  │ Cellular data
           HTTP/JSON    │                  │ HTTPS/JSON
                        ▼                  ▼
        ┌─────────────────────┐   ┌────────────────────────┐
        │ ESP32 DevKit V1     │   │ Existing backend/call  │
        │ LIRA local node     │   │ agent repository       │
        │                     │   │                        │
        │ PIR + vibration     │   │ Gemini tool router     │
        │ RC522 RFID/NFC      │   │ Incident store         │
        │ TFT + state machine │   │ Call-agent adapter     │
        │ Optional relay      │   │ Call status/transcript │
        │ Local REST API      │   │ Whitelisted contacts   │
        └─────────────────────┘   └────────────┬───────────┘
                                               │
                                               ▼
                                 Existing telephony provider
                                 / call-agent transport
                                 (actual outbound call path)
```

### Responsibility boundaries

**ESP32 firmware** owns sensor sampling, debounce/filtering, RFID reads, arm/disarm state, local incident creation, acknowledgement timer, optional relay output, local TFT updates, and the local HTTP API. It must not call Gemini directly.

**Android app** owns user controls, Wi-Fi setup, cellular/backend connectivity, optional phone NFC, live status, incident timeline, consent/policy settings, call progress, transcript display, and understandable error recovery.

**Backend** owns secrets, Gemini requests, allowed-tool execution, persistence of escalation jobs, integration with the existing caller, contact allowlists, call retry limits, and evidence-backed result generation.

**Gemini** proposes/coordinates tool actions and explains results. It does not directly operate hardware or place calls; backend code validates each tool request and then performs the action.

**Calling agent** remains the source of truth for actual call status, transcript, extracted responses, and telephony errors. Do not synthesize a successful call result from Gemini text.

### Cellular wording clarification

The phone's **cellular data connection** carries HTTPS requests from LIRA to the backend. The actual outbound voice call is placed through the telephony/calling transport already used by the calling-agent repository. Do not assume that a cloud calling agent places calls through the phone's SIM. Preserve the existing repository's telephony implementation.

---

## 4. Hardware inventory and pin plan

This plan uses the user's existing parts only. The display pinout below is copied from the supplied TFT-only connection image.

### 4.1 Main ESP32

- Board: classic ESP32 DevKit V1 / ESP-WROOM-32 class board.
- Firmware: Arduino C++ using the ESP32 Arduino core.
- Network: Wi-Fi SoftAP for local phone-to-device communication.
- Suggested local SSID: `LIRA-ESP32-SETUP`.
- Suggested local IPv4 address: `192.168.4.1` (set explicitly in firmware with `WiFi.softAPConfig`; do not assume the default if configuration changes).
- Set a non-empty WPA2 password in a local firmware configuration file. Do not commit the actual password to a public repository.

### 4.2 TFT ILI9341 — keep existing wiring

| TFT pin | ESP32 DevKit V1 GPIO / pin |
|---|---|
| VCC | 3V3 |
| LED | 3V3 |
| GND | GND |
| CS | GPIO 5 |
| RESET / RST | GPIO 4 |
| DC / RS | GPIO 2 |
| SDI / MOSI | GPIO 23 |
| SCK / CLK | GPIO 18 |
| SDO / MISO | GPIO 19 |

Use the currently installed compatible ILI9341/GFX library APIs. Do not call unsupported methods such as `pushColors()` unless the installed library version actually provides them. Touchscreen pins are explicitly out of scope.

### 4.3 RC522 RFID reader — shared SPI bus

| RC522 pin | ESP32 DevKit V1 GPIO / pin |
|---|---|
| SDA / SS / CS | GPIO 21 (dedicated CS) |
| SCK | GPIO 18 (shared with TFT) |
| MOSI | GPIO 23 (shared with TFT) |
| MISO | GPIO 19 (shared with TFT) |
| RST | GPIO 22 |
| 3.3V | 3V3 |
| GND | GND |

Both SPI devices share SCK/MOSI/MISO but must have separate chip-select pins. Ensure both CS pins are inactive before initializing/using the other device. The RC522 should be powered at 3.3 V. Test TFT-only first, then add RC522 and verify both devices continue to work.

**RFID limitation:** For this demo, a registered tag UID may be used as a convenient allowlist key, but a UID is cloneable and is not secure authentication. Do not claim that UID-only access control is production-grade security.

### 4.4 MVP sensors

| Component | Signal | ESP32 GPIO | Notes |
|---|---|---:|---|
| PIR HC-SR501 | OUT | GPIO 27 | Verify the PIR output is safe for 3.3 V GPIO. Power the module according to its own board label/specification. |
| HW-072 vibration/shock module | DO | GPIO 34 | GPIO 34 is input-only on classic ESP32; suitable for a digital input. Power the module so DO cannot exceed 3.3 V. |

Firmware should support configurable active-high/active-low logic for both digital sensors because breakout-board comparators may differ. Debounce and rate-limit their events.

### 4.5 Optional relay outputs

Only wire if time remains and a safe, already-owned low-voltage load is available.

| Relay module input | ESP32 GPIO |
|---|---:|
| IN1 | GPIO 25 |
| IN2 | GPIO 26 |
| IN3 | GPIO 32 |
| IN4 | GPIO 33 |

Power the relay board only according to its printed/module specifications. If the board requires 5 V, use a 5 V/VIN supply pin only if it is clearly present on the user's **classic ESP32 DevKit V1** and powered appropriately; do not apply this assumption to the separate ESP32-C6 board. Share ground where the module's input interface requires it. Check whether the board inputs are active-low and whether 3.3 V GPIO logic is accepted.

**No mains wiring.** A relay switches a circuit; it does not reduce voltage. With no connected load, show relay state in the app/TFT and label it “output state only.”

### 4.6 Other available parts

HC-SR04, IR obstacle sensor, KY-037 sound sensor, MPU6050, DS3231 RTC, MQ-2, SSR module, Arduino UNO, breadboard, jumper wires, and passive components are not required for the MVP. If the HC-SR04 is added later, its Echo output must pass through an appropriate divider/level shifter. Do not connect the Echo pin directly to ESP32 GPIO. Do not use the MQ-2 as a certified gas alarm.

### 4.7 GPIO caution

The existing TFT wiring uses GPIO 2 and GPIO 5, which can be strapping pins on classic ESP32 variants. Keep the user-provided wiring for this build, but test cold boot with the TFT connected. If boot becomes unreliable, diagnose the attached module's CS/DC/reset levels rather than randomly changing the known display pinout.

---

## 5. App technology and repository layout

### Recommended app stack

- **Android:** Kotlin, Jetpack Compose, AndroidX lifecycle/view-model, coroutines, and native Android connectivity/NFC APIs.
- **Minimum Android version:** API 29 (Android 10) so the app can use `WifiNetworkSpecifier` for a user-approved local device connection. Build against the SDK installed in the build environment.
- **Firmware:** Arduino C++ for the classic ESP32 DevKit V1.
- **Backend:** Keep the current calling-agent repository's language and package manager. Add a small adapter/API layer; do not rewrite the call agent. If the existing repo is Node.js, use its existing framework or a minimal Express router and the official `@google/genai` SDK already compatible with the repo.
- **Transport:** REST/JSON with bounded polling for the hackathon MVP. WebSockets are not required.

### Suggested project layout

```text
LIRA/
├── LIRA_ARCHITECTURE.md
├── android-app/                    # Native Kotlin + Compose client
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/<package>/
│       │   ├── MainActivity.kt
│       │   ├── navigation/
│       │   ├── ui/dashboard/
│       │   ├── ui/incidents/
│       │   ├── ui/nfc/
│       │   ├── ui/calls/
│       │   ├── ui/settings/
│       │   ├── data/esp32/
│       │   ├── data/backend/
│       │   ├── networking/
│       │   ├── domain/models/
│       │   └── domain/state/
│       └── res/xml/network_security_config.xml
├── firmware/
│   └── esp32-lira/
│       ├── esp32-lira.ino
│       ├── config.example.h
│       └── README.md
└── backend-adapter/                # Only if adapter belongs in a separate folder
    ├── README.md
    └── .env.example
```

If the existing calling-agent repository already has a backend layout, add the adapter and routes inside that repository instead of moving files or creating a duplicate service. Use the existing package lockfile; do not replace the package manager or upgrade dependencies unnecessarily.

---

## 6. Android app: required screens

### 6.1 First launch / connection setup

Show three independent statuses:

- `ESP32 Wi-Fi`: Disconnected / Connecting / Connected / Timed out.
- `Cellular internet`: Available / Unavailable / Permission or carrier issue.
- `Backend`: Online / Offline / Authentication error.

Fields/actions:

- ESP32 Wi-Fi SSID (default `LIRA-ESP32-SETUP`, editable).
- ESP32 Wi-Fi password (user-entered; do not hardcode the real password).
- Device address (default `http://192.168.4.1`, editable for alternate firmware configuration).
- **Connect to ESP32** button. On Android 10+, use `WifiNetworkSpecifier` to request the network and let Android show its permission/connection dialog. A fallback button may open Android Wi-Fi settings.
- **Test cellular/backend** button.
- Plain-language instructions to keep mobile data enabled and accept “stay connected/use this network without internet” if Android shows that prompt.

Do not display a false “connected” state merely because the phone joined an SSID. The app must complete a successful request to `GET /api/v1/health` to mark the ESP32 API reachable.

### 6.2 Dashboard

- App name and status: **LIRA — Linked Intelligence & Response Agent**.
- Device online/offline, firmware version, last-seen timestamp.
- Armed/disarmed status.
- PIR state, vibration state, RC522 status, last tag event (mask tag UID by default).
- Current incident and acknowledgement countdown.
- Cellular/backend status and call-agent readiness.
- Last action, its timestamp, and actual outcome.
- Large primary actions: `Arm`, `Disarm`, `Acknowledge`, `Open incident`, `Start approved test call`.

Disable or hide controls that cannot be executed because the relevant device/service is offline.

### 6.3 Incidents and task timeline

Each incident should show ordered timestamps and states such as:

1. Physical event detected.
2. Sensor corroboration (if available).
3. Awaiting acknowledgement.
4. Acknowledged or timeout.
5. Escalation requested/approved.
6. Calling / no answer / connected / failed.
7. Human response recorded.
8. Resolved or still unresolved.

Distinguish `sensor event`, `AI inference`, `call-provider outcome`, and `human acknowledgement`. Never label a sensor inference as a confirmed fact.

### 6.4 NFC and authorization settings

Provide a dedicated `Access & NFC` screen with two independent modes:

**A. External RC522 reader (primary):**
- Shows reader status reported by ESP32.
- Lets the user add/remove a tag for the **demo allowlist** by entering the UID displayed during an enrollment action, or by confirming a just-scanned tag.
- Supports `Tap tag to arm/disarm` and `Tap registered tag to acknowledge an incident`.
- Unknown tags are logged as “unregistered tag seen”; do not automatically call a contact just because an unknown tag was scanned.

**B. Phone NFC (optional):**
- Show `Enable phone NFC` only when the phone has NFC hardware (`PackageManager.FEATURE_NFC`). Otherwise show `Not supported on this phone` and preserve the RC522 mode.
- If enabled, use Android NFC reader mode while the relevant screen is active. Prefer parsing a known LIRA NDEF payload; optionally display tag technology/identifier for troubleshooting.
- A phone NFC tap may request arm/disarm/acknowledgement in the app, but must still call the ESP32 API over the Wi-Fi network. If Wi-Fi is disconnected, queue no privileged action; show a clear failure and ask the user to reconnect.
- Do **not** assume every RC522 key fob is readable by every phone. Tag protocols and NDEF formatting vary. Keep the external RC522 and phone NFC paths independent.
- Phone NFC is an optional convenience for compatible hardware/tags, not a requirement for the hackathon to pass.

**Security limitation:** Neither a raw NFC tag UID nor a static NDEF string is strong authentication. Treat it as a demo token/acknowledgement only. Real access control would require a cryptographic credential and server/device challenge-response, outside the five-hour MVP.

### 6.5 Calls and escalation

Show:

- Incident summary passed to caller.
- Whitelisted contact/display name (mask number in normal UI).
- Call job ID and actual provider status.
- Start time, duration if provided, response/ETA if extracted, transcript or transcript summary if available.
- Provider error or `no answer` as a failed call outcome.
- Retry/fallback only when policy allows and the call-attempt limit is not reached.

A prominent `Call test contact` action must use a user-approved contact and visibly indicate the outbound call before execution.

### 6.6 Automation policy

Defaults:

- `Auto-escalation`: OFF until the user explicitly enables it.
- `Allowed contacts`: empty until configured.
- `Max calls per incident`: 1 by default; hard maximum 2 for the demo.
- `Acknowledgement timeout`: 20 seconds as an editable **demo** value.
- `Duplicate-incident cooldown`: 60 seconds as a demo default.
- `Relay automation`: OFF.

When auto-escalation is enabled, show a confirmation page that explains which contact can be called, what sensor event triggers it, the timeout, and maximum attempts. Never let Gemini expand the contact list, change the limit, or override the user's policy.

---

## 7. Wi-Fi and cellular networking — critical implementation detail

This is a core acceptance requirement. Implement two separate network paths; do not rely on whichever network Android happens to make the default.

### 7.1 ESP32 local Wi-Fi path

- ESP32 advertises its configured SoftAP, suggested SSID `LIRA-ESP32-SETUP`.
- Android connects with a `WifiNetworkSpecifier` and a user-approved `ConnectivityManager.requestNetwork()` request on Android 10+.
- The ESP32 local network does not provide internet. The Wi-Fi request must not require `NET_CAPABILITY_INTERNET`.
- Store the `Network` object delivered by the Wi-Fi callback as `esp32Network`.
- Send local requests through that `Network` specifically (for simple HTTP, `esp32Network.openConnection(URL(...))` inside `Dispatchers.IO`). This method routes the connection over the selected `Network`.
- Do not use a normal unbound HTTP client for local ESP32 requests if it may choose the cellular/default network.
- If Wi-Fi is lost, clear `esp32Network`, stop status polling, mark the device disconnected, and never report a successful control action.

### 7.2 Cellular internet path

- Keep the phone's mobile data enabled.
- Register/request a cellular `Network` with `TRANSPORT_CELLULAR` and `NET_CAPABILITY_INTERNET` through `ConnectivityManager`.
- Store it separately as `cellularNetwork` when available.
- Send all cloud/backend HTTPS requests through `cellularNetwork.openConnection(URL(...))`, or a dedicated HTTP client whose socket factory and DNS resolution are bound to this `Network`.
- Do not globally call `bindProcessToNetwork()` for the entire app: that could route local ESP32 traffic incorrectly or cut off one of the required paths.
- If cellular is unavailable, do not silently send cloud traffic through the ESP32 Wi-Fi network. Show `Cellular unavailable` and keep local monitoring controls working.
- If the target phone/carrier does not support simultaneous Wi-Fi-local and cellular-data connections as requested, display the issue rather than claiming dual networking works. Validate this on the actual demonstration phone early.

### 7.3 Connection lifecycle

- Register network callbacks in a lifecycle-aware connection manager; unregister them when no longer needed.
- Use network callback events to refresh UI status.
- Do not perform network I/O on the UI thread.
- Apply finite connection/read timeouts (for example, 2–4 seconds for local health/status, 5–10 seconds for the backend); make them configurable and handle timeouts as errors.
- The MVP can poll `GET /api/v1/status` every 1–2 seconds only while the dashboard is visible. Slow/stop polling when it is not visible.
- Do not claim that background monitoring continues because the Android UI is open. Local incident logic belongs on ESP32; the remote backend owns an accepted call job.

### 7.4 Android permissions/configuration

Include only the permissions required by the implemented APIs, typically:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.CHANGE_NETWORK_STATE`
- `android.permission.NFC` for optional phone NFC
- `android.permission.NEARBY_WIFI_DEVICES` for applicable Android 13+ Wi-Fi APIs, requested at runtime when required by the specific API and target SDK
- `android.hardware.nfc` as `required="false"` in the manifest so phones without NFC can install/use LIRA

Do not request location permission just to connect to a known SSID unless the exact Wi-Fi API used requires it. Avoid Wi-Fi scanning; ask the user for the SSID directly. Use a manifest/network-security configuration appropriate to your target SDK. If local ESP32 HTTP requires cleartext support during the prototype, scope it to local device communication as narrowly as the platform allows; backend requests must use HTTPS. Never transmit cloud API secrets over the ESP32 local HTTP endpoint.

---

## 8. ESP32 firmware responsibilities and local state machine

### 8.1 Firmware modules

1. `SensorManager`: read PIR and vibration digital inputs.
2. `RfidManager`: initialize RC522 on shared SPI, read tags, apply demo allowlist.
3. `IncidentManager`: debounce events, deduplicate repeated triggers, manage acknowledgement timeout and incident ID.
4. `DisplayManager`: render a small stable status screen without touch input.
5. `LocalApi`: REST endpoints returning JSON and receiving permitted commands.
6. `NetworkManager`: create the configured SoftAP and report its IP/SSID.
7. `OptionalRelayManager`: disabled by default; accept only explicit, validated channel changes.

Keep sensor sampling and incident state transitions non-blocking. Do not use long `delay()` calls that stop the web server or RFID loop. Use a `millis()`-based timer/state machine.

### 8.2 States

```text
DISARMED
  └─ registered RC522 tag or app action → ARMED_IDLE

ARMED_IDLE
  ├─ PIR/vibration trigger → INCIDENT_DETECTED
  └─ registered RC522 tag or app action → DISARMED

INCIDENT_DETECTED
  └─ create event, update TFT/app → AWAITING_ACK

AWAITING_ACK
  ├─ registered RC522 tag or valid app acknowledgement → ACKNOWLEDGED
  └─ timeout while incident unresolved → ESCALATION_PENDING

ESCALATION_PENDING
  ├─ auto-escalation policy + approved contact → ESCALATION_REQUESTED
  └─ approval required → wait for user approval in app

ESCALATION_REQUESTED
  └─ backend accepts unique job → CALLING

CALLING
  ├─ provider says connected/completed → CALL_RESULT_AVAILABLE
  └─ no answer/error/timeout → CALL_FAILED

CALL_RESULT_AVAILABLE
  ├─ human acknowledges and incident quiet → RESOLVED
  └─ evidence says still unresolved → OPEN / eligible fallback within policy

CALL_FAILED
  └─ show failure, allow only policy-bounded retry or manual action
```

The ESP32 does not itself make cloud calls. When the phone/backend is absent, the ESP32 still detects events and reports them on the TFT and local API. If the backend cannot be reached, show `Escalation unavailable — backend offline`; never label the incident resolved merely because a request timed out.

### 8.3 Trigger filtering

- Add configurable debounce for PIR and vibration.
- Group repeated sensor edges into one incident for a configurable event window.
- Optionally classify triggers as `motion`, `vibration`, or `motion_and_vibration` based only on actual sensor evidence.
- Do not infer “intruder”, “break-in”, “machine failure”, or “emergency” from a PIR/vibration reading alone.
- Demo acknowledgement timeout: 20 seconds by default; this is a presentation setting, not a real security recommendation.

---

## 9. ESP32 local HTTP API contract

Use JSON and return meaningful HTTP status codes. Keep the API small and versioned under `/api/v1`.

### `GET /api/v1/health`

Example:

```json
{
  "schemaVersion": 1,
  "deviceId": "lira-esp32-01",
  "firmwareVersion": "0.1.0",
  "uptimeSeconds": 120,
  "ipAddress": "192.168.4.1",
  "rc522Ready": true,
  "armed": false
}
```

### `GET /api/v1/status`

Example:

```json
{
  "schemaVersion": 1,
  "deviceId": "lira-esp32-01",
  "timestampUptimeMs": 123456,
  "armed": true,
  "state": "AWAITING_ACK",
  "sensors": {
    "pir": {"configured": true, "active": false},
    "vibration": {"configured": true, "active": true},
    "rc522": {"configured": true, "ready": true},
    "relay": {"configured": false}
  },
  "currentIncident": {
    "incidentId": "inc-0007",
    "type": "vibration",
    "state": "AWAITING_ACK",
    "startedUptimeMs": 123000,
    "ackDeadlineUptimeMs": 143000
  },
  "lastEventId": "evt-0009"
}
```

### `GET /api/v1/events?after=<eventId>`

Return recent events in chronological order. Keep an in-memory ring buffer of a limited size on the ESP32 (for example, 50 items); do not depend on external database hardware.

### `POST /api/v1/arm`

Request:

```json
{"armed": true}
```

Response includes actual `armed` and `state`. A failed change must return an error response.

### `POST /api/v1/acknowledge`

Request:

```json
{
  "incidentId": "inc-0007",
  "method": "app"
}
```

Allowed `method` values: `app`, `rc522`, `phone_nfc`. The firmware validates that the incident exists and is in an acknowledgeable state. If `phone_nfc` is used, the app must have actually detected a tag tap and the local API request must have reached the ESP32; simply tapping a tag while disconnected must not show success.

### `POST /api/v1/relay` (optional; disabled by default)

Request:

```json
{"channel": 1, "state": "on"}
```

Validate channel range, value, whether relay hardware is enabled, and configured safety policy. Return `501`/a clear disabled response if the optional relay module is not configured. Do not expose a general-purpose pin-write endpoint.

### Local API errors

Return JSON with stable `code` and human-readable `message`, such as `DEVICE_NOT_ARMED`, `INCIDENT_NOT_FOUND`, `INVALID_CHANNEL`, `MODULE_DISABLED`, `UNAUTHORIZED`, or `INTERNAL_ERROR`. Include no stack traces in API responses.

---

## 10. Backend API and calling-agent integration

### 10.1 Integration rule

Inspect the existing call-agent repository before implementing this section. Identify its current start command, environment variables, call-creation function/endpoint, job status endpoint, transcript format, provider status names, and error behavior. Wrap those interfaces with an adapter. Do not replace a working implementation with a new telephony library.

### 10.2 Backend endpoints

Use HTTPS for deployed backend requests. Route names can be adapted to the existing repository, but preserve equivalent behavior.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/v1/health` | Backend, Gemini and call-agent readiness (never return secrets). |
| `POST` | `/api/v1/incidents` | Submit incident data collected from the ESP32 by the app. |
| `POST` | `/api/v1/escalations` | Create a call job only after policy/consent validation. |
| `GET` | `/api/v1/escalations/{jobId}` | Return provider-derived status. |
| `GET` | `/api/v1/escalations/{jobId}/transcript` | Return transcript/summary if available and caller is authorized. |
| `POST` | `/api/v1/escalations/{jobId}/cancel` | Cancel queued jobs when the underlying provider supports it. |
| `POST` | `/api/v1/agent/turn` | Optional Gemini orchestration endpoint for user natural-language tasks. |

### 10.3 Create escalation request

```json
{
  "schemaVersion": 1,
  "incidentId": "inc-0007",
  "deviceId": "lira-esp32-01",
  "eventType": "vibration",
  "startedAt": "2026-10-15T07:30:00Z",
  "sensorEvidence": ["vibration_active", "ack_timeout"],
  "localState": "ESCALATION_PENDING",
  "contactId": "approved-contact-1",
  "goal": "Notify the supervisor of a repeated vibration event and ask for acknowledgement",
  "userApproved": true,
  "idempotencyKey": "inc-0007-escalation-1"
}
```

Do not include a contact phone number supplied freely by Gemini. The backend resolves `contactId` against the user's configured allowlist. Require an explicit pre-approved automation policy or a fresh user approval. Enforce idempotency so repeated HTTP requests do not create duplicate calls.

### 10.4 Job response and status

```json
{
  "jobId": "calljob-0012",
  "incidentId": "inc-0007",
  "status": "queued",
  "attempt": 1,
  "createdAt": "2026-10-15T07:30:05Z"
}
```

Normalize provider states to:

- `queued`
- `dialing`
- `in_progress`
- `completed`
- `no_answer`
- `busy`
- `failed`
- `cancelled`
- `unknown`

Retain the provider's original status in a diagnostic field. Do not mark `completed` as “incident resolved” unless a real acknowledgement/resolution signal supports that conclusion.

### 10.5 Call-agent tool adapter

The backend should expose a small adapter with operations equivalent to:

- `startCall(approvedContact, structuredGoal, incidentContext, idempotencyKey)`
- `getCallStatus(providerJobId)`
- `getTranscript(providerJobId)`
- `cancelCall(providerJobId)` if supported

Map these operations to the existing repository's real functions/endpoints. Return typed errors for timeout, rate limit, missing credentials, provider rejection, no answer, and invalid response shape.

---

## 11. Gemini orchestration contract

Use Gemini from the backend only. The currently supported model identifier should be configured through `GEMINI_MODEL`; reuse the model already working in the user's repository rather than pinning a possibly obsolete model name.

### Allowed tools

1. `get_device_status()` — read-only.
2. `get_recent_events(afterEventId)` — read-only.
3. `request_arm_change(armed, reason)` — app/device policy checks required.
4. `acknowledge_incident(incidentId, method)` — only for valid pending incidents and actual acknowledgement actions.
5. `get_call_status(jobId)` — read-only.
6. `get_call_transcript(jobId)` — read-only, if permitted.
7. `request_escalation(incidentId, contactId, goal)` — backend validates allowlist, policy, attempt limit, idempotency, and approval/pre-authorization.
8. `set_relay_state(channel, state)` — optional; disabled unless relay is explicitly configured and user-authorized.

Gemini must not have arbitrary shell, file-system, HTTP, arbitrary GPIO, or raw phone-number tools.

### Tool-call execution loop

1. Send the user's request and allowed tool declarations to Gemini.
2. If Gemini returns a tool call, validate its schema and policy server-side.
3. Execute only the corresponding allowlisted function in code.
4. Return the actual function result (including errors) to Gemini.
5. Continue only for a small bounded number of turns (suggested maximum 4 per user action).
6. Return a final response that clearly separates facts, inferred summary, and actions that are still pending.

The model does not execute tools by itself; backend code must execute and return results. Always validate function arguments even if Gemini provided them in a structured format.

### Safety and reliability guardrails

- Use a contact allowlist; no arbitrary destinations.
- Auto-calling is OFF by default. When enabled, it is limited to contacts and triggers explicitly configured by the user.
- Default to one call per incident; hard cap at two attempts for the demo. Never retry indefinitely.
- Add a cooldown and idempotency key to prevent repeated sensor events/HTTP retries creating repeated calls.
- Do not call emergency services automatically.
- Do not let Gemini enable the relay, change safety policy, add contacts, or bypass consent.
- Do not claim a person accepted a job, confirmed safety, or gave a quote unless the call record/transcript contains supporting evidence.
- Summarize transcript excerpts faithfully. Clearly label transcript-derived statements and uncertain extraction.

---

## 12. Data model

Use typed data classes/DTOs on Android and equivalent schema validation in the backend.

### DeviceStatus

- `schemaVersion: Int`
- `deviceId: String`
- `firmwareVersion: String`
- `uptimeSeconds: Long`
- `armed: Boolean`
- `state: IncidentState`
- `sensors: Map<String, SensorStatus>`
- `currentIncident: IncidentSummary?`
- `lastEventId: String?`

### SensorStatus

- `configured: Boolean`
- `active: Boolean?`
- `ready: Boolean?`
- `lastChangedUptimeMs: Long?`
- `errorCode: String?`

### Incident

- `incidentId: String`
- `deviceId: String`
- `type: String` (`motion`, `vibration`, `motion_and_vibration`, `unknown_tag`, or an explicitly configured optional sensor type)
- `state: IncidentState`
- `startedAt: Instant?` (backend timestamp may be unavailable offline)
- `startedUptimeMs: Long?`
- `acknowledgementDeadline: Instant?` or `ackDeadlineUptimeMs: Long?`
- `evidence: List<String>`
- `acknowledgedBy: String?`
- `escalationJobId: String?`
- `resolutionNote: String?`

### IncidentState enum

`DISARMED`, `ARMED_IDLE`, `INCIDENT_DETECTED`, `AWAITING_ACK`, `ACKNOWLEDGED`, `ESCALATION_PENDING`, `CALLING`, `CALL_RESULT_AVAILABLE`, `CALL_FAILED`, `RESOLVED`, `OPEN`.

Keep provider call statuses separate from the incident state; they describe different things.

---

## 13. Reliability, error handling, and offline behavior

Every async operation must have explicit loading, success, empty, and error UI states.

| Failure | Required behavior |
|---|---|
| ESP32 AP unavailable | Keep app usable; show disconnected and provide reconnect/setup guidance. |
| Wi-Fi connects but local API fails | Keep device as unreachable until `/health` succeeds. Show host/SSID troubleshooting. |
| Cellular unavailable | Local sensor view and local control can continue; disable cloud actions and show why. |
| Backend offline | Keep local event detection; queue only a non-privileged incident record in app memory or local device buffer if implemented; do not queue an unapproved call silently. |
| Gemini failure/timeout | Use deterministic state machine and direct UI; do not block incident detection or invent a summary. |
| Calling service failure | Mark call failed with provider error; expose manual retry only under policy. |
| No answer/busy | Keep incident open, not resolved. |
| Invalid JSON/unexpected status | Validate schema, log a redacted diagnostic, show a recoverable error. |
| NFC unsupported/disabled | Explain and use RC522 or app button instead. |
| RC522 not initialized | Show reader offline; do not accept a missing tag as authorization. |
| Optional relay disabled | Return a disabled/unsupported error; UI must not show a successful switch. |
| Duplicate sensor triggers | Debounce/deduplicate into an existing incident within the cooldown window. |

Never block the ESP32 sensor loop while waiting for Android, Gemini, or a phone call. A call can take many seconds or minutes; it must run asynchronously through the backend job system.

---

## 14. Security, privacy, and demo limitations

- Keep `GEMINI_API_KEY`, telephony provider keys, call-agent credentials, and backend signing secrets in server environment variables.
- Provide `.env.example` with placeholder names only. Never commit `.env`, real tokens, phone numbers, transcripts, or tag allowlists.
- Generate a unique local device token for the prototype and require it for control endpoints; never expose the Gemini key or call-agent key to the ESP32.
- Use a WPA2 password for the ESP32 AP. Do not expose an open AP in the live demo.
- Local HTTP may be used only for the isolated prototype AP. Treat it as untrusted transport; do not send cloud secrets over it. The deployed backend must use HTTPS.
- Mask RFID UIDs and phone numbers in normal UI. Keep logs minimal and provide a way to clear demo incidents/transcripts.
- Obtain permission from the receiving person before test calls. Identify the agent as AI where applicable and limit calls to consenting test contacts.
- The PIR/vibration setup is a demonstration sensor system, not certified intrusion detection. MQ-2 is not a certified gas detector; do not create a gas leak or use this prototype to control a real gas supply.
- Do not switch AC mains with the relay/SSR in the demo. Use only safe low-voltage demonstration conditions and existing available loads.

---

## 15. Android manifest and configuration checklist

The coding agent must produce a manifest that matches the actual APIs used. Review target SDK requirements before final compilation. At minimum verify:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `CHANGE_NETWORK_STATE`
- `NFC` (optional feature)
- `uses-feature android.hardware.nfc required="false"`
- applicable `NEARBY_WIFI_DEVICES` declaration and runtime request on Android 13+ if the selected Wi-Fi API needs it
- appropriate cleartext policy for local ESP32 HTTP only, with backend endpoints HTTPS
- no unnecessary camera, microphone, contacts, SMS, location, or call-log permissions

The app is not itself the phone dialer and should not request direct call/SMS permissions just because the cloud calling agent uses telephony. The existing server-side caller handles voice calls.

### Android local configuration (example only)

```text
ESP32_SSID=LIRA-ESP32-SETUP
ESP32_BASE_URL=http://192.168.4.1
BACKEND_BASE_URL=https://<your-deployed-backend-host>
PHONE_NFC_ENABLED=false
```

The backend URL is a configuration value, not a literal to leave in the code. Do not use a fake URL as a working default; the Settings screen must ask for/validate the actual backend URL.

### Backend `.env.example` (names only)

```text
GEMINI_API_KEY=
GEMINI_MODEL=
CALL_AGENT_BASE_URL=
CALL_AGENT_API_KEY=
LIRA_APP_TOKEN=
ALLOWED_CONTACTS_JSON=
PORT=3000
```

Use the existing repo's variable names if they are already defined; do not create duplicate or conflicting settings.

---

## 16. Build and implementation order for Antigravity

The time box is approximately five hours. Implement the smallest end-to-end slice first.

### Phase 0 — inspect, do not overwrite (first 10–15 minutes)

1. Inspect the current working directory and identify the existing AI calling-agent repository.
2. Find the current call initiation path, credentials, job-status API, transcript schema, and working test command.
3. Run its existing tests or its smallest working call test before changing anything.
4. Preserve language, lockfiles, environment-variable names, provider SDK, and working telephony behavior.
5. Report existing constraints before adding dependencies. Do not scaffold a second calling agent.

### Phase 1 — ESP32 first proof

1. Flash a minimal firmware sketch using the existing TFT wiring.
2. Add PIR and vibration sensor reads, non-blocking debounce, and the local event state machine.
3. Add RC522 on the shared SPI bus with its dedicated CS and reset pins.
4. Add `GET /api/v1/health` and `GET /api/v1/status` on the ESP32 SoftAP.
5. Test the API from a phone or laptop directly connected to the ESP32 AP.
6. Add TFT status rendering after the core event state is reliable.

### Phase 2 — Android dual-network proof

1. Create Kotlin/Compose app skeleton and a visible connection diagnostics page.
2. Implement user-approved ESP32 Wi-Fi connection and `esp32Network` routing.
3. Implement separate cellular network detection/request and `cellularNetwork` routing.
4. Prove local `GET /health` works over Wi-Fi and backend `GET /health` works over cellular concurrently on the actual phone.
5. Do not proceed to UI polish until both paths work.

### Phase 3 — incidents and NFC

1. Implement the dashboard and incident timeline.
2. Poll status only while relevant screens are visible.
3. Add RC522 event/allowlist UI.
4. Add optional phone NFC screen; gracefully handle phones without NFC and unsupported tag technologies.
5. Confirm app acknowledgement succeeds only after the ESP32 has accepted the request.

### Phase 4 — existing caller + Gemini

1. Add a backend adapter to the existing repository.
2. Add allowlisted contact selection and call approval/policy checks.
3. Implement escalation creation, unique idempotency key, polling for actual status, and transcript display.
4. Add Gemini tool declarations only for the allowlisted actions in Section 11.
5. Validate every argument and result server-side. Add a maximum tool loop and call-attempt count.

### Phase 5 — end-to-end test and rehearse

Run the complete acceptance flow in Section 17. Prepare a short backup recording for the live call in case the carrier/provider or internet connection fails. Label any recorded fallback as recorded; do not portray it as a live call.

---

## 17. Acceptance tests — do not mark complete without these

### Hardware and firmware

- [ ] Classic ESP32 DevKit V1 boots reliably with the supplied TFT wiring.
- [ ] TFT shows LIRA, armed/disarmed state, and incident state without touchscreen wiring.
- [ ] PIR changes are reported; vibration input changes are reported.
- [ ] RC522 is initialized on the shared SPI bus; TFT still works after RC522 is added.
- [ ] A registered demo tag can arm/disarm and acknowledge only in a valid state.
- [ ] An unknown tag is not treated as authorized.
- [ ] Local event detection still works when the phone is disconnected.
- [ ] Local health/status endpoints return valid JSON.

### Android networking

- [ ] App can request connection to the configured ESP32 AP.
- [ ] App can read the ESP32 `/health` endpoint over the Wi-Fi `Network` object.
- [ ] Mobile data remains enabled and the app can reach the configured HTTPS backend over the cellular `Network` object while connected to the ESP32 AP.
- [ ] The app does not claim cloud connectivity when cellular/backend is unavailable.
- [ ] Network loss changes the UI state and stops incorrect success messages.

### NFC

- [ ] RC522 mode works independently of phone NFC availability.
- [ ] A phone without NFC can still use the full MVP.
- [ ] Phone NFC is enabled only when supported and gracefully handles unreadable/non-NDEF tags.
- [ ] A phone NFC tap while the ESP32 Wi-Fi API is unavailable does not show an acknowledged incident.

### AI calling and failure handling

- [ ] Existing calling-agent repository still runs after the adapter change.
- [ ] A user-approved call to a consenting test contact starts successfully.
- [ ] Backend status reflects the provider's actual job state.
- [ ] No-answer/provider failure remains a failed/unresolved step.
- [ ] Transcript/summary is shown only if returned by the actual calling service.
- [ ] No repeated request can create duplicate calls for the same idempotency key.
- [ ] Call attempts are bounded and contacts are allowlisted.
- [ ] Gemini cannot bypass policy or call an arbitrary number.

### Final 5-minute demo

- [ ] Tap registered tag to arm.
- [ ] Trigger PIR or vibration.
- [ ] Show incident and acknowledgement timer on TFT and Android.
- [ ] Let the timer expire and show escalation pending.
- [ ] Start an approved call through the existing call-agent repository.
- [ ] Display real call status and any available transcript/outcome.
- [ ] Demonstrate at least one failure/timeout state without claiming success.

---

## 18. Explicit Antigravity build instruction

Use this document as the source of truth. Implement the project incrementally and compile/test after each phase. Before changing existing files, inspect them. Do not invent hardware, APIs, call outcomes, SDK methods, or configuration values. When an integration detail is unknown, search the existing repository and use its actual contract; if it cannot be established, add a typed adapter interface and mark the missing configuration clearly rather than fabricating an implementation.

**Completion definition:** LIRA is complete for the hackathon when the ESP32 detects and records an event, the TFT and Android app show the same state, an approved RFID/app acknowledgement resolves it, an unacknowledged event reaches the escalation flow, and the existing AI calling agent returns a truthful result over the cellular-to-backend path. All other sensors and the relay remain optional.

---

## 19. Official references

- Android network state and multiple networks: <https://developer.android.com/develop/connectivity/network-ops/reading-network-state>
- Android `Network.openConnection(URL)` routing: <https://developer.android.com/reference/android/net/Network#openConnection(java.net.URL)>
- Android Wi-Fi peer-to-peer request / `WifiNetworkSpecifier`: <https://developer.android.com/develop/connectivity/wifi/wifi-bootstrap>
- Android NFC overview and supported tag modes: <https://developer.android.com/develop/connectivity/nfc>
- ESP32 Arduino Wi-Fi SoftAP API: <https://docs.espressif.com/projects/arduino-esp32/en/latest/api/wifi.html>
- MFRC522 Arduino RFID library and security notes: <https://github.com/miguelbalboa/rfid>
- Gemini API function calling: <https://ai.google.dev/gemini-api/docs/function-calling>

