package pl.gymbro.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.gymbro.gym.RunAnalysis
import java.time.Instant

/**
 * Szeregi czasowe pojedynczej aktywności, pobierane dopiero na żądanie.
 *
 * Godzinny bieg to tysiące próbek tętna. Trzymanie tego u siebie dla każdego
 * treningu oznaczałoby bazę rosnącą o megabajty miesięcznie po to, żeby raz na
 * jakiś czas ktoś spojrzał na wykres. Health Connect i tak je ma, więc czytamy
 * je w chwili otwarcia szczegółów i zapominamy po zamknięciu.
 *
 * Kosztuje to dwa zapytania na wejście w trening - przy naprawionym
 * stronicowaniu to nic wobec przydziału.
 */
class ActivityDetail(private val context: Context) {

    data class Series(
        val heartRate: List<RunAnalysis.Sample>,
        val legs: List<RunAnalysis.Leg>,
        /**
         * Okrążenia zapisane przez zegarek.
         *
         * To jest najlepsze możliwe źródło tempa na kilometr: zegarek mierzy
         * je sam, w momencie przekroczenia, z dokładnością GPS. Każde
         * wyliczanie z przyrostów dystansu jest wobec tego przybliżeniem.
         */
        val laps: List<RunAnalysis.Leg> = emptyList(),
        /** Suma kroków w oknie treningu; podstawa rytmu i długości kroku. */
        val steps: Long? = null
    ) {
        val hasAnything: Boolean get() = heartRate.isNotEmpty() || legs.isNotEmpty()
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun load(startedAt: Long, endedAt: Long): Series = withContext(Dispatchers.IO) {
        val range = TimeRangeFilter.between(
            Instant.ofEpochMilli(startedAt),
            Instant.ofEpochMilli(endedAt)
        )

        val hr = runCatching {
            readAll(range) { token ->
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = range,
                        pageSize = 1000,
                        pageToken = token
                    )
                ).let { it.records to it.pageToken }
            }.flatMap { rec ->
                // Jeden rekord tętna niesie wiele próbek - to one są szeregiem,
                // nie rekordy.
                rec.samples.map {
                    RunAnalysis.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble())
                }
            }
        }.onFailure { Log.w(TAG, "Nie udało się pobrać tętna", it) }.getOrDefault(emptyList())

        val legs = runCatching {
            readAll(range) { token ->
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = range,
                        pageSize = 1000,
                        pageToken = token
                    )
                ).let { it.records to it.pageToken }
            }.map {
                RunAnalysis.Leg(
                    from = it.startTime.toEpochMilli(),
                    to = it.endTime.toEpochMilli(),
                    meters = it.distance.inMeters
                )
            }
        }.onFailure { Log.w(TAG, "Nie udało się pobrać dystansu", it) }.getOrDefault(emptyList())

        // Kroki w oknie treningu. Nie dla samej liczby - z nich i z czasu
        // wychodzi rytm, a z nich i z dystansu długość kroku. Dwie rzeczy,
        // na które biegacz realnie wpływa, bez żadnego nowego uprawnienia.
        val steps = runCatching {
            readAll(range) { token ->
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = range,
                        pageSize = 1000,
                        pageToken = token
                    )
                ).let { it.records to it.pageToken }
            }.distinctBy { it.metadata.id }.sumOf { it.count }
        }.onFailure { Log.w(TAG, "Nie udało się pobrać kroków", it) }.getOrNull()
            ?.takeIf { it > 0 }

        // Okrążenia siedzą wewnątrz rekordu treningu, nie w osobnym strumieniu.
        val laps = runCatching {
            readAll(range) { token ->
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = range,
                        pageSize = 50,
                        pageToken = token
                    )
                ).let { it.records to it.pageToken }
            }.distinctBy { it.metadata.id }
                .also { records ->
                    // Czujka na serie z zegarka.
                    //
                    // Health Connect ma w rekordzie treningu pole `segments`
                    // z typem i liczbą powtórzeń. Gdyby Zepp je wypełniał,
                    // serie klikane na zegarku dałoby się zaciągnąć wprost
                    // i nie trzeba by ich wklepywać drugi raz w aplikacji.
                    //
                    // Sprawdzone 2026-10-11: nie wypełnia. Zarówno bieg, jak
                    // i trening siłowy dostają jeden segment-zaślepkę z jednym
                    // „powtórzeniem". Logujemy więc tylko odstępstwo od tego,
                    // żeby zmiana po aktualizacji zegarka nie przeszła
                    // niezauważona - a przy okazji nie zaśmiecać logu.
                    records.filter { it.segments.size > 1 }.forEach { r ->
                        Log.i(
                            TAG,
                            "Zegarek zaczął przysyłać segmenty! Typ ${r.exerciseType}: " +
                                r.segments.size + " segmentów, " +
                                r.segments.take(6).joinToString { s ->
                                    "typ=${s.segmentType} powt=${s.repetitions}"
                                }
                        )
                    }
                }
                .flatMap { it.laps }
                .mapNotNull { lap ->
                    val meters = lap.length?.inMeters ?: return@mapNotNull null
                    RunAnalysis.Leg(
                        from = lap.startTime.toEpochMilli(),
                        to = lap.endTime.toEpochMilli(),
                        meters = meters
                    )
                }
        }.onFailure { Log.w(TAG, "Nie udało się pobrać okrążeń", it) }.getOrDefault(emptyList())

        Log.i(
            TAG,
            "Szczegóły: ${hr.size} próbek tętna, ${legs.size} odcinków dystansu, " +
                "${laps.size} okrążeń, kroki=${steps ?: "brak"}"
        )
        Series(hr, legs, laps, steps)
    }

    /**
     * Ta sama ostrożność ze stronicowaniem co w imporcie.
     *
     * Dostawca zwraca token także na ostatniej stronie, więc pętla bez hamulca
     * oddaje te same rekordy w kółko - tu skończyłoby się to wykresem z tą samą
     * minutą powtórzoną pięćdziesiąt razy.
     */
    /**
     * Samo tętno z okna czasu - pod kartę w Historii.
     *
     * Jedno zapytanie zamiast czterech z [load]: karta potrzebuje wyłącznie
     * przebiegu tętna, a każde zapytanie zjada przydział Health Connect.
     * Brak zgody albo błąd to pusta lista, nie wyjątek.
     */
    suspend fun heartRate(startedAt: Long, endedAt: Long): List<RunAnalysis.Sample> =
        withContext(Dispatchers.IO) {
            val range = TimeRangeFilter.between(
                Instant.ofEpochMilli(startedAt), Instant.ofEpochMilli(endedAt)
            )
            runCatching {
                readAll(range) { token ->
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = range,
                            pageSize = 1000,
                            pageToken = token
                        )
                    ).let { it.records to it.pageToken }
                }.flatMap { rec ->
                    rec.samples.map { RunAnalysis.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) }
                }.sortedBy { it.at }
            }.getOrDefault(emptyList())
        }

    private suspend fun <T> readAll(
        range: TimeRangeFilter,
        page: suspend (String?) -> Pair<List<T>, String?>
    ): List<T> {
        val out = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        while (pages < 20) {
            val (records, next) = page(token)
            out += records
            pages++
            if (next == null || records.isEmpty() || !seen.add(next)) break
            token = next
        }
        return out
    }

    private companion object {
        const val TAG = "GymBroImport"
    }
}
