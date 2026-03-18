# ⚡ MyWeld Android App

> Android companion app for the **MyWeld DIY supercapacitor spot welder**, providing real-time Bluetooth control, live monitoring, and preset management from your phone.

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-blue)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-1.3.1-indigo)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-24-orange)](#)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Screenshots & Features](#screenshots--features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [BLE Protocol](#ble-protocol)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Permissions](#permissions)
- [Firmware Counterpart](#firmware-counterpart)

---

## Overview

**MyWeld** is an Android app that connects to the ESP32-based spot welder via Bluetooth Low Energy (BLE). Once paired and authenticated, you can:

- Monitor capacitor charge, weld state, and pulse parameters in real time
- Adjust **P1 / T / P2 / P3 / P4** pulse timings and AUTO/MAN mode without touching the device
- **Configure max supercapacitor voltage** (4.0–12.0 V) for different capacitor bank setups
- Load and save up to **20 presets** (7 factory + 13 user-defined)
- View session and lifetime weld counters
- **Calibrate ADC channels** with per-channel correction factors
- Configure device settings (display name, PIN, brightness, sound, volume)
- **OTA firmware update** — check for new releases on GitHub and flash over BLE

---

## Screenshots & Features

### 📡 Scan Screen
Scans for nearby BLE devices advertising the `MyWeld` service. Displays device name, RSSI signal strength, and previously-paired devices.

### 🔐 PIN Authentication
On first connection (and on demand), a PIN entry screen is shown. The PIN is verified directly by the firmware — the app only proceeds to the dashboard after receiving a positive ACK.

### 📊 Dashboard Screen
Live dashboard showing:
- **Supercapacitor voltage bar** with color-coded charge level (green → yellow → red)
- **Charge percentage** (derived from configurable max voltage)
- **Weld state** badge (IDLE / ARMED / FIRING / BLOCKED / ERROR)
- **P1 / T / P2 / P3 / P4** pulse parameters with `+` / `−` controls
- **AUTO / MAN** mode toggle
- **Session** and **total** weld counters (with reset buttons)
- **Active preset** indicator

### 🗂️ Presets Screen
Browse, load, and save 20 preset slots (7 factory presets + 13 user-custom). Each preset stores:
- Name (up to 20 characters)
- P1, T, P2, P3, P4 durations
- S delay (AUTO mode)
- AUTO/MAN mode flag

### 📦 Firmware Update Screen
Check GitHub Releases for new firmware versions. When an update is available:
- View changelog and release notes
- Download the `.bin` binary directly from GitHub
- Flash over BLE with real-time progress bar and SHA-256 verification
- Device reboots automatically after successful update

### ⚙️ Settings Screen
- Change BLE display name (updates firmware over BLE)
- Change PIN
- Toggle sound, adjust **volume** (0–100%)
- Select theme (dark / light)
- **Configure max supercapacitor voltage** (4.0–12.0 V, stored in calibration partition)
- **ADC calibration** — per-channel correction factors for voltage readings
- Trigger factory reset

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 |
| **Navigation** | Compose Navigation 2.8.5 |
| **DI** | Koin 4.0.1 |
| **BLE** | Nordic Semiconductor BLE KTX 2.9.0 + Scanner 1.6.0 |
| **Storage** | Jetpack DataStore (Preferences) |
| **Charts** | Vico compose-m3 |
| **Async** | Kotlin Coroutines + Flow 1.9.0 |
| **Permissions** | Accompanist Permissions 0.36.0 |
| **Build** | Gradle (Kotlin DSL), AGP 8.7.3 |
| **Min SDK** | API 24 (Android 7.0) |
| **Target SDK** | API 35 (Android 15) |

---

## Architecture

The app follows a clean **MVVM** architecture with unidirectional data flow:

```
UI Layer (Compose Screens)
        ↕ observes StateFlow / events
ViewModel Layer (per-screen)
        ↕ calls repository methods
Repository Layer (WelderRepository)
        ↕ emits/collects BLE data
BLE Layer (MyWeldBleManager)
        ↕ reads/writes GATT characteristics
ESP32 Firmware (BLE GATT Server)
```

### Key Components

| Component | Responsibility |
|-----------|----------------|
| `MyWeldBleManager` | Nordic BLE KTX wrapper — connects, discovers services, subscribes to notifications |
| `BleProtocol` | Stateless encoder/decoder for the binary packet protocol |
| `WelderRepositoryImpl` | Combines BLE streams into domain-level `StateFlow<WelderStatus>` |
| `DashboardViewModel` | Exposes live `WelderStatus` and handles user intents |
| `ScanViewModel` | BLE device scanning, filtering to MyWeld service UUID |
| `PresetsViewModel` | Preset load/save commands via BLE |
| `SettingsViewModel` | Device config reads and writes |
| `FirmwareViewModel` | OTA update orchestration — check, download, flash |
| `OtaService` | BLE OTA transfer engine — chunking, SHA-256, progress |
| `GitHubReleaseChecker` | GitHub REST API client — fetches latest release & .bin asset |

### State Machine (Connection)

```
DISCONNECTED
    │  user taps device
    ▼
CONNECTING
    │  GATT connected + services discovered
    ▼
AUTHENTICATING  ──→ (wrong PIN) ──→ AUTH_FAILED
    │  ACK received
    ▼
CONNECTED  ──→  DISCONNECTING  ──→  DISCONNECTED
```

---

## BLE Protocol

The app implements the **MyWeld Binary Protocol V2**, which is documented in detail in the firmware repository.

### GATT Service UUIDs

| Characteristic | UUID | Properties |
|----------------|------|------------|
| Service | `00001234-0000-1000-8000-00805F9B34FB` | — |
| Params | `00001235-0000-1000-8000-00805F9B34FB` | READ |
| Status / TX | `00001236-0000-1000-8000-00805F9B34FB` | READ, NOTIFY |
| Command / RX | `00001237-0000-1000-8000-00805F9B34FB` | WRITE |

### Packet Frame

```
┌────────┬────────┬────────┬──────────────┬────────┐
│  0xAA  │  TYPE  │  LEN   │   PAYLOAD    │  CRC   │
│  SYNC  │ 1 byte │ 1 byte │  0–240 bytes │ 1 byte │
└────────┴────────┴────────┴──────────────┴────────┘
CRC = XOR( TYPE, LEN, PAYLOAD[0..N] )
All multi-byte values: little-endian
```

### Status Packet (0x01) — 46 bytes, sent every ~500 ms via NOTIFY

| Offset | Field | Type | Notes |
|--------|-------|------|-------|
| 0 | `supercap_mv` | u16 | Supercap voltage in mV |
| 2 | `protection_mv` | u16 | Gate drive rail in mV |
| 4 | `state` | u8 | Weld state enum |
| 5 | `charge_percent` | u8 | 0–100 % |
| 6 | `auto_mode` | u8 | 0=MAN, 1=AUTO |
| 7 | `active_preset` | u8 | 0–19 (0xFF = user-defined) |
| 8 | `session_welds` | u32 | Session counter |
| 12 | `total_welds` | u32 | Lifetime counter |
| 16 | `ble_connected` | u8 | 1 if BLE connected |
| 17 | `sound_on` | u8 | |
| 18 | `theme` | u8 | 0=dark, 1=light |
| 19 | `error_code` | u8 | 0=none |
| 20 | `p1_x10` | u16 | P1 × 10 (e.g. 50 = 5.0 ms) |
| 22 | `t_x10` | u16 | T × 10 |
| 24 | `p2_x10` | u16 | P2 × 10 |
| 26 | `p3_x10` | u16 | P3 × 10 (0 = disabled) |
| 28 | `p4_x10` | u16 | P4 × 10 (0 = disabled) |
| 30 | `s_x10` | u16 | S × 10 (e.g. 5 = 0.5 s) |
| 32 | `fw_major` | u8 | Firmware major version |
| 33 | `fw_minor` | u8 | Firmware minor version |
| 34 | `volume` | u8 | Master volume 0–100% |
| 35 | `auth_lockout_sec` | u8 | Remaining lockout seconds |
| 36 | `raw_supercap_mv` | u16 | Uncalibrated supercap voltage |
| 38 | `raw_protection_mv` | u16 | Uncalibrated protection voltage |
| 40 | `cal_factor_v_x1000` | u16 | Supercap cal factor × 1000 |
| 42 | `cal_factor_p_x1000` | u16 | Protection cal factor × 1000 |
| 44 | `max_supercap_mv` | u16 | Configured max supercap voltage (mV) |

### Weld States

| Code | State | Description |
|------|-------|-------------|
| 0 | IDLE | Ready, waiting for trigger |
| 1 | ARMED | Contact detected (AUTO), waiting for S delay |
| 2 | PRE_FIRE | Charger disabling, settling |
| 3 | FIRING_P1 | Firing pulse 1 |
| 4 | PAUSE | Pause between pulses |
| 5 | FIRING_P2 | Firing pulse 2 |
| 6 | COOLDOWN | Charger re-enabling |
| 7 | BLOCKED | Blocked (low voltage or fault) |
| 8 | ERROR | Hardware fault |

---

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+) or newer
- Android device or emulator with **BLE support** (API 24+)
- A flashed [MyWeld ESP32](https://github.com/hisham2630/MyWeld-ESP32) device

### Build

```bash
# Clone repo
git clone https://github.com/hisham2630/MyWeld-Android.git
cd MyWeld-Android

# Open in Android Studio, or build via Gradle:
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### First Connection

1. Power up the ESP32 welder — it will advertise as **`MyWeld`**.
2. Open the app → **Scan** → tap the device.
3. Enter the PIN (factory default: **`1234`**).
4. Dashboard loads automatically after successful authentication.

---

## Project Structure

```
MyWeld-Android/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/myweld/app/
│           ├── MainActivity.kt             # Single-activity host
│           ├── MyWeldApp.kt                # Koin DI init
│           │
│           ├── data/
│           │   ├── ble/
│           │   │   ├── BleProtocol.kt      # Binary protocol encoder/decoder
│           │   │   ├── BleUuids.kt         # GATT service & characteristic UUIDs
│           │   │   ├── MyWeldBleManager.kt # Nordic BLE KTX wrapper
│           │   │   ├── BleConnectionState.kt
│           │   │   ├── AppEvent.kt         # One-shot UI events (snackbar, nav)
│           │   │   ├── OtaService.kt       # BLE OTA transfer engine
│           │   │   └── OtaModels.kt        # OTA state machine & data classes
│           │   │
│           │   ├── model/
│           │   │   ├── WelderStatus.kt     # Live status from STATUS packet
│           │   │   ├── WeldParams.kt       # Mutable weld parameters
│           │   │   ├── WeldPreset.kt       # Preset data class
│           │   │   ├── WeldState.kt        # Weld state enum
│           │   │   └── SavedDevice.kt      # Persisted paired device info
│           │   │
│           │   └── repository/
│           │       ├── WelderRepository.kt      # Interface
│           │       ├── WelderRepositoryImpl.kt  # BLE → domain mapping
│           │       ├── DeviceRepository.kt      # DataStore for saved device
│           │       └── GitHubReleaseChecker.kt  # GitHub API firmware release client
│           │
│           ├── di/
│           │   └── AppModule.kt            # Koin module definitions
│           │
│           ├── navigation/
│           │   ├── NavGraph.kt             # Compose navigation graph
│           │   └── Routes.kt               # Route constants
│           │
│           ├── ui/
│           │   ├── screens/
│           │   │   ├── ScanScreen.kt           # BLE device scanner
│           │   │   ├── DashboardScreen.kt      # Main live control screen
│           │   │   ├── PresetsScreen.kt        # Preset management
│           │   │   ├── SettingsScreen.kt       # Device configuration
│           │   │   ├── FirmwareUpdateScreen.kt # OTA firmware update UI
│           │   │   └── AboutScreen.kt          # App & firmware info
│           │   │
│           │   ├── components/
│           │   │   ├── ConnectionBadge.kt  # BLE status chip
│           │   │   ├── ParamCard.kt        # P1/T/P2 control card
│           │   │   ├── VoltageBar.kt       # Supercap charge bar
│           │   │   ├── WeldCounter.kt      # Session/total counter widget
│           │   │   └── StatusIndicator.kt  # Weld state badge
│           │   │
│           │   └── theme/
│           │       ├── Color.kt
│           │       ├── Theme.kt            # Material 3 dark/light themes
│           │       ├── Type.kt
│           │       └── Shape.kt
│           │
│           └── viewmodel/
│               ├── ScanViewModel.kt
│               ├── DashboardViewModel.kt
│               ├── PresetsViewModel.kt
│               ├── SettingsViewModel.kt
│               └── FirmwareViewModel.kt  # OTA update orchestration
│
├── gradle/
│   ├── libs.versions.toml      # Version catalog
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Permissions

The app requests the following Android permissions:

| Permission | Required For |
|------------|-------------|
| `BLUETOOTH_SCAN` | BLE device scanning (API 31+) |
| `BLUETOOTH_CONNECT` | BLE GATT connection (API 31+) |
| `BLUETOOTH` | BLE (API < 31) |
| `BLUETOOTH_ADMIN` | BLE (API < 31) |
| `ACCESS_FINE_LOCATION` | BLE scanning (required by Android for BLE on API < 31) |

The app uses **Accompanist Permissions** for runtime permission handling with clear user-facing rationale dialogs.

---

## Firmware Counterpart

The ESP32 firmware that runs on the welder device is available at:  
👉 **[github.com/hisham2630/MyWeld-ESP32](https://github.com/hisham2630/MyWeld-ESP32)**

It includes full documentation of the BLE binary protocol, hardware wiring, and the welding state machine.

---

## Acknowledgments

This companion app is part of the **MyWeld ESP32-S3** project — an adaptation of the **MyWeld V2.0 PRO** spot welder originally designed by **[Aka Kasyan](https://www.youtube.com/@akakasyan)**.

Thank you Aka Kasyan for the original hardware design, welding logic, and for inspiring the DIY spot welder community through your YouTube channel! 🙏

---

## License

This project is open source. See [LICENSE](LICENSE) for details.
