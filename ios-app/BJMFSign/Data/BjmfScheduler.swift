import BackgroundTasks
import Foundation
import UserNotifications

/// 定时签到调度。
///
/// iOS 不允许 App 像 Android `AlarmManager` 那样在精确时间点后台执行代码，因此采用三重机制：
/// 1. 在每个任务的下一次执行时间投递本地通知；点击通知或通知上的「立即签到」按钮即执行签到。
/// 2. 申请 `BGAppRefreshTask` 后台刷新，系统调度到时自动补签（时间由系统决定，不保证准时）。
/// 3. App 回到前台时，自动补签最近 `catchUpWindow` 内错过的执行时间点。
final class BjmfScheduler {
    static let refreshTaskIdentifier = "com.bjmf.sign.ios.refresh"
    static let notificationCategory = "BJMF_SIGN_TASK"
    static let signActionIdentifier = "BJMF_SIGN_NOW"
    static let taskIdKey = "taskId"
    /// 错过执行时间后，仍允许自动补签的时间窗口。
    static let catchUpWindow: TimeInterval = 30 * 60

    private static let lastAutoRunKey = "last_auto_run"
    private static let zone = TimeZone(identifier: "Asia/Shanghai")!

    private let store: BjmfStore
    private let defaults: UserDefaults

    init(store: BjmfStore, defaults: UserDefaults = .standard) {
        self.store = store
        self.defaults = defaults
    }

    // MARK: Registration

    static func registerNotificationCategory() {
        let action = UNNotificationAction(identifier: signActionIdentifier, title: "立即签到", options: [])
        let category = UNNotificationCategory(identifier: notificationCategory, actions: [action], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }

    static func requestNotificationPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    // MARK: Scheduling

    func scheduleAll(_ tasks: [BjmfTask]) {
        tasks.forEach { task in
            if task.enabled { schedule(task) } else { cancel(task.id) }
        }
        scheduleBackgroundRefresh(tasks)
    }

    func schedule(_ task: BjmfTask) {
        cancel(task.id)
        guard task.enabled, let next = nextRun(for: task) else { return }

        let content = UNMutableNotificationContent()
        content.title = "签到时间到：\(task.name)"
        content.body = "点击通知或「立即签到」执行本次签到"
        content.sound = .default
        content.categoryIdentifier = Self.notificationCategory
        content.userInfo = [Self.taskIdKey: NSNumber(value: task.id)]

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = Self.zone
        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: next)
        components.timeZone = Self.zone
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        let request = UNNotificationRequest(identifier: Self.notificationId(task.id), content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
        scheduleBackgroundRefresh(store.loadTasks())
    }

    func cancel(_ taskId: Int64) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [Self.notificationId(taskId)])
    }

    func scheduleBackgroundRefresh(_ tasks: [BjmfTask]) {
        let nextDates = tasks.filter(\.enabled).compactMap { nextRun(for: $0) }
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshTaskIdentifier)
        guard let earliest = nextDates.min() else { return }
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshTaskIdentifier)
        request.earliestBeginDate = earliest
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: Catch-up

    /// 返回在补签窗口内已到执行时间、但尚未自动执行过的任务。
    func dueTasks(now: Date = Date()) -> [BjmfTask] {
        let lastRuns = defaults.dictionary(forKey: Self.lastAutoRunKey) as? [String: Double] ?? [:]
        return store.loadTasks().filter { task in
            guard task.enabled, let due = previousRun(for: task, before: now) else { return false }
            guard now.timeIntervalSince(due) <= Self.catchUpWindow else { return false }
            let lastRun = lastRuns[String(task.id)].map { Date(timeIntervalSince1970: $0) }
            return lastRun.map { $0 < due } ?? true
        }
    }

    func markAutoRun(_ taskId: Int64, at date: Date = Date()) {
        var lastRuns = defaults.dictionary(forKey: Self.lastAutoRunKey) as? [String: Double] ?? [:]
        lastRuns[String(taskId)] = date.timeIntervalSince1970
        defaults.set(lastRuns, forKey: Self.lastAutoRunKey)
    }

    // MARK: Time calculation

    func nextRun(for task: BjmfTask, now: Date = Date()) -> Date? {
        let candidates = runDates(for: task, around: now)
        return candidates.first { $0 > now }
    }

    func previousRun(for task: BjmfTask, before now: Date) -> Date? {
        runDates(for: task, around: now).last { $0 <= now }
    }

    /// 与 Android 端一致：按北京时间计算，从 max(今天, 开始日期) 起向后最多 370 天，超过结束日期即停止。
    private func runDates(for task: BjmfTask, around now: Date) -> [Date] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = Self.zone

        let times = task.times.compactMap(Self.parseTime).sorted { ($0.0, $0.1, $0.2) < ($1.0, $1.1, $1.2) }
        guard !times.isEmpty else { return [] }

        let today = calendar.startOfDay(for: now)
        let startDate = task.dateStart.flatMap { Self.parseDate($0, calendar: calendar) }
        let endDate = task.dateEnd.flatMap { Self.parseDate($0, calendar: calendar) }
        let yesterday = calendar.date(byAdding: .day, value: -1, to: today)!
        let firstDate = max(yesterday, startDate ?? yesterday)

        var result: [Date] = []
        for offset in 0...371 {
            guard let date = calendar.date(byAdding: .day, value: offset, to: firstDate) else { continue }
            if let endDate, date > endDate { break }
            for (hour, minute, second) in times {
                if let candidate = calendar.date(bySettingHour: hour, minute: minute, second: second, of: date) {
                    result.append(candidate)
                }
            }
            if result.contains(where: { $0 > now }) && offset > 1 { break }
        }
        return result
    }

    static func notificationId(_ taskId: Int64) -> String {
        "bjmf-task-\(taskId)"
    }

    private static func parseTime(_ text: String) -> (Int, Int, Int)? {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard (2...3).contains(parts.count) else { return nil }
        let second = parts.count == 3 ? parts[2] : 0
        guard (0...23).contains(parts[0]), (0...59).contains(parts[1]), (0...59).contains(second) else { return nil }
        return (parts[0], parts[1], second)
    }

    private static func parseDate(_ text: String, calendar: Calendar) -> Date? {
        let parts = text.trimmingCharacters(in: .whitespaces).split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }
}

/// 执行签到并写入日志、重新安排下一次提醒；前台、通知和后台刷新共用。
final class SignRunner {
    let store: BjmfStore
    let scheduler: BjmfScheduler
    let service: BjmfNativeService

    init(store: BjmfStore, scheduler: BjmfScheduler, service: BjmfNativeService) {
        self.store = store
        self.scheduler = scheduler
        self.service = service
    }

    @discardableResult
    func run(_ tasks: [BjmfTask], summary: Bool, markAuto: Bool = false) async -> [SignResult] {
        var results: [SignResult] = []
        for task in tasks {
            let result = await service.runSign(task)
            store.appendLog(TaskLog(
                id: Clock.nowMillis() + task.id,
                taskId: result.taskId,
                taskName: result.taskName,
                runAt: Clock.nowMillis(),
                status: result.status,
                message: result.message
            ))
            if markAuto { scheduler.markAutoRun(task.id) }
            results.append(result)
        }
        if summary, let wxKey = tasks.first(where: { !$0.wxKey.isEmpty })?.wxKey {
            try? await service.sendSummary(wxKey: wxKey, results: results)
        }
        scheduler.scheduleAll(store.loadTasks())
        return results
    }

    /// 自动补签：执行窗口内到期但未执行的任务。
    @discardableResult
    func runDueTasks() async -> [SignResult] {
        let due = scheduler.dueTasks()
        guard !due.isEmpty else { return [] }
        return await run(due, summary: false, markAuto: true)
    }

    func runTask(id: Int64) async -> SignResult? {
        guard let task = store.findTask(id), task.enabled else { return nil }
        return await run([task], summary: false, markAuto: true).first
    }
}
