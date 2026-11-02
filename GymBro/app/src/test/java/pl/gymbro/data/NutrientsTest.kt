package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Model wartości odżywczych i klucz wyszukiwania.
 *
 * Reguła, której pilnuje większość tych przypadków: **zero i brak danych to
 * dwie różne rzeczy.** Zero znaczy „produkt tego nie zawiera", null znaczy
 * „nie wiadomo" - a Open Food Facts wypełnia składniki opcjonalne nierówno,
 * więc jedno i drugie zdarza się codziennie.
 */
class NutrientsTest {

    private val pelne = Nutrients(
        kcal = 100.0, proteinG = 10.0, carbsG = 20.0, fatG = 5.0,
        fiberG = 2.0, sugarsG = 8.0, saturatedG = 1.0, saltG = 0.5
    )

    private val bezOpcjonalnych = Nutrients(
        kcal = 50.0, proteinG = 5.0, carbsG = 10.0, fatG = 2.0
    )

    @Test
    fun `wymagane skladniki zawsze sie dodaja`() {
        val suma = pelne + bezOpcjonalnych
        assertEquals(150.0, suma.kcal, 0.001)
        assertEquals(15.0, suma.proteinG, 0.001)
        assertEquals(30.0, suma.carbsG, 0.001)
        assertEquals(7.0, suma.fatG, 0.001)
    }

    @Test
    fun `brak danych plus liczba daje te liczbe`() {
        // Dzien z jednym produktem bez danych o blonniku ma pokazac sume
        // czesciowa - a nie udawac, ze ten produkt mial zero blonnika.
        assertEquals(2.0, (pelne + bezOpcjonalnych).fiberG!!, 0.001)
        assertEquals(2.0, (bezOpcjonalnych + pelne).fiberG!!, 0.001)
    }

    @Test
    fun `brak danych plus brak danych zostaje brakiem`() {
        // Gdyby brak traktowac jak zero, dzien bez zadnych danych o soli
        // wygladalby jak dzien bez soli. To falszywa informacja, i w drugim
        // kierunku niz te, ktore sie wybacza.
        val suma = bezOpcjonalnych + bezOpcjonalnych
        assertNull(suma.fiberG)
        assertNull(suma.sugarsG)
        assertNull(suma.saturatedG)
        assertNull(suma.saltG)
    }

    @Test
    fun `zero nie jest brakiem danych`() {
        val zZerem = bezOpcjonalnych.copy(fiberG = 0.0)
        val suma = zZerem + bezOpcjonalnych
        assertEquals(0.0, suma.fiberG!!, 0.0)
    }

    @Test
    fun `skalowanie zachowuje brak danych`() {
        val polowa = pelne.scaled(0.5)
        assertEquals(50.0, polowa.kcal, 0.001)
        assertEquals(1.0, polowa.fiberG!!, 0.001)

        val polowaBez = bezOpcjonalnych.scaled(0.5)
        assertEquals(25.0, polowaBez.kcal, 0.001)
        assertNull(polowaBez.fiberG)
    }

    @Test
    fun `zero jako suma pustej listy nie wnosi zerowych skladnikow`() {
        // Nutrients.ZERO jest punktem startowym kazdej sumy dnia, wiec jego
        // skladniki opcjonalne musza byc puste, nie zerowe - inaczej kazdy
        // dzien raportowalby zero blonnika.
        assertNull(Nutrients.ZERO.fiberG)
        assertEquals(0.0, Nutrients.ZERO.kcal, 0.0)
    }

    @Test
    fun `kalorie z makro sluza do wylapywania bledow w danych`() {
        // 10 * 4 + 20 * 4 + 5 * 9 = 165, a etykieta mowi 100. Taki rozjazd
        // to zwykle kilodzule wpisane w pole kilokalorii albo literowka
        // przy przepisywaniu etykiety.
        assertEquals(165.0, pelne.kcalFromMacros, 0.001)
    }

    // ---- Klucz wyszukiwania ---------------------------------------------

    @Test
    fun `ogonki znikaja, bo nikt ich nie wpisuje jedna reka w sklepie`() {
        assertEquals("zurek staropolski", searchKey("Żurek Staropolski"))
        assertEquals("losos wedzony", searchKey("Łosoś wędzony"))
        assertEquals("maslo oslone", searchKey("Masło Osłone"))
    }

    @Test
    fun `l z kreska tez`() {
        // Normalizer rozklada znaki na litere i znak diakrytyczny, co dziala
        // dla wiekszosci - ale nie dla "l z kreska", ktora nie ma rozkladu
        // kanonicznego i przezywala normalizacje nietknieta. Akurat ta litera
        // jest w polskich nazwach wszedzie, stad wlasna tablica.
        assertEquals("mleko", searchKey("mleko"))
        assertEquals("jogurt naturalny", searchKey("Jogurt Naturalny"))
        assertEquals("chalwa", searchKey("Chałwa"))
    }

    @Test
    fun `znaki inne niz litery i cyfry staja sie odstepami`() {
        // "mleko 3,2%" i "mleko 3.2 %" maja trafiac w to samo.
        assertEquals("mleko 3 2", searchKey("Mleko 3,2%"))
        assertEquals("mleko 3 2", searchKey("Mleko 3.2 %"))
    }

    @Test
    fun `nadmiarowe odstepy sie zwijaja`() {
        assertEquals("jogurt naturalny", searchKey("  Jogurt    naturalny  "))
    }

    @Test
    fun `puste wejscie daje pusty klucz`() {
        assertEquals("", searchKey(""))
        assertEquals("", searchKey("   "))
        assertEquals("", searchKey("%%%"))
    }
}
