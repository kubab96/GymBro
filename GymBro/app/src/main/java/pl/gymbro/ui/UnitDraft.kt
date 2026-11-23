package pl.gymbro.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pl.gymbro.data.Exercise
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.gym.ProgressionAdvisor

/**
 * Szkic jednostki: ćwiczenia i nazwa, zmieniane na ekranie i zapisywane
 * jednym przyciskiem - tak jak Profil.
 *
 * Dnia tygodnia już tu nie ma. Był wybierany opcjonalnie przy jednostce,
 * ale jednostki i tak robi się po kolei, a dzień tylko dokładał pytanie
 * w kreatorze i linijkę na kartach. Kolumna `day` w bazie zostaje -
 * danych użytkownika nie kasujemy - tylko nic jej już nie czyta.
 *
 * Dotąd każde dotknięcie plusa szło od razu do bazy. Przy przestawianiu
 * kilku ćwiczeń naraz nie było jak się wycofać, a zegarek potrafił w tym
 * czasie dostać jednostkę w połowie przeróbki. Szkic zmienia plan dopiero
 * wtedy, gdy jest skończony.
 *
 * Propozycje progresji też idą przez szkic: „Zastosuj" zmienia liczby na
 * ekranie, a gasi propozycję w bazie dopiero zapis. Wyjście bez zapisu
 * zostawia ją do decyzji.
 *
 * Nowe ćwiczenia dostają ujemne numery - klucz listy musi być stały, a baza
 * nada prawdziwy dopiero przy zapisie.
 */
internal class UnitDraft(plan: Plan) {
    val planId = plan.id

    private var original by mutableStateOf<List<PlanItem>>(emptyList())
    private var originalName by mutableStateOf(plan.name)

    var items by mutableStateOf<List<PlanItem>>(emptyList())
        private set
    var name by mutableStateOf(plan.name)
    var loaded by mutableStateOf(false)
        private set

    private var nextTemp = -1L

    val dirty: Boolean
        get() = loaded && (items != original || name != originalName)

    /** Ile propozycji czeka w szkicu - te zastosowane albo pominięte już nie. */
    val pendingAdvice: Int get() = items.count { it.advice != null }

    /** Wczytanie z bazy. Szkicu z niezapisanymi zmianami nie nadpisuje. */
    fun load(fromDb: List<PlanItem>, plan: Plan) {
        if (dirty) return
        original = fromDb
        items = fromDb
        originalName = plan.name
        name = plan.name
        loaded = true
    }

    /** Porzucenie zmian - szkic wraca do stanu z bazy. */
    fun discard() {
        items = original
        name = originalName
    }

    fun update(updated: PlanItem) {
        // Propozycja policzona przed zmianą trybu progresji znika razem
        // z nim: "dołóż 2,5 kg" przy ćwiczeniu, w którym przed chwilą
        // wyłączono dokładanie ciężaru, wyglądałoby na zignorowane ustawienie.
        val fixed = if (ProgressionAdvisor.stillFits(updated)) updated else updated.copy(advice = null)
        items = items.map { if (it.id == fixed.id) fixed else it }
    }

    fun move(item: PlanItem, up: Boolean) {
        val i = items.indexOfFirst { it.id == item.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in items.indices) return
        items = items.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun remove(item: PlanItem) {
        items = items.filterNot { it.id == item.id }
    }

    fun add(chosen: List<Exercise>) {
        // Zakres i przerwa od razu ustawione: bez zakresu aplikacja nie ma
        // z czego proponować progresji, a 8-12 to rozsądny środek, który
        // łatwo przestawić. Ciężar pusty - nie znamy niczyjej siły.
        items = items + chosen.map { e ->
            PlanItem(
                id = nextTemp--,
                planId = planId,
                exerciseId = e.id,
                position = 0,
                targetKg = 0.0,
                sets = 3,
                repsFrom = 8,
                repsTo = 12,
                restSec = 120,
                exerciseName = e.name,
                equipment = e.equipment,
                stepKg = e.stepKg
            )
        }
    }

    /** Zastosowanie propozycji w szkicu: liczby zmieniają się od razu, zapis gasi ją w bazie. */
    fun accept(item: PlanItem) {
        items = items.map { if (it.id == item.id) ProgressionAdvisor.applyTo(it).copy(advice = null) else it }
    }

    fun skip(item: PlanItem) {
        items = items.map { if (it.id == item.id) it.copy(advice = null) else it }
    }

    fun acceptAll() { items.filter { it.advice != null }.forEach(::accept) }
    fun skipAll() { items.filter { it.advice != null }.forEach(::skip) }

    /**
     * Zapis szkicu do bazy. Blokujące - z wątku roboczego.
     *
     * Kolejność w bazie to kolejność na ekranie: przestawianie przesuwa
     * pozycje na liście, a numery nadaje dopiero zapis. Zamiana numerów
     * dwóch ćwiczeń przy każdej strzałce, jak dotąd, zostawiała dziury
     * i powtórki po usunięciu czegoś ze środka.
     */
    fun save(repo: PlanRepository) {
        val before = original.associateBy { it.id }
        val keep = items.filter { it.id > 0 }.map { it.id }.toSet()
        original.filter { it.id !in keep }.forEach { repo.deleteItem(it.id) }
        items.forEachIndexed { index, it ->
            val item = it.copy(position = index)
            if (item.id < 0) {
                repo.upsertItem(item.copy(id = 0))
            } else {
                val old = before[item.id]
                if (old == null || old.copy(advice = null) != item.copy(advice = null)) repo.upsertItem(item)
                // Propozycja zastosowana albo pominięta w szkicu gaśnie dopiero tutaj.
                if (old?.advice != null && item.advice == null) repo.setAdvice(item.id, null)
            }
        }
        if (name.isNotBlank() && name != originalName) repo.renamePlan(planId, name.trim())
    }

    /**
     * Zapis i od razu nowy punkt odniesienia z bazy. Blokujące.
     *
     * Szkic po samym [save] dalej uważa się za zmieniony, a drugi zapis
     * wstawiłby nowe ćwiczenia jeszcze raz - stąd jedno i drugie razem
     * wszędzie tam, gdzie zapis może się powtórzyć.
     */
    fun commit(repo: PlanRepository) {
        save(repo)
        saved(repo.items(planId), repo.planById(planId))
    }

    /** Po zapisie stan z bazy staje się nowym punktem odniesienia. */
    fun saved(fromDb: List<PlanItem>, plan: Plan?) {
        original = fromDb
        items = fromDb
        originalName = plan?.name ?: name
        name = originalName
    }
}
