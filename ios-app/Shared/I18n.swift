import Foundation

/// 轻量多语言：以简体中文原文作为 key，繁体中文与英文译文见 `I18nTables`（由 i18n/translations.json 生成）。
/// 占位符使用 {0}、{1}…，与 Android 端共用同一份译文。
enum I18n {
    static let system = "system"
    static let zhHans = "zh-Hans"
    static let zhHant = "zh-Hant"
    static let en = "en"

    private static let languageKey = "language"

    /// 用户选择的界面语言，保存在 App Group 中，小组件也能读取。
    static var language: String {
        get { SharedStore.defaults.string(forKey: languageKey) ?? system }
        set { SharedStore.defaults.set(newValue, forKey: languageKey) }
    }

    /// 实际生效的语言（跟随系统时按系统语言解析）。
    static var resolved: String {
        switch language {
        case zhHans, zhHant, en: return language
        default: return systemLanguage
        }
    }

    static var systemLanguage: String {
        guard let preferred = Locale.preferredLanguages.first else { return zhHans }
        let locale = Locale(identifier: preferred)
        guard locale.language.languageCode?.identifier == "zh" else { return en }
        if locale.language.script?.identifier == "Hant" { return zhHant }
        if let region = locale.region?.identifier, ["TW", "HK", "MO"].contains(region) { return zhHant }
        return zhHans
    }

    static func translate(_ key: String) -> String {
        switch resolved {
        case zhHant: return I18nTables.hant[key] ?? key
        case en: return I18nTables.en[key] ?? key
        default: return key
        }
    }

    /// 当前语言对应的 Locale，用于日期格式化。
    static var locale: Locale {
        switch resolved {
        case zhHant: return Locale(identifier: "zh_Hant")
        case en: return Locale(identifier: "en_US")
        default: return Locale(identifier: "zh_CN")
        }
    }
}

/// 翻译并替换 {0}、{1}… 占位符。
func tr(_ key: String, _ args: Any...) -> String {
    var text = I18n.translate(key)
    for (index, arg) in args.enumerated() {
        text = text.replacingOccurrences(of: "{\(index)}", with: "\(arg)")
    }
    return text
}
