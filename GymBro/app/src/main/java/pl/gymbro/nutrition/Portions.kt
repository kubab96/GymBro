package pl.gymbro.nutrition

import pl.gymbro.data.Food
import pl.gymbro.data.FoodLogEntry
import pl.gymbro.data.FoodPortion
import pl.gymbro.data.Meal
import pl.gymbro.data.NutritionTarget
import pl.gymbro.data.Nutrients
import pl.gymbro.data.roundGram
import pl.gymbro.data.roundKcal

/**
 * Porcje, gramatura i sumy dnia.
 *
 * Projekt: `docs/kalorie.md`, rozdział 7.
 *
 * Wszystko tutaj jest czystą arytmetyką na modelu - ale arytmetyką, która
 * decyduje o tym, czy dziennik da się wypełniać. Nikt nie wie, ile waży
 * kromka chleba; każdy wie, że zjadł dwie.
 */
object Portions {

    /**
     * Ile czegoś zjedzono, w jednostce, w jakiej człowiek o tym myśli.
     *
     * Cztery warianty zamiast jednego pola „gramy", bo wpisywanie w gramach
     * wymaga wagi kuchennej przy każdym posiłku. Trzy z nich mogą się nie
     * dać przeliczyć - produkt nie musi mieć znanej porcji producenta ani
     * wagi opakowania - i wtedy [gramsOf] zwraca null, zamiast podstawiać
     * liczbę z powietrza.
     */
    sealed interface Amount {
        /** Zważone wprost. */
        data class Grams(val g: Double) : Amount

        /** Policzone w porcjach nazwanych: dwie kromki, pół łyżki. */
        data class ByPortion(val portion: FoodPortion, val count: Double) : Amount

        /** Całe opakowania - wymaga [Food.packageG]. */
        data class Packages(val count: Double) : Amount

        /** Porcje producenta z etykiety - wymaga [Food.servingG]. */
        data class Servings(val count: Double) : Amount
    }

    /** Gramy z podanej ilości; null, gdy produkt nie zna tej jednostki. */
    fun gramsOf(food: Food, amount: Amount): Double? {
        val grams = when (amount) {
            is Amount.Grams -> amount.g
            is Amount.ByPortion -> amount.portion.grams * amount.count
            is Amount.Packages -> (food.packageG ?: return null) * amount.count
            is Amount.Servings -> (food.servingG ?: return null) * amount.count
        }
        return if (grams < 0.0) null else roundGram(grams)
    }

    /** Wartości odżywcze za podaną ilość produktu. */
    fun nutrientsFor(food: Food, grams: Double): Nutrients =
        food.per100.scaled(grams / 100.0)

    /**
     * Wpis do dziennika z produktu i gramatury.
     *
     * Nazwa i makro są tu **przepisywane**, nie wiązane odwołaniem - patrz
     * uzasadnienie przy [FoodLogEntry].
     */
    fun entry(
        day: Long,
        meal: Meal,
        food: Food,
        grams: Double,
        at: Long = 0
    ): FoodLogEntry = FoodLogEntry(
        day = day,
        meal = meal,
        foodId = food.id.takeIf { it > 0 },
        name = food.fullName,
        grams = roundGram(grams),
        nutrients = nutrientsFor(food, grams),
        at = at
    )

    /**
     * Szybki wpis samych kalorii.
     *
     * Obiad w restauracji nie ma kodu kreskowego ani znanego składu, a
     * udawanie, że ma, jest najszybszym sposobem na porzucenie dziennika.
     * Makro jest opcjonalne; brak znaczy brak, nie zero.
     */
    fun quickEntry(
        day: Long,
        meal: Meal,
        name: String,
        kcal: Double,
        proteinG: Double = 0.0,
        carbsG: Double = 0.0,
        fatG: Double = 0.0,
        at: Long = 0
    ): FoodLogEntry = FoodLogEntry(
        day = day,
        meal = meal,
        foodId = null,
        name = name,
        grams = null,
        nutrients = Nutrients(
            kcal = roundKcal(kcal),
            proteinG = roundGram(proteinG),
            carbsG = roundGram(carbsG),
            fatG = roundGram(fatG)
        ),
        at = at
    )

    /**
     * Ile wpisów nie miało danych o składniku opcjonalnym.
     *
     * Potrzebne, żeby suma dnia mogła być uczciwa. „Błonnik: 12 g" przy
     * pięciu wpisach bez danych to nie suma, to dolna granica - i ekran
     * powinien móc to powiedzieć.
     */
    data class Missing(
        val fiber: Int = 0,
        val sugars: Int = 0,
        val saturated: Int = 0,
        val salt: Int = 0
    ) {
        val any: Boolean get() = fiber > 0 || sugars > 0 || saturated > 0 || salt > 0
    }

    data class DaySummary(
        val day: Long,
        val total: Nutrients,
        val byMeal: Map<Meal, Nutrients>,
        val entries: Int,
        val missing: Missing
    ) {
        val isEmpty: Boolean get() = entries == 0
    }

    /** Podsumowanie dnia: suma całości, sumy posiłków i to, czego brakuje. */
    fun summarize(day: Long, entries: List<FoodLogEntry>): DaySummary {
        val total = entries.fold(Nutrients.ZERO) { acc, e -> acc + e.nutrients }
        val byMeal = entries
            .groupBy { it.meal }
            .mapValues { (_, list) ->
                list.fold(Nutrients.ZERO) { acc, e -> acc + e.nutrients }
            }
        val missing = Missing(
            fiber = entries.count { it.nutrients.fiberG == null },
            sugars = entries.count { it.nutrients.sugarsG == null },
            saturated = entries.count { it.nutrients.saturatedG == null },
            salt = entries.count { it.nutrients.saltG == null }
        )
        return DaySummary(day, total, byMeal, entries.size, missing)
    }

    /**
     * Co zostało do celu. Wartości ujemne znaczą przekroczenie.
     *
     * Bez wartości bezwzględnej i bez obcinania do zera: „zostało 0 kcal"
     * i „jesteś 600 kcal nad celem" to dwie różne wiadomości, a tylko druga
     * jest przydatna wieczorem.
     */
    data class Remaining(
        val kcal: Double,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double
    ) {
        val overKcal: Boolean get() = kcal < 0.0
    }

    fun remaining(summary: DaySummary, target: NutritionTarget): Remaining = Remaining(
        kcal = roundKcal(target.kcal - summary.total.kcal),
        proteinG = roundGram(target.proteinG - summary.total.proteinG),
        carbsG = roundGram(target.carbsG - summary.total.carbsG),
        fatG = roundGram(target.fatG - summary.total.fatG)
    )
}
