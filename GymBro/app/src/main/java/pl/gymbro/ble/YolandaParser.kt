package pl.gymbro.ble

/**
 * Parser wag z rodziny Qingniu / Yolanda (QN-Scale).
 *
 * Twoja waga przedstawia sie jako "Yoda0" i nalezy wlasnie tutaj - a nie do
 * rodziny Chipsea/OKOK, pod ktora pisalem pierwszy parser. Aplikacja OKOK
 * International obsluguje sprzet od roznych producentow, wiec jej nazwa nie
 * przesadza o protokole. Dlatego sprawdzenie nazwy rozgloszeniowej bylo konieczne.
 *
 * Rodzina ma dwa warianty i obslugujemy oba, bo nie wiadomo z gory, ktory to:
 *
 *  1. ROZGLOSZENIOWY - pomiar leci w manufacturer data pod ID 0xFFFF,
 *     z magicznym naglowkiem AA BB. Nie trzeba sie laczyc.
 *  2. GATT - trzeba sie polaczyc z serwisem 0xFFE0 (notify 0xFFE1)
 *     albo 0xFFF0 (notify 0xFFF1) i czytac ramki 0x10 / 0x12 / 0x23.
 */
object YolandaParser {

    /** ID producenta uzywane przez wariant rozgloszeniowy. */
    const val BROADCAST_COMPANY_ID = 0xFFFF

    /** Nazwy rozgloszeniowe tej rodziny. */
    private val NAMES = listOf("qn-scale", "yoda", "yolanda", "renpho-scale", "seb-scale", "fit plus")

    fun matchesName(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return NAMES.any { n.contains(it) }
    }

    data class Reading(
        val source: String,
        val weightKg: Double,
        val resistance1: Int?,
        val resistance2: Int?,
        val stable: Boolean
    ) {
        fun pretty(): String {
            val sb = StringBuilder()
            sb.append(if (stable) ">>> POMIAR STABILNY: " else "    pomiar w trakcie: ")
            sb.append("%.2f kg".format(weightKg))
            if (resistance1 != null && resistance1 > 0) sb.append("   R1=$resistance1")
            if (resistance2 != null && resistance2 > 0) sb.append("   R2=$resistance2")
            sb.append("   [$source]")
            return sb.toString()
        }
    }

    private fun u16le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u16be(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    // ---- Wariant 1: rozgloszenie ----------------------------------------

    /**
     * Ramka AABB w manufacturer data pod ID 0xFFFF:
     *   [0-1]   AA BB   magiczny naglowek
     *   [15]    status  (bit 0x20 = pomiar stabilny, bit 0x01 dla rodziny FT-26R)
     *   [17-18] masa    uint16 little-endian, dzielnik 100
     */
    fun parseBroadcast(companyId: Int, d: ByteArray): Reading? {
        if (companyId != BROADCAST_COMPANY_ID) return null
        if (d.size < 19) return null
        if ((d[0].toInt() and 0xFF) != 0xAA || (d[1].toInt() and 0xFF) != 0xBB) return null

        val kg = u16le(d, 17) / 100.0
        if (kg !in 0.5..300.0) return null

        val status = d[15].toInt() and 0xFF
        val stable = (status and 0x20) != 0 || (status and 0x01) != 0

        return Reading("Yolanda/QN rozgloszenie", kg, null, null, stable)
    }

    // ---- Wariant 2: ramki GATT -------------------------------------------

    /**
     * Ramka 0x10 - pomiar na zywo. Istnieja dwa ulozenia pol i nie da sie ich
     * rozroznic po naglowku, wiec liczymy oba i wybieramy to, ktore daje
     * sensowna mase ciala. Przy jednym pomiarze tylko jedno trafia w zakres.
     *
     *   uklad A: [3,4]=masa BE, [5]=stabilny, [6,7]=R1, [8,9]=R2
     *   uklad B: [4]=stabilny,  [5,6]=masa BE, [7,8]=R1, [9,10]=R2
     *
     * @param divisor 10 albo 100 - podaje go ramka 0x12 (bajt 10).
     */
    fun parseLiveFrame(d: ByteArray, divisor: Int): Reading? {
        if (d.isEmpty() || (d[0].toInt() and 0xFF) != 0x10) return null

        if (d.size >= 10) {
            val kg = u16be(d, 3).toDouble() / divisor
            if (kg in 2.0..300.0) {
                return Reading(
                    "Yolanda/QN 0x10 uklad A",
                    kg,
                    u16be(d, 6),
                    u16be(d, 8),
                    (d[5].toInt() and 0xFF) != 0
                )
            }
        }
        if (d.size >= 11) {
            val kg = u16be(d, 5).toDouble() / divisor
            if (kg in 2.0..300.0) {
                return Reading(
                    "Yolanda/QN 0x10 uklad B",
                    kg,
                    u16be(d, 7),
                    u16be(d, 9),
                    (d[4].toInt() and 0xFF) != 0
                )
            }
        }
        return null
    }

    /**
     * Ramka 0x23 - pomiar odtworzony z pamieci wagi.
     *   [10,11] masa BE, dzielnik 100
     *   [13,14] R1 little-endian
     *   [15,16] R2 little-endian
     */
    fun parseMemoryFrame(d: ByteArray): Reading? {
        if (d.isEmpty() || (d[0].toInt() and 0xFF) != 0x23) return null
        if (d.size < 17) return null
        val kg = u16be(d, 10) / 100.0
        if (kg !in 2.0..300.0) return null
        return Reading("Yolanda/QN 0x23 z pamieci", kg, u16le(d, 13), u16le(d, 15), true)
    }

    /**
     * Ramka 0x12 niesie typ protokolu i skale masy na bajcie 10:
     * wartosc 1 oznacza dzielnik 100, cokolwiek innego - 10.
     */
    fun parseScaleFactor(d: ByteArray): Int? {
        if (d.isEmpty() || (d[0].toInt() and 0xFF) != 0x12) return null
        if (d.size < 11) return null
        return if ((d[10].toInt() and 0xFF) == 1) 100 else 10
    }

    /** Suma kontrolna rodziny QN: suma bajtow modulo 256. */
    fun checksum(buf: ByteArray, from: Int, to: Int): Int {
        var s = 0
        for (i in from..to) s = (s + (buf[i].toInt() and 0xFF)) and 0xFF
        return s
    }

    /** Zbiorcza proba dla ramki przychodzacej po GATT. */
    fun parseGattFrame(d: ByteArray, divisor: Int): Reading? =
        parseLiveFrame(d, divisor) ?: parseMemoryFrame(d)
}
