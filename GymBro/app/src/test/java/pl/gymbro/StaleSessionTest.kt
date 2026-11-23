package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.gymbro.gym.StaleSession
import pl.gymbro.gym.StaleSession.Verdict

class StaleSessionTest {

    private fun h(n: Long) = n * 3_600_000L
    private val start = 1_000_000_000_000L

    @Test
    fun `swiezy trening trwa`() {
        assertEquals(
            Verdict.Active,
            StaleSession.verdict(start, lastSetAt = start + h(1), now = start + h(2))
        )
    }

    @Test
    fun `dlugi ale jeszcze nie porzucony trening trwa`() {
        // Siedem godzin to absurd jak na trening, ale próg jest na ósmej.
        assertEquals(
            Verdict.Active,
            StaleSession.verdict(start, lastSetAt = start + h(6), now = start + h(7))
        )
    }

    @Test
    fun `porzucony trening z seriami konczy sie na ostatniej serii`() {
        val last = start + h(1)
        assertEquals(
            Verdict.Close(last),
            StaleSession.verdict(start, lastSetAt = last, now = start + h(30))
        )
    }

    @Test
    fun `porzucony trening bez serii idzie do kosza`() {
        assertEquals(
            Verdict.Discard,
            StaleSession.verdict(start, lastSetAt = null, now = start + h(30))
        )
    }

    @Test
    fun `pusta sesja z zegarka czeka zamknieta na zalegle serie`() {
        // START doszedł, serie stoją jeszcze w kolejce zegarka. Skasowana sesja
        // odrzucałaby każdą z nich jako "brak trwającego treningu".
        assertEquals(
            Verdict.Close(start),
            StaleSession.verdict(start, lastSetAt = null, now = start + h(30), fromWatch = true)
        )
    }

    @Test
    fun `sesja z zegarka z seriami konczy sie jak kazda inna`() {
        val last = start + h(1)
        assertEquals(
            Verdict.Close(last),
            StaleSession.verdict(start, lastSetAt = last, now = start + h(30), fromWatch = true)
        )
    }

    @Test
    fun `swieza pusta sesja z zegarka trwa`() {
        assertEquals(
            Verdict.Active,
            StaleSession.verdict(start, lastSetAt = null, now = start + h(1), fromWatch = true)
        )
    }

    @Test
    fun `dokladnie na progu trening juz nie trwa`() {
        assertEquals(
            Verdict.Discard,
            StaleSession.verdict(start, lastSetAt = null, now = start + StaleSession.STALE_AFTER_MS)
        )
    }

    @Test
    fun `seria wczesniejsza niz start nie daje ujemnego czasu trwania`() {
        // Obrona przed przestawionym zegarem: koniec nie może wypaść przed startem.
        assertEquals(
            Verdict.Close(start),
            StaleSession.verdict(start, lastSetAt = start - h(5), now = start + h(30))
        )
    }

    @Test
    fun `czterodniowa sesja z naszego telefonu domyka sie na ostatniej serii`() {
        // Przypadek z 6 października: sesja otwarta prawie cztery doby.
        val last = start + h(2)
        assertEquals(
            Verdict.Close(last),
            StaleSession.verdict(start, lastSetAt = last, now = start + h(90))
        )
    }
}
