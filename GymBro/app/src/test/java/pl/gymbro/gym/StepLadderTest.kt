package pl.gymbro.gym

import pl.gymbro.data.DEFAULT_STEP_KG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progi ciężaru na wyciągu i maszynie - [StepLadder].
 *
 * Zgłoszone po treningu 02.11: prostowania na wyciągu górnym nie dało się
 * ustawić na 28,4 kg, bo plus i minus chodziły co 2,5 kg od zera i każdy
 * próg wypadał obok. Te same liczby liczy telefon dla siebie i dla zegarka,
 * więc sprawdzamy je raz, tutaj, bez telefonu i bez emulatora.
 */
class StepLadderTest {

    /** Stos w funtach: 12,5 lb to 5,67 kg i żadna okrągła liczba w nie nie trafia. */
    private val funtowy = 5.67

    @Test
    fun `plus i minus chodza po progach maszyny`() {
        assertEquals(34.07, StepLadder.next(28.4, funtowy, up = true), 0.0001)
        assertEquals(22.73, StepLadder.next(28.4, funtowy, up = false), 0.0001)
    }

    @Test
    fun `brak kroku schodzi na konwencje`() {
        assertEquals(
            roundTo(30.0 + DEFAULT_STEP_KG),
            StepLadder.next(30.0, null, up = true),
            0.0001
        )
    }

    /**
     * Dół zatrzymuje się na zerze.
     *
     * Ciężar ujemny nie istnieje, a stos nie schodzi poniżej pustego -
     * przycisk ma stanąć, a nie wpisać do dziennika minus trzy kilogramy.
     */
    @Test
    fun `minus nie schodzi ponizej zera`() {
        assertEquals(0.0, StepLadder.next(2.0, funtowy, up = false), 0.0001)
        assertEquals(0.0, StepLadder.next(0.0, funtowy, up = false), 0.0001)
    }

    @Test
    fun `drabinka stoi na biezacym ciezarze i jest symetryczna`() {
        val progi = StepLadder.rungs(28.4, funtowy, 2)
        assertEquals(listOf(17.06, 22.73, 28.4, 34.07, 39.74), progi)
    }

    /**
     * Zera i wartości ujemnych nie ma na liście.
     *
     * Zero znaczy „bez ciężaru" i jest osobną decyzją, a nie najlżejszym
     * progiem - gdyby weszło do drabinki, minus z najlżejszego ciężaru
     * wyłączałby ćwiczeniu obciążenie zamiast je zmniejszyć.
     */
    @Test
    fun `drabinka nie schodzi do zera ani nizej`() {
        val progi = StepLadder.rungs(5.0, 5.0, 3)
        assertTrue(progi.toString(), progi.all { it > 0.0 })
        assertEquals(listOf(5.0, 10.0, 15.0, 20.0), progi)
    }

    /** Krok bez sensu nie unieruchamia przycisków - patrz `stepOrDefault`. */
    @Test
    fun `krok zerowy schodzi na konwencje`() {
        assertEquals(
            roundTo(40.0 + DEFAULT_STEP_KG),
            StepLadder.next(40.0, 0.0, up = true),
            0.0001
        )
    }

    private fun roundTo(v: Double): Double = Math.round(v * 100.0) / 100.0
}
