package io.github.odomu.honorduo.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.withSave
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.render.GlyphInk

/** An upright level display drawn from the existing battery reading. */
internal class StandaloneBatteryView(context: Context) : View(context) {
    var vertical = true
    var level = -1
    var charging = false
    var powerSave = false
    var low = false
    var showNumber = true
    var foreground = Color.BLACK

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val body = RectF()
    private val track = Path()
    private val bolt = Path()
    private val textInk = Rect()

    override fun onDraw(canvas: Canvas) {
        if (level !in 0..100 || width <= 0 || height <= 0) return
        val designW = if (vertical) VERTICAL_WIDTH_DP.toFloat() else 30f
        val designH = if (vertical) VERTICAL_HEIGHT_DP.toFloat() else 17f
        val scaleX = width / designW
        val scaleY = height / designH
        val fill = when {
            charging -> Prefs.COLOR_BATTERY_CHARGING
            powerSave -> 0xFFD59A28.toInt()
            low -> Prefs.DEF_COLOR_LOW_ON_LIGHT
            else -> foreground
        }
        val radius = 3f
        val fraction = level / 100f
        canvas.withSave {
            scale(scaleX, scaleY)
            paint.color = foreground
            if (vertical) {
                body.set(0.75f, 3f, designW - 0.75f, designH - 0.75f)
                drawRoundRect(designW / 2f - 2.6f, 0.4f, designW / 2f + 2.6f, 2.1f, 0.8f, 0.8f, paint)
            } else {
                body.set(0.75f, 0.75f, designW - 3f, designH - 0.75f)
                drawRoundRect(designW - 2.3f, designH / 2f - 2.5f, designW - 0.3f, designH / 2f + 2.5f, 1f, 1f, paint)
            }
            paint.color = GlyphInk.withAlpha(fill, 68)
            drawRoundRect(body, radius, radius, paint)
            val fillTop = body.bottom - body.height() * if (vertical) fraction else 1f
            val fillRight = body.left + body.width() * if (vertical) 1f else fraction
            track.reset()
            track.addRoundRect(body, radius, radius, Path.Direction.CW)
            withSave {
                clipPath(track)
                paint.color = fill
                drawRect(body.left, fillTop, fillRight, body.bottom, paint)
                drawMark(this, foreground)
                withSave {
                    clipRect(body.left, fillTop, fillRight, body.bottom)
                    val contrast = if (Color.luminance(fill) > 0.42f) Color.BLACK else Color.WHITE
                    drawMark(this, contrast)
                }
            }
        }
    }

    private fun drawMark(canvas: Canvas, color: Int) {
        paint.color = color
        if (showNumber) {
            val digits = level.toString()
            paint.textSize = if (vertical) 7.3f else 9.2f
            paint.getTextBounds(digits, 0, digits.length, textInk)
            val y = body.centerY() - if (charging && vertical) 3f else 0f
            canvas.drawText(digits, body.centerX(), y - (textInk.top + textInk.bottom) / 2f, paint)
        }
        if (!charging || (showNumber && !vertical)) return
        val x = body.centerX()
        val y = body.centerY() + if (showNumber) 5f else 0f
        bolt.reset()
        bolt.moveTo(x + 1.1f, y - 3.8f)
        bolt.lineTo(x - 2.7f, y + 0.6f)
        bolt.lineTo(x - 0.4f, y + 0.6f)
        bolt.lineTo(x - 1.1f, y + 3.8f)
        bolt.lineTo(x + 2.7f, y - 0.6f)
        bolt.lineTo(x + 0.4f, y - 0.6f)
        bolt.close()
        canvas.drawPath(bolt, paint)
    }

    companion object {
        const val VERTICAL_WIDTH_DP = 15
        const val VERTICAL_HEIGHT_DP = 26
    }
}
