package com.example.shoppinglist.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = CatalogProduct::class,
            parentColumns = ["id"],
            childColumns = ["catalogProductId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["catalogProductId"])],
)
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetStore: TargetStore = TargetStore.GENERAL,
    val estimatedPrice: Double = 0.0,
    val isBought: Boolean = false,
    val catalogProductId: Long? = null,
)
