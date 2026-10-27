package com.example.countdownday

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 同步引擎（textdb 单槽位整包同步）：
 * - 上行：本地全部事件（含软删除）→ JSON → AES-GCM 密文 → 覆盖云端
 * - 下行（登录/拉取）：解密云端 JSON → 按 uuid 合并进 Room（updatedAt 新者胜）→ 返回合并详情
 * - 乐观锁：下载时记 rev；上传前再读一次云端 rev，若被别人改过则先合并再推
 */
object SyncEngine {

    data class Outcome(val ok: Boolean, val message: String)

    suspend fun exportLocalJson(dao: CountdownEventDao): Pair<JSONObject, List<CountdownEvent>> {
        val rows = dao.getAllRows()
        val array = JSONArray()
        for (event in rows) {
            array.put(
                JSONObject()
                    .put("uuid", event.uuid)
                    .put("name", event.name)
                    .put("targetDate", event.targetDate)
                    .put("reminderEnabled", event.reminderEnabled)
                    .put("reminderMinutes", event.reminderMinutes)
                    .put("deleted", event.deleted)
                    .put("updatedAt", event.updatedAt)
                    .put("category", event.category)
                    .put("note", event.note)
                    .put("isPinned", event.isPinned)
                    .put("repeatRule", event.repeatRule)
                    .put("colorTheme", event.colorTheme)
            )
        }
        val json = JSONObject()
            .put("rev", System.currentTimeMillis()) // 本包生成时间
            .put("events", array)
        return json to rows
    }

    /** 下行合并：把云端 JSON 合并进本地。返回 (合并条数, 新增条数) */
    /** 下行合并：把云端 JSON 合并进本地。返回 (合并条数, 新增条数) */
    suspend fun mergeRemoteIntoRoom(dao: CountdownEventDao, cloudJson: JSONObject): Pair<Int, Int> {
        val events = cloudJson.optJSONArray("events") ?: JSONArray()
        var merged = 0
        var added = 0
        for (i in 0 until events.length()) {
            val obj = events.optJSONObject(i) ?: continue
            val uuid = obj.optString("uuid")
            if (uuid.isEmpty()) continue
            val remoteUpdated = obj.optLong("updatedAt", 0L)
            val local = dao.getByUuid(uuid)
            if (local == null) {
                dao.insert(
                    CountdownEvent(
                        name = obj.optString("name"),
                        targetDate = obj.optLong("targetDate", 0L),
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderMinutes = obj.optInt("reminderMinutes", 540),
                        lastNotifiedDay = 0,
                        uuid = uuid,
                        updatedAt = remoteUpdated,
                        deleted = obj.optBoolean("deleted", false),
                        category = obj.optString("category", "其他"),
                        note = obj.optString("note", ""),
                        isPinned = obj.optBoolean("isPinned", false),
                        repeatRule = obj.optString("repeatRule", "不重复"),
                        colorTheme = obj.optInt("colorTheme", 0)
                    )
                )
                added++
            } else if (remoteUpdated > local.updatedAt) {
                dao.update(
                    local.copy(
                        name = obj.optString("name", local.name),
                        targetDate = obj.optLong("targetDate", local.targetDate),
                        reminderEnabled = obj.optBoolean("reminderEnabled", local.reminderEnabled),
                        reminderMinutes = obj.optInt("reminderMinutes", local.reminderMinutes),
                        updatedAt = remoteUpdated,
                        deleted = obj.optBoolean("deleted", local.deleted),
                        pendingSync = false,
                        category = obj.optString("category", local.category),
                        note = obj.optString("note", local.note),
                        isPinned = obj.optBoolean("isPinned", local.isPinned),
                        repeatRule = obj.optString("repeatRule", local.repeatRule),
                        colorTheme = obj.optInt("colorTheme", local.colorTheme)
                    )
                )
                merged++
            }
        }
        // 物理清除已同步的软删除行
        dao.getAllRows().filter { it.deleted && !it.pendingSync }.forEach { dao.deleteById(it.id) }
        return merged to added
    }

    /** 全量同步：登录成功后调用。先拉云端合并，再推本地整包。 */
    suspend fun fullSync(context: Context, account: String, password: String): Outcome =
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).eventDao()
            try {
                // 1) 拉云端
                val remote = CloudStore.download(account)
                if (remote != null) {
                    val plain = CloudStore.decrypt(remote.second, password, account)
                        ?: return@withContext Outcome(false, "密码不正确（云端数据解密失败）")
                    mergeRemoteIntoRoom(dao, JSONObject(plain))
                }
                // 2) 推本地
                val (json, _) = exportLocalJson(dao)
                val baseRev = remote?.first ?: 0L
                val upload = CloudStore.upload(account, password, json.toString(), baseRev + 1)
                if (!upload.ok) return@withContext Outcome(false, upload.message)
                // 3) 收尾
                AlarmScheduler.rescheduleAll(context)
                WidgetUpdater.updateAll(context)
                Outcome(true, "同步完成")
            } catch (e: CloudStore.NetworkException) {
                Outcome(false, e.message ?: "云端连接失败")
            } catch (e: Exception) {
                Outcome(false, "同步失败：${e.message?.take(60) ?: "网络异常"}")
            }
        }

    /** 推送上行（数据变化后调用；云端有更新时会先合并再推）。网络异常抛给 Worker 重试 */
    suspend fun pushChanges(context: Context, account: String, password: String): Outcome =
        withContext(Dispatchers.IO) {
            val dao = AppDatabase.get(context).eventDao()
            try {
                val remote = CloudStore.download(account)
                if (remote != null) {
                    val plain = CloudStore.decrypt(remote.second, password, account)
                    if (plain != null) mergeRemoteIntoRoom(dao, JSONObject(plain))
                }
                val (json, _) = exportLocalJson(dao)
                val baseRev = remote?.first ?: 0L
                val upload = CloudStore.upload(account, password, json.toString(), baseRev + 1)
                if (upload.ok) Outcome(true, "已同步") else Outcome(false, upload.message)
            } catch (e: CloudStore.NetworkException) {
                Outcome(false, e.message ?: "云端连接失败") // Worker 收到非 ok 会 retry
            } catch (e: Exception) {
                Outcome(false, "同步失败：${e.message?.take(60) ?: "网络异常"}")
            }
        }
}
