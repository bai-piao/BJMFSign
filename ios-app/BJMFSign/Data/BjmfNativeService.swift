import Foundation
import SwiftSoup

/// 签到网络逻辑，逐行移植自 Android 端 `BjmfNativeService.kt`。
final class BjmfNativeService {
    static let qrLoginURL = "https://bjmf.k8n.cn/weixin/qrlogin/student"
    static let bjmfBase = "https://bjmf.k8n.cn"
    static let bjLogin = "https://bj.k8n.cn"

    struct QrSession {
        let id: String
        let imageData: Data
        let client: HTTPClient
    }

    private let sharedClient = HTTPClient()

    // MARK: QR login

    func createQrSession() async throws -> QrSession {
        let client = HTTPClient(useCookieJar: true, followRedirects: true)
        let html = try await client.getString(Self.qrLoginURL, headers: Self.mobileHeaders())
        let qrURL = try extractQrURL(html)
        let bytes = try await client.getData(qrURL, headers: Self.mobileHeaders())
        return QrSession(id: UUID().uuidString, imageData: bytes, client: client)
    }

    func pollQrLogin(_ session: QrSession) async throws -> LoginAccount? {
        let json = try await session.client.getString("\(Self.qrLoginURL)?op=checklogin", headers: Self.mobileHeaders())
        guard let data = json.data(using: .utf8),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw BjmfError.message("登录状态解析失败")
        }
        let status = (object["status"] as? Bool) ?? ((object["status"] as? NSNumber)?.boolValue ?? false)
        guard status else { return nil }
        let redirectURL = (object["url"] as? String) ?? ""
        guard !redirectURL.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        let cookie = try await cookieFromRedirect(redirectURL)
        let info = try await getUserAndClassInfo(cookie: cookie)
        return LoginAccount(cookie: cookie, userInfo: info)
    }

    // MARK: User info

    func getUserAndClassInfo(cookie: String) async throws -> UserInfo {
        let cookieHeader = Self.extractRememberCookie(cookie)
        let headers = Self.browserHeaders(cookie: cookieHeader)
        let myHtml = try await sharedClient.getString("\(Self.bjmfBase)/student/my", headers: headers)
        let classHtml = try await sharedClient.getString("\(Self.bjmfBase)/student", headers: headers)

        var userName = Regexes.firstGroup("uname:'([^']+)'", in: myHtml) ?? ""
        if userName.trimmingCharacters(in: .whitespaces).isEmpty { userName = "未找到" }

        let classInfo = parseClassInfo(classHtml)
        return UserInfo(
            name: userName,
            classId: classInfo.classId,
            className: classInfo.className,
            classCode: classInfo.classCode
        )
    }

    // MARK: Sign

    func runSign(_ task: BjmfTask) async -> SignResult {
        var lines: [String] = []
        func log(_ text: String) { lines.append(text) }
        func result(_ status: String) -> SignResult {
            SignResult(taskId: task.id, taskName: task.name, status: status, message: lines.joined(separator: "\n"))
        }

        do {
            let userInfo = try await getUserAndClassInfo(cookie: task.cookie)
            let userName = userInfo.name != "未找到" ? userInfo.name : task.name
            let classId = (userInfo.classId != "未找到" && !userInfo.classId.isEmpty) ? userInfo.classId : task.classId
            if userInfo.name == "未找到" || classId.isEmpty {
                logHeader(log, task: task, userName: userName, classId: classId)
                log("无法获取用户或班级信息，可能是 Cookie 过期")
                return result("skip")
            }

            logHeader(log, task: task, userName: userName, classId: classId)

            let session = HTTPClient(useCookieJar: false, followRedirects: true)

            do {
                _ = try await session.getString("\(Self.bjmfBase)/student/my", headers: Self.browserHeaders(cookie: task.cookie))
            } catch {
                log("会话预热失败: \(error.localizedDescription)")
            }

            var punchIds: [String] = []
            var punchTypes: [String: String] = [:]
            func addPunch(_ id: String, _ type: String) {
                guard punchTypes[id] == nil else { return }
                punchIds.append(id)
                punchTypes[id] = type
            }

            var lastHtml = ""
            var lastCode = 0
            for module in ["punchs", "daka"] {
                let listURL = "\(Self.bjmfBase)/student/course/\(classId)/\(module)"
                var headers = Self.browserHeaders(cookie: task.cookie)
                headers["Referer"] = "\(Self.bjmfBase)/student/course/\(classId)"
                let response = try await session.get(listURL, headers: headers)
                lastHtml = response.body
                lastCode = response.code

                for match in Regexes.allGroups("punchcard_(\\d+)", in: response.body) {
                    addPunch(match[1], module)
                }
                for match in Regexes.allGroups("/student/(punch\\w+|daka)/course/\\d+/(\\d+)", in: response.body) {
                    addPunch(match[2], match[1])
                }
                if let match = Regexes.allGroups("/student/(punch\\w+|daka)/course/\\d+/(\\d+)", in: response.finalURL).first {
                    addPunch(match[2], match[1])
                }
            }

            if punchIds.isEmpty {
                let doc = try? SwiftSoup.parse(lastHtml)
                let successInfo = (try? doc?.select(".punch-success-info").first()?.text()) ?? ""
                let statusInfo = (try? doc?.select(".punch-status").first()?.text()) ?? ""
                let status = (successInfo.contains("已签到") || statusInfo.contains("已签到")) ? "already_signed" : "no_sign_in"
                log(status == "already_signed" ? "检测到已完成签到" : "未找到在进行的签到/不在签到时间内")
                log("Debug: Status Code: \(lastCode)")
                await notify(task, status: status)
                return result(status)
            }

            for punchId in punchIds {
                let punchType = punchTypes[punchId] ?? "punchs"
                log("签到项: \(punchId) (模块: \(punchType))")
                let postURL = "\(Self.bjmfBase)/student/\(punchType)/course/\(classId)/\(punchId)"
                let response = try await session.postForm(
                    postURL,
                    headers: Self.browserHeaders(cookie: task.cookie),
                    fields: [
                        ("id", punchId),
                        ("lat", task.lat),
                        ("lng", task.lng),
                        ("acc", task.acc),
                        ("res", ""),
                        ("gps_addr", ""),
                    ]
                )
                if response.code != 200 {
                    log("请求失败，状态码: \(response.code)")
                    await notify(task, status: "error")
                    return result("error")
                }

                let title = (try? SwiftSoup.parse(response.body).select("#title").first()?.text()) ?? ""
                let status: String
                if title.contains("已签到") {
                    status = "already_signed"
                } else if title.contains("未开始") {
                    status = "not_started"
                } else {
                    status = "success"
                }
                switch status {
                case "already_signed": log("已签到！无需再次签到")
                case "not_started": log("未开始签到，请稍后")
                default: log("本次签到成功")
                }
                await notify(task, status: status)
                return result(status)
            }

            await notify(task, status: "no_sign_in")
            return result("no_sign_in")
        } catch {
            log("发生错误: \(error.localizedDescription)")
            await notify(task, status: "error")
            return result("error")
        }
    }

    func sendSummary(wxKey: String, results: [SignResult]) async throws {
        guard !wxKey.isEmpty, !results.isEmpty else { return }
        let failed = results.filter { !SignStatus.isSuccess($0.status) }
        let title = failed.isEmpty ? "全部签到成功" : "部分失败"
        let detail = results
            .map { "\($0.taskName)\(SignStatus.isSuccess($0.status) ? "成功" : "失败")" }
            .joined(separator: "\n")
        try await postNotification(url: "https://sctapi.ftqq.com/\(wxKey).send", fields: [("text", title), ("desp", detail)])
    }

    // MARK: Notifications

    private func notify(_ task: BjmfTask, status: String) async {
        let success = SignStatus.isSuccess(status)
        let desc = SignStatus.description(status)
        let message = success ? "签到成功！" : "签到失败，原因：\(desc)"
        let now = Self.currentTime()
        if !task.wxKey.isEmpty {
            try? await postNotification(
                url: "https://sctapi.ftqq.com/\(task.wxKey).send",
                fields: [("text", "\(now)  \(message)"), ("desp", desc)]
            )
        }
        if !task.qqKey.isEmpty {
            try? await postNotification(
                url: "https://qmsg.zendee.cn/send/\(task.qqKey)",
                fields: [("msg", "\(now)  \(message)")]
            )
        }
    }

    private func postNotification(url: String, fields: [(String, String)]) async throws {
        let response = try await sharedClient.postForm(url, fields: fields)
        guard (200...299).contains(response.code) else {
            throw BjmfError.message("通知发送失败：HTTP \(response.code)")
        }
    }

    private func logHeader(_ log: (String) -> Void, task: BjmfTask, userName: String, classId: String) {
        log("==================\(Self.currentTime())===================")
        log("=========== 用户和班级信息 ===============")
        log("任务 ID: \(task.id)")
        log("用户姓名: \(userName)")
        log("班级标识: \(classId)")
        log("=========== 位置信息 ===============")
        log("纬度(lat): \(task.lat)")
        log("经度(lng): \(task.lng)")
        log("精度(acc): \(task.acc)")
        log("=========== 签到结果 ===============")
    }

    // MARK: Parsing

    private struct ClassInfo {
        let classId: String
        let className: String
        let classCode: String
    }

    private func parseClassInfo(_ html: String) -> ClassInfo {
        let gconfig = Regexes.firstGroup("var gconfig=\\{([^}]+)\\}", in: html) ?? ""
        let cname = Regexes.firstGroup("cname:'([^']+)'", in: gconfig)
        let classIdFromConfig = Regexes.firstGroup("id:'([^']+)'", in: gconfig)

        var classId = classIdFromConfig ?? ""
        var className = cname ?? ""
        var classCode = "未找到"

        if let doc = try? SwiftSoup.parse(html, Self.bjmfBase) {
            if let links = try? doc.select("a[href]") {
                for link in links {
                    let href = (try? link.attr("href")) ?? ""
                    if let id = Regexes.firstGroup("/student/course/(\\d+)", in: href), classId.isEmpty {
                        classId = id
                        className = ((try? link.text()) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                }
            }
            if let cards = try? doc.select("div.card") {
                for card in cards {
                    let text = (try? card.text()) ?? ""
                    if text.contains("班级码") {
                        if let code = Regexes.firstGroup("班级码\\s*([A-Z0-9]{4,10})", in: text) {
                            classCode = code
                        }
                        break
                    }
                }
            }
        }

        if classCode == "未找到" {
            let candidates = Regexes.allGroups("\\b[A-Z0-9]{4,8}\\b", in: html)
                .map { $0[0] }
                .filter { code in
                    let allDigits = code.allSatisfy(\.isNumber)
                    let tooBig = code.count > 6 || (Int(code).map { $0 > 2030 } ?? false)
                    return !(allDigits && tooBig)
                }
            classCode = candidates.first { (5...6).contains($0.count) && !$0.allSatisfy(\.isNumber) }
                ?? candidates.first
                ?? "未找到"
        }

        return ClassInfo(
            classId: classId.isEmpty ? "未找到" : classId,
            className: className.isEmpty ? "未找到" : className,
            classCode: classCode
        )
    }

    private func extractQrURL(_ html: String) throws -> String {
        let doc = try SwiftSoup.parse(html, Self.qrLoginURL)
        if let img = try doc.select("div#qrcode img[src]").first() {
            let src = try img.absUrl("src")
            if !src.isEmpty { return src }
        }
        for img in try doc.select("img[src]") where ((try? img.attr("src")) ?? "").contains("ticket=") {
            let src = try img.absUrl("src")
            if !src.isEmpty { return src }
        }
        throw BjmfError.message("未找到二维码图片")
    }

    private func cookieFromRedirect(_ redirectURL: String) async throws -> String {
        guard let range = redirectURL.range(of: "?") else { throw BjmfError.message("登录跳转地址无效") }
        let query = String(redirectURL[range.upperBound...])
        guard !query.isEmpty else { throw BjmfError.message("登录跳转地址无效") }

        let url = "\(Self.bjLogin)/student/uidlogin?\(query)"
        let client = HTTPClient(useCookieJar: true, followRedirects: false)
        let response = try await client.get(url, headers: Self.uidLoginHeaders())
        if !(200...299).contains(response.code) && !(300...399).contains(response.code) {
            throw BjmfError.message("获取 Cookie 失败：HTTP \(response.code)")
        }

        var fields: [String: String] = [:]
        for (key, value) in response.headers {
            if let key = key as? String, let value = value as? String { fields[key] = value }
        }
        let cookies = URL(string: url).map { HTTPCookie.cookies(withResponseHeaderFields: fields, for: $0) } ?? []
        guard let cookie = cookies.first(where: { $0.name != "s" }) else {
            throw BjmfError.message("未获取到有效 Cookie")
        }
        return "\(cookie.name)=\(cookie.value)"
    }

    static func extractRememberCookie(_ cookie: String) -> String {
        Regexes.firstMatch("remember_student_[^=;]+=[^;]+", in: cookie) ?? cookie
    }

    // MARK: Headers

    static func mobileHeaders() -> [String: String] {
        [
            "User-Agent": "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36 MicroMessenger/7.0.10.1580(0x27000A50) Process/tools NetType/WIFI Language/zh_CN ABI/arm64",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Referer": "https://wx.qq.com/",
            "X-Requested-With": "XMLHttpRequest",
        ]
    }

    static func browserHeaders(cookie: String) -> [String: String] {
        [
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; X64; Linux; Android 9;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Firefox/92.0 WeChat/x86_64 Weixin NetType/4G Language/zh_CN ABI/x86_64",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/wxpic,image/tpg,image/webp,image/apng,*/*;q=0.8",
            "Cookie": cookie,
        ]
    }

    static func uidLoginHeaders() -> [String: String] {
        [
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Referer": "https://login.b8n.cn/",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        ]
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    static func currentTime() -> String {
        timeFormatter.string(from: Date())
    }
}

enum Regexes {
    /// 返回所有匹配，每个匹配为 [整体, 分组1, 分组2, ...]，未参与匹配的分组为空字符串。
    static func allGroups(_ pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let ns = text as NSString
        return regex.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { match in
            (0..<match.numberOfRanges).map { index in
                let range = match.range(at: index)
                return range.location == NSNotFound ? "" : ns.substring(with: range)
            }
        }
    }

    static func firstGroup(_ pattern: String, in text: String) -> String? {
        allGroups(pattern, in: text).first.flatMap { $0.count > 1 ? $0[1] : nil }
    }

    static func firstMatch(_ pattern: String, in text: String) -> String? {
        allGroups(pattern, in: text).first?.first
    }
}
