package com.example.shoppinglist.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {

    private fun bestValue(text: String): Double? =
        PriceParser.parse(text).maxByOrNull { it.rank }?.value

    @Test
    fun parsesCommaDecimal() {
        assertEquals(12.49, bestValue("12,49")!!, 0.001)
    }

    @Test
    fun parsesDotDecimal() {
        assertEquals(12.49, bestValue("12.49")!!, 0.001)
    }

    @Test
    fun parsesDecimalWithCurrency() {
        assertEquals(4.25, bestValue("4,25 lei")!!, 0.001)
    }

    @Test
    fun parsesSplitLeiPrice() {
        assertEquals(9.99, bestValue("9 lei 99")!!, 0.001)
    }

    @Test
    fun parsesSplitLeiPriceWithoutSpace() {
        assertEquals(9.99, bestValue("9 lei99")!!, 0.001)
    }

    @Test
    fun parsesIntegerLei() {
        assertEquals(4.0, bestValue("4 lei")!!, 0.001)
    }

    @Test
    fun ignoresYear() {
        assertNull(bestValue("KW 44/2026"))
        assertNull(bestValue("2026"))
    }

    @Test
    fun ignoresWeightAndVolume() {
        assertNull(bestValue("250g"))
        assertNull(bestValue("1000ml"))
        assertNull(bestValue("1,5L"))
    }

    @Test
    fun ignoresBarcode() {
        assertNull(bestValue("5901234123457"))
    }

    @Test
    fun ignoresUnitPrice() {
        assertNull(bestValue("1 kg = 13,98 lei"))
    }

    @Test
    fun ignoresValidityDate() {
        assertNull(bestValue("de luni, 26.10 până miercuri, 28.10"))
    }

    @Test
    fun keepsRealPriceNextToWeight() {
        assertEquals(6.49, bestValue("Lapte 1,5L 6,49")!!, 0.001)
    }

    @Test
    fun prefersCurrencyMatch() {
        val best = PriceParser.parse("Ulei 1,5 l 9,49 lei").maxByOrNull { it.rank }
        assertEquals(9.49, best!!.value, 0.001)
    }

    @Test
    fun rejectsZeroPrice() {
        assertNull(bestValue("0,00 lei"))
    }
}
