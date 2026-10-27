package com.example.countdownday

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    /**
     * "补发"场景：如果用户在事件提醒时间已过之后才添加/修改了一个"今天到期"且开了提醒的事件，
     * 周期任务最迟 1 小时内会覆盖，这里立即排队一次一次性检查，提醒更及时。
     * 提醒时间未到的情况由每小时周期任务在时间到后触发，不会重复（lastNotifiedDay 防重）。
     */
    fun scheduleSameDayCatchUp(context: Context, targetDate: Long, reminderMinutes: Int) {
        if (DateUtils.normalize(targetDate) != DateUtils.today().timeInMillis) return
        if (System.currentTimeMillis() < DateUtils.todayAt(reminderMinutes)) return

        val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS) // 给数据库写入留点余量
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "same_day_reminder_catch_up",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
