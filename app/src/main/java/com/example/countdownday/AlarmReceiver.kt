package com.example.countdownday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 精准闹钟触发：到点立即给对应事件发通知并标记已提醒 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM_FIRE) return
        val eventId = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId <= 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).eventDao()
                val event = dao.getAll().firstOrNull { it.id == eventId } ?: return@launch
                val todayDay = DateUtils.todayEpochDay()
                val isDueToday = DateUtils.normalize(event.targetDate) == DateUtils.today().timeInMillis
                if (!isDueToday || !event.reminderEnabled || event.lastNotifiedDay >= todayDay) return@launch

                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) return@launch

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(
                    NotificationChannel(
                        EventReminderWorker.CHANNEL_ID, "倒数日提醒", NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "事件当天的提醒通知" }
                )

                val launch = Intent(context, MainActivity::class.java)
                val contentIntent = PendingIntent.getActivity(
                    context, 1, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, EventReminderWorker.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("倒数日到了！")
                    .setContentText("「${event.name}」就是今天！")
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify((2000 + event.id).toInt(), notification)
                dao.markNotified(event.id, todayDay)

                // 重复事件：触发后立即把 targetDate 推进到下一个周期并重排闹钟。
                // setExactAndAllowWhileIdle 在 Doze 下依然准时触发，重排后的闹钟同样享受此精度；
                // 若重排时系统在 Doze，仍由每小时的 WorkManager 轮询兜底。
                if (event.repeatRule != "不重复") {
                    val next = DateUtils.nextOccurrence(event.targetDate, event.repeatRule)
                    if (next > event.targetDate) {
                        dao.update(
                            event.copy(
                                targetDate = next,
                                updatedAt = System.currentTimeMillis(),
                                pendingSync = true,
                                lastNotifiedDay = 0
                            )
                        )
                        AlarmScheduler.rescheduleAll(context)
                        WidgetUpdater.updateAll(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
