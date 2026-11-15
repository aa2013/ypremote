package com.yp.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.round

/**
 * 坡度尺，复刻原 App com.yp.yilian.sports.view.RulerView（逆向文档 §9.3）：
 * - 刻度间距 12dp，每 1 个坡度单位 3 格，主线 32dp / 次线 16dp，线宽 4dp 圆角；
 * - 刻度线 #AAAAAA，按距中心距离三次方淡出；中心附近改用指示渐变；
 * - 当前值刻度固定居中，数值向右增大；整数刻度下方标注数字；
 * - 中心指示条 4dp 宽、上下各超出 1/6，渐变 #14C8C8 → #4060FF。
 */
@Composable
fun SlopeRuler(
    value: Float,
    minValue: Float,
    maxValue: Float,
    onValueChange: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val itemSpacing = with(density) { 12.dp.toPx() }
    val lineWidth = with(density) { 4.dp.toPx() }
    val middleHeight = with(density) { 32.dp.toPx() }
    val minHeight = with(density) { 16.dp.toPx() }
    val textMarginTop = with(density) { 8.dp.toPx() }
    val yp = LocalYp.current

    // 偏移：offset = -(value - min) * spacing * 3，范围 [maxOffset, 0]
    var offset by remember(value, minValue, maxValue) {
        mutableFloatStateOf(-(value - minValue) * itemSpacing * 3)
    }
    val totalLines = ((maxValue - minValue) * 3).toInt() + 1
    val maxOffset = -(totalLines - 1) * itemSpacing
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = yp.muted, fontSize = 14.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .pointerInput(itemSpacing, minValue, maxValue) {
                detectDragGestures(
                    onDragEnd = {
                        // 松手吸附到整数格（对应 RulerView.b()）
                        val snap = round(abs(offset) / (itemSpacing * 3)) * 3
                        offset = -snap * itemSpacing
                        val v = minValue + snap / 3f
                        onValueChange(v, false)
                    },
                    onDragCancel = {
                        val snap = round(abs(offset) / (itemSpacing * 3)) * 3
                        offset = -snap * itemSpacing
                    },
                ) { change, dragAmount ->
                    change.consume()
                    offset = (offset - dragAmount.x).coerceIn(maxOffset, 0f)
                    // value = min + rint(|offset| / spacing) * perSpan / 3
                    val v = minValue + round(abs(offset) / itemSpacing) / 3f
                    onValueChange(v, true)
                }
            },
    ) {
        val centerX = size.width / 2f
        val halfWidth = size.width / 2f
        val lineRadius = lineWidth / 2f
        val brush = Brush.verticalGradient(listOf(yp.gradA, yp.gradB))
        for (i in 0 until totalLines) {
            val x = centerX + offset + i * itemSpacing
            if (x < -itemSpacing || x > size.width + itemSpacing) continue
            val fade = (1f - abs(x - centerX) / halfWidth).coerceIn(0f, 1f)
            val alpha = (fade * fade * fade * 255f).toInt()
            val isMajor = i % 3 == 0
            val lineH = if (isMajor) middleHeight else minHeight
            val nearCenter = abs(x - centerX) / halfWidth < 0.12f
            val rect = RoundRect(
                Rect(
                    Offset(x - lineRadius, 0f),
                    Size(lineWidth, lineH),
                ),
                CornerRadius(lineRadius, lineRadius),
            )
            if (nearCenter) {
                drawPath(Path().apply { addRoundRect(rect) }, brush, alpha = alpha / 255f, style = Fill)
            } else {
                drawRoundRect(
                    color = yp.ink2,
                    topLeft = Offset(x - lineRadius, 0f),
                    size = Size(lineWidth, lineH),
                    cornerRadius = CornerRadius(lineRadius, lineRadius),
                    alpha = alpha / 255f,
                )
            }
            if (isMajor) {
                val label = (minValue + i / 3).toInt().toString()
                val layout = textMeasurer.measure(label, textStyle)
                drawText(
                    layout,
                    color = yp.muted,
                    alpha = alpha / 255f,
                    topLeft = Offset(
                        x - layout.size.width / 2f,
                        lineH + textMarginTop,
                    ),
                )
            }
        }
        // 中心指示条（上下各延伸 1/6 高度）
        val over = middleHeight / 6f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(centerX - lineRadius, -over),
            size = Size(lineWidth, middleHeight + over * 2),
            cornerRadius = CornerRadius(lineRadius, lineRadius),
        )
    }
}
