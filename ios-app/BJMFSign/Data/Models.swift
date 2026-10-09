import Foundation

struct UserInfo: Codable, Equatable {
    var name: String
    var classId: String
    var className: String
    var classCode: String
}

struct LoginAccount: Equatable {
    var cookie: String
    var userInfo: UserInfo
}

struct BjmfTask: Codable, Identifiable, Equatable {
    var id: Int64
    var name: String
    var classId: String
    var cookie: String
    var lat: String
    var lng: String
    var acc: String = "30"
    var wxKey: String = ""
    var qqKey: String = ""
    var times: [String] = []
    var dateStart: String?
    var dateEnd: String?
    var enabled: Bool = true
    var createdAt: Int64 = Clock.nowMillis()
    var updatedAt: Int64 = Clock.nowMillis()
}

struct TaskLog: Codable, Identifiable, Equatable {
    var id: Int64
    var taskId: Int64
    var taskName: String
    var runAt: Int64
    var status: String
    var message: String
}

struct SignResult: Equatable {
    var taskId: Int64
    var taskName: String
    var status: String
    var message: String
}

struct FavoriteLocation: Codable, Identifiable, Equatable {
    var id: Int64
    var name: String
    var lat: String
    var lng: String
    var createdAt: Int64 = Clock.nowMillis()
}

struct TaskForm: Equatable {
    var selectedTaskId: Int64?
    var name: String = ""
    var classId: String = ""
    var cookie: String = ""
    var coord: String = ""
    var acc: String = "30"
    var timesText: String = "07:30:00,12:00:00,18:00:00"
    var wxKey: String = ""
    var qqKey: String = ""
    var dateStart: String = ""
    var dateEnd: String = ""
}

enum Clock {
    static func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}

enum SignStatus {
    static func isSuccess(_ status: String) -> Bool {
        status == "success" || status == "already_signed"
    }

    static func label(_ status: String) -> String {
        switch status {
        case "success": return "成功"
        case "already_signed": return "已签到"
        case "not_started": return "未开始"
        case "no_sign_in": return "无签到"
        case "skip": return "跳过"
        case "error": return "失败"
        default: return status.isEmpty ? "未知" : status
        }
    }

    static func description(_ status: String) -> String {
        switch status {
        case "success": return "签到成功"
        case "already_signed": return "已签到，无需重复"
        case "not_started": return "未开始签到，请稍后"
        case "no_sign_in": return "当前无可用签到"
        case "skip": return "跳过（信息不完整或 Cookie 失效）"
        case "error": return "执行出错"
        default: return "未知状态: \(status)"
        }
    }
}
