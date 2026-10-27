package com.example.countdownday

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据仓库层：本地 Room 为唯一事实来源。
 */
class EventRepository(private val dao: CountdownEventDao) {

    companion object {
        /** 用户可见列表也走软删除过滤（getAllFlow 已过滤） */
        private const val TAG = "EventRepository"
    }

    val events: Flow<List<CountdownEvent>> = dao.getAllFlow()

    suspend fun getAll(): List<CountdownEvent> = dao.getAll()

    suspend fun add(
        name: String, targetDate: Long, reminder: Boolean, reminderMinutes: Int,
        category: String, note: String, isPinned: Boolean, repeatRule: String, colorTheme: Int
    ) {
        val now = System.currentTimeMillis()
        val event = CountdownEvent(
            name = name,
            targetDate = targetDate,
            reminderEnabled = reminder,
            reminderMinutes = reminderMinutes,
            updatedAt = now,
            pendingSync = true,
            category = category,
            note = note,
            isPinned = isPinned,
            repeatRule = repeatRule,
            colorTheme = colorTheme
        )
        dao.insert(event)
    }

    suspend fun update(
        id: Long, name: String, targetDate: Long, reminder: Boolean, reminderMinutes: Int,
        category: String, note: String, isPinned: Boolean, repeatRule: String, colorTheme: Int
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                name = name,
                targetDate = targetDate,
                reminderEnabled = reminder,
                reminderMinutes = reminderMinutes,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true,
                category = category,
                note = note,
                isPinned = isPinned,
                repeatRule = repeatRule,
                colorTheme = colorTheme
            )
        )
    }

    /** 软删除：打 deleted + pendingSync，同步引擎上传后物理清除 */
    suspend fun delete(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true
            )
        )
    }

    /** 撤销滑动删除：软删除行恢复为正常（v10 字段已随 UiEvent 快照保留） */
    suspend fun restore(event: UiEvent) {
        val existing = dao.getById(event.id) ?: return
        dao.update(
            existing.copy(
                deleted = false,
                updatedAt = System.currentTimeMillis(),
                pendingSync = true,
                reminderEnabled = event.reminder,
                reminderMinutes = event.reminderMinutes,
                name = event.name,
                targetDate = event.targetMillis,
                category = event.category,
                note = event.note,
                isPinned = event.isPinned,
                repeatRule = event.repeatRule,
                colorTheme = event.colorTheme
            )
        )
    }

    /**
     * 将全部事件导出为 JSON，通过 SAF 写入用户在系统对话框中选定的位置。
     */
    suspend fun exportJson(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val events = dao.getAll()
            val array = JSONArray()
            for (event in events) {
                array.put(
                    JSONObject()
                        .put("name", event.name)
                        .put("targetDate", event.targetDate)
                        .put("targetDateText", DateUtils.format(event.targetDate))
                        .put("reminderEnabled", event.reminderEnabled)
                        .put("reminderMinutes", event.reminderMinutes)
                )
            }
            val json = JSONObject()
                .put("app", "CountdownDay")
                .put("version", 2)
                .put("exportedAt", System.currentTimeMillis())
                .put("count", events.size)
                .put("events", array)

            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext "导出失败：无法写入所选文件"

            "已导出 ${events.size} 个事件"
        } catch (e: Exception) {
            "导出失败：${e.message ?: "未知错误"}"
        }
    }
}
