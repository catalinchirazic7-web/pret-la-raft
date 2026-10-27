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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Produsele valabile din cataloage, filtrate după categoria selectată. */
class CatalogViewModel(
    repository: ShoppingRepository,
    nowMillis: Long,
) : ViewModel() {

    private val selected = MutableStateFlow(CatalogCategory.ALL)

    val selectedCategory: StateFlow<CatalogCategory> = selected.asStateFlow()

    val products: StateFlow<List<CatalogProduct>> = combine(
        repository.observeActiveProducts(nowMillis),
        selected,
    ) { all, category ->
        if (category == CatalogCategory.ALL) {
            all
        } else {
            all.filter { CatalogCategory.of(it.category, it.title) == category }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    fun selectCategory(category: CatalogCategory) {
        selected.value = category
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getInstance(context)
                CatalogViewModel(
                    repository = ShoppingRepository(database.catalogDao(), database.shoppingDao()),
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }
}
