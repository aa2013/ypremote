package com.yp.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yp.remote.R
import com.yp.remote.data.WorkoutRecord
import com.yp.remote.data.WorkoutStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 跑步记录页：时间倒序列表，单条可删、可一键清空（清空带确认）。
 * History screen: newest-first list, per-item delete and clear-all with confirm.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val yp = LocalYp.current
    val records by WorkoutStore.records.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WorkoutRecord?>(null) }
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            trailing = {
                if (records.isNotEmpty()) {
                    Text(
                        stringResource(R.string.history_clear),
                        color = yp.danger,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showClearDialog = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            },
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    color = yp.faint,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        dateText = Instant.ofEpochMilli(record.startTime)
                            .atZone(ZoneId.systemDefault())
                            .format(dateFmt),
                        onDelete = { pendingDelete = record },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = yp.surface,
            title = { Text(stringResource(R.string.history_clear_title), color = yp.ink) },
            text = { Text(stringResource(R.string.history_clear_body), color = yp.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    WorkoutStore.clearAll()
                }) { Text(stringResource(R.string.history_clear), color = yp.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel), color = yp.brand)
                }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = yp.surface,
            title = { Text(stringResource(R.string.record_delete_title), color = yp.ink) },
            text = {
                Text(
                    Instant.ofEpochMilli(target.startTime)
                        .atZone(ZoneId.systemDefault())
                        .format(dateFmt),
                    color = yp.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    WorkoutStore.delete(target.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete), color = yp.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel), color = yp.brand)
                }
            },
        )
    }
}

/** 单条记录卡片：日期 + 删除按钮，下方四项小统计 */
@Composable
private fun RecordCard(
    record: WorkoutRecord,
    dateText: String,
    onDelete: () -> Unit,
) {
    val yp = LocalYp.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YpRadius.md)
            .background(yp.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dateText,
                color = yp.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                contentDescription = stringResource(R.string.delete),
                tint = yp.faint,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            RecordStat(Format.elapsed(record.durationSec), stringResource(R.string.stat_time))
            RecordStat(
                Format.distance(record.distanceMeter),
                stringResource(R.string.stat_distance),
            )
            RecordStat(record.kcal.toString(), stringResource(R.string.stat_kcal))
            RecordStat(
                Format.speed(record.maxSpeedKmh),
                stringResource(R.string.record_max_speed),
            )
        }
    }
}

@Composable
private fun RowScope.RecordStat(value: String, label: String) {
    val yp = LocalYp.current
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = yp.ink, fontSize = 15.sp)
        Text(label, color = yp.muted, fontSize = 12.sp)
    }
}
