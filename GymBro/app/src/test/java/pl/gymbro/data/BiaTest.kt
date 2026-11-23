package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skład ciała z oporu. Reguły i źródła: `docs/sklad-ciala.md`.
 *
 * Wartości oczekiwane policzone ręcznie ze wzorów z dokumentu, nie
 * z implementacji - test ma złapać pomyłkę w stałej albo zamienione płcie.
 */
class BiaTest {

    private fun ftf(ohm: Double) = BiaValue(BiaPath.FOOT_TO_FOOT, ohm)
    private fun htf(ohm: Double) = BiaValue(BiaPath.HAND_TO_FOOT, ohm)

    // ---- Wzory ----

    @Test
    fun `mezczyzna 180 cm 85 kg 480 omow 30 lat`() {
        // indeks 180^2/480 = 67,5
        val e = Bia.estimate(listOf(htf(480.0)), 85.0, 180.0, 30, Sex.MALE)!!
        assertEquals(62.802, e.leanKg, 0.001)   // -10,678 + 22,27 + 44,01 + 7,2
        assertEquals(22.198, e.fatKg, 0.001)
        assertEquals(26.115, e.fatPct, 0.01)
        assertEquals(46.875, e.waterKg, 0.001)  // 1,2 + 30,375 + 15,3
        assertEquals(33.8645, e.muscleKg!!, 0.001) // 27,0675 + 3,825 - 2,13 + 5,102
        assertEquals(Bia.Basis.DIRECT, e.basis)
    }

    @Test
    fun `kobieta 165 cm 62 kg 600 omow 28 lat`() {
        // indeks 165^2/600 = 45,375
        val e = Bia.estimate(listOf(htf(600.0)), 62.0, 165.0, 28, Sex.FEMALE)!!
        assertEquals(42.068, e.leanKg, 0.001)   // -9,529 + 10,416 + 31,581 + 9,6
        assertEquals(32.148, e.fatPct, 0.01)
        assertEquals(30.98875, e.waterKg, 0.001)
        assertEquals(21.309375, e.muscleKg!!, 0.001) // bez 3,825 za płeć
    }

    @Test
    fun `nizszy opor przy tej samej masie to wiecej tkanki beztluszczowej`() {
        val more = Bia.estimate(listOf(htf(420.0)), 85.0, 180.0, 30, Sex.MALE)!!
        val less = Bia.estimate(listOf(htf(520.0)), 85.0, 180.0, 30, Sex.MALE)!!
        assertTrue(more.leanKg > less.leanKg)
        assertTrue(more.fatPct < less.fatPct)
    }

    // ---- Droga prądu ----

    @Test
    fun `stopa - stopa liczy sie wzorem pozyczonym`() {
        val e = Bia.estimate(listOf(ftf(480.0)), 85.0, 180.0, 30, Sex.MALE)!!
        assertEquals(Bia.Basis.BORROWED, e.basis)
        assertEquals(BiaPath.FOOT_TO_FOOT, e.path)
    }

    @Test
    fun `reka - stopa wygrywa ze stopa - stopa`() {
        val e = Bia.estimate(listOf(ftf(510.0), htf(480.0)), 85.0, 180.0, 30, Sex.MALE)!!
        assertEquals(Bia.Basis.DIRECT, e.basis)
        assertEquals(480.0, e.ohm, 0.0)
    }

    @Test
    fun `cale cialo skladane z segmentow prawej strony`() {
        val bia = listOf(
            BiaValue(BiaPath.RIGHT_ARM, 300.0),
            BiaValue(BiaPath.TRUNK, 25.0),
            BiaValue(BiaPath.RIGHT_LEG, 230.0),
            BiaValue(BiaPath.LEFT_ARM, 290.0),
            BiaValue(BiaPath.LEFT_LEG, 240.0),
            ftf(500.0)
        )
        val e = Bia.estimate(bia, 85.0, 180.0, 30, Sex.MALE)!!
        assertEquals(BiaPath.HAND_TO_FOOT, e.path)
        assertEquals(555.0, e.ohm, 0.0)
        assertEquals(Bia.Basis.DIRECT, e.basis)
    }

    @Test
    fun `asymetria to odwrotnosc stosunku oporow`() {
        val a = Bia.asymmetry(
            listOf(
                BiaValue(BiaPath.LEFT_ARM, 290.0),
                BiaValue(BiaPath.RIGHT_ARM, 300.0),
                BiaValue(BiaPath.LEFT_LEG, 240.0),
                BiaValue(BiaPath.RIGHT_LEG, 230.0)
            )
        )!!
        // lewa ręka ma mniejszy opór, więc więcej tkanki: 300/290 - 1
        assertEquals(3.448, a.armsPct!!, 0.001)
        assertEquals(-4.167, a.legsPct!!, 0.001)
    }

    @Test
    fun `bez segmentow nie ma asymetrii`() {
        assertNull(Bia.asymmetry(listOf(ftf(480.0))))
        assertNull(Bia.estimate(listOf(ftf(480.0)), 85.0, 180.0, 30, Sex.MALE)!!.asymmetry)
    }

    // ---- Kiedy nie liczymy ----

    @Test
    fun `waga bez oporu daje sama mase`() {
        assertNull(Bia.estimate(emptyList(), 85.0, 180.0, 30, Sex.MALE))
    }

    @Test
    fun `bez wzrostu nie ma skladu`() {
        assertNull(Bia.estimate(listOf(htf(480.0)), 85.0, null, 30, Sex.MALE))
    }

    @Test
    fun `bez wieku nie ma miesni, reszta jest`() {
        val e = Bia.estimate(listOf(htf(480.0)), 85.0, 180.0, null, Sex.MALE)!!
        assertNull(e.muscleKg)
        assertEquals(62.802, e.leanKg, 0.001)
    }

    @Test
    fun `liczy sie tylko 50 kHz`() {
        val only5 = listOf(BiaValue(BiaPath.HAND_TO_FOOT, 520.0, freqKHz = 5.0))
        assertNull(Bia.estimate(only5, 85.0, 180.0, 30, Sex.MALE))

        val mixed = only5 + BiaValue(BiaPath.HAND_TO_FOOT, 480.0, freqKHz = 50.0) +
            BiaValue(BiaPath.HAND_TO_FOOT, 440.0, freqKHz = 250.0)
        assertEquals(480.0, Bia.estimate(mixed, 85.0, 180.0, 30, Sex.MALE)!!.ohm, 0.0)
    }

    @Test
    fun `opor poza zakresem to nie pomiar`() {
        assertNull(Bia.estimate(listOf(htf(60.0)), 85.0, 180.0, 30, Sex.MALE))
        assertNull(Bia.estimate(listOf(htf(5000.0)), 85.0, 180.0, 30, Sex.MALE))
    }

    @Test
    fun `masa beztluszczowa wieksza niz cialo to blad oporu`() {
        // indeks 216 przy 50 kg daje ponad 140 kg masy beztłuszczowej
        assertNull(Bia.estimate(listOf(htf(150.0)), 50.0, 180.0, 30, Sex.MALE))
    }

    // ---- Historia ----

    @Test
    fun `wiek liczy sie w roku pomiaru, nie dzis`() {
        val cal = java.util.Calendar.getInstance().apply { set(2020, 5, 1) }
        assertEquals(30, Bia.ageAt(1990, cal.timeInMillis))
        assertNull(Bia.ageAt(null, cal.timeInMillis))
    }

    @Test
    fun `zapisany pomiar przelicza sie z profilu`() {
        val m = BodyMeasurement(
            measuredAt = java.util.Calendar.getInstance().apply { set(2026, 0, 10) }.timeInMillis,
            weightKg = 85.0,
            source = "waga BLE",
            bia = listOf(htf(480.0))
        )
        val e = Bia.of(m, UserProfile(heightCm = 180.0, sex = Sex.MALE, birthYear = 1996))
        assertNotNull(e)
        assertEquals(33.8645, e!!.muscleKg!!, 0.001)
    }
}
