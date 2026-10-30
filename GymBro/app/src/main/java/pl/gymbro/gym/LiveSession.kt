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
                    inventory = inventory
                )
            )
        }
    }

    /** Pierwsze nieukończone ćwiczenie, a gdy wszystkie zrobione - ostatnie. */
    fun firstUnfinished(states: List<ExerciseState>): Int {
        val i = states.indexOfFirst { !it.complete }
        return if (i >= 0) i else states.lastIndex.coerceAtLeast(0)
    }
}
