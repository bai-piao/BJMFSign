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
