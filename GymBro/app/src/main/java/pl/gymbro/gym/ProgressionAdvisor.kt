package pl.gymbro.gym

import pl.gymbro.data.Phase
import pl.gymbro.data.PlanItem
import pl.gymbro.data.roundKg
import pl.gymbro.data.SetEntry
import pl.gymbro.data.TrainingGoal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Propozycje zmian w planie, wyliczane po zakończonym treningu.
 *
 * Projekt i uzasadnienie liczb: `docs/progresja.md`.
 *
 * Zasada naczelna: **koszt milczenia jest wyższy niż koszt zbędnej
 * propozycji**. Propozycja, którą się ignoruje, kosztuje jedno spojrzenie;
 * propozycja, której nie było, kosztuje tygodnie stania w miejscu. Przy
 * wątpliwościach mówimy. Wyjątkiem są propozycje zejścia z ciężaru - te
 * kosztują więcej niż spojrzenie, więc przychodzą odznaczone.
 *
 * Z tej samej zasady wynika stosunek do zapasu powtórzeń: **zapas nigdy nie
 * blokuje propozycji, którą uzasadniają same powtórzenia.** Może ją
 * przyspieszyć albo zmniejszyć skok. Część osób nie wypełni go wcale, część
 * trenuje zawsze do upadku i ma stałe zero - obie muszą dostać sensowne
 * podpowiedzi.
 */
object ProgressionAdvisor {

    enum class Kind { WEIGHT_UP, REPS_UP, SET_UP, WEIGHT_DOWN, HOLD }

    data class Advice(
        val item: PlanItem,
        val kind: Kind,
        /** Nowy cel ciężaru, gdy [kind] go dotyczy. */
        val weightKg: Double? = null,
        /** Nowa górna granica zakresu powtórzeń. */
        val repsTo: Int? = null,
        /** Nowa liczba serii. */
        val sets: Int? = null,
        val reason: String,
        /** Czy zaznaczyć domyślnie. Zejścia i cięcia - nie. */
        val preselected: Boolean
    )

    /** Jedna sesja z punktu widzenia jednego ćwiczenia. */
    data class Outing(val sets: List<SetEntry>) {
        val working: List<SetEntry> = sets.filter { !it.isWarmup }
        val topWeight: Double = working.maxOfOrNull { it.weightKg } ?: 0.0
        val topSets: List<SetEntry> =
            working.filter { abs(it.weightKg - topWeight) < 0.01 }
        val minReps: Int = topSets.minOfOrNull { it.reps } ?: 0
        val minRir: Int? = if (topSets.isNotEmpty() && topSets.all { it.rir != null }) {
            topSets.minOf { it.rir!! }
        } else null
    }

    /**
     * @param history sesje z tym ćwiczeniem, od najnowszej, wraz z bieżącą.
     * @param ladder osiągalne ciężary, rosnąco. Pusta = ciężar dowolny.
     * @param rirUseless zapas bez zmienności nic nie niesie - patrz [rirIsUseless].
     */
    fun advise(
        item: PlanItem,
        history: List<Outing>,
        ladder: List<Double>,
        goal: TrainingGoal,
        phase: Phase,
        rirUseless: Boolean = false,
        daysSincePrevious: Int? = null
    ): Advice? {
        if (item.noProgress) return null
        val now = history.firstOrNull() ?: return null

        // ---- Krok 0: czy w ogóle się wypowiadamy ----
        if (now.working.size < 2) return null
        if (now.working.size < item.sets) return null
        if (item.hasTargetWeight && now.topWeight < item.targetKg - 0.01) return null
        if (daysSincePrevious != null && daysSincePrevious > STALE_DAYS) return null

        val previous = history.getOrNull(1)
        val sameWeightBefore = previous != null &&
            abs(previous.topWeight - now.topWeight) < 0.01
        if (sameWeightBefore && now.minReps < previous!!.minReps) {
            // Powtórzenia spadły na tym samym ciężarze - to zmęczenie, nie gotowość.
            return null
        }

        val rir = if (rirUseless) null else now.minRir
        val step = smallestStepPercent(item, ladder, now.topWeight)

        // ---- Krok 1 i 2: co podnosić ----
        val atTop = item.repsTo > 0 && now.minReps >= item.repsTo
        val plenty = rir != null && rir >= RIR_TOO_LIGHT

        // Zakres niedobity i zapasu brak - zostaje zastoj albo, gdy trenujesz
        // ciezej niz plan, podciagniecie planu do tego, co faktycznie robisz.
        if (!atTop && !plenty) {
            return stallAdvice(item, history, now, phase, rir) ?: catchUp(item, now)
        }

        // Przy grubym progu ciężar ustępuje objętości. Powtórzenia budują masę
        // tak samo jak ciężar (Mitchell 2012: 6.8% vs 7.2%, p=0.18), więc to
        // zamiennik, nie proteza - ale tylko wtedy, gdy celem jest masa.
        val coarse = step != null && step > COARSE_STEP_PERCENT
        if (coarse && goal != TrainingGoal.STRENGTH && atTop) {
            return volumeAdvice(item, history, phase, goal)
        }

        // Regula 2-for-2 mowi o dobiciu GORY zakresu, czyli o sytuacji na styk:
        // zrobiles 12 przy zakresie 8-12, wiec poczekajmy, czy to nie byl dobry
        // dzien. Przy przekroczeniu o kilka powtorzen nie ma czego potwierdzac -
        // dwadziescia powtorzen przy gornej granicy dwanascie to nie jest
        // przypadek graniczny, a czekanie wyglada jak brak reakcji.
        val ponad = if (item.repsTo > 0) now.minReps - item.repsTo else 0
        if (ponad < BIG_OVERSHOOT && !confirmedEnough(item, history, step)) {
            return catchUp(item, now)
        }

        val target = nextWeight(item, now, ladder, rir)
            ?: return if (coarse) volumeAdvice(item, history, phase, goal) else null

        return Advice(
            item = item,
            kind = Kind.WEIGHT_UP,
            weightKg = target,
            reason = weightReason(now, rir, atTop),
            preselected = true
        )
    }

    /**
     * Pozycja planu po przyjeciu czekajacej propozycji.
     *
     * Czysta funkcja, bez bazy, bo to miejsce latwo pomylic: rodzaj jest
     * tekstem, wiec literowka nie wywala kompilacji - po prostu po cichu
     * zmienia nie to pole, co trzeba.
     */
    fun applyTo(item: PlanItem): PlanItem {
        val a = item.advice ?: return item
        return when (a.kind) {
            Kind.WEIGHT_UP.name, Kind.WEIGHT_DOWN.name ->
                item.copy(targetKg = a.weightKg ?: item.targetKg)
            Kind.REPS_UP.name -> item.copy(repsTo = a.repsTo ?: item.repsTo)
            Kind.SET_UP.name -> item.copy(sets = a.sets ?: item.sets)
            else -> item
        }
    }

    // ---- Ciężar -----------------------------------------------------------

    private fun nextWeight(
        item: PlanItem,
        now: Outing,
        ladder: List<Double>,
        rir: Int?
    ): Double? {
        val from = now.topWeight
        if (from <= 0.0) return null
        val over = now.minReps - item.repsTo

        // Przy dużym przekroczeniu stałe progi się załamują: ktoś, kto robi
        // 4x20 przy zakresie 8-12, potrzebuje nie dwóch progów, tylko około
        // dwudziestu procent. Liczymy to z równoważności powtórzeń.
        val cap = from * (1.0 + (if (over >= BIG_OVERSHOOT) BIG_CAP_PERCENT else SMALL_CAP_PERCENT) / 100.0)
        val wanted = if (over >= BIG_OVERSHOOT && item.repsTo > 0) {
            val oneRm = from * (1.0 + now.minReps / EPLEY_DIVISOR)
            val estimate = oneRm / (1.0 + item.repsTo / EPLEY_DIVISOR)
            minOf(estimate, from * (1.0 + BIG_CAP_PERCENT / 100.0))
        } else {
            val rungs = when {
                rir != null && rir >= RIR_TOO_LIGHT -> 3
                over >= 2 || rir == 2 -> 2
                else -> 1
            }
            val byRungs = rungUp(from, ladder, rungs, item)
            minOf(byRungs, from * (1.0 + SMALL_CAP_PERCENT / 100.0))
        }

        val snapped = snap(wanted, ladder, cap)
        if (snapped <= from + 0.001) return null
        // Suma talerzy wychodzi z arytmetyki zmiennoprzecinkowej jako
        // 81.39999999999999 i taka trafiala do planu przy przyjeciu
        // propozycji. Na ekranie tego nie bylo widac, bo formatowanie tnie do
        // jednego miejsca - ale w bazie zostawal ogon, ktory potem wracal
        // w kazdym kolejnym porownaniu ciezaru.
        return roundKg(snapped)
    }

    private fun rungUp(from: Double, ladder: List<Double>, rungs: Int, item: PlanItem): Double {
        if (ladder.isEmpty()) return freeStep(from, rungs)
        val i = ladder.indexOfFirst { it > from + 0.001 }
        if (i < 0) return from
        return ladder.getOrElse(i + rungs - 1) { ladder.last() }
    }

    /**
     * Krok przy sprzęcie bez ograniczeń - wyciąg, stos, maszyna.
     *
     * Procent od obciążenia, nie stałe kilogramy: 2.5 kg to dwa procent przy
     * stu kilogramach i dwanaście przy dwudziestu. Z dolnym ogranicznikiem,
     * bo poniżej pół kilograma nie ma czym załadować.
     */
    private fun freeStep(from: Double, rungs: Int): Double {
        val krok = max(FREE_STEP_MIN, from * FREE_STEP_PERCENT / 100.0 * rungs)
        return from + (krok * 2).roundToInt() / 2.0
    }

    /**
     * Najbliższy osiągalny ciężar, ale nigdy ponad limit.
     *
     * Zaokrąglenie do najbliższego progu potrafi przeskoczyć górne
     * ograniczenie - a limit ma być limitem, nie sugestią.
     */
    private fun snap(target: Double, ladder: List<Double>, cap: Double): Double {
        if (ladder.isEmpty()) return (target * 10).roundToInt() / 10.0
        val mieszczace = ladder.filter { it <= cap + 0.001 }
        val z = if (mieszczace.isEmpty()) ladder else mieszczace
        return z.minByOrNull { abs(it - target) } ?: target
    }

    /** Najmniejszy dostępny skok jako procent obecnego ciężaru. null = dowolny. */
    private fun smallestStepPercent(
        item: PlanItem,
        ladder: List<Double>,
        from: Double
    ): Double? {
        if (!item.hasTargetWeight || from <= 0.0) return null
        // Ciężar dowolny jest praktycznie płynny, więc nigdy nie jest "gruby".
        // Stałe 2.5 kg robiło z lekkiego wyciągu sprzęt gruboziarnisty
        // i spychało go na progresję powtórzeniami, zupełnie bez powodu.
        if (ladder.isEmpty()) return FREE_STEP_PERCENT
        val up = ladder.firstOrNull { it > from + 0.001 } ?: return null
        return (up - from) / from * 100.0
    }

    /**
     * Ile sesji pod rząd na szczycie zakresu, zanim ruszymy ciężar.
     *
     * Peterson 2010 wymagał dwóch kolejnych treningów. Przy mikroładowaniu
     * (poniżej trzech procent) to zbyt ostrożne - koszt pomyłki to jedna
     * słabsza sesja. Przy grubszym progu wracamy do jego reguły.
     */
    /**
     * Plan ma dogonic to, co naprawde robisz.
     *
     * Ciezar z planu to punkt wyjscia, a nie obowiazek - na silowni zdarza sie
     * wziac wiecej, bo tak wyszlo albo bo akurat stal inny hantel. Dotad plan
     * zostawal wtedy w tyle w nieskonczonosc: trenujesz 39,1 kg, a w planie
     * dalej stoi 36,8 i nic z tego nie wynika.
     *
     * Warunek jest jeden i twardy: powtorzenia musza miescic sie w zakresie.
     * Ciezszy trening zakonczony ponizej dolnej granicy to przeciazenie,
     * nie gotowosc na wiecej - takiego ciezaru nie wpisujemy do planu.
     */
    private fun catchUp(item: PlanItem, now: Outing): Advice? {
        if (!item.hasTargetWeight) return null
        if (now.topWeight <= item.targetKg + 0.01) return null
        if (item.repsFrom > 0 && now.minReps < item.repsFrom) return null
        return Advice(
            item = item,
            kind = Kind.WEIGHT_UP,
            weightKg = roundKg(now.topWeight),
            reason = "trenujesz ciezej, niz masz w planie",
            preselected = true
        )
    }

    private fun confirmedEnough(item: PlanItem, history: List<Outing>, step: Double?): Boolean {
        if (step == null || step <= FINE_STEP_PERCENT) return true
        val now = history.firstOrNull() ?: return false
        val previous = history.getOrNull(1) ?: return false
        val sameWeight = abs(previous.topWeight - now.topWeight) < 0.01
        return sameWeight && item.repsTo > 0 && previous.minReps >= item.repsTo
    }

    private fun weightReason(now: Outing, rir: Int?, atTop: Boolean): String {
        val base = when {
            rir != null && rir >= RIR_TOO_LIGHT -> "zapas ${rirLabel(rir)} w każdej serii"
            atTop -> "${now.working.size} serie po ${now.minReps} powt., szczyt zakresu"
            else -> "${now.minReps} powt. w najsłabszej serii"
        }
        return if (rir == 0) "$base, na granicy - najmniejszy skok" else base
    }

    private fun rirLabel(rir: Int) = if (rir >= RIR_TOO_LIGHT) "3+" else rir.toString()

    // ---- Objętość ---------------------------------------------------------

    /**
     * Gdy ciężaru nie da się ruszyć drobno - rosną powtórzenia, potem serie.
     *
     * Najpierw zakres, bo to tańsze niż dokładanie serii i nie wydłuża
     * treningu. Serie dopiero, gdy zakres zrobił się na tyle długi, że dalsze
     * wydłużanie zmieniłoby charakter ćwiczenia.
     */
    private fun volumeAdvice(
        item: PlanItem,
        history: List<Outing>,
        phase: Phase,
        goal: TrainingGoal
    ): Advice? {
        if (item.repsTo in 1 until LONG_RANGE_REPS) {
            return Advice(
                item = item,
                kind = Kind.REPS_UP,
                repsTo = item.repsTo + REPS_BUMP,
                reason = "szczyt zakresu, ale skok ciężaru jest za duży",
                preselected = true
            )
        }

        // Na redukcji nie dokładamy objętości - regeneracja jest ograniczona.
        if (phase == Phase.CUT) return null
        // Dla samej siły dokładanie serii ma słabe uzasadnienie: Schoenfeld
        // 2018 nie znalazł różnicy w sile miedzy jedna a pieciu seriami.
        if (goal == TrainingGoal.STRENGTH) return null
        if (item.sets >= MAX_SETS) return null

        return Advice(
            item = item,
            kind = Kind.SET_UP,
            sets = item.sets + 1,
            reason = "zakres powtórzeń wyczerpany",
            preselected = true
        )
    }

    // ---- Zastój -----------------------------------------------------------

    private fun stallAdvice(
        item: PlanItem,
        history: List<Outing>,
        now: Outing,
        phase: Phase,
        rir: Int?
    ): Advice? {
        if (item.repsTo <= 0) return null

        // Ile sesji pod rząd stoisz na tym ciężarze bez dobicia zakresu.
        val stuck = history.takeWhile {
            abs(it.topWeight - now.topWeight) < 0.01 && it.minReps < item.repsTo
        }.size

        // Na redukcji utrzymanie ciężaru jest sukcesem, nie zastojem.
        val limit = if (phase == Phase.CUT) STALL_CUT else STALL_NORMAL
        if (stuck < limit) return null

        // Powtórzenia rosną - to postęp wewnątrz zakresu, nie zastój.
        val oldest = history.getOrNull(stuck - 1)
        if (oldest != null && now.minReps > oldest.minReps) return null

        val below = item.repsFrom > 0 && now.minReps < item.repsFrom
        val grinding = rir == 0
        if (!below && !grinding) return null

        val target = now.topWeight * (1.0 - DELOAD_PERCENT / 100.0)
        return Advice(
            item = item,
            kind = Kind.WEIGHT_DOWN,
            weightKg = (target * 10).roundToInt() / 10.0,
            reason = if (below) {
                "$stuck sesje poniżej dolnej granicy zakresu"
            } else {
                "$stuck sesje na granicy bez poprawy"
            },
            preselected = false
        )
    }

    // ---- Wiarygodność zapasu ----------------------------------------------

    /**
     * Czy zapas powtórzeń niesie jakąkolwiek informację.
     *
     * Jeśli ktoś wpisuje wszędzie zero albo wszędzie 3+, to nie jest pomiar,
     * tylko sposób wypełniania pola. Sygnał bez zmienności jest gorszy niż
     * jego brak, bo udaje wiedzę - wracamy wtedy do samych powtórzeń.
     */
    fun rirIsUseless(all: List<SetEntry>): Boolean {
        val values = all.filter { !it.isWarmup }.mapNotNull { it.rir }
        if (values.size < MIN_RIR_SAMPLE) return true
        return values.distinct().size <= 1
    }

    // ---- Liczby -----------------------------------------------------------

    /** Zapas, od którego ciężar uznajemy za zdecydowanie za lekki. */
    private const val RIR_TOO_LIGHT = 3

    /** Powyżej tego procentu skok ciężaru jest za gruby - patrz docs. */
    private const val COARSE_STEP_PERCENT = 5.0

    /** Poniżej tego procentu nie wymagamy potwierdzenia z drugiej sesji. */
    private const val FINE_STEP_PERCENT = 3.0

    private const val SMALL_CAP_PERCENT = 5.0
    private const val BIG_CAP_PERCENT = 10.0

    /** Od ilu powtórzeń ponad zakres liczymy ciężar z równoważności. */
    private const val BIG_OVERSHOOT = 3

    private const val EPLEY_DIVISOR = 30.0

    private const val FREE_STEP_PERCENT = 2.5
    private const val FREE_STEP_MIN = 0.5

    /** Zakres dłuższy niż to - dokładamy serię zamiast kolejnych powtórzeń. */
    private const val LONG_RANGE_REPS = 15

    private const val REPS_BUMP = 2

    /** Górna granica objętości: Baz-Valle podaje 12-20 serii na partię. */
    private const val MAX_SETS = 6

    private const val STALL_NORMAL = 3
    private const val STALL_CUT = 6
    private const val DELOAD_PERCENT = 10.0

    /** Po tylu dniach przerwy to powrót, nie progresja. */
    private const val STALE_DAYS = 21

    private const val MIN_RIR_SAMPLE = 6
}
