package com.example.shoppinglist.ui.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.shoppinglist.data.CatalogProduct
import com.example.shoppinglist.data.ShoppingItem
import com.example.shoppinglist.ui.common.CardShape
import com.example.shoppinglist.ui.common.formatLei

/** Conținutul tab-ului „Lista mea”. Bara de sus și totalul sunt în scheletul aplicației. */
@Composable
fun ShoppingListContent(
    uiState: ShoppingListUiState,
    viewModel: ShoppingListViewModel,
    onScanItem: (ShoppingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        StoreFilterRow(
            selected = uiState.storeFilter,
            onSelect = viewModel::onStoreFilterSelected,
        )

        QuickAddSection(
            query = uiState.query,
            suggestions = uiState.suggestions,
            onQueryChange = viewModel::onQueryChanged,
            onAddTyped = viewModel::addTypedItem,
            onSuggestionClick = viewModel::addFromCatalog,
        )

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = viewModel::dismissError)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (uiState.items.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                items(items = uiState.items, key = { it.id }) { item ->
                    ShoppingItemCard(
                        item = item,
                        onToggle = { viewModel.toggleBought(item) },
                        onScan = { onScanItem(item) },
                        onDelete = { viewModel.deleteItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreFilterRow(
    selected: StoreFilter,
    onSelect: (StoreFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StoreFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

/** Bara de total afișată deasupra barei de navigare. */
@Composable
fun ShoppingTotalBar(total: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total estimat (nebifate)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = total.formatLei(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun QuickAddSection(
    query: String,
    suggestions: List<CatalogProduct>,
    onQueryChange: (String) -> Unit,
    onAddTyped: () -> Unit,
    onSuggestionClick: (CatalogProduct) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = { Text("Ce vrei să cumperi?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddTyped() }),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAddTyped,
                enabled = query.isNotBlank(),
            ) {
                Text("Adaugă")
            }
        }

        suggestions.forEach { product ->
            SuggestionRow(
                product = product,
                onClick = { onSuggestionClick(product) },
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    product: CatalogProduct,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${product.storeName.displayName} · ${product.category}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = product.price.formatLei(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isBought,
                onCheckedChange = { onToggle() },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isBought) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isBought) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(
                    text = item.targetStore.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.estimatedPrice > 0.0) {
                Text(
                    text = item.estimatedPrice.formatLei(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            TextButton(onClick = onScan) {
                Text("📷")
            }
            TextButton(onClick = onDelete) {
                Text("Șterge")
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Lista este goală. Adaugă primul produs de mai sus sau caută în oferte.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
