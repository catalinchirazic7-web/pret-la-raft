package com.example.shoppinglist.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCategoryTest {

    @Test
    fun pennyCategoriesAreMapped() {
        assertEquals(CatalogCategory.DAIRY, CatalogCategory.of("Unt si margarina", "UNIREA ORIGINAL MARGARINA 60%"))
        assertEquals(CatalogCategory.DAIRY, CatalogCategory.of("Iaurt", "ACTIMEL CAPSUNI 4X100G"))
        assertEquals(CatalogCategory.DRINKS, CatalogCategory.of("Vin", "Rosu sec"))
        assertEquals(CatalogCategory.SWEETS, CatalogCategory.of("Napolitane si nuga", "ALFERS NAPOLITANE"))
        assertEquals(CatalogCategory.CLEANING, CatalogCategory.of("Unica folosinta si menaj", "Saci gunoi"))
    }

    @Test
    fun lidlCategoriesAreMapped() {
        assertEquals(CatalogCategory.FRUIT_VEG, CatalogCategory.of("F+V", "Struguri albi cu sâmburi"))
        assertEquals(CatalogCategory.FRUIT_VEG, CatalogCategory.of("Fructe și legume", "Avocado"))
        assertEquals(CatalogCategory.DAIRY, CatalogCategory.of("Lactate, branzeturi si oua", "Telemea de vacă"))
    }

    @Test
    fun genericCategoryFallsBackToTitle() {
        assertEquals(CatalogCategory.DRINKS, CatalogCategory.of("Alimente", "Bere blondă"))
        assertEquals(CatalogCategory.MEAT, CatalogCategory.of("Alimente", "Salam"))
        assertEquals(CatalogCategory.DAIRY, CatalogCategory.of("Alimente", "Cașcaval"))
    }

    @Test
    fun unknownProductIsOther() {
        assertEquals(CatalogCategory.OTHER, CatalogCategory.of("Generale", "Produs necunoscut"))
        assertEquals(CatalogCategory.OTHER, CatalogCategory.of("Alimente", "Bounce"))
    }

    @Test
    fun tokensMatchWholeWordsOnly() {
        // "ou" nu trebuie să se potrivească în „bounty”
        assertEquals(CatalogCategory.SWEETS, CatalogCategory.of("", "Bounty baton"))
    }
}
