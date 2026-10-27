package com.example.countdownday

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

object Migration {

    private const val V1_PREFS = "countdown_prefs"
    private const val V1_KEY_TARGET = "target_millis"
    private const val MIGRATED_FLAG = "v1_migrated_to_room"

    /**
     * v1 版本把单个目标日期存在 SharedPreferences（countdown_prefs / target_millis）。
     * 升级到 v3 后首启动时：若有该数据且 Room 还没迁过，就把"我的倒数日"
     * 作为一个事件写入 Room，然后打上迁移标记（标记存同一 prefs，避免重复导入）。
     */
    fun migrateV1PrefsIfNeeded(context: Context) {
        thread {
            val prefs = context.getSharedPreferences(V1_PREFS, Context.MODE_PRIVATE)
            val target = prefs.getLong(V1_KEY_TARGET, 0L)
            if (target > 0L && !prefs.getBoolean(MIGRATED_FLAG, false)) {
                runBlocking {
                    AppDatabase.get(context).eventDao().insert(
                        CountdownEvent(name = "我的倒数日", targetDate = target)
                    )
                }
                prefs.edit().putBoolean(MIGRATED_FLAG, true).apply()
            }
        }
    }
}
