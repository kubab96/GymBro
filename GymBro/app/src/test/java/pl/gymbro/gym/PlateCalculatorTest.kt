package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plate

/**
 * Testy kalkulatora obciążeń.
 *
 * Używają **neutralnego zestawu** talerzy, a nie sprzętu konkretnego użytkownika:
 * testujemy reguły, nie czyjąś siłownię. Wartości oczekiwane policzone niezależnie
 * od implementacji.
 */
class PlateCalculatorTest {

    /** Gryf 20 kg, standardowe talerze plus jeden, który nie wchodzi na sztangę. */
    private val inv = Inventory(
        barKg = 20.0,
        plates = listOf(
            Plate(kg = 20.0, count = 4),
            Plate(kg = 10.0, count = 4),
            Plate(kg = 5.0, count = 4),
            Plate(kg = 2.5, count = 4),
            Plate(kg = 1.25, count = 4),
            Plate(kg = 3.0, count = 4, fitsBarbell = false)
        )
    )

    private fun totals(mode: LoadMode) =
        PlateCalculator.achievable(mode, inv).map { it.totalKg }

    // ---- Reguła podstawowa ----

    @Test
    fun `sam gryf jest osiagalny i jest najnizszym ciezarem`() {
        val t = totals(LoadMode.BARBELL)
        assertEquals(20.0, t.first(), 0.001)
    }

    @Test
    fun `ciezar sztangi to gryf plus dwukrotnosc strony`() {
        // 20 + 2 * (20 + 20) = 100
        val l = PlateCalculator.nearest(100.0, LoadMode.BARBELL, inv)
        assertNotNull(l)
        assertEquals(100.0, l!!.totalKg, 0.001)
        assertEquals(2, l.perSide[20.0])
    }

    @Test
    fun `najwiekszy osiagalny ciezar wykorzystuje caly zestaw`() {
        // strona = 2*20 + 2*10 + 2*5 + 2*2.5 + 2*1.25 = 77.5  ->  20 + 155 = 175
        assertEquals(175.0, totals(LoadMode.BARBELL).last(), 0.001)
    }

    // ---- Talerze niepasujące na gryf ----

    @Test
    fun `talerz bez pasowania na gryf nie wchodzi do ciezarow sztangi`() {
        // 20 + 2*3 = 26 byloby osiagalne, gdyby 3 kg wchodzilo na sztange
        assertFalse(totals(LoadMode.BARBELL).any { kotlin.math.abs(it - 26.0) < 0.001 })
    }

    @Test
    fun `ten sam talerz dziala przy hantlach`() {
        assertTrue(totals(LoadMode.DUMBBELL_SINGLE).any { kotlin.math.abs(it - 6.0) < 0.001 })
    }

    // ---- Zużycie talerzy zależne od trybu ----

    @Test
    fun `para hantli zuzywa cztery talerze naraz wiec ma mniejszy zasieg niz jeden hantel`() {
        // Przy parze kazdy nominal wchodzi raz na strone (count/4 = 1),
        // przy jednym hantlu dwa razy (count/2 = 2) - stad dwukrotna roznica.
        val pair = totals(LoadMode.DUMBBELL_PAIR).last()
        val single = totals(LoadMode.DUMBBELL_SINGLE).last()
        assertEquals(83.5, pair, 0.001)
        assertEquals(167.0, single, 0.001)
        assertTrue("jeden hantel musi siegac wyzej niz para", single > pair)
    }

    @Test
    fun `talerz w liczbie mniejszej niz cztery nie dziala przy parze hantli`() {
        val scarce = Inventory(
            barKg = 20.0,
            plates = listOf(Plate(kg = 1.25, count = 2))
        )
        // Dwie sztuki wystarczaja na sztange i jeden hantel, ale nie na pare.
        assertTrue(PlateCalculator.achievable(LoadMode.BARBELL, scarce).size > 1)
        assertTrue(PlateCalculator.achievable(LoadMode.DUMBBELL_SINGLE, scarce).size > 1)
        assertEquals(1, PlateCalculator.achievable(LoadMode.DUMBBELL_PAIR, scarce).size)
    }

    // ---- Progi ----

    @Test
    fun `bez wygodnego progu w zasiegu bierzemy najblizszy osiagalny`() {
        val next = PlateCalculator.nextUp(20.0, LoadMode.BARBELL, inv)
        assertNotNull(next)
        assertEquals(22.5, next!!.totalKg, 0.001)
    }

    @Test
    fun `nie ma progu powyzej maksimum`() {
        assertNull(PlateCalculator.nextUp(175.0, LoadMode.BARBELL, inv))
    }

    @Test
    fun `nie ma progu ponizej samego gryfu`() {
        assertNull(PlateCalculator.nextDown(20.0, LoadMode.BARBELL, inv))
    }

    @Test
    fun `progi sa rosnace i bez powtorzen`() {
        val t = totals(LoadMode.BARBELL)
        assertEquals(t.sorted(), t)
        assertEquals(t.size, t.distinct().size)
    }

    // ---- Wybór najmniejszej liczby krążków ----

    @Test
    fun `przy dwoch sposobach na ten sam ciezar wybieramy mniej krazkow`() {
        // 2 x 2.5 kg na strone daje to samo co 1 x 5 kg, ale 5 kg jest szybsze w zalozeniu.
        val l = PlateCalculator.nearest(30.0, LoadMode.BARBELL, inv)
        assertNotNull(l)
        assertEquals(30.0, l!!.totalKg, 0.001)
        assertEquals(1, l.perSide.values.sum())
    }

    // ---- Sprzęt nieładowany talerzami ----

    @Test
    fun `wyciag i maszyna nie maja trybu ladowania`() {
        assertNull(LoadMode.of(Equipment.CABLE))
        assertNull(LoadMode.of(Equipment.MACHINE))
        assertNull(LoadMode.of(Equipment.BODYWEIGHT))
    }

    @Test
    fun `resolve dla wyciagu zwraca zadany ciezar bez zmian`() {
        val r = PlateCalculator.resolve(37.5, Equipment.CABLE, inv)
        assertEquals(37.5, r.actualKg, 0.001)
        assertNull(r.loading)
        assertTrue(r.exact)
    }

    @Test
    fun `resolve oznacza ciezar niemozliwy do zlozenia jako nieprecyzyjny`() {
        // 21 kg nie da sie zlozyc: najblizsze to 20 albo 22.5
        val r = PlateCalculator.resolve(21.0, Equipment.BARBELL, inv)
        assertFalse(r.exact)
        assertTrue(r.actualKg == 20.0 || r.actualKg == 22.5)
    }

    @Test
    fun `pusty zestaw talerzy daje tylko sam gryf`() {
        val empty = Inventory(barKg = 20.0, plates = emptyList())
        val t = PlateCalculator.achievable(LoadMode.BARBELL, empty)
        assertEquals(1, t.size)
        assertEquals(20.0, t.first().totalKg, 0.001)
    }
    // ---- Wygodne progi: zmiana ciezaru bez rozkladania sztangi ----

    /**
     * Zestaw z nietypowymi nominalami - takie sie trafiaja, gdy ktos dokupil
     * talerze z innego systemu. Arytmetyka daje tu bardzo drobne progi, ale
     * zlozenie ich wymaga zdjecia calego kompletu, wiec nie sa to progi,
     * z ktorych da sie korzystac miedzy seriami.
     */
    private val nietypowe = Inventory(
        barKg = 10.0,
        plates = listOf(
            Plate(kg = 10.0, count = 4),
            Plate(kg = 5.0, count = 8),
            Plate(kg = 3.4, count = 4),
            Plate(kg = 1.15, count = 4)
        )
    )

    @Test
    fun `krok ustawiania siega kazdego osiagalnego progu`() {
        // Krok ciezaru to narzedzie do USTAWIANIA, wiec musi dosiegnac
        // wszystkiego, co da sie nalozyc - takze progow niewygodnych.
        // 80 kg to gryf 10 + 2 x (10+10+5+5+5); najblizszy prog w gore
        // to 81,4 kg, choc zlozenie go wymaga przelozenia kompletu.
        val next = PlateCalculator.nextUp(80.0, LoadMode.BARBELL, nietypowe)
        assertNotNull(next)
        assertEquals(81.4, next!!.totalKg, 0.001)
    }

    @Test
    fun `z niewygodnego ciezaru da sie wrocic na rowny`() {
        // To jest ten blad: gdy krok chodzil wylacznie po wygodnych progach,
        // ze stojacych 81,4 kg nie dalo sie zejsc na rowne 80, bo z tamtego
        // kompletu talerzy to az cztery ruchy. Ustawic trzeba moc wszystko.
        val dol = PlateCalculator.nextDown(81.4, LoadMode.BARBELL, nietypowe)
        assertNotNull(dol)
        assertEquals(80.0, dol!!.totalKg, 0.001)
    }

    @Test
    fun `wygodny prog w gore to dolozenie jednego talerza`() {
        // Regula wygodnych progow zyje w drabince dla propozycji progresji,
        // nie w kroku ustawiania. Z 80 kg pierwszy wygodny prog to 82,3 kg:
        // wsuwasz jeden najmniejszy talerz z kazdej strony.
        val wygodne = PlateCalculator.handyLadder(80.0, LoadMode.BARBELL, nietypowe)
        val wGore = wygodne.first { it.totalKg > 80.001 }
        assertEquals(82.3, wGore.totalKg, 0.001)

        val teraz = PlateCalculator.nearest(80.0, LoadMode.BARBELL, nietypowe)!!
        assertEquals(1, PlateCalculator.swapCost(teraz, wGore))
    }

    @Test
    fun `prog wymagajacy przelozenia kompletu nie trafia do drabiny`() {
        val wszystkie = PlateCalculator.achievable(LoadMode.BARBELL, nietypowe)
            .map { it.totalKg }
        // Nadal da sie go zlozyc - tylko nikt tego nie proponuje.
        assertTrue(wszystkie.any { kotlin.math.abs(it - 81.4) < 0.001 })

        val wygodne = PlateCalculator.handyLadder(80.0, LoadMode.BARBELL, nietypowe)
            .map { it.totalKg }
        assertFalse(wygodne.any { kotlin.math.abs(it - 81.4) < 0.001 })
        // Punkt wyjscia jest w drabinie, inaczej propozycja nie ma od czego liczyc.
        assertTrue(wygodne.any { kotlin.math.abs(it - 80.0) < 0.001 })
    }

    @Test
    fun `kazdy prog w drabinie da sie zlozyc dwoma ruchami`() {
        val teraz = PlateCalculator.nearest(80.0, LoadMode.BARBELL, nietypowe)!!
        PlateCalculator.handyLadder(80.0, LoadMode.BARBELL, nietypowe).forEach {
            assertTrue(
                "prog ${it.totalKg} kg wymaga zbyt wielu krazkow",
                PlateCalculator.swapCost(teraz, it) <= 2
            )
        }
    }

    @Test
    fun `wygodny prog w dol to wymiana jednego talerza`() {
        // Zdjecie piatki i zalozenie 3,4 - dwa ruchy na strone.
        val wygodne = PlateCalculator.handyLadder(80.0, LoadMode.BARBELL, nietypowe)
        val wDol = wygodne.last { it.totalKg < 79.999 }
        assertEquals(76.8, wDol.totalKg, 0.001)
    }

    @Test
    fun `typowy zestaw silowni nie traci najmniejszego kroku`() {
        // 80 kg = gryf 20 + 2 x (10+20). Dolozenie 1,25 na strone to jeden ruch,
        // wiec krok i wygodny prog to przy takim sprzecie to samo.
        val next = PlateCalculator.nextUp(80.0, LoadMode.BARBELL, inv)
        assertNotNull(next)
        assertEquals(82.5, next!!.totalKg, 0.001)

        val wygodne = PlateCalculator.handyLadder(80.0, LoadMode.BARBELL, inv)
        assertEquals(82.5, wygodne.first { it.totalKg > 80.001 }.totalKg, 0.001)
    }

    @Test
    fun `koszt przelozenia liczy zdjete i dolozone krazki`() {
        val a = Loading(80.0, mapOf(10.0 to 2, 5.0 to 3))
        val b = Loading(81.4, mapOf(10.0 to 2, 5.0 to 2, 3.4 to 1, 1.15 to 2))
        assertEquals(4, PlateCalculator.swapCost(a, b))
        assertEquals(4, PlateCalculator.swapCost(b, a))
        assertEquals(0, PlateCalculator.swapCost(a, a))
    }
    @Test
    fun `zaden prog nie niesie ogona zmiennoprzecinkowego`() {
        // 10 + 2 x (10+10+5+5+3,4+1,15+1,15) liczone w Double daje
        // 81.39999999999999. Na ekranie tego nie widac, ale do bazy szlo
        // dokladnie tyle i wracalo przy kazdym porownaniu ciezaru.
        PlateCalculator.achievable(LoadMode.BARBELL, nietypowe).forEach {
            assertEquals(
                "prog ${it.totalKg} niesie ogon",
                Math.round(it.totalKg * 100.0) / 100.0,
                it.totalKg,
                0.0
            )
        }
    }

    @Test
    fun `prog z talerzami o nierownych nominalach wychodzi rowno`() {
        val prog = PlateCalculator.achievable(LoadMode.BARBELL, nietypowe)
            .first { kotlin.math.abs(it.totalKg - 81.4) < 0.001 }
        assertEquals(81.4, prog.totalKg, 0.0)
    }
}
