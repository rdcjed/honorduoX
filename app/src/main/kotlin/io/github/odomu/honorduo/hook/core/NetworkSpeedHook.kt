package io.github.odomu.honorduo.hook.core

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.statusbar.StatusBarIconManager
import io.github.odomu.honorduo.hook.tracker.WifiPresence
import io.github.odomu.honorduo.model.ModuleConfig
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Style the native TextView. SystemUI retains its sampler, labels, and drawing. */
internal object NetworkSpeedHook {
    private data class NativeMetrics(
        val size: Float,
        val minWidth: Int,
        val maxWidth: Int,
        val minEms: Int,
        val maxEms: Int,
        val lineSpace: Float,
        val lineMultiplier: Float,
        val paddingEnd: Int,
    )

    private val views = WeakHashMap<TextView, NativeMetrics>()
    private val numberBounds = Rect()
    private val unitBounds = Rect()

    fun install(module: XposedModule, loader: ClassLoader): Int {
        val view = Refl.cls("com.android.systemui.networkspeed.NetworkSpeedView", loader) ?: return 0
        val field = Refl.field(view, "mNetSpeedText") ?: return 0
        var hooked = listOf("onFinishInflate", "onDensityOrFontScaleChanged").sumOf { name ->
            SystemUiHook.hook(module, Refl.methodNamed(view, name, 0), "honorduo-network-speed-$name") { chain ->
                val result = chain.proceed()
                val row = chain.thisObject as? View ?: return@hook result
                val text = Refl.get(field, row) as? TextView ?: return@hook result
                val base = NativeMetrics(text.textSize, text.minWidth, text.maxWidth, text.minEms,
                    text.maxEms, text.lineSpacingExtra, text.lineSpacingMultiplier,
                    views[text]?.paddingEnd ?: row.paddingEnd)
                views[text] = base
                apply(text, base)
                result
            }
        }
        hooked += SystemUiHook.hook(module, Refl.methodNamed(view, "refreshAreaTint", 0),
            "honorduo-network-speed-tint") { chain ->
            val result = chain.proceed()
            val row = chain.thisObject as? View ?: return@hook result
            val text = Refl.get(field, row) as? TextView ?: return@hook result
            if (ModuleConfig.appearance().enabled) applyTint(row, text)
            result
        }
        return hooked
    }

    fun updateConfig() {
        views.forEach { (text, base) -> apply(text, base) }
    }

    private fun apply(text: TextView, base: NativeMetrics) {
        val row = text.parent as? View ?: return
        val a = ModuleConfig.appearance()
        val factor = if (a.enabled) a.networkSpeedScale / 100f else 1f
        val density = row.resources.displayMetrics.density
        val size = base.size * factor
        if (text.textSize != size) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        if (a.enabled) {
            val sample = if (text.maxLines == 1) "000.0 M/s" else "000.0"
            val minimumWidth = ceil(text.paint.measureText(sample) + density).toInt() +
                text.compoundPaddingLeft + text.compoundPaddingRight
            val nativeWidth = if (base.minWidth > 0 && base.minWidth == base.maxWidth) {
                ceil(base.minWidth * factor).toInt()
            } else 0
            val width = maxOf(minimumWidth, nativeWidth)
            if (text.minWidth != width || text.maxWidth != width) text.setWidth(width)
            applyTint(row, text)
        } else {
            if (base.minWidth >= 0 && text.minWidth != base.minWidth) text.minWidth = base.minWidth
            if (base.maxWidth >= 0 && text.maxWidth != base.maxWidth) text.maxWidth = base.maxWidth
            if (base.minEms >= 0 && text.minEms != base.minEms) text.minEms = base.minEms
            if (base.maxEms >= 0 && text.maxEms != base.maxEms) text.maxEms = base.maxEms
        }

        val lineSpace = if (a.enabled && text.maxLines != 1) {
            // Fixed font metrics keep both baselines still when digits or units change.
            text.paint.getTextBounds("0123456789.", 0, 11, numberBounds)
            text.paint.getTextBounds("KMGB/s", 0, 6, unitBounds)
            val minimumAdvance = numberBounds.bottom - unitBounds.top + density
            val fm = text.paint.fontMetricsInt
            val nativeAdvance = (fm.descent - fm.ascent) * base.lineMultiplier
            maxOf(base.lineSpace * factor, minimumAdvance - nativeAdvance)
        } else base.lineSpace
        if (text.lineSpacingExtra != lineSpace || text.lineSpacingMultiplier != base.lineMultiplier) {
            text.setLineSpacing(lineSpace, base.lineMultiplier)
        }

        val end = when {
            !a.enabled -> base.paddingEnd
            a.showWifiIndicator && WifiPresence.isConnected -> 0
            // Compensate for a negative icon margin when 5G is the next item.
            else -> ((3 - minOf(0, a.statusIconSpacing)) * density).roundToInt()
        }
        if (row.paddingEnd != end) {
            row.setPaddingRelative(row.paddingStart, row.paddingTop, end, row.paddingBottom)
        }
        row.invalidate()
    }

    private fun applyTint(row: View, text: TextView) {
        val tint = StatusBarIconManager.foreground(row, text.currentTextColor)
        if (text.currentTextColor != tint) text.setTextColor(tint)
    }
}
