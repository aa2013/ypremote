package com.yp.remote.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.yp.remote.data.WorkoutRecord
import com.yp.remote.data.WorkoutStore
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID

/** 连接状态 */
enum class ConnState { SCANNING, DISCONNECTED, CONNECTING, READY }

/** 开始键自动唤醒状态：NONE 无动作 / WAKING 重试唤醒中 / SLEEPING 重试耗尽（设备休眠） */
enum class WakeState { NONE, WAKING, SLEEPING }

/** 设备速度/坡度范围（来自 0x2AD4 / 0x2AD5） */
data class DeviceRange(
    val speedMinKmh: Double = 0.0,
    val speedMaxKmh: Double = 0.0,
    val inclineMinPct: Int = 0,
    val inclineMaxPct: Int = 0,
)

/**
 * 跑步机蓝牙管理单例。
 * Treadmill BLE manager singleton: scan / connect / notify / control commands.
 *
 * 控制序列严格复刻原 App 真机抓包（HCI btsnoop 验证）：
 * 订阅 2ACD → 读型号 → 解锁帧（无应答写）→ 读 2AD4/2AD5 → 请求控制（带应答写）。
 * 不订阅 2AD9 通知（原 App 盲发控制指令）、不触碰 FFF0（ESL 与本机型无关）；
 * 所有控制指令一次性写入，无周期重发。
 */
@SuppressLint("MissingPermission")
object TreadmillManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state

    private val _devices = MutableStateFlow<List<ScanResult>>(emptyList())
    val devices: StateFlow<List<ScanResult>> = _devices

    private val _sample = MutableStateFlow(TreadmillParser.Sample())
    val sample: StateFlow<TreadmillParser.Sample> = _sample

    private val _rawNotify = MutableStateFlow("")
    val rawNotify: StateFlow<String> = _rawNotify

    private val _range = MutableStateFlow(DeviceRange())
    val range: StateFlow<DeviceRange> = _range

    /** 开始键唤醒状态（UI 提示"唤醒中/设备休眠"） */
    private val _wakeState = MutableStateFlow(WakeState.NONE)
    val wakeState: StateFlow<WakeState> = _wakeState

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName

    /** 当前连接目标 MAC（连接中/已连接都非空，用于设备列表防重复连接） */
    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress

    /** 暂停/停止减速过渡：指令已发、等待设备速度归零（期间播放/停止键禁用） */
    private val _finishing = MutableStateFlow(false)
    val finishing: StateFlow<Boolean> = _finishing

    /** 最近一次 GATT 操作的描述（调试页显示） */
    private val _lastOp = MutableStateFlow("—")
    val lastOp: StateFlow<String> = _lastOp

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

    /** 常规开始后等待皮带转动的窗口（ms） */
    private const val START_WATCH_MS = 2000L

    /** 每轮唤醒重试后等待窗口（ms） */
    private const val WAKE_WATCH_MS = 3000L

    /** 唤醒重试轮数 */
    private const val WAKE_ROUNDS = 3

    /** 减速过渡兜底超时（设备无响应时防止播放/停止键永久禁用） */
    private const val FINISH_TIMEOUT_MS = 8000L

    /** 停止确认：连续收到 N 条原始速度为 0 的通知（防瞬时 0 读数误判） */
    private const val FINISH_ZERO_NOTIFIES = 2

    private var wakeJob: kotlinx.coroutines.Job? = null
    private var finishJob: kotlinx.coroutines.Job? = null

    /** 减速过渡中"原始速度=0"的通知计数 */
    private var finishZeroNotifies = 0

    // ---- 跑步会话（开始→停止为一次会话，用于生成跑步记录） ----
    private var sessionActive = false

    /** 本次会话出现过的最大原始速度（km/h） */
    private var sessionMaxSpeed = 0f

    // ---- 指令队列（同一时刻只允许一个 GATT 操作在途） ----
    private sealed class Op {
        /** noResponse=true 走 WriteCmd（无应答写），对应原 App 解锁帧的写入方式 */
        class Write(
            val serviceUuid: UUID,
            val uuid: UUID,
            val data: ByteArray,
            val noResponse: Boolean = false,
        ) : Op()
        class WriteDesc(val serviceUuid: UUID, val charUuid: UUID, val descUuid: UUID, val value: ByteArray) : Op()
        class Read(val serviceUuid: UUID, val uuid: UUID, val onValue: ((ByteArray) -> Unit)? = null) : Op()
    }

    private val opQueue = ArrayDeque<Op>()
    private var opBusy = false

    private fun adapter(): BluetoothAdapter? {
        val manager = appContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 供 Toast 等系统 UI 使用 */
    fun context(): Context? = appContext

    // ================= 扫描 =================

    /**
     * 扫描模式（复刻原 App，协议文档 §4.2）：
     * scanByService=false（默认）= 原 App 行为——无过滤扫描全部广播，
     * 按品牌名关键词回调过滤（大小写不敏感 contains）；
     * scanByService=true = FTMS 0x1826 服务硬件过滤兜底（可发现其他品牌 FTMS 设备）。
     */
    fun startScan(scanByService: Boolean = false) {
        val a = adapter() ?: return
        if (!a.isEnabled) return
        stopScan()
        _devices.value = emptyList()
        val filterList = if (scanByService) {
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(FtmsUuids.SERVICE))
                    .build(),
            )
        } else {
            emptyList()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!scanByService && !matchesBrandName(result.device?.name)) {
                    Log.d(TAG, "扫描丢弃（品牌名不匹配）: ${result.device?.name} ${result.device?.address}")
                    return
                }
                val list = _devices.value.toMutableList()
                val existing = list.indexOfFirst { it.device.address == result.device.address }
                if (existing >= 0) list[existing] = result else list.add(result)
                _devices.value = list
            }
        }
        scanCallback = cb
        _state.value = ConnState.SCANNING
        a.bluetoothLeScanner.startScan(filterList, settings, cb)
    }

    /** 品牌名关键词（原 App BluetoothScanUtils.scanBluetooth 的 FastBLE 模糊匹配集合，含大小写变体） */
    private val brandNameKeywords = listOf(
        "yp", "lawin", "sld", "esl", "ypem", "ypbm", "p1", "l8_p1pro", "-yj-",
    )

    /** 原 App 同款名称过滤：空名一律丢弃（FastBLE 把 null 名当 "" 处理） */
    private fun matchesBrandName(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        val lower = name.lowercase()
        return brandNameKeywords.any { lower.contains(it) }
    }

    fun stopScan() {
        scanCallback?.let { adapter()?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        if (_state.value == ConnState.SCANNING) _state.value = ConnState.DISCONNECTED
    }

    // ================= 连接 =================

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectedAddress.value = device.address
        _state.value = ConnState.CONNECTING
        gatt?.close()
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        connectGeneration++
        cancelWake()
        finishSession()
        clearFinishing()
        eslState = EslState.IDLE
        eslRetries = 0
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnState.DISCONNECTED
        _connectedName.value = null
        _connectedAddress.value = null
        _sample.value = TreadmillParser.Sample()
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectGeneration++
                    finishSession()
                    clearFinishing()
                    _connectedAddress.value = null
                    _state.value = ConnState.DISCONNECTED
                    _connectedName.value = null
                    g.close()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered status=$status services=${g.services.joinToString { it.uuid.toString() }}")
            if (status != BluetoothGatt.GATT_SUCCESS) return
            enableNotify(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "onDescriptorWrite ${d.uuid} status=$status")
            finishOp()
            if (status == BluetoothGatt.GATT_SUCCESS && d.uuid == FtmsUuids.CCCD &&
                d.characteristic.uuid == FtmsUuids.TREADMILL_DATA
            ) {
                // 复刻原 App 真机序列（HCI 抓包逐帧验证）：读型号 → 解锁帧（无应答写）→
                // 读速度/坡度范围 → 请求控制（带应答写）。步骤间 300ms 节奏。
                // 不订阅 2AD9 通知（原 App 盲发）、不触碰 FFF0（ESL 与本机型无关）。
                val gen = connectGeneration
                enqueueDelayed(gen, 300) {
                    Op.Read(FtmsUuids.DIS_SERVICE, FtmsUuids.MODEL_NUMBER) { bytes ->
                        Log.d(TAG, "型号=${String(bytes)}")
                    }
                }
                enqueueDelayed(gen, 300) {
                    // YPTM 系列必须先发解锁帧（无应答写），否则控制点指令被固件忽略
                    Op.Write(
                        FtmsUuids.SERVICE,
                        FtmsUuids.VENDOR_UNLOCK,
                        TreadmillCommands.unlockYptm(),
                        noResponse = true,
                    )
                }
                enqueueDelayed(gen, 300) {
                    Op.Read(FtmsUuids.SERVICE, FtmsUuids.SUPPORTED_SPEED_RANGE) { bytes ->
                        parseSpeedRange(bytes)?.let {
                            _range.value = _range.value.copy(speedMinKmh = it.first, speedMaxKmh = it.second)
                        }
                    }
                }
                enqueueDelayed(gen, 300) {
                    Op.Read(FtmsUuids.SERVICE, FtmsUuids.SUPPORTED_INCLINE_RANGE) { bytes ->
                        parseInclineRange(bytes)?.let {
                            _range.value = _range.value.copy(inclineMinPct = it.first, inclineMaxPct = it.second)
                        }
                    }
                }
                enqueueDelayed(gen, 300) {
                    Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.requestControl())
                }
                _state.value = ConnState.READY
                _connectedName.value = g.device.name
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            Log.d(TAG, "onCharacteristicWrite ${c.uuid} status=$status")
            finishOp()
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            Log.d(TAG, "onCharacteristicRead ${c.uuid} status=$status value=${value.joinToString { "%02X".format(it) }}")
            handleRead(c, value)
            finishOp()
        }

        @Deprecated("legacy path")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            handleRead(c, c.value ?: ByteArray(0))
            finishOp()
        }

        // Android 13+ 通知回调
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleChanged(c, value)
        }

        // Android 13 以下通知回调
        @Deprecated("legacy path")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handleChanged(c, c.value ?: ByteArray(0))
        }
    }

    private fun handleChanged(c: BluetoothGattCharacteristic, value: ByteArray) {
        val hex = value.joinToString(" ") { "%02X".format(it) }
        when (c.uuid) {
            FtmsUuids.TREADMILL_DATA -> handleNotify(value)
            FtmsUuids.CONTROL_POINT -> {
                // 控制点应答：0x80 = Response Code + 原操作码 + 结果码（1成功 2不支持 3参数错 4失败 5不允许）
                Log.d(TAG, "控制点应答 ← $hex")
                _lastOp.value = "控制点应答 ← $hex"
            }
            FtmsUuids.ESL_NOTIFY -> handleEslFrame(value, hex)
            else -> Log.d(TAG, "notify ${c.uuid} ← $hex")
        }
    }

    // ================= ESL（FFF0/FFF1/FFF2）原生协议 =================

    /** ESL 握手状态 */
    private enum class EslState { IDLE, REQUESTED, UNLOCKED }

    private var eslState = EslState.IDLE
    private var eslRandom = 0

    /** 握手重试计数（调试面板可手动清零后重新握手） */
    var eslRetries = 0

    /** 发起 ESL 握手（连接后自动调用，最多重试 3 次） */
    fun eslStartHandshake() {
        if (eslRetries >= 3) {
            Log.w(TAG, "ESL 握手重试次数用尽")
            _lastOp.value = "ESL握手失败（重试 3 次）"
            return
        }
        eslState = EslState.REQUESTED
        eslRandom = Random.nextInt(256)
        val frame = TreadmillCommands.eslHandshakeRequest(eslRandom)
        Log.d(TAG, "ESL握手请求 random=$eslRandom frame=${frame.joinToString(" ") { "%02X".format(it) }}")
        _lastOp.value = "ESL握手请求 N=$eslRandom"
        enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, frame))
        // 5s 内无有效应答则重试（对应原 App againSendYiShiOne）
        scope.launch {
            delay(5000)
            if (eslState == EslState.REQUESTED) {
                eslRetries++
                Log.d(TAG, "ESL握手超时重试 #${eslRetries}")
                eslStartHandshake()
            }
        }
    }

    private fun handleEslFrame(frame: ByteArray, hex: String) {
        Log.d(TAG, "FFF1 ← $hex (state=$eslState)")
        _rawNotify.value = "FFF1: $hex"
        // 机器回 NAK → 重发握手请求（原 App againSendYiShiOne）
        if (frame.size == 5 && frame[0].toInt() and 0xFF == 0xA9 &&
            frame[1].toInt() and 0xFF == 0x08 && frame[3].toInt() and 0xFF == 0xFF
        ) {
            Log.d(TAG, "ESL NAK，重发握手")
            eslStartHandshake()
            return
        }
        // 17 字节数据帧：byte1=0x02
        if (frame.size == 17 && (frame[1].toInt() and 0xFF) == 0x02) {
            if (eslState == EslState.REQUESTED) {
                // 握手完成后的会话帧（原 App 顺序：f0 → a0）
                eslState = EslState.UNLOCKED
                enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslSessionOpen1()))
                enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslSessionOpen2()))
                Log.d(TAG, "ESL 已解锁，补发会话帧")
            }
            TreadmillCommands.parseEslData(frame)?.let {
                _sample.value = it
            }
            return
        }
        // 8 字节握手应答
        if (eslState == EslState.REQUESTED && frame.size == 8) {
            val d = TreadmillCommands.eslVerifyReply(frame, eslRandom)
            if (d == null) {
                Log.d(TAG, "ESL 握手应答校验失败，重发")
                eslStartHandshake()
            } else {
                Log.d(TAG, "ESL 握手应答校验通过 d=$d，发送确认")
                enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslHandshakeConfirm(d)))
            }
            return
        }
    }

    /** ESL 通道控制（握手完成后可用） */
    fun eslStart() {
        enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslStart()))
    }

    fun eslStop() {
        enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslStop()))
    }

    fun eslSetSpeed(kmh: Double) {
        enqueue(Op.Write(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_WRITE, TreadmillCommands.eslSpeed(kmh)))
    }

    private fun handleRead(c: BluetoothGattCharacteristic, value: ByteArray) {
        val op = currentOp as? Op.Read ?: return
        op.onValue?.invoke(value)
    }

    private var currentOp: Op? = null

    private fun handleNotify(value: ByteArray) {
        val hex = value.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "2ACD ← $hex")
        _rawNotify.value = hex
        val parsed = TreadmillParser.parse(value)
        _sample.value = parsed
        // 会话期间跟踪本次跑步的最大速度（记录展示用）
        if (sessionActive) {
            val v = parsed.rawSpeedKmh ?: 0f
            if (v > sessionMaxSpeed) sessionMaxSpeed = v
        }
        // 减速过渡解除：连续两条"原始速度=0"的通知才算真停。
        // 设备减速开始会先报一条瞬时 0；通知间隔可达 1.5s，不能用时间窗轮询缓存值。
        if (_finishing.value) {
            if ((parsed.rawSpeedKmh ?: 0f) > 0f) {
                finishZeroNotifies = 0
            } else {
                finishZeroNotifies++
                if (finishZeroNotifies >= FINISH_ZERO_NOTIFIES) clearFinishing()
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt) {
        val service = g.getService(FtmsUuids.SERVICE)
        if (service == null) {
            Log.e(TAG, "未发现 0x1826 服务，设备可能不是 FTMS 跑步机")
            _lastOp.value = "未发现 0x1826 服务"
            return
        }
        val c = service.getCharacteristic(FtmsUuids.TREADMILL_DATA)
        if (c == null) {
            Log.e(TAG, "未发现 0x2ACD 特征")
            _lastOp.value = "未发现 0x2ACD 特征"
            return
        }
        g.setCharacteristicNotification(c, true)
        enqueue(Op.WriteDesc(FtmsUuids.SERVICE, FtmsUuids.TREADMILL_DATA, FtmsUuids.CCCD, byteArrayOf(0x01, 0x00)))
    }

    // ---- 操作队列 ----

    private fun enqueue(op: Op) {
        synchronized(opQueue) { opQueue.addLast(op) }
        drain()
    }

    private fun drain() {
        val g = gatt ?: run { synchronized(opQueue) { opQueue.clear() }; return }
        val op = synchronized(opQueue) {
            if (opBusy) return
            val next = opQueue.pollFirst() ?: return
            opBusy = true
            next
        }
        currentOp = op
        _lastOp.value = when (op) {
            is Op.Write -> "写 ${shortUuid(op.uuid)} ← ${op.data.joinToString(" ") { "%02X".format(it) }}"
            is Op.WriteDesc -> "订阅 ${shortUuid(op.charUuid)} ← ${op.value.joinToString(" ") { "%02X".format(it) }}"
            is Op.Read -> "读 ${shortUuid(op.uuid)}"
        }
        Log.d(TAG, "drain: ${_lastOp.value}")
        when (op) {
            is Op.Write -> {
                val c = g.getService(op.serviceUuid)?.getCharacteristic(op.uuid)
                if (c == null) {
                    Log.w(TAG, "特征不存在: ${op.uuid}")
                    finishOp()
                    return
                }
                val type = if (op.noResponse) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                // API 33+ 新重载返回 int 状态码；旧重载返回 boolean
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (g.writeCharacteristic(c, op.data, type) != BluetoothGatt.GATT_SUCCESS) {
                        finishOp()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    c.value = op.data
                    @Suppress("DEPRECATION")
                    if (!g.writeCharacteristic(c)) finishOp()
                }
            }
            is Op.WriteDesc -> {
                val c = g.getService(op.serviceUuid)?.getCharacteristic(op.charUuid)
                val d = c?.getDescriptor(op.descUuid)
                if (d == null) {
                    finishOp()
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (g.writeDescriptor(d, op.value) != BluetoothGatt.GATT_SUCCESS) finishOp()
                } else {
                    @Suppress("DEPRECATION")
                    d.value = op.value
                    @Suppress("DEPRECATION")
                    if (!g.writeDescriptor(d)) finishOp()
                }
            }
            is Op.Read -> {
                val c = g.getService(op.serviceUuid)?.getCharacteristic(op.uuid)
                if (c == null) {
                    finishOp()
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!g.readCharacteristic(c)) finishOp()
                } else {
                    @Suppress("DEPRECATION")
                    if (!g.readCharacteristic(c)) finishOp()
                }
            }
        }
    }

    private fun finishOp() {
        synchronized(opQueue) { opBusy = false }
        drain()
    }

    // ================= 控制指令 =================

    /** 速度/坡度/开始/停止/暂停均为一次性写入（真机抓包确认原 App 无周期重发） */
    fun setSpeed(kmh: Double) {
        enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.setSpeed(kmh)))
    }

    fun setIncline(percent: Int) {
        enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.setIncline(percent)))
    }

    fun start() {
        enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.start()))
    }

    fun stop() {
        cancelWake()
        finishSession()
        markFinishing()
        enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.stop()))
    }

    fun pause() {
        cancelWake()
        markFinishing()
        enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.pause()))
    }

    // ================= 开始键自动唤醒 =================

    /**
     * 点开始：先常规下发 07；若 2s 内皮带未转（设备疑似休眠），自动重发
     * 解锁帧→请求控制→开始，最多 [WAKE_ROUNDS] 轮。全部失败置 SLEEPING，由 UI 提示
     * "设备休眠，请手动激活"。唤醒成功（2ACD 速度 > 0）即恢复正常状态。
     */
    fun startOrWake() {
        cancelWake()
        _wakeState.value = WakeState.WAKING
        wakeJob = scope.launch {
            enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.start()))
            if (awaitBeltRunning(START_WATCH_MS)) {
                _wakeState.value = WakeState.NONE
                beginSession()
                return@launch
            }
            repeat(WAKE_ROUNDS) {
                enqueue(
                    Op.Write(
                        FtmsUuids.SERVICE,
                        FtmsUuids.VENDOR_UNLOCK,
                        TreadmillCommands.unlockYptm(),
                        noResponse = true,
                    ),
                )
                enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.requestControl()))
                enqueue(Op.Write(FtmsUuids.SERVICE, FtmsUuids.CONTROL_POINT, TreadmillCommands.start()))
                if (awaitBeltRunning(WAKE_WATCH_MS)) {
                    _wakeState.value = WakeState.NONE
                    beginSession()
                    return@launch
                }
            }
            Log.w(TAG, "唤醒重试 ${WAKE_ROUNDS} 轮后皮带仍未转动，判定设备休眠")
            _wakeState.value = WakeState.SLEEPING
        }
    }

    /** 轮询 2ACD 原始速度是否 > 0（皮带转动即视为设备已唤醒） */
    private suspend fun awaitBeltRunning(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if ((_sample.value.rawSpeedKmh ?: 0f) > 0f) return true
            delay(100)
        }
        return (_sample.value.rawSpeedKmh ?: 0f) > 0f
    }

    /** 取消唤醒流程（断开/停止/暂停/重新点击开始时调用） */
    fun cancelWake() {
        wakeJob?.cancel()
        wakeJob = null
        if (_wakeState.value != WakeState.NONE) _wakeState.value = WakeState.NONE
    }

    /**
     * 进入减速过渡态：播放/停止键禁用。
     * 解除条件 = 连续收到 [FINISH_ZERO_NOTIFIES] 条"原始速度=0"的通知（见 handleNotify），
     * 或 [FINISH_TIMEOUT_MS] 超时兜底。
     */
    private fun markFinishing() {
        _finishing.value = true
        finishZeroNotifies = 0
        finishJob?.cancel()
        finishJob = scope.launch {
            delay(FINISH_TIMEOUT_MS)
            Log.w(TAG, "减速过渡超时解除（设备可能无响应）")
            _finishing.value = false
        }
    }

    private fun clearFinishing() {
        finishJob?.cancel()
        finishJob = null
        if (_finishing.value) _finishing.value = false
    }

    // ================= 跑步会话与记录 =================

    /** 会话开始（皮带确认转动后调用；暂停后恢复不重置累计） */
    private fun beginSession() {
        if (!sessionActive) {
            sessionActive = true
            sessionMaxSpeed = 0f
        }
    }

    /**
     * 会话收尾并按需落一条跑步记录（停止/退出遥控器/断连时调用）。
     * 开始时间按"当前时刻 - 设备上报用时"回推；用时与距离都为 0 视为误触，不记录。
     */
    fun finishSession() {
        if (!sessionActive) return
        sessionActive = false
        val now = System.currentTimeMillis()
        val s = _sample.value
        val duration = s.elapsedSec ?: 0L
        val distance = s.distanceMeter ?: 0
        if (duration <= 0L && distance <= 0) return
        WorkoutStore.add(
            WorkoutRecord(
                id = now,
                startTime = now - duration * 1000,
                durationSec = duration,
                distanceMeter = distance,
                kcal = (s.kcal ?: 0f).toInt(),
                maxSpeedKmh = sessionMaxSpeed,
            ),
        )
        sessionMaxSpeed = 0f
    }

    // ================= 调试：原始帧测试 =================

    /** 调试面板用：向任意服务/特征写原始字节（noResponse=true 复刻解锁帧写法） */
    fun sendRaw(service: UUID, char: UUID, data: ByteArray, label: String, noResponse: Boolean = false) {
        val hex = data.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "TEST[$label] $service/$char ← $hex")
        enqueue(Op.Write(service, char, data, noResponse))
    }

    /** 调试面板用：订阅任意特征的通知 */
    fun subscribeRaw(service: UUID, char: UUID) {
        val g = gatt ?: return
        val c = g.getService(service)?.getCharacteristic(char) ?: run {
            Log.w(TAG, "TEST 特征不存在: $service/$char")
            _lastOp.value = "特征不存在 $char"
            return
        }
        g.setCharacteristicNotification(c, true)
        enqueue(Op.WriteDesc(service, char, FtmsUuids.CCCD, byteArrayOf(0x01, 0x00)))
    }

    /** 调试面板用：读任意特征 */
    fun readRaw(service: UUID, char: UUID) {
        enqueue(Op.Read(service, char) { bytes ->
            Log.d(TAG, "TEST read $char = ${bytes.joinToString(" ") { "%02X".format(it) }}")
            _lastOp.value = "读 $char ← ${bytes.joinToString(" ") { "%02X".format(it) }}"
        })
    }

    /** 连接代次：断开/重连后使仍在途的延迟入队失效 */
    private var connectGeneration = 0

    /** 延迟入队（复刻原 App 连接序列的 300ms 节奏，gen 失配则丢弃） */
    private fun enqueueDelayed(gen: Int, delayMs: Long, op: () -> Op) {
        scope.launch {
            delay(delayMs)
            if (gen == connectGeneration) enqueue(op())
        }
    }

    /** UUID 缩短显示（调试用） */
    private fun shortUuid(uuid: UUID): String = uuid.toString().substring(0, 8).uppercase()

    private const val TAG = "YpRemote"

    // ================= 范围解析 =================

    /** 0x2AD4：min/max 各 u16 小端，0.01 km/h；解析失败返回 null */
    private fun parseSpeedRange(bytes: ByteArray): Pair<Double, Double>? {
        if (bytes.size < 4) return null
        val lo = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        val hi = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        if (hi == 0) return null
        return lo / 100.0 to hi / 100.0
    }

    /** 0x2AD5：min/max 各 s16 小端，0.1%；解析失败返回 null */
    private fun parseInclineRange(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 4) return null
        fun s16(i: Int): Int {
            val v = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            return v.toShort().toInt()
        }
        val min = s16(0)
        val max = s16(2)
        if (max == 0) return null
        return min / 10 to max / 10
    }
}
