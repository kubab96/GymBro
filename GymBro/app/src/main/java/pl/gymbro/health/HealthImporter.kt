package pl.gymbro.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import pl.gymbro.data.DailyMetric
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.MetricKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Import danych z Health Connect do naszej bazy.
 *
 * Dwie rzeczy, które odróżniają to od wcześniejszego zrzutu diagnostycznego:
 *
 *  **Paginacja.** `readRecords` zwraca najwyżej jedną stronę wyników i token
 *  do następnej. Poprzednia wersja brała tylko pierwszą stronę, przez co raport
 *  urywał się na tysiącu rekordów tętna i wyglądało to na komplet danych.
 *  Tutaj czytamy do skutku.
 *
 *  **Idempotentność.** Każda sesja ma identyfikator ze źródła, a metryki dzienne
 *  klucz (dzień, rodzaj). Synchronizację można puszczać dowolnie często -
 *  powtórzone dane nadpisują stare zamiast się dokładać.
 */
class HealthImporter(private val context: Context) {

    data class Result(
        val sessions: Int,
        val hrvDays: Int,
        val restingHrDays: Int,
        val sleepDays: Int,
        val stepsDays: Int,
        val vo2maxDays: Int,
        val warnings: List<String>
    ) {
        val total: Int get() = sessions + hrvDays + restingHrDays + sleepDays + stepsDays + vo2maxDays
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val repo = HealthRepository(context)

    /**
     * Błędy poszczególnych kategorii trafiają i do komunikatu dla użytkownika,
     * i do logu systemowego z pełnym stosem wywołań. Samo zdanie w interfejsie
     * nie wystarcza do diagnozy - komunikat `null` z wyjątku bez typu nie mówi nic.
     */
    private fun warn(warnings: MutableList<String>, label: String, e: Throwable) {
        Log.w(TAG, "Import kategorii '$label' nie powiódł się", e)
        warnings += "$label: ${e::class.simpleName}: ${e.message ?: "bez komunikatu"}"
    }

    private companion object {
        const val TAG = "GymBroImport"

        /**
         * Ile sesji wolno odpytac o szczegoly w jednym przebiegu.
         *
         * Health Connect ma dzienny przydzial zapytan odczytu. Kazda sesja to
         * jedno zapytanie agregujace, a przy osmiuset treningach pochlanialy one
         * caly przydzial i reszta kategorii nie miala z czego sie pobrac.
         *
         * Sto pięćdziesiąt starcza, żeby nadrobić zaległości w kilku
         * synchronizacjach, i zostawia zapas na wszystko inne. Po pierwszym
         * nadrobieniu limit nie ma znaczenia, bo sesje z danymi sa pomijane.
         */
        const val AGGREGATE_BUDGET = 150
    }

    /** Wynik agregacji razem z informacja, czy skonczyl sie przydzial. */
    private data class Aggregated(
        val result: AggregationResult?,
        val rateLimited: Boolean
    )

    /**
     * Czy blad to wyczerpany przydzial zapytan.
     *
     * Health Connect nie ma na to osobnego typu wyjatku - leci `RemoteException`
     * z komunikatem. Rozpoznanie po tresci jest brzydkie, ale to jedyne, co
     * API daje, a roznica jest istotna: limit mija sam, a kazdy inny blad nie.
     */
    private fun isRateLimit(e: Throwable): Boolean {
        val m = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return m.contains("rate limit", ignoreCase = true) ||
            m.contains("quota", ignoreCase = true)
    }

    /**
     * Czyta WSZYSTKIE strony wyników, nie tylko pierwszą - i umie przestać.
     *
     * Pierwsza wersja kończyła na jednej stronie i gubiła dane. Druga czytała
     * do skutku i wpadła w odwrotną pułapkę: ten dostawca Health Connect zwraca
     * `pageToken` **także na ostatniej stronie**, więc pętla kręciła się aż do
     * `maxPages` i oddawała te same rekordy pięćdziesiąt razy. Szesnaście
     * treningów wyglądało w logu na osiemset, a każdy z nich był pięćdziesiąt
     * razy odpytywany o szczegóły - to właśnie to wyczerpywało przydział zapytań.
     *
     * Stąd trzy niezależne hamulce. Pusta strona kończy odczyt. Token, który
     * już widzieliśmy, kończy odczyt. A na końcu i tak odsiewamy powtórzenia po
     * identyfikatorze rekordu, bo na cudzej implementacji nie ma co polegać.
     */
    private suspend fun <T : Record> readAll(
        type: KClass<T>,
        range: TimeRangeFilter,
        maxPages: Int = 50
    ): List<T> {
        val out = mutableListOf<T>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        var pages = 0
        while (pages < maxPages) {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = range,
                    pageSize = 1000,
                    pageToken = token
                )
            )
            out += response.records
            pages++
            val next = response.pageToken
            if (next == null || response.records.isEmpty() || !seenTokens.add(next)) break
            token = next
        }
        val unique = out.distinctBy { it.metadata.id }
        if (unique.size != out.size) {
            Log.i(
                TAG,
                "${type.simpleName}: dostawca oddał ${out.size} rekordów, " +
                    "unikalnych ${unique.size} - powtórzenia odsiane."
            )
        }
        return unique
    }

    private fun epochDay(instant: Instant): Long =
        instant.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    suspend fun sync(days: Long = 90): Result {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
        val range = TimeRangeFilter.between(start, end)
        val warnings = mutableListOf<String>()

        // Kolejność nie jest tu kosmetyką, tylko częścią poprawki.
        //
        // Metryki dzienne idą pierwsze, bo każda kosztuje jedno zapytanie.
        // Wcześniej szły na końcu - za ośmiuset agregacjami sesji - i to właśnie
        // one wypadały poza przydział Health Connect. Kroki i VO2max nie
        // znikały dlatego, że ich nie ma; dlatego, że nigdy nie dochodziła
        // do nich kolej.

        // ---- HRV: dzienna średnia ----
        var hrvDays = 0
        runCatching {
            readAll(HeartRateVariabilityRmssdRecord::class, range)
                .groupBy { epochDay(it.time) }
                .forEach { (day, records) ->
                    val avg = records.map { it.heartRateVariabilityMillis }.average()
                    repo.upsertDaily(DailyMetric(day, MetricKind.HRV_RMSSD, avg))
                    hrvDays++
                }
        }.onFailure { warn(warnings, "HRV", it) }

        // ---- Tętno spoczynkowe ----
        var restingDays = 0
        runCatching {
            readAll(RestingHeartRateRecord::class, range)
                .groupBy { epochDay(it.time) }
                .forEach { (day, records) ->
                    val avg = records.map { it.beatsPerMinute.toDouble() }.average()
                    repo.upsertDaily(DailyMetric(day, MetricKind.RESTING_HR, avg))
                    restingDays++
                }
        }.onFailure { warn(warnings, "tętno spoczynkowe", it) }

        // ---- Sen: minuty na dzień ----
        var sleepDays = 0
        runCatching {
            readAll(SleepSessionRecord::class, range)
                // Sen przypisujemy do dnia, w którym się ZAKOŃCZYŁ - noc z soboty
                // na niedzielę to sen niedzielny, bo po nim trenujesz w niedzielę.
                .groupBy { epochDay(it.endTime) }
                .forEach { (day, records) ->
                    val minutes = records.sumOf {
                        Duration.between(it.startTime, it.endTime).toMinutes()
                    }.toDouble()
                    repo.upsertDaily(DailyMetric(day, MetricKind.SLEEP_MINUTES, minutes))
                    sleepDays++
                }
        }.onFailure { warn(warnings, "sen", it) }

        // ---- Kroki ----
        var stepsDays = 0
        runCatching {
            readAll(StepsRecord::class, range)
                .groupBy { epochDay(it.startTime) }
                .forEach { (day, records) ->
                    val total = records.sumOf { it.count }.toDouble()
                    repo.upsertDaily(DailyMetric(day, MetricKind.STEPS, total))
                    stepsDays++
                }
        }.onFailure { warn(warnings, "kroki", it) }

        // ---- VO2max ----
        var vo2Days = 0
        runCatching {
            readAll(Vo2MaxRecord::class, range)
                .groupBy { epochDay(it.time) }
                .forEach { (day, records) ->
                    val avg = records.map { it.vo2MillilitersPerMinuteKilogram }.average()
                    repo.upsertDaily(DailyMetric(day, MetricKind.VO2MAX, avg))
                    vo2Days++
                }
        }.onFailure { warn(warnings, "VO2max", it) }

        // ---- Treningi ----
        var sessionCount = 0
        var enriched = 0
        var skipped = 0
        var rateLimited = false
        runCatching {
            val sessions = readAll(ExerciseSessionRecord::class, range)

            // Zestawienie tego, co zegarek w ogóle przysyła.
            //
            // Przy ośmiuset rekordach sama liczba nic nie mówi - dopiero podział
            // na typy i długości pokazuje, co jest treningiem, a co śladem po
            // autodetekcji ruchu. Od tego zależy, co ma prawo trafić do historii.
            val histogram = sessions
                .groupBy { exerciseName(it.exerciseType) }
                .map { (type, list) ->
                    val mins = list.map {
                        Duration.between(it.startTime, it.endTime).toMinutes()
                    }
                    val short = mins.count { it < 5 }
                    "$type: ${list.size} szt., mediana ${mins.sorted()[mins.size / 2]} min, " +
                        "poniżej 5 min: $short"
                }
                .sorted()
            Log.i(TAG, "Co przysyła zegarek:\n" + histogram.joinToString("\n"))
            val known = repo.allByExternalId()
            var budget = AGGREGATE_BUDGET

            // Od najnowszych, bo przydział może się skończyć w połowie listy.
            // Tętno sprzed trzech miesięcy nie jest nic warte, tętno z wczoraj
            // owszem - więc jeśli coś ma przepaść, niech to będzie stare.
            sessions.sortedByDescending { it.startTime }.forEach { s ->
                val existing = known[s.metadata.id]

                // Sesji, która ma już dane, nie pytamy drugi raz. To jest
                // właściwa oszczędność: po pierwszej pełnej synchronizacji
                // kolejne nie kosztują prawie nic.
                val worthAsking = existing?.isEnriched != true
                val agg = if (worthAsking && budget > 0 && !rateLimited) {
                    budget--
                    val a = aggregateFor(s)
                    if (a.rateLimited) rateLimited = true
                    a.result
                } else {
                    if (worthAsking) skipped++
                    null
                }
                if (agg != null) enriched++

                repo.upsertSession(
                    ExternalSession(
                        externalId = s.metadata.id,
                        source = s.metadata.dataOrigin.packageName,
                        type = exerciseName(s.exerciseType),
                        title = s.title,
                        startedAt = s.startTime.toEpochMilli(),
                        endedAt = s.endTime.toEpochMilli(),
                        // Zapis nadpisuje wiersz, więc przy pominiętej agregacji
                        // trzeba świadomie przepisać to, co już było - inaczej
                        // synchronizacja kasowałaby własne wcześniejsze wyniki.
                        // AggregationResult udostępnia metryki operatorem
                        // indeksowania, który sam zwraca null, gdy metryki nie ma.
                        distanceM = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters
                            ?: existing?.distanceM,
                        energyKcal = agg?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
                            ?.inKilocalories ?: existing?.energyKcal,
                        hrAvg = agg?.get(HeartRateRecord.BPM_AVG)?.toDouble()
                            ?: existing?.hrAvg,
                        hrMax = agg?.get(HeartRateRecord.BPM_MAX)?.toDouble()
                            ?: existing?.hrMax
                    )
                )
                sessionCount++
            }

            // Jedno zdanie zamiast setek identycznych ostrzeżeń.
            if (rateLimited) {
                warnings += "Health Connect wyczerpał przydział zapytań - " +
                    "szczegóły części treningów dociągną się przy następnej " +
                    "synchronizacji."
            } else if (skipped > 0) {
                warnings += "Szczegółów $skipped starszych treningów nie pobrano " +
                    "w tym przebiegu; dociągną się przy kolejnym."
            }
        }.onFailure { warn(warnings, "treningi", it) }

        Log.i(
            TAG,
            "Synchronizacja zakończona: treningi=$sessionCount (szczegóły: " +
                "$enriched nowych, $skipped odłożonych, limit=$rateLimited), " +
                "HRV=$hrvDays, tętno spocz.=$restingDays, sen=$sleepDays, " +
                "kroki=$stepsDays, VO2max=$vo2Days, ostrzeżeń=${warnings.size}"
        )
        return Result(sessionCount, hrvDays, restingDays, sleepDays, stepsDays, vo2Days, warnings)
    }
    /**
     * Dystans, kalorie i tętno dla jednej sesji.
     *
     * Health Connect nie trzyma tych wartości w rekordzie treningu - leżą
     * w osobnych strumieniach, więc agregujemy je po zakresie czasu sesji.
     * Zwraca null, gdy brak uprawnień albo danych.
     */
    private suspend fun aggregateFor(s: ExerciseSessionRecord): Aggregated =
        try {
            Aggregated(
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX
                        ),
                        timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime)
                    )
                ),
                rateLimited = false
            )
        } catch (e: Exception) {
            // Brak danych dla sesji to normalka i nie jest bledem wartym logu.
            // Wyczerpany przydzial juz tak - i musi zatrzymac dalsze pytania.
            if (isRateLimit(e)) Log.w(TAG, "Przydzial zapytan wyczerpany", e)
            Aggregated(null, isRateLimit(e))
        }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Bieg"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Bieg (bieżnia)"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Spacer"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trekking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Rower"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Rower stacjonarny"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pływanie (basen)"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Pływanie (otwarte)"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Trening siłowy"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Podnoszenie ciężarów"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Orbitrek"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Wioślarz"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Schody"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Joga"
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Inny trening"
        else -> "Typ $type"
    }
}
