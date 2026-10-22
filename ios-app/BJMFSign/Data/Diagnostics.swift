import Foundation
import UIKit

/// 把失败日志翻译成可读的原因和建议（与 Android 端规则一致）。
enum Diagnostics {
    static func readableReason(status: String, message: String) -> String {
        switch status {
        case "success", "already_signed": return tr("签到成功")
        case "skip": return tr("登录已失效（Cookie 过期），请重新扫码登录并更新任务")
        case "no_sign_in": return tr("当前没有进行中的签到，或不在签到时间内")
        case "not_started": return tr("签到尚未开始，请稍后再试")
        default:
            let lower = message.lowercased()
            let networkWords = [
                "timeout", "timed out", "unable to resolve host", "unknownhost", "failed to connect",
                "network", "connection", "socket", "ssl", "offline", "互联网", "网络",
            ]
            if networkWords.contains(where: { lower.contains($0) }) {
                return tr("网络连接失败，请检查网络后重试")
            }
            if let code = Regexes.allGroups("(?:状态码|HTTP)[:：\\s]*(\\d{3})", in: message).first?[1] {
                return code.hasPrefix("4")
                    ? tr("请求被服务器拒绝（HTTP {0}），可能是登录失效", code)
                    : tr("服务器暂时不可用（HTTP {0}），请稍后重试", code)
            }
            return tr("执行出错，请复制给开发者排查")
        }
    }

    /// 生成给开发者的诊断信息（隐藏姓名与精确坐标）。
    static func report(for log: TaskLog) -> String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = BjmfScheduler.zone
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        let time = formatter.string(from: Date(timeIntervalSince1970: TimeInterval(log.runAt) / 1000))
        let device = UIDevice.current
        return [
            "BJMFSign diagnostics",
            "App: iOS \(version) (\(build))",
            "System: \(device.systemName) \(device.systemVersion) · \(device.model)",
            "Time: \(time)",
            "Status: \(log.status)",
            "Reason: \(readableReason(status: log.status, message: log.message))",
            "--- log (name and precise location hidden) ---",
            mask(log.message),
        ].joined(separator: "\n")
    }

    private static func mask(_ message: String) -> String {
        message.components(separatedBy: .newlines).map { line in
            if line.hasPrefix("用户姓名") { return "用户姓名: ***" }
            if line.hasPrefix("纬度") || line.hasPrefix("经度") {
                let parts = line.split(separator: ":", maxSplits: 1).map(String.init)
                if parts.count == 2, let value = Double(parts[1].trimmed) {
                    return parts[0] + ": " + String(format: "%.2f", value) + "**"
                }
            }
            return line
        }.joined(separator: "\n")
    }
}
