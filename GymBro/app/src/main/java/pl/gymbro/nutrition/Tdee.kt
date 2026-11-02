package pl.gymbro.nutrition

import pl.gymbro.data.TdeeMethod
import pl.gymbro.data.roundKcal
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wydatek energetyczny policzony z bilansu energii.
 *
 * Projekt, liczby i rachunek niepewności: `docs/kalorie.md`, rozdziały 3 i 4.
 *
 * Zasada: **masa ciała jest licznikiem zapasu energii.** Jeśli jesz średnio
 * 2400 kcal i masa stoi, wydatek wynosi 2400 kcal - niezależnie od tego, co
 * twierdzi wzór. To jest pomiar, a nie przewidywanie, i dlatego z czasem
 * wypiera [Bmr.formulaTdee].
 *
 * Trudność nie leży w arytmetyce, leży w szumie. Masa waha się o kilogram
 * z powodów niezwiązanych z energią: glikogen wiąże wodę 1:3, sól zatrzymuje
 * wodę, treść przewodu pokarmowego waha się o pół kilograma. Przy ważeniu raz
 * w tygodniu nie ma czym tego uśrednić, więc każdy punkt niesie pełny szum.
 * Stąd cały ten plik liczy nie tylko wynik, ale i jego szerokość - i miesza
 * oba oszacowania wagami odwrotnymi do kwadratu niepewności, zamiast
 * przełączać się między nimi na jakimś progu.
 *
 * Czysta funkcja: żadnego dostępu do bazy, żadnego zegara. Dzień „dzisiaj"
 * jest parametrem, bo inaczej testu nie da się napisać.
 */
object Tdee {

    /**
     * Okno regresji w dniach.
     *
     * Osiem tygodni, bo przy ważeniu raz w tygodniu daje osiem punktów, czyli
     * niepewność około ±136 kcal - najmniejsze okno, przy którym pomiar coś
     * znaczy, a jeszcze nie uśrednia dwóch różnych faz.
     */
    const val WINDOW_DAYS = 56

    const val MIN_WEIGH_INS = 4
    const val MIN_SPAN_DAYS = 28

    /**
     * Ile dni okna musi mieć wpisy w dzienniku.
     *
     * Bez tego progu wystarczyłoby zapominać o kolacjach, żeby algorytm
     * ogłosił niski wydatek i obniżył cel.
     */
    const val MIN_LOG_COVERAGE = 0.70

    /**
     * Podłoga odchylenia pojedynczego ważenia.
     *
     * Cztery ważenia mogą z przypadku ułożyć się w prostą niemal idealnie
     * i wtedy odchylenie reszt wychodzi rzędu 0.05 kg, a wzór na niepewność
     * ogłasza pewność, której nie ma. Podłoga jest ważniejsza od elegancji.
     */
    const val WEIGH_SD_FLOOR = 0.6

    /**
     * Niepewność wydatku policzonego ze wzoru.
     *
     * 180 kcal z samego wzoru (Mifflin mieści się w 10 % u czterech osób na
     * pięć) plus około 250 kcal z nieznanego poziomu aktywności, dodane
     * geometrycznie. Konwencja o znanym pochodzeniu.
     */
    const val FORMULA_SD = 300.0

    /** Reguła Wishnofsky'ego - konwencja z 1958 roku, nie prawo fizyki. */
    const val KCAL_PER_KG_LOSS = 7700.0

    /**
     * Przyrost jest tańszy: tkanka beztłuszczowa to głównie woda, około
     * 1800 kcal/kg, a przyrost na nadwyżce przy treningu jest mieszanką.
     */
    const val KCAL_PER_KG_GAIN = 5500.0

    /** Od jakiej przewagi pomiaru nad wzorem nazywamy wynik pomiarem. */
    private const val MEASURED_WEIGHT_RATIO = 4.0

    /** Ważenie: numer dnia od epoki i masa. */
    data class WeighIn(val day: Long, val kg: Double)

    /** Dzień z wypełnionym dziennikiem. Dni bez wpisów tu nie trafiają. */
    data class IntakeDay(val day: Long, val kcal: Double)

    data class Estimate(
        val kcal: Double,
        val method: TdeeMethod,

        /** Odchylenie standardowe wyniku w kcal. Szerokość, nie ozdoba. */
        val sdKcal: Double,

        /** Trend masy w kg na tydzień; null, gdy pomiaru nie było. */
        val trendKgPerWeek: Double? = null,

        val weighIns: Int = 0,
        val loggedDays: Int = 0,

        /** Jedno zdanie dla użytkownika - czego brakuje albo co widać. */
        val note: String = ""
    )

    /**
     * Oszacowanie wydatku na dzisiaj.
     *
     * @param formulaKcal wynik [Bmr.formulaTdee] - punkt wyjścia
     * @param weighIns wszystkie ważenia; filtrowanie do okna jest tutaj
     * @param intake dni z wypełnionym dziennikiem i ich suma kalorii
     * @param today numer dnia od epoki
     * @param fromDay dzień zerowania oszacowania, np. zmiana fazy
     */
    fun estimate(
        formulaKcal: Double,
        weighIns: List<WeighIn>,
        intake: List<IntakeDay>,
        today: Long,
        windowDays: Int = WINDOW_DAYS,
        fromDay: Long? = null,
        formulaSd: Double = FORMULA_SD
    ): Estimate {
        val windowStart = maxOf(today - windowDays + 1, fromDay ?: Long.MIN_VALUE)
        val daysInWindow = (today - windowStart + 1).coerceAtLeast(1L).toInt()

        // Kilka ważeń tego samego dnia to jedna sesja ważenia, nie kilka
        // punktów - waga rozgłasza odczyt wielokrotnie. Bez uśrednienia
        // jeden dzień z trzema odczytami ciągnąłby regresję do siebie.
        val points = weighIns
            .filter { it.day in windowStart..today && it.kg > 0.0 }
            .groupBy { it.day }
            .map { (day, group) -> WeighIn(day, group.sumOf { it.kg } / group.size) }
            .sortedBy { it.day }

        val logged = intake.filter { it.day in windowStart..today && it.kcal > 0.0 }
        val span = if (points.size >= 2) points.last().day - points.first().day else 0L
        val coverage = logged.size.toDouble() / daysInWindow

        val missing = missingData(points.size, span, logged.size, daysInWindow, coverage)
        if (missing != null) {
            return Estimate(
                kcal = roundKcal(formulaKcal),
                method = TdeeMethod.FORMULA,
                sdKcal = formulaSd,
                weighIns = points.size,
                loggedDays = logged.size,
                note = missing
            )
        }

        // ---- Regresja liniowa masy po dniach -------------------------------
        val n = points.size
        val meanDay = points.sumOf { it.day.toDouble() } / n
        val meanKg = points.sumOf { it.kg } / n
        val sxx = points.sumOf { (it.day - meanDay) * (it.day - meanDay) }
        val sxy = points.sumOf { (it.day - meanDay) * (it.kg - meanKg) }
        val slopeKgPerDay = sxy / sxx

        val residualSd = sqrt(
            points.sumOf { p ->
                val fit = meanKg + slopeKgPerDay * (p.day - meanDay)
                val r = p.kg - fit
                r * r
            } / (n - 2)
        )
        val sigma = maxOf(residualSd, WEIGH_SD_FLOOR)
        val slopeSd = sigma / sqrt(sxx)

        // ---- Z nachylenia na kalorie ---------------------------------------
        val perKg = if (slopeKgPerDay < 0.0) KCAL_PER_KG_LOSS else KCAL_PER_KG_GAIN
        val meanIntake = logged.sumOf { it.kcal } / logged.size

        // Spadek masy znaczy, że wydatek jest WYŻSZY od spożycia, stąd minus
        // przy ujemnym nachyleniu.
        val measured = meanIntake - slopeKgPerDay * perKg

        // Niepewność ma dwa źródła. Nachylenie dominuje; średnia spożycia
        // dzieli swój rozrzut przez pierwiastek z liczby dni, więc przy
        // czterdziestu dniach wnosi kilkadziesiąt kcal.
        val intakeSd = if (logged.size >= 2) {
            val m = meanIntake
            sqrt(logged.sumOf { (it.kcal - m) * (it.kcal - m) } / (logged.size - 1)) /
                sqrt(logged.size.toDouble())
        } else 0.0
        val slopeKcalSd = slopeSd * perKg
        val measuredSd = sqrt(slopeKcalSd * slopeKcalSd + intakeSd * intakeSd)

        // ---- Mieszanie wagami odwrotnymi do kwadratu niepewności -----------
        val wMeasured = 1.0 / (measuredSd * measuredSd)
        val wFormula = 1.0 / (formulaSd * formulaSd)
        val blended = (wMeasured * measured + wFormula * formulaKcal) / (wMeasured + wFormula)
        val blendedSd = sqrt(1.0 / (wMeasured + wFormula))

        val method = if (wMeasured > MEASURED_WEIGHT_RATIO * wFormula) {
            TdeeMethod.MEASURED
        } else {
            TdeeMethod.BLENDED
        }

        val trendPerWeek = slopeKgPerDay * 7.0

        return Estimate(
            kcal = roundKcal(blended),
            method = method,
            sdKcal = roundKcal(blendedSd),
            trendKgPerWeek = Math.round(trendPerWeek * 1000.0) / 1000.0,
            weighIns = n,
            loggedDays = logged.size,
            note = describe(method, blendedSd, trendPerWeek)
        )
    }

    /**
     * Czego brakuje do policzenia pomiaru; null, gdy nie brakuje niczego.
     *
     * Komunikat mówi wprost, ile jeszcze trzeba - „jeszcze dwa ważenia" jest
     * instrukcją, a „za mało danych" tylko informacją o porażce.
     */
    private fun missingData(
        weighIns: Int,
        span: Long,
        loggedDays: Int,
        daysInWindow: Int,
        coverage: Double
    ): String? {
        if (weighIns < MIN_WEIGH_INS) {
            val left = MIN_WEIGH_INS - weighIns
            return "Na razie ze wzoru. Do pomiaru brakuje jeszcze " +
                "${left} ${wazenia(left)}."
        }
        if (span < MIN_SPAN_DAYS) {
            return "Na razie ze wzoru. Ważenia obejmują $span dni, " +
                "pomiar potrzebuje $MIN_SPAN_DAYS."
        }
        val needed = Math.ceil(MIN_LOG_COVERAGE * daysInWindow).toInt()
        if (coverage < MIN_LOG_COVERAGE) {
            return "Na razie ze wzoru. Dziennik wypełniony w $loggedDays z " +
                "$daysInWindow dni, pomiar potrzebuje $needed."
        }
        return null
    }

    /** Odmiana „ważenie" - dwa, trzy i cztery mają inną formę niż jedno. */
    private fun wazenia(n: Int): String = if (n == 1) "ważenie" else "ważenia"

    private fun describe(method: TdeeMethod, sd: Double, trendPerWeek: Double): String {
        val kierunek = when {
            abs(trendPerWeek) < 0.05 -> "masa stoi"
            trendPerWeek < 0 -> "masa spada o %.2f kg na tydzień".format(abs(trendPerWeek))
            else -> "masa rośnie o %.2f kg na tydzień".format(trendPerWeek)
        }
        val skad = if (method == TdeeMethod.MEASURED) {
            "Liczone z Twoich danych"
        } else {
            "Wzór i pomiar razem"
        }
        return "$skad, ${kierunek}. Dokładność ±${Math.round(sd)} kcal."
    }
}
