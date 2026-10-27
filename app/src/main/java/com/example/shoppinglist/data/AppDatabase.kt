package com.example.shoppinglist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CatalogProduct::class, ShoppingItem::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(StoreConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        private const val DATABASE_NAME = "shopping_list.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
    }
}
