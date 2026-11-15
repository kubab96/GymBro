package pl.gymbro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 *
 * **Pytamy tylko o to, co masz.** Sekcja obciążeń dopasowuje się do zaznaczonego
 * sprzętu: bez sztangi nie ma pytania o gryf, bez sprzętu ładowanego talerzami
 * nie ma w ogóle o czym rozmawiać.
 */
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileRepository(context) }
    val gymRepo = remember { GymRepository(context) }

    var profile by remember { mutableStateOf(Cache.peek<UserProfile>(Cache.PROFILE) ?: UserProfile()) }
    var inventory by remember { mutableStateOf(Cache.peek<Inventory>(Cache.INVENTORY)) }
    var loaded by remember { mutableStateOf(Cache.peek<Inventory>(Cache.INVENTORY) != null) }

    var height by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var barText by remember { mutableStateOf("") }

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

    suspend fun reloadInventory() {
        val i = withContext(Dispatchers.IO) { gymRepo.inventory() }
        inventory = Cache.put(Cache.INVENTORY, i)
        barText = Loading.fmt(i.barKg)
    }

    LaunchedEffect(Unit) {
        Busy.track {
            val p = withContext(Dispatchers.IO) { profileRepo.get() }
            profile = Cache.put(Cache.PROFILE, p)
            height = p.heightCm?.let { "%.0f".format(it) } ?: ""
            birthYear = p.birthYear?.toString() ?: ""
            reloadInventory()
            lastSync = withContext(Dispatchers.IO) { health.lastSyncedAt() }
            loaded = true
        }
    }

    // Osobno od reszty profilu, bo liczniki starzeją się same: zakładka żyje
    // w tle i nie składa się od nowa po treningu. Reszta ekranu to ustawienia,
    // które zmieniasz tylko tutaj, więc tamto wystarczy wczytać raz.
    LaunchedEffect(Refresh.version) {
        counts = withContext(Dispatchers.IO) { sessions.historyCounts() }
        batteryFree = WatchLinkService.batteryUnrestricted(context)
        linkListening = WatchLinkService.listening
        linkAllowed = WatchLinkService.canStart(context)
    }

    fun persist(updated: UserProfile) {
        profile = updated
        scope.launch {
            withContext(Dispatchers.IO) { profileRepo.save(updated) }
            // Tryb obciążeń mieszka w profilu, a wpływa na inwentarz - po zmianie
            // trzeba go przeczytać na nowo, żeby reszta ekranu się zgadzała.
            reloadInventory()
        }
    }

    // Naciskiem w otwarta zakladke wracamy do glownego widoku profilu.
    // Przed sprawdzeniem `loaded`, bo uchwyt musi zyc w kazdym skladaniu:
    // wypadniecie z drzewa i powrot odpalilyby go po raz drugi.
    TabReselectHandler { showWatch = false }

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

    val owned = profile.ownedEquipment
    val hasBarbell = profile.canDo(Equipment.BARBELL)
    val hasDumbbells = profile.canDo(Equipment.DUMBBELL_PAIR) ||
        profile.canDo(Equipment.DUMBBELL_SINGLE)
    val usesPlates = hasBarbell || hasDumbbells

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Profil", style = MaterialTheme.typography.headlineLarge)
        profile.age?.let {
            Text(
                "$it lat",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Dane ----
        Spacer(Modifier.height(18.dp))
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it },
                    label = { Text("Rok urodzenia") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s ->
                    val label = if (s == Sex.MALE) "mężczyzna" else "kobieta"
                    if (s == profile.sex) {
                        Button(
                            onClick = { persist(profile.copy(sex = s)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { persist(profile.copy(sex = s)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(label) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    persist(
                        profile.copy(
                            heightCm = height.trim().replace(',', '.').toDoubleOrNull(),
                            birthYear = birthYear.trim().toIntOrNull()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Zapisz dane") }
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
                    if (g == profile.trainingGoal) {
                        Button(
                            onClick = { persist(profile.copy(trainingGoal = g)) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { persist(profile.copy(trainingGoal = g)) },
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
                    if (p == profile.phase) {
                        Button(
                            onClick = { persist(profile.copy(phase = p)) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { persist(profile.copy(phase = p)) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) { Text(label) }
                    }
                }
            }
        }

        // ---- Sprzęt ----
        Spacer(Modifier.height(12.dp))
        SectionCard("Sprzęt") {
            ToggleRow(
                title = "Mam dostęp do wszystkiego",
                subtitle = "Tak działa siłownia komercyjna. Wyłącz, jeśli trenujesz " +
                    "w domu albo masz ograniczony sprzęt.",
                checked = owned == null,
                onChange = { all ->
                    // Przy wyłączeniu zaznaczamy wszystko, żeby zacząć od pełnej
                    // listy i odznaczać, a nie budować ją od zera.
                    persist(profile.copy(ownedEquipment = if (all) null else Equipment.entries.toSet()))
                }
            )

            AnimatedVisibility(visible = owned != null) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Equipment.entries.forEach { eq ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = owned?.contains(eq) == true,
                                onCheckedChange = { on ->
                                    val next = (owned ?: emptySet()).toMutableSet().apply {
                                        if (on) add(eq) else remove(eq)
                                    }
                                    persist(profile.copy(ownedEquipment = next))
                                }
                            )
                            Text(equipmentName(eq), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (owned.isNullOrEmpty()) {
                        Text(
                            "Nic nie zaznaczone - traktuję to jak brak ograniczeń.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        // ---- Obciążenia ----
        Spacer(Modifier.height(12.dp))
        SectionCard("Obciążenia") {
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
                ToggleRow(
                    title = "Dowolny ciężar",
                    subtitle = "Wyłącz, jeśli masz konkretny zestaw talerzy. Aplikacja " +
                        "będzie wtedy proponować tylko ciężary, które da się złożyć.",
                    checked = !profile.limitedWeights,
                    onChange = { any -> persist(profile.copy(limitedWeights = !any)) }
                )

                AnimatedVisibility(visible = profile.limitedWeights) {
                    Column {
                        val inv = inventory

                        // Gryf tylko wtedy, gdy masz sztangę.
                        if (hasBarbell) {
                            Spacer(Modifier.height(14.dp))
                            Text("Gryf", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = barText,
                                    onValueChange = { barText = it },
                                    label = { Text("Masa (kg)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    val kg = barText.replace(',', '.').toDoubleOrNull()
                                    if (kg != null && kg > 0) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { gymRepo.setBarWeight(kg) }
                                            reloadInventory()
                                        }
                                    }
                                }) { Text("Zapisz") }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Talerze", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Podaj łączną liczbę sztuk, nie na stronę.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        if (inv == null || inv.plates.isEmpty()) {
                            Text(
                                "Nie masz jeszcze żadnych talerzy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            inv.plates.forEach { p ->
                                PlateRow(
                                    plate = p,
                                    hasBarbell = hasBarbell,
                                    hasDumbbells = hasDumbbells,
                                    onEdit = { editingPlate = p },
                                    onDelete = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { gymRepo.deletePlate(p.id) }
                                            reloadInventory()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { addingPlate = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Dodaj talerz") }
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

    val toEdit = editingPlate
    if (toEdit != null || addingPlate) {
        PlateDialog(
            plate = toEdit,
            // Pytanie o pasowanie na gryf ma sens tylko wtedy, gdy masz sztangę.
            askBarbellFit = hasBarbell,
            onDismiss = { editingPlate = null; addingPlate = false },
            onSave = { plate ->
                scope.launch {
                    withContext(Dispatchers.IO) { gymRepo.upsertPlate(plate) }
                    editingPlate = null
                    addingPlate = false
                    reloadInventory()
                }
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
}
