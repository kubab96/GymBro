package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy obliczeń treningowych na modelu dziedzinowym.
 *
 * To są liczby, na których w Fazie 2 oprze się silnik progresji - jeśli e1RM
 * liczy się źle, aplikacja zacznie podpowiadać złe ciężary. Stąd siatka
 * bezpieczeństwa założona, zanim ten silnik powstanie.
 */
class TrainingMathTest {

    private fun set(weight: Double, reps: Int, warmup: Boolean = false) = SetEntry(
        sessionId = 1,
        exerciseId = 1,
        setIndex = 1,
        weightKg = weight,
        reps = reps,
        isWarmup = warmup,
        doneAt = 0
    )

    // ---- Szacowany ciężar maksymalny, wzór Epleya ----

    @Test
    fun `przy jednym powtorzeniu e1RM rowna sie podniesionemu ciezarowi`() {
        // Zgodnie z definicją: jedno powtórzenie TO jest ciężar maksymalny.
        assertEquals(100.0, set(100.0, 1).estimated1RM, 0.0001)
    }

    @Test
    fun `przy zerze powtorzen nie podbijamy ciezaru`() {
        // Sytuacja patologiczna, ale wzór nie moze zwrocic mniej niz ciezar.
        assertEquals(100.0, set(100.0, 0).estimated1RM, 0.0001)
    }

    @Test
    fun `e1RM rosnie z liczba powtorzen`() {
        assertEquals(116.6667, set(100.0, 5).estimated1RM, 0.001)
        assertEquals(101.3333, set(80.0, 8).estimated1RM, 0.001)
        assertEquals(84.0, set(60.0, 12).estimated1RM, 0.001)
    }

    @Test
    fun `wiecej powtorzen przy tym samym ciezarze to wyzszy e1RM`() {
        assertTrue(set(80.0, 10).estimated1RM > set(80.0, 8).estimated1RM)
    }

    @Test
    fun `ciezsza seria o mniejszej liczbie powtorzen moze dac wyzszy e1RM`() {
        // 100x3 = 110, 80x8 = 101.3 - to jest wlasnie powod, dla ktorego
        // porownujemy serie przez e1RM, a nie przez sam ciezar.
        assertTrue(set(100.0, 3).estimated1RM > set(80.0, 8).estimated1RM)
    }

    // ---- Tonaż ----

    @Test
    fun `tonaz to ciezar razy powtorzenia`() {
        assertEquals(640.0, set(80.0, 8).tonnage, 0.0001)
        assertEquals(0.0, set(80.0, 0).tonnage, 0.0001)
    }

    // ---- Sesja ----

    @Test
    fun `sesja bez czasu zakonczenia jest aktywna`() {
        val s = Session(id = 1, planId = 1, startedAt = 1000)
        assertTrue(s.isActive)
        assertTrue(s.copy(endedAt = 2000).isActive.not())
    }

    // ---- Sesja zewnętrzna z zegarka ----

    @Test
    fun `tempo biegu liczy sie z dystansu i czasu`() {
        // 10 km w 50 minut to 5:00 min/km
        val s = ExternalSession(
            externalId = "x",
            source = "test",
            type = "Bieg",
            startedAt = 0,
            endedAt = 50 * 60_000L,
            distanceM = 10_000.0
        )
        assertEquals(50, s.durationMin)
        assertEquals(5.0, s.paceMinPerKm!!, 0.001)
    }

    @Test
    fun `trening bez dystansu nie ma tempa`() {
        val s = ExternalSession(
            externalId = "x",
            source = "test",
            type = "Trening siłowy",
            startedAt = 0,
            endedAt = 60 * 60_000L,
            distanceM = null
        )
        assertNull(s.paceMinPerKm)
    }

    @Test
    fun `znikomy dystans nie produkuje absurdalnego tempa`() {
        // Bez zabezpieczenia 10 metrow w godzine dawaloby 6000 min/km.
        val s = ExternalSession(
            externalId = "x",
            source = "test",
            type = "Trening siłowy",
            startedAt = 0,
            endedAt = 60 * 60_000L,
            distanceM = 10.0
        )
        assertNull(s.paceMinPerKm)
    }

    // ---- Skład ciała ----

    @Test
    fun `pusty sklad ciala jest rozpoznawany`() {
        assertTrue(BodyComposition.EMPTY.isEmpty)
        assertTrue(BodyComposition(bodyFatPct = 18.0).isEmpty.not())
    }

    // ---- Sprzęt ----

    @Test
    fun `tylko sztanga i hantle sa ladowane talerzami`() {
        assertTrue(Equipment.BARBELL.isPlateLoaded)
        assertTrue(Equipment.DUMBBELL_PAIR.isPlateLoaded)
        assertTrue(Equipment.DUMBBELL_SINGLE.isPlateLoaded)
        assertTrue(Equipment.CABLE.isPlateLoaded.not())
        assertTrue(Equipment.MACHINE.isPlateLoaded.not())
        assertTrue(Equipment.BODYWEIGHT.isPlateLoaded.not())
    }

    @Test
    fun `nieznana nazwa sprzetu nie wywraca odczytu z bazy`() {
        // Gdyby w bazie znalazla sie wartosc z nowszej wersji aplikacji.
        assertEquals(Equipment.MACHINE, Equipment.fromName("COS_NOWEGO"))
        assertEquals(Equipment.MACHINE, Equipment.fromName(null))
        assertEquals(Equipment.BARBELL, Equipment.fromName("BARBELL"))
    }
}
