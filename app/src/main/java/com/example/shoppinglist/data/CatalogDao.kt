package com.example.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<CatalogProduct>): List<Long>

    /** Numără ofertele încă valabile la momentul [now] (milisecunde epoch). */
    @Query("SELECT COUNT(*) FROM catalog_products WHERE validUntil >= :now")
    suspend fun countActiveProducts(now: Long): Int

    @Query("SELECT * FROM catalog_products WHERE validUntil >= :now ORDER BY title")
    fun observeActive(now: Long): Flow<List<CatalogProduct>>

    @Query("SELECT price FROM catalog_products WHERE id = :id")
    suspend fun getPrice(id: Long): Double?

    @Query("DELETE FROM catalog_products WHERE validUntil < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM catalog_products")
    suspend fun deleteAll()

    /**
     * Caută produse după titlu (fără diferențiere între majuscule/minuscule pentru caractere ASCII).
     * Parametrul [query] trebuie escapat pentru LIKE înainte de apel (vezi ShoppingRepository).
     */
    @Query(
        """
        SELECT * FROM catalog_products
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY title
        LIMIT :limit
        """
    )
    fun searchProducts(query: String, limit: Int): Flow<List<CatalogProduct>>
}
