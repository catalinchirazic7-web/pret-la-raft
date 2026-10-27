package com.example.shoppinglist.data

import androidx.room.TypeConverter

/** Magazinele din care provin produsele din catalog (oferte). */
enum class CatalogStore(val displayName: String) {
    LIDL("Lidl"),
    PENNY("Penny");

    fun toTargetStore(): TargetStore = TargetStore.valueOf(name)
}

/** Magazinul în care se cumpără un articol din lista de cumpărături. */
enum class TargetStore(val displayName: String) {
    LIDL("Lidl"),
    PENNY("Penny"),
    GENERAL("General");
}

class StoreConverters {
    @TypeConverter
    fun catalogStoreToString(store: CatalogStore): String = store.name

    @TypeConverter
    fun stringToCatalogStore(value: String): CatalogStore = CatalogStore.valueOf(value)

    @TypeConverter
    fun targetStoreToString(store: TargetStore): String = store.name

    @TypeConverter
    fun stringToTargetStore(value: String): TargetStore = TargetStore.valueOf(value)
}
