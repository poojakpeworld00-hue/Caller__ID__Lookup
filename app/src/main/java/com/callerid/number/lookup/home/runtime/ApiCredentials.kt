package com.callerid.number.lookup.home.runtime

import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.Veiled

object ApiCredentials {

    val API_KEY: String by lazy { Veiled.s(BuildConfig.CONTACTS_API_KEY) }

    val isConfigured: Boolean
        get() = API_KEY.isNotBlank()
}
