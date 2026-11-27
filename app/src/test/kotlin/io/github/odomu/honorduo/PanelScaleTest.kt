package io.github.odomu.honorduo

import android.content.Context
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.graphics.RectF
import org.junit.Assert.assertFalse
import io.github.odomu.honorduo.hook.core.NotificationScaleHook
import io.github.odomu.honorduo.hook.tracker.ControlCenterPanelScale
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

    private class FrameQueuePanel(context: Context) : FrameLayout(context) {
        val frames = ArrayDeque<Runnable>()
        override fun postOnAnimation(action: Runnable) { frames.addLast(action) }
    }

    private fun visibleContent(panel: FrameLayout): View = object : View(context) {
        override fun isShown(): Boolean = visibility == VISIBLE
    }.also { panel.addView(it) }

    @Test
    fun scaledViewportStillReachesThePhysicalScreenBottom() {
        val panel = FrameLayout(context)
        ControlCenterPanelScale.register(panel)
        for (scale in listOf(0.5f, 0.67f, 0.85f, 1f)) {
            ControlCenterPanelScale.applyAll(scale)
            for (mode in listOf(View.MeasureSpec.EXACTLY, View.MeasureSpec.AT_MOST)) {
                val requested = View.MeasureSpec.makeMeasureSpec(2800, mode)
                val actual = ControlCenterPanelScale.heightMeasureSpec(panel, requested)
                assertEquals(mode, View.MeasureSpec.getMode(actual))
                val physicalBottom = View.MeasureSpec.getSize(actual) * scale
                assertTrue(physicalBottom >= 2800f)
                assertTrue(physicalBottom < 2801f)
            }
        }
        val natural = View.MeasureSpec.makeMeasureSpec(2800, View.MeasureSpec.UNSPECIFIED)
        ControlCenterPanelScale.applyAll(0.85f)
        assertEquals(natural, ControlCenterPanelScale.heightMeasureSpec(panel, natural))
    }

    @Test
    fun reboundKeepsTheSmallSizeUntilTheOutgoingContentHasFaded() {
        val panel = FrameQueuePanel(context)
        val content = visibleContent(panel)
        ControlCenterPanelScale.register(panel, content)
        ControlCenterPanelScale.applyAll(0.85f)
        ControlCenterPanelScale.setExpanded(panel, false)
        assertEquals(0.85f, panel.scaleX, 0f)
        assertEquals(1, panel.frames.size)
        content.alpha = 0f
        panel.frames.removeFirst().run()
        assertEquals(1f, panel.scaleX, 0f)
        val native = View.MeasureSpec.makeMeasureSpec(2800, View.MeasureSpec.EXACTLY)
        assertEquals(native, ControlCenterPanelScale.heightMeasureSpec(panel, native))
        assertTrue(panel.frames.isEmpty())
    }

    @Test
    fun reopeningCancelsThePendingRestoreFromThePreviousClose() {
        val panel = FrameQueuePanel(context)
        val content = visibleContent(panel)
        ControlCenterPanelScale.register(panel, content)
        ControlCenterPanelScale.applyAll(0.85f)
        ControlCenterPanelScale.setExpanded(panel, false)
        ControlCenterPanelScale.setExpanded(panel, true)
        panel.frames.removeFirst().run()
        assertEquals(0.85f, panel.scaleX, 0f)
        assertTrue(panel.frames.isEmpty())
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
        ControlCenterPanelScale.register(panel, body)
        ControlCenterPanelScale.applyAll(0.67f)
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
        ControlCenterPanelScale.applyAll(1f)
        assertEquals(1f, panel.scaleX, 0f)
        assertFalse(panel.isPivotSet)
    }

    @Test
    fun hiddenControlCenterAndEditingRestoreTheNativeTransform() {
        val panel = FrameLayout(context).apply { layout(0, 0, 1272, 2800) }
        val content = View(context)
        panel.addView(content)
        ControlCenterPanelScale.register(panel, content)
        ControlCenterPanelScale.applyAll(0.67f)
        assertEquals(0.67f, panel.scaleX, 0f)
        ControlCenterPanelScale.setExpanded(panel, false)
        ControlCenterPanelScale.applyAll(0.67f)
        assertEquals(1f, panel.scaleX, 0f)
        ControlCenterPanelScale.register(panel, content, expanded = true)
        ControlCenterPanelScale.applyAll(0.67f)
        assertEquals(0.67f, panel.scaleX, 0f)
        content.visibility = View.GONE
        ControlCenterPanelScale.applyAll(0.67f)
        assertEquals(1f, panel.scaleX, 0f)
        content.visibility = View.VISIBLE
        ControlCenterPanelScale.register(panel, content, editing = true)
        ControlCenterPanelScale.applyAll(0.67f)
        assertEquals(1f, panel.scaleX, 0f)
    }

    @Test
    fun settingsSurvivePreferencesCopyLiveUpdateAndBackupIndependently() {
        val prefs = context.getSharedPreferences("panel-scale-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt(Prefs.KEY_SHADE_ICON_SCALE, 65)
            .putInt(Prefs.KEY_SHADE_NOTIFICATION_SCALE, 85).commit()
        val settings = ModuleSettings.from(prefs).copy()
        val backup = ModuleSettings.fromBundle(settings.toBundle())
        assertEquals(65, backup.shadeIconScale)
        assertEquals(85, backup.shadeNotificationScale)
        val live = ModuleSettings.defaults()
        assertTrue(live.applyKeyFrom(backup, Prefs.KEY_SHADE_ICON_SCALE))
        assertEquals(65, live.shadeIconScale)
        assertEquals(100, live.shadeNotificationScale)
        assertTrue(live.applyKeyFrom(backup, Prefs.KEY_SHADE_NOTIFICATION_SCALE))
        assertEquals(85, live.shadeNotificationScale)
        assertEquals(100, live.statusIconScale)
    }

    @Test
    fun importedValuesAreClampedAndMasterSwitchRestoresBothPanels() {
        val bundle = ModuleSettings.defaults().toBundle().apply {
            putInt(Prefs.KEY_SHADE_ICON_SCALE, -5)
            putInt(Prefs.KEY_SHADE_NOTIFICATION_SCALE, 999)
        }
        val settings = ModuleSettings.fromBundle(bundle).apply { enabled = true }
        assertEquals(Prefs.MIN_SHADE_ICON_SCALE, settings.shadeIconScale)
        assertEquals(Prefs.MAX_SHADE_NOTIFICATION_SCALE, settings.shadeNotificationScale)
        assertEquals(0.5f, StatusAppearance.of(settings).shadeIconScale, 0f)
        assertEquals(1.2f, StatusAppearance.of(settings).shadeNotificationScale, 0f)
        settings.enabled = false
        assertEquals(1f, StatusAppearance.of(settings).shadeIconScale, 0f)
        assertEquals(1f, StatusAppearance.of(settings).shadeNotificationScale, 0f)
    }

    @Test
    fun changingScaleRepeatedlyDoesNotCompoundAndRestoresTheOriginalTransform() {
        val icon = View(context).apply { scaleX = 0.9f; scaleY = 0.95f }
        ControlCenterPanelScale.register(icon)
        ControlCenterPanelScale.applyAll(0.7f)
        ControlCenterPanelScale.applyAll(0.8f)
        ControlCenterPanelScale.applyAll(0.8f)
        assertEquals(0.72f, icon.scaleX, 0.0001f)
        assertEquals(0.76f, icon.scaleY, 0.0001f)
        ControlCenterPanelScale.applyAll(1f)
        assertEquals(0.9f, icon.scaleX, 0f)
        assertEquals(0.95f, icon.scaleY, 0f)
    }

    @Test
    fun notificationViewportKeepsBothScreenEdgesAndHeaderAnchorAtEverySize() {
        for (percent in listOf(70, 85, 100, 120)) {
            val scale = percent / 100f
            val width = NotificationScaleHook.inverseSpec(View.MeasureSpec.makeMeasureSpec(1188, View.MeasureSpec.EXACTLY), scale)
            val height = NotificationScaleHook.inverseSpec(View.MeasureSpec.makeMeasureSpec(2716, View.MeasureSpec.AT_MOST), scale, 495f)
            assertEquals(View.MeasureSpec.EXACTLY, View.MeasureSpec.getMode(width))
            assertEquals(View.MeasureSpec.AT_MOST, View.MeasureSpec.getMode(height))
            assertEquals(1188f, View.MeasureSpec.getSize(width) * scale, 0.6f)
            val rawWidth = View.MeasureSpec.getSize(width).toFloat()
            val centeredLeft = (1272f - rawWidth) / 2f
            val visibleLeft = centeredLeft + rawWidth / 2f * (1f - scale)
            assertEquals(42f, visibleLeft, 0.6f)
            assertEquals(2716f, 495f + (View.MeasureSpec.getSize(height) - 495f) * scale, 0.6f)
            assertEquals(495f, NotificationScaleHook.inverseLength(495f, scale, 495f), 0f)
            assertEquals(0f, NotificationScaleHook.inverseLength(0f, scale, 495f), 0f)
        }
    }
}
