package com.yp.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yp.remote.R
import com.yp.remote.ble.ConnState
import com.yp.remote.ble.TreadmillManager
import com.yp.remote.ble.WakeState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 加减速单步（km/h），与原 App 一致 */
private const val SPEED_STEP = 0.1

/** 长按连发间隔（原 App scheduleWithFixedDelay 100ms） */
private const val HOLD_INTERVAL_MS = 100L

/**
 * 遥控器页面：复刻原 App 布局（逆向文档 §9），配色走语义 token 支持双主题。
 * Remote screen replicating the original layout, reskinned with semantic tokens.
 * 顶部数据全部来自 0x2ACD 通知；锁屏为纯本地状态，不发指令。
 */
@Composable
fun RemoteScreen(
    onExit: () -> Unit,
    onOpenConnect: () -> Unit,
) {
    val yp = LocalYp.current
    val sample by TreadmillManager.sample.collectAsState()
    val range by TreadmillManager.range.collectAsState()
    val connState by TreadmillManager.state.collectAsState()
    val wake by TreadmillManager.wakeState.collectAsState()
    val finishing by TreadmillManager.finishing.collectAsState()

    var locked by remember { mutableStateOf(false) }
    var targetSpeed by remember { mutableDoubleStateOf(0.0) }
    var holdDir by remember { mutableStateOf(0) } // 0 无长按；1 加速；-1 减速
    var showExitDialog by remember { mutableStateOf(false) }

    // 运行判定用原始速度 + 减速过渡期锁定：暂停减速中设备会先报瞬时 0、
    // 随后 ≤0.7 的读数又被显示规则钳成 0，两者都会让图标错误闪回"开始"
    val playing = finishing || (sample.rawSpeedKmh ?: 0f) > 0f
    val speedMax = if (range.speedMaxKmh > 0.0) range.speedMaxKmh else 20.0
    val speedMin = if (range.speedMinKmh > 0.0) range.speedMinKmh else 0.0
    val showSlope = range.inclineMaxPct > 0
    var slopeValue by remember(showSlope) { mutableStateOf(0f) }

    // 长按连发：按下立即步进一次，之后每 100ms 一步；松手/越界停止
    LaunchedEffect(holdDir) {
        while (holdDir != 0) {
            val next = targetSpeed + holdDir * SPEED_STEP
            if (next < speedMin) {
                showSpeedBoundToast(false)
                break
            }
            if (next > speedMax) {
                showSpeedBoundToast(true)
                break
            }
            targetSpeed = (next * 10).roundToInt() / 10.0
            delay(HOLD_INTERVAL_MS)
        }
    }

    // 唤醒重试耗尽：提示设备休眠需手动激活
    LaunchedEffect(wake) {
        if (wake == WakeState.SLEEPING) showSleepHintToast()
    }

    // 开始/恢复瞬间：目标速度基准同步为设备实际速度（长按步进从真实状态起步）
    LaunchedEffect(playing) {
        if (playing) targetSpeed = (sample.speedKmh ?: 0f).toDouble()
    }

    // 退出请求：皮带未转（未开始/暂停已停稳）直接退出并落记录；运行中先弹确认
    val requestExit = {
        if (playing || finishing) {
            showExitDialog = true
        } else {
            TreadmillManager.finishSession()
            onExit()
        }
    }

    // 系统返回与返回图标同逻辑：锁定时吞掉返回（防误触），运行中弹确认，否则直接退出
    BackHandler { if (!locked) requestExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ---- 标题栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = yp.ink,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { if (!locked) requestExit() }
                    .padding(8.dp),
            )
            Text(
                stringResource(R.string.remote_title),
                color = yp.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            // 蓝牙状态：在线绿/淡绿底，离线灰/surface2 底
            val connected = connState == ConnState.READY
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (connected) yp.onlineSoft else yp.surface2)
                    .clickable { onOpenConnect() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bluetooth),
                    contentDescription = stringResource(R.string.bluetooth_status),
                    tint = if (connected) yp.online else yp.faint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ---- 顶部四宫格：用时/千卡 + 配速/公里 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 36.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            StatRow(
                left = Format.elapsed(sample.elapsedSec ?: 0L) to stringResource(R.string.stat_time),
                right = ((sample.kcal ?: 0f).toInt()).toString() to stringResource(R.string.stat_kcal),
            )
            Spacer(modifier = Modifier.height(36.dp))
            val paceText = if (playing && (sample.speedKmh ?: 0f) > 0f) {
                Format.pace((3600.0 / sample.speedKmh!!).roundToInt())
            } else {
                stringResource(R.string.placeholder_dash)
            }
            StatRow(
                left = paceText to stringResource(R.string.stat_pace),
                right = Format.distance(sample.distanceMeter ?: 0) to stringResource(R.string.stat_distance),
            )
        }

        // ---- 速度控制：−/＋/快捷挡 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedButton(
                    iconRes = R.drawable.ic_minus,
                    locked = locked,
                    onHoldStart = { holdDir = -1 },
                    onHoldEnd = {
                        holdDir = 0
                        sendSpeed(targetSpeed)
                    },
                    modifier = Modifier.weight(1.5f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("km/h", color = yp.muted, fontSize = 13.sp)
                    // 中央显示设备实际上报速度（0x2ACD），目标速度只在长按浮泡中出现
                    Text(
                        Format.speed(sample.speedKmh ?: 0f),
                        color = yp.ink,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.speed_label), color = yp.muted, fontSize = 13.sp)
                }
                SpeedButton(
                    iconRes = R.drawable.ic_add,
                    locked = locked,
                    onHoldStart = { holdDir = 1 },
                    onHoldEnd = {
                        holdDir = 0
                        sendSpeed(targetSpeed)
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(2, 4, 6, 8).forEach { preset ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        QuickSpeedButton(value = preset, locked = locked) {
                            targetSpeed = preset.toDouble()
                            sendSpeed(preset.toDouble())
                        }
                    }
                }
            }
        }

        // ---- 坡度尺 + 底部按钮 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f),
        ) {
            if (showSlope) {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        slopeValue.roundToInt().toString(),
                        color = yp.ink,
                        fontSize = 20.sp,
                    )
                    Text(
                        stringResource(R.string.slope_label),
                        color = yp.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                    )
                }
                SlopeRuler(
                    value = slopeValue,
                    minValue = range.inclineMinPct.toFloat(),
                    maxValue = range.inclineMaxPct.toFloat(),
                    onValueChange = { v, scrolling ->
                        slopeValue = v
                        if (!scrolling && !locked) {
                            TreadmillManager.setIncline(v.roundToInt())
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // fillMaxWidth：Box 需要占满宽度，BottomBar 的居中对齐才生效
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                BottomBar(
                    modifier = Modifier.align(Alignment.Center),
                    locked = locked,
                    playing = playing,
                    waking = wake == WakeState.WAKING,
                    finishing = finishing,
                    onLock = { locked = !locked },
                    onPlayPause = {
                        if (playing) TreadmillManager.pause() else TreadmillManager.startOrWake()
                    },
                    onStop = { if (!locked) requestExit() },
                )
                if (holdDir != 0) {
                    // 长按调速浮泡：surface 卡片 + 当前目标速度
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .clip(YpRadius.md)
                            .background(yp.surface),
                    ) {
                        Text(
                            Format.speed(targetSpeed.toFloat()),
                            color = yp.ink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = yp.surface,
            title = { Text(stringResource(R.string.exit_dialog_title), color = yp.ink) },
            text = { Text(stringResource(R.string.exit_dialog_body), color = yp.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    TreadmillManager.stop()
                    onExit()
                }) { Text(stringResource(R.string.exit_confirm), color = yp.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.exit_dismiss), color = yp.brand)
                }
            },
        )
    }
}

private fun sendSpeed(kmh: Double) {
    if (kmh > 0.0) TreadmillManager.setSpeed(kmh)
}

private fun showSpeedBoundToast(isMax: Boolean) {
    val context = TreadmillManager.context() ?: return
    val message = context.getString(
        if (isMax) R.string.max_speed_toast else R.string.min_speed_toast,
    )
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}

/** 设备休眠提示：蓝牙只能到达蓝牙模块，主控板需在跑步机上手动激活 */
private fun showSleepHintToast() {
    val context = TreadmillManager.context() ?: return
    android.widget.Toast.makeText(
        context,
        context.getString(R.string.sleep_toast),
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

@Composable
private fun StatRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StatItem(left.first, left.second, Modifier.weight(1f))
        StatItem(right.first, right.second, Modifier.weight(1f))
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    val yp = LocalYp.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = yp.ink, fontSize = 30.sp)
        Text(label, color = yp.muted, fontSize = 15.sp)
    }
}

/** −/＋ 大圆按钮：按下即开始连发，松手下发最终值 */
@Composable
private fun SpeedButton(
    iconRes: Int,
    locked: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yp = LocalYp.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .alpha(if (locked) 0.3f else 1f)
                .clip(CircleShape)
                .background(yp.surface)
                .holdPress(!locked, onHoldStart, onHoldEnd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = yp.ink,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 按压手势：按下触发连发起点，松手触发最终下发 */
private fun Modifier.holdPress(enabled: Boolean, onStart: () -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(enabled) {
        detectTapGestures(
            onPress = {
                if (enabled) {
                    onStart()
                    try {
                        awaitRelease()
                    } finally {
                        onEnd()
                    }
                }
            },
        )
    }

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
        ) { onClick() },
    )

@Composable
private fun QuickSpeedButton(value: Int, locked: Boolean, onClick: () -> Unit) {
    val yp = LocalYp.current
    Box(
        modifier = Modifier
            .size(54.dp)
            .alpha(if (locked) 0.3f else 1f)
            .clip(CircleShape)
            .background(yp.brandSofter)
            .noRippleClickable { if (!locked) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            color = yp.brandSoftInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BottomBar(
    modifier: Modifier = Modifier,
    locked: Boolean,
    playing: Boolean,
    waking: Boolean,
    finishing: Boolean,
    onLock: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    val yp = LocalYp.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // 锁屏（选中态 = brandSoft 实色，无描边）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.noRippleClickable { onLock() },
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(if (locked) yp.brandSoft else yp.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (locked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                    ),
                    contentDescription = stringResource(R.string.lock_action),
                    tint = if (locked) yp.brandSoftInk else yp.ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(stringResource(R.string.lock_action), color = yp.muted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.width(30.dp))
        // 开始/暂停：开始 = 状态绿、暂停 = 品牌填充；减速过渡期禁用并显示"暂停中…"
        val playEnabled = !locked && !finishing
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(if (finishing) 0.35f else 1f)
                .noRippleClickable { if (playEnabled) onPlayPause() },
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(if (playing) yp.brandFill else yp.online),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = null,
                    tint = yp.onBrand,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                when {
                    playing && finishing -> stringResource(R.string.action_pausing)
                    playing -> stringResource(R.string.action_pause)
                    waking -> stringResource(R.string.waking)
                    else -> stringResource(R.string.action_start)
                },
                color = if (playing) yp.brand else yp.online,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.width(30.dp))
        // 停止：减速过渡期同步禁用（避免与暂停流程叠加）
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(if (finishing) 0.35f else 1f)
                .noRippleClickable { if (!locked && !finishing) onStop() },
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(yp.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.action_stop),
                    tint = yp.ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(stringResource(R.string.action_stop), color = yp.muted, fontSize = 13.sp)
        }
    }
}
