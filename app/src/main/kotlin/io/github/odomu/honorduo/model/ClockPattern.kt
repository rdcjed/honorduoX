package io.github.odomu.honorduo.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object ClockPattern {
    const val MAX_LENGTH = 64
    private data class Format(val pattern: String, val locale: Locale, val formatter: SimpleDateFormat)
    private val cache = ThreadLocal<Format>()

    fun format(pattern: String, date: Date, locale: Locale = Locale.getDefault()): String? {
        if (pattern.isBlank() || pattern.length > MAX_LENGTH || pattern.any(Char::isISOControl)) return null
        // The cache is keyed by the pattern and locale, and the zone can change while SystemUI lives.
        return try {
            val current = cache.get()
            val fmt = if (current?.pattern == pattern && current.locale == locale) {
                current.formatter
            } else {
                SimpleDateFormat(pattern, locale).also { cache.set(Format(pattern, locale, it)) }
            }
            fmt.timeZone = TimeZone.getDefault()
            fmt.format(date)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun usesSeconds(pattern: String): Boolean {
        var quoted = false
        for (char in pattern) {
            if (char == '\'') quoted = !quoted
            if (!quoted && (char == 's' || char == 'S')) return true
        }
        return false
    }

    fun timeWithSeconds(is24Hour: Boolean): String = if (is24Hour) "HH:mm:ss" else "h:mm:ss"

    fun datePreset(format: Int): String = when (format) {
        Prefs.CLOCK_DATE_SLASH_YMD -> "yyyy/MM/dd"
        Prefs.CLOCK_DATE_ZH_MD -> "M月d日"
        Prefs.CLOCK_DATE_ZH_YMD -> "yyyy年M月d日"
        else -> "MM/dd"
    }
}
