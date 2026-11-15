package pl.gymbro.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightSettlerTest {

    private fun sample(kg: Double, stable: Boolean?) = WeightSample(kg, stable, source = "test")

    @Test
    fun `wynik potwierdzony przez wage jest brany od pierwszej ramki`() {
        val v = WeightSettler().offer(sample(84.05, true), now = 0)!!
        assertEquals(WeightSettler.Reason.SCALE_SAID, v.reason)
        assertEquals(84.05, v.sample.weightKg, 0.001)
        assertTrue(v.certain)
    }

    @Test
    fun `jedna ramka bez potwierdzenia to jeszcze nie wynik`() {
        val s = WeightSettler()
        assertNull(s.offer(sample(84.05, null), now = 0))
        assertNull(s.tick(now = 5_000))
    }

    @Test
    fun `liczba, ktora stoi, staje sie wynikiem bez bitu od wagi`() {
        val s = WeightSettler()
        assertNull(s.offer(sample(84.05, null), now = 0))
        assertNull(s.offer(sample(84.05, null), now = 700))
        val v = s.offer(sample(84.05, null), now = 1_600)!!
        assertEquals(WeightSettler.Reason.STEADY, v.reason)
        assertTrue(v.certain)
    }

    @Test
    fun `zmiana odczytu zaczyna liczenie od nowa`() {
        val s = WeightSettler()
        assertNull(s.offer(sample(60.0, null), now = 0))
        assertNull(s.offer(sample(84.0, null), now = 1_000))
        // 2000 ms od startu, ale dopiero 1000 ms od zmiany - za wczesnie.
        assertNull(s.offer(sample(84.05, null), now = 2_000))
        val v = s.offer(sample(84.05, null), now = 2_600)!!
        assertEquals(84.05, v.sample.weightKg, 0.001)
    }

    @Test
    fun `waga mowi jeszcze nie - czekamy dluzej, ale nie w nieskonczonosc`() {
        val s = WeightSettler()
        assertNull(s.offer(sample(84.05, false), now = 0))
        assertNull(s.offer(sample(84.05, false), now = 2_000))
        val v = s.offer(sample(84.05, false), now = 3_600)!!
        assertEquals(WeightSettler.Reason.STEADY, v.reason)
    }

    @Test
    fun `cisza po odczycie oddaje ostatnia liczbe z zastrzezeniem`() {
        val s = WeightSettler()
        assertNull(s.offer(sample(83.95, false), now = 0))
        // Dziura w odbiorze na kilka sekund jest zwykla rzecza - to nie koniec.
        assertNull(s.tick(now = 7_000))
        val v = s.tick(now = 8_000)!!
        assertEquals(WeightSettler.Reason.SILENCE, v.reason)
        assertEquals(83.95, v.sample.weightKg, 0.001)
        assertFalse(v.certain)
    }

    @Test
    fun `po wyniku nie ma drugiego z tej samej serii`() {
        val s = WeightSettler()
        s.offer(sample(84.05, true), now = 0)
        assertNull(s.tick(now = 60_000))
    }

    @Test
    fun `bez zadnego odczytu cisza nic nie znaczy`() {
        assertNull(WeightSettler().tick(now = 60_000))
    }
}
