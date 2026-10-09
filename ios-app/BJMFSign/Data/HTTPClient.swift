import Foundation

struct HTTPTextResponse {
    let code: Int
    let body: String
    let finalURL: String
    let headers: [AnyHashable: Any]
}

enum BjmfError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text): return text
        }
    }
}

/// 对 URLSession 的轻量封装，行为对齐 Android 端的 OkHttp 客户端：
/// - `useCookieJar` 为 true 时使用自管的内存 Cookie 罐（扫码会话），包括跳转过程中下发的 Cookie；
///   否则只发送手动设置的 Cookie 头。
/// - `followRedirects` 控制是否跟随跳转；跟随时保留原请求头（与 OkHttp 一致）。
///
/// 注意：不能使用 `HTTPCookieStorage()` 自建存储——该实例在 Apple 平台上不会真正保存 Cookie，
/// 会导致扫码轮询时丢失会话 Cookie、服务端永远返回未登录。
final class HTTPClient {
    private let session: URLSession
    let cookieJar: CookieJar?

    init(useCookieJar: Bool = false, followRedirects: Bool = true, timeout: TimeInterval = 20) {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.httpCookieAcceptPolicy = .never
        let jar = useCookieJar ? CookieJar() : nil
        cookieJar = jar
        // URLSession 会强引用 delegate，因此使用独立的 delegate 对象，并在释放时使会话失效。
        session = URLSession(
            configuration: config,
            delegate: RedirectDelegate(followRedirects: followRedirects, cookieJar: jar),
            delegateQueue: nil
        )
    }

    deinit {
        session.finishTasksAndInvalidate()
    }

    func get(_ url: String, headers: [String: String]) async throws -> HTTPTextResponse {
        try await send(makeRequest(url, method: "GET", headers: headers))
    }

    func getString(_ url: String, headers: [String: String]) async throws -> String {
        let response = try await get(url, headers: headers)
        guard (200...299).contains(response.code) else {
            throw BjmfError.message("请求失败：HTTP \(response.code)")
        }
        return response.body
    }

    func getData(_ url: String, headers: [String: String]) async throws -> Data {
        let request = try makeRequest(url, method: "GET", headers: headers)
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        if let http { cookieJar?.save(from: http) }
        let code = http?.statusCode ?? 0
        guard (200...299).contains(code) else {
            throw BjmfError.message("请求失败：HTTP \(code)")
        }
        guard !data.isEmpty else { throw BjmfError.message("响应为空") }
        return data
    }

    func postForm(_ url: String, headers: [String: String] = [:], fields: [(String, String)]) async throws -> HTTPTextResponse {
        var request = try makeRequest(url, method: "POST", headers: headers)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Self.formEncode(fields).data(using: .utf8)
        return try await send(request)
    }

    // MARK: Helpers

    private func makeRequest(_ url: String, method: String, headers: [String: String]) throws -> URLRequest {
        guard let target = URL(string: url) else { throw BjmfError.message("无效地址：\(url)") }
        var request = URLRequest(url: target)
        request.httpMethod = method
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        cookieJar?.apply(to: &request)
        return request
    }

    private func send(_ request: URLRequest) async throws -> HTTPTextResponse {
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        if let http { cookieJar?.save(from: http) }
        let body = String(data: data, encoding: .utf8)
            ?? String(data: data, encoding: .isoLatin1)
            ?? ""
        return HTTPTextResponse(
            code: http?.statusCode ?? 0,
            body: body,
            finalURL: http?.url?.absoluteString ?? request.url?.absoluteString ?? "",
            headers: http?.allHeaderFields ?? [:]
        )
    }

    static func formEncode(_ fields: [(String, String)]) -> String {
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._*")
        func encode(_ text: String) -> String {
            text.addingPercentEncoding(withAllowedCharacters: allowed) ?? text
        }
        return fields.map { "\(encode($0.0))=\(encode($0.1))" }.joined(separator: "&")
    }
}

/// 简易内存 Cookie 罐，对应 Android 端 `SimpleCookieJar`：同名同域同路径覆盖，过期自动清理。
final class CookieJar {
    private var storage: [HTTPCookie] = []
    private let lock = NSLock()

    func save(from response: HTTPURLResponse) {
        guard let url = response.url else { return }
        var fields: [String: String] = [:]
        for (key, value) in response.allHeaderFields {
            if let key = key as? String, let value = value as? String { fields[key] = value }
        }
        let received = HTTPCookie.cookies(withResponseHeaderFields: fields, for: url)
        guard !received.isEmpty else { return }
        lock.lock(); defer { lock.unlock() }
        storage.removeAll { old in
            received.contains { $0.name == old.name && $0.domain == old.domain && $0.path == old.path }
        }
        storage.append(contentsOf: received)
    }

    func apply(to request: inout URLRequest) {
        guard let url = request.url else { return }
        let matching = cookies(for: url)
        guard !matching.isEmpty else { return }
        let header = matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
        request.setValue(header, forHTTPHeaderField: "Cookie")
    }

    func cookies(for url: URL) -> [HTTPCookie] {
        lock.lock(); defer { lock.unlock() }
        let now = Date()
        storage.removeAll { cookie in cookie.expiresDate.map { $0 < now } ?? false }
        return storage.filter { Self.matches($0, url: url) }
    }

    func all() -> [HTTPCookie] {
        lock.lock(); defer { lock.unlock() }
        return storage
    }

    private static func matches(_ cookie: HTTPCookie, url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        if cookie.isSecure && url.scheme?.lowercased() != "https" { return false }
        var domain = cookie.domain.lowercased()
        let hostOnly = !domain.hasPrefix(".")
        if domain.hasPrefix(".") { domain.removeFirst() }
        let domainMatches = host == domain || (!hostOnly && host.hasSuffix("." + domain))
        guard domainMatches else { return false }
        let path = url.path.isEmpty ? "/" : url.path
        let cookiePath = cookie.path.isEmpty ? "/" : cookie.path
        return path == cookiePath
            || (path.hasPrefix(cookiePath) && (cookiePath.hasSuffix("/") || path.dropFirst(cookiePath.count).hasPrefix("/")))
    }
}

private final class RedirectDelegate: NSObject, URLSessionTaskDelegate {
    private let followRedirects: Bool
    private let cookieJar: CookieJar?

    init(followRedirects: Bool, cookieJar: CookieJar?) {
        self.followRedirects = followRedirects
        self.cookieJar = cookieJar
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        // 跳转响应里的 Set-Cookie 也要保存（OkHttp 的 CookieJar 同样会处理）。
        cookieJar?.save(from: response)
        guard followRedirects else {
            completionHandler(nil)
            return
        }
        var next = request
        let originalHost = task.originalRequest?.url?.host
        task.originalRequest?.allHTTPHeaderFields?.forEach { key, value in
            if key.lowercased() == "authorization" && request.url?.host != originalHost { return }
            if cookieJar != nil && key.lowercased() == "cookie" { return }
            if next.value(forHTTPHeaderField: key) == nil {
                next.setValue(value, forHTTPHeaderField: key)
            }
        }
        if let cookieJar {
            next.setValue(nil, forHTTPHeaderField: "Cookie")
            cookieJar.apply(to: &next)
        }
        completionHandler(next)
    }
}
