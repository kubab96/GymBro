package pl.gymbro.gym

import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plate
import pl.gymbro.data.roundKg
import kotlin.math.abs

/**
 * Kalkulator obciążeń dla dowolnego zestawu talerzy.
 *
 * Nie zakłada niczego o nominałach ani o gryfie - wszystko bierze z [Inventory],
 * które pochodzi z bazy i jest edytowalne. Podajemy załadunek, a ciężar z niego
 * wynika, więc rozjazd między opisem a rzeczywistością jest niemożliwy.
 *
 * Respektuje też to, że nie każdy talerz pasuje na każdy sprzęt - np. talerze,
 * których otwór nie mieści się na gryf, są dostępne wyłącznie dla hantli.
 */

enum class LoadMode {
    /** Ciężar = gryf + 2 × strona. */
    BARBELL,

    /** Dwa hantle naraz, ciężar podawany na jeden hantel. Uchwytu nie liczymy. */
    DUMBBELL_PAIR,

    /** Jeden hantel. Ciężar na hantel. */
    DUMBBELL_SINGLE;

    companion object {
        /** null dla sprzętu, którego ciężaru nie składa się z talerzy. */
        fun of(equipment: Equipment): LoadMode? = when (equipment) {
            Equipment.BARBELL -> BARBELL
            Equipment.DUMBBELL_PAIR -> DUMBBELL_PAIR
            Equipment.DUMBBELL_SINGLE -> DUMBBELL_SINGLE
            else -> null
        }

        /**
         * Wariant uwzględniający tryb obciążeń.
         *
         * Przy nieograniczonych ciężarach nie ma czego liczyć z talerzy - ciężar
         * zmienia się stałym krokiem, tak jak przy wyciągu. Jedno sprawdzenie
         * tutaj sprawia, że cała reszta aplikacji obsługuje ten przypadek bez zmian.
         */
        fun of(equipment: Equipment, inv: Inventory): LoadMode? =
            if (inv.unlimited) null else of(equipment)
    }
}

data class Loading(
    val totalKg: Double,
    /** Talerze na jedną stronę gryfu lub uchwytu. */
    val perSide: Map<Double, Int>
) {
    fun describe(mode: LoadMode, inv: Inventory): String {
        if (perSide.isEmpty()) {
            return if (mode == LoadMode.BARBELL) "sam gryf ${fmt(inv.barKg)} kg" else "sam uchwyt"
        }
        val plates = perSide.entries
            .sortedByDescending { it.key }
            .flatMap { (kg, n) -> List(n) { fmt(kg) } }
            .joinToString(" + ")
        return if (mode == LoadMode.BARBELL) {
            "gryf ${fmt(inv.barKg)} + $plates kg / stronę"
        } else {
            "$plates kg / stronę uchwytu"
        }
    }

    companion object {
        fun fmt(v: Double): String =
            if (abs(v - Math.round(v)) < 0.001) Math.round(v).toString()
            else "%.2f".format(v).trimEnd('0').trimEnd('.', ',')
    }
}

object PlateCalculator {

    /**
     * Ile sztuk danego talerza można użyć na jedną stronę, żeby załadunek był
     * symetryczny i mieścił się w posiadanym zestawie.
     *
     *  - sztanga: 2 strony          → count / 2
     *  - jeden hantel: 2 strony     → count / 2
     *  - para hantli: 4 strony      → count / 4
     *
     * Ten ostatni przypadek zaskakuje: przy parze hantli każdy talerz zużywa się
     * w czterech egzemplarzach naraz, więc rozdzielczość progów spada czterokrotnie.
     */
    private fun maxPerSide(plate: Plate, mode: LoadMode): Int = when (mode) {
        LoadMode.BARBELL -> if (!plate.fitsBarbell) 0 else plate.count / 2
        LoadMode.DUMBBELL_SINGLE -> plate.count / 2
        LoadMode.DUMBBELL_PAIR -> plate.count / 4
    }

    private fun baseKg(mode: LoadMode, inv: Inventory): Double =
        if (mode == LoadMode.BARBELL) inv.barKg else 0.0

    /**
     * Ile krążków na jedną stronę wolno przełożyć, żeby zmiana ciężaru była
     * zmianą, a nie rozkładaniem sztangi od zera.
     *
     * Liczbowo najmniejszy krok bardzo często wymaga zdjęcia wszystkiego i
     * złożenia innego kompletu. Przy zestawie 10/5/3,4/1,15 kg przejście
     * z 80 na 81,4 kg to zdjęcie piątki i założenie 3,4 + 1,15 + 1,15 -
     * cztery krążki ruszone po to, żeby zyskać 1,4 kg. Nikt tego nie robi
     * między seriami, więc aplikacja nie ma prawa tego proponować.
     */
    private const val HANDY_SWAP = 2


    /** Wszystkie symetryczne komplety talerzy, także te dające ten sam ciężar. */
    private fun loadings(mode: LoadMode, inv: Inventory): List<Loading> {
        val usable = inv.plates
            .map { it to maxPerSide(it, mode) }
            .filter { it.second > 0 }
            .sortedByDescending { it.first.kg }

        val out = mutableListOf<Loading>()

        fun recurse(index: Int, chosen: MutableMap<Double, Int>) {
            if (index == usable.size) {
                val perSideSum = chosen.entries.sumOf { it.key * it.value }
                // Zaokraglenie tutaj, a nie u odbiorcow: to jest jedyne miejsce,
                // w ktorym ciezar powstaje z sumowania talerzy, wiec jedyne,
                // w ktorym moze sie urodzic ogon zmiennoprzecinkowy.
                out += Loading(roundKg(baseKg(mode, inv) + 2 * perSideSum), chosen.toMap())
                return
            }
            val (plate, max) = usable[index]
            for (n in 0..max) {
                if (n > 0) chosen[plate.kg] = n else chosen.remove(plate.kg)
                recurse(index + 1, chosen)
            }
            chosen.remove(plate.kg)
        }
        recurse(0, mutableMapOf())
        return out
    }

    /** Wszystkie ciężary, jakie realnie da się złożyć z posiadanego sprzętu. */
    fun achievable(mode: LoadMode, inv: Inventory): List<Loading> =
        // Różne komplety talerzy mogą dać ten sam ciężar - zostawiamy ten
        // z mniejszą liczbą krążków, bo szybciej się go składa.
        loadings(mode, inv)
            .groupBy { Math.round(it.totalKg * 100) }
            .map { (_, v) -> v.minBy { l -> l.perSide.values.sum() } }
            .sortedBy { it.totalKg }

    /** Ile krążków na stronę trzeba ruszyć, żeby przejść z jednego kompletu na drugi. */
    fun swapCost(from: Loading, to: Loading): Int =
        (from.perSide.keys + to.perSide.keys).sumOf {
            abs((to.perSide[it] ?: 0) - (from.perSide[it] ?: 0))
        }

    /**
     * Progi osiągalne z [current] bez przekładania całej sztangi.
     *
     * Dla każdego ciężaru liczy się najtańszy sposób jego złożenia, nie ten
     * z najmniejszą liczbą krążków - dołożenie dwóch małych talerzy do tego,
     * co już leży, bije komplet "elegancki", ale wymagający zdjęcia reszty.
     *
     * Zawiera sam [current] (koszt zero), więc nigdy nie jest pusta, dopóki
     * cokolwiek da się złożyć.
     */
    fun handyLadder(
        current: Double,
        mode: LoadMode,
        inv: Inventory,
        maxSwap: Int = HANDY_SWAP
    ): List<Loading> {
        val all = loadings(mode, inv)
        val here = nearest(current, mode, inv) ?: return emptyList()
        return all
            .groupBy { Math.round(it.totalKg * 100) }
            .map { (_, v) -> v.minBy { l -> swapCost(here, l) } }
            .filter { swapCost(here, it) <= maxSwap }
            .sortedBy { it.totalKg }
    }

    fun nearest(target: Double, mode: LoadMode, inv: Inventory): Loading? =
        achievable(mode, inv).minByOrNull { abs(it.totalKg - target) }

    /**
     * Najbliższy próg w górę względem obecnego ciężaru.
     *
     * Ważniejsze, niż wygląda: progi nie są równomierne. Przy jednym ćwiczeniu
     * następny skok to +1,5%, przy innym +15%. Aplikacja ma pokazywać realny
     * następny próg, a nie "dodaj 2,5 kg", którego nie ma jak złożyć.
     *
     * Celowo **wszystkie** osiągalne progi, także te niewygodne. To jest krok
     * ustawiania ciężaru, a ustawić trzeba móc wszystko, co da się nałożyć -
     * inaczej z 81,4 kg nie da się zejść na równe 80. Wygodne progi, patrz
     * [handyLadder], są od czegoś innego: od **proponowania** progresji.
     */
    fun nextUp(current: Double, mode: LoadMode, inv: Inventory): Loading? =
        achievable(mode, inv).firstOrNull { it.totalKg > current + 0.01 }

    fun nextDown(current: Double, mode: LoadMode, inv: Inventory): Loading? =
        achievable(mode, inv).lastOrNull { it.totalKg < current - 0.01 }

    /** Wynik dopasowania ciężaru z planu do realnego sprzętu. */
    data class Resolved(
        val actualKg: Double,
        val loading: Loading?,
        val nextUp: Loading?,
        /** Czy ciężar z planu da się złożyć co do grama. */
        val exact: Boolean
    )

    fun resolve(targetKg: Double, equipment: Equipment, inv: Inventory): Resolved {
        val mode = LoadMode.of(equipment, inv)
            ?: return Resolved(targetKg, null, null, exact = true)

        val best = nearest(targetKg, mode, inv)
            ?: return Resolved(targetKg, null, null, exact = false)

        return Resolved(
            actualKg = best.totalKg,
            loading = best,
            nextUp = nextUp(best.totalKg, mode, inv),
            exact = abs(best.totalKg - targetKg) < 0.02
        )
    }
}
