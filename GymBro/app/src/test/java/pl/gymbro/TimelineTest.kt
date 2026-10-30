package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Activity
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.SessionSummary
import pl.gymbro.gym.Timeline

class TimelineTest {

    private fun day(n: Long) = n * 86_400_000L

    private fun lift(at: Long, matched: ExternalSession? = null) = SessionSummary(
        sessionId = at, planName = "A", startedAt = at, endedAt = at + 3_600_000L,
        workingSets = 20, tonnage = 8000.0, watch = matched
    )

    private fun watch(
        at: Long,
        type: String,
        id: String = "w$at",
        distance: Double? = null
    ) = ExternalSession(
        id = at, externalId = id, source = "zegarek", type = type,
        startedAt = at, endedAt = at + 1_800_000L, distanceM = distance
    )

    @Test
    fun `kazdy strumien dostaje swoje`() {
        val split = Timeline.build(
            strength = listOf(lift(day(5))),
            watch = listOf(
                watch(day(4), "Bieg"),
                watch(day(3), "Spacer"),
                watch(day(2), "Inny trening")
            )
        )
        assertEquals(1, split.strength.size)
        assertEquals(2, split.cardio.size)
        assertEquals(1, split.other.size)
    }

    @Test
    fun `bieganie i chodzenie laduja razem`() {
        // O to chodziło: tydzień z jednym biegiem i trzema spacerami ma być
        // jedną historią, nie dwiema.
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(
                watch(day(1), "Bieg"),
                watch(day(2), "Spacer"),
                watch(day(3), "Spacer"),
                watch(day(4), "Bieg (bieżnia)")
            )
        )
        assertEquals(4, split.cardio.size)
        assertTrue(split.other.isEmpty())
    }

    @Test
    fun `dystans przesadza nawet przy nieznanej nazwie typu`() {
        // Zegarek potrafi nazwać trening „Typ 93". Jeśli ma dystans,
        // to jednak jest ruch w terenie.
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(watch(day(1), "Typ 93", distance = 4200.0))
        )
        assertEquals(1, split.cardio.size)
    }

    @Test
    fun `trening dowolny i joga ida do innych`() {
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(watch(day(1), "Inny trening"), watch(day(2), "Joga"))
        )
        assertEquals(2, split.other.size)
        assertTrue(split.cardio.isEmpty())
    }

    @Test
    fun `sesja sklejona z Twoim zapisem nie dubluje sie jako osobna pozycja`() {
        val matched = watch(day(5), "Trening siłowy", id = "sklejona")
        val split = Timeline.build(
            strength = listOf(lift(day(5), matched = matched)),
            watch = listOf(matched)
        )
        assertEquals(1, split.strength.size)
        assertTrue(split.strength[0] is Activity.Strength)
    }

    @Test
    fun `silowy z zegarka bez Twojego zapisu trafia do silowych`() {
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(watch(day(1), "Trening siłowy"))
        )
        assertEquals(1, split.strength.size)
        assertTrue(split.strength[0] is Activity.Watch)
        assertTrue(split.other.isEmpty())
    }

    @Test
    fun `silowe sa posortowane od najnowszych mimo dwoch zrodel`() {
        val split = Timeline.build(
            strength = listOf(lift(day(1)), lift(day(5))),
            watch = listOf(watch(day(3), "Trening siłowy"))
        )
        assertEquals(listOf(day(5), day(3), day(1)), split.strength.map { it.at })
    }

    @Test
    fun `limit obcina kazdy strumien osobno`() {
        val split = Timeline.build(
            strength = (1..30L).map { lift(day(it)) },
            watch = (1..30L).map { watch(day(it), "Bieg", id = "b$it") },
            limit = 4
        )
        assertEquals(4, split.strength.size)
        assertEquals(4, split.cardio.size)
        assertEquals(day(30), split.strength.first().at)
    }

    @Test
    fun `rozpoznawanie typow`() {
        assertTrue(Timeline.isCardio(watch(0, "Bieg")))
        assertTrue(Timeline.isCardio(watch(0, "Bieg (bieżnia)")))
        assertTrue(Timeline.isCardio(watch(0, "Spacer")))
        assertTrue(Timeline.isCardio(watch(0, "Rower stacjonarny")))
        assertFalse(Timeline.isCardio(watch(0, "Inny trening")))
        assertFalse(Timeline.isCardio(watch(0, "Trening siłowy")))
        assertTrue(Timeline.isStrength(watch(0, "Trening siłowy")))
        assertTrue(Timeline.isStrength(watch(0, "Podnoszenie ciężarów")))
        assertFalse(Timeline.isStrength(watch(0, "Joga")))
    }

    @Test
    fun `silowy z dystansem nadal jest silowy`() {
        // Zdarza się, że zegarek dolicza parę metrów przejścia po sali.
        // Kolejność sprawdzania w build() ma to wytrzymać.
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(watch(day(1), "Trening siłowy", distance = 120.0))
        )
        assertEquals(1, split.strength.size)
        assertTrue(split.cardio.isEmpty())
    }

    @Test
    fun `pusto na wejsciu daje trzy puste strumienie`() {
        val split = Timeline.build(emptyList(), emptyList())
        assertTrue(split.strength.isEmpty())
        assertTrue(split.cardio.isEmpty())
        assertTrue(split.other.isEmpty())
    }
}
