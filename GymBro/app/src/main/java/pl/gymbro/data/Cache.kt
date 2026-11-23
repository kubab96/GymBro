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
        generation++
    }

    /** Rosnie przy kazdym [clear] - po nim poznaje sie wynik policzony sprzed zmiany. */
    private var generation = 0
    private val loadLock = Any()

    /**
     * Wartosc z pamieci albo wczytana raz, dla wszystkich pytajacych naraz.
     *
     * Progres i Historia potrzebuja tych samych serii z calej historii i pytaly
     * o nie rownoczesnie, kazda osobno. Drugi pytajacy czeka teraz na wynik
     * pierwszego. Wynik wczytany przed [clear] nie trafia do pamieci - inaczej
     * zmiana w trakcie wczytywania zostawialaby tu stare dane do nastepnej.
     *
     * @param tag to, od czego wynik zalezy, a co zmienia sie bez [clear] -
     *   zakonczenie treningu na telefonie zmienia liste aktywnosci, ale nie
     *   podbija odswiezenia. Inny znacznik to inny wynik.
     *
     * Wolac z watku roboczego: [load] czyta baze.
     */
    fun <T : Any> getOrLoad(key: String, tag: Any?, load: () -> T): T {
        synchronized(loadLock) {
            @Suppress("UNCHECKED_CAST")
            val stored = synchronized(this) { entries[key] as? Pair<Any?, T> }
            if (stored != null && stored.first == tag) return stored.second
            val gen = synchronized(this) { generation }
            val value = load()
            synchronized(this) { if (generation == gen) entries[key] = tag to value }
            return value
        }
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
    const val WORKING_SETS = "working_sets"
}
