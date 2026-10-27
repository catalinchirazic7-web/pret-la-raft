package com.example.shoppinglist.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_products")
data class CatalogProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: CatalogStore,
    val title: String,
    val price: Double,
    val category: String,
    /** Data de valabilitate a ofertei, în milisecunde (epoch UTC). */
    val validUntil: Long,
)
