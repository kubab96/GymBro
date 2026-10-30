package pl.gymbro.data

/**
 * Pamięć podręczna wyników odczytu, żyjąca przez cały czas działania procesu.
 *
 * Powód: przełączenie zakładki niszczy kompozycję ekranu razem z całym jego
 * stanem. Przy powrocie wszystko ładowało się od nowa - zapytania, przeskok
 * na wątek roboczy i migające „Wczytuję…". Przy szybkim przeskakiwaniu między
 * zakładkami to się kumulowało i było odczuwalne.
 *
 * Ekran zaczyna teraz od tego, co zostało tu zapisane poprzednio, i odświeża
 * dane w tle. Zamiast pustki widzisz poprzedni stan, który najczęściej jest
 * już aktualny.
 *
 * **Unieważnianie jest celowo gruboziarniste**: każdy zapis czyści całość.
 * Precyzyjne unieważnianie oznaczałoby pilnowanie zależności między kluczami,
 * a każde przeoczenie pokazywałoby nieaktualne dane. Przy tej wielkości bazy
 * ponowny odczyt kosztuje ułamek milisekundy, więc nie ma czego oszczędzać -
 * a poprawność jest wtedy oczywista zamiast wymagać dowodu.
 */
object Cache {

    private val entries = HashMap<String, Any?>()

    @Synchronized
    fun <T> put(key: String, value: T): T {
        entries[key] = value
        return value
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> peek(key: String): T? = entries[key] as? T

    @Synchronized
    fun clear() {
        entries.clear()
    }

    // Klucze trzymamy w jednym miejscu, żeby literówka nie tworzyła po cichu
    // drugiego, nigdy nieunieważnianego wpisu.
    const val PLAN_OPTIONS = "plan_options"
    const val ACTIVE_PROGRAM = "active_program"
    const val INVENTORY = "inventory"
    const val PROFILE = "profile"
    const val BODY_RECENT = "body_recent"
    const val BODY_TREND = "body_trend"
    const val PROGRAMS = "programs"
    const val EXERCISES = "exercises"
}
