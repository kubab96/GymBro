package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Sprawdzenie zestawu produktów podstawowych.
 *
 * To nie jest test logiki - to test **danych wpisanych ręcznie**. Stu
 * pięćdziesięciu wierszy po pięć liczb nie da się przeczytać uważnie dwa razy,
 * a przestawione cyfry w kaloriach kurczaka przekłamują potem każdy dzień,
 * w którym ten kurczak wystąpi. Dlatego liczby pilnuje arytmetyka: kalorie
 * muszą zgadzać się z makroskładnikami.
 */
class FoodSeedTest {

    /**
     * Dopuszczalny rozjazd kalorii z sumą makro.
     *
     * Procentowy, bo 15 % przy orzechach to kilkadziesiąt kalorii, a przy
     * ogórku ułamek. Z podłogą kilkunastu kalorii, bo przy produktach prawie
     * bezkalorycznych - kawa, cola zero - procent przestaje mieć sens:
     * 2 kcal wobec 0.4 kcal to pięciokrotność i zupełnie nic.
     */
    private val tolerancjaProcent = 0.15
    private val tolerancjaKcal = 15.0

    /**
     * Energia ze składu, wzorem europejskim.
     *
     * Białko i węglowodany po 4 kcal/g, tłuszcz 9, **błonnik 2**. Ten ostatni
     * składnik jest tu kluczowy i wynika z konwencji: skoro węglowodany na
     * etykiecie nie zawierają błonnika, to bez osobnego członu energia
     * produktów pełnoziarnistych wychodziłaby za niska.
     */
    private fun energiaZeSkladu(item: FoodSeed.Item): Double =
        item.proteinG * 4 + item.carbsG * 4 + item.fatG * 9 + (item.fiberG ?: 0.0) * 2

    @Test
    fun `kalorie zgadzaja sie ze skladem`() {
        val bledy = FoodSeed.ITEMS
            .filter { !it.alcohol }
            .mapNotNull { item ->
                val policzone = energiaZeSkladu(item)
                val rozjazd = abs(item.kcal - policzone)
                val dopuszczalny = max(item.kcal * tolerancjaProcent, tolerancjaKcal)
                if (rozjazd > dopuszczalny) {
                    "%s: etykieta %.0f, ze skladu %.1f (rozjazd %.1f, dopuszczalny %.1f)"
                        .format(item.name, item.kcal, policzone, rozjazd, dopuszczalny)
                } else null
            }
        assertTrue(bledy.joinToString("\n"), bledy.isEmpty())
    }

    @Test
    fun `wegle nie zawieraja blonnika, bo tak liczy to europejska etykieta`() {
        // Najwazniejszy test w tym pliku i najmniej oczywisty.
        //
        // W Unii deklaracja podaje "weglowodany" BEZ blonnika, w Stanach
        // "Total Carbohydrate" Z blonnikiem. Zestaw jest europejski, bo tak
        // samo podaje dane Open Food Facts dla produktow z Europy.
        //
        // Dowod, ze konwencja sie nie przesunela: istnieje pozycja, w ktorej
        // blonnika jest WIECEJ niz wegli. Po amerykansku to niemozliwe, bo
        // blonnik jest tam czescia wegli. Gdyby ktos "poprawil" te liczby
        // na amerykanskie, ten test padnie.
        val zPrzewaga = FoodSeed.ITEMS.filter { (it.fiberG ?: 0.0) > it.carbsG }
        assertTrue(
            "zadna pozycja nie ma blonnika powyzej wegli - konwencja sie przesunela",
            zPrzewaga.isNotEmpty()
        )

        // Awokado to podrecznikowy przypadek: 1 g wegli i 7 g blonnika.
        val awokado = FoodSeed.ITEMS.first { searchKey(it.name) == "awokado" }
        assertTrue(awokado.carbsG.toString(), awokado.carbsG < 3.0)
        assertEquals(7.0, awokado.fiberG!!, 0.01)
    }

    @Test
    fun `alkohol jest oznaczony, a nie poprawiony`() {
        // Alkohol ma 7 kcal na gram i nie jest makroskladnikiem, wiec dla tych
        // pozycji kalorie NIE moga wynikac ze skladu. Znacznik istnieje wlasnie
        // po to - a test pilnuje, zeby nikt nie "naprawil" tych liczb,
        // dopasowujac je do sumy makro.
        val alkohole = FoodSeed.ITEMS.filter { it.alcohol }
        assertTrue(alkohole.isNotEmpty())
        alkohole.forEach { item ->
            assertTrue(
                "${item.name}: ${energiaZeSkladu(item)}",
                item.kcal > energiaZeSkladu(item) + tolerancjaKcal
            )
        }
    }

    @Test
    fun `nazwy sie nie powtarzaja`() {
        // Powtorzona nazwa to dwa wiersze tego samego produktu na liscie
        // wyboru, bez sposobu, zeby je rozroznic.
        val klucze = FoodSeed.ITEMS.map { searchKey(it.name) }
        val duplikaty = klucze.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue(duplikaty.joinToString(), duplikaty.isEmpty())
        assertEquals(FoodSeed.ITEMS.size, klucze.distinct().size)
    }

    @Test
    fun `sklad miesci sie w stu gramach`() {
        // Bialko, wegle, tluszcz i blonnik razem nie moga wazyc wiecej niz
        // produkt, w ktorym siedza. Taki wiersz znaczy przestawiona kolumne.
        val bledy = FoodSeed.ITEMS.mapNotNull { item ->
            val suma = item.proteinG + item.carbsG + item.fatG + (item.fiberG ?: 0.0)
            if (suma > 100.0) "${item.name}: $suma g na 100 g" else null
        }
        assertTrue(bledy.joinToString("\n"), bledy.isEmpty())
    }

    @Test
    fun `zadna wartosc nie jest ujemna ani absurdalna`() {
        FoodSeed.ITEMS.forEach { item ->
            assertTrue(item.name, item.kcal >= 0.0 && item.kcal <= 900.0)
            assertTrue(item.name, item.proteinG >= 0.0 && item.proteinG <= 90.0)
            assertTrue(item.name, item.carbsG >= 0.0 && item.carbsG <= 100.0)
            assertTrue(item.name, item.fatG >= 0.0 && item.fatG <= 100.0)
            assertTrue(item.name, (item.fiberG ?: 0.0) >= 0.0 && (item.fiberG ?: 0.0) <= 50.0)
            assertTrue(item.name, item.name.isNotBlank())
        }
    }

    @Test
    fun `porcje maja sensowna wage i unikalna nazwe`() {
        FoodSeed.ITEMS.forEach { item ->
            val etykiety = item.portions.map { it.label }
            assertEquals(item.name, etykiety.size, etykiety.distinct().size)
            item.portions.forEach { portion ->
                assertTrue(
                    "${item.name} / ${portion.label}: ${portion.grams}",
                    portion.grams > 0.0 && portion.grams <= 1000.0
                )
            }
        }
    }

    @Test
    fun `nazwy nie niosa dlugich myslnikow`() {
        // Konwencja projektu: krotkie myslniki wszedzie, takze w tekstach,
        // ktore trafiaja do bazy.
        FoodSeed.ITEMS.forEach { item ->
            assertTrue(item.name, !item.name.contains('—'))
            assertTrue(item.name, !item.name.contains('–'))
        }
    }

    @Test
    fun `zestaw pokrywa to, co sie naprawde je`() {
        // Prog arbitralny, ale nie bez sensu: ponizej stu pozycji zestaw
        // przestaje wystarczac na zwykly dzien i wpisywanie wraca do
        // dodawania wlasnych produktow przy kazdym posilku.
        assertTrue(FoodSeed.ITEMS.size.toString(), FoodSeed.ITEMS.size >= 100)

        // Kilka pozycji, bez ktorych zestaw nie ma sensu dla kogos, kto
        // trenuje silowo.
        val klucze = FoodSeed.ITEMS.map { searchKey(it.name) }
        listOf(
            "piers z kurczaka surowa", "ryz bialy suchy", "jajo kurze cale",
            "twarog chudy", "platki owsiane", "oliwa z oliwek", "odzywka bialkowa wpc"
        ).forEach { assertTrue(it, klucze.contains(it)) }
    }

    @Test
    fun `surowiec i potrawa to dwie rozne pozycje`() {
        // Najwazniejszy podzial w calym zestawie. 100 g suchego ryzu to okolo
        // 250 g ugotowanego, wiec "ryz" bez dopisku znaczy dwie rzeczy
        // rozniace sie dwuipolkrotnie - a wazy sie to, co sie naklada.
        val klucze = FoodSeed.ITEMS.map { searchKey(it.name) }
        listOf(
            "ziemniaki" to "ziemniaki gotowane",
            "ryz bialy suchy" to "ryz bialy ugotowany",
            "makaron pszenny suchy" to "makaron ugotowany"
        ).forEach { (surowiec, potrawa) ->
            assertTrue(surowiec, klucze.contains(surowiec))
            assertTrue(potrawa, klucze.contains(potrawa))
        }

        // I najwazniejsze: potrawa musi miec MNIEJ kalorii na 100 g niz
        // surowiec, bo woda z gotowania wazy, a nie dokłada energii.
        // Odwrotny wynik znaczylby, ze ktos wpisal wartosci surowca.
        val suchy = FoodSeed.ITEMS.first { searchKey(it.name) == "ryz bialy suchy" }
        val ugotowany = FoodSeed.ITEMS.first { searchKey(it.name) == "ryz bialy ugotowany" }
        assertTrue(
            "${suchy.kcal} vs ${ugotowany.kcal}",
            ugotowany.kcal < suchy.kcal / 2
        )
    }

    @Test
    fun `smazenie i panierka dokladaja tluszczu`() {
        // Konwencja z naglowka zestawu: smazenie okolo 5-7 g tluszczu
        // na 100 g, panierowanie 10-13 g. Test pilnuje kierunku, nie
        // dokladnej liczby - te sa i tak umowne.
        val gotowane = FoodSeed.ITEMS.first { searchKey(it.name) == "ziemniaki gotowane" }
        val smazone = FoodSeed.ITEMS.first { searchKey(it.name) == "ziemniaki smazone" }
        assertTrue(smazone.fatG > gotowane.fatG + 5.0)

        val grillowana = FoodSeed.ITEMS.first { searchKey(it.name) == "piers z kurczaka grillowana" }
        val panierowana = FoodSeed.ITEMS.first { searchKey(it.name) == "piers z kurczaka panierowana" }
        assertTrue(panierowana.fatG > grillowana.fatG + 5.0)
        // Panierka to tez wegle, ktorych w samym miesie nie ma.
        assertTrue(panierowana.carbsG > 5.0)
        assertEquals(0.0, grillowana.carbsG, 0.0)
    }

    @Test
    fun `kazda pozycja zestawu ma porcje`() {
        // Kromka, lyzka, sztuka - bez nich kazdy wpis wymaga wagi kuchennej.
        //
        // Wczesniej ten test sprawdzal recznie wybrana liste jedenastu
        // produktow, "ktorych nikt nie wazy". To bylo obejscie, nie regula:
        // porcje mialo 248 pozycji z 311, a wyboru bronilo tylko to, ze
        // reszty nikt jeszcze nie uzupelnil. Teraz maja wszystkie, wiec
        // regula brzmi tak, jak powinna byla od poczatku - i nowa pozycja
        // bez porcji zatrzyma sie tutaj, zamiast trafic do zestawu.
        val bez = FoodSeed.ITEMS.filter { it.portions.isEmpty() }.map { it.name }
        assertTrue("bez porcji: ${bez.joinToString()}", bez.isEmpty())
    }
}
