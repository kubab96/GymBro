package pl.gymbro.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement

/**
 * Paczka produktów z tabel opublikowanych przez sklep.
 *
 * Projekt: `docs/kalorie.md`, rozdział 10.
 *
 * Po co, skoro jest już paczka z Open Food Facts: **tamta baza jest
 * indeksowana kodami kreskowymi.** Bułka sprzedawana na wagę kodu nie ma,
 * więc nie ma jej i tam - a to właśnie pieczywo, nabiał z lady i wędliny
 * wypadały przy wyszukiwaniu. Sklep, który prowadzi sprzedaż przez stronę,
 * publikuje te tabele sam, bo musi: wartości odżywcze są obowiązkowe przy
 * sprzedaży na odległość.
 *
 * ## Skąd się bierze
 *
 * Z kart produktów Frisco.pl, z czterech działów - pieczywo i cukiernia,
 * warzywa i owoce, nabiał z jajami i serami, mięso z wędlinami. Pobieranie
 * i składanie pliku opisuje `docs/kalorie.md`; tutaj wczytujemy już gotowy
 * plik z zasobów.
 *
 * Każdy wiersz przeszedł przy pobieraniu **sprawdzenie wzorem unijnym**:
 * 4·białko + 4·węglowodany + 9·tłuszcz + 2·błonnik musi zgadzać się z
 * deklarowanymi kaloriami w granicach dziesięciu procent. To nie ozdoba, a
 * jedyna obrona przed cichym błędem czytnika strony - jego pierwsza wersja
 * zaczepiała się o wiersz z kilodżulami, a na części kart ten wiersz stoi
 * na końcu tabeli, nie na początku, więc czytnik czytał stopkę i wypisywał
 * liczby w rodzaju „003". Wzór takie wiersze odrzuca.
 */
object ShopPack {

    /** Nazwa pliku w zasobach aplikacji. */
    private const val ASSET = "sklepy-polska.tsv"

    /**
     * Wersja paczki.
     *
     * Trzymana w [app_state]. Podbicie tej liczby razem z nowym plikiem
     * sprawia, że import wykona się jeszcze raz.
     */
    const val VERSION = 1

    private const val STATE_KEY = "shop_pack_version"

    /** Ile kolumn ma wiersz paczki; mniej znaczy, że plik jest uszkodzony. */
    private const val COLUMNS = 10

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
     * Wołać z wątku roboczego i nie z migracji - z tego samego powodu, co
     * [FoodPack.import].
     *
     * @return ile produktów dopisano
     */
    fun import(context: Context, db: SQLiteDatabase): Int {
        val insert = db.compileStatement(
            """
            INSERT INTO food
                (barcode, name, name_key, brand, kcal_100, protein_100, carbs_100,
                 fat_100, fiber_100, sugars_100, saturated_100, salt_100,
                 serving_g, package_g, source, updated_at)
            VALUES (NULL,?,?,NULL,?,?,?,?,?,?,?,?,NULL,?,?,0)
            """.trimIndent()
        )

        var dodane = 0
        db.beginTransaction()
        try {
            // Czyścimy poprzednią paczkę, zamiast dokładać do niej - inaczej
            // poprawka czytnika strony nie usuwałaby tego, co wpuściła jego
            // poprzednia wersja.
            //
            // Tu wystarczy sam znacznik źródła, bez `updated_at = 0`: wiersze
            // SHOP powstają wyłącznie tutaj, a kodu kreskowego nie mają, więc
            // skan nigdy nie trafi w ten sam wiersz i nie podstawi mu
            // znacznika czasu.
            db.execSQL(
                "DELETE FROM food WHERE source = ?",
                arrayOf(FoodSource.SHOP.name)
            )

            // Czemu ustępujemy: produktom własnym, przepisom, zestawowi
            // podstawowemu, temu co zeskanowane, i paczce Open Food Facts.
            //
            // Ostatnie z nich może zaskakiwać, bo w kolejności wyszukiwania
            // paczka OFF stoi niżej. Ale tutaj nie wybieramy lepszego wiersza,
            // tylko unikamy dwóch wierszy o tej samej nazwie na liście
            // wyników - a przy równej nazwie ten z kodem kreskowym jest
            // bardziej użyteczny, bo wychodzi też przy skanowaniu.
            val istniejace = HashSet<String>()
            db.rawQuery("SELECT name_key FROM food", null).use { c ->
                while (c.moveToNext()) istniejace += c.getString(0)
            }

            context.assets.open(ASSET).use { raw ->
                raw.bufferedReader().forEachLine { line ->
                    val f = line.split('\t')
                    if (f.size < COLUMNS) return@forEachLine

                    val name = f[0].trim()
                    if (name.isEmpty()) return@forEachLine

                    val kcal = f[1].toDoubleOrNull() ?: return@forEachLine
                    val protein = f[2].toDoubleOrNull() ?: return@forEachLine
                    val carbs = f[3].toDoubleOrNull() ?: return@forEachLine
                    val fat = f[4].toDoubleOrNull() ?: return@forEachLine

                    // Klucz liczony tutaj, a nie w paczce: gdyby reguła
                    // normalizacji nazw kiedyś się zmieniła, paczka zostałaby
                    // z kluczami w starej postaci i przestała się znajdować.
                    val key = searchKey(name)
                    if (!istniejace.add(key)) return@forEachLine

                    insert.clearBindings()
                    insert.bindString(1, name)
                    insert.bindString(2, key)
                    insert.bindDouble(3, roundKcal(kcal))
                    insert.bindDouble(4, roundGram(protein))
                    insert.bindDouble(5, roundGram(carbs))
                    insert.bindDouble(6, roundGram(fat))
                    bindNumberOrNull(insert, 7, f[5])
                    bindNumberOrNull(insert, 8, f[6])
                    bindNumberOrNull(insert, 9, f[7])
                    bindNumberOrNull(insert, 10, f[8])
                    bindNumberOrNull(insert, 11, f[9])
                    insert.bindString(12, FoodSource.SHOP.name)

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
     * Liczba albo brak danych.
     *
     * Puste pole znaczy „sklep tego nie podał", a nie „zero" - ta sama
     * zasada, co w [FoodPack]. Zero gramów błonnika to twierdzenie, i zwykle
     * fałszywe.
     */
    private fun bindNumberOrNull(st: SQLiteStatement, i: Int, raw: String) {
        val v = raw.trim().toDoubleOrNull()
        if (v == null || v < 0) st.bindNull(i) else st.bindDouble(i, roundGram(v))
    }
}
