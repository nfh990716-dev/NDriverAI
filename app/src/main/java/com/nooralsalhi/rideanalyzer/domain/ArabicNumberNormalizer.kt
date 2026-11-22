package com.nooralsalhi.rideanalyzer.domain

object ArabicNumberNormalizer {
    private val digitMap = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    fun normalize(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when {
                digitMap.containsKey(ch) -> append(digitMap[ch])
                ch == '٫' -> append('.')
                ch == '٬' -> append(',')
                ch == '\u200f' || ch == '\u200e' || ch == '\u202a' || ch == '\u202b' || ch == '\u202c' -> Unit
                else -> append(ch)
            }
        }
    }

    /** Treats 5,000 as thousands and 8.5 as a decimal. */
    fun parseNumber(raw: String): Double? {
        var s = normalize(raw).filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isBlank()) return null
        if (s.contains(',') && s.contains('.')) {
            val lastComma = s.lastIndexOf(',')
            val lastDot = s.lastIndexOf('.')
            val decimal = maxOf(lastComma, lastDot)
            val decimals = s.length - decimal - 1
            s = if (decimals in 1..2) {
                s.substring(0, decimal).replace(",", "").replace(".", "") + "." + s.substring(decimal + 1)
            } else s.replace(",", "").replace(".", "")
        } else if (s.contains(',') || s.contains('.')) {
            val sep = if (s.contains(',')) ',' else '.'
            val pieces = s.split(sep)
            s = if (pieces.size == 2 && pieces[1].length in 1..2) {
                pieces[0] + "." + pieces[1]
            } else pieces.joinToString("")
        }
        return s.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
}
