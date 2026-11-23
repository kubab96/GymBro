package pl.gymbro.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Phase

/**
 * Cele kaloryczne i makroskładniki - `docs/kalorie.md`, rozdział 6.
 *
 * Osoba odniesienia: 85 kg, 20 % tłuszczu (czyli 68 kg masy beztłuszczowej),
 * wydatek 2938 kcal - tak jak w `BmrTest`.
 */
class TargetsTest {

    private val wydatek = 2938.3
    private val masa = 85.0
    private val beztluszczowa = 68.0

    @Test
    fun `domyslne tempo zalezy od fazy`() {
        assertEquals(0.7, Targets.defaultRatePct(Phase.CUT), 0.001)
        assertEquals(0.35, Targets.defaultRatePct(Phase.BULK), 0.001)
        assertEquals(0.0, Targets.defaultRatePct(Phase.MAINTAIN), 0.001)
    }

    @Test
    fun `tempa do wyboru maja domyslne w srodku`() {
        for (phase in listOf(Phase.CUT, Phase.BULK)) {
            val tempa = Targets.ratePresets(phase)
            assertEquals(3, tempa.size)
            assertEquals(Targets.defaultRatePct(phase), tempa[1], 0.001)
            assertTrue("$phase rosnaco", tempa[0] < tempa[1] && tempa[1] < tempa[2])
        }
        assertTrue(Targets.ratePresets(Phase.MAINTAIN).isEmpty())
    }

    @Test
    fun `tempo z innej fazy nie przechodzi do nowej`() {
        // 0.7 % zapisane na redukcji, potem zmiana na nadwyzke: dwa razy
        // szybsze przybieranie, niz ktokolwiek wybral. Obowiazuje domyslne.
        assertEquals(0.35, Targets.effectiveRatePct(Phase.BULK, 0.7), 0.001)
        assertEquals(0.7, Targets.effectiveRatePct(Phase.CUT, 0.25), 0.001)
        assertEquals(0.0, Targets.effectiveRatePct(Phase.MAINTAIN, 0.7), 0.001)
    }

    @Test
    fun `tempo wybrane w zakresie fazy obowiazuje`() {
        assertEquals(1.0, Targets.effectiveRatePct(Phase.CUT, 1.0), 0.001)
        assertEquals(0.25, Targets.effectiveRatePct(Phase.BULK, 0.25), 0.001)
        assertEquals(0.7, Targets.effectiveRatePct(Phase.CUT, null), 0.001)
        // 1 % z 85 kg to 850 g na tydzien, czyli 935 kcal na dobe - ale to
        // 32 % wydatku, wiec wiaze limit cwiartki: 2938 - 735.
        assertEquals(2200.0, Targets.kcalTarget(wydatek, masa, Phase.CUT, 1.0), 0.01)
        assertEquals(0.85, Targets.kgPerWeek(masa, 1.0), 0.001)
    }

    @Test
    fun `redukcja odejmuje 0_7 procent masy na tydzien`() {
        // 0.7 % z 85 kg to 0.595 kg na tydzien, czyli 654.5 kcal na dobe.
        assertEquals(2280.0, Targets.kcalTarget(wydatek, masa, Phase.CUT), 0.01)
    }

    @Test
    fun `nadwyzka jest wyrazniej mniejsza niz deficyt`() {
        // 0.35 %, nie 0.7 - nadmiaru nadwyzki nie da sie zamienic w miesnie.
        assertEquals(3270.0, Targets.kcalTarget(wydatek, masa, Phase.BULK), 0.01)
    }

    @Test
    fun `utrzymanie nie zmienia niczego poza zaokragleniem`() {
        assertEquals(2940.0, Targets.kcalTarget(wydatek, masa, Phase.MAINTAIN), 0.01)
    }

    @Test
    fun `limit deficytu dziala przy malej masie i agresywnym tempie`() {
        // 1.5 % z 55 kg to 907 kcal na dobe, a to 45 % wydatku. Wiazacy jest
        // limit cwiartki wydatku, nie procent masy - i to jest jego caly cel.
        assertEquals(1500.0, Targets.kcalTarget(2000.0, 55.0, Phase.CUT, 1.5), 0.01)
    }

    @Test
    fun `bialko z masy beztluszczowej, gdy ja znamy`() {
        val cel = Targets.target(0, wydatek, masa, Phase.CUT, beztluszczowa)
        // 2.4 g na kilogram masy beztluszczowej: 2.4 * 68
        assertEquals(163.0, cel.proteinG, 0.01)
        assertEquals(2280.0, cel.kcal, 0.01)
    }

    @Test
    fun `bez skladu ciala bialko liczone z masy ciala`() {
        val cel = Targets.target(0, wydatek, masa, Phase.CUT, leanKg = null)
        // 2.2 * 85 = 187
        assertEquals(187.0, cel.proteinG, 0.01)
    }

    @Test
    fun `w utrzymaniu bialka jest mniej niz w deficycie`() {
        val redukcja = Targets.macros(0, 2280.0, masa, Phase.CUT, beztluszczowa)
        val utrzymanie = Targets.macros(0, 2280.0, masa, Phase.MAINTAIN, beztluszczowa)
        assertEquals(163.0, redukcja.proteinG, 0.01)
        assertEquals(136.0, utrzymanie.proteinG, 0.01)
    }

    @Test
    fun `tluszcz ma podloge na kilogram masy`() {
        // 25 % z 1600 kcal to 44 g, a podloga przy 90 kg to 54 g.
        val cel = Targets.macros(0, 1600.0, 90.0, Phase.CUT)
        assertEquals(54.0, cel.fatG, 0.01)
    }

    @Test
    fun `gorny limit bialka dziala przy duzej masie bez skladu ciala`() {
        // 2.2 * 120 = 264 g, czyli 1056 kcal z 1500 - ponad dwie trzecie.
        // Wiazacy jest limit 40 % kalorii.
        val cel = Targets.macros(0, 1500.0, 120.0, Phase.CUT)
        assertEquals(150.0, cel.proteinG, 0.01)
    }

    @Test
    fun `wegle nie wychodza ponizej zera`() {
        // Przypadek skrajny, ale prawdziwy: bialko i podloga tluszczu same
        // zjadaja caly limit. Ustepuje najpierw tluszcz do podlogi, potem
        // bialko - a wegle zostaja na zerze, bo cel z ujemna liczba gramow
        // jest nie do wykonania.
        val cel = Targets.macros(0, 800.0, 100.0, Phase.CUT)
        assertEquals(0.0, cel.carbsG, 0.01)
        assertEquals(60.0, cel.fatG, 0.01)
        assertEquals(65.0, cel.proteinG, 0.01)
        // Tluszcz zostal na podlodze, bialko ustapilo - i suma sie domyka.
        assertEquals(800.0, cel.proteinKcal + cel.carbsKcal + cel.fatKcal, 0.01)
    }

    @Test
    fun `suma makro zgadza sie z liczba kalorii`() {
        // To jedyne miejsce w aplikacji, gdzie uzytkownik moze sume sprawdzic
        // w glowie. Zaokraglanie do calych gramow zostawia najwyzej 2 kcal.
        for (phase in Phase.entries) {
            val cel = Targets.target(0, wydatek, masa, phase, beztluszczowa)
            val suma = cel.proteinKcal + cel.carbsKcal + cel.fatKcal
            assertTrue(
                "$phase: ${cel.kcal} vs $suma",
                kotlin.math.abs(cel.kcal - suma) <= 2.0
            )
        }
    }

    @Test
    fun `blonnik liczony na tysiac kalorii`() {
        assertEquals(32.0, Targets.fiberTarget(2280.0), 0.01)
    }

    @Test
    fun `cel reczny zachowuje swoj znacznik`() {
        // Cel wpisany recznie nie moze byc nadpisany przez kolejne
        // oszacowanie wydatku - stad ten znacznik jedzie razem z celem.
        val cel = Targets.macros(10, 2000.0, masa, Phase.CUT, manual = true)
        assertTrue(cel.manual)
        assertEquals(10L, cel.fromDay)
    }
}
