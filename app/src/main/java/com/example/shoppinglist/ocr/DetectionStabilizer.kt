package com.example.shoppinglist.ocr

/** Preț stabil (confirmat de cadre multiple) și denumirea cea mai frecventă pentru el. */
data class StablePrice(val price: Double, val title: String?)

/**
 * Netezește rezultatele cadrelor OCR:
 *  - un preț devine stabil după [minVotes] apariții în ultimele [window] cadre;
 *  - după o confirmare, auto-deschiderea se rearmează abia după [rearmAfterEmptyFrames]
 *    cadre consecutive fără preț, ca aceeași etichetă să nu redeschidă cardul imediat.
 */
class DetectionStabilizer(
    private val window: Int = 6,
    private val minVotes: Int = 2,
    private val rearmAfterEmptyFrames: Int = 8,
) {

    private val recent = ArrayDeque<ScanResult>()
    private var emptyStreak = 0
    private var armed = true

    /** Dacă un preț stabil poate deschide automat cardul de confirmare. */
    val isArmed: Boolean get() = armed

    fun add(result: ScanResult): StablePrice? {
        emptyStreak = if (result.price == null) emptyStreak + 1 else 0
        if (emptyStreak >= rearmAfterEmptyFrames) armed = true

        recent.addLast(result)
        if (recent.size > window) recent.removeFirst()

        val winner = recent
            .mapNotNull { it.price?.value }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?: return null
        if (winner.value < minVotes) return null

        val title = recent
            .filter { it.price?.value == winner.key }
            .mapNotNull { it.title }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        return StablePrice(price = winner.key, title = title)
    }

    /** Apelat după o confirmare sau o renunțare: golește istoricul și dezarmează auto-deschiderea. */
    fun disarm() {
        armed = false
        emptyStreak = 0
        recent.clear()
    }
}
