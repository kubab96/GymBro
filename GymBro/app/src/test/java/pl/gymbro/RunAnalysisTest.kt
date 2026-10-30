package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.gym.RunAnalysis
import pl.gymbro.gym.RunAnalysis.Leg
import pl.gymbro.gym.RunAnalysis.Sample

class RunAnalysisTest {

    private val t0 = 1_700_000_000_000L
    private fun sec(n: Long) = t0 + n * 1000L

    // ---- Tempo na kilometr -------------------------------------------------

    @Test
    fun `rowny bieg daje rowne splity`() {
        // Dziesięć odcinków po 500 m, każdy po 150 s → 5:00/km.
        val legs = (0 until 10).map {
            Leg(sec(it * 150L), sec((it + 1) * 150L), 500.0)
        }
        val splits = RunAnalysis.splits(legs)
        assertEquals(5, splits.size)
        splits.forEach { assertEquals(300L, it.seconds) }
        assertEquals(5.0, splits.first().paceMinPerKm, 0.001)
    }

    @Test
    fun `granica kilometra wypada w srodku odcinka i jest interpolowana`() {
        // Jeden odcinek: 2000 m w 600 s. Pierwszy kilometr musi wyjść 300 s,
        // a nie 600 s - inaczej cały czas odcinka doklejałby się do jednego splitu.
        val splits = RunAnalysis.splits(listOf(Leg(sec(0), sec(600), 2000.0)))
        assertEquals(2, splits.size)
        assertEquals(300L, splits[0].seconds)
        assertEquals(300L, splits[1].seconds)
    }

    @Test
    fun `niepelna koncowka nie tworzy splitu`() {
        // 1300 m: jeden pełny kilometr, 300 m zostaje bez przeliczania na tempo.
        val splits = RunAnalysis.splits(listOf(Leg(sec(0), sec(390), 1300.0)))
        assertEquals(1, splits.size)
        assertEquals(1, splits[0].km)
    }

    @Test
    fun `splity numeruja sie po kolei`() {
        val splits = RunAnalysis.splits(listOf(Leg(sec(0), sec(900), 3000.0)))
        assertEquals(listOf(1, 2, 3), splits.map { it.km })
    }

    @Test
    fun `zmienne tempo widac w splitach`() {
        // Pierwszy kilometr szybko (240 s), drugi wolno (360 s).
        val legs = listOf(
            Leg(sec(0), sec(240), 1000.0),
            Leg(sec(240), sec(600), 1000.0)
        )
        val splits = RunAnalysis.splits(legs)
        assertEquals(240L, splits[0].seconds)
        assertEquals(360L, splits[1].seconds)
        assertTrue(splits[0].paceMinPerKm < splits[1].paceMinPerKm)
    }

    @Test
    fun `odcinki bez dystansu i o zerowym czasie sa pomijane`() {
        val legs = listOf(
            Leg(sec(0), sec(0), 100.0),
            Leg(sec(0), sec(300), 0.0),
            Leg(sec(0), sec(300), 1000.0)
        )
        assertEquals(1, RunAnalysis.splits(legs).size)
    }

    @Test
    fun `brak danych daje brak splitow`() {
        assertTrue(RunAnalysis.splits(emptyList()).isEmpty())
    }


    // ---- Gęstość danych ----------------------------------------------------

    @Test
    fun `dwa odcinki na dziesiec kilometrow to za malo na tempo`() {
        // Dokładnie przypadek z zegarka: 10 km w dwóch rekordach. Interpolacja
        // dałaby jedenaście identycznych kilometrów udających równe tempo.
        val legs = listOf(
            Leg(sec(0), sec(1860), 5030.0),
            Leg(sec(1860), sec(3720), 5030.0)
        )
        assertFalse(RunAnalysis.splitsAreMeaningful(legs))
    }

    @Test
    fun `odcinek na kazdy kilometr wystarcza`() {
        val legs = (0 until 10).map { Leg(sec(it * 300L), sec((it + 1) * 300L), 1000.0) }
        assertTrue(RunAnalysis.splitsAreMeaningful(legs))
    }

    @Test
    fun `bieg krotszy niz kilometr nie ma czego dzielic`() {
        assertFalse(RunAnalysis.splitsAreMeaningful(listOf(Leg(sec(0), sec(180), 600.0))))
    }

    @Test
    fun `splity nigdy nie wychodza ujemne przy odcinkach nie po kolei`() {
        // Zachodzące na siebie odcinki potrafiły dać tempo "-56:00".
        val legs = listOf(
            Leg(sec(0), sec(1800), 5000.0),
            Leg(sec(600), sec(2400), 5000.0)
        )
        RunAnalysis.splits(legs).forEach {
            assertTrue("split " + it.km + " ma " + it.seconds + " s", it.seconds >= 0)
        }
    }

    // ---- Okrążenia z zegarka -----------------------------------------------

    @Test
    fun `okrazenie liczy tempo z wlasnego dystansu i czasu`() {
        // Kilometr w 5:40 - jak pierwszy kilometr z zegarka.
        val laps = listOf(Leg(sec(0), sec(340), 1000.0))
        val lap = RunAnalysis.lapStats(laps, emptyList()).single()
        assertEquals(340L, lap.seconds)
        assertEquals(5.667, lap.paceMinPerKm!!, 0.01)
        assertTrue(lap.isFull)
    }

    @Test
    fun `tetno przypisuje sie do okna okrazenia`() {
        val laps = listOf(
            Leg(sec(0), sec(300), 1000.0),
            Leg(sec(300), sec(600), 1000.0)
        )
        val hr = listOf(
            Sample(sec(100), 150.0),
            Sample(sec(200), 150.0),
            Sample(sec(400), 170.0),
            Sample(sec(500), 170.0)
        )
        val stats = RunAnalysis.lapStats(laps, hr)
        assertEquals(150.0, stats[0].hrAvg!!, 0.01)
        assertEquals(170.0, stats[1].hrAvg!!, 0.01)
    }

    @Test
    fun `okrazenie bez probek tetna nie zmysla sredniej`() {
        val laps = listOf(Leg(sec(0), sec(300), 1000.0))
        assertNull(RunAnalysis.lapStats(laps, emptyList()).single().hrAvg)
    }

    @Test
    fun `koncowka trasy nie udaje pelnego kilometra`() {
        val laps = listOf(
            Leg(sec(0), sec(300), 1000.0),
            Leg(sec(300), sec(330), 50.0)
        )
        val stats = RunAnalysis.lapStats(laps, emptyList())
        assertTrue(stats[0].isFull)
        assertFalse(stats[1].isFull)
    }

    @Test
    fun `okrazenia numeruja sie po kolei mimo bylejakiej kolejnosci na wejsciu`() {
        val laps = listOf(
            Leg(sec(600), sec(900), 1000.0),
            Leg(sec(0), sec(300), 1000.0),
            Leg(sec(300), sec(600), 1000.0)
        )
        val stats = RunAnalysis.lapStats(laps, emptyList())
        assertEquals(listOf(1, 2, 3), stats.map { it.index })
        assertEquals(sec(0), laps.minOf { it.from })
    }

    @Test
    fun `zerowe okrazenie nie daje tempa`() {
        val laps = listOf(Leg(sec(0), sec(0), 1000.0))
        assertTrue(RunAnalysis.lapStats(laps, emptyList()).isEmpty())
    }

    // ---- Rytm i krok -------------------------------------------------------

    @Test
    fun `rytm i krok z Twojego biegu zgadzaja sie z zegarkiem`() {
        // Zepp: 10,05 km w 62 min, rytm 147, krok 109 cm.
        val steps = 9150L
        assertEquals(147.6, RunAnalysis.cadence(steps, 62)!!, 0.5)
        assertEquals(109.9, RunAnalysis.strideCm(10060.0, steps)!!, 1.0)
    }

    @Test
    fun `brak krokow nie daje zmyslonego rytmu`() {
        assertNull(RunAnalysis.cadence(0, 62))
        assertNull(RunAnalysis.strideCm(10000.0, 0))
    }

    @Test
    fun `zerowy czas nie dzieli przez zero`() {
        assertNull(RunAnalysis.cadence(5000, 0))
    }

    @Test
    fun `trening bez dystansu nie ma dlugosci kroku`() {
        assertNull(RunAnalysis.strideCm(0.0, 5000))
    }

    // ---- Strefy tętna ------------------------------------------------------

    @Test
    fun `tetno maksymalne z wieku`() {
        assertEquals(190.0, RunAnalysis.maxHrFromAge(30), 0.001)
    }

    @Test
    fun `granice stref`() {
        val max = 200.0
        assertEquals(1, RunAnalysis.zoneOf(100.0, max))  // 50 %
        assertEquals(2, RunAnalysis.zoneOf(120.0, max))  // 60 %
        assertEquals(3, RunAnalysis.zoneOf(140.0, max))  // 70 %
        assertEquals(4, RunAnalysis.zoneOf(160.0, max))  // 80 %
        assertEquals(5, RunAnalysis.zoneOf(180.0, max))  // 90 %
        assertEquals(5, RunAnalysis.zoneOf(205.0, max))  // ponad maksimum
    }

    @Test
    fun `czas w strefach liczy sie z odstepow a nie z liczby probek`() {
        // Trzy próbki w strefie 2 gęsto obok siebie (po 1 s) i jedna w strefie 4
        // trzymana przez 100 s. Liczenie próbek dałoby przewagę strefie 2.
        val max = 200.0
        val samples = listOf(
            Sample(sec(0), 120.0),
            Sample(sec(1), 120.0),
            Sample(sec(2), 120.0),
            Sample(sec(3), 160.0),
            Sample(sec(103), 160.0)
        )
        val zones = RunAnalysis.timeInZones(samples, max)
        // Trzy odstępy po sekundzie, nie dwa: (0→1), (1→2), (2→3).
        assertEquals(3L, zones[2])
        assertEquals(100L, zones[4])
    }

    @Test
    fun `pojedyncza probka nie wystarcza na czas w strefie`() {
        assertTrue(RunAnalysis.timeInZones(listOf(Sample(sec(0), 150.0)), 200.0).isEmpty())
    }

    // ---- Przerzedzanie -----------------------------------------------------

    @Test
    fun `krotka seria zostaje bez zmian`() {
        val s = listOf(Sample(sec(1), 1.0), Sample(sec(2), 2.0))
        assertEquals(2, RunAnalysis.downsample(s, 100).size)
    }

    @Test
    fun `dluga seria schodzi do zadanej liczby punktow`() {
        val s = (0 until 5000).map { Sample(sec(it.toLong()), it.toDouble()) }
        assertEquals(200, RunAnalysis.downsample(s, 200).size)
    }

    @Test
    fun `przerzedzanie usrednia a nie wycina`() {
        // Pojedynczy skok czujnika ma się rozmyć w koszyku, nie zniknąć
        // i nie zdominować wykresu.
        val s = (0 until 100).map { Sample(sec(it.toLong()), if (it == 50) 300.0 else 100.0) }
        val out = RunAnalysis.downsample(s, 10)
        assertEquals(10, out.size)
        assertTrue(out.all { it.value in 100.0..140.0 })
    }

    @Test
    fun `przerzedzona seria zostaje w kolejnosci czasu`() {
        val s = (0 until 1000).map { Sample(sec(it.toLong()), it.toDouble()) }
        val out = RunAnalysis.downsample(s, 50)
        assertEquals(out.sortedBy { it.at }, out)
    }
}
