# AGENTS.md — YpRemote 工程约定

本目录是 YPOOFIT 遥控器复刻的正式工程（Jetpack Compose），协议来源：`docs/YPOOFIT蓝牙遥控协议逆向.md`（§4.1 为 HCI 真机抓包验证）。

## 协议约定

- 协议常量只允许出现在 `ble/FtmsUuids.kt`；解析逻辑集中在 `ble/TreadmillParser.kt`，UI 不直接拼字节。
- GATT 操作必须走 `TreadmillManager` 的操作队列（同一时刻仅一个在途操作），禁止绕过队列直接调 BluetoothGatt。
- 全部控制指令一次性写入；不做周期重发（HCI 抓包证伪了 keep-alive 推断，见文档 §5.1）。
- 连接序列复刻原 App：订阅 2ACD → 读型号 → 解锁帧写 d18d2c10（**无应答写**）→ 读 2AD4/2AD5 → 请求控制；不订阅 2AD9、不碰 FFF0。
- 扫描过滤复刻原 App（文档 §4.2）：默认 = 无过滤扫描 + 品牌名关键词回调过滤（`brandNameKeywords`，大小写不敏感 contains，空名丢弃）；FTMS 0x1826 服务过滤只是切换的兜底模式。原 App 用 FastBLE 名称模糊匹配（"YP/lawin/SLD/ESL/…"），不用 ScanFilter。
- 解析按标准 FTMS 小端 flags（`84 25` 真机验证）；bit13 = 厂商步数 u24。
- 用户约束：adb 只允许安装 APK 和抓日志，禁止 adb 操作手机 UI。

## UI 约定（2026-11-15 重构，token 契约对齐 ClipShare tokens.md）

- 组件样式只引用 `LocalYp` 语义 token（surface/ink/brand 三档/状态色），不写裸色值；亮暗一一对应，新增 token 必须同时给两份。
- 无边框：卡片/按钮/分段/输入一律不描边，层次靠底色差与内边距；选中态 = `brandSoft` 实色 + `brandSoftInk` 文字。
- 实心大面积（主按钮）走 `brandGradient()`（gradA→gradB 135°）；`brand` 是文字档、`brandFill` 是填充档，不可互换。
- 浮层材质：弹窗 = 毛玻璃遮罩 + 实色面板；菜单/toast 用半透明面板。开始键绿 = `online`（状态绿唯一来源），暂停 = `brandFill`。
- 所有 UI 文案放 `values/strings.xml`（英文默认）+ `values-zh/strings.xml`；新增文案两份同步。语言切换走 appcompat per-app locales（autoStoreLocales 已配置）。
- 主题模式持久化在 `YpSettings`（SharedPreferences）；系统栏前景色随主题在 MainActivity 同步。
- 图标用 `res/drawable` 可着色矢量图 + `Icon(tint=)`，不再使用原 APK 的暗色 PNG。
- 调试内容只允许出现在 `DebugScreen`（入口：设置页 + 连接页版本号长按），连接/遥控页不放协议测试按钮。
- 跑步记录存 `data/WorkoutStore`（SharedPreferences + JSON，恒时间倒序）；会话生命周期由 `TreadmillManager` 跟踪（皮带转动开始、停止/退出/断连落盘），UI 不拼记录数据。
- 遥控页退出约定：皮带未转（未开始/暂停停稳）直接退出；运行中先弹确认（确认=停止并退出）。锁定态吞掉返回键。
- 语言切换不重建：MainActivity 已声明 `configChanges="locale|layoutDirection"`（API 33+ 由框架原地应用 locale，Compose 随 LocalConfiguration 原地重组）。**另有已知坑：per-app locale 首次"空→非空"变更会绕过 configChanges 强制重建窗口（issuetracker 321401619）**，规避方式=YpApp 启动时预置非空 locale + 设置页「跟随系统」写系统语言快照（YpSettings.langMode）而非空列表；任何时候不要把 applicationLocales 置回空。窗口底色对齐主题 bg（themes.xml `yp_window_bg` 亮/暗两份）是冷启动/主题切换的辅助。

## 其他

- 注释克制、中英双语（关键决策处中文为主）；不写魔法值。
- 用户代码风格：零魔法值、长调用抽局部变量、抽象提取，与 `D:\Agent` 工作区通用规则一致。
- `_work/` 目录是逆向一次性产物（反编译源码、btsnoop 工具），不进版本控制；正式内容只放本目录与 `../docs/`。
