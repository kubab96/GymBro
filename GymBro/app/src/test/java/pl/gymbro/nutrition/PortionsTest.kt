package pl.gymbro.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Food
import pl.gymbro.data.FoodPortion
import pl.gymbro.data.Meal
import pl.gymbro.data.NutritionTarget
import pl.gymbro.data.Nutrients

/**
 * Porcje, gramatura i sumy dnia - `docs/kalorie.md`, rozdział 7.
 */
class PortionsTest {

    private val kromka = FoodPortion(id = 1, foodId = 1, label = "kromka", grams = 35.0)

    private val chleb = Food(
        id = 1,
        barcode = "5901234567890",
        name = "Chleb żytni",
        brand = "Oskroba",
        per100 = Nutrients(
            kcal = 250.0,
            proteinG = 8.5,
            carbsG = 48.0,
            fatG = 1.2,
            fiberG = 6.0,
            saltG = 1.1
        ),
        servingG = 30.0,
        packageG = 500.0,
        portions = listOf(kromka)
    )

    // ---- Gramatura -------------------------------------------------------

    @Test
    fun `gramy wprost`() {
        assertEquals(36.0, Portions.gramsOf(chleb, Portions.Amount.Grams(36.0))!!, 0.001)
    }

    @Test
    fun `porcja nazwana mnozy sie przez liczbe porcji`() {
        // Nikt nie wie, ile wazy kromka. Kazdy wie, ze zjadl dwie.
        val g = Portions.gramsOf(chleb, Portions.Amount.ByPortion(kromka, 2.0))
        assertEquals(70.0, g!!, 0.001)
    }

    @Test
    fun `polowka porcji tez przechodzi`() {
        val g = Portions.gramsOf(chleb, Portions.Amount.ByPortion(kromka, 0.5))
        assertEquals(17.5, g!!, 0.001)
    }

    @Test
    fun `opakowanie i porcja producenta z etykiety`() {
        assertEquals(500.0, Portions.gramsOf(chleb, Portions.Amount.Packages(1.0))!!, 0.001)
        assertEquals(60.0, Portions.gramsOf(chleb, Portions.Amount.Servings(2.0))!!, 0.001)
    }

    @Test
    fun `nieznana jednostka nie jest zgadywana`() {
        // Produkt bez wagi opakowania nie dostaje podstawionych stu gramow -
        // lepiej zapytac o gramature, niz wpisac do bazy wymyslona liczbe.
        val bezEtykiety = chleb.copy(servingG = null, packageG = null)
        assertNull(Portions.gramsOf(bezEtykiety, Portions.Amount.Packages(1.0)))
        assertNull(Portions.gramsOf(bezEtykiety, Portions.Amount.Servings(1.0)))
    }

    @Test
    fun `ujemna ilosc nie przechodzi`() {
        assertNull(Portions.gramsOf(chleb, Portions.Amount.Grams(-5.0)))
    }

    // ---- Przeliczanie ----------------------------------------------------

    @Test
    fun `makro przeliczone na porcje`() {
        val n = Portions.nutrientsFor(chleb, 36.0)
        assertEquals(90.0, n.kcal, 0.001)
        assertEquals(3.06, n.proteinG, 0.0001)
        assertEquals(17.28, n.carbsG, 0.0001)
        assertEquals(0.432, n.fatG, 0.0001)
        assertEquals(2.16, n.fiberG!!, 0.0001)
    }

    @Test
    fun `przeliczenie nie niesie ogona zmiennoprzecinkowego`() {
        // 8.5 * 0.36 w Double to 3.0599999999999996, i dokladnie tyle szloby
        // do bazy, a potem wracalo do kazdej kolejnej sumy dnia. Przy
        // trzydziestu wpisach w tygodniu te ogony sie dodaja.
        // Delta zero: nie "mniej wiecej 3.06", tylko dokladnie ta liczba.
        val n = Portions.nutrientsFor(chleb, 36.0)
        assertEquals(3.06, n.proteinG, 0.0)
    }

    @Test
    fun `sol wypada w tysiecznych grama, i dlatego tam zaokraglamy`() {
        // Produkt z 0.107 g soli na 100 g daje w 30 g porcji 0.032 g.
        // Przy dwoch miejscach po przecinku dzienna suma soli byla fikcja.
        val wafel = chleb.copy(per100 = chleb.per100.copy(saltG = 0.107))
        val n = Portions.nutrientsFor(wafel, 30.0)
        assertEquals(0.032, n.saltG!!, 0.0)
    }

    @Test
    fun `brak danych skaluje sie na brak danych`() {
        // Nieznane cukry w 100 g zostaja nieznanymi cukrami w 36 g.
        val n = Portions.nutrientsFor(chleb, 36.0)
        assertNull(n.sugarsG)
        assertNull(n.saturatedG)
    }

    // ---- Wpisy -----------------------------------------------------------

    @Test
    fun `wpis przepisuje nazwe i makro, nie odwoluje sie do produktu`() {
        val wpis = Portions.entry(100L, Meal.BREAKFAST, chleb, 70.0, at = 5L)
        assertEquals("Chleb żytni, Oskroba", wpis.name)
        assertEquals(1L, wpis.foodId)
        assertEquals(70.0, wpis.grams!!, 0.001)
        assertEquals(175.0, wpis.nutrients.kcal, 0.001)
        assertFalse(wpis.isQuickEntry)
    }

    @Test
    fun `szybki wpis nie ma produktu ani gramatury`() {
        // Obiad w restauracji nie ma kodu kreskowego, a udawanie, ze ma,
        // jest najszybsza droga do porzucenia dziennika.
        val wpis = Portions.quickEntry(100L, Meal.LUNCH, "Obiad u mamy", 800.0)
        assertTrue(wpis.isQuickEntry)
        assertNull(wpis.grams)
        assertEquals(800.0, wpis.nutrients.kcal, 0.001)
        assertNull(wpis.nutrients.fiberG)
    }

    // ---- Sumy dnia -------------------------------------------------------

    @Test
    fun `suma dnia liczy posilki osobno i razem`() {
        val wpisy = listOf(
            Portions.entry(100L, Meal.BREAKFAST, chleb, 70.0, at = 1L),
            Portions.entry(100L, Meal.BREAKFAST, chleb, 35.0, at = 2L),
            Portions.quickEntry(100L, Meal.LUNCH, "Obiad", 800.0, at = 3L)
        )
        val dzien = Portions.summarize(100L, wpisy)

        assertEquals(3, dzien.entries)
        assertEquals(1062.5, dzien.total.kcal, 0.01)
        assertEquals(262.5, dzien.byMeal[Meal.BREAKFAST]!!.kcal, 0.01)
        assertEquals(800.0, dzien.byMeal[Meal.LUNCH]!!.kcal, 0.01)
        assertNull(dzien.byMeal[Meal.DINNER])
    }

    @Test
    fun `brak danych u jednego wpisu nie psuje sumy pozostalych`() {
        // Szybki wpis nie wie nic o blonniku, chleb wie. Suma ma pokazac
        // to, co wiadomo, i policzyc, ilu wpisom danych brakowalo - bo
        // "blonnik 4.2 g" przy wpisie bez danych to dolna granica, nie suma.
        val wpisy = listOf(
            Portions.entry(100L, Meal.BREAKFAST, chleb, 70.0, at = 1L),
            Portions.quickEntry(100L, Meal.LUNCH, "Obiad", 800.0, at = 2L)
        )
        val dzien = Portions.summarize(100L, wpisy)

        assertEquals(4.2, dzien.total.fiberG!!, 0.001)
        assertEquals(1, dzien.missing.fiber)
        assertTrue(dzien.missing.any)
    }

    @Test
    fun `dzien bez zadnych danych o skladniku pokazuje brak, nie zero`() {
        // Zero gramow blonnika to twierdzenie, i zwykle falszywe.
        val wpisy = listOf(
            Portions.quickEntry(100L, Meal.LUNCH, "Obiad", 800.0, at = 1L),
            Portions.quickEntry(100L, Meal.DINNER, "Kolacja", 600.0, at = 2L)
        )
        val dzien = Portions.summarize(100L, wpisy)

        assertNull(dzien.total.fiberG)
        assertEquals(2, dzien.missing.fiber)
    }

    @Test
    fun `pusty dzien jest pusty, nie zerowy`() {
        val dzien = Portions.summarize(100L, emptyList())
        assertTrue(dzien.isEmpty)
        assertEquals(0.0, dzien.total.kcal, 0.0)
        assertFalse(dzien.missing.any)
    }

    // ---- Do celu ---------------------------------------------------------

    @Test
    fun `przekroczenie celu pokazuje sie liczba ujemna`() {
        // "Zostalo 0 kcal" i "jestes 620 kcal nad celem" to dwie rozne
        // wiadomosci, a tylko druga jest przydatna wieczorem.
        val cel = NutritionTarget(0L, 2280.0, 163.0, 265.0, 63.0)
        val dzien = Portions.summarize(
            100L,
            listOf(Portions.quickEntry(100L, Meal.LUNCH, "Wszystko", 2900.0))
        )
        val zostalo = Portions.remaining(dzien, cel)

        assertEquals(-620.0, zostalo.kcal, 0.01)
        assertTrue(zostalo.overKcal)
    }

    @Test
    fun `do celu zostaje tyle, ile zostaje`() {
        val cel = NutritionTarget(0L, 2280.0, 163.0, 265.0, 63.0)
        val dzien = Portions.summarize(
            100L,
            listOf(Portions.quickEntry(100L, Meal.BREAKFAST, "Owsianka", 480.0, proteinG = 20.0))
        )
        val zostalo = Portions.remaining(dzien, cel)

        assertEquals(1800.0, zostalo.kcal, 0.01)
        assertEquals(143.0, zostalo.proteinG, 0.01)
        assertFalse(zostalo.overKcal)
    }
}
