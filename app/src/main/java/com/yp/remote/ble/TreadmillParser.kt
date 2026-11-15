package com.yp.remote.ble

import kotlin.random.Random

/**
 * 0x2ACD Treadmill Data 通知解析（标准 FTMS 小端 flags）。
 *
 * 真机 YPTM-RZ 验证：flags `84 25` = 0x2584（bit2 距离/bit7 能量/bit8 心率/bit10 用时），
 * 字段偏移全部吻合。原 App 的"大端读取 + MSB 下标"在数学上恰好还原标准位序（逆向文档 §6）。
 * 标准保留位 bit13 被厂商用作"步数"（u24）。
 */
object TreadmillParser {

    /** 一次通知解析出的运动数据快照（未上报的字段为 null） */
    data class Sample(
        /** 显示速度（沿用原 App 规则：≤0.7 km/h 显示为 0） */
        val speedKmh: Float? = null,
        /** 原始速度（不钳制，供运行/停止状态判定，避免减速中读数抖动干扰） */
        val rawSpeedKmh: Float? = null,
        val distanceMeter: Int? = null,
        val slopePct: Int? = null,
        val kcal: Float? = null,
        val heartRate: Int? = null,
        val elapsedSec: Long? = null,
        val steps: Int? = null,
    )

    /** 解析入口；字节不足或结构异常时返回已解析出的部分（速度恒尝试解析） */
    fun parse(data: ByteArray): Sample {
        if (data.size < 4) return Sample()
        val flags = (data[1].toInt() and 0xFF) shl 8 or (data[0].toInt() and 0xFF)
        val r = Reader(data, 2)

        var speed: Float? = null
        var rawSpeed: Float? = null
        var distance: Int? = null
        var slope: Int? = null
        var kcal: Float? = null
        var heartRate: Int? = null
        var elapsed: Long? = null
        var steps: Int? = null

        if (flags and (1 shl 0) == 0) {
            // 瞬时速度恒存在；原 App 对 <=0.7 km/h 的读数显示为 0（仅影响显示，不影响状态判定）
            val raw = r.u16()
            val v = raw / 100f
            rawSpeed = v
            speed = if (v <= 0.7f) 0f else v
        }
        if (flags and (1 shl 1) != 0) r.skip(2) // 平均速度（忽略）
        if (flags and (1 shl 2) != 0) distance = r.u24()
        if (flags and (1 shl 3) != 0) {
            slope = r.s16() / 10
            r.skip(2) // 坡度角（忽略）
        }
        if (flags and (1 shl 4) != 0) r.skip(4) // 海拔增益 + 损失
        if (flags and (1 shl 5) != 0) r.skip(1) // 即时配速
        if (flags and (1 shl 6) != 0) r.skip(1) // 平均配速
        if (flags and (1 shl 7) != 0) {
            kcal = r.u16().toFloat()
            r.skip(3) // 每小时 + 每分钟
        }
        if (flags and (1 shl 8) != 0) heartRate = r.u8()
        if (flags and (1 shl 9) != 0) r.skip(1) // MET
        if (flags and (1 shl 10) != 0) elapsed = r.u16().toLong()
        if (flags and (1 shl 11) != 0) r.skip(2) // 剩余时间
        if (flags and (1 shl 12) != 0) r.skip(4) // 功率 + 力
        if (flags and (1 shl 13) != 0) steps = r.u24() // 厂商扩展：步数

        return TreadmillParser.Sample(
            speedKmh = speed,
            rawSpeedKmh = rawSpeed,
            distanceMeter = distance,
            slopePct = slope,
            kcal = kcal,
            heartRate = heartRate,
            elapsedSec = elapsed,
            steps = steps,
        )
    }

    /** 带边界保护的顺序读取器 */
    private class Reader(private val data: ByteArray, private var pos: Int) {
        fun u8(): Int {
            if (pos >= data.size) return 0
            return data[pos++].toInt() and 0xFF
        }

        fun u16(): Int {
            val lo = u8()
            val hi = u8()
            return lo or (hi shl 8)
        }

        fun s16(): Int = u16().toShort().toInt()

        fun u24(): Int {
            val lo = u16()
            val hi = u8()
            return lo or (hi shl 16)
        }

        fun skip(n: Int) {
            pos += n
        }
    }
}

/** 控制帧构造（全部写入 0x2AD9，原始字节、无帧头校验） */
object TreadmillCommands {
    /** 请求控制权（FTMS 要求先于一切控制指令） */
    fun requestControl(): ByteArray = byteArrayOf(FtmsOpCode.REQUEST_CONTROL)

    /** 设定速度：0.01 km/h 小端。6.0 km/h → [02, 58, 02] */
    fun setSpeed(kmh: Double): ByteArray {
        val v = (kmh * 100).toInt().coerceIn(0, 0xFFFF)
        return byteArrayOf(FtmsOpCode.SET_TARGET_SPEED, v.toByte(), (v shr 8).toByte())
    }

    /** 设定坡度：0.1% 小端有符号。3.0% → [03, 1E, 00] */
    fun setIncline(percent: Int): ByteArray {
        val v = (percent * 10).coerceIn(-32768, 32767)
        return byteArrayOf(
            FtmsOpCode.SET_TARGET_INCLINE,
            v.toByte(),
            (v shr 8).toByte(),
        )
    }

    /** 开始/恢复 */
    fun start(): ByteArray = byteArrayOf(FtmsOpCode.START_RESUME)

    /** 停止 */
    fun stop(): ByteArray = byteArrayOf(FtmsOpCode.STOP_PAUSE, FtmsOpCode.STOP_PARAM)

    /** 暂停 */
    fun pause(): ByteArray = byteArrayOf(FtmsOpCode.STOP_PAUSE, FtmsOpCode.PAUSE_PARAM)

    /**
     * YPTM 系列解锁帧（protocol 4）：原 App unlock() 对该机型固定发送，
     * 写入 VENDOR_UNLOCK 特征。真机验证控制前必须先发。
     */
    fun unlockYptm(): ByteArray = byteArrayOf(0x01, 0x00, 0x02, 0x02, 0x00, 0x05, 0x01, 0x01)

    // ---- ESL（protocol 2）长帧：A9 + 命令 + 长度 + 数据 + XOR 校验 ----

    /** ESL 帧封装：A9 + 各段字节 + 整体 XOR 校验（原 App f8.g.B） */
    fun eslFrame(vararg body: Int): ByteArray {
        val bytes = ByteArray(body.size + 1) { i ->
            if (i < body.size) body[i].toByte() else 0
        }
        var xor = 0
        for (i in body.indices) xor = xor xor (body[i] and 0xFF)
        bytes[body.size] = xor.toByte()
        return bytes
    }

    /** ESL 握手请求：A9 08 01 N XOR（N 为随机数，原 App sendYiShiOne） */
    fun eslHandshakeRequest(random: Int): ByteArray = eslFrame(0xA9, 0x08, 0x01, random and 0xFF)

    /**
     * 校验握手应答（原 App checkYiShiData）：8 字节，
     * 字节2 状态位为 0x01 视为无效；a/b/c/d 依 random%6 的排列关系校验，返回 d 供下一步使用。
     */
    fun eslVerifyReply(reply: ByteArray, random: Int): Int? {
        if (reply.size != 8) return null
        if ((reply[2].toInt() and 0xFF) == 0x01) return null
        val a = reply[3].toInt() and 0xFF
        val b = reply[4].toInt() and 0xFF
        val c = reply[5].toInt() and 0xFF
        val d = reply[6].toInt() and 0xFF
        val mod256 = { x: Int -> if (x > 256) x % 256 else x }
        val ok = when (random % 6) {
            0 -> mod256(a + b) == c && mod256(a * b) == d
            1 -> mod256(a * b) == c && mod256(a + b) == d
            2 -> mod256(b + c) == a && mod256(b * c) == d
            3 -> mod256(b * c) == a && mod256(b + c) == d
            4 -> mod256(c * d) == b && mod256(c + d) == a
            else -> mod256(c * d) == a && mod256(c + d) == b
        }
        return if (ok) d else null
    }

    /**
     * 生成握手确认数据（原 App generateYiShiCheckData）：随机 C/C2 及其和/积（mod 256），
     * 按 d%6 的排列组成 4 字节。
     */
    fun eslHandshakeConfirm(d: Int): ByteArray {
        val c1 = Random.nextInt(256)
        val c2 = Random.nextInt(256)
        val mod256 = { x: Int -> if (x > 256) x % 256 else x }
        val sum = mod256(c1 + c2)
        val product = mod256(c1 * c2)
        val body = when (d % 6) {
            0 -> intArrayOf(c1, c2, sum, product)
            1 -> intArrayOf(c1, c2, product, sum)
            2 -> intArrayOf(sum, c1, c2, product)
            3 -> intArrayOf(product, c1, c2, sum)
            4 -> intArrayOf(sum, product, c1, c2)
            else -> intArrayOf(product, sum, c1, c2)
        }
        return eslFrame(0xA9, 0x08, 0x04, *body)
    }

    /** ESL 开始：a3 / 01 / 01 → A9 A3 01 01 0A */
    fun eslStart(): ByteArray = eslFrame(0xA9, 0xA3, 0x01, 0x01)

    /** ESL 停止：a3 / 01 / 00 → A9 A3 01 00 0B */
    fun eslStop(): ByteArray = eslFrame(0xA9, 0xA3, 0x01, 0x00)

    /** ESL 会话帧1（数据帧到来后补发）：A9 F0 01 01 59 */
    fun eslSessionOpen1(): ByteArray = eslFrame(0xA9, 0xF0, 0x01, 0x01)

    /** ESL 会话帧2：A9 A0 03 01 01 00 0A */
    fun eslSessionOpen2(): ByteArray = eslFrame(0xA9, 0xA0, 0x03, 0x01, 0x01, 0x00)

    /** ESL 速度：01 / 01 / (speed*10) → A9 01 01 XX XX。4.0km/h = A9 01 01 28 81 */
    fun eslSpeed(kmh: Double): ByteArray {
        val v = ((kmh * 10).toInt()).coerceIn(0, 0xFF)
        return eslFrame(0xA9, 0x01, 0x01, v)
    }

    /** ESL 数据帧（FFF1，17 字节，byte1=0x02）解析；非数据帧返回 null */
    fun parseEslData(frame: ByteArray): TreadmillParser.Sample? {
        if (frame.size != 17) return null
        if ((frame[1].toInt() and 0xFF) != 0x02) return null
        val be = { i: Int, n: Int ->
            var v = 0
            for (k in 0 until n) v = (v shl 8) or (frame[i + k].toInt() and 0xFF)
            v
        }
        val heartRate = be(3, 1)
        val kcal = be(4, 3) / 100f
        val distance = be(7, 3)
        val steps = be(10, 3)
        val elapsed = be(13, 2).toLong()
        val speed = be(15, 1) / 10f
        val slope = frame[16].toInt() and 0x1F
        return TreadmillParser.Sample(
            speedKmh = speed,
            rawSpeedKmh = speed,
            distanceMeter = distance,
            slopePct = slope,
            kcal = kcal,
            heartRate = heartRate,
            elapsedSec = elapsed,
            steps = steps,
        )
    }
}
