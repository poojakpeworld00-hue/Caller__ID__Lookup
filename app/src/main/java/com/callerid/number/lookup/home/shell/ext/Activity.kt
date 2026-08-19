package com.callerid.number.lookup.home.shell.ext

import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.callerid.number.lookup.home.kit.LogRail
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.MenuCompat
import androidx.core.view.forEach
import com.google.android.material.color.MaterialColors
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isSPlus
import com.callerid.number.lookup.home.R
import com.callerid.number.lookup.home.shell.screens.BoardSettingsActivity
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_FOLDER
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_ICON
import com.callerid.number.lookup.home.shell.support.ITEM_TYPE_WIDGET
import com.callerid.number.lookup.home.shell.support.REQUEST_SET_DEFAULT
import com.callerid.number.lookup.home.shell.support.UNINSTALL_APP_REQUEST_CODE
import com.callerid.number.lookup.home.shell.contracts.TileMenuListener
import com.callerid.number.lookup.home.shell.entities.BoardItem
import com.callerid.admesh.surface.TipSheetActivity

fun Activity.launchApp(packageName: String, activityName: String) {
    try {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
            component = ComponentName.unflattenFromString("$packageName/$activityName")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            startActivity(this)
        }
    } catch (e: Exception) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            startActivity(launchIntent)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.launchAppInfo(packageName: String) {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        startActivity(this)
    }
}

/**
 * Tries, in order: the system's home-app picker, the Q+ role request (shows a one-tap system
 * confirmation instead of a settings list), the generic default-apps settings, then plain
 * Settings. Used by the long-press "Set as default" menu item and by onboarding.
 */
fun Activity.requestSetAsDefaultLauncher() {
    // second = whether the page is a list the user has to find this app in, and so
    // whether the coach mark helps. The Q+ role request is a one-tap confirmation
    // with nothing to hunt for, and a card over it would just cover the buttons.
    val intents = buildList {
        add(Intent(Settings.ACTION_HOME_SETTINGS) to true)
        if (isQPlus()) {
            add(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME) to false)
        }
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) to true)
        add(Intent(Settings.ACTION_SETTINGS) to true)
    }

    for ((intent, isListPage) in intents) {
        try {
            startActivityForResult(intent, REQUEST_SET_DEFAULT)
            if (isListPage) {
                TipSheetActivity.show(this, TipSheetActivity.MODE_HOME)
            }
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

fun Activity.canAppBeUninstalled(packageName: String): Boolean {
    return try {
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
    } catch (ignored: Exception) {
        false
    }
}

fun Activity.uninstallApp(packageName: String) {
    Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        startActivityForResult(this, UNINSTALL_APP_REQUEST_CODE)
    }
}

fun Activity.handleGridItemPopupMenu(
    anchorView: View,
    gridItem: BoardItem,
    isOnAllAppsFragment: Boolean,
    listener: TileMenuListener,
): PopupMenu {
    val contextTheme = ContextThemeWrapper(this, getPopupMenuTheme())
    return PopupMenu(contextTheme, anchorView, Gravity.TOP or Gravity.END).apply {
        if (isQPlus()) {
            setForceShowIcon(true)
        }

        inflate(R.menu.menu_app_icon)
        menu.forEach {
            val default = getProperTextColor()
            val color = if (isSPlus() && isDynamicTheme()) {
                default
            } else {
                MaterialColors.getColor(contextTheme, android.R.attr.actionMenuTextColor, default)
            }
            it.iconTintList = ColorStateList.valueOf(color)
        }
        menu.findItem(R.id.renameVw).isVisible =
            (gridItem.type == ITEM_TYPE_ICON || gridItem.type == ITEM_TYPE_FOLDER) && !isOnAllAppsFragment
        menu.findItem(R.id.hide_iconVw).isVisible =
            gridItem.type == ITEM_TYPE_ICON && isOnAllAppsFragment
        menu.findItem(R.id.resizeVw).isVisible = gridItem.type == ITEM_TYPE_WIDGET
        menu.findItem(R.id.app_infoVw).isVisible = gridItem.type == ITEM_TYPE_ICON
        menu.findItem(R.id.uninstallVw).isVisible = gridItem.type == ITEM_TYPE_ICON
                && canAppBeUninstalled(gridItem.packageName)
                && gridItem.packageName != packageName
        menu.findItem(R.id.removeVw).isVisible = !isOnAllAppsFragment

        val launcherApps =
            applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val shortcuts = if (launcherApps.hasShortcutHostPermission()) {
            try {
                val query = LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                ).setPackage(gridItem.packageName)
                launcherApps.getShortcuts(query, Process.myUserHandle())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val hasShortcuts = !shortcuts.isNullOrEmpty()
        MenuCompat.setGroupDividerEnabled(menu, hasShortcuts)
        menu.setGroupVisible(R.id.group_shortcutsVw, hasShortcuts)
        if (hasShortcuts) {
            val iconSize = resources.getDimensionPixelSize(R.dimen.menu_icon_size)
            shortcuts?.forEach { shortcutInfo ->
                val iconDrawable = launcherApps.getShortcutIconDrawable(
                    shortcutInfo, resources.displayMetrics.densityDpi
                )

                menu.add(R.id.group_shortcutsVw, Menu.NONE, Menu.NONE, shortcutInfo.getLabel())
                    .setIcon(
                        (iconDrawable ?: Color.TRANSPARENT.toDrawable())
                            .toBitmap(width = iconSize, height = iconSize)
                            .toDrawable(resources)
                    )
                    .setOnMenuItemClickListener { _ ->
                        listener.onAnyClick()
                        val id = shortcutInfo.id
                        val packageName = shortcutInfo.`package`
                        val userHandle = Process.myUserHandle()
                        launcherApps.startShortcut(packageName, id, Rect(), null, userHandle)
                        true
                    }
            }
        }

        setOnMenuItemClickListener { item ->
            listener.onAnyClick()
            when (item.itemId) {
                R.id.hide_iconVw -> listener.hide(gridItem)
                R.id.renameVw -> listener.rename(gridItem)
                R.id.resizeVw -> listener.resize(gridItem)
                R.id.app_infoVw -> listener.appInfo(gridItem)
                R.id.removeVw -> listener.remove(gridItem)
                R.id.uninstallVw -> listener.uninstall(gridItem)
            }
            true
        }

        setOnDismissListener {
            listener.onDismiss()
        }

        listener.beforeShow(menu)

        show()
    }
}

/**
 * Drops this app's tasks out of the recents list at runtime.
 *
 * The `excludeFromRecents` manifest attribute is not enough for the onboarding screens: it
 * only applies to the ROOT activity of a task, and those screens are started into the task
 * the caller-ID splash already rooted (`rootOfTask=false`). Setting it on the AppTask works
 * whatever the root is.
 *
 * It also takes the system's "Default apps" page and the role dialog with it — both are
 * started for a result, so they run inside this same task and inherit its recents state.
 * That is what stops the settings page lingering in recents, and being resumable in the
 * background, once the user has allowed or denied.
 *
 * Best-effort: some OEM shells refuse the call, and it is decoration rather than behaviour,
 * so a failure is logged and swallowed rather than surfaced.
 */
fun Activity.excludeAppFromRecents() {
    try {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        manager.appTasks.forEach { task -> task.setExcludeFromRecents(true) }
    } catch (e: Exception) {
        LogRail.error("Recents", "could not exclude task from recents", e)
    }
}
