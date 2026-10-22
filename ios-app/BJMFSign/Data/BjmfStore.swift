import Foundation

/// 本机私有存储：任务、日志、位置收藏和主题色，保存在 App 沙盒的 UserDefaults 中。
final class BjmfStore {
    static let defaultAccentColor: Int64 = 0xFF3482FF

    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let lock = NSLock()

    private static let keyTasks = "tasks"
    private static let keyLogs = "logs"
    private static let keyFavoriteLocations = "favorite_locations"
    private static let keyAccentColor = "accent_color"
    private static let keyThemeMode = "theme_mode"
    private static let keyOnboardingDone = "onboarding_done"
    private static let maxLogs = 300

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    // MARK: Tasks

    func loadTasks() -> [BjmfTask] {
        decode([BjmfTask].self, key: Self.keyTasks).sorted { $0.updatedAt > $1.updatedAt }
    }

    @discardableResult
    func saveTask(_ task: BjmfTask) -> BjmfTask {
        lock.lock(); defer { lock.unlock() }
        let now = Clock.nowMillis()
        var stored = loadTasks()
        var next = task
        if task.id == 0 {
            next.id = now
            next.createdAt = now
            next.updatedAt = now
        } else {
            next.updatedAt = now
        }
        if let index = stored.firstIndex(where: { $0.id == next.id }) {
            stored[index] = next
        } else {
            stored.append(next)
        }
        encode(stored.sorted { $0.updatedAt > $1.updatedAt }, key: Self.keyTasks)
        return next
    }

    func deleteTask(_ taskId: Int64) {
        lock.lock(); defer { lock.unlock() }
        encode(loadTasks().filter { $0.id != taskId }, key: Self.keyTasks)
    }

    func findTask(_ taskId: Int64) -> BjmfTask? {
        loadTasks().first { $0.id == taskId }
    }

    // MARK: Favorite locations

    func loadFavoriteLocations() -> [FavoriteLocation] {
        decode([FavoriteLocation].self, key: Self.keyFavoriteLocations).sorted { $0.createdAt > $1.createdAt }
    }

    @discardableResult
    func saveFavoriteLocation(_ location: FavoriteLocation) -> FavoriteLocation {
        lock.lock(); defer { lock.unlock() }
        let now = Clock.nowMillis()
        var next = location
        if location.id == 0 {
            next.id = now
            next.createdAt = now
        }
        var stored = loadFavoriteLocations()
        if let index = stored.firstIndex(where: { $0.id == next.id }) {
            stored[index] = next
        } else {
            stored.append(next)
        }
        encode(stored.sorted { $0.createdAt > $1.createdAt }, key: Self.keyFavoriteLocations)
        return next
    }

    func deleteFavoriteLocation(_ locationId: Int64) {
        lock.lock(); defer { lock.unlock() }
        encode(loadFavoriteLocations().filter { $0.id != locationId }, key: Self.keyFavoriteLocations)
    }

    // MARK: Accent color

    func loadAccentColor() -> Int64 {
        guard defaults.object(forKey: Self.keyAccentColor) != nil else { return Self.defaultAccentColor }
        return Int64(defaults.integer(forKey: Self.keyAccentColor))
    }

    func saveAccentColor(_ color: Int64) {
        defaults.set(Int(color), forKey: Self.keyAccentColor)
        SharedStore.accentColor = color
    }

    // MARK: Appearance

    func loadThemeMode() -> String {
        defaults.string(forKey: Self.keyThemeMode) ?? "system"
    }

    func saveThemeMode(_ mode: String) {
        defaults.set(mode, forKey: Self.keyThemeMode)
    }

    // MARK: Onboarding

    /// 老用户（已有任务）升级后不再弹出首次引导。
    var isOnboardingDone: Bool {
        get { defaults.bool(forKey: Self.keyOnboardingDone) || !loadTasks().isEmpty }
        set { defaults.set(newValue, forKey: Self.keyOnboardingDone) }
    }

    // MARK: Backup

    func buildBackupJson() throws -> Data {
        let tasks = try JSONSerialization.jsonObject(with: encoder.encode(loadTasks()))
        let locations = try JSONSerialization.jsonObject(with: encoder.encode(loadFavoriteLocations()))
        let root: [String: Any] = [
            "format": BackupCrypto.format,
            "version": BackupCrypto.version,
            "platform": "ios",
            "exportedAt": Clock.nowMillis(),
            "tasks": tasks,
            "favoriteLocations": locations,
            "settings": [
                "accentColor": loadAccentColor(),
                "themeMode": loadThemeMode(),
                "language": I18n.language,
                "dynamicColor": false,
            ] as [String: Any],
        ]
        return try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
    }

    struct RestoreSummary {
        var tasks: Int
        var locations: Int
    }

    /// 用备份替换本机任务与位置收藏，并恢复界面设置。
    func restoreBackupJson(_ data: Data) throws -> RestoreSummary {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["format"] as? String == BackupCrypto.format else {
            throw BackupCrypto.Failure.invalidFile
        }
        let tasks = (root["tasks"] as? [[String: Any]] ?? []).compactMap(Self.task(from:))
        let locations = (root["favoriteLocations"] as? [[String: Any]] ?? []).compactMap(Self.location(from:))
        lock.lock()
        encode(tasks.sorted { $0.updatedAt > $1.updatedAt }, key: Self.keyTasks)
        encode(locations.sorted { $0.createdAt > $1.createdAt }, key: Self.keyFavoriteLocations)
        lock.unlock()
        if let settings = root["settings"] as? [String: Any] {
            if let color = (settings["accentColor"] as? NSNumber)?.int64Value { saveAccentColor(color) }
            if let mode = settings["themeMode"] as? String, !mode.isEmpty { saveThemeMode(mode) }
            if let language = settings["language"] as? String, !language.isEmpty { I18n.language = language }
        }
        return RestoreSummary(tasks: tasks.count, locations: locations.count)
    }

    private static func int64(_ value: Any?) -> Int64? {
        (value as? NSNumber)?.int64Value ?? (value as? String).flatMap { Int64($0) }
    }

    private static func string(_ value: Any?) -> String? {
        if let text = value as? String { return text }
        if let number = value as? NSNumber { return number.stringValue }
        return nil
    }

    private static func task(from json: [String: Any]) -> BjmfTask? {
        guard let id = int64(json["id"]), let name = string(json["name"]) else { return nil }
        let now = Clock.nowMillis()
        return BjmfTask(
            id: id,
            name: name,
            classId: string(json["classId"]) ?? "",
            cookie: string(json["cookie"]) ?? "",
            lat: string(json["lat"]) ?? "",
            lng: string(json["lng"]) ?? "",
            acc: string(json["acc"])?.nilIfEmpty ?? "30",
            wxKey: string(json["wxKey"]) ?? "",
            qqKey: string(json["qqKey"]) ?? "",
            times: (json["times"] as? [Any] ?? []).compactMap { string($0)?.nilIfEmpty },
            dateStart: string(json["dateStart"])?.nilIfEmpty,
            dateEnd: string(json["dateEnd"])?.nilIfEmpty,
            enabled: (json["enabled"] as? Bool) ?? true,
            createdAt: int64(json["createdAt"]) ?? now,
            updatedAt: int64(json["updatedAt"]) ?? now
        )
    }

    private static func location(from json: [String: Any]) -> FavoriteLocation? {
        guard let id = int64(json["id"]),
              let lat = string(json["lat"]),
              let lng = string(json["lng"]) else { return nil }
        return FavoriteLocation(
            id: id,
            name: string(json["name"]) ?? "",
            lat: lat,
            lng: lng,
            createdAt: int64(json["createdAt"]) ?? Clock.nowMillis()
        )
    }

    // MARK: Logs

    func appendLog(_ log: TaskLog) {
        lock.lock(); defer { lock.unlock() }
        var logs = loadLogs(limit: Int.max)
        logs.insert(log, at: 0)
        let recent = Array(logs.sorted { $0.runAt > $1.runAt }.prefix(Self.maxLogs))
        encode(recent, key: Self.keyLogs)
    }

    func clearLogs() {
        lock.lock(); defer { lock.unlock() }
        encode([TaskLog](), key: Self.keyLogs)
    }

    func loadLogs(taskId: Int64? = nil, limit: Int = 100) -> [TaskLog] {
        let logs = decode([TaskLog].self, key: Self.keyLogs)
            .filter { taskId == nil || $0.taskId == taskId }
            .sorted { $0.runAt > $1.runAt }
        return Array(logs.prefix(limit))
    }

    // MARK: Helpers

    private func decode<T: Decodable & RangeReplaceableCollection>(_ type: T.Type, key: String) -> T {
        guard let data = defaults.data(forKey: key),
              let value = try? decoder.decode(T.self, from: data) else {
            return T()
        }
        return value
    }

    private func encode<T: Encodable>(_ value: T, key: String) {
        if let data = try? encoder.encode(value) {
            defaults.set(data, forKey: key)
        }
    }
}
