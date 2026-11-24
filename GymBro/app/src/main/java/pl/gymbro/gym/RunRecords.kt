package pl.gymbro.gym

import pl.gymbro.data.ExternalSession

/**
 * Rekordy biegowe i rowerowe - czyste funkcje, bez bazy.
 *
 * Najlepszy czas na dystansie liczy się z trasy ([TrackMath.bestEffortMs]),
 * z dowolnego fragmentu biegu. Bieg z zegarka trasy u nas nie ma - tylko
 * dystans i czas całości - więc do czasów na dystansie nie wchodzi. Do
 * najdłuższego biegu i tygodni wchodzi każdy.
 */
object RunRecords {

    private const val WEEK_MS = 7L * 24 * 3600 * 1000

    /**
     * Bieg nagrany telefonem i ten sam bieg nagrany zegarkiem to jeden bieg -
     * jak w [Timeline]. Bez tego tydzień z jednym biegiem pokazywał dwa razy
     * tyle kilometrów.
     */
    fun unique(sessions: List<ExternalSession>): List<ExternalSession> {
        val phone = sessions.filter { it.fromPhone }
        return sessions.filterNot { w ->
            !w.fromPhone && phone.any { p ->
                val shorter = minOf(p.endedAt - p.startedAt, w.endedAt - w.startedAt)
                shorter > 0 && WatchMatch.overlapMs(p.startedAt, p.endedAt, w.startedAt, w.endedAt) >=
                    shorter * WatchMatch.MIN_OVERLAP_RATIO
            }
        }
    }

    /** Dystanse z rekordem czasu, w kolejności na ekranie. */
    val DISTANCES = listOf(1000.0 to "1 km", 5000.0 to "5 km", 10_000.0 to "10 km")

    /** Typy liczone jako bieg w terenie. Bieżnia osobno - tam nie ma trasy ani porównywalnego tempa. */
    val RUN_TYPES = listOf("Bieg", "Bieg w terenie")
    val BIKE_TYPES = listOf("Rower", "Rower górski")

    /** Najlepszy fragment jednego biegu na danym dystansie. */
    data class Effort(val externalId: String, val at: Long, val ms: Long)

    data class DistanceRecord(
        val meters: Double,
        val label: String,
        val best: Effort,
        /** Najnowszy bieg, który miał ten dystans - „ostatnio 27:58" pod rekordem. */
        val latest: Effort,
        /** Ile biegów miało ten dystans. Przy jednym „rekord" nic nie znaczy. */
        val runs: Int
    )

    data class Records(
        val distances: List<DistanceRecord>,
        val longestRun: ExternalSession?,
        val longestRide: ExternalSession?,
        /** Najwyższa średnia prędkość jazdy w km/h, z jazd od pięciu kilometrów. */
        val fastestRide: Pair<ExternalSession, Double>?
    ) {
        val isEmpty: Boolean
            get() = distances.isEmpty() && longestRun == null && longestRide == null

        /** Dystanse, na których ten bieg jest rekordem - do plakietki w szczegółach. */
        fun heldBy(externalId: String): List<DistanceRecord> =
            distances.filter { it.best.externalId == externalId && it.runs >= 2 }
    }

    /**
     * @param sessions biegi i jazdy ze wszystkich źródeł
     * @param tracks trasy biegów nagranych telefonem, po `external_id`
     */
    fun compute(all: List<ExternalSession>, tracks: Map<String, List<TrackMath.Fix>>): Records {
        val sessions = unique(all)
        val runs = sessions.filter { it.type in RUN_TYPES }
        val rides = sessions.filter { it.type in BIKE_TYPES }

        val distances = DISTANCES.mapNotNull { (m, label) ->
            val efforts = runs.mapNotNull { s ->
                val pts = tracks[s.externalId] ?: return@mapNotNull null
                TrackMath.bestEffortMs(pts, m)?.let { Effort(s.externalId, s.startedAt, it) }
            }
            if (efforts.isEmpty()) null
            else DistanceRecord(
                meters = m,
                label = label,
                best = efforts.minBy { it.ms },
                latest = efforts.maxBy { it.at },
                runs = efforts.size
            )
        }

        val fastest = rides
            .filter { (it.distanceM ?: 0.0) >= 5000.0 && it.endedAt > it.startedAt }
            .map { it to (it.distanceM!! / 1000.0) / ((it.endedAt - it.startedAt) / 3_600_000.0) }
            // Ponad 60 km/h średnio to nie jazda, tylko samochód z włączonym zegarkiem.
            .filter { it.second < 60.0 }
            .maxByOrNull { it.second }

        return Records(
            distances = distances,
            longestRun = runs.filter { (it.distanceM ?: 0.0) > 0 }.maxByOrNull { it.distanceM!! },
            longestRide = rides.filter { (it.distanceM ?: 0.0) > 0 }.maxByOrNull { it.distanceM!! },
            fastestRide = fastest
        )
    }

    /**
     * Kilometry biegu w ostatnich [weeks] tygodniach, od najstarszego.
     * Tydzień od poniedziałku, jak w Progresie siłowym.
     */
    fun weeklyKm(sessions: List<ExternalSession>, weeks: Int, now: Long): List<Pair<Long, Double>> {
        // Każdy tydzień z własnego poniedziałku, nie „siedem dni wstecz" -
        // przy zmianie czasu tydzień ma 167 albo 169 godzin.
        val starts = (weeks - 1 downTo 0).map { ProgressStats.weekStart(now - it * WEEK_MS) }
        val ends = starts.drop(1) + Long.MAX_VALUE
        val runs = unique(sessions).filter { it.type in RUN_TYPES }
        return starts.zip(ends).map { (start, end) ->
            start to runs.filter { it.startedAt in start until end }.sumOf { (it.distanceM ?: 0.0) / 1000.0 }
        }
    }
}
