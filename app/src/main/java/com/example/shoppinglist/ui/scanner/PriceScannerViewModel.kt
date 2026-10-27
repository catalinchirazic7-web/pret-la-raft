package com.example.shoppinglist.ui.scanner

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
import com.example.shoppinglist.data.TargetStore
import com.example.shoppinglist.ocr.DetectionStabilizer
import com.example.shoppinglist.ocr.ScanResult
import com.example.shoppinglist.ocr.StablePrice
import com.example.shoppinglist.ui.catalog.CatalogMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Asistentul de scanare la raft.
 *
 * @param itemId articolul din listă scanat (confirmarea îl marchează în coș); null = scanare liberă,
 *               confirmarea adaugă un produs nou în coș.
 * @param initialStore magazinul activ la deschiderea scanerului; se poate schimba din ecran.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PriceScannerViewModel(
    private val itemId: Long?,
    private val repository: ShoppingRepository,
    initialStore: TargetStore,
) : ViewModel() {

    private val stabilizer = DetectionStabilizer()

    private val _detected = MutableStateFlow<StablePrice?>(null)
    /** Ultimul preț stabil citit de pe etichetă (preț și denumire). */
    val detected: StateFlow<StablePrice?> = _detected.asStateFlow()

    private val _cardOpen = MutableStateFlow(false)
    /** Cardul de confirmare este deschis; analiza cadrelor este înghețată cât timp e deschis. */
    val cardOpen: StateFlow<Boolean> = _cardOpen.asStateFlow()

    /** Se schimbă la fiecare deschidere a cardului, ca valorile din card să se reinițializeze. */
    private val _cardId = MutableStateFlow(0)
    val cardId: StateFlow<Int> = _cardId.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    /** Numărul de produse confirmate în sesiunea curentă a scanerului. */
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _store = MutableStateFlow(initialStore)
    val store: StateFlow<TargetStore> = _store.asStateFlow()

    private val _catalogPrice = MutableStateFlow<Double?>(null)
    val catalogPrice: StateFlow<Double?> = _catalogPrice.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    val item: StateFlow<ShoppingItem?> = (itemId?.let { repository.observeShoppingItem(it) } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Totalul coșului pentru magazinul activ; se recalculează reactiv din Room. */
    val cartTotal: StateFlow<Double> = _store
        .flatMapLatest { repository.observeCartTotal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0.0)

    /** Ultimele produse din coșul magazinului activ. */
    val recentCart: StateFlow<List<ShoppingItem>> = _store
        .flatMapLatest { repository.observeRecentCart(it, RECENT_CART_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val createdAt = System.currentTimeMillis()

    /** Produsele valabile din revistă pentru magazinul activ. */
    private val activeCatalog: Flow<List<CatalogProduct>> = _store.flatMapLatest { store ->
        repository.observeActiveProducts(createdAt).map { products ->
            products.filter { it.storeName.toTargetStore() == store }
        }
    }

    /**
     * Produsul din revistă corespunzător denumirii de pe raft, pentru compararea prețurilor.
     * Null dacă nu s-a citit o denumire sau nu există o potrivire suficientă.
     */
    val catalogMatch: StateFlow<CatalogProduct?> = combine(_detected, activeCatalog) { detected, products ->
        detected?.title?.let { CatalogMatcher.bestMatch(it, products) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            val catalogProductId = itemId
                ?.let { repository.observeShoppingItem(it).first() }
                ?.catalogProductId
            if (catalogProductId != null) {
                _catalogPrice.value = repository.catalogPriceFor(catalogProductId)
            }
        }
    }

    /** Primește rezultatul unui cadru OCR; ignorat cât timp cardul de confirmare e deschis. */
    fun onFrameResult(result: ScanResult) {
        if (_cardOpen.value) return
        val stable = stabilizer.add(result)
        if (stable != null) {
            _detected.value = stable
            if (stabilizer.isArmed) openCard()
        }
    }

    /** Deschide cardul de confirmare (automat la un preț stabil, sau manual). */
    fun openCard() {
        _cardId.value = _cardId.value + 1
        _cardOpen.value = true
    }

    /** Închide cardul fără salvare și reia scanarea. */
    fun rescan() {
        stabilizer.disarm()
        _detected.value = null
        _cardOpen.value = false
    }

    fun selectStore(store: TargetStore) {
        _store.value = store
    }

    /**
     * Confirmă produsul: îl salvează în coș cu denumirea și prețul din card.
     * În modul articol (itemId != null) se apelează [onDone] și ecranul se închide;
     * în scanare liberă se resetează vizorul pentru următorul produs.
     */
    fun confirm(title: String, price: Double, onDone: () -> Unit) {
        val id = itemId
        val target = _store.value
        viewModelScope.launch {
            try {
                if (id != null) {
                    repository.markInCart(id, title, price)
                } else {
                    repository.addToCart(target, title, price)
                }
                _saveError.value = null
                _sessionCount.value = _sessionCount.value + 1
                stabilizer.disarm()
                _detected.value = null
                _cardOpen.value = false
                if (id != null) onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Salvarea în coș a eșuat", e)
                _saveError.value = "Produsul nu a putut fi salvat. Încearcă din nou."
            }
        }
    }

    companion object {
        private const val TAG = "PriceScannerViewModel"
        private const val RECENT_CART_LIMIT = 3
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context, itemId: Long?, initialStore: TargetStore): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val database = AppDatabase.getInstance(context.applicationContext)
                    PriceScannerViewModel(
                        itemId = itemId,
                        repository = ShoppingRepository(database.catalogDao(), database.shoppingDao()),
                        initialStore = initialStore,
                    )
                }
            }
    }
}
