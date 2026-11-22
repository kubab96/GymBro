package pl.gymbro.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import pl.gymbro.nutrition.Portions
import pl.gymbro.nutrition.Recipes
import pl.gymbro.nutrition.Tdee

/**
 * Repozytoria jedzenia - jedyna droga do tabel z wersji 16.
 *
 * Osobny plik od [Repos.kt][ExerciseRepository], bo tamten ma już ponad tysiąc
 * trzysta linii; pomocnicze odczyty z kursora są wspólne i dlatego `internal`.
 *
 * Wszystkie metody są synchroniczne i blokujące - tak samo jak w `Repos.kt`.
 * Wywołuj je z wątku roboczego, nigdy z wątku interfejsu.
 */

// ---- Katalog produktów ---------------------------------------------------

class FoodRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = Food(
        id = c.long("id"),
        barcode = c.strOrNull("barcode"),
        name = c.str("name"),
        brand = c.strOrNull("brand"),
        per100 = Nutrients(
            kcal = c.dbl("kcal_100"),
            proteinG = c.dbl("protein_100"),
            carbsG = c.dbl("carbs_100"),
            fatG = c.dbl("fat_100"),
            fiberG = c.dblOrNull("fiber_100"),
            sugarsG = c.dblOrNull("sugars_100"),
            saturatedG = c.dblOrNull("saturated_100"),
            saltG = c.dblOrNull("salt_100")
        ),
        servingG = c.dblOrNull("serving_g"),
        packageG = c.dblOrNull("package_g"),
        source = FoodSource.fromName(c.str("source")),
        updatedAt = c.long("updated_at")
    )

    private fun portionFrom(c: Cursor) = FoodPortion(
        id = c.long("id"),
        foodId = c.long("food_id"),
        label = c.str("label"),
        grams = c.dbl("grams"),
        position = c.int("position")
    )

    /**
     * Klucz wyszukiwania obejmuje nazwę **i markę**.
     *
     * Bez marki wpisanie „mlekovita" nie znajdowało niczego, a tak właśnie
     * szuka się produktu, którego nazwy się nie pamięta.
     */
    private fun keyOf(food: Food): String =
        searchKey(listOfNotNull(food.name, food.brand).joinToString(" "))

    private fun values(food: Food) = ContentValues().apply {
        if (food.barcode.isNullOrBlank()) putNull("barcode") else put("barcode", food.barcode)
        put("name", food.name)
        put("name_key", keyOf(food))
        if (food.brand.isNullOrBlank()) putNull("brand") else put("brand", food.brand)
        put("kcal_100", food.per100.kcal)
        put("protein_100", food.per100.proteinG)
        put("carbs_100", food.per100.carbsG)
        put("fat_100", food.per100.fatG)
        putOrNull("fiber_100", food.per100.fiberG)
        putOrNull("sugars_100", food.per100.sugarsG)
        putOrNull("saturated_100", food.per100.saturatedG)
        putOrNull("salt_100", food.per100.saltG)
        putOrNull("serving_g", food.servingG)
        putOrNull("package_g", food.packageG)
        put("source", food.source.name)
        put("updated_at", food.updatedAt)
    }

    /** Produkt razem z porcjami - to, co pokazujemy po wybraniu z listy. */
    fun byId(id: Long): Food? =
        db.readableDatabase.rawQuery("SELECT * FROM food WHERE id = ?", arrayOf(id.toString()))
            .mapAll(::from)
            .firstOrNull()
            ?.let { it.copy(portions = portions(it.id)) }

    fun byBarcode(code: String): Food? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM food WHERE barcode = ?",
            arrayOf(code)
        ).mapAll(::from)
            .firstOrNull()
            ?.let { it.copy(portions = portions(it.id)) }

    fun portions(foodId: Long): List<FoodPortion> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM food_portion WHERE food_id = ? ORDER BY position, id",
            arrayOf(foodId.toString())
        ).mapAll(::portionFrom)

    /**
     * Wyszukiwanie po nazwie.
     *
     * Kolejność wyników jest tu całą wartością, bo przy trzydziestu tysiącach
     * produktów trafienie w pierwsze trzy wiersze decyduje o tym, czy wpis
     * zajmuje dwie sekundy, czy dwadzieścia:
     *
     * Szukamy **każdego słowa osobno**, bo nazwa produktu rzadko wygląda
     * tak, jak się ją wpisuje - patrz [searchWords]. Kolejność wyników:
     *
     * 1. wszystkie słowa na początku wyrazów przed trafieniem w środek
     *    wyrazu - „ser" ma znaleźć ser przed deserem,
     * 2. pierwsze wpisane słowo na samym początku nazwy,
     * 3. **hierarchia źródeł** - opisana niżej,
     * 4. krótsza nazwa przed dłuższą; „mleko 2 %" jest zwykle tym, czego
     *    szukasz, a nie „napój mleczny o smaku…".
     *
     * Warunek z gwiazdką z przodu nie korzysta z indeksu, więc to przegląd
     * całej tabeli - przy trzydziestu tysiącach krótkich napisów to u SQLite
     * kilkanaście milisekund i nie ma czego optymalizować. Gdyby katalog
     * kiedyś urósł o rząd wielkości, odpowiedzią jest FTS5, nie ręczne
     * sztuczki z indeksem.
     *
     * Hierarchia idzie od danych najpewniejszych do najbardziej surowych,
     * a miarą pewności jest to, **czy ktoś te liczby widział na oczy**:
     *
     * 1. produkt własny - przepisałeś z opakowania,
     * 2. przepis - policzony z Twoich składników,
     * 3. zestaw podstawowy - kurowany, sprawdzony arytmetyką,
     * 4. tabela ze strony sklepu - wartości podaje sprzedawca dla swojego
     *    asortymentu, a każdy wiersz przeszedł sprawdzenie wzorem unijnym
     *    przy pobieraniu,
     * 5. zeskanowany z Open Food Facts - trzymałeś opakowanie w ręce,
     *    a dane przyszły świeżo z serwera (stąd `updated_at > 0`),
     * 6. paczka - wsad masowy ze zrzutu, którego nikt nie oglądał.
     *
     * Porcji tu nie dociągamy - przy liście wyników byłoby to trzydzieści
     * dodatkowych zapytań na każde wpisane słowo.
     */
    fun search(query: String, limit: Int = 30): List<Food> {
        val words = searchWords(query)
        if (words.isEmpty()) return emptyList()

        // Każde słowo musi wystąpić, w dowolnym miejscu i w dowolnej
        // kolejności - patrz [searchWords].
        val warunek = words.joinToString(" AND ") { "name_key LIKE ?" }

        // Słowo stojące na początku nazwy albo na początku któregoś z jej
        // wyrazów liczy się lepiej niż trafienie w środku wyrazu. Dzięki
        // temu „ser" pokazuje ser przed deserem, a nie odwrotnie.
        val naPoczatkuWyrazu = words.joinToString(" AND ") {
            "(name_key LIKE ? OR name_key LIKE ?)"
        }

        val args = buildList {
            words.forEach { add("%$it%") }
            words.forEach { add("$it%"); add("% $it%") }
            add("${words.first()}%")
            add(limit.toString())
        }

        return db.readableDatabase.rawQuery(
            """
            SELECT * FROM food
            WHERE $warunek
            ORDER BY
                CASE WHEN $naPoczatkuWyrazu THEN 0 ELSE 1 END,
                CASE WHEN name_key LIKE ? THEN 0 ELSE 1 END,
                CASE
                    WHEN source = 'OWN' THEN 0
                    WHEN source = 'RECIPE' THEN 1
                    WHEN source = 'SEED' THEN 2
                    WHEN source = 'SHOP' THEN 3
                    WHEN source = 'OFF' AND updated_at > 0 THEN 4
                    ELSE 5
                END,
                LENGTH(name_key)
            LIMIT ?
            """.trimIndent(),
            args.toTypedArray()
        ).mapAll(::from)
    }

    /**
     * Ostatnio jedzone, najnowsze pierwsze.
     *
     * Druga co do ważności lista w całej zakładce, po samym skanowaniu:
     * jada się kilkadziesiąt produktów, nie trzydzieści tysięcy.
     */
    fun recent(limit: Int = 20): List<Food> =
        db.readableDatabase.rawQuery(
            """
            SELECT f.* FROM food f
            JOIN (
                SELECT food_id, MAX(at) AS last_at
                FROM food_log
                WHERE food_id IS NOT NULL
                GROUP BY food_id
            ) l ON l.food_id = f.id
            ORDER BY l.last_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).mapAll(::from)

    /**
     * Ile tego wzięto ostatnim razem.
     *
     * Najlepsza domyślna gramatura, jaką da się podstawić: jeśli zawsze
     * jesz 180 g ryżu, to podstawienie 100 g jest poprawką do wykonania
     * przy każdym wpisie.
     */
    fun lastGrams(foodId: Long): Double? =
        db.readableDatabase.rawQuery(
            """
            SELECT grams FROM food_log
            WHERE food_id = ? AND grams IS NOT NULL
            ORDER BY at DESC LIMIT 1
            """.trimIndent(),
            arrayOf(foodId.toString())
        ).mapAll { it.dblOrNull("grams") }.firstOrNull()

    fun insert(food: Food): Long {
        val id = db.writable.insert("food", null, values(food))
        if (id > 0 && food.portions.isNotEmpty()) setPortions(id, food.portions)
        return id
    }

    fun update(food: Food): Int =
        db.writable.update("food", values(food), "id = ?", arrayOf(food.id.toString()))

    /**
     * Dopisanie albo odświeżenie produktu ze źródła zewnętrznego.
     *
     * Kod kreskowy jest tożsamością produktu, więc drugie trafienie na ten
     * sam kod ma go **zaktualizować**, a nie założyć drugi wiersz. Własnego
     * produktu nie nadpisujemy nigdy, nawet jeśli dostał ten sam kod -
     * ręczny wpis wygrywa z danymi społecznościowymi, bo to Ty widziałeś
     * etykietę.
     */
    fun upsertFromSource(food: Food): Long {
        val code = food.barcode?.takeIf { it.isNotBlank() } ?: return insert(food)
        val existing = db.readableDatabase.rawQuery(
            "SELECT * FROM food WHERE barcode = ?",
            arrayOf(code)
        ).mapAll(::from).firstOrNull() ?: return insert(food)

        if (!existing.isRefreshable) return existing.id

        db.writable.update(
            "food",
            values(food.copy(id = existing.id)),
            "id = ?",
            arrayOf(existing.id.toString())
        )
        return existing.id
    }

    /**
     * Porcje zapisywane w całości, nie wiersz po wierszu.
     *
     * Przy edycji porcję można usunąć albo dopisać, więc dopasowywanie
     * wierszy wymagałoby trwałych identyfikatorów tam, gdzie użytkownik
     * widzi zwykłą listę. Kasowanie i zapis od nowa nie zostawia sierot.
     */
    fun setPortions(foodId: Long, portions: List<FoodPortion>) {
        val write = db.writable
        write.beginTransaction()
        try {
            write.delete("food_portion", "food_id = ?", arrayOf(foodId.toString()))
            portions.forEachIndexed { i, p ->
                write.insert("food_portion", null, ContentValues().apply {
                    put("food_id", foodId)
                    put("label", p.label)
                    put("grams", p.grams)
                    put("position", i)
                })
            }
            write.setTransactionSuccessful()
        } finally {
            write.endTransaction()
        }
    }

    /**
     * Usuwa produkt z katalogu.
     *
     * Wpisy w dzienniku zostają: mają przepisane makro i nazwę, więc
     * historia nie traci nic poza możliwością wejścia w szczegóły produktu.
     * Tak właśnie ma być - usunięcie produktu nie jest powodem, żeby
     * skasować wtorkowe śniadanie.
     */
    fun delete(id: Long): Int =
        db.writable.delete("food", "id = ?", arrayOf(id.toString()))
}

// ---- Dziennik ------------------------------------------------------------

class DiaryRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = FoodLogEntry(
        id = c.long("id"),
        day = c.long("day"),
        meal = Meal.fromName(c.str("meal")),
        foodId = c.longOrNull("food_id"),
        name = c.str("name"),
        grams = c.dblOrNull("grams"),
        nutrients = Nutrients(
            kcal = c.dbl("kcal"),
            proteinG = c.dbl("protein_g"),
            carbsG = c.dbl("carbs_g"),
            fatG = c.dbl("fat_g"),
            fiberG = c.dblOrNull("fiber_g"),
            sugarsG = c.dblOrNull("sugars_g"),
            saturatedG = c.dblOrNull("saturated_g"),
            saltG = c.dblOrNull("salt_g")
        ),
        at = c.long("at")
    )

    private fun values(e: FoodLogEntry) = ContentValues().apply {
        put("day", e.day)
        put("meal", e.meal.name)
        if (e.foodId != null) put("food_id", e.foodId) else putNull("food_id")
        put("name", e.name)
        putOrNull("grams", e.grams)
        put("kcal", e.nutrients.kcal)
        put("protein_g", e.nutrients.proteinG)
        put("carbs_g", e.nutrients.carbsG)
        put("fat_g", e.nutrients.fatG)
        putOrNull("fiber_g", e.nutrients.fiberG)
        putOrNull("sugars_g", e.nutrients.sugarsG)
        putOrNull("saturated_g", e.nutrients.saturatedG)
        putOrNull("salt_g", e.nutrients.saltG)
        put("at", if (e.at > 0) e.at else System.currentTimeMillis())
    }

    fun add(entry: FoodLogEntry): Long =
        db.writable.insert("food_log", null, values(entry))

    /** Poprawienie gramatury - razem z przeliczonym makro, bo to jedna zmiana. */
    fun update(entry: FoodLogEntry): Int =
        db.writable.update("food_log", values(entry), "id = ?", arrayOf(entry.id.toString()))

    fun delete(id: Long): Int =
        db.writable.delete("food_log", "id = ?", arrayOf(id.toString()))

    fun entries(day: Long): List<FoodLogEntry> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM food_log WHERE day = ? ORDER BY meal, at",
            arrayOf(day.toString())
        ).mapAll(::from)

    fun summary(day: Long): Portions.DaySummary = Portions.summarize(day, entries(day))

    /** Dni z choć jednym wpisem w przedziale - do kropek na pasku tygodnia. */
    fun daysWithEntries(from: Long, to: Long): Set<Long> =
        db.readableDatabase.rawQuery(
            "SELECT DISTINCT day FROM food_log WHERE day BETWEEN ? AND ?",
            arrayOf(from.toString(), to.toString())
        ).mapAll { it.long("day") }.toSet()

    /**
     * Dni z wypełnionym dziennikiem i ich suma kalorii - wejście do [Tdee].
     *
     * Dni bez wpisów **nie trafiają na tę listę** i to jest istotne: dzień
     * bez wpisów nie jest dniem zerowego spożycia. Gdyby wchodził jako zero,
     * zapomniana kolacja obniżałaby oszacowany wydatek, a za nim cel.
     */
    fun intakeDays(fromDay: Long, toDay: Long): List<Tdee.IntakeDay> =
        db.readableDatabase.rawQuery(
            """
            SELECT day, SUM(kcal) AS kcal FROM food_log
            WHERE day BETWEEN ? AND ?
            GROUP BY day
            ORDER BY day
            """.trimIndent(),
            arrayOf(fromDay.toString(), toDay.toString())
        ).mapAll { Tdee.IntakeDay(it.long("day"), it.dbl("kcal")) }

    /** Ile dni w zakresie ma jakikolwiek wpis - do oceny kompletności. */
    fun loggedDays(fromDay: Long, toDay: Long): Int =
        db.readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT day) AS n FROM food_log WHERE day BETWEEN ? AND ?",
            arrayOf(fromDay.toString(), toDay.toString())
        ).mapAll { it.int("n") }.firstOrNull() ?: 0

    /**
     * Przepisanie całego dnia na inny dzień.
     *
     * Dni jedzenia powtarzają się bardziej niż treningi - tydzień roboczy
     * bywa pięcioma kopiami tego samego. Kopiujemy przez SQL, bo przepisanie
     * dwudziestu wpisów przez model to dwadzieścia zapytań.
     */
    fun copyDay(fromDay: Long, toDay: Long, at: Long = System.currentTimeMillis()): Int {
        val ile = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS n FROM food_log WHERE day = ?",
            arrayOf(fromDay.toString())
        ).mapAll { it.int("n") }.firstOrNull() ?: 0
        if (ile == 0) return 0

        db.writable.execSQL(
            """
            INSERT INTO food_log
                (day, meal, food_id, name, grams, kcal, protein_g, carbs_g, fat_g,
                 fiber_g, sugars_g, saturated_g, salt_g, at)
            SELECT ?, meal, food_id, name, grams, kcal, protein_g, carbs_g, fat_g,
                 fiber_g, sugars_g, saturated_g, salt_g, ?
            FROM food_log WHERE day = ?
            """.trimIndent(),
            arrayOf(toDay, at, fromDay)
        )
        return ile
    }
}

// ---- Cele ----------------------------------------------------------------

class NutritionTargetRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = NutritionTarget(
        fromDay = c.long("from_day"),
        kcal = c.dbl("kcal"),
        proteinG = c.dbl("protein_g"),
        carbsG = c.dbl("carbs_g"),
        fatG = c.dbl("fat_g"),
        manual = c.bool("manual")
    )

    /**
     * Cel obowiązujący w danym dniu.
     *
     * Najświeższy wpis **nie późniejszy** niż ten dzień. Dzięki temu
     * patrząc na marzec widzisz cel z marca, a nie dzisiejszy.
     */
    fun current(day: Long): NutritionTarget? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM nutrition_target WHERE from_day <= ? ORDER BY from_day DESC LIMIT 1",
            arrayOf(day.toString())
        ).mapAll(::from).firstOrNull()

    /** Zapis celu; druga zmiana tego samego dnia nadpisuje pierwszą. */
    fun set(target: NutritionTarget) {
        db.writable.insertWithOnConflict(
            "nutrition_target",
            null,
            ContentValues().apply {
                put("from_day", target.fromDay)
                put("kcal", target.kcal)
                put("protein_g", target.proteinG)
                put("carbs_g", target.carbsG)
                put("fat_g", target.fatG)
                put("manual", if (target.manual) 1 else 0)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun history(limit: Int = 30): List<NutritionTarget> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM nutrition_target ORDER BY from_day DESC LIMIT ?",
            arrayOf(limit.toString())
        ).mapAll(::from)
}

// ---- Oszacowanie wydatku -------------------------------------------------

class TdeeRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = StoredTdee(
        day = c.long("day"),
        kcal = c.dbl("kcal"),
        method = TdeeMethod.fromName(c.str("method")),
        sdKcal = c.dbl("sd_kcal"),
        trendKgPerWeek = c.dblOrNull("trend_kg_week")
    )

    fun save(e: StoredTdee) {
        db.writable.insertWithOnConflict(
            "tdee_estimate",
            null,
            ContentValues().apply {
                put("day", e.day)
                put("kcal", e.kcal)
                put("method", e.method.name)
                put("sd_kcal", e.sdKcal)
                putOrNull("trend_kg_week", e.trendKgPerWeek)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun latest(): StoredTdee? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM tdee_estimate ORDER BY day DESC LIMIT 1",
            null
        ).mapAll(::from).firstOrNull()

    /** Krzywa wydatku - to ona pokazuje, że na redukcji wydatek spada. */
    fun history(fromDay: Long): List<StoredTdee> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM tdee_estimate WHERE day >= ? ORDER BY day",
            arrayOf(fromDay.toString())
        ).mapAll(::from)
}

// ---- Przepisy ------------------------------------------------------------

class RecipeRepository(context: Context) {
    private val db = Db.get(context)
    private val foods = FoodRepository(context)

    private fun from(c: Cursor) = Recipe(
        id = c.long("id"),
        name = c.str("name"),
        cookedG = c.dblOrNull("cooked_g"),
        portions = c.longOrNull("portions")?.toInt(),
        note = c.strOrNull("note"),
        foodId = c.longOrNull("food_id")
    )

    fun all(): List<Recipe> =
        db.readableDatabase.rawQuery("SELECT * FROM recipe ORDER BY name", null)
            .mapAll(::from)

    fun byId(id: Long): Recipe? =
        db.readableDatabase.rawQuery("SELECT * FROM recipe WHERE id = ?", arrayOf(id.toString()))
            .mapAll(::from)
            .firstOrNull()
            ?.let { it.copy(items = items(it.id)) }

    /**
     * Składniki razem z danymi produktu.
     *
     * Jedno zapytanie ze złączeniem, nie jedno na składnik: przepis
     * z dziesięcioma pozycjami otwierałby się dziesięcioma odczytami.
     */
    fun items(recipeId: Long): List<RecipeItem> =
        db.readableDatabase.rawQuery(
            """
            SELECT ri.*, f.name AS food_name,
                   f.kcal_100, f.protein_100, f.carbs_100, f.fat_100,
                   f.fiber_100, f.sugars_100, f.saturated_100, f.salt_100
            FROM recipe_item ri
            JOIN food f ON f.id = ri.food_id
            WHERE ri.recipe_id = ?
            ORDER BY ri.position, ri.id
            """.trimIndent(),
            arrayOf(recipeId.toString())
        ).mapAll { c ->
            RecipeItem(
                id = c.long("id"),
                recipeId = c.long("recipe_id"),
                foodId = c.long("food_id"),
                grams = c.dbl("grams"),
                position = c.int("position"),
                foodName = c.str("food_name"),
                per100 = Nutrients(
                    kcal = c.dbl("kcal_100"),
                    proteinG = c.dbl("protein_100"),
                    carbsG = c.dbl("carbs_100"),
                    fatG = c.dbl("fat_100"),
                    fiberG = c.dblOrNull("fiber_100"),
                    sugarsG = c.dblOrNull("sugars_100"),
                    saturatedG = c.dblOrNull("saturated_100"),
                    saltG = c.dblOrNull("salt_100")
                )
            )
        }

    /**
     * Zapis przepisu razem z jego wierszem w katalogu.
     *
     * Trzy tabele w jednej transakcji, bo przepis bez składników albo
     * przepis bez wiersza w katalogu to stany, w których aplikacja nie
     * umie działać: pierwszy nie ma makro, drugiego nie da się wpisać
     * do dziennika.
     *
     * Wiersz w katalogu jest przeliczany **przy każdym zapisie**, bo zmiana
     * jednego składnika zmienia wartości na 100 g całego dania.
     */
    fun save(recipe: Recipe): Long {
        val write = db.writable
        write.beginTransaction()
        try {
            val id = if (recipe.id > 0) {
                write.update(
                    "recipe",
                    ContentValues().apply {
                        put("name", recipe.name)
                        putOrNull("cooked_g", recipe.cookedG)
                        if (recipe.portions != null && recipe.portions > 0) {
                            put("portions", recipe.portions)
                        } else {
                            putNull("portions")
                        }
                        if (recipe.note.isNullOrBlank()) putNull("note")
                        else put("note", recipe.note)
                    },
                    "id = ?",
                    arrayOf(recipe.id.toString())
                )
                recipe.id
            } else {
                write.insert("recipe", null, ContentValues().apply {
                    put("name", recipe.name)
                    putOrNull("cooked_g", recipe.cookedG)
                    if (recipe.portions != null && recipe.portions > 0) {
                        put("portions", recipe.portions)
                    } else {
                        putNull("portions")
                    }
                    if (recipe.note.isNullOrBlank()) putNull("note")
                    else put("note", recipe.note)
                })
            }

            write.delete("recipe_item", "recipe_id = ?", arrayOf(id.toString()))
            recipe.items.forEachIndexed { i, item ->
                write.insert("recipe_item", null, ContentValues().apply {
                    put("recipe_id", id)
                    put("food_id", item.foodId)
                    put("grams", item.grams)
                    put("position", i)
                })
            }

            val stored = recipe.copy(id = id)
            val mirror = Recipes.asFood(stored)
            if (mirror != null) {
                val foodId = if (recipe.foodId != null && recipe.foodId > 0) {
                    foods.update(mirror.copy(id = recipe.foodId))
                    recipe.foodId
                } else {
                    foods.insert(mirror)
                }
                write.update(
                    "recipe",
                    ContentValues().apply { put("food_id", foodId) },
                    "id = ?",
                    arrayOf(id.toString())
                )
            }

            write.setTransactionSuccessful()
            return id
        } finally {
            write.endTransaction()
        }
    }

    /**
     * Usuwa przepis, składniki (kaskadą) i jego wiersz w katalogu.
     *
     * Wpisy w dzienniku zostają - mają przepisane makro, tak samo jak przy
     * usuwaniu produktu.
     */
    fun delete(id: Long): Int {
        val recipe = byId(id) ?: return 0
        val write = db.writable
        write.beginTransaction()
        try {
            recipe.foodId?.let { foods.delete(it) }
            val n = write.delete("recipe", "id = ?", arrayOf(id.toString()))
            write.setTransactionSuccessful()
            return n
        } finally {
            write.endTransaction()
        }
    }
}
