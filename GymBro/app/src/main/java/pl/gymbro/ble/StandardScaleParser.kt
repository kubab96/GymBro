package pl.gymbro.ble

/**
 * Wagi mowiace standardem Bluetooth SIG: Weight Scale Service (0x181D,
 * charakterystyka 0x2A9D) i Body Composition Service (0x181B, 0x2A9C).
 *
 * To jedyny protokol, ktory nie nalezy do zadnego producenta - trzyma sie go
 * wiekszosc wag markowych (Beurer, A&D, Soehnle, Medisana i podobne). Waga
 * wysyla pomiar przez polaczenie GATT, raz, gdy wynik jest juz ustalony,
 * wiec kazda ramke traktujemy jako wynik koncowy.
 */
object StandardScaleParser {

    private const val LB_TO_KG = 0.45359237

    /** Tak waga zglasza "pomiar sie nie udal". */
    private const val FAILED = 0xFFFF

    private fun u16le(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun kg(raw: Int, imperial: Boolean): Double? {
        if (raw == FAILED) return null
        val v = if (imperial) raw * 0.01 * LB_TO_KG else raw * 0.005
        return v.takeIf { it in 2.0..300.0 }
    }

    /**
     * 0x2A9D: [0] flagi (bit0 = jednostki imperialne), [1-2] masa uint16 LE
     * w krokach 0,005 kg albo 0,01 funta. Reszta ramki to data, numer
     * uzytkownika i BMI - nie sa nam potrzebne.
     */
    fun parseWeight(d: ByteArray): WeightSample? {
        if (d.size < 3) return null
        val imperial = d[0].toInt() and 0x01 != 0
        val weight = kg(u16le(d, 1), imperial) ?: return null
        return WeightSample(weight, stable = true, source = "standard BLE")
    }

    /**
     * 0x2A9C: [0-1] flagi, [2-3] tkanka tluszczowa, dalej pola opcjonalne
     * w stalej kolejnosci - kazde obecne tylko wtedy, gdy ma swoj bit.
     * Masa jest jednym z nich (bit 10), wiec trzeba przejsc po wszystkich
     * wczesniejszych, zeby wiedziec, gdzie lezy.
     */
    fun parseBodyComposition(d: ByteArray): WeightSample? {
        if (d.size < 4) return null
        val flags = u16le(d, 0)
        val imperial = flags and 0x0001 != 0
        var at = 4
        // bit -> dlugosc pola, w kolejnosci ze specyfikacji
        val optional = listOf(
            1 to 7,  // data i godzina
            2 to 1,  // numer uzytkownika
            3 to 2,  // podstawowa przemiana materii
            4 to 2,  // procent miesni
            5 to 2,  // masa miesni
            6 to 2,  // masa beztluszczowa
            7 to 2,  // masa tkanek miekkich
            8 to 2   // masa wody
        )
        for ((bit, len) in optional) if (flags and (1 shl bit) != 0) at += len

        var impedance: Double? = null
        if (flags and (1 shl 9) != 0) {
            if (d.size < at + 2) return null
            impedance = (u16le(d, at) / 10.0).takeIf { it in 100.0..3000.0 }
            at += 2
        }
        if (flags and (1 shl 10) == 0 || d.size < at + 2) return null
        val weight = kg(u16le(d, at), imperial) ?: return null
        return WeightSample(weight, stable = true, impedanceOhm = impedance, source = "standard BLE")
    }
}
