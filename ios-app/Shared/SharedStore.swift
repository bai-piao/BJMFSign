import Foundation

/// App 与小组件共享的数据（App Group）：界面语言、主题色和上一次执行结果。
enum SharedStore {
    static let appGroup = "group.com.bjmf.sign.ios"
    static let widgetKind = "LastResultWidget"

    private static let lastResultKey = "last_result"
    private static let accentKey = "widget_accent_color"

    static let defaults: UserDefaults = UserDefaults(suiteName: appGroup) ?? .standard

    struct LastResult: Codable, Equatable {
        var taskName: String
        var status: String
        var reason: String
        var runAt: Int64
        var succeeded: Int
        var total: Int
        var nextRunAt: Int64?
    }

    static func loadLastResult() -> LastResult? {
        guard let data = defaults.data(forKey: lastResultKey) else { return nil }
        return try? JSONDecoder().decode(LastResult.self, from: data)
    }

    static func saveLastResult(_ result: LastResult) {
        if let data = try? JSONEncoder().encode(result) {
            defaults.set(data, forKey: lastResultKey)
        }
    }

    static var accentColor: Int64 {
        get {
            guard defaults.object(forKey: accentKey) != nil else { return 0xFF3482FF }
            return Int64(defaults.integer(forKey: accentKey))
        }
        set { defaults.set(Int(newValue), forKey: accentKey) }
    }

    static func isSuccess(_ status: String) -> Bool {
        status == "success" || status == "already_signed"
    }
}
