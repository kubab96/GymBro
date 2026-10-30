package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.gymbro.data.Equipment
import pl.gymbro.data.PlanItem
import pl.gymbro.data.StoredAdvice
import pl.gymbro.ui.adviceText

/**
 * Propozycja zapisana w bazie wraca jako zmiana dokładnie tego pola, którego
 * dotyczyła.
 *
 * Wygląda trywialnie i właśnie dlatego ma test: mapowanie rodzaju na pole to
 * miejsce, w którym literówka nie wywala kompilacji, a po cichu podnosi liczbę
 * serii zamiast ciężaru. Zapis i odczyt dzieli tu tekstowy rodzaj, więc żadne
 * sprawdzenie typów tego nie złapie.
 */
class StoredAdviceTest {

    private fun item() = PlanItem(
        id = 1, planId = 1, exerciseId = 1, position = 0,
        targetKg = 80.0, sets = 4, repsFrom = 8, repsTo = 12, restSec = 180,
        exerciseName = "Przysiad ze sztangą", equipment = Equipment.BARBELL
    )

    private fun withAdvice(a: StoredAdvice) = item().copy(advice = a)

    @Test
    fun `podniesienie ciezaru zmienia tylko ciezar`() {
        val src = withAdvice(StoredAdvice(kind = "WEIGHT_UP", weightKg = 81.4))
        val out = ProgressionAdvisor.applyTo(src)
        assertEquals(81.4, out.targetKg, 0.001)
        assertEquals(4, out.sets)
        assertEquals(12, out.repsTo)
    }

    @Test
    fun `zejscie z ciezaru tez rusza ciezar`() {
        val src = withAdvice(StoredAdvice(kind = "WEIGHT_DOWN", weightKg = 72.0))
        assertEquals(72.0, ProgressionAdvisor.applyTo(src).targetKg, 0.001)
    }

    @Test
    fun `rozszerzenie zakresu nie rusza ciezaru ani serii`() {
        val src = withAdvice(StoredAdvice(kind = "REPS_UP", repsTo = 14))
        val out = ProgressionAdvisor.applyTo(src)
        assertEquals(14, out.repsTo)
        assertEquals(8, out.repsFrom)
        assertEquals(80.0, out.targetKg, 0.001)
        assertEquals(4, out.sets)
    }

    @Test
    fun `dodanie serii nie rusza niczego innego`() {
        val src = withAdvice(StoredAdvice(kind = "SET_UP", sets = 5))
        val out = ProgressionAdvisor.applyTo(src)
        assertEquals(5, out.sets)
        assertEquals(80.0, out.targetKg, 0.001)
        assertEquals(12, out.repsTo)
    }

    @Test
    fun `nieznany rodzaj nie zmienia nic`() {
        val src = withAdvice(StoredAdvice(kind = "COS_NOWEGO", weightKg = 999.0))
        assertEquals(item().targetKg, ProgressionAdvisor.applyTo(src).targetKg, 0.001)
    }

    @Test
    fun `brak propozycji nie zmienia nic`() {
        assertEquals(item().targetKg, ProgressionAdvisor.applyTo(item()).targetKg, 0.001)
    }

    // ---- Opis dla oka --------------------------------------------------

    @Test
    fun `opis pokazuje z czego na co`() {
        // Separator dziesietny bierze sie z jezyka urzadzenia - po polsku
        // przecinek. Test nie ma go narzucac, ma sprawdzac tresc opisu.
        val kropka = String.format("%.1f", 81.4)
        assertEquals(
            "ciężar 80 na $kropka kg",
            adviceText(item(), StoredAdvice(kind = "WEIGHT_UP", weightKg = 81.4))
        )
        assertEquals(
            "zakres 8-12 na 8-14",
            adviceText(item(), StoredAdvice(kind = "REPS_UP", repsTo = 14))
        )
        assertEquals(
            "serie 4 na 5",
            adviceText(item(), StoredAdvice(kind = "SET_UP", sets = 5))
        )
    }
}
