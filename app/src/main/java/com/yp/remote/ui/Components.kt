package com.yp.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yp.remote.R

/**
 * 共享组件：按 token 契约实现——默认无边框，层次靠底色差与内边距；
 * 实心大面积走品牌渐变，选中态 = brandSoft 实色 + brandSoftInk 文字、不加描边。
 * Shared widgets following the token contract: borderless, brand-gradient fills,
 * solid brandSoft selection without strokes.
 */

/** 主操作按钮：品牌渐变填充 + onBrand 文字（禁用 = surface3 底 + faint 文字） */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val yp = LocalYp.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(YpRadius.md)
            .background(if (enabled) yp.brandGradient() else SolidColor(yp.surface3))
            .clickable(enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) yp.onBrand else yp.faint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 次级按钮：surface2 实色底 + ink 文字（无边框契约） */
@Composable
fun PlainButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val yp = LocalYp.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(YpRadius.md)
            .background(yp.surface2)
            .clickable(enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) yp.ink else yp.faint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 小号测试按钮（调试页用）：surface2 底 + ink 文字 */
@Composable
fun MiniButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val yp = LocalYp.current
    Box(
        modifier = modifier
            .clip(YpRadius.sm)
            .background(yp.surface2)
            .clickable(enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) yp.ink else yp.faint, fontSize = 12.sp)
    }
}

/** 圆形图标按钮（可着色矢量图标 + 实色底） */
@Composable
fun CircleIconButton(
    iconRes: Int,
    size: Dp,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/** 页头：返回 + 标题 + 右侧插槽（含状态栏内边距） */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val yp = LocalYp.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = yp.ink,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(8.dp),
            )
            Spacer(Modifier.size(4.dp))
        }
        Text(
            title,
            color = yp.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** 卡片分区：surface 实色 + r8 圆角 + 可选标题（无边框） */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val yp = LocalYp.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(YpRadius.md)
            .background(yp.surface)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(
                title,
                color = yp.ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** 卡片行：图标 + 标题/描述 + 右侧动作（设置页用） */
@Composable
fun SettingsRow(
    iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val yp = LocalYp.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(YpRadius.sm)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = yp.ink2,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = yp.ink, fontSize = 15.sp)
            if (description != null) {
                Text(description, color = yp.muted, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}

/** 卡片内分隔线（菜单分组允许的分隔线，仅限内部细节） */
@Composable
fun CardDivider() {
    HorizontalDivider(color = LocalYp.current.line, thickness = 1.dp)
}

/**
 * 分段选择器：surface2 轨道 + brandSoft 实色选中（无描边）。
 * Segmented control: surface2 track, solid brandSoft selected pill.
 */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val yp = LocalYp.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(YpRadius.full)
            .background(yp.surface2)
            .padding(2.dp),
    ) {
        options.forEach { (value, label) ->
            val selectedNow = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(YpRadius.full)
                    .background(if (selectedNow) yp.brandSoft else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selectedNow) yp.brandSoftInk else yp.ink2,
                    fontSize = 14.sp,
                    fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 键值信息行（调试页用） */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val yp = LocalYp.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = yp.muted, fontSize = 13.sp)
        Text(value, color = yp.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * mesh 光晕背景：bg 底色 + 左上 cyan / 中部 violet / 右下 blue 三组径向柔光
 * （对应 ClipShare tokens.md §9 舞台光晕，亮暗透明度在 token 里）。
 * Mesh glow background: bg base + three radial glows per the ClipShare stage tokens.
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    val yp = LocalYp.current
    Canvas(modifier) {
        drawRect(color = yp.bg)
        glow(yp.stageCyan, 0.12f, 0.05f, 0.8f)
        glow(yp.stageViolet, 0.55f, 0.42f, 0.85f)
        glow(yp.stageBlue, 0.95f, 0.92f, 0.75f)
    }
}

/** 单个径向光晕：色心到透明，半径按屏宽比例 */
private fun DrawScope.glow(color: Color, cx: Float, cy: Float, radiusScale: Float) {
    val center = Offset(cx * size.width, cy * size.height)
    val radius = radiusScale * size.width
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
