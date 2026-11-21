package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testy oszacowania tkanki tłuszczowej metodą US Navy.
 *
 * Wartości oczekiwane policzone niezależnie od implementacji, wprost ze wzoru.
 * Tolerancja 0,05 punktu - chodzi o wyłapanie pomyłki w stałych albo w logarytmach,
 * a nie o ostatnie miejsce po przecinku.
 */
class BodyFatTest {

    // ---- Wartości ze wzoru ----

    @Test
    fun `mezczyzna 180cm talia 85 szyja 38`() {
        val r = BodyFat.navy(Sex.MALE, heightCm = 180.0, waistCm = 85.0, neckCm = 38.0)
        assertNotNull(r)
        assertEquals(16.107, r!!, 0.05)
    }

    @Test
    fun `mezczyzna 175cm talia 95 szyja 40`() {
        val r = BodyFat.navy(Sex.MALE, heightCm = 175.0, waistCm = 95.0, neckCm = 40.0)
        assertNotNull(r)
        assertEquals(22.746, r!!, 0.05)
    }

    @Test
    fun `kobieta 165cm talia 70 biodra 95 szyja 32`() {
        val r = BodyFat.navy(
            Sex.FEMALE, heightCm = 165.0, waistCm = 70.0, neckCm = 32.0, hipsCm = 95.0
        )
        assertNotNull(r)
        assertEquals(24.856, r!!, 0.05)
    }

    // ---- Kierunek zależności ----

    @Test
    fun `wieksza talia przy tej samej reszcie oznacza wiecej tluszczu`() {
        val chudszy = BodyFat.navy(Sex.MALE, 180.0, 80.0, 38.0)!!
        val grubszy = BodyFat.navy(Sex.MALE, 180.0, 95.0, 38.0)!!
        assertTrue("wzrost obwodu talii musi podnosic wynik", grubszy > chudszy)
    }

    @Test
    fun `grubsza szyja przy tej samej talii oznacza mniej tluszczu`() {
        // Szyja jest w tym wzorze przybliżeniem masy beztłuszczowej, więc działa
        // w przeciwną stronę niż talia. Odwrócenie tego znaku byłoby cichym błędem.
        val a = BodyFat.navy(Sex.MALE, 180.0, 90.0, 36.0)!!
        val b = BodyFat.navy(Sex.MALE, 180.0, 90.0, 42.0)!!
        assertTrue("wiekszy obwod szyi musi obnizac wynik", b < a)
    }

    @Test
    fun `wyzsza osoba przy tych samych obwodach ma mniej tluszczu`() {
        // Wzrost wchodzi do wzoru - to odróżnia tę metodę od liczenia z samej masy.
        val niski = BodyFat.navy(Sex.MALE, 165.0, 85.0, 38.0)!!
        val wysoki = BodyFat.navy(Sex.MALE, 195.0, 85.0, 38.0)!!
        assertTrue("wzrost musi wplywac na wynik", wysoki < niski)
    }

    // ---- Brakujące dane ----

    @Test
    fun `brak wzrostu daje null`() {
        assertNull(BodyFat.navy(Sex.MALE, null, 85.0, 38.0))
    }

    @Test
    fun `brak talii daje null`() {
        assertNull(BodyFat.navy(Sex.MALE, 180.0, null, 38.0))
    }

    @Test
    fun `brak szyi daje null`() {
        assertNull(BodyFat.navy(Sex.MALE, 180.0, 85.0, null))
    }

    @Test
    fun `kobieta bez obwodu bioder daje null`() {
        assertNull(BodyFat.navy(Sex.FEMALE, 165.0, 70.0, 32.0, hipsCm = null))
    }

    // ---- Wejścia niefizyczne ----

    @Test
    fun `talia niewieksza od szyi daje null zamiast logarytmu z zera`() {
        assertNull(BodyFat.navy(Sex.MALE, 180.0, 38.0, 38.0))
        assertNull(BodyFat.navy(Sex.MALE, 180.0, 30.0, 38.0))
    }

    @Test
    fun `wartosci ujemne i zerowe daja null`() {
        assertNull(BodyFat.navy(Sex.MALE, 0.0, 85.0, 38.0))
        assertNull(BodyFat.navy(Sex.MALE, 180.0, -85.0, 38.0))
        assertNull(BodyFat.navy(Sex.MALE, 180.0, 85.0, 0.0))
    }

    @Test
    fun `wynik poza zakresem fizjologicznym jest odrzucany`() {
        // Skrajnie mała różnica talia−szyja wypycha wzór poza sensowny zakres.
        val r = BodyFat.navy(Sex.MALE, 250.0, 38.1, 38.0)
        assertTrue("wynik ma byc null albo miescic sie w 2..70", r == null || r in 2.0..70.0)
    }

    // ---- Podpowiedź o brakach ----

    @Test
    fun `missing wymienia dokladnie brakujace pozycje`() {
        val m = BodyFat.missing(Sex.MALE, heightCm = null, waistCm = 85.0, neckCm = null, hipsCm = null)
        assertEquals(2, m.size)
        assertTrue(m.any { it.contains("wzrost") })
        assertTrue(m.any { it.contains("szyi") })
    }

    @Test
    fun `missing dla kompletu danych jest puste`() {
        val m = BodyFat.missing(Sex.MALE, 180.0, 85.0, 38.0, null)
        assertTrue(m.isEmpty())
    }

    @Test
    fun `missing dla kobiety wymaga bioder`() {
        val m = BodyFat.missing(Sex.FEMALE, 165.0, 70.0, 32.0, hipsCm = null)
        assertEquals(1, m.size)
        assertTrue(m.first().contains("bioder"))
    }

    // ---- Instrukcje pomiaru zgodne ze wzorem ----

    @Test
    fun `obwody potrzebne do wyniku to dokladnie te, ktorych brak zglasza wzor`() {
        // Obwód oznaczony jako potrzebny musi naprawdę blokować wynik, gdy go
        // brakuje - inaczej instrukcja i wzór mówią co innego.
        for (sex in Sex.entries) {
            val all = mapOf(
                MeasurementSite.WAIST to 85.0,
                MeasurementSite.NECK to 38.0,
                MeasurementSite.HIPS to 100.0
            )
            for (site in all.keys) {
                val without = all - site
                val r = BodyFat.navy(
                    sex, 180.0,
                    without[MeasurementSite.WAIST],
                    without[MeasurementSite.NECK],
                    without[MeasurementSite.HIPS]
                )
                assertEquals("$sex bez $site", site.usedForBodyFat(sex), r == null)
            }
        }
    }

    @Test
    fun `talia mezczyzny przez pepek, kobiety w najwezszym miejscu`() {
        // Procedura US Navy: u mężczyzn brzuch na wysokości pępka, u kobiet
        // talia naturalna. Poprzednio obie płcie miały „tuż nad pępkiem".
        assertTrue(MeasurementSite.WAIST.howTo(Sex.MALE).contains("pępek"))
        assertTrue(MeasurementSite.WAIST.howTo(Sex.FEMALE).contains("najwęższym"))
    }
}
