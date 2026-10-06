package com.callerid.admesh.surface

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.callerid.admesh.engine.PromoVault
import com.callerid.number.lookup.home.R
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.Locale
import kotlin.math.floor

object NativeAdLook {

    fun bind(adView: NativeAdView, nativeAd: NativeAd) {
        applyCardSpacing(adView)
        applyRemoteColors(adView)
        bindStars(adView, nativeAd)
        adView.findViewById<TextView?>(R.id.ad_call_to_action)?.let { cta ->
            if (cta.tag == "glow") glow(cta)
        }
    }

    // Ads are inflated without a parent, so the layout root margins are dropped; reapply them here.
    private fun applyCardSpacing(adView: NativeAdView) {
        val res = adView.resources
        val h = res.getDimensionPixelSize(R.dimen.ad_card_margin_h)
        val top = res.getDimensionPixelSize(R.dimen.ad_card_margin_top)
        val bottom = res.getDimensionPixelSize(R.dimen.ad_card_margin_bottom)
        val params = (adView.layoutParams as? ViewGroup.MarginLayoutParams)
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        params.setMargins(h, top, h, bottom)
        adView.layoutParams = params
        adView.elevation = res.getDimension(R.dimen.ad_card_elevation)
        adView.post { fitSideMargins(adView, h) }
    }

    private fun fitSideMargins(adView: NativeAdView, target: Int) {
        val parent = adView.parent as? View ?: return
        val root = adView.rootView
        val windowWidth = root.width
        if (parent.width <= 0 || windowWidth <= 0) return
        // Layout position, not window location: side panels may still be sliding in (translationX).
        var x = 0
        var v: View? = parent
        while (v != null && v !== root) {
            x += v.left
            v = v.parent as? View
        }
        val insetLeft = x.coerceAtLeast(0)
        val insetRight = (windowWidth - x - parent.width).coerceAtLeast(0)
        val params = adView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val left = (target - insetLeft).coerceAtLeast(0)
        val right = (target - insetRight).coerceAtLeast(0)
        if (params.leftMargin == left && params.rightMargin == right) return
        params.leftMargin = left
        params.rightMargin = right
        adView.layoutParams = params
    }

    private fun applyRemoteColors(adView: NativeAdView) {
        val pref = PromoVault.getInstance(adView.context)
        fun rc(key: String): Int? = pref.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try { Color.parseColor(it) } catch (_: IllegalArgumentException) { null }
        }
        rc("NativeBgColor")?.let { fill(adView, it) }
        rc("NativetxtColor")?.let { txt ->
            adView.findViewById<TextView?>(R.id.ad_headline)?.setTextColor(txt)
            adView.findViewById<TextView?>(R.id.ad_body)?.setTextColor(txt)
        }
        adView.findViewById<TextView?>(R.id.ad_call_to_action)?.let { cta ->
            rc("NativebtnColor")?.let { fill(cta, it) }
            rc("NativebtntxtColor")?.let { btnTxt ->
                cta.setTextColor(btnTxt)
                cta.compoundDrawablesRelative.forEach { d -> d?.mutate()?.setTint(btnTxt) }
            }
        }
    }

    private fun fill(v: View, color: Int) {
        when (val bg = v.background?.mutate()) {
            is GradientDrawable -> bg.setColor(color)
            null -> v.setBackgroundColor(color)
            else -> v.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun bindStars(adView: NativeAdView, nativeAd: NativeAd) {
        val stars = adView.findViewById<TextView?>(R.id.ad_stars) ?: return
        val rating = nativeAd.starRating
        if (rating == null || rating <= 0.0) {
            stars.visibility = View.GONE
            return
        }
        val value = String.format(Locale.getDefault(), "%.1f", rating)
        stars.text = if (adView.findViewById<View?>(R.id.ad_media) == null) {
            val full = floor(rating + 0.5).toInt().coerceIn(0, 5)
            SpannableStringBuilder().apply {
                append("★".repeat(5))
                setSpan(ForegroundColorSpan(color(stars, R.color.ad_star)), 0, full, 0)
                if (full < 5) setSpan(
                    ForegroundColorSpan(ColorUtils.setAlphaComponent(color(stars, R.color.ad_star), 0x99)),
                    full, 5, 0
                )
                append("  ")
                val start = length
                append(value)
                setSpan(ForegroundColorSpan(color(stars, R.color.lk_ink)), start, length, 0)
            }
        } else {
            SpannableStringBuilder().apply {
                append("★ ")
                setSpan(ForegroundColorSpan(color(stars, R.color.ad_star)), 0, 1, 0)
                append(listOfNotNull(value, nativeAd.price?.takeIf { it.isNotBlank() }).joinToString(" · "))
            }
        }
        stars.visibility = View.VISIBLE
        adView.starRatingView = stars
    }

    private fun glow(cta: TextView) {
        val dp = cta.resources.displayMetrics.density
        (cta.getTag(R.id.ad_call_to_action) as? ValueAnimator)?.cancel()
        val anim = ValueAnimator.ofFloat(4 * dp, 9 * dp).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { cta.elevation = it.animatedValue as Float }
        }
        cta.setTag(R.id.ad_call_to_action, anim)
        cta.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = anim.start()
            override fun onViewDetachedFromWindow(v: View) = anim.cancel()
        })
        if (cta.isAttachedToWindow) anim.start()
    }

    private fun color(v: View, res: Int) = ContextCompat.getColor(v.context, res)
}
