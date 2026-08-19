package com.callerid.number.lookup.home.ui.lookup

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import com.callerid.admesh.domain.PromoVault
import com.callerid.admesh.presentation.BonusPromo
import com.callerid.number.lookup.home.databinding.DlgWatchAdBinding

/**
 * Gates revealing a caller name behind a rewarded ad — the shared flow used by the
 * Lookup card and the recent-lookup list. Ads off → reveals immediately; ads on →
 * "watch ad" confirm dialog → rewarded ad → reveal.
 */
object BonusReveal {

    /** First letter + dots (e.g. "John" → "J•••"). */
    fun blur(name: String): String =
        if (name.isNotEmpty()) name[0] + "•".repeat(name.length - 1) else name

    /**
     * Runs the reveal flow for [fullName] (shown blurred in the confirm dialog next
     * to [number]); [onRevealed] fires once the reward is earned (or immediately
     * when ads are off).
     */
    fun reveal(activity: Activity, fullName: String, number: String, onRevealed: () -> Unit) {
        // Ads off → straight through, no ad, no dialog.
        if (!PromoVault.getInstance(activity).getBoolean("IsAdsON")) {
            onRevealed()
            return
        }

        val db = DlgWatchAdBinding.inflate(activity.layoutInflater)
        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(db.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        db.tvPreviewName.text = blur(fullName)
        db.tvPreviewNumber.text = number
        db.btnWatchAd.setOnClickListener {
            dialog.dismiss()
            BonusPromo().show(activity) { onRevealed() }
        }
        db.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
