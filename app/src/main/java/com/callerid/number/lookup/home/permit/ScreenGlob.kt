package com.callerid.number.lookup.home.permit

/**
 * Decides which [PermitRule]s apply to a given Activity.
 *
 * Matching is done on the Activity's *simple class name*, case-insensitively,
 * so Remote LauncherPrefs stays readable and refactors that only move a class between
 * packages don't break targeting.
 *
 * [LEGACY_NAMES] maps every name an earlier build used to the current class names,
 * so Remote LauncherPrefs documents written against any of them keep matching. Two
 * renames have happened across generations, and the table is chained: a first-generation name maps
 * straight to today's class, not to the intermediate one.
 *
 * The value is a list because one old name can now mean two screens: the original
 * app's single MainActivity became this app's AppHubActivity, and the launcher
 * module later brought its own MainActivity, which is now HomeDeckActivity. A rule
 * targeting "MainActivity" has always applied to both, and still does.
 *
 * Drop an entry once the server-side rule has been updated.
 */
object ScreenGlob {

    private val LEGACY_NAMES: Map<String, List<String>> = mapOf(
        "ADDashboardActivity" to listOf("PromoAnchorActivity"),
        "ADHomeActivity" to listOf("PromoAnchorActivity"),
        "AdRelayActivity" to listOf("PromoAnchorActivity"),
        "AgreementActivity" to listOf("ConsentGateActivity"),
        "AppHubActivity" to listOf("AppHomeActivity"),
        "BaseActivity" to listOf("FrameActivity"),
        "BatteryActivity" to listOf("BatteryToolActivity"),
        "BatteryToolActivity" to listOf("BatteryToolActivity"),
        "BearingActivity" to listOf("CompassToolActivity"),
        "BlockLedgerActivity" to listOf("BlockCenterActivity"),
        "BlockRosterActivity" to listOf("BlockCenterActivity"),
        "BlocklistActivity" to listOf("BlockCenterActivity"),
        "BootSplashActivity" to listOf("LaunchGateActivity"),
        "BrightToolActivity" to listOf("LightMeterActivity"),
        "CallBriefActivity" to listOf("CallDetailActivity"),
        "CallDetailActivity" to listOf("CallDetailActivity"),
        "CallInsightActivity" to listOf("CallDetailActivity"),
        "ChronoActivity" to listOf("StopwatchActivity"),
        "CompassActivity" to listOf("CompassToolActivity"),
        "ConsentActivity" to listOf("ConsentGateActivity"),
        "CoreDeckActivity" to listOf("ShellBaseActivity"),
        "CountdownActivity" to listOf("CountdownActivity"),
        "CountryPickerActivity" to listOf("CountryPickActivity"),
        "DecibelActivity" to listOf("NoiseToolActivity"),
        "DeckSettingsActivity" to listOf("BoardSettingsActivity"),
        "DefaultHomeHintActivity" to listOf("RoleCoachActivity"),
        "DefaultHomeStepActivity" to listOf("HomeRoleGateActivity"),
        "DialPadActivity" to listOf("DialPadActivity"),
        "DialerActivity" to listOf("DialPadActivity"),
        "EggTimerActivity" to listOf("CountdownActivity"),
        "FlashToolActivity" to listOf("TorchToolActivity"),
        "FlashlightActivity" to listOf("TorchToolActivity"),
        "FloatAccessActivity" to listOf("OverlayGateActivity"),
        "FsiGateActivity" to listOf("FsiGateActivity"),
        "FsiPermissionActivity" to listOf("FsiGateActivity"),
        "FullScreenAccessActivity" to listOf("FsiGateActivity"),
        "GuideSheetActivity" to listOf("TipSheetActivity"),
        "HeadingToolActivity" to listOf("CompassToolActivity"),
        "HiddenIconsActivity" to listOf("MaskedAppsActivity"),
        "HomeDeckActivity" to listOf("HomeBoardActivity"),
        "HostActivity" to listOf("FrameActivity"),
        "IdentifyInsightActivity" to listOf("LookupResultActivity"),
        "IdentifyTraceActivity" to listOf("LookupHistoryActivity"),
        "InboundCallActivity" to listOf("RingScreenActivity"),
        "IncomingCallActivity" to listOf("RingScreenActivity"),
        "IntroActivity" to listOf("SlideIntroActivity"),
        "KeypadActivity" to listOf("DialPadActivity"),
        "LanguageActivity" to listOf("LanguageSelectActivity"),
        "LanguagePickActivity" to listOf("LanguageSelectActivity"),
        "LaunchActivity" to listOf("LaunchGateActivity"),
        "LevelActivity" to listOf("LevelToolActivity"),
        "LevelToolActivity" to listOf("LevelToolActivity"),
        "LightMeterActivity" to listOf("LightMeterActivity"),
        "LocaleActivity" to listOf("LanguageSelectActivity"),
        "LookupBriefActivity" to listOf("LookupResultActivity"),
        "LookupDetailActivity" to listOf("LookupResultActivity"),
        "LookupHistoryActivity" to listOf("LookupHistoryActivity"),
        "LookupLogActivity" to listOf("LookupHistoryActivity"),
        "LuxMeterActivity" to listOf("LightMeterActivity"),
        "MainActivity" to listOf("AppHomeActivity", "HomeBoardActivity"),
        "MaskedAppsActivity" to listOf("MaskedAppsActivity"),
        "NoiseToolActivity" to listOf("NoiseToolActivity"),
        "OnboardingActivity" to listOf("SlideIntroActivity"),
        "OnboardingDefaultLauncherActivity" to listOf("HomeRoleGateActivity"),
        "OnboardingWelcomeActivity" to listOf("HelloStepActivity"),
        "OptionsDeckActivity" to listOf("SettingsHubActivity"),
        "OverlayGateActivity" to listOf("OverlayGateActivity"),
        "OverlayGuideActivity" to listOf("TipSheetActivity"),
        "OverlayPermissionActivity" to listOf("OverlayGateActivity"),
        "PowerGaugeActivity" to listOf("BatteryToolActivity"),
        "PreferencesActivity" to listOf("SettingsHubActivity"),
        "RegionPickerActivity" to listOf("CountryPickActivity"),
        "RingScreenActivity" to listOf("RingScreenActivity"),
        "ScreenBaseActivity" to listOf("FrameActivity"),
        "SettingsActivity" to listOf("SettingsHubActivity", "BoardSettingsActivity"),
        "ShellActivity" to listOf("AppHomeActivity"),
        "SimCardActivity" to listOf("SimInfoActivity"),
        "SimDeckActivity" to listOf("SimInfoActivity"),
        "SimInfoActivity" to listOf("SimInfoActivity"),
        "SimpleActivity" to listOf("ShellBaseActivity"),
        "SoundMeterActivity" to listOf("NoiseToolActivity"),
        "SpeedToolActivity" to listOf("SpeedToolActivity"),
        "SpeedometerActivity" to listOf("SpeedToolActivity"),
        "SplashActivity" to listOf("LaunchGateActivity"),
        "StopClockActivity" to listOf("StopwatchActivity"),
        "StopwatchActivity" to listOf("StopwatchActivity"),
        "TermsActivity" to listOf("ConsentGateActivity"),
        "TerritoryPickerActivity" to listOf("CountryPickActivity"),
        "TiltActivity" to listOf("LevelToolActivity"),
        "TimerActivity" to listOf("CountdownActivity"),
        "ToolboxActivity" to listOf("ToolboxActivity"),
        "ToolsActivity" to listOf("ToolboxActivity"),
        "TorchActivity" to listOf("TorchToolActivity"),
        "TourActivity" to listOf("SlideIntroActivity"),
        "UtilityActivity" to listOf("ToolboxActivity"),
        "VelocityActivity" to listOf("SpeedToolActivity"),
        "WelcomeStepActivity" to listOf("HelloStepActivity"),
    )

    /**
     * True when a Remote LauncherPrefs screen name refers to [activitySimpleName], directly
     * or through [LEGACY_NAMES].
     *
     * Every name-keyed lookup in the app goes through here, so a class rename only
     * has to be recorded in [LEGACY_NAMES] once instead of being chased across the
     * permission engine, the splash primer and the per-screen ad config.
     */
    fun matches(configuredName: String, activitySimpleName: String): Boolean =
        configuredName.equals(activitySimpleName, ignoreCase = true) ||
            LEGACY_NAMES[configuredName]
                ?.any { it.equals(activitySimpleName, ignoreCase = true) } == true

    /**
     * The key in [keys] that refers to [activitySimpleName], or null. For config
     * objects keyed by screen name (`ScreenAds`), where the key on the server may be
     * a name from an earlier build.
     */
    fun keyFor(keys: Iterator<String>, activitySimpleName: String): String? {
        while (keys.hasNext()) {
            val key = keys.next()
            if (matches(key, activitySimpleName)) return key
        }
        return null
    }

    /**
     * Returns the enabled rules that target [activitySimpleName], preserving
     * the caller's ordering (the queue applies priority afterwards).
     */
    fun rulesFor(
        activitySimpleName: String,
        allRules: List<PermitRule>,
    ): List<PermitRule> = allRules.filter { rule ->
        rule.enabled && rule.activities.any { named -> matches(named, activitySimpleName) }
    }
}
