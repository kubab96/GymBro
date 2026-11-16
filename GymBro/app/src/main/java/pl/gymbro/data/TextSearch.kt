package pl.gymbro.data

/**
 * Wyszukiwanie tekstu tak, jak się go wpisuje na telefonie.
 *
 * Polskie litery są opcjonalne: „wioslowanie" ma znaleźć „Wiosłowanie".
 * Klawiatura w telefonie podsuwa je dopiero po przytrzymaniu klawisza,
 * więc w praktyce pół zapytań przychodzi bez nich - a wyszukiwarka, która
 * wtedy nie znajduje nic, wygląda na pustą bazę.
 *
 * Kilka słów to kilka warunków naraz, w dowolnej kolejności: „hantle klatka"
 * znajduje „Wyciskanie hantli leżąc" przy partii „klatka". Każde słowo
 * dopasowuje się od początku dowolnego wyrazu, nie od środka - inaczej
 * „ok" łapałoby każde „bOKiem" i „skOK".
 * Odmiana działa przez początek wyrazu: „hantl" znajdzie i „hantle",
 * i „hantlami".
 */
object TextSearch {

    private val FOLD = mapOf(
        'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
        'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z'
    )

    /** Małe litery bez polskich znaków i bez interpunkcji między słowami. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.lowercase()) {
            val f = FOLD[c] ?: c
            sb.append(if (f.isLetterOrDigit()) f else ' ')
        }
        return sb.toString()
    }

    /** Słowa zapytania, już po [fold]. Puste zapytanie to brak warunków. */
    fun words(query: String): List<String> =
        fold(query).split(' ').filter { it.isNotEmpty() }

    /**
     * Czy każde słowo zapytania zaczyna któryś wyraz tekstu.
     *
     * [haystack] musi być już po [fold] - tekst przeszukiwany składa się raz,
     * a zapytanie zmienia się przy każdej literze.
     */
    fun matches(words: List<String>, haystack: String): Boolean =
        words.all { w -> haystack.startsWith(w) || haystack.contains(" $w") }

    fun matches(query: String, vararg fields: String): Boolean =
        matches(words(query), fold(fields.joinToString(" ")))
}
