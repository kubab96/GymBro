package pl.gymbro.data

import kotlin.math.exp

/**
 * Trend masy ciała i tempo jego zmiany - czyste funkcje, bez bazy.
 *
 * Trend to masa po odfiltrowaniu dziennych wahań (woda, sól, treść jelit),
 * czyli odpowiedź na pytanie „czy naprawdę przybywa, czy ubywa".
 *
 * **Wygładzanie liczy się po dniach, nie po pomiarach.** Pierwsza wersja
 * przesuwała trend o stałe 10% różnicy przy każdym ważeniu, co przy
 * codziennym ważeniu daje średnią z ok. 10 dni. Przy ważeniu co 3-7 dni ta
 * sama reguła uśredniała dwa miesiące: w listopadzie 2026 pomiary stały na
 * 81,8-82,35 kg od trzech tygodni, a linia dalej pokazywała 80,9 kg i rosła
 * nawet po spadku. Teraz waga pomiaru zależy od przerwy przed nim - pomiar po
 * tygodniu przerwy przesuwa trend mocniej niż pomiar z następnego dnia.
 */
object WeightTrend {

    /**
     * Stała czasowa wygładzania w dniach. Konwencja, nie wynik badania:
     * dziesięć dni gasi wahania z dnia na dzień, a prawdziwą zmianę pokazuje
     * po około tygodniu - tyle, ile trwa od jednego ważenia do drugiego.
     */
    const val TAU_DAYS = 10.0

    /**
     * Okno, z którego liczy się tempo na tydzień. To samo co przy wydatku
     * z bilansu energii (`kalorie.md`, rozdział 3): przy kilku ważeniach
     * w miesiącu nachylenie z krótszego okna ma niepewność rzędu pół
     * kilograma na tydzień, czyli więcej niż samo tempo.
     */
    const val RATE_WINDOW_DAYS = 56

    private const val DAY_MS = 86_400_000.0

    /**
     * Masa wygładzona średnią wykładniczą ważoną czasem.
     *
     * @param points pary (czas w ms, masa), od najstarszej.
     * @return pary (czas, trend) w tej samej kolejności.
     */
    fun smooth(points: List<Pair<Long, Double>>, tauDays: Double = TAU_DAYS): List<Pair<Long, Double>> {
        if (points.isEmpty()) return emptyList()
        var ema = points.first().second
        var prevT = points.first().first
        return points.map { (t, w) ->
            // Dwa pomiary z tej samej chwili (albo wpisane wstecz w złej
            // kolejności) mają przerwę zero - wtedy nowy nic nie waży, więc
            // ujemną przerwę też traktujemy jako zero, a nie jako cofanie trendu.
            val days = ((t - prevT) / DAY_MS).coerceAtLeast(0.0)
            val alpha = 1 - exp(-days / tauDays)
            ema += alpha * (w - ema)
            prevT = t
            t to ema
        }
    }

    /**
     * Tempo zmiany masy w kg na tydzień: nachylenie prostej dopasowanej do
     * surowych pomiarów z ostatnich [windowDays] dni.
     *
     * Z pomiarów, nie z trendu: trend jest z definicji opóźniony, więc różnica
     * trendu między końcami okna zaniżała tempo na początku każdej zmiany.
     *
     * @param points pary (czas w ms, masa), od najstarszej.
     * @return null, gdy w oknie są mniej niż trzy pomiary albo obejmują mniej
     *   niż tydzień - z dwóch punktów prosta przechodzi przez szum.
     */
    fun weeklyRate(points: List<Pair<Long, Double>>, windowDays: Int = RATE_WINDOW_DAYS): Double? {
        val last = points.lastOrNull()?.first ?: return null
        val from = last - windowDays * DAY_MS.toLong()
        val window = points.filter { it.first >= from }
        if (window.size < 3) return null
        val xs = window.map { (it.first - window.first().first) / DAY_MS }
        if (xs.last() < 7.0) return null
        val ys = window.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx <= 0.0) return null
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        return sxy / sxx * 7.0
    }
}
