package com.callerid.admesh.engine

import android.app.Activity
import android.content.Context
import android.util.Log
import com.callerid.admesh.engine.ShellPromoConfig.DrawerAdFlow
import com.callerid.admesh.engine.ShellPromoConfig.DrawerAdSpec
import com.callerid.admesh.engine.ShellPromoConfig.DrawerAdType
import com.callerid.admesh.surface.DrawerAdRunner
import com.callerid.number.lookup.home.BuildConfig
import org.json.JSONArray

/**
 * The launcher's full-screen ad placements, configured with QRScanner's keys so one Remote Config
 * shape serves both apps. Every key resolves `<placement>_<key>` first, then the global `<key>`:
 *
 * | key                                | meaning                                                   |
 * |------------------------------------|-----------------------------------------------------------|
 * | `<p>_ads_on`                       | `false` mutes the placement (default on)                  |
 * | `googleInter` / `googleFullNative` | the unit for that format (`googleFullNative` → `googleNative`) |
 * | `googleRewarded` / `googleAppopen` | units for the link-first follow-ups                       |
 * | `DirectLink`                       | one URL or a JSON array of URLs                           |
 * | `link_first_then`                  | e.g. `"reward,inter,app_open"`: open the DirectLink first, then these once it closes. Needs `IsCustomADS` |
 * | `link_first_show_all`              | `true` shows every follow-up that is ready; default is a waterfall (first that shows ends it) |
 * | `link_open_in`                     | `webview` / `custom_tab` / `browser`                      |
 * | `inter_fallback`                   | `full_native`: an interstitial that cannot show falls back to a full-screen native |
 *
 * Placements (the launcher's gesture names are mapped the way QRScanner maps them):
 * `leftSwipe` → `leftPanel`, `rightSwipe` → `rightPanel`, `appLaunch` → `drawer`.
 *
 * Everything runs through [DrawerAdRunner], which owns per-unit loading and guarantees its
 * `proceed` runs exactly once — a gesture is never left stuck behind an ad.
 *
 * ```
 * adb logcat -s LauncherPlacementAds DrawerAdRunner
 * ```
 */
object LauncherPlacementAds {

    private const val TAG = "LauncherPlacementAds"

    private val PLACEMENTS = listOf("leftPanel", "rightPanel", "drawer")

    private val GLOBAL_KEYS = setOf(
        "link_first_then", "link_first_show_all", "link_open_in", "inter_fallback", "RewardedAds",
        "googleFullNative",
    )

    /** Whether [key] is one [PromoConfigLoader] must ingest for this class. */
    fun isPlacementKey(key: String): Boolean =
        key in GLOBAL_KEYS || PLACEMENTS.any { key.startsWith("${it}_") }

    private fun normalize(placement: String?): String? = when (placement) {
        "leftSwipe", "leftPanel" -> "leftPanel"
        "rightSwipe", "rightPanel" -> "rightPanel"
        "appLaunch", "drawerOpen", "drawer" -> "drawer"
        else -> placement
    }

    private fun resolve(vault: PromoVault, placement: String?, key: String): String {
        val name = normalize(placement)
        if (!name.isNullOrBlank()) {
            vault.getString("${name}_$key")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return vault.getString(key).orEmpty()
    }

    private fun resolveFullNative(vault: PromoVault, placement: String?): String =
        resolve(vault, placement, "googleFullNative").ifBlank { vault.getString("googleNative").orEmpty() }

    fun placementEnabled(context: Context, placement: String?): Boolean {
        val name = normalize(placement) ?: return true
        return PromoVault.getInstance(context).getString("${name}_ads_on")
            ?.trim()?.lowercase() != "false"
    }

    private fun directLinks(vault: PromoVault, placement: String?): List<String> {
        val raw = resolve(vault, placement, "DirectLink").trim()
        if (raw.isEmpty()) return emptyList()
        if (!raw.startsWith("[")) return listOf(raw)
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    /** The follow-up names QRScanner uses, as runner formats. Unknown names are dropped. */
    private fun followUp(vault: PromoVault, placement: String?, step: String): DrawerAdSpec? =
        when (step.trim().lowercase()) {
            "reward", "rewarded" ->
                if (vault.getString("RewardedAds")?.trim()?.lowercase() == "false") null
                else DrawerAdSpec(DrawerAdType.REWARDED, resolve(vault, placement, "googleRewarded"))
            "inter", "interstitial" -> DrawerAdSpec(DrawerAdType.INTER, resolve(vault, placement, "googleInter"))
            "app_open", "appopen" -> DrawerAdSpec(DrawerAdType.APPOPEN, resolve(vault, placement, "googleAppopen"))
            "full_native", "fullscreen_native" ->
                DrawerAdSpec(DrawerAdType.FULLSCREEN_NATIVE, resolveFullNative(vault, placement))
            else -> null
        }?.takeIf { it.adUnitId.isNotBlank() }

    /** The link-first chain for [placement], or null when it is off (no order, no links, or no `IsCustomADS`). */
    private fun linkFirst(vault: PromoVault, placement: String?): Pair<DrawerAdSpec, DrawerAdFlow>? {
        val order = resolve(vault, placement, "link_first_then")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val links = directLinks(vault, placement)
        if (order.isEmpty() || links.isEmpty() || !vault.getBoolean("IsCustomADS")) return null
        val link = DrawerAdSpec(
            type = DrawerAdType.DIRECTLINK,
            adUnitId = links.first(),
            urls = links,
            openType = resolve(vault, placement, "link_open_in"),
        )
        val showAll = resolve(vault, placement, "link_first_show_all").trim().toBoolean()
        val then = DrawerAdFlow(
            enabled = true,
            counter = 0,
            sequence = order.mapNotNull { followUp(vault, placement, it) },
            showAll = showAll,
            startFromFirst = true,
        )
        return link to then
    }

    /** The plain chain: the placement's interstitial, then a full-screen native when `inter_fallback` asks. */
    private fun interFlow(vault: PromoVault, placement: String?): DrawerAdFlow {
        val sequence = buildList {
            resolve(vault, placement, "googleInter").takeIf { it.isNotBlank() }
                ?.let { add(DrawerAdSpec(DrawerAdType.INTER, it)) }
            if (resolve(vault, placement, "inter_fallback").trim().lowercase() == "full_native") {
                resolveFullNative(vault, placement).takeIf { it.isNotBlank() }
                    ?.let { add(DrawerAdSpec(DrawerAdType.FULLSCREEN_NATIVE, it)) }
            }
        }
        return DrawerAdFlow(true, 0, sequence, showAll = false, startFromFirst = true)
    }

    /** App launch: a full-screen native, falling back to the placement's interstitial. */
    private fun fullNativeFlow(vault: PromoVault, placement: String?): DrawerAdFlow {
        val sequence = buildList {
            resolveFullNative(vault, placement).takeIf { it.isNotBlank() }
                ?.let { add(DrawerAdSpec(DrawerAdType.FULLSCREEN_NATIVE, it)) }
            resolve(vault, placement, "googleInter").takeIf { it.isNotBlank() }
                ?.let { add(DrawerAdSpec(DrawerAdType.INTER, it)) }
        }
        return DrawerAdFlow(true, 0, sequence, showAll = false, startFromFirst = true)
    }

    /**
     * Whether [placement] wants the runner at all. False means the caller keeps its existing path
     * (the app-wide preloaded interstitial): no link-first chain, and no unit of its own.
     */
    fun hasOwnInter(context: Context, placement: String?): Boolean {
        val vault = PromoVault.getInstance(context)
        if (linkFirst(vault, placement) != null) return true
        val unit = resolve(vault, placement, "googleInter")
        return (unit.isNotBlank() && unit != vault.getString("googleInter").orEmpty()) ||
            resolve(vault, placement, "inter_fallback").trim().lowercase() == "full_native"
    }

    /** Loads every format the three placements may need, so the first gesture has something to show. */
    fun preload(context: Context) {
        val vault = PromoVault.getInstance(context)
        if (!vault.getBoolean("IsAdsON")) return
        listOf("leftSwipe", "rightSwipe", "appLaunch").filter { placementEnabled(context, it) }.forEach { p ->
            linkFirst(vault, p)?.let { DrawerAdRunner.preload(context, it.second) }
            DrawerAdRunner.preload(context, if (p == "appLaunch") fullNativeFlow(vault, p) else interFlow(vault, p))
        }
    }

    /** The interstitial placement: link-first when configured, else the placement's own interstitial. */
    fun showInterstitial(activity: Activity, placement: String?, proceed: () -> Unit) {
        val vault = PromoVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !placementEnabled(activity, placement)) return proceed()

        val chain = linkFirst(vault, placement)
        if (chain != null) {
            val (link, then) = chain
            log("$placement: link-first → ${link.urls.size} link(s), then ${then.sequence.map { it.type.key }}")
            // Suppress the global App Open on the return from the link: the follow-up chosen here is the ad.
            com.callerid.admesh.surface.OpenPromoRegistry.skipNextAppOpenAd = true
            DrawerAdRunner.run(activity, DrawerAdFlow(true, 0, listOf(link), startFromFirst = true), pointerKey(placement)) {
                // Consumed by the foreground hook on a real return; cleared here for a link that never opened.
                com.callerid.admesh.surface.OpenPromoRegistry.skipNextAppOpenAd = false
                if (then.sequence.isEmpty()) proceed()
                else DrawerAdRunner.run(activity, then, pointerKey(placement), proceed)
            }
            return
        }

        val flow = interFlow(vault, placement)
        log("$placement: inter chain ${flow.sequence.map { "${it.type.key}(${it.adUnitId})" }}")
        DrawerAdRunner.run(activity, flow, pointerKey(placement), proceed)
    }

    /** The app-launch placement: a full-screen native (QRScanner's choice for leaving to another app). */
    fun showFullNative(activity: Activity, placement: String?, proceed: () -> Unit) {
        val vault = PromoVault.getInstance(activity)
        if (!vault.getBoolean("IsAdsON") || !placementEnabled(activity, placement)) return proceed()
        val flow = fullNativeFlow(vault, placement)
        log("$placement: full-native chain ${flow.sequence.map { "${it.type.key}(${it.adUnitId})" }}")
        DrawerAdRunner.run(activity, flow, pointerKey(placement), proceed)
    }

    private fun pointerKey(placement: String?) = "__launcher_placement_ptr_${normalize(placement)}"

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
