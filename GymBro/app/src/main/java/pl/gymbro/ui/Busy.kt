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
 * Sygnał „przelicz plakietkę na zakładce Plan".
 *
 * Plakietka liczyła się tylko przy zmianie zakładki, a propozycje gasną
 * w środku Planu - po wejściu w jednostkę liczba dalej świeciła, dopóki nie
 * wyszło się z zakładki i nie wróciło. Pełny [Refresh] przeładowałby przy tym
 * wszystkie zakładki, więc plakietka ma własny, lekki licznik.
 */
object AdviceBadge {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
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

/**
 * Sygnał „zmienił się trwający trening" - seria albo pauza z zegarka.
 *
 * Osobno od [Refresh], bo [Refresh] przeładowuje wszystkie zakładki naraz:
 * historię z trzech tysięcy aktywności, dzień w Kaloriach, Profil, Plan.
 * Seria z zegarka zmienia z tego tylko trwający trening, a przychodziła
 * po kilkanaście w paczce - każda z pełnym przeładowaniem wszystkiego.
 */
object LiveRefresh {
    var version by mutableIntStateOf(0)
        private set

    fun bump() {
        version++
    }
}

/**
 * Zmiany z zegarka zebrane w jedną.
 *
 * Paczka zaległych poleceń to kilkanaście zgłoszeń w kilkadziesiąt
 * milisekund. Odświeżenie leci raz, po chwili ciszy, i tylko takie, jakiego
 * wymaga najszersza ze zgłoszonych zmian.
 *
 * [post] wolno wołać z dowolnego wątku; reszta dzieje się na głównym.
 */
class WatchChanges(private val handler: android.os.Handler) {
    private val reasons = mutableSetOf<String>()

    private val flush = Runnable {
        val all = reasons.toSet()
        reasons.clear()
        if ("set" in all) ExternalSet.bump()
        if (all.any { it != "set" && it != "pause" }) Refresh.bump() else LiveRefresh.bump()
    }

    fun post(reason: String) {
        handler.post {
            reasons += reason
            handler.removeCallbacks(flush)
            handler.postDelayed(flush, DELAY_MS)
        }
    }

    fun cancel() {
        handler.removeCallbacks(flush)
    }

    private companion object {
        /** Dłużej niż przerwa między poleceniami jednej paczki, krócej niż da się zauważyć. */
        const val DELAY_MS = 250L
    }
}
