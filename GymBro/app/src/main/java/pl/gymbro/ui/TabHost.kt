package pl.gymbro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** Ułamek szerokości ekranu, o jaki treść przesuwa się przy zmianie zakładki. */
private const val SHIFT = 0.10f

/**
 * Zakładka poza zasięgiem wzroku: nadal zmierzona, ale nierysowana i głucha
 * na dotyk.
 *
 * Pomijanie samego rysowania, a nie pomiaru, jest tu celowe. Gdyby ukryty ekran
 * nie był mierzony, przy każdym powrocie trzeba by go mierzyć od zera - a pomiar
 * rozbudowanego ekranu to kilkadziesiąt milisekund, czyli dokładnie ta zwiecha,
 * którą usuwamy. Zmierzony układ zostaje w pamięci i dopóki nic się w nim nie
 * zmienia, nie kosztuje nic na klatkę.
 *
 * Głuchota na dotyk jest konieczna, bo ukryty ekran nadal leży w drzewie:
 * dotknięcie w miejscu, gdzie widoczny ekran niczego nie obsługuje, trafiłoby
 * w niewidoczny przycisk pod spodem. Zdarzenie jest pochłaniane w przebiegu
 * wstępnym, więc nie dochodzi do niczego poniżej.
 */
private fun Modifier.inert(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * Pojemnik na zakładki, w którym ekran składa się raz i dalej żyje.
 *
 * Poprzednio przełączanie szło przez `AnimatedContent`, czyli poprzedni ekran
 * był usuwany, a następny składany od zera - przy każdym dotknięciu paska.
 * Pomiar na telefonie (`dumpsys gfxinfo`) pokazywał z tego powodu klatki po
 * 100-150 ms, i to niezależnie od tego, czy dane były już w pamięci podręcznej.
 * Składanie ekranu, nie odczyt z bazy, było tu najdroższą częścią pracy. Zwiecha
 * wypadała dokładnie na początku animacji, więc przejście nie tyle zwalniało, co
 * najpierw stawało, a potem skakało - stąd wrażenie sztywności.
 *
 * Tutaj ekran powstaje przy pierwszym wejściu i zostaje. Zmiana zakładki nie
 * składa już niczego: przestawia tylko to, co jest mierzone i rysowane. Przy
 * okazji każda zakładka zachowuje swój stan - pozycję przewijania, rozwinięte
 * karty, wpisane wartości - zamiast wracać do punktu wyjścia.
 *
 * W trakcie przejścia rysowana jest wyłącznie zakładka docelowa. Poprzednia
 * znika od razu i nie bierze udziału w animacji - dwie treści naraz na ekranie
 * czytają się jak usterka, a nie jak ruch.
 *
 * @param prewarm czy zakładki, w których jeszcze nie byłeś, mają się po cichu
 *   złożyć w tle. Dzięki temu również pierwsze wejście jest natychmiastowe.
 */
@Composable
fun TabHost(
    selected: Int,
    count: Int,
    modifier: Modifier = Modifier,
    prewarm: Boolean = true,
    tab: @Composable (Int) -> Unit
) {
    var from by remember { mutableIntStateOf(selected) }
    var to by remember { mutableIntStateOf(selected) }
    val progress = remember { Animatable(1f) }

    // Zakładki, które już istnieją. Ekranu, w którym nie byłeś i który nie
    // zdążył się rozgrzać, nie ma w drzewie wcale.
    var alive by remember { mutableStateOf(setOf(selected)) }

    LaunchedEffect(selected) {
        if (selected == to) return@LaunchedEffect
        alive = alive + selected
        from = to
        to = selected
        progress.snapTo(0f)
        // Sprężyna bez odbicia zamiast stałego czasu: zwalnia pod koniec sama,
        // a przerwana w połowie podejmuje ruch od miejsca, w którym była,
        // zamiast przeskakiwać. Przy szybkim klikaniu to cała różnica.
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    // Rozgrzewanie: pozostałe zakładki składają się po kolei, gdy nic się nie
    // dzieje. Ustępują pierwszeństwa temu, na co właśnie patrzysz - stąd
    // czekanie na koniec wczytywania i odstęp między kolejnymi.
    LaunchedEffect(prewarm, count) {
        if (!prewarm) return@LaunchedEffect
        delay(700)
        for (i in 0 until count) {
            if (i in alive) continue
            while (Busy.isBusy) delay(80)
            alive = alive + i
            delay(300)
        }
    }

    val p = progress.value
    val moving = p < 1f && from != to
    val direction = if (to > from) 1f else -1f

    Box(modifier) {
        for (i in 0 until count) {
            if (i !in alive) continue
            val shown = i == to
            Box(
                Modifier
                    .fillMaxSize()
                    // Widoczna zakładka na wierzchu.
                    //
                    // Nie jest to kwestia wyglądu, tylko dotyku: w Box trafienie
                    // sprawdza się od ostatniego dziecka do pierwszego, więc bez
                    // tego zakładki o wyższym numerze przechwytywałyby zdarzenia
                    // przeznaczone dla niższych - ukryte, ale wciąż pierwsze
                    // w kolejce. Przewijanie na Zegarku zjadały wtedy Plan
                    // i Profil.
                    //
                    // Kolejności w pętli nie wolno tu ruszyć: Compose wiąże stan
                    // z pozycją w drzewie, więc przestawienie zakładek skasowałoby
                    // to, co każda z nich pamięta.
                    .zIndex(if (shown) 1f else 0f)
                    .drawWithContent { if (shown) drawContent() }
                    // Ukryta zakładka znika też dla czytnika ekranu. Bez tego
                    // TalkBack czytałby zawartość wszystkich pięciu naraz -
                    // leżą w drzewie równocześnie, choć widać jedną.
                    .then(if (shown) Modifier else Modifier.inert().clearAndSetSemantics {})
                    // Nieprzezroczyste tło pod treścią zakładki.
                    //
                    // To ono sprawia, że poprzedniej zakładki nie widać ani
                    // przez moment: jest rysowana wyłącznie zakładka docelowa,
                    // a miejsce po poprzedniej zakrywa jednolite tło. Treść
                    // dojeżdża dopiero po nim.
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Rusza się sama treść, nie tło. Gdyby przesuwało
                            // się całe tło, przy krawędzi odsłaniałby się pasek
                            // tego, co leży pod spodem - czyli znowu dwie
                            // zakładki naraz.
                            translationX =
                                if (moving) (1f - p) * direction * SHIFT * size.width else 0f
                            alpha = if (moving) p.coerceIn(0f, 1f) else 1f
                        }
                ) {
                    CompositionLocalProvider(LocalTabShown provides shown) { tab(i) }
                }
            }
        }
    }
}

/**
 * Czy zakładka, w której znalazł się ten ekran, jest tą oglądaną.
 *
 * Potrzebne, bo [TabHost] trzyma wszystkie zakładki złożone naraz. Bez tego
 * uchwyt wstecz z ukrytego Profilu łapałby cofnięcie na widocznym Treningu -
 * ten sam problem, co wcześniej z dotykiem i z czytnikiem ekranu, tylko
 * objawiający się cofaniem w niewidocznym miejscu.
 *
 * Domyślne `true` jest dla ekranów spoza zakładek, np. okien dialogowych.
 *
 * Celowo compositionLocalOf, nie staticCompositionLocalOf: ta wartość zmienia się
 * przy każdym przełączeniu zakładki, a wersja statyczna nie śledzi odczytów i
 * przebudowałaby wtedy całą zawartość zakładki. To jest dokładnie ten koszt,
 * którego [TabHost] ma unikać - tutaj przebudowuje się sam uchwyt wstecz.
 */
val LocalTabShown = compositionLocalOf { true }

/**
 * Systemowy przycisk wstecz dla ekranu wewnątrz zakładki.
 *
 * Każdy ekran z własnym „Wróć" powinien użyć tego zamiast gołego BackHandler:
 * cofanie ma robić dokładnie to samo, co ten przycisk, i tylko wtedy, gdy ekran
 * jest naprawdę na wierzchu. Uchwyty zarejestrowane później mają pierwszeństwo,
 * więc zagnieżdżony ekran wygrywa z tym, z którego został otwarty - i o to chodzi.
 */
@Composable
fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled && LocalTabShown.current, onBack = onBack)
}

/**
 * Licznik naciśnięć zakładki, która już jest otwarta.
 *
 * Ekran zakładki obserwuje swój licznik i wraca wtedy do swojego widoku
 * głównego - tak jak zachowuje się każda aplikacja z paskiem zakładek.
 * Bez tego otwarty szczegół treningu albo ekran postępów zostawał na
 * wierzchu i jedynym wyjściem był przycisk wstecz.
 *
 * Licznik, nie flaga: dwa naciśnięcia pod rząd mają zadziałać dwa razy,
 * a flaga wymagałaby zerowania z drugiej strony.
 *
 * Każda zakładka dostaje swój własny licznik. Jeden wspólny znaczyłby, że
 * naciśnięcie Planu zwija też szczegóły otwarte w Treningu - niewidocznie,
 * więc wychodziłoby to dopiero przy powrocie na tamtą zakładkę.
 */
val LocalTabReselect = compositionLocalOf { 0 }

/**
 * Powrót do widoku głównego zakładki po naciśnięciu jej ikony na pasku.
 *
 * Ekran zakładki z zagnieżdżonymi widokami wywołuje to raz i w [onReselect]
 * zeruje to, co trzyma go głębiej. Celowo nie dotyczy to rozpoczętej pracy:
 * tak jak naciśnięcie Treningu nie przerywa trwającego treningu, tak nie
 * przerywa tu wpisywania pomiaru.
 */
@Composable
fun TabReselectHandler(onReselect: () -> Unit) {
    val licznik = LocalTabReselect.current
    LaunchedEffect(licznik) { if (licznik > 0) onReselect() }
}

/**
 * Prośba o przejście na inną zakładkę, wysyłana z wnętrza ekranu.
 *
 * Potrzebna, bo propozycje progresji pokazują się w Planie, a natrafia się
 * na nie też w historii treningu - i stamtąd trzeba umieć tam skoczyć bez
 * szukania po zakładkach.
 */
val LocalGoToTab = compositionLocalOf<(Int) -> Unit> { {} }

/**
 * Dokąd ma otworzyć się Plan, gdy ktoś skoczył do niego z innego miejsca.
 *
 * Zwykła zmiana zakładki wrzuca na listę programów, a stamtąd do ćwiczenia
 * z propozycją są jeszcze trzy kliknięcia i zgadywanie, w którym planie ono
 * leży. Ten uchwyt niesie cel skoku przez granicę zakładek.
 *
 * Jednorazowy: [take] oddaje cel i od razu go zapomina, żeby kolejne wejście
 * w Plan nie wracało tam samo.
 */
object PlanJump {
    private var planId: Long? = null
    private var itemId: Long? = null

    fun to(plan: Long, item: Long? = null) {
        planId = plan
        itemId = item
    }

    fun take(): Pair<Long, Long?>? {
        val p = planId ?: return null
        val i = itemId
        planId = null
        itemId = null
        return p to i
    }
}

/**
 * Sesje, po których czeka propozycja progresji.
 *
 * Uchwyt kontekstowy, bo to wyłącznie podpowiedź wizualna na liście historii.
 * Przewlekanie go przez trzy poziomy wywołań zaciemniałoby ich sygnatury
 * bardziej, niż jest wart jeden znacznik.
 */
val LocalAdviceSessions = compositionLocalOf { emptySet<Long>() }
