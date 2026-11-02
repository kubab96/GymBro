package pl.gymbro.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


/**
 * Sprawdzenie paczki polskich produktów.
 *
 * To test **pliku w zasobach**, nie kodu. Paczka powstaje poza aplikacją, ze
 * zrzutu Open Food Facts przepuszczonego przez `tools/filtr-off.awk`, i jest
 * jedyną rzeczą w projekcie, której nie da się przeczytać oczami - ma
 * kilkadziesiąt tysięcy wierszy. Zepsuty filtr, przesunięta kolumna
 * w kolejnym zrzucie albo obcięty plik dałyby katalog pełen śmieci,
 * a zobaczyłbyś to dopiero przy wyszukiwaniu produktu.
 *
 * Sposób dotarcia do pliku: testy JVM uruchamiają się z katalogiem modułu
 * jako roboczym, więc ścieżka jest względna wobec `app/`.
 */
class FoodPackTest {

    private val plik = File("src/main/assets/off-polska.tsv")

    /** Tyle wierszy miała paczka przy budowaniu; spadek znaczy urwany filtr. */
    private val minimumWierszy = 25_000

    private fun wiersze(): List<List<String>> =
        plik.bufferedReader()
            .useLines { lines -> lines.map { it.split('\t') }.toList() }

    @Test
    fun `paczka jest na miejscu i da sie ja rozpakowac`() {
        assertTrue(
            "brak ${plik.absolutePath} - patrz docs/kalorie.md, rozdzial o odtwarzaniu paczki",
            plik.isFile
        )
        assertTrue(plik.length().toString(), plik.length() > 1_000_000)
    }

    @Test
    fun `paczka ma tyle produktow, ile powinna`() {
        // Prog, nie dokladna liczba: kolejny zrzut bedzie mial inna.
        // Chodzi o wylapanie filtra, ktory przestal cokolwiek przepuszczac,
        // albo pliku urwanego w polowie.
        val n = wiersze().size
        assertTrue("$n wierszy, oczekiwano co najmniej $minimumWierszy", n >= minimumWierszy)
    }

    @Test
    fun `kazdy wiersz ma komplet kolumn`() {
        val zle = wiersze().withIndex()
            .filter { (_, f) -> f.size != 13 }
            .take(5)
            .map { (i, f) -> "wiersz ${i + 1}: ${f.size} kolumn" }
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `kody kreskowe sa cyframi i sie nie powtarzaja`() {
        // Powtorzony kod to dwa wiersze tego samego produktu, z ktorych
        // drugi i tak przepadnie na unikalnym indeksie - lepiej wiedziec
        // o tym tutaj niz zastanawiac sie, czemu import dodal mniej.
        val kody = wiersze().map { it[0] }
        val niecyfry = kody.filter { it.isEmpty() || !it.all(Char::isDigit) }.take(5)
        assertTrue(niecyfry.joinToString(), niecyfry.isEmpty())

        val powtorki = kody.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(powtorki.keys.take(5).joinToString(), powtorki.isEmpty())
    }

    @Test
    fun `wartosci odzywcze miesza sie w skali`() {
        // Ta sama granica co przy odczycie po kodzie kreskowym: nic
        // jadalnego nie przekracza czystego tluszczu, a makro nie moze
        // wazyc wiecej niz produkt, w ktorym siedzi.
        val zle = wiersze().withIndex().mapNotNull { (i, f) ->
            val kcal = f[3].toDoubleOrNull()
            val prot = f[4].toDoubleOrNull()
            val carb = f[5].toDoubleOrNull()
            val fat = f[6].toDoubleOrNull()
            when {
                kcal == null || kcal <= 0 || kcal > 900 -> "wiersz ${i + 1}: kcal ${f[3]}"
                prot == null || prot < 0 || prot > 100 -> "wiersz ${i + 1}: bialko ${f[4]}"
                carb == null || carb < 0 || carb > 100 -> "wiersz ${i + 1}: wegle ${f[5]}"
                fat == null || fat < 0 || fat > 100 -> "wiersz ${i + 1}: tluszcz ${f[6]}"
                prot + carb + fat > 105 -> "wiersz ${i + 1}: suma makro ${prot + carb + fat}"
                else -> null
            }
        }.take(5)
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `nazwy nie sa puste ani absurdalnie dlugie`() {
        val zle = wiersze().withIndex()
            .filter { (_, f) -> f[1].isBlank() || f[1].length > 90 }
            .take(5)
            .map { (i, f) -> "wiersz ${i + 1}: \"${f[1].take(40)}\"" }
        assertTrue(zle.joinToString("\n"), zle.isEmpty())
    }

    @Test
    fun `w paczce sa polskie marki`() {
        // Sprawdzian, ze filtr po kraju faktycznie zadzialal, a nie
        // przepuscil losowego przekroju swiata.
        val tekst = wiersze().joinToString("\n") { it[2] }.lowercase()
        val znalezione = listOf("mlekovita", "łowicz", "winiary", "wedel", "sokołów")
            .count { tekst.contains(it) }
        assertTrue("znaleziono $znalezione polskich marek z 5", znalezione >= 3)
    }
}
