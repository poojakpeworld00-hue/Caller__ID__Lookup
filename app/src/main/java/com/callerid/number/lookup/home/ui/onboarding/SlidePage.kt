package com.callerid.number.lookup.home.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.callerid.number.lookup.home.R

/**
 * A page shows EITHER a composed [customArtRes] layout (when non-zero) or a
 * simple [artRes] drawable.
 */
data class SlidePage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int,
    @param:DrawableRes val artRes: Int = 0,
    @param:LayoutRes val customArtRes: Int = 0
)

object SlideCatalog {
    val all: List<SlidePage> = listOf(
        SlidePage(R.string.onboarding_title, R.string.onboarding_desc, customArtRes = R.layout.canvas_onboarding_caller_view),
        SlidePage(R.string.onboarding_title_2, R.string.onboarding_desc_2, customArtRes = R.layout.canvas_onboarding_spam_view),
        SlidePage(R.string.onboarding_title_3, R.string.onboarding_desc_3, customArtRes = R.layout.canvas_onboarding_spam_view1)
    )
}
