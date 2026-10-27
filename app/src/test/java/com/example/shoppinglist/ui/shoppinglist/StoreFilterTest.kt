package com.example.shoppinglist.ui.shoppinglist

import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.TargetStore
import org.junit.Assert.assertEquals
import org.junit.Test

class StoreFilterTest {

    private val items = listOf(
        ShoppingItem(id = 1, title = "Lapte", targetStore = TargetStore.LIDL, estimatedPrice = 6.49),
        ShoppingItem(id = 2, title = "Iaurt", targetStore = TargetStore.PENNY, estimatedPrice = 5.29),
        ShoppingItem(id = 3, title = "Sare", targetStore = TargetStore.GENERAL, estimatedPrice = 2.00),
        ShoppingItem(id = 4, title = "Pâine", targetStore = TargetStore.LIDL, estimatedPrice = 3.99, isBought = true),
    )

    @Test
    fun allShowsEverything() {
        assertEquals(4, items.visibleFor(StoreFilter.ALL).size)
    }

    @Test
    fun lidlShowsOnlyLidl() {
        val visible = items.visibleFor(StoreFilter.LIDL)
        assertEquals(listOf(1L, 4L), visible.map { it.id })
    }

    @Test
    fun pennyShowsOnlyPenny() {
        assertEquals(listOf(2L), items.visibleFor(StoreFilter.PENNY).map { it.id })
    }

    @Test
    fun totalCountsOnlyUnboughtFromSelectedStore() {
        assertEquals(6.49, estimatedTotalFor(items, StoreFilter.LIDL), 0.001)
        assertEquals(5.29, estimatedTotalFor(items, StoreFilter.PENNY), 0.001)
    }

    @Test
    fun allTotalIncludesGeneralButNotBought() {
        assertEquals(6.49 + 5.29 + 2.00, estimatedTotalFor(items, StoreFilter.ALL), 0.001)
    }
}
