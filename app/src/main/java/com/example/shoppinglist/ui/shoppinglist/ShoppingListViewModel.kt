package com.example.shoppinglist.ui.shoppinglist

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.shoppinglist.data.AppDatabase
import com.example.shoppinglist.data.CatalogProduct
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.ShoppingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    /** Articolele vizibile pentru filtrul selectat. */
    val items: List<ShoppingItem> = emptyList(),
    /** Totalul estimat al produselor nebifate din magazinul selectat. */
    val estimatedTotal: Double = 0.0,
    val storeFilter: StoreFilter = StoreFilter.ALL,
    val query: String = "",
    val suggestions: List<CatalogProduct> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(
    private val repository: ShoppingRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val errorMessage = MutableStateFlow<String?>(null)
    private val storeFilter = MutableStateFlow(StoreFilter.ALL)

    private val suggestions: Flow<List<CatalogProduct>> = query
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isBlank()) flowOf(emptyList()) else repository.searchCatalog(text)
        }

    val uiState: StateFlow<ShoppingListUiState> = combine(
        repository.observeShoppingItems(),
        query,
        suggestions,
        errorMessage,
        storeFilter,
    ) { items, currentQuery, matches, error, selected ->
        ShoppingListUiState(
            items = items.visibleFor(selected),
            estimatedTotal = estimatedTotalFor(items, selected),
            storeFilter = selected,
            query = currentQuery,
            suggestions = matches,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ShoppingListUiState(),
    )

    fun onQueryChanged(value: String) {
        query.value = value
    }

    fun onStoreFilterSelected(filter: StoreFilter) {
        storeFilter.value = filter
    }

    fun dismissError() {
        errorMessage.value = null
    }

    /** Adaugă textul scris: dacă se potrivește exact cu un produs din catalog, folosește prețul din catalog. */
    fun addTypedItem() {
        val state = uiState.value
        val typed = state.query.trim()
        if (typed.isEmpty()) {
            errorMessage.value = "Scrie numele produsului înainte de adăugare."
            return
        }
        val exactMatch = state.suggestions.firstOrNull { it.title.equals(typed, ignoreCase = true) }
        if (exactMatch != null) {
            addFromCatalog(exactMatch)
        } else {
            addFreeText(typed)
        }
    }

    fun addFromCatalog(product: CatalogProduct) {
        addProductToList(product)
        query.value = ""
    }

    /** Adaugă un produs din catalog în listă, fără să modifice textul de căutare. */
    fun addProductToList(product: CatalogProduct) {
        launchSafely {
            repository.addItem(
                ShoppingItem(
                    title = product.title,
                    targetStore = product.storeName.toTargetStore(),
                    estimatedPrice = product.price,
                    catalogProductId = product.id,
                ),
            )
        }
    }

    fun toggleBought(item: ShoppingItem) {
        launchSafely { repository.setBought(item.id, !item.isBought) }
    }

    fun deleteItem(item: ShoppingItem) {
        launchSafely { repository.deleteItem(item.id) }
    }

    private fun addFreeText(title: String) {
        launchSafely {
            repository.addItem(ShoppingItem(title = title))
            query.value = ""
        }
    }

    private fun launchSafely(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                errorMessage.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Operația pe baza de date a eșuat", e)
                errorMessage.value = "Modificarea nu a putut fi salvată. Încearcă din nou."
            }
        }
    }

    companion object {
        private const val TAG = "ShoppingListViewModel"
        private const val SEARCH_DEBOUNCE_MS = 200L
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getInstance(context)
                ShoppingListViewModel(
                    ShoppingRepository(database.catalogDao(), database.shoppingDao()),
                )
            }
        }
    }
}
