package com.example.shoppinglist.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.shoppinglist.BuildConfig
import com.example.shoppinglist.data.AppDatabase
import com.example.shoppinglist.data.CatalogImportResult
import com.example.shoppinglist.data.CatalogImporter
import com.example.shoppinglist.data.CatalogJsonStore
import com.example.shoppinglist.data.CatalogSync
import com.example.shoppinglist.data.CatalogSyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * La pornire: încearcă să descarce catalogul actualizat, apoi îl importă în Room.
 * Dacă descărcarea eșuează, se folosește copia locală sau fișierul din assets.
 * [isAppReady] devine true când pornirea s-a încheiat, indiferent de rezultat,
 * ca aplicația să nu rămână blocată pe ecranul de start.
 */
class MainViewModel(
    private val sync: CatalogSync,
    private val importer: CatalogImporter,
) : ViewModel() {

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady: StateFlow<Boolean> = _isAppReady.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                when (val syncResult = sync.refresh()) {
                    is CatalogSyncResult.Failed -> Log.w(TAG, syncResult.reason)
                    CatalogSyncResult.NotConfigured, CatalogSyncResult.Updated -> Unit
                }
                when (val importResult = importer.importIfNeeded()) {
                    is CatalogImportResult.Failed -> Log.w(TAG, importResult.reason)
                    CatalogImportResult.AlreadyActive, is CatalogImportResult.Imported -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pornirea a eșuat", e)
            } finally {
                _isAppReady.value = true
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val store = CatalogJsonStore(appContext)
                MainViewModel(
                    sync = CatalogSync(store, BuildConfig.CATALOG_URL),
                    importer = CatalogImporter(appContext, AppDatabase.getInstance(appContext), store),
                )
            }
        }
    }
}
