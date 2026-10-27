package com.example.shoppinglist.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Sursa JSON a catalogului: copia descărcată (dacă există) sau fișierul din assets.
 */
class CatalogJsonStore(private val context: Context) {

    private val cacheDir = File(context.filesDir, "catalog")
    private val cacheFile = File(cacheDir, ASSET_NAME)

    /** Copia descărcată anterior, sau null dacă nu există sau nu poate fi citită. */
    fun readCachedJson(): String? {
        if (!cacheFile.isFile) return null
        return try {
            cacheFile.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            Log.w(TAG, "Copia descărcată nu poate fi citită", e)
            null
        }
    }

    /** Fișierul livrat cu aplicația. */
    fun readAssetJson(): String =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Salvează atomic o copie descărcată, astfel încât o citire să nu vadă un fișier pe jumătate scris. */
    fun saveCachedJson(json: String) {
        cacheDir.mkdirs()
        val temporary = File(cacheDir, "$ASSET_NAME.tmp")
        temporary.writeText(json, Charsets.UTF_8)
        Files.move(
            temporary.toPath(),
            cacheFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    companion object {
        const val ASSET_NAME = "catalog_products.json"
        private const val TAG = "CatalogJsonStore"
    }
}
