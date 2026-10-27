package com.example.countdownday

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CountdownApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Migration.migrateV1PrefsIfNeeded(this)
        scheduleDailyReminder()
        // 进程启动时全量重排精准闹钟（配合 BootReceiver 覆盖重启场景）
        CoroutineScope(Dispatchers.IO).launch {
            AlarmScheduler.rescheduleAll(this@CountdownApp)
        }
    }

    private fun scheduleDailyReminder() {
        val wm = WorkManager.getInstance(this)
        // v7 之前是每天 9:00 的固定任务，换成按小时检查后取消旧的
        wm.cancelUniqueWork("daily_event_reminder")

        // 每小时检查一次：各事件到达自己的提醒时间后触发一次（用 lastNotifiedDay 防重复）
        // 初次执行对齐到下一个整点的第 2 分钟
        val now = Calendar.getInstance()
        val nextSlot = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val initialDelay = nextSlot.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<EventReminderWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        wm.enqueueUniquePeriodicWork(
            "event_reminder_hourly",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
