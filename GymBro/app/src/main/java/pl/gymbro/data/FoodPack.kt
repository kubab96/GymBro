package pl.gymbro.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Paczka polskich produktów z Open Food Facts, dostarczana z aplikacją.
 *
 * Projekt: `docs/kalorie.md`, rozdział 9.
 *
 * Po co, skoro skaner i tak pyta serwer: **żeby dało się szukać po nazwie.**
 * Do tej pory wyszukiwanie obejmowało zestaw własny i to, co już raz
 * zeskanowałeś - czyli żeby znaleźć jogurt konkretnej mleczarni, trzeba go
 * było mieć w ręce. Z paczką wpisanie marki wystarczy.
 *
 * Przy okazji znika zależność od zasięgu: sklep w piwnicy przestaje być
 * problemem, bo kody z paczki rozwiązują się lokalnie.
 *
 * ## Skąd się bierze
 *
 * Z nocnego zrzutu CSV Open Food Facts, przefiltrowanego do produktów
 * sprzedawanych w Polsce, które mają komplet: kalorie, białko, węglowodany
 * i tłuszcz. Filtrowanie robi się przy budowaniu paczki, nie tutaj - zrzut
 * ma półtora giga po spakowaniu i dziewięć po rozpakowaniu, a wynik schodzi
 * do kilku megabajtów.
 *
 * To **nie jest** pobieranie masowe przez API: Open Food Facts prosi, żeby
 * jedno wywołanie API odpowiadało jednemu prawdziwemu skanowi, a do
 * pobierania hurtem służą właśnie zrzuty.
 *
 * Licencja ODbL wymaga wskazania źródła - linijka o tym jest w Profilu.
 */
object FoodPack {

    /**
     * Nazwa pliku w zasobach aplikacji.
     *
     * **Bez rozszerzenia `.gz`, i to nie jest przeoczenie.** Narzędzie
     * budujące Androida rozpakowuje pliki `.gz` w zasobach przy składaniu
     * pakietu - plik `off-polska.tsv.gz` ląduje w APK jako `off-polska.tsv`,
     * a aplikacja szuka nazwy, której tam nie ma. Kosztowało to jeden cykl
     * budowania i instalacji, zanim stało się widoczne.
     *
     * Nic na tym nie tracimy: samo APK jest archiwum i kompresuje zasoby,
     * więc dwa megabajty tekstu zajmują w nim i tak około dziewięciuset
     * kilobajtów. Zyskujemy jedną rzecz mniej do zepsucia.
     */
    private const val ASSET = "off-polska.tsv"

    /**
     * Wersja paczki.
     *
     * Trzymana w [app_state]. Podbicie tej liczby razem z nowym plikiem
     * sprawia, że import wykona się jeszcze raz i dociągnie to, co doszło.
     */
    const val VERSION = 5

    private const val STATE_KEY = "food_pack_version"

    /** Ile kolumn ma wiersz paczki; mniej znaczy, że plik jest uszkodzony. */
    private const val COLUMNS = 13

    /** Czy paczka w tej wersji została już wczytana. */
    fun isImported(db: SQLiteDatabase): Boolean =
        db.rawQuery(
            "SELECT value FROM app_state WHERE key = ?",
            arrayOf(STATE_KEY)
        ).use { c ->
            c.moveToFirst() && (c.getString(0)?.toIntOrNull() ?: 0) >= VERSION
        }

    /**
     * Wczytuje paczkę do katalogu produktów.
     *
     * Wołać z wątku roboczego i **nie z migracji**: trzydzieści kilka tysięcy
     * wierszy to kilka sekund, a migracja wykonuje się przy pierwszym dotknięciu
     * bazy, czyli przy starcie aplikacji. Zamrożenie ekranu startowego na kilka
     * sekund raz w życiu instalacji jest gorsze niż wczytanie w tle, podczas
     * którego wyszukiwanie po prostu przez chwilę zwraca mniej wyników.
     *
     * Wpisy już istniejące są **pomijane, nie nadpisywane**. Kod kreskowy jest
     * tożsamością produktu, a jeśli ten kod już w bazie jest, to znaczy, że
     * albo sam go wpisałeś, albo przyszedł świeżo z serwera - w obu
     * przypadkach jest lepszy niż wiersz ze zrzutu sprzed tygodni.
     *
     * @return ile produktów dopisano
     */
    fun import(context: Context, db: SQLiteDatabase): Int {
        val insert = db.compileStatement(
            """
            INSERT OR IGNORE INTO food
                (barcode, name, name_key, brand, kcal_100, protein_100, carbs_100,
                 fat_100, fiber_100, sugars_100, saturated_100, salt_100,
                 serving_g, package_g, source, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)
            """.trimIndent()
        )

        var dodane = 0
        db.beginTransaction()
        try {
            // Czyścimy poprzednią paczkę, zamiast dokładać do niej.
            //
            // Inaczej poprawka filtra nie usuwałaby tego, co wpuścił filtr
            // poprzedni - a dokładnie tak powstała ta linijka: pierwsza
            // paczka wniosła 182 produkty o nazwach cyrylicą i jeden
            // nazwany kropką.
            //
            // Kasujemy wyłącznie wiersze paczki: `updated_at = 0` mają tylko
            // one, bo produkty z serwera i wpisane ręcznie dostają znacznik
            // czasu. Wpisy w dzienniku i tak mają własną migawkę makro, więc
            // historia nie traci nic poza odnośnikiem do produktu.
            db.execSQL(
                "DELETE FROM food WHERE source = ? AND updated_at = 0",
                arrayOf(FoodSource.OFF.name)
            )

            // Co już jest w katalogu po skasowaniu poprzedniej paczki:
            // produkty własne, przepisy, zestaw podstawowy i to, co
            // zeskanowałeś z serwera.
            //
            // Paczka ustępuje każdemu z nich. Wszystkie powstały tak, że
            // ktoś je sprawdził - sam wpisałeś albo trzymałeś opakowanie
            // w ręce przy skanie - a paczka jest wsadem masowym, którego
            // nikt nie oglądał. Dwa wiersze o tej samej nazwie na liście
            // wyników to pytanie bez odpowiedzi: który wybrać.
            val istniejace = HashSet<String>()
            db.rawQuery("SELECT name_key FROM food", null).use { c ->
                while (c.moveToNext()) istniejace += c.getString(0)
            }

            context.assets.open(ASSET).use { raw ->
                raw.bufferedReader().forEachLine { line ->
                    val f = line.split('\t')
                    if (f.size < COLUMNS) return@forEachLine

                    val barcode = f[0].trim()
                    val name = f[1].trim()
                    if (barcode.isEmpty() || name.isEmpty()) return@forEachLine

                    val kcal = f[3].toDoubleOrNull() ?: return@forEachLine
                    val protein = f[4].toDoubleOrNull() ?: return@forEachLine
                    val carbs = f[5].toDoubleOrNull() ?: return@forEachLine
                    val fat = f[6].toDoubleOrNull() ?: return@forEachLine

                    val brand = f[2].trim().takeIf { it.isNotEmpty() }
                    // Sprawdzamy SAMA NAZWE, nie klucz razem z marka.
                    //
                    // Pierwsza wersja patrzyla na jedno i drugie - i wtedy
                    // produkt o nazwie "." wchodzil do katalogu, bo marka
                    // "Mlekovita" wystarczala, zeby klucz dalo sie wyszukac.
                    // Na liscie wynikow widac jednak nazwe, a ta byla kropka.
                    if (!searchable(searchKey(name))) return@forEachLine

                    val key = searchKey(listOfNotNull(name, brand).joinToString(" "))
                    // Ustępujemy temu, co już jest - i sobie samym, bo ten
                    // sam produkt bywa w zrzucie pod kilkoma kodami.
                    if (!istniejace.add(key)) return@forEachLine

                    insert.clearBindings()
                    insert.bindString(1, barcode)
                    insert.bindString(2, name)
                    // Klucz liczony tutaj, a nie w paczce: gdyby regula
                    // normalizacji nazw kiedys sie zmienila, paczka zostalaby
                    // z kluczami w starej postaci i przestala sie znajdowac.
                    insert.bindString(3, key)
                    bindOrNull(insert, 4, brand)
                    insert.bindDouble(5, roundKcal(kcal))
                    insert.bindDouble(6, roundGram(protein))
                    insert.bindDouble(7, roundGram(carbs))
                    insert.bindDouble(8, roundGram(fat))
                    bindNumberOrNull(insert, 9, f[7])
                    bindNumberOrNull(insert, 10, f[8])
                    bindNumberOrNull(insert, 11, f[9])
                    bindNumberOrNull(insert, 12, f[10])
                    bindNumberOrNull(insert, 13, f[11])
                    bindNumberOrNull(insert, 14, f[12])
                    insert.bindString(15, FoodSource.OFF.name)

                    if (insert.executeInsert() > 0) dodane++
                }
            }

            db.execSQL(
                "INSERT OR REPLACE INTO app_state (key, value) VALUES (?, ?)",
                arrayOf(STATE_KEY, VERSION.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            insert.close()
        }
        return dodane
    }

    /**
     * Czy tej nazwy da się w ogóle poszukać z polskiej klawiatury.
     *
     * Liczymy znaki **łacińskie** w kluczu wyszukiwania, bo [searchKey]
     * sprowadza polskie ogonki do łacinki, ale cyrylicy ani japońskiego nie
     * tyka - dla Kotlina to nadal litery. W zrzucie siedzi kilkaset produktów
     * oznaczonych jako sprzedawane w Polsce, lecz z etykietą po rosyjsku,
     * bułgarsku albo japońsku, i jeden nazwany po prostu kropką. Nie da się
     * ich wpisać, więc w katalogu są wyłącznie szumem - a przez krótki klucz
     * ten z kropką wychodził nawet na pierwsze miejsce wyników.
     */
    private fun searchable(key: String): Boolean =
        key.count { it in 'a'..'z' || it in '0'..'9' } >= 2

    private fun bindOrNull(st: android.database.sqlite.SQLiteStatement, i: Int, v: String?) {
        if (v.isNullOrBlank()) st.bindNull(i) else st.bindString(i, v)
    }

    /**
     * Liczba albo brak danych.
     *
     * Puste pole w zrzucie znaczy „nikt tego nie uzupełnił", a nie „zero" -
     * ta sama zasada, co przy odczycie po kodzie kreskowym. Zero wpisane
     * tam, gdzie nikt nic nie podał, byłoby twierdzeniem.
     */
    private fun bindNumberOrNull(
        st: android.database.sqlite.SQLiteStatement,
        i: Int,
        raw: String
    ) {
        val v = raw.trim().toDoubleOrNull()
        if (v == null || v < 0) st.bindNull(i) else st.bindDouble(i, roundGram(v))
    }
}
