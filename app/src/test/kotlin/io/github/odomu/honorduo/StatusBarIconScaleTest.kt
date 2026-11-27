package io.github.odomu.honorduo

import android.app.Application
import android.view.View
import android.widget.LinearLayout
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconScale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class StatusBarIconScaleTest {
    @Test
    fun emptyNetworkGroupReleasesItsWidthCompensationAndCanReturn() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layoutParams = LinearLayout.LayoutParams(100, 20).apply { marginEnd = 4 }
        }
        try {
            repeat(3) {
                measureWidth(view, 100)
                StatusBarIconScale.apply(view, 0.7f)
                assertEquals(-26, (view.layoutParams as LinearLayout.LayoutParams).marginEnd)

                measureWidth(view, 0)
                StatusBarIconScale.apply(view, 0.7f)
                assertEquals(4, (view.layoutParams as LinearLayout.LayoutParams).marginEnd)
            }
            StatusBarIconScale.restore(view)
            assertEquals(4, (view.layoutParams as LinearLayout.LayoutParams).marginEnd)
            assertEquals(1f, view.scaleX, 0f)
        } finally {
            StatusBarIconScale.restore(view)
        }
    }

    @Test
    fun enlargingAnEmptyGroupAlsoReleasesItsReservedWidth() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layoutParams = LinearLayout.LayoutParams(100, 20).apply { marginEnd = 4 }
        }
        try {
            measureWidth(view, 100)
            StatusBarIconScale.apply(view, 1.2f)
            assertEquals(24, (view.layoutParams as LinearLayout.LayoutParams).marginEnd)
            measureWidth(view, 0)
            StatusBarIconScale.apply(view, 1.2f)
            assertEquals(4, (view.layoutParams as LinearLayout.LayoutParams).marginEnd)
        } finally {
            StatusBarIconScale.restore(view)
        }
    }

    private fun measureWidth(view: View, width: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
        )
    }
}
