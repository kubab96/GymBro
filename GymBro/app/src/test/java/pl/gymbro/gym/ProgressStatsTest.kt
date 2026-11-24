package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Activity
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.SessionSummary
import pl.gymbro.data.SetEntry
import java.time.ZoneId

class ProgressStatsTest {

    private val DAY = 86_400_000L
    private val UTC = ZoneId.of("UTC")
    /** Poniedziałek 2026-11-16, 00:00 UTC. */
    private val MONDAY = 1_794_787_200_000L

    private fun seria(session: Long, kg: Double, reps: Int, at: Long, warmup: Boolean = false) =
        SetEntry(sessionId = session, exerciseId = 1, setIndex = 1, weightKg = kg, reps = reps,
            isWarmup = warmup, doneAt = at)

    private fun sila(at: Long, sets: Int = 10, min: Long = 60) = Activity.Strength(
        SessionSummary(sessionId = at, planName = "A", startedAt = at, endedAt = at + min * 60_000,
            workingSets = sets, tonnage = sets * 500.0)
    )

    private fun bieg(at: Long, min: Long = 30) = Activity.Watch(
        ExternalSession(externalId = "b$at", source = "zegarek", type = "Bieg",
            startedAt = at, endedAt = at + min * 60_000, distanceM = 5000.0)
    )

    @Test
    fun `poniedzialek jako poczatek tygodnia`() {
        assertEquals(MONDAY, ProgressStats.weekStart(MONDAY + 3 * DAY + 5_000, UTC))
        assertEquals(MONDAY, ProgressStats.weekStart(MONDAY, UTC))
        assertEquals(MONDAY - 7 * DAY, ProgressStats.weekStart(MONDAY - 1, UTC))
    }

    @Test
    fun `podsumowanie okresu liczy bieg do treningow, ale nie do serii`() {
        val t = ProgressStats.totals(listOf(sila(MONDAY), bieg(MONDAY + DAY)), MONDAY, MONDAY + 7 * DAY)
        assertEquals(2, t.workouts)
        assertEquals(90L, t.minutes)
        assertEquals(10, t.sets)
    }

    @Test
    fun `tydzien bez treningu jest zerem, nie dziura`() {
        val w = ProgressStats.weekly(
            listOf(sila(MONDAY - 14 * DAY), sila(MONDAY + DAY, sets = 12)),
            ProgressStats.Metric.SETS, weeks = 3, now = MONDAY + 2 * DAY, zone = UTC
        )
        assertEquals(listOf(10.0, 0.0, 12.0), w.map { it.second })
        assertEquals(MONDAY, w.last().first)
    }

    @Test
    fun `pierwsza sesja nie jest rekordem, pobicie jest`() {
        val p = ProgressStats.points(listOf(
            seria(1, 100.0, 5, 1000), seria(2, 100.0, 5, 2000), seria(3, 102.5, 5, 3000)
        ))
        assertEquals(listOf(false, false, true), p.map { it.record })
    }

    @Test
    fun `rozgrzewka nie liczy sie do rekordu`() {
        val p = ProgressStats.points(listOf(seria(1, 60.0, 5, 1000, warmup = true), seria(1, 50.0, 5, 1100)))
        assertEquals(50.0, p.single().topKg, 0.01)
    }

    @Test
    fun `seria na szesc liczy sie do rekordu na piec`() {
        val r = ProgressStats.repRecords(listOf(seria(1, 100.0, 6, 1000), seria(2, 90.0, 5, 2000)))
        assertEquals(100.0, r[2]!!.kg, 0.01)
        assertNull(r[3]) // nigdy dziesięć powtórzeń
    }

    @Test
    fun `przy remisie rekord ma date pierwszego razu`() {
        val r = ProgressStats.repRecords(listOf(seria(1, 100.0, 5, 1000), seria(2, 100.0, 5, 9000)))
        assertEquals(1000L, r[2]!!.at)
    }

    @Test
    fun `zmiana wzgledem poprzedniego okresu`() {
        val now = 100 * DAY
        val sets = listOf(
            seria(1, 100.0, 1, now - 40 * DAY), // poprzednie 4 tygodnie
            seria(2, 105.0, 1, now - 10 * DAY)
        )
        val lift = ProgressStats.lifts(listOf(Triple(1L, "Przysiad", sets)), ProgressStats.Period.WEEKS4, now)
            .single()
        assertEquals(105.0, lift.current, 0.01)
        assertEquals(5.0, lift.change!!, 0.01)
    }

    @Test
    fun `cztery tygodnie bez rekordu przy trzech sesjach to zastoj`() {
        val now = 100 * DAY
        val sets = listOf(
            seria(1, 100.0, 1, now - 60 * DAY),
            seria(2, 110.0, 1, now - 35 * DAY), // ostatni rekord
            seria(3, 105.0, 1, now - 20 * DAY),
            seria(4, 105.0, 1, now - 10 * DAY),
            seria(5, 108.0, 1, now - 2 * DAY)
        )
        val lift = ProgressStats.lifts(listOf(Triple(1L, "Martwy", sets)), ProgressStats.Period.MONTHS3, now)
            .single()
        assertNotNull(lift.stalledWeeks)
        assertEquals(5, lift.stalledWeeks)
    }

    @Test
    fun `dwie sesje bez rekordu to jeszcze nie zastoj`() {
        val now = 100 * DAY
        val sets = listOf(
            seria(1, 110.0, 1, now - 50 * DAY),
            seria(2, 105.0, 1, now - 20 * DAY),
            seria(3, 105.0, 1, now - 10 * DAY)
        )
        val lift = ProgressStats.lifts(listOf(Triple(1L, "Martwy", sets)), ProgressStats.Period.MONTHS3, now)
            .single()
        assertNull(lift.stalledWeeks)
    }

    @Test
    fun `glowne boje to najciezsze, nie najczestsze`() {
        val now = 100 * DAY
        fun ex(id: Long, name: String, kg: Double, sesji: Int) = Triple(
            id, name, (1..sesji).map { seria(id * 10 + it, kg, 5, now - it * DAY) }
        )
        val lifts = ProgressStats.lifts(
            listOf(
                ex(1, "Face pull", 30.0, sesji = 4), // w dwóch jednostkach FBW
                ex(2, "Przysiad", 120.0, sesji = 2),
                ex(3, "Prasa", 200.0, sesji = 1), // jedna sesja - brak trendu
                ex(4, "Biceps", 15.0, sesji = 4)
            ),
            ProgressStats.Period.WEEKS4, now
        )
        assertEquals(listOf("Przysiad", "Face pull", "Biceps", "Prasa"), lifts.map { it.name })
    }

    @Test
    fun `karta treningu ma cwiczenia w kolejnosci wykonania i rekordy z tej sesji`() {
        fun s(ex: Long, session: Long, kg: Double, reps: Int, at: Long) =
            SetEntry(sessionId = session, exerciseId = ex, setIndex = 1, weightKg = kg, reps = reps, doneAt = at)
        val h = ProgressStats.highlights(listOf(
            Triple(1L, "Wiosłowanie", listOf(s(1, 2, 60.0, 8, 2_500))),
            Triple(2L, "Przysiad", listOf(
                s(2, 1, 100.0, 5, 1_000),
                s(2, 2, 100.0, 5, 2_000), s(2, 2, 105.0, 5, 2_100), s(2, 2, 105.0, 3, 2_200)
            ))
        ))
        val druga = h.getValue(2)
        assertEquals(listOf("Przysiad", "Wiosłowanie"), druga.exercises.map { it.name })
        assertEquals(3, druga.exercises[0].sets)
        assertEquals(105.0, druga.exercises[0].bestKg, 0.01)
        assertEquals(5, druga.exercises[0].bestReps)
        assertEquals(1, druga.records) // przysiad pobity; wiosłowanie to pierwsza sesja
        assertEquals(0, h.getValue(1).records)
    }

    @Test
    fun `szczegoly treningu wskazuja cwiczenie i serie rekordu, zgodnie z karta`() {
        fun s(ex: Long, session: Long, kg: Double, reps: Int, at: Long) =
            SetEntry(sessionId = session, exerciseId = ex, setIndex = 1, weightKg = kg, reps = reps, doneAt = at)
        val wioslowanie = listOf(s(1, 2, 60.0, 8, 2_500))
        val przysiad = listOf(
            s(2, 1, 100.0, 5, 1_000),
            s(2, 2, 100.0, 5, 2_000), s(2, 2, 105.0, 5, 2_100), s(2, 2, 105.0, 3, 2_200),
            s(2, 3, 100.0, 5, 3_000)
        )
        val druga = ProgressStats.recordsIn(2, mapOf(1L to wioslowanie, 2L to przysiad))
        assertEquals(setOf(2L), druga.keys)
        val r = druga.getValue(2)
        assertEquals(105.0, r.bestSet.weightKg, 0.01)
        assertEquals(5, r.bestSet.reps)
        assertTrue(r.e1rm > r.previousBest)
        // Ta sama liczba co odznaka na karcie w Historii.
        val karta = ProgressStats.highlights(listOf(
            Triple(1L, "Wiosłowanie", wioslowanie), Triple(2L, "Przysiad", przysiad)
        ))
        assertEquals(karta.getValue(2).records, druga.size)
        // Trening słabszy od poprzedniego nie ma rekordu.
        assertTrue(ProgressStats.recordsIn(3, mapOf(2L to przysiad)).isEmpty())
    }

    @Test
    fun `cwiczenie nieruszane w okresie znika z listy`() {
        val now = 100 * DAY
        val lifts = ProgressStats.lifts(
            listOf(Triple(1L, "Stare", listOf(seria(1, 50.0, 5, now - 60 * DAY)))),
            ProgressStats.Period.WEEKS4, now
        )
        assertTrue(lifts.isEmpty())
        assertFalse(ProgressStats.lifts(
            listOf(Triple(1L, "Stare", listOf(seria(1, 50.0, 5, now - 60 * DAY)))),
            ProgressStats.Period.ALL, now
        ).isEmpty())
    }
}
