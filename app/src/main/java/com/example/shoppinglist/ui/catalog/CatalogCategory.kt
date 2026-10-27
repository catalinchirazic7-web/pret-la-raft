package com.example.shoppinglist.ui.catalog

import java.text.Normalizer
import java.util.Locale

/**
 * Grupele afișate în bara de categorii. Categoriile din cataloage sunt texte libere
 * (Penny: „Unt si margarina”; Lidl: „Alimente”, „F+V”), deci grupele se deduc din
 * categoria și, dacă aceasta nu spune nimic, din titlul produsului.
 */
enum class CatalogCategory(
    val label: String,
    val emoji: String,
    private val keywords: List<String>,
) {
    ALL("Toate", "🛍️", emptyList()),
    MEAT("Carne", "🥩", listOf("carne", "pui", "porc", "vita", "curcan", "salam", "cotlet", "cordon", "mezeluri", "pastrama", "slanina", "carnati", "pulpe", "piept", "file")),
    FISH("Pește", "🐟", listOf("peste", "pesti", "hering", "somon", "midii")),
    DAIRY("Lactate", "🥛", listOf("lapte", "iaurt", "unt", "margarina", "branza", "branzeturi", "smantana", "cascaval", "telemea", "oua", "ou", "lactate", "actimel", "mozzarella", "mascarpone")),
    BAKERY("Brutărie", "🥖", listOf("paine", "chifla", "covrig", "patiserie", "panificatie", "grisine", "lipie", "franzela", "toast", "cozonac", "croissant", "bagheta")),
    FRUIT_VEG("Legume & fructe", "🍎", listOf("fructe", "legume", "f v", "mere", "lamai", "struguri", "avocado", "cartofi", "varza", "ceapa", "mazare", "kaki", "rosii", "ciuperci", "dovleac", "castraveti", "salata", "banane", "portocale", "pere", "usturoi", "morcov", "ardei")),
    DRINKS("Băuturi", "🥤", listOf("bere", "vin", "sucuri", "suc", "apa", "bauturi", "whisky", "cola", "rom", "votca", "sampanie", "alcool", "carbogazoase", "cafea", "ceai")),
    CLEANING("Curățenie", "🧽", listOf("curatenie", "menaj", "detergent", "folosinta", "unica", "igiena", "sapun", "sampon", "hartie", "servetele", "dinti", "ingrijire", "personala")),
    SWEETS("Dulciuri", "🍫", listOf("napolitane", "biscuiti", "fursecuri", "prajituri", "ciocolata", "bomboane", "dulciuri", "snacks", "pufuleti", "covrigei", "nuga", "gogoasa", "magdalene", "bounty", "mixuri", "batoane")),
    OTHER("Altele", "🛒", emptyList());

    companion object {
        /** Categoria unui produs: întâi după categoria din catalog, apoi după titlu. */
        fun of(category: String, title: String): CatalogCategory {
            val fromCategory = matchingGroup(category)
            if (fromCategory != OTHER) return fromCategory
            return matchingGroup(title)
        }

        private fun matchingGroup(text: String): CatalogCategory {
            val normalized = " ${tokens(text).joinToString(" ")} "
            return entries.firstOrNull { group ->
                group != ALL && group != OTHER &&
                    group.keywords.any { keyword -> normalized.contains(" $keyword ") }
            } ?: OTHER
        }

        /** Litere mici, fără diacritice, cuvinte separate prin spațiu. */
        fun tokens(text: String): List<String> {
            val plain = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            return plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        }
    }
}
