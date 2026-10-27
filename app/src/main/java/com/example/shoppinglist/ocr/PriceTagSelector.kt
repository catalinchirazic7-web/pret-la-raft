package com.example.shoppinglist.ocr

import java.util.Locale

/** Dreptunghi în coordonatele imaginii (după rotire, adică în orientarea în care se vede eticheta). */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** O linie de text recunoscută de OCR, cu înălțimea și poziția ei. */
data class OcrLine(val text: String, val heightPx: Float, val box: PixelBox?)

enum class StoreHint { PENNY, LIDL }

/** Prețul ales și linia de text din care a fost citit (folosită pentru a găsi denumirea). */
data class PriceSelection(val candidate: PriceCandidate, val line: OcrLine)

/**
 * Alege prețul de raft dintre mai mulți candidați.
 *
 * - Penny: cardul de preț promoțional este roșu, deci candidații din zone roșii au prioritate.
 * - Lidl: prețul vechi este tăiat și mai mic, iar prețul nou are fontul cel mai mare.
 *   Se alege strict linia cu înălțimea cea mai mare.
 * - Magazin necunoscut: se aplică prioritatea roșie, apoi înălțimea.
 */
object PriceTagSelector {

    /** Fracțiunea minimă de pixeli roșii dintr-o linie ca să fie considerată „pe fundal roșu”. */
    const val RED_FRACTION_THRESHOLD = 0.3f

    private data class Option(
        val candidate: PriceCandidate,
        val line: OcrLine,
        val isRed: Boolean,
    )

    fun storeHint(lines: List<OcrLine>): StoreHint? {
        val allText = lines.joinToString(separator = " ") { it.text }.lowercase(Locale.ROOT)
        return when {
            "penny" in allText -> StoreHint.PENNY
            "lidl" in allText -> StoreHint.LIDL
            else -> null
        }
    }

    /**
     * @param redFraction întoarce fracțiunea de pixeli roșii dintr-un dreptunghi al imaginii.
     */
    fun select(lines: List<OcrLine>, redFraction: (PixelBox) -> Float): PriceSelection? {
        val options = lines.flatMap { line ->
            val red = line.box?.let(redFraction) ?: 0f
            PriceParser.parse(line.text).map { candidate ->
                Option(
                    candidate = candidate,
                    line = line,
                    isRed = red >= RED_FRACTION_THRESHOLD,
                )
            }
        }
        if (options.isEmpty()) return null

        val chosen = when (storeHint(lines)) {
            StoreHint.LIDL -> options.maxWith(
                compareBy<Option>({ it.line.heightPx }, { it.candidate.rank }),
            )
            StoreHint.PENNY, null -> options.maxWith(
                compareBy<Option>({ it.isRed }, { it.line.heightPx }, { it.candidate.rank }),
            )
        }
        return PriceSelection(candidate = chosen.candidate, line = chosen.line)
    }
}
