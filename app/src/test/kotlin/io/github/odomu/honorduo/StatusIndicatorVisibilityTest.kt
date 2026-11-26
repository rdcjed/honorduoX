package io.github.odomu.honorduo

import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusIndicatorVisibilityTest {
    @Test
    fun hiddenIndicatorsLeaveTheirPlacementAndReturnWhenEnabled() {
        val settings = ModuleSettings.defaults().apply {
            enabled = true
            mergeIcons = true
            showWifi = true
            showValue = true
            showMobile = true
            screenshotSignal = true
            ringZones[Prefs.ZONE_CENTRE] = intArrayOf(Prefs.PART_VALUE, Prefs.PART_WIFI, Prefs.PART_MOBILE)
            showBatteryIndicator = false
            showWifiIndicator = false
            showMobileIndicator = false
        }

        val hidden = StatusAppearance.of(settings)
        assertFalse(hidden.ownsValue())
        assertFalse(hidden.ownsWifi())
        assertFalse(hidden.stackedOut())

        val shown = StatusAppearance.of(settings.copy().apply {
            showBatteryIndicator = true
            showWifiIndicator = true
            showMobileIndicator = true
        })
        assertTrue(shown.ownsValue())
        assertTrue(shown.ownsWifi())
        assertTrue(shown.stackedOut())
    }
}
