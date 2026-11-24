package pl.gymbro.gym

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Aktywność nagrywana telefonem - z kafelków „Inny trening" na ekranie Treningu.
 *
 * [type] to nazwa, pod jaką aktywność ląduje w `external_session`, i musi
 * być tą samą, którą import z Health Connect nadaje treningom z zegarka
 * („Bieg", „Spacer", „Trening siłowy"). Dzięki temu historia, podział na
 * kardio i siłę i ekran szczegółów nie mają dla telefonu osobnej ścieżki.
 *
 * Nazw stałych nie zmieniaj - są formatem zapisu trwającej aktywności.
 * Nazwy typów też nie: przy tych, które zna import z zegarka („Bieg
 * (bieżnia)", „Orbitrek", „Trekking"), są tymi samymi napisami.
 *
 * Na ekranie wyboru jest osiem pozycji i trening siłowy. Wcześniej było
 * ich ponad trzydzieści, ale poza GPS każda prowadziła do tego samego
 * stopera i różniła się tylko nazwą. Reszta jest ukryta
 * ([ActivityGroup.HIDDEN]), nie skasowana: aktywność zaczęta w starszej
 * wersji musi wstać po aktualizacji.
 *
 * MET z Compendium of Physical Activities, wartość dla wysiłku
 * umiarkowanego - konwencja, nie pomiar.
 */
enum class PhoneActivity(
    val label: String,
    val type: String,
    val group: ActivityGroup,
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
    RUN("Bieganie", "Bieg", ActivityGroup.OUTDOOR, gps = true, maxSpeedMs = 9.0, met = null),
    WALK("Chodzenie", "Spacer", ActivityGroup.OUTDOOR, gps = true, maxSpeedMs = 3.5, met = null),
    BIKE("Rower", "Rower", ActivityGroup.OUTDOOR, gps = true, maxSpeedMs = 22.0, met = 7.5),
    HIKE("Wędrówka", "Trekking", ActivityGroup.OUTDOOR, gps = true, maxSpeedMs = 3.5, met = 6.0),

    TREADMILL("Bieżnia", "Bieg (bieżnia)", ActivityGroup.INDOOR, gps = false, maxSpeedMs = 0.0, met = 9.0),
    BIKE_INDOOR("Rower stacjonarny", "Rower stacjonarny", ActivityGroup.INDOOR, gps = false, maxSpeedMs = 0.0, met = 7.0),
    ELLIPTICAL("Orbitrek", "Orbitrek", ActivityGroup.INDOOR, gps = false, maxSpeedMs = 0.0, met = 5.0),
    ROWING("Wioślarz", "Wioślarz", ActivityGroup.INDOOR, gps = false, maxSpeedMs = 0.0, met = 7.0),

    /**
     * Ogólny trening siłowy: serie i przerwy, bez nazw ćwiczeń - jak „Siła"
     * w zegarkach. Typ ten sam co z importu zegarka, więc w historii stoi
     * razem z siłą, a ten sam trening nagrany przy okazji zegarkiem skleja
     * się z nim w jeden. MET 5,0 to środek między wysiłkiem umiarkowanym
     * (3,5) a intensywnym (6,0) - konwencja: sama liczba serii nie mówi,
     * jak ciężko było.
     */
    STRENGTH("Trening siłowy", "Trening siłowy", ActivityGroup.STRENGTH, gps = false, maxSpeedMs = 0.0, met = 5.0),

    // Ukryte. Zostają, bo aktywność zaczęta w starszej wersji musi wstać.
    TRAIL("Bieg w terenie", "Bieg w terenie", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 9.0, met = null),
    NORDIC("Nordic walking", "Marsz nordic walking", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 3.5, met = 4.8),
    MTB("Rower górski", "Rower górski", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 20.0, met = 8.5),
    INLINE("Rolki", "Rolki", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 12.0, met = 7.5),
    STAIRS("Schody", "Schody", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 9.0),
    SWIM("Pływanie", "Pływanie", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 6.0),
    HIIT("HIIT", "HIIT", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 8.0),
    CROSSFIT("Crossfit", "Crossfit", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 8.0),
    STRETCH("Rozciąganie", "Rozciąganie", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 2.5),
    YOGA("Joga", "Joga", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 2.5),
    PILATES("Pilates", "Pilates", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 3.0),
    DANCE("Taniec", "Taniec", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.0),
    BOXING("Boks", "Boks", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 7.8),
    MARTIAL("Sztuki walki", "Sztuki walki", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.3),
    CLIMB("Wspinaczka", "Wspinaczka", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.8),
    FOOTBALL("Piłka nożna", "Piłka nożna", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 7.0),
    BASKETBALL("Koszykówka", "Koszykówka", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 6.5),
    VOLLEYBALL("Siatkówka", "Siatkówka", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 4.0),
    TENNIS("Tenis", "Tenis", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 7.3),
    PADEL("Padel", "Padel", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 6.0),
    SQUASH("Squash", "Squash", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 7.3),
    BADMINTON("Badminton", "Badminton", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.5),
    TABLE_TENNIS("Tenis stołowy", "Tenis stołowy", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 4.0),
    GOLF("Golf", "Golf", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 4.8),
    XC_SKI("Narty biegowe", "Narty biegowe", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 10.0, met = 9.0),
    SKI("Narty zjazdowe", "Narty zjazdowe", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.3),
    SNOWBOARD("Snowboard", "Snowboard", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 5.3),
    SKATE("Łyżwy", "Łyżwy", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = 7.0),
    KAYAK("Kajak", "Kajak", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 4.0, met = 5.0),
    SUP("Deska SUP", "Deska SUP", ActivityGroup.HIDDEN, gps = true, maxSpeedMs = 3.0, met = 6.0),

    /** Dawny stoper „Inna aktywność". Zastąpił go [STRENGTH]. */
    OTHER("Inna aktywność", "Trening dowolny", ActivityGroup.HIDDEN, gps = false, maxSpeedMs = 0.0, met = null);

    /** Czy aktywność liczy serie i przerwy, a nie trasę. */
    val sets: Boolean get() = this == STRENGTH

    companion object {
        /** Po nazwie typu z historii - do ikony. */
        fun byType(type: String): PhoneActivity? = entries.firstOrNull { it.type == type }
    }
}

/** Grupy na ekranie wyboru, w kolejności wyświetlania. */
enum class ActivityGroup(val label: String) {
    STRENGTH("Trening siłowy"),
    OUTDOOR("Z trasą"),
    INDOOR("W pomieszczeniu"),
    HIDDEN("")
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
     * Miejsca, w których kończy się każdy pełny kilometr - do cyferek na mapie.
     * Położenie wyliczone z proporcji między dwoma punktami, tak samo jak
     * czas przekroczenia w [splits], więc cyferka stoi tam, gdzie zaczyna
     * się następny wiersz tabeli kilometrów.
     */
    fun kmMarks(points: List<Fix>): List<Fix> {
        val out = mutableListOf<Fix>()
        var acc = 0.0
        for ((a, b) in points.zipWithNext()) {
            if (a.seg != b.seg) continue
            val d = meters(a, b)
            if (d <= 0) continue
            var next = (out.size + 1) * 1000.0
            while (acc + d >= next) {
                val f = (next - acc) / d
                out += Fix(
                    at = a.at + ((b.at - a.at) * f).toLong(),
                    lat = a.lat + (b.lat - a.lat) * f,
                    lon = a.lon + (b.lon - a.lon) * f,
                    accuracyM = 0f,
                    seg = a.seg
                )
                next += 1000.0
            }
            acc += d
        }
        return out
    }

    /**
     * Kawałek trasy i jego prędkość względem średniej całej aktywności:
     * 1,1 to dziesięć procent szybciej niż zwykle w tym biegu.
     */
    data class PaceChunk(val points: List<Fix>, val ratio: Double)

    /**
     * Trasa pocięta na odcinki po około [chunkM] metrów, do kolorowania mapy
     * tempem.
     *
     * Względem średniej z tej samej aktywności, nie sztywnych progów: przy
     * progach typu „szybciej niż 5:00/km" spacer byłby cały czerwony, a bieg
     * interwałowy cały zielony i mapa nie mówiłaby nic. Odcinek krótszy niż
     * sto metrów to w GPS telefonu głównie szum - kolor skakałby co kilka kroków.
     *
     * Kolejny odcinek zaczyna się od ostatniego punktu poprzedniego, żeby linia
     * na mapie nie miała dziur. Po pauzie - nowy odcinek bez łączenia.
     */
    fun paceChunks(points: List<Fix>, chunkM: Double = 100.0): List<PaceChunk> {
        val raw = mutableListOf<Triple<List<Fix>, Double, Double>>() // punkty, metry, sekundy
        var cur = mutableListOf<Fix>()
        var d = 0.0
        fun flush() {
            if (cur.size >= 2) {
                val s = (cur.last().at - cur.first().at) / 1000.0
                if (s > 0) raw += Triple(cur.toList(), d, s)
            }
        }
        for (p in points) {
            val last = cur.lastOrNull()
            if (last != null && last.seg != p.seg) {
                flush(); cur = mutableListOf(p); d = 0.0
                continue
            }
            if (last != null) d += meters(last, p)
            cur += p
            if (d >= chunkM) {
                flush(); cur = mutableListOf(p); d = 0.0
            }
        }
        flush()
        val meters = raw.sumOf { it.second }
        val seconds = raw.sumOf { it.third }
        if (meters <= 0 || seconds <= 0) return emptyList()
        val avg = meters / seconds
        return raw.map { (pts, m, s) -> PaceChunk(pts, (m / s) / avg) }
    }

    /**
     * Najkrótszy czas, w jakim aktywność pokonała [targetM] metrów, w ms -
     * z dowolnego miejsca trasy, nie tylko od startu. Tak liczą rekordy
     * Strava i Garmin: kilometr w 4:48 ze środka biegu na sześć to dalej
     * kilometr w 4:48.
     *
     * Okno nie przechodzi przez pauzę - czasu pauzy nie da się przypisać do
     * drogi. Czas przeskalowany z najkrótszego okna, które przekracza
     * dystans, do równego dystansu; przy odczycie co sekundę różnica to
     * ułamek sekundy. Null, gdy trasa była krótsza.
     */
    fun bestEffortMs(points: List<Fix>, targetM: Double): Long? {
        var best: Long? = null
        var from = 0
        while (from < points.size) {
            var to = from
            while (to + 1 < points.size && points[to + 1].seg == points[from].seg) to++
            val run = points.subList(from, to + 1)
            val cum = DoubleArray(run.size)
            for (k in 1 until run.size) cum[k] = cum[k - 1] + meters(run[k - 1], run[k])
            var i = 0
            for (j in run.indices) {
                while (i + 1 < j && cum[j] - cum[i + 1] >= targetM) i++
                val dist = cum[j] - cum[i]
                if (dist >= targetM) {
                    val t = ((run[j].at - run[i].at) * targetM / dist).toLong()
                    if (best == null || t < best) best = t
                }
            }
            from = to + 1
        }
        return best
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
            PhoneActivity.RUN, PhoneActivity.TRAIL, PhoneActivity.WALK -> {
                if (distanceM <= 0) return null
                val v = distanceM / min
                3.5 + (if (kind == PhoneActivity.WALK) 0.1 else 0.2) * v
            }
            else -> (kind.met ?: return null) * 3.5
        }
        return vo2 * kg * min / 1000.0 * 5.0
    }
}
