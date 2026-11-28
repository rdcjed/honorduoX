package io.github.odomu.honorduo

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.odomu.honorduo.hook.statusbar.StatusBarRowManager
import io.github.odomu.honorduo.hook.statusbar.StatusIconSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class StatusIconSpacingTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private class LayoutCountingView(context: Context) : View(context) {
        var writes = 0
        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            writes++
            super.setLayoutParams(params)
        }
    }

    private fun component(root: LinearLayout) = LayoutCountingView(root.context).also {
        root.addView(it, LinearLayout.LayoutParams(100, 20).apply { gravity = Gravity.CENTER_VERTICAL })
        it.writes = 0
    }

    /** Exercise the actual component layout writer, followed by the real spacing pass. */
    private fun syncComponent(view: View, baseStartDp: Int = 0, gapEndDp: Int = 0) {
        val method = StatusBarRowManager::class.java.getDeclaredMethod(
            "size", View::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(StatusBarRowManager, view, 100, 20, gapEndDp, baseStartDp, view.resources.displayMetrics.density)
    }

    @Test
    fun nonzeroSpacingDoesNotKeepRequestingLayoutsDuringComponentSync() {
        val root = LinearLayout(context)
        val view = component(root)
        try {
            for (spacing in listOf(2, -2, 3)) {
                syncComponent(view)
                StatusIconSpacing.applyRight(root, spacing)
                val settledWrites = view.writes
                repeat(30) {
                    syncComponent(view)
                    StatusIconSpacing.applyRight(root, spacing)
                }
                assertEquals("Settled spacing must not start another layout", settledWrites, view.writes)
                assertEquals((spacing * view.resources.displayMetrics.density).roundToInt(),
                    (view.layoutParams as LinearLayout.LayoutParams).marginStart)
            }
        } finally {
            StatusIconSpacing.applyRight(root, 0)
        }
    }

    @Test
    fun componentBaseMarginCanChangeAndZeroRestoresItWithoutTouchingItsEndGap() {
        val root = LinearLayout(context)
        val view = component(root)
        val density = view.resources.displayMetrics.density
        try {
            syncComponent(view, baseStartDp = 1, gapEndDp = 4)
            StatusIconSpacing.applyRight(root, 2)
            syncComponent(view, baseStartDp = 3, gapEndDp = 4)
            StatusIconSpacing.applyRight(root, 2)
            val params = view.layoutParams as LinearLayout.LayoutParams
            assertEquals((3 * density).roundToInt() + (2 * density).roundToInt(), params.marginStart)
            assertEquals((4 * density).roundToInt(), params.marginEnd)
            StatusIconSpacing.applyRight(root, 0)
            assertEquals((3 * density).roundToInt(), params.marginStart)
            val settledWrites = view.writes
            repeat(30) {
                syncComponent(view, baseStartDp = 3, gapEndDp = 4)
                StatusIconSpacing.applyRight(root, 0)
            }
            assertEquals(settledWrites, view.writes)
        } finally {
            StatusIconSpacing.applyRight(root, 0)
        }
    }

    @Test
    fun missingVendorResourceNamesAreNotSearchedAgainOnEveryLayout() {
        val base = context.resources
        var lookups = 0
        val resources = object : Resources(base.assets, base.displayMetrics, base.configuration) {
            override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
                lookups++
                return 0
            }
        }
        val wrapped = object : ContextWrapper(context) {
            override fun getResources(): Resources = resources
        }
        val root = LinearLayout(wrapped)
        component(root)
        try {
            StatusIconSpacing.applyRight(root, 2)
            val firstLookups = lookups
            assertTrue(firstLookups > 0)
            repeat(30) { StatusIconSpacing.applyRight(root, 2) }
            assertEquals(firstLookups, lookups)
        } finally {
            StatusIconSpacing.applyRight(root, 0)
        }
    }
}
