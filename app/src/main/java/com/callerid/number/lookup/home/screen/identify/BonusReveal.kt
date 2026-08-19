package com.callerid.number.lookup.home.screen.identify

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import com.callerid.admesh.engine.PromoVault
import com.callerid.admesh.surface.BonusPromo
import com.callerid.number.lookup.home.databinding.DlgWatchAdBinding

object BonusReveal {

    fun blur(name: String): String =
        if (name.isNotEmpty()) name[0] + "•".repeat(name.length - 1) else name

    fun reveal(activity: Activity, fullName: String, number: String, onRevealed: () -> Unit) {

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
        db.lblPreviewName.text = blur(fullName)
        db.lblPreviewNumber.text = number
        db.padWatchAd.setOnClickListener {
            dialog.dismiss()
            BonusPromo().show(activity) { onRevealed() }
        }
        db.padCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
