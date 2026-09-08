package com.callerid.number.lookup.home.screen.consent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.TipSheetActivity
import com.callerid.number.lookup.home.kit.InstallIdRegistry
import com.callerid.number.lookup.home.runtime.incoming.IdentOverlayService
import com.callerid.number.lookup.home.runtime.incoming.PhoneStateReceiver
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher

object OverlayKit {

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Master switch for asking the user for "display over other apps".
     *
     * **On**, so the prompt reaches the users who actually need it. It was off while the app
     * assumed it would hold a default role — the post-call screen reaches those users through
     * the role's background-start exemption (see [PhoneStateReceiver.handlePostCall]) or a
     * full-screen-intent notification, which made the prompt look like it was buying nothing.
     * It buys everything for a user who sets no default: without the role *and* without the
     * overlay there is no background-activity start at all, and [IdentOverlayService] gives up
     * on the caller-ID card entirely.
     *
     * Which user gets asked is [isOfferable]'s job, and its default-launcher gate is what
     * keeps this from asking role holders for something they do not need. Set this back to
     * `false` to switch every overlay prompt off again.
     */
    private const val ASK_FOR_OVERLAY = true

    /**
     * True when the overlay permission may still be *offered* to this user.
     *
     * [ASK_FOR_OVERLAY] switches the whole thing off. While it is on, one gate decides:
     * **are we the default launcher?**
     *
     * Holding `ROLE_HOME` is itself a background-activity-start exemption, so the caller-ID
     * screens already start through it (see [InstallIdRegistry.holdsSystemDefaultRole]) and
     * asking for "display over other apps" on top buys nothing. Checked live, because the role
     * can be granted mid-session by the set-as-default onboarding step. The trade-off is
     * deliberate: the ringing-time card genuinely needs a WindowManager overlay, so on an
     * unlocked phone a launcher user gets the full-screen card instead.
     *
     * Everyone else is asked — a user who sets no default has neither route, so without the
     * overlay there is no caller-ID card at all. The IP-location `CountryList_Counter_NShow`
     * list deliberately does NOT gate this: it exists to keep the HD_VBC house surfaces quiet
     * in a region, and taking the caller-ID card away from every non-launcher user there was a
     * side effect, not the intent. It still gates those surfaces through
     * [PromoVault.isNShowLocation]; it just no longer decides who may be asked for the overlay.
     *
     * Every surface that *asks* for the overlay honours this — the permission sheet row,
     * Home's Enable banner, the Terms step. It says nothing about a permission the user has
     * already granted: the caller-ID card keeps working for them, because this gates the
     * prompt, not the feature.
     */
    fun isOfferable(context: Context): Boolean {
        if (!ASK_FOR_OVERLAY) return false
        return !runCatching { context.isDefaultLauncher() }.getOrDefault(false)
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
