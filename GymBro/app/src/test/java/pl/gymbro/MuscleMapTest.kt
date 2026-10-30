package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.gym.MuscleMap
import pl.gymbro.gym.MuscleMap.Region

class MuscleMapTest {

    @Test
    fun `kazda partia z bazy ma swoj obszar`() {
        // Gdyby ktoraś została bez przypisania, zniknęłaby z mapy po cichu.
        val groups = listOf(
            "klatka", "plecy", "barki", "biceps", "triceps",
            "przedramiona", "brzuch", "pośladki", "łydki", "nogi"
        )
        groups.forEach {
            assertTrue("brak obszaru dla: $it", MuscleMap.regionsOf(it).isNotEmpty())
        }
    }

    @Test
    fun `nogi i barki swieca z dwoch stron`() {
        assertEquals(
            listOf(Region.QUADS, Region.HAMSTRINGS),
            MuscleMap.regionsOf("nogi")
        )
        assertEquals(
            listOf(Region.SHOULDERS, Region.REAR_SHOULDERS),
            MuscleMap.regionsOf("barki")
        )
    }

    @Test
    fun `nazwa z wielkiej litery i ze spacjami tez dziala`() {
        assertEquals(listOf(Region.CHEST), MuscleMap.regionsOf("  Klatka "))
    }

    @Test
    fun `nieznana partia nie wywala mapy`() {
        assertTrue(MuscleMap.regionsOf("kark").isEmpty())
    }

    @Test
    fun `nasycenie rosnie z liczba serii`() {
        val i = MuscleMap.intensities(mapOf("klatka" to 10, "biceps" to 5), fullAt = 20)
        assertEquals(0.5f, i[Region.CHEST]!!, 0.001f)
        assertEquals(0.25f, i[Region.BICEPS]!!, 0.001f)
    }

    @Test
    fun `powyzej progu nie przekracza pelni`() {
        val i = MuscleMap.intensities(mapOf("klatka" to 31), fullAt = 20)
        assertEquals(1f, i[Region.CHEST]!!, 0.001f)
    }

    @Test
    fun `jedna seria jest widoczna a nie przezroczysta`() {
        // Różnica między „coś zrobiłem" a „nic" musi być widać.
        val i = MuscleMap.intensities(mapOf("łydki" to 1), fullAt = 20)
        assertTrue(i[Region.CALVES]!! >= 0.18f)
    }

    @Test
    fun `partia bez serii nie trafia na mape`() {
        val i = MuscleMap.intensities(mapOf("klatka" to 0, "plecy" to 4), fullAt = 20)
        assertNull(i[Region.CHEST])
        assertTrue(i.containsKey(Region.BACK))
    }

    @Test
    fun `obszar dzielony przez dwie partie bierze mocniejsza`() {
        // Barki i tylne barki: gdyby doszła osobna partia na ten sam obszar,
        // ma wygrać większa liczba serii, a nie ostatnia w kolejności.
        val i = MuscleMap.intensities(mapOf("barki" to 4, "nogi" to 16), fullAt = 20)
        assertEquals(0.2f, i[Region.SHOULDERS]!!, 0.001f)
        assertEquals(0.8f, i[Region.QUADS]!!, 0.001f)
    }

    @Test
    fun `najmocniej obrobiona partia`() {
        val sets = mapOf("klatka" to 31, "nogi" to 26, "biceps" to 15)
        assertEquals("klatka", MuscleMap.hardestWorked(sets))
    }

    @Test
    fun `bez pracy nie ma najmocniejszej partii`() {
        assertNull(MuscleMap.hardestWorked(mapOf("klatka" to 0)))
        assertNull(MuscleMap.hardestWorked(emptyMap()))
    }

    @Test
    fun `zerowy prog nie dzieli przez zero`() {
        val i = MuscleMap.intensities(mapOf("klatka" to 5), fullAt = 0)
        assertEquals(1f, i[Region.CHEST]!!, 0.001f)
    }
}
