package com.callerid.number.lookup.home.permit

import android.content.Context

class PermitVault(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun wasShown(key: String): Boolean = prefs.getBoolean(shownKey(key), false)

    fun markShown(key: String) {
        prefs.edit().putBoolean(shownKey(key), true).apply()
    }

    fun wasAsked(key: String): Boolean = prefs.getBoolean(askedKey(key), false)

    fun markAsked(key: String) {
        prefs.edit().putBoolean(askedKey(key), true).apply()
    }

    private fun shownKey(key: String) = "shown_$key"
    private fun askedKey(key: String) = "asked_$key"

    companion object {
        private const val FILE = "permission_engine_prefs"
    }
}
