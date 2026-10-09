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
/// - `useCookieJar` 为 true 时使用独立的内存 Cookie 存储（扫码会话）；否则只发送手动设置的 Cookie 头。
/// - `followRedirects` 控制是否跟随跳转；跟随时保留原请求头（与 OkHttp 一致）。
final class HTTPClient {
    private let session: URLSession

    init(useCookieJar: Bool = false, followRedirects: Bool = true, timeout: TimeInterval = 20) {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        if useCookieJar {
            config.httpCookieStorage = HTTPCookieStorage()
            config.httpShouldSetCookies = true
            config.httpCookieAcceptPolicy = .always
        } else {
            config.httpCookieStorage = nil
            config.httpShouldSetCookies = false
        }
        // URLSession 会强引用 delegate，因此使用独立的 delegate 对象，并在释放时使会话失效。
        session = URLSession(
            configuration: config,
            delegate: RedirectDelegate(followRedirects: followRedirects),
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
        let (data, response) = try await session.data(for: makeRequest(url, method: "GET", headers: headers))
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
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
        return request
    }

    private func send(_ request: URLRequest) async throws -> HTTPTextResponse {
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
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

private final class RedirectDelegate: NSObject, URLSessionTaskDelegate {
    private let followRedirects: Bool

    init(followRedirects: Bool) {
        self.followRedirects = followRedirects
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard followRedirects else {
            completionHandler(nil)
            return
        }
        var next = request
        let originalHost = task.originalRequest?.url?.host
        task.originalRequest?.allHTTPHeaderFields?.forEach { key, value in
            if key.lowercased() == "authorization" && request.url?.host != originalHost { return }
            if next.value(forHTTPHeaderField: key) == nil {
                next.setValue(value, forHTTPHeaderField: key)
            }
        }
        completionHandler(next)
    }
}
