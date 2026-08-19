package com.callerid.number.lookup.home.permit

/**
 * Resolves which [PermitRule]s target a given Activity.
 *
 * Screen names in Remote Config are matched against an Activity's simple class
 * name, case-insensitively, so a document stays readable and moving a class
 * between packages never breaks targeting.
 *
 * [ALIASES] carries the names earlier generations of this codebase used. The
 * chain is flattened: a first-generation name resolves straight to today's class
 * rather than hopping through the intermediate one. A single alias can fan out to
 * more than one screen, which is why the values are lists.
 *
 * Everything is folded once into [index], a lowercased alias -> target-set map, so
 * a lookup is a hash probe and a set membership test rather than a scan of the
 * table. Rows that mapped a name to itself were dropped: [refersTo] compares the
 * names directly before consulting the index, so those rows could never fire.
 */
object ScreenGlob {

    private val ALIASES: Map<String, List<String>> = mapOf(
        "ADDashboardActivity" to listOf("PromoAnchorActivity"),
        "ADHomeActivity" to listOf("PromoAnchorActivity"),
        "AdRelayActivity" to listOf("PromoAnchorActivity"),
        "AgreementActivity" to listOf("ConsentGateActivity"),
        "AppHubActivity" to listOf("AppHomeActivity"),
        "BaseActivity" to listOf("FrameActivity"),
        "BatteryActivity" to listOf("BatteryToolActivity"),
        "BearingActivity" to listOf("CompassToolActivity"),
        "BlockLedgerActivity" to listOf("BlockCenterActivity"),
        "BlockRosterActivity" to listOf("BlockCenterActivity"),
        "BlocklistActivity" to listOf("BlockCenterActivity"),
        "BootSplashActivity" to listOf("LaunchGateActivity"),
        "BrightToolActivity" to listOf("LightMeterActivity"),
        "CallBriefActivity" to listOf("CallDetailActivity"),
        "CallInsightActivity" to listOf("CallDetailActivity"),
        "ChronoActivity" to listOf("StopwatchActivity"),
        "CompassActivity" to listOf("CompassToolActivity"),
        "ConsentActivity" to listOf("ConsentGateActivity"),
        "CoreDeckActivity" to listOf("ShellBaseActivity"),
        "CountryPickerActivity" to listOf("CountryPickActivity"),
        "DecibelActivity" to listOf("NoiseToolActivity"),
        "DeckSettingsActivity" to listOf("BoardSettingsActivity"),
        "DefaultHomeHintActivity" to listOf("RoleCoachActivity"),
        "DefaultHomeStepActivity" to listOf("HomeRoleGateActivity"),
        "DialerActivity" to listOf("DialPadActivity"),
        "EggTimerActivity" to listOf("CountdownActivity"),
        "FlashToolActivity" to listOf("TorchToolActivity"),
        "FlashlightActivity" to listOf("TorchToolActivity"),
        "FloatAccessActivity" to listOf("OverlayGateActivity"),
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
        "LocaleActivity" to listOf("LanguageSelectActivity"),
        "LookupBriefActivity" to listOf("LookupResultActivity"),
        "LookupDetailActivity" to listOf("LookupResultActivity"),
        "LookupLogActivity" to listOf("LookupHistoryActivity"),
        "LuxMeterActivity" to listOf("LightMeterActivity"),
        "MainActivity" to listOf("AppHomeActivity", "HomeBoardActivity"),
        "OnboardingActivity" to listOf("SlideIntroActivity"),
        "OnboardingDefaultLauncherActivity" to listOf("HomeRoleGateActivity"),
        "OnboardingWelcomeActivity" to listOf("HelloStepActivity"),
        "OptionsDeckActivity" to listOf("SettingsHubActivity"),
        "OverlayGuideActivity" to listOf("TipSheetActivity"),
        "OverlayPermissionActivity" to listOf("OverlayGateActivity"),
        "PowerGaugeActivity" to listOf("BatteryToolActivity"),
        "PreferencesActivity" to listOf("SettingsHubActivity"),
        "RegionPickerActivity" to listOf("CountryPickActivity"),
        "ScreenBaseActivity" to listOf("FrameActivity"),
        "SettingsActivity" to listOf("SettingsHubActivity", "BoardSettingsActivity"),
        "ShellActivity" to listOf("AppHomeActivity"),
        "SimCardActivity" to listOf("SimInfoActivity"),
        "SimDeckActivity" to listOf("SimInfoActivity"),
        "SimpleActivity" to listOf("ShellBaseActivity"),
        "SoundMeterActivity" to listOf("NoiseToolActivity"),
        "SpeedometerActivity" to listOf("SpeedToolActivity"),
        "SplashActivity" to listOf("LaunchGateActivity"),
        "StopClockActivity" to listOf("StopwatchActivity"),
        "TermsActivity" to listOf("ConsentGateActivity"),
        "TerritoryPickerActivity" to listOf("CountryPickActivity"),
        "TiltActivity" to listOf("LevelToolActivity"),
        "TimerActivity" to listOf("CountdownActivity"),
        "ToolsActivity" to listOf("ToolboxActivity"),
        "TorchActivity" to listOf("TorchToolActivity"),
        "TourActivity" to listOf("SlideIntroActivity"),
        "UtilityActivity" to listOf("ToolboxActivity"),
        "VelocityActivity" to listOf("SpeedToolActivity"),
        "WelcomeStepActivity" to listOf("HelloStepActivity"),
    )

    /** Lowercased alias -> lowercased targets. Built once, on first use. */
    private val index: Map<String, Set<String>> by lazy {
        ALIASES.entries.associate { (alias, targets) ->
            alias.lowercase() to targets.mapTo(HashSet(targets.size)) { it.lowercase() }
        }
    }

    /**
     * True when the configured screen name denotes [activitySimpleName], either
     * directly or through an alias.
     */
    fun refersTo(configuredName: String, activitySimpleName: String): Boolean {
        if (configuredName.equals(activitySimpleName, ignoreCase = true)) return true
        val targets = index[configuredName.lowercase()] ?: return false
        return activitySimpleName.lowercase() in targets
    }

    /**
     * The first key in [keys] denoting [activitySimpleName], or null. Config objects
     * keyed by screen name (ScreenAds) may still be keyed by an older build's name.
     */
    fun keyFor(keys: Iterator<String>, activitySimpleName: String): String? =
        keys.asSequence().firstOrNull { refersTo(it, activitySimpleName) }

    /**
     * The enabled rules targeting [activitySimpleName], in the caller's order; the
     * queue applies priority afterwards.
     */
    fun rulesFor(
        activitySimpleName: String,
        allRules: List<PermitRule>,
    ): List<PermitRule> = allRules.filter { rule ->
        rule.enabled && rule.activities.any { refersTo(it, activitySimpleName) }
    }
}
