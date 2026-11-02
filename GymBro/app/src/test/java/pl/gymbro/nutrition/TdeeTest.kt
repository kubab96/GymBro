package pl.gymbro.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.TdeeMethod
import kotlin.math.abs

/**
 * Oszacowanie wydatku z bilansu energii - `docs/kalorie.md`, rozdziały 3 i 4.
 *
 * Wszystkie przypadki przy **ważeniu raz w tygodniu**, bo tak się ważysz,
 * a to jest najtrudniejszy wariant: jeden punkt na tydzień niesie pełny szum
 * dobowy i nie ma czym go uśrednić.
 *
 * Dzień odniesienia 1000 i okno 56 dni, czyli 945..1000.
 */
class TdeeTest {

    private val dzisiaj = 1000L
    private val poczatekOkna = dzisiaj - Tdee.WINDOW_DAYS + 1

    /** Wzór dla osoby odniesienia z `BmrTest` - 85 kg, 10 tys. kroków, 4 treningi. */
    private val wzor = 2938.3

    private fun wazenia(od: Long, masy: List<Double>) =
        masy.mapIndexed { i, kg -> Tdee.WeighIn(od + 7L * i, kg) }

    private fun dziennik(od: Long, dni: Int, kcal: Double) =
        (0 until dni).map { Tdee.IntakeDay(od + it, kcal) }

    private fun oszacuj(
        wazenia: List<Tdee.WeighIn>,
        dziennik: List<Tdee.IntakeDay>,
        fromDay: Long? = null
    ) = Tdee.estimate(wzor, wazenia, dziennik, dzisiaj, fromDay = fromDay)

    // ---- Kiedy pomiar w ogóle wchodzi -----------------------------------

    @Test
    fun `trzy wazenia to za malo, zostaje wzor`() {
        val e = oszacuj(
            wazenia(poczatekOkna, listOf(85.0, 84.8, 84.5)),
            dziennik(poczatekOkna, 56, 2400.0)
        )
        assertEquals(TdeeMethod.FORMULA, e.method)
        assertEquals(wzor, e.kcal, 0.1)
        assertNull(e.trendKgPerWeek)
        // Komunikat ma byc instrukcja, nie informacja o porazce.
        assertTrue(e.note, e.note.contains("1 ważenie"))
    }

    @Test
    fun `cztery wazenia w trzy tygodnie to za krotko`() {
        // Punktow dosc, rozpietosci nie: 21 dni przy wymaganych 28.
        val od = dzisiaj - 21
        val e = oszacuj(
            wazenia(od, listOf(85.0, 84.6, 84.3, 83.9)),
            dziennik(poczatekOkna, 56, 2400.0)
        )
        assertEquals(TdeeMethod.FORMULA, e.method)
        assertTrue(e.note, e.note.contains("21 dni"))
    }

    @Test
    fun `dziurawy dziennik nie uprawnia do pomiaru`() {
        // Bez tego progu wystarczyloby zapominac o kolacjach, zeby algorytm
        // oglosil niski wydatek i obnizyl cel.
        val e = oszacuj(
            wazenia(poczatekOkna, List(8) { 85.0 }),
            dziennik(poczatekOkna, 20, 2400.0)
        )
        assertEquals(TdeeMethod.FORMULA, e.method)
        assertTrue(e.note, e.note.contains("20 z 56"))
        assertEquals(20, e.loggedDays)
    }

    @Test
    fun `zerowanie oszacowania obcina okno`() {
        // Zmiana fazy odcina stare dane: po zerowaniu zostaje jeden punkt,
        // wiec wracamy do wzoru - i tak ma byc, bo pierwszy tydzien deficytu
        // to ubytek wody.
        val e = oszacuj(
            wazenia(poczatekOkna, List(8) { 85.0 }),
            dziennik(poczatekOkna, 56, 2400.0),
            fromDay = dzisiaj - 10
        )
        assertEquals(TdeeMethod.FORMULA, e.method)
    }

    // ---- Sam pomiar ------------------------------------------------------

    @Test
    fun `masa stoi, wiec wydatek rowna sie spozyciu`() {
        // Zasada naczelna calego modulu: jesli jesz 2400 i masa stoi,
        // wydatek wynosi 2400 - niezaleznie od tego, co twierdzi wzor,
        // a wzor twierdzi tu 2938.
        val e = oszacuj(
            wazenia(poczatekOkna, List(8) { 85.0 }),
            dziennik(poczatekOkna, 56, 2400.0)
        )
        assertEquals(TdeeMethod.MEASURED, e.method)
        assertEquals(2400.0, e.kcal, 50.0)
        assertEquals(0.0, e.trendKgPerWeek!!, 0.001)
        assertEquals(8, e.weighIns)
    }

    @Test
    fun `spadek masy znaczy wydatek wyzszy od spozycia`() {
        // Pol kilograma na tydzien to 550 kcal na dobe przy 7700 kcal/kg.
        val masy = (0 until 8).map { 85.0 - 0.5 * it }
        val e = oszacuj(
            wazenia(poczatekOkna, masy),
            dziennik(poczatekOkna, 56, 2200.0)
        )
        assertEquals(TdeeMethod.MEASURED, e.method)
        assertEquals(-0.5, e.trendKgPerWeek!!, 0.001)
        // Sam pomiar to 2750; wzor dociaga o kilkanascie kcal w gore.
        assertTrue(e.kcal.toString(), e.kcal > 2740.0 && e.kcal < 2820.0)
    }

    @Test
    fun `przyrost liczy sie tansza stawka niz ubytek`() {
        // Tkanka beztluszczowa to glownie woda, wiec przyrost kosztuje
        // 5500 kcal/kg, nie 7700. Przy 0.25 kg na tydzien roznica miedzy
        // tymi stawkami to blisko 80 kcal na dobe - wiecej niz szerokosc
        // samego oszacowania, wiec nie jest to subtelnosc.
        val masy = (0 until 8).map { 85.0 + 0.25 * it }
        val e = oszacuj(
            wazenia(poczatekOkna, masy),
            dziennik(poczatekOkna, 56, 3000.0)
        )
        assertEquals(0.25, e.trendKgPerWeek!!, 0.001)
        // Przy 5500: 3000 - 196 = 2804. Przy 7700 byloby 2725.
        assertTrue(e.kcal.toString(), e.kcal > 2790.0 && e.kcal < 2835.0)
    }

    @Test
    fun `kilka odczytow tego samego dnia to jedno wazenie`() {
        // Waga rozglasza odczyt wielokrotnie. Bez usrednienia jeden dzien
        // z trzema odczytami ciagnalby regresje do siebie.
        val masy = (0 until 8).map { 85.0 - 0.5 * it }
        val pojedyncze = wazenia(poczatekOkna, masy)
        val zPowtorka = pojedyncze + listOf(
            Tdee.WeighIn(poczatekOkna, 84.0),
            Tdee.WeighIn(poczatekOkna, 86.0)
        )

        val a = oszacuj(pojedyncze, dziennik(poczatekOkna, 56, 2200.0))
        val b = oszacuj(zPowtorka, dziennik(poczatekOkna, 56, 2200.0))

        assertEquals(a.weighIns, b.weighIns)
        assertEquals(a.trendKgPerWeek!!, b.trendKgPerWeek!!, 0.0001)
        assertEquals(a.kcal, b.kcal, 0.1)
    }

    // ---- Niepewność ------------------------------------------------------

    @Test
    fun `idealnie prosta linia nie daje falszywej pewnosci`() {
        // Osiem punktow lezacych doslownie na prostej - odchylenie reszt
        // wynosi zero. Bez podlogi 0.6 kg wzor na niepewnosc oglosilby
        // dokladnosc rzedu kilku kcal.
        val masy = (0 until 8).map { 85.0 - 0.5 * it }
        val e = oszacuj(
            wazenia(poczatekOkna, masy),
            dziennik(poczatekOkna, 56, 2200.0)
        )
        assertTrue(e.sdKcal.toString(), e.sdKcal > 50.0)
    }

    @Test
    fun `wiecej wazen zwieza oszacowanie`() {
        // Tabela z dokumentu: cztery wazenia +-394 kcal, osiem +-136.
        // Pierwiastek z sumy kwadratow odchylen od sredniej robi tu wszystko.
        val krotkie = oszacuj(
            wazenia(dzisiaj - 35, listOf(85.0, 84.75, 84.5, 84.25, 84.0, 83.75)),
            dziennik(poczatekOkna, 56, 2200.0)
        )
        val dlugie = oszacuj(
            wazenia(poczatekOkna, (0 until 8).map { 85.0 - 0.25 * it }),
            dziennik(poczatekOkna, 56, 2200.0)
        )
        assertTrue(
            "${krotkie.sdKcal} vs ${dlugie.sdKcal}",
            dlugie.sdKcal < krotkie.sdKcal
        )
    }

    @Test
    fun `przy slabych danych wynik lezy miedzy wzorem a pomiarem`() {
        // Piec wazen przy ubytku daje niepewnosc okolo +-209 kcal, czyli
        // mniej wagi niz wzor (300 kcal). Wynik ma wtedy lezec miedzy jednym
        // i drugim, blizej pomiaru - i ma sie nazywac mieszanka, a nie
        // udawac pomiaru.
        val e = oszacuj(
            wazenia(dzisiaj - 28, listOf(85.0, 84.75, 84.5, 84.25, 84.0)),
            dziennik(poczatekOkna, 56, 2400.0)
        )
        assertEquals(TdeeMethod.BLENDED, e.method)

        val pomiar = 2675.0 // 2400 + 0.25 kg/tyg. * 7700 / 7
        assertTrue(e.kcal.toString(), e.kcal > pomiar && e.kcal < wzor)
        assertTrue(
            e.kcal.toString(),
            abs(e.kcal - pomiar) < abs(e.kcal - wzor)
        )
    }

    @Test
    fun `dodanie wazenia nie przesuwa wyniku skokowo`() {
        // Wejscie w pomiar jest skokiem i musi byc - progi wejscia sa twarde,
        // bo policzona z trzech punktow liczba jest gorsza niz jej brak.
        // Ale OD TEGO MIEJSCA przejscie jest ciagle: kazde kolejne wazenie
        // dokłada wagi pomiarowi, a nie przestawia zworke.
        val stabilne = List(9) { 85.0 }
        var poprzednia: Double? = null
        for (n in 5..9) {
            val e = oszacuj(
                wazenia(dzisiaj - 7L * (n - 1), stabilne.take(n)),
                dziennik(poczatekOkna, 56, 2400.0)
            )
            assertNotNull(e.trendKgPerWeek)
            poprzednia?.let { assertTrue("$n: $it -> ${e.kcal}", abs(e.kcal - it) < 100.0) }
            // Kazde kolejne wazenie ma przyblizac do spozycia, nie oddalac.
            poprzednia?.let { assertTrue("$n: $it -> ${e.kcal}", e.kcal <= it) }
            poprzednia = e.kcal
        }
    }

    @Test
    fun `rozrzut spozycia tez wchodzi do niepewnosci`() {
        // Dwa dzienniki o tej samej sredniej, inny rozrzut. Ten o wiekszym
        // daje szerszy wynik - to drugie zrodlo niepewnosci po srednim
        // spozyciu, mniejsze niz nachylenie, ale nie zerowe.
        val rowno = dziennik(poczatekOkna, 56, 2400.0)
        val skokowo = (0 until 56).map {
            Tdee.IntakeDay(poczatekOkna + it, if (it % 2 == 0) 1800.0 else 3000.0)
        }
        val a = oszacuj(wazenia(poczatekOkna, List(8) { 85.0 }), rowno)
        val b = oszacuj(wazenia(poczatekOkna, List(8) { 85.0 }), skokowo)

        assertEquals(a.kcal, b.kcal, 50.0)
        assertTrue("${a.sdKcal} vs ${b.sdKcal}", b.sdKcal > a.sdKcal)
    }
}
