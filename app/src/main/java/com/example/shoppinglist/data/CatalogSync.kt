package com.example.shoppinglist.data

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

sealed interface CatalogSyncResult {
    /** Nu este configurat un URL; se folosește catalogul local. */
    data object NotConfigured : CatalogSyncResult
    data object Updated : CatalogSyncResult
    /** Descărcarea a eșuat; rămâne catalogul local (copia anterioară sau cel din assets). */
    data class Failed(val reason: String) : CatalogSyncResult
}

/**
 * Descarcă catalogul actualizat de la [url] (de obicei fișierul raw din GitHub).
 * Timeout-urile socket-ului limitează durata: o descărcare blocată nu ține splash-ul la infinit.
 */
class CatalogSync(
    private val store: CatalogJsonStore,
    private val url: String,
) {

    suspend fun refresh(): CatalogSyncResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext CatalogSyncResult.NotConfigured
        try {
            val json = download(url)
            validate(json)
            store.saveCachedJson(json)
            Log.i(TAG, "Catalog actualizat de la $url")
            CatalogSyncResult.Updated
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Descărcarea catalogului a eșuat, rămân datele locale", e)
            CatalogSyncResult.Failed("Nu s-a putut descărca catalogul.")
        } catch (e: JSONException) {
            Log.w(TAG, "Catalogul descărcat are un format invalid", e)
            CatalogSyncResult.Failed("Catalogul descărcat este invalid.")
        }
    }

    private fun download(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Un catalog gol sau care nu este un array JSON nu înlocuiește datele existente. */
    private fun validate(json: String) {
        val array = JSONArray(json)
        if (array.length() == 0) throw JSONException("Catalog gol")
    }

    private companion object {
        const val TAG = "CatalogSync"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 8_000
    }
}
