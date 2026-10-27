package com.example.shoppinglist.data

import kotlinx.coroutines.flow.Flow

class ShoppingRepository(
    private val catalogDao: CatalogDao,
    private val shoppingDao: ShoppingDao,
) {

    fun observeShoppingItems(): Flow<List<ShoppingItem>> = shoppingDao.observeItems()

    fun searchCatalog(query: String): Flow<List<CatalogProduct>> =
        catalogDao.searchProducts(escapeLike(query.trim()), SUGGESTION_LIMIT)

    fun observeActiveProducts(nowMillis: Long): Flow<List<CatalogProduct>> =
        catalogDao.observeActive(nowMillis)

    suspend fun addItem(item: ShoppingItem): Long = shoppingDao.insert(item)

    suspend fun setBought(id: Long, isBought: Boolean) {
        shoppingDao.setBought(id, isBought)
    }

    suspend fun deleteItem(id: Long) {
        shoppingDao.deleteById(id)
    }

    fun observeShoppingItem(id: Long): Flow<ShoppingItem?> = shoppingDao.observeItem(id)

    suspend fun updateEstimatedPrice(id: Long, price: Double) {
        shoppingDao.setEstimatedPrice(id, price)
    }

    suspend fun catalogPriceFor(catalogProductId: Long): Double? = catalogDao.getPrice(catalogProductId)

    private fun escapeLike(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private companion object {
        const val SUGGESTION_LIMIT = 8
    }
}
