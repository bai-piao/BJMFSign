import CommonCrypto
import CryptoKit
import Foundation

/// 加密备份文件格式（Android / iOS 通用）：
/// ```
/// {"format":"bjmfsign-backup","version":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":200000,
///  "salt":"<base64 16B>","nonce":"<base64 12B>","ciphertext":"<base64 密文||16B GCM tag>"}
/// ```
/// 明文为 UTF-8 JSON，密钥由密码经 PBKDF2-HMAC-SHA256 派生（32 字节），使用 AES-256-GCM 加密。
enum BackupCrypto {
    static let format = "bjmfsign-backup"
    static let version = 1
    private static let iterations = 200_000

    enum Failure: LocalizedError {
        case wrongPassword
        case invalidFile

        var errorDescription: String? {
            switch self {
            case .wrongPassword: return tr("密码错误或文件已损坏")
            case .invalidFile: return tr("不是有效的备份文件")
            }
        }
    }

    static func encrypt(_ plain: Data, password: String) throws -> Data {
        let salt = randomBytes(16)
        let nonce = try AES.GCM.Nonce(data: randomBytes(12))
        let key = try deriveKey(password: password, salt: salt, iterations: iterations)
        let sealed = try AES.GCM.seal(plain, using: key, nonce: nonce)
        let envelope: [String: Any] = [
            "format": format,
            "version": version,
            "kdf": "PBKDF2-HMAC-SHA256",
            "iterations": iterations,
            "salt": salt.base64EncodedString(),
            "nonce": nonce.withUnsafeBytes { Data($0) }.base64EncodedString(),
            "ciphertext": (sealed.ciphertext + sealed.tag).base64EncodedString(),
        ]
        return try JSONSerialization.data(withJSONObject: envelope, options: [.sortedKeys])
    }

    static func decrypt(_ file: Data, password: String) throws -> Data {
        guard let json = try? JSONSerialization.jsonObject(with: file) as? [String: Any],
              json["format"] as? String == format,
              let salt = (json["salt"] as? String).flatMap({ Data(base64Encoded: $0) }),
              let nonceData = (json["nonce"] as? String).flatMap({ Data(base64Encoded: $0) }),
              let sealed = (json["ciphertext"] as? String).flatMap({ Data(base64Encoded: $0) }),
              sealed.count > 16 else {
            throw Failure.invalidFile
        }
        let rounds = (json["iterations"] as? Int) ?? iterations
        do {
            let key = try deriveKey(password: password, salt: salt, iterations: rounds)
            let box = try AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: nonceData),
                ciphertext: sealed.prefix(sealed.count - 16),
                tag: sealed.suffix(16)
            )
            return try AES.GCM.open(box, using: key)
        } catch {
            throw Failure.wrongPassword
        }
    }

    private static func deriveKey(password: String, salt: Data, iterations: Int) throws -> SymmetricKey {
        let passwordData = Data(password.utf8)
        var derived = Data(count: 32)
        let status = derived.withUnsafeMutableBytes { derivedBytes in
            salt.withUnsafeBytes { saltBytes in
                passwordData.withUnsafeBytes { passwordBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress?.assumingMemoryBound(to: CChar.self),
                        passwordData.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        32
                    )
                }
            }
        }
        guard status == Int32(kCCSuccess) else { throw Failure.wrongPassword }
        return SymmetricKey(data: derived)
    }

    private static func randomBytes(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count)
        _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }
}
