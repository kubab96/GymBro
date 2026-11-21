package pl.gymbro.gym

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Aktywność nagrywana telefonem - bieg, spacer i reszta z ekranu
 * „Rozpocznij inny trening".
 *
 * [type] to nazwa, pod jaką aktywność ląduje w `external_session`, i musi
 * być tą samą, którą import z Health Connect nadaje treningom z zegarka
 * („Bieg", „Spacer"). Dzięki temu historia, podział na kardio i ekran
 * szczegółów nie mają dla telefonu osobnej ścieżki.
 *
 * Nazw stałych nie zmieniaj - są formatem zapisu trwającej aktywności.
 */
enum class PhoneActivity(
    val label: String,
    val type: String,
    /** Czy telefon mierzy trasę. Bez GPS to sam stoper. */
    val gps: Boolean,
    /**
     * Prędkość, powyżej której skok między odczytami GPS uznajemy za błąd.
     * Z zapasem nad tym, co człowiek realnie robi w tej aktywności.
     */
    val maxSpeedMs: Double,
    /** Koszt energetyczny w MET, gdy nie liczymy go z prędkości. Null - nie zgadujemy. */
    val met: Double?
) {
    RUN("Bieganie", "Bieg", gps = true, maxSpeedMs = 9.0, met = null),
    WALK("Chodzenie", "Spacer", gps = true, maxSpeedMs = 3.5, met = null),
    BIKE("Rower", "Rower", gps = true, maxSpeedMs = 22.0, met = 7.5),
    SWIM("Pływanie", "Pływanie", gps = false, maxSpeedMs = 0.0, met = 6.0),
    STRETCH("Rozciąganie", "Rozciąganie", gps = false, maxSpeedMs = 0.0, met = 2.5),
    OTHER("Inna aktywność", "Trening dowolny", gps = false, maxSpeedMs = 0.0, met = null);
}

/**
 * Trasa z GPS telefonu: odsiewanie odczytów, dystans, kilometry, tempo.
 *
 * Czyste funkcje, bez Androida - to tu siedzą wszystkie decyzje, przez
 * które dystans z telefonu zgadza się albo nie z tym, co pokazałby zegarek.
 */
object TrackMath {

    /**
     * Odczyt gorszy niż tyle metrów nie wchodzi do trasy wcale. Pierwsze
     * odczyty po starcie potrafią mieć i sto metrów niepewności - wliczone,
     * dorzucały do biegu zygzak przez pół osiedla.
     */
    const val MAX_ACCURACY_M = 25f

    /**
     * Najkrótszy krok, który dopisujemy do dystansu.
     *
     * Stojący telefon dostaje co sekundę inną pozycję w promieniu kilku
     * metrów. Liczone od poprzedniego odczytu, takie drganie dokładało przy
     * czerwonym świetle kilkadziesiąt metrów na minutę. Dlatego dystans
     * liczymy od ostatniego punktu zakotwiczenia i przesuwamy kotwicę dopiero
     * wtedy, gdy ruch przekroczy niepewność pomiaru.
     */
    const val MIN_STEP_M = 4.0

    /**
     * Punkt trasy. [seg] rośnie po każdej pauzie: między segmentami nie
     * liczymy dystansu, bo w pauzie można przejść przez pół parku z telefonem
     * w kieszeni i nie chcieć tego w biegu.
     */
    data class Fix(
        val at: Long,
        val lat: Double,
        val lon: Double,
        val accuracyM: Float,
        val seg: Int = 0
    )

    /** Odległość po kuli w metrach (haversine). Przy kilometrach błąd jest pomijalny. */
    fun meters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun meters(a: Fix, b: Fix): Double = meters(a.lat, a.lon, b.lat, b.lon)

    /**
     * Dystans liczony na bieżąco, odczyt po odczycie.
     *
     * Zapisujemy wyłącznie punkty zakotwiczenia - te, które [offer] przyjął.
     * Odtworzenie po ubiciu procesu ([restore]) przechodzi więc po dokładnie
     * tych samych punktach i daje ten sam dystans.
     */
    class Accumulator(private val maxSpeedMs: Double) {
        var anchor: Fix? = null
            private set
        var distanceM: Double = 0.0
            private set

        /** @return true, gdy odczyt został nowym punktem trasy i trzeba go zapisać. */
        fun offer(f: Fix): Boolean {
            if (f.accuracyM > MAX_ACCURACY_M) return false
            val a = anchor
            // Pierwszy punkt po starcie albo po pauzie: początek nowego
            // odcinka, bez dystansu od poprzedniego.
            if (a == null || a.seg != f.seg) {
                anchor = f
                return true
            }
            val dt = (f.at - a.at) / 1000.0
            if (dt <= 0) return false
            val d = meters(a, f)
            // Skok szybszy, niż da się przebiec - odbicie sygnału od bloku.
            // Kotwica zostaje, więc następny dobry odczyt policzy się od niej.
            if (d / dt > maxSpeedMs) return false
            if (d < max(MIN_STEP_M, f.accuracyM.toDouble())) return false
            distanceM += d
            anchor = f
            return true
        }

        fun restore(points: List<Fix>) {
            distanceM = distance(points)
            anchor = points.lastOrNull()
        }
    }

    /** Dystans po zapisanych punktach, bez przeskoków między segmentami. */
    fun distance(points: List<Fix>): Double =
        points.zipWithNext().sumOf { (a, b) -> if (a.seg == b.seg) meters(a, b) else 0.0 }

    /**
     * Kolejne kilometry jako odcinki, z czasem przekroczenia wyliczonym
     * z proporcji między dwoma punktami.
     *
     * Ostatni, niepełny kilometr też wraca - ekran szczegółów rysuje go
     * jako resztę, tak samo jak przy okrążeniach z zegarka.
     */
    fun splits(points: List<Fix>): List<RunAnalysis.Leg> {
        if (points.size < 2) return emptyList()
        val legs = mutableListOf<RunAnalysis.Leg>()
        var legStart = points.first().at
        var legMeters = 0.0
        for ((a, b) in points.zipWithNext()) {
            if (a.seg != b.seg) continue
            var d = meters(a, b)
            var from = a.at.toDouble()
            val msPerMeter = if (d > 0) (b.at - a.at) / d else 0.0
            while (legMeters + d >= 1000.0) {
                val need = 1000.0 - legMeters
                val crossAt = from + need * msPerMeter
                legs += RunAnalysis.Leg(legStart, crossAt.toLong(), 1000.0)
                legStart = crossAt.toLong()
                from = crossAt
                d -= need
                legMeters = 0.0
            }
            legMeters += d
        }
        if (legMeters >= 50.0) legs += RunAnalysis.Leg(legStart, points.last().at, legMeters)
        return legs
    }

    /**
     * Tempo z ostatniego kawałka trasy, w minutach na kilometr.
     *
     * Średnia z całego biegu prawie nie drgnie w trzydziestej minucie, więc do
     * oceny „jak idzie teraz" się nie nadaje. Za krótki odcinek albo za mało
     * metrów - brak wyniku, a nie szalona liczba z jednego skoku GPS.
     */
    fun recentPace(points: List<Fix>, now: Long, windowMs: Long = 30_000): Double? {
        val last = points.lastOrNull() ?: return null
        if (now - last.at > windowMs) return null
        val recent = points.filter { it.at >= now - windowMs && it.seg == last.seg }
        if (recent.size < 2) return null
        val m = distance(recent)
        val ms = recent.last().at - recent.first().at
        if (m < 20 || ms <= 0) return null
        return (ms / 60_000.0) / (m / 1000.0)
    }

    /**
     * Energia aktywności w kilokaloriach (wartość brutto, z przemianą spoczynkową).
     *
     * Bieg i spacer z równań metabolicznych ACSM: zużycie tlenu
     * 3,5 + 0,2 · v (bieg) albo 3,5 + 0,1 · v (spacer) ml/kg/min, v w m/min,
     * i 5 kcal na litr tlenu. To wynik badań, nie konwencja - ale dla biegu
     * płaskiego; podbiegi go zaniżają. Reszta z tabeli MET, gdy ją mamy.
     * Bez masy ciała albo bez MET - null, a nie liczba z sufitu.
     */
    fun energyKcal(kind: PhoneActivity, distanceM: Double, movingMs: Long, weightKg: Double?): Double? {
        val kg = weightKg?.takeIf { it in 30.0..250.0 } ?: return null
        val min = movingMs / 60_000.0
        if (min <= 0) return null
        val vo2 = when (kind) {
            PhoneActivity.RUN, PhoneActivity.WALK -> {
                if (distanceM <= 0) return null
                val v = distanceM / min
                3.5 + (if (kind == PhoneActivity.RUN) 0.2 else 0.1) * v
            }
            else -> (kind.met ?: return null) * 3.5
        }
        return vo2 * kg * min / 1000.0 * 5.0
    }
}
