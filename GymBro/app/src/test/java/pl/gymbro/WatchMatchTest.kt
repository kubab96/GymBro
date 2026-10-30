package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.ExternalSession
import pl.gymbro.gym.WatchMatch

class WatchMatchTest {

    private fun min(n: Long) = n * 60_000L

    private fun watch(
        id: Long,
        startMin: Long,
        endMin: Long,
        distance: Double? = null
    ) = ExternalSession(
        id = id,
        externalId = "x$id",
        source = "zegarek",
        type = "Trening siłowy",
        startedAt = min(startMin),
        endedAt = min(endMin),
        distanceM = distance
    )

    @Test
    fun `sesja pokrywajaca sie niemal w calosci zostaje dopasowana`() {
        val candidates = listOf(watch(1, 100, 160))
        val m = WatchMatch.bestMatch(min(102), min(158), candidates)
        assertEquals(1L, m?.id)
    }

    @Test
    fun `sesja stykajaca sie tylko koncami nie jest dopasowana`() {
        // Zegarek kończy dokładnie wtedy, gdy zaczynasz zapisywać - to dwa
        // różne treningi, nie jeden.
        val candidates = listOf(watch(1, 60, 100))
        assertNull(WatchMatch.bestMatch(min(100), min(160), candidates))
    }

    @Test
    fun `za male pokrycie nie wystarcza`() {
        // 10 min wspólne przy 60-minutowych sesjach to 17 procent.
        val candidates = listOf(watch(1, 50, 110))
        assertNull(WatchMatch.bestMatch(min(100), min(160), candidates))
    }

    @Test
    fun `przy dwoch kandydatach wygrywa wieksze pokrycie`() {
        val candidates = listOf(
            watch(1, 95, 125),   // 25 min wspólnych
            watch(2, 100, 155)   // 55 min wspólnych
        )
        val m = WatchMatch.bestMatch(min(100), min(160), candidates)
        assertEquals(2L, m?.id)
    }

    @Test
    fun `krotka sesja w srodku dlugiej liczy sie wzgledem krotszej`() {
        // Zegarek włączony na 20 min w trakcie 90-minutowego treningu: pokrycie
        // to całe 100 procent krótszej sesji, więc to ten sam trening.
        val candidates = listOf(watch(1, 120, 140))
        val m = WatchMatch.bestMatch(min(100), min(190), candidates)
        assertEquals(1L, m?.id)
    }

    @Test
    fun `sesja bez czasu trwania jest pomijana`() {
        val candidates = listOf(watch(1, 100, 100))
        assertNull(WatchMatch.bestMatch(min(100), min(160), candidates))
    }

    @Test
    fun `trwajacy trening bez konca nie dopasowuje sie do niczego`() {
        val candidates = listOf(watch(1, 100, 160))
        assertNull(WatchMatch.bestMatch(min(160), min(100), candidates))
    }

    @Test
    fun `pusta lista kandydatow daje null`() {
        assertNull(WatchMatch.bestMatch(min(100), min(160), emptyList()))
    }

    @Test
    fun `czesc wspolna rozlacznych przedzialow wynosi zero`() {
        assertEquals(0L, WatchMatch.overlapMs(0, 10, 20, 30))
    }

    @Test
    fun `dystans rozstrzyga o tym czy to kardio`() {
        assertTrue(WatchMatch.isCardio(watch(1, 0, 30, distance = 5000.0)))
        assertFalse(WatchMatch.isCardio(watch(2, 0, 30)))
        assertFalse(WatchMatch.isCardio(watch(3, 0, 30, distance = 0.0)))
    }
}
