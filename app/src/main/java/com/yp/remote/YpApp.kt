package com.yp.remote

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import com.yp.remote.ui.LangMode
import com.yp.remote.ui.YpSettings

/**
 * 应用级初始化：设置存储 + per-app locale 预置。
 * App-level init: settings storage + per-app locale pre-seeding.
 */
class YpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        YpSettings.init(this)
        preseedAppLocales()
    }

    /**
     * 语言预置：规避 API 33+ 已知行为——per-app locale 首次从"未设置"变"已设置"
     * （空 → 非空）会绕过 configChanges 强制重建窗口，产生一次可见闪烁
     * （Google issuetracker 321401619；后续 set→set 变更均原地应用）。
     * 处理：进程启动时（尚无任何窗口，重建不可见）把语言固化为非空——
     * 跟随系统写系统语言快照、显式语言确保与目标一致。
     * 代价：系统语言在应用运行期间变更不实时跟随，重新进入应用时重新快照。
     */
    private fun preseedAppLocales() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val lm = getSystemService(LocaleManager::class.java) ?: return
        val target = when (YpSettings.langMode.value) {
            LangMode.ZH -> LocaleList.forLanguageTags(LANG_ZH)
            LangMode.EN -> LocaleList.forLanguageTags(LANG_EN)
            LangMode.SYSTEM -> lm.systemLocales
        }
        if (lm.applicationLocales.isEmpty || lm.applicationLocales != target) {
            lm.setApplicationLocales(target)
        }
    }

    private companion object {
        const val LANG_ZH = "zh-CN"
        const val LANG_EN = "en"
    }
}
