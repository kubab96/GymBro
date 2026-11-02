package pl.gymbro.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.gymbro.data.FoodSource
import pl.gymbro.data.Nutrients
import pl.gymbro.data.Recipe
import pl.gymbro.data.RecipeItem

/**
 * Przepisy - `docs/kalorie.md`, rozdział 7.
 *
 * Przepis odniesienia: 100 g suchego ryżu i 200 g piersi z kurczaka,
 * po ugotowaniu 500 g. Składniki waży się na surowo, bo tak się je kupuje;
 * danie po ugotowaniu, bo tak się je nakłada.
 */
class RecipeTest {

    private val ryz = RecipeItem(
        foodId = 1,
        grams = 100.0,
        foodName = "Ryż biały",
        per100 = Nutrients(
            kcal = 350.0, proteinG = 7.0, carbsG = 78.0, fatG = 1.0, fiberG = 1.4
        )
    )

    private val kurczak = RecipeItem(
        foodId = 2,
        grams = 200.0,
        foodName = "Pierś z kurczaka",
        per100 = Nutrients(kcal = 110.0, proteinG = 23.0, carbsG = 0.0, fatG = 2.0)
    )

    private val przepis = Recipe(
        id = 1,
        name = "Ryż z kurczakiem",
        cookedG = 500.0,
        items = listOf(ryz, kurczak)
    )

    @Test
    fun `suma skladnikow`() {
        val suma = Recipes.totals(przepis.items)
        assertEquals(570.0, suma.kcal, 0.01)
        assertEquals(53.0, suma.proteinG, 0.01)
        assertEquals(78.0, suma.carbsG, 0.01)
        assertEquals(5.0, suma.fatG, 0.01)
        assertEquals(300.0, Recipes.rawWeight(przepis.items), 0.01)
    }

    @Test
    fun `wartosci na 100 g odnosza sie do wagi po ugotowaniu`() {
        // To jest cala trudnosc przepisow: 100 g suchego ryzu to okolo 250 g
        // ugotowanego, wiec "100 g ryzu" w dzienniku znaczy dwie rozne rzeczy
        // i jedna z nich jest dwuipolkrotnie wieksza.
        val na100 = Recipes.per100(przepis)!!
        assertEquals(114.0, na100.kcal, 0.01)
        assertEquals(10.6, na100.proteinG, 0.01)
        assertEquals(15.6, na100.carbsG, 0.01)
        assertEquals(1.0, na100.fatG, 0.01)
    }

    @Test
    fun `bez wagi po ugotowaniu bierzemy sume skladnikow`() {
        // Salatka ani koktajl nie zmieniaja wagi, wiec wymuszanie tam
        // wazenia gotowego dania byloby uprzykrzaniem.
        val salatka = przepis.copy(cookedG = null)
        assertEquals(300.0, Recipes.effectiveWeight(salatka), 0.01)
        val na100 = Recipes.per100(salatka)!!
        assertEquals(190.0, na100.kcal, 0.01)
        assertEquals(17.667, na100.proteinG, 0.001)
    }

    @Test
    fun `zerowa waga po ugotowaniu znaczy to samo co jej brak`() {
        val zZerem = przepis.copy(cookedG = 0.0)
        assertEquals(300.0, Recipes.effectiveWeight(zZerem), 0.01)
        assertEquals(190.0, Recipes.per100(zZerem)!!.kcal, 0.01)
    }

    @Test
    fun `brak danych u jednego skladnika zostawia sume czesciowa`() {
        // Ryz ma blonnik, kurczak nie ma danych. Suma pokazuje to, co
        // wiadomo, a nie zero.
        val na100 = Recipes.per100(przepis)!!
        assertEquals(0.28, na100.fiberG!!, 0.001)
        assertNull(na100.saltG)
    }

    @Test
    fun `pusty przepis nie ma wartosci na 100 g`() {
        // Nie ma czego dzielic, a nieskonczonosc w bazie jest gorsza
        // niz brak wiersza.
        val pusty = Recipe(name = "Nic", items = emptyList())
        assertNull(Recipes.per100(pusty))
        assertNull(Recipes.asFood(pusty))
    }

    @Test
    fun `przepis staje sie produktem, ktory da sie wpisac do dziennika`() {
        val produkt = Recipes.asFood(przepis)!!
        assertEquals("Ryż z kurczakiem", produkt.name)
        assertEquals(FoodSource.RECIPE, produkt.source)
        assertEquals(114.0, produkt.per100.kcal, 0.01)
        assertNull(produkt.barcode)
        // Cale opakowanie to tutaj cale danie - zjedzenie calego garnka
        // jednym wpisem to najczestszy przypadek przy gotowaniu na raz.
        assertEquals(500.0, produkt.packageG!!, 0.01)
    }

    @Test
    fun `zapisany przepis trafia w swoj istniejacy wiersz katalogu`() {
        // Bez tego kazdy zapis przepisu zakladalby nowy produkt, a stare
        // wiersze zostawalyby w wyszukiwaniu jako nieaktualne duplikaty.
        val zWierszem = przepis.copy(foodId = 42)
        assertEquals(42L, Recipes.asFood(zWierszem)!!.id)
        assertEquals(0L, Recipes.asFood(przepis)!!.id)
    }
}
