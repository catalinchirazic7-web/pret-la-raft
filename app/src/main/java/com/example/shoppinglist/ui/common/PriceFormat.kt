package com.example.shoppinglist.ui.common

import java.text.NumberFormat
import java.util.Locale

private val priceFormat: NumberFormat =
    NumberFormat.getNumberInstance(Locale.of("ro", "RO")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

/** Prețul în format românesc, ex. "4,25 lei". */
fun Double.formatLei(): String = "${priceFormat.format(this)} lei"
