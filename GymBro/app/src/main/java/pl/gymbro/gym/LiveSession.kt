package pl.gymbro.gym

import pl.gymbro.data.Inventory
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.Session
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SetEntry

/**
 * Stan trwającego treningu: co jest w planie, co już zrobione, co proponujemy.
 *
 * Mieszkało to wewnątrz ekranu treningu, dopóki ekran był jedynym miejscem,
 * które o to pytało. Odkąd serie można zapisywać także z zegarka, pytają dwa
 * niezależne wejścia - a dwa miejsca liczące propozycję ciężaru rozjechałyby
 * się przy pierwszej zmianie w progresji. Dlatego liczy to jedno miejsce,
 * nieświadome tego, kto pyta.
 */
object LiveSession {

    data class ExerciseState(
        val item: PlanItem,
        val doneSets: List<SetEntry>,
        val lastTime: List<SetEntry>,
        val bestWeight: Double?,
        val suggestion: ProgressionEngine.Suggestion
    ) {
        val complete: Boolean get() = doneSets.size >= item.sets

        /**
         * Ciężar, który podstawiamy przy następnej serii.
         *
         * W trakcie ćwiczenia liczy się to, co faktycznie podniosłeś przed
         * chwilą, a nie propozycja sprzed pierwszej serii: jeśli zszedłeś
         * z ciężaru, kolejna seria ma zaczynać od tej niższej wartości.
         */
        /**
         * Ciężar, który podstawiamy przy następnej serii.
         *
         * W trakcie ćwiczenia liczy się to, co faktycznie podniosłeś przed
         * chwilą: jeśli zszedłeś z ciężaru, kolejna seria zaczyna od tej
         * niższej wartości. Zanim padnie pierwsza seria - ciężar z planu.
         *
         * Nie propozycja progresji. Plan jest tym, co ustawiłeś świadomie,
         * i to on ma być widoczny na obu ekranach; progresja licząca po cichu
         * swoje dawała dwie różne liczby w dwóch miejscach i wyglądała jak
         * brak synchronizacji. Zmiany wpisane w trakcie treningu zostają
         * w tym treningu i nie przenoszą się na następny.
         */
        val nextWeight: Double get() = doneSets.lastOrNull()?.weightKg
            ?: item.targetKg.takeIf { it > 0.0 }
            ?: suggestion.weightKg

        val nextReps: Int get() = doneSets.lastOrNull()?.reps ?: suggestion.reps
    }

    /**
     * Czyta stan wszystkich ćwiczeń jednostki.
     *
     * Wywoływane po każdej zapisanej serii, więc zapytania są zbiorcze:
     * cztery na całe przeładowanie zamiast dziewięciu na ćwiczenie. Koszt
     * inaczej mnożyłby się przez liczbę ćwiczeń i rósł z historią.
     *
     * Wywołuj z wątku roboczego - metoda sama nie przełącza kontekstu, żeby
     * nie narzucać go wywołującemu.
     */
    fun load(
        planRepo: PlanRepository,
        sessionRepo: SessionRepository,
        session: Session,
        inventory: Inventory
    ): List<ExerciseState> {
        val planId = session.planId ?: return emptyList()
        val planItems = planRepo.items(planId)
        val ids = planItems.map { it.exerciseId }

        val doneByExercise = sessionRepo.setsBySessionGrouped(session.id)
        val historyByExercise = sessionRepo.recentSessionsForMany(ids, 6, session.id)
        val bestByExercise = sessionRepo.bestWeights(ids)

        return planItems.map { item ->
            val history = historyByExercise[item.exerciseId].orEmpty()
            ExerciseState(
                item = item,
                doneSets = doneByExercise[item.exerciseId].orEmpty(),
                lastTime = history.firstOrNull()?.filter { !it.isWarmup }.orEmpty(),
                bestWeight = bestByExercise[item.exerciseId],
                suggestion = ProgressionEngine.suggest(
                    equipment = item.equipment,
                    targetKg = item.targetKg,
                    repsFrom = item.repsFrom,
                    repsTo = item.repsTo,
                    history = history,
                    inventory = inventory,
                    progress = item.progress
                )
            )
        }
    }

    /** Pierwsze nieukończone ćwiczenie, a gdy wszystkie zrobione - ostatnie. */
    fun firstUnfinished(states: List<ExerciseState>): Int {
        val i = states.indexOfFirst { !it.complete }
        return if (i >= 0) i else states.lastIndex.coerceAtLeast(0)
    }

    /**
     * Które ćwiczenie pokazać po przeładowaniu stanu - ekran idzie tam,
     * gdzie wylądowała najnowsza seria.
     *
     * Seria mogła przyjść z telefonu albo z zegarka, a trening prowadzi
     * zegarek. Zasada "zostań na bieżącym, dopóki nie jest domknięte"
     * trzymała telefon na ćwiczeniu, którego nikt nie robił - do tego
     * z numerem zostawionym przez poprzedni trening - i ekran ruszał się
     * dopiero po naciśnięciu "Następne". Teraz:
     *  - nowy trening: pierwsze niedokończone,
     *  - nowa seria w ćwiczeniu niedokończonym: to ćwiczenie,
     *  - nowa seria domknęła ćwiczenie: następne niedokończone za nim
     *    (jak [advance] na zegarku), a gdy za nim nic - od początku,
     *  - bez nowej serii (poprawka, kasowanie, pauza): zostajemy.
     *
     * @param complete czy ćwiczenie ma komplet serii, po kolei
     * @param newest ćwiczenie z serią nowszą niż przed przeładowaniem, albo null
     */
    fun follow(complete: List<Boolean>, newest: Int?, current: Int, fresh: Boolean): Int {
        if (complete.isEmpty()) return 0
        fun firstOpen() = complete.indexOfFirst { !it }.takeIf { it >= 0 } ?: complete.lastIndex
        if (fresh) return firstOpen()
        if (newest == null || newest !in complete.indices) return current.coerceIn(0, complete.lastIndex)
        if (!complete[newest]) return newest
        val after = (newest + 1 until complete.size).firstOrNull { !complete[it] }
        if (after != null) return after
        return complete.indexOfFirst { !it }.takeIf { it >= 0 } ?: newest
    }

    /** Ćwiczenie z serią zapisaną później niż [since], albo null. */
    fun newestAfter(states: List<ExerciseState>, since: Long): Int? {
        var best: Int? = null
        var at = since
        states.forEachIndexed { i, st ->
            st.doneSets.forEach { set ->
                if (set.doneAt > at) {
                    at = set.doneAt
                    best = i
                }
            }
        }
        return best
    }
}
