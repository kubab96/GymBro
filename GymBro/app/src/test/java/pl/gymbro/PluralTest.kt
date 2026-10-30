package pl.gymbro

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.gymbro.ui.plural

class PluralTest {

    private fun dni(n: Int) = plural(n, "dzień", "dni", "dni")
    private fun noce(n: Int) = plural(n, "noc", "noce", "nocy")
    private fun treningi(n: Int) = plural(n, "trening", "treningi", "treningów")

    @Test
    fun `jeden bierze forme pojedyncza`() {
        assertEquals("1 dzień", dni(1))
        assertEquals("1 noc", noce(1))
        assertEquals("1 trening", treningi(1))
    }

    @Test
    fun `od dwoch do czterech bierze forme mnoga`() {
        assertEquals("2 noce", noce(2))
        assertEquals("3 noce", noce(3))
        assertEquals("4 treningi", treningi(4))
    }

    @Test
    fun `piec i wiecej bierze dopelniacz`() {
        assertEquals("5 nocy", noce(5))
        assertEquals("11 nocy", noce(11))
        assertEquals("800 treningów", treningi(800))
    }

    @Test
    fun `nastolatki sa wyjatkiem mimo koncowki`() {
        // 12, 13, 14 kończą się na 2, 3, 4, ale odmieniają się jak 5 i więcej.
        assertEquals("12 nocy", noce(12))
        assertEquals("13 nocy", noce(13))
        assertEquals("14 nocy", noce(14))
    }

    @Test
    fun `dziesiatki powyzej nastolatkow wracaja do reguly koncowki`() {
        assertEquals("22 noce", noce(22))
        assertEquals("23 treningi", treningi(23))
        assertEquals("25 nocy", noce(25))
    }

    @Test
    fun `zero bierze dopelniacz`() {
        assertEquals("0 nocy", noce(0))
    }

    @Test
    fun `siedemnascie dni krokow z naszej synchronizacji`() {
        assertEquals("17 dni", dni(17))
    }
}
