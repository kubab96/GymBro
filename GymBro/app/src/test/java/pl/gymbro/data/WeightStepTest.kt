package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Krok ciężaru przy sprzęcie bez talerzy - [stepOrDefault] i [PlanItem.step].
 *
 * Zgłoszone po treningu 02.11: prostowania na wyciągu górnym nie dało się
 * ustawić na 28,4 kg. Stos tamtej maszyny jest w funtach, czyli ma progi co
 * 5,67 kg, a aplikacja chodziła co 2,5 kg od zera - i każdy jej próg wypadał
 * obok. Nie był to problem symetrii talerzy: na wyciągu nie ma czego dokładać
 * z dwóch stron, więc ograniczenie nie miało żadnego uzasadnienia w sprzęcie.
 */
class WeightStepTest {

    private fun item(stepKg: Double?) = PlanItem(
        planId = 1,
        exerciseId = 1,
        position = 0,
        targetKg = 28.4,
        sets = 3,
        repsFrom = 8,
        repsTo = 12,
        restSec = 120,
        equipment = Equipment.CABLE,
        stepKg = stepKg
    )

    @Test
    fun `brak kroku schodzi na konwencje`() {
        assertEquals(DEFAULT_STEP_KG, stepOrDefault(null), 0.0001)
        assertEquals(DEFAULT_STEP_KG, item(null).step, 0.0001)
    }

    /**
     * Zero i liczby ujemne traktujemy jak brak, a nie jak wartość.
     *
     * Krok zerowy zatrzymałby plus i minus w miejscu - przycisk, który nic
     * nie robi, czyta się jak zacięcie aplikacji, a nie jak cudze ustawienie.
     */
    @Test
    fun `krok bez sensu nie unieruchamia przyciskow`() {
        assertEquals(DEFAULT_STEP_KG, stepOrDefault(0.0), 0.0001)
        assertEquals(DEFAULT_STEP_KG, stepOrDefault(-5.0), 0.0001)
    }

    @Test
    fun `wlasny krok wygrywa z konwencja`() {
        assertEquals(5.67, item(5.67).step, 0.0001)
    }

    /**
     * To jest ten trening: 28,4 kg i stos w funtach.
     *
     * Plus ma trafić w kolejny prawdziwy próg maszyny, a nie w okrągłą liczbę
     * obok niego. Minus tak samo, i dół pilnuje zera - stos nie schodzi poniżej.
     */
    @Test
    fun `stos w funtach chodzi po swoich progach`() {
        val krok = item(5.67).step
        assertEquals(34.07, roundKg(28.4 + krok), 0.0001)
        assertEquals(22.73, roundKg(28.4 - krok), 0.0001)
        assertEquals(0.0, roundKg((2.0 - krok).coerceAtLeast(0.0)), 0.0001)
    }

    /**
     * Krok ma sens tylko tam, gdzie ciężaru nie składa się z talerzy.
     *
     * Przy sztandze progi wynikają z zawartości siłowni i liczy je
     * [pl.gymbro.gym.PlateCalculator]; zapisana tu liczba byłaby drugim,
     * cichym źródłem prawdy o tym samym.
     */
    @Test
    fun `talerze rozstrzygaja same, bez kroku`() {
        assertTrue(Equipment.BARBELL.isPlateLoaded)
        assertTrue(Equipment.DUMBBELL_PAIR.isPlateLoaded)
        assertTrue(!Equipment.CABLE.isPlateLoaded)
        assertTrue(!Equipment.MACHINE.isPlateLoaded)
    }

    /** Krok nowego ćwiczenia jest pusty, nie zerowy - puste znaczy konwencja. */
    @Test
    fun `nowe cwiczenie nie ma wlasnego kroku`() {
        val e = Exercise(name = "Prostowanie", equipment = Equipment.CABLE, muscleGroup = "triceps")
        assertEquals(null, e.stepKg)
        assertEquals(DEFAULT_STEP_KG, stepOrDefault(e.stepKg), 0.0001)
    }
}
