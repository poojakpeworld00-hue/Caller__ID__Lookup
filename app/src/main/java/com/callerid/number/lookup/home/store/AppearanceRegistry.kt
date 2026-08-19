package com.callerid.number.lookup.home.store

import androidx.appcompat.app.AppCompatDelegate

object AppearanceRegistry {

    val modes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    fun apply(mode: Int) = AppCompatDelegate.setDefaultNightMode(mode)

    fun indexOf(mode: Int): Int = modes.indexOf(mode).coerceAtLeast(0)
}
