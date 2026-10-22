package com.bjmf.sign.android.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 轻量多语言：以简体中文原文作为 key，繁体中文与英文译文见 [I18nTables]（由 i18n/translations.json 生成）。
 * 占位符使用 {0}、{1}…，与 iOS 端共用同一份译文。
 */
object I18n {
    const val SYSTEM = "system"
    const val ZH_HANS = "zh-Hans"
    const val ZH_HANT = "zh-Hant"
    const val EN = "en"

    /** 用户选择的界面语言；Compose 中读取会在切换后自动重组。 */
    var language by mutableStateOf(SYSTEM)

    /** 实际生效的语言（跟随系统时按系统语言解析）。 */
    fun resolved(): String = when (language) {
        ZH_HANS, ZH_HANT, EN -> language
        else -> systemLanguage()
    }

    fun systemLanguage(): String {
        val locale = Locale.getDefault()
        if (locale.language != "zh") return EN
        val traditional = locale.script == "Hant" ||
            (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
        return if (traditional) ZH_HANT else ZH_HANS
    }

    fun translate(key: String): String = when (resolved()) {
        ZH_HANT -> I18nTables.hant[key] ?: key
        EN -> I18nTables.en[key] ?: key
        else -> key
    }

    /** 当前语言对应的 Locale，用于日期格式化。 */
    fun locale(): Locale = when (resolved()) {
        ZH_HANT -> Locale.TRADITIONAL_CHINESE
        EN -> Locale.ENGLISH
        else -> Locale.SIMPLIFIED_CHINESE
    }
}

/** 翻译并替换 {0}、{1}… 占位符。 */
fun tr(key: String, vararg args: Any?): String {
    var text = I18n.translate(key)
    args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
    return text
}
