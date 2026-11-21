package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plate
import pl.gymbro.data.ProgressMode
import pl.gymbro.data.SetEntry

/**
 * Testy silnika progresji.
 *
 * Ten kod decyduje, ile masz podnieść - błąd tutaj kończy się na sztandze,
 * a nie na ekranie. Stąd osobny test na każdą regułę i na każdy przypadek,
 * w którym reguły mogłyby się zderzyć.
 */
class ProgressionEngineTest {

    private val inv = Inventory(
        barKg = 20.0,
        plates = listOf(
            Plate(kg = 20.0, count = 4),
            Plate(kg = 10.0, count = 4),
            Plate(kg = 5.0, count = 4),
            Plate(kg = 2.5, count = 4),
            Plate(kg = 1.25, count = 4)
        )
    )

    private fun sets(weight: Double, vararg reps: Int, rir: Int? = null) =
        reps.mapIndexed { i, r ->
            SetEntry(
                sessionId = 1, exerciseId = 1, setIndex = i + 1,
                weightKg = weight, reps = r, rir = rir, doneAt = 0
            )
        }

    private fun suggest(
        history: List<List<SetEntry>>,
        equipment: Equipment = Equipment.BARBELL,
        target: Double = 60.0,
        progress: ProgressMode = ProgressMode.ALL
    ) = ProgressionEngine.suggest(
        equipment = equipment,
        targetKg = target,
        repsFrom = 8,
        repsTo = 12,
        history = history,
        inventory = inv,
        progress = progress
    )

    // ---- Brak historii ----

    @Test
    fun `bez historii bierzemy ciezar z planu`() {
        val s = suggest(emptyList(), target = 60.0)
        assertEquals(ProgressionEngine.Reason.FIRST_TIME, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(8, s.reps)
    }

    @Test
    fun `bez historii ciezar z planu jest zaokraglany do osiagalnego`() {
        // 61 kg nie da sie zlozyc; najblizsze to 60 albo 62.5
        val s = suggest(emptyList(), target = 61.0)
        assertTrue(s.weightKg == 60.0 || s.weightKg == 62.5)
        assertNotNull(s.loading)
    }

    // ---- Trzymanie ciężaru ----

    @Test
    fun `niepelny zakres powtorzen oznacza ten sam ciezar`() {
        val s = suggest(listOf(sets(60.0, 10, 9, 9)))
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
    }

    @Test
    fun `przy trzymaniu celujemy w jedno powtorzenie wiecej`() {
        val s = suggest(listOf(sets(60.0, 10, 9, 9)))
        // najslabsza seria miala 9, wiec cel to 10
        assertEquals(10, s.reps)
    }

    @Test
    fun `cel powtorzen nie przekracza gornej granicy zakresu`() {
        val s = suggest(listOf(sets(60.0, 12, 12, 11)))
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(12, s.reps)
    }

    // ---- Podbicie ----
    //
    // Przy ciezarze w planie o ciezarze decyduje plan, bo to jego pokazuje
    // zegarek. Testy wlasnej progresji silnika (talerze, przepasc, stos)
    // ida wiec na pozycji bez ciezaru w planie - target = 0.0.

    @Test
    fun `komplet gornych powtorzen podbija ciezar na nastepny prog`() {
        val s = suggest(listOf(sets(60.0, 12, 12, 12)), target = 0.0)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(62.5, s.weightKg, 0.001)
        assertEquals(8, s.reps)
    }

    @Test
    fun `podbicie czekajace w planie zostawia ciezar i komplet powtorzen`() {
        // Plan dalej 60 kg, bo karty z podbiciem nikt nie przyjal. Dotad
        // zegarek pokazywal 60 x 8 - cztery powtorzenia mniej niz ostatnio.
        val s = suggest(listOf(sets(60.0, 12, 12, 12)), target = 60.0)
        assertEquals(ProgressionEngine.Reason.AWAITING, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(12, s.reps)
    }

    @Test
    fun `przyjete podbicie bierze ciezar z planu i cel od dolu`() {
        // Plan 65 kg, choc najblizszy prog od 60 to 62,5 - telefon ma
        // pokazac to samo co zegarek, czyli plan.
        val s = suggest(listOf(sets(60.0, 12, 12, 12)), target = 65.0)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(65.0, s.weightKg, 0.001)
        assertEquals(8, s.reps)
    }

    @Test
    fun `za lekko przy czekajacym podbiciu tez zostawia ciezar`() {
        val s = suggest(listOf(sets(60.0, 9, 9, 9, rir = 4)), target = 60.0)
        assertEquals(ProgressionEngine.Reason.AWAITING, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(10, s.reps)
    }

    @Test
    fun `podbicie idzie na realny prog z talerzy a nie o stale 2 i pol kilo`() {
        val rzadkie = Inventory(
            barKg = 20.0,
            plates = listOf(Plate(kg = 10.0, count = 4))
        )
        // Majac tylko dziesiatki, najmniejszy mozliwy skok to 20 kg. Po 3 x 28
        // na 40 kg wychodzi z rownowaznosci osiem powtorzen na 60 - w zakresie.
        val s = ProgressionEngine.suggest(
            Equipment.BARBELL, 0.0, 8, 12,
            listOf(sets(40.0, 28, 28, 28)), rzadkie
        )
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
    }

    @Test
    fun `przepasc z talerzy zostawia ciezar, dopoki nie da sie jej przeskoczyc`() {
        // 40 -> 60 kg to 50%. Po 3 x 12 na 60 kg wyszloby z rownowaznosci
        // jedno-dwa powtorzenia. Dotad zegarek proponowal 60 kg na osiem.
        val rzadkie = Inventory(
            barKg = 20.0,
            plates = listOf(Plate(kg = 10.0, count = 4))
        )
        val s = ProgressionEngine.suggest(
            Equipment.BARBELL, 0.0, 8, 12,
            listOf(sets(40.0, 12, 12, 12)), rzadkie
        )
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(40.0, s.weightKg, 0.001)
        assertEquals(13, s.reps)
    }

    @Test
    fun `wysokie RIR podbija ciezar mimo niepelnego zakresu`() {
        val s = suggest(listOf(sets(60.0, 9, 9, 9, rir = 4)), target = 0.0)
        assertEquals(ProgressionEngine.Reason.INCREASE_EASY, s.reason)
        assertEquals(62.5, s.weightKg, 0.001)
    }

    @Test
    fun `niskie RIR nie podbija ciezaru`() {
        val s = suggest(listOf(sets(60.0, 9, 9, 9, rir = 1)))
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
    }

    @Test
    fun `brak RIR nie jest traktowany jak lekka seria`() {
        val s = suggest(listOf(sets(60.0, 9, 9, 9)))
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
    }

    // ---- Cofnięcie ----

    @Test
    fun `jednorazowy spadek ponizej zakresu jeszcze nie cofa ciezaru`() {
        // Jedna slabsza sesja to szum - nie reagujemy.
        val s = suggest(listOf(sets(60.0, 7, 6, 6)))
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
    }

    @Test
    fun `drugi spadek ponizej zakresu cofa ciezar`() {
        val s = suggest(
            listOf(
                sets(60.0, 7, 6, 6),
                sets(60.0, 8, 7, 7)
            )
        )
        assertEquals(ProgressionEngine.Reason.DELOAD, s.reason)
        assertTrue("cofniecie musi zmniejszyc ciezar", s.weightKg < 60.0)
    }

    @Test
    fun `cztery sesje bez postepu uznajemy za zastoj`() {
        val s = suggest(List(4) { sets(60.0, 10, 10, 9) })
        assertEquals(ProgressionEngine.Reason.STALLED, s.reason)
        assertTrue(s.weightKg < 60.0)
    }

    @Test
    fun `trzy sesje bez postepu to jeszcze nie zastoj`() {
        val s = suggest(List(3) { sets(60.0, 10, 10, 9) })
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
    }

    @Test
    fun `zmiana ciezaru przerywa liczenie zastoju`() {
        // Trzy sesje na 60 kg, ale wczesniej byl inny ciezar - seria sie nie sumuje.
        val s = suggest(
            listOf(
                sets(60.0, 10, 10, 9),
                sets(60.0, 10, 9, 9),
                sets(60.0, 9, 9, 9),
                sets(57.5, 12, 12, 12)
            )
        )
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
    }

    @Test
    fun `cofniecie nigdy nie podnosi ciezaru`() {
        // Przy ubogim zestawie najblizszy prog do 90% moglby wypasc wyzej.
        val rzadkie = Inventory(barKg = 20.0, plates = listOf(Plate(kg = 10.0, count = 4)))
        val s = ProgressionEngine.suggest(
            Equipment.BARBELL, 60.0, 8, 12,
            List(4) { sets(60.0, 10, 10, 9) }, rzadkie
        )
        assertTrue("deload nie moze zwiekszyc obciazenia", s.weightKg < 60.0)
    }

    // ---- Serie dojściowe ----

    @Test
    fun `serie rozgrzewkowe nie wplywaja na decyzje`() {
        val warmup = SetEntry(
            sessionId = 1, exerciseId = 1, setIndex = 1,
            weightKg = 100.0, reps = 5, isWarmup = true, doneAt = 0
        )
        val s = suggest(listOf(listOf(warmup) + sets(60.0, 12, 12, 12)), target = 0.0)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        // Gdyby rozgrzewka weszla do rachunku, ciezarem roboczym byloby 100 kg.
        assertEquals(62.5, s.weightKg, 0.001)
    }

    @Test
    fun `lzejsze serie dojsciowe nie obnizaja ciezaru roboczego`() {
        val s = suggest(listOf(sets(40.0, 10) + sets(60.0, 12, 12, 12)), target = 0.0)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(62.5, s.weightKg, 0.001)
    }

    // ---- Sprzęt bez talerzy ----

    @Test
    fun `wyciag podbija sie stalym krokiem`() {
        val s = suggest(listOf(sets(35.0, 12, 12, 12)), equipment = Equipment.CABLE, target = 0.0)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(37.5, s.weightKg, 0.001)
    }

    @Test
    fun `przepasc na lekkim wyciagu zostawia ciezar i dokłada powtorzen`() {
        // 10 -> 12,5 kg to 25%: z rownowaznosci trzy powtorzenia na 12,5.
        // Zegarek ma podpowiedziec 13 na 10 kg, nie 8 na ciezarze z przepasci.
        val s = suggest(listOf(sets(10.0, 12, 12, 12)), equipment = Equipment.CABLE, target = 0.0)
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(10.0, s.weightKg, 0.001)
        assertEquals(13, s.reps)
    }

    @Test
    fun `wyciag cofa sie stalym krokiem`() {
        val s = suggest(
            List(4) { sets(35.0, 10, 10, 9) },
            equipment = Equipment.CABLE
        )
        assertEquals(ProgressionEngine.Reason.STALLED, s.reason)
        assertEquals(32.5, s.weightKg, 0.001)
    }

    // ---- Progresja wyłączona w całości albo w części ----

    @Test
    fun `wylaczona progresja powtarza ostatni trening`() {
        val s = suggest(listOf(sets(60.0, 10, 9, 9)), progress = ProgressMode.OFF)
        assertEquals(ProgressionEngine.Reason.KEEP, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        // W trybie pelnym celem byloby 10 - patrz "celujemy w jedno wiecej".
        assertEquals(9, s.reps)
    }

    @Test
    fun `wylaczona progresja nie podbija nawet po komplecie`() {
        val s = suggest(listOf(sets(60.0, 12, 12, 12, rir = 4)), progress = ProgressMode.OFF)
        assertEquals(ProgressionEngine.Reason.KEEP, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(12, s.reps)
    }

    @Test
    fun `bez dokladania ciezaru komplet powtorzen zostaje na tym samym ciezarze`() {
        val s = suggest(listOf(sets(60.0, 12, 12, 12)), progress = ProgressMode.NO_WEIGHT)
        assertEquals(ProgressionEngine.Reason.KEEP, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(12, s.reps)
    }

    @Test
    fun `bez dokladania ciezaru powtorzenia dalej rosna`() {
        val s = suggest(listOf(sets(60.0, 10, 9, 9)), progress = ProgressMode.NO_WEIGHT)
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(10, s.reps)
    }

    @Test
    fun `bez dokladania ciezaru wysokie RIR go nie podbija`() {
        val s = suggest(listOf(sets(60.0, 9, 9, 9, rir = 4)), progress = ProgressMode.NO_WEIGHT)
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(10, s.reps)
    }

    @Test
    fun `bez dokladania ciezaru stanie na ciezarze to nie zastoj`() {
        // W trybie pelnym cztery takie sesje koncza sie cofnieciem.
        val wZakresie = suggest(List(4) { sets(60.0, 10, 10, 9) }, progress = ProgressMode.NO_WEIGHT)
        assertEquals(ProgressionEngine.Reason.HOLD, wZakresie.reason)
        assertEquals(60.0, wZakresie.weightKg, 0.001)

        val naSzczycie = suggest(List(4) { sets(60.0, 12, 12, 12) }, progress = ProgressMode.NO_WEIGHT)
        assertEquals(ProgressionEngine.Reason.KEEP, naSzczycie.reason)
        assertEquals(60.0, naSzczycie.weightKg, 0.001)
    }

    @Test
    fun `blokada dokladania nie blokuje cofniecia po spadku ponizej zakresu`() {
        val spadek = listOf(sets(60.0, 7, 6, 6), sets(60.0, 8, 7, 7))
        listOf(ProgressMode.NO_WEIGHT, ProgressMode.NO_REPS).forEach { tryb ->
            val s = suggest(spadek, progress = tryb)
            assertEquals("tryb $tryb", ProgressionEngine.Reason.DELOAD, s.reason)
            assertTrue(s.weightKg < 60.0)
        }
    }

    @Test
    fun `bez dokladania powtorzen cel zostaje tam, gdzie byl`() {
        val s = suggest(listOf(sets(60.0, 10, 9, 9)), progress = ProgressMode.NO_REPS)
        assertEquals(ProgressionEngine.Reason.KEEP, s.reason)
        assertEquals(60.0, s.weightKg, 0.001)
        assertEquals(9, s.reps)
    }

    @Test
    fun `bez dokladania powtorzen komplet dalej podbija ciezar`() {
        val s = suggest(listOf(sets(60.0, 12, 12, 12)), target = 0.0, progress = ProgressMode.NO_REPS)
        assertEquals(ProgressionEngine.Reason.INCREASE, s.reason)
        assertEquals(62.5, s.weightKg, 0.001)
    }

    // ---- Brak wyższego progu ----

    @Test
    fun `wyczerpany zestaw talerzy nie proponuje ciezaru nie do zlozenia`() {
        val maly = Inventory(barKg = 20.0, plates = listOf(Plate(kg = 1.25, count = 2)))
        // Maksimum to 22,5 kg - nie ma dokad podbic.
        val s = ProgressionEngine.suggest(
            Equipment.BARBELL, 0.0, 8, 12,
            listOf(sets(22.5, 12, 12, 12)), maly
        )
        assertEquals(ProgressionEngine.Reason.HOLD, s.reason)
        assertEquals(22.5, s.weightKg, 0.001)
        // Dotad 8, czyli dol zakresu - cofniecie o cztery powtorzenia komus,
        // kto wlasnie dobil gore. Bez wyzszego ciezaru rosna powtorzenia.
        assertEquals(13, s.reps)
        assertTrue(
            "komunikat ma tlumaczyc, co blokuje progresje",
            s.note.contains("talerz", ignoreCase = true)
        )
    }
}
