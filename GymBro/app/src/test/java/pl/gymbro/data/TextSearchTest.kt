package pl.gymbro.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wyszukiwanie tak, jak się pisze na telefonie - [TextSearch]. */
class TextSearchTest {

    @Test
    fun `bez polskich znakow znajduje z polskimi znakami`() {
        assertTrue(TextSearch.matches("wioslowanie", "Wiosłowanie sztangą"))
        assertTrue(TextSearch.matches("ZOLW", "żółw"))
    }

    @Test
    fun `z polskimi znakami tez znajduje`() {
        assertTrue(TextSearch.matches("wiosłowanie", "Wiosłowanie sztangą"))
    }

    @Test
    fun `kilka slow to kilka warunkow w dowolnej kolejnosci`() {
        assertTrue(TextSearch.matches("klatka hantli", "Wyciskanie hantli leżąc", "klatka"))
        assertFalse(TextSearch.matches("klatka sztanga", "Wyciskanie hantli leżąc", "klatka"))
    }

    @Test
    fun `slowo dopasowuje sie od poczatku wyrazu`() {
        assertTrue(TextSearch.matches("hantl", "Uginanie hantlami"))
        assertFalse(TextSearch.matches("ok", "Unoszenie bokiem"))
    }

    @Test
    fun `myslnik i nawiasy rozdzielaja wyrazy`() {
        assertTrue(TextSearch.matches("dni", "FBW - 3 dni"))
        assertTrue(TextSearch.matches("5", "Siła (5×5)"))
    }

    @Test
    fun `puste zapytanie pasuje do wszystkiego`() {
        assertTrue(TextSearch.matches("   ", "cokolwiek"))
    }
}
