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
        OnboardScreen.WELCOME,
        OnboardScreen.SET_DEFAULT,
        OnboardScreen.INTRO,
        OnboardScreen.LANGUAGE,
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
