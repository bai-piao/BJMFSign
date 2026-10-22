package com.bjmf.sign.android.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * 加密备份文件格式（Android / iOS 通用）：
 * ```
 * {"format":"bjmfsign-backup","version":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":200000,
 *  "salt":"<base64 16B>","nonce":"<base64 12B>","ciphertext":"<base64 密文||16B GCM tag>"}
 * ```
 * 明文为 UTF-8 JSON，密钥由密码经 PBKDF2-HMAC-SHA256 派生（32 字节），使用 AES-256-GCM 加密。
 */
object BackupCrypto {
    const val FORMAT = "bjmfsign-backup"
    const val VERSION = 1
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class WrongPasswordException : Exception("密码错误或文件已损坏")
    class InvalidFileException : Exception("不是有效的备份文件")

    fun encrypt(plainJson: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, nonce))
        val sealed = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("kdf", "PBKDF2-HMAC-SHA256")
            .put("iterations", ITERATIONS)
            .put("salt", b64(salt))
            .put("nonce", b64(nonce))
            .put("ciphertext", b64(sealed))
            .toString()
    }

    fun decrypt(fileContent: String, password: String): String {
        val json = runCatching { JSONObject(fileContent) }.getOrNull() ?: throw InvalidFileException()
        if (json.optString("format") != FORMAT) throw InvalidFileException()
        val iterations = json.optInt("iterations", ITERATIONS)
        val salt = unb64(json.optString("salt")) ?: throw InvalidFileException()
        val nonce = unb64(json.optString("nonce")) ?: throw InvalidFileException()
        val sealed = unb64(json.optString("ciphertext")) ?: throw InvalidFileException()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: Exception) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(text: String): ByteArray? =
        if (text.isBlank()) null else runCatching { Base64.decode(text, Base64.DEFAULT) }.getOrNull()
}
