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

    /**
     * @param programId plan, którego historię siły pokazujemy; null = wszystkie.
     *   Przy dwóch planach treningi z PPL stały na liście pod FBW i nie dało
     *   się z niej wyczytać, kiedy ostatnio szło to, na co się patrzy.
     *   Trening bez przynależności (skasowana jednostka albo plan) zostaje
     *   przy każdym planie - inaczej nie byłoby skąd go otworzyć.
     */
    /** Czy dwie aktywności pokrywają się w czasie na tyle, by być jedną. */
    private fun sameActivity(a: ExternalSession, b: ExternalSession): Boolean {
        val shorter = minOf(a.endedAt - a.startedAt, b.endedAt - b.startedAt)
        if (shorter <= 0) return false
        val overlap = WatchMatch.overlapMs(a.startedAt, a.endedAt, b.startedAt, b.endedAt)
        return overlap >= shorter * WatchMatch.MIN_OVERLAP_RATIO
    }

    fun build(
        strength: List<SessionSummary>,
        watch: List<ExternalSession>,
        limit: Int = 30,
        programId: Long? = null
    ): Split {
        // Sesje z zegarka, które już doczepiły się do Twojego zapisu, nie mogą
        // pojawić się drugi raz jako osobna pozycja - to ten sam trening.
        // Liczone ze WSZYSTKICH sesji, przed odsianiem po planie: inaczej
        // zegarkowa połowa treningu z drugiego planu wracałaby na listę jako
        // „z zegarka, bez zapisanych serii".
        val matched = strength.mapNotNull { it.watch?.externalId }.toSet()
        // Bieg nagrany telefonem i ten sam bieg nagrany zegarkiem to jeden
        // bieg. Zostaje zapis z telefonu - to ten, który świadomie zacząłeś
        // w aplikacji, i tylko jego da się z niej skasować.
        val phone = watch.filter { it.fromPhone }
        val loose = watch.filterNot { w ->
            w.externalId in matched ||
                (!w.fromPhone && phone.any { p -> sameActivity(p, w) })
        }

        val own = if (programId == null) strength
        else strength.filter { it.programId == null || it.programId == programId }

        // Siła z zegarka bez zapisanych serii nie należy do żadnego planu.
        // Bez wyboru planu stoi obok Twoich zapisów, jak dotąd. Przy wybranym
        // planie idzie do „Inne": pod planem robionym pierwszy raz stało
        // szesnaście takich wpisów sprzed miesięcy i wyglądały na jego historię.
        val unlogged = loose.filter(::isStrength)
        val ownAndUnlogged = own.map { Activity.Strength(it) } +
            if (programId == null) unlogged.map { Activity.Watch(it) } else emptyList()
        val unloggedElsewhere = if (programId == null) emptyList() else unlogged

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
            other = (loose.filterNot { isCardio(it) || isStrength(it) } + unloggedElsewhere)
                .sortedByDescending { it.startedAt }.take(limit)
                .map { Activity.Watch(it) }
        )
    }
}
