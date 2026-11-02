package pl.gymbro.nutrition

import pl.gymbro.data.Sex
import pl.gymbro.data.roundKcal

/**
 * Spoczynkowa przemiana materii i wydatek policzony ze wzoru.
 *
 * Projekt i uzasadnienie liczb: `docs/kalorie.md`, rozdział 5.
 *
 * To jest **ścieżka startowa, nie docelowa**. Wzór odpowiada na pytanie
 * „ile osoba o takich parametrach zwykle wydaje", a nie „ile wydajesz Ty".
 * Odpowiedź na to drugie liczy [Tdee] z bilansu energii i z czasem wypiera
 * tę tutaj. Wzór jest potrzebny, bo pierwszego dnia bilansu jeszcze nie ma,
 * a jakąś liczbę trzeba pokazać.
 */
object Bmr {

    /**
     * Wszystko, co nie jest ani krokami, ani treningiem: siedzenie, stanie,
     * wiercenie się, mycie zębów. Konwencja - tego nie mierzy nic, co mamy.
     */
    const val RESTING_NEAT = 1.15

    /** Efekt termiczny pożywienia, 10 % spożycia. */
    const val TEF = 0.10

    /**
     * Koszt jednego kroku na kilogram masy ciała.
     *
     * Z kosztu marszu około 0.53 kcal/kg/km i długości kroku 0.75 m.
     * Dla 85 kg i 10 000 kroków daje 340 kcal.
     */
    const val KCAL_PER_KG_PER_STEP = 0.0004

    /** Koszt jednej sesji siłowej. Konwencja; sesje liczymy z bazy, nie z deklaracji. */
    const val TRAINING_KCAL = 350.0

    /**
     * Kroki przyjmowane, gdy nie ma danych z Health Connect.
     *
     * Nie zero: zero kroków to osoba leżąca, a nie osoba bez zegarka.
     * Cztery tysiące to tyle, ile robi się mimochodem w dniu spędzonym w domu.
     */
    const val DEFAULT_STEPS = 4000.0

    /** Granice, w których procent tkanki tłuszczowej z wagi uznajemy za sensowny. */
    private const val FAT_PCT_MIN = 3.0
    private const val FAT_PCT_MAX = 60.0

    enum class Source {
        /** Wzór Mifflina-St Jeor z masy, wzrostu i wieku. */
        MIFFLIN,

        /** Wzór Katcha-McArdle'a z masy beztłuszczowej. */
        KATCH_MCARDLE
    }

    data class Resting(val kcal: Double, val source: Source)

    /**
     * Mifflin-St Jeor, 1990.
     *
     * Najdokładniejszy ze wzorów przewidujących - co nie znaczy dokładny:
     * mieści się w 10 % u czterech osób na pięć. Nie wie nic o składzie ciała,
     * więc dla dwóch osób o tej samej masie i wzroście daje tę samą liczbę.
     */
    fun mifflin(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return roundKcal(base + if (sex == Sex.MALE) 5.0 else -161.0)
    }

    /**
     * Katch-McArdle z masy beztłuszczowej.
     *
     * Nie pyta o wzrost, wiek ani płeć, bo wszystkie trzy są tu tylko
     * pośrednikami do tego, ile masz tkanki metabolicznie czynnej. Jeśli
     * wiemy to wprost, pośrednicy są zbędni - a dla kogoś, kto trenuje
     * siłowo, to nie jest różnica teoretyczna: osoba z 12 % tłuszczu i osoba
     * z 30 % przy tej samej masie wydają inaczej.
     */
    fun katchMcArdle(leanKg: Double): Double = roundKcal(370.0 + 21.6 * leanKg)

    /** Masa beztłuszczowa z masy ciała i procentu tkanki tłuszczowej. */
    fun leanMass(weightKg: Double, bodyFatPct: Double): Double =
        weightKg * (1.0 - bodyFatPct / 100.0)

    /**
     * Spoczynkowa przemiana - lepszym z dostępnych wzorów.
     *
     * Zwraca null, gdy nie ma z czego liczyć. To jest celowe: lepiej
     * powiedzieć „podaj wzrost", niż podstawić wzrost przeciętny i podać
     * wynik, który wygląda jak pomiar.
     *
     * `bmr_kcal` z wagi nie jest tu brane pod uwagę - waga liczy je własnym,
     * nieujawnionym wzorem z tej samej bioimpedancji, która daje procent
     * tłuszczu. Trzymamy to jako liczbę do porównania na ekranie.
     */
    fun resting(
        weightKg: Double?,
        heightCm: Double?,
        age: Int?,
        sex: Sex,
        bodyFatPct: Double? = null
    ): Resting? {
        if (weightKg == null || weightKg <= 0.0) return null

        val fat = bodyFatPct?.takeIf { it in FAT_PCT_MIN..FAT_PCT_MAX }
        if (fat != null) {
            return Resting(katchMcArdle(leanMass(weightKg, fat)), Source.KATCH_MCARDLE)
        }

        if (heightCm == null || heightCm <= 0.0 || age == null || age <= 0) return null
        return Resting(mifflin(weightKg, heightCm, age, sex), Source.MIFFLIN)
    }

    /**
     * Wydatek całodobowy ze wzoru.
     *
     * Budowany ze składników, a nie mnożony przez współczynnik wybrany z listy.
     * Różnica jest praktyczna: „umiarkowanie aktywny" to deklaracja osoby,
     * która nie wie, ile się rusza, a kroki i sesje treningowe leżą w bazie.
     *
     * Dzielenie przez `1 - TEF` na końcu, a nie dodawanie efektu termicznego
     * jako składnika - inaczej trzeba by znać spożycie, żeby policzyć wydatek,
     * żeby wyliczyć cel spożycia.
     *
     * @param stepsPerDay średnia dzienna z Health Connect; null = [DEFAULT_STEPS]
     * @param sessionsPerWeek sesje siłowe na tydzień, liczone z bazy
     */
    fun formulaTdee(
        restingKcal: Double,
        weightKg: Double,
        stepsPerDay: Double? = null,
        sessionsPerWeek: Double = 0.0
    ): Double {
        val steps = (stepsPerDay ?: DEFAULT_STEPS).coerceAtLeast(0.0)
        val walking = steps * weightKg * KCAL_PER_KG_PER_STEP
        val training = sessionsPerWeek.coerceAtLeast(0.0) * TRAINING_KCAL / 7.0
        return roundKcal((restingKcal * RESTING_NEAT + walking + training) / (1.0 - TEF))
    }
}
