import CoreLocation
import Foundation

enum CoordinateUtils {
    static let defaultMapPoint = (lat: 39.904200, lng: 116.407400)

    static func coordText(_ value: Double) -> String {
        String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), value)
    }

    /// 解析 "经度 纬度" / "纬度,经度" 等格式，返回 (lat, lng)。
    static func parse(_ raw: String) -> (lat: Double, lng: Double)? {
        let parts = raw
            .replacingOccurrences(of: "，", with: " ")
            .replacingOccurrences(of: ",", with: " ")
            .replacingOccurrences(of: "|", with: " ")
            .split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
        guard parts.count >= 2, let first = Double(parts[0]), let second = Double(parts[1]) else { return nil }
        guard let pair = normalizePair(first, second) else { return nil }
        guard (-90.0...90.0).contains(pair.lat), (-180.0...180.0).contains(pair.lng) else { return nil }
        return pair
    }

    static func parseText(_ raw: String) -> (lat: String, lng: String)? {
        parse(raw).map { (lat: coordText($0.lat), lng: coordText($0.lng)) }
    }

    static func normalizePair(_ first: Double, _ second: Double) -> (lat: Double, lng: Double)? {
        let lngRange = -180.0...180.0
        let latRange = -90.0...90.0
        if lngRange.contains(first) && latRange.contains(second) && !latRange.contains(first) { return (lat: second, lng: first) }
        if latRange.contains(first) && lngRange.contains(second) && !latRange.contains(second) { return (lat: first, lng: second) }
        if lngRange.contains(first) && latRange.contains(second) { return (lat: second, lng: first) }
        if latRange.contains(first) && lngRange.contains(second) { return (lat: first, lng: second) }
        return nil
    }

    // MARK: WGS-84 → GCJ-02（与 Android 端相同的算法）

    private static let a = 6378245.0
    private static let ee = 0.00669342162296594323

    static func wgs84ToGcj02(lat: Double, lng: Double) -> (lat: Double, lng: Double) {
        if outOfChina(lat: lat, lng: lng) { return (lat: lat, lng: lng) }
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        let radLat = lat / 180.0 * .pi
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        let sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * .pi)
        dLng = (dLng * 180.0) / (a / sqrtMagic * cos(radLat) * .pi)
        return (lat: lat + dLat, lng: lng + dLng)
    }

    private static func outOfChina(lat: Double, lng: Double) -> Bool {
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    private static func transformLat(_ x: Double, _ y: Double) -> Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * .pi) + 20.0 * sin(2.0 * x * .pi)) * 2.0 / 3.0
        ret += (20.0 * sin(y * .pi) + 40.0 * sin(y / 3.0 * .pi)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * .pi) + 320 * sin(y * .pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    private static func transformLng(_ x: Double, _ y: Double) -> Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * .pi) + 20.0 * sin(2.0 * x * .pi)) * 2.0 / 3.0
        ret += (20.0 * sin(x * .pi) + 40.0 * sin(x / 3.0 * .pi)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * .pi) + 300.0 * sin(x / 30.0 * .pi)) * 2.0 / 3.0
        return ret
    }
}

/// 一次性获取当前位置（WGS-84），15 秒超时。
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?
    private var authContinuation: CheckedContinuation<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    @MainActor
    func currentLocation() async throws -> CLLocation {
        if manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { cont in
                authContinuation = cont
                manager.requestWhenInUseAuthorization()
            }
        }
        switch manager.authorizationStatus {
        case .denied, .restricted:
            throw BjmfError.message("需要定位权限才能收藏当前位置")
        default:
            break
        }
        guard CLLocationManager.locationServicesEnabled() else {
            throw BjmfError.message("定位服务未开启")
        }
        if let last = manager.location, Date().timeIntervalSince(last.timestamp) < 120 {
            return last
        }

        return try await withCheckedThrowingContinuation { cont in
            continuation = cont
            manager.requestLocation()
            DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self] in
                guard let self, let pending = self.continuation else { return }
                self.continuation = nil
                pending.resume(throwing: BjmfError.message("暂时无法获取当前位置，请稍后重试"))
            }
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard manager.authorizationStatus != .notDetermined else { return }
        DispatchQueue.main.async {
            guard let pending = self.authContinuation else { return }
            self.authContinuation = nil
            pending.resume()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        DispatchQueue.main.async {
            guard let pending = self.continuation else { return }
            self.continuation = nil
            pending.resume(returning: location)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        DispatchQueue.main.async {
            guard let pending = self.continuation else { return }
            self.continuation = nil
            pending.resume(throwing: error)
        }
    }
}
