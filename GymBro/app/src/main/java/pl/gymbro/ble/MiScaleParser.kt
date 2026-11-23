package pl.gymbro.ble

/**
 * Wagi Xiaomi: Mi Smart Scale (pierwsza i druga) oraz Mi Body Composition Scale.
 *
 * Tak jak wagi OKOK nadaja pomiar w samym rozgloszeniu, tyle ze w danych
 * serwisu, a nie producenta: 0x181D dla zwyklej wagi, 0x181B dla wagi ze
 * skladem ciala. Laczyc sie nie trzeba.
 *
 * Uklad ramek wg otwartych opisow protokolu (openScale, ESPHome). Nie byl
 * sprawdzany na sprzecie - w domu takiej wagi nie ma.
 */
object MiScaleParser {

    private const val LB_TO_KG = 0.45359237

    private fun u16le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    /**
     * Masa w kilogramach albo null, gdy waga jest ustawiona na jednostke,
     * ktorej przelicznika nie jestesmy pewni (jin bywa 0,5 kg i 0,6 kg zaleznie
     * od regionu). Brak odczytu jest tu lepszy niz odczyt o 20% obok.
     */
    private fun kg(raw: Int, lbs: Boolean, jin: Boolean): Double? = when {
        jin -> null
        lbs -> raw / 100.0 * LB_TO_KG
        else -> raw / 200.0
    }

    /**
     * Dane serwisu 0x181D, 10 bajtow:
     *   [0]    stan: bit0 = funty, bit4 = jin, bit5 = wynik ustalony, bit7 = zejscie z wagi
     *   [1-2]  masa, uint16 little-endian (kg: /200, funty: /100)
     *   [3-9]  data i godzina
     */
    fun parseV1(d: ByteArray): AdvertRead? {
        if (d.size < 3) return null
        val status = d[0].toInt() and 0xFF
        val removed = status and 0x80 != 0
        val weight = kg(u16le(d, 1), lbs = status and 0x01 != 0, jin = status and 0x10 != 0)
        if (removed || weight == null || weight !in 2.0..300.0) return AdvertRead(FAMILY, null)
        return AdvertRead(
            FAMILY,
            WeightSample(weight, stable = status and 0x20 != 0, source = "Xiaomi Mi Scale")
        )
    }

    /**
     * Dane serwisu 0x181B, 13 bajtow:
     *   [0]      jednostka: bit0 = funty, bit4 = jin
     *   [1]      stan: bit1 = jest impedancja, bit5 = wynik ustalony, bit7 = zejscie z wagi
     *   [9-10]   impedancja w omach, uint16 little-endian
     *   [11-12]  masa, uint16 little-endian (kg: /200, funty: /100)
     */
    fun parseV2(d: ByteArray): AdvertRead? {
        if (d.size < 13) return null
        val unit = d[0].toInt() and 0xFF
        val status = d[1].toInt() and 0xFF
        val removed = status and 0x80 != 0
        val weight = kg(u16le(d, 11), lbs = unit and 0x01 != 0, jin = unit and 0x10 != 0)
        if (removed || weight == null || weight !in 2.0..300.0) return AdvertRead(FAMILY, null)
        // Waga wpisuje w to pole wartosc dopiero po zmierzeniu; wczesniej leza
        // tam smieci, wiec wierzymy mu wylacznie razem z bitem.
        val imp = if (status and 0x02 != 0) u16le(d, 9).takeIf { it in 100..3000 } else null
        return AdvertRead(
            FAMILY,
            WeightSample(
                weight,
                stable = status and 0x20 != 0,
                footToFootOhm = imp?.toDouble(),
                source = "Xiaomi Body Composition Scale"
            )
        )
    }

    private const val FAMILY = "Xiaomi Mi Scale (rozgloszenie)"
}
