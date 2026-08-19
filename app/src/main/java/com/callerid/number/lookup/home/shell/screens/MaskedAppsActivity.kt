package com.callerid.number.lookup.home.shell.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyGridLayoutManager
import com.callerid.number.lookup.home.shell.lists.MaskedIconsAdapter
import com.callerid.number.lookup.home.databinding.ScreenHiddenIconsBinding
import com.callerid.number.lookup.home.shell.ext.config
import com.callerid.number.lookup.home.shell.ext.getDrawableForPackageName
import com.callerid.number.lookup.home.shell.ext.hiddenIconsDB
import com.callerid.number.lookup.home.shell.entities.MaskedIcon

class MaskedAppsActivity : ShellBaseActivity(), RefreshRecyclerViewListener {
    private val binding by viewBinding(ScreenHiddenIconsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateIcons()

        setupEdgeToEdge(padBottomSystem = listOf(binding.manageHiddenIconsListVw))
        setupMaterialScrollListener(binding.manageHiddenIconsListVw, binding.manageHiddenIconsAppbarVw)

        val layoutManager = binding.manageHiddenIconsListVw.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = config.drawerColumnCount
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageHiddenIconsAppbarVw, NavigationIcon.Arrow)
    }

    private fun updateIcons() {
        ensureBackgroundThread {
            val hiddenIcons = hiddenIconsDB.getHiddenIcons().sortedWith(
                compareBy({
                    it.title.normalizeString().lowercase()
                }, {
                    it.packageName
                })
            ).toMutableList() as ArrayList<MaskedIcon>

            val hiddenIconsEmpty = hiddenIcons.isEmpty()
            runOnUiThread {
                binding.manageHiddenIconsPlaceholderVw.beVisibleIf(hiddenIconsEmpty)
            }

            if (hiddenIcons.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_MAIN, null)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)

                val list = packageManager.queryIntentActivities(intent, PackageManager.PERMISSION_GRANTED)
                for (info in list) {
                    val componentInfo = info.activityInfo.applicationInfo
                    val packageName = componentInfo.packageName
                    val activityName = info.activityInfo.name
                    hiddenIcons.firstOrNull { it.getIconIdentifier() == "$packageName/$activityName" }?.apply {
                        drawable = info.loadIcon(packageManager) ?: getDrawableForPackageName(packageName)
                    }
                }

                hiddenIcons.firstOrNull { it.packageName == applicationContext.packageName }?.apply {
                    drawable = getDrawableForPackageName(packageName)
                }
            }

            val iconsToRemove = hiddenIcons.filter { it.drawable == null }
            if (iconsToRemove.isNotEmpty()) {
                hiddenIconsDB.removeHiddenIcons(iconsToRemove)
                hiddenIcons.removeAll(iconsToRemove)
            }

            runOnUiThread {
                MaskedIconsAdapter(this, hiddenIcons, this, binding.manageHiddenIconsListVw) {
                }.apply {
                    binding.manageHiddenIconsListVw.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        updateIcons()
    }
}
