package pl.gymbro.gym

import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.ProgressMode
import pl.gymbro.data.SetEntry
import kotlin.math.abs

/**
 * Silnik progresji - proponuje ciężar na dzisiejszą sesję.
 *
 * Metoda: **podwójna progresja**. Przy stałym ciężarze dokładasz powtórzeń, aż
 * dojdziesz do górnej granicy zakresu we wszystkich seriach roboczych; dopiero
 * wtedy ciężar idzie w górę, a powtórzenia wracają na dół zakresu. To jest
 * najprostszy schemat, który działa długo i nie wymaga testów maksymalnych.
 *
 * Dwie rzeczy odróżniają to od typowych kalkulatorów:
 *
 * **Podbicie idzie na realny próg z Twoich talerzy**, a nie o sztywne 2,5 kg.
 * Przy jednym ćwiczeniu następny możliwy skok to +1,5%, przy innym +15% - i to
 * drugie bywa powodem, dla którego progresja staje.
 *
 * **Zastój jest wykrywany po liczbie sesji na tym samym ciężarze**, a nie po
 * samym nieosiągnięciu zakresu. Jedna słabsza sesja to szum; cztery z rzędu
 * to sygnał.
 *
 * Czysty Kotlin, bez zależności od Androida - testowalny na JVM.
 */
object ProgressionEngine {

    /** Po tylu sesjach bez ruchu uznajemy ciężar za zastój. */
    const val STALL_SESSIONS = 4

    /** O tyle schodzimy przy deloadzie. */
    const val DELOAD_FACTOR = 0.9

    /** Krok dla sprzętu, którego nie składa się z talerzy (wyciąg, maszyna). */
    const val FREE_STEP_KG = 2.5

    enum class Reason {
        /** Brak historii - bierzemy ciężar z planu. */
        FIRST_TIME,

        /** Zakres powtórzeń jeszcze niewyrobiony: ten sam ciężar, więcej powtórzeń. */
        HOLD,

        /** Komplet powtórzeń na górze zakresu - czas na podbicie. */
        INCREASE,

        /** Seria była wyraźnie zbyt lekka wg RIR, mimo niepełnego zakresu. */
        INCREASE_EASY,

        /** Spadek poniżej dolnej granicy zakresu - cofamy się, żeby ruszyć dalej. */
        DELOAD,

        /** Długo bez postępu - cofnięcie i ponowne podejście. */
        STALLED,

        /**
         * To samo, co ostatnio, bo tak ustawiono ćwiczenie - patrz [ProgressMode].
         * Osobno od [HOLD]: tamto znaczy "dołóż powtórzeń", a tu nie ma czego dokładać.
         */
        KEEP
    }

    data class Suggestion(
        val weightKg: Double,
        val reps: Int,
        val reason: Reason,
        val note: String,
        /** Załadunek, jeśli sprzęt składa się z talerzy. */
        val loading: Loading? = null
    )

    /**
     * @param history sesje z tym ćwiczeniem, od najnowszej. Każda to lista serii.
     * @param progress co w tym ćwiczeniu wolno podnosić. Blokada dotyczy dokładania:
     *   zejście po spadku poniżej zakresu zostaje w każdym trybie poza wyłączonym.
     */
    fun suggest(
        equipment: Equipment,
        targetKg: Double,
        repsFrom: Int,
        repsTo: Int,
        history: List<List<SetEntry>>,
        inventory: Inventory,
        progress: ProgressMode = ProgressMode.ALL
    ): Suggestion {
        val mode = LoadMode.of(equipment, inventory)
        val last = history.firstOrNull()?.filter { !it.isWarmup }.orEmpty()

        // Bez zakresu powtórzeń podwójna progresja nie ma na czym się oprzeć -
        // nie wiadomo, co znaczy "wyrobiony zakres". Powtarzamy wtedy ostatnie
        // obciążenie i zostawiamy decyzję użytkownikowi, zamiast zgadywać.
        if (repsTo <= 0 || repsFrom <= 0) {
            val w = last.maxOfOrNull { it.weightKg } ?: targetKg
            val r = last.minOfOrNull { it.reps } ?: 0
            return Suggestion(
                weightKg = w,
                reps = r,
                reason = if (last.isEmpty()) Reason.FIRST_TIME else Reason.HOLD,
                note = "Bez ustawionego zakresu powtórzeń nie podpowiadam progresji.",
                loading = if (mode != null) PlateCalculator.nearest(w, mode, inventory) else null
            )
        }

        if (last.isEmpty()) {
            val resolved = PlateCalculator.resolve(targetKg, equipment, inventory)
            return Suggestion(
                weightKg = resolved.actualKg,
                reps = repsFrom,
                reason = Reason.FIRST_TIME,
                note = "Pierwszy raz w tym ćwiczeniu - ciężar z planu.",
                loading = resolved.loading
            )
        }

        // Ciężar roboczy to najcięższa seria sesji; lżejsze traktujemy jako dojściowe.
        val workingWeight = last.maxOf { it.weightKg }
        val workingSets = last.filter { abs(it.weightKg - workingWeight) < 0.01 }
        val minReps = workingSets.minOf { it.reps }
        val rirs = workingSets.mapNotNull { it.rir }

        // Ile sesji z rzędu stoisz na tym samym ciężarze.
        val streak = history.takeWhile { session ->
            val w = session.filter { !it.isWarmup }.maxOfOrNull { it.weightKg }
            w != null && abs(w - workingWeight) < 0.01
        }.size

        val allAtTop = minReps >= repsTo
        val belowBottom = minReps < repsFrom
        // RIR 3 i więcej w każdej serii roboczej znaczy, że ciężar jest za lekki,
        // nawet jeśli nie wyrobiłeś jeszcze górnej granicy powtórzeń.
        val tooEasy = rirs.size == workingSets.size && rirs.isNotEmpty() && rirs.min() >= 3

        // Wyłączona progresja: nie liczymy niczego, powtarzasz ostatni trening.
        // Bez tego ćwiczenie rozgrzewkowe i tak dostawało co tydzień jedno
        // powtórzenie więcej - wyciszone były tylko propozycje po treningu.
        if (progress == ProgressMode.OFF) {
            return keep(
                equipment, workingWeight, minReps.coerceAtMost(repsTo), inventory,
                "Progresja wyłączona - to samo, co ostatnio."
            )
        }

        return when {
            // Komplet powtórzeń przy ciężarze, który ma stać: nie ma czego
            // dokładać ani w górę, ani w zakresie. Przed regułą zastoju, bo
            // stanie na jednym ciężarze jest tu zamiarem, nie problemem.
            allAtTop && !progress.weight -> keep(
                equipment, workingWeight, repsTo, inventory,
                "Komplet $repsTo powtórzeń. Ciężar w tym ćwiczeniu zostaje."
            )

            allAtTop -> increase(
                equipment, workingWeight, repsFrom, inventory, mode,
                Reason.INCREASE,
                "Komplet $repsTo powtórzeń w każdej serii - czas na więcej."
            )

            tooEasy && progress.weight -> increase(
                equipment, workingWeight, repsFrom, inventory, mode,
                Reason.INCREASE_EASY,
                "RIR 3 i więcej w każdej serii - ciężar jest za lekki."
            )

            belowBottom && streak >= 2 -> deload(
                equipment, workingWeight, repsFrom, inventory, mode,
                Reason.DELOAD,
                "Drugi raz poniżej $repsFrom powtórzeń - cofnięcie pozwoli ruszyć dalej."
            )

            // Zastój to brak ruchu ciężaru - przy ciężarze, który ma stać,
            // ta reguła zgłaszałaby jako problem dokładnie to, o co proszono.
            streak >= STALL_SESSIONS && progress.weight -> deload(
                equipment, workingWeight, repsFrom, inventory, mode,
                Reason.STALLED,
                "$streak sesje na tym samym ciężarze bez wyrobienia zakresu."
            )

            // Powtórzeń nie dokładamy: zostaje to, co wyszło ostatnio, a ciężar
            // ruszy dopiero wtedy, gdy sam dobijesz do góry zakresu.
            !progress.reps -> keep(
                equipment, workingWeight, minReps.coerceAtMost(repsTo), inventory,
                "Ten sam ciężar i te same powtórzenia. " +
                    "Ciężar wzrośnie po $repsTo w każdej serii."
            )

            else -> {
                val resolved = PlateCalculator.resolve(workingWeight, equipment, inventory)
                Suggestion(
                    weightKg = workingWeight,
                    reps = (minReps + 1).coerceAtMost(repsTo),
                    reason = Reason.HOLD,
                    note = "Ten sam ciężar, dołóż powtórzeń. Cel: $repsTo w każdej serii.",
                    loading = resolved.loading
                )
            }
        }
    }

    /** Ten sam ciężar i podane powtórzenia - bez dokładania czegokolwiek. */
    private fun keep(
        equipment: Equipment,
        weight: Double,
        reps: Int,
        inv: Inventory,
        note: String
    ): Suggestion = Suggestion(
        weightKg = weight,
        reps = reps,
        reason = Reason.KEEP,
        note = note,
        loading = PlateCalculator.resolve(weight, equipment, inv).loading
    )

    private fun increase(
        equipment: Equipment,
        current: Double,
        repsFrom: Int,
        inv: Inventory,
        mode: LoadMode?,
        reason: Reason,
        note: String
    ): Suggestion {
        if (mode != null) {
            val next = PlateCalculator.nextUp(current, mode, inv)
            if (next == null) {
                // Wyczerpany zestaw talerzy. Proponowanie ciężaru, którego nie da się
                // złożyć, byłoby gorsze niż brak propozycji - mówimy wprost, co blokuje.
                return Suggestion(
                    weightKg = current,
                    reps = repsFrom,
                    reason = Reason.HOLD,
                    note = "To najcięższy układ, jaki złożysz z posiadanych talerzy. " +
                        "Żeby iść dalej, dołóż obciążenia albo zwiększ liczbę powtórzeń.",
                    loading = PlateCalculator.nearest(current, mode, inv)
                )
            }
            val pct = (next.totalKg - current) / current * 100
            return Suggestion(
                weightKg = next.totalKg,
                reps = repsFrom,
                reason = reason,
                note = note + "  Skok %+.1f%%.".format(pct),
                loading = next
            )
        }

        // Sprzęt nieładowany talerzami - stały krok, bez układu talerzy.
        val weight = current + FREE_STEP_KG
        val pct = (weight - current) / current * 100

        return Suggestion(
            weightKg = weight,
            reps = repsFrom,
            reason = reason,
            note = note + "  Skok %+.1f%%.".format(pct),
            loading = null
        )
    }

    private fun deload(
        equipment: Equipment,
        current: Double,
        repsFrom: Int,
        inv: Inventory,
        mode: LoadMode?,
        reason: Reason,
        note: String
    ): Suggestion {
        val target = current * DELOAD_FACTOR
        val resolved = if (mode != null) {
            // Schodzimy do najbliższego osiągalnego progu, ale NIGDY nie wyżej
            // niż obecny ciężar - inaczej "deload" mógłby podbić obciążenie.
            PlateCalculator.nearest(target, mode, inv)
                ?.takeIf { it.totalKg < current - 0.01 }
                ?: PlateCalculator.nextDown(current, mode, inv)
        } else null

        val weight = resolved?.totalKg ?: (current - FREE_STEP_KG).coerceAtLeast(0.0)

        return Suggestion(
            weightKg = weight,
            reps = repsFrom,
            reason = reason,
            note = note,
            loading = resolved
        )
    }
}
