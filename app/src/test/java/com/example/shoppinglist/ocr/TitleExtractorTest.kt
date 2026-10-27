package com.example.shoppinglist.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleExtractorTest {

    private fun line(text: String, height: Float, top: Int) =
        OcrLine(text = text, heightPx = height, box = PixelBox(0, top, 300, top + height.toInt()))

    private val price = line("6,49", 60f, 150)

    @Test
    fun picksLargeTitleAbovePrice() {
        val lines = listOf(
            line("Lapte integral 1L", 30f, 50),
            price,
            line("PRET VECHI 7,99", 15f, 250),
        )
        assertEquals("Lapte integral", TitleExtractor.extract(lines, price))
    }

    @Test
    fun ignoresNoiseWords() {
        val lines = listOf(
            line("LEI BUC KG", 30f, 60),
            line("26.10 pana miercuri", 30f, 90),
            line("5901234123457", 30f, 120),
            price,
        )
        assertNull(TitleExtractor.extract(lines, price))
    }

    @Test
    fun removesDeLaPhrase() {
        assertEquals("Ulei de floarea soarelui", TitleExtractor.cleanTitle("Ulei de floarea soarelui"))
        assertNull(TitleExtractor.cleanTitle("DE LA PENNY"))
    }

    @Test
    fun dropsWordsWithDigitsAndUnits() {
        assertEquals("Iaurt natural", TitleExtractor.cleanTitle("Iaurt natural 400g"))
        assertEquals("Lapte", TitleExtractor.cleanTitle("Lapte 1,5L"))
    }

    @Test
    fun ignoresTextFarFromPrice() {
        val lines = listOf(
            line("Titlu departe", 40f, 900),
            price,
        )
        assertNull(TitleExtractor.extract(lines, price))
    }

    @Test
    fun ignoresSmallText() {
        val lines = listOf(
            line("Text mic", 8f, 60),
            line("Text mare", 60f, 10),
            price,
        )
        // "Text mic" are sub 35% din înălțimea maximă și e ignorat; "Text mare" are cea mai mare înălțime.
        assertEquals("Text mare", TitleExtractor.extract(lines, price))
    }

    @Test
    fun returnsNullWithoutPriceLine() {
        assertNull(TitleExtractor.extract(listOf(line("Lapte", 30f, 0)), null))
    }

    @Test
    fun rejectsTooFewLetters() {
        assertNull(TitleExtractor.cleanTitle("Ab"))
    }
}
