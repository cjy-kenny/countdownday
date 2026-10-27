package com.example.countdownday

import android.content.Context

/**
 * 登录会话：账号 + 密码保存在应用私有目录（Android 沙箱 + private 模式）。
 * 密码留在设备上是为了 SyncWorker 离线恢复后能自动重新加密上传；
 * 泄露面仅限本机 root，云端永远只有密文。
 */
data class Session(val account: String?, val password: String?)

object SessionManager {

    private const val PREFS = "session_prefs"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_PASSWORD = "password"

    fun get(context: Context): Session {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Session(prefs.getString(KEY_ACCOUNT, null), prefs.getString(KEY_PASSWORD, null))
    }

    fun save(context: Context, account: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT, account.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
