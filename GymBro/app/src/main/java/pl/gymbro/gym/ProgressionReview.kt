package pl.gymbro.gym

import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.Session
import pl.gymbro.data.StoredAdvice
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.TrainingGoal

/**
 * Zbiera dane, których potrzebuje [ProgressionAdvisor], i oddaje gotową listę
 * propozycji do pokazania po treningu.
 *
 * Rozdzielone od samego doradcy celowo: doradca nie dotyka bazy i daje się
 * przetestować na wymyślonych danych, a tutaj siedzi całe ładowanie. Dzięki
 * temu reguły z `docs/progresja.md` można sprawdzić bez emulatora.
 *
 * Wołać z wątku roboczego.
 */
object ProgressionReview {

    fun forSession(
        planRepo: PlanRepository,
        sessionRepo: SessionRepository,
        profileRepo: ProfileRepository,
        gymRepo: GymRepository,
        session: Session
    ): List<ProgressionAdvisor.Advice> {
        val planId = session.planId ?: return emptyList()
        val items = planRepo.items(planId).filter { !it.noProgress }
        if (items.isEmpty()) return emptyList()

        val profile = profileRepo.get()
        val inventory = gymRepo.inventory()
        val ids = items.map { it.exerciseId }

        val thisSession = sessionRepo.setsBySessionGrouped(session.id)
        val earlier = sessionRepo.recentSessionsForMany(ids, HISTORY_DEPTH, session.id)

        // Zapas powtórzeń oceniamy raz, na całym treningu: jeśli ktoś wpisuje
        // wszędzie to samo, to nie jest pomiar i nie wolno na nim decydować.
        val useless = ProgressionAdvisor.rirIsUseless(thisSession.values.flatten())

        return items.mapNotNull { item ->
            val now = thisSession[item.exerciseId].orEmpty()
            if (now.isEmpty()) return@mapNotNull null

            val history = buildList {
                add(ProgressionAdvisor.Outing(now))
                earlier[item.exerciseId].orEmpty().forEach {
                    add(ProgressionAdvisor.Outing(it))
                }
            }

            ProgressionAdvisor.advise(
                item = item,
                history = history,
                ladder = ladderFor(item, inventory),
                goal = item.goal ?: profile.trainingGoal,
                phase = profile.phase,
                rirUseless = useless,
                daysSincePrevious = null
            )
        }
    }

    /**
     * Osiągalne ciężary dla tej pozycji. Pusta lista znaczy "dowolny".
     *
     * Ta sama droga, którą liczy ekran treningu i pilot na zegarku - propozycja
     * nie może wskazać obciążenia, którego nie da się złożyć z talerzy.
     *
     * Celowo nie wszystkie progi, a tylko te wygodne z obecnego obciążenia.
     * Propozycja "80 -> 81,4 kg" jest poprawna arytmetycznie i bezużyteczna
     * w praktyce, bo oznacza zdjęcie całego kompletu i złożenie innego.
     * Zasięg liczymy od ciężaru z planu, bo to on jest punktem wyjścia
     * następnego treningu.
     */
    private fun ladderFor(
        item: pl.gymbro.data.PlanItem,
        inventory: Inventory
    ): List<Double> {
        if (!item.hasTargetWeight) return emptyList()
        val mode = LoadMode.of(item.equipment, inventory) ?: return emptyList()
        return PlateCalculator.handyLadder(item.targetKg, mode, inventory)
            .map { it.totalKg }
    }

    /** Ile wcześniejszych sesji bierzemy pod uwagę przy liczeniu zastoju. */
    private const val HISTORY_DEPTH = 6

    /**
     * Policzenie propozycji i zapisanie ich przy pozycjach planu.
     *
     * Wołane po zakończeniu treningu, z telefonu i z zegarka. Nadpisuje
     * poprzednie propozycje dla tej jednostki: kolejny trening unieważnia
     * poprzedni, a kolejka nieaktualnych podpowiedzi sprzed tygodni byłaby
     * gorsza niż ich brak.
     *
     * Pozycje, które tym razem nie dały propozycji, czyści się tak samo -
     * stara podpowiedź przy ćwiczeniu, które właśnie zrobiłeś inaczej,
     * wprowadzałaby w błąd.
     */
    fun saveFor(
        planRepo: PlanRepository,
        sessionRepo: SessionRepository,
        profileRepo: ProfileRepository,
        gymRepo: GymRepository,
        session: Session
    ): Int {
        val planId = session.planId ?: return 0
        // Oznaczamy od razu: policzone raz znaczy policzone, nawet gdy nic
        // z tego nie wyszlo. Inaczej doliczanie wracaloby przy kazdym
        // otwarciu aplikacji i wskrzeszalo odrzucone podpowiedzi.
        sessionRepo.markAdvised(session.id)
        val found = forSession(planRepo, sessionRepo, profileRepo, gymRepo, session)
        val byItem = found.associateBy { it.item.id }
        val done = sessionRepo.setsBySessionGrouped(session.id)

        var saved = 0
        planRepo.items(planId).forEach { item ->
            val advice = byItem[item.id]
            if (advice == null) {
                // Czyścimy tylko wtedy, gdy ćwiczenie było dziś robione -
                // inaczej skasowalibyśmy podpowiedź, której nikt jeszcze
                // nie widział, bo trening dotyczył innej jednostki.
                if (done.containsKey(item.exerciseId)) planRepo.setAdvice(item.id, null)
                return@forEach
            }
            planRepo.setAdvice(
                item.id,
                StoredAdvice(
                    kind = advice.kind.name,
                    weightKg = advice.weightKg,
                    repsTo = advice.repsTo,
                    sets = advice.sets,
                    reason = advice.reason,
                    last = lastDone(done[item.exerciseId].orEmpty(), item),
                    sessionId = session.id,
                    seen = false
                )
            )
            saved += 1
        }
        return saved
    }

    /**
     * Co zrobiłeś ostatnio, jednym zdaniem.
     *
     * Bez tego propozycja wisi w próżni: "81.4 kg" nic nie znaczy, dopóki
     * nie widać, że poprzednio było 80 na dwanaście powtórzeń.
     */
    private fun lastDone(
        sets: List<pl.gymbro.data.SetEntry>,
        item: pl.gymbro.data.PlanItem
    ): String {
        val working = sets.filter { !it.isWarmup }
        if (working.isEmpty()) return ""
        val top = working.maxOf { it.weightKg }
        val atTop = working.filter { kotlin.math.abs(it.weightKg - top) < 0.01 }
        val reps = atTop.map { it.reps }
        val repsText = if (reps.distinct().size == 1) {
            "${atTop.size} x ${reps.first()}"
        } else {
            reps.joinToString("/")
        }
        val weight = if (item.hasTargetWeight) " @ ${fmt(top)} kg" else ""
        val rirs = atTop.mapNotNull { it.rir }
        val rir = if (rirs.size == atTop.size && rirs.isNotEmpty()) {
            ", zapas ${if (rirs.min() >= 3) "3+" else rirs.min()}"
        } else ""
        return "$repsText$weight$rir"
    }

    /**
     * Przyjecie propozycji zapisanej przy pozycji planu.
     *
     * Czyta z samej pozycji, nie z wyliczenia w locie: decyzja zapada czasem
     * dzien po treningu i ma dotyczyc tego, co wtedy zaproponowalismy, a nie
     * tego, co wyszloby dzisiaj.
     */
    fun accept(planRepo: PlanRepository, item: pl.gymbro.data.PlanItem) {
        if (item.advice == null) return
        val updated = ProgressionAdvisor.applyTo(item)
        if (updated !== item) planRepo.upsertItem(updated)
    }

    /** Zastosowanie przyjętej propozycji do planu. */
    fun apply(planRepo: PlanRepository, advice: ProgressionAdvisor.Advice) {
        val item = advice.item
        val updated = when (advice.kind) {
            ProgressionAdvisor.Kind.WEIGHT_UP,
            ProgressionAdvisor.Kind.WEIGHT_DOWN ->
                item.copy(targetKg = advice.weightKg ?: item.targetKg)

            ProgressionAdvisor.Kind.REPS_UP ->
                item.copy(repsTo = advice.repsTo ?: item.repsTo)

            ProgressionAdvisor.Kind.SET_UP ->
                item.copy(sets = advice.sets ?: item.sets)

            ProgressionAdvisor.Kind.HOLD -> return
        }
        planRepo.upsertItem(updated)
    }

    /**
     * Doliczenie propozycji dla ostatniego treningu, jesli ich nie ma.
     *
     * Propozycje licza sie przy nacisnieciu "Zakoncz" - ale trening nie
     * zawsze tak sie konczy. Bywa porzucony i zamkniety po osmiu godzinach,
     * bywa tez, ze liczyla je wersja aplikacji, ktora czegos jeszcze nie
     * umiala. Wtedy caly trening przepada bez zadnego wniosku.
     *
     * Liczymy wylacznie dla ostatniego zakonczonego treningu i wylacznie raz.
     *
     * @return ile propozycji zapisano; 0 takze wtedy, gdy nie bylo czego liczyc.
     */
    fun catchUpLatest(
        planRepo: PlanRepository,
        sessionRepo: SessionRepository,
        profileRepo: ProfileRepository,
        gymRepo: GymRepository
    ): Int {
        val session = sessionRepo.lastUnadvised() ?: return 0
        return saveFor(planRepo, sessionRepo, profileRepo, gymRepo, session)
    }

    /** Krótki opis zmiany, do pokazania w karcie. */
    fun describe(advice: ProgressionAdvisor.Advice): String = when (advice.kind) {
        ProgressionAdvisor.Kind.WEIGHT_UP, ProgressionAdvisor.Kind.WEIGHT_DOWN ->
            "${fmt(advice.item.targetKg)} na ${fmt(advice.weightKg ?: 0.0)} kg"

        ProgressionAdvisor.Kind.REPS_UP ->
            "zakres ${advice.item.repsFrom}-${advice.item.repsTo} " +
                "na ${advice.item.repsFrom}-${advice.repsTo}"

        ProgressionAdvisor.Kind.SET_UP ->
            "${advice.item.sets} na ${advice.sets} serie"

        ProgressionAdvisor.Kind.HOLD -> "bez zmian"
    }

    private fun fmt(v: Double): String =
        if (kotlin.math.abs(v - Math.round(v)) < 0.01) Math.round(v).toString()
        else String.format("%.1f", v)
}
