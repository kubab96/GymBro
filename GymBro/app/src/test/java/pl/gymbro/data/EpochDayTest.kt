package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Numer dnia od epoki - [epochDayOf] i [todayEpochDay].
 *
 * Trzy linijki kodu, które łatwo napisać źle i trudno potem zauważyć:
 * przesunięcie o jeden dzień widać wyłącznie przy wpisach z wczesnego rana,
 * i to tylko czasem.
 */
class EpochDayTest {

    private fun ms(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `cala doba lokalna to jeden dzien`() {
        val dzien = LocalDate.of(2026, 3, 15)
        listOf(0 to 1, 7 to 0, 13 to 30, 23 to 59).forEach { (h, m) ->
            assertEquals(
                "$h:$m",
                dzien.toEpochDay(),
                epochDayOf(ms(dzien, h, m))
            )
        }
    }

    @Test
    fun `kolejne doby to kolejne numery`() {
        val a = LocalDate.of(2026, 3, 15)
        val b = a.plusDays(1)
        assertEquals(1L, epochDayOf(ms(b, 12, 0)) - epochDayOf(ms(a, 12, 0)))
    }

    @Test
    fun `skrot przez dzielenie milisekund jest tu zly`() {
        // Dowod, ze `ms / 86_400_000` nie wystarcza. Taki skrot daje dzien
        // UTC: w Polsce, ktora jest wschodnia od UTC, wpis z 0:30 w nocy
        // wypada wtedy dzien wczesniej - i wazenie z tego poranka lezaloby
        // w innym dniu niz zjedzone wtedy sniadanie.
        //
        // Test jest warunkowy, bo zalezy od strefy maszyny: na UTC oba
        // sposoby dadza to samo i wtedy nie ma czego sprawdzac.
        val dzien = LocalDate.of(2026, 7, 15)
        val wpol = ms(dzien, 0, 30)
        val skrotem = wpol / 86_400_000L
        val przesuniecie = ZoneId.systemDefault().rules
            .getOffset(Instant.ofEpochMilli(wpol)).totalSeconds

        if (przesuniecie > 1800) {
            assertNotEquals(epochDayOf(wpol), skrotem)
            assertEquals(epochDayOf(wpol) - 1, skrotem)
        } else {
            assertEquals(epochDayOf(wpol), skrotem)
        }
    }

    @Test
    fun `dzisiaj to dzien dzisiejszej chwili`() {
        assertEquals(todayEpochDay(), epochDayOf(System.currentTimeMillis()))
    }

    @Test
    fun `zmiana czasu nie gubi ani nie dubluje dnia`() {
        // Ostatni weekend marca i ostatni pazdziernika: doba ma wtedy 23
        // albo 25 godzin. Numery dni musza i tak isc po jednym - inaczej
        // dziennik zgubilby dzien albo pokazal dwa razy ten samy.
        listOf(
            LocalDate.of(2026, 3, 28),
            LocalDate.of(2026, 10, 24)
        ).forEach { przed ->
            val numery = (0..3).map { epochDayOf(ms(przed.plusDays(it.toLong()), 12, 0)) }
            assertEquals(numery.toString(), listOf(0L, 1L, 2L, 3L), numery.map { it - numery.first() })
        }
    }
}
