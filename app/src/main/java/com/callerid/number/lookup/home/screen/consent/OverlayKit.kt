package com.callerid.number.lookup.home.screen.consent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.callerid.admesh.surface.TipSheetActivity

object OverlayKit {

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun buildOverlayIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        ).addFlags(
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        )

    fun showGuide(context: Context) =
        TipSheetActivity.show(context, TipSheetActivity.MODE_OVERLAY)
}
