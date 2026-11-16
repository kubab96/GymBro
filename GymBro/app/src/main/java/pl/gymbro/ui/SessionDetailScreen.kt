package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import pl.gymbro.data.Equipment
import pl.gymbro.data.Exercise
import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.SetEntry
import pl.gymbro.data.stepOrDefault
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ExerciseRepository
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SessionSummary
import pl.gymbro.gym.Loading
import pl.gymbro.gym.MuscleMap
import pl.gymbro.gym.RunAnalysis
import pl.gymbro.gym.StrengthAnalysis
import pl.gymbro.health.ActivityDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zapis podsumowania sesji na czas odtworzenia aktywności.
 *
 * Ten sam powód co przy aktywnościach z zegarka: system potrafi ubić
 * aplikację w tle i odtworzyć ją po powrocie, a historia wczytuje się
 * asynchronicznie, więc szukanie po identyfikatorze wracałoby na listę.
 * Obrót ekranu aktywności już nie niszczy - patrz configChanges w manifeście. Sklejona sesja z zegarka jedzie razem,
 * bo bez niej ekran straciłby tętno.
 */
val SessionSummarySaver: Saver<SessionSummary?, Any> = listSaver(
    save = { s ->
        if (s == null) emptyList() else listOf(
            s.sessionId, s.planName ?: "", s.startedAt, s.endedAt,
            s.workingSets, s.tonnage,
            s.watch?.externalId ?: "", s.watch?.startedAt ?: 0L, s.watch?.endedAt ?: 0L,
            s.watch?.type ?: "", s.watch?.hrAvg ?: -1.0, s.watch?.hrMax ?: -1.0,
            s.watch?.energyKcal ?: -1.0
        )
    },
    restore = { v ->
        if (v.isEmpty()) null else SessionSummary(
            sessionId = v[0] as Long,
            planName = (v[1] as String).ifEmpty { null },
            startedAt = v[2] as Long,
            endedAt = v[3] as Long,
            workingSets = v[4] as Int,
            tonnage = v[5] as Double,
            watch = (v[6] as String).ifEmpty { null }?.let { id ->
                ExternalSession(
                    externalId = id,
                    source = "zegarek",
                    type = v[9] as String,
                    startedAt = v[7] as Long,
                    endedAt = v[8] as Long,
                    hrAvg = (v[10] as Double).takeIf { it >= 0 },
                    hrMax = (v[11] as Double).takeIf { it >= 0 },
                    energyKcal = (v[12] as Double).takeIf { it >= 0 }
                )
            }
        )
    }
)

/**
 * Szczegóły treningu siłowego: Twój zapis złożony z pomiarem zegarka.
 *
 * Osobno żadne z tych źródeł nie opisuje treningu. Zegarek widzi godzinę
 * podwyższonego tętna i nie wie, czy to były przysiady, czy rower. Aplikacja
 * zna każdy kilogram, ale nie wie, który z nich Cię zabolał. Spoiwem jest czas
 * zapisu każdej serii - i dopiero on pozwala powiedzieć, że przysiad podbił
 * tętno do 165, a uginanie ledwie do 120.
 *
 * Wykres tętna z zaznaczonymi seriami pokazuje to samo od drugiej strony:
 * widać, jak wysoko wchodzisz przy pracy i jak nisko schodzisz w przerwie.
 * Zanikające spadki pod koniec sesji to najczytelniejszy znak, że masz dość.
 */
@Composable
fun SessionDetailScreen(summary: SessionSummary, onBack: () -> Unit) {
    // Systemowy wstecz robi to samo co „Wróć" - bez tego wychodził z aplikacji.
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val exerciseRepo = remember { ExerciseRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }
    val reader = remember { ActivityDetail(context) }

    val planRepo = remember { PlanRepository(context) }
    val goToTab = LocalGoToTab.current
    // Propozycje z tego wlasnie treningu. Trafia sie na nie tutaj, patrzac
    // co sie zrobilo - i stad ma byc jedno klikniecie do planu, a nie
    // szukanie po zakladkach.
    var fromHere by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    LaunchedEffect(summary.sessionId) {
        fromHere = withContext(Dispatchers.IO) {
            planRepo.itemsWithAdvice().filter { it.advice?.sessionId == summary.sessionId }
        }
    }

    var blocks by remember { mutableStateOf<List<StrengthAnalysis.Block>>(emptyList()) }
    var byMuscle by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var hr by remember { mutableStateOf<List<RunAnalysis.Sample>>(emptyList()) }
    var setTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var maxHr by remember { mutableStateOf(190.0) }
    var loading by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Poprawianie po fakcie: ćwiczenie, którego serie są otwarte, i seria
    // w trakcie zmiany. Numery, nie obiekty - po każdym zapisie bloki
    // wczytują się od nowa i trzymany obiekt pokazywałby stan sprzed zmiany.
    val gymRepo = remember { GymRepository(context) }
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var editingExercise by remember { mutableStateOf<Long?>(null) }
    var editingSet by remember { mutableStateOf<SetEntry?>(null) }

    /**
     * Serie i wszystko, co z nich wynika. Wołane także po każdej poprawce.
     *
     * Tętna nie czyta się tu ponownie: to kilkaset próbek z Health Connect,
     * a poprawienie liczby powtórzeń niczego w nich nie zmienia.
     */
    suspend fun reloadSets(samples: List<RunAnalysis.Sample>) {
        val loaded = withContext(Dispatchers.IO) {
            val sets = sessionRepo.setsFor(summary.sessionId)
            val all = exerciseRepo.all().associateBy { it.id }
            Triple(sets, all, sessionRepo.setsByMuscleInSession(summary.sessionId))
        }
        exercises = loaded.second
        byMuscle = loaded.third
        blocks = StrengthAnalysis.blocks(
            loaded.first, loaded.second.mapValues { it.value.name }, samples
        )
        setTimes = loaded.first.map { it.doneAt }
    }

    /** Zmiana w seriach: zapis, przeładowanie i sygnał dla listy historii. */
    fun change(action: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            reloadSets(hr)
            // Lista w zakładce Trening pokazuje liczbę serii i objętość.
            Refresh.bump()
        }
    }

    LaunchedEffect(summary.sessionId) {
        val profile = withContext(Dispatchers.IO) { profileRepo.get() }
        profile.birthYear?.let { year ->
            val age = java.time.LocalDate.now().year - year
            if (age in 10..100) maxHr = RunAnalysis.maxHrFromAge(age)
        }
        // Tętno tylko wtedy, gdy ta sesja skleiła się z zapisem zegarka.
        val samples = summary.watch?.let {
            reader.load(it.startedAt, it.endedAt).heartRate
        } ?: emptyList()
        inventory = withContext(Dispatchers.IO) { gymRepo.inventory() }
        reloadSets(samples)
        hr = samples
        loading = false
    }

    // Liczby w nagłówku z serii, nie z podsumowania, z którym tu weszliśmy:
    // tamto powstało przed poprawką i po zmianie ciężaru pokazywałoby
    // objętość treningu, którego już nie ma. Póki serie się wczytują,
    // zostaje podsumowanie - inaczej nagłówek mignąłby zerem.
    val workingSets = if (loading) summary.workingSets else blocks.sumOf { it.sets.size }
    val tonnage = if (loading) summary.tonnage else blocks.sumOf { it.tonnage }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                SimpleDateFormat("EEEE, d MMMM, HH:mm", Locale.getDefault())
                    .format(Date(summary.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            summary.planName ?: "Trening",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${tonnage.toInt()}",
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                " kg objętości",
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        val w = summary.watch
        StatCard(
            listOfNotNull(
                "Czas" to if (summary.durationMin < 1) "<1 min" else "${summary.durationMin} min",
                "Serie" to "$workingSets",
                "Powtórzenia" to "${blocks.sumOf { it.totalReps }}",
                w?.hrAvg?.let { "Tętno śr." to "${it.toInt()}" },
                w?.hrMax?.let { "Tętno maks." to "${it.toInt()}" },
                w?.energyKcal?.takeIf { it > 0 }?.let { "Energia" to "${it.toInt()} kcal" }
            )
        )

        if (loading) {
            Spacer(Modifier.height(26.dp))
            Text("Wczytuję…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(60.dp))
            return@Column
        }

        if (hr.size >= 4) {
            Spacer(Modifier.height(26.dp))
            Text("Tętno i serie", style = MaterialTheme.typography.titleMedium)
            Text(
                "Pionowe kreski to momenty zapisania serii. Im płytsze spadki " +
                    "między nimi, tym mniej zdążyłeś odpocząć.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            HrWithSets(RunAnalysis.downsample(hr, 180), setTimes, maxHr)
        }


        if (byMuscle.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Text("Co obrobiłeś", style = MaterialTheme.typography.titleMedium)
            Text(
                "Z wykonanych ćwiczeń. Pełny kolor to partia najmocniej " +
                    "obrobiona w tym treningu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Skala względem tej sesji, nie progów tygodniowych: pojedynczy
            // trening rzadko dobija do dwudziestu serii na partię, więc przy
            // tamtej skali wszystko byłoby blade i nic by z tego nie wynikało.
            MuscleBody(MuscleMap.intensities(byMuscle, byMuscle.values.max()))
            Spacer(Modifier.height(10.dp))
            MuscleLegend("nietknięte", "${byMuscle.values.max()} serii")
        }
        Spacer(Modifier.height(26.dp))
        Text("Ćwiczenia", style = MaterialTheme.typography.titleMedium)
        if (hr.isNotEmpty()) {
            Text(
                "Tętno przy każdym ćwiczeniu - widać, co naprawdę męczy, " +
                    "a co tylko wygląda ciężko.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (blocks.isNotEmpty()) {
            Text(
                "Dotknij ćwiczenia, żeby poprawić jego serie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))

        if (blocks.isEmpty()) {
            Text(
                "Ten trening nie ma zapisanych serii.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val hardest = blocks.mapNotNull { it.hrAvg }.maxOrNull()
            blocks.forEach { b ->
                ExerciseBlockRow(
                    block = b,
                    maxHr = maxHr,
                    hardest = b.hrAvg != null && b.hrAvg == hardest,
                    onClick = { editingExercise = b.exerciseId }
                )
            }
        }

        if (fromHere.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                onClick = {
                    // Celujemy w konkretne cwiczenie, nie w sama zakladke -
                    // inaczej ladujesz na liscie programow i szukasz, w ktorej
                    // jednostce to cwiczenie lezy.
                    val pierwsze = fromHere.first()
                    PlanJump.to(pierwsze.planId, pierwsze.id)
                    goToTab(PLAN_TAB)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Po tym treningu czeka " + plural(
                            fromHere.size, "propozycja", "propozycje", "propozycji"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    fromHere.take(3).forEach { item ->
                        val a = item.advice
                        if (a != null) {
                            Text(
                                item.exerciseName + ": " + adviceText(item, a),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        "Dotknij, żeby otworzyć plan na tym ćwiczeniu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (summary.watch == null) {
            Spacer(Modifier.height(16.dp))
            // Najczęstszy powód nie jest awarią: serie zapisane pilotem na
            // zegarku nie tworzą natywnego treningu siłowego, bo mini-aplikacja
            // nie może go uruchomić - API treningu jest tylko do odczytu.
            // Dawny komunikat brzmiał jak błąd synchronizacji i mylił.
            Text(
                "Brak zapisu tętna dla tego treningu. Jeśli zapisywałeś serie " +
                    "pilotem na zegarku, to normalne: pilot nie uruchamia " +
                    "natywnego treningu siłowego, więc zegarek nie prowadzi " +
                    "osobnego pomiaru. Żeby mieć tętno, włącz na zegarku " +
                    "trening siłowy obok pilota.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        // Kasowanie treningu siedzi w jego szczegółach, a nie na liście.
        // Tutaj widać, co dokładnie znika - na liście łatwo trafić w sąsiedni
        // wiersz, a pomyłkowo rozpoczęty trening i tak trzeba najpierw otworzyć.
        Spacer(Modifier.height(28.dp))
        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Usuń ten trening", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(36.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć trening?") },
            text = {
                Text(
                    if (workingSets == 0)
                        "Ten trening nie ma zapisanych serii - zniknie bez śladu."
                    else "Zniknie razem z " +
                        plural(workingSets, "serią", "seriami", "seriami") +
                        ". Tego nie da się cofnąć."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            sessionRepo.deleteSession(summary.sessionId)
                        }
                        confirmDelete = false
                        // Historia w zakładce Trening kluczuje na tym sygnale.
                        Refresh.bump()
                        onBack()
                    }
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
            }
        )
    }

    // ---- Poprawianie serii ----
    //
    // Blok szukany przy każdym składaniu, po numerze ćwiczenia. Gdy usunięto
    // jego ostatnią serię, bloku już nie ma i okno zamyka się samo.
    val openBlock = blocks.firstOrNull { it.exerciseId == editingExercise }
    val inv = inventory
    if (openBlock != null && inv != null) {
        val exercise = exercises[openBlock.exerciseId]
        SetsDialog(
            block = openBlock,
            unit = exercise?.equipment?.weightUnitLabel ?: "kg",
            onDismiss = { editingExercise = null },
            onEdit = { editingSet = it },
            onAdd = {
                // Dopisana seria dziedziczy ciężar i powtórzenia po ostatniej -
                // zapomniana seria to prawie zawsze powtórka poprzedniej,
                // a poprawić ją można od razu. Godzina sekundę po ostatniej:
                // prawdziwej nikt nie zna, a ta trzyma kolejność i nie
                // dostawia kreski w przypadkowym miejscu wykresu tętna.
                val last = openBlock.sets.last()
                change {
                    sessionRepo.addSet(
                        last.copy(
                            id = 0,
                            setIndex = openBlock.sets.maxOf { it.setIndex } + 1,
                            rir = null,
                            doneAt = last.doneAt + 1_000
                        )
                    )
                }
            }
        )

        editingSet?.let { s ->
            val equipment = exercise?.equipment ?: Equipment.MACHINE
            EditSetDialog(
                entry = s,
                exerciseName = openBlock.name,
                equipment = equipment,
                step = stepOrDefault(exercise?.stepKg),
                // Zero przy masie ciała to „bez obciążenia", nie pomyłka do
                // poprawienia. Gdy ciężar jednak zapisano, da się go zmienić.
                showWeight = equipment != Equipment.BODYWEIGHT || s.weightKg > 0.0,
                inventory = inv,
                onDismiss = { editingSet = null },
                onSave = { w, r, rir ->
                    editingSet = null
                    change { sessionRepo.updateSet(s.id, w, r, rir) }
                },
                onDelete = {
                    editingSet = null
                    change { sessionRepo.deleteSet(s.id) }
                }
            )
        }
    }
    // W efekcie, nie wprost w składaniu: zapis stanu w trakcie rysowania
    // wymusza drugie składanie i potrafi się zapętlić.
    val zniknal = openBlock == null && editingExercise != null && !loading
    LaunchedEffect(zniknal) {
        if (zniknal) {
            editingExercise = null
            editingSet = null
        }
    }
}

/**
 * Serie jednego ćwiczenia z zakończonego treningu - do poprawienia.
 *
 * Historia była dotąd tylko do czytania: pomyłkę zauważoną po naciśnięciu
 * „Zakończ" dało się naprawić wyłącznie kasując cały trening. A to z niej
 * liczy się następny ciężar, więc seria wpisana z 80 zamiast 60 kg psuła
 * propozycję na kolejny raz.
 *
 * Lista, a nie od razu jedno okno poprawiania: ćwiczenie ma kilka serii
 * i najpierw trzeba wskazać, o którą chodzi.
 */
@Composable
private fun SetsDialog(
    block: StrengthAnalysis.Block,
    unit: String,
    onDismiss: () -> Unit,
    onEdit: (SetEntry) -> Unit,
    onAdd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(block.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Dotknij serii, żeby zmienić ciężar, powtórzenia albo zapas - " +
                        "albo ją usunąć.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                block.sets.forEachIndexed { i, s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(s) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            // Przy zerowym ciężarze same powtórzenia: „0 kg × 12"
                            // przy podciąganiu czyta się jak błąd zapisu.
                            if (s.weightKg > 0.0) "${Loading.fmt(s.weightKg)} $unit × ${s.reps}"
                            else "${s.reps} powt.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            s.rir?.let { "zapas " + if (it >= 3) "3+" else "$it" } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text("Dodaj serię")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Gotowe") } }
    )
}

// ---- Elementy ------------------------------------------------------------

@Composable
private fun StatCard(items: List<Pair<String, String>>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 14.dp)) {
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                value,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Przebieg tętna z zaznaczonymi momentami serii.
 *
 * To jest ta rzecz, której nie zrobi ani sam zegarek, ani sama aplikacja.
 * Zegarek nie wie, kiedy były serie; aplikacja nie wie, co robiło serce.
 */
@Composable
private fun HrWithSets(
    samples: List<RunAnalysis.Sample>,
    setTimes: List<Long>,
    maxHr: Double
) {
    val lo = samples.minOf { it.value }
    val hi = samples.maxOf { it.value }
    val span = (hi - lo).coerceAtLeast(1.0)
    val t0 = samples.first().at
    val tSpan = (samples.last().at - t0).coerceAtLeast(1L).toDouble()

    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        fun x(at: Long) = ((at - t0) / tSpan * size.width).toFloat()
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height * 0.84) - 12).toFloat()

        // Serie najpierw, pod krzywą - mają być tłem, nie bohaterem.
        setTimes.filter { it in t0..samples.last().at }.forEach {
            drawLine(
                Color.White.copy(alpha = 0.16f),
                Offset(x(it), 0f), Offset(x(it), size.height), 2f
            )
        }

        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            drawLine(
                zoneTint(RunAnalysis.zoneOf(a.value, maxHr)),
                Offset(x(a.at), y(a.value)), Offset(x(b.at), y(b.value)), 4f
            )
        }
    }
}

@Composable
private fun ExerciseBlockRow(
    block: StrengthAnalysis.Block,
    maxHr: Double,
    hardest: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(block.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${block.sets.size} × ${block.totalReps / block.sets.size} powt. · " +
                        "${Loading.fmt(block.topWeight)} kg · " +
                        "${block.tonnage.toInt()} kg objętości",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hardest) {
                    Text(
                        "najwyższe tętno w tej sesji",
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneTint(RunAnalysis.zoneOf(block.hrAvg ?: 0.0, maxHr))
                    )
                }
            }
            block.hrAvg?.let { avg ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${avg.toInt()}",
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Monospace,
                        color = zoneTint(RunAnalysis.zoneOf(avg, maxHr))
                    )
                    Text(
                        "tętno",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun zoneTint(zone: Int): Color = when (zone) {
    1 -> Color(0xFF5B8FD9)
    2 -> Color(0xFF4FA3A3)
    3 -> Color(0xFF7FB069)
    4 -> Color(0xFFE0A458)
    else -> Color(0xFFD9654B)
}
