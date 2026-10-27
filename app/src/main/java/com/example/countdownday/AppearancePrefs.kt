package com.example.countdownday

import android.content.Context

/**
 * 个性化偏好（设置页"外观"分区）：
 * - useDynamicColor：Android 12+ 用壁纸动态取色（默认开）
 * - themeSeedIndex：关闭动态取色时使用的固定主题色（对应 ColorThemes 索引，默认 0 蓝）
 * - darkMode：0=跟随系统 1=浅色 2=深色
 */
object AppearancePrefs {

    private const val PREFS = "appearance_prefs"
    private const val KEY_DYNAMIC = "use_dynamic_color"
    private const val KEY_SEED = "theme_seed_index"
    private const val KEY_DARK = "dark_mode"

    data class Appearance(
        val useDynamicColor: Boolean,
        val themeSeedIndex: Int,
        val darkMode: Int
    )

    fun get(context: Context): Appearance {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Appearance(
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
            themeSeedIndex = prefs.getInt(KEY_SEED, 0),
            darkMode = prefs.getInt(KEY_DARK, 0)
        )
    }

    fun save(context: Context, useDynamicColor: Boolean? = null, themeSeedIndex: Int? = null, darkMode: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (useDynamicColor != null) prefs.putBoolean(KEY_DYNAMIC, useDynamicColor)
        if (themeSeedIndex != null) prefs.putInt(KEY_SEED, themeSeedIndex)
        if (darkMode != null) prefs.putInt(KEY_DARK, darkMode)
        prefs.apply()
    }
}
