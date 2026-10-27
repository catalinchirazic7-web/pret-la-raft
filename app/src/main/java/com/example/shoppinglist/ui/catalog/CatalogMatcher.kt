package com.example.shoppinglist.ui.catalog

import com.example.shoppinglist.data.CatalogProduct

/**
 * Găsește în catalogul (pliantul) activ produsul corespunzător denumirii citite de pe raft.
 * Compară cuvintele, fără diacritice și fără cifre (gramaje, coduri), cu similaritatea Jaccard.
 */
object CatalogMatcher {

    /** Similaritatea minimă (0..1) sub care nu considerăm că e același produs. */
    const val MIN_SIMILARITY = 0.5

    private val STOP_WORDS = setOf("si", "de", "cu", "din", "la", "pentru", "fara", "in")

    /** Produsul din [candidates] cel mai asemănător cu [title], sau null dacă nu există unul suficient de asemănător. */
    fun bestMatch(title: String, candidates: List<CatalogProduct>): CatalogProduct? {
        val wanted = words(title)
        if (wanted.isEmpty()) return null
        return candidates
            .map { product -> product to similarity(wanted, words(product.title)) }
            .filter { (_, score) -> score >= MIN_SIMILARITY }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    /** Cuvintele relevante ale unei denumiri: litere, fără cifre și fără cuvinte de legătură. */
    fun words(text: String): Set<String> =
        CatalogCategory.tokens(text)
            .filter { word -> word.length >= 2 && word.none(Char::isDigit) && word !in STOP_WORDS }
            .toSet()

    fun similarity(first: Set<String>, second: Set<String>): Double {
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val common = first.intersect(second).size
        val total = first.union(second).size
        return common.toDouble() / total
    }
}
