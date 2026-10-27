package com.example.shoppinglist.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BUCHAREST: ZoneId = ZoneId.of("Europe/Bucharest")
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("ro", "RO"))

/** Data de valabilitate (milisecunde epoch) în format românesc, ex. "01.11.2026". */
fun Long.formatValidUntil(): String =
    Instant.ofEpochMilli(this).atZone(BUCHAREST).toLocalDate().format(DAY_MONTH_YEAR)
