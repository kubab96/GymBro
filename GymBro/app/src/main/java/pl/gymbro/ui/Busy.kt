package pl.gymbro.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import pl.gymbro.data.Cache
import androidx.compose.runtime.setValue

/**
 * Licznik trwających wczytywań, wspólny dla całej aplikacji.
 *
 * Nawigacja czyta go, żeby zablokować przeskakiwanie między zakładkami w trakcie
 * ładowania. Bez tego szybkie klikanie uruchamiało kolejne wczytywania, zanim
 * poprzednie się skończyły - a każde z nich i tak musiało dobiec do końca.
 *
 * To **licznik, nie flaga**: kilka ekranów może ładować się naraz, a flaga
 * zdjęta przez pierwszy kończący się gasiłaby wskaźnik mimo trwającej pracy.
 */
object Busy {

    var count by mutableIntStateOf(0)
        private set

    val isBusy: Boolean get() = count > 0

    /**
     * Opakowuje wczytywanie, podnosząc licznik na czas jego trwania.
     * `finally` gwarantuje zejście licznika także wtedy, gdy korutyna zostanie
     * przerwana - a przerywa ją każde opuszczenie ekranu.
     */
    suspend fun <T> track(block: suspend () -> T): T {
        count++
        try {
            return block()
        } finally {
            count--
        }
    }
}

/**
 * Sygnał „dane się zmieniły, przeładuj".
 *
 * Potrzebny, odkąd zakładki żyją w tle i nie składają się od nowa przy każdym
 * wejściu - ekran raz wczytany sam z siebie nigdy by nie zobaczył treningu,
 * który dociągnął się z zegarka w tle. Ekrany kluczują swoje wczytywanie na
 * [version], więc podbicie licznika wymusza odświeżenie tam, gdzie trzeba.
 */
object Refresh {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        Cache.clear()
        version++
    }
}

/**
 * Sygnał „seria doszła spoza aplikacji", czyli z pilota na zegarku.
 *
 * Osobno od [Refresh], bo niesie inną decyzję: skoro doszła seria, to trening
 * trwa dalej - a jeśli telefon stoi na podsumowaniu, ma z niego wrócić.
 * Inaczej dorobione z zegarka serie dopisywały się do bazy, a ekran telefonu
 * pokazywał dalej liczby sprzed nich.
 */
object ExternalSet {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}
