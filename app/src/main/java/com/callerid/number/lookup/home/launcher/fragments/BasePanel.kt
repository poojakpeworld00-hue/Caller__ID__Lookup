package com.callerid.number.lookup.home.launcher.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.viewbinding.ViewBinding
import com.callerid.number.lookup.home.launcher.activities.HomeBoardActivity

abstract class BasePanel<BINDING : ViewBinding>(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet) {
    protected var activity: HomeBoardActivity? = null
    protected lateinit var binding: BINDING

    abstract fun setupFragment(activity: HomeBoardActivity)
}
