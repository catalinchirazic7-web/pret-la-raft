package com.example.shoppinglist.ocr

/**
 * Un preț găsit în text.
 * [rank] reflectă cât de sigur este tiparul: "12,49 lei" > "12,49" > "4 lei".
 */
data class PriceCandidate(
    val value: Double,
    val matchedText: String,
    val rank: Int,
)

object PriceParser {

    private const val MIN_PRICE = 0.10
    private const val MAX_PRICE = 9_999.0

    private const val RANK_INTEGER_LEI = 1
    private const val RANK_DECIMAL = 2
    private const val RANK_CURRENCY = 3

    /** "9 lei 99", "9 lei99" */
    private val LEI_SPLIT = Regex("""(?<![\d.,/])(\d{1,4})\s*lei\s*(\d{2})(?!\d)""", RegexOption.IGNORE_CASE)

    /** "12,49", "12.49", opțional urmat de "lei" */
    private val DECIMAL = Regex(
        """(?<![\d.,/])(\d{1,4})[.,](\d{2})(?!\d)(?:\s*(lei|ron)\b)?""",
        RegexOption.IGNORE_CASE,
    )

    /** "4 lei" (fără zecimale) */
    private val INTEGER_LEI = Regex("""(?<![\d.,/])(\d{1,4})\s*(?:lei|ron)\b""", RegexOption.IGNORE_CASE)

    /** Prețuri pe unitate: "1 kg = 13,98 lei" nu sunt prețul de raft. */
    private val UNIT_PRICE = Regex(
        """\d+(?:[.,]\d+)?\s*(?:kg|gr|g|ml|cl|l|buc)\s*=\s*\d+(?:[.,]\d+)?\s*(?:lei|ron)?""",
        RegexOption.IGNORE_CASE,
    )

    /** Gramaje și volume: "250g", "1000ml", "1,5 l". "lei" nu se potrivește din cauza lookahead-ului. */
    private val QUANTITY = Regex(
        """\b\d+(?:[.,]\d+)?\s*(?:kg|gr|g|ml|cl|l|buc)(?![a-zăâîșțş])""",
        RegexOption.IGNORE_CASE,
    )

    /** Coduri de bare EAN/GTIN (8 până la 14 cifre). */
    private val BARCODE = Regex("""\b\d{8,14}\b""")

    /** Ani din intervalul 2020–2039, ex. "2026", "KW 44/2026". */
    private val YEAR = Regex("""\b20[2-3]\d\b""")

    /** Date calendaristice: "26.10.2026", "26.10 până", "până miercuri 28.10", "valabil 26.10". */
    private val FULL_DATE = Regex("""\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b""")
    private val DATE_NEAR_WORDS = Regex(
        """(?i)(?:până|pana|valabil\w*|luni|mart[ie]|miercuri|joi|vineri|sâmb[ăa]t[ăa]|duminic[ăa])\W{0,12}\d{1,2}[./]\d{1,2}\b|\b\d{1,2}[./]\d{1,2}\W{0,12}(?:până|pana|luni|mart[ie]|miercuri|joi|vineri|sâmb[ăa]t[ăa]|duminic[ăa])\b"""
    )

    fun parse(raw: String): List<PriceCandidate> {
        val text = sanitize(raw)
        val candidates = mutableListOf<PriceCandidate>()

        LEI_SPLIT.findAll(text).forEach { match ->
            candidate(match.value, match.groupValues[1], match.groupValues[2], RANK_CURRENCY)?.let(candidates::add)
        }
        DECIMAL.findAll(text).forEach { match ->
            val rank = if (match.groupValues[3].isNotEmpty()) RANK_CURRENCY else RANK_DECIMAL
            candidate(match.value, match.groupValues[1], match.groupValues[2], rank)?.let(candidates::add)
        }
        INTEGER_LEI.findAll(text).forEach { match ->
            candidate(match.value, match.groupValues[1], "00", RANK_INTEGER_LEI)?.let(candidates::add)
        }
        return candidates
    }

    private fun candidate(matched: String, integerPart: String, cents: String, rank: Int): PriceCandidate? {
        val value = "$integerPart.$cents".toDoubleOrNull() ?: return null
        if (value < MIN_PRICE || value > MAX_PRICE) return null
        return PriceCandidate(value = value, matchedText = matched.trim(), rank = rank)
    }

    private fun sanitize(raw: String): String {
        var text = raw
        text = FULL_DATE.replace(text, " ")
        text = DATE_NEAR_WORDS.replace(text, " ")
        text = UNIT_PRICE.replace(text, " ")
        text = QUANTITY.replace(text, " ")
        text = BARCODE.replace(text, " ")
        text = YEAR.replace(text, " ")
        return text
    }
}
