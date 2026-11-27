package io.github.odomu.honorduo

import android.content.SharedPreferences
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class StatusBarLayoutSettingsTest {
    @Test
    fun legacySpacingIsRetainedOnTheRight() {
        val settings = ModuleSettings.from(preferences("status_icon_spacing" to -2))
        assertEquals(-2, settings.statusRightIconSpacing)
        assertEquals(0, settings.statusLeftIconSpacing)
        assertEquals(Prefs.NETWORK_SPEED_SYSTEM, settings.networkSpeedPosition)
    }

    @Test
    fun spacingAndNetworkSpeedPositionUpdateIndependently() {
        val settings = ModuleSettings.from(preferences(
            Prefs.KEY_STATUS_LEFT_ICON_SPACING to 6,
            Prefs.KEY_STATUS_RIGHT_ICON_SPACING to -1,
            Prefs.KEY_NETWORK_SPEED_POSITION to Prefs.NETWORK_SPEED_CENTER,
        )).copy()
        assertEquals(6, settings.statusLeftIconSpacing)
        assertEquals(-1, settings.statusRightIconSpacing)
        assertEquals(Prefs.NETWORK_SPEED_CENTER, settings.networkSpeedPosition)

        val applied = ModuleSettings.defaults()
        assertTrue(applied.applyKeyFrom(settings, Prefs.KEY_STATUS_LEFT_ICON_SPACING))
        assertEquals(6, applied.statusLeftIconSpacing)
        assertEquals(0, applied.statusRightIconSpacing)
        assertEquals(Prefs.NETWORK_SPEED_SYSTEM, applied.networkSpeedPosition)
    }

    @Test
    fun statusIconScaleKeepsItsOwnRangeAndLeavesBatteryScaleIndependent() {
        val defaults = ModuleSettings.from(preferences())
        assertEquals(1f, StatusAppearance.of(defaults).statusIconScale, 0f)
        val small = ModuleSettings.from(preferences(Prefs.KEY_STATUS_ICON_SCALE to 30))
        val large = ModuleSettings.from(preferences(Prefs.KEY_STATUS_ICON_SCALE to 150)).copy()
        assertEquals(50, small.statusIconScale)
        assertEquals(120, large.statusIconScale)

        defaults.batteryIconScale = 80
        assertTrue(defaults.applyKeyFrom(large, Prefs.KEY_STATUS_ICON_SCALE))
        assertEquals(1.2f, StatusAppearance.of(defaults).statusIconScale, 0f)
        assertEquals(80, defaults.batteryIconScale)
    }

    @Test
    fun verticalBatteryOnlyReplacesAVisibleStandaloneBattery() {
        val settings = ModuleSettings.from(preferences(Prefs.KEY_BATTERY_ICON_SCALE to 200)).apply {
            enabled = true
            mergeIcons = false
        }
        assertEquals(Prefs.MAX_BATTERY_ICON_SCALE, settings.batteryIconScale)
        assertFalse(StatusAppearance.of(settings).drawsVerticalBattery())
        settings.batteryIconVertical = true
        assertTrue(StatusAppearance.of(settings.copy()).drawsVerticalBattery())
        settings.mergeIcons = true
        assertFalse(StatusAppearance.of(settings).drawsVerticalBattery())
        settings.mergeIcons = false
        settings.showBatteryIndicator = false
        assertFalse(StatusAppearance.of(settings).drawsVerticalBattery())
    }

    private fun preferences(vararg entries: Pair<String, Int>): SharedPreferences {
        val values = entries.toMap()
        val type = SharedPreferences::class.java
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "getAll" -> values
                "contains" -> values.containsKey(args[0])
                "getInt" -> values[args[0]] ?: args[1]
                "getBoolean", "getFloat", "getLong", "getString", "getStringSet" -> args[1]
                else -> error("Unexpected preference call: " + method.name)
            }
        } as SharedPreferences
    }
}
