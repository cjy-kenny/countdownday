package com.example.countdownday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 登录 / 注册页：单页双模式（分段按钮切换） */
@Composable
fun AuthScreen(
    authVm: AuthViewModel = viewModel(),
    onSuccess: () -> Unit
) {
    val state by authVm.state.collectAsState()
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showOfflineFallback by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val emailValid = email.trim().length >= 3
    val passwordValid = password.length >= 6
    val canSubmit = emailValid && passwordValid && !state.loading

    // 登录/注册成功 → 进入主界面（AuthViewModel 在成功时已写入 Session）
    LaunchedEffect(state.loading, state.error) {
        if (!state.loading && state.error == null && SessionManager.get(context).account != null) {
            onSuccess()
        }
    }

    // 注册因网络失败 → 弹出"纯本地离线模式"降级对话框；其他错误显示 Snackbar 提示
    state.error?.let { err ->
        LaunchedEffect(err) {
            if (isRegister) {
                showOfflineFallback = true
            } else {
                android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
            }
            authVm.consumeError()
        }
    }

    if (showOfflineFallback) {
        AlertDialog(
            onDismissRequest = { showOfflineFallback = false },
            title = { Text("云端注册失败") },
            text = {
                Text("无法连接云端服务器（可能是网络不稳定或服务暂时不可用）。\n\n是否先以纯本地离线模式继续使用？数据仅保存在本机，之后可随时在登录页注册账号开启同步。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showOfflineFallback = false
                    // 纯本地模式：不写 Session（登录态仍是"未登录"），直接进主界面用本地库
                    authVm.enterOfflineMode()
                    onSuccess()
                }) { Text("离线使用") }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineFallback = false }) { Text("返回重试") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("倒数日", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            if (isRegister) "自定用户名与密码，云端加密同步" else "输入用户名密码，同步你的倒数日",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !isRegister,
                onClick = { isRegister = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("登录") }
            SegmentedButton(
                selected = isRegister,
                onClick = { isRegister = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("注册") }
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("用户名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                if (email.isNotEmpty() && !emailValid) Text("用户名至少 3 个字符", color = MaterialTheme.colorScheme.error)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                if (password.isNotEmpty() && !passwordValid) Text("密码至少 6 位", color = MaterialTheme.colorScheme.error)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Button(
            enabled = canSubmit,
            onClick = { authVm.submit(email, password, isRegister) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isRegister) "注册并登录" else "登录")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "离线也可正常使用：未登录时数据仅保存在本机，登录后自动与云端合并同步",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
