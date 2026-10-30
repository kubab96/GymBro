package pl.gymbro.data

import kotlin.math.log10

/**
 * Oszacowanie tkanki tłuszczowej z obwodów ciała - metoda US Navy.
 *
 * Dlaczego akurat ta, a nie wzór z masy ciała, jakiego używa aplikacja wagi:
 * tutaj wejściem jest **faktyczny pomiar Twojego ciała**, przede wszystkim obwód
 * talii. Masa ciała nie mówi nic o tym, ile masz tłuszczu - dwie osoby o tej samej
 * masie i wzroście mogą różnić się składem o kilkanaście punktów. Obwód talii
 * mierzy tłuszcz brzuszny bezpośrednio.
 *
 * Błąd metody to około ±3-4 punkty procentowe wobec pomiaru DEXA. To niewiele
 * gorzej niż porządna waga z prawdziwą bioimpedancją i zdecydowanie lepiej niż
 * wzór oparty wyłącznie na masie.
 *
 * Mimo wszystko to **oszacowanie**, nie pomiar, i tak je w aplikacji podpisujemy.
 * Jak przy masie ciała: liczy się kierunek zmiany, nie wartość bezwzględna.
 */
object BodyFat {

    /**
     * @param waistCm obwód talii
     * @param neckCm obwód szyi
     * @param hipsCm obwód bioder - wymagany tylko dla kobiet
     * @return oszacowanie w procentach albo null, gdy brakuje danych
     *   lub wychodzi wartość niefizyczna
     */
    fun navy(
        sex: Sex,
        heightCm: Double?,
        waistCm: Double?,
        neckCm: Double?,
        hipsCm: Double? = null
    ): Double? {
        if (heightCm == null || waistCm == null || neckCm == null) return null
        if (heightCm <= 0 || waistCm <= 0 || neckCm <= 0) return null

        val result = when (sex) {
            Sex.MALE -> {
                // Logarytm z różnicy talia−szyja: przy nierealnej różnicy
                // wzór nie ma sensu, więc od razu rezygnujemy.
                val diff = waistCm - neckCm
                if (diff <= 0) return null
                495.0 / (1.0324 - 0.19077 * log10(diff) + 0.15456 * log10(heightCm)) - 450.0
            }

            Sex.FEMALE -> {
                if (hipsCm == null || hipsCm <= 0) return null
                val sum = waistCm + hipsCm - neckCm
                if (sum <= 0) return null
                495.0 / (1.29579 - 0.35004 * log10(sum) + 0.22100 * log10(heightCm)) - 450.0
            }
        }

        // Poza tym zakresem wynik oznacza błąd w pomiarach, nie stan ciała.
        return result.takeIf { it in 2.0..70.0 }
    }

    /** Czego jeszcze brakuje, żeby dało się policzyć. Do podpowiedzi w interfejsie. */
    fun missing(
        sex: Sex,
        heightCm: Double?,
        waistCm: Double?,
        neckCm: Double?,
        hipsCm: Double?
    ): List<String> = buildList {
        if (heightCm == null) add("wzrost w profilu")
        if (waistCm == null) add("obwód talii")
        if (neckCm == null) add("obwód szyi")
        if (sex == Sex.FEMALE && hipsCm == null) add("obwód bioder")
    }

    /**
     * Orientacyjny opis zakresu dla mężczyzn. Granice są umowne i różnią się
     * między źródłami - traktuj jako punkt odniesienia, nie diagnozę.
     */
    fun comment(sex: Sex, pct: Double): String = when {
        sex == Sex.FEMALE -> when {
            pct < 14 -> "bardzo nisko"
            pct < 21 -> "poziom sportowy"
            pct < 25 -> "w normie"
            pct < 32 -> "powyżej normy"
            else -> "wysoko"
        }
        pct < 6 -> "bardzo nisko"
        pct < 14 -> "poziom sportowy"
        pct < 18 -> "w normie"
        pct < 25 -> "powyżej normy"
        else -> "wysoko"
    }
}
