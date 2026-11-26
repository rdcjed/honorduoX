package io.github.odomu.honorduo.hook.statusbar

import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Adjust the leading space of icons inside the right-hand status-bar row. */
internal object StatusIconSpacing {
    private data class Margin(val original: Int, val applied: Int)
    private val margins = WeakHashMap<View, Margin>()
    private val groupNames = listOf("statusIcons", "network_area_icons", "signalIcons")

    fun apply(root: ViewGroup, spacingDp: Int) {
        val resources = root.resources
        val extra = (spacingDp * resources.displayMetrics.density).roundToInt()
        if (extra == 0 && margins.isEmpty()) return
        val groups = groupNames.mapNotNull { name ->
            val id = resources.getIdentifier(name, "id", "com.android.systemui")
            if (id == 0) null else root.findViewById<ViewGroup>(id)
        }
        for (group in groups) {
            for (index in 0 until group.childCount) {
                updateMargin(group.getChildAt(index), extra)
            }
        }
        for (index in 0 until root.childCount) {
            val view = root.getChildAt(index)
            if (view !in groups) updateMargin(view, extra)
        }
    }

    private fun updateMargin(view: View, extra: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val current = params.marginStart
        val previous = margins[view]
        val original = if (previous?.applied == current) previous.original else current
        val target = original + extra
        if (extra == 0) margins.remove(view) else margins[view] = Margin(original, target)
        if (target != current) {
            params.marginStart = target
            view.layoutParams = params
        }
    }
}
