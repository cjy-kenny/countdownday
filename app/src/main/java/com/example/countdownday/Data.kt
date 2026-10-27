package com.example.countdownday

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "events")
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetDate: Long, // epoch millis, 时间归零到当天 00:00
    val reminderEnabled: Boolean = false, // 当天是否提醒
    val reminderMinutes: Int = 540, // 提醒时间（当天 0 点起的分钟数，默认 540 = 09:00）
    val lastNotifiedDay: Long = 0, // 上次已提醒的"天"序号（epoch day），防止一天重复通知
    val uuid: String = UUID.randomUUID().toString(), // 云同步主键
    val updatedAt: Long = System.currentTimeMillis(), // 最后修改时间（合并冲突裁决）
    val deleted: Boolean = false, // 软删除标记（同步后由云端拉取对齐）
    val pendingSync: Boolean = false, // 本地有变更待上传
    // ---- v10 新增 ----
    val category: String = "其他", // 分类：生日/纪念日/考试/节日/其他
    val note: String = "", // 详细备注
    val isPinned: Boolean = false, // 是否置顶
    val repeatRule: String = "不重复", // 重复规则：不重复/每年/每月
    val colorTheme: Int = 0, // 卡片主题色索引（0-4）
)

@Dao
interface CountdownEventDao {
    /** 用户可见列表：过滤软删除 */
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY targetDate ASC")
    fun getAllFlow(): Flow<List<CountdownEvent>>

    /** 全量行（含软删除）：同步引擎使用 */
    @Query("SELECT * FROM events")
    suspend fun getAllRows(): List<CountdownEvent>

    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY targetDate ASC")
    suspend fun getAll(): List<CountdownEvent>

    @Query("SELECT * FROM events WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): CountdownEvent?

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CountdownEvent?

    @Insert
    suspend fun insert(event: CountdownEvent)

    @Update
    suspend fun update(event: CountdownEvent)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("UPDATE events SET lastNotifiedDay = :day WHERE id = :id")
    suspend fun markNotified(id: Long, day: Long)
}

@Database(entities = [CountdownEvent::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): CountdownEventDao

    companion object {
        /** v1 → v2：新增提醒开关列 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：自定义提醒时间 + 已通知标记 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN reminderMinutes INTEGER NOT NULL DEFAULT 540")
                db.execSQL("ALTER TABLE events ADD COLUMN lastNotifiedDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：云同步字段（存量数据补 UUID 和时间戳） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE events SET uuid = hex(randomblob(16)) WHERE uuid = ''")
                db.execSQL("UPDATE events SET updatedAt = strftime('%s','now') * 1000 WHERE updatedAt = 0")
            }
        }

        /** v4 → v5：分类/备注/置顶/重复规则/主题色（存量数据给默认值） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN category TEXT NOT NULL DEFAULT '其他'")
                db.execSQL("ALTER TABLE events ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE events ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN repeatRule TEXT NOT NULL DEFAULT '不重复'")
                db.execSQL("ALTER TABLE events ADD COLUMN colorTheme INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "countdown.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
