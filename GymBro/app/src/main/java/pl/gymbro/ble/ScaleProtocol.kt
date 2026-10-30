package pl.gymbro.ble

import java.util.UUID
import kotlin.experimental.and

/**
 * Dekodery protokołów wag BLE.
 *
 * Nie wiemy jeszcze, jaka to dokładnie waga, więc zamiast jednego parsera mamy tutaj:
 *  1. parsery znanych rodzin wag (standard BLE, Chipsea/Fitdays, QN-Scale, Xiaomi),
 *  2. "brute force" - skaner, który przeszukuje każdą pozycję w ramce i zgłasza te,
 *     w których liczba wygląda jak sensowna masa ciała.
 *
 * Punkt 2 jest w praktyce najszybszą drogą do rozgryzienia nieznanej wagi:
 * stajesz na nią kilka razy ze znaną masą i sprawdzasz, który offset się zgadza.
 */
object ScaleProtocol {

    // ---- Znane UUID-y ----------------------------------------------------

    fun short(uuid: UUID): String {
        val s = uuid.toString().uppercase()
        // Skrót dla 16-bitowych UUID-ów Bluetooth SIG: 0000XXXX-0000-1000-8000-00805F9B34FB
        return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805F9B34FB")) {
            "0x" + s.substring(4, 8)
        } else s
    }

    /** Rodziny wag rozpoznawane po UUID serwisu. */
    private val KNOWN_SERVICES = mapOf(
        "0x181D" to "Weight Scale Service (standard BLE SIG)",
        "0x181B" to "Body Composition Service (standard BLE SIG / Xiaomi)",
        "0xFFB0" to "Chipsea / Fitdays / OKOK  (notify 0xFFB2, write 0xFFB1)",
        "0xFFF0" to "QN-Scale / Yolanda  (notify 0xFFF1, write 0xFFF2)",
        "0xFFE0" to "Chipsea wariant / wiele tanich wag",
        "0xFFE5" to "Chipsea wariant (zapis)",
        "0x1910" to "Xiaomi Mi Body Composition v2",
        "0xFFF1" to "Sinocare / wariant QN"
    )

    fun describeService(uuid: UUID): String? = KNOWN_SERVICES[short(uuid)]

    /** Nazwy rozgłoszeniowe typowe dla tanich wag z bioimpedancją. */
    private val KNOWN_NAMES = listOf(
        "MY_SCALE" to "Fitdays / Chipsea",
        "CHIPSEA" to "Chipsea",
        "000FatScale" to "Chipsea / OKOK",
        "QN-Scale" to "Yolanda QN",
        "Yunmai" to "Yunmai",
        "ADV" to "Chipsea wariant",
        "Health Scale" to "generyczna (często Chipsea)",
        "BodyFatScale" to "generyczna BIA",
        "SWAN" to "Chipsea wariant",
        "MIBCS" to "Xiaomi Body Composition",
        "MI SCALE" to "Xiaomi"
    )

    fun guessFamilyFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return KNOWN_NAMES.firstOrNull { name.contains(it.first, ignoreCase = true) }?.second
    }

    // ---- Pomocnicze ------------------------------------------------------

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private fun u16le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u16be(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    // ---- Parsery znanych rodzin -----------------------------------------

    /**
     * Standardowa charakterystyka 0x2A9D (Weight Measurement).
     * Bajt flag, potem masa jako uint16 LE.
     *   bit0 = 0 -> SI, rozdzielczość 0.005 kg
     *   bit0 = 1 -> funty, rozdzielczość 0.01 lb
     */
    fun parseStandardWeight(data: ByteArray): String? {
        if (data.size < 3) return null
        val flags = data[0]
        val raw = u16le(data, 1)
        val imperial = (flags and 0x01).toInt() != 0
        val kg = if (imperial) raw * 0.01 * 0.45359237 else raw * 0.005
        if (kg !in 2.0..300.0) return null
        return "standard BLE 0x2A9D -> %.2f kg%s".format(kg, if (imperial) " (przeliczone z lb)" else "")
    }

    /**
     * Ramka Chipsea/Fitdays: 20 bajtów, nagłówek AC 27,
     * [AC 27][dane 2..17][typ ramki 18][suma kontrolna 19].
     * Suma kontrolna = XOR bajtów 0..18.
     */
    fun parseFitdays(data: ByteArray): String? {
        if (data.size < 4) return null
        if ((data[0].toInt() and 0xFF) != 0xAC) return null
        val sb = StringBuilder("ramka Chipsea/Fitdays")
        if (data.size >= 20) {
            var xor = 0
            for (i in 0..18) xor = xor xor (data[i].toInt() and 0xFF)
            val ok = xor == (data[19].toInt() and 0xFF)
            sb.append(if (ok) " | CRC XOR OK" else " | CRC XOR NIE pasuje (sprawdź wariant sumy)")
            sb.append(" | typ=0x%02X".format(data[18]))
        }
        return sb.toString()
    }

    /** Ramka QN-Scale zaczyna się od bajtu długości równego rozmiarowi ramki. */
    fun parseQnScale(data: ByteArray): String? {
        if (data.size < 4) return null
        val declared = data[0].toInt() and 0xFF
        if (declared != data.size) return null
        return "ramka QN-Scale/Yolanda | typ=0x%02X | długość zgodna".format(data[1])
    }

    // ---- Brute force: gdzie tu jest masa? --------------------------------

    data class Candidate(
        val offset: Int,
        val endian: String,
        val divisor: Int,
        val value: Double
    ) {
        override fun toString() = "offset=%2d %s /%d -> %.2f".format(offset, endian, divisor, value)
    }

    /**
     * Przeszukuje ramkę w poszukiwaniu wartości, które wyglądają jak masa ciała.
     * Zwraca wszystkie 16-bitowe pola (LE i BE, dzielone przez 10 i 100),
     * które mieszczą się w podanym zakresie.
     *
     * Jak tego użyć: stań na wagę znając swoją masę (np. 82,4 kg) i zobacz,
     * który offset konsekwentnie daje tę liczbę w kolejnych pomiarach.
     */
    fun findWeightCandidates(
        data: ByteArray,
        min: Double = 20.0,
        max: Double = 250.0
    ): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (i in 0..data.size - 2) {
            for (div in intArrayOf(10, 100)) {
                val le = u16le(data, i).toDouble() / div
                if (le in min..max) out += Candidate(i, "LE", div, le)
                val be = u16be(data, i).toDouble() / div
                if (be in min..max) out += Candidate(i, "BE", div, be)
            }
        }
        return out
    }

    /**
     * Impedancja (bioimpedancja) w tanich wagach to zwykle uint16 w omach,
     * realnie 200-1200 Ω. Szukamy jej tak samo jak masy.
     */
    fun findImpedanceCandidates(data: ByteArray): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (i in 0..data.size - 2) {
            val le = u16le(data, i).toDouble()
            if (le in 150.0..1500.0) out += Candidate(i, "LE", 1, le)
            val be = u16be(data, i).toDouble()
            if (be in 150.0..1500.0) out += Candidate(i, "BE", 1, be)
        }
        return out
    }

    /**
     * ID producentów, których rozgłoszenia zalewają każdy skan i NIGDY nie są wagą.
     * Telefony, laptopy, słuchawki, telewizory. Ich payloady są zwykle zaszyfrowane
     * i losowe, więc brute force dopasowuje się do nich zawsze - i zawsze błędnie.
     */
    private val NOISE_COMPANIES = setOf(
        0x0006, // Microsoft
        0x004C, // Apple
        0x0075, // Samsung
        0x00E0, // Google
        0x0087, // Garmin
        0x0157, // Huawei
        0x038F  // Xiaomi
    )

    fun isNoiseCompany(id: Int): Boolean = id in NOISE_COMPANIES

    /** Serwisy systemowe Google/Apple - Fast Pair, Eddystone, Exposure Notification. */
    private val NOISE_SERVICES = setOf("0xFEF3", "0xFE2C", "0xFD6F", "0xFE9F", "0xFEAA", "0xFD5A")

    fun isNoiseService(uuid: UUID): Boolean = short(uuid) in NOISE_SERVICES

    /**
     * Zbiorcza próba rozszyfrowania jednej ramki.
     *
     * @param deep czy uruchamiać brute force. Dla rozgłoszeń systemowych (Microsoft,
     *   Google, Apple) jest bezwartościowy: przy losowych bajtach ZAWSZE znajdzie
     *   kilkanaście „kandydatów na masę", zasypie log i nic nie wnosi.
     */
    fun annotate(sourceUuid: UUID?, data: ByteArray, deep: Boolean = true): String {
        val lines = mutableListOf<String>()

        // Dekoder standardu BLE wolno stosować WYŁĄCZNIE do charakterystyki 0x2A9D.
        // Puszczony na dowolne bajty dopasowywał się niemal zawsze i produkował
        // zmyślone kilogramy z pakietów, które wagą nie są.
        if (sourceUuid != null && short(sourceUuid) == "0x2A9D") {
            parseStandardWeight(data)?.let { lines += "  ✓ $it" }
        }
        parseFitdays(data)?.let { lines += "  ✓ $it" }
        parseQnScale(data)?.let { lines += "  ✓ $it" }

        if (!deep) {
            return if (lines.isEmpty()) "  (rozgłoszenie systemowe - pomijam analizę)"
            else lines.joinToString("\n")
        }

        val w = findWeightCandidates(data)
        if (w.isNotEmpty()) {
            lines += "  kandydaci na masę: " + w.take(10).joinToString("; ") { it.toString() }
        }
        val imp = findImpedanceCandidates(data)
        if (imp.isNotEmpty()) {
            lines += "  kandydaci na impedancję: " + imp.take(6).joinToString("; ") { it.toString() }
        }
        return if (lines.isEmpty()) "  (brak dopasowania - zapisz ramkę i porównaj z kolejnym pomiarem)"
        else lines.joinToString("\n")
    }

    /**
     * Kazde 16-bitowe pole ramki, big-endian, z offsetem.
     *
     * Sluzy do rozpoznawania ukladu ramki przez obserwacje: przy kilku pomiarach
     * widac, ktore pole sie zmienia razem z masa, ktore razem ze skladem ciala,
     * a ktore stoi w miejscu i jest stala protokolu.
     */
    fun fields16(b: ByteArray): String =
        (0..b.size - 2).joinToString("  ") { i ->
            val v = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            "@%d=%d".format(i, v)
        }
}
