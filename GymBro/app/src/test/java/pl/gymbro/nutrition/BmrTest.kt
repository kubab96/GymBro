package pl.gymbro.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.gymbro.data.Sex

/**
 * Wzory z `docs/kalorie.md`, rozdział 5.
 *
 * Osoba odniesienia we wszystkich przypadkach: mężczyzna 30 lat, 180 cm,
 * 85 kg, 20 % tkanki tłuszczowej. To ta sama osoba, na której liczony był
 * przykład w dokumencie.
 */
class BmrTest {

    @Test
    fun `Mifflin dla mezczyzny`() {
        // 10*85 + 6.25*180 - 5*30 + 5 = 850 + 1125 - 150 + 5
        assertEquals(1830.0, Bmr.mifflin(85.0, 180.0, 30, Sex.MALE), 0.01)
    }

    @Test
    fun `Mifflin dla kobiety ma inna stala`() {
        // Roznica miedzy plciami to 166 kcal i siedzi wylacznie w stalej.
        //
        // Wzor daje 1270.25, ale wynik przechodzi przez roundKcal, ktory tnie
        // do dziesiatej czesci kcal. Oczekiwanie jest wiec 1270.3 - i tak ma
        // byc: setne czesci kilokalorii w przemianie spoczynkowej to udawanie
        // precyzji, ktorej sam wzor nie ma (myli sie o 10 %).
        assertEquals(1270.3, Bmr.mifflin(60.0, 165.0, 40, Sex.FEMALE), 0.01)
        val m = Bmr.mifflin(60.0, 165.0, 40, Sex.MALE)
        val k = Bmr.mifflin(60.0, 165.0, 40, Sex.FEMALE)
        assertEquals(166.0, m - k, 0.01)
    }

    @Test
    fun `Katch-McArdle liczy z masy beztluszczowej`() {
        assertEquals(68.0, Bmr.leanMass(85.0, 20.0), 0.001)
        // 370 + 21.6 * 68
        assertEquals(1838.8, Bmr.katchMcArdle(68.0), 0.01)
    }

    @Test
    fun `znany sklad ciala wybiera Katcha, nieznany Mifflina`() {
        val zSkladem = Bmr.resting(85.0, 180.0, 30, Sex.MALE, 20.0)!!
        assertEquals(Bmr.Source.KATCH_MCARDLE, zSkladem.source)
        assertEquals(1838.8, zSkladem.kcal, 0.01)

        val bezSkladu = Bmr.resting(85.0, 180.0, 30, Sex.MALE, null)!!
        assertEquals(Bmr.Source.MIFFLIN, bezSkladu.source)
        assertEquals(1830.0, bezSkladu.kcal, 0.01)
    }

    @Test
    fun `Katch rozroznia osoby, ktorych Mifflin nie rozroznia`() {
        // Ta sama masa i ten sam wzrost, inny sklad ciala. Mifflin daje te
        // sama liczbe, Katch rozne - i to jest caly powod, dla ktorego ten
        // drugi wzor tu jest.
        val chudy = Bmr.resting(85.0, 180.0, 30, Sex.MALE, 12.0)!!
        val gruby = Bmr.resting(85.0, 180.0, 30, Sex.MALE, 30.0)!!
        assertEquals(330.5, chudy.kcal - gruby.kcal, 0.5)
    }

    @Test
    fun `absurdalny procent tluszczu z wagi jest ignorowany`() {
        // Waga po bioimpedancji potrafi zwrocic zero albo osiemdziesiat
        // procent. Takiej liczby nie wolno wpuscic do wzoru - wtedy lepszy
        // jest Mifflin, ktory o skladzie nic nie wie.
        val zero = Bmr.resting(85.0, 180.0, 30, Sex.MALE, 0.0)!!
        assertEquals(Bmr.Source.MIFFLIN, zero.source)

        val absurd = Bmr.resting(85.0, 180.0, 30, Sex.MALE, 85.0)!!
        assertEquals(Bmr.Source.MIFFLIN, absurd.source)
    }

    @Test
    fun `bez danych nie ma wyniku, nie ma podstawionej sredniej`() {
        // Lepiej poprosic o wzrost, niz podstawic wzrost przecietny i podac
        // liczbe, ktora wyglada jak pomiar.
        assertNull(Bmr.resting(null, 180.0, 30, Sex.MALE))
        assertNull(Bmr.resting(85.0, null, 30, Sex.MALE))
        assertNull(Bmr.resting(85.0, 180.0, null, Sex.MALE))
        assertNull(Bmr.resting(0.0, 180.0, 30, Sex.MALE))
    }

    @Test
    fun `wydatek ze wzoru dla przykladu z dokumentu`() {
        // (1830 * 1.15 + 340 + 200) / 0.90
        // kroki: 10000 * 85 * 0.0004 = 340
        // trening: 4 * 350 / 7 = 200
        val tdee = Bmr.formulaTdee(1830.0, 85.0, stepsPerDay = 10_000.0, sessionsPerWeek = 4.0)
        assertEquals(2938.3, tdee, 0.05)

        // Mnoznik wzgledem spoczynkowej ma wypasc w zakresie, jaki podaje sie
        // dla osoby trenujacej - na tym polega dobor stalych 1.15 i 0.90.
        assertEquals(1.61, tdee / 1830.0, 0.02)
    }

    @Test
    fun `brak danych o krokach to nie zero krokow`() {
        // Zero krokow to osoba lezaca, a nie osoba bez zegarka.
        val bezZegarka = Bmr.formulaTdee(1830.0, 85.0, stepsPerDay = null)
        val lezaca = Bmr.formulaTdee(1830.0, 85.0, stepsPerDay = 0.0)
        assertEquals(2489.4, bezZegarka, 0.05)
        assertEquals(136.0 / 0.9, bezZegarka - lezaca, 0.05)
    }

    @Test
    fun `trening podnosi wydatek proporcjonalnie do liczby sesji`() {
        val zero = Bmr.formulaTdee(1830.0, 85.0, 10_000.0, 0.0)
        val cztery = Bmr.formulaTdee(1830.0, 85.0, 10_000.0, 4.0)
        // 4 * 350 / 7 = 200 kcal dziennie, plus efekt termiczny
        assertEquals(200.0 / 0.9, cztery - zero, 0.05)
    }
}
