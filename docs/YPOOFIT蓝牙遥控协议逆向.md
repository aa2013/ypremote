# YPOOFIT 蓝牙遥控器协议逆向文档

> 逆向对象：`YPOOFIT_2.3.5.apk`（包名 `com.yp.yilian`，versionCode 56，minSdk 23 / targetSdk 35）
> 分析方式：jadx 1.5.1 全量反编译（产物在 `ypoofit/_work/out/`），结论均标注反编译源码位置。
> 用途：为自研 Jetpack Compose 遥控器 App 提供协议依据，仅用于本人已购设备的互联互通。

---

## 1. 应用架构概览

APK 总体积 465MB，其中 318MB 是 Unity 场景资源（`assets/bin/Data/data.unity3d` + `libil2cpp.so`），属于跑步课程 3D 场景功能，**与遥控器完全无关**。遥控器是原生 Android（View + DataBinding）实现的：

| 模块 | 说明 |
|---|---|
| `com.clj.fastble.*`（混淆后 `h2.a`） | FastBLE 开源库，负责扫描/连接/GATT 操作 |
| `com.yp.yilian.bluetooth.utils.BluetoothScanUtils` | 扫描、连接、连接成功后的初始化 |
| `com.yp.yilian.bluetooth.utils.BluetoothDataUtils` | 订阅通知 + 按协议分型解析数据 |
| `com.yp.yilian.bluetooth.utils.BluetoothInstructionUtils` | 指令队列、指令构造、发送、限值读取 |
| `com.yp.yilian.bluetooth.bean.InstructionBean` | 一条指令：cmd1/cmd2/dataLenth/data/cmdStr/UUID |
| `f8.g` | hex / 校验 / 格式化工具类 |
| `com.yp.yilian.sports.activity.RemoteControlActivity` | 遥控器页面（截图界面） |
| `com.yp.lib_common.base.Constants` | 全部 UUID 常量 |

支持 9 种"协议分型"（protocolType 1~9），按**蓝牙设备名**自动识别；本文重点是跑步机的 **protocolType 1（默认）/ 4**，即华为系 FTMS 变体协议。

---

## 2. 协议分型识别（`BluetoothDataUtils.setProtocolType(String)`）

连接成功后按设备名匹配（`contains`，不区分大小写后缀判断）：

| protocolType | 设备名特征 | 机型 | 数据通知 | 控制写入 |
|---|---|---|---|---|
| **1** | **其余全部（默认）** | 跑步机（FTMS/华为系） | `0x1826` / `0x2ACD` | `0x1826` / `0x2AD9` |
| 2 | `esl`、`ESLinker` | ESL 跑步机 | `FFF0` / `FFF1` | `FFF0` / `FFF2` |
| 3 | `-YJ-`，或服务列表含 `f8c0` | 椭圆机（智坚） | `FFF0` / `FFF1` | `FFF0` / `FFF2` |
| 4 | `yptm`/`YPTM`/`-M1`/`m1` | 跑步机 | 同 1 | 同 1 |
| 5 | `-u1-`、`-e2`~`-e8-` | 椭圆机 | `0x1826` / `0x2ACE` | `0x1826` / `0x2AD9` |
| 6 | `-310`/`-380`/`-uu`/`-u3`/`-U5`/`2028`/`YPEM` | 椭圆机 | 同 5 | 同 5 |
| 7 | `YPBM`、`-f1-`~`-f7-`、`-FSD`、`-d3-`、`-hyx-`、`-x5-`、`-x3-` | 动感单车 | `0x1826` / `0x2AD2`（+`FFF1`灯效） | `0x1826` / `0x2AD9` |
| 8 | `YPRM`、`-v1-`~`-v6-` | 划船机 | `0x1826` / `0x2AD1` | `0x1826` / `0x2AD9` |
| 9 | `L8_P1PRO`、`-P1-` | 功率器械 | `FFF0` / `FFF1` | `FFF0` / `FFF2` |

> 解锁失败兜底：protocol 2/3 的设备会走 `02…校验…03` 旧帧格式（见 §8 附录）。

---

## 3. GATT 服务与特征（`Constants.java`）

跑步机（protocol 1/4）用到的 UUID：

| UUID | 名称 | 用途 |
|---|---|---|
| `00001826-0000-1000-8000-00805f9b34fb` | Fitness Machine Service | 主服务 |
| `00002ac2-…`（未用） | — | — |
| `00002acd-0000-1000-8000-00805f9b34fb` | Treadmill Data | **Notify**：实时运动数据 |
| `00002ad9-0000-1000-8000-00805f9b34fb` | Fitness Machine Control Point | **Write**：所有控制指令 |
| `00002ad4-0000-1000-8000-00805f9b34fb` | Supported Speed Range | **Read**：min/max 速度（u16×2，0.01 km/h） |
| `00002ad5-0000-1000-8000-00805f9b34fb` | Supported Inclination Range | **Read**：min/max 坡度（s16×2，0.1%） |
| `0000180a-0000-1000-8000-00805f9b34fb` | Device Information Service | 型号/版本 |
| `00002a24-…` | Model Number String | Read，型号（ASCII） |
| `00002a26-…` | Firmware Revision String | Read，固件版本 |
| `00002a27-…` | Hardware Revision String | Read |
| `00002a28-…` | Software Revision String | Read |
| `d18d2c10-c44c-11e8-a355-529269fb1459` | **厂商解锁特征** | Write，解锁码（protocol 1） |
| `0000fff0-…` / `fff1` / `fff2` | ESL 服务 / 通知 / 写 | protocol 2 及灯效（WLT）设备 |
| `3f2d8b7c-91a4-4e6d-bc82-9e7f1a3d5b00/01/02` | WLT 灯效服务 / 通知 / 写 | 带彩灯的跑步机（Ignite 模式） |
| `59554c55-8000-…`/`59554c55-0000-…` | MERACH（心跳服务/特征） | 麦瑞克椭圆机 |

ASCII 提示：`59554c55` = "YULU"，`4d4552414348` = "MERACH"——同一套主板方案供多个品牌贴牌。

---

## 4. 连接流程（`BluetoothScanUtils` / `BluetoothDataUtils` / `BluetoothInstructionUtils`）

```
权限检查(BLUETOOTH_SCAN/CONNECT、定位)
   │
扫描(FastBLE, 按设备名规则过滤) ──点选设备──► connectGatt
   │
onConnectSuccess
   ├─ setProtocolType(device.name)            // 定协议分型
   ├─ subscriptionNotify()                    // 0x1826/0x2ACD 开启 Notify
   │    └─ 失败重试：最多 20 次（mDataHandler 4001）
   ├─ (跑步机) 检查 3f2d8b7c 服务 → 有则订阅灯效通知 3f2d…b01
   │    └─ 无则读 0x2A26 固件版本，识别 Y42_V205.18.2x 固件再订
   ├─ 读 0x180A/0x2A24 型号（ASCII，滤除 0x20/0x00 字节）
   ├─ unlock(解锁码)                           // 见 §7；成功写后自动补发 0x00 请求控制权
   └─ getMax()
        ├─ 读 0x2AD4 → huaweiMinSpeed / huaweiMaxSpeed（u16 LE ×2，0.01 km/h）
        └─ 读 0x2AD5 → huaweiMinSlope / huaweiMaxSlope（s16 LE ×2，0.1%）
```

- 读到的速度/坡度范围决定 UI：加减速上限（`已是该设备的最大速度`提示）、坡度尺最大值。
- `analysisMaxSpeed/Slope` 读到 0 会自动重发（最多 10 次）。
- App 还有一步 `deviceInfo(model)` 上报服务器换取"是否有坡度/最大速度"等运营配置，**复刻时可省略**：直接以 `0x2AD5` 是否可读来判断有无坡度。

### 4.1 真机 HCI 抓包验证（YPTM-RZ / FS-BT-D2，2026-11-15）

开启手机"蓝牙 HCI 信息收集日志"，用原 App 完成一次成功试跑（皮带转、用时累加），从 bugreport 提取 `btsnoop_hci.log` 解析 ATT 层：

```
T+0.00  WriteReq  0x15(2ACD CCCD) ← 01 00                    订阅实时数据
T+0.32  ReadReq   0x05(2A24) → "FS-BT-D2"                    读型号
T+3.70  WriteCmd  0x24(d18d2c10) ← 01 00 02 02 00 05 01 01   解锁帧（无应答写！）
T+3.73  ReadReq   0x20(2AD4) → 50 00 20 03 0A 00             速度范围 0.8~8.0
T+4.01  ReadReq   0x22(2AD5) → 00 00 00 00 0A 00             坡度范围（无坡度）
T+4.32  WriteReq  0x1D(2AD9) ← 00                            请求控制权
T+4.93  WriteReq  0x1D(2AD9) ← 07                            开始
T+5.72  2ACD 通知 84 25 50 00 …（速度 0.8 km/h，用时开始累加）
```

关键结论（App 侧 logcat 与 HCI 双重印证）：

1. **解锁帧写入厂商特征 `d18d2c10`（handle 0x24），WriteCmd 无应答写（opcode 0x52）**；2AD9 控制指令用 WriteReq 带应答写（App 侧解锁回调 2ms 即返回、控制点回调 ~300ms 返回，佐证写入类型）。
2. **全程不订阅 2AD9 CCCD**——控制点应答从不读取，盲发。
3. **全程零 FFF0/FFF1/FFF2 流量**——YPTM-RZ 虽广播 FFF0 服务，原 App 完全不碰；ESL 握手与本机型控制无关。
4. **无周期控制重发**：从开始到停止的 15s 内 2AD9 只有 `00`/`07` 两条写；周期性流量只有对 2AD5 的 Read（~300-500ms 一次，坡度尺 UI 轮询）与对从未完成指令（如设备不支持的 0x2A26 读）的队列重试。§5.1 的"keep-alive"实为队列重试机制。
5. 停止由机器面板完成（BLE 层无停止指令）。
6. 连接参数：interval=24(30ms)/latency=10/timeout=600；无 MTU 请求；GATT 发现命中 Android 缓存（onSearchComplete 11ms，无 ATT 发现流量，故 btsnoop 中看不到服务发现）。
7. 三星 MARs 在 App 启动 33s 后强杀了原 App（后台化即杀）——复刻 App 需保持前台。

### 4.2 扫描过滤规则（原 App 同款，2026-11-15 用户问询后核实）

原 App **不用服务 UUID 做 ScanFilter**——它无过滤扫描全部 BLE 广播，由 FastBLE 扫描器（混淆类 `l2.a`）在回调里按名称规则丢弃（`c(BleDevice)`：name 不匹配直接 return），`BluetoothScanUtils.scanBluetooth` 配置的规则：

```
BleScanRuleConfig: 模糊匹配(contains) + 名称关键词集合
  "YP" "yp" "LAWIN" "lawin" "SLD" "sld" "ESL" "esl"
  "YPEM" "YPBM" "P1" "L8_P1PRO" "-YJ-" "-yj-" "-Yj-" "-yJ-"
扫描超时 3s；扫完再按 GetBlueToothUtiles.deviceTypeString(name) == deviceType 复筛
```

要点：

1. 本机广播名 `YPTM-RZ` 命中 "YP"/"YPTM"（§2 分型也是按名 contains "YPTM"）。
2. 空名设备一律被过滤（FastBLE 把 null name 当 "" 处理，contains 不成立）。
3. 复刻侧处理：默认模式 = 无过滤扫描 + 品牌名关键词回调过滤（大小写不敏感 contains，覆盖原规则全部大小写变体）；FTMS 0x1826 服务过滤作为切换的兜底模式（可发现其他品牌 FTMS 设备，原 App 看不到这类）。

---

## 5. 控制指令（写入 `0x2AD9`，原始字节，无帧头无校验）

protocol 1/4/5/6/7/8 的控制指令**就是标准 FTMS Control Point 操作码**（App 里由 `cmdStr` 直接拼好发送）：

| 动作 | 字节 | FTMS 操作码 | 数值编码 | 源码 |
|---|---|---|---|---|
| 请求控制权 | `00` | Request Control | — | location 102 |
| 设定速度 | `02` + u16LE | Set Target Speed | 0.01 km/h | `changeSpeed()` |
| 设定坡度 | `03` + s16LE | Set Target Inclination | 0.1 % | `changeSlop()` |
| 设定阻力 | `04` + u16LE | Set Target Resistance | 0.1 档 | protocol 5-8 |
| 开始/恢复 | `07` | Start or Resume | — | `startRun()` |
| 停止 | `08 01` | Stop or Pause | param=1 停止 | `stopRun()` |
| 暂停 | `08 02` | Stop or Pause | param=2 暂停 | `pauseRun()` |

**示例**（来自 `g.w`/`g.v` 的小端编码）：

- 6.0 km/h → `6.0×100=600=0x0258` → 字节 `02 58 02`
- 8.5 km/h → `850=0x0352` → `02 52 03`
- 坡度 3.0% → `3.0×10=30=0x001E` → `03 1E 00`
- 坡度 0 → `03 00 00`

protocol 2（ESL）则是 `a3/a4…` + `02/03` 头尾帧（`02 + body + XOR校验 + 03`），仅作参考，不在本复刻范围。

### 5.1 指令队列机制（真机验证后修正）

`BluetoothInstructionUtils.addInstruction()`：

- 按 `location` 去重，同 location 的新指令**替换**旧的并移到队尾；
- Handler(1001) 每 **300ms** 取队首指令发送（location 501~507 的走 GATT read，其余走 write）；
- `onWriteSuccess` 后：`isRemove=true` → 从队列移除；`isRemove=false` → **重新入队**。

> **真机 HCI 修正（2026-11-15，见 §4.1）**：控制指令（`00`/`07`/`02xx`）均为 `isRemove=true` 的**一次性写入**，成功即移出队列；周期性流量只有对 2AD5 的读（坡度尺轮询）与对**从未完成的指令**（如设备不支持的 0x2A26 读，重试 10 次后放弃）的 300ms 重发。早期版本推断的"控制指令 keep-alive 周期重发"不成立——**复刻实现按一次性写入即可，周期重发反而可能干扰固件**。

---

## 6. 实时数据通知（`0x2ACD` Notify，`runHuaWeiParse`）

### 6.1 与标准 FTMS 的差异（复刻关键）

逐段核对 `runHuaWeiParse` 的字节消费量后确认：**字段部分与标准 FTMS Treadmill Data 完全一致**（小端、顺序、分辨率全部相同）。唯一 wire 差异是 **flags 两字节按大端组合**（`g.F(hex)` 直接对字节流做二进制，等于把标准 LE flags 做了字节交换）——即设备把标准 flags 值以**大端字节序**发出，其余多字节字段仍是小端。

App 的位门控与标准 FTMS 位表逐位对应（见 §6.2 验证），另外把标准中的保留位 bit13 用作"步数"（厂商扩展）。

> 复刻策略：默认按"厂商模式"（flags 大端 + bit13 步数）解析；同时提供"标准模式"（flags 小端）开关与原始 hex 调试显示用于核对。

### 6.2 flags 位表（App 门控 ↔ 标准 FTMS 对照验证）

App 把前两字节按大端得到 16 位 flags，再按下标取二进制位（`F[i]` = 第 `15-i` 位）。逐位验证与标准 FTMS 完全一致：

| App 下标 | 标准 bit | 含义 | 字段编码 |
|---|---|---|---|
| F[7]==0 | bit0 | More Data=0 时解析速度 | 瞬时速度 u16（恒存在） |
| F[6] | bit1 | Average Speed Present | 平均速度 u16（读后忽略） |
| F[5] | bit2 | Total Distance Present | 总距离 u24，米 |
| F[4] | bit3 | Inclination Present | 坡度 s16(0.1%) + 坡度角 s16 |
| F[3] | bit4 | Elevation Gain Present | 海拔增益 u16 + 损失 u16 |
| F[2] | bit5 | Instantaneous Pace Present | 即时配速 u8 |
| F[1] | bit6 | Average Pace Present | 平均配速 u8 |
| F[0] | bit7 | Expended Energy Present | 总能量 u16 + 每小时 u16 + 每分钟 u8 |
| F[15] | bit8 | Heart Rate Present | 心率 u8 |
| F[14] | bit9 | MET Present | MET u8 |
| F[13] | bit10 | Elapsed Time Present | 用时 u16，秒 |
| F[12] | bit11 | Remaining Time Present | 剩余时间 u16 |
| F[11] | bit12 | Force/Power Present | 功率 s16 + 力 u16 |
| F[10] | bit13 | **步数（厂商扩展，标准保留位）** | 步数 u24 |

（每行字段消费的字节数与 `runHuaWeiParse` 中 `g.l/g.m` 的偏移推进逐一吻合。）

### 6.3 字段顺序与编码（跟在 flags 后，全部小端）

| 顺序 | 字段 | 类型 | 单位/分辨率 | App 处理 |
|---|---|---|---|---|
| 1 | 瞬时速度 | u16 | 0.01 km/h | ÷100；**≤0.7 视为 0**（`runHuaWeiParse` 中阈值） |
| 2 | 平均速度 | u16 | 0.01 km/h | 忽略 |
| 3 | 总距离 | u24 | 1 m | ÷1000 → 公里，保留 3 位 |
| 4 | 坡度 | s16 | 0.1 % | ÷10 → 整数坡度（驱动坡度尺） |
| 4b | 坡度角 | s16 | 度 | 忽略 |
| 5 | 海拔增益/损失 | u16+u16 | m | 忽略 |
| 6 | 即时配速 | u8 | — | 忽略（配速由速度换算） |
| 7 | 平均配速 | u8 | — | 忽略 |
| 8 | 能量 | u16+u16+u8 | kcal | 取总能量 → 千卡（取整显示） |
| 9 | 心率 | u8 | bpm | 留存 |
| 10 | MET | u8 | — | 忽略 |
| 11 | 用时 | u16 | 秒 | `g.x()` 格式化（>1h 显示 H:MM:SS，否则 MM:SS） |
| 12 | 剩余时间 | u16 | 秒 | 忽略 |
| 13 | 功率/力 | s16+u16 | W/N | 忽略 |
| 14 | 步数 | u24 | 步 | 留存（bit13 厂商扩展） |

解析伪代码（与 `runHuaWeiParse` 等价）：

```kotlin
// flags 大端（厂商唯一 wire 差异）
val flags = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
var off = 2
fun u16(): Int { val v = le16(data, off); off += 2; return v }
fun s16(): Int { val v = le16(data, off).toShort().toInt(); off += 2; return v }

if (flags and (1 shl 0) == 0) {                     // More Data == 0
    speed = u16() / 100f
    if (speed <= 0.7f) speed = 0f
}
if (flags and (1 shl 1) != 0) { off += 2 }           // 平均速度
if (flags and (1 shl 2) != 0) {                      // 总距离 u24
    distanceM = u16() or ((data[off++].toInt() and 0xFF) shl 16)
}
if (flags and (1 shl 3) != 0) {                      // 坡度 + 坡度角
    slope = s16() / 10
    off += 2
}
if (flags and (1 shl 4) != 0) { off += 4 }           // 海拔增益+损失
if (flags and (1 shl 5) != 0) { off += 1 }           // 即时配速
if (flags and (1 shl 6) != 0) { off += 1 }           // 平均配速
if (flags and (1 shl 7) != 0) {                      // 能量 2+2+1
    kcal = u16().toFloat(); off += 3
}
if (flags and (1 shl 8) != 0) { heartRate = data[off++].toInt() and 0xFF }
if (flags and (1 shl 9) != 0) { off += 1 }           // MET
if (flags and (1 shl 10) != 0) { elapsed = u16().toLong() }
if (flags and (1 shl 11) != 0) { off += 2 }          // 剩余时间
if (flags and (1 shl 12) != 0) { off += 4 }          // 功率+力
if (flags and (1 shl 13) != 0) {                     // 步数 u24（厂商扩展）
    steps = u16() or ((data[off++].toInt() and 0xFF) shl 16)
}
```

### 6.4 运行状态判定（`judgementRunStatus`）

App 用速度变化 + 5 次采样稳定判定状态机（0 待机 / 1 已启动 / 2 加速 / 3 匀速 / 4 减速 / 5 暂停 / 6 停止已运动 / 8 结束），遥控器页据此把"开始"按钮切成"暂停"。复刻可用简化版：`speed > 0` = 运行中。

---

## 7. 解锁机制（防盗锁功能，可跳过）

- **protocol 4（真机 YPTM-RZ 验证，§4.1）**：固定帧 `01 00 02 02 00 05 01 01`，写入**厂商解锁特征 `d18d2c10-c44c-11e8-a355-529269fb1459`**（handle 0x24），**无应答写（WriteCmd）**——不是写 0x2AD9。解锁帧发出后 App 才补发 `00` 请求控制权。
- **protocol 1**：写**厂商解锁特征** `d18d2c10-c44c-11e8-a355-529269fb1459`：
  - 解锁码来源：扫设备二维码（`ScanAcitivty`），拼装规则 `"0100" + 每个字符前插 "00"`（即 ASCII 码按 hex 字符对拼接）；
  - 本地 MMKV 按 MAC 缓存（`UNLOCK_CODE`）；
  - 无码时弹扫码页；复刻时可做成"手动输入解锁码（可选）"，大多数机器不输入也能控制。
- **写完解锁（location 101）成功后，App 自动补发一条 location 102 = `00`（FTMS Request Control）**。这是 FTMS 的规范要求：控制点写速度/坡度前必须先拿到控制权。复刻必须实现这一步。

---

## 8. 附录：ESL 旧协议帧（protocol 2/3/9，供参考）

`sendInstruction()` 中 `dataLenth` 非空时走旧帧：

```
02 + cmd1 + [cmd2] + data + XOR(data 各字节) + 03        // 短帧
a9 + cmd1 + [cmd2] + dataLenth + data + XOR(整段)        // 长帧
```

protocol 9（P1）帧示例：`f5 08 00 05 04 99 c2 fa`（尾字节 `fa` 为帧尾，`f5` 帧头）。

ESL 指令：解锁 `43 01`、读参数 `42`、开始 `44 01`、速度 `44 05 + 数据`、停止 `44 04`。

---

## 9. 遥控器页面（截图）控件 → 协议映射

布局：`res/layout/activity_remote_control.xml`；逻辑：`RemoteControlActivity`。

### 9.1 数据展示（全部来自 0x2ACD 通知）

| 控件 | 数据源 | 显示规则 |
|---|---|---|
| 用时 | `elapsedTime` 回调 | `g.x(秒)`：>3600 显示 `H:MM:SS`，否则 `MM:SS` |
| 千卡 | `expendedEnergy` 回调 | 取整 |
| 配速 | 速度换算 | `3600/速度` 秒 → `g.u()` → `M'SS''`；速度 0 时显示空 |
| 公里 | `distance` 回调 | 米 ÷1000，3 位小数 |
| 速度 | `speed` 回调 | 保留 1 位小数（0 显示 `0`） |
| 坡度值 | `slop` 回调 | 整数 + "坡度" |
| 坡度尺 | 设备上报坡度 | `RulerView` 跟随设备实际坡度 |
| 开始/暂停 | 速度>0 | 图标与文字切换（开始绿 `#4DC080` / 暂停蓝 `#4060FF`） |

### 9.2 控制操作

| 控件 | 行为 | 协议动作 |
|---|---|---|
| 返回键/停止按钮 | 距离<100m 弹"运动数据无法保存，是否确认退出"（确定退出/再运动一会）；否则走结算 | 停止 = `08 01` 后退出 |
| **锁屏** | **纯本地**：切换 `unlock` 布尔，锁住所有控件（透明度 0.3），返回键失效 | 不发任何指令 |
| **开始/暂停** | 开始 → `startRun(1)`；运行中点击 → `pauseRun()` | `07` / `08 02` |
| **停止** | 弹确认 → `stopRun()` → 清数据退出 | `08 01` |
| **速度 − / ＋** | 按下即启动 100ms 定时器连续 ±0.1 km/h（浮泡显示目标速度），松手发送最终值；上限 = 0x2AD4 max，下限 = min | `02` + u16LE |
| **快捷 2/4/6/8** | 跑步机直接 `changeSpeed(2/4/6/8)`；非跑步机按阻力百分比换算 | `02` + u16LE |
| **坡度尺** | 拖动松手后 `changeSlop(value)`；拖动中不上报 | `03` + s16LE |
| 右上蓝牙图标 | 未连接点击 → 跳设备选择页 | — |

### 9.3 坡度尺 `RulerView` 绘制参数（复刻用）

- 刻度间距 12dp，每 1 个坡度单位 3 格；主线高 32dp，次线 16dp，线宽 4dp 圆角；
- 刻度线颜色 `#AAAAAA`，随距中心距离做 `alpha=(1−d)³×255` 淡出；中心 ±0.12 宽度内改用指示渐变；
- 中心指示条：宽 4dp，高 32dp 上下各延伸 1/6，渐变 `#14C8C8 → #4060FF`；
- 数字标签 14dp `#999999`，每 3 格（1 个单位）标一个整数；
- 坐标系：当前值刻度固定居中，数值向右增大；值 = min + 偏移取整；
- 高度 60dp，页面背景 `#333333`。

### 9.4 其他颜色/资源

- 页面背景 `color_33 = #333`；次级文字 `color_99 = #999`；
- 加/减按钮：半透明白圆 + 橙红 `±`（`icon_handset_add/subtract.png`，可直接从 APK 提取）；
- 快捷速度按钮背景：`ic_remote_control_quick_bg.webp`（半透明圆）；
- 开始（绿圆播放）、停止（白方块）、锁（`icon_handset_play/stop/lock/unlock.png`）。

---

## 10. 复刻实现方案（Jetpack Compose）

```
YpRemote/                     ← 正式工程目录
├─ app/src/main/java/com/yp/remote/
│  ├─ ble/
│  │  ├─ FtmsUuids.kt          ← §3 UUID 表
│  │  ├─ TreadmillProtocol.kt  ← §5 控制帧构造 + §6 通知解析（厂商模式/标准模式双实现）
│  │  ├─ CommandQueue.kt       ← §5.1 300ms 重发队列（keep-alive）
│  │  └─ TreadmillConnection.kt← 连接状态机：扫描→连接→订阅→RequestControl→读限值
│  ├─ ui/
│  │  ├─ RemoteScreen.kt       ← 遥控器页面（复刻布局）
│  │  ├─ SlopeRuler.kt         ← Compose Canvas 复刻 RulerView
│  │  ├─ ConnectScreen.kt      ← 扫描/连接列表
│  │  └─ theme/Color.kt        ← §9.4 色值
│  └─ MainViewModel.kt         ← 状态：speed/slope/distance/kcal/elapsed/playStatus/locked
└─ docs/（本文档）
```

要点：

1. 通知解析默认走**厂商模式**（§6.2 位表），调试页显示原始 hex 并支持切换标准 FTMS 解析对比；
2. 连接序列按 §4.1 真机验证：订阅 2ACD → 读 0x2A24 型号 → 解锁帧写 d18d2c10（**无应答写**）→ 读 `0x2AD4/0x2AD5` → 写 `00` 请求控制权；不订阅 2AD9 通知；读不到 `0x2AD5` 时隐藏坡度尺；
3. 全部控制指令（速度/坡度/开始/停止/暂停）**一次性写入**，不做周期重发（§5.1 修正）；
4. `BLUETOOTH_SCAN`（neverForLocation）+ `BLUETOOTH_CONNECT` 权限，按名称规则过滤扫描结果（默认不过滤，列表全显）；
5. minSdk 23 与原 App 一致。

---

## 11. 验证方法（拿真机核对）

1. 用 nRF Connect 连接设备，确认服务 `0x1826`、特征 `0x2ACD/0x2AD4/0x2AD5/0x2AD9`；
2. 观察 `0x2ACD` 通知：确认 flags 是否大端（如 `04 00` 表示距离标志，而非标准 LE 的 `00 04`）；
3. 写 `0x2AD9` = `07` 看是否启动；`02 58 02` 看是否 6.0 km/h；`03 1E 00` 看坡度；
4. 复刻 App 调试页打开"原始通知 hex"与原 App 同屏对比。

## 12. 风险与待确认项

- [x] 用户设备 YPTM-RZ = protocol 4（FTMS 标准小端 flags，§6 已按真机 hex `84 25` 修正）；
- [x] flags 位表厂商约定——真机 hex 验证为标准 FTMS 位表 + bit13 步数（§6）；
- [x] 解锁帧：YPTM-RZ 必须发 `01 00 02 02 00 05 01 01` 到 d18d2c10（**无应答写**），且先于一切控制指令（§4.1）；
- [ ] 复刻 App 按抓包序列改造后能否控制成功——待真机复测；
- [ ] 长按 ± 的 0.1 步进在部分固件上可能要求 ≥0.5 步进，待真机确认。

## 13. 设备休眠与自动唤醒（真机行为，2026-11-15 用户实测确认）

- 控制序列按 §4.1 复刻后**真机控制成功**（开始/停止均生效）。
- **休眠的是主控板而非蓝牙模块**：长时间不动机器进入休眠后，BLE 层一切正常（连接可建立、写入有 ACK、0x2ACD 持续推流全零），但控制指令到不了驱动板——表现为"点开始没反应、蜂鸣器不响"。**原 App 同样无法唤醒**（抓包那次成功的前提是机器刚被手动激活过）。
- 复刻 App 的自动唤醒策略（`TreadmillManager.startOrWake()`）：
  1. 点开始先常规下发 `07`，观察 2s（0x2ACD 速度 > 0 即成功）；
  2. 未转动则重发 **解锁帧（无应答写）→ `00` → `07`**，每轮观察 3s，最多 3 轮（按钮显示"唤醒中…"）；
  3. 仍失败则 Toast 提示"设备已休眠，请先在跑步机上按任意键激活后再点开始"；停止/暂停/断开会取消唤醒流程。
- 若机器为深度休眠（主控级断电），BLE 物理上无法唤醒，只能手动激活——这是省电/安全设计，激活后再点开始立即生效。
