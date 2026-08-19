package com.callerid.number.lookup.home.shell.screens

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.support.REPOSITORY_NAME

open class ShellBaseActivity : BaseSimpleActivity() {

    private var spoofFossifyPackageName = false

    protected fun <T> withFossifyPackageNameSpoofed(block: () -> T): T {
        spoofFossifyPackageName = true
        try {
            return block()
        } finally {
            spoofFossifyPackageName = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        withFossifyPackageNameSpoofed { super.onCreate(savedInstanceState) }
    }

    override fun getPackageName(): String {

        return if (spoofFossifyPackageName) "org.fossify.home" else super.getPackageName()
    }

    override fun getAppIconIDs() = ArrayList(List(APP_ICON_COLOR_COUNT) { R.mipmap.ic_launcher })

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = REPOSITORY_NAME

    private companion object {

        const val APP_ICON_COLOR_COUNT = 19
    }
}
