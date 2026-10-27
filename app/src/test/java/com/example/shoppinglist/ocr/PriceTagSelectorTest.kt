package com.example.shoppinglist.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceTagSelectorTest {

    /** Zona de sus a cadrului (top < 100) este roșie, restul nu. */
    private val redTop: (PixelBox) -> Float = { box -> if (box.top < 100) 0.9f else 0f }
    private val noRed: (PixelBox) -> Float = { 0f }

    private fun line(text: String, height: Float, top: Int) =
        OcrLine(text = text, heightPx = height, box = PixelBox(0, top, 200, top + height.toInt()))

    private fun selectedValue(lines: List<OcrLine>, redFraction: (PixelBox) -> Float): Double? =
        PriceTagSelector.select(lines, redFraction)?.candidate?.value

    @Test
    fun pennyPrefersRedCardPrice() {
        val lines = listOf(
            line("Penny", 30f, 300),
            line("7,49", 60f, 20),   // promo, pe fundal roșu
            line("8,99", 40f, 400),  // preț întreg, mai mic dar nu roșu
        )
        assertEquals(7.49, selectedValue(lines, redTop)!!, 0.001)
    }

    @Test
    fun pennyFallsBackToTallestWithoutRed() {
        val lines = listOf(
            line("Penny", 30f, 300),
            line("4,25", 30f, 200),
            line("5,99", 60f, 400),
        )
        assertEquals(5.99, selectedValue(lines, noRed)!!, 0.001)
    }

    @Test
    fun lidlPicksStrictlyTallestEvenIfOtherIsRed() {
        val lines = listOf(
            line("Lidl", 30f, 300),
            line("9,99", 20f, 20),   // preț vechi, tăiat, mic
            line("6,99", 70f, 400),  // preț nou, font mare
        )
        assertEquals(6.99, selectedValue(lines, redTop)!!, 0.001)
    }

    @Test
    fun selectionKeepsTheLineOfThePrice() {
        val lines = listOf(
            line("Lapte integral", 30f, 50),
            line("6,49", 60f, 150),
        )
        val selection = PriceTagSelector.select(lines, noRed)
        assertEquals("6,49", selection!!.line.text)
    }

    @Test
    fun storeHintIsDetectedFromText() {
        assertEquals(StoreHint.LIDL, PriceTagSelector.storeHint(listOf(line("LIDL plus", 20f, 0))))
        assertEquals(StoreHint.PENNY, PriceTagSelector.storeHint(listOf(line("PENNY", 20f, 0))))
        assertNull(PriceTagSelector.storeHint(listOf(line("Lapte", 20f, 0))))
    }

    @Test
    fun returnsNullWithoutPrice() {
        val lines = listOf(line("Lapte integral", 30f, 0), line("250g", 20f, 50))
        assertNull(PriceTagSelector.select(lines, noRed))
    }

    @Test
    fun ignoresLinesWithoutBox() {
        val lines = listOf(OcrLine("3,29", 40f, null))
        assertEquals(3.29, selectedValue(lines, noRed)!!, 0.001)
    }
}
