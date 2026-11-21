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
import pl.gymbro.data.ProgressMode
import pl.gymbro.data.StoredAdvice
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
        progress: ProgressMode = ProgressMode.ALL
    ) = PlanItem(
        id = 1, planId = 1, exerciseId = 1, position = 0,
        targetKg = target, sets = sets, repsFrom = from, repsTo = to, restSec = 180,
        equipment = Equipment.BARBELL, goal = goal, progress = progress
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

    private fun sets(weight: Double, vararg reps: Int) = ProgressionAdvisor.Outing(
        reps.mapIndexed { i, r ->
            SetEntry(
                sessionId = 1, exerciseId = 1, setIndex = i,
                weightKg = weight, reps = r, rir = null, doneAt = 0
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
    fun `niedobity zakres daje cel powtorzen, nie cisze`() {
        // Dotad tu byla cisza i podwojna progresja dzialala po cichu - nikt
        // nie mowil, ze nastepnym razem trzeba celowac w jedenascie.
        val a = advise(listOf(outing(80.0, 10, rir = 1)))
        assertEquals(ProgressionAdvisor.Kind.REPS_GOAL, a?.kind)
        assertEquals(11, a?.repsTo)
        assertTrue(a!!.preselected)
    }

    @Test
    fun `cel powtorzen ze sredniej, nie z najslabszej serii`() {
        val teraz = sets(80.0, 12, 11, 8, 9)
        val a = advise(listOf(teraz))
        assertEquals(ProgressionAdvisor.Kind.REPS_GOAL, a?.kind)
        assertEquals("srednia 10 plus jedno", 11, a?.repsTo)
    }

    @Test
    fun `spadek tonazu ponizej szczytu mowi bez zmian z liczbami`() {
        val a = advise(listOf(outing(80.0, 10), outing(80.0, 11)))
        assertEquals(ProgressionAdvisor.Kind.HOLD, a?.kind)
        assertTrue("powod ma niesc procent spadku: ${a?.reason}", a!!.reason.contains("-9%"))
        assertTrue(!a.preselected)
    }

    @Test
    fun `wzrost tonazu ponizej szczytu daje cel powtorzen`() {
        val a = advise(listOf(outing(80.0, 11), outing(80.0, 10)))
        assertEquals(ProgressionAdvisor.Kind.REPS_GOAL, a?.kind)
        assertEquals(12, a?.repsTo)
        assertTrue(a!!.reason.contains("+10%"))
    }

    @Test
    fun `zakres liczymy z sumy powtorzen, nie z najslabszej serii`() {
        // 13/12/12/11 to 48 powtorzen przy 4 x 12 - zakres wyczerpany, choc
        // jedna seria wyszla jedenascie.
        val a = advise(listOf(sets(80.0, 13, 12, 12, 11)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
    }

    @Test
    fun `seria ponizej dolu zakresu blokuje podbicie mimo sumy`() {
        val a = advise(listOf(sets(80.0, 20, 16, 7, 7)))
        assertTrue("seria ponizej osmiu to nie gotowosc", a?.kind != ProgressionAdvisor.Kind.WEIGHT_UP)
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
    fun `gruby prog, ktory tonaz udzwignie, idzie w ciezar`() {
        // 15/12/12 na 16,8 kg: z rownowaznosci osiem powtorzen na 18,6 kg,
        // czyli w zakresie. Dotad skok ponad piec procent zawsze przegrywal
        // z wydluzaniem zakresu.
        val para = listOf(16.8, 18.6, 20.4)
        val teraz = ProgressionAdvisor.Outing(
            listOf(15, 12, 12).mapIndexed { i, r ->
                SetEntry(sessionId = 1, exerciseId = 1, setIndex = i,
                    weightKg = 16.8, reps = r, rir = 1, doneAt = 0)
            }
        )
        val a = advise(listOf(teraz), item = item(sets = 3, target = 16.8), ladder = para)
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertEquals(18.6, a?.weightKg!!, 0.01)
        assertTrue(a.preselected)
    }

    @Test
    fun `przepasc nie do przeskoczenia wydluza zakres ponad dwanascie`() {
        // Rowne 3 x 12 na granicy (zapas 0): na 18,6 kg wychodzi siedem powtorzen.
        // Dotad od razu szla seria, a powtorzenia - jedyne, co przybliza do
        // skoku - nie rosly wcale.
        val para = listOf(16.8, 18.6, 20.4)
        val a = advise(
            listOf(outing(16.8, 12, n = 3, rir = 0)),
            item = item(sets = 3, target = 16.8),
            ladder = para
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_UP, a?.kind)
        assertEquals(14, a?.repsTo)
        assertTrue(a!!.reason, a.reason.contains("przepaść"))
    }

    @Test
    fun `przepasc 10 na 18 kg tez idzie powtorzeniami`() {
        val a = advise(
            listOf(outing(10.0, 12, n = 3, rir = 1)),
            item = item(sets = 3, target = 10.0),
            ladder = listOf(10.0, 18.0)
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_UP, a?.kind)
        assertEquals(14, a?.repsTo)
    }

    @Test
    fun `przepasc i zakres do pietnastu doklada serie`() {
        // 3 x 15 na 10 kg to z rownowaznosci okolo 15 kg na jedno powtorzenie -
        // do 18 dalej daleko. Powtorzenia wyczerpane, dochodzi seria.
        val a = advise(
            listOf(outing(10.0, 15, n = 3, rir = 1)),
            item = item(sets = 3, target = 10.0, to = 15),
            ladder = listOf(10.0, 18.0)
        )
        assertEquals(ProgressionAdvisor.Kind.SET_UP, a?.kind)
        assertEquals(4, a?.sets)
    }

    @Test
    fun `most przez przepasc konczy sie skokiem i powrotem zakresu`() {
        // Zakres wydluzony na czas przepasci do 8-14. Przy 3 x 13 jest juz
        // osiem powtorzen na 18,6 kg - skok, choc 14 nie zostalo dobite.
        val para = listOf(16.8, 18.6, 20.4)
        val pozycja = item(sets = 3, target = 16.8, to = 14)
        val a = advise(listOf(outing(16.8, 13, n = 3, rir = 1)), item = pozycja, ladder = para)
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertEquals(18.6, a?.weightKg!!, 0.01)
        assertEquals("zakres wraca do dwunastu", 12, a.repsTo)

        val poPrzyjeciu = ProgressionAdvisor.applyTo(
            pozycja.copy(advice = StoredAdvice(kind = a.kind.name, weightKg = a.weightKg, repsTo = a.repsTo))
        )
        assertEquals(18.6, poPrzyjeciu.targetKg, 0.01)
        assertEquals(12, poPrzyjeciu.repsTo)
        assertEquals(8, poPrzyjeciu.repsFrom)
    }

    @Test
    fun `zakres z dolem od dwunastu nie jest mostem i zostaje po skoku`() {
        // Lydki 15-20: wydluzony zakres jest wyborem, nie przejsciem.
        val a = advise(
            listOf(outing(16.8, 20, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8, from = 15, to = 20),
            ladder = listOf(16.8, 18.6, 20.4)
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertNull(a?.repsTo)
    }

    @Test
    fun `dla sily nie dokladamy serii`() {
        val para = listOf(16.8, 18.6, 20.4)
        val a = advise(
            listOf(outing(16.8, 12, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8),
            ladder = para,
            goal = TrainingGoal.STRENGTH
        )
        assertTrue(a?.kind != ProgressionAdvisor.Kind.SET_UP)
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
        assertEquals("tonaz rosl - to postep", ProgressionAdvisor.Kind.REPS_GOAL, a?.kind)
    }

    @Test
    fun `na redukcji zastoj zglaszamy pozniej`() {
        val trzy = List(3) { outing(80.0, 10, rir = 0) }
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_DOWN, advise(trzy, phase = Phase.MAINTAIN)?.kind)
        assertTrue(advise(trzy, phase = Phase.CUT)?.kind != ProgressionAdvisor.Kind.WEIGHT_DOWN)
    }

    // ---- Kiedy milczymy ---------------------------------------------------

    @Test
    fun `wyciszone cwiczenie nie odzywa sie wcale`() {
        assertNull(advise(listOf(outing(80.0, 12, rir = 3)), item = item(progress = ProgressMode.OFF)))
    }

    // ---- Progresja wylaczona czesciowo ------------------------------------

    @Test
    fun `bez dokladania ciezaru szczyt zakresu wydluza zakres`() {
        // Prog jest drobny (80 -> 81,4), wiec zwykle poszedlby ciezar.
        val a = advise(
            listOf(outing(80.0, 10, rir = 1)),
            item = item(to = 10, progress = ProgressMode.NO_WEIGHT)
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_UP, a?.kind)
        assertEquals(12, a?.repsTo)
    }

    @Test
    fun `bez dokladania ciezaru zakres do dwunastu doklada serie`() {
        val a = advise(
            listOf(outing(80.0, 12, rir = 1)),
            item = item(progress = ProgressMode.NO_WEIGHT)
        )
        assertEquals(ProgressionAdvisor.Kind.SET_UP, a?.kind)
        assertEquals(5, a?.sets)
    }

    @Test
    fun `bez dokladania ciezaru duzy zapas przy niedobitym zakresie to cisza`() {
        // W trybie pelnym to samo daje podbicie ciezaru - patrz wyzej.
        val a = advise(
            listOf(outing(80.0, 10, rir = 3)),
            item = item(progress = ProgressMode.NO_WEIGHT)
        )
        assertNull("w zakresie jest jeszcze miejsce na powtorzenia", a)
    }

    @Test
    fun `bez dokladania ciezaru plan nie goni ciezszego treningu`() {
        val teraz = ProgressionAdvisor.Outing(
            listOf(12, 9, 8).mapIndexed { i, reps ->
                SetEntry(
                    sessionId = 53, exerciseId = 4, setIndex = i,
                    weightKg = 39.1, reps = reps, rir = 0, doneAt = 0
                )
            }
        )
        val a = advise(
            listOf(teraz),
            item = item(sets = 3, target = 36.8, progress = ProgressMode.NO_WEIGHT)
        )
        assertTrue("podciagniecie planu to tez dolozenie ciezaru",
            a?.kind != ProgressionAdvisor.Kind.WEIGHT_UP)
    }

    @Test
    fun `bez dokladania ciezaru dlugi zakres doklada serie`() {
        val a = advise(
            listOf(outing(80.0, 16, rir = 1)),
            item = item(to = 16, progress = ProgressMode.NO_WEIGHT)
        )
        assertEquals(ProgressionAdvisor.Kind.SET_UP, a?.kind)
        assertEquals(5, a?.sets)
    }

    @Test
    fun `blokada dokladania nie blokuje zejscia po zastoju`() {
        val zastoj = List(3) { outing(80.0, 10, rir = 0) }
        listOf(ProgressMode.NO_WEIGHT, ProgressMode.NO_REPS).forEach { tryb ->
            val a = advise(zastoj, item = item(progress = tryb))
            assertEquals("tryb $tryb", ProgressionAdvisor.Kind.WEIGHT_DOWN, a?.kind)
        }
    }

    @Test
    fun `bez dokladania powtorzen drobny prog dziala jak zwykle`() {
        val a = advise(
            listOf(outing(80.0, 12, rir = 1)),
            item = item(progress = ProgressMode.NO_REPS)
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertEquals(81.4, a?.weightKg!!, 0.01)
    }

    @Test
    fun `bez dokladania powtorzen gruby prog idzie w ciezar, nie w zakres`() {
        // W trybie pelnym ten sam uklad wydluza zakres - patrz testy przepasci.
        // Zapas 0, bo z zapasem 1 rownowaznosc juz pozwala na skok od razu.
        val para = listOf(16.8, 18.6, 20.4)
        val pozycja = item(sets = 3, target = 16.8, progress = ProgressMode.NO_REPS)

        // Skok o jedenascie procent wymaga potwierdzenia z drugiej sesji -
        // do tego czasu karta mowi, ze czekamy, zamiast milczec.
        val raz = advise(listOf(outing(16.8, 12, n = 3, rir = 0)), item = pozycja, ladder = para)
        assertEquals("jedna sesja na szczycie to za malo na taki skok",
            ProgressionAdvisor.Kind.HOLD, raz?.kind)

        val dwa = advise(
            List(2) { outing(16.8, 12, n = 3, rir = 0) },
            item = pozycja, ladder = para
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, dwa?.kind)
        assertEquals(18.6, dwa?.weightKg!!, 0.01)
        assertTrue("skok ponad limit przychodzi odznaczony", !dwa.preselected)
    }

    @Test
    fun `bez dokladania powtorzen i bez wyzszego progu dochodzi seria`() {
        val sufit = listOf(15.0, 16.8)
        val a = advise(
            List(2) { outing(16.8, 12, n = 3, rir = 1) },
            item = item(sets = 3, target = 16.8, progress = ProgressMode.NO_REPS),
            ladder = sufit
        )
        assertEquals("nie ma ciezaru do dolozenia, a powtorzen nie wolno",
            ProgressionAdvisor.Kind.SET_UP, a?.kind)
    }

    // ---- Talerze wyczerpane -----------------------------------------------

    private val sufit = listOf(15.0, 16.8)

    @Test
    fun `koniec talerzy na szczycie zakresu wydluza zakres ponad dwanascie`() {
        // Dwanascie to granica wtedy, gdy ciezar moze rosnac. Tu nie moze.
        val a = advise(
            listOf(outing(16.8, 12, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8), ladder = sufit
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_UP, a?.kind)
        assertEquals(14, a?.repsTo)
        assertTrue(a!!.preselected)
    }

    @Test
    fun `koniec talerzy i zakres do pietnastu doklada serie`() {
        val a = advise(
            listOf(outing(16.8, 15, n = 3, rir = 1)),
            item = item(sets = 3, target = 16.8, to = 15), ladder = sufit
        )
        assertEquals(ProgressionAdvisor.Kind.SET_UP, a?.kind)
        assertEquals(4, a?.sets)
    }

    @Test
    fun `koniec talerzy w srodku zakresu daje cel powtorzen`() {
        // Zapas 3+ zwykle podbija ciezar - ale nie ma czego dolozyc.
        val a = advise(
            listOf(outing(16.8, 10, n = 3, rir = 3)),
            item = item(sets = 3, target = 16.8), ladder = sufit
        )
        assertEquals(ProgressionAdvisor.Kind.REPS_GOAL, a?.kind)
        assertEquals(11, a?.repsTo)
    }

    @Test
    fun `propozycja sprzed zmiany trybu przestaje pasowac`() {
        fun czeka(rodzaj: ProgressionAdvisor.Kind, tryb: ProgressMode) =
            ProgressionAdvisor.stillFits(
                item(progress = tryb).copy(advice = StoredAdvice(kind = rodzaj.name))
            )
        assertTrue(!czeka(ProgressionAdvisor.Kind.WEIGHT_UP, ProgressMode.NO_WEIGHT))
        assertTrue(czeka(ProgressionAdvisor.Kind.WEIGHT_UP, ProgressMode.NO_REPS))
        assertTrue(!czeka(ProgressionAdvisor.Kind.REPS_UP, ProgressMode.NO_REPS))
        assertTrue(czeka(ProgressionAdvisor.Kind.REPS_UP, ProgressMode.NO_WEIGHT))
        assertTrue(czeka(ProgressionAdvisor.Kind.WEIGHT_DOWN, ProgressMode.NO_WEIGHT))
        assertTrue(!czeka(ProgressionAdvisor.Kind.WEIGHT_DOWN, ProgressMode.OFF))
        assertTrue("bez propozycji nie ma czego kasowac",
            ProgressionAdvisor.stillFits(item(progress = ProgressMode.OFF)))
    }

    @Test
    fun `kod trybu w bazie nie zmienia znaczenia starych wartosci`() {
        // Kolumna trzymala dotad flage: 0 - proponuj, 1 - wycisz.
        assertEquals(ProgressMode.ALL, ProgressMode.fromCode(0))
        assertEquals(ProgressMode.OFF, ProgressMode.fromCode(1))
        assertEquals(ProgressMode.NO_WEIGHT, ProgressMode.fromCode(2))
        assertEquals(ProgressMode.NO_REPS, ProgressMode.fromCode(3))
        assertEquals("nieznany kod to pelna progresja", ProgressMode.ALL, ProgressMode.fromCode(9))
        assertEquals(ProgressMode.entries.size, ProgressMode.entries.map { it.code }.toSet().size)
    }

    @Test
    fun `mniej serii niz w planie to nie podstawa`() {
        assertNull(advise(listOf(outing(80.0, 12, n = 2, rir = 1))))
    }

    @Test
    fun `szczyt zakresu podnosi ciezar nawet po slabszym dniu`() {
        // Dawniej spadek z 14 na 12 wstrzymywal wszystko. Ale 4 x 12 przy
        // zakresie 8-12 to wyczerpany zakres, niezaleznie od tego, co bylo
        // tydzien temu - tonaz porownujemy tylko ponizej szczytu.
        val a = advise(listOf(outing(80.0, 12, rir = 0), outing(80.0, 14, rir = 0)))
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
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
        assertTrue("ponizej dolnej granicy zakresu nie podciagamy",
            a?.kind != ProgressionAdvisor.Kind.WEIGHT_UP)
    }

    // ---- Z prawdziwego treningu C z 21.11.2026 ---------------------------

    private fun wyciag(weight: Double, vararg reps: Int) = ProgressionAdvisor.Outing(
        reps.mapIndexed { i, r ->
            SetEntry(sessionId = 68, exerciseId = 18, setIndex = i,
                weightKg = weight, reps = r, rir = 1, doneAt = 0)
        }
    )

    private fun pozycjaC(from: Int, to: Int) = PlanItem(
        id = 18, planId = 3, exerciseId = 18, position = 4,
        targetKg = 40.0, sets = 3, repsFrom = from, repsTo = to, restSec = 120,
        equipment = Equipment.CABLE
    )

    @Test
    fun `prostowanie 18-13-11 przy zakresie do 12 podnosi ciezar`() {
        // Najslabsza seria 11 trzymala ten ciezar w miejscu, choc suma to 42
        // powtorzenia na 36 z pelnego zakresu.
        val a = ProgressionAdvisor.advise(
            pozycjaC(8, 12), listOf(wyciag(40.0, 18, 13, 11), wyciag(40.0, 16, 15, 12)),
            emptyList(), TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, a?.kind)
        assertTrue(a!!.weightKg!! > 40.0)
    }

    @Test
    fun `wyciag dostaje ciezar ze stosu, nie z procentu`() {
        // 36 kg na uginanie i 42 kg na prostowanie - liczby, ktorych na stosie
        // co 2,5 kg nie ma. Drabinka ta sama, po ktorej chodzi zegarek.
        val stos35 = StepLadder.rungs(35.0, null, 20)
        val uginanie = ProgressionAdvisor.advise(
            pozycjaC(8, 12).copy(targetKg = 35.0),
            listOf(wyciag(35.0, 15, 12, 12)), stos35,
            TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, uginanie?.kind)
        assertEquals(37.5, uginanie?.weightKg!!, 0.001)

        val stos40 = StepLadder.rungs(40.0, null, 20)
        val prostowanie = ProgressionAdvisor.advise(
            pozycjaC(8, 12), listOf(wyciag(40.0, 18, 13, 11)), stos40,
            TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertEquals(ProgressionAdvisor.Kind.WEIGHT_UP, prostowanie?.kind)
        assertEquals(42.5, prostowanie?.weightKg!!, 0.001)
    }

    @Test
    fun `prostowanie 18-13-11 przy zakresie do 15 mowi o spadku tonazu`() {
        // Poprzednio 16/15/12, czyli 43 powtorzenia na 40 kg - teraz 42.
        val a = ProgressionAdvisor.advise(
            pozycjaC(10, 15), listOf(wyciag(40.0, 18, 13, 11), wyciag(40.0, 16, 15, 12)),
            emptyList(), TrainingGoal.HYPERTROPHY, Phase.BULK
        )
        assertEquals(ProgressionAdvisor.Kind.HOLD, a?.kind)
        assertTrue(a!!.reason, a.reason.contains("1720") && a.reason.contains("1680"))
    }
}
