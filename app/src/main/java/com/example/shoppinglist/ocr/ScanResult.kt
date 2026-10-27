package com.example.shoppinglist.ocr

/** Rezultatul unui cadru OCR: prețul ales și denumirea sugerată de pe etichetă (ambele pot lipsi). */
data class ScanResult(
    val price: PriceCandidate?,
    val title: String?,
)
