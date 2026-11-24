package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.ExternalSession

class RunRecordsTest {

    private val day = 24 * 3600 * 1000L

    private fun run(id: String, start: Long, km: Double, min: Int, phone: Boolean = true, type: String = "Bieg") =
        ExternalSession(
            externalId = id,
            source = if (phone) ExternalSession.PHONE_SOURCE else "com.huami.watch",
            type = type,
            startedAt = start,
            endedAt = start + min * 60_000L,
            distanceM = km * 1000
        )

    /** Trasa prosto na północ ze stałą prędkością. */
    private fun track(start: Long, meters: Double, speed: Double) =
        (0..(meters / speed).toInt()).map {
            TrackMath.Fix(start + it * 1000L, 52.0 + it * speed / 111_195.0, 21.0, 5f)
        }

    @Test
    fun `rekord na dystansie to najszybszy bieg, a ostatnio to najnowszy`() {
        val a = run("a", 0, 2.0, 10)
        val b = run("b", day, 2.0, 8)
        val c = run("c", 2 * day, 2.0, 12)
        val tracks = mapOf(
            "a" to track(0, 2000.0, 3.3),
            "b" to track(day, 2000.0, 4.2),
            "c" to track(2 * day, 2000.0, 2.8)
        )
        val r = RunRecords.compute(listOf(a, b, c), tracks)
        val km = r.distances.single { it.meters == 1000.0 }
        assertEquals("b", km.best.externalId)
        assertEquals("c", km.latest.externalId)
        assertEquals(3, km.runs)
        assertTrue(r.distances.none { it.meters == 5000.0 })
        assertEquals(listOf(km), r.heldBy("b"))
    }

    @Test
    fun `pierwszy bieg nie dostaje plakietki rekordu`() {
        val a = run("a", 0, 2.0, 10)
        val r = RunRecords.compute(listOf(a), mapOf("a" to track(0, 2000.0, 3.3)))
        assertTrue(r.heldBy("a").isEmpty())
    }

    @Test
    fun `ten sam bieg z zegarka nie liczy sie drugi raz`() {
        val phone = run("p", 0, 5.0, 30)
        val watch = run("w", 60_000, 5.1, 29, phone = false)
        val weekly = RunRecords.weeklyKm(listOf(phone, watch), 1, 10 * 60_000)
        assertEquals(5.0, weekly.single().second, 0.01)
    }

    @Test
    fun `bieg z zegarka wchodzi do najdluzszego, ale nie do czasow`() {
        val watch = run("w", 0, 12.0, 70, phone = false)
        val r = RunRecords.compute(listOf(watch), emptyMap())
        assertEquals("w", r.longestRun?.externalId)
        assertTrue(r.distances.isEmpty())
    }

    @Test
    fun `najszybsza jazda od pieciu kilometrow i bez samochodu`() {
        val short = run("s", 0, 3.0, 5, type = "Rower")
        val ok = run("o", day, 20.0, 50, type = "Rower")
        val car = run("c", 2 * day, 40.0, 30, type = "Rower")
        val r = RunRecords.compute(listOf(short, ok, car), emptyMap())
        assertEquals("o", r.fastestRide?.first?.externalId)
        assertEquals(24.0, r.fastestRide!!.second, 0.01)
        assertEquals("c", r.longestRide?.externalId)
        assertNull(r.longestRun)
    }
}
