package com.example.countdownday

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** @param isRegister true=注册，false=登录 */
    fun submit(account: String, password: String, isRegister: Boolean) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            val result = if (isRegister) repo.signUp(account, password) else repo.signIn(account, password)
            if (result.ok) {
                SessionManager.save(getApplication(), account, password)
                // 登录成功：后台全量同步（拉云端合并 + 推本地），会话已建立即刻放行进入主界面。
                // 同步放后台跑，失败由 Worker 网络恢复后自动重试，不阻塞进入 App。
                SyncWorker.schedulePeriodicSync(getApplication())
                SyncWorker.requestSync(getApplication())
                _state.value = AuthUiState(loading = false)
            } else {
                _state.value = AuthUiState(loading = false, error = result.message)
            }
        }
    }

    fun consumeError() {
        if (_state.value.error != null) {
            _state.value = _state.value.copy(error = null)
        }
    }

    /** 降级方案：用户选择"纯本地离线模式"——不写登录会话，直接进主界面用本地库。
     *  数据照常保存 Room；之后登录成功时会自动与云端合并。 */
    fun enterOfflineMode() {
        // 无需额外状态：SessionManager 保持无账号，AppNavGraph 已放行
    }
}
