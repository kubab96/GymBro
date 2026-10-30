package pl.gymbro.health

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cicha synchronizacja z zegarkiem przy otwarciu aplikacji.
 *
 * Health Connect nie powiadamia aplikacji o nowym treningu - nie ma takiego
 * mechanizmu dla aplikacji trzecich. Zostaje odpytywanie, a jedyny moment,
 * w którym ma to sens bez budzenia telefonu w tle, to chwila, gdy i tak
 * otwierasz aplikację. Wybiegasz, otwierasz, bieg już jest.
 *
 * Stało się to wykonalne dopiero po naprawieniu limitu zapytań: wcześniej
 * jedna synchronizacja kosztowała około ośmiuset żądań, więc uruchamianie jej
 * przy każdym otwarciu wyczerpałoby dzienny przydział po kilku razach. Teraz,
 * gdy sesje z danymi są pomijane, kosztuje kilka żądań.
 */
object AutoSync {

    private const val PREFS = "gymbro_sync"
    private const val KEY_LAST = "last_auto_sync_at"
    private const val TAG = "GymBroImport"

    /**
     * Minimalny odstęp między automatycznymi synchronizacjami.
     *
     * Kwadrans, bo tyle mniej więcej trwa, zanim Zepp wypchnie świeży trening
     * do Health Connect - częstsze pytanie i tak nic by nie przyniosło, a każde
     * kosztuje. Ręczny przycisk w Profilu działa niezależnie od tego progu:
     * jeśli świadomie naciskasz „Synchronizuj", to znaczy, że chcesz teraz.
     */
    const val MIN_INTERVAL_MS: Long = 15 * 60 * 1000L

    /** Czy od ostatniej automatycznej synchronizacji minęło dość czasu. */
    fun isDue(lastAt: Long?, now: Long): Boolean =
        lastAt == null || now - lastAt >= MIN_INTERVAL_MS

    /**
     * Synchronizuje, jeśli pora. Zwraca true, gdy faktycznie coś pobrano.
     *
     * Błędy są połykane celowo. To jest czynność w tle, o którą nikt nie prosił;
     * okienko z błędem przy starcie aplikacji byłoby gorsze niż nieudany import,
     * o którym i tak dowiesz się z ekranu Zegarka.
     */
    suspend fun runIfDue(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0L }
        if (!isDue(last, now)) return@withContext false

        // Znacznik zapisujemy przed próbą, nie po niej. Gdyby import padał
        // w kółko, zapis po sukcesie oznaczałby ponawianie przy każdym
        // otwarciu aplikacji - czyli dokładnie to bombardowanie przydziału,
        // którego unikamy.
        prefs.edit().putLong(KEY_LAST, now).apply()

        runCatching { HealthImporter(context).sync() }
            .onFailure { Log.w(TAG, "Automatyczna synchronizacja nie powiodła się", it) }
            .map { it.total > 0 }
            .getOrDefault(false)
    }
}
