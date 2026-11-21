package pl.gymbro.gym

import pl.gymbro.data.Activity
import pl.gymbro.data.SetEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Liczby zakładki Progres: podsumowanie okresu, tygodnie, boje i rekordy.
 *
 * Czyste funkcje na gotowych listach - bez bazy, więc reguły („co jest
 * rekordem", „kiedy to już zastój") testuje się na wymyślonych danych.
 */
object ProgressStats {

    private const val DAY = 86_400_000L

    enum class Period(val label: String, val days: Int?) {
        WEEKS4("4 tyg.", 28), MONTHS3("3 mies.", 91), YEAR("Rok", 365), ALL("Wszystko", null)
    }

    enum class Metric(val label: String) { SETS("Serie"), TIME("Czas"), TONNAGE("Tonaż") }

    /** Ile zrobiłeś w okresie. Serie i tonaż tylko z siły - bieg ich nie ma. */
    data class Totals(val workouts: Int, val minutes: Long, val sets: Int, val tonnage: Double)

    fun totals(items: List<Activity>, from: Long, to: Long): Totals {
        val inRange = items.filter { it.at in from until to }
        val strength = inRange.filterIsInstance<Activity.Strength>().map { it.summary }
        return Totals(
            workouts = inRange.size,
            minutes = inRange.sumOf { minutesOf(it) },
            sets = strength.sumOf { it.workingSets },
            tonnage = strength.sumOf { it.tonnage }
        )
    }

    private fun minutesOf(a: Activity): Long = when (a) {
        is Activity.Strength -> a.summary.durationMin
        is Activity.Watch -> a.session.durationMin
    }

    /** Poniedziałek 00:00 czasu lokalnego tygodnia, w którym leży [ms]. */
    fun weekStart(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Wartość na każdy z ostatnich [weeks] tygodni, od najstarszego; ostatni
     * to bieżący, jeszcze niepełny. Tydzień bez treningu jest zerem, nie dziurą -
     * przerwa ma być widać na wykresie.
     */
    fun weekly(
        items: List<Activity>,
        metric: Metric,
        weeks: Int,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Pair<Long, Double>> {
        val thisWeek = Instant.ofEpochMilli(weekStart(now, zone)).atZone(zone).toLocalDate()
        val starts = (weeks - 1 downTo 0).map {
            thisWeek.minusWeeks(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        val byWeek = items.groupBy { weekStart(it.at, zone) }
        return starts.map { start ->
            val list = byWeek[start].orEmpty()
            start to when (metric) {
                Metric.SETS -> list.filterIsInstance<Activity.Strength>()
                    .sumOf { it.summary.workingSets }.toDouble()
                Metric.TIME -> list.sumOf { minutesOf(it) }.toDouble()
                Metric.TONNAGE -> list.filterIsInstance<Activity.Strength>()
                    .sumOf { it.summary.tonnage }
            }
        }
    }

    // ---- Jedno ćwiczenie ---------------------------------------------------

    /**
     * Jedna sesja ćwiczenia. [record] - czy ta sesja podniosła najlepszy
     * dotąd szacowany ciężar maksymalny.
     */
    data class Point(
        val at: Long,
        val e1rm: Double,
        val topKg: Double,
        val volume: Double,
        val bestSet: SetEntry,
        val sets: Int,
        val record: Boolean
    )

    /** Serie robocze ćwiczenia zgrupowane po sesjach, od najstarszej. */
    fun points(sets: List<SetEntry>): List<Point> {
        var best = 0.0
        return sets.filter { !it.isWarmup && it.reps > 0 }
            .groupBy { it.sessionId }
            .values
            .map { s -> s.sortedBy { it.doneAt } }
            .sortedBy { it.first().doneAt }
            .map { s ->
                val top = s.maxBy { it.estimated1RM }
                // Pierwsza sesja to nie rekord - nie ma czego pobić, a złota
                // kropka przy każdym nowym ćwiczeniu nic by nie znaczyła.
                val record = best > 0 && top.estimated1RM > best + 1e-6
                best = maxOf(best, top.estimated1RM)
                Point(
                    at = s.first().doneAt,
                    e1rm = top.estimated1RM,
                    topKg = s.maxOf { it.weightKg },
                    volume = s.sumOf { it.tonnage },
                    bestSet = top,
                    sets = s.size,
                    record = record
                )
            }
    }

    /** Jedno ćwiczenie na karcie treningu: ile serii i najlepsza z nich. */
    data class ExerciseLine(val name: String, val sets: Int, val bestKg: Double, val bestReps: Int)

    /** Co pokazać na karcie treningu w Historii. */
    data class Highlights(val exercises: List<ExerciseLine>, val records: Int)

    /**
     * Ćwiczenia i rekordy każdego treningu, po numerze sesji.
     *
     * Z tych samych serii co Progres i tą samą regułą rekordu ([points]),
     * więc złota odznaka na karcie i złota kropka na wykresie ćwiczenia
     * zawsze mówią to samo. Ćwiczenia w kolejności wykonania - na karcie
     * stoi to, od czego trening się zaczął, czyli zwykle główny bój.
     */
    fun highlights(exercises: List<Triple<Long, String, List<SetEntry>>>): Map<Long, Highlights> {
        val lines = mutableMapOf<Long, MutableList<Pair<Long, ExerciseLine>>>()
        val records = mutableMapOf<Long, Int>()
        for ((_, name, sets) in exercises) {
            points(sets).forEach { p -> if (p.record) records.merge(p.bestSet.sessionId, 1, Int::plus) }
            sets.filter { !it.isWarmup && it.reps > 0 }.groupBy { it.sessionId }.forEach { (sid, s) ->
                // Najlepsza seria to najcięższa, a przy równym ciężarze ta
                // z większą liczbą powtórzeń - tak się o serii myśli na sali.
                val best = s.maxWith(compareBy<SetEntry> { it.weightKg }.thenBy { it.reps })
                lines.getOrPut(sid) { mutableListOf() } +=
                    s.minOf { it.doneAt } to ExerciseLine(name, s.size, best.weightKg, best.reps)
            }
        }
        return lines.mapValues { (sid, l) ->
            Highlights(l.sortedBy { it.first }.map { it.second }, records[sid] ?: 0)
        }
    }

    /** Najcięższy ciężar podniesiony na co najmniej [reps] powtórzeń. */
    data class RepRecord(val reps: Int, val kg: Double, val at: Long)

    /**
     * Rekordy na liczbę powtórzeń - „co umiem na piątkę".
     *
     * Seria na 6 liczy się do rekordu na 5: jeśli przerzuciłeś 100 kg sześć
     * razy, to pięć też. Przy remisie wygrywa wcześniejsza data - rekord to
     * moment, w którym pierwszy raz się to udało.
     */
    fun repRecords(sets: List<SetEntry>, reps: List<Int> = listOf(1, 3, 5, 10)): List<RepRecord?> {
        val working = sets.filter { !it.isWarmup }
        return reps.map { n ->
            val pool = working.filter { it.reps >= n }
            val kg = pool.maxOfOrNull { it.weightKg } ?: return@map null
            val first = pool.filter { it.weightKg >= kg - 1e-6 }.minBy { it.doneAt }
            RepRecord(n, kg, first.doneAt)
        }
    }

    /**
     * Ćwiczenie w skrócie, do listy na Progresie.
     *
     * @property current najlepszy szacowany maks. w okresie
     * @property change różnica względem poprzedniego okresu tej samej długości;
     *   bez danych z poprzedniego okresu - względem pierwszej sesji w okresie
     * @property stalledWeeks od ilu tygodni nie było rekordu, gdy to już
     *   zastój; null, gdy rekordy idą albo za mało sesji, żeby coś mówić
     */
    data class Lift(
        val exerciseId: Long,
        val name: String,
        val current: Double,
        val change: Double?,
        val sessionsInPeriod: Int,
        val lastRecordAt: Long?,
        val stalledWeeks: Int?,
        val trend: List<Double>
    )

    /**
     * Zastój: cztery tygodnie bez rekordu i co najmniej trzy sesje w tym
     * czasie. Konwencja, nie wynik badania - przy dwóch sesjach brak
     * rekordu to jeszcze przypadek, przy trzech już wzór.
     */
    const val STALL_DAYS = 28
    const val STALL_SESSIONS = 3

    fun lifts(
        exercises: List<Triple<Long, String, List<SetEntry>>>,
        period: Period,
        now: Long
    ): List<Lift> = exercises.mapNotNull { (id, name, sets) ->
        val pts = points(sets)
        if (pts.isEmpty()) return@mapNotNull null
        val from = period.days?.let { now - it * DAY } ?: Long.MIN_VALUE
        val inPeriod = pts.filter { it.at >= from }
        if (inPeriod.isEmpty()) return@mapNotNull null

        val current = inPeriod.maxOf { it.e1rm }
        val change = period.days?.let { days ->
            val prevFrom = from - days * DAY
            pts.filter { it.at in prevFrom until from }.maxOfOrNull { it.e1rm }
                ?.let { current - it }
        } ?: if (inPeriod.size >= 2) current - inPeriod.first().e1rm else null

        // Rekord liczony z całej historii: ostatni raz, gdy maks. poszedł w górę.
        // Pierwsza sesja ćwiczenia też jest jego „rekordem" - inaczej ćwiczenie
        // z jedną sesją nie miałoby daty odniesienia dla zastoju.
        val lastRecord = (pts.lastOrNull { it.record } ?: pts.first()).at
        val since = pts.count { it.at > lastRecord }
        val stalled = if (now - lastRecord >= STALL_DAYS * DAY && since >= STALL_SESSIONS)
            ((now - lastRecord) / (7 * DAY)).toInt() else null

        Lift(
            exerciseId = id,
            name = name,
            current = current,
            change = change,
            sessionsInPeriod = inPeriod.size,
            lastRecordAt = pts.lastOrNull { it.record }?.at,
            stalledWeeks = stalled,
            trend = inPeriod.takeLast(12).map { it.e1rm }
        )
        // Kolejność: najcięższe na górze, z co najmniej dwiema sesjami w okresie.
        //
        // Po liczbie sesji na górę „głównych bojów" szły prostowania na
        // wyciągu i uginania - w FBW akcesoria siedzą w kilku jednostkach naraz,
        // więc mają więcej sesji niż przysiad. Ciężar odróżnia bój od dodatku
        // lepiej niż częstotliwość. Pojedyncza sesja nie daje trendu, więc
        // idzie na koniec, choćby była najcięższa.
    }.sortedWith(
        compareByDescending<Lift> { it.sessionsInPeriod >= 2 }
            .thenByDescending { it.current }
            .thenBy { it.name }
    )
}
