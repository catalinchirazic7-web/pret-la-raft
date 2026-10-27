package com.example.shoppinglist.ui.scanner

private val PRICE_PATTERN = Regex("""\d{1,4}(\.\d{1,2})?""")

private const val MIN_MANUAL_PRICE = 0.01
private const val MAX_MANUAL_PRICE = 9_999.0

/**
 * Transformă textul introdus de utilizator într-un preț (ex. "4.25", "4,25", "12").
 * Întoarce null dacă textul nu este un preț valid între 0,01 și 9999.
 */
fun parseManualPrice(raw: String): Double? {
    val normalized = raw.trim().replace(',', '.')
    if (!PRICE_PATTERN.matches(normalized)) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it in MIN_MANUAL_PRICE..MAX_MANUAL_PRICE }
}
