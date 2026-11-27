package io.github.odomu.honorduo.hook.statusbar

import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Preserves native margins while applying spacing adjustments to status-bar components. */
internal object StatusIconSpacing {
    private data class Margin(val original: Int, val applied: Int)
    private val rightMargins = WeakHashMap<View, Margin>()
    private data class Translation(val original: Float, val applied: Float)
    private val leftTranslations = WeakHashMap<View, Translation>()
    private val groupNames = listOf("statusIcons", "network_area_icons", "signalIcons")

    fun applyRight(root: ViewGroup, spacingDp: Int) {
        val resources = root.resources
        val extra = (spacingDp * resources.displayMetrics.density).roundToInt()
        if (extra == 0 && rightMargins.isEmpty()) return
        val groups = groupNames.mapNotNull { name ->
            val id = resources.getIdentifier(name, "id", "com.android.systemui")
            if (id == 0) null else root.findViewById<ViewGroup>(id)
        }
        for (group in groups) {
            for (index in 0 until group.childCount) {
                updateMargin(rightMargins, group.getChildAt(index), extra)
            }
        }
        for (index in 0 until root.childCount) {
            val view = root.getChildAt(index)
            if (view !in groups) updateMargin(rightMargins, view, extra)
        }
    }

    fun applyLeft(row: ViewGroup, spacingDp: Int, readouts: View? = null) {
        if (spacingDp == 0 && leftTranslations.isEmpty()) return
        val contents = StatusBarIconManager.contentsOf(row) ?: return
        if (StatusBarIconManager.isKeyguardTree(contents)) return
        val id = contents.resources.getIdentifier("notification_icon_area", "id", "com.android.systemui")
        if (id == 0) return
        val notifications = contents.findViewById<View>(id) ?: return
        val clock = contents.findViewById<View>(StatusBarIconManager.clockId)
        // Only adjust the gap ahead of the notification area when a clock or reading precedes it.
        val hasLeadingContent = clock?.visibility == View.VISIBLE || readouts?.visibility == View.VISIBLE
        val direction = if (notifications.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        val extra = if (hasLeadingContent) spacingDp * direction * contents.resources.displayMetrics.density else 0f
        // MagicOS lays out this notification view itself and ignores its start margin.
        val current = notifications.translationX
        val previous = leftTranslations[notifications]
        val original = if (previous?.applied == current) previous.original else current
        val target = original + extra
        if (extra == 0f) leftTranslations.remove(notifications)
        else leftTranslations[notifications] = Translation(original, target)
        if (target != current) notifications.translationX = target
    }

    private fun updateMargin(margins: MutableMap<View, Margin>, view: View, extra: Int) {
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
