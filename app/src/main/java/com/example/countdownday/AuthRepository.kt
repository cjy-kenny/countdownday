package com.example.countdownday

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AuthResult(val ok: Boolean, val message: String? = null)

/**
 * 鉴权仓库（零信任设计）：
 * - 注册 = 云端必须为空（否则提示账号已存在），随后写入首次加密包
 * - 登录 = 下载云端密文并尝试解密；GCM 校验失败即"密码错误"
 * - 无服务器账号体系，密码永不离开设备
 * - 所有网络异常翻译成中文文案返回，绝不静默
 */
class AuthRepository {

    suspend fun signUp(account: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (account.trim().length < 3) return@withContext AuthResult(false, "用户名至少 3 个字符")
        if (password.length < 6) return@withContext AuthResult(false, "密码至少 6 位")
        // 先探测云端槽位：区分"已注册"与"网络失败"（网络失败不给用户误报"已注册"）
        when (CloudStore.probe(account)) {
            CloudStore.ProbeState.EXISTS ->
                return@withContext AuthResult(false, "该用户名已注册，请直接登录")
            CloudStore.ProbeState.NETWORK_ERROR ->
                return@withContext AuthResult(false, "云端服务器连接失败，请检查网络后重试")
            CloudStore.ProbeState.EMPTY -> { /* 可注册，继续 */ }
        }
        // 写入首包（空事件列表，rev=1）
        val initJson = JSONObject()
            .put("rev", 1)
            .put("events", JSONArray())
            .toString()
        val result = try {
            CloudStore.upload(account, password, initJson, 1)
        } catch (e: CloudStore.NetworkException) {
            return@withContext AuthResult(false, e.message ?: "云端注册失败，请稍后重试")
        }
        if (result.ok) AuthResult(true)
        else AuthResult(false, result.message)
    }

    suspend fun signIn(account: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val remote = try {
            CloudStore.download(account)
        } catch (e: CloudStore.NetworkException) {
            return@withContext AuthResult(false, e.message ?: "云端连接失败，请检查网络")
        }
            ?: return@withContext AuthResult(false, "账号不存在，请先注册")
        val decrypted = CloudStore.decrypt(remote.second, password, account)
            ?: return@withContext AuthResult(false, "密码不正确")
        AuthResult(true)
    }

    fun signOut() { /* 无服务器会话，登出只清本地 */ }
}
