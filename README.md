<div align="center">

**简体中文** | [English](README_EN.md)

# YpRemote

YPOOFIT 跑步机蓝牙遥控器（Android 原生复刻版）

基于对官方 App 蓝牙协议的逆向分析，用 Kotlin + Jetpack Compose 复刻其遥控器功能。

</div>

## 功能

- 扫描并连接 YPOOFIT 跑步机（BLE / FTMS 0x1826）
- 速度调节：− / ＋ 长按连发、快捷挡位（2 / 4 / 6 / 8 km/h）
- 开始 / 暂停 / 停止，设备休眠自动唤醒重试
- 实时数据：速度、用时、距离、千卡、配速
- 跑步记录：本地保存、时间倒序、单条删除与一键清空
- 深色 / 浅色 / 跟随系统主题，中英文界面

## 系统要求

- Android 8.0（API 26）及以上
- 支持 FTMS 的 YPOOFIT 跑步机（YPTM-RZ 真机验证）

## 构建

```bash
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 协议文档

蓝牙协议逆向分析详见 [`docs/YPOOFIT蓝牙遥控协议逆向.md`](docs/YPOOFIT蓝牙遥控协议逆向.md)。
