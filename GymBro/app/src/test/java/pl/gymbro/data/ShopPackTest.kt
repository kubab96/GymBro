package pl.gymbro.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Sprawdzenie paczki produktów z tabel opublikowanych przez sklep.
 *
 * To test **pliku w zasobach**, nie kodu - jak [FoodPackTest]. Paczka
 * powstaje poza aplikacją, z kart produktów Frisco.pl przepuszczonych przez
 * `tools/frisco-zbierz.sh`, i ma ponad tysiąc wierszy, których nie da się
 * przeczytać oczami.
 *
 * Czytnik strony wywrócił się trzy razy, a każda z tych usterek wyglądała
 * jak brak danych, nie jak błąd: nieznany zestaw etykiet, przestawiona
 * kolejność wierszy i nieodcięta tabela, przy której słowo „błonnik"
 * dopasowało się 185 344 znaki dalej, w nazwie innego produktu. Wszystkie
 * trzy wyłapał jeden warunek - zgodność z wzorem unijnym - i dlatego ten
 * warunek stoi tutaj jako test.
 *
 * Sposób dotarcia do pliku: testy JVM uruchamiają się z katalogiem modułu
 * jako roboczym, więc ścieżka jest względna wobec `app/`.
 */
class ShopPackTest {

    private val plik = File("src/main/assets/sklepy-polska.tsv")

    /** Tyle wierszy miała paczka przy budowaniu; spadek znaczy urwany zbiór. */
    private val minimumWierszy = 1_000

    private fun wiersze(): List<List<String>> =
        plik.bufferedReader()
            .useLines { lines -> lines.filter { it.isNotBlank() }.map { it.split('\t') }.toList() }

    @Test
    fun `paczka jest na miejscu`() {
        assertTrue(
            "brak ${plik.absolutePath} - patrz docs/kalorie.md, " +
                "rozdzial o odtwarzaniu paczki sklepowej",
            plik.isFile
        )
    }

    @Test
    fun `paczka ma tyle produktow, ile powinna`() {
        // Prog, nie dokladna liczba: kolejny zbior bedzie mial inna, bo
        // asortyment sklepu sie zmienia. Chodzi o wylapanie zbieracza,
        // ktory przestal cokolwiek przepuszczac, albo pliku urwanego
        // w polowie.
        val n = wiersze().size
        assertTrue("$n wierszy, oczekiwano co najmniej $minimumWierszy", n >= minimumWierszy)
    }

    @Test
    fun `kazdy wiersz ma komplet kolumn`() {
        val zle = wiersze().withIndex()
            .filter { (_, f) -> f.size != 10 }
            .take(5)
            .map { (i, f) -> "wiersz ${i + 1}: ${f.size} kolumn" }
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `zaden wiersz nie przeszedl z odrzutu`() {
        // Zbieracz wypisuje odrzuty na to samo wyjscie, co wiersze udane,
        // i odsiewa je dopiero `grep -v ODRZUT`. Pominiecie tego kroku
        // wpuscilo by do katalogu produkty z napisem "ODRZUT" w miejscu
        // kalorii - widoczne dopiero przy wyszukiwaniu.
        val zle = wiersze().withIndex()
            .filter { (_, f) -> f.any { it.contains("ODRZUT") } }
            .take(5)
            .map { (i, _) -> "wiersz ${i + 1}" }
        assertTrue(zle.joinToString(), zle.isEmpty())
    }

    @Test
    fun `nazwy sa niepuste, rozsadnej dlugosci i sie nie powtarzaja`() {
        val nazwy = wiersze().map { it[0] }

        val zle = nazwy.withIndex()
            .filter { (_, n) -> n.isBlank() || n.length > 120 }
            .take(5)
            .map { (i, n) -> "wiersz ${i + 1}: \"${n.take(40)}\"" }
        assertTrue(zle.joinToString("\n"), zle.isEmpty())

        // Nazwa jest tu jedyna tozsamoscia produktu - kodu kreskowego te
        // wiersze nie maja. Powtorzona nazwa to dwa wiersze, z ktorych
        // drugi i tak przepadnie przy imporcie, bo [ShopPack] odrzuca
        // klucze, ktore juz widzial.
        val powtorki = nazwy.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(powtorki.keys.take(5).joinToString("\n"), powtorki.isEmpty())
    }

    @Test
    fun `wartosci odzywcze miesza sie w skali`() {
        val zle = wiersze().withIndex().mapNotNull { (i, f) ->
            val kcal = f[1].toDoubleOrNull()
            val prot = f[2].toDoubleOrNull()
            val carb = f[3].toDoubleOrNull()
            val fat = f[4].toDoubleOrNull()
            when {
                kcal == null || kcal <= 0 || kcal > 900 -> "wiersz ${i + 1}: kcal ${f[1]}"
                prot == null || prot < 0 || prot > 100 -> "wiersz ${i + 1}: bialko ${f[2]}"
                carb == null || carb < 0 || carb > 100 -> "wiersz ${i + 1}: wegle ${f[3]}"
                fat == null || fat < 0 || fat > 100 -> "wiersz ${i + 1}: tluszcz ${f[4]}"
                prot + carb + fat > 105 -> "wiersz ${i + 1}: suma makro ${prot + carb + fat}"
                else -> null
            }
        }.take(5)
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `pola opcjonalne sa albo puste, albo liczba w skali`() {
        // Puste znaczy "sklep tego nie podal", a nie "zero". Zero gramow
        // blonnika to twierdzenie, i zwykle falszywe - dlatego [ShopPack]
        // wstawia tam NULL, a nie zero.
        val zle = wiersze().withIndex().mapNotNull { (i, f) ->
            val nazwy = listOf(5 to "blonnik", 6 to "cukry", 7 to "nasycone", 8 to "sol")
            nazwy.firstNotNullOfOrNull { (kol, co) ->
                val raw = f[kol]
                if (raw.isBlank()) null
                else raw.toDoubleOrNull()
                    .let { v -> if (v == null || v < 0 || v > 100) "wiersz ${i + 1}: $co \"$raw\"" else null }
            }
        }.take(5)
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `gramatura jest pusta albo mieszcza sie w opakowaniu`() {
        val zle = wiersze().withIndex().mapNotNull { (i, f) ->
            val raw = f[9]
            if (raw.isBlank()) return@mapNotNull null
            val g = raw.toDoubleOrNull()
            // Gorna granica to piec kilogramow: wiekszych opakowan sklep
            // nie sprzedaje, a wyzsza liczba znaczy, ze z "0,4 kg"
            // wyciagnieto samo "4".
            if (g == null || g < 1 || g > 5_000) "wiersz ${i + 1}: gramatura \"$raw\"" else null
        }.take(5)
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `kazdy wiersz zgadza sie z wzorem unijnym`() {
        // Jedyna obrona przed cichym bledem czytnika strony. Wzor liczy
        // energie ze skladu: 4 kcal za gram bialka i weglowodanow, 9 za
        // gram tluszczu, 2 za gram blonnika - patrz docs/kalorie.md,
        // "Przeliczanie masy na energie".
        //
        // Weglowodany sa tu BEZ blonnika, zgodnie z etykieta europejska.
        // Przy konwencji amerykanskiej, gdzie blonnik siedzi w
        // weglowodanach, ten test sypalby sie na produktach z duza
        // zawartoscia blonnika - i to jest jego drugie zadanie.
        val zle = wiersze().withIndex().mapNotNull { (i, f) ->
            val kcal = f[1].toDoubleOrNull() ?: return@mapNotNull "wiersz ${i + 1}: kcal ${f[1]}"
            val prot = f[2].toDoubleOrNull() ?: return@mapNotNull "wiersz ${i + 1}: bialko ${f[2]}"
            val carb = f[3].toDoubleOrNull() ?: return@mapNotNull "wiersz ${i + 1}: wegle ${f[3]}"
            val fat = f[4].toDoubleOrNull() ?: return@mapNotNull "wiersz ${i + 1}: tluszcz ${f[4]}"
            val fib = f[5].toDoubleOrNull() ?: 0.0

            val zeSkladu = 4 * prot + 4 * carb + 9 * fat + 2 * fib
            val granica = max(10.0, 0.1 * kcal)
            if (abs(zeSkladu - kcal) > granica) {
                "wiersz ${i + 1} (${f[0].take(40)}): ze skladu ${"%.0f".format(zeSkladu)}, " +
                    "deklarowane $kcal"
            } else null
        }.take(5)
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `w paczce jest pieczywo sprzedawane na wage`() {
        // Sprawdzian, ze zebrano wlasciwe dzialy. To wlasnie towar bez kodu
        // kreskowego byl luka, ktora ta paczka zalatwia - gdyby zbieracz
        // trafil w zly dzial, reszta testow nadal by przechodzila.
        val tekst = wiersze().joinToString("\n") { it[0] }.lowercase()
        val znalezione = listOf("bułk", "chleb", "rogal", "ser", "wędlin", "pomidor")
            .count { tekst.contains(it) }
        assertTrue("znaleziono $znalezione rodzajow z 6", znalezione >= 4)
    }
}
