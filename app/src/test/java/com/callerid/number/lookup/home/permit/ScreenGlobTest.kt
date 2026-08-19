package com.callerid.number.lookup.home.permit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the behaviour the Stage-3a rewrite had to preserve. The implementation moved
 * from a map-of-lists scanned per call to a lowercased inverted index, and the rows
 * that mapped a name to itself were dropped; every case here passes under both.
 */
class ScreenGlobTest {

    @Test
    fun `direct name matches regardless of case`() {
        assertTrue(ScreenGlob.refersTo("LaunchGateActivity", "LaunchGateActivity"))
        assertTrue(ScreenGlob.refersTo("launchgateactivity", "LaunchGateActivity"))
        assertTrue(ScreenGlob.refersTo("LAUNCHGATEACTIVITY", "LaunchGateActivity"))
    }

    /** The dropped self-mapping rows must still match — via the equality check. */
    @Test
    fun `self referring names still match after the table was trimmed`() {
        for (n in listOf(
            "BatteryToolActivity", "CountdownActivity", "DialPadActivity", "FsiGateActivity",
            "LevelToolActivity", "LightMeterActivity", "LookupHistoryActivity", "MaskedAppsActivity",
            "NoiseToolActivity", "OverlayGateActivity", "RingScreenActivity", "SimInfoActivity",
            "SpeedToolActivity", "StopwatchActivity", "ToolboxActivity", "CallDetailActivity",
        )) {
            assertTrue("expected $n to match itself", ScreenGlob.refersTo(n, n))
        }
    }

    @Test
    fun `alias from an earlier generation resolves to the current class`() {
        assertTrue(ScreenGlob.refersTo("BootSplashActivity", "LaunchGateActivity"))
        assertTrue(ScreenGlob.refersTo("SplashActivity", "LaunchGateActivity"))
        assertTrue(ScreenGlob.refersTo("LanguagePickActivity", "LanguageSelectActivity"))
        assertTrue(ScreenGlob.refersTo("WelcomeStepActivity", "HelloStepActivity"))
    }

    @Test
    fun `one alias can fan out to two screens`() {
        assertTrue(ScreenGlob.refersTo("MainActivity", "AppHomeActivity"))
        assertTrue(ScreenGlob.refersTo("MainActivity", "HomeBoardActivity"))
        assertTrue(ScreenGlob.refersTo("SettingsActivity", "SettingsHubActivity"))
        assertTrue(ScreenGlob.refersTo("SettingsActivity", "BoardSettingsActivity"))
    }

    @Test
    fun `unrelated names do not match`() {
        assertFalse(ScreenGlob.refersTo("MainActivity", "LaunchGateActivity"))
        assertFalse(ScreenGlob.refersTo("NoSuchActivity", "AppHomeActivity"))
        assertFalse(ScreenGlob.refersTo("", "AppHomeActivity"))
    }

    @Test
    fun `keyFor returns the first denoting key, else null`() {
        val keys = listOf("default", "SplashActivity", "AppHomeActivity")
        assertEquals("SplashActivity", ScreenGlob.keyFor(keys.iterator(), "LaunchGateActivity"))
        assertEquals("AppHomeActivity", ScreenGlob.keyFor(keys.iterator(), "AppHomeActivity"))
        assertNull(ScreenGlob.keyFor(keys.iterator(), "NoiseToolActivity"))
    }

    @Test
    fun `rulesFor keeps caller order and drops disabled rules`() {
        fun rule(key: String, enabled: Boolean, screens: List<String>, priority: Int) =
            PermitRule(key, enabled, screens, delayMs = 0L, priority = priority, showOnce = false)
        val a = rule("notification", true, listOf("SplashActivity"), 1)
        val b = rule("phone_state", false, listOf("LaunchGateActivity"), 2)
        val c = rule("overlay", true, listOf("MainActivity"), 3)
        val got = ScreenGlob.rulesFor("LaunchGateActivity", listOf(a, b, c))
        assertEquals(listOf("notification"), got.map { it.key })
    }
}
