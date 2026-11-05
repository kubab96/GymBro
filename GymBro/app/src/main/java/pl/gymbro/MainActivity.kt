package pl.gymbro

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import pl.gymbro.net.WatchLinkService
import pl.gymbro.ui.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import pl.gymbro.data.Db
import pl.gymbro.data.FoodPack
import pl.gymbro.data.ShopPack
import pl.gymbro.data.GymRepository
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.ui.LocalGoToTab
import pl.gymbro.ui.LocalTabReselect
import pl.gymbro.ui.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.ble.ScaleScanner
import pl.gymbro.health.AutoSync
import pl.gymbro.health.HealthReader
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
 * GymBro - dziennik treningu siłowego i biegowego.
 *
 * Zakładki Waga i Zegarek pochodzą jeszcze z fazy rozpoznania i mają surowy,
 * diagnostyczny charakter. Zostają, bo są przydatne przy debugowaniu integracji,
 * ale docelowo zastąpią je normalne ekrany danych.
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
                SpikeApp()
            }
        }
    }
}

/**
 * Ktora zakladka jest Planem - stad plakietka z propozycjami.
 *
 * Numer, nie nazwa, bo pasek rysuje sie z listy po indeksie. Dopisanie
 * zakladki przed Planem musi przesunac te stala - inaczej plakietka
 * z propozycjami progresji ladowala na Wadze.
 */
private const val PLAN_TAB = 3

/** Jedna zakładka nawigacji. */
private data class NavTab(
    val label: String,
    val icon: ImageVector
)

// Zakładki opisują to, co robisz, nie urządzenia, z których pochodzą dane.
// Zegarek miał tu kiedyś własne miejsce - teraz jego treningi i biegi trafiają
// do Postępu, a samo połączenie jest ustawieniem w Profilu.
// Jedzenie stoi na drugim miejscu, bo wchodzi sie tam kilka razy dziennie -
// czesciej niz w Plan i Profil, ktore zaglada sie raz na kilka dni. Przy
// pasku na dole blizej lewej krawedzi znaczy blizej kciuka.
private val navTabs = listOf(
    NavTab("Trening", Icons.Filled.FitnessCenter),
    NavTab("Jedzenie", Icons.Filled.Restaurant),
    NavTab("Waga", Icons.Filled.MonitorWeight),
    NavTab("Plan", Icons.AutoMirrored.Filled.ListAlt),
    NavTab("Profil", Icons.Filled.Person)
)

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
fun SpikeApp() {
    // rememberSaveable, nie remember: obrót ekranu niszczy i odtwarza aktywność,
    // a zwykły remember ginie razem z nią. Bez tego każdy obrót wyrzucał
    // z powrotem na pierwszą zakładkę.
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
                            else {
                                reselect[tab] = (reselect[tab] ?: 0) + 1
                                tab = i
                            }
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
            // Jedna droga na zmiane zakladki: pasek, wstecz i skok z wnetrza
            // ekranu. Opuszczana zakladka zwija sie do swojego widoku glownego.
            val idzDo: (Int) -> Unit = { cel ->
                if (cel != tab) {
                    reselect[tab] = (reselect[tab] ?: 0) + 1
                    tab = cel
                }
            }

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
                        LocalTabReselect provides (reselect[index] ?: 0)
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

// ============================================================ WAGA / BLE ===

@Composable
internal fun ScaleScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scanner = remember { ScaleScanner(context) }
    val devices by scanner.devices.collectAsState()
    val log by scanner.log.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    val connected by scanner.connected.collectAsState()

    var hasPerms by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPerms = result.values.all { it } }

    LaunchedEffect(Unit) { permLauncher.launch(blePermissions()) }

    // Tanie wagi BLE obsługują naraz tylko jednego klienta. Skanowanie zostawione
    // w tle blokuje wagę dla aplikacji producenta - i właśnie tak się stało:
    // parowanie w OKOK nie przechodziło, dopóki ten ekran nie przestał nasłuchiwać.
    DisposableEffect(Unit) {
        onDispose {
            scanner.stopScan()
            scanner.disconnect()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // Wagi OKOK rozglaszaja pomiar w pakiecie reklamowym, wiec nie trzeba
        // sie z nimi laczyc. Wystarczy wlaczyc skanowanie i stanac na wadze.
        val stable by scanner.lastStable.collectAsState()
        if (stable != null) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("%.2f kg".format(stable!!.weightKg), fontSize = 34.sp)
                    stable!!.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stable!!.source,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            "Twoja waga to \"Yoda0\" - rodzina Qingniu/Yolanda (QN-Scale).\n\n" +
                "Krok 1: wlacz skanowanie.\n" +
                "Krok 2: DOPIERO TERAZ stan na wadze i stoj, az pokaze wynik.\n" +
                "Krok 3: jesli masa nie wyskoczy sama, dotknij \"Yoda0\" na liscie -\n" +
                "        ten wariant wymaga polaczenia GATT zamiast rozgloszenia.\n" +
                "Krok 4: powtorz 2-3 razy i skopiuj log.\n\n" +
                "Wagi tej rodziny nadaja tylko kilkanascie sekund od obudzenia,\n" +
                "wiec kolejnosc ma znaczenie: najpierw skan, potem wejscie na wage.",
            style = MaterialTheme.typography.bodySmall
        )

        val packets by scanner.packets.collectAsState()
        var locationOn by remember { mutableStateOf(scanner.isLocationOn()) }

        if (!locationOn) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Usługi lokalizacji są wyłączone. Android w tym stanie potrafi " +
                            "nie zwrócić ani jednego wyniku skanowania BLE i nie zgłosić błędu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    ) { Text("Włącz lokalizację") }
                }
            }
        }

        Text(
            "urządzenia: ${devices.size}    pakiety: $packets",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    locationOn = scanner.isLocationOn()
                    if (scanning) scanner.stopScan() else scanner.startScan()
                },
                enabled = hasPerms
            ) { Text(if (scanning) "Stop" else "Skanuj") }

            OutlinedButton(
                onClick = { scanner.disconnect() },
                enabled = connected != null
            ) { Text("Rozlacz") }

            OutlinedButton(onClick = {
                copyToClipboard(context, "GymBro BLE", scanner.logAsText())
            }) { Text("Kopiuj log") }

            OutlinedButton(onClick = { scanner.clearLog() }) { Text("Wyczysc") }
        }

        if (!hasPerms) {
            Text(
                "Brak uprawnien Bluetooth. Przyznaj je w ustawieniach aplikacji.",
                color = MaterialTheme.colorScheme.error
            )
        }

        // Kandydaci na wage - pokazujemy tylko te urzadzenia, ktore rozpoznalismy,
        // a ponizej wszystkie pozostale, gdyby waga rozglaszala sie nietypowo.
        val likely = devices.values.filter { it.family != null }.sortedByDescending { it.rssi }
        val rest = devices.values.filter { it.family == null }.sortedByDescending { it.rssi }

        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (likely.isNotEmpty()) {
                item { SectionLabel("Prawdopodobne wagi") }
                items(likely, key = { it.address }) { d -> DeviceRow(d.name, d.address, d.rssi, d.family) { scanner.connect(d.address) } }
            }
            if (rest.isNotEmpty()) {
                item { SectionLabel("Pozostale urzadzenia (${rest.size})") }
                items(rest.take(30), key = { it.address }) { d -> DeviceRow(d.name, d.address, d.rssi, null) { scanner.connect(d.address) } }
            }
        }

        SectionLabel("Log (${log.size} linii)")
        // weight() jest modyfikatorem ColumnScope, wiec przekazujemy go z tego miejsca.
        LogView(log, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun DeviceRow(
    name: String?,
    address: String,
    rssi: Int,
    family: String?,
    onClick: () -> Unit
) {
    // Card(onClick = ...) jest oznaczony jako eksperymentalny, wiec klikalnosc
    // dokladamy modyfikatorem - mniej ostrzezen przy kompilacji.
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name ?: "(bez nazwy)", style = MaterialTheme.typography.bodyLarge)
                Text("$rssi dBm", style = MaterialTheme.typography.bodySmall)
            }
            Text(address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            if (family != null) {
                Text(family, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LogView(lines: List<String>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1)
    }
    LazyColumn(modifier, state = state) {
        items(lines) { line ->
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

// ================================================== ZEGAREK / HEALTH CONNECT

@Composable
internal fun HealthDumpScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reader = remember { HealthReader(context) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(reader.availability()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dump by remember { mutableStateOf("Nacisnij \"Wczytaj dane\", zeby zobaczyc, co przyszlo z zegarka.") }
    var busy by remember { mutableStateOf(false) }

    val hcLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    LaunchedEffect(status) {
        if (status == HealthReader.Availability.OK) {
            granted = runCatching { reader.grantedPermissions() }.getOrDefault(emptySet())
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        when (status) {
            HealthReader.Availability.UNAVAILABLE -> {
                Text(
                    "Health Connect nie jest dostepny na tym urzadzeniu.\n" +
                        "Na Androidzie 13 i starszym trzeba zainstalowac aplikacje Health Connect ze Sklepu Play.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                        )
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Otworz w Sklepie Play") }
            }

            HealthReader.Availability.NEEDS_UPDATE -> {
                Text("Health Connect wymaga aktualizacji.", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                        )
                    }
                    status = reader.availability()
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Aktualizuj") }
            }

            HealthReader.Availability.OK -> {
                Text(
                    "Zanim wczytasz dane, w aplikacji Zepp wejdz w:\n" +
                        "Profil > Konta zewnetrzne > Health Connect i zezwol na zapis.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Przyznane uprawnienia: ${granted.size} / ${HealthReader.PERMISSIONS.size}",
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { hcLauncher.launch(HealthReader.PERMISSIONS) }) {
                        Text("Przyznaj dostep")
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                dump = withContext(Dispatchers.IO) {
                                    runCatching { reader.dumpLastDays(30) }
                                        .getOrElse { "BLAD: ${it.message}" }
                                }
                                busy = false
                            }
                        }
                    ) { Text(if (busy) "Czytam..." else "Wczytaj dane") }

                    OutlinedButton(onClick = {
                        copyToClipboard(context, "GymBro HC", dump)
                    }) { Text("Kopiuj") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(dump.lines()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

