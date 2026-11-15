package com.yp.remote.ui

import android.bluetooth.le.ScanResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yp.remote.R
import com.yp.remote.ble.ConnState
import com.yp.remote.ble.TreadmillManager

/**
 * 连接页：状态卡 + 扫描操作 + 设备列表，干净无调试内容；
 * 调试入口收进设置页，版本号长按是隐藏入口。
 * Connect screen: status card + scan actions + device list; debug lives in Settings
 * (long-press on the version label is the hidden shortcut).
 */
@Composable
fun ConnectScreen(
    onOpenRemote: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val yp = LocalYp.current
    val devices by TreadmillManager.devices.collectAsState()
    val state by TreadmillManager.state.collectAsState()
    val connectedName by TreadmillManager.connectedName.collectAsState()
    val connectedAddress by TreadmillManager.connectedAddress.collectAsState()
    val scanning = state == ConnState.SCANNING
    val ready = state == ConnState.READY
    // 默认按品牌名扫描（原 App 同款）；切换到 FTMS 服务过滤兜底
    var scanByService by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- 标题 + 设置入口 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.connect_title),
                color = yp.ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // 记录入口（与设置入口同款式）
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = stringResource(R.string.history_title),
                tint = yp.ink2,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onOpenHistory() }
                    .padding(8.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings),
                tint = yp.ink2,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onOpenSettings() }
                    .padding(8.dp),
            )
        }

        // ---- 状态卡 ----
        SectionCard(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(YpRadius.md)
                        .background(if (ready) yp.onlineSoft else yp.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bluetooth),
                        contentDescription = stringResource(R.string.bluetooth_status),
                        tint = if (ready) yp.online else yp.faint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    val title = when (state) {
                        ConnState.SCANNING -> stringResource(R.string.status_scanning)
                        ConnState.CONNECTING -> stringResource(R.string.status_connecting)
                        ConnState.READY ->
                            stringResource(R.string.status_connected_fmt, connectedName ?: "")
                        ConnState.DISCONNECTED -> stringResource(R.string.status_disconnected)
                    }
                    Text(title, color = yp.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(
                            if (ready) R.string.hint_ready else R.string.hint_disconnected,
                        ),
                        color = yp.muted,
                        fontSize = 13.sp,
                    )
                }
                if (scanning || state == ConnState.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = yp.brandFill,
                    )
                }
            }

            // ---- 主操作行 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ready) {
                    GradientButton(
                        stringResource(R.string.open_remote),
                        Modifier.weight(1.6f),
                    ) { onOpenRemote() }
                    PlainButton(
                        stringResource(R.string.disconnect),
                        Modifier.weight(1f),
                    ) { TreadmillManager.disconnect() }
                } else {
                if (scanning) {
                    PlainButton(stringResource(R.string.stop_scan), Modifier.weight(1.6f)) {
                        TreadmillManager.stopScan()
                    }
                } else {
                    GradientButton(stringResource(R.string.scan_devices), Modifier.weight(1.6f)) {
                        TreadmillManager.startScan(scanByService)
                    }
                }
                PlainButton(
                    stringResource(
                        if (scanByService) R.string.scan_by_brand else R.string.scan_by_service,
                    ),
                    Modifier.weight(1f),
                ) {
                    scanByService = !scanByService
                    TreadmillManager.startScan(scanByService)
                }
                }
            }
        }

        // ---- 设备列表 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.nearby_devices),
                color = yp.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (devices.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_devices),
                        color = yp.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp),
                    )
                }
            }
            items(devices, key = { it.device.address }) { result ->
                DeviceCard(
                    result = result,
                    isCurrent = result.device.address == connectedAddress,
                    idle = state == ConnState.DISCONNECTED,
                    connecting = state == ConnState.CONNECTING,
                ) { TreadmillManager.connect(result.device) }
            }
        }

        // ---- 版本号（长按 = 隐藏调试入口） ----
        VersionLabel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            onLongPress = onOpenDebug,
        )
    }
}

/** 版本号标签：faint 小字，长按打开调试页 */
@Composable
private fun VersionLabel(modifier: Modifier = Modifier, onLongPress: () -> Unit) {
    val yp = LocalYp.current
    val context = LocalContext.current
    val versionName = rememberVersionName(context)
    Text(
        "v$versionName",
        color = yp.faint,
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    )
}

@Composable
private fun rememberVersionName(context: android.content.Context): String {
    val version = androidx.compose.runtime.remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
    }
    return version
}

/**
 * 设备行卡片：名称 + 地址/RSSI；当前连接目标高亮 brandSofter 底，
 * 连接中/已连接不可再点（防重复连接），空闲才响应点击。
 */
@Composable
private fun DeviceCard(
    result: ScanResult,
    isCurrent: Boolean,
    idle: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
) {
    val yp = LocalYp.current
    val device = result.device
    val name = device.name ?: stringResource(R.string.device_unknown)
    val actionText = when {
        isCurrent && connecting -> stringResource(R.string.status_connecting)
        isCurrent -> stringResource(R.string.device_connected)
        else -> stringResource(R.string.connect)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YpRadius.md)
            .background(if (isCurrent) yp.brandSofter else yp.surface)
            .then(if (idle) Modifier.clickable { onConnect() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = yp.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "${device.address} · RSSI ${result.rssi}",
                color = yp.muted,
                fontSize = 12.sp,
            )
        }
        Text(
            actionText,
            color = when {
                isCurrent -> yp.online
                idle -> yp.brand
                else -> yp.faint
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
