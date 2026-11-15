package com.yp.remote.ui

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import com.yp.remote.R
import com.yp.remote.ble.TreadmillManager

/**
 * 设置页：外观（主题）+ 语言 + 通用（调试入口）+ 关于。
 * Settings: appearance (theme) + language + general (debug entry) + about.
 * 语言经 appcompat per-app locales 切换并自动持久化。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    val mode by YpSettings.themeMode.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings),
            onBack = onBack,
        )

        // ---- 外观：主题三档 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.appearance)) {
            SettingsRow(iconRes = R.drawable.ic_theme, title = stringResource(R.string.theme_mode))
            Spacer(Modifier.height(6.dp))
            Segmented(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = mode,
            ) { YpSettings.setThemeMode(it) }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 通用：语言 + 调试入口 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.general)) {
            SettingsRow(iconRes = R.drawable.ic_language, title = stringResource(R.string.language))
            Spacer(Modifier.height(6.dp))
            val langMode by YpSettings.langMode.collectAsState()
            Segmented(
                options = listOf(
                    LangMode.SYSTEM to stringResource(R.string.lang_system),
                    LangMode.ZH to "中文",
                    LangMode.EN to "English",
                ),
                selected = langMode,
            ) { mode ->
                YpSettings.setLangMode(mode)
                // "跟随系统"固化系统语言快照而非空列表：空→非空的首次变更会重建窗口（YpApp 注释）
                val locales = when (mode) {
                    LangMode.SYSTEM -> LocaleListCompat.forLanguageTags(systemLocaleTags(context))
                    LangMode.ZH -> LocaleListCompat.forLanguageTags("zh-CN")
                    LangMode.EN -> LocaleListCompat.forLanguageTags("en")
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            Spacer(Modifier.height(10.dp))
            CardDivider()
            Spacer(Modifier.height(10.dp))
            SettingsRow(
                iconRes = R.drawable.ic_debug,
                title = stringResource(R.string.debug_entry),
                description = stringResource(R.string.debug_entry_desc),
                onClick = onOpenDebug,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 关于 ----
        SectionCard(Modifier.padding(horizontal = 20.dp), title = stringResource(R.string.about)) {
            SettingsRow(
                iconRes = R.drawable.ic_info,
                title = stringResource(R.string.version_fmt, versionName),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 系统语言快照：API 33+ 走 LocaleManager，低版本取进程默认 locale 列表 */
private fun systemLocaleTags(context: Context): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            ?.systemLocales?.toLanguageTags()
            ?: LocaleList.getDefault().toLanguageTags()
    } else {
        LocaleList.getDefault().toLanguageTags()
    }
