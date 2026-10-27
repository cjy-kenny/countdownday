package com.example.countdownday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object WidgetUpdater {
    /** 数据变化后立即刷新所有已添加到桌面的小组件 */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, NearestEventWidget::class.java)
        )
        if (ids.isEmpty()) return
        val intent = Intent(context, NearestEventWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}

class NearestEventWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        thread {
            val repository = EventRepository(AppDatabase.get(context).eventDao())
            val events = runBlocking { repository.getAll() }
            // 最近事件：置顶优先 → 未过期按天数升序 → 已过期
            val nearest = events.filter { it.isPinned }
                .minByOrNull { DateUtils.daysFromToday(it.targetDate) }
                ?: events.filter { DateUtils.daysFromToday(it.targetDate) >= 0 }
                    .minByOrNull { DateUtils.daysFromToday(it.targetDate) }
                ?: events.minByOrNull { it.targetDate }

            val views = RemoteViews(context.packageName, R.layout.widget_nearest).apply {
                // 按最近事件的主题色渲染小组件背景（无事件时用默认蓝）
                setInt(R.id.widget_root, "setBackgroundColor", ColorThemes.argb(nearest?.colorTheme ?: 0))
                if (nearest == null) {
                    setTextViewText(R.id.widget_days, "--")
                    setTextViewText(R.id.widget_name, "暂无倒数日")
                    setTextViewText(R.id.widget_date, "添加事件后显示")
                } else {
                    val days = DateUtils.daysFromToday(nearest.targetDate)
                    setTextViewText(R.id.widget_days, when {
                        days > 0 -> days.toString()
                        days == 0 -> "今天"
                        else -> (-days).toString()
                    })
                    setTextViewText(R.id.widget_name, nearest.name)
                    setTextViewText(R.id.widget_date, when {
                        days > 0 -> "距离 ${DateUtils.format(nearest.targetDate)}"
                        days == 0 -> "就是今天！"
                        else -> "「${nearest.name}」已过去"
                    })
                }
                val launch = Intent(context, MainActivity::class.java)
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 0, launch, pendingIntentFlags())
                )
            }

            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    android.content.ComponentName(context, NearestEventWidget::class.java)
                )
                onUpdate(context, manager, ids)
            }
        }
    }
}
