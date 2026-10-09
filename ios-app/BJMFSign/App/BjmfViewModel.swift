import Foundation
import Observation
import SwiftUI

enum BjmfScreen: String, CaseIterable, Identifiable, Hashable {
    case login, task, logs, settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .login: return "登录"
        case .task: return "任务"
        case .logs: return "日志"
        case .settings: return "管理"
        }
    }

    var largeTitle: String {
        switch self {
        case .login: return "扫码登录"
        case .task: return "签到任务"
        case .logs: return "执行日志"
        case .settings: return "本机管理"
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
    var qrStatusText = "尚未创建扫码会话"
    var account: LoginAccount?
    var tasks: [BjmfTask] = []
    var logs: [TaskLog] = []
    var favoriteLocations: [FavoriteLocation] = []
    var taskForm = TaskForm()
    var selectedTaskId: Int64?
    var accentColor: Int64 = BjmfStore.defaultAccentColor
    var accentColorText = ColorHex.text(BjmfStore.defaultAccentColor)
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
        scheduler.scheduleAll(tasks)
    }

    var accent: Color { ColorHex.color(accentColor) }
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
        qrStatusText = "正在获取微信扫码二维码..."
        qrImageData = nil
        message = nil
        Task {
            do {
                let session = try await service.createQrSession()
                qrSession = session
                isBusy = false
                isPolling = true
                qrImageData = session.imageData
                qrStatusText = "请使用微信扫码并确认登录"
                account = nil
                beginPolling(session)
            } catch {
                isBusy = false
                isPolling = false
                qrStatusText = "二维码创建失败"
                message = error.cleanMessage("二维码创建失败")
            }
        }
    }

    func clearQrSession() {
        pollingTask?.cancel()
        qrSession = nil
        isPolling = false
        qrImageData = nil
        qrStatusText = "已清除扫码会话"
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
                        qrStatusText = "登录成功"
                        self.account = account
                        taskForm = accountFormOrEmpty(account)
                        selectedTaskId = nil
                        screen = .task
                        message = "\(account.userInfo.name) 已登录，可保存为本机任务"
                        return
                    } else {
                        qrStatusText = "等待扫码确认..."
                        isPolling = true
                    }
                } catch {
                    if Task.isCancelled { return }
                    isPolling = false
                    qrStatusText = "登录轮询失败"
                    message = error.cleanMessage("登录轮询失败")
                    return
                }
            }
            isPolling = false
            qrStatusText = "二维码已过期"
            message = "请重新获取二维码"
        }
    }

    // MARK: Tasks

    func selectTask(_ task: BjmfTask) {
        selectedTaskId = task.id
        taskForm = Self.form(from: task)
        screen = .task
        message = "已载入 \(task.name) 的任务"
    }

    func newTaskFromAccount() {
        selectedTaskId = nil
        taskForm = accountFormOrEmpty(account)
        screen = .task
        message = account == nil ? "请先扫码登录，或手动填入 Cookie" : "已用当前登录账号填充任务"
    }

    func saveTask() {
        let form = taskForm
        let coord = CoordinateUtils.parseText(form.coord)
        let times = Self.normalizeTimes(form.timesText)

        if form.name.isBlankText || form.classId.isBlankText || form.cookie.isBlankText {
            message = "姓名、班级 ID 和 Cookie 不能为空"
            return
        }
        guard let coord, !times.isEmpty else {
            message = "坐标和执行时间格式不正确"
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
        message = "任务已保存，并已安排下一次签到提醒"
    }

    func deleteSelectedTask() {
        guard let taskId = selectedTaskId else { return }
        scheduler.cancel(taskId)
        store.deleteTask(taskId)
        reloadLocalData(selectedTaskId: nil)
        taskForm = accountFormOrEmpty(account)
        message = "任务已删除"
    }

    func toggleTask(_ task: BjmfTask) {
        var next = task
        next.enabled.toggle()
        let saved = store.saveTask(next)
        if saved.enabled { scheduler.schedule(saved) } else { scheduler.cancel(saved.id) }
        reloadLocalData(selectedTaskId: saved.id)
        message = saved.enabled ? "任务已启用" : "任务已停用"
    }

    func runSelectedNow() {
        guard let task = selectedTaskId.flatMap(store.findTask) else {
            message = "请先选择一个任务"
            return
        }
        runTasksNow([task], summary: false)
    }

    func runAllEnabledNow() {
        let enabled = store.loadTasks().filter(\.enabled)
        guard !enabled.isEmpty else {
            message = "没有已启用的任务"
            return
        }
        runTasksNow(enabled, summary: true)
    }

    func rescheduleAll() {
        scheduler.scheduleAll(store.loadTasks())
        message = "已重新安排所有已启用任务"
    }

    func refreshLocalData() {
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = "已刷新本地数据"
    }

    func clearLogs() {
        store.clearLogs()
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = "日志已清除"
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
        message = "正在补签 \(due.count) 个到期任务..."
        Task {
            let results = await runner.runDueTasks()
            isBusy = false
            reloadLocalData(selectedTaskId: selectedTaskId)
            if !results.isEmpty {
                message = "自动签到完成：\(results.map(\.status).joined(separator: ", "))"
            }
        }
    }

    func runFromNotification(taskId: Int64) async {
        isBusy = true
        message = "正在执行签到..."
        let result = await runner.runTask(id: taskId)
        isBusy = false
        reloadLocalData(selectedTaskId: selectedTaskId)
        if let result {
            screen = .logs
            message = "签到执行完成：\(result.status)"
        }
    }

    private func runTasksNow(_ tasks: [BjmfTask], summary: Bool) {
        isBusy = true
        message = "正在执行签到..."
        Task {
            let results = await runner.run(tasks, summary: summary)
            reloadLocalData(selectedTaskId: selectedTaskId)
            isBusy = false
            screen = .logs
            message = "签到执行完成：\(results.map(\.status).joined(separator: ", "))"
        }
    }

    // MARK: Locations

    func setCoordinate(lat: Double, lng: Double) {
        taskForm.coord = "\(CoordinateUtils.coordText(lng)) \(CoordinateUtils.coordText(lat))"
    }

    func favoriteSelectedLocation() {
        guard let coord = CoordinateUtils.parseText(taskForm.coord) else {
            message = "请先在地图上选择位置"
            return
        }
        let saved = store.saveFavoriteLocation(FavoriteLocation(id: 0, name: "地图选点 \(Self.shortTime())", lat: coord.lat, lng: coord.lng))
        reloadLocalData(selectedTaskId: selectedTaskId)
        taskForm.coord = "\(saved.lng) \(saved.lat)"
        message = "已收藏选取位置"
    }

    func useFavoriteLocation(_ location: FavoriteLocation) {
        taskForm.coord = "\(location.lng) \(location.lat)"
        message = "已选用 \(location.name)"
    }

    func deleteFavoriteLocation(_ location: FavoriteLocation) {
        store.deleteFavoriteLocation(location.id)
        reloadLocalData(selectedTaskId: selectedTaskId)
        message = "已删除位置收藏"
    }

    func useCurrentLocationAndFavorite() {
        message = "正在获取当前位置..."
        Task {
            do {
                let location = try await locationProvider.currentLocation()
                let mapped = CoordinateUtils.wgs84ToGcj02(lat: location.coordinate.latitude, lng: location.coordinate.longitude)
                let saved = store.saveFavoriteLocation(FavoriteLocation(
                    id: 0,
                    name: "当前位置 \(Self.shortTime())",
                    lat: CoordinateUtils.coordText(mapped.lat),
                    lng: CoordinateUtils.coordText(mapped.lng)
                ))
                reloadLocalData(selectedTaskId: selectedTaskId)
                taskForm.coord = "\(saved.lng) \(saved.lat)"
                message = "已获取并收藏当前位置"
            } catch {
                message = error.cleanMessage("无法获取当前位置")
            }
        }
    }

    // MARK: Accent color

    func selectAccentColor(_ color: Int64) {
        store.saveAccentColor(color)
        accentColor = color
        accentColorText = ColorHex.text(color)
        message = "主题色已更新"
    }

    func applyAccentColor() {
        guard let parsed = ColorHex.parse(accentColorText) else {
            message = "颜色格式应为 #RRGGBB 或 #AARRGGBB"
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
