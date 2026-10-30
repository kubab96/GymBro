package pl.gymbro.gym

import pl.gymbro.data.SetEntry

/**
 * Złożenie tego, co zapisałeś, z tym, co zmierzył zegarek.
 *
 * Każde z tych źródeł osobno jest ślepe na połowę treningu. Zegarek wie, jak
 * biło Ci serce, ale nie ma pojęcia, czy robiłeś przysiady, czy uginania.
 * Aplikacja wie co do kilograma, co podniosłeś, ale nie wie, ile Cię to
 * kosztowało. Łączy je jedna rzecz: znacznik czasu każdej serii.
 *
 * Z tego wychodzi liczba, której nie da się dostać znikąd indziej - **tętno
 * przy konkretnym ćwiczeniu**. Pokazuje, co naprawdę Cię męczy, a co tylko
 * wygląda ciężko: przysiad i wyciskanie o tym samym tonażu potrafią różnić
 * się o trzydzieści uderzeń.
 */
object StrengthAnalysis {

    /**
     * Ile czasu wokół zapisu serii uznajemy za jej wysiłek.
     *
     * Zapisujesz serię zaraz po jej zakończeniu, a tętno ma opóźnienie -
     * szczyt przychodzi kilkanaście sekund po ostatnim powtórzeniu. Okno
     * w obie strony łapie i rozbieg, i to opóźnienie. Półtorej minuty byłoby
     * już przerwą, pół minuty gubiłoby szczyt przy rzadkim próbkowaniu.
     */
    const val SET_WINDOW_MS = 45_000L

    /** Jedno ćwiczenie w ramach sesji: Twoje serie plus tętno z zegarka. */
    data class Block(
        val exerciseId: Long,
        val name: String,
        val sets: List<SetEntry>,
        /** Średnia ze szczytów tętna poszczególnych serii. */
        val hrAvg: Double?,
        /** Najwyższe tętno, jakie wypadło przy którejkolwiek serii. */
        val hrMax: Double?
    ) {
        val tonnage: Double get() = sets.sumOf { it.weightKg * it.reps }
        val topWeight: Double get() = sets.maxOfOrNull { it.weightKg } ?: 0.0
        val totalReps: Int get() = sets.sumOf { it.reps }
        val from: Long get() = sets.minOf { it.doneAt }
        val to: Long get() = sets.maxOf { it.doneAt }
    }

    /**
     * Szczyt tętna wokół jednej serii.
     *
     * Szczyt, nie średnia: seria trwa kilkadziesiąt sekund, a okno obejmuje
     * też fragment przerwy. Średnia rozmyłaby wysiłek w odpoczynku, a chodzi
     * właśnie o to, jak wysoko tętno podbiło.
     */
    fun setPeakHr(doneAt: Long, heartRate: List<RunAnalysis.Sample>): Double? =
        heartRate
            .filter { it.at in (doneAt - SET_WINDOW_MS)..(doneAt + SET_WINDOW_MS) }
            .maxOfOrNull { it.value }

    /**
     * Ćwiczenia sesji w kolejności wykonywania, z tętnem z zegarka.
     *
     * Rozgrzewki pomijamy: nie są pracą, a ich lekkie tętno zaniżałoby obraz
     * ćwiczenia. Kolejność bierzemy z pierwszej serii każdego ćwiczenia, nie
     * z planu - liczy się to, co zrobiłeś, a nie to, co było zapisane.
     */
    fun blocks(
        sets: List<SetEntry>,
        names: Map<Long, String>,
        heartRate: List<RunAnalysis.Sample> = emptyList()
    ): List<Block> =
        sets.filterNot { it.isWarmup }
            .groupBy { it.exerciseId }
            .map { (id, own) ->
                val ordered = own.sortedBy { it.doneAt }
                val peaks = ordered.mapNotNull { setPeakHr(it.doneAt, heartRate) }
                Block(
                    exerciseId = id,
                    name = names[id] ?: "Ćwiczenie $id",
                    sets = ordered,
                    hrAvg = peaks.takeIf { it.isNotEmpty() }?.average(),
                    hrMax = peaks.maxOrNull()
                )
            }
            .sortedBy { it.from }
}
