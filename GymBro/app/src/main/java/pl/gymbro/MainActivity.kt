package pl.gymbro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import pl.gymbro.net.WatchLinkService
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import pl.gymbro.data.Db
import pl.gymbro.data.FoodPack
import pl.gymbro.data.ShopPack
import pl.gymbro.data.GymRepository
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.ui.LeaveGuard
import pl.gymbro.ui.LocalGoToTab
import pl.gymbro.ui.LocalLeaveGuard
import pl.gymbro.ui.LocalTabReselect
import pl.gymbro.ui.PLAN_TAB
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.health.AutoSync
import kotlinx.coroutines.delay
import pl.gymbro.ui.Busy
import pl.gymbro.ui.FoodScreen
import pl.gymbro.ui.PlanEditorScreen
import pl.gymbro.ui.ProfileScreen
import pl.gymbro.ui.Refresh
import pl.gymbro.ui.TabHost
import pl.gymbro.ui.WeightScreen
import pl.gymbro.ui.WorkoutScreen
import pl.gymbro.ui.theme.GymBroTheme

/**
 * GymBro - dziennik treningu siłowego.
 *
 * Aktywność jest jedna i trzyma tylko nawigację: pięć zakładek w [GymBroApp],
 * każda z własnym ekranem w `ui/`. Surowe ekrany z fazy rozpoznania, które
 * kiedyś były tu całą aplikacją, mieszkają w `ui/Diagnostics.kt` i są
 * dostępne wyłącznie w wariancie debug.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pilot na zegarku wstaje razem z aplikacją, nie dopiero z treningiem:
        // trening zaczyna się teraz na zegarku, więc pierwsze pytanie o
        // jednostki przychodzi, gdy żadnej sesji jeszcze nie ma.
        WatchLinkService.sync(this, sessionActive = false)
        enableEdgeToEdge()
        setContent {
            GymBroTheme {
                GymBroApp()
            }
        }
    }
}

/** Jedna zakładka nawigacji. */
private data class NavTab(
    val label: String,
    val icon: ImageVector
)

// Zakładki opisują to, co robisz, nie urządzenia, z których pochodzą dane.
// Zegarek miał tu kiedyś własne miejsce - teraz jego treningi i biegi trafiają
// do Postępu, a samo połączenie jest ustawieniem w Profilu.
// Kalorie (dziennik jedzenia) stoja na drugim miejscu, bo wchodzi sie tam
// kilka razy dziennie - czesciej niz w Plan i Profil, ktore zaglada sie raz
// na kilka dni. Przy pasku na dole blizej lewej krawedzi znaczy blizej kciuka.
// Kolejnosc jest zszyta z PLAN_TAB w ui/TabHost.kt.
private val navTabs = listOf(
    NavTab("Trening", Icons.Filled.FitnessCenter),
    NavTab("Kalorie", Icons.Filled.Restaurant),
    NavTab("Waga", Icons.Filled.MonitorWeight),
    NavTab("Plan", Icons.AutoMirrored.Filled.ListAlt),
    NavTab("Profil", Icons.Filled.Person)
)

@Composable
fun GymBroApp() {
    // rememberSaveable, nie remember: system potrafi ubić aplikację w tle
    // i odtworzyć ją po powrocie, a zwykły remember ginie razem z nią - wtedy
    // wracało się na pierwszą zakładkę. Obrót ekranu już aktywności nie
    // niszczy (configChanges w manifeście), więc tamten przypadek nie
    // potrzebuje tu niczego.
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    // Osobny licznik dla kazdej zakladki - patrz LocalTabReselect.
    val reselect = remember { mutableStateMapOf<Int, Int>() }
    var unseenAdvice by remember { mutableStateOf(0) }

    // Liczymy przy wejsciu i po kazdym opuszczeniu Planu: propozycje rodza
    // sie po treningu, a gasna po zajrzeniu w plan.
    val planRepo = remember { PlanRepository(context) }

    // Doliczenie propozycji dla ostatniego treningu, jesli ich nie ma.
    //
    // Normalnie licza sie przy nacisnieciu "Zakoncz", ale trening nie zawsze
    // tak sie konczy - bywa porzucony i zamkniety po osmiu godzinach. Wtedy
    // caly trening przepadal bez zadnego wniosku. Liczymy raz, dla ostatniego.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                ProgressionReview.catchUpLatest(
                    planRepo,
                    SessionRepository(context),
                    ProfileRepository(context),
                    GymRepository(context)
                )
            }
        }
        unseenAdvice = withContext(Dispatchers.IO) { planRepo.adviceCounts().second }
    }

    LaunchedEffect(tab) {
        unseenAdvice = withContext(Dispatchers.IO) { planRepo.adviceCounts().second }
    }
    val busy = Busy.isBusy

    // Zgody dla pilota: powiadomienia i - od Androida 14 - urządzenia
    // w pobliżu.
    //
    // Pytamy przy starcie, a nie przy pierwszym treningu, bo trening zaczyna
    // się na zegarku, z telefonem w szatni: gdy zgoda okaże się potrzebna,
    // nie ma już komu jej udzielić. Po odpowiedzi podnosimy pilota jeszcze
    // raz - bez urządzeń w pobliżu nie mógł wstać w onCreate.
    //
    // Odmowy nie pamiętamy sami: po drugiej system przestaje pokazywać okno
    // i odpowiada od razu, więc nagabywania przy każdym otwarciu nie będzie.
    // Dla urządzeń w pobliżu jest wtedy przycisk w Profilu.
    val pilotScope = rememberCoroutineScope()
    val pilotPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        WatchLinkService.sync(context, sessionActive = false)
        // Usługa wstaje asynchronicznie - Profil odczytany od razu pokazałby
        // jeszcze „Pilot nie działa".
        pilotScope.launch {
            delay(400)
            Refresh.bump()
        }
    }
    LaunchedEffect(Unit) {
        // Wyłączony pilot nie potrzebuje żadnej z tych zgód.
        if (!WatchLinkService.always(context)) return@LaunchedEffect
        val brakuje = WatchLinkService.permissionsToAsk(context)
        if (brakuje.isNotEmpty()) pilotPerms.launch(brakuje)
    }

    // Cicha synchronizacja z zegarkiem przy otwarciu aplikacji.
    //
    // Celowo poza Busy: to czynność w tle, o którą nikt nie prosił, więc nie
    // powinna pokazywać paska ładowania ani blokować nawigacji. Jeśli coś
    // przyniesie, ekrany dowiedzą się o tym przez Refresh.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        if (AutoSync.runIfDue(appContext)) Refresh.bump()
    }

    // Paczka polskich produktow z Open Food Facts - wczytywana raz, w tle.
    //
    // Celowo poza migracja: trzydziesci kilka tysiecy wierszy to kilka
    // sekund, a migracja wykonuje sie przy pierwszym dotknieciu bazy, czyli
    // przy starcie aplikacji. Zamrozenie ekranu startowego raz w zyciu
    // instalacji jest gorsze niz wczytanie w tle, w trakcie ktorego
    // wyszukiwanie przez chwile zwraca mniej wynikow.
    //
    // Tak samo poza Busy, jak synchronizacja z zegarkiem: to czynnosc,
    // o ktora nikt nie prosil, wiec nie ma prawa pokazywac paska ladowania
    // ani blokowac nawigacji.
    LaunchedEffect(Unit) {
        val dodane = withContext(Dispatchers.IO) {
            runCatching {
                val db = Db.get(appContext)
                var dodane = 0

                // Paczka z Open Food Facts idzie pierwsza, bo paczka sklepowa
                // ustepuje jej przy rownych nazwach - patrz ShopPack.import.
                // Przy odwrotnej kolejnosci bulka z lady wygrywalaby z
                // produktem majacym kod kreskowy, czyli z tym, ktory wychodzi
                // tez przy skanowaniu.
                val wczytanoOff = !FoodPack.isImported(db.readableDatabase)
                if (wczytanoOff) dodane += FoodPack.import(appContext, db.writableDatabase)

                // Po ponownym wczytaniu paczki OFF paczke sklepowa trzeba
                // zlozyc od nowa, nawet jesli jej wersja sie nie zmienila:
                // FoodPack kasuje i wstawia swoje wiersze ponownie, a wtedy
                // to one ustepowalyby sklepowym, zamiast odwrotnie.
                if (wczytanoOff || !ShopPack.isImported(db.readableDatabase)) {
                    dodane += ShopPack.import(appContext, db.writableDatabase)
                }
                dodane
            }.onFailure {
                // Blad musi zostawic slad. Pierwsza wersja konczyla sie
                // samym getOrDefault(0) i kiedy paczka nie wczytala sie
                // przez zla nazwe pliku, wygladalo to dokladnie tak samo
                // jak poprawne dzialanie - wyszukiwanie po prostu nic nie
                // znajdowalo, bez jednej linijki w logu.
                android.util.Log.w("GymBroPack", "Nie wczytano paczki produktow", it)
            }.getOrDefault(0)
        }
        if (dodane > 0) Refresh.bump()
    }
    // Wskaźnik pojawia się dopiero, gdy wczytywanie trwa dłużej niż chwilę.
    //
    // Pokazywanie go natychmiast byłoby gorsze niż jego brak: przy szybkim
    // wczytaniu mignąłby i zniknął, co odbiera się jako usterkę, a nie jako
    // informację. Przy tym progu nie zobaczysz go wcale, jeśli dane są gotowe
    // od razu - a zobaczysz, gdy faktycznie trzeba poczekać.
    var showLoader by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) {
            delay(180)
            showLoader = Busy.isBusy
        } else {
            showLoader = false
        }
    }

    // Jedna droga na zmiane zakladki: pasek, wstecz i skok z wnetrza
    // ekranu. Opuszczana zakladka zwija sie do swojego widoku glownego.
    //
    // Zanim to nastapi, opuszczana zakladka moze zapytac o niezapisane
    // zmiany - patrz LeaveGuard. Dlatego wszystkie trzy drogi musza isc
    // tedy: pasek zmienial kiedys zakladke wprost i pytanie z Profilu
    // padaloby przy wstecz, ale nie przy dotknieciu paska.
    val guards = remember { List(navTabs.size) { LeaveGuard() } }
    val idzDo: (Int) -> Unit = { cel ->
        if (cel != tab) {
            val przejdz = {
                reselect[tab] = (reselect[tab] ?: 0) + 1
                tab = cel
            }
            val pytanie = guards[tab].ask
            if (pytanie == null || !pytanie(przejdz)) przejdz()
        }
    }

    // Nawigacja na dole, nie na górze: w sali obsługujesz telefon jedną ręką,
    // a górna krawędź ekranu jest poza zasięgiem kciuka.
    Scaffold(
        bottomBar = {
            NavigationBar {
                navTabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            // Wyjscie z zakladki zwija ja do widoku glownego.
                            // Zakladka zachowuje stan, wiec bez tego wracalo
                            // sie w szczegoly sprzed kwadransa - a przelaczenie
                            // zakladki czyta sie jak zamkniecie tego, co bylo
                            // otwarte. Nacisniecie w juz otwarta zakladke robi
                            // to samo, tylko od razu widac efekt.
                            if (tab == i) reselect[i] = (reselect[i] ?: 0) + 1
                            else idzDo(i)
                        },
                        icon = {
                            // Plakietka tylko na Planie i tylko dopoki jej nie
                            // zobaczysz. Propozycje zostaja do decyzji dluzej,
                            // ale kropka ma przypominac raz, a nie swiecic
                            // tygodniami - wtedy przestaje cokolwiek znaczyc.
                            if (i == PLAN_TAB && unseenAdvice > 0) {
                                BadgedBox(badge = { Badge { Text("$unseenAdvice") } }) {
                                    Icon(t.icon, contentDescription = t.label)
                                }
                            } else {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                        // Zaznaczenie kolorem, bez pigułki pod ikoną.
                        //
                        // Kształtu tej pigułki nie da się podać - Material bierze
                        // go z tokena zakodowanego na sztywno jako koło, a sam
                        // NavigationBarItem nie przyjmuje kształtu. Jedyne, co tu
                        // można zrobić, to ją wygasić; różnicę niesie wtedy kolor
                        // ikony i podpisu, a pasek przestaje odstawać od reszty.
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {

            // Wstecz na zakładce innej niż pierwsza wraca na Trening, zamiast
            // zamykać aplikację. Rejestrujemy go przed TabHost, bo uchwyty
            // dodane później mają pierwszeństwo - dzięki temu ekran otwarty
            // wewnątrz zakładki przejmuje cofnięcie przed nami.
            //
            // Z Treningu wstecz wychodzi normalnie: musi istnieć wyjście,
            // którego nie trzeba szukać.

            // Wstecz wraca na Trening.
            BackHandler(enabled = tab != 0) { idzDo(0) }

            // Zakładki trzyma TabHost: każdy ekran składa się raz i dalej żyje,
            // więc przełączenie nie buduje już niczego od nowa.
            CompositionLocalProvider(LocalGoToTab provides idzDo) {
                TabHost(
                    selected = tab,
                    count = navTabs.size,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    CompositionLocalProvider(
                        LocalTabReselect provides (reselect[index] ?: 0),
                        LocalLeaveGuard provides guards[index]
                    ) {
                        when (index) {
                            0 -> WorkoutScreen()
                            1 -> FoodScreen()
                            2 -> WeightScreen()
                            3 -> PlanEditorScreen()
                            4 -> ProfileScreen()
                        }
                    }
                }
            }

            // Pasek ładowania jako nakładka, nie element układu - inaczej jego
            // pojawienie się spychałoby całą zawartość w dół, czyli dokładnie
            // ten skok, który usuwamy animacją.
            AnimatedVisibility(
                visible = showLoader,
                modifier = Modifier.align(Alignment.TopCenter)
            ) { LoadingBar() }
        }
    }
}

/**
 * Cienki pasek z przesuwającym się rozbłyskiem.
 *
 * Rysowany ręcznie zamiast przez LinearProgressIndicator, bo sygnatura tamtego
 * zmieniała się między wersjami Material3 i wymusza wybór wariantu.
 */
@Composable
private fun LoadingBar() {
    val transition = rememberInfiniteTransition(label = "ladowanie")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "faza"
    )

    val track = MaterialTheme.colorScheme.surfaceVariant
    val head = MaterialTheme.colorScheme.primary

    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        drawRect(track)
        val w = size.width
        val bandWidth = w * 0.35f
        // Rozbłysk wjeżdża z lewej i wyjeżdża prawą krawędzią.
        val start = -bandWidth + phase * (w + bandWidth)
        drawRect(
            color = head,
            topLeft = Offset(start.coerceAtLeast(0f), 0f),
            size = androidx.compose.ui.geometry.Size(
                width = (minOf(start + bandWidth, w) - start.coerceAtLeast(0f)).coerceAtLeast(0f),
                height = size.height
            )
        )
    }
}
