package com.yp.remote.ble

import java.util.UUID

/**
 * 跑步机（protocolType 1/4）使用的 GATT 服务与特征。
 * 与原 App com.yp.lib_common.base.Constants 中的 huaWei* 系列一致（FTMS）。
 */
object FtmsUuids {
    /** Fitness Machine Service */
    val SERVICE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

    /** Treadmill Data（Notify）：实时运动数据 */
    val TREADMILL_DATA: UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")

    /** Fitness Machine Control Point（Write）：全部控制指令 */
    val CONTROL_POINT: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

    /** Supported Speed Range（Read）：min/max 速度，各 u16 小端，0.01 km/h */
    val SUPPORTED_SPEED_RANGE: UUID = UUID.fromString("00002ad4-0000-1000-8000-00805f9b34fb")

    /** Supported Inclination Range（Read）：min/max 坡度，各 s16 小端，0.1% */
    val SUPPORTED_INCLINE_RANGE: UUID = UUID.fromString("00002ad5-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** 厂商解锁特征（原 App unlock 流程写入，位于 0x1826 服务内） */
    val VENDOR_UNLOCK: UUID = UUID.fromString("d18d2c10-c44c-11e8-a355-529269fb1459")

    /** ESL 厂商服务（protocol 2 机型使用；YPTM-RZ 同时暴露了该服务，用于对比测试） */
    val ESL_SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val ESL_NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val ESL_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    /** Device Information */
    val DIS_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REV: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
}

/** 控制点操作码（FTMS 标准，原 App 直接以字节数组写入 0x2AD9） */
object FtmsOpCode {
    const val REQUEST_CONTROL: Byte = 0x00
    const val RESET: Byte = 0x01
    const val SET_TARGET_SPEED: Byte = 0x02
    const val SET_TARGET_INCLINE: Byte = 0x03
    const val START_RESUME: Byte = 0x07
    const val STOP_PAUSE: Byte = 0x08

    /** Stop or Pause 的参数：1=停止 */
    const val STOP_PARAM: Byte = 0x01

    /** Stop or Pause 的参数：2=暂停 */
    const val PAUSE_PARAM: Byte = 0x02
}
