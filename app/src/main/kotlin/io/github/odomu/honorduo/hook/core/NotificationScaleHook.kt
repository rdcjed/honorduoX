package io.github.odomu.honorduo.hook.core

import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.ModuleConfig
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Scale the notification viewport as one Android view, so RemoteViews, actions,
 * swipe gestures and accessibility all use the same coordinate transformation.
 * Measure in inverse coordinates to keep the cards inside the original screen
 * width and retain the entire scrollable viewport. The header stays unscaled.
 */
internal object NotificationScaleHook {
    private const val CLASS_STACK = "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout"
    private class Entry(var scale: Float = 1f, var expandedHeight: Float? = null)
    private val stacks = WeakHashMap<View, Entry>()

    fun install(module: XposedModule, cl: ClassLoader): Int {
        val stack = Refl.cls(CLASS_STACK, cl) ?: return 0
        var n = 0
        n += SystemUiHook.hook(module, Refl.methodNamed(stack, "onMeasure", 2), "honorduo-notification-measure") { chain ->
            val view = chain.thisObject as? View
            if (view == null) {
                chain.proceed()
            } else {
                val scale = scaleFor(view)
                apply(view, scale)
                if (scale == 1f) chain.proceed() else {
                    val width = inverseSpec(chain.getArg(0) as Int, scale)
                    val height = inverseSpec(chain.getArg(1) as Int, scale, topPadding(view))
                    chain.proceed(arrayOf<Any>(width, height))
                }
            }
        }
        n += SystemUiHook.hook(module, Refl.methodNamed(stack, "onLayout", 5), "honorduo-notification-layout") { chain ->
            val result = chain.proceed()
            (chain.thisObject as? View)?.let { apply(it, scaleFor(it)) }
            result
        }
        n += SystemUiHook.hook(module, Refl.methodNamed(stack, "setExpandedHeight", 1), "honorduo-notification-height") { chain ->
            val view = chain.thisObject as? View
            val height = (chain.getArg(0) as? Number)?.toFloat()
            if (view == null || height == null) chain.proceed() else {
                stacks.getOrPut(view) { Entry() }.expandedHeight = height
                val scale = scaleFor(view, height > 0f)
                val result = if (scale == 1f) chain.proceed() else {
                    chain.proceed(arrayOf<Any>(inverseLength(height, scale, topPadding(view))))
                }
                apply(view, scale)
                result
            }
        }
        for (method in listOf("setIsExpanded", "setStatusBarState")) {
            n += SystemUiHook.hook(module, Refl.methodNamed(stack, method, 1), "honorduo-notification-$method") { chain ->
                val result = chain.proceed()
                (chain.thisObject as? View)?.let { apply(it, scaleFor(it)) }
                result
            }
        }
        return n
    }

    fun onConfigChanged() {
        // setExpandedHeight may re-enter the hook and update its existing entry.
        for ((view, entry) in stacks.entries.toList()) {
            apply(view, scaleFor(view))
            entry.expandedHeight?.let { height ->
                Refl.invoke(Refl.methodNamed(view.javaClass, "setExpandedHeight", 1), view, height)
            }
            view.requestLayout()
        }
    }

    private fun scaleFor(view: View, expanded: Boolean? = null): Float {
        // Heads-up and lock-screen notifications retain their native geometry.
        if (Refl.getInt(Refl.field(view.javaClass, "mStatusBarState"), view, -1) != 0) return 1f
        if (!(expanded ?: Refl.getBool(Refl.field(view.javaClass, "mIsExpanded"), view, false))) return 1f
        return ModuleConfig.appearance().shadeNotificationScale
    }

    private fun topPadding(view: View): Float {
        val ambient = Refl.get(Refl.field(view.javaClass, "mAmbientState"), view) ?: return 0f
        return Refl.getInt(Refl.field(ambient.javaClass, "mTopPadding"), ambient, 0).toFloat()
    }

    private fun apply(view: View, scale: Float) {
        val entry = stacks.getOrPut(view) { Entry() }
        // MagicOS centers this child using its unscaled measured width. Scaling
        // around that same center preserves equal screen margins at every size.
        view.pivotX = view.width / 2f
        view.pivotY = topPadding(view)
        view.scaleX = scale
        view.scaleY = scale
        if (entry.scale != scale) {
            entry.scale = scale
            view.requestLayout()
        }
    }

    internal fun inverseLength(length: Float, scale: Float, anchor: Float = 0f): Float =
        if (length <= anchor) length else anchor + (length - anchor) / scale

    internal fun inverseSpec(spec: Int, scale: Float, anchor: Float = 0f): Int =
        View.MeasureSpec.makeMeasureSpec(
            inverseLength(View.MeasureSpec.getSize(spec).toFloat(), scale, anchor).roundToInt().coerceAtLeast(0),
            View.MeasureSpec.getMode(spec),
        )
}
