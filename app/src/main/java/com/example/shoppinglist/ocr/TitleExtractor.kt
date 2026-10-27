package com.example.shoppinglist.ocr

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Extrage denumirea produsului de pe etichetă: linia cu font mediu/mare, aflată deasupra
 * sau lângă blocul de preț, din care se scot cuvintele tipice de pe etichete.
 */
object TitleExtractor {

    private const val MIN_LETTERS = 3

    /** Linia trebuie să aibă cel puțin această fracțiune din înălțimea celei mai mari linii din cadru. */
    private const val MIN_HEIGHT_RATIO = 0.35f

    /** Distanța maximă față de prețul principal, în înălțimi de font ale prețului. */
    private const val MAX_DISTANCE_IN_PRICE_HEIGHTS = 4f

    /** Cuvinte de zgomot, fără diacritice și cu majuscule. */
    private val NOISE_WORDS = setOf(
        "LEI", "RON", "BUC", "BUCATA", "BUCATI", "KG", "GR", "G", "ML", "CL", "L",
        "PRET", "PRETUL", "VECHI", "PROMO", "PROMOTIE", "REDUCERE", "OFERTA",
        "LIDL", "PENNY", "PLUS", "CARD",
        "VALABIL", "VALABILA", "VALABILE", "PANA", "KW", "PERIOADA",
        "LUNI", "MARTI", "MIERCURI", "JOI", "VINERI", "SAMBATA", "DUMINICA",
    )

    private val WHITESPACE = Regex("\\s+")

    /**
     * Denumirea cea mai probabilă pentru prețul [priceLine], sau null dacă nu există una.
     * Scorul favorizează liniile mari și apropiate de preț.
     */
    fun extract(lines: List<OcrLine>, priceLine: OcrLine?): String? {
        val price = priceLine ?: return null
        val priceBox = price.box ?: return null
        val maxHeight = lines.maxOfOrNull { it.heightPx } ?: return null
        val priceCenter = (priceBox.top + priceBox.bottom) / 2f
        val priceHeight = price.heightPx.coerceAtLeast(1f)

        var best: String? = null
        var bestScore = 0f
        for (line in lines) {
            if (line == price) continue
            val box = line.box ?: continue
            if (line.heightPx < MIN_HEIGHT_RATIO * maxHeight) continue
            if (PriceParser.parse(line.text).isNotEmpty()) continue
            val distance = abs((box.top + box.bottom) / 2f - priceCenter) / priceHeight
            if (distance > MAX_DISTANCE_IN_PRICE_HEIGHTS) continue
            val title = cleanTitle(line.text) ?: continue
            val score = line.heightPx / (1f + distance)
            if (score > bestScore) {
                best = title
                bestScore = score
            }
        }
        return best
    }

    /** Elimină cuvintele de zgomot dintr-o linie; null dacă rămân prea puține litere. */
    fun cleanTitle(raw: String): String? {
        val cores = raw.trim()
            .split(WHITESPACE)
            .map { word -> word.trim { !it.isLetter() } }
            .filter { it.isNotEmpty() }

        val kept = mutableListOf<String>()
        var index = 0
        while (index < cores.size) {
            val current = cores[index]
            val next = cores.getOrNull(index + 1)
            if (normalize(current) == "DE" && next != null && normalize(next) == "LA") {
                index += 2
                continue
            }
            if (!isNoise(current)) kept += current
            index++
        }

        val title = kept.joinToString(separator = " ")
        return title.takeIf { title.count { it.isLetter() } >= MIN_LETTERS }
    }

    private fun isNoise(word: String): Boolean =
        word.any { it.isDigit() } ||
            word.count { it.isLetter() } < 2 ||
            normalize(word) in NOISE_WORDS

    private fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.ROOT)
}
