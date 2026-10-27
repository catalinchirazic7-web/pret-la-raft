# ShoppingTracker & Scanner (Lidl & Penny)

## Stack & Reguli Tehnice
- **Limbaj:** Kotlin (ultimul standard, coroutine, StateFlow)
- **UI:** Jetpack Compose + Material 3
- **Bază de date locală:** Room (SQLite) cu suport offline complet
- **Scanare OCR:** CameraX + Google ML Kit Text Recognition (latin)
- **Arhitectură:** MVVM simplificat, fără over-engineering (fără Dagger/Hilt dacă nu e strict necesar, manual DI sau ViewModelProvider simplu)
- **Stil de cod:** Curat, funcțional, tratare erori explicită, fără cod trunchiat (fără `// TODO: implement later`)

## Module Cheie
1. **Catalog (Oferte):** Produse extrase din cataloagele Lidl și Penny (titlu, preț catalog, magazin, categorie, dată valabilitate).
2. **ShoppingList (stil Listonic):** Articole de cumpărat, bifabile, grupate pe magazine/categorii, calcul total estimat.
3. **PriceScanner (OCR):** Overlay peste camera telefonului pentru a citi prețul de pe etichetele de la raft și a-l asocia cu produsul curent.