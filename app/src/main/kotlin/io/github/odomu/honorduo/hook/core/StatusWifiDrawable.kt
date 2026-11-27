package io.github.odomu.honorduo.hook.core

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import io.github.odomu.honorduo.model.ModuleConfig
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.render.WifiIconRenderer

/** Keep the native slot, dimensions and state while allowing the Wi-Fi ink to change live. */
internal class StatusWifiDrawable(
    private val original: Drawable,
    private val level: Int,
) : Drawable(), Drawable.Callback {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recolor = Paint()
    private val box = RectF()
    private var tint: ColorStateList? = null
    private var filter: ColorFilter? = null
    private var opacity = original.alpha
    private var preset: Int? = null

    init { original.callback = this }

    override fun draw(canvas: Canvas) {
        val a = ModuleConfig.appearance()
        val foreground = tint?.getColorForState(state, tint?.defaultColor ?: Color.WHITE) ?: Color.WHITE
        val fixed = if (a.enabled) WifiIconRenderer.color(a.wifiIconColor, a, foreground) else null
        box.set(bounds)
        if (a.enabled && (a.wifiIconStyle != Prefs.WIFI_STYLE_ORIGINAL || a.wifiStyleSwitch)) {
            val scale = a.wifiScaleFactor()
            val defaultFit = if (a.wifiStyleSwitch) 0.76f else 0.82f
            // Keep the high end of the slider within the native slot.
            val fit = if (scale <= 1f) defaultFit * scale else
                defaultFit + (1f - defaultFit) * (scale - 1f) / 0.6f
            val inset = (1f - fit) / 2f
            box.inset(box.width() * inset, box.height() * inset)
            val color = fixed ?: foreground
            ink.colorFilter = if (fixed == null) filter else null
            val inkColor = (color and 0x00ffffff) or ((Color.alpha(color) * opacity / 255) shl 24)
            WifiIconRenderer.draw(canvas, box, a.wifiIconStyle, a.wifiStyleSwitch, level,
                inkColor, 65, a.arcStroke, ink)
        } else if (fixed != null) {
            if (fixed != preset) {
                recolor.colorFilter = PorterDuffColorFilter(fixed, PorterDuff.Mode.SRC_IN)
                preset = fixed
            }
            val save = canvas.saveLayer(box, recolor)
            original.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            original.draw(canvas)
        }
    }

    override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }
    override fun getIntrinsicWidth(): Int = original.intrinsicWidth
    override fun getIntrinsicHeight(): Int = original.intrinsicHeight
    override fun getAlpha(): Int = opacity
    override fun setAlpha(alpha: Int) { opacity = alpha; original.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; original.colorFilter = colorFilter; invalidateSelf() }
    override fun setTintList(tint: ColorStateList?) { this.tint = tint; original.setTintList(tint); invalidateSelf() }
    override fun setTintMode(tintMode: PorterDuff.Mode?) { original.setTintMode(tintMode); invalidateSelf() }
    override fun isStateful(): Boolean = original.isStateful || tint?.isStateful == true
    override fun onStateChange(state: IntArray): Boolean = original.setState(state) || tint?.isStateful == true
    override fun onLevelChange(level: Int): Boolean = original.setLevel(level)
    @Deprecated("Required by Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
