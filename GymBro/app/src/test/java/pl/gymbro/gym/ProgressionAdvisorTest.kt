package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.Phase
import pl.gymbro.data.PlanItem
import pl.gymbro.data.Plate
import pl.gymbro.data.SetEntry
import pl.gymbro.data.TrainingGoal

/**
 * Przypadki wprost z `docs/progresja.md`, rozdział 5.
 *
 * Drabinka sztangi odtworzona z prawdziwego sprzętu: gryf 10 kg, talerze
 * 10x4, 5x8, 3.4x4, 1.15x4 - te same liczby, na których liczony był dokument.
 */
class ProgressionAdvisorTest {

    private val barbell = listOf(
        68.2, 70.0, 71.4, 72.3, 73.6, 74.6, 75.9, 76.8, 78.2, 79.1,
        80.0, 81.4, 82.3, 83.6, 84.6, 85.9, 86.8, 88.2, 89.1, 90.0
    )

    private fun item(
        sets: Int = 4,
        target: Double = 80.0,
        from: Int = 8,
        to: Int = 12,
        goal: TrainingGoal? = null,
        noProgress: Boolean = false
    ) = PlanItem(
        id = 1, planId = 1, exerciseId = 1, position = 0,
        targetKg = target, sets = sets, repsFrom = from, repsTo = to, restSec = 180,
        equipment = Equipment.BARBELL, goal = goal, noProgress = noProgress
    )

    private fun outing(weight: Double, reps: Int, n: Int = 4, rir: Int? = null) =
        ProgressionAdvisor.Outing(
            (0 until n).map {
                SetEntry(
                    sessionId = 1, exerciseId = 1, setIndex = it,
                    weightKg = weight, reps = reps, rir = rir, doneAt = 0
                )
            }
        )

    private fun advise(
        history: List<ProgressionAdvisor.Outing>,
        item: PlanItem = item(),
        ladder: List<Double> = barbell,
        goal: TrainingGoal = TrainingGoal.MIXED,
        phase: Phase = Phase.MAINTAIN
    ) = ProgressionAdvisor.advise(item, history, ladder, goal, phase)

    // ---- Szczyt zakresu ---------------------------------------------------

    @Test
    fun `propozycja nie niesie ogona zmiennoprzecinkowego`() {
        // Suma talerzy to 81.39999999999999. Na ekranie tego nie widac, bo
        // formatowanie tnie do jednego miejsca - ale w bazie ogon zostawal
        // i wracal przy kazdym kolejnym porownaniu ciezaru.
        // Drabinka liczona tak jak liczy ja kalkulator talerzy: gryf plus
        // dwa razy suma strony. Literal 71.4 nie ma ogona, suma 20+5+3.4+1.15
        // juz tak - i wlasnie taka wartosc trafiala do bazy.
        val zOgonem = 10.0 + 2 * (20.0 + 5.0 + 3.4 + 1.15 + 1.15)
        val drabinka = listOf(70.0, zOgonem, 72.3)
        val w = ProgressionAdvisor.advise(
            item(target = 70.0), listOf(outing(70.0, 12, rir = 1)),
            drabinka, TrainingGoal.MIXED, Phase.MAINTAIN
        )?.weightKg!!
        assertEquals("propozycja ma byc zaokraglona", w, Math.round(w * 100.0) / 100.0, 0.0)
    }

    @Test
    fun `szczyt zakresu z zapasem podnosi ciezar`() {
        val a = advise(listOf(outing(80.0, 12, rir = 1)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertEquals(81.4, a?.weightKg!!, 0.01)
        assertTrue(a.preselected)
    }

    @Test
    fun `zapas zero nie blokuje, tylko zmniejsza skok`() {
        val zero = advise(listOf(outing(80.0, 12, rir = 0)))
        val dwa = advise(listOf(outing(80.0, 12, rir = 2)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, zero?.kind)
        assertEquals(81.4, zero?.weightKg!!, 0.01)
        assertTrue("wiekszy zapas to wiekszy skok", dwa?.weightKg!! > zero.weightKg!!)
    }

    @Test
    fun `duzy zapas podnosi mocniej mimo niewyrobionego zakresu`() {
        val a = advise(listOf(outing(80.0, 10, rir = 3)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertTrue(a?.weightKg!! > 82.0)
    }

    @Test
    fun `bez zapasu i bez szczytu zakresu milczymy`() {
        assertNull(advise(listOf(outing(80.0, 10, rir = 1))))
    }

    // ---- Duze przekroczenie zakresu ---------------------------------------

    @Test
    fun `cztery razy dwadziescia to nie dwa progi`() {
        val a = advise(listOf(outing(80.0, 20, rir = 0)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        // Rownowaznosc daje okolo 95 kg. Limit dziesieciu procent to 88.0,
        // a najwyzszy prog mieszczacy sie pod nim to 86.8 - limit jest limitem,
        // wiec nie przeskakujemy go zaokragleniem do najblizszego progu.
        assertEquals(86.8, a?.weightKg!!, 0.01)
    }

    @Test
    fun `przekroczenie rosnie, skok rosnie`() {
        val malo = advise(listOf(outing(80.0, 12, rir = 0)))?.weightKg!!
        val srednio = advise(listOf(outing(80.0, 15, rir = 0)))?.weightKg!!
        val duzo = advise(listOf(outing(80.0, 20, rir = 0)))?.weightKg!!
        assertTrue("$malo < $srednio", malo < srednio)
        assertTrue("$srednio <= $duzo", srednio <= duzo)
    }

    @Test
    fun `nigdy wiecej niz dziesiec procent naraz`() {
        val a = advise(listOf(outing(80.0, 30, rir = 0)))
        assertTrue(a?.weightKg!! <= 88.0)
    }

    // ---- Gruby prog ciezaru -----------------------------------------------

    @Test
    fun `gruby prog rozszerza zakres zamiast dokladac ciezar`() {
        val para = listOf(16.8, 18.6, 20.4)
        val a = advise(
            listOf(outing(16.8, 12, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8),
            ladder = para
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_UP, a?.kind)
        assertEquals(14, a?.repsTo)
    }

    @Test
    fun `dlugi zakres dokłada serie`() {
        val para = listOf(16.8, 18.6, 20.4)
        val a = advise(
            listOf(outing(16.8, 16, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8, to = 16),
            ladder = para
        )
        assertEquals(ProgressionAdvisor.Kind.SET_UP, a?.kind)
        assertEquals(4, a?.sets)
    }

    @Test
    fun `dla sily nie dokladamy serii`() {
        val para = listOf(16.8, 18.6, 20.4)
        val a = advise(
            listOf(outing(16.8, 16, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8, to = 16),
            ladder = para,
            goal = TrainingGoal.STRENGTH
        )
        assertNull(a)
    }

    // ---- Zastoj -----------------------------------------------------------

    @Test
    fun `trzy sesje na granicy bez poprawy proponuja zejscie`() {
        val a = advise(List(3) { outing(80.0, 10, rir = 0) })
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_DOWN, a?.kind)
        assertEquals(72.0, a?.weightKg!!, 0.1)
        assertTrue("zejscie ma byc odznaczone", !a.preselected)
    }

    @Test
    fun `rosnace powtorzenia to nie zastoj`() {
        val a = advise(
            listOf(outing(80.0, 11, rir = 0), outing(80.0, 10, rir = 0), outing(80.0, 9, rir = 0))
        )
        assertNull("powtorzenia rosly - to postep", a)
    }

    @Test
    fun `na redukcji zastoj zglaszamy pozniej`() {
        val trzy = List(3) { outing(80.0, 10, rir = 0) }
        assertNotNull(advise(trzy, phase = Phase.MAINTAIN))
        assertNull(advise(trzy, phase = Phase.CUT))
    }

    // ---- Kiedy milczymy ---------------------------------------------------

    @Test
    fun `wyciszone cwiczenie nie odzywa sie wcale`() {
        assertNull(advise(listOf(outing(80.0, 12, rir = 3)), item = item(noProgress = true)))
    }

    @Test
    fun `mniej serii niz w planie to nie podstawa`() {
        assertNull(advise(listOf(outing(80.0, 12, n = 2, rir = 1))))
    }

    @Test
    fun `spadek powtorzen wstrzymuje propozycje`() {
        val a = advise(listOf(outing(80.0, 12, rir = 0), outing(80.0, 14, rir = 0)))
        assertNull("powtorzenia spadly z 14 na 12", a)
    }

    @Test
    fun `ciezar ponizej planu nie jest podstawa do podnoszenia`() {
        assertNull(advise(listOf(outing(72.3, 12, rir = 1))))
    }

    // ---- Wiarygodnosc zapasu ----------------------------------------------

    @Test
    fun `zapas bez zmiennosci jest ignorowany`() {
        val same = (1..8).map {
            SetEntry(sessionId = 1, exerciseId = 1, setIndex = it, weightKg = 80.0,
                reps = 10, rir = 0, doneAt = 0)
        }
        assertTrue(ProgressionAdvisor.rirIsUseless(same))

        val mixed = same.mapIndexed { i, s -> s.copy(rir = i % 3) }
        assertTrue(!ProgressionAdvisor.rirIsUseless(mixed))
    }

    @Test
    fun `bez zapasu dzialamy na samych powtorzeniach`() {
        val a = advise(listOf(outing(80.0, 12, rir = null)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
    }

    // ---- Ciezar dowolny ---------------------------------------------------

    @Test
    fun `bez drabinki ciezar rosnie plynnie`() {
        val a = advise(listOf(outing(40.0, 12, rir = 1)), ladder = emptyList(),
            item = item(target = 40.0))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertTrue(a?.weightKg!! > 40.0)
        assertTrue("limit pieciu procent", a.weightKg!! <= 42.1)
    }
    // ---- Drabinka, jaka naprawde dostaje doradca -------------------------

    /**
     * Powyzsze testy karmia doradce **wszystkimi** osiagalnymi progami, zeby
     * sprawdzic same reguly przycinania. Aplikacja podaje mu wezszy zestaw:
     * tylko progi, ktore da sie zlozyc bez rozkladania sztangi. Ten test
     * przechodzi cala droge - kalkulator talerzy, potem doradca - i pilnuje,
     * zeby na wyjsciu nie bylo ciezaru wymagajacego przelozenia kompletu.
     */
    @Test
    fun `propozycja trzyma sie progow, ktore da sie zlozyc miedzy seriami`() {
        val sprzet = Inventory(
            barKg = 10.0,
            plates = listOf(
                Plate(kg = 10.0, count = 4),
                Plate(kg = 5.0, count = 8),
                Plate(kg = 3.4, count = 4),
                Plate(kg = 1.15, count = 4)
            )
        )
        val wygodne = PlateCalculator
            .handyLadder(80.0, LoadMode.BARBELL, sprzet)
            .map { it.totalKg }

        val a = advise(listOf(outing(80.0, 12, rir = 1)), ladder = wygodne)
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        // Nie 81,4 kg (zdjecie piatki i trzy inne krazki), a 82,3 kg:
        // jeden najmniejszy talerz dolozony z kazdej strony.
        assertEquals(82.3, a?.weightKg!!, 0.01)
    }
    // ---- Z prawdziwego treningu A z 29.10.2026 ---------------------------

    /**
     * Dwadziescia powtorzen przy zakresie 8-12 to nie przypadek graniczny.
     *
     * Regula 2-for-2 Petersona kaze poczekac na potwierdzenie z drugiej sesji,
     * zanim podniesiemy ciezar - ale ona mowi o dobiciu GORY zakresu, czyli
     * o sytuacji na styk. Przy przekroczeniu o polowe nie ma czego
     * potwierdzac i czekanie wyglada jak brak reakcji.
     */
    @Test
    fun `duze przekroczenie zakresu nie czeka na potwierdzenie`() {
        // Przysiad: plan 81,4 kg, 4 x 8-12. Zrobione 20, 18, 18, 18 przy
        // zapasie 2, 2, 1, 0. Nastepny wygodny prog to 84,6 kg, czyli 3,9% -
        // powyzej progu 3%, wiec stara regula kazala czekac.
        val sprzet = Inventory(
            barKg = 10.0,
            plates = listOf(
                Plate(kg = 10.0, count = 4),
                Plate(kg = 5.0, count = 8),
                Plate(kg = 3.4, count = 4),
                Plate(kg = 1.15, count = 4)
            )
        )
        val drabinka = PlateCalculator
            .handyLadder(81.4, LoadMode.BARBELL, sprzet)
            .map { it.totalKg }

        val teraz = ProgressionAdvisor.Outing(
            listOf(20 to 2, 18 to 2, 18 to 1, 18 to 0).mapIndexed { i, (reps, rir) ->
                SetEntry(
                    sessionId = 53, exerciseId = 1, setIndex = i,
                    weightKg = 81.4, reps = reps, rir = rir, doneAt = 0
                )
            }
        )
        // Poprzedni raz: ten sam ciezar, ale bez dobicia gory zakresu -
        // czyli dokladnie to, czego stara regula wymagala, a czego nie bylo.
        val poprzednio = outing(81.4, 10)

        val a = ProgressionAdvisor.advise(
            item(target = 81.4), listOf(teraz, poprzednio), drabinka,
            TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertNotNull("przy 20 powtorzeniach na 12 musi byc propozycja", a)
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a!!.kind)
        assertTrue("ciezar ma isc w gore", a.weightKg!! > 81.4)
    }

    /**
     * Trening ciezszy niz plan: plan ma za tym nadazyc.
     *
     * OHP z tamtego treningu: plan 36,8 kg, a zrobione 39,1 kg po 12, 9 i 8
     * powtorzen - czyli w zakresie. Dotad nie dzialo sie nic, bo gora zakresu
     * nie zostala dobita, i plan zostawal na 36,8 w nieskonczonosc.
     */
    @Test
    fun `ciezszy trening niz plan podciaga plan`() {
        val teraz = ProgressionAdvisor.Outing(
            listOf(12, 9, 8).mapIndexed { i, reps ->
                SetEntry(
                    sessionId = 53, exerciseId = 4, setIndex = i,
                    weightKg = 39.1, reps = reps, rir = 0, doneAt = 0
                )
            }
        )
        val a = ProgressionAdvisor.advise(
            item(sets = 3, target = 36.8), listOf(teraz), barbell,
            TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertNotNull("trenowanie ciezej niz plan musi cos dac", a)
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a!!.kind)
        assertEquals("plan ma dogonic to, co faktycznie robisz",
            39.1, a.weightKg!!, 0.01)
    }

    @Test
    fun `ciezszy trening z powtorzeniami ponizej zakresu nie podciaga planu`() {
        // Wyciskanie z tamtego treningu konczylo sie na 7 powtorzeniach przy
        // dolnej granicy 8. To nie jest gotowosc na wiecej, tylko przeciazenie.
        val teraz = ProgressionAdvisor.Outing(
            listOf(8, 7, 6).mapIndexed { i, reps ->
                SetEntry(
                    sessionId = 53, exerciseId = 2, setIndex = i,
                    weightKg = 80.0, reps = reps, rir = 0, doneAt = 0
                )
            }
        )
        val a = ProgressionAdvisor.advise(
            item(sets = 3, target = 72.3), listOf(teraz), barbell,
            TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertNull("ponizej dolnej granicy zakresu nie podciagamy", a)
    }
}
