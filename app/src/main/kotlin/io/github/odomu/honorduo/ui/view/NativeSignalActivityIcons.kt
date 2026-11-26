package io.github.odomu.honorduo.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

/** The device's own mobile-data activity drawables, trimmed to their visible ink. */
internal class NativeSignalActivityIcons private constructor(private val icons: Array<Bitmap>) {
    fun forDirection(direction: Int): Bitmap = when (direction) {
        1 -> icons[0]
        2 -> icons[1]
        else -> icons[2]
    }

    companion object {
        fun load(context: Context): NativeSignalActivityIcons? = runCatching {
            val resources = context.resources
            val names = arrayOf("stat_sys_signal_in", "stat_sys_signal_out", "stat_sys_signal_inout")
            val source = names.map { name ->
                val id = resources.getIdentifier(name, "drawable", "com.android.systemui")
                if (id == 0) return@runCatching null
                val drawable = resources.getDrawable(id, context.theme).mutate()
                drawable.setTint(Color.WHITE)
                Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).also { bitmap ->
                    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                    drawable.draw(Canvas(bitmap))
                }
            }
            // All three states share geometry. Use their union to avoid jitter when the
            // inactive half is translucent or absent in a particular system theme.
            var left = 160
            var top = 120
            var right = -1
            var bottom = -1
            val pixels = IntArray(160 * 120)
            for (bitmap in source) {
                bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 120)
                for (y in 0 until 120) for (x in 0 until 160) {
                    if (Color.alpha(pixels[y * 160 + x]) > 8) {
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
            if (right < left || bottom < top) return@runCatching null
            val trimmed = source.map { bitmap ->
                Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
            }.toTypedArray()
            source.forEachIndexed { index, bitmap ->
                if (bitmap !== trimmed[index]) bitmap.recycle()
            }
            NativeSignalActivityIcons(trimmed)
        }.getOrNull()
    }
}
