package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy trendu masy liczonego po dniach.
 *
 * Wartości oczekiwane policzone osobno (Node, ten sam wzór na kopii bazy
 * z telefonu), nie przepisane z wyniku implementacji.
 */
class WeightTrendTest {

    private val day = 86_400_000L

    /** Ważenia z telefonu, 22.10-21.11.2026 - przypadek, na którym stary trend stał w miejscu. */
    private val prawdziwe = listOf(
        1792651941445L to 80.10,
        1793083931050L to 80.60,
        1793439088037L to 81.35,
        1794036523188L to 81.85,
        1794382886462L to 82.35,
        1794647894870L to 82.35,
        1795251095306L to 81.80,
    )

    @Test
    fun `trend dochodzi do wagi przy wazeniu co kilka dni`() {
        val t = WeightTrend.smooth(prawdziwe)
        // Stary trend (10% na pomiar) pokazywał tu 80,89 kg.
        assertEquals(81.804, t.last().second, 0.005)
        assertEquals(81.808, t[5].second, 0.005)
    }

    @Test
    fun `spadek wagi nie podnosi trendu`() {
        val t = WeightTrend.smooth(prawdziwe)
        // 21.11 waga spadła z 82,35 na 81,80 - stara reguła mimo to podniosła linię.
        assertTrue(t.last().second <= t[5].second)
    }

    @Test
    fun `trend nie zalezy od tego jak czesto sie wazysz`() {
        // Masa skacze z 80 na 82 i stoi. Po dwóch tygodniach trend ma być
        // taki sam przy ważeniu codziennym i raz w tygodniu.
        val codziennie = listOf(0L to 80.0) + (1..14).map { it * day to 82.0 }
        val coTydzien = listOf(0L to 80.0, 7 * day to 82.0, 14 * day to 82.0)
        val a = WeightTrend.smooth(codziennie).last().second
        val b = WeightTrend.smooth(coTydzien).last().second
        assertEquals(a, b, 1e-9)
        assertEquals(82.0 - 2.0 * Math.exp(-1.4), a, 1e-9)
    }

    @Test
    fun `dwa pomiary z tej samej chwili nie ruszaja trendu`() {
        val t = WeightTrend.smooth(listOf(0L to 80.0, 0L to 85.0))
        assertEquals(80.0, t.last().second, 1e-9)
    }

    @Test
    fun `pierwszy pomiar jest trendem`() {
        assertEquals(80.1, WeightTrend.smooth(prawdziwe).first().second, 1e-9)
        assertTrue(WeightTrend.smooth(emptyList()).isEmpty())
    }

    // ---- Tempo na tydzień ----

    @Test
    fun `tempo z prawdziwych wazen`() {
        assertEquals(0.484, WeightTrend.weeklyRate(prawdziwe)!!, 0.005)
    }

    @Test
    fun `rowny przyrost daje dokladne tempo`() {
        val pts = (0..6).map { it * 7 * day to 80.0 + 0.5 * it }
        assertEquals(0.5, WeightTrend.weeklyRate(pts)!!, 1e-9)
    }

    @Test
    fun `stare pomiary spoza okna nie licza sie do tempa`() {
        // Pół roku temu 70 kg, teraz od ośmiu tygodni stoi 80.
        val stare = listOf(0L to 70.0, 7 * day to 70.0)
        val nowe = (0..7).map { (180 + it * 7) * day to 80.0 }
        assertEquals(0.0, WeightTrend.weeklyRate(stare + nowe)!!, 1e-9)
    }

    @Test
    fun `za malo danych na tempo`() {
        assertNull(WeightTrend.weeklyRate(listOf(0L to 80.0, 7 * day to 81.0)))
        // Trzy ważenia w trzy dni - prosta przez szum.
        assertNull(WeightTrend.weeklyRate(listOf(0L to 80.0, day to 81.0, 2 * day to 80.5)))
        assertNull(WeightTrend.weeklyRate(emptyList()))
    }
}
