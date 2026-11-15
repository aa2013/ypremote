package com.yp.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yp.remote.R
import com.yp.remote.ble.ConnState
import com.yp.remote.ble.FtmsUuids
import com.yp.remote.ble.TreadmillCommands
import com.yp.remote.ble.TreadmillManager
import com.yp.remote.ble.WakeState

/**
 * 调试页（原连接页调试区迁入）：连接状态、解析数据、原始 hex、协议测试按钮。
 * Debug screen (moved from connect page): state, parsed data, raw hex, protocol tests.
 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val state by TreadmillManager.state.collectAsState()
    val connectedName by TreadmillManager.connectedName.collectAsState()
    val lastOp by TreadmillManager.lastOp.collectAsState()
    val raw by TreadmillManager.rawNotify.collectAsState()
    val sample by TreadmillManager.sample.collectAsState()
    val wake by TreadmillManager.wakeState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.debug_title),
            onBack = onBack,
        )

        // ---- 状态 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.debug_state)) {
            InfoRow(
                stringResource(R.string.debug_conn_state),
                when (state) {
                    ConnState.SCANNING -> stringResource(R.string.status_scanning)
                    ConnState.CONNECTING -> stringResource(R.string.status_connecting)
                    ConnState.READY -> connectedName ?: ""
                    ConnState.DISCONNECTED -> "—"
                },
            )
            InfoRow(
                stringResource(R.string.debug_wake_state),
                when (wake) {
                    WakeState.NONE -> "NONE"
                    WakeState.WAKING -> "WAKING"
                    WakeState.SLEEPING -> "SLEEPING"
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 解析数据 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.debug_parsed)) {
            InfoRow(
                stringResource(R.string.speed_label),
                "${sample.speedKmh ?: "—"} km/h",
            )
            InfoRow(stringResource(R.string.stat_distance), "${sample.distanceMeter ?: "—"} m")
            InfoRow(stringResource(R.string.stat_kcal), "${sample.kcal ?: "—"}")
            InfoRow("HR", "${sample.heartRate ?: "—"}")
            InfoRow(stringResource(R.string.stat_time), "${sample.elapsedSec ?: "—"} s")
            InfoRow("Steps", "${sample.steps ?: "—"}")
            Spacer(Modifier.height(8.dp))
            InfoRow(stringResource(R.string.debug_last_op), lastOp)
            InfoRow(stringResource(R.string.debug_raw_notify), raw.ifEmpty { "—" })
        }

        Spacer(Modifier.height(12.dp))

        // ---- 协议测试 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.debug_tests)) {
            val ready = state == ConnState.READY
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton(stringResource(R.string.test_esl_handshake), Modifier.weight(1f), ready) {
                    TreadmillManager.eslRetries = 0
                    TreadmillManager.eslStartHandshake()
                }
                MiniButton(stringResource(R.string.test_esl_start), Modifier.weight(1f), ready) {
                    TreadmillManager.eslStart()
                }
                MiniButton(stringResource(R.string.test_esl_speed), Modifier.weight(1f), ready) {
                    TreadmillManager.eslSetSpeed(4.0)
                }
                MiniButton(stringResource(R.string.test_esl_stop), Modifier.weight(1f), ready) {
                    TreadmillManager.eslStop()
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton(stringResource(R.string.test_subscribe_fff1), Modifier.weight(1f), ready) {
                    TreadmillManager.subscribeRaw(FtmsUuids.ESL_SERVICE, FtmsUuids.ESL_NOTIFY)
                }
                MiniButton(stringResource(R.string.test_read_model), Modifier.weight(1f), ready) {
                    TreadmillManager.readRaw(FtmsUuids.DIS_SERVICE, FtmsUuids.MODEL_NUMBER)
                }
                MiniButton(stringResource(R.string.test_read_fw), Modifier.weight(1f), ready) {
                    TreadmillManager.readRaw(FtmsUuids.DIS_SERVICE, FtmsUuids.FIRMWARE_REV)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniButton(stringResource(R.string.test_ftms_unlock), Modifier.weight(1f), ready) {
                    TreadmillManager.sendRaw(
                        FtmsUuids.SERVICE,
                        FtmsUuids.VENDOR_UNLOCK,
                        TreadmillCommands.unlockYptm(),
                        "FTMS解锁",
                        noResponse = true,
                    )
                }
                MiniButton(stringResource(R.string.test_ftms_start), Modifier.weight(1f), ready) {
                    TreadmillManager.sendRaw(
                        FtmsUuids.SERVICE,
                        FtmsUuids.CONTROL_POINT,
                        TreadmillCommands.start(),
                        "FTMS开始",
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
