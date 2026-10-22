import Foundation
import Observation
import SwiftUI

enum BjmfScreen: String, CaseIterable, Identifiable, Hashable {
    case login, task, logs, settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .login: return tr("登录")
        case .task: return tr("任务")
        case .logs: return tr("日志")
        case .settings: return tr("管理")
        }
    }

    var largeTitle: String {
        switch self {
        case .login: return tr("扫码登录")
        case .task: return tr("签到任务")
        case .logs: return tr("执行日志")
        case .settings: return tr("本机管理")
        }
    }

    var systemImage: String {
        switch self {
        case .login: return "qrcode.viewfinder"
        case .task: return "checklist"
        case .logs: return "doc.text.magnifyingglass"
        case .settings: return "gearshape"
        }
    }
}

@MainActor
@Observable
final class BjmfViewModel {
    var screen: BjmfScreen = .login
    var isBusy = false
    var isPolling = false
    var qrImageData: Data?
    var qrStatusText = tr("尚未创建扫码会话")
    var account: LoginAccount?
    var tasks: [BjmfTask] = []
    var logs: [TaskLog] = []
    var favoriteLocations: [FavoriteLocation] = []
    var taskForm = TaskForm()
    var selectedTaskId: Int64?
    var accentColor: Int64 = BjmfStore.defaultAccentColor
    var accentColorText = ColorHex.text(BjmfStore.defaultAccentColor)
    var themeMode = "system"
    var language = I18n.system
    var showOnboarding = false
    var message: String?

    let store: BjmfStore
    let scheduler: BjmfScheduler
    let runner: SignRunner
    private let service: BjmfNativeService
    private let locationProvider = LocationProvider()
    @ObservationIgnored private var qrSession: BjmfNativeService.QrSession?
    @ObservationIgnored private var pollingTask: Task<Void, Never>?

    init(store: BjmfStore, scheduler: BjmfScheduler, runner: SignRunner, service: BjmfNativeService) {
        self.store = store
        self.scheduler = scheduler
        self.runner = runner
        self.service = service
        tasks = store.loadTasks()
        logs = store.loadLogs(limit: Int.max)
        favoriteLocations = store.loadFavoriteLocations()
        accentColor = store.loadAccentColor()
        accentColorText = ColorHex.text(accentColor)
        themeMode = store.loadThemeMode()
        language = I18n.language
        showOnboarding = !store.isOnboardingDone
        SharedStore.accentColor = accentColor
        scheduler.scheduleAll(tasks)
    }

    var accent: Color { ColorHex.color(accentColor) }

    /// nil 表示跟随系统。
    var preferredColorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    func setThemeMode(_ mode: String) {
        store.saveThemeMode(mode)
        themeMode = mode
        switch mode {
        case "light": message = tr("已切换为浅色模式")
        case "dark": message = tr("已切换为深色模式")
        default: message = tr("已设为跟随系统")
        }
    }

    func startNewTask() {
        selectedTaskId = nil
        taskForm = accountFormOrEmpty(account)
        screen = .task
        message = account == nil ? tr("新建任务：请填写信息或先扫码登录") : tr("新建任务：已填入当前扫码账号")
    }

    func runTaskNow(_ task: BjmfTask) {
        guard let latest = store.findTask(task.id) else { return }
        runTasksNow([latest], summary: false)
    }
    var enabledCount: Int { tasks.filter(\.enabled).count }

    func selectScreen(_ screen: BjmfScreen) {
        self.screen = screen
        message = nil
    }

    // MARK: QR login

    func createQrSession() {
        pollingTask?.cancel()
        isBusy = true
        isPolling = false
        qrStatusText = tr("正在获取微信扫码二维码...")
        qrImageData = nil
        message = nil
        Task {
            do {
                let session = try await service.createQrSession()
                qrSession = session
                isBusy = false
                isPolling = true
                qrImageData = session.imageData
                qrStatusText = tr("请使用微信扫码并确认登录")
                account = nil
                beginPolling(session)
            } catch {
                isBusy = false
                isPolling = false
                qrStatusText = tr("二维码创建失败")
                message = error.cleanMessage(tr("二维码创建失败"))
            }
        }
    }

    func clearQrSession() {
        pollingTask?.cancel()
        qrSession = nil
        isPolling = false
        qrImageData = nil
        qrStatusText = tr("已清除扫码会话")
        message = nil
    }

    private func beginPolling(_ session: BjmfNativeService.QrSession) {
        pollingTask?.cancel()
        pollingTask = Task {
            for _ in 0..<80 {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                if Task.isCancelled { return }
                do {
                    if let account = try await service.pollQrLogin(session) {
                        if Task.isCancelled { return }
                        isPolling = false
                        qrStatusText = tr("登录成功")
                        self.account = account
                        taskForm = accountFormOrEmpty(account)
                        selectedTaskId = nil
                        screen = .task
                        message = tr("{0} 已登录，可保存为本机任务", account.userInfo.name)
                        return
                    } else {
                        qrStatusText = tr("等待扫码确认...")
                        isPolling = true
                    }
                } catch {
                    if Task.isCancelled { return }
                    isPolling = false
                    qrStatusText = tr("登录轮询失败")
                    message = error.cleanMessage(tr("登录轮询失败"))
                    return
                }
            }
            isPolling = false
            qrStatusText = tr("二维码已过期")
            message = tr("请重新获取二维码")
        }
    }

    // MARK: Tasks

    func selectTask(_ task: BjmfTask) {
        selectedTaskId = task.id
        taskForm = Self.form(from: task)
        screen = .task
        message = tr("已载入 {0} 的任务", task.name)
    }

    func newTaskFromAccount() {
        selectedTaskId = nil
        taskForm = accountFormOrEmpty(account)
        screen = .task
        message = account == nil ? tr("请先扫码登录，或手动填入 Cookie") : tr("已用当前登录账号填充任务")
    }

    func saveTask() {
        let form = taskForm
        let coord = CoordinateUtils.parseText(form.coord)
        let times = Self.normalizeTimes(form.timesText)

        if form.name.isBlankText || form.classId.isBlankText || form.cookie.isBlankText {
            message = tr("姓名、班级 ID 和 Cookie 不能为空")
            return
        }
        guard let coord, !times.isEmpty else {
            message = tr("坐标和执行时间格式不正确")
            return
        }

        let old = form.selectedTaskId.flatMap(store.findTask)
        let task = BjmfTask(
            id: old?.id ?? 0,
            name: form.name.trimmed,
            classId: form.classId.trimmed,
            cookie: form.cookie.trimmed,
            lat: coord.lat,
            lng: coord.lng,
            acc: form.acc.trimmed.isEmpty ? "30" : form.acc.trimmed,
            wxKey: form.wxKey.trimmed,
            qqKey: form.qqKey.trimmed,
            times: times,
            dateStart: form.dateStart.trimmed.nilIfEmpty,
            dateEnd: form.dateEnd.trimmed.nilIfEmpty,
            enabled: old?.enabled ?? true,
            createdAt: old?.createdAt ?? Clock.nowMillis()
        )

        let saved = store.saveTask(task)
        Task { _ = await BjmfScheduler.requestNotificationPermission() }
        scheduler.schedule(saved)
        reloadLocalData(selectedTaskId: saved.id)
        screen = .task
        taskForm = Self.form(from: saved)
        message = tr("任务已保存，并已安排下一次签到提醒")
    }

    func deleteSelectedTask() {
        guard let taskId = selectedTaskId else { return }
        scheduler.cancel(taskId)
        store.deleteTask(taskId)
        reloadLocalData(selectedTaskId: nil)
        taskForm = accountFormOrEmpty(account)
        message = tr("任务已删除")
    }

    func toggleTask(_ task: BjmfTask) {
        var next = task
        next.enabled.toggle()
        let saved = store.saveTask(next)
        if saved.enabled { scheduler.schedule(saved) } else { scheduler.cancel(saved.id) }
        reloadLocalData(selectedTaskId: saved.id)
        message = saved.enabled ? tr("任务已启用") : tr("任务已停用")
    }

    func runSelectedNow() {
        guard let task = selectedTaskId.flatMap(store.findTask) else {
            message = tr("请先选择一个任务")
            return
        }
        runTasksNow([task], summary: false)
    }

    func runAllEnabledNow() {
        let enabled = store.loadTasks().filter(\.enabled)
        guard !enabled.isEmpty else {
            message = tr("没有已启用的任务")
            return
        }
        runTasksNow(enabled, summary: true)
    }

    func rescheduleAll() {
        scheduler.scheduleAll(store.loadTasks())
        message = tr("已重新安排所有已启用任务")
    }

    func refreshLocalData() {
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = tr("已刷新本地数据")
    }

    func clearLogs() {
        store.clearLogs()
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = tr("日志已清除")
    }

    /// App 回到前台或收到通知后：补签错过的任务并刷新界面。
    func runDueTasksIfNeeded() {
        guard !isBusy else { return }
        let due = scheduler.dueTasks()
        guard !due.isEmpty else {
            reloadLocalData(selectedTaskId: selectedTaskId)
            return
        }
        isBusy = true
        message = tr("正在补签 {0} 个到期任务...", due.count)
        Task {
            let results = await runner.runDueTasks()
            isBusy = false
            reloadLocalData(selectedTaskId: selectedTaskId)
            if !results.isEmpty {
                message = Self.summaryText(results)
            }
        }
    }

    func runFromNotification(taskId: Int64) async {
        isBusy = true
        message = tr("正在执行签到...")
        let result = await runner.runTask(id: taskId)
        isBusy = false
        reloadLocalData(selectedTaskId: selectedTaskId)
        if let result {
            screen = .logs
            message = Self.summaryText([result])
        }
    }

    private func runTasksNow(_ tasks: [BjmfTask], summary: Bool) {
        isBusy = true
        message = tr("正在执行签到...")
        Task {
            let results = await runner.run(tasks, summary: summary)
            reloadLocalData(selectedTaskId: selectedTaskId)
            isBusy = false
            screen = .logs
            message = Self.summaryText(results)
        }
    }

    // MARK: Language & onboarding

    func setLanguage(_ value: String) {
        I18n.language = value
        language = value
        qrStatusText = qrImageData == nil ? tr("尚未创建扫码会话") : qrStatusText
        scheduler.scheduleAll(store.loadTasks())
        message = tr("界面语言已切换")
    }

    func completeOnboarding() {
        store.isOnboardingDone = true
        showOnboarding = false
        if account != nil { screen = .task }
    }

    func reopenOnboarding() {
        showOnboarding = true
    }

    // MARK: Backup

    func exportBackupData(password: String) async throws -> Data {
        let json = try store.buildBackupJson()
        return try await Task.detached(priority: .userInitiated) {
            try BackupCrypto.encrypt(json, password: password)
        }.value
    }

    func importBackup(from url: URL, password: String) {
        isBusy = true
        message = tr("正在解密导入...")
        Task {
            do {
                let accessing = url.startAccessingSecurityScopedResource()
                defer { if accessing { url.stopAccessingSecurityScopedResource() } }
                let file = try Data(contentsOf: url)
                let plain = try await Task.detached(priority: .userInitiated) {
                    try BackupCrypto.decrypt(file, password: password)
                }.value
                let summary = try store.restoreBackupJson(plain)
                accentColor = store.loadAccentColor()
                accentColorText = ColorHex.text(accentColor)
                themeMode = store.loadThemeMode()
                language = I18n.language
                scheduler.scheduleAll(store.loadTasks())
                reloadLocalData(selectedTaskId: nil)
                taskForm = accountFormOrEmpty(account)
                isBusy = false
                message = tr("已导入 {0} 个任务、{1} 个位置收藏", summary.tasks, summary.locations)
            } catch let failure as BackupCrypto.Failure {
                isBusy = false
                message = failure.errorDescription
            } catch {
                isBusy = false
                message = tr("导入失败：{0}", error.localizedDescription)
            }
        }
    }

    func backupExported(_ result: Result<URL, Error>) {
        switch result {
        case .success: message = tr("备份已导出，请妥善保管文件和密码")
        case .failure(let error): message = tr("导出失败：{0}", error.localizedDescription)
        }
    }

    // MARK: Locations

    func setCoordinate(lat: Double, lng: Double) {
        taskForm.coord = "\(CoordinateUtils.coordText(lng)) \(CoordinateUtils.coordText(lat))"
    }

    func favoriteSelectedLocation() {
        guard let coord = CoordinateUtils.parseText(taskForm.coord) else {
            message = tr("请先在地图上选择位置")
            return
        }
        let saved = store.saveFavoriteLocation(FavoriteLocation(id: 0, name: tr("地图选点 {0}", Self.shortTime()), lat: coord.lat, lng: coord.lng))
        reloadLocalData(selectedTaskId: selectedTaskId)
        taskForm.coord = "\(saved.lng) \(saved.lat)"
        message = tr("已收藏选取位置")
    }

    func useFavoriteLocation(_ location: FavoriteLocation) {
        taskForm.coord = "\(location.lng) \(location.lat)"
        message = tr("已选用 {0}", location.name)
    }

    func deleteFavoriteLocation(_ location: FavoriteLocation) {
        store.deleteFavoriteLocation(location.id)
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = tr("已删除位置收藏")
    }

    func useCurrentLocationAndFavorite() {
        message = tr("正在获取当前位置...")
        Task {
            do {
                let location = try await locationProvider.currentLocation()
                let mapped = CoordinateUtils.wgs84ToGcj02(lat: location.coordinate.latitude, lng: location.coordinate.longitude)
                let saved = store.saveFavoriteLocation(FavoriteLocation(
                    id: 0,
                    name: tr("当前位置 {0}", Self.shortTime()),
                    lat: CoordinateUtils.coordText(mapped.lat),
                    lng: CoordinateUtils.coordText(mapped.lng)
                ))
                reloadLocalData(selectedTaskId: selectedTaskId)
                taskForm.coord = "\(saved.lng) \(saved.lat)"
                message = tr("已获取并收藏当前位置")
            } catch {
                message = error.cleanMessage(tr("无法获取当前位置"))
            }
        }
    }

    // MARK: Accent color

    func selectAccentColor(_ color: Int64) {
        store.saveAccentColor(color)
        accentColor = color
        accentColorText = ColorHex.text(color)
        message = tr("主题色已更新")
    }

    func applyAccentColor() {
        guard let parsed = ColorHex.parse(accentColorText) else {
            message = tr("颜色格式应为 #RRGGBB 或 #AARRGGBB")
            return
        }
        selectAccentColor(parsed)
    }

    // MARK: Helpers

    func reloadLocalData(selectedTaskId: Int64?) {
        tasks = store.loadTasks()
        logs = store.loadLogs(limit: Int.max)
        favoriteLocations = store.loadFavoriteLocations()
        let selected = selectedTaskId.flatMap { id in tasks.first { $0.id == id } }
        self.selectedTaskId = selected?.id
        if let selected {
            taskForm = Self.form(from: selected)
        } else {
            taskForm.selectedTaskId = nil
        }
    }

    private func accountFormOrEmpty(_ account: LoginAccount?) -> TaskForm {
        guard let account else { return TaskForm() }
        return TaskForm(name: account.userInfo.name, classId: account.userInfo.classId, cookie: account.cookie)
    }

    static func form(from task: BjmfTask) -> TaskForm {
        TaskForm(
            selectedTaskId: task.id,
            name: task.name,
            classId: task.classId,
            cookie: task.cookie,
            coord: [task.lng, task.lat].filter { !$0.isEmpty }.joined(separator: " "),
            acc: task.acc,
            timesText: task.times.joined(separator: ","),
            wxKey: task.wxKey,
            qqKey: task.qqKey,
            dateStart: task.dateStart ?? "",
            dateEnd: task.dateEnd ?? ""
        )
    }

    static func normalizeTimes(_ raw: String) -> [String] {
        var seen = Set<String>()
        var result: [String] = []
        let items = raw.components(separatedBy: CharacterSet(charactersIn: ",，\n ").union(.whitespaces))
        for item in items {
            let parts = item.trimmed.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
            guard (2...3).contains(parts.count),
                  let hour = Int(parts[0]),
                  let minute = Int(parts[1]) else { continue }
            let second = parts.count == 3 ? Int(parts[2]) : 0
            guard let second,
                  (0...23).contains(hour), (0...59).contains(minute), (0...59).contains(second) else { continue }
            let text = String(format: "%02d:%02d:%02d", hour, minute, second)
            if seen.insert(text).inserted { result.append(text) }
        }
        return result
    }

    private static func summaryText(_ results: [SignResult]) -> String {
        let success = results.filter { SignStatus.isSuccess($0.status) }.count
        return tr("签到执行完成：成功 {0}，未完成 {1}", success, results.count - success)
    }

    private static func shortTime() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "MM-dd HH:mm"
        return formatter.string(from: Date())
    }
}

enum ColorHex {
    static func text(_ argb: Int64) -> String {
        String(format: "#%06X", Int(argb & 0xFFFFFF))
    }

    static func parse(_ raw: String) -> Int64? {
        var clean = raw.trimmingCharacters(in: .whitespaces)
        for prefix in ["#", "0x", "0X"] where clean.hasPrefix(prefix) {
            clean.removeFirst(prefix.count)
        }
        let argb: String
        switch clean.count {
        case 6: argb = "FF" + clean
        case 8: argb = clean
        default: return nil
        }
        guard let value = Int64(argb, radix: 16), (0...0xFFFFFFFF).contains(value) else { return nil }
        return value
    }

    static func color(_ argb: Int64) -> Color {
        let a = Double((argb >> 24) & 0xFF) / 255
        let r = Double((argb >> 16) & 0xFF) / 255
        let g = Double((argb >> 8) & 0xFF) / 255
        let b = Double(argb & 0xFF) / 255
        return Color(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var isBlankText: Bool { trimmed.isEmpty }
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

extension Error {
    func cleanMessage(_ fallback: String) -> String {
        let text = localizedDescription
        return text.isEmpty ? fallback : text
    }
}
