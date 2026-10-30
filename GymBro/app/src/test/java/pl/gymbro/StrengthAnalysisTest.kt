package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.SetEntry
import pl.gymbro.gym.RunAnalysis.Sample
import pl.gymbro.gym.StrengthAnalysis

class StrengthAnalysisTest {

    private val t0 = 1_700_000_000_000L
    private fun sec(n: Long) = t0 + n * 1000L

    private fun set(
        exerciseId: Long,
        at: Long,
        weight: Double = 100.0,
        reps: Int = 5,
        warmup: Boolean = false
    ) = SetEntry(
        sessionId = 1, exerciseId = exerciseId, setIndex = 0,
        weightKg = weight, reps = reps, isWarmup = warmup, doneAt = at
    )

    private val names = mapOf(1L to "Przysiad", 2L to "Uginanie")

    @Test
    fun `cwiczenia ida w kolejnosci wykonywania`() {
        val sets = listOf(
            set(2, sec(600)),
            set(1, sec(100)),
            set(1, sec(200))
        )
        val blocks = StrengthAnalysis.blocks(sets, names)
        assertEquals(listOf("Przysiad", "Uginanie"), blocks.map { it.name })
    }

    @Test
    fun `rozgrzewki nie wchodza do pracy`() {
        val sets = listOf(
            set(1, sec(100), weight = 20.0, warmup = true),
            set(1, sec(200), weight = 100.0, reps = 5)
        )
        val block = StrengthAnalysis.blocks(sets, names).single()
        assertEquals(1, block.sets.size)
        assertEquals(500.0, block.tonnage, 0.001)
    }

    @Test
    fun `tetno przypisuje sie do serii po czasie`() {
        // Przysiad podbija tętno do 160, uginanie ledwie do 120 - dokładnie
        // ta różnica jest sensem tego ekranu.
        val sets = listOf(set(1, sec(100)), set(2, sec(600)))
        val hr = listOf(
            Sample(sec(90), 155.0),
            Sample(sec(110), 160.0),
            Sample(sec(590), 118.0),
            Sample(sec(610), 120.0)
        )
        val blocks = StrengthAnalysis.blocks(sets, names, hr)
        assertEquals(160.0, blocks[0].hrMax!!, 0.001)
        assertEquals(120.0, blocks[1].hrMax!!, 0.001)
    }

    @Test
    fun `liczy sie szczyt serii a nie srednia z okna`() {
        // W oknie jest też fragment przerwy z niskim tętnem. Średnia
        // zaniżyłaby wysiłek; szczyt go pokazuje.
        val sets = listOf(set(1, sec(100)))
        val hr = listOf(
            Sample(sec(70), 110.0),
            Sample(sec(100), 158.0),
            Sample(sec(140), 112.0)
        )
        assertEquals(158.0, StrengthAnalysis.blocks(sets, names, hr).single().hrMax!!, 0.001)
    }

    @Test
    fun `srednia cwiczenia to srednia ze szczytow serii`() {
        val sets = listOf(set(1, sec(100)), set(1, sec(300)))
        val hr = listOf(Sample(sec(100), 150.0), Sample(sec(300), 170.0))
        val block = StrengthAnalysis.blocks(sets, names, hr).single()
        assertEquals(160.0, block.hrAvg!!, 0.001)
        assertEquals(170.0, block.hrMax!!, 0.001)
    }

    @Test
    fun `probki spoza okna serii nie licza sie`() {
        val sets = listOf(set(1, sec(100)))
        val hr = listOf(Sample(sec(1000), 180.0))
        assertNull(StrengthAnalysis.blocks(sets, names, hr).single().hrMax)
    }

    @Test
    fun `bez zegarka cwiczenia nadal maja tonaz`() {
        val sets = listOf(set(1, sec(100), weight = 80.0, reps = 8))
        val block = StrengthAnalysis.blocks(sets, names).single()
        assertNull(block.hrAvg)
        assertEquals(640.0, block.tonnage, 0.001)
        assertEquals(8, block.totalReps)
        assertEquals(80.0, block.topWeight, 0.001)
    }

    @Test
    fun `nieznane cwiczenie dostaje zastepcza nazwe zamiast wywalic ekran`() {
        val block = StrengthAnalysis.blocks(listOf(set(99, sec(100))), names).single()
        assertTrue(block.name.contains("99"))
    }

    @Test
    fun `sama rozgrzewka daje pusta liste`() {
        val sets = listOf(set(1, sec(100), warmup = true))
        assertTrue(StrengthAnalysis.blocks(sets, names).isEmpty())
    }
}
