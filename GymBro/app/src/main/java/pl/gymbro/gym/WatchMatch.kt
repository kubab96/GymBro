package pl.gymbro.gym

import pl.gymbro.data.ExternalSession

/**
 * Łączy trening zapisany w aplikacji z tym, co w tym samym czasie nagrał zegarek.
 *
 * Ten sam trening siłowy istnieje w dwóch miejscach: Ty zapisujesz serie i ciężary,
 * zegarek zapisuje czas i tętno. Żadna z tych wersji nie jest pełna - dopiero razem
 * mówią, ile zrobiłeś i jak ciężko to było.
 *
 * Łączymy po czasie, bo nic innego nie mamy: zegarek nie wie o planie, a aplikacja
 * nie dostaje identyfikatora sesji. Dopasowanie po samym dniu byłoby za luźne
 * (dwa treningi jednego dnia trafiłyby na siebie), a po dokładnej godzinie za ciasne
 * - zegarek startuje i kończy w innym momencie niż Ty dotykasz telefonu.
 */
object WatchMatch {

    /**
     * Jaka część krótszej z dwóch sesji musi się pokrywać, żeby uznać je za tę samą.
     *
     * Połowa, bo rozjazd bywa duży w obie strony: zegarek potrafi chodzić jeszcze
     * w szatni, a zdarza się i odwrotnie - włączasz go dopiero po rozgrzewce.
     * Niżej zaczęłyby się sklejać sesje, które tylko sąsiadują w czasie.
     */
    const val MIN_OVERLAP_RATIO = 0.5

    /** Część wspólna dwóch przedziałów czasu w milisekundach; 0, gdy się nie stykają. */
    fun overlapMs(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long =
        (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0L)

    /**
     * Najlepiej pasująca sesja z zegarka albo null, gdy żadna nie pokrywa się
     * dostatecznie. Przy kilku kandydatach wygrywa ta z największą częścią wspólną.
     */
    fun bestMatch(
        start: Long,
        end: Long,
        candidates: List<ExternalSession>
    ): ExternalSession? {
        if (end <= start) return null
        var best: ExternalSession? = null
        var bestOverlap = 0L
        for (c in candidates) {
            val duration = c.endedAt - c.startedAt
            if (duration <= 0L) continue
            val overlap = overlapMs(start, end, c.startedAt, c.endedAt)
            if (overlap <= 0L) continue
            val shorter = minOf(end - start, duration)
            if (overlap.toDouble() / shorter < MIN_OVERLAP_RATIO) continue
            if (overlap > bestOverlap) {
                bestOverlap = overlap
                best = c
            }
        }
        return best
    }

    /**
     * Czy sesja z zegarka jest aktywnością wytrzymałościową, a nie siłową.
     *
     * Rozstrzyga dystans, nie nazwa typu: zegarek nazywa rzeczy po swojemu i lista
     * nazw nigdy nie będzie pełna, a dystans albo jest, albo go nie ma. Trening
     * siłowy go nie ma.
     */
    fun isCardio(s: ExternalSession): Boolean = (s.distanceM ?: 0.0) > 0.0
}
