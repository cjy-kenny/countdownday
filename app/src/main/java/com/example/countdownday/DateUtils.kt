package com.example.countdownday

import java.util.Calendar

object DateUtils {
    /** 归零到当天 00:00 */
    fun startOfDay(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    /** 今天 00:00 */
    fun today(): Calendar =
        startOfDay(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )

    /** 任意毫秒值归一到当天 00:00 */
    fun normalize(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** 距离目标日的天数：正 = 未来，0 = 今天，负 = 已过去 */
    fun daysFromToday(targetMillis: Long): Int =
        ((targetMillis - today().timeInMillis) / 86400000L).toInt()

    fun format(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    /** 分钟数（当天 0 点起）→ "HH:mm" */
    fun formatTime(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)

    /** 今天是第几个"天"（epoch day），用于已通知标记 */
    fun todayEpochDay(): Long = today().timeInMillis / 86400000L

    /** 当天的 HH:mm 对应的时间戳 */
    fun todayAt(minutes: Int): Long = today().timeInMillis + minutes * 60000L

    /**
     * 重复规则推进：把目标日推进到下一个周期（保持月/日与提醒时间）。
     * 每年：+1 年（2/29 → 2/28 由 Calendar add 语义兜底）；每月：+1 月。
     * 持续推进直到结果在未来（处理设备长期关机后漏推多次的情况）。
     */
    fun nextOccurrence(targetMillis: Long, repeatRule: String): Long {
        if (repeatRule == "不重复") return targetMillis
        val cal = Calendar.getInstance().apply { timeInMillis = targetMillis }
        val now = today().timeInMillis
        var guard = 0
        do {
            when (repeatRule) {
                "每年" -> cal.add(Calendar.YEAR, 1)
                "每月" -> cal.add(Calendar.MONTH, 1)
                else -> return targetMillis
            }
            guard++
        } while (cal.timeInMillis <= now && guard < 100)
        return cal.timeInMillis
    }
}
