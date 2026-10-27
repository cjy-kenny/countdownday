package com.example.countdownday

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 精准提醒调度：
 * - 每个开启提醒的事件在其目标日 reminderMinutes 时刻安排一个 setExactAndAllowWhileIdle 闹钟，
 *   深度休眠（Doze）下也能准时触发；
 * - Android 12+ 需要 SCHEDULE_EXACT_ALARM 用户授权，若无权限则不安排闹钟，
 *   由每小时的 WorkManager 轮询兜底（最多延迟 1 小时）；
 * - 任何数据变化（增/删/改/撤销）与开机重启都会全量重排。
 */
object AlarmScheduler {

    const val ACTION_ALARM_FIRE = "com.example.countdownday.ACTION_ALARM_FIRE"
    const val EXTRA_EVENT_ID = "event_id"

    /** Android 12+ 检查精准闹钟权限；低版本始终可用 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 全量重排：先取消库中所有事件的闹钟，再为"开启提醒且时刻在未来"的事件重新安排。
     *  重复事件（每年/每月）的目标日若已过期，自动推进到下一个周期。 */
    suspend fun rescheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dao = AppDatabase.get(context).eventDao()
        val events = dao.getAllRows()
        val exact = canScheduleExact(context)
        val now = System.currentTimeMillis()

        for (event in events) {
            if (event.deleted) continue
            var current = event
            // 重复事件的目标日已过去：推进到下一个周期（保持闹钟语义连续）
            if (event.repeatRule != "不重复" && event.targetDate <= DateUtils.today().timeInMillis) {
                val next = DateUtils.nextOccurrence(event.targetDate, event.repeatRule)
                if (next > event.targetDate) {
                    current = event.copy(
                        targetDate = next,
                        updatedAt = System.currentTimeMillis(),
                        pendingSync = true
                    )
                    dao.update(current)
                }
            }

            val pi = pendingIntent(context, event.id)
            am.cancel(pi)
            if (!current.reminderEnabled || !exact) continue
            val fireAt = DateUtils.normalize(current.targetDate) + current.reminderMinutes * 60000L
            if (fireAt <= now) continue // 已过去的时间不补闹钟，当天遗漏由每小时 Worker 兜底
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    fun pendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRE
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
