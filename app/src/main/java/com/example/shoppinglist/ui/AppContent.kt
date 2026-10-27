package com.example.shoppinglist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shoppinglist.R
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.data.TargetStore
import com.example.shoppinglist.ui.catalog.CatalogScreen
import com.example.shoppinglist.ui.catalog.CatalogViewModel
import com.example.shoppinglist.ui.common.AppBackground
import com.example.shoppinglist.ui.scanner.PriceScannerScreen
import com.example.shoppinglist.ui.scanner.PriceScannerViewModel
import com.example.shoppinglist.ui.shoppinglist.ShoppingListContent
import com.example.shoppinglist.ui.shoppinglist.StoreFilter
import com.example.shoppinglist.ui.shoppinglist.ShoppingListViewModel
import com.example.shoppinglist.ui.shoppinglist.ShoppingTotalBar
import kotlinx.coroutines.launch

private const val TAB_LIST = 0
private const val TAB_CATALOG = 1

/**
 * Navigare: scheletul cu tab-uri, sau ecranul de scanare (pe tot ecranul).
 * Starea este salvabilă ca să supraviețuiască rotirii ecranului.
 * [scanItemId] null înseamnă scanare generală a unui raft, nelegată de un articol.
 */
@Composable
fun AppContent() {
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var scanItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var scanStoreName by rememberSaveable { mutableStateOf(TargetStore.LIDL.name) }

    if (showScanner) {
        val context = LocalContext.current
        val initialStore = TargetStore.valueOf(scanStoreName)
        val scannerViewModel: PriceScannerViewModel = viewModel(
            key = "scanner-${scanItemId ?: "shelf"}-$scanStoreName",
            factory = PriceScannerViewModel.factory(context.applicationContext, scanItemId, initialStore),
        )
        PriceScannerScreen(
            viewModel = scannerViewModel,
            onClose = {
                showScanner = false
                scanItemId = null
            },
        )
    } else {
        MainScaffold(
            onScanItem = { item: ShoppingItem ->
                scanItemId = item.id
                scanStoreName = scanStoreFor(item.targetStore).name
                showScanner = true
            },
            onScanShelf = { filter: StoreFilter ->
                scanItemId = null
                scanStoreName = scanStoreFor(filter.targetStore).name
                showScanner = true
            },
        )
    }
}

/** Scanerul lucrează doar cu Lidl sau Penny; articolele „General” și filtrul „Toate” cad pe Lidl. */
private fun scanStoreFor(store: TargetStore?): TargetStore =
    if (store == TargetStore.PENNY) TargetStore.PENNY else TargetStore.LIDL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    onScanItem: (ShoppingItem) -> Unit,
    onScanShelf: (StoreFilter) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val shoppingViewModel: ShoppingListViewModel = viewModel(
        factory = ShoppingListViewModel.factory(context),
    )
    val catalogViewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModel.factory(context),
    )
    val uiState by shoppingViewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(TAB_LIST) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_pliant),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (tab == TAB_LIST) "PrețLaRaft · Lista mea" else "PrețLaRaft · Oferte",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
            )
        },
        bottomBar = {
            Column {
                if (tab == TAB_LIST) {
                    ShoppingTotalBar(total = uiState.estimatedTotal)
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == TAB_LIST,
                        onClick = { tab = TAB_LIST },
                        icon = { Text("🛒") },
                        label = { Text("Lista mea") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_CATALOG,
                        onClick = { tab = TAB_CATALOG },
                        icon = { Text("🏷️") },
                        label = { Text("Oferte & Cataloage") },
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onScanShelf(uiState.storeFilter) },
                text = { Text("📷 Scanează la raft") },
                icon = {},
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (tab) {
            TAB_LIST -> ShoppingListContent(
                uiState = uiState,
                viewModel = shoppingViewModel,
                onScanItem = onScanItem,
                modifier = Modifier.padding(padding),
            )
            else -> CatalogScreen(
                viewModel = catalogViewModel,
                onAddToList = { product ->
                    shoppingViewModel.addProductToList(product)
                    scope.launch {
                        snackbarHostState.showSnackbar("Adăugat în listă: ${product.title}")
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
