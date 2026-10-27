package com.example.shoppinglist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    /** Articolele nebifate apar primele, apoi grupate pe magazin și sortate alfabetic. */
    @Query(
        """
        SELECT * FROM shopping_items
        ORDER BY isBought ASC, targetStore ASC, title ASC
        """
    )
    fun observeItems(): Flow<List<ShoppingItem>>

    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Query("UPDATE shopping_items SET isBought = :isBought WHERE id = :id")
    suspend fun setBought(id: Long, isBought: Boolean): Int

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    fun observeItem(id: Long): Flow<ShoppingItem?>

    @Query("UPDATE shopping_items SET estimatedPrice = :price WHERE id = :id")
    suspend fun setEstimatedPrice(id: Long, price: Double): Int

    /** Totalul articolelor din coș (bifate) pentru magazinul dat. */
    @Query("SELECT COALESCE(SUM(estimatedPrice), 0.0) FROM shopping_items WHERE isBought = 1 AND targetStore = :store")
    fun observeCartTotal(store: TargetStore): Flow<Double>

    /** Ultimele articole adăugate în coș pentru magazinul dat. */
    @Query(
        """
        SELECT * FROM shopping_items
        WHERE isBought = 1 AND targetStore = :store
        ORDER BY id DESC
        LIMIT :limit
        """
    )
    fun observeRecentCart(store: TargetStore, limit: Int): Flow<List<ShoppingItem>>

    /** Marchează un articol existent ca fiind în coș, cu denumirea și prețul confirmate. */
    @Query("UPDATE shopping_items SET title = :title, estimatedPrice = :price, isBought = 1 WHERE id = :id")
    suspend fun updateCartItem(id: Long, title: String, price: Double): Int
}
