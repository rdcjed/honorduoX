package io.github.odomu.honorduo

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.render.OutSignalRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSignalTest {
    @Test
    fun dataArrowsAreDrawnOnlyAboveSelectedRow() {
        val a = StatusAppearance(ModuleSettings.defaults().apply { screenshotSignal = true })
        val h = 420
        val w = OutSignalRenderer.outSignalWidth(h, a, null, null, true)
        fun render(row: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            OutSignalRenderer.drawOutSignal(Canvas(it), w, h, 4, 4, Color.BLACK, a,
                activityRow = row, activityDirection = 3)
        }
        val none = render(-1)
        val first = render(0)
        val second = render(1)
        fun differences(a: Bitmap, b: Bitmap, start: Int, end: Int): Int {
            var count = 0
            for (y in start until end) for (x in 0 until w) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) count++
            }
            return count
        }
        assertTrue(differences(none, first, 0, h / 2) > 0)
        assertEquals(0, differences(none, first, h / 2, h))
        assertEquals(0, differences(none, second, 0, h / 2))
        assertTrue(differences(none, second, h / 2, h) > 0)
    }
    @Test
    fun settingSurvivesCopyBundleAndIncrementalUpdate() {
        val original = ModuleSettings.defaults().apply { screenshotSignal = true }
        assertTrue(original.copy().screenshotSignal)
        assertTrue(ModuleSettings.fromBundle(original.toBundle()).screenshotSignal)
        val updated = ModuleSettings.defaults()
        assertTrue(updated.applyKeyFrom(original, Prefs.KEY_SCREENSHOT_SIGNAL))
        assertTrue(updated.screenshotSignal)
        assertNotEquals(original, ModuleSettings.defaults())
    }

    @Test
    fun worksWithoutMergedIconsAndReleasesSignalWhenDisabled() {
        val settings = ModuleSettings.defaults().apply {
            enabled = true
            mergeIcons = false
            screenshotSignal = true
        }
        val on = StatusAppearance(settings)
        assertTrue(on.stackedOut())
        assertTrue(on.signalOutOfRing)
        assertTrue(on.labelInReading())
        settings.screenshotSignal = false
        val oldRules = StatusAppearance(settings)
        assertFalse(oldRules.screenshotSignal)
        assertEquals(settings.signalMode == Prefs.SIGNAL_OUT_RING, oldRules.signalOutOfRing)
    }

    @Test
    fun drawsBothCardsWithinMeasuredBounds() {
        val settings = ModuleSettings.defaults().apply { enabled = true; screenshotSignal = true }
        val a = StatusAppearance(settings)
        val h = OutSignalRenderer.outSignalHeight(a, 16, 3f, true)
        val w = OutSignalRenderer.outSignalWidth(h, a, "5G", "4G", true)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        assertTrue(OutSignalRenderer.drawOutSignal(Canvas(bitmap), w, h, 4, 2, Color.BLACK, a, "5G", "4G"))
        fun hasInk(from: Int, end: Int): Boolean = (from until end).any { y ->
            (0 until w).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
        }
        assertTrue(hasInk(0, h / 2))
        assertTrue(hasInk(h / 2, h))
    }

    @Test
    fun screenshotSizeRespondsToExistingSlider() {
        val a = StatusAppearance(ModuleSettings.defaults().apply { screenshotSignal = true })
        val smaller = OutSignalRenderer.outSignalHeight(a, 12, 3f, true)
        val larger = OutSignalRenderer.outSignalHeight(a, 16, 3f, true)
        assertTrue(smaller > 0)
        assertTrue(larger > smaller)
        assertEquals(42, larger)
    }

    @Test
    fun screenshotWidthRespondsToFourOrFiveBars() {
        val settings = ModuleSettings.defaults().apply { screenshotSignal = true; signalColumns = 4 }
        val four = OutSignalRenderer.outSignalWidth(42, StatusAppearance(settings), "5G", "5G", true)
        settings.signalColumns = 5
        val five = OutSignalRenderer.outSignalWidth(42, StatusAppearance(settings), "5G", "5G", true)
        assertTrue(five > four)
    }

    @Test
    fun screenshotWidthRespondsToExistingBarControls() {
        val settings = ModuleSettings.defaults().apply { screenshotSignal = true }
        val original = OutSignalRenderer.outSignalWidth(42, StatusAppearance(settings), "5G", "5G", true)
        settings.outBarWidth = 140
        settings.outBarPitch = 140
        val wider = OutSignalRenderer.outSignalWidth(42, StatusAppearance(settings), "5G", "5G", true)
        assertTrue(wider > original)
    }

    @Test
    fun networkLabelsAlignWithTheirSignalRows() {
        val settings = ModuleSettings.defaults().apply { screenshotSignal = true; signalColumns = 4 }
        val appearance = StatusAppearance(settings)
        val h = 420
        val w = OutSignalRenderer.outSignalWidth(h, appearance, "5G", "5G", true)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        OutSignalRenderer.drawOutSignal(Canvas(bitmap), w, h, 4, 4, Color.BLACK, appearance, "5G", "5G")
        fun bottom(x0: Int, x1: Int, y0: Int, y1: Int): Int {
            for (y in y1 - 1 downTo y0) {
                if ((x0 until x1).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 128 }) return y
            }
            return -1
        }
        for ((top, end) in listOf(0 to h / 2, h / 2 to h)) {
            val labelBottom = bottom(0, w * 2 / 5, top, end)
            val barsBottom = bottom(w * 3 / 5, w, top, end)
            assertTrue(labelBottom >= 0 && barsBottom >= 0)
            assertTrue("label and bars should share a baseline", kotlin.math.abs(labelBottom - barsBottom) <= 4)
        }
    }

    @Test
    fun firstSignalStepIsRoundAfterHorizontalStretch() {
        val settings = ModuleSettings.defaults().apply { screenshotSignal = true; signalColumns = 4 }
        val appearance = StatusAppearance(settings)
        val h = 420
        val w = OutSignalRenderer.outSignalWidth(h, appearance, null, null, true)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        OutSignalRenderer.drawOutSignal(Canvas(bitmap), w, h, 4, 4, Color.BLACK, appearance)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h / 2) for (x in 0 until w / 5) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 128) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue(right > left && bottom > top)
        assertTrue("first bar should be round", kotlin.math.abs((right - left) - (bottom - top)) <= 3)
    }
}
