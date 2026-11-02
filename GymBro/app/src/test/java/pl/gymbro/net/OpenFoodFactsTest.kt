package pl.gymbro.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.FoodSource

/**
 * Mapowanie odpowiedzi Open Food Facts.
 *
 * Wszystkie przypadki stoją na **prawdziwych odpowiedziach serwera**,
 * zapisanych w `src/test/resources/off`. Wymyślony JSON sprawdzałby tylko to,
 * czy kod czyta pola, które sam sobie wymyśliłem - a cała trudność tego
 * mapowania bierze się z tego, jak te dane naprawdę wyglądają: nazwa bywa
 * pusta zamiast nieobecna, marka bywa listą trzech firm, a błonnik raz jest
 * zadeklarowanym zerem, a raz go nie ma wcale.
 *
 * Przypadki brzegowe, których nie udało się znaleźć w naturze, powstają przez
 * podmianę w prawdziwej odpowiedzi - zaznaczone w komentarzu przy każdym.
 */
class OpenFoodFactsTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/off/$name.json")!!
            .bufferedReader()
            .use { it.readText() }

    private fun found(name: String, barcode: String = "000"): OpenFoodFacts.Lookup.Found {
        val wynik = OpenFoodFacts.parse(barcode, fixture(name))
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Found)
        return wynik as OpenFoodFacts.Lookup.Found
    }

    // ---- Pełne mapowanie -------------------------------------------------

    @Test
    fun `migdaly z Lidla, wszystkie pola`() {
        val food = found("migdaly", "20724696").food

        assertEquals("Migdały łuskane", food.name)
        assertEquals("Alesto", food.brand)
        assertEquals("20724696", food.barcode)
        assertEquals(FoodSource.OFF, food.source)

        assertEquals(621.0, food.per100.kcal, 0.01)
        assertEquals(24.5, food.per100.proteinG, 0.01)
        assertEquals(4.8, food.per100.carbsG, 0.01)
        assertEquals(53.3, food.per100.fatG, 0.01)
        assertEquals(12.1, food.per100.fiberG!!, 0.01)
        assertEquals(4.3, food.per100.saturatedG!!, 0.01)
        assertEquals(0.01, food.per100.saltG!!, 0.001)

        // serving_quantity i product_quantity, nie teksty obok nich
        assertEquals(30.0, food.servingG!!, 0.01)
        assertEquals(200.0, food.packageG!!, 0.01)
    }

    @Test
    fun `migdaly potwierdzaja konwencje europejska`() {
        // 4.8 g weglowodanow przy 12.1 g blonnika. Po amerykansku byloby
        // to niemozliwe, bo tam blonnik jest czescia weglowodanow - a to
        // dokladnie ta konwencja, ktora przyjeto w zestawie wlasnym.
        // Gdyby Open Food Facts kiedys przeszlo na liczenie amerykanskie,
        // ten test to pokaze.
        val food = found("migdaly").food
        assertTrue(
            "${food.per100.carbsG} vs ${food.per100.fiberG}",
            food.per100.fiberG!! > food.per100.carbsG
        )
    }

    // ---- Nazwa -----------------------------------------------------------

    @Test
    fun `nazwa polska wygrywa z miedzynarodowa`() {
        // W pliku sa obie: "Amandes decortiquees" i "Migdaly luskane".
        // Francuska nazwa na liscie wynikow byla czyms, czego nie da sie
        // znalezc szukajac po polsku.
        assertEquals("Migdały łuskane", found("migdaly").food.name)
        assertEquals("Coca-cola", found("cola").food.name)
    }

    @Test
    fun `pusta nazwa polska nie przykrywa miedzynarodowej`() {
        // Open Food Facts zwraca dla tego produktu product_name_pl jako
        // pusty napis, a nie pomija pole. Rozroznienie "puste" od "brak"
        // jest tu rzecza, ktora latwo przeoczyc i ktora zostawia produkt
        // bez nazwy.
        assertEquals("Oat No Sugars", found("napoj-owsiany").food.name)
    }

    @Test
    fun `marka to pierwsza pozycja z listy`() {
        // Pole "brands" bywa lista producenta, dystrybutora i marki wlasnej.
        val food = found("cola").food
        assertNotNull(food.brand)
        assertTrue(food.brand!!, !food.brand!!.contains(','))
    }

    // ---- Zero kontra brak danych -----------------------------------------

    @Test
    fun `zadeklarowane zero zostaje zerem`() {
        // Coca-Cola ma w deklaracji 0 g blonnika i 0 g tluszczu. To jest
        // informacja, nie jej brak - i musi przejsc jako zero.
        val food = found("cola").food
        assertEquals(0.0, food.per100.fiberG!!, 0.0)
        assertEquals(0.0, food.per100.fatG, 0.0)
        assertEquals(0.0, food.per100.proteinG, 0.0)
    }

    @Test
    fun `brak pola zostaje brakiem`() {
        // Z prawdziwej odpowiedzi wyciete pole blonnika - tak wyglada
        // wiekszosc produktow, ktorych nikt nie uzupelnil do konca.
        val bezBlonnika = fixture("migdaly").replace("\"fiber_100g\":12.1,", "")
        val wynik = OpenFoodFacts.parse("20724696", bezBlonnika)
        val food = (wynik as OpenFoodFacts.Lookup.Found).food
        assertNull(food.per100.fiberG)
        // reszta ma przejsc nietknieta
        assertEquals(621.0, food.per100.kcal, 0.01)
    }

    // ---- Energia ---------------------------------------------------------

    @Test
    fun `brak kilokalorii przelicza sie z kilodzuli`() {
        // Czesc produktow ma tylko energie w kJ. 2567 / 4.184 = 613.5 -
        // blisko zadeklarowanych 621, bo producent zaokraglil inaczej.
        val bezKcal = fixture("migdaly").replace("\"energy-kcal_100g\":621,", "")
        val food = (OpenFoodFacts.parse("20724696", bezKcal) as OpenFoodFacts.Lookup.Found).food
        assertEquals(613.5, food.per100.kcal, 0.5)
    }

    @Test
    fun `kilodzule wpisane w pole kilokalorii sa odrzucane`() {
        // Najczestszy blad w danych spolecznosciowych. 2567 kcal na 100 g
        // to wiecej niz czysty tluszcz, wiec takiej liczby nie wolno
        // wpuscic do katalogu - raz zapisana wracalaby przy kazdym wpisie.
        val zKJ = fixture("migdaly").replace("\"energy-kcal_100g\":621", "\"energy-kcal_100g\":2567")
        val wynik = OpenFoodFacts.parse("20724696", zKJ)
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Incomplete)
        assertTrue(
            (wynik as OpenFoodFacts.Lookup.Incomplete).reason,
            wynik.reason.contains("poza skalą")
        )
    }

    // ---- Niekompletne ----------------------------------------------------

    @Test
    fun `brak bialka to nie jest zero bialka`() {
        // Produkt z samymi kaloriami da sie wpisac szybkim wpisem.
        // Wpuszczony do katalogu jako "0 g bialka" klamalby przy kazdym
        // kolejnym uzyciu, a bialko jest pierwsza liczba, na ktora patrzysz.
        val bezBialka = fixture("migdaly").replace("\"proteins_100g\":24.5,", "")
        val wynik = OpenFoodFacts.parse("20724696", bezBialka)
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Incomplete)
    }

    @Test
    fun `niekompletny produkt oddaje nazwe do przepisania`() {
        // Zeby formularz wlasnego produktu dalo sie wypelnic bez
        // przepisywania nazwy z opakowania.
        val bezBialka = fixture("migdaly").replace("\"proteins_100g\":24.5,", "")
        val wynik = OpenFoodFacts.parse("20724696", bezBialka) as OpenFoodFacts.Lookup.Incomplete
        assertEquals("Migdały łuskane", wynik.name)
        assertEquals("Alesto", wynik.brand)
    }

    // ---- Nieznane i zepsute ----------------------------------------------

    @Test
    fun `nieznany kod to nie jest blad`() {
        val wynik = OpenFoodFacts.parse("0000000000000", fixture("nieznaleziony"))
        assertEquals(OpenFoodFacts.Lookup.NotFound, wynik)
    }

    @Test
    fun `nieczytelna odpowiedz to blad, nie brak produktu`() {
        // "Nie znaleziono" i "cos poszlo nie tak" prowadza do roznych
        // dzialan, wiec nie moga wygladac tak samo.
        val wynik = OpenFoodFacts.parse("20724696", "<html>Page temporarily unavailable</html>")
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Failed)
    }

    // ---- Kod odpowiedzi HTTP kontra jej tresc ----------------------------

    @Test
    fun `nieznany produkt przychodzi z kodem 404 i to nie jest awaria`() {
        // Usterka znaleziona dopiero na telefonie, przy prawdziwym skanie.
        //
        // Open Food Facts odpowiada na nieznany kod kreskowy **404**, ale
        // z poprawnym ciałem {"status":0,"status_verbose":"product not
        // found"}. Klient, ktory odrzuca wszystko poza 200, melduje wtedy
        // "serwer odpowiedzial 404" - czyli awarie - zamiast zwyklego braku
        // produktu. To dwie rozne wiadomosci: jedna mowi "wpisz recznie",
        // druga "sprobuj pozniej".
        val wynik = OpenFoodFacts.interpret("5005807920730", 404, fixture("nieznaleziony"))
        assertEquals(OpenFoodFacts.Lookup.NotFound, wynik)
    }

    @Test
    fun `czytelna tresc wygrywa z kodem odpowiedzi`() {
        // Nawet gdyby serwer oddal produkt z dziwnym kodem HTTP, dane sa
        // danymi. Decyduje tresc, nie naglowek.
        val wynik = OpenFoodFacts.interpret("20724696", 404, fixture("migdaly"))
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Found)
    }

    @Test
    fun `awaria serwera zostaje awaria`() {
        // Kod bledu plus nieczytelna tresc to naprawde awaria - i wtedy
        // numer jest uzyteczna informacja.
        val wynik = OpenFoodFacts.interpret("20724696", 503, "<html>Service Unavailable</html>")
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Failed)
        assertTrue(
            (wynik as OpenFoodFacts.Lookup.Failed).reason,
            wynik.reason.contains("503")
        )
    }

    @Test
    fun `pusta odpowiedz to awaria, nie brak produktu`() {
        val wynik = OpenFoodFacts.interpret("20724696", 200, "")
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Failed)
    }

    @Test
    fun `zepsuta tresc przy kodzie 200 to zepsuta odpowiedz serwera`() {
        // Tak wyglada "Page temporarily unavailable" z Open Food Facts:
        // kod 200, a w srodku strona HTML.
        val wynik = OpenFoodFacts.interpret("20724696", 200, "<html>Page temporarily unavailable</html>")
        assertTrue(wynik.toString(), wynik is OpenFoodFacts.Lookup.Failed)
        assertTrue(
            (wynik as OpenFoodFacts.Lookup.Failed).reason,
            wynik.reason.contains("nieczytelna")
        )
    }

    @Test
    fun `za krotki kod nie idzie do sieci`() {
        assertEquals(OpenFoodFacts.Lookup.NotFound, OpenFoodFacts.fetch("123"))
    }

    @Test
    fun `naglowek przedstawia aplikacje i zostawia kontakt`() {
        // Open Food Facts o to prosi. Kontakt ma prowadzic do autora
        // aplikacji, a nie do osoby, ktora ja ma zainstalowana.
        assertTrue(OpenFoodFacts.USER_AGENT, OpenFoodFacts.USER_AGENT.startsWith("GymBro/"))
        assertTrue(OpenFoodFacts.USER_AGENT, OpenFoodFacts.USER_AGENT.contains("github.com"))
        assertTrue(OpenFoodFacts.USER_AGENT, !OpenFoodFacts.USER_AGENT.contains("@"))
    }
}
