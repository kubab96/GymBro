package pl.gymbro.net

import org.json.JSONObject
import pl.gymbro.data.Food
import pl.gymbro.data.FoodSource
import pl.gymbro.data.Nutrients
import pl.gymbro.data.roundGram
import pl.gymbro.data.roundKcal
import java.net.HttpURLConnection
import java.net.URL

/**
 * Odczyt produktu z Open Food Facts po kodzie kreskowym.
 *
 * Licencja danych: Open Database License (ODbL). Korzystanie wymaga uznania
 * autorstwa - stąd linijka o źródle w Profilu.
 *
 * Projekt zapytania wynika z tego, o co prosi sam projekt: **jedno wywołanie
 * odpowiada jednemu prawdziwemu skanowi.** Masowego pobierania nie robimy tędy
 * (od tego są nocne zrzuty), a każdy odczyt trafia do lokalnego katalogu, więc
 * drugi skan tego samego produktu nie rusza już sieci.
 *
 * Wyszukiwanie tekstowe celowo **nie** idzie przez sieć. Przy sprawdzaniu
 * endpoint `search` co drugie zapytanie zwracał „Page temporarily
 * unavailable", a odczyt po kodzie działał za każdym razem - stąd podział:
 * szukamy lokalnie, przez sieć pytamy tylko o konkretny kod.
 */
object OpenFoodFacts {

    /**
     * Open Food Facts prosi, żeby aplikacja się przedstawiła i zostawiła
     * kontakt. Zostawiamy adres repozytorium, a nie adres użytkownika -
     * kontakt ma prowadzić do autora aplikacji, nie do osoby, która ją ma
     * zainstalowaną.
     */
    const val USER_AGENT = "GymBro/0.1 (Android; https://github.com/kubab96/GymBro)"

    private const val BASE = "https://world.openfoodfacts.org/api/v2/product/"

    /**
     * Pola, o które pytamy.
     *
     * Bez tej listy odpowiedź dla jednego produktu ma kilkadziesiąt kilobajtów
     * - alergeny, kategorie, historia edycji, adresy zdjęć. Z nią mieści się
     * w dwóch, co przy skanowaniu w sklepie na słabym zasięgu jest różnicą
     * między „od razu" a „zaraz".
     */
    private const val FIELDS =
        "code,product_name,product_name_pl,brands,quantity,product_quantity," +
            "serving_size,serving_quantity,nutriments"

    /** Przelicznik kilodżuli na kilokalorie. */
    private const val KJ_PER_KCAL = 4.184

    private const val TIMEOUT_MS = 8000

    /** Granice, poza którymi uznajemy dane za błędne, a nie nietypowe. */
    private const val MAX_KCAL = 900.0
    private const val MAX_MACRO_G = 100.0

    /**
     * Wynik zapytania.
     *
     * Cztery przypadki, bo użytkownik musi zobaczyć cztery różne komunikaty.
     * „Nie znaleziono" i „nie ma zasięgu" wyglądają tak samo tylko w kodzie,
     * który nie rozróżnia błędów - a prowadzą do zupełnie innych działań.
     */
    sealed interface Lookup {
        /** Produkt znaleziony i kompletny. */
        data class Found(val food: Food) : Lookup

        /** Serwer odpowiedział, ale tego kodu nie zna. */
        data object NotFound : Lookup

        /**
         * Produkt jest w bazie, ale brakuje mu danych, bez których wpis nie
         * ma sensu. [name] i [brand] podajemy mimo to - posłużą do wypełnienia
         * formularza własnego produktu, żeby nie trzeba było przepisywać
         * nazwy z opakowania.
         */
        data class Incomplete(
            val reason: String,
            val name: String? = null,
            val brand: String? = null
        ) : Lookup

        /** Nie udało się zapytać: brak sieci, błąd serwera, nieczytelna odpowiedź. */
        data class Failed(val reason: String) : Lookup
    }

    /**
     * Pyta serwer o produkt. Wołać z wątku roboczego.
     *
     * Zwykły [HttpURLConnection], bez dodatkowej biblioteki - tak samo jak
     * reszta sieci w tym projekcie. Jedno zapytanie na skan nie jest powodem,
     * żeby wciągać klienta HTTP z własnym cyklem życia.
     */
    fun fetch(barcode: String, timeoutMs: Int = TIMEOUT_MS): Lookup {
        val code = barcode.filter { it.isDigit() }
        if (code.length < 6) return Lookup.NotFound

        val connection = runCatching {
            (URL("$BASE$code?fields=$FIELDS").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
        }.getOrElse { return Lookup.Failed("nie udało się połączyć") }

        return try {
            val status = connection.responseCode
            // Przy kodzie błędu treść leży w errorStream - inputStream wtedy
            // rzuca wyjątkiem i cała odpowiedź przepadłaby niewykorzystana.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            interpret(code, status, body)
        } catch (e: Exception) {
            Lookup.Failed(e.message ?: "brak połączenia")
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Co znaczy ta para: kod odpowiedzi HTTP i jej treść.
     *
     * Osobno od [fetch], bo tu siedziała usterka znaleziona dopiero na
     * telefonie. **Open Food Facts odpowiada na nieznany produkt kodem 404**,
     * ale z poprawnym ciałem `{"status":0,"status_verbose":"product not
     * found"}`. Klient, który odrzuca wszystko poza 200, melduje wtedy awarię
     * serwera zamiast zwykłego braku produktu - a to dwie różne wiadomości:
     * jedna mówi „wpisz ręcznie", druga „spróbuj później".
     *
     * Stąd zasada: **jeśli przyszła czytelna treść, decyduje treść, nie kod
     * odpowiedzi.** Kod HTTP rozstrzyga tylko wtedy, gdy z treści nie da się
     * nic wyczytać - wtedy 500 ze stroną błędu to naprawdę awaria.
     */
    internal fun interpret(code: String, status: Int, body: String?): Lookup {
        if (body.isNullOrBlank()) return Lookup.Failed("serwer odpowiedział $status")

        val parsed = parse(code, body)
        if (parsed !is Lookup.Failed) return parsed

        // Treść nieczytelna. Przy 200 to zepsuta odpowiedź serwera
        // (zdarza się strona „Page temporarily unavailable"), przy innym
        // kodzie - zwykła awaria, i wtedy numer jest użyteczną informacją.
        return if (status in 200..299) parsed
        else Lookup.Failed("serwer odpowiedział $status")
    }

    /**
     * Zamienia odpowiedź serwera na produkt.
     *
     * Osobno od [fetch] i bez dotykania sieci, żeby dało się to sprawdzić na
     * prawdziwych odpowiedziach zapisanych w `src/test/resources/off`.
     * Mapowanie jest tu najbardziej kruchą częścią - dane są społecznościowe,
     * więc każde pole bywa puste, tekstowe albo absurdalne.
     */
    fun parse(barcode: String, body: String): Lookup {
        val root = runCatching { JSONObject(body) }
            .getOrElse { return Lookup.Failed("nieczytelna odpowiedź") }

        if (root.optInt("status", 0) != 1) return Lookup.NotFound
        val product = root.optJSONObject("product") ?: return Lookup.NotFound

        // Nazwa polska przed międzynarodową: „Migdały łuskane" zamiast
        // „Amandes décortiquées" - ten sam produkt z Lidla.
        val name = firstNonBlank(
            product.optString("product_name_pl"),
            product.optString("product_name")
        ) ?: return Lookup.Incomplete("produkt bez nazwy")

        val brand = firstNonBlank(product.optString("brands"))
            // Bywa listą producenta, dystrybutora i marki własnej. Bierzemy
            // pierwszą - to zwykle ta z przodu opakowania.
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val n = product.optJSONObject("nutriments")
            ?: return Lookup.Incomplete("brak wartości odżywczych", name, brand)

        val kcal = number(n, "energy-kcal_100g")
            ?: number(n, "energy-kj_100g")?.div(KJ_PER_KCAL)
            ?: number(n, "energy_100g")?.div(KJ_PER_KCAL)
            ?: return Lookup.Incomplete("brak kalorii na 100 g", name, brand)

        // Makro jest wymagane w całości. Produkt z samymi kaloriami da się
        // wpisać szybkim wpisem; wpuszczony do katalogu jako „0 g białka"
        // kłamałby przy każdym kolejnym użyciu, a białko jest pierwszą
        // liczbą, na którą patrzysz.
        val protein = number(n, "proteins_100g")
            ?: return Lookup.Incomplete("brak białka", name, brand)
        val carbs = number(n, "carbohydrates_100g")
            ?: return Lookup.Incomplete("brak węglowodanów", name, brand)
        val fat = number(n, "fat_100g")
            ?: return Lookup.Incomplete("brak tłuszczu", name, brand)

        val per100 = Nutrients(
            kcal = roundKcal(kcal),
            proteinG = roundGram(protein),
            carbsG = roundGram(carbs),
            fatG = roundGram(fat),
            // Opcjonalne zostają puste, gdy pola nie ma. Zero wpisane tam,
            // gdzie nikt nic nie podał, byłoby twierdzeniem - a Coca-Cola
            // ma zadeklarowane zero błonnika i to jest co innego.
            fiberG = number(n, "fiber_100g")?.let(::roundGram),
            sugarsG = number(n, "sugars_100g")?.let(::roundGram),
            saturatedG = number(n, "saturated-fat_100g")?.let(::roundGram),
            saltG = number(n, "salt_100g")?.let(::roundGram)
        )

        implausible(per100)?.let { return Lookup.Incomplete(it, name, brand) }

        return Lookup.Found(
            Food(
                barcode = barcode,
                name = name,
                brand = brand,
                per100 = per100,
                // `serving_quantity` i `product_quantity` podaje serwer liczbą,
                // podczas gdy `serving_size` to tekst w rodzaju „1 portion
                // (100 ml)". Dla płynów są to mililitry, nie gramy - przy
                // gęstości bliskiej jedynce bierzemy je wprost, co przy napoju
                // daje kilka procent błędu i jest lepsze niż brak porcji.
                servingG = number(product, "serving_quantity")?.takeIf { it > 0 },
                packageG = number(product, "product_quantity")?.takeIf { it > 0 },
                source = FoodSource.OFF,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Czy wartości w ogóle mogą być prawdziwe; null, gdy mogą.
     *
     * Dane są społecznościowe, więc trafia się kilodżul wpisany w pole
     * kilokalorii - migdały miałyby wtedy 2567 kcal na 100 g. Granica 900
     * wyłapuje dokładnie ten przypadek, bo nic jadalnego nie przekracza
     * czystego tłuszczu.
     *
     * Zgodności kalorii ze składem **nie** sprawdzamy: piwo i wino mają kod
     * kreskowy i prawo do kalorii, które nie wynikają z makroskładników.
     */
    private fun implausible(n: Nutrients): String? = when {
        n.kcal < 0 || n.kcal > MAX_KCAL -> "kalorie poza skalą (${n.kcal.toInt()} na 100 g)"
        n.proteinG < 0 || n.proteinG > MAX_MACRO_G -> "białko poza skalą"
        n.carbsG < 0 || n.carbsG > MAX_MACRO_G -> "węglowodany poza skalą"
        n.fatG < 0 || n.fatG > MAX_MACRO_G -> "tłuszcz poza skalą"
        n.proteinG + n.carbsG + n.fatG > 105.0 -> "składniki nie mieszczą się w 100 g"
        else -> null
    }

    /**
     * Liczba spod klucza; null, gdy klucza nie ma albo nie da się go odczytać.
     *
     * Rozróżnienie braku od zera jest tu całą rzeczą: `optDouble` z wartością
     * domyślną zwróciłby zero dla pola, którego nie ma, i Coca-Cola bez danych
     * o błonniku wyglądałaby tak samo jak Coca-Cola z zadeklarowanym zerem.
     */
    private fun number(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
