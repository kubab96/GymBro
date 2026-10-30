package pl.gymbro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.health.AutoSync

class AutoSyncTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `pierwsze uruchomienie zawsze synchronizuje`() {
        assertTrue(AutoSync.isDue(lastAt = null, now = now))
    }

    @Test
    fun `tuz po synchronizacji nie powtarzamy`() {
        assertFalse(AutoSync.isDue(lastAt = now - 60_000L, now = now))
    }

    @Test
    fun `dokladnie na progu juz mozna`() {
        assertTrue(AutoSync.isDue(lastAt = now - AutoSync.MIN_INTERVAL_MS, now = now))
    }

    @Test
    fun `po kwadransie synchronizujemy ponownie`() {
        assertTrue(AutoSync.isDue(lastAt = now - 20 * 60 * 1000L, now = now))
    }

    @Test
    fun `znacznik z przyszlosci nie blokuje na zawsze`() {
        // Przestawiony zegar systemowy mógłby zapisać znacznik w przyszłości.
        // Nie chcemy, żeby to wyłączyło synchronizację do czasu, aż czas go
        // dogoni - ale jedno pominięcie jest tu akceptowalne.
        assertFalse(AutoSync.isDue(lastAt = now + 60_000L, now = now))
    }
}
