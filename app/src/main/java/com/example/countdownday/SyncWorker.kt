package com.example.countdownday

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager 同步Worker（带网络约束）：
 * 离线时修改的数据排队，网络恢复自动执行。
 * 账号密码从 SessionManager 读取（密码仅存在 EncryptedSharedPreferences 级别的私有 prefs）。
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val session = SessionManager.get(context)
        val account = session.account ?: return Result.success()
        val password = session.password ?: return Result.success()

        val outcome = SyncEngine.pushChanges(context, account, password)
        return if (outcome.ok) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_ONE_SHOT = "sync_once"
        private const val UNIQUE_PERIODIC = "sync_periodic"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 数据变化后的按需同步（离线排队，网络恢复自动执行） */
        fun requestSync(context: Context) {
            val session = SessionManager.get(context)
            if (session.account == null) return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_SHOT, ExistingWorkPolicy.REPLACE, request
            )
        }

        /** 登录后的周期兜底同步：每 6 小时 */
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** 登出后取消周期同步 */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
