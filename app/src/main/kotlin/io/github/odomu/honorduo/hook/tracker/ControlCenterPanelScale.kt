package io.github.odomu.honorduo.hook.tracker

import android.view.View
import io.github.odomu.honorduo.model.ModuleConfig
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil

/** One transform for the entire control-center column, including its headers. */
internal object ControlCenterPanelScale {
    private class Original(view: View) {
        val x = view.scaleX
        val y = view.scaleY
        val pivotExplicit = view.isPivotSet
        val pivotX = view.pivotX
        val pivotY = view.pivotY
        var content: WeakReference<View>? = null
        var editing = false
        var expanded = true
        var requestedScale = 1f
        var appliedScale = 1f
        var generation = 0
    }
    private val panels = WeakHashMap<View, Original>()

    fun register(panel: View, content: View? = null, editing: Boolean = false, expanded: Boolean = true) {
        val original = panels[panel] ?: Original(panel).also {
            panels[panel] = it
            panel.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                apply(view, it, ModuleConfig.appearance().shadeIconScale)
            }
        }
        original.content = content?.let(::WeakReference)
        original.editing = editing
        original.expanded = expanded
        apply(panel, original, ModuleConfig.appearance().shadeIconScale)
    }

    fun applyAll(scale: Float) {
        panels.forEach { (panel, original) -> apply(panel, original, scale) }
    }

    fun setExpanded(panel: View, expanded: Boolean) {
        panels[panel]?.let { original ->
            original.expanded = expanded
            original.generation++
            apply(panel, original, original.requestedScale)
            if (!expanded && contentVisible(panel, original)) {
                awaitHidden(panel, original, original.generation, 90)
            }
        }
    }

    /** Keep the physical viewport full height after the entire column is scaled. */
    fun heightMeasureSpec(panel: View, spec: Int): Int {
        val scale = panels[panel]?.appliedScale ?: 1f
        if (scale == 1f || View.MeasureSpec.getMode(spec) == View.MeasureSpec.UNSPECIFIED) return spec
        val height = ceil(View.MeasureSpec.getSize(spec) / scale.toDouble()).toInt()
        return View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.getMode(spec))
    }

    private fun awaitHidden(panel: View, original: Original, generation: Int, frames: Int) {
        val reference = WeakReference(panel)
        panel.postOnAnimation {
            val view = reference.get() ?: return@postOnAnimation
            if (original.generation != generation || original.expanded) return@postOnAnimation
            apply(view, original, original.requestedScale)
            if (frames > 0 && contentVisible(view, original)) awaitHidden(view, original, generation, frames - 1)
        }
    }

    private fun contentVisible(panel: View, original: Original): Boolean {
        var view = original.content?.get() ?: return false
        if (!view.isShown) return false
        while (view !== panel) {
            if (view.visibility != View.VISIBLE || view.alpha <= 0.01f) return false
            view = view.parent as? View ?: return false
        }
        return true
    }

    private fun apply(panel: View, original: Original, scale: Float) {
        original.requestedScale = scale
        val content = original.content?.get()
        val inControlCenter = original.content == null || (content != null && content.parent != null && content.visibility == View.VISIBLE)
        // The vendor announces collapse before its rebound animation finishes.
        // Restore only after the outgoing control-center content is actually hidden.
        val active = original.expanded || contentVisible(panel, original)
        val factor = if (original.editing || !active || !inControlCenter) 1f else scale
        panel.scaleX = original.x * factor
        panel.scaleY = original.y * factor
        if (factor == 1f) {
            if (original.pivotExplicit) {
                panel.pivotX = original.pivotX
                panel.pivotY = original.pivotY
            } else panel.resetPivot()
        } else {
            panel.pivotX = panel.width / 2f
            panel.pivotY = 0f
        }
        if (original.appliedScale != factor) {
            original.appliedScale = factor
            panel.requestLayout()
        }
    }
}
