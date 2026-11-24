package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Które ćwiczenie pokazuje telefon po każdej serii - [LiveSession.follow].
 *
 * Trening prowadzi zegarek, więc ekran telefonu ma iść za seriami, a nie
 * stać na ćwiczeniu, które sam uznał za bieżące.
 */
class LiveSessionFollowTest {

    private val nic = listOf(false, false, false, false)

    @Test
    fun `nowy trening zaczyna od pierwszego niedokonczonego, nie od numeru z poprzedniego`() {
        assertEquals(0, LiveSession.follow(nic, newest = null, current = 3, fresh = true))
        assertEquals(1, LiveSession.follow(listOf(true, false, false), newest = null, current = 2, fresh = true))
    }

    @Test
    fun `domkniecie cwiczenia przenosi na nastepne`() {
        val stan = listOf(true, false, false, false)
        assertEquals(1, LiveSession.follow(stan, newest = 0, current = 0, fresh = false))
    }

    @Test
    fun `seria z zegarka na innym cwiczeniu przenosi telefon za nia`() {
        // Telefon stal na ostatnim, a seria wpadla do drugiego.
        assertEquals(1, LiveSession.follow(nic, newest = 1, current = 3, fresh = false))
    }

    @Test
    fun `domkniecie przeskakuje zrobione w przod`() {
        val stan = listOf(false, true, true, false)
        assertEquals(3, LiveSession.follow(stan, newest = 1, current = 1, fresh = false))
    }

    @Test
    fun `za ostatnim domknietym wraca do pominietego z poczatku`() {
        val stan = listOf(false, true, true, true)
        assertEquals(0, LiveSession.follow(stan, newest = 3, current = 3, fresh = false))
    }

    @Test
    fun `bez nowej serii ekran zostaje tam, gdzie stal`() {
        // Poprawka albo skasowanie serii nie przestawia ekranu.
        assertEquals(2, LiveSession.follow(listOf(true, false, false, false), newest = null, current = 2, fresh = false))
    }

    @Test
    fun `wszystko zrobione zostaje na ostatnim domknietym`() {
        val stan = listOf(true, true, true)
        assertEquals(2, LiveSession.follow(stan, newest = 2, current = 2, fresh = false))
    }
}
