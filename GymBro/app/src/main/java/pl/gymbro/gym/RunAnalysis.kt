package pl.gymbro.gym

import kotlin.math.roundToInt

/**
 * Rozbiór pojedynczej aktywności na to, czego nie widać w jednej liczbie.
 *
 * Średnie tempo biegu mówi mało: 6:09/km może być równym tempem albo pierwszym
 * kilometrem po 5:20 i ostatnim po 7:00. Dopiero rozbicie na kilometry i przebieg
 * tętna pokazują, co się naprawdę wydarzyło - czy równo rozłożyłeś siły, czy
 * wystrzeliłeś i dowlokłeś się do końca.
 *
 * Wszystko liczymy z surowych danych z Health Connect, bez zapisywania ich
 * u siebie: szereg czasowy z jednego biegu to tysiące punktów, a potrzebny
 * jest tylko w chwili, gdy patrzysz na ten konkretny trening.
 */
object RunAnalysis {

    /** Punkt szeregu czasowego: moment i wartość. */
    data class Sample(val at: Long, val value: Double)

    /** Odcinek dystansu: Health Connect podaje je jako przedziały, nie punkty. */
    data class Leg(val from: Long, val to: Long, val meters: Double)

    /** Jeden ukończony kilometr. */
    data class Split(val km: Int, val seconds: Long) {
        val paceMinPerKm: Double get() = seconds / 60.0
    }

    /**
     * Tempo na każdy pełny kilometr.
     *
     * Odcinki z zegarka nie kończą się równo na kilometrze, więc moment
     * przekroczenia granicy wyliczamy proporcjonalnie wewnątrz odcinka.
     * Bez tego ostatnie sekundy kilometra doklejałyby się do następnego
     * i każdy split byłby przesunięty.
     *
     * Niepełna końcówka nie wchodzi: 300 metrów przeliczone na tempo kilometra
     * to liczba, która wygląda jak pomiar, a jest ekstrapolacją.
     */
    /**
     * Okrążenie zmierzone przez zegarek: dystans, czas i tętno na tym odcinku.
     *
     * To jest lepsze źródło niż cokolwiek wyliczonego z przyrostów dystansu -
     * zegarek odnotowuje moment przekroczenia kilometra w chwili, gdy on
     * następuje, a nie po fakcie z interpolacji.
     */
    data class Lap(
        val index: Int,
        val meters: Double,
        val seconds: Long,
        val hrAvg: Double?
    ) {
        /** Null dla okrążeń bez dystansu - tempo bez drogi nie istnieje. */
        val paceMinPerKm: Double?
            get() = if (meters >= 1.0 && seconds > 0) (seconds / 60.0) / (meters / 1000.0) else null

        /** Czy to pełny kilometr, czy końcówka trasy. */
        val isFull: Boolean get() = meters >= 950.0
    }

    /**
     * Okrążenia z tętnem przypisanym po czasie.
     *
     * Tętno liczymy ze średniej próbek, które wypadły w oknie okrążenia.
     * Zegarek zapisuje je osobnym strumieniem, bez powiązania z okrążeniami,
     * więc jedynym spoiwem jest czas.
     */
    fun lapStats(laps: List<Leg>, heartRate: List<Sample>): List<Lap> =
        laps.filter { it.to > it.from }
            .sortedBy { it.from }
            .mapIndexed { i, lap ->
                val inLap = heartRate.filter { it.at in lap.from..lap.to }
                Lap(
                    index = i + 1,
                    meters = lap.meters,
                    seconds = (lap.to - lap.from) / 1000,
                    hrAvg = if (inLap.isEmpty()) null else inLap.sumOf { it.value } / inLap.size
                )
            }

    /**
     * Czy odcinki są dość gęste, żeby tempo na kilometr cokolwiek znaczyło.
     *
     * Zegarek potrafi przysłać dziesięciokilometrowy bieg w dwóch kawałkach.
     * Interpolacja wyliczy wtedy jedenaście identycznych kilometrów - wykres
     * wyglądający jak idealnie równe tempo, choć nie wiemy o tym biegu nic
     * poza średnią. Taki obraz jest gorszy niż jego brak, bo wygląda na pomiar.
     *
     * Wymagamy co najmniej jednego odcinka na kilometr. To nie gwarantuje
     * dokładności, ale odcina przypadki, w których cały wykres jest zmyślony.
     */
    fun splitsAreMeaningful(legs: List<Leg>): Boolean {
        val usable = legs.filter { it.meters > 0 && it.to > it.from }
        val km = usable.sumOf { it.meters } / 1000.0
        return km >= 1.0 && usable.size >= km
    }

    fun splits(legs: List<Leg>): List<Split> {
        val sorted = legs.filter { it.meters > 0 && it.to > it.from }.sortedBy { it.from }
        if (sorted.isEmpty()) return emptyList()

        val out = mutableListOf<Split>()
        var covered = 0.0
        var kmStartedAt = sorted.first().from.toDouble()
        var nextBoundary = 1000.0

        for (leg in sorted) {
            val span = (leg.to - leg.from).toDouble()
            while (covered + leg.meters >= nextBoundary) {
                // Jaka część TEGO odcinka wypada przed granicą kilometra.
                //
                // Liczona od początku odcinka, nie od poprzedniej granicy. Gdy
                // w jednym odcinku mieszczą się dwa kilometry, ułamek dla
                // drugiego musi być większy - inaczej oba wypadają w tym samym
                // momencie i drugi split wychodzi zerowy.
                val intoLeg = nextBoundary - covered
                val fraction = (intoLeg / leg.meters).coerceIn(0.0, 1.0)
                // Granica nie może wypaść przed początkiem bieżącego kilometra.
                //
                // Odcinki z zegarka potrafią na siebie zachodzić albo przyjść
                // nie po kolei; bez tego ograniczenia różnica wychodziła ujemna
                // i na ekranie pojawiało się tempo „-56:00".
                val crossedAt = maxOf(leg.from + span * fraction, kmStartedAt)
                out += Split(
                    km = (nextBoundary / 1000).toInt(),
                    seconds = ((crossedAt - kmStartedAt) / 1000.0).roundToInt().toLong()
                        .coerceAtLeast(0L)
                )
                kmStartedAt = crossedAt
                nextBoundary += 1000.0
            }
            covered += leg.meters
        }
        return out
    }

    /**
     * Rytm biegu w krokach na minutę.
     *
     * Liczony z sumy kroków i czasu, bo zegarek nie wystawia rytmu osobno bez
     * dodatkowych uprawnień, a dzielenie daje dokładnie tę samą liczbę.
     *
     * To jedna z niewielu rzeczy w bieganiu, na które wpływasz świadomie
     * i od razu. Poniżej mniej więcej 160 kroków na minutę zwykle oznacza
     * za długi wykrok i lądowanie na pięcie - czyli hamowanie przy każdym
     * kroku i więcej wstrząsu w kolanach.
     */
    fun cadence(steps: Long, durationMin: Long): Double? =
        if (steps > 0 && durationMin > 0) steps.toDouble() / durationMin else null

    /**
     * Średnia długość kroku w centymetrach.
     *
     * Razem z rytmem rozkłada prędkość na dwa składniki: możesz biec szybciej
     * częściej stawiając nogi albo dłuższym krokiem. Pierwsze jest zwykle
     * tańsze energetycznie i bezpieczniejsze.
     */
    fun strideCm(meters: Double, steps: Long): Double? =
        if (steps > 0 && meters > 0) meters / steps * 100.0 else null

    /**
     * Tętno maksymalne z wieku - klasyczne 220 minus wiek.
     *
     * Wzór jest zgrubny i wiadomo, że bywa o kilkanaście uderzeń obok. Używamy
     * go, bo alternatywą jest test maksymalny, którego nikt nie robi dla samej
     * aplikacji. Do podziału na strefy wystarcza: liczy się, w której części
     * swojego zakresu się poruszasz, a nie bezwzględna wartość granicy.
     */
    fun maxHrFromAge(age: Int): Double = (220 - age).toDouble()

    /**
     * Strefa tętna 1-5 według procentu tętna maksymalnego.
     *
     * Granice 60/70/80/90 % to najczęstszy podział pięciostrefowy. Poniżej
     * pierwszej granicy wszystko jest strefą 1 - rozróżnianie „bardzo lekko"
     * od „lekko" nie wnosi nic do treningu.
     */
    fun zoneOf(bpm: Double, maxHr: Double): Int {
        if (maxHr <= 0) return 1
        val pct = bpm / maxHr
        return when {
            pct < 0.60 -> 1
            pct < 0.70 -> 2
            pct < 0.80 -> 3
            pct < 0.90 -> 4
            else -> 5
        }
    }

    /**
     * Ile sekund w każdej strefie.
     *
     * Czas przypisujemy do odstępu między próbkami, nie do samej próbki -
     * zegarek próbkuje nierówno i liczenie „ile próbek w strefie" dałoby wynik
     * zależny od gęstości pomiaru, a nie od czasu.
     */
    fun timeInZones(samples: List<Sample>, maxHr: Double): Map<Int, Long> {
        val sorted = samples.sortedBy { it.at }
        if (sorted.size < 2) return emptyMap()
        val out = sortedMapOf<Int, Long>()
        for (i in 0 until sorted.size - 1) {
            val seconds = (sorted[i + 1].at - sorted[i].at) / 1000
            if (seconds <= 0) continue
            val zone = zoneOf(sorted[i].value, maxHr)
            out[zone] = (out[zone] ?: 0L) + seconds
        }
        return out
    }

    /**
     * Zmniejsza szereg do mniej więcej [target] punktów, uśredniając koszyki.
     *
     * Godzinny bieg to kilka tysięcy próbek, a wykres ma kilkaset pikseli
     * szerokości. Rysowanie wszystkiego kosztuje, a wygląda tak samo -
     * z tą różnicą, że pojedyncze skoki czujnika stają się pionowymi kreskami.
     * Uśrednianie koszykami wygładza je przy okazji.
     */
    fun downsample(samples: List<Sample>, target: Int): List<Sample> {
        if (target <= 0 || samples.size <= target) return samples.sortedBy { it.at }
        val sorted = samples.sortedBy { it.at }
        val bucket = sorted.size.toDouble() / target
        return (0 until target).map { i ->
            val from = (i * bucket).toInt()
            val to = (((i + 1) * bucket).toInt()).coerceAtMost(sorted.size)
            val slice = sorted.subList(from, maxOf(to, from + 1))
            Sample(
                at = slice[slice.size / 2].at,
                value = slice.sumOf { it.value } / slice.size
            )
        }
    }
}
