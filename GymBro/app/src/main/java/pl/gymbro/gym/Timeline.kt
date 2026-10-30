package pl.gymbro.gym

import pl.gymbro.data.Activity
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.SessionSummary

/**
 * Podział historii na trzy strumienie: siła, bieganie, reszta.
 *
 * Siła działa inaczej niż pozostałe i to nie jest przypadek. Ciężary zapisujesz
 * sam, bo zegarek ich nie zna - wie tylko, że przez godzinę biło Ci szybciej
 * serce. Bieganie i cała reszta idą w drugą stronę: zegarek wie wszystko,
 * a aplikacja nie ma czego dokładać. Stąd jeden strumień, który się wpisuje,
 * i dwa, które się czyta.
 */
object Timeline {

    /** Gotowy podział do wyświetlenia, każdy strumień od najnowszych. */
    data class Split(
        val strength: List<Activity>,
        val cardio: List<Activity>,
        val other: List<Activity>
    )

    /**
     * Typy, w których przemieszczasz się w terenie.
     *
     * Bieg i spacer trafiają razem, bo to ta sama rzecz w różnym tempie -
     * i opisuje je ten sam zestaw liczb: dystans, tempo, rytm. Rozdzielanie
     * ich na osobne zakładki rozbijałoby tydzień, w którym raz pobiegłeś,
     * a trzy razy poszedłeś na spacer.
     */
    private val CARDIO_TYPES = listOf(
        "Bieg", "Spacer", "Marsz", "Trekking", "Rower", "Pływanie", "Orbitrek", "Wioślarz"
    )

    /**
     * Czy to aktywność wytrzymałościowa.
     *
     * Najpierw po dystansie, bo to twardy dowód przemieszczania się niezależnie
     * od tego, jak zegarek nazwał trening. Nazwa jest zapasem dla przypadków,
     * w których dystansu zabrakło - na przykład biegu na bieżni.
     */
    fun isCardio(s: ExternalSession): Boolean =
        (s.distanceM ?: 0.0) > 0.0 ||
            CARDIO_TYPES.any { s.type.startsWith(it, ignoreCase = true) }

    /** Czy to siłowy trening z zegarka - kandydat do sklejenia z Twoim zapisem. */
    fun isStrength(s: ExternalSession): Boolean =
        s.type.equals("Trening siłowy", ignoreCase = true) ||
            s.type.equals("Podnoszenie ciężarów", ignoreCase = true)

    fun build(
        strength: List<SessionSummary>,
        watch: List<ExternalSession>,
        limit: Int = 30
    ): Split {
        // Sesje z zegarka, które już doczepiły się do Twojego zapisu, nie mogą
        // pojawić się drugi raz jako osobna pozycja - to ten sam trening.
        val matched = strength.mapNotNull { it.watch?.externalId }.toSet()
        val loose = watch.filterNot { it.externalId in matched }

        val ownAndUnlogged = strength.map { Activity.Strength(it) } +
            loose.filter(::isStrength).map { Activity.Watch(it) }

        return Split(
            strength = ownAndUnlogged.sortedByDescending { it.at }.take(limit),
            // Siła wygrywa z dystansem. Zegarek potrafi doliczyć parę metrów
            // przejścia między stanowiskami, a to nie czyni z przysiadów
            // aktywności wytrzymałościowej - bez tego warunku taki trening
            // pojawiłby się w dwóch zakładkach naraz.
            cardio = loose.filter { isCardio(it) && !isStrength(it) }
                .sortedByDescending { it.startedAt }.take(limit)
                .map { Activity.Watch(it) },
            // Wszystko, co nie jest ani siłą, ani ruchem w terenie. O takim
            // treningu zegarek wie tylko tyle, ile zmierzy z nadgarstka.
            other = loose.filterNot { isCardio(it) || isStrength(it) }
                .sortedByDescending { it.startedAt }.take(limit)
                .map { Activity.Watch(it) }
        )
    }
}
