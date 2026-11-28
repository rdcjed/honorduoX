package io.github.odomu.honorduo

import android.content.Context
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.graphics.RectF
import org.junit.Assert.assertFalse
import io.github.odomu.honorduo.hook.tracker.ShadePanelScale
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class PanelScaleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun scaledViewportStillReachesThePhysicalScreenBottom() {
        val panel = FrameLayout(context)
        ShadePanelScale.register(panel)
        for (scale in listOf(0.5f, 0.67f, 0.85f, 1f)) {
            ShadePanelScale.applyAll(scale)
            for (mode in listOf(View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST)) {
                val requested = View.MeasureSpec.makeMeasureSpec(2800, mode)
                val actual = ShadePanelScale.heightMeasureSpec(panel, requested)
                assertEquals(mode, View.MeasureSpec.getMode(actual))
                val physicalBottom = View.MeasureSpec.getSize(actual) * scale
                assertTrue(physicalBottom >= 2800f)
                assertTrue(physicalBottom < 2801f)
            }
        }
        val natural = View.MeasureSpec.makeMeasureSpec(2800, View.MeasureSpec.UNSPECIFIED)
        ShadePanelScale.applyAll(0.85f)
        assertEquals(natural, ShadePanelScale.heightMeasureSpec(panel, natural))
    }

    @Test
    fun wholePanelScalesHeadersCardsAndSpacingTogetherWithoutReflow() {
        val panel = FrameLayout(context).apply { layout(0, 0, 1272, 2800) }
        val header = View(context)
        val body = FrameLayout(context)
        panel.addView(header)
        panel.addView(body)
        header.layout(128, 215, 1143, 495)
        body.layout(128, 495, 1143, 2800)
        val card = View(context)
        body.addView(card)
        card.layout(0, 805, 217, 1134)
        ShadePanelScale.register(panel)
        ShadePanelScale.applyAll(0.67f)
        assertEquals(1272, panel.width)
        assertEquals(805, card.top)
        assertEquals(217, card.width)
        assertEquals(1f, card.scaleX, 0f)
        val headerBounds = RectF(128f, 215f, 1143f, 495f)
        val cardBounds = RectF(128f, 1300f, 345f, 1629f)
        panel.matrix.mapRect(headerBounds)
        panel.matrix.mapRect(cardBounds)
        assertEquals(1015 * 0.67f, headerBounds.width(), 0.01f)
        assertEquals(217 * 0.67f, cardBounds.width(), 0.01f)
        assertEquals((1300 - 215) * 0.67f, cardBounds.top - headerBounds.top, 0.01f)
        assertEquals(headerBounds.left, cardBounds.left, 0.01f)
        ShadePanelScale.applyAll(1f)
        assertEquals(1f, panel.scaleX, 0f)
        assertFalse(panel.isPivotSet)
    }

    @Test
    fun unifiedSettingSurvivesPreferencesCopyLiveUpdateAndBackup() {
        val prefs = context.getSharedPreferences("panel-scale-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt(Prefs.KEY_SHADE_ICON_SCALE, 65).commit()
        val settings = ModuleSettings.from(prefs).copy()
        val backup = ModuleSettings.fromBundle(settings.toBundle())
        assertEquals(65, backup.shadeIconScale)
        val live = ModuleSettings.defaults()
        assertTrue(live.applyKeyFrom(backup, Prefs.KEY_SHADE_ICON_SCALE))
        assertEquals(65, live.shadeIconScale)
        assertEquals(100, live.statusIconScale)
    }

    @Test
    fun importedValuesAreClampedAndMasterSwitchRestoresBothPanels() {
        val bundle = ModuleSettings.defaults().toBundle().apply {
            putInt(Prefs.KEY_SHADE_ICON_SCALE, -5)
        }
        val settings = ModuleSettings.fromBundle(bundle).apply { enabled = true }
        assertEquals(Prefs.MIN_SHADE_ICON_SCALE, settings.shadeIconScale)
        assertEquals(0.5f, StatusAppearance.of(settings).shadeIconScale, 0f)
        settings.enabled = false
        assertEquals(1f, StatusAppearance.of(settings).shadeIconScale, 0f)
    }

    @Test
    fun changingScaleRepeatedlyDoesNotCompoundAndRestoresTheOriginalTransform() {
        val icon = View(context).apply { scaleX = 0.9f; scaleY = 0.95f }
        ShadePanelScale.register(icon)
        ShadePanelScale.applyAll(0.7f)
        ShadePanelScale.applyAll(0.8f)
        ShadePanelScale.applyAll(0.8f)
        assertEquals(0.72f, icon.scaleX, 0.0001f)
        assertEquals(0.76f, icon.scaleY, 0.0001f)
        ShadePanelScale.applyAll(1f)
        assertEquals(0.9f, icon.scaleX, 0.0001f)
        assertEquals(0.95f, icon.scaleY, 0.0001f)
    }

    @Test
    fun reboundNativeResetIsComposedWithUserScaleWithoutChangingParents() {
        val parent = FrameLayout(context)
        val column = FrameLayout(context)
        parent.addView(column)
        ShadePanelScale.register(column)
        ShadePanelScale.applyAll(0.85f)
        for (nativeScale in listOf(0.97f, 1.01f, 1f)) {
            column.scaleX = ShadePanelScale.nativeScale(column, nativeScale)
            column.scaleY = ShadePanelScale.nativeScale(column, nativeScale)
            ShadePanelScale.applyAll(0.85f)
            assertEquals(0.85f * nativeScale, column.scaleX, 0.0001f)
            assertEquals(parent, column.parent)
        }
        ShadePanelScale.applyAll(1f)
        assertEquals(1f, column.scaleX, 0.0001f)
    }

    @Test
    fun nativeChildAnimationAndPanelSwitchDoNotChangeOuterScaleOrRequestLayout() {
        class Panel : FrameLayout(context) {
            var layoutRequests = 0
            override fun requestLayout() { layoutRequests++; super.requestLayout() }
        }
        val panel = Panel().apply { layout(0, 0, 1272, 2800) }
        val notifications = View(context)
        val controls = View(context)
        panel.addView(notifications)
        panel.addView(controls)
        ShadePanelScale.register(panel)
        ShadePanelScale.applyAll(0.85f)
        val requests = panel.layoutRequests
        repeat(120) { frame ->
            notifications.scaleX = 0.8f + frame / 600f
            notifications.scaleY = notifications.scaleX
            controls.alpha = frame / 120f
            ShadePanelScale.applyAll(0.85f)
            assertEquals(0.85f, panel.scaleX, 0f)
        }
        notifications.visibility = View.GONE
        controls.visibility = View.VISIBLE
        ShadePanelScale.applyAll(0.85f)
        assertEquals(requests, panel.layoutRequests)
        assertEquals(0.85f, panel.scaleX, 0f)
    }
}
