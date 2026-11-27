package io.github.odomu.honorduo.hook.statusbar

import android.view.View
import android.view.ViewGroup
import io.github.odomu.honorduo.hook.core.Refl
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Scales status-bar views around their start edge while keeping them vertically centred. */
internal object StatusBarIconScale {
    private class Original(view: View) {
        val scaleX = view.scaleX
        val scaleY = view.scaleY
        val pivotSet = view.isPivotSet
        val pivotX = view.pivotX
        val pivotY = view.pivotY
        var appliedMargin: Int? = null
        var baseMargin = 0
    }

    private val originals = WeakHashMap<View, Original>()

    fun isActive(view: View): Boolean = originals.containsKey(view)

    fun applyGroups(row: ViewGroup, container: ViewGroup, scale: Float) {
        if (scale == 1f && originals.isEmpty()) return
        val resources = container.resources
        for (name in listOf("statusIcons", "signalIcons")) {
            val id = Refl.resId(resources, name)
            if (id != 0) container.findViewById<View>(id)?.let { apply(it, scale) }
        }
        val networkAreaId = Refl.resId(resources, "network_area_icons")
        val networkSpeedId = Refl.resId(resources, "network_speed_view")
        if (networkAreaId != 0 && networkSpeedId != 0) {
            val area = container.findViewById<ViewGroup>(networkAreaId)
            if (area != null) {
                for (i in 0 until area.childCount) {
                    val icon = area.getChildAt(i)
                    if (icon.findViewById<View>(networkSpeedId) == null) apply(icon, scale)
                }
            }
        }
        val contents = StatusBarIconManager.contentsOf(row) ?: return
        val notificationId = Refl.resId(resources, "notification_icon_area")
        if (notificationId != 0) {
            contents.findViewById<View>(notificationId)?.let {
                // MagicOS positions the left notification area itself.
                apply(it, scale, reserveWidth = false)
            }
        }
    }

    fun apply(view: View, scale: Float, reserveWidth: Boolean = true) {
        if (scale == 1f) {
            restore(view)
            return
        }
        val width = view.measuredWidth
        if (width <= 0 && !originals.containsKey(view)) return
        val original = originals.getOrPut(view) { Original(view) }
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        if (reserveWidth && params != null) {
            val current = params.marginEnd
            if (current != original.appliedMargin) original.baseMargin = current
            // Empty, still-visible network groups take part in LinearLayout.
            // Clear their previous width compensation when Wi-Fi or data icons disappear.
            val target = original.baseMargin + (width.coerceAtLeast(0) * (scale - 1f)).roundToInt()
            original.appliedMargin = target
            if (target != current) {
                params.marginEnd = target
                view.layoutParams = params
            }
        }
        if (width <= 0 || view.measuredHeight <= 0) return
        val pivot = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) width.toFloat() else 0f
        val centerY = view.measuredHeight / 2f
        if (!view.isPivotSet || view.pivotX != pivot) view.pivotX = pivot
        if (view.pivotY != centerY) view.pivotY = centerY
        if (view.scaleX != scale) view.scaleX = scale
        if (view.scaleY != scale) view.scaleY = scale
    }

    fun restore(view: View) {
        val original = originals.remove(view) ?: return
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null && params.marginEnd == original.appliedMargin && params.marginEnd != original.baseMargin) {
            params.marginEnd = original.baseMargin
            view.layoutParams = params
        }
        view.scaleX = original.scaleX
        view.scaleY = original.scaleY
        if (original.pivotSet) {
            view.pivotX = original.pivotX
            view.pivotY = original.pivotY
        } else {
            view.resetPivot()
        }
    }
}
