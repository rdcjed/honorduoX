package io.github.odomu.honorduo

import io.github.odomu.honorduo.model.ClockPattern
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.ReadoutPlacement
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.IconZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CustomClockAndReadoutTest {
    @Test
    fun formatsCustomDateAndTimeAndRejectsInvalidInput() {
        val example = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 25, 16, 19, 8)
        }.time
        assertEquals("2026-11-25", ClockPattern.format("yyyy-MM-dd", example, Locale.US))
        assertEquals("16:19:08", ClockPattern.format("HH:mm:ss", example, Locale.US))
        assertNull(ClockPattern.format("HH:mm '", example, Locale.US))
        assertTrue(ClockPattern.usesSeconds("HH:mm:ss"))
        assertFalse(ClockPattern.usesSeconds("HH:mm 'seconds'"))
    }

    @Test
    fun movingReadoutIntoGlyphClearsTheLeftDestination() {
        val current = ModuleSettings.defaults().apply {
            enabled = true
            mergeIcons = true
            perfTemp = true
            leftReadouts = ReadoutPlacement.setLeft(0, Prefs.PART_TEMP, true)
            ringZones[Prefs.ZONE_TOP] = intArrayOf(Prefs.PART_TEMP)
        }
        val left = StatusAppearance.of(current)
        assertTrue(left.readoutOnLeft(Prefs.PART_TEMP))
        assertFalse(left.placed(Prefs.PART_TEMP))

        val moved = IconZones.withPiece(current, Prefs.ZONE_CENTRE, IconZones.Part.TEMP)
        assertTrue(ReadoutPlacement.enabled(moved, Prefs.PART_TEMP))
        assertFalse(StatusAppearance.of(moved).readoutOnLeft(Prefs.PART_TEMP))
        assertTrue(StatusAppearance.of(moved).placed(Prefs.PART_TEMP))
    }
}
