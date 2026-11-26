package io.github.odomu.honorduo.ui.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.odomu.honorduo.model.Prefs

/** Upright Wi-Fi shapes in a 48 × 36 box, shared by the status bar and merged icon. */
internal object WifiIconRenderer {
    private val threeRadii = floatArrayOf(10f, 18f, 26f)
    private val fourRadii = floatArrayOf(6f, 12.5f, 19f, 25.5f)

    private class LoopPaths(count: Int) {
        val arcs = Array(count) { index ->
            val factor = (index + 1f) / count
            Path().apply {
                moveTo(24f + 19f * factor, 33f - 23f * factor)
                cubicTo(24f + 9f * factor, 33f - 34f * factor, 24f - 10f * factor,
                    33f - 34f * factor, 24f - 20f * factor, 33f - 24f * factor)
                cubicTo(24f - 23f * factor, 33f - 20f * factor, 24f - 19f * factor,
                    33f - 17f * factor, 24f - 15f * factor, 33f - 20f * factor)
            }
        }
    }
    private val loopThree = LoopPaths(3)
    private val loopFour = LoopPaths(4)

    fun color(preset: Int): Int? = when (preset) {
        2 -> 0xff005fb5.toInt()
        3 -> 0xff43a047.toInt()
        4 -> 0xffef4999.toInt()
        else -> null
    }

    fun draw(
        canvas: Canvas,
        box: RectF,
        style: Int,
        fourArcs: Boolean,
        level: Int,
        color: Int,
        inactiveAlpha: Int,
        stroke: Int,
        paint: Paint,
    ) {
        val scale = minOf(box.width() / 48f, box.height() / 36f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(box.centerX() - 24f * scale, box.centerY() - 18f * scale)
        canvas.scale(scale, scale)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f + stroke * 0.28f
        val inactive = GlyphInk.withAlpha(color, (color ushr 24) * inactiveAlpha / 255)
        val radii = if (fourArcs) fourRadii else threeRadii
        val active = if (fourArcs) level else (level - 1).coerceAtLeast(0)
        if (style == 2) {
            drawFan(canvas, radii.size, if (fourArcs) level else level.coerceAtMost(3),
                color, inactive, stroke, paint)
            canvas.restoreToCount(saved)
            return
        }
        val loopPaths = if (fourArcs) loopFour.arcs else loopThree.arcs
        val cy = if (fourArcs) 34f else 33f

        for (i in radii.indices) {
            paint.color = if (i < active) color else inactive
            val r = radii[i]
            when (style) {
                1 -> canvas.drawPath(loopPaths[i], paint)
                else -> canvas.drawArc(24f - r, cy - r, 24f + r, cy + r, 224f, 92f, false, paint)
            }
        }
        if (!fourArcs) {
            paint.style = Paint.Style.FILL
            paint.color = if (level > 0) color else inactive
            canvas.drawCircle(24f, 33f, paint.strokeWidth * 0.66f, paint)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawFan(
        canvas: Canvas, bands: Int, active: Int, color: Int, inactive: Int, stroke: Int, paint: Paint,
    ) {
        val cx = 24f
        val cy = 34f
        val radius = 30f + (stroke - Prefs.DEF_ARC_STROKE) * 0.27f
        val bandWidth = radius / bands
        val gap = (Prefs.DEF_ARC_STROKE - stroke).coerceAtLeast(0) * 0.26f
        val path = Path()
        paint.style = Paint.Style.FILL
        for (i in bands - 1 downTo 0) {
            val outer = (i + 1) * bandWidth
            val inner = i * bandWidth
            val tintAlpha = 0.6f + 0.4f * i / (bands - 1)
            val fill = if (i < active) color else inactive
            paint.color = GlyphInk.withAlpha(fill, ((fill ushr 24) * tintAlpha).toInt())
            path.rewind()
            path.arcTo(cx - outer, cy - outer, cx + outer, cy + outer, 225f, 90f, true)
            if (i == 0) {
                path.lineTo(cx, cy)
            } else {
                val edge = inner + gap
                path.arcTo(cx - edge, cy - edge, cx + edge, cy + edge, 315f, -90f, false)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }
}
