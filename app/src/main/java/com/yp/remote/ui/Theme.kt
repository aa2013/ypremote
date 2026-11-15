package com.yp.remote.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 主题模式：跟随系统 / 浅色 / 深色 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 语言模式：跟随系统 / 中文 / English（"跟随系统" = 固化系统语言快照，不落空 locale） */
enum class LangMode { SYSTEM, ZH, EN }

/** 应用设置（主题/语言模式持久化） */
object YpSettings {
    private const val PREFS_NAME = "yp_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LANG_MODE = "lang_mode"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _langMode = MutableStateFlow(LangMode.SYSTEM)
    val langMode: StateFlow<LangMode> = _langMode

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_THEME_MODE, null)
        _themeMode.value = saved?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM)
        } ?: ThemeMode.SYSTEM
        val savedLang = prefs?.getString(KEY_LANG_MODE, null)
        _langMode.value = savedLang?.let {
            runCatching { LangMode.valueOf(it) }.getOrDefault(LangMode.SYSTEM)
        } ?: LangMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setLangMode(mode: LangMode) {
        _langMode.value = mode
        prefs?.edit()?.putString(KEY_LANG_MODE, mode.name)?.apply()
    }
}

/**
 * 设计 token（语义契约对齐 ClipShare tokens.md，亮暗一一对应）。
 * Design tokens aligned with the ClipShare token contract; light/dark values paired 1:1.
 *
 * 组件样式只引用这里的语义 token，不写裸色值。
 * Components reference semantic tokens only — no raw color literals.
 */
@Immutable
data class YpColors(
    // 表面 Surface
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    // 文字 Text
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val faint: Color,
    // 品牌 Brand（brand=文字档 / brandFill=填充档 / brandSoft=选中淡底，三者不可互换）
    val brand: Color,
    val brandFill: Color,
    val brandFillStrong: Color,
    val brandSoft: Color,
    val brandSoftInk: Color,
    val brandSofter: Color,
    val onBrand: Color,
    val gradA: Color,
    val gradB: Color,
    // 状态 Status
    val online: Color,
    val onlineSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    // 线与交互态 Line & state
    val line: Color,
    val line2: Color,
    val stateHover: Color,
    val statePressed: Color,
    // mesh 光晕（页面背景柔光，含透明度的预计算值）
    val stageCyan: Color,
    val stageViolet: Color,
    val stageBlue: Color,
) {
    /** 135° 品牌渐变（实心大面积：主按钮/FAB/实心选中） */
    fun brandGradient(): Brush = Brush.linearGradient(listOf(gradA, gradB))
}

/** 亮色主题（取值 = ClipShare tokens.md 定案值） */
val LightYp = YpColors(
    bg = Color(0xFFF0F2F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEBEEF2),
    surface3 = Color(0xFFE3E7ED),
    ink = Color(0xFF1F2329),
    ink2 = Color(0xFF4E5969),
    muted = Color(0xFF616D7B),
    faint = Color(0xFF8A95A1),
    brand = Color(0xFF4262B8),
    brandFill = Color(0xFF4E71C6),
    brandFillStrong = Color(0xFF3D549E),
    brandSoft = Color(0xFFCFDAF6),
    brandSoftInk = Color(0xFF3955A0),
    brandSofter = Color(0xFFEDF1FC),
    onBrand = Color(0xFFFFFFFF),
    gradA = Color(0xFF4FA8E8),
    gradB = Color(0xFF8B7FF0),
    online = Color(0xFF4CAF50),
    onlineSoft = Color(0xFFE6F3E6),
    warning = Color(0xFF8F5B08),
    warningSoft = Color(0xFFFBF1DE),
    danger = Color(0xFFB1423F),
    dangerSoft = Color(0xFFFBE9E7),
    info = Color(0xFF175E9E),
    infoSoft = Color(0xFFE3F0FA),
    line = Color(0xFFE2E6EC),
    line2 = Color(0xFFD4DAE2),
    stateHover = Color(0x0F1F2329),
    statePressed = Color(0x1A1F2329),
    stageCyan = Color(0x574FA8E8),
    stageViolet = Color(0x478B7FF0),
    stageBlue = Color(0x332563EB),
)

/** 暗色主题（与亮色一一对应） */
val DarkYp = YpColors(
    bg = Color(0xFF0C0E11),
    surface = Color(0xFF24292F),
    surface2 = Color(0xFF15181C),
    surface3 = Color(0xFF2E343B),
    ink = Color(0xFFECEFF3),
    ink2 = Color(0xFFB4BCC6),
    muted = Color(0xFF99A3AF),
    faint = Color(0xFF5F6B77),
    brand = Color(0xFFA9BCDF),
    brandFill = Color(0xFF4E71C6),
    brandFillStrong = Color(0xFF3D549E),
    brandSoft = Color(0xFF243046),
    brandSoftInk = Color(0xFFA9BCDF),
    brandSofter = Color(0xFF1B2536),
    onBrand = Color(0xFFFFFFFF),
    gradA = Color(0xFF5CB2F0),
    gradB = Color(0xFF9D8CF5),
    online = Color(0xFF82C785),
    onlineSoft = Color(0xFF16301E),
    warning = Color(0xFFE2A84F),
    warningSoft = Color(0xFF33270F),
    danger = Color(0xFFF0918A),
    dangerSoft = Color(0xFF3A1F1D),
    info = Color(0xFF7FB8E8),
    infoSoft = Color(0xFF16293A),
    line = Color(0xFF333940),
    line2 = Color(0xFF424A54),
    stateHover = Color(0x14FFFFFF),
    statePressed = Color(0x1FFFFFFF),
    stageCyan = Color(0x294FA8E8),
    stageViolet = Color(0x248B7FF0),
    stageBlue = Color(0x242563EB),
)

/** 语义 token 的 CompositionLocal */
val LocalYp = staticCompositionLocalOf { LightYp }

/** 圆角 token（卡片/按钮 8，浮层 12，FAB 16，胶囊 full） */
object YpRadius {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(8.dp)
    val overlay = RoundedCornerShape(12.dp)
    val xl = RoundedCornerShape(16.dp)
    val full = RoundedCornerShape(50)
}

/** 动效 token：统一缓动 + 分层时长（微交互 150 / 转场 250 / 页面 350） */
object YpMotion {
    val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val easeInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    const val durMicro = 150
    const val durTrans = 250
    const val durPage = 350
}

/**
 * 应用主题：提供 LocalYp 语义 token，并把常用 token 映射进 MaterialTheme，
 * 让 AlertDialog/Progress 等 M3 组件随主题走。
 * Provides LocalYp and mirrors tokens into MaterialTheme so M3 widgets follow the theme.
 */
@Composable
fun YpTheme(dark: Boolean, content: @Composable () -> Unit) {
    val yp = if (dark) DarkYp else LightYp
    CompositionLocalProvider(LocalYp provides yp) {
        MaterialTheme(colorScheme = m3Scheme(yp, dark)) { content() }
    }
}

private fun m3Scheme(yp: YpColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = yp.brandFill,
        onPrimary = yp.onBrand,
        primaryContainer = yp.brandSoft,
        onPrimaryContainer = yp.brandSoftInk,
        secondary = yp.brandFill,
        onSecondary = yp.onBrand,
        background = yp.bg,
        onBackground = yp.ink,
        surface = yp.surface,
        onSurface = yp.ink,
        surfaceVariant = yp.surface2,
        onSurfaceVariant = yp.ink2,
        error = yp.danger,
        outline = yp.line2,
        outlineVariant = yp.line,
    )
} else {
    lightColorScheme(
        primary = yp.brandFill,
        onPrimary = yp.onBrand,
        primaryContainer = yp.brandSoft,
        onPrimaryContainer = yp.brandSoftInk,
        secondary = yp.brandFill,
        onSecondary = yp.onBrand,
        background = yp.bg,
        onBackground = yp.ink,
        surface = yp.surface,
        onSurface = yp.ink,
        surfaceVariant = yp.surface2,
        onSurfaceVariant = yp.ink2,
        error = yp.danger,
        outline = yp.line2,
        outlineVariant = yp.line,
    )
}

/** 显示格式化工具（对应原 App f8.g 的 x/u） */
object Format {

    /** 秒 → H:MM:SS（>1 小时）或 MM:SS */
    fun elapsed(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** 秒/公里 → 配速 M'SS'' */
    fun pace(secPerKm: Int): String {
        val m = secPerKm / 60
        val s = secPerKm % 60
        return String.format(Locale.US, "%d'%02d''", m, s)
    }

    /** 速度 → 保留 1 位小数，去掉多余的 .0 */
    fun speed(kmh: Float): String {
        val rounded = (kmh * 10).toInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /** 距离米 → 公里，3 位小数 */
    fun distance(meter: Int): String = String.format(Locale.US, "%.3f", meter / 1000.0)
}
