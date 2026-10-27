package com.example.shoppinglist.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppinglist.data.CatalogProduct
import com.example.shoppinglist.ui.common.CardShape
import com.example.shoppinglist.ui.common.badgeColor
import com.example.shoppinglist.ui.common.formatLei
import com.example.shoppinglist.ui.common.formatValidUntil
import com.example.shoppinglist.ui.shoppinglist.StoreFilter

/** Tab-ul „Oferte & Cataloage”: categorii și produsele valabile. */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onAddToList: (CatalogProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    val products by viewModel.products.collectAsState()
    val selected by viewModel.category.collectAsState()
    val storeFilter by viewModel.storeFilter.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == storeFilter,
                    onClick = { viewModel.selectStore(option) },
                    label = { Text(option.label) },
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = CatalogCategory.entries, key = { it.name }) { category ->
                FilterChip(
                    selected = category == selected,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text("${category.emoji} ${category.label}") },
                )
            }
        }

        if (products.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nu există oferte valabile în această categorie.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = products, key = { it.id }) { product ->
                    ProductCard(product = product, onAddToList = { onAddToList(product) })
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: CatalogProduct,
    onAddToList: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    StoreBadge(store = product.storeName.displayName, color = product.storeName.badgeColor())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = product.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${product.category} · valabil până la ${product.validUntil.formatValidUntil()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = product.price.formatLei(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAddToList, modifier = Modifier.fillMaxWidth()) {
                Text("+ Adaugă în lista mea")
            }
        }
    }
}

@Composable
private fun StoreBadge(store: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = store, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
