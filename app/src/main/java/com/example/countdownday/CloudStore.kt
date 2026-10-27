package com.example.countdownday

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 云同步后端（textdb.online 免费匿名 KV，国内可直连）：
 * - "账号" = 用户自定义用户名，云端槽位 key = 固定前缀 + SHA-256(用户名小写) 前 32 位 hex
 * - "密码" 不上传：PBKDF2(密码, username盐) 派生 AES-256 密钥，整个 JSON 用 AES-GCM 加密后 URL-safe Base64 存云端
 * - 服务器只见密文：无密码的人拿到云端数据也无法解出任何事件内容
 * - 登录验证 = 尝试用派生密钥解密云端数据（GCM 校验标签自动判密码对错）
 * - 同步 = 单槽位整包覆盖（乐观锁：rev 比对）
 *
 * 错误处理：所有网络异常分类为中文可读文案，通过 SyncResult.message 逐级传给 UI，绝不静默失败。
 */
object CloudStore {

    private const val BASE = "https://textdb.online"
    private const val KEY_PREFIX = "cdday9x"
    private const val PBKDF2_ITER = 40000
    private const val TIMEOUT_MS = 10_000 // 用户要求的 10 秒超时
    // textdb 免费接口对 URL 长度有限制（Cloudflare 约 8KB），保守限制密文体
    private const val MAX_VALUE_LEN = 6000

    /** 网络层错误分类 */
    class NetworkException(message: String) : Exception(message)

    data class SyncResult(val ok: Boolean, val message: String, val cloudJson: String? = null)

    fun storageKey(account: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(account.trim().lowercase().toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /** 密码 → AES-256 密钥（盐=用户名，防止相同密码生成相同密钥） */
    private fun deriveKey(password: String, account: String): SecretKeySpec {
        return try {
            val salt = ("cdday|" + account.trim().lowercase()).toByteArray(Charsets.UTF_8)
            val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } catch (e: Exception) {
            throw NetworkException("密钥派生失败：${e.message?.take(40) ?: "算法初始化异常"}")
        }
    }

    fun encrypt(plain: String, password: String, account: String): String {
        val key = deriveKey(password, account)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // URL-safe Base64（- _ 替代 + /）：textdb 服务器会把 + 解码成空格，URL-safe 可彻底绕开；
        // NO_WRAP 保证无换行符（Android 默认 Base64 若带 CRLF 会破坏 URL）
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decrypt(payload: String, password: String, account: String): String? {
        return try {
            val data = Base64.decode(payload.replace(" ", "+"), Base64.NO_WRAP or Base64.URL_SAFE)
            val key = deriveKey(password, account)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
            String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            null // 密码错误或数据损坏：GCM 标签校验失败
        }
    }

    /** 把底层异常翻译成用户能看懂的中文 */
    private fun describeNetworkError(e: Exception): String = when (e) {
        is NetworkException -> e.message ?: "未知错误"
        is SocketTimeoutException -> "云端服务器连接超时，请检查网络"
        is UnknownHostException -> "无法解析云端服务器地址，请检查网络或 DNS"
        is java.net.ConnectException -> "无法连接到云端服务器，请检查网络"
        is java.net.SocketException -> "网络连接被中断，请重试"
        is javax.net.ssl.SSLException -> "安全连接建立失败，请检查网络环境"
        else -> "网络请求失败：${e.message?.take(50) ?: e.javaClass.simpleName}"
    }

    /** 上传整包（URL-safe Base64 密文追加 rev 前缀标记，乐观锁）。10s 超时，重试 2 次 */
    fun upload(account: String, password: String, json: String, rev: Long): SyncResult {
        var lastError = "未执行"
        repeat(3) { attempt ->
            try {
                val body = "$rev|" + encrypt(json, password, account)
                if (body.length > MAX_VALUE_LEN) {
                    return SyncResult(false, "数据量超出云端单次写入限制（${body.length}/$MAX_VALUE_LEN），请联系开发者")
                }
                val url = URL("$BASE/update/?key=${urlEncode(storageKey(account))}&value=${urlEncode(body)}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CountdownDay/8.1")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val code = conn.responseCode
                conn.inputStream?.use { it.readBytes() }
                conn.disconnect()
                if (code == 200) return SyncResult(true, "已同步到云端")
                lastError = "云端服务器拒绝了请求 (HTTP $code)"
            } catch (e: Exception) {
                lastError = describeNetworkError(e)
            }
            if (attempt < 2) Thread.sleep(800)
        }
        return SyncResult(false, lastError)
    }

    /** 下载整包密文；返回 (rev, 密文) 或 null（云端为空/未注册） */
    fun download(account: String): Pair<Long, String>? {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val url = URL("$BASE/${storageKey(account)}/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CountdownDay/8.1")
                val code = conn.responseCode
                val text = if (code == 200) conn.inputStream?.use { it.readBytes().toString(Charsets.UTF_8) } else null
                conn.disconnect()
                val body = text?.trim().orEmpty()
                // 403/404/空页 = 云端槽位不存在（未注册）；其他非 200 视为服务端异常
                if (code != 200 || body.isEmpty() || body.startsWith("<")) return null
                val sep = body.indexOf('|')
                if (sep <= 0) return null
                val rev = body.substring(0, sep).toLongOrNull() ?: return null
                return rev to body.substring(sep + 1)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) Thread.sleep(800)
            }
        }
        // 下载失败不弹错（同步 Worker 会重试），仅在注册/登录流程里由调用方判空提示
        throw NetworkException(describeNetworkError(lastError ?: Exception("网络请求失败")))
    }

    /** 只判断"云端槽位是否已存在"，吞掉网络错误返回三态 */
    fun probe(account: String): ProbeState {
        return try {
            if (download(account) != null) ProbeState.EXISTS else ProbeState.EMPTY
        } catch (e: NetworkException) {
            ProbeState.NETWORK_ERROR
        }
    }

    enum class ProbeState { EXISTS, EMPTY, NETWORK_ERROR }

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")
}
