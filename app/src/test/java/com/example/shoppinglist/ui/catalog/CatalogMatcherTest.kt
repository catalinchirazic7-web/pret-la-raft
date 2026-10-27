package com.example.shoppinglist.ui.catalog

import com.example.shoppinglist.data.CatalogProduct
import com.example.shoppinglist.data.CatalogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMatcherTest {

    private fun product(id: Long, title: String) = CatalogProduct(
        id = id,
        storeName = CatalogStore.LIDL,
        title = title,
        price = 6.49,
        category = "Alimente",
        validUntil = 0L,
    )

    @Test
    fun matchesIgnoringWeightAndDiacritics() {
        val catalog = listOf(product(1, "Lapte integral 1L"), product(2, "Iaurt natural 400g"))
        assertEquals(1L, CatalogMatcher.bestMatch("LAPTE INTEGRAL", catalog)!!.id)
    }

    @Test
    fun picksTheMostSimilarProduct() {
        val catalog = listOf(
            product(1, "Ulei de masline"),
            product(2, "Ulei floarea soarelui 1L"),
        )
        assertEquals(2L, CatalogMatcher.bestMatch("Ulei floarea soarelui", catalog)!!.id)
    }

    @Test
    fun returnsNullBelowThreshold() {
        val catalog = listOf(product(1, "Lapte integral"))
        assertNull(CatalogMatcher.bestMatch("Iaurt grecesc", catalog))
    }

    @Test
    fun stopWordsDoNotCreateAMatch() {
        // Doar „lapte” se potrivește; „de” și „vaca” nu contează, deci scorul e sub prag.
        val catalog = listOf(product(1, "Lapte integral"))
        assertNull(CatalogMatcher.bestMatch("Lapte de vaca", catalog))
    }

    @Test
    fun emptyTitleReturnsNull() {
        assertNull(CatalogMatcher.bestMatch("1L 250g", listOf(product(1, "Lapte"))))
    }
}
