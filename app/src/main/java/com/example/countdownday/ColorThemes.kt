package com.example.countdownday

/**
 * 卡片主题色板：5 种预设。索引存入 colorTheme 字段（0-4），
 * Compose 卡片与小组件（RemoteViews）共用此色值。
 */
object ColorThemes {
    data class Theme(val label: String, val argb: Int)

    val all = listOf(
        Theme("蓝", 0xFF1E88E5.toInt()),
        Theme("青", 0xFF00897B.toInt()),
        Theme("紫", 0xFF7E57C2.toInt()),
        Theme("粉", 0xFFEC407A.toInt()),
        Theme("橙", 0xFFFB8C00.toInt()),
    )

    fun argb(index: Int): Int = all.getOrNull(index.coerceIn(0, all.size - 1))?.argb ?: all[0].argb
}
