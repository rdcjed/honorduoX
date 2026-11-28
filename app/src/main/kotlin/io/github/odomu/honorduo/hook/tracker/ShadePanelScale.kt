package io.github.odomu.honorduo.hook.tracker

import android.view.View
import io.github.odomu.honorduo.model.ModuleConfig
import java.util.WeakHashMap
import kotlin.math.ceil

/** One user scale for the existing shared page. No polling or hierarchy changes. */
internal object ShadePanelScale {
    private class Original(view: View) {
        val pivotExplicit = view.isPivotSet
        val pivotX = view.pivotX
        val pivotY = view.pivotY
        var appliedScale = 1f
    }
    private val panels = WeakHashMap<View, Original>()
    private val writing = ThreadLocal<Boolean>()
    fun internalWrite(): Boolean = writing.get() == true
    fun nativeScale(panel: View, value: Float): Float = value * (panels[panel]?.appliedScale ?: 1f)

    fun register(panel: View) {
        val original = panels[panel] ?: Original(panel).also { original ->
            panels[panel] = original
            panel.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                apply(view, original, ModuleConfig.appearance().shadeIconScale)
            }
        }
        apply(panel, original, ModuleConfig.appearance().shadeIconScale)
    }

    fun applyAll(scale: Float) {
        panels.forEach { (panel, original) -> apply(panel, original, scale) }
    }

    fun heightMeasureSpec(panel: View, spec: Int): Int {
        val scale = panels[panel]?.appliedScale ?: 1f
        if (scale == 1f || View.MeasureSpec.getMode(spec) == View.MeasureSpec.UNSPECIFIED) return spec
        val height = ceil(View.MeasureSpec.getSize(spec) / scale.toDouble()).toInt()
        return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.getMode(spec))
    }

    private fun apply(panel: View, original: Original, scale: Float) {
        if (original.appliedScale != scale) {
            // Preserve the native animation's current progress when the user changes scale.
            val ratio = scale / original.appliedScale
            writing.set(true)
            try {
                panel.scaleX *= ratio
                panel.scaleY *= ratio
            } finally {
                writing.remove()
            }
            original.appliedScale = scale
            panel.requestLayout()
        }
        if (scale == 1f) {
            if (original.pivotExplicit) {
                if (panel.pivotX != original.pivotX) panel.pivotX = original.pivotX
                if (panel.pivotY != original.pivotY) panel.pivotY = original.pivotY
            } else if (panel.isPivotSet) panel.resetPivot()
        } else {
            if (panel.pivotX != panel.width / 2f) panel.pivotX = panel.width / 2f
            if (panel.pivotY != 0f) panel.pivotY = 0f
        }
    }
}
