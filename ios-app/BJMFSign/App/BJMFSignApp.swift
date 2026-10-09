import BackgroundTasks
import SwiftUI
import UserNotifications

/// 全局依赖，前台界面、通知回调和后台刷新共用同一套存储与调度。
enum AppEnvironment {
    static let store = BjmfStore()
    static let service = BjmfNativeService()
    static let scheduler = BjmfScheduler(store: store)
    static let runner = SignRunner(store: store, scheduler: scheduler, service: service)
    @MainActor static let viewModel = BjmfViewModel(store: store, scheduler: scheduler, runner: runner, service: service)
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        BjmfScheduler.registerNotificationCategory()
        BGTaskScheduler.shared.register(forTaskWithIdentifier: BjmfScheduler.refreshTaskIdentifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            Self.handleRefresh(refresh)
        }
        return true
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        let work = Task {
            await AppEnvironment.runner.runDueTasks()
            AppEnvironment.scheduler.scheduleBackgroundRefresh(AppEnvironment.store.loadTasks())
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            work.cancel()
            AppEnvironment.scheduler.scheduleBackgroundRefresh(AppEnvironment.store.loadTasks())
        }
    }

    // MARK: UNUserNotificationCenterDelegate

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        // App 在前台时到点：直接执行签到，同时展示横幅。
        if let taskId = Self.taskId(from: notification.request.content.userInfo) {
            Task { @MainActor in
                await AppEnvironment.viewModel.runFromNotification(taskId: taskId)
            }
        }
        return [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard let taskId = Self.taskId(from: response.notification.request.content.userInfo) else { return }
        switch response.actionIdentifier {
        case BjmfScheduler.signActionIdentifier:
            // 通知上的「立即签到」：在后台执行，不打开 App。
            _ = await AppEnvironment.runner.runTask(id: taskId)
        case UNNotificationDefaultActionIdentifier:
            await AppEnvironment.viewModel.runFromNotification(taskId: taskId)
        default:
            break
        }
    }

    private static func taskId(from userInfo: [AnyHashable: Any]) -> Int64? {
        (userInfo[BjmfScheduler.taskIdKey] as? NSNumber)?.int64Value
    }
}

@main
struct BJMFSignApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: AppEnvironment.viewModel)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                AppEnvironment.viewModel.runDueTasksIfNeeded()
            }
        }
    }
}
