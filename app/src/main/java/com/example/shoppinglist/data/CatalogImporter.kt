package com.example.shoppinglist.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

sealed interface CatalogImportResult {
    /** Catalogul din tabelă este cel curent și conține oferte valabile; nu s-a importat nimic. */
    data object AlreadyActive : CatalogImportResult
    data class Imported(val count: Int, val skipped: Int) : CatalogImportResult
    data class Failed(val reason: String) : CatalogImportResult
}

/**
 * Importă catalogul JSON (copia descărcată sau cel din assets) în tabela `catalog_products`.
 *
 * Reimportă când:
 *  - conținutul JSON s-a schimbat față de ultimul import (amprentă SHA-256), sau
 *  - tabela nu mai are oferte valabile.
 * Ofertele expirate se șterg la fiecare pornire.
 */
class CatalogImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val store: CatalogJsonStore,
) {

    suspend fun importIfNeeded(nowMillis: Long = System.currentTimeMillis()): CatalogImportResult =
        withContext(Dispatchers.IO) {
            val catalogDao = database.catalogDao()
            try {
                val json = loadJson()
                val parsed = parse(json, nowMillis)
                val version = sha256(json)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

                catalogDao.deleteExpired(nowMillis)
                val sameVersion = prefs.getString(KEY_VERSION, null) == version
                if (sameVersion && catalogDao.countActiveProducts(nowMillis) > 0) {
                    return@withContext CatalogImportResult.AlreadyActive
                }

                database.withTransaction {
                    catalogDao.deleteAll()
                    catalogDao.insertProducts(parsed.products)
                }
                prefs.edit().putString(KEY_VERSION, version).apply()
                Log.i(TAG, "Importate ${parsed.products.size} produse (${parsed.skipped} ignorate)")
                CatalogImportResult.Imported(parsed.products.size, parsed.skipped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: JSONException) {
                Log.e(TAG, "Catalogul are un format invalid", e)
                CatalogImportResult.Failed("Catalogul are un format invalid.")
            } catch (e: Exception) {
                Log.e(TAG, "Importul catalogului a eșuat", e)
                CatalogImportResult.Failed("Importul catalogului a eșuat.")
            }
        }

    /** Copia descărcată are prioritate; dacă nu se poate parsa, se folosește fișierul din assets. */
    private fun loadJson(): String {
        val cached = store.readCachedJson() ?: return store.readAssetJson()
        return try {
            parse(cached, System.currentTimeMillis())
            cached
        } catch (e: JSONException) {
            Log.w(TAG, "Copia descărcată este invalidă; folosesc catalogul din assets", e)
            store.readAssetJson()
        }
    }

    private data class ParsedCatalog(val products: List<CatalogProduct>, val skipped: Int)

    private fun parse(json: String, nowMillis: Long): ParsedCatalog {
        val array = JSONArray(json)
        val products = ArrayList<CatalogProduct>(array.length())
        var skipped = 0
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val storeName = parseStore(item.getString("storeName"))
            val validUntil = parseValidUntil(item.getString("validUntil"))
            if (storeName == null || validUntil == null) {
                Log.w(TAG, "Intrare ignorată la indexul $index: magazin sau dată invalidă")
                skipped++
                continue
            }
            if (validUntil < nowMillis) {
                skipped++
                continue
            }
            products += CatalogProduct(
                storeName = storeName,
                title = item.getString("title"),
                price = item.getDouble("price"),
                category = item.optString("category", DEFAULT_CATEGORY),
                validUntil = validUntil,
            )
        }
        return ParsedCatalog(products, skipped)
    }

    private fun parseStore(value: String): CatalogStore? =
        CatalogStore.entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) }

    /** Data din JSON (yyyy-MM-dd) este valabilă până la sfârșitul ei, ora României. */
    private fun parseValidUntil(value: String): Long? =
        try {
            LocalDate.parse(value)
                .atTime(23, 59, 59)
                .atZone(BUCHAREST)
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val TAG = "CatalogImporter"
        const val PREFS_NAME = "catalog_import"
        const val KEY_VERSION = "imported_version"
        const val DEFAULT_CATEGORY = "Generale"
        val BUCHAREST: ZoneId = ZoneId.of("Europe/Bucharest")
    }
}
