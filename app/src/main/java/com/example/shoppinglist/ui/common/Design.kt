package com.example.shoppinglist.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.shoppinglist.data.CatalogStore

val AppBackground = Color(0xFFF7F8FA)
val CardShape = RoundedCornerShape(16.dp)

private val LidlBadge = Color(0xFF0050AA)
private val PennyBadge = Color(0xFFCC0000)

fun CatalogStore.badgeColor(): Color = when (this) {
    CatalogStore.LIDL -> LidlBadge
    CatalogStore.PENNY -> PennyBadge
}
