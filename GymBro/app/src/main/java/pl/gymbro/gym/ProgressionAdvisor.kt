package pl.gymbro.gym

import pl.gymbro.data.Phase
import pl.gymbro.data.PlanItem
import pl.gymbro.data.ProgressMode
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

    /**
     * [REPS_GOAL] i [HOLD] niczego w planie nie zmieniają - to informacja,
     * nie decyzja. Bez nich podwójna progresja działała po cichu: powtórzenia
     * miały rosnąć, ale nikt o tym nie mówił, a karta milczała przez kilka
     * treningów z rzędu i wyglądała jak brak progresji.
     */
    enum class Kind { WEIGHT_UP, REPS_UP, SET_UP, WEIGHT_DOWN, HOLD, REPS_GOAL }

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

        /** Powtórzenia na ciężarze roboczym, razem. */
        val totalReps: Int = topSets.sumOf { it.reps }
        val avgReps: Double = if (topSets.isEmpty()) 0.0 else totalReps.toDouble() / topSets.size

        /** Tonaż serii roboczych: suma ciężar x powtórzenia. */
        val tonnage: Double = working.sumOf { it.weightKg * it.reps }
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
        val mode = item.progress
        if (mode == ProgressMode.OFF) return null
        val now = history.firstOrNull() ?: return null

        // ---- Krok 0: czy w ogóle się wypowiadamy ----
        if (now.working.size < 2) return null
        if (now.working.size < item.sets) return null
        if (item.hasTargetWeight && now.topWeight < item.targetKg - 0.01) return null
        if (daysSincePrevious != null && daysSincePrevious > STALE_DAYS) return null

        val previous = history.getOrNull(1)
        val rir = if (rirUseless) null else now.minRir
        val step = smallestStepPercent(item, ladder, now.topWeight)

        // ---- Krok 1 i 2: co podnosić ----
        // Zakres wyczerpany liczymy z sumy powtórzeń, nie z najsłabszej serii.
        // Najsłabsza seria trzymała ciężar w miejscu przy 18/13/11 na zakresie
        // 10-12: dwie serie ponad górę zakresu nic nie znaczyły, bo trzecia
        // wyszła jedenaście.
        val atTop = rangeDone(now.topSets, item.repsFrom, item.repsTo, item.sets)
        val plenty = rir != null && rir >= RIR_TOO_LIGHT

        // Przepaść między progami (para hantli, rzadki zestaw talerzy, stos
        // wyciągu). Skok sprawdzamy, gdy tylko zwykły zakres do dwunastu jest
        // zrobiony - także wtedy, gdy plan ma już zakres wydłużony na czas
        // przechodzenia przez przepaść. Inaczej przy 8-14 czekalibyśmy na
        // 3 x 14, choć 3 x 13 już wystarcza, żeby udźwignąć wyższy próg.
        val coarse = step != null && step > COARSE_STEP_PERCENT
        if (coarse && goal != TrainingGoal.STRENGTH && mode.weight) {
            val zwyklyZakres = minOf(item.repsTo, LONG_RANGE_REPS)
            if (rangeDone(now.topSets, item.repsFrom, zwyklyZakres, item.sets)) {
                coarseByTonnage(item, now, ladder, rir)?.let { return it }
            }
        }

        // Zakres niedobity i zapasu brak - zostaje zastój, podciągnięcie planu
        // do tego, co faktycznie robisz, a w pozostałych przypadkach cel
        // powtórzeń na następny raz. Nigdy cisza: milczenie przez kilka
        // treningów wyglądało jak zepsuta progresja.
        if (!atTop && !plenty) {
            return stallAdvice(item, history, now, phase, rir)
                ?: catchUp(item, now)
                ?: repsAdvice(item, now, previous)
        }

        // Ciężar ma stać - cała progresja idzie w objętość, bez względu na
        // to, jak drobny jest próg. Tylko ze szczytu zakresu: sam zapas przy
        // niedobitym zakresie znaczy "dołóż powtórzeń", a na to jest jeszcze
        // miejsce bez ruszania planu.
        if (!mode.weight) {
            return if (atTop) {
                volumeAdvice(item, phase, goal, "szczyt zakresu, a ciężar ma tu stać")
            } else null
        }

        // Talerze się skończyły: wyżej nie ma żadnego układu. Wtedy, i tylko
        // wtedy, rosną powtórzenia ponad zwykłą granicę dwunastu, a po nich
        // serie. Dotąd była tu cisza - nextWeight nie miał dokąd pójść.
        val exhausted = ladder.isNotEmpty() && ladder.none { it > now.topWeight + 0.001 }
        if (exhausted) {
            return if (atTop) {
                exhaustedAdvice(
                    item, phase, goal,
                    repsReason = "to najcięższy ciężar z twoich talerzy - rosną powtórzenia",
                    setReason = "najcięższy ciężar z twoich talerzy i zakres wyczerpany - dochodzi seria"
                )
            } else repsAdvice(item, now, previous)
        }

        // Przepaść, której jeszcze nie da się przeskoczyć (sprawdzone wyżej):
        // traktujemy ją jak koniec talerzy - powtórzenia ponad dwanaście, potem
        // seria - aż równoważność powie, że wyższy próg jest w zasięgu.
        // Dotąd zakres stawał na dwunastu i od razu szła seria, a powtórzenia,
        // które jako jedyne przybliżają do skoku, nie rosły wcale.
        if (coarse && goal != TrainingGoal.STRENGTH && atTop && mode.reps) {
            val next = ladder.first { it > now.topWeight + 0.001 }
            val pct = ((next - now.topWeight) / now.topWeight * 100.0).roundToInt()
            val naWyzszym = repsAt(now.topSets, now.topWeight, next, rir)
            val przepasc = "przepaść ${fmt(now.topWeight)} → ${fmt(next)} kg (+$pct%), " +
                "na ${fmt(next)} kg wyszłoby około $naWyzszym powt."
            return exhaustedAdvice(
                item, phase, goal,
                repsReason = "$przepasc - rosną powtórzenia",
                setReason = "$przepasc, zakres do ${EXHAUSTED_RANGE_REPS} wyczerpany - dochodzi seria"
            )
        }

        // Regula 2-for-2 mowi o dobiciu GORY zakresu, czyli o sytuacji na styk:
        // zrobiles 12 przy zakresie 8-12, wiec poczekajmy, czy to nie byl dobry
        // dzien. Przy przekroczeniu o kilka powtorzen nie ma czego potwierdzac -
        // dwadziescia powtorzen przy gornej granicy dwanascie to nie jest
        // przypadek graniczny, a czekanie wyglada jak brak reakcji.
        val ponad = if (item.repsTo > 0) now.avgReps - item.repsTo else 0.0
        if (ponad < BIG_OVERSHOOT && !confirmedEnough(item, history, step)) {
            return catchUp(item, now) ?: Advice(
                item = item,
                kind = Kind.HOLD,
                repsTo = item.repsTo,
                reason = "szczyt zakresu pierwszy raz - jeszcze raz to samo, potem ciężar",
                preselected = false
            )
        }

        val target = nextWeight(item, now, ladder, rir)
            ?: return when {
                !coarse -> null
                mode.reps -> volumeAdvice(item, phase, goal)
                else -> coarseJump(item, now, ladder) ?: volumeAdvice(item, phase, goal)
            }

        return Advice(
            item = item,
            kind = Kind.WEIGHT_UP,
            weightKg = target,
            reason = weightReason(now, rir, atTop, bigJump = ponad >= BIG_OVERSHOOT),
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
            // Skok przez przepaść niesie też powrót zakresu do dwunastu.
            Kind.WEIGHT_UP.name, Kind.WEIGHT_DOWN.name -> item.copy(
                targetKg = a.weightKg ?: item.targetKg,
                repsTo = a.repsTo ?: item.repsTo
            )
            Kind.REPS_UP.name -> item.copy(repsTo = a.repsTo ?: item.repsTo)
            Kind.SET_UP.name -> item.copy(sets = a.sets ?: item.sets)
            else -> item
        }
    }

    /**
     * Czy czekająca propozycja mieści się jeszcze w trybie progresji pozycji.
     *
     * Propozycje czekają przy planie do decyzji, a tryb da się zmienić
     * w międzyczasie. Zejście z ciężaru i dołożenie serii zostają we
     * wszystkich trybach poza wyłączonym - blokady dotyczą tylko dokładania
     * ciężaru i powtórzeń.
     */
    fun stillFits(item: PlanItem): Boolean {
        val a = item.advice ?: return true
        return when (a.kind) {
            Kind.WEIGHT_UP.name -> item.progress.weight
            Kind.REPS_UP.name, Kind.REPS_GOAL.name -> item.progress.reps
            else -> item.progress != ProgressMode.OFF
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
        // Średnia, nie najsłabsza seria - tonaż mówi, ile naprawdę zrobiłeś.
        val over = now.avgReps - item.repsTo

        // Przy dużym przekroczeniu stałe progi się załamują: ktoś, kto robi
        // 4x20 przy zakresie 8-12, potrzebuje nie dwóch progów, tylko około
        // dwudziestu procent. Liczymy to z równoważności powtórzeń.
        val cap = from * (1.0 + (if (over >= BIG_OVERSHOOT) BIG_CAP_PERCENT else SMALL_CAP_PERCENT) / 100.0)
        val wanted = if (over >= BIG_OVERSHOOT && item.repsTo > 0) {
            val oneRm = from * (1.0 + now.avgReps / EPLEY_DIVISOR)
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

    /**
     * Gruby skok ciężaru, gdy innej drogi nie zostawiono.
     *
     * Zwykle skok ponad pięć procent zamieniamy na dłuższy zakres powtórzeń.
     * Przy wyłączonym dokładaniu powtórzeń tej wymiany nie ma, a milczenie
     * oznaczałoby stanie w miejscu bez końca - więc mówimy o następnym progu
     * wprost, z ostrzeżeniem w powodzie. Odznaczona, jak wszystko, co kosztuje
     * więcej niż spojrzenie.
     */
    private fun coarseJump(item: PlanItem, now: Outing, ladder: List<Double>): Advice? {
        val next = ladder.firstOrNull { it > now.topWeight + 0.001 } ?: return null
        return Advice(
            item = item,
            kind = Kind.WEIGHT_UP,
            weightKg = roundKg(next),
            reason = "szczyt zakresu; skok jest duży, ale powtórzeń tu nie dokładasz",
            preselected = false
        )
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
        // Podciągnięcie planu to też dołożenie ciężaru - a ten ma stać.
        if (!item.progress.weight) return null
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

    // ---- Tonaż ------------------------------------------------------------

    /**
     * Czy zakres powtórzeń jest wyczerpany na tym ciężarze.
     *
     * Suma powtórzeń co najmniej taka, jakby każda seria dobiła górę zakresu,
     * i żadna seria poniżej dołu. Przy stałym ciężarze suma powtórzeń to
     * tonaż podzielony przez ciężar, więc to jest warunek tonażowy: zrobiłeś
     * tyle pracy, ile daje pełny zakres.
     *
     * Dolna granica zostaje, bo 20/12/4 ma sumę w porządku, a ostatnia seria
     * mówi, że ciężar już teraz jest na granicy.
     *
     * Wspólne z [ProgressionEngine] - inaczej zegarek proponowałby ten sam
     * ciężar, a karta po treningu wyższy.
     */
    fun rangeDone(sets: List<SetEntry>, repsFrom: Int, repsTo: Int, plannedSets: Int = sets.size): Boolean {
        if (repsTo <= 0 || sets.isEmpty()) return false
        if (sets.size < plannedSets) return false
        if (repsFrom > 0 && sets.minOf { it.reps } < repsFrom) return false
        return sets.sumOf { it.reps } >= plannedSets * repsTo
    }

    /**
     * Cel powtórzeń na serię na następny trening: średnia plus jedno, nie
     * wyżej niż góra zakresu.
     *
     * Średnia, bo jedna słaba seria nie może cofać celu całego ćwiczenia.
     * Ta sama liczba idzie na zegarek jako propozycja pierwszej serii.
     */
    fun repsGoal(sets: List<SetEntry>, repsFrom: Int, repsTo: Int): Int {
        if (sets.isEmpty()) return repsFrom
        val avg = sets.sumOf { it.reps } / sets.size
        return (avg + 1).coerceAtLeast(repsFrom).coerceAtMost(repsTo)
    }

    /**
     * Zakres jeszcze niewyczerpany: cel powtórzeń albo „bez zmian" z powodem.
     *
     * Porównanie z poprzednim razem idzie po tonażu - przy stałym ciężarze to
     * suma powtórzeń, przy zmienionym uwzględnia też ciężar. Gdy tonaż rośnie,
     * mówimy, ile powtórzeń celować następnym razem. Gdy spadł, mówimy to
     * wprost z liczbami: bez tego wydawało się, że zrobiłeś więcej, a karta
     * milczała bez powodu.
     */
    private fun repsAdvice(item: PlanItem, now: Outing, previous: Outing?): Advice? {
        if (item.repsTo <= 0) return null
        if (!item.progress.reps) return null
        val goal = repsGoal(now.topSets, item.repsFrom, item.repsTo)
        val przy = if (item.hasTargetWeight) " przy ${fmt(now.topWeight)} kg" else ""
        val zmiana = previous?.takeIf { it.tonnage > 0.0 }?.let {
            (now.tonnage - it.tonnage) / it.tonnage * 100.0
        }
        val tonaz = if (zmiana != null) {
            "tonaż ${fmt(previous!!.tonnage)} → ${fmt(now.tonnage)} kg " +
                "(${if (zmiana >= 0) "+" else ""}${zmiana.roundToInt()}%)"
        } else ""

        if (zmiana != null && zmiana < 0.0) {
            return Advice(
                item = item,
                kind = Kind.HOLD,
                repsTo = goal,
                reason = "$tonaz - słabiej niż poprzednio, cel $goal powt.$przy zostaje",
                preselected = false
            )
        }
        return Advice(
            item = item,
            kind = Kind.REPS_GOAL,
            repsTo = goal,
            reason = listOf(
                tonaz,
                "ciężar w górę po ${item.sets} x ${item.repsTo}"
            ).filter { it.isNotBlank() }.joinToString(", "),
            preselected = true
        )
    }

    /**
     * Gruby próg, ale sprawdzony tonażem: czy na następnym progu zostaniesz
     * w zakresie.
     *
     * Powtórzenia na wyższym ciężarze szacujemy z równoważności (Epley), od
     * średniej z tego treningu. Przy parze hantli skok to dziewięć-jedenaście
     * procent i dotąd zawsze przegrywał z wydłużaniem zakresu. Przy 15/12/12
     * na 16,8 kg wychodzi osiem powtórzeń na 18,6, czyli w zakresie 8-12 -
     * a przy równym 3 x 12 siedem, więc tam rosną powtórzenia.
     */
    private fun coarseByTonnage(item: PlanItem, now: Outing, ladder: List<Double>, rir: Int?): Advice? {
        if (!item.progress.weight) return null
        val next = ladder.firstOrNull { it > now.topWeight + 0.001 } ?: return null
        val reps = repsAt(now.topSets, now.topWeight, next, rir)
        if (item.repsFrom > 0 && reps < item.repsFrom) return null
        val pct = ((next - now.topWeight) / now.topWeight * 100.0).roundToInt()
        // Zakres wydłużony na czas przepaści wraca do dwunastu razem ze skokiem:
        // był mostem, nie celem. Bez tego po skoku na 18 kg plan dalej kazałby
        // dobijać czternastu, a ciężar stałby kolejne tygodnie. Zakres z dołem
        // od dwunastu wzwyż (łydki 15-20) jest wyborem, nie mostem - zostaje.
        val zakres = if (item.repsTo > LONG_RANGE_REPS && item.repsFrom < LONG_RANGE_REPS) {
            LONG_RANGE_REPS
        } else null
        return Advice(
            item = item,
            kind = Kind.WEIGHT_UP,
            weightKg = roundKg(next),
            repsTo = zakres,
            reason = "${now.totalReps} powt. w ${now.topSets.size} seriach; skok $pct%, " +
                "z równoważności wychodzi około $reps powt. na ${fmt(next)} kg",
            preselected = true
        )
    }

    private fun fmt(v: Double): String =
        if (abs(v - Math.round(v)) < 0.01) Math.round(v).toString()
        else String.format(java.util.Locale.getDefault(), "%.1f", v)

    private fun confirmedEnough(item: PlanItem, history: List<Outing>, step: Double?): Boolean {
        if (step == null || step <= FINE_STEP_PERCENT) return true
        val now = history.firstOrNull() ?: return false
        val previous = history.getOrNull(1) ?: return false
        val sameWeight = abs(previous.topWeight - now.topWeight) < 0.01
        return sameWeight && rangeDone(previous.topSets, item.repsFrom, item.repsTo, item.sets)
    }

    private fun weightReason(now: Outing, rir: Int?, atTop: Boolean, bigJump: Boolean = false): String {
        // Przy dużym przekroczeniu skok liczy się z równoważności powtórzeń
        // i zapas go nie zmniejsza - „najmniejszy skok" przy +7% był nieprawdą.
        if (bigJump) {
            return "${now.totalReps} powt. w ${now.topSets.size} seriach, " +
                "średnio ${fmt(now.avgReps)} - ciężar z równoważności powtórzeń"
        }
        val base = when {
            rir != null && rir >= RIR_TOO_LIGHT -> "zapas ${rirLabel(rir)} w każdej serii"
            atTop -> "${now.totalReps} powt. w ${now.topSets.size} seriach, zakres wyczerpany"
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
        phase: Phase,
        goal: TrainingGoal,
        repsReason: String = "szczyt zakresu, ale skok ciężaru jest za duży"
    ): Advice? {
        if (item.repsTo in 1 until LONG_RANGE_REPS) {
            // Krótki zakres, ale powtórzeń nie wolno dokładać. Seria nie jest
            // tu zamiennikiem: dokładamy ją dopiero po wyczerpaniu zakresu,
            // a ten wyczerpany nie jest - po prostu ma stać.
            if (!item.progress.reps) return null
            return Advice(
                item = item,
                kind = Kind.REPS_UP,
                repsTo = minOf(item.repsTo + REPS_BUMP, LONG_RANGE_REPS),
                reason = repsReason,
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

    /**
     * Najcięższy układ z posiadanych talerzy, a zakres wyczerpany.
     *
     * Najpierw powtórzenia, potem seria - jak przy grubym progu, tylko
     * z wyższą granicą zakresu. Dwanaście to granica wtedy, gdy ciężar może
     * rosnąć; tutaj nie może, a Schoenfeld 2021 nie widzi różnicy w masie
     * między kilkoma a kilkunastoma powtórzeniami przy podobnym wysiłku.
     * Piętnaście to stara granica z tego dokumentu, konwencja.
     */
    private fun exhaustedAdvice(
        item: PlanItem,
        phase: Phase,
        goal: TrainingGoal,
        repsReason: String,
        setReason: String
    ): Advice? {
        if (item.progress.reps && item.repsTo in 1 until EXHAUSTED_RANGE_REPS) {
            return Advice(
                item = item,
                kind = Kind.REPS_UP,
                repsTo = minOf(item.repsTo + REPS_BUMP, EXHAUSTED_RANGE_REPS),
                reason = repsReason,
                preselected = true
            )
        }
        if (phase == Phase.CUT) return null
        if (goal == TrainingGoal.STRENGTH) return null
        if (item.sets >= MAX_SETS) return null
        return Advice(
            item = item,
            kind = Kind.SET_UP,
            sets = item.sets + 1,
            reason = setReason,
            preselected = true
        )
    }

    /**
     * Ile powtórzeń wyjdzie na ciężarze [to], sądząc po seriach na [from].
     *
     * Równoważność Epleya od średniej. Celowo nie sam tonaż: 3 x 12 na 10 kg
     * to tyle co 3 x 8 na 14 kg, a z równoważności na 14 kg wychodzi jedno
     * powtórzenie. Serie też tu nie wchodzą - czwarta seria nie sprawia, że
     * pojedyncza staje się cięższa.
     *
     * Wspólne z [ProgressionEngine], żeby zegarek i karta zgadzały się, czy
     * skok jest już w zasięgu.
     *
     * [rir] to zapas, który dolicza się do powtórzeń: 9 z zapasem 4 to
     * w praktyce 13. Bez tego seria z dużym zapasem uznawała skok za
     * niemożliwy, choć sam zapas mówi, że ciężar jest za lekki. null, gdy
     * zapasu nie ma albo nie jest wiarygodny ([rirIsUseless]).
     */
    fun repsAt(sets: List<SetEntry>, from: Double, to: Double, rir: Int? = null): Int {
        if (sets.isEmpty() || from <= 0.0 || to <= 0.0) return 0
        val avg = sets.sumOf { it.reps }.toDouble() / sets.size + (rir ?: 0).coerceAtLeast(0)
        val oneRm = from * (1.0 + avg / EPLEY_DIVISOR)
        return (EPLEY_DIVISOR * (oneRm / to - 1.0)).toInt().coerceAtLeast(0)
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
            abs(it.topWeight - now.topWeight) < 0.01 &&
                !rangeDone(it.topSets, item.repsFrom, item.repsTo, item.sets)
        }.size

        // Na redukcji utrzymanie ciężaru jest sukcesem, nie zastojem.
        val limit = if (phase == Phase.CUT) STALL_CUT else STALL_NORMAL
        if (stuck < limit) return null

        // Tonaż rośnie - to postęp wewnątrz zakresu, nie zastój.
        val oldest = history.getOrNull(stuck - 1)
        if (oldest != null && now.tonnage > oldest.tonnage + 0.01) return null

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

    /**
     * Górna granica zakresu, do której wolno go wydłużać - potem seria.
     *
     * Było 15. Schoenfeld 2021 nie znajduje strefy hipertrofii: przy podobnym
     * wysiłku masa rośnie od około 30% 1RM w górę. Ale ten sam przegląd
     * nazywa umiarkowany ciężar najbardziej efektywną drogą, a lekki -
     * bardziej męczącym i dłuższym. Dwanaście to więc wybór użytkownika
     * w granicach, które literatura dopuszcza, nie jej wynik.
     */
    private const val LONG_RANGE_REPS = 12

    private const val REPS_BUMP = 2

    /** Granica zakresu, gdy talerzy już nie ma czego dołożyć. */
    private const val EXHAUSTED_RANGE_REPS = 15

    /** Górna granica objętości: Baz-Valle podaje 12-20 serii na partię. */
    private const val MAX_SETS = 6

    private const val STALL_NORMAL = 3
    private const val STALL_CUT = 6
    private const val DELOAD_PERCENT = 10.0

    /** Po tylu dniach przerwy to powrót, nie progresja. */
    private const val STALE_DAYS = 21

    private const val MIN_RIR_SAMPLE = 6
}
