package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trasa z GPS telefonu. Liczby dobrane tak, żeby dało się je sprawdzić w głowie:
 * jedna tysięczna stopnia szerokości to około 111 m.
 */
class TrackMathTest {

    /** Punkt [m] metrów na północ od startu, [s] sekund po starcie. */
    private fun fix(m: Double, s: Int, acc: Float = 5f, seg: Int = 0) =
        TrackMath.Fix(at = s * 1000L, lat = 52.0 + m / 111_195.0, lon = 21.0, accuracyM = acc, seg = seg)

    @Test
    fun `odleglosc po kuli zgadza sie z poludnikiem`() {
        val d = TrackMath.meters(52.0, 21.0, 52.001, 21.0)
        assertEquals(111.2, d, 0.5)
    }

    @Test
    fun `bieg prosto liczy caly dystans`() {
        val acc = TrackMath.Accumulator(PhoneActivity.RUN.maxSpeedMs)
        (0..100).forEach { acc.offer(fix(it * 3.0, it)) }
        assertEquals(300.0, acc.distanceM, 1.0)
    }

    @Test
    fun `stojacy telefon nie nabija dystansu`() {
        val acc = TrackMath.Accumulator(PhoneActivity.RUN.maxSpeedMs)
        // Minuta drgania w promieniu trzech metrów, jak przy czerwonym świetle.
        (0..60).forEach { acc.offer(fix(if (it % 2 == 0) 0.0 else 3.0, it)) }
        assertEquals(0.0, acc.distanceM, 0.01)
    }

    @Test
    fun `skok szybszy niz bieg jest odrzucany`() {
        val acc = TrackMath.Accumulator(PhoneActivity.RUN.maxSpeedMs)
        acc.offer(fix(0.0, 0))
        assertFalse(acc.offer(fix(200.0, 2)))
        assertTrue(acc.offer(fix(10.0, 3)))
        assertEquals(10.0, acc.distanceM, 0.5)
    }

    @Test
    fun `odczyt o slabej dokladnosci nie wchodzi do trasy`() {
        val acc = TrackMath.Accumulator(PhoneActivity.WALK.maxSpeedMs)
        assertFalse(acc.offer(fix(0.0, 0, acc = 80f)))
        assertNull(acc.anchor)
    }

    @Test
    fun `po pauzie nie liczymy drogi przebytej w pauzie`() {
        val acc = TrackMath.Accumulator(PhoneActivity.WALK.maxSpeedMs)
        acc.offer(fix(0.0, 0))
        acc.offer(fix(10.0, 10))
        // Pauza: pół kilometra dalej, nowy segment.
        acc.offer(fix(510.0, 400, seg = 1))
        acc.offer(fix(520.0, 410, seg = 1))
        assertEquals(20.0, acc.distanceM, 0.5)
    }

    @Test
    fun `odtworzenie z zapisanych punktow daje ten sam dystans`() {
        val acc = TrackMath.Accumulator(PhoneActivity.RUN.maxSpeedMs)
        val zapisane = mutableListOf<TrackMath.Fix>()
        (0..50).forEach { val f = fix(it * 3.0, it); if (acc.offer(f)) zapisane += f }
        val drugi = TrackMath.Accumulator(PhoneActivity.RUN.maxSpeedMs)
        drugi.restore(zapisane)
        assertEquals(acc.distanceM, drugi.distanceM, 0.01)
    }

    @Test
    fun `kilometry dziela trase z czasem przekroczenia`() {
        // 2,5 km po 4 m/s - kilometr co 250 s.
        val pts = (0..625).map { fix(it * 4.0, it) }
        val legs = TrackMath.splits(pts)
        assertEquals(3, legs.size)
        assertEquals(1000.0, legs[0].meters, 0.01)
        assertEquals(250_000.0, (legs[0].to - legs[0].from).toDouble(), 1_000.0)
        assertEquals(500.0, legs[2].meters, 5.0)
    }

    @Test
    fun `tempo z ostatnich trzydziestu sekund`() {
        // 3 m/s to 5:33 na kilometr.
        val pts = (0..60).map { fix(it * 3.0, it) }
        val pace = TrackMath.recentPace(pts, now = 60_000)
        assertNotNull(pace)
        assertEquals(1000.0 / 3.0 / 60.0, pace!!, 0.05)
    }

    @Test
    fun `brak swiezych punktow to brak tempa, nie zero`() {
        val pts = (0..10).map { fix(it * 3.0, it) }
        assertNull(TrackMath.recentPace(pts, now = 120_000))
    }

    @Test
    fun `energia biegu z rownania ACSM`() {
        // 10 km/h przez godzinę, 80 kg: v = 166,7 m/min, VO2 = 36,8 ml/kg/min,
        // 36,8 · 80 · 60 / 1000 · 5 = 884 kcal.
        val kcal = TrackMath.energyKcal(PhoneActivity.RUN, 10_000.0, 3_600_000, 80.0)
        assertEquals(884.0, kcal!!, 2.0)
    }

    @Test
    fun `kazda aktywnosc ma wlasna nazwe w historii i wraca po niej`() {
        val types = PhoneActivity.entries.map { it.type }
        assertEquals(types.size, types.toSet().size)
        PhoneActivity.entries.forEach { assertEquals(it, PhoneActivity.byType(it.type)) }
    }

    @Test
    fun `kazda aktywnosc z ekranu wyboru ma energie`() {
        // Bieg i spacer liczą się z dystansu, reszta z MET. Kafelek bez
        // jednego i drugiego zapisywałby trening z kaloriami równymi zeru.
        PhoneActivity.entries.filter { it.group != ActivityGroup.HIDDEN }.forEach { k ->
            val kcal = TrackMath.energyKcal(k, 5000.0, 1_800_000, 80.0)
            assertTrue("${k.name} bez energii", kcal != null && kcal > 0)
        }
    }

    @Test
    fun `na ekranie wyboru jest osiem aktywnosci i trening silowy`() {
        val shown = PhoneActivity.entries.filter { it.group != ActivityGroup.HIDDEN }
        assertEquals(9, shown.size)
        assertEquals(listOf(PhoneActivity.STRENGTH), shown.filter { it.sets })
        assertEquals(4, shown.count { it.gps })
    }

    @Test
    fun `znaczniki kilometrow stoja na kazdym pelnym kilometrze`() {
        // 2,5 km prosto na północ.
        val pts = (0..625).map { fix(it * 4.0, it) }
        val marks = TrackMath.kmMarks(pts)
        assertEquals(2, marks.size)
        assertEquals(1000.0, TrackMath.meters(pts.first(), marks[0]), 1.0)
        assertEquals(2000.0, TrackMath.meters(pts.first(), marks[1]), 1.0)
        // Czas przekroczenia ten sam co w tabeli kilometrów.
        assertEquals(TrackMath.splits(pts)[0].to.toDouble(), marks[0].at.toDouble(), 1000.0)
    }

    @Test
    fun `kolor tempa wzgledem sredniej z tego samego biegu`() {
        // Pierwsze 500 m po 4 m/s, drugie po 2 m/s.
        val fast = (0..125).map { fix(it * 4.0, it) }
        val slow = (1..250).map { fix(500.0 + it * 2.0, 125 + it) }
        val chunks = TrackMath.paceChunks(fast + slow)
        assertTrue(chunks.first().ratio > 1.04)
        assertTrue(chunks.last().ratio < 0.96)
        // Odcinki stykają się - linia na mapie bez dziur.
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.points.last(), b.points.first()) }
    }

    @Test
    fun `po pauzie odcinki tempa sie nie lacza`() {
        val a = (0..50).map { fix(it * 3.0, it) }
        val b = (0..50).map { fix(1000.0 + it * 3.0, 200 + it, seg = 1) }
        val chunks = TrackMath.paceChunks(a + b)
        assertTrue(chunks.none { c -> c.points.map { it.seg }.distinct().size > 1 })
    }

    @Test
    fun `najlepszy kilometr z dowolnego miejsca biegu`() {
        // Kilometr po 3 m/s, kilometr po 5 m/s, kilometr po 3 m/s.
        var t = 0
        val pts = mutableListOf<TrackMath.Fix>()
        var m = 0.0
        fun go(speed: Double, meters: Double) {
            val steps = (meters / speed).toInt()
            repeat(steps) { m += speed; t += 1; pts += fix(m, t) }
        }
        pts += fix(0.0, 0)
        go(3.0, 1000.0); go(5.0, 1000.0); go(3.0, 1000.0)
        val best = TrackMath.bestEffortMs(pts, 1000.0)!!
        assertEquals(200_000.0, best.toDouble(), 2_000.0)
        assertNull(TrackMath.bestEffortMs(pts, 5000.0))
    }

    @Test
    fun `najlepszy wynik nie przechodzi przez pauze`() {
        // Dwa razy po 600 m - kilometra w jednym kawałku nie było.
        val a = (0..200).map { fix(it * 3.0, it) }
        val b = (0..200).map { fix(5000.0 + it * 3.0, 1000 + it, seg = 1) }
        assertNull(TrackMath.bestEffortMs(a + b, 1000.0))
    }

    @Test
    fun `bez masy ciala nie zgadujemy energii`() {
        assertNull(TrackMath.energyKcal(PhoneActivity.RUN, 5000.0, 1_800_000, null))
        assertNull(TrackMath.energyKcal(PhoneActivity.OTHER, 0.0, 1_800_000, 80.0))
    }
}
