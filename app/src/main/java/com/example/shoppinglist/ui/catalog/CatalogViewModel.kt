package com.example.shoppinglist.ui.catalog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.shoppinglist.data.AppDatabase
import com.example.shoppinglist.data.CatalogProduct
import com.example.shoppinglist.data.ShoppingRepository
import com.example.shoppinglist.ui.shoppinglist.StoreFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Produsele valabile din cataloage, filtrate după magazin și după categorie. */
class CatalogViewModel(
    repository: ShoppingRepository,
    nowMillis: Long,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow(CatalogCategory.ALL)
    private val selectedStoreFilter = MutableStateFlow(StoreFilter.ALL)

    val category: StateFlow<CatalogCategory> = selectedCategory.asStateFlow()
    val storeFilter: StateFlow<StoreFilter> = selectedStoreFilter.asStateFlow()

    val products: StateFlow<List<CatalogProduct>> = combine(
        repository.observeActiveProducts(nowMillis),
        selectedCategory,
        selectedStoreFilter,
    ) { all, categoryFilter, storeFilter ->
        all.filter { product ->
            (storeFilter.targetStore == null || product.storeName.toTargetStore() == storeFilter.targetStore) &&
                (categoryFilter == CatalogCategory.ALL ||
                    CatalogCategory.of(product.category, product.title) == categoryFilter)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    fun selectCategory(category: CatalogCategory) {
        selectedCategory.value = category
    }

    fun selectStore(filter: StoreFilter) {
        selectedStoreFilter.value = filter
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getInstance(context.applicationContext)
                CatalogViewModel(
                    repository = ShoppingRepository(database.catalogDao(), database.shoppingDao()),
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }
}
