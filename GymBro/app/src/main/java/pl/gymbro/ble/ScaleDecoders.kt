package pl.gymbro.ble

import java.util.UUID

/**
 * Jeden odczyt z wagi, zanim zapadnie decyzja, czy to juz wynik.
 *
 * [stable] ma trzy stany celowo. Czesc protokolow mowi wprost "ustalone" albo
 * "jeszcze nie", a czesc nie niesie tej informacji wcale - i wtedy o tym, czy
 * liczba przestala sie zmieniac, musi rozstrzygnac [WeightSettler].
 */
data class WeightSample(
    val weightKg: Double,
    val stable: Boolean?,
    val impedanceOhm: Double? = null,
    val source: String
)

/**
 * Co wyczytalismy z rozgloszenia: [sample] jest puste, gdy to na pewno waga,
 * ale bez masy do pokazania (pusta, wlasnie obudzona, ktos z niej zszedl).
 * Ten stan tez jest informacja - ekran moze powiedziec "waga znaleziona".
 */
data class AdvertRead(val family: String, val sample: WeightSample?)

/**
 * Jedno miejsce, ktore zna wszystkie obslugiwane rodziny wag.
 *
 * Nie ma protokolu wspolnego dla wszystkich wag - kazda aplikacja, ktora
 * "laczy sie z kazda waga", ma w srodku liste sterownikow i dobiera je po
 * tym, czym waga sie przedstawia. Tutaj jest tak samo:
 *
 *   rozgloszenie (bez laczenia)        polaczenie GATT
 *   - OKOK / Chipsea, cztery warianty  - standard Bluetooth SIG (0x2A9D, 0x2A9C)
 *   - Qingniu / Yolanda, ramka AABB    - Qingniu / Yolanda (Renpho, Yoda, QN-Scale)
 *   - Xiaomi Mi Scale i Body Composition
 *
 * Czyste funkcje na bajtach, bez Androida - dzieki temu kazda rodzine da sie
 * sprawdzic w tescie na prawdziwych ramkach, bez wagi i bez telefonu.
 */
object ScaleDecoders {

    private const val SVC_WEIGHT = "0x181D"
    private const val SVC_BODY = "0x181B"

    /**
     * @param manufacturer pary (ID producenta, dane bez dwoch bajtow ID)
     * @param serviceData pary (UUID serwisu, dane)
     */
    fun fromAdvert(
        manufacturer: List<Pair<Int, ByteArray>>,
        serviceData: List<Pair<UUID, ByteArray>>
    ): AdvertRead? {
        var awake: AdvertRead? = null

        for ((id, bytes) in manufacturer) {
            if (YolandaParser.isBroadcastFrame(id, bytes)) {
                val y = YolandaParser.parseBroadcast(id, bytes)
                val family = "Qingniu / Yolanda (rozgloszenie)"
                if (y != null) return AdvertRead(family, WeightSample(y.weightKg, y.stable, source = y.source))
                awake = AdvertRead(family, null)
                continue
            }
            if (OkOkParser.looksLikeOkOk(id) && bytes.size >= 13) {
                val family = "OKOK / Chipsea (rozgloszenie)"
                val m = OkOkParser.parse(id, bytes)
                if (m != null) {
                    return AdvertRead(
                        family,
                        WeightSample(m.weightKg, m.stable, m.impedanceOhm, m.variant)
                    )
                }
                awake = AdvertRead(family, null)
            }
        }

        for ((uuid, bytes) in serviceData) {
            val read = when (ScaleProtocol.short(uuid)) {
                SVC_WEIGHT -> MiScaleParser.parseV1(bytes)
                SVC_BODY -> MiScaleParser.parseV2(bytes)
                else -> null
            } ?: continue
            if (read.sample != null) return read
            awake = read
        }
        return awake
    }

    /**
     * Czy z tym urzadzeniem warto sie polaczyc, zeby dostac pomiar.
     *
     * Celowo wasko: serwisy standardu i nazwy rodziny Qingniu. Serwisy 0xFFE0
     * i 0xFFF0 maja tez tysiace urzadzen, ktore waga nie sa (moduly UART,
     * zarowki, zamki) - laczenie sie z kazdym z nich na czyjejs klatce
     * schodowej nie jest tym, czego chcemy.
     */
    fun wantsConnection(name: String?, services: List<UUID>): Boolean {
        val short = services.map { ScaleProtocol.short(it) }
        return SVC_WEIGHT in short || SVC_BODY in short || YolandaParser.matchesName(name)
    }

    /**
     * Ramka z powiadomienia GATT.
     *
     * Dekoder dobieramy po charakterystyce, nigdy po samym ksztalcie bajtow:
     * dekoder standardu puszczony na dowolne dane dopasowywal sie prawie
     * zawsze i produkowal zmyslone kilogramy.
     */
    fun fromGatt(characteristic: UUID, data: ByteArray, qnDivisor: Int): WeightSample? =
        when (ScaleProtocol.short(characteristic)) {
            "0x2A9D" -> StandardScaleParser.parseWeight(data)
            "0x2A9C" -> StandardScaleParser.parseBodyComposition(data)
            "0xFFE1", "0xFFF1" -> YolandaParser.parseGattFrame(data, qnDivisor)?.let { y ->
                WeightSample(
                    y.weightKg,
                    y.stable,
                    // Rodzina QN podaje opor w surowych jednostkach przetwornika,
                    // nie w omach - do pola impedancji sie nie nadaje.
                    impedanceOhm = null,
                    source = y.source
                )
            }
            else -> null
        }
}
