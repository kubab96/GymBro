package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Cache
import pl.gymbro.data.Equipment
import pl.gymbro.data.GearGroup
import pl.gymbro.data.GymRepository
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plate
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.TrainingGoal
import pl.gymbro.data.Sex
import pl.gymbro.data.UserProfile
import pl.gymbro.net.WatchLinkService
import pl.gymbro.gym.Loading

/**
 * Ekran Profilu, na którym aktualnie stoimy.
 *
 * Główny widok to dane i dwie krótkie listy - Trening i Ustawienia - a każda
 * pozycja prowadzi na własny ekran. Wcześniej wszystko leżało na jednej
 * długiej stronie: zgody, kod parowania i talerze między wzrostem a celem,
 * a połowa tego dotyczy rzeczy ustawianych raz na zawsze.
 */
private enum class ProfileSub { NONE, GOAL, GEAR, GEAR_PICK, WATCH, HEALTH, DATA }

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
 * i czyszczenie historii. Faza (schudnąć, utrzymać, przybrać) mieszka
 * w zakładce Kalorie.
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

    var sub by rememberSaveable { mutableStateOf(ProfileSub.NONE) }
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
    // przy obwodach, faza w Kaloriach), więc czytamy go na nowo - ale tylko
    // wtedy, gdy nie ma tu rozgrzebanych zmian, bo odczyt zamazałby szkic.
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
     * tego ekranu: faza, tempo diety i dzień odcięcia wydatku należą do
     * zakładki Kalorie, a zapisanie ich z kopii sprzed kwadransa cofałoby
     * tamte zmiany.
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
                        customGear = p.customGear,
                        ownedGear = p.ownedGear
                    )
                )
                gymRepo.saveGear(bar, set)
                profileRepo.get() to gymRepo.inventory()
            }
            adopt(fresh.first, fresh.second)
            // Cel w Kaloriach liczy się ze wzrostu, załadunek w Treningu
            // z talerzy - a tamte zakładki żyją w tle.
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
    TabReselectHandler { sub = ProfileSub.NONE }

    // Okno pytania stoi przed wczesnymi wyjściami z funkcji. Niżej nie
    // zostałoby złożone na żadnym z podekranów - a wtedy wyjście z zakładki
    // nie robiłoby nic, bez żadnego okna.
    leaving?.let { proceed ->
        UnsavedChangesDialog(
            canSave = canSave,
            onStay = { leaving = null },
            onSave = {
                leaving = null
                save(then = proceed)
            },
            onDiscard = {
                leaving = null
                // Szkic wraca do stanu z bazy. Zostawiony w polach
                // wyglądałby po powrocie jak zapisany.
                inventory?.let { adopt(profile, it) }
                proceed()
            }
        )
    }

    if (!loaded) {
        Text("Wczytuję…", Modifier.padding(16.dp))
        return
    }

    // Sekcje liczą się ze szkicu, nie z bazy: po dodaniu sztangi pole gryfu
    // ma się pojawić od razu, a nie dopiero po zapisie.
    val hasBarbell = draft.canDo(Equipment.BARBELL)
    val hasDumbbells = draft.canDo(Equipment.DUMBBELL_PAIR) ||
        draft.canDo(Equipment.DUMBBELL_SINGLE)
    val usesPlates = hasBarbell || hasDumbbells

    // Okna talerza i czyszczenia też przed wczesnymi wyjściami - otwiera się
    // je z podekranów, które kończą funkcję własnym `return`.
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

    // Stan pilota wprost, bo inaczej jedyną oznaką martwego pilota jest
    // "brak połączenia" na zegarku, już na siłowni.
    val pilotOk = linkListening || (linkAllowed && !linkAlways)
    val pilotStatus = when {
        linkListening -> "Działa - zegarek może zapisywać serie"
        // Powód wprost, bo rada „otwórz jeszcze raz" nic tu nie da: bez
        // zgody pilot nie wstanie za żadnym razem.
        !linkAllowed -> "Nie działa - system wymaga zgody na urządzenia w pobliżu"
        !linkAlways -> "Wyłączony - włączy się na czas treningu"
        else -> "Nie działa - otwórz tę aplikację jeszcze raz"
    }

    when (sub) {
        // Zegarek jest tu ustawieniem, nie zakładką: podłączasz go raz
        // i wracasz, gdy coś nie przyszło. To, co przysyła, widać w Postępie.
        ProfileSub.HEALTH -> {
            HealthScreen(onBack = { sub = ProfileSub.NONE })
            return
        }

        // Wybór sprzętu na całym ekranie - pięćdziesiąt pozycji w okienku
        // czytało się przez szparę. Wynik idzie do szkicu, jak każda zmiana
        // w Profilu, a powrót prowadzi tam, skąd się przyszło.
        ProfileSub.GEAR_PICK -> {
            GearPickerScreen(
                selected = draft.ownedGear,
                onCancel = { sub = ProfileSub.GEAR },
                onDone = { picked ->
                    draft = draft.copy(ownedGear = picked)
                    sub = ProfileSub.GEAR
                }
            )
            return
        }

        ProfileSub.GOAL -> {
            GoalScreen(
                goal = draft.trainingGoal,
                onBack = { sub = ProfileSub.NONE },
                onPick = {
                    draft = draft.copy(trainingGoal = it)
                    sub = ProfileSub.NONE
                }
            )
            return
        }

        // ---- Sprzęt i obciążenia ----
        //
        // Jeden przełącznik. Były dwa o odwróconym znaczeniu („mam
        // wszystko", „dowolny ciężar"), które pytały o to samo - czy
        // trenujesz tam, gdzie jest wszystko.
        ProfileSub.GEAR -> {
            ProfileSubScreen(
                title = "Sprzęt i obciążenia",
                onBack = { sub = ProfileSub.NONE },
                // Talerze i gryf zmienia się tutaj, więc i tutaj musi dać
                // się je zapisać - po powrocie łatwo o tym zapomnieć.
                canSave = canSave,
                onSave = { save() }
            ) {
                SettingsGroup {
                    SettingsRow(
                        title = "Dostosuj sprzęt i obciążenia",
                        subtitle = if (draft.customGear) "Plany, jednostki i ćwiczenia dobrane do Twojego sprzętu"
                        else "Wyłączone: dostęp do wszystkiego, ciężar dowolny",
                        icon = Icons.Filled.FitnessCenter,
                        first = true,
                        onClick = { draft = draft.copy(customGear = !draft.customGear) }
                    ) {
                        Switch(checked = draft.customGear, onCheckedChange = { draft = draft.copy(customGear = it) })
                    }
                    if (draft.customGear) {
                        SettingsRow(
                            title = "Sprzęt",
                            // Podsumowanie po działach, nie lista - zmienia
                            // się to na ekranie wyboru, tu ma tylko być
                            // widać, co jest.
                            subtitle = if (draft.ownedGear.isEmpty()) "Nic nie dodano - przyjmuję, że masz wszystko"
                            else GearGroup.entries.mapNotNull { g ->
                                draft.ownedGear.filter { it.group == g }.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ") { it.label.lowercase() }
                            }.joinToString(" · "),
                            icon = Icons.AutoMirrored.Filled.List,
                            value = draft.ownedGear.size.takeIf { it > 0 }?.toString(),
                            chevron = true,
                            onClick = { sub = ProfileSub.GEAR_PICK }
                        )
                    }
                }
                if (draft.customGear && usesPlates) {
                    GroupLabel("Obciążenia")
                    SettingsGroup {
                        // Gryf tylko wtedy, gdy masz sztangę.
                        if (hasBarbell) {
                            SettingsRow(
                                title = "Gryf",
                                subtitle = "masa samego gryfu",
                                icon = Icons.Filled.Remove,
                                first = true
                            ) {
                                OutlinedTextField(
                                    value = barText,
                                    onValueChange = { barText = it },
                                    suffix = { Text("kg") },
                                    isError = !barOk,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(110.dp)
                                )
                            }
                        }
                        SettingsRow(
                            title = "Talerze",
                            subtitle = if (plates.isEmpty())
                                "Bez talerzy ciężar jest dowolny. Dodaj je, jeśli aplikacja ma proponować " +
                                    "tylko to, co da się złożyć."
                            else "Łączna liczba sztuk, nie na stronę",
                            icon = Icons.Filled.Album,
                            first = !hasBarbell
                        ) {
                            TextButton(onClick = { addingPlate = true }) { Text("+ Dodaj") }
                        }
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
                } else if (draft.customGear) {
                    // Bez sztangi i hantli nie ma czego składać z talerzy.
                    Text(
                        "Twój sprzęt nie wymaga składania ciężaru z talerzy - na wyciągu " +
                            "i maszynach ustawiasz obciążenie bezpośrednio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, start = 2.dp)
                    )
                }
            }
            return
        }

        // ---- Zegarek: pilot ----
        ProfileSub.WATCH -> {
            ProfileSubScreen(title = "Zegarek", onBack = { sub = ProfileSub.NONE }) {
                SettingsGroup {
                    SettingsRow(
                        title = "Pilot na zegarku",
                        subtitle = pilotStatus,
                        icon = Icons.Filled.Watch,
                        iconTint = if (pilotOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        first = true
                    ) {
                        StatusDot(pilotOk)
                    }
                    if (!linkListening && !linkAllowed) {
                        Button(
                            onClick = { nearbyLauncher.launch(WatchLinkService.NEARBY) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp)
                        ) { Text("Zezwól na urządzenia w pobliżu") }
                    }
                    // Drugi stan, osobny od pierwszego i ważniejszy, niż
                    // wygląda: pilot może działać teraz i zginąć za kwadrans.
                    // Dopóki system ma prawo go ubić, "działa" mówi prawdę
                    // tylko o tej chwili.
                    SettingsRow(
                        title = "Praca w tle",
                        subtitle = if (batteryFree) "Bez ograniczeń - pilot przeżyje wygaszony ekran"
                        else "System może ubić pilota w szatni. Serie poczekają w kolejce zegarka, " +
                            "ale dojadą dopiero po ponownym starcie pilota.",
                        icon = Icons.Filled.BatteryChargingFull,
                        iconTint = if (batteryFree) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                    ) {
                        if (batteryFree) StatusDot(true)
                    }
                    if (!batteryFree) {
                        Button(
                            onClick = { WatchLinkService.askBattery(context) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 12.dp)
                        ) { Text("Pozwól pracować w tle") }
                    }
                    SettingsRow(
                        title = "Działa też poza treningiem",
                        // Bez tego nie da się zacząć treningu z zegarka: zanim
                        // sesja istnieje, nie ma komu odebrać żądania.
                        subtitle = "Start i koniec treningu z zegarka",
                        icon = Icons.Filled.Schedule,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Switch(
                            checked = linkAlways,
                            onCheckedChange = {
                                linkAlways = it
                                WatchLinkService.setAlways(context, it)
                                // Usługa wstaje i gaśnie asynchronicznie, więc
                                // stan odczytujemy po chwili - inaczej
                                // pokazalibyśmy poprzedni.
                                mainHandler.postDelayed({ linkListening = WatchLinkService.listening }, 400)
                            }
                        )
                    }
                    // „Kod parowania", nie „token": to jedyna rzecz stąd,
                    // którą przepisuje się do aplikacji na zegarku, i nazwa
                    // ma mówić po co.
                    SettingsRow(
                        title = "Kod parowania",
                        subtitle = linkToken,
                        icon = Icons.Filled.Key,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Adres tylko w wariancie debug: na co dzień to szczegół
                    // budowy, przydaje się wyłącznie przy sprawdzaniu pilota
                    // z komputera.
                    if (DIAGNOSTICS) {
                        SettingsRow(
                            title = "Adres",
                            subtitle = "http://127.0.0.1:${WatchLinkService.PORT}",
                            icon = Icons.Filled.Code,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return
        }

        // Czyszczenie historii jest ustawieniem, nie czynnością treningową -
        // dlatego siedzi w Profilu, a nie pod ręką w zakładce Trening.
        ProfileSub.DATA -> {
            ProfileSubScreen(title = "Dane", onBack = { sub = ProfileSub.NONE }) {
                SettingsGroup {
                    val c = counts
                    SettingsRow(
                        title = "Wyczyść historię treningów",
                        subtitle = when {
                            c == null -> "Liczę…"
                            c.first == 0 -> "Historia jest pusta"
                            else -> plural(c.first, "trening", "treningi", "treningów") + " · " +
                                plural(c.second, "seria", "serie", "serii") +
                                " · plany, masa i profil zostają"
                        },
                        icon = Icons.Filled.Delete,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        first = true,
                        chevron = (c?.first ?: 0) > 0,
                        onClick = if ((c?.first ?: 0) > 0) ({ confirmWipe = true }) else null
                    )
                }
                wipeNote?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }

                // Uznanie autorstwa danych o produktach.
                //
                // Nie uprzejmość, tylko warunek licencji: Open Food Facts jest
                // na Open Database License, która wymaga wskazania źródła
                // wszędzie tam, gdzie korzysta się z bazy - a ustawienia
                // Profilu są miejscem, gdzie nie przeszkadza przy codziennym
                // użyciu.
                GroupLabel("Źródła danych o produktach")
                Text(
                    "Dane o produktach pochodzą z Open Food Facts i są udostępniane " +
                        "na licencji Open Database License (ODbL). Pieczywo, nabiał, " +
                        "warzywa i wędliny bez kodu kreskowego pochodzą z tabel " +
                        "opublikowanych przez sklepy - Biedronkę i Frisco.pl. " +
                        "Produkty podstawowe i wpisane ręcznie są własne.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        ProfileSub.NONE -> Unit
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        // Nagłówek poza przewijaniem. Przycisk Zapisz dotyczy całego ekranu,
        // więc musi być widać go z każdego miejsca listy - przewinięty razem
        // z tytułem znikałby dokładnie wtedy, gdy zmieniasz coś na dole.
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Profil", style = MaterialTheme.typography.headlineLarge)
                Text(
                    when {
                        dirty && !canSave -> "Popraw zaznaczone pola"
                        dirty -> "Niezapisane zmiany"
                        else -> "Wszystko zapisane"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        dirty && !canSave -> MaterialTheme.colorScheme.error
                        dirty -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Button(onClick = { save() }, enabled = canSave) { Text("Zapisz") }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

        // ---- Kim jesteś ----
        // Karta-wizytówka u góry: to, co widać na pierwszy rzut oka, jest
        // podsumowaniem pól pod nią, więc poprawka od razu się w nim odbija.
        Spacer(Modifier.height(6.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(27.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        listOfNotNull(
                            birthYear.toIntOrNull()?.let { java.time.LocalDate.now().year - it }?.takeIf { it in 5..110 }
                                ?.let { "$it lat" },
                            height.toIntOrNull()?.takeIf { it in 100..250 }?.let { "$it cm" }
                        ).joinToString(" · ").ifEmpty { "Uzupełnij dane" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TagPill(if (draft.sex == Sex.MALE) "mężczyzna" else "kobieta", MaterialTheme.colorScheme.primary)
                        TagPill(goalLabel(draft.trainingGoal).lowercase(), MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        GroupLabel("Dane")
        AppCard {
            Column(Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Wzrost (cm)") },
                        isError = !heightOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = birthYear,
                        onValueChange = { birthYear = it },
                        label = { Text("Rok urodzenia") },
                        isError = !birthOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                SegmentedTabs(
                    labels = listOf("mężczyzna", "kobieta"),
                    selected = if (draft.sex == Sex.MALE) 0 else 1,
                    onSelect = { draft = draft.copy(sex = if (it == 0) Sex.MALE else Sex.FEMALE) }
                )
                Text(
                    "Wzrost i płeć wchodzą do oszacowania tkanki tłuszczowej z obwodów.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // ---- Trening ----
        GroupLabel("Trening")
        SettingsGroup {
            SettingsRow(
                title = "Cel",
                subtitle = goalLabel(draft.trainingGoal),
                icon = Icons.Filled.Flag,
                first = true,
                chevron = true,
                onClick = { sub = ProfileSub.GOAL }
            )
            SettingsRow(
                title = "Sprzęt i obciążenia",
                subtitle = when {
                    !draft.customGear -> "Pełna siłownia, ciężar dowolny"
                    draft.ownedGear.isEmpty() -> "Dostosowane - nic nie dodano"
                    else -> plural(draft.ownedGear.size, "pozycja", "pozycje", "pozycji") +
                        if (usesPlates && plates.isNotEmpty())
                            " · " + plural(plates.size, "rodzaj talerzy", "rodzaje talerzy", "rodzajów talerzy")
                        else ""
                },
                icon = Icons.Filled.FitnessCenter,
                chevron = true,
                onClick = { sub = ProfileSub.GEAR }
            )
        }

        // ---- Ustawienia ----
        GroupLabel("Ustawienia")
        SettingsGroup {
            SettingsRow(
                title = "Zegarek",
                subtitle = if (pilotOk && !batteryFree) "Pilot działa, ale system może go ubić w tle"
                else pilotStatus,
                icon = Icons.Filled.Watch,
                first = true,
                chevron = true,
                onClick = { sub = ProfileSub.WATCH }
            ) {
                StatusDot(pilotOk && batteryFree, warn = pilotOk)
            }
            SettingsRow(
                title = "Health Connect",
                subtitle = lastSync?.let { "Ostatnio pobrano " + syncDate(it) } ?: "Jeszcze nic nie pobrano",
                icon = Icons.Filled.MonitorHeart,
                chevron = true,
                onClick = { sub = ProfileSub.HEALTH }
            )
            SettingsRow(
                title = "Dane",
                subtitle = "Historia treningów, źródła danych",
                icon = Icons.Filled.Storage,
                chevron = true,
                onClick = { sub = ProfileSub.DATA }
            )
        }

        Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Podekran Profilu: strzałka wstecz, tytuł i - gdy podekran zmienia szkic -
 * przycisk Zapisz, ten sam co na głównym widoku.
 */
@Composable
private fun ProfileSubScreen(
    title: String,
    onBack: () -> Unit,
    canSave: Boolean = false,
    onSave: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenBackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onSave != null) Button(onClick = onSave, enabled = canSave) { Text("Zapisz") }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(6.dp))
            content()
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Cel treningu: jedno pytanie, trzy odpowiedzi.
 *
 * Stało tu „Co ma rosnąć: siła / masa / mieszany" i pod spodem „Faza:
 * redukcja / utrzymanie / budowa" - a „masa" w pierwszym i „redukcja"
 * w drugim czytały się jak sprzeczność, bo jedno słowo znaczyło raz mięśnie,
 * raz wagę ciała. Faza poszła do Kalorii, tu został sam trening.
 */
@Composable
private fun GoalScreen(goal: TrainingGoal, onBack: () -> Unit, onPick: (TrainingGoal) -> Unit) {
    ProfileSubScreen(title = "Cel treningu", onBack = onBack) {
        listOf(
            Triple(TrainingGoal.STRENGTH, Icons.Filled.FitnessCenter, "Progresja głównie ciężarem"),
            Triple(TrainingGoal.HYPERTROPHY, Icons.Filled.SportsGymnastics, "Ciężar, powtórzenia i serie"),
            Triple(TrainingGoal.MIXED, Icons.Filled.Balance, "Po trochu jednego i drugiego")
        ).forEach { (g, icon, opis) ->
            OptionCard(
                title = goalLabel(g),
                subtitle = opis,
                icon = icon,
                selected = g == goal,
                onClick = { onPick(g) }
            )
        }
    }
}

/** Karta jednej odpowiedzi z ikoną; wybrana ma obwódkę w kolorze akcji i ptaszek. */
@Composable
internal fun OptionCard(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(
        modifier.padding(vertical = 4.dp),
        accent = if (selected) MaterialTheme.colorScheme.primary else null,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                IconBadge(icon)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Wybrane", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Data ostatniego pobrania danych z zegarka, w formie czytelnej na ekranie. */
private fun syncDate(ms: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

// ---- Elementy wspólne ----------------------------------------------------

@Composable
private fun PlateRow(
    plate: Plate,
    hasBarbell: Boolean,
    hasDumbbells: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Wiersz w grupie, nie osobna karta: talerz jest częścią listy „Talerze",
    // a karta w karcie robiła z siedmiu talerzy siedem ramek.
    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Column(Modifier.fillMaxWidth().padding(start = 62.dp, end = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Loading.fmt(plate.kg)} kg  × ${plate.count}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Zmień", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Kropka stanu: zielona - działa, czerwona - nie. [warn] - działa, ale
 * z zastrzeżeniem (bursztynowa zamiast czerwonej).
 */
@Composable
private fun StatusDot(ok: Boolean, warn: Boolean = false) {
    val kolor: Color = when {
        ok -> MaterialTheme.colorScheme.secondary
        warn -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        Modifier.padding(start = 8.dp).size(10.dp).clip(RoundedCornerShape(5.dp))
            .background(kolor)
    )
}

/** Nazwa celu treningu - „mięśnie", nie „masa": masa to waga ciała, a ta jest w Kaloriach. */
private fun goalLabel(g: TrainingGoal): String = when (g) {
    TrainingGoal.STRENGTH -> "Siła"
    TrainingGoal.HYPERTROPHY -> "Mięśnie"
    TrainingGoal.MIXED -> "Siła i mięśnie"
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
