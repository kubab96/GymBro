package pl.gymbro.ble

/**
 * Parser wag na ukladzie Chipsea, obslugiwanych przez aplikacje OKOK International.
 *
 * Kluczowa wlasciwosc tych wag: pomiar leci w ROZGLOSZENIU BLE (manufacturer data),
 * a nie przez polaczenie GATT. Czyli nie trzeba sie z waga parowac ani laczyc -
 * wystarczy nasluchiwac. To jest duzo lepsze niz GATT:
 *   - dziala natychmiast, bez negocjacji polaczenia,
 *   - nie walczymy z aplikacja OKOK o wylaczny dostep do urzadzenia,
 *   - zuzywa mniej baterii.
 *
 * Znane warianty roznia sie ID producenta i ukladem ramki.
 *
 * UWAGA na pulapke: Android w `scanRecord.manufacturerSpecificData` trzyma ID producenta
 * jako KLUCZ, a w wartosci juz TYLKO reszte bajtow (bez 2 bajtow ID). Offsety ponizej
 * sa liczone wzgledem tej wartosci. Gdyby w logu wyszlo, ze masa siedzi 2 bajty obok,
 * to wlasnie ta roznica - dlatego rownolegle zostawiamy brute force z ScaleProtocol,
 * ktory to natychmiast pokaze.
 */
object OkOkParser {

    const val MANUF_V20 = 0x20CA
    const val MANUF_V11 = 0x11CA
    const val MANUF_VF0 = 0xF0FF

    data class Measurement(
        val variant: String,
        val weightKg: Double,
        val impedanceOhm: Double?,
        /** null, gdy wariant nie mowi, czy wynik jest ustalony. */
        val stable: Boolean?,
        val divisorUsed: Int,
        val checksumOk: Boolean?
    ) {
        fun pretty(): String {
            val sb = StringBuilder()
            sb.append(if (stable == true) ">>> POMIAR STABILNY: " else "    pomiar w trakcie: ")
            sb.append("%.2f kg".format(weightKg))
            if (impedanceOhm != null && impedanceOhm > 0) {
                sb.append("   impedancja %.1f Ohm".format(impedanceOhm))
            }
            sb.append("   [$variant, /$divisorUsed")
            if (checksumOk != null) sb.append(if (checksumOk) ", CRC OK" else ", CRC ZLY")
            sb.append("]")
            return sb.toString()
        }
    }

    private fun u16be(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /**
     * Dzielnik masy siedzi w bitach flag, ale warianty roznia sie tym, ktore to bity.
     * Zamiast zgadywac, liczymy obie mozliwosci i wybieramy te, ktora daje sensowna
     * mase ciala. Przy masie 20-250 kg tylko jedna z nich trafia w zakres, wiec
     * jest to jednoznaczne - i odporne na to, ze konkretny egzemplarz wagi
     * uzywa innego wariantu flag, niz zakladamy.
     */
    private fun pickDivisor(raw: Int): Pair<Double, Int>? {
        val byTen = raw / 10.0
        val byHundred = raw / 100.0
        return when {
            byHundred in 20.0..250.0 && byTen !in 20.0..250.0 -> byHundred to 100
            byTen in 20.0..250.0 && byHundred !in 20.0..250.0 -> byTen to 10
            // Oba w zakresie - wybieramy /100, bo te wagi maja rozdzielczosc 0,01 kg.
            byHundred in 20.0..250.0 -> byHundred to 100
            byTen in 20.0..250.0 -> byTen to 10
            else -> null
        }
    }

    /**
     * @param manufacturerId klucz z manufacturerSpecificData
     * @param data wartosc z manufacturerSpecificData (juz bez 2 bajtow ID)
     */
    fun parse(manufacturerId: Int, data: ByteArray): Measurement? {
        // KONIECZNA bramka po ID producenta. Bez niej parseC0 akceptowal dowolny
        // pakiet dluzszy niz 13 bajtow i "znajdowal" w nim mase - w logu pojawil sie
        // odczyt 26,50 kg wyciagniety z rozgloszenia komputera z Windows (ID 0x0006).
        // Fałszywy pomiar jest gorszy niz brak pomiaru, bo mu wierzysz.
        if (!looksLikeOkOk(manufacturerId)) return null

        parseV20(manufacturerId, data)?.let { return it }
        parseV11(manufacturerId, data)?.let { return it }
        parseC0(data)?.let { return it }
        return null
    }

    /** Wariant V20: ramka 19 bajtow, masa na 8-9, impedancja na 10-11, flagi na 6. */
    private fun parseV20(manufacturerId: Int, d: ByteArray): Measurement? {
        if (manufacturerId != MANUF_V20 && manufacturerId != MANUF_VF0) return null
        if (d.size < 13) return null

        val (weight, div) = pickDivisor(u16be(d, 8)) ?: return null
        val imp = u16be(d, 10) / 10.0
        val flags = d[6].toInt() and 0xFF
        val stable = (flags and 0x01) != 0

        var xor = 0
        for (i in 0..11) xor = xor xor (d[i].toInt() and 0xFF)
        xor = xor xor 0x20
        val crcOk = (d.size > 12) && (xor and 0xFF) == (d[12].toInt() and 0xFF)

        return Measurement("OKOK V20", weight, imp.takeIf { it > 0 }, stable, div, crcOk)
    }

    /** Wariant V11: ramka 23 bajty, masa na 3-4, jednostka na 9. */
    private fun parseV11(manufacturerId: Int, d: ByteArray): Measurement? {
        if (manufacturerId != MANUF_V11) return null
        if (d.size < 17) return null

        val (weight, div) = pickDivisor(u16be(d, 3)) ?: return null

        var xor = 0
        for (i in 0..15) xor = xor xor (d[i].toInt() and 0xFF)
        xor = xor xor (0xCA xor 0x11)
        val crcOk = (xor and 0xFF) == (d[16].toInt() and 0xFF)

        // Ten wariant nie podaje impedancji w rozgloszeniu. Nie mowi tez, czy
        // wynik jest ustalony: wczesniej kazda ramka uchodzila tu za stabilna,
        // wiec pierwsza liczba zlapana w trakcie wchodzenia na wage stawala sie
        // pomiarem. O ustaleniu rozstrzyga teraz WeightSettler.
        return Measurement("OKOK V11", weight, null, null, div, crcOk)
    }

    /**
     * Wartosc, ktora firmware wstawia w pole impedancji zamiast pomiaru.
     *
     * Ustalone na urzadzeniu (2026-10-10, pietnascie kolejnych ramek z jednej
     * sesji): pole @2-3 wynosi 0x1388 przy KAZDEJ niezerowej masie i 0x0000 przy
     * pustej wadze. Te same 0x1388 przyszly przy 80,20 kg (czlowiek) i przy
     * 14,20 kg (przedmiot) - a przedmiot nie domyka obwodu miedzy elektrodami,
     * wiec realny pomiar nie moglby wyjsc identyczny. To stala, nie odczyt.
     */
    private const val FAKE_IMPEDANCE = 0x1388

    /**
     * Wariant "C0": rozpoznawany po mlodszym bajcie ID rownym 0xC0.
     * Masa na 0-1, flagi na 6.
     *
     * Impedancji ta rodzina nie podaje, mimo ze waga ma elektrody. Pole @2-3
     * wyglada na nia z nazwy i z zakresu, ale sie nie zmienia; pola @4-5 sa
     * stale zerowe. Polaczenia GATT urzadzenie nie przyjmuje, wiec nie ma tez
     * drugiej drogi po te dane. Sklad ciala liczy sobie aplikacja producenta
     * z masy i profilu - my liczymy go z obwodow i tak go podpisujemy.
     */
    private fun parseC0(d: ByteArray): Measurement? {
        if (d.size < 13) return null
        val (weight, div) = pickDivisor(u16be(d, 0)) ?: return null
        val raw = u16be(d, 2)
        // Podanie zmyslonej liczby byloby gorsze niz jej brak: uzytkownik
        // uwierzylby w pomiar, ktorego nie bylo.
        val imp = if (raw == FAKE_IMPEDANCE || raw == 0) null else raw / 10.0
        val flags = d[6].toInt() and 0xFF
        val stable = (flags and 0x01) != 0
        return Measurement("OKOK C0", weight, imp, stable, div, null)
    }

    /** Czy to w ogole moze byc waga OKOK - po samym ID producenta. */
    fun looksLikeOkOk(manufacturerId: Int): Boolean =
        manufacturerId == MANUF_V20 ||
            manufacturerId == MANUF_V11 ||
            manufacturerId == MANUF_VF0 ||
            (manufacturerId and 0xFF) == 0xC0
}
