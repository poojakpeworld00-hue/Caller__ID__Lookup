package com.callerid.admesh.surface.interstitial

import com.callerid.admesh.engine.PromoVault

object InterLoader {

    const val KEY = "inter_loader"

    fun enabled(pref: PromoVault): Boolean = pref.getBoolean(KEY, pref.getBoolean("isLoaderForFB"))
}
