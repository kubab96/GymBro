package pl.gymbro.data

/**
 * Którędy płynął prąd przy pomiarze oporu.
 *
 * Nazwy stałych są formatem zapisu (kolumna `bia_value.path`) - nie zmieniać.
 *
 * Droga jest częścią pomiaru, nie szczegółem wagi: ten sam człowiek ma inny
 * opór „stopa - stopa" niż „ręka - stopa", bo prąd przechodzi przez inną
 * część ciała. Wzory są kalibrowane na konkretnej drodze, więc bez niej opór
 * jest liczbą, z którą nie wiadomo, co zrobić.
 */
enum class BiaPath(val label: String) {
    /** Waga z czterema elektrodami w podstawie: nogi i dół brzucha. */
    FOOT_TO_FOOT("stopa - stopa"),

    /** Całe ciało jedną stroną - waga z rączką albo analizator kliniczny. */
    HAND_TO_FOOT("ręka - stopa"),

    /** Analizator trzymany w dłoniach: ręce i klatka. */
    HAND_TO_HAND("ręka - ręka"),

    LEFT_ARM("lewa ręka"),
    RIGHT_ARM("prawa ręka"),
    TRUNK("tułów"),
    LEFT_LEG("lewa noga"),
    RIGHT_LEG("prawa noga");

    val isSegment: Boolean
        get() = this in SEGMENTS

    companion object {
        val SEGMENTS = setOf(LEFT_ARM, RIGHT_ARM, TRUNK, LEFT_LEG, RIGHT_LEG)

        fun parse(name: String): BiaPath? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Jeden surowy opór z wagi.
 *
 * Tyle i tylko tyle przesyła waga: procent tłuszczu, mięśnie i woda to wzory
 * liczone już w aplikacji producenta. Trzymamy ten surowiec, nie wyniki -
 * dzięki temu zmiana wzoru przelicza całą historię, a waga z ośmioma
 * elektrodami mieści się w tym samym zapisie co waga z czterema.
 *
 * @param freqKHz częstotliwość prądu. Wagi jednoczęstotliwościowe mierzą przy
 *   50 kHz i nie mówią o tym w ramce - stąd wartość domyślna.
 */
data class BiaValue(
    val path: BiaPath,
    val ohm: Double,
    val freqKHz: Double = Bia.FREQ_KHZ
)

/**
 * Skład ciała z oporu - opublikowanymi wzorami, nie wzorem producenta wagi.
 *
 * Projekt, źródła i powody każdej liczby: `docs/sklad-ciala.md`.
 *
 * Wzory biorą opór, wzrost, masę, płeć i wiek. Wszystko poza oporem mamy
 * w profilu, więc każda waga, która opór podaje, dostaje ten sam skład ciała
 * liczony tak samo - niezależnie od producenta. Waga, która oporu nie podaje,
 * daje samą masę i tu nic nie wnosi.
 */
object Bia {

    /** Częstotliwość, przy której powstały wszystkie wzory poniżej. */
    const val FREQ_KHZ = 50.0

    /** Wagi podają 50 kHz zaokrąglone różnie; dalej to już inny pomiar. */
    private const val FREQ_TOLERANCE_KHZ = 5.0

    /**
     * Opór całego ciała, który uznajemy za pomiar. Poza tym zakresem to bosa
     * stopa na jednej elektrodzie albo skarpetki, nie skład ciała.
     */
    val WHOLE_BODY_OHM = 150.0..1500.0

    /** Segmenty są krótsze, a tułów ma opór rzędu kilkudziesięciu omów. */
    val SEGMENT_OHM = 5.0..1000.0

    /** Jak blisko warunków, w których wzór powstał, jest ten pomiar. */
    enum class Basis {
        /**
         * Ręka - stopa przy 50 kHz: ta sama droga, na której kalibrowano wzory.
         * Waga mierzy na stojąco, a wzory powstały na leżąco - to przesuwa
         * opór o kilka procent, ale nie zmienia tego, przez co płynie prąd.
         */
        DIRECT,

        /**
         * Stopa - stopa. Dla dorosłych nie ma opublikowanego wzoru na tę drogę,
         * więc liczymy wzorem ręka - stopa. Wartość bezwzględna ma przesunięcie,
         * ale rośnie i maleje razem z tkanką - kierunek zmiany jest prawdziwy.
         */
        BORROWED
    }

    /**
     * Wynik dla jednego pomiaru.
     *
     * [muscleKg] jest null bez wieku w profilu: wzór Janssena go wymaga,
     * a podstawienie wieku przeciętnego dałoby liczbę udającą pomiar.
     */
    data class Estimate(
        val basis: Basis,
        val path: BiaPath,
        val ohm: Double,
        val leanKg: Double,
        val fatKg: Double,
        val fatPct: Double,
        val waterKg: Double,
        val muscleKg: Double?,
        val asymmetry: Asymmetry?
    )

    /**
     * Lewa strona względem prawej, w procentach tkanki beztłuszczowej.
     * +3 znaczy, że lewa ma jej o 3 % więcej niż prawa; null, gdy waga nie
     * podała obu segmentów.
     *
     * Przy tej samej długości kończyny objętość tkanki przewodzącej jest
     * odwrotnie proporcjonalna do oporu, więc stosunek lewa/prawa to po prostu
     * odwrotność stosunku oporów. Nie potrzeba do tego żadnego wzoru
     * z regresji - i dlatego to jedyna liczba z segmentów, której ufamy.
     */
    data class Asymmetry(val armsPct: Double?, val legsPct: Double?)

    private fun at50(v: BiaValue) = kotlin.math.abs(v.freqKHz - FREQ_KHZ) <= FREQ_TOLERANCE_KHZ

    /**
     * Opór całego ciała przy 50 kHz i droga, z której pochodzi.
     *
     * Kolejność: ręka - stopa wprost, potem suma segmentów jednej strony
     * (tak waga z rączką składa całe ciało, gdy nie podaje go osobno), na
     * końcu stopa - stopa. Prawa strona przed lewą, bo na prawej kalibrowano
     * wzory - mierzy się ją w badaniach z przyzwyczajenia.
     */
    fun wholeBody(values: List<BiaValue>): Pair<BiaPath, Double>? {
        val usable = values.filter { at50(it) }
        fun ohm(p: BiaPath) = usable.firstOrNull { it.path == p }?.ohm

        ohm(BiaPath.HAND_TO_FOOT)?.takeIf { it in WHOLE_BODY_OHM }?.let {
            return BiaPath.HAND_TO_FOOT to it
        }
        for ((arm, leg) in listOf(
            BiaPath.RIGHT_ARM to BiaPath.RIGHT_LEG,
            BiaPath.LEFT_ARM to BiaPath.LEFT_LEG
        )) {
            val parts = listOf(ohm(arm), ohm(BiaPath.TRUNK), ohm(leg))
            if (parts.all { it != null && it in SEGMENT_OHM }) {
                val sum = parts.sumOf { it!! }
                if (sum in WHOLE_BODY_OHM) return BiaPath.HAND_TO_FOOT to sum
            }
        }
        ohm(BiaPath.FOOT_TO_FOOT)?.takeIf { it in WHOLE_BODY_OHM }?.let {
            return BiaPath.FOOT_TO_FOOT to it
        }
        return null
    }

    fun asymmetry(values: List<BiaValue>): Asymmetry? {
        val usable = values.filter { at50(it) && it.path.isSegment && it.ohm in SEGMENT_OHM }
        fun ohm(p: BiaPath) = usable.firstOrNull { it.path == p }?.ohm
        fun side(left: BiaPath, right: BiaPath): Double? {
            val l = ohm(left) ?: return null
            val r = ohm(right) ?: return null
            return (r / l - 1.0) * 100.0
        }
        val arms = side(BiaPath.LEFT_ARM, BiaPath.RIGHT_ARM)
        val legs = side(BiaPath.LEFT_LEG, BiaPath.RIGHT_LEG)
        return if (arms == null && legs == null) null else Asymmetry(arms, legs)
    }

    /**
     * Masa beztłuszczowa, NHANES III (Chumlea 2002), kryterium: model
     * czteroskładnikowy. Opór ręka - stopa przy 50 kHz.
     */
    fun leanMass(ohm: Double, weightKg: Double, heightCm: Double, sex: Sex): Double {
        val index = heightCm * heightCm / ohm
        return when (sex) {
            Sex.MALE -> -10.678 + 0.262 * weightKg + 0.652 * index + 0.015 * ohm
            Sex.FEMALE -> -9.529 + 0.168 * weightKg + 0.696 * index + 0.016 * ohm
        }
    }

    /** Woda całkowita w kilogramach (litrach), Sun 2003, kryterium: deuter. */
    fun water(ohm: Double, weightKg: Double, heightCm: Double, sex: Sex): Double {
        val index = heightCm * heightCm / ohm
        return when (sex) {
            Sex.MALE -> 1.2 + 0.45 * index + 0.18 * weightKg
            Sex.FEMALE -> 3.75 + 0.45 * index + 0.11 * weightKg
        }
    }

    /** Mięśnie szkieletowe, Janssen 2000, kryterium: rezonans magnetyczny. */
    fun skeletalMuscle(ohm: Double, heightCm: Double, age: Int, sex: Sex): Double {
        val index = heightCm * heightCm / ohm
        val male = if (sex == Sex.MALE) 1.0 else 0.0
        return 0.401 * index + 3.825 * male - 0.071 * age + 5.102
    }

    /**
     * Skład ciała z jednego pomiaru albo null, gdy nie ma z czego liczyć:
     * brak oporu, brak wzrostu albo wynik niefizyczny. Null zamiast liczby
     * z założeń - jak w [BodyFat.navy].
     */
    fun estimate(
        values: List<BiaValue>,
        weightKg: Double,
        heightCm: Double?,
        age: Int?,
        sex: Sex
    ): Estimate? {
        if (heightCm == null || heightCm <= 0.0 || weightKg <= 0.0) return null
        val (path, ohm) = wholeBody(values) ?: return null

        val lean = leanMass(ohm, weightKg, heightCm, sex)
        // Masa beztłuszczowa większa niż masa ciała albo prawie równa jej
        // znaczy, że coś jest nie tak z oporem, nie że ktoś nie ma tłuszczu.
        if (lean <= 0.0 || lean >= weightKg) return null
        val fat = weightKg - lean
        val pct = fat / weightKg * 100.0
        if (pct !in 2.0..70.0) return null

        return Estimate(
            basis = if (path == BiaPath.FOOT_TO_FOOT) Basis.BORROWED else Basis.DIRECT,
            path = path,
            ohm = ohm,
            leanKg = lean,
            fatKg = fat,
            fatPct = pct,
            waterKg = water(ohm, weightKg, heightCm, sex),
            muscleKg = age?.takeIf { it > 0 }?.let { skeletalMuscle(ohm, heightCm, it, sex) },
            asymmetry = asymmetry(values)
        )
    }

    /** Wiek w roku pomiaru - historia nie może się zmieniać z kalendarzem. */
    fun ageAt(birthYear: Int?, measuredAt: Long): Int? {
        birthYear ?: return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = measuredAt }
        return (cal.get(java.util.Calendar.YEAR) - birthYear).takeIf { it > 0 }
    }

    /** To samo dla zapisanego pomiaru - jedno wejście dla wszystkich ekranów. */
    fun of(m: BodyMeasurement, profile: UserProfile): Estimate? =
        estimate(m.bia, m.weightKg, profile.heightCm, ageAt(profile.birthYear, m.measuredAt), profile.sex)
}
