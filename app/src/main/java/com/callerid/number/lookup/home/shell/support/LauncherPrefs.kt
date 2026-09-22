package com.callerid.number.lookup.home.shell.support

import android.content.Context
import org.fossify.commons.helpers.BaseConfig
import com.callerid.number.lookup.home.R

class LauncherPrefs(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = LauncherPrefs(context)
    }

    var wasHomeScreenInit: Boolean
        get() = prefs.getBoolean(WAS_HOME_SCREEN_INIT, false)
        set(wasHomeScreenInit) = prefs.edit().putBoolean(WAS_HOME_SCREEN_INIT, wasHomeScreenInit).apply()

    var wasOnboardingCompleted: Boolean
        get() = prefs.getBoolean(WAS_ONBOARDING_COMPLETED, false)
        set(wasOnboardingCompleted) = prefs.edit()
            .putBoolean(WAS_ONBOARDING_COMPLETED, wasOnboardingCompleted).apply()

    var wasSearchBarSeeded: Boolean
        get() = prefs.getBoolean(WAS_SEARCH_BAR_SEEDED, false)
        set(wasSearchBarSeeded) = prefs.edit().putBoolean(WAS_SEARCH_BAR_SEEDED, wasSearchBarSeeded).apply()

    var wasClockSeeded: Boolean
        get() = prefs.getBoolean(WAS_CLOCK_SEEDED, false)
        set(wasClockSeeded) = prefs.edit().putBoolean(WAS_CLOCK_SEEDED, wasClockSeeded).apply()

    /** Whether the quick-actions card (clock + Dialer/Block/Lookup/Tools) has been placed. */
    var wasQuickActionsSeeded: Boolean
        get() = prefs.getBoolean(WAS_QUICK_ACTIONS_SEEDED, false)
        set(value) = prefs.edit().putBoolean(WAS_QUICK_ACTIONS_SEEDED, value).apply()

    /**
     * Whether the one-time repair for the old seeding bug has run.
     *
     * That bug marked both widgets seeded before placing either, so an install that failed
     * once said "done" forever. The repair clears the flag for a widget that is genuinely
     * absent, exactly once per install, so those home screens get their clock back.
     */
    var wasHomeWidgetsRepaired: Boolean
        get() = prefs.getBoolean(WAS_HOME_WIDGETS_REPAIRED, false)
        set(value) = prefs.edit().putBoolean(WAS_HOME_WIDGETS_REPAIRED, value).apply()

    var wasSwipeHintShown: Boolean
        get() = prefs.getBoolean(WAS_SWIPE_HINT_SHOWN, false)
        set(wasSwipeHintShown) = prefs.edit()
            .putBoolean(WAS_SWIPE_HINT_SHOWN, wasSwipeHintShown).apply()

    var swipeHintIndex: Int
        get() = prefs.getInt(SWIPE_HINT_INDEX, 0)
        set(swipeHintIndex) = prefs.edit().putInt(SWIPE_HINT_INDEX, swipeHintIndex).apply()

    var onboardingStep: Int
        get() = prefs.getInt(ONBOARDING_STEP, 0)
        set(onboardingStep) = prefs.edit().putInt(ONBOARDING_STEP, onboardingStep).apply()

    var homeColumnCount: Int
        get() = prefs.getInt(HOME_COLUMN_COUNT, COLUMN_COUNT)
        set(homeColumnCount) = prefs.edit().putInt(HOME_COLUMN_COUNT, homeColumnCount).apply()

    var homeRowCount: Int
        get() = prefs.getInt(HOME_ROW_COUNT, ROW_COUNT)
        set(homeRowCount) = prefs.edit().putInt(HOME_ROW_COUNT, homeRowCount).apply()

    var drawerColumnCount: Int
        get() = prefs.getInt(DRAWER_COLUMN_COUNT, context.resources.getInteger(R.integer.portrait_column_count))
        set(drawerColumnCount) = prefs.edit().putInt(DRAWER_COLUMN_COUNT, drawerColumnCount).apply()

    var showSearchBar: Boolean
        get() = prefs.getBoolean(SHOW_SEARCH_BAR, true)
        set(showSearchBar) = prefs.edit().putBoolean(SHOW_SEARCH_BAR, showSearchBar).apply()

    var closeAppDrawer: Boolean
        get() = prefs.getBoolean(CLOSE_APP_DRAWER, false)
        set(closeAppDrawer) = prefs.edit().putBoolean(CLOSE_APP_DRAWER, closeAppDrawer).apply()

    var autoShowKeyboardInAppDrawer: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD_IN_APP_DRAWER, false)
        set(autoShowKeyboardInAppDrawer) = prefs.edit()
            .putBoolean(AUTO_SHOW_KEYBOARD_IN_APP_DRAWER, autoShowKeyboardInAppDrawer).apply()

    var showDrawerAppLabels: Boolean
        get() = prefs.getBoolean(SHOW_DRAWER_APP_LABELS, true)
        set(showDrawerAppLabels) = prefs.edit().putBoolean(SHOW_DRAWER_APP_LABELS, showDrawerAppLabels).apply()

    var showHomeAppLabels: Boolean
        get() = prefs.getBoolean(SHOW_HOME_APP_LABELS, true)
        set(showHomeAppLabels) = prefs.edit().putBoolean(SHOW_HOME_APP_LABELS, showHomeAppLabels).apply()
}
