package io.github.odomu.honorduo.hook.statusbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import io.github.odomu.honorduo.model.Prefs
import java.lang.ref.WeakReference

/** Draws the current native reading in the top band while its producer stays attached. */
internal class NetworkSpeedPlacement(row: View, text: TextView) :
    View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {

    private val rowRef = WeakReference(row)
    private val textRef = WeakReference(text)
    private val label = SpeedLabel()
    private val hostLocation = IntArray(2)
    private val anchorLocation = IntArray(2)
    private val ink = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsuleIds = listOf("dynamic_capsule", "status_capsule_container", "colored_capsule_parent")
        .map { row.resources.getIdentifier(it, "id", "com.android.systemui") }.filter { it != 0 }

    private var position = Prefs.NETWORK_SPEED_SYSTEM
    private var observer: ViewTreeObserver? = null
    private var watcher: TextWatcher? = null
    private var overlayHost: ViewGroup? = null
    private var nativeParams: ViewGroup.LayoutParams? = null
    private var nativeWidth = 0
    private var nativeTextAlpha: Float? = null

    fun setPosition(next: Int) {
        if (position == next) return
        val row = rowRef.get() ?: return
        position = next
        if (next == Prefs.NETWORK_SPEED_SYSTEM) {
            row.removeOnAttachStateChangeListener(this)
            detach(row)
        } else {
            row.removeOnAttachStateChangeListener(this)
            row.addOnAttachStateChangeListener(this)
            if (row.isAttachedToWindow) observe(row)
            row.invalidate()
        }
    }

    override fun onViewAttachedToWindow(v: View) = observe(v)
    override fun onViewDetachedFromWindow(v: View) = detach(v)

    private fun observe(row: View) {
        if (observer?.isAlive == true) return
        observer = row.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        val text = textRef.get() ?: return
        if (watcher == null) watcher = text.doAfterTextChanged { rowRef.get()?.invalidate() }
    }

    private fun detach(row: View) {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        watcher?.let { textRef.get()?.removeTextChangedListener(it) }
        watcher = null
        restore(row)
    }

    override fun onPreDraw(): Boolean {
        val row = rowRef.get() ?: return true
        val text = textRef.get() ?: return true
        if (!runCatching { place(row, text) }.getOrDefault(false)) restore(row)
        return true
    }

    private fun place(row: View, text: TextView): Boolean {
        if (StatusBarRowManager.suppressed || !row.isShown || text.visibility != View.VISIBLE) return false
        val host = StatusBarIconManager.contentsOf(row) ?: return false
        if (host.height <= 0 || host.width <= 0 || StatusBarIconManager.isKeyguardTree(host)) return false
        val value = text.text.toString().replace('\n', ' ').replace('\r', ' ').trim()
        if (value.isEmpty()) return false
        host.getLocationInWindow(hostLocation)
        val cutouts = host.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        val camera = cutouts.firstOrNull { it.top < hostLocation[1] + host.height && it.bottom > hostLocation[1] }
        val battery = host.findViewById<View>(StatusBarIconManager.batteryId)
        val rightRow = battery?.let(StatusBarRowManager::rowOfBattery)
        val batteryAnchor = rightRow?.verticalBattery?.takeIf { it.isShown }
            ?: rightRow?.glyph?.takeIf { it.isShown } ?: battery?.takeIf { it.isShown }
        val clock = host.findViewById<View>(StatusBarIconManager.clockId)?.takeIf { it.isShown }
        val anchor = when (position) {
            Prefs.NETWORK_SPEED_RIGHT_TOP -> batteryAnchor
            Prefs.NETWORK_SPEED_LEFT_TOP -> clock ?: rightRow?.leftReadouts?.takeIf { it.isShown }
            Prefs.NETWORK_SPEED_CENTER -> capsuleAnchor(host)
            else -> return false
        }
        if (anchor == null && position != Prefs.NETWORK_SPEED_CENTER) return false
        val alignment = when (position) {
            Prefs.NETWORK_SPEED_LEFT_TOP -> 0f
            Prefs.NETWORK_SPEED_RIGHT_TOP -> 1f
            else -> 0.5f
        }
        var anchorX: Float
        val anchorTop: Int
        if (anchor != null && anchor.width > 0) {
            anchor.getLocationInWindow(anchorLocation)
            val localX = if (position == Prefs.NETWORK_SPEED_LEFT_TOP && anchor is TextView) {
                BarClockManager.timeInkLeft(anchor, ink)
            } else anchor.width * anchor.scaleX * alignment
            anchorX = anchorLocation[0] + localX - hostLocation[0]
            anchorTop = anchorLocation[1] - hostLocation[1]
        } else {
            anchorX = camera?.let { it.exactCenterX() - hostLocation[0] } ?: (host.width / 2f)
            val reference = clock ?: batteryAnchor ?: return false
            reference.getLocationInWindow(anchorLocation)
            anchorTop = anchorLocation[1] - hostLocation[1]
        }
        val density = host.resources.displayMetrics.density
        val edgePadding = density.coerceAtLeast(1f)
        val anchorGap = if (position == Prefs.NETWORK_SPEED_CENTER) edgePadding else 2f * density
        paint.set(text.paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = StatusBarIconManager.foreground(anchor ?: text, text.currentTextColor)
        paint.getTextBounds(value, 0, value.length, ink)
        if (ink.isEmpty) return false
        if (position == Prefs.NETWORK_SPEED_CENTER && camera != null &&
            camera.top - hostLocation[1] < 5f * density + 2f * edgePadding
        ) {
            // Some ROMs reserve the camera column up to the screen edge. Use its nearest
            // upper shoulder so the speed remains in the middle without covering the camera.
            val leftSpace = camera.left - hostLocation[0]
            val rightEdge = camera.right - hostLocation[0]
            val rightSpace = host.width - rightEdge
            if (maxOf(leftSpace, rightSpace) < ink.width() + 2f * edgePadding) return false
            anchorX = if (leftSpace >= rightSpace) leftSpace - edgePadding - ink.width() / 2f
                else rightEdge + edgePadding + ink.width() / 2f
        }
        val windowAnchor = hostLocation[0] + anchorX
        val requestedLeft = windowAnchor - ink.width() * alignment
        val cutoutTop = cutouts.filter {
            it.left < requestedLeft + ink.width() && it.right > requestedLeft && it.bottom > hostLocation[1]
        }.minOfOrNull { it.top - hostLocation[1] } ?: host.height
        val bottom = minOf(anchorTop - anchorGap, cutoutTop - edgePadding, host.height - edgePadding)
        val room = bottom - edgePadding
        if (room < 5f * density) return false
        if (ink.height() > room) {
            paint.textSize *= room / ink.height()
            paint.getTextBounds(value, 0, value.length, ink)
        }
        val inkWidth = ink.width()
        val rightLimit = host.width - inkWidth - edgePadding
        if (rightLimit < edgePadding) return false
        val inkLeft = (anchorX - inkWidth * alignment).coerceIn(edgePadding, rightLimit)
        val x = inkLeft - ink.left
        val baseline = bottom - ink.bottom
        val left = hostLocation[0] + inkLeft
        val top = hostLocation[1] + baseline + ink.top
        if (cutouts.any { it.left < left + inkWidth && it.right > left &&
                it.top < hostLocation[1] + bottom && it.bottom > top
            }) return false

        val params = row.layoutParams ?: return false
        if (overlayHost !== host) {
            overlayHost?.overlay?.remove(label)
            host.overlay.add(label)
            overlayHost = host
        }
        label.setBounds(0, 0, host.width, host.height)
        label.update(value, x, baseline, paint)
        if (nativeParams !== params || params.width != 0) {
            nativeParams = params
            nativeWidth = params.width
            if (params.width != 0) {
                params.width = 0
                row.layoutParams = params
            }
        }
        if (nativeTextAlpha == null || text.alpha != 0f) nativeTextAlpha = text.alpha
        if (text.alpha != 0f) text.alpha = 0f
        return true
    }

    private fun capsuleAnchor(host: ViewGroup): View? = capsuleIds.firstNotNullOfOrNull { id ->
        host.rootView.findViewById<View>(id)?.takeIf {
            it.isShown && it.width in 1..(host.width * 3 / 4) && it.height in 1 until host.height
        }
    }

    private fun restore(row: View) {
        overlayHost?.overlay?.remove(label)
        overlayHost = null
        val params = nativeParams
        if (params != null && row.layoutParams === params && params.width == 0 && nativeWidth != 0) {
            params.width = nativeWidth
            row.layoutParams = params
        }
        nativeParams = null
        val text = textRef.get()
        val alpha = nativeTextAlpha
        if (text != null && alpha != null && text.alpha == 0f) text.alpha = alpha
        nativeTextAlpha = null
    }

    private class SpeedLabel : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var value = ""
        private var x = 0f
        private var baseline = 0f

        fun update(text: String, anchorX: Float, anchorBaseline: Float, style: Paint) {
            if (value == text && x == anchorX && baseline == anchorBaseline &&
                paint.color == style.color && paint.textSize == style.textSize &&
                paint.typeface == style.typeface && paint.textScaleX == style.textScaleX
            ) return
            value = text
            x = anchorX
            baseline = anchorBaseline
            paint.set(style)
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) = canvas.drawText(value, x, baseline, paint)
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Required by Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
