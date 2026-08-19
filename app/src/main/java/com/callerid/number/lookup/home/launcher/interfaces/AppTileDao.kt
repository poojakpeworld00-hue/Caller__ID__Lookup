package com.callerid.number.lookup.home.launcher.interfaces

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.callerid.number.lookup.home.launcher.databases.TileDatabase.Companion.TABLE_APPS
import com.callerid.number.lookup.home.launcher.models.AppTile

/** The app-drawer cache, rebuilt from the installed launcher activities on every refresh. */
class AppTileDao(private val helper: SQLiteOpenHelper) {

    fun getAppLaunchers(): List<AppTile> {
        val launchers = ArrayList<AppTile>()
        helper.readableDatabase.rawQuery("SELECT * FROM $TABLE_APPS", null).use { cursor ->
            val id = cursor.getColumnIndexOrThrow("id")
            val title = cursor.getColumnIndexOrThrow("title")
            val packageName = cursor.getColumnIndexOrThrow("package_name")
            val activityName = cursor.getColumnIndexOrThrow("activity_name")
            val order = cursor.getColumnIndexOrThrow("order")
            val thumbnailColor = cursor.getColumnIndexOrThrow("thumbnail_color")

            while (cursor.moveToNext()) {
                launchers += AppTile(
                    id = cursor.getLong(id),
                    title = cursor.getString(title),
                    packageName = cursor.getString(packageName),
                    activityName = cursor.getString(activityName),
                    order = cursor.getInt(order),
                    thumbnailColor = cursor.getInt(thumbnailColor),
                    drawable = null,
                )
            }
        }

        return launchers
    }

    fun insertAll(appLaunchers: List<AppTile>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            appLaunchers.forEach { launcher ->
                val values = ContentValues().apply {
                    launcher.id?.let { put("id", it) }
                    put("title", launcher.title)
                    put("package_name", launcher.packageName)
                    put("activity_name", launcher.activityName)
                    // `order` is a SQL keyword, so it has to stay quoted everywhere.
                    put("`order`", launcher.order)
                    put("thumbnail_color", launcher.thumbnailColor)
                }

                db.insertWithOnConflict(TABLE_APPS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteApp(packageName: String) {
        helper.writableDatabase.delete(TABLE_APPS, "package_name = ?", arrayOf(packageName))
    }

    fun deleteById(id: Long) {
        helper.writableDatabase.delete(TABLE_APPS, "id = ?", arrayOf(id.toString()))
    }
}
