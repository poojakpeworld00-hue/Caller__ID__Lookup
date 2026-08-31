package com.callerid.number.lookup.home.screen.consent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.TipSheetActivity
import com.callerid.number.lookup.home.kit.InstallIdRegistry
import com.callerid.number.lookup.home.runtime.incoming.PhoneStateReceiver
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher

object OverlayKit {

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Master switch for asking the user for "display over other apps".
     *
     * **Off.** The app does not request the overlay permission any more: the post-call screen
     * reaches the user through the default-role background-start exemption (see
     * [PhoneStateReceiver.handlePostCall]) or its full-screen-intent notification, and the
     * ringing-time card still comes up as an activity — so the prompt was buying too little
     * to be worth asking for.
     *
     * Flip this to `true` to bring every overlay prompt back; the per-user gates in
     * [isOfferable] below are still wired and take over from there.
     */
    private const val ASK_FOR_OVERLAY = false

    /**
     * True when the overlay permission may still be *offered* to this user.
     *
     * [ASK_FOR_OVERLAY] switches the whole thing off. When it is on, two further gates apply,
     * either one closing it:
     *
     *  1. **We are the default launcher.** Holding `ROLE_HOME` is itself a
     *     background-activity-start exemption, so the caller-ID screens already start through
     *     it (see [InstallIdRegistry.holdsSystemDefaultRole]) and asking for "display over
     *     other apps" on top buys nothing. Checked live, because the role can be granted
     *     mid-session by the set-as-default onboarding step. The trade-off is deliberate: the
     *     ringing-time card genuinely needs a WindowManager overlay, so on an unlocked phone a
     *     launcher user gets the full-screen card instead.
     *  2. **The IP-location "do not show" gate** — `Iscountry_Counter` +
     *     `CountryList_Counter_NShow`, resolved once at splash into
     *     [PromoVault.isNShowLocation]. Put a country / region / city in that list and the
     *     permission disappears there; put the literal `all` in it and it disappears
     *     worldwide.
     *
     * Every surface that *asks* for the overlay honours this — the permission sheet row,
     * Home's Enable banner, the Terms step. It says nothing about a permission the user has
     * already granted: the caller-ID card keeps working for them, because this gates the
     * prompt, not the feature.
     */
    fun isOfferable(context: Context): Boolean {
        if (!ASK_FOR_OVERLAY) return false
        if (runCatching { context.isDefaultLauncher() }.getOrDefault(false)) return false
        return !PromoVault.getInstance(context).isNShowLocation
    }

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
