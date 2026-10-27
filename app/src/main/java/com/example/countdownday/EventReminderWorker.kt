package com.example.countdownday

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class EventReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val today = DateUtils.today().timeInMillis
        val todayDay = DateUtils.todayEpochDay()
        val now = System.currentTimeMillis()

        val dao = AppDatabase.get(context).eventDao()
        val events = dao.getAllFlow().first()
        // 当天到期 + 开了提醒 + 到达该事件设定的提醒时间 + 今天还没提醒过
        val due = events.filter {
            DateUtils.normalize(it.targetDate) == today &&
                    it.reminderEnabled &&
                    now >= DateUtils.todayAt(it.reminderMinutes) &&
                    it.lastNotifiedDay < todayDay
        }

        if (due.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success() // 权限未授予，静默跳过；主界面会引导授权
            }

            ensureChannel(context)
            val launch = Intent(context, MainActivity::class.java)
            val contentIntent = PendingIntent.getActivity(
                context, 1, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            due.forEachIndexed { index, event ->
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("倒数日到了！")
                    .setContentText("「${event.name}」就是今天！")
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
                // 每个事件用不同 notificationId，避免相互覆盖
                NotificationManagerCompat.from(context).notify((2000 + event.id).toInt(), notification)
                dao.markNotified(event.id, todayDay)
            }
        }
        return Result.success()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "倒数日提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "事件当天的提醒通知" }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "event_reminder"
    }
}
