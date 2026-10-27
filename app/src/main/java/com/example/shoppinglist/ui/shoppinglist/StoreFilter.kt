package com.example.shoppinglist.ui.shoppinglist

import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.TargetStore

/** Filtrul de magazin din lista de cumpărături. */
enum class StoreFilter(val label: String, private val store: TargetStore?) {
    ALL("Toate", null),
    LIDL("Lidl", TargetStore.LIDL),
    PENNY("Penny", TargetStore.PENNY);

    fun matches(item: ShoppingItem): Boolean = store == null || item.targetStore == store
}

/** Articolele vizibile pentru filtrul dat. */
fun List<ShoppingItem>.visibleFor(selected: StoreFilter): List<ShoppingItem> =
    filter(selected::matches)

/** Totalul estimat al produselor nebifate, doar din magazinul selectat. */
fun estimatedTotalFor(items: List<ShoppingItem>, selected: StoreFilter): Double =
    items.filter { !it.isBought && selected.matches(it) }.sumOf { it.estimatedPrice }
