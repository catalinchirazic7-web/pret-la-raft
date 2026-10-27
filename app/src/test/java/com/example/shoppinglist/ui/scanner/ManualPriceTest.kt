package com.example.shoppinglist.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualPriceTest {

    @Test
    fun acceptsDotAndCommaDecimals() {
        assertEquals(4.25, parseManualPrice("4.25")!!, 0.001)
        assertEquals(4.25, parseManualPrice("4,25")!!, 0.001)
    }

    @Test
    fun acceptsIntegersAndSpaces() {
        assertEquals(12.0, parseManualPrice(" 12 ")!!, 0.001)
        assertEquals(7.5, parseManualPrice("7.5")!!, 0.001)
    }

    @Test
    fun rejectsInvalidText() {
        assertNull(parseManualPrice(""))
        assertNull(parseManualPrice("abc"))
        assertNull(parseManualPrice("4,25 lei"))
        assertNull(parseManualPrice("12.345"))
    }

    @Test
    fun rejectsOutOfRange() {
        assertNull(parseManualPrice("0"))
        assertNull(parseManualPrice("0,00"))
        assertNull(parseManualPrice("10000"))
    }
}
