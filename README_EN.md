<div align="center">

[简体中文](README.md) | **English**

# YpRemote

Bluetooth remote for YPOOFIT treadmills (native Android replica)

A replica of the official app's remote-control feature, rebuilt in Kotlin + Jetpack Compose on top of the BLE protocol reverse-engineered from the original APK.

</div>

## Features

- Scan and connect to YPOOFIT treadmills (BLE / FTMS 0x1826)
- Speed control: hold − / ＋ for repeat steps, quick presets (2 / 4 / 6 / 8 km/h)
- Start / pause / stop, with automatic wake-up retries for sleeping devices
- Live stats: speed, elapsed time, distance, kcal, pace
- Workout history: stored locally, newest first, per-item delete and clear-all
- Dark / light / system theme, English and Chinese UI

## Requirements

- Android 8.0 (API 26) or later
- A FTMS-capable YPOOFIT treadmill (verified on YPTM-RZ)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Protocol Notes

The reverse-engineered BLE protocol is documented in [`docs/YPOOFIT蓝牙遥控协议逆向.md`](docs/YPOOFIT蓝牙遥控协议逆向.md) (Chinese).
