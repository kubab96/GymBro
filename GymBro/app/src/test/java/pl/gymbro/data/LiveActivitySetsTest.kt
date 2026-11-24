package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.gym.PhoneActivity

/** Serie ogólnego treningu siłowego nagrywanego telefonem. */
class LiveActivitySetsTest {

    private val start = LiveActivity("phone-1", PhoneActivity.STRENGTH, startedAt = 0)

    @Test
    fun `seria od dotkniecia do dotkniecia`() {
        val a = start.startSet(10_000).endSet(40_000)
        assertEquals(listOf(PhoneSet(10_000, 40_000)), a.sets)
        assertFalse(a.inSet)
    }

    @Test
    fun `pauza zamyka trwajaca serie`() {
        val a = start.startSet(10_000).pause(50_000)
        assertEquals(listOf(PhoneSet(10_000, 50_000)), a.sets)
        assertFalse(a.inSet)
        // We wstrzymanym treningu seria nie rusza.
        assertFalse(a.startSet(60_000).inSet)
    }

    @Test
    fun `cofniecie zabiera trwajaca serie, a bez niej ostatnia`() {
        val two = start.startSet(0).endSet(10).startSet(100).endSet(110)
        val running = two.startSet(200)
        assertEquals(two.sets, running.dropSet().sets)
        assertFalse(running.dropSet().inSet)
        assertEquals(1, two.dropSet().sets.size)
    }

    @Test
    fun `usuniecie serii ze srodka skleja przerwy wokol niej`() {
        val three = start.startSet(0).endSet(30_000)
            .startSet(60_000).endSet(70_000)
            .startSet(120_000).endSet(150_000)
        val without = three.dropSetAt(1)
        assertEquals(listOf(PhoneSet(0, 30_000), PhoneSet(120_000, 150_000)), without.sets)
        assertEquals(listOf(90_000L), restsMs(without.sets))
        // Numer spoza listy niczego nie rusza.
        assertEquals(three, three.dropSetAt(5))
    }

    @Test
    fun `przerwy miedzy kolejnymi seriami`() {
        val sets = listOf(PhoneSet(0, 30_000), PhoneSet(120_000, 150_000), PhoneSet(240_000, 270_000))
        assertEquals(listOf(90_000L, 90_000L), restsMs(sets))
    }

    @Test
    fun `serie i biegnaca seria przezywaja ubicie procesu`() {
        val a = start.startSet(0).endSet(30_000).copy(restSec = 120).startSet(150_000)
        val back = LiveActivity.fromJson(a.toJson())!!
        assertEquals(a, back)
        assertTrue(back.inSet)
    }

    @Test
    fun `zapis sprzed treningu silowego wstaje bez serii`() {
        val old = """{"key":"phone-2","kind":"RUN","startedAt":5,"pausedAt":-1,"pausedMs":0,"seg":0}"""
        val back = LiveActivity.fromJson(old)!!
        assertTrue(back.sets.isEmpty())
        assertNull(back.setStartedAt)
        assertEquals(LiveActivity.DEFAULT_REST_SEC, back.restSec)
    }
}
