package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Podpowiedzi partii przy tworzeniu ćwiczenia - [MuscleMap.GROUPS].
 *
 * Lista i [MuscleMap.regionsOf] to dwa miejsca z tymi samymi nazwami.
 * Podpowiedź, po której dotknięciu ćwiczenie nie świeci na sylwetce, byłaby
 * gorsza niż brak podpowiedzi.
 */
class MuscleMapTest {

    @Test
    fun `kazda podpowiadana partia swieci na sylwetce`() {
        MuscleMap.GROUPS.forEach { partia ->
            assertTrue("partia $partia", MuscleMap.regionsOf(partia).isNotEmpty())
        }
    }

    @Test
    fun `podpowiedzi pokrywaja kazdy obszar sylwetki`() {
        val pokryte = MuscleMap.GROUPS.flatMap { MuscleMap.regionsOf(it) }.toSet()
        assertEquals(MuscleMap.Region.entries.toSet(), pokryte)
    }

    @Test
    fun `partia spoza listy nie swieci, ale tez niczego nie wywraca`() {
        assertTrue(MuscleMap.regionsOf("uda").isEmpty())
    }
}
