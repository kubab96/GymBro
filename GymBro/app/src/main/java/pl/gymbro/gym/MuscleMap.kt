package pl.gymbro.gym

/**
 * Przełożenie partii mięśniowych na obszary sylwetki.
 *
 * Zegarek też rysuje taką mapę, ale **zgaduje** - wnioskuje partie z ruchu
 * nadgarstka, dlatego przy jednym treningu podświetla naraz klatkę, uda
 * i plecy. My nie musimy zgadywać: mamy listę wykonanych ćwiczeń, a każde
 * ma przypisaną partię. Mapa wychodzi z tego, co naprawdę zrobiłeś.
 *
 * Dzięki temu da się też pokazać **stopień**, a nie trzy sztywne poziomy:
 * intensywność koloru idzie z liczby serii roboczych.
 */
object MuscleMap {

    /** Obszar na rysunku. Przód i tył sylwetki mają osobne zestawy. */
    enum class Region(val onFront: Boolean) {
        CHEST(true),
        ABS(true),
        SHOULDERS(true),
        BICEPS(true),
        FOREARMS(true),
        QUADS(true),
        BACK(false),
        REAR_SHOULDERS(false),
        TRICEPS(false),
        GLUTES(false),
        HAMSTRINGS(false),
        CALVES(false)
    }

    /**
     * Partie, które mapa zna - w tej pisowni, w jakiej je rozpoznaje.
     *
     * Podawane jako podpowiedzi przy tworzeniu ćwiczenia. Partia jest wolnym
     * tekstem, więc „Klata" albo „uda" zapisywały się bez słowa sprzeciwu,
     * a potem ćwiczenie nie świeciło na sylwetce po treningu.
     * Test pilnuje, żeby lista i [regionsOf] mówiły to samo.
     */
    val GROUPS = listOf(
        "klatka", "plecy", "barki", "biceps", "triceps",
        "przedramiona", "brzuch", "pośladki", "nogi", "łydki"
    )

    /**
     * Które obszary podświetla dana partia.
     *
     * Kilka partii oświetla więcej niż jeden obszar, bo nazwa z bazy jest
     * grubsza niż anatomia. „Nogi" to u nas i przód, i tył uda - rozbijanie
     * tego wymagałoby pytania przy każdym ćwiczeniu, czy to wyprost, czy
     * uginanie, a to koszt nieproporcjonalny do zysku. „Barki" świecą
     * z obu stron, bo i tak pracują w obu płaszczyznach.
     */
    fun regionsOf(muscleGroup: String): List<Region> =
        when (muscleGroup.trim().lowercase()) {
            "klatka" -> listOf(Region.CHEST)
            "plecy" -> listOf(Region.BACK)
            "barki" -> listOf(Region.SHOULDERS, Region.REAR_SHOULDERS)
            "biceps" -> listOf(Region.BICEPS)
            "triceps" -> listOf(Region.TRICEPS)
            "przedramiona" -> listOf(Region.FOREARMS)
            "brzuch" -> listOf(Region.ABS)
            "pośladki" -> listOf(Region.GLUTES)
            "łydki" -> listOf(Region.CALVES)
            "nogi" -> listOf(Region.QUADS, Region.HAMSTRINGS)
            else -> emptyList()
        }

    /**
     * Nasycenie każdego obszaru w skali 0-1.
     *
     * @param setsByGroup serie robocze na partię
     * @param fullAt ile serii oznacza pełne nasycenie
     *
     * Skala jest podawana z zewnątrz, bo te same liczby znaczą co innego
     * w różnych oknach czasu. Przy jednym treningu pełnym kolorem świeci
     * partia najmocniej obrobiona tego dnia; przy tygodniu - próg objętości
     * produktywnej, żeby kolor niósł sąd, a nie tylko porównanie wewnętrzne.
     *
     * Obszar, który dostał choć jedną serię, nigdy nie jest całkiem blady:
     * różnica między „jedna seria" a „nic" musi być widoczna.
     */
    fun intensities(setsByGroup: Map<String, Int>, fullAt: Int): Map<Region, Float> {
        val limit = fullAt.coerceAtLeast(1)
        val out = mutableMapOf<Region, Float>()
        setsByGroup.forEach { (group, sets) ->
            if (sets <= 0) return@forEach
            val value = (sets.toFloat() / limit).coerceIn(0.18f, 1f)
            regionsOf(group).forEach { region ->
                out[region] = maxOf(out[region] ?: 0f, value)
            }
        }
        return out
    }

    /** Partia z największą liczbą serii; null, gdy nic nie zrobiono. */
    fun hardestWorked(setsByGroup: Map<String, Int>): String? =
        setsByGroup.filterValues { it > 0 }.maxByOrNull { it.value }?.key
}
