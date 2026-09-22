package com.callerid.admesh.engine

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.callerid.admesh.surface.StripWatcher
import com.callerid.admesh.surface.StripKind
import com.callerid.admesh.surface.StripPromo
import com.callerid.admesh.surface.StripScale
import com.callerid.admesh.surface.DrawerAdRunner
import com.callerid.admesh.surface.InlinePromo
import com.callerid.admesh.surface.InlinePromoStrip
import com.callerid.admesh.surface.interstitial.FlowInterstitial
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.shell.ext.isDefaultLauncher
import org.json.JSONObject

object ShellPromoConfig {

    private const val TAG = "ShellPromoConfig"
    private const val CONFIG_KEY = "launcher_ads"

    private const val VARIANT_DEFAULT_HOME = "defaultHome"
    private const val VARIANT_NOT_DEFAULT_HOME = "notDefaultHome"

    enum class Surface(val block: String, val counterKey: String) {
        APP_CLICK("app_click", "__launcher_ads_app_click_count"),
        SWIPE_RIGHT("swipe_right", "__launcher_ads_swipe_right_count"),
        SWIPE_LEFT("swipe_left", "__launcher_ads_swipe_left_count"),
    }

    enum class GestureAd { INTER, LINK, NONE }

    data class Rule(
        val enabled: Boolean,
        val adType: GestureAd,
        val adsCounter: Int,
        val fallbackLinkEnabled: Boolean,
        val fallbackLink: String,
    ) {
        val canShowInter: Boolean get() = adType == GestureAd.INTER
        val canOpenLink: Boolean
            get() = adType == GestureAd.LINK && fallbackLinkEnabled && fallbackLink.isNotBlank()
    }

    private val DISABLED = Rule(
        enabled = false,
        adType = GestureAd.NONE,
        adsCounter = 0,
        fallbackLinkEnabled = false,
        fallbackLink = "",
    )

    fun rule(context: Context, surface: Surface): Rule {
        val block = config(context).optJSONObject(surface.block) ?: return DISABLED

        val linkEnabled = block.optBoolean("fallback_link_enabled", false)
        val link = block.optString("fallback_link", "")
        val interEnabled = block.optBoolean("inter_enabled", false)

        val adType = when (block.optString("ad_type").trim().lowercase()) {
            "inter" -> GestureAd.INTER
            "link" -> GestureAd.LINK
            "none" -> GestureAd.NONE
            else -> when {
                interEnabled -> GestureAd.INTER
                linkEnabled && link.isNotBlank() -> GestureAd.LINK
                else -> GestureAd.NONE
            }
        }

        return Rule(
            enabled = block.optBoolean("enabled", false),
            adType = adType,
            adsCounter = block.optInt("ads_counter", 0),
            fallbackLinkEnabled = linkEnabled,
            fallbackLink = link,
        )
    }

    fun run(activity: Activity, surface: Surface, proceed: () -> Unit) {
        val rule = rule(activity, surface)

        if (!rule.enabled) {
            log("${surface.block}: disabled")
            return proceed()
        }

        if (!rule.canShowInter && !rule.canOpenLink) {
            log("${surface.block}: nothing enabled (ad_type=${rule.adType})")
            return proceed()
        }

        if (!isDue(activity, surface.counterKey, rule.adsCounter, surface.block)) {
            return proceed()
        }

        if (rule.canShowInter) {
            log("${surface.block}: showing interstitial")
            FlowInterstitial().renderInterstitial(activity) { proceed() }
            return
        }

        log("${surface.block}: ad_type=link, opening fallback link")
        openLink(activity, rule.fallbackLink)
        proceed()
    }

    enum class SlotAd { NATIVE, BANNER, NONE }

    data class Slot(
        val enabled: Boolean,
        val adType: SlotAd,
        val nativeType: String,
        val bannerType: String,
        val adUnitId: String,

        val position: Int = 0,
    ) {
        val visible: Boolean get() = enabled && adType != SlotAd.NONE

        val needsNativePreload: Boolean get() = visible && adType == SlotAd.NATIVE
    }

    fun sidePanelSlot(context: Context): Slot = slot(
        block = config(context).optJSONObject("right_panel")?.optJSONObject("bottom_native"),
        defaultNativeType = "mid2",
        label = "right_panel.bottom_native",
    )

    fun sidePanelSuggestedSlot(context: Context): Slot {
        val block = config(context).optJSONObject("right_panel")?.optJSONObject("suggested_banner")
            ?: return Slot(false, SlotAd.NONE, "native_banner", "adaptive", "")

        return slot(block, defaultNativeType = "native_banner", label = "right_panel.suggested_banner")
    }

    fun drawerSlot(context: Context): Slot {
        val block = config(context).optJSONObject("app_drawer")?.optJSONObject("bottom_native")
            ?: return Slot(false, SlotAd.NONE, "mid2", "adaptive", "")

        return slot(block, defaultNativeType = "mid2", label = "app_drawer.bottom_native")
    }

    /** What the install / uninstall result screen may do, from `package_result` in Remote Config. */
    data class PackageResultSettings(
        val enabled: Boolean,
        val minGapMs: Long,
        val bodyNative: Boolean,
    )

    /**
     * `package_result: { "enabled": true, "min_gap_ms": 0, "body_native": true }`.
     *
     * `enabled` turns the whole screen off without a release; `min_gap_ms` throttles it so a burst
     * of package events cannot show it repeatedly; `body_native` carries its in-card native ad.
     */
    fun packageResultSettings(context: Context): PackageResultSettings {
        val block = config(context).optJSONObject("package_result")
        return PackageResultSettings(
            enabled = block?.optBoolean("enabled", true) ?: true,
            minGapMs = block?.optLong("min_gap_ms", 0L) ?: 0L,
            bodyNative = block?.optBoolean("body_native", true) ?: true,
        ).also { log("package_result → $it") }
    }

    /** One configurable promo tile in the app drawer: an icon + title that opens [link] when tapped. */
    data class DrawerPromo(
        val position: Int,
        val title: String,
        val icon: String,
        val link: String,
    )

    /**
     * The drawer's promo tiles, from `app_drawer.promo` in Remote Config:
     * `{ "enabled": true, "items": [ { position, title, icon, link, enabled } ] }`. Only enabled
     * items with both an icon and a link survive; each is placed at its own `position` in the list.
     * `landing_url` is accepted as an alias for `link`. An absent or disabled block yields nothing.
     */
    fun drawerPromoItems(context: Context): List<DrawerPromo> {
        val block = config(context).optJSONObject("app_drawer")?.optJSONObject("promo")
            ?: return emptyList()
        if (!block.optBoolean("enabled", true)) return emptyList()
        val items = block.optJSONArray("items") ?: return emptyList()

        val out = ArrayList<DrawerPromo>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (!item.optBoolean("enabled", true)) continue
            // Accept the reference field names as aliases: logo→icon, label→title, landing_url→link.
            val icon = item.optString("icon").ifBlank { item.optString("logo") }.trim()
            val link = item.optString("link").ifBlank { item.optString("landing_url") }.trim()
            val title = item.optString("title").ifBlank { item.optString("label") }.trim()
            if (icon.isBlank() || link.isBlank()) continue
            out += DrawerPromo(
                position = item.optInt("position", 0).coerceAtLeast(0),
                title = title,
                icon = icon,
                link = link,
            )
        }
        log("app_drawer.promo → ${out.size} item(s)")
        return out
    }

    // ---------- App-drawer ad sequence (Remote-Config-driven fallback chain) ----------

    /**
     * The ad formats the app-drawer sequence can play. `idFallbackKey` is the legacy PromoVault key
     * an ad-unit-id (or URL, for [DIRECTLINK]) falls back to when the config leaves `ad_unit_id`
     * blank, so existing global ids keep working without being duplicated into the new block.
     */
    enum class DrawerAdType(val key: String, val idFallbackKey: String) {
        INTER("inter", "googleInter"),
        APPOPEN("appopen", "googleAppopen"),
        DIRECTLINK("directlink", "DirectLink"),
        REWARDED("rewarded", "googleRewarded"),
        FULLSCREEN_NATIVE("fullscreen_native", "googleNative");

        companion object {
            fun from(raw: String?): DrawerAdType? =
                entries.firstOrNull { it.key == raw?.trim()?.lowercase() }
        }
    }

    /** One resolved ad in the sequence: its format and the ad-unit-id (or URL, for directlink). */
    data class DrawerAdSpec(val type: DrawerAdType, val adUnitId: String)

    /** The whole app-drawer ad flow: whether it runs, how often, and the resolved fallback chain. */
    data class DrawerAdFlow(
        val enabled: Boolean,
        val counter: Int,
        val sequence: List<DrawerAdSpec>,
    )

    private val DEFAULT_DRAWER_SEQUENCE = listOf(
        DrawerAdType.INTER,
        DrawerAdType.APPOPEN,
        DrawerAdType.DIRECTLINK,
        DrawerAdType.REWARDED,
        DrawerAdType.FULLSCREEN_NATIVE,
    )

    private const val DRAWER_COUNTER_KEY = "__launcher_ads_app_drawer_count"
    private const val DRAWER_POINTER_KEY = "__launcher_ads_app_drawer_seq_ptr"

    /**
     * Parses `launcher_ads.app_drawer` into the ad flow. Every field is optional and defaults
     * safely: a missing block, or `enabled=false`, yields a flow that shows nothing. `sequence`
     * sets the fallback priority (unknown names dropped, duplicates collapsed, an empty/omitted
     * list → the default order); each entry in `ads.<type>` may be disabled or carry its own
     * `ad_unit_id`, and a type with no id (neither configured nor a legacy fallback key) is dropped
     * from the chain. `bottom_native` and `promo` are read elsewhere and untouched by this.
     */
    fun drawerAdFlow(context: Context): DrawerAdFlow {
        val block = config(context).optJSONObject("app_drawer")
            ?: return DrawerAdFlow(false, 0, emptyList())

        val enabled = block.optBoolean("enabled", false)
        val counter = block.optInt("ad_counter", 0).coerceAtLeast(0)
        val ads = block.optJSONObject("ads")

        val listed = block.optJSONArray("sequence")
        val order = if (listed == null) DEFAULT_DRAWER_SEQUENCE else
            (0 until listed.length()).mapNotNull { DrawerAdType.from(listed.optString(it)) }
                .ifEmpty { DEFAULT_DRAWER_SEQUENCE }

        val seen = LinkedHashSet<DrawerAdType>()
        val specs = ArrayList<DrawerAdSpec>(order.size)
        order.forEach { type ->
            if (!seen.add(type)) return@forEach
            val ad = ads?.optJSONObject(type.key)
            if (ad != null && !ad.optBoolean("enabled", true)) return@forEach
            val id = resolveDrawerAdId(context, type, ad)
            if (id.isBlank()) return@forEach
            specs += DrawerAdSpec(type, id)
        }

        return DrawerAdFlow(enabled, counter, specs).also {
            log("app_drawer ad flow → enabled=$enabled counter=$counter seq=${specs.map { s -> s.type.key }}")
        }
    }

    private fun resolveDrawerAdId(context: Context, type: DrawerAdType, ad: JSONObject?): String {
        val configured = ad?.optString("ad_unit_id", "").orEmpty().trim()
        if (configured.isNotBlank()) return configured
        return PromoVault.getInstance(context).getString(type.idFallbackKey).orEmpty().trim()
    }

    /** Preload the sequence's ad formats so they are ready by the time an app is tapped. */
    fun preloadDrawerAds(context: Context) {
        if (!PromoVault.getInstance(context).getBoolean("IsAdsON")) return
        val flow = drawerAdFlow(context)
        if (!flow.enabled || flow.sequence.isEmpty()) return
        DrawerAdRunner.preload(context, flow)
    }

    /**
     * The app-drawer app-tap gate: every `ad_counter`-th tap, walk the configured sequence and show
     * the first ready ad, then continue to [proceed]; skip any ad that is disabled, has no id, is
     * not loaded, or fails to show; if none can show (or ads are off, or the flow is disabled),
     * continue immediately. The sequence resumes from where it last showed and restarts after the
     * last item, so the pointer resets correctly once the options are exhausted.
     */
    fun runDrawerAdFlow(activity: Activity, proceed: () -> Unit) {
        if (!PromoVault.getInstance(activity).getBoolean("IsAdsON")) return proceed()
        val flow = drawerAdFlow(activity)
        if (!flow.enabled || flow.sequence.isEmpty()) {
            log("app_drawer: flow off — proceeding")
            return proceed()
        }
        if (!isDue(activity, DRAWER_COUNTER_KEY, flow.counter, "app_drawer")) return proceed()

        DrawerAdRunner.run(activity, flow, DRAWER_POINTER_KEY, proceed)
    }

    fun renderSlot(
        activity: Activity,
        slot: Slot,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (!slot.visible) {
            log("slot hidden (enabled=${slot.enabled}, ad_type=${slot.adType})")
            container.removeAllViews()
            container.visibility = View.GONE
            shimmer?.stopShimmer()
            shimmer?.visibility = View.GONE
            return
        }

        if (slot.adType == SlotAd.BANNER) {
            val (size, collapsible) = when (slot.bannerType.lowercase()) {
                "inline" -> StripScale.INLINE to false
                "normal" -> StripScale.NORMAL to false
                "collapsible" -> StripScale.ADAPTIVE to true
                else -> StripScale.ADAPTIVE to false
            }
            log("slot: banner type=${slot.bannerType} collapsible=$collapsible")

            StripPromo().renderBanner(
                activity = activity,
                container = container,
                type = StripKind.AUTO,
                size = size,
                isCollapsable = collapsible,
                shimmer = shimmer,
                customAdUnitId = slot.adUnitId.takeIf { it.isNotBlank() },
                disableInternalFallback = true,
                observer = object : StripWatcher {
                    override fun onAdFailed() {
                        log("slot: banner failed, falling back to native banner")
                        InlinePromoStrip().renderNativeBanner(activity, container, shimmer)
                    }
                }
            )
            return
        }

        log("slot: native type=${slot.nativeType}")
        when (slot.nativeType.lowercase()) {
            "big" -> InlinePromo().renderBigNative(activity, container, shimmer)
            "mid" -> InlinePromo().renderMidNative(activity, container, shimmer)
            "native_banner" -> InlinePromoStrip().renderNativeBanner(activity, container, shimmer)
            else -> InlinePromo().renderMidNative2(activity, container, shimmer)
        }
    }

    /**
     * [renderSlot] with a refresh policy, for the surfaces that are opened over and over — the
     * app drawer and the left panel.
     *
     * Re-showing unconditionally is what made those two look broken. The native pool is a
     * SINGLE static ad: rendering it consumes the ad and starts a refill, so a second open
     * before that refill lands finds nothing, drops through to the fallback branch, and
     * replaces a perfectly good ad with a custom one. The left panel hit it every single
     * time — it asks for two native frames back to back, and the second could never win.
     *
     * So: fill an empty frame exactly as before, replace a filled one only when a fresh
     * native is actually in hand, and otherwise leave what is on screen and warm the next.
     * Banner slots are untouched — they own their own refresh.
     */
    fun refreshSlot(
        activity: Activity,
        slot: Slot,
        container: FrameLayout,
        shimmer: ShimmerFrameLayout? = null,
    ) {
        
        val holdsAnAd = (0 until container.childCount).any { container.getChildAt(it) !== shimmer }
        if (!slot.needsNativePreload || !holdsAnAd || InlinePromo.hasPreloadedNative()) {
            renderSlot(activity, slot, container, shimmer)
            return
        }

        log("slot: no fresh native — keeping the one on screen, warming the next")
        InlinePromo().fetchNativeAds(activity)
    }

    private fun slot(block: JSONObject?, defaultNativeType: String, label: String): Slot {
        if (block == null) {

            return Slot(true, SlotAd.NATIVE, defaultNativeType, "adaptive", "")
        }

        val adType = when (block.optString("ad_type").trim().lowercase()) {
            "banner" -> SlotAd.BANNER
            "none" -> SlotAd.NONE
            else -> SlotAd.NATIVE
        }

        return Slot(
            enabled = block.optBoolean("enabled", true),
            adType = adType,
            nativeType = block.optString("native_type").ifBlank { defaultNativeType },
            bannerType = block.optString("banner_type").ifBlank { "adaptive" },
            adUnitId = block.optString("ad_unit_id", ""),

            position = block.optInt("position", 0).coerceAtLeast(0),
        ).also { log("$label → $it") }
    }

    enum class HintDirection(val key: String) {
        RIGHT("right"), LEFT("left"), UP("up"), DOWN("down");

        companion object {
            fun from(raw: String?): HintDirection? =
                entries.firstOrNull { it.key == raw?.trim()?.lowercase() }
        }
    }

    enum class HintMode { ONCE, ALWAYS, APP_LAUNCHES }

    data class HomeHint(
        val enabled: Boolean,
        val directions: List<HintDirection>,
        val mode: HintMode,
        val interval: Int,
        val autoHideSec: Int,
    ) {
        val visible: Boolean get() = enabled && directions.isNotEmpty()
    }

    private val DEFAULT_HINT = HomeHint(
        enabled = true,
        directions = listOf(HintDirection.RIGHT),
        mode = HintMode.ONCE,
        interval = 0,
        autoHideSec = 0,
    )

    fun boardHint(context: Context): HomeHint {
        val block = config(context).optJSONObject("home_hint") ?: return DEFAULT_HINT

        val listed = block.optJSONArray("swipeHints")
        val directions = when {
            listed == null -> DEFAULT_HINT.directions

            else -> (0 until listed.length()).mapNotNull { HintDirection.from(listed.optString(it)) }
        }

        return HomeHint(
            enabled = block.optBoolean("enabled", true),
            directions = directions,
            mode = when (block.optString("show_mode").trim().lowercase()) {
                "always" -> HintMode.ALWAYS
                "app_launches" -> HintMode.APP_LAUNCHES
                else -> HintMode.ONCE
            },
            interval = block.optInt("interval", 0),
            autoHideSec = block.optInt("auto_hide_sec", 0),
        ).also { log("home_hint → $it") }
    }

    fun hintDue(context: Context, hint: HomeHint): Boolean =
        isDue(context, HINT_COUNTER_KEY, hint.interval, "home_hint")

    private const val HINT_COUNTER_KEY = "__launcher_ads_home_hint_count"

    enum class OnboardScreen(
        val key: String,
        private val interByDefault: Boolean,
        private val nativeByDefault: String,
    ) {
        WELCOME("welcome", false, "mid"),
        SET_DEFAULT("set_default", false, "mid"),
        INTRO("intro", true, "mid2"),
        LANGUAGE("language", true, "big");

        val counterKey: String get() = "__launcher_ads_onboarding_${key}_count"

        internal fun defaults(): Pair<Boolean, String> = interByDefault to nativeByDefault

        companion object {
            fun from(raw: String?): OnboardScreen? =
                entries.firstOrNull { it.key == raw?.trim()?.lowercase() }
        }
    }

    fun onboardSlot(context: Context, screen: OnboardScreen): Slot = slot(
        block = onboardingBlock(context, screen)?.optJSONObject("slot"),
        defaultNativeType = screen.defaults().second,
        label = "onboarding.${screen.key}.slot",
    )

    data class ScreenUi(val skipEnabled: Boolean, val backAdvances: Boolean)

    fun onboardUi(context: Context, screen: OnboardScreen): ScreenUi {
        val block = onboardingBlock(context, screen)
        return ScreenUi(
            skipEnabled = block?.optBoolean("skip_enabled", true) ?: true,
            backAdvances = block?.optString("back_action")
                ?.trim()?.lowercase() == "next_screen",
        ).also { log("onboarding.${screen.key}.ui → $it") }
    }

    fun runOnboardInterstitial(activity: Activity, screen: OnboardScreen, proceed: () -> Unit) {
        val block = onboardingBlock(activity, screen)
        val enabled = block?.optBoolean("inter_enabled", screen.defaults().first)
            ?: screen.defaults().first

        if (!enabled) {
            log("onboarding.${screen.key}: interstitial off")
            return proceed()
        }

        val counter = block?.optInt("ads_counter", 0) ?: 0
        if (!isDue(activity, screen.counterKey, counter, "onboarding.${screen.key}")) {
            return proceed()
        }

        log("onboarding.${screen.key}: showing interstitial")
        FlowInterstitial().renderInterstitial(activity) { proceed() }
    }

    fun onboardOrder(context: Context): List<OnboardScreen> {
        val listed = config(context).optJSONObject("onboarding")?.optJSONArray("order")
            ?: return DEFAULT_ORDER

        val order = (0 until listed.length()).mapNotNull { OnboardScreen.from(listed.optString(it)) }
        return order.ifEmpty { DEFAULT_ORDER }.also { log("onboarding.order → ${it.map(OnboardScreen::key)}") }
    }

    private val DEFAULT_ORDER = listOf(
        OnboardScreen.SET_DEFAULT,
        OnboardScreen.LANGUAGE,
        OnboardScreen.WELCOME,
        OnboardScreen.INTRO,
    )

    private fun onboardingBlock(context: Context, screen: OnboardScreen): JSONObject? =
        config(context).optJSONObject("onboarding")?.optJSONObject(screen.key)

    data class DefaultHomeStep(
        val enabled: Boolean,
        val skipIfDefault: Boolean,
        val skipRestOnGrant: Boolean,
    )

    fun defaultBoardStep(context: Context): DefaultHomeStep {
        val block = config(context).optJSONObject("default_home_screen")
        return DefaultHomeStep(
            enabled = block?.optBoolean("enabled", true) ?: true,
            skipIfDefault = block?.optBoolean("skip_if_default", true) ?: true,
            skipRestOnGrant = block?.optBoolean("skip_rest_on_grant", true) ?: true,
        )
    }

    @Volatile
    private var cache: Pair<String, JSONObject>? = null

    private fun config(context: Context): JSONObject {
        val raw = PromoVault.getInstance(context).getString(CONFIG_KEY, "").orEmpty()
        if (raw.isBlank()) return JSONObject()

        val isDefaultHome = runCatching { context.isDefaultLauncher() }.getOrDefault(false)
        val cacheKey = "${isDefaultHome}|$raw"
        cache?.let { (key, value) -> if (key == cacheKey) return value }

        val base = runCatching { JSONObject(raw) }.getOrNull() ?: return JSONObject()
        val variantKey = if (isDefaultHome) VARIANT_DEFAULT_HOME else VARIANT_NOT_DEFAULT_HOME
        val variant = base.optJSONObject(variantKey)

        val merged = runCatching { JSONObject(base.toString()) }.getOrNull() ?: JSONObject()
        merged.remove(VARIANT_DEFAULT_HOME)
        merged.remove(VARIANT_NOT_DEFAULT_HOME)
        if (variant != null) deepMerge(merged, variant)

        log("config resolved with $variantKey (${variant?.length() ?: 0} override(s))")
        cache = cacheKey to merged
        return merged
    }

    private fun deepMerge(base: JSONObject, overlay: JSONObject) {
        overlay.keys().forEach { key ->
            val overlayValue = overlay.opt(key)
            val baseValue = base.opt(key)
            if (overlayValue is JSONObject && baseValue is JSONObject) {
                deepMerge(baseValue, overlayValue)
            } else {
                base.put(key, overlayValue)
            }
        }
    }

    private fun isDue(context: Context, counterKey: String, target: Int, label: String): Boolean {
        if (target <= 0) return true

        val pref = PromoVault.getInstance(context)
        val seen = pref.getInt(counterKey, 0)

        return if (seen < target) {
            pref.putInt(counterKey, seen + 1)
            log("$label: counter ${seen + 1}/$target, skipping")
            false
        } else {
            pref.putInt(counterKey, 0)
            true
        }
    }

    private fun openLink(activity: Activity, url: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            log("no browser for $url")
        } catch (e: Exception) {
            log("fallback link failed: ${e.message}")
        }
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
