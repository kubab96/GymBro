package pl.gymbro.gym

import pl.gymbro.data.roundKg
import pl.gymbro.data.stepOrDefault

/**
 * Progi ciężaru dla sprzętu, którego nie składa się z talerzy.
 *
 * Druga połowa tego, co [PlateCalculator] robi dla sztangi i hantli. Tam
 * progi wynikają z zawartości siłowni i trzeba je wyliczyć; tutaj są równe
 * i wynikają z jednej liczby - kroku zapisanego przy ćwiczeniu. Mimo to
 * mieszkają w osobnym pliku, a nie w trzech miejscach [pl.gymbro.net.WatchApi],
 * gdzie leżały wcześniej: **ta sama arytmetyka w trzech kopiach rozjechała
 * się przy pierwszej zmianie reguły.** Dokładnie tak, jak rozjechałyby się
 * dwa miejsca liczące propozycję ciężaru.
 *
 * Przy okazji daje się to sprawdzić na wymyślonych liczbach, bez telefonu
 * i bez emulatora - a to jedyna droga, żeby „28,4 kg na wyciągu" zostało
 * naprawione raz.
 */
object StepLadder {

    /**
     * Następny próg w górę albo w dół.
     *
     * Dół pilnuje zera: ciężar ujemny nie istnieje, a stos nie schodzi
     * poniżej pustego. Góry nie zna nikt - maszyny mają swój koniec, ale
     * aplikacja nie ma skąd go znać, więc nie udaje, że zna.
     */
    fun next(from: Double, stepKg: Double?, up: Boolean): Double {
        val krok = stepOrDefault(stepKg)
        return roundKg(if (up) from + krok else (from - krok).coerceAtLeast(0.0))
    }

    /**
     * Okno progów wokół [from], po [span] w każdą stronę.
     *
     * Zera i wartości ujemnych nie ma na liście: zero znaczy „bez ciężaru",
     * a to zupełnie inna decyzja niż najlżejszy możliwy próg i podejmuje
     * się ją gdzie indziej.
     */
    fun rungs(from: Double, stepKg: Double?, span: Int): List<Double> {
        val krok = stepOrDefault(stepKg)
        return (-span..span)
            .map { roundKg(from + it * krok) }
            .filter { it > 0.0 }
    }
}
