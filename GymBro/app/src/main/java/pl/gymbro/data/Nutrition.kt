package pl.gymbro.data

/**
 * Model jedzenia: katalog produktów, dziennik, cele i oszacowanie wydatku.
 *
 * Projekt i uzasadnienie liczb: `docs/kalorie.md`.
 *
 * Tak samo jak [Models.kt][Exercise], te typy nie wiedzą nic o SQLite ani
 * o Compose. Cała logika z pakietu `nutrition` operuje wyłącznie na nich.
 */

/**
 * Posiłek w ciągu dnia.
 *
 * Pięć stałych plus przekąski, a nie dowolne nazwy. Krótka, zamknięta lista
 * to krótsze wybieranie przy każdym wpisie - a tych jest kilkanaście dziennie.
 * Dodatkowo porównanie śniadań między tygodniami ma sens tylko wtedy, gdy
 * „śniadanie" znaczy to samo w każdym dniu.
 */
enum class Meal(val label: String) {
    BREAKFAST("Śniadanie"),
    SECOND_BREAKFAST("Drugie śniadanie"),
    LUNCH("Obiad"),
    AFTERNOON("Podwieczorek"),
    DINNER("Kolacja"),
    SNACKS("Przekąski");

    companion object {
        fun fromName(s: String?): Meal = entries.firstOrNull { it.name == s } ?: SNACKS
    }
}

/**
 * Skąd pochodzi wpis w katalogu.
 *
 * Ma znaczenie przy kolejności wyszukiwania (własne przed obcymi) i przy
 * odświeżaniu: wiersz z Open Food Facts wolno nadpisać świeższymi danymi
 * z sieci, własny produkt - nigdy.
 */
enum class FoodSource {
    /** Open Food Facts, po kodzie kreskowym albo z paczki offline. */
    OFF,

    /** Zestaw produktów podstawowych dostarczony z aplikacją. */
    SEED,

    /**
     * Tabela wartości odżywczych opublikowana przez sklep na własnej stronie.
     *
     * Osobne źródło, a nie [SEED], z dwóch powodów. Pierwszy: proweniencja
     * jest inna - [SEED] to pozycje wpisane ręcznie, po jednej, z konwencjami
     * opisanymi w `docs/kalorie.md`, a to jest wsad maszynowy. Drugi: wsad
     * maszynowy musi dać się wycofać w całości, bo poprawka w czytniku strony
     * ma usunąć to, co wpuściła jego poprzednia wersja - dokładnie ta lekcja,
     * która kazała [OFF] kasować paczkę przed ponownym wczytaniem.
     *
     * Ufamy temu więcej niż paczce [OFF]: wartości podaje sprzedawca dla
     * swojego asortymentu, a nie przypadkowy współautor otwartej bazy.
     */
    SHOP,

    /** Produkt wpisany ręcznie. */
    OWN,

    /** Wiersz wyliczony z przepisu - patrz [Recipe]. */
    RECIPE;

    companion object {
        fun fromName(s: String?): FoodSource = entries.firstOrNull { it.name == s } ?: OWN
    }
}

/**
 * Wartości odżywcze - albo na 100 g produktu, albo za zjedzoną ilość.
 *
 * Cztery pierwsze są wymagane: produkt bez kalorii i makro jest w dzienniku
 * bezużyteczny. Cztery kolejne są opcjonalne i **naprawdę opcjonalne, czyli
 * `null`, a nie zero** - Open Food Facts wypełnia je nierówno, a zero znaczy
 * „produkt tego nie zawiera", co jest zupełnie inną informacją niż „nie
 * wiadomo". Zero gramów błonnika to twierdzenie, i zwykle fałszywe.
 *
 * **[carbsG] nie zawiera błonnika.** Tak podaje węglowodany etykieta
 * europejska i tak zapisuje je Open Food Facts dla produktów z Europy:
 * „węglowodany" i „błonnik" to dwie osobne pozycje. W Stanach `Total
 * Carbohydrate` obejmuje błonnik, więc dane z amerykańskich źródeł trzeba
 * przy wczytywaniu przeliczyć. Pomylenie tego podwaja węglowodany w migdałach
 * i w awokado. Z tej samej konwencji wynika, że energia ze składu liczy się
 * jako 4/4/9 plus **2 kcal za gram błonnika** - bez tego członu produkty
 * pełnoziarniste wychodzą za lekkie.
 */
data class Nutrients(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saturatedG: Double? = null,
    val saltG: Double? = null
) {
    /**
     * Suma dwóch wpisów, zachowująca różnicę między zerem i brakiem danych.
     *
     * Reguła dla pól opcjonalnych: `null + null = null`, `null + 5 = 5`.
     * Dzięki temu dzień, w którym żaden produkt nie miał danych o błonniku,
     * pokazuje brak danych, a dzień z jednym takim produktem pokazuje sumę
     * częściową. Gdyby brak traktować jak zero, oba dni wyglądałyby tak samo
     * i oba kłamałyby - pierwszy o zerze, drugi o pełnej sumie.
     */
    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        kcal = roundKcal(kcal + other.kcal),
        proteinG = roundGram(proteinG + other.proteinG),
        carbsG = roundGram(carbsG + other.carbsG),
        fatG = roundGram(fatG + other.fatG),
        fiberG = addOptional(fiberG, other.fiberG),
        sugarsG = addOptional(sugarsG, other.sugarsG),
        saturatedG = addOptional(saturatedG, other.saturatedG),
        saltG = addOptional(saltG, other.saltG)
    )

    /**
     * Przeskalowanie - z wartości na 100 g na wartości za podaną ilość.
     *
     * Brak danych skaluje się na brak danych: nieznany błonnik w 100 g
     * zostaje nieznanym błonnikiem w 36 g.
     */
    fun scaled(factor: Double): Nutrients = Nutrients(
        kcal = roundKcal(kcal * factor),
        proteinG = roundGram(proteinG * factor),
        carbsG = roundGram(carbsG * factor),
        fatG = roundGram(fatG * factor),
        fiberG = fiberG?.let { roundGram(it * factor) },
        sugarsG = sugarsG?.let { roundGram(it * factor) },
        saturatedG = saturatedG?.let { roundGram(it * factor) },
        saltG = saltG?.let { roundGram(it * factor) }
    )

    /**
     * Kalorie policzone z makroskładników, współczynnikami Atwatera.
     *
     * Służy do wyłapywania wpisów, w których coś się nie zgadza: etykiety
     * bywają błędnie przepisane, a w Open Food Facts zdarza się energia
     * w kilodżulach wpisana w pole kilokalorii. Rozjazd powyżej kilkunastu
     * procent to sygnał, nie wynik.
     */
    val kcalFromMacros: Double
        get() = roundKcal(proteinG * 4 + carbsG * 4 + fatG * 9)

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0)

        private fun addOptional(a: Double?, b: Double?): Double? =
            if (a == null && b == null) null else roundGram((a ?: 0.0) + (b ?: 0.0))
    }
}

/**
 * Porcja nazwana - „kromka", „łyżka", „sztuka".
 *
 * To jest rzecz, która decyduje o szybkości wpisywania. Nikt nie wie, ile
 * waży kromka chleba, a każdy wie, że zjadł dwie. Bez porcji każdy wpis
 * wymaga wagi kuchennej albo zgadywania.
 */
data class FoodPortion(
    val id: Long = 0,
    val foodId: Long = 0,
    val label: String,
    val grams: Double,
    val position: Int = 0
)

/**
 * Produkt w katalogu.
 *
 * Wartości zawsze na 100 g, niezależnie od tego, jak podawało je źródło.
 * Jedna jednostka w całej bazie, bo przeliczanie w kilku miejscach kończy
 * się dwoma różnymi wynikami dla tego samego produktu.
 *
 * [servingG] i [packageG] to podpowiedzi z etykiety: ile waży porcja
 * producenta i ile całe opakowanie. Jedno i drugie bywa puste.
 */
data class Food(
    val id: Long = 0,

    /** Kod kreskowy; puste dla produktów własnych i przepisów. */
    val barcode: String? = null,

    val name: String,
    val brand: String? = null,
    val per100: Nutrients,
    val servingG: Double? = null,
    val packageG: Double? = null,
    val source: FoodSource = FoodSource.OWN,

    /** Kiedy dane przyszły ze źródła - decyduje o odświeżaniu. */
    val updatedAt: Long = 0,

    val portions: List<FoodPortion> = emptyList()
) {
    /** Nazwa z marką, jeśli marka jest znana - to, co widać na liście wyników. */
    val fullName: String
        get() = if (brand.isNullOrBlank()) name else "$name, $brand"

    /** Czy dane wolno nadpisać świeższą wersją ze źródła. */
    val isRefreshable: Boolean get() = source == FoodSource.OFF
}

/**
 * Jeden wpis w dzienniku.
 *
 * Trzyma **gotowe wartości za zjedzoną ilość**, a nie samo odwołanie do
 * produktu. To ta sama decyzja, co przy [StoredAdvice]: produkt w Open Food
 * Facts zmieni się, gdy ktoś poprawi etykietę albo producent zmieni
 * recepturę - a wtorek z marca ma zostać wtorkiem z marca. Historia, która
 * zmienia się sama, nie jest historią.
 *
 * [foodId] i [grams] mogą być puste: szybki wpis samych kalorii nie ma
 * produktu ani gramatury, bo obiad w restauracji nie ma kodu kreskowego,
 * a udawanie, że ma, jest najszybszym sposobem na porzucenie dziennika.
 */
data class FoodLogEntry(
    val id: Long = 0,

    /** Numer dnia od epoki, tak jak [DailyMetric.day]. */
    val day: Long,

    val meal: Meal,
    val foodId: Long? = null,

    /** Nazwa przepisana w chwili wpisu - zostaje, nawet gdy produkt zniknie. */
    val name: String,

    val grams: Double? = null,
    val nutrients: Nutrients,

    /** Znacznik czasu - wyłącznie do kolejności wewnątrz posiłku. */
    val at: Long = 0
) {
    val isQuickEntry: Boolean get() = foodId == null && grams == null
}

/**
 * Cel na dobę, obowiązujący od danego dnia.
 *
 * Historia, nie jeden wiersz: patrząc na marzec chcesz wiedzieć, do jakiej
 * liczby wtedy celowałeś, a nie do jakiej celujesz dzisiaj. Bez tego każda
 * zmiana celu przepisywałaby przeszłość.
 *
 * [manual] oznacza cel wpisany ręcznie - taki nie jest nadpisywany przez
 * kolejne oszacowanie wydatku.
 */
data class NutritionTarget(
    val fromDay: Long,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val manual: Boolean = false
) {
    /** Udział makroskładników w kaloriach - do belek na ekranie. */
    val proteinKcal: Double get() = proteinG * 4
    val carbsKcal: Double get() = carbsG * 4
    val fatKcal: Double get() = fatG * 9
}

/** Skąd wzięła się liczba wydatku - patrz `docs/kalorie.md`, rozdział 3. */
enum class TdeeMethod {
    /** Sam wzór; danych z ważeń i dziennika jeszcze nie starcza. */
    FORMULA,

    /** Wzór i pomiar zmieszane wagami odwrotnymi do kwadratu niepewności. */
    BLENDED,

    /** Pomiar z bilansu energii; wzór ma już znikomy udział. */
    MEASURED;

    companion object {
        fun fromName(s: String?): TdeeMethod = entries.firstOrNull { it.name == s } ?: FORMULA
    }
}

/**
 * Zapisane oszacowanie wydatku na dany dzień.
 *
 * Zapisujemy je, zamiast liczyć na bieżąco i zapominać, bo jedna liczba nic
 * nie mówi: dopiero krzywa pokazuje, czy wydatek spada - a spada przy każdej
 * dłuższej redukcji i to jest najważniejsza rzecz, jaką ten moduł ma do
 * powiedzenia.
 */
data class StoredTdee(
    val day: Long,
    val kcal: Double,
    val method: TdeeMethod,

    /** Odchylenie standardowe oszacowania w kcal - szerokość, nie ozdoba. */
    val sdKcal: Double,

    val trendKgPerWeek: Double? = null
)

/** Składnik przepisu: produkt z katalogu plus ilość surowa. */
data class RecipeItem(
    val id: Long = 0,
    val recipeId: Long = 0,
    val foodId: Long,
    val grams: Double,
    val position: Int = 0,

    /** Wypełniane przy odczycie, dla wygody wyświetlania. */
    val foodName: String = "",
    val per100: Nutrients = Nutrients.ZERO
)

/**
 * Przepis - własne danie o znanym składzie.
 *
 * [cookedG] to waga gotowego dania. Bez niej ryż i makaron kłamią o połowę:
 * 100 g suchego ryżu to około 250 g ugotowanego, więc „100 g ryżu" w dzienniku
 * znaczy dwie zupełnie różne rzeczy. Puste [cookedG] oznacza danie, które nie
 * zmienia wagi - sałatkę, koktajl - i wtedy bierzemy sumę składników.
 *
 * [foodId] wskazuje wiersz w katalogu, przeliczany przy każdym zapisie
 * przepisu. Dzięki temu wpisanie przepisu do dziennika jest tym samym, co
 * wpisanie produktu, i nie ma w dzienniku drugiej ścieżki do utrzymania.
 */
data class Recipe(
    val id: Long = 0,
    val name: String,

    /**
     * Waga gotowego dania.
     *
     * Interfejs jej już nie pyta - zastąpiła ją [portions], bo łatwiej
     * powiedzieć „wychodzą z tego cztery obiady" niż zważyć pełny garnek.
     * Pole zostaje, bo przepisy zapisane wcześniej mają tu prawdziwą liczbę
     * i [Recipes.effectiveWeight] nadal ją honoruje.
     */
    val cookedG: Double? = null,

    /**
     * Na ile porcji dzieli się danie.
     *
     * Jedna porcja to waga dania podzielona przez tę liczbę i trafia do
     * katalogu jako `serving_g`, czyli jednostka „porcja" przy wpisywaniu.
     * `null` albo zero znaczy „nie dzielimy" - wtedy zostają gramy i całe
     * danie jako opakowanie.
     */
    val portions: Int? = null,

    val note: String? = null,
    val foodId: Long? = null,
    val items: List<RecipeItem> = emptyList()
)

/**
 * Zapytanie rozbite na słowa do wyszukiwania.
 *
 * Każde słowo szuka się osobno, bo nazwa produktu rzadko wygląda tak, jak
 * się ją wpisuje. „Skrzydełka panierowane" marki Yemmy mają klucz
 * `skrzydelka panierowane yemmy` - wpisanie „yemmy skrzydelka" nie trafia
 * w żaden ciągły fragment, mimo że oba słowa tam są. Przy szukaniu jednym
 * kawałkiem trzeba było zgadnąć kolejność i nie pominąć ani jednego słowa
 * ze środka.
 *
 * Ograniczenie liczby słów nie jest oszczędnością, tylko zabezpieczeniem:
 * każde słowo dokłada warunek do zapytania, a wklejone zdanie zrobiłoby
 * z tego SQL bez końca i tak bez żadnego wyniku.
 */
fun searchWords(query: String, max: Int = 5): List<String> =
    searchKey(query).split(' ').filter { it.isNotBlank() }.take(max)

/**
 * Numer dnia od epoki dla podanej chwili, w strefie czasowej urządzenia.
 *
 * Ta sama droga, którą liczy dzień import z Health Connect - i to jest tu
 * cały powód. Dzielenie znacznika czasu przez liczbę milisekund w dobie daje
 * dzień UTC, czyli u nas przesunięty o jedną lub dwie godziny: pomiar masy
 * zrobiony o 7 rano trafiałby czasem na dzień poprzedni, a wtedy ważenie
 * i śniadanie z tego samego poranka leżałyby w dwóch różnych dniach.
 */
fun epochDayOf(ms: Long): Long =
    java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

/** Dzisiejszy numer dnia od epoki, lokalnie. */
fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

/**
 * Nazwa sprowadzona do postaci, po której da się szukać.
 *
 * Małe litery i bez ogonków, bo inaczej „jogurt" nie znajduje „Jogurtu",
 * a „zurek" nie znajduje „żurku" - a przy wpisywaniu jedną ręką w sklepie
 * nikt nie sięga po ogonki. Znaki inne niż litery i cyfry stają się
 * odstępami, żeby „mleko 3,2%" i „mleko 3.2 %" trafiały w to samo.
 *
 * Wynik idzie do kolumny `food.name_key` i wyłącznie tam - do wyświetlania
 * zawsze używamy oryginalnej nazwy.
 */
fun searchKey(s: String): String {
    val sb = StringBuilder(s.length)
    var lastWasSpace = true
    for (ch in s.lowercase()) {
        val mapped = DIACRITICS[ch] ?: ch
        when {
            mapped.isLetterOrDigit() -> {
                sb.append(mapped)
                lastWasSpace = false
            }
            !lastWasSpace -> {
                sb.append(' ')
                lastWasSpace = true
            }
        }
    }
    return sb.toString().trim()
}

/**
 * Polskie znaki na łacińskie odpowiedniki.
 *
 * Wypisane wprost, a nie przez `Normalizer`: ten rozkłada znaki na literę
 * plus znak diakrytyczny, co działa dla większości, ale nie dla „ł" - ta nie
 * ma rozkładu kanonicznego i przeżywała normalizację nietknięta. Akurat „ł"
 * jest w polskich nazwach produktów wszędzie.
 */
private val DIACRITICS: Map<Char, Char> = mapOf(
    'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
    'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z'
)
