package pl.gymbro.ble

import kotlin.math.abs

/**
 * Rozstrzyga, kiedy ciag odczytow z wagi jest juz wynikiem.
 *
 * Wczesniej wynikiem byla wylacznie ramka, w ktorej waga sama ustawila bit
 * "ustalone" - i to zawodzilo, bo do telefonu dociera ulamek tego, co waga
 * nadaje (zmierzone 2026-11-13: dwa pakiety na dziesiec sekund). Wystarczylo
 * nie trafic na te jedna ramke, zanim waga zgasla, i pomiaru nie bylo wcale.
 *
 * Dlatego sa trzy drogi do wyniku, od najpewniejszej:
 *  1. waga potwierdzila sama - bierzemy od razu, pierwsza taka ramke;
 *  2. ta sama liczba utrzymuje sie odpowiednio dlugo - uznajemy ja sami;
 *  3. waga zamilkla po podaniu liczby - proponujemy ostatnia, z zastrzezeniem.
 *
 * Czysta klasa bez zegara: czas podaje wolajacy, wiec test przechodzi przez
 * caly pomiar w milisekundzie.
 */
class WeightSettler(
    /** Protokol nie mowi nic o stabilnosci - wystarczy krotkie utrzymanie. */
    private val steadyUnknownMs: Long = 1_500,
    /**
     * Waga mowi "jeszcze nie", a liczba stoi. Dluzej, bo wagi potrafia na
     * koniec skorygowac wynik o jedna dzialke - ale nie w nieskonczonosc:
     * bit stabilnosci bywa w innym miejscu, niz zaklada parser.
     */
    private val steadyUnflaggedMs: Long = 3_500,
    /**
     * Cisza, po ktorej uznajemy, ze waga zgasla. Sporo ponad zmierzone
     * odstepy miedzy pakietami (2-5 s), inaczej zwykla dziura w odbiorze
     * konczylaby pomiar w polowie wazenia.
     */
    private val silenceMs: Long = 8_000,
    /** Jedna dzialka wagi 0,05 kg z zapasem na zaokraglenie. */
    private val toleranceKg: Double = 0.051
) {
    enum class Reason { SCALE_SAID, STEADY, SILENCE }

    data class Verdict(val sample: WeightSample, val reason: Reason) {
        /** Przy ciszy mogla nam umknac ostatnia korekta - ekran o tym mowi. */
        val certain: Boolean get() = reason != Reason.SILENCE
    }

    private var run: WeightSample? = null
    private var runSince = 0L
    private var runCount = 0
    private var lastAt = 0L

    fun reset() {
        run = null
        runCount = 0
    }

    fun offer(sample: WeightSample, now: Long): Verdict? {
        if (sample.stable == true) {
            reset()
            return Verdict(sample, Reason.SCALE_SAID)
        }
        val current = run
        if (current != null && abs(sample.weightKg - current.weightKg) <= toleranceKg) {
            runCount++
            // Poczatek serii zostaje, ale impedancja potrafi dojsc pozniej
            // niz masa - bierzemy najswiezsza ramke.
            run = sample
        } else {
            run = sample
            runSince = now
            runCount = 1
        }
        lastAt = now

        val needed = if (sample.stable == null) steadyUnknownMs else steadyUnflaggedMs
        // Dwie ramki, nie jedna: pojedynczy odczyt nie mowi, ze liczba stoi.
        if (runCount >= 2 && now - runSince >= needed) {
            reset()
            return Verdict(sample, Reason.STEADY)
        }
        return null
    }

    /** Wolane co chwile - jedyna droga, zeby zauwazyc, ze nic nie przychodzi. */
    fun tick(now: Long): Verdict? {
        val current = run ?: return null
        if (now - lastAt < silenceMs) return null
        reset()
        return Verdict(current, Reason.SILENCE)
    }
}
