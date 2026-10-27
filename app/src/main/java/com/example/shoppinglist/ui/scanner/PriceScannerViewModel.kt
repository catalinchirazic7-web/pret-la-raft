package com.example.shoppinglist.ui.scanner

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.shoppinglist.data.AppDatabase
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.ShoppingRepository
import com.example.shoppinglist.ocr.PriceCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param itemId articolul din listă pentru care se scanează prețul; null = scanare generală.
 */
class PriceScannerViewModel(
    private val itemId: Long?,
    private val repository: ShoppingRepository,
) : ViewModel() {

    private val recentValues = ArrayDeque<Double>()

    private val _detectedPrice = MutableStateFlow<Double?>(null)
    val detectedPrice: StateFlow<Double?> = _detectedPrice.asStateFlow()

    private val _catalogPrice = MutableStateFlow<Double?>(null)
    val catalogPrice: StateFlow<Double?> = _catalogPrice.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    val item: StateFlow<ShoppingItem?> = (itemId?.let { repository.observeShoppingItem(it) } ?: flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

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

    /**
     * Primește rezultatul fiecărui cadru OCR. Un preț este publicat doar după ce apare
     * de cel puțin [MIN_VOTES] ori în ultimele [WINDOW] cadre, ca să nu sară valorile
     * din cauza zgomotului.
     */
    fun onFrameResult(candidate: PriceCandidate?) {
        if (candidate == null) return
        recentValues.addLast(candidate.value)
        if (recentValues.size > WINDOW) recentValues.removeFirst()
        val (value, votes) = recentValues
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.let { it.key to it.value }
            ?: return
        if (votes >= MIN_VOTES) _detectedPrice.value = value
    }

    /** Salvează prețul de raft (detectat sau introdus manual) ca estimatedPrice al articolului. */
    fun saveShelfPrice(price: Double, onSaved: () -> Unit) {
        val id = itemId ?: return
        viewModelScope.launch {
            try {
                repository.updateEstimatedPrice(id, price)
                _saveError.value = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Salvarea prețului de raft a eșuat", e)
                _saveError.value = "Prețul nu a putut fi salvat. Încearcă din nou."
            }
        }
    }

    companion object {
        private const val TAG = "PriceScannerViewModel"
        private const val WINDOW = 6
        private const val MIN_VOTES = 2
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(context: Context, itemId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val database = AppDatabase.getInstance(context)
                PriceScannerViewModel(
                    itemId = itemId,
                    repository = ShoppingRepository(database.catalogDao(), database.shoppingDao()),
                )
            }
        }
    }
}
