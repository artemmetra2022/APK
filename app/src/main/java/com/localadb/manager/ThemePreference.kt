package com.localadb.manager

import android.content.Context

/** null = следовать системной теме, true = всегда тёмная, false = всегда светлая. */
class ThemePreference(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun get(): Boolean? = when (prefs.getString(KEY, "system")) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    fun set(darkMode: Boolean?) {
        prefs.edit().putString(
            KEY,
            when (darkMode) {
                true -> "dark"
                false -> "light"
                null -> "system"
            },
        ).apply()
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
