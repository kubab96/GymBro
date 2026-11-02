package pl.gymbro.nutrition

import pl.gymbro.data.NutritionTarget
import pl.gymbro.data.Phase

/**
 * Cel kaloryczny i rozbicie na makroskładniki.
 *
 * Projekt i uzasadnienie liczb: `docs/kalorie.md`, rozdział 6.
 *
 * Kolejność jest tu istotna i nieprzypadkowa: **najpierw białko, potem
 * tłuszcz z podłogą, węglowodany na końcu jako resztka.** Białko ma próg,
 * poniżej którego traci się mięśnie, tłuszcz ma próg zdrowotny, a
 * węglowodany są paliwem, którego ilość wynika z tego, co zostało. Odwrotna
 * kolejność - najpierw „40 % węglowodanów" - prowadzi do celów, w których
 * białka brakuje dokładnie wtedy, gdy jest najbardziej potrzebne.
 */
object Targets {

    /** Domyślne tempo redukcji, % masy ciała na tydzień. */
    const val CUT_RATE_PCT = 0.7

    /** Domyślne tempo nadwyżki. Wyraźnie mniejsze - nadmiar idzie w tłuszcz. */
    const val BULK_RATE_PCT = 0.35

    /** Deficyt nie przekracza tej części wydatku. */
    const val MAX_DEFICIT_FRACTION = 0.25

    /** Nadwyżka nie przekracza tej części wydatku. */
    const val MAX_SURPLUS_FRACTION = 0.20

    /** Białko w deficycie, gramy na kilogram masy beztłuszczowej (Helms 2014). */
    const val PROTEIN_CUT_PER_LBM = 2.4

    /** Białko w deficycie, gdy składu ciała nie znamy - na kilogram masy ciała. */
    const val PROTEIN_CUT_PER_KG = 2.2

    const val PROTEIN_BASE_PER_LBM = 2.0
    const val PROTEIN_BASE_PER_KG = 1.8

    /**
     * Górna granica udziału białka w kaloriach.
     *
     * Zabezpieczenie przed absurdem, nie zalecenie. Przy dużej masie i braku
     * danych o składzie ciała 2.2 g na kilogram masy daje u osoby otyłej
     * blisko połowy kalorii z białka - wtedy wiążący jest ten limit, a nie
     * gramatura.
     */
    const val MAX_PROTEIN_FRACTION = 0.40

    /** Udział tłuszczu w kaloriach, gdy podłoga nie jest wiążąca. */
    const val FAT_FRACTION = 0.25

    /** Podłoga tłuszczu, gramy na kilogram masy ciała. Konwencja zdrowotna. */
    const val FAT_FLOOR_PER_KG = 0.6

    /** Cel błonnika: gramy na 1000 kcal. */
    const val FIBER_PER_1000_KCAL = 14.0

    /** Energia z kilograma zmiany masy - ta sama konwencja, co w [Tdee]. */
    private const val KCAL_PER_KG = 7700.0

    fun defaultRatePct(phase: Phase): Double = when (phase) {
        Phase.CUT -> CUT_RATE_PCT
        Phase.BULK -> BULK_RATE_PCT
        Phase.MAINTAIN -> 0.0
    }

    /**
     * Cel kaloryczny na dobę.
     *
     * Tempo w procentach masy ciała, nie w kilogramach: ten sam ubytek
     * 0.5 kg na tydzień jest umiarkowany przy 100 kg i agresywny przy 55 kg.
     *
     * Limit deficytu i nadwyżki działa po przeliczeniu, bo to on rozstrzyga
     * przypadki skrajne: przy małej masie i agresywnym tempie procent daje
     * liczbę, której nie da się jeść.
     */
    fun kcalTarget(
        tdeeKcal: Double,
        weightKg: Double,
        phase: Phase,
        ratePct: Double? = null
    ): Double {
        val rate = (ratePct ?: defaultRatePct(phase)).coerceAtLeast(0.0)
        val perDay = rate / 100.0 * weightKg * KCAL_PER_KG / 7.0

        val kcal = when (phase) {
            Phase.MAINTAIN -> tdeeKcal
            Phase.CUT -> tdeeKcal - minOf(perDay, tdeeKcal * MAX_DEFICIT_FRACTION)
            Phase.BULK -> tdeeKcal + minOf(perDay, tdeeKcal * MAX_SURPLUS_FRACTION)
        }
        return roundToTen(kcal)
    }

    /**
     * Pełny cel: kalorie i trzy makroskładniki.
     *
     * @param leanKg masa beztłuszczowa, gdy znana - wtedy białko liczone z niej
     */
    fun target(
        fromDay: Long,
        tdeeKcal: Double,
        weightKg: Double,
        phase: Phase,
        leanKg: Double? = null,
        ratePct: Double? = null
    ): NutritionTarget {
        val kcal = kcalTarget(tdeeKcal, weightKg, phase, ratePct)
        return macros(fromDay, kcal, weightKg, phase, leanKg)
    }

    /**
     * Rozbicie gotowej liczby kalorii na makroskładniki.
     *
     * Osobno od [kcalTarget], bo cel kaloryczny bywa wpisany ręcznie -
     * i wtedy makro trzeba policzyć do liczby podanej przez użytkownika,
     * a nie do tej, która wyszłaby ze wzoru.
     */
    fun macros(
        fromDay: Long,
        kcal: Double,
        weightKg: Double,
        phase: Phase,
        leanKg: Double? = null,
        manual: Boolean = false
    ): NutritionTarget {
        val perLbm = if (phase == Phase.CUT) PROTEIN_CUT_PER_LBM else PROTEIN_BASE_PER_LBM
        val perKg = if (phase == Phase.CUT) PROTEIN_CUT_PER_KG else PROTEIN_BASE_PER_KG
        val wanted = if (leanKg != null && leanKg > 0.0) perLbm * leanKg else perKg * weightKg

        var proteinG = minOf(wanted, kcal * MAX_PROTEIN_FRACTION / 4.0).coerceAtLeast(0.0)

        val fatFloor = FAT_FLOOR_PER_KG * weightKg
        var fatG = maxOf(kcal * FAT_FRACTION / 9.0, fatFloor)

        // Resztka na węglowodany. Jeśli wychodzi ujemna - agresywna redukcja
        // przy wysokim białku, co przy małej masie zdarza się naprawdę -
        // ustępuje najpierw tłuszcz, do swojej podłogi, a dopiero potem
        // białko. Węglowodany nie mogą wyjść poniżej zera, bo cel z ujemną
        // liczbą gramów jest nie do wykonania.
        var rest = kcal - proteinG * 4.0 - fatG * 9.0
        if (rest < 0.0) {
            fatG = maxOf(fatFloor, (kcal - proteinG * 4.0) / 9.0).coerceAtLeast(0.0)
            rest = kcal - proteinG * 4.0 - fatG * 9.0
        }
        if (rest < 0.0) {
            proteinG = ((kcal - fatG * 9.0) / 4.0).coerceAtLeast(0.0)
        }

        // Węglowodany liczone z **zaokrąglonych** białka i tłuszczu, nie
        // z wartości dokładnych. Inaczej suma makro na belkach nie zgadzała
        // się z liczbą kalorii u góry ekranu o kilka kcal - a to jedyne
        // miejsce w aplikacji, gdzie użytkownik może tę sumę sprawdzić
        // w głowie, więc musi się zgadzać.
        val protein = Math.round(proteinG).toDouble()
        val fat = Math.round(fatG).toDouble()
        val carbs = Math.round(
            ((kcal - protein * 4.0 - fat * 9.0) / 4.0).coerceAtLeast(0.0)
        ).toDouble()

        return NutritionTarget(
            fromDay = fromDay,
            kcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            manual = manual
        )
    }

    /** Cel błonnika - pomocniczy, nie wchodzi do bilansu kalorii. */
    fun fiberTarget(kcal: Double): Double =
        Math.round(kcal / 1000.0 * FIBER_PER_1000_KCAL).toDouble()

    /**
     * Cel kaloryczny do dziesiątek.
     *
     * Jedna kaloria dokładności w celu dobowym jest udawaniem precyzji:
     * samo oszacowanie wydatku ma szerokość ponad stu kalorii.
     */
    private fun roundToTen(kcal: Double): Double =
        (Math.round(kcal / 10.0) * 10).toDouble()
}
