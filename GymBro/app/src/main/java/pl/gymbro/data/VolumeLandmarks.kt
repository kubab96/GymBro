package pl.gymbro.data

/**
 * Ocena tygodniowej objętości treningowej na grupę mięśniową.
 *
 * Objętość mierzymy **liczbą serii roboczych**, nie tonażem. Tonaż faworyzuje
 * ćwiczenia z dużym ciężarem i mówi więcej o tym, co trenujesz, niż ile.
 * Liczba serii bliżej odpowiada bodźcowi wzrostowemu.
 *
 * Progi są umowne i pochodzą z literatury o hipertrofii - różne źródła podają
 * nieco inne wartości, a odpowiedź na objętość jest mocno indywidualna.
 * Dlatego aplikacja **opisuje**, a nie ocenia: mówi „mało" i „dużo", a nie
 * „źle" i „dobrze". Decyzja należy do Ciebie.
 */
object VolumeLandmarks {

    /** Poniżej tylu serii bodziec zwykle nie wystarcza do wzrostu. */
    const val MINIMUM = 6

    /** Typowy zakres produktywny. */
    const val PRODUCTIVE_FROM = 10
    const val PRODUCTIVE_TO = 20

    /** Powyżej tylu serii regeneracja zwykle przestaje nadążać. */
    const val HIGH = 22

    enum class Level { NONE, LOW, PRODUCTIVE, HIGH, VERY_HIGH }

    fun level(sets: Int): Level = when {
        sets == 0 -> Level.NONE
        sets < MINIMUM -> Level.LOW
        sets <= PRODUCTIVE_TO -> Level.PRODUCTIVE
        sets <= HIGH + 6 -> Level.HIGH
        else -> Level.VERY_HIGH
    }

    fun describe(sets: Int): String = when (level(sets)) {
        Level.NONE -> "brak pracy w tym tygodniu"
        Level.LOW -> "poniżej progu, od którego zwykle widać wzrost"
        Level.PRODUCTIVE -> "w typowym zakresie produktywnym"
        Level.HIGH -> "sporo - pilnuj regeneracji"
        Level.VERY_HIGH -> "bardzo dużo, rzadko da się to utrzymać"
    }

    /** Odniesienie do skali na wykresie słupkowym. */
    const val CHART_MAX = 28
}
