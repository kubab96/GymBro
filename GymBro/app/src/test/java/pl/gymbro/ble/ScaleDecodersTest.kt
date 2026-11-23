package pl.gymbro.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.BiaPath
import pl.gymbro.data.BiaValue
import java.util.UUID

class ScaleDecodersTest {

    private fun bytes(hex: String): ByteArray =
        hex.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun sig(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    private fun manufacturer(id: Int, hex: String) =
        ScaleDecoders.fromAdvert(listOf(id to bytes(hex)), emptyList())

    private fun service(short: String, hex: String) =
        ScaleDecoders.fromAdvert(emptyList(), listOf(sig(short) to bytes(hex)))

    // ---- Domowa waga: ramki przepisane z logu z 2026-11-13 ----------------

    @Test
    fun `ramka domowej wagi z ustalonym wynikiem daje mase od razu`() {
        val read = manufacturer(0x5BC0, "20 D5 13 88 00 00 25 00 00 00 00 00 00")
        val s = read!!.sample!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(true, s.stable)
        // 0x1388 to stala wpisywana przez firmware, nie pomiar.
        assertTrue(s.bia.isEmpty())
    }

    @Test
    fun `pusta waga jest rozpoznana, ale nie daje pomiaru`() {
        val read = manufacturer(0x5BC0, "00 00 00 00 00 00 24 00 00 00 00 00 00")
        assertNotNull(read)
        assertNull(read!!.sample)
    }

    @Test
    fun `ta sama ramka bez bitu stabilnosci jest odczytem w trakcie`() {
        val s = manufacturer(0x5BC0, "20 D5 13 88 00 00 24 00 00 00 00 00 00")!!.sample!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(false, s.stable)
    }

    @Test
    fun `rozgloszenie komputera nie jest waga`() {
        // ID 0x0006 to Microsoft. Z takiego pakietu parser wyciagnal kiedys 26,50 kg.
        val read = manufacturer(
            0x0006,
            "01 09 20 22 EA 85 D0 0C 5F E7 52 42 0B 38 C8 F3 06 7D 98 0B 14 05 BC AC B7 43 3D"
        )
        assertNull(read)
    }

    @Test
    fun `wariant V11 nie udaje, ze wynik jest ustalony`() {
        val s = manufacturer(0x11CA, "00 00 00 20 D5 00 00 00 00 00 00 00 00 00 00 00 00")!!.sample!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertNull(s.stable)
    }

    // ---- Xiaomi -----------------------------------------------------------

    @Test
    fun `mi scale ustalony wynik w kilogramach`() {
        // 0x3660 = 13920, w krokach 0,005 kg
        val s = service("181d", "22 60 36 E3 07 07 11 0F 1F 11")!!.sample!!
        assertEquals(69.6, s.weightKg, 0.001)
        assertEquals(true, s.stable)
    }

    @Test
    fun `mi scale odczyt w trakcie nie jest jeszcze wynikiem`() {
        val s = service("181d", "02 60 36 E3 07 07 11 0F 1F 11")!!.sample!!
        assertEquals(false, s.stable)
    }

    @Test
    fun `mi scale po zejsciu z wagi nie podaje masy`() {
        val read = service("181d", "A2 60 36 E3 07 07 11 0F 1F 11")
        assertNotNull(read)
        assertNull(read!!.sample)
    }

    @Test
    fun `mi scale w funtach jest przeliczona na kilogramy`() {
        // 18530 = 185,30 funta
        val s = service("181d", "23 62 48 E3 07 07 11 0F 1F 11")!!.sample!!
        assertEquals(84.05, s.weightKg, 0.01)
    }

    @Test
    fun `mi scale ustawiona na jin nie daje zgadywanej masy`() {
        val read = service("181d", "32 60 36 E3 07 07 11 0F 1F 11")
        assertNull(read!!.sample)
    }

    @Test
    fun `waga xiaomi ze skladem ciala podaje mase i impedancje`() {
        // masa 0x41AA = 16810 -> 84,05 kg; impedancja 0x01F4 = 500 omow
        val s = service("181b", "02 26 E3 07 07 11 0F 1F 11 F4 01 AA 41")!!.sample!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(listOf(BiaValue(BiaPath.FOOT_TO_FOOT, 500.0)), s.bia)
        assertEquals(true, s.stable)
    }

    @Test
    fun `impedancja bez bitu potwierdzenia jest pomijana`() {
        val s = service("181b", "02 20 E3 07 07 11 0F 1F 11 F4 01 AA 41")!!.sample!!
        assertTrue(s.bia.isEmpty())
    }

    // ---- Standard Bluetooth SIG -------------------------------------------

    @Test
    fun `standardowy pomiar masy w kilogramach`() {
        val s = ScaleDecoders.fromGatt(sig("2a9d"), bytes("00 AA 41"), 100)!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(true, s.stable)
    }

    @Test
    fun `standardowy pomiar masy w funtach`() {
        val s = ScaleDecoders.fromGatt(sig("2a9d"), bytes("01 62 48"), 100)!!
        assertEquals(84.05, s.weightKg, 0.01)
    }

    @Test
    fun `nieudany pomiar standardu nie jest masa`() {
        assertNull(ScaleDecoders.fromGatt(sig("2a9d"), bytes("00 FF FF"), 100))
    }

    @Test
    fun `sklad ciala - masa lezy za polami opcjonalnymi`() {
        // flagi 0x0602: data (7 B), impedancja, masa
        val frame = "02 06  C8 00  E3 07 07 11 0F 1F 11  88 13  AA 41".replace("  ", " ")
        val s = ScaleDecoders.fromGatt(sig("2a9c"), bytes(frame), 100)!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(listOf(BiaValue(BiaPath.FOOT_TO_FOOT, 500.0)), s.bia)
    }

    @Test
    fun `sklad ciala bez pola masy nie daje odczytu`() {
        assertNull(ScaleDecoders.fromGatt(sig("2a9c"), bytes("00 00 C8 00"), 100))
    }

    @Test
    fun `dobre bajty z obcej charakterystyki nie sa pomiarem`() {
        // Dekoder dobierany po ksztalcie ramki produkowal zmyslone kilogramy.
        assertNull(ScaleDecoders.fromGatt(sig("2a19"), bytes("00 AA 41"), 100))
    }

    // ---- Qingniu / Yolanda ------------------------------------------------

    @Test
    fun `ramka qn na zywo z polaczenia`() {
        val s = ScaleDecoders.fromGatt(sig("ffe1"), bytes("10 0B 15 20 D5 01 01 F4 01 F0 00"), 100)!!
        assertEquals(84.05, s.weightKg, 0.001)
        assertEquals(true, s.stable)
    }

    @Test
    fun `powitanie qn ma poprawna sume kontrolna`() {
        assertEquals(
            "13 09 15 01 10 00 00 00 42",
            ScaleProtocol.hex(YolandaParser.unitCommand(0x15))
        )
    }

    // ---- Z kim sie laczyc -------------------------------------------------

    @Test
    fun `laczymy sie z waga standardu i z rodzina qn, ale nie z kazdym modulem uart`() {
        assertTrue(ScaleDecoders.wantsConnection(null, listOf(sig("181d"))))
        assertTrue(ScaleDecoders.wantsConnection(null, listOf(sig("181b"))))
        assertTrue(ScaleDecoders.wantsConnection("QN-Scale", emptyList()))
        assertFalse(ScaleDecoders.wantsConnection("Lampka", listOf(sig("ffe0"))))
        assertFalse(ScaleDecoders.wantsConnection(null, emptyList()))
    }
}
