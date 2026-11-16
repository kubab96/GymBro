package pl.gymbro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Cache
import pl.gymbro.data.Equipment
import pl.gymbro.data.Gear
import pl.gymbro.data.GearGroup
import pl.gymbro.data.GymRepository
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plate
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.TrainingGoal
import pl.gymbro.data.Phase
import pl.gymbro.data.Sex
import pl.gymbro.data.UserProfile
import pl.gymbro.net.WatchLinkService
import pl.gymbro.gym.Loading

/**
 * Profil: dane do wzorów oraz to, czym dysponujesz.
 *
 * Dwie zasady porządkujące ten ekran:
 *
 * **Domyślnie nic nie jest ograniczone.** Aplikacja zakłada pełną siłownię
 * i dowolne obciążenia, dopóki sam nie powiesz inaczej. Odwrotne założenie
 * byłoby arogancją - program nie ma prawa twierdzić, że czegoś nie masz.
 * Sprzęt i talerze pojawiają się dopiero po włączeniu „Dostosuj sprzęt oraz
 * obciążenia".
 *
 * **Pytamy tylko o to, co masz.** Sekcja obciążeń dopasowuje się do dodanego
 * sprzętu: bez sztangi nie ma pytania o gryf, bez sprzętu ładowanego talerzami
 * nie ma w ogóle o czym rozmawiać.
 *
 * **Jeden przycisk Zapisz na cały ekran**, w nagłówku, czynny dopiero po
 * zmianie. Wyjście z zakładki z niezapisanymi zmianami pyta, co z nimi
 * zrobić. Poza szkicem zostają tylko rzeczy, które nie są danymi profilu:
 * przełącznik pilota (włącza i gasi usługę od razu), zgody systemowe
 * i czyszczenie historii.
 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileRepository(context) }
    val gymRepo = remember { GymRepository(context) }

    // Dwie kopie tego samego: to, co leży w bazie, i szkic z ekranu.
    //
    // Każdy przełącznik zapisywał się tu kiedyś od razu, a pola tekstowe
    // dopiero po naciśnięciu „Zapisz dane" - więc nie było wiadomo, co już
    // jest zapisane, a zmiana wzrostu bez tamtego przycisku przepadała bez
    // słowa. Teraz wszystko trafia do szkicu i idzie do bazy jednym
    // przyciskiem, który świeci tylko wtedy, gdy szkic różni się od bazy.
    val cachedProfile = remember { Cache.peek<UserProfile>(Cache.PROFILE) }
    val cachedInventory = remember { Cache.peek<Inventory>(Cache.INVENTORY) }
    var profile by remember { mutableStateOf(cachedProfile ?: UserProfile()) }
    var inventory by remember { mutableStateOf(cachedInventory) }
    var loaded by remember { mutableStateOf(cachedProfile != null && cachedInventory != null) }

    // Szkic. Wzrost, rok i gryf jako tekst, bo tak się je wpisuje - w szkicu
    // profilu lądują dopiero przy zapisie, po sprawdzeniu.
    var draft by remember { mutableStateOf(profile) }
    var height by remember { mutableStateOf(profile.heightCm?.let { Loading.fmt(it) } ?: "") }
    var birthYear by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var barText by remember { mutableStateOf(cachedInventory?.let { Loading.fmt(it.barKg) } ?: "") }
    var plates by remember { mutableStateOf(cachedInventory?.plates ?: emptyList()) }
    // Nowe talerze dostają numery ujemne, żeby dało się je poprawić i usunąć
    // jeszcze przed zapisem - bez numeru dwa nowe talerze byłyby nie do
    // odróżnienia.
    var nextTempId by remember { mutableStateOf(-1L) }
    var addingGear by remember { mutableStateOf(false) }

    var showWatch by rememberSaveable { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf<Long?>(null) }
    val health = remember { HealthRepository(context) }

    var editingPlate by remember { mutableStateOf<Plate?>(null) }
    var addingPlate by remember { mutableStateOf(false) }

    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var wipeNote by remember { mutableStateOf<String?>(null) }
    val sessions = remember { SessionRepository(context) }
    val linkToken = remember { WatchLinkService.tokenOf(context) }
    var linkAlways by remember { mutableStateOf(WatchLinkService.always(context)) }
    var linkListening by remember { mutableStateOf(WatchLinkService.listening) }
    // Czytane przy każdym wejściu w zakładkę, nie raz: zgodę daje się
    // w systemowym oknie, czyli poza aplikacją, i po powrocie stan ma
    // się zgadzać bez zamykania całości.
    var batteryFree by remember { mutableStateOf(WatchLinkService.batteryUnrestricted(context)) }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    // Od Androida 14 pilot nie wstanie bez zgody na urządzenia w pobliżu -
    // patrz WatchLinkService.canStart. Czytane jak batteryFree: zgodę daje
    // się w systemowym oknie, więc stan odświeżamy po powrocie.
    var linkAllowed by remember { mutableStateOf(WatchLinkService.canStart(context)) }
    val nearbyLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        linkAllowed = WatchLinkService.canStart(context)
        WatchLinkService.sync(context, sessionActive = false)
        mainHandler.postDelayed({ linkListening = WatchLinkService.listening }, 400)
        // Po dwóch odmowach system nie pokazuje już okna i odpowiada od
        // razu - przycisk wyglądałby na martwy. Wtedy jedyna droga wiedzie
        // przez ustawienia aplikacji, więc tam prowadzimy. Po pierwszej
        // odmowie nie: ktoś dopiero co powiedział „nie" i wyrzucenie go
        // do ustawień byłoby natręctwem.
        val trwale = !linkAllowed && (context as? android.app.Activity)
            ?.shouldShowRequestPermissionRationale(WatchLinkService.NEARBY.first()) == false
        if (trwale) {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    /** Stan z bazy staje się i stanem zapisanym, i szkicem. */
    fun adopt(p: UserProfile, inv: Inventory) {
        profile = Cache.put(Cache.PROFILE, p)
        inventory = Cache.put(Cache.INVENTORY, inv)
        draft = p
        // Loading.fmt, nie "%.0f": zaokrąglenie 178,5 do 179 w polu znaczyło,
        // że sam odczyt różnił się od zapisu i ekran od wejścia wyglądałby
        // na zmieniony.
        height = p.heightCm?.let { Loading.fmt(it) } ?: ""
        birthYear = p.birthYear?.toString() ?: ""
        barText = Loading.fmt(inv.barKg)
        plates = inv.plates
    }

    // ---- Szkic wobec bazy ----
    val heightVal = height.trim().replace(',', '.').toDoubleOrNull()
    val heightOk = height.isBlank() || (heightVal != null && heightVal in 80.0..250.0)
    val birthVal = birthYear.trim().toIntOrNull()
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val birthOk = birthYear.isBlank() || (birthVal != null && birthVal in 1900..thisYear)
    val barVal = barText.trim().replace(',', '.').toDoubleOrNull()
    val barOk = barVal != null && barVal > 0.0

    val target = draft.copy(
        heightCm = if (height.isBlank()) null else heightVal,
        birthYear = if (birthYear.isBlank()) null else birthVal
    )
    val dirty = loaded && (
        target != profile ||
            barVal != inventory?.barKg ||
            plates != inventory?.plates.orEmpty()
        )
    // Błędne pole też jest zmianą - ale taką, której nie da się zapisać.
    val canSave = dirty && heightOk && birthOk && barOk
    val dirtyNow by rememberUpdatedState(dirty)

    LaunchedEffect(Unit) {
        Busy.track {
            val p = withContext(Dispatchers.IO) { profileRepo.get() }
            val inv = withContext(Dispatchers.IO) { gymRepo.inventory() }
            adopt(p, inv)
            lastSync = withContext(Dispatchers.IO) { health.lastSyncedAt() }
            loaded = true
        }
    }

    // Liczniki starzeją się same: zakładka żyje w tle i nie składa się od
    // nowa po treningu. Profil też bywa zapisywany poza tym ekranem (wzrost
    // przy obwodach), więc czytamy go na nowo - ale tylko wtedy, gdy nie ma
    // tu rozgrzebanych zmian, bo odczyt zamazałby szkic.
    LaunchedEffect(Refresh.version) {
        counts = withContext(Dispatchers.IO) { sessions.historyCounts() }
        batteryFree = WatchLinkService.batteryUnrestricted(context)
        linkListening = WatchLinkService.listening
        linkAllowed = WatchLinkService.canStart(context)
        if (loaded && !dirtyNow) {
            val p = withContext(Dispatchers.IO) { profileRepo.get() }
            val inv = withContext(Dispatchers.IO) { gymRepo.inventory() }
            if (!dirtyNow) adopt(p, inv)
        }
    }

    /**
     * Zapis całego szkicu.
     *
     * Profil czytany z bazy tuż przed zapisem i nadpisywany tylko w polach
     * tego ekranu: tempo diety i dzień odcięcia wydatku należą do zakładki
     * Kalorie, a zapisanie ich z kopii sprzed kwadransa cofałoby tamte zmiany.
     */
    fun save(then: () -> Unit = {}) {
        val p = target
        val bar = barVal ?: return
        val set = plates
        scope.launch {
            val fresh = withContext(Dispatchers.IO) {
                profileRepo.save(
                    profileRepo.get().copy(
                        heightCm = p.heightCm,
                        sex = p.sex,
                        birthYear = p.birthYear,
                        trainingGoal = p.trainingGoal,
                        phase = p.phase,
                        customGear = p.customGear,
                        ownedGear = p.ownedGear
                    )
                )
                gymRepo.saveGear(bar, set)
                profileRepo.get() to gymRepo.inventory()
            }
            adopt(fresh.first, fresh.second)
            // Cel w Kaloriach liczy się ze wzrostu i fazy, załadunek
            // w Treningu z talerzy - a tamte zakładki żyją w tle.
            Refresh.bump()
            then()
        }
    }

    // Pytanie przy wyjściu z zakładki - patrz LeaveGuard. `leaving` trzyma
    // przejście, które czeka na odpowiedź.
    val guard = LocalLeaveGuard.current
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(guard) {
        guard.ask = { proceed ->
            if (dirtyNow) {
                leaving = proceed
                true
            } else false
        }
        onDispose { guard.ask = null }
    }

    // Naciskiem w otwarta zakladke wracamy do glownego widoku profilu.
    // Przed sprawdzeniem `loaded`, bo uchwyt musi zyc w kazdym skladaniu:
    // wypadniecie z drzewa i powrot odpalilyby go po raz drugi.
    TabReselectHandler { showWatch = false }

    // Okno pytania stoi przed wczesnymi wyjściami z funkcji. Niżej nie
    // zostałoby złożone przy otwartej synchronizacji z zegarkiem - a wtedy
    // wyjście z zakładki nie robiłoby nic, bez żadnego okna.
    leaving?.let { proceed ->
        AlertDialog(
            // Dotknięcie obok znaczy „jednak zostaję".
            onDismissRequest = { leaving = null },
            title = { Text("Niezapisane zmiany") },
            text = {
                Text(
                    if (canSave) "Zmieniłeś dane w Profilu i ich nie zapisałeś."
                    else "Zmieniłeś dane w Profilu, ale część pól ma wartości, " +
                        "których nie da się zapisać. Zostań i je popraw albo " +
                        "wyjdź bez zapisywania."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canSave,
                    onClick = {
                        leaving = null
                        save(then = proceed)
                    }
                ) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = {
                    leaving = null
                    // Szkic wraca do stanu z bazy. Zostawiony w polach
                    // wyglądałby po powrocie jak zapisany.
                    inventory?.let { adopt(profile, it) }
                    proceed()
                }) { Text("Nie zapisuj") }
            }
        )
    }

    if (!loaded) {
        Text("Wczytuję…", Modifier.padding(16.dp))
        return
    }

    // Zegarek jest tu ustawieniem, nie zakładką: podłączasz go raz i wracasz,
    // gdy coś nie przyszło. To, co przysyła, widać w Postępie.
    if (showWatch) {
        HealthScreen(onBack = { showWatch = false })
        return
    }

    // Wybór sprzętu na całym ekranie - pięćdziesiąt pozycji w okienku czytało
    // się przez szparę. Wynik idzie do szkicu, jak każda zmiana w Profilu.
    if (addingGear) {
        GearPickerScreen(
            selected = draft.ownedGear,
            onCancel = { addingGear = false },
            onDone = { picked ->
                draft = draft.copy(ownedGear = picked)
                addingGear = false
            }
        )
        return
    }

    // Sekcje liczą się ze szkicu, nie z bazy: po dodaniu sztangi pole gryfu
    // ma się pojawić od razu, a nie dopiero po zapisie.
    val hasBarbell = draft.canDo(Equipment.BARBELL)
    val hasDumbbells = draft.canDo(Equipment.DUMBBELL_PAIR) ||
        draft.canDo(Equipment.DUMBBELL_SINGLE)
    val usesPlates = hasBarbell || hasDumbbells

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        // Nagłówek poza przewijaniem. Przycisk Zapisz dotyczy całego ekranu,
        // więc musi być widać go z każdego miejsca listy - przewinięty razem
        // z tytułem znikałby dokładnie wtedy, gdy zmieniasz coś na dole.
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Profil", style = MaterialTheme.typography.headlineLarge)
                when {
                    dirty && !canSave -> Text(
                        "Popraw zaznaczone pola",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    dirty -> Text(
                        "Niezapisane zmiany",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    else -> profile.age?.let {
                        Text(
                            "$it lat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(onClick = { save() }, enabled = canSave) { Text("Zapisz") }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

        // ---- Dane ----
        Spacer(Modifier.height(6.dp))
        SectionCard("Dane") {
            Text(
                "Wzrost i płeć wchodzą do oszacowania tkanki tłuszczowej z obwodów.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Wzrost (cm)") },
                    isError = !heightOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it },
                    label = { Text("Rok urodzenia") },
                    isError = !birthOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s ->
                    val label = if (s == Sex.MALE) "mężczyzna" else "kobieta"
                    if (s == draft.sex) {
                        Button(
                            onClick = { draft = draft.copy(sex = s) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { draft = draft.copy(sex = s) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                }
            }
        }

        // ---- Cel ----
        Spacer(Modifier.height(12.dp))
        SectionCard("Cel") {
            Text(
                "Steruje tym, co aplikacja proponuje po treningu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            Text("Co ma rosnąć", style = MaterialTheme.typography.labelMedium)
            Text(
                "Ciężar buduje siłę, objętość masę. Przy sile prawie nie " +
                    "dokładamy serii, przy masie powtórzenia są równie dobre " +
                    "jak ciężar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrainingGoal.entries.forEach { g ->
                    val label = when (g) {
                        TrainingGoal.STRENGTH -> "siła"
                        TrainingGoal.HYPERTROPHY -> "masa"
                        TrainingGoal.MIXED -> "mieszany"
                    }
                    if (g == draft.trainingGoal) {
                        Button(
                            onClick = { draft = draft.copy(trainingGoal = g) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { draft = draft.copy(trainingGoal = g) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Faza", style = MaterialTheme.typography.labelMedium)
            Text(
                "Nie zmienia tego, co podnosić - zmienia to, jak czytać brak " +
                    "postępu. Na redukcji utrzymanie ciężaru jest sukcesem, nie " +
                    "zastojem. Samo ustawienie nie wpływa na masę ciała; o tym " +
                    "decyduje jedzenie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Phase.entries.forEach { p ->
                    val label = when (p) {
                        Phase.CUT -> "redukcja"
                        Phase.MAINTAIN -> "utrzymanie"
                        Phase.BULK -> "budowa"
                    }
                    if (p == draft.phase) {
                        Button(
                            onClick = { draft = draft.copy(phase = p) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { draft = draft.copy(phase = p) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    }
                }
            }
        }

        // ---- Sprzęt i obciążenia ----
        //
        // Jedna karta i jeden przełącznik. Były dwie karty z dwoma
        // przełącznikami o odwróconym znaczeniu („mam wszystko", „dowolny
        // ciężar"), które pytały o to samo - czy trenujesz tam, gdzie jest
        // wszystko - i przy pełnej siłowni zajmowały pół ekranu tylko po to,
        // żeby potwierdzić ustawienie domyślne.
        Spacer(Modifier.height(12.dp))
        SectionCard("Sprzęt i obciążenia") {
            ToggleRow(
                title = "Dostosuj sprzęt oraz obciążenia",
                subtitle = "Wyłączone: zakładam dostęp do wszystkiego - widzisz każdy " +
                    "plan i każde ćwiczenie, ciężar jest dowolny. Włączone: podajesz, " +
                    "co masz, a plany, jednostki i ćwiczenia dobieram do tego. " +
                    "Wszystkie da się wtedy i tak pokazać jednym przełącznikiem.",
                checked = draft.customGear,
                onChange = { draft = draft.copy(customGear = it) }
            )

            AnimatedVisibility(visible = draft.customGear) {
                Column {
                    // ---- Sprzęt ----
                    Spacer(Modifier.height(14.dp))
                    Text("Sprzęt", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (draft.ownedGear.isEmpty()) {
                        Text(
                            "Nic nie dodano - przyjmuję, że masz wszystko.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Podsumowanie po działach, nie wiersz na pozycję:
                        // przy pełnej domowej siłowni to kilkanaście wierszy
                        // z „Usuń", w których ginęła reszta Profilu.
                        // Zmienia się to na ekranie wyboru.
                        GearGroup.entries.forEach { group ->
                            val wGrupie = draft.ownedGear.filter { it.group == group }
                                .sortedBy { it.ordinal }
                            if (wGrupie.isEmpty()) return@forEach
                            Text(
                                group.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                wGrupie.joinToString(", ") { it.label },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { addingGear = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (draft.ownedGear.isEmpty()) "+ Wybierz sprzęt" else "Zmień sprzęt") }

                    // ---- Obciążenia ----
                    Spacer(Modifier.height(18.dp))
                    Text("Obciążenia", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (!usesPlates) {
                        // Bez sztangi i hantli nie ma czego składać z talerzy. Pytanie
                        // o gryf przy samym wyciągu było dokładnie tym, co nie miało sensu.
                        Text(
                            "Twój sprzęt nie wymaga składania ciężaru z talerzy - " +
                                "na wyciągu i maszynach ustawiasz obciążenie bezpośrednio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Gryf tylko wtedy, gdy masz sztangę.
                        if (hasBarbell) {
                            OutlinedTextField(
                                value = barText,
                                onValueChange = { barText = it },
                                label = { Text("Masa gryfu (kg)") },
                                isError = !barOk,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        Text("Talerze", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Podaj łączną liczbę sztuk, nie na stronę.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        if (plates.isEmpty()) {
                            // Pusta lista nie znaczy „mam sam gryf" - patrz
                            // GymRepository.inventory.
                            Text(
                                "Bez talerzy na liście ciężar jest dowolny. Dodaj je, " +
                                    "jeśli aplikacja ma proponować tylko to, co da się złożyć.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            plates.forEach { p ->
                                PlateRow(
                                    plate = p,
                                    hasBarbell = hasBarbell,
                                    hasDumbbells = hasDumbbells,
                                    onEdit = { editingPlate = p },
                                    onDelete = { plates = plates.filterNot { it.id == p.id } }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { addingPlate = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ Dodaj talerz") }
                    }
                }
            }
        }

        // Odstępy jak między kartami wyżej - te trzy stykały się krawędziami,
        // bo dopisywano je później i każda zapomniała o swoim.
        Spacer(Modifier.height(12.dp))
        SectionCard("Zegarek") {
            Text(
                "Amazfit przez Health Connect. Treningi i biegi lądują w Postępie - " +
                    "tutaj zostaje samo połączenie, bo to ustawienie, a nie treść.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                lastSync?.let { "Ostatnio pobrano: ${syncDate(it)}" }
                    ?: "Jeszcze nic nie pobrano.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showWatch = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Synchronizacja z zegarkiem") }
        }

        // Czyszczenie historii jest ustawieniem, nie czynnością treningową -
        // dlatego siedzi w Profilu, a nie pod ręką w zakładce Trening.

        // Pilot na zegarku: tutaj widać token, bo mini-aplikacja Zepp OS musi
        // go dostać w swoich ustawieniach. Inaczej nie da się go nigdzie odczytać.
        Spacer(Modifier.height(12.dp))
        SectionCard("Pilot na zegarku") {
            Text(
                "Zegarek zapisuje serie przez adres lokalny telefonu. Nic nie " +
                    "wychodzi do internetu, więc działa też bez zasięgu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // Stan wprost, bo inaczej jedyną oznaką martwego pilota jest
            // "brak połączenia" na zegarku, już na siłowni.
            Text("Stan", style = MaterialTheme.typography.labelMedium)
            Text(
                when {
                    linkListening -> "Pilot działa - zegarek może zapisywać serie"
                    // Powód wprost, bo rada „otwórz jeszcze raz" nic tu nie
                    // da: bez zgody pilot nie wstanie za żadnym razem.
                    !linkAllowed -> "Pilot nie działa - system wymaga zgody na " +
                        "urządzenia w pobliżu, zanim pozwoli mu pracować w tle"
                    !linkAlways -> "Pilot jest wyłączony - włączy się na czas treningu"
                    else -> "Pilot nie działa - otwórz tę aplikację jeszcze raz"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (linkListening || (linkAllowed && !linkAlways)) {
                    MaterialTheme.colorScheme.onSurface
                } else MaterialTheme.colorScheme.error
            )
            if (!linkListening && !linkAllowed) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { nearbyLauncher.launch(WatchLinkService.NEARBY) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Zezwól na urządzenia w pobliżu") }
            }
            // Drugi stan, osobny od pierwszego i ważniejszy, niż wygląda:
            // pilot może działać teraz i zginąć za kwadrans. Dopóki system
            // ma prawo go ubić, "Pilot działa" mówi prawdę tylko o tej chwili.
            Spacer(Modifier.height(10.dp))
            Text("Praca w tle", style = MaterialTheme.typography.labelMedium)
            if (batteryFree) {
                Text(
                    "Bez ograniczeń - pilot przeżyje wygaszony ekran",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "System może ubić pilota, gdy telefon leży w szatni. " +
                        "Serie nie przepadną - czekają w kolejce zegarka - " +
                        "ale na telefon dojadą dopiero wtedy, gdy pilot " +
                        "znów wstanie, zwykle po otwarciu tej aplikacji.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { WatchLinkService.askBattery(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pozwól pracować w tle") }
            }

            // Adres tylko w wariancie debug. Jest stały i zegarek zna go bez
            // pytania, więc na co dzień to szczegół budowy, nie ustawienie -
            // przydaje się wyłącznie przy sprawdzaniu pilota z komputera.
            if (DIAGNOSTICS) {
                Spacer(Modifier.height(8.dp))
                Text("Adres", style = MaterialTheme.typography.labelMedium)
                Text(
                    "http://127.0.0.1:${WatchLinkService.PORT}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))
            // „Kod parowania", nie „token": to jedyna rzecz z tej karty, którą
            // przepisuje się do aplikacji na zegarku, i nazwa ma mówić po co.
            Text("Kod parowania", style = MaterialTheme.typography.labelMedium)
            Text(
                linkToken,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = "Działa też poza treningiem",
                // Bez tego nie da się zacząć treningu z zegarka: zanim sesja
                // istnieje, nie ma komu odebrać żądania.
                subtitle = "Pozwala zacząć i zakończyć trening z zegarka",
                checked = linkAlways,
                onChange = {
                    linkAlways = it
                    WatchLinkService.setAlways(context, it)
                    // Usługa wstaje i gaśnie asynchronicznie, więc stan
                    // odczytujemy po chwili - inaczej pokazalibyśmy poprzedni.
                    mainHandler.postDelayed(
                        { linkListening = WatchLinkService.listening },
                        400
                    )
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard("Historia") {
            val c = counts
            Text(
                if (c == null) "Liczę…"
                else if (c.first == 0) "Historia jest pusta."
                else "W historii: ${plural(c.first, "trening", "treningi", "treningów")}, " +
                    plural(c.second, "seria", "serie", "serii") + ".",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Czyszczenie usuwa treningi i serie. Plany, ćwiczenia, masa ciała, " +
                    "obwody i profil zostają. Treningi z zegarka wrócą przy " +
                    "kolejnej synchronizacji - to jego dane, nie nasze.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            wipeNote?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmWipe = true },
                enabled = (counts?.first ?: 0) > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Wyczyść historię treningów") }
        }

        Spacer(Modifier.height(20.dp))

        // Uznanie autorstwa danych o produktach.
        //
        // Nie uprzejmość, tylko warunek licencji: Open Food Facts jest na
        // Open Database License, która wymaga wskazania źródła wszędzie tam,
        // gdzie korzysta się z bazy. Skanowanie kodów kreskowych sięga
        // właśnie tam, więc ta linijka musi gdzieś być - a Profil jest
        // jedynym miejscem, gdzie nie przeszkadza przy codziennym użyciu.
        Text(
            "Dane o produktach pochodzą z Open Food Facts i są udostępniane " +
                "na licencji Open Database License (ODbL). Pieczywo, nabiał, " +
                "warzywa i wędliny bez kodu kreskowego pochodzą z tabel " +
                "opublikowanych przez sklepy - Biedronkę i Frisco.pl. " +
                "Produkty podstawowe i wpisane ręcznie są własne.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        }
    }

    val toEdit = editingPlate
    if (toEdit != null || addingPlate) {
        PlateDialog(
            plate = toEdit,
            // Pytanie o pasowanie na gryf ma sens tylko wtedy, gdy masz sztangę.
            askBarbellFit = hasBarbell,
            onDismiss = { editingPlate = null; addingPlate = false },
            onSave = { plate ->
                // Do szkicu, nie do bazy - talerze idą razem z resztą profilu
                // pod przyciskiem Zapisz.
                plates = if (plate.id == 0L) {
                    val nowy = plate.copy(id = nextTempId)
                    nextTempId -= 1
                    plates + nowy
                } else {
                    plates.map { if (it.id == plate.id) plate else it }
                }.sortedByDescending { it.kg }
                editingPlate = null
                addingPlate = false
            }
        )
    }


    if (confirmWipe) {
        val c = counts
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wyczyścić historię?") },
            text = {
                Text(
                    "Zniknie " +
                        plural(c?.first ?: 0, "trening", "treningi", "treningów") +
                        " i " +
                        plural(c?.second ?: 0, "seria", "serie", "serii") +
                        ". Tego nie da się cofnąć - kopii zapasowej nie ma."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    scope.launch {
                        val removed = withContext(Dispatchers.IO) { sessions.wipeHistory() }
                        counts = withContext(Dispatchers.IO) { sessions.historyCounts() }
                        wipeNote = "Usunięto " +
                            plural(removed, "trening", "treningi", "treningów") + "."
                        // Zakładki żyją w tle i same nie zauważą, że historia
                        // zniknęła - bez tego Trening nadal pokazywałby stare wpisy.
                        Refresh.bump()
                    }
                }) { Text("Wyczyść") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Anuluj") }
            }
        )
    }
}

/** Data ostatniego pobrania danych z zegarka, w formie czytelnej na ekranie. */
private fun syncDate(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

// ---- Elementy wspólne ----------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PlateRow(
    plate: Plate,
    hasBarbell: Boolean,
    hasDumbbells: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${Loading.fmt(plate.kg)} kg  ×${plate.count}")
                Text(
                    usableFor(plate, hasBarbell, hasDumbbells),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                shortageHint(plate, hasDumbbells)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            TextButton(onClick = onEdit) { Text("Zmień") }
            TextButton(onClick = onDelete) { Text("Usuń") }
        }
    }
}

/**
 * Do czego ten talerz realnie się nadaje - wyłącznie w kontekście sprzętu,
 * który masz. Wymienianie sztangi komuś, kto jej nie ma, byłoby szumem.
 *
 * Symetryczny załadunek zużywa talerze parami, a przy DWÓCH hantlach naraz -
 * czwórkami, bo jeden krążek wchodzi na każdą stronę każdego uchwytu.
 */
private fun usableFor(p: Plate, hasBarbell: Boolean, hasDumbbells: Boolean): String {
    val uses = buildList {
        if (hasBarbell && p.fitsBarbell && p.count >= 2) add("sztanga")
        if (hasDumbbells && p.count >= 2) add("jeden hantel")
        if (hasDumbbells && p.count >= 4) add("para hantli")
    }
    return if (uses.isEmpty()) "za mało sztuk, żeby użyć symetrycznie"
    else "działa przy: " + uses.joinToString(", ")
}

/** Ile sztuk brakuje do kolejnego zastosowania. */
private fun shortageHint(p: Plate, hasDumbbells: Boolean): String? = when {
    p.count < 2 -> "dokup ${2 - p.count} szt., żeby dało się załadować symetrycznie"
    hasDumbbells && p.count < 4 -> "dokup ${4 - p.count} szt., żeby działał też przy parze hantli"
    else -> null
}

@Composable
private fun PlateDialog(
    plate: Plate?,
    askBarbellFit: Boolean,
    onDismiss: () -> Unit,
    onSave: (Plate) -> Unit
) {
    var kgText by remember { mutableStateOf(plate?.let { Loading.fmt(it.kg) } ?: "") }
    var countText by remember { mutableStateOf(plate?.count?.toString() ?: "2") }
    var fitsBarbell by remember { mutableStateOf(plate?.fitsBarbell ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plate == null) "Nowy talerz" else "Talerz") },
        text = {
            Column {
                OutlinedTextField(
                    value = kgText,
                    onValueChange = { kgText = it },
                    label = { Text("Masa jednego talerza (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it },
                    label = { Text("Ile sztuk masz łącznie") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (askBarbellFit) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = fitsBarbell, onCheckedChange = { fitsBarbell = it })
                        Column {
                            Text("Wchodzi na sztangę")
                            Text(
                                "Odznacz, jeśli otwór nie pasuje na gryf",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = kgText.replace(',', '.').toDoubleOrNull()
                val count = countText.toIntOrNull()
                if (kg != null && kg > 0 && count != null && count > 0) {
                    onSave(
                        Plate(
                            id = plate?.id ?: 0,
                            kg = kg,
                            count = count,
                            fitsBarbell = if (askBarbellFit) fitsBarbell else true
                        )
                    )
                }
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

internal fun equipmentName(e: Equipment): String = when (e) {
    Equipment.BARBELL -> "Sztanga"
    Equipment.DUMBBELL_PAIR -> "Hantle (para)"
    Equipment.DUMBBELL_SINGLE -> "Hantel (jeden)"
    Equipment.CABLE -> "Wyciąg"
    Equipment.MACHINE -> "Maszyny"
    Equipment.BODYWEIGHT -> "Masa ciała"
    Equipment.KETTLEBELL -> "Kettlebell"
    Equipment.BAND -> "Gumy"
}
