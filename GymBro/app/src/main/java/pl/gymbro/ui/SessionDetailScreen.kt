package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Equipment
import pl.gymbro.data.Exercise
import pl.gymbro.data.ExerciseRepository
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SessionSummary
import pl.gymbro.data.SetEntry
import pl.gymbro.data.stepOrDefault
import pl.gymbro.gym.Loading
import pl.gymbro.gym.ProgressStats
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

/** Serie treningu i wszystko, co z nich wynika - wczytywane razem, po każdej poprawce. */
private class Loaded(
    val sets: List<SetEntry>,
    val exercises: Map<Long, Exercise>,
    /** Serie robocze każdego ćwiczenia z tego treningu, z całej historii. */
    val history: Map<Long, List<SetEntry>>,
    val records: Map<Long, ProgressStats.Point>
)

/**
 * Szczegóły treningu siłowego: Twój zapis złożony z pomiarem zegarka.
 *
 * Od góry to, co najczęściej chce się sprawdzić: liczby całości, propozycje
 * progresji (jedyna rzecz, która czeka na decyzję) i ćwiczenia z każdą serią
 * na wierzchu. Wiersz z „3 × 9 powt." był średnią zaokrągloną w dół
 * i kazał otwierać okienko, żeby zobaczyć, że naprawdę było 10, 9 i 8.
 *
 * Dotknięcie ćwiczenia otwiera ten sam ekran co w podsumowaniu trwającego
 * treningu ([ExerciseReview]) - tabela serii z godziną i odstępem,
 * porównanie z poprzednim razem i poprawianie. Wcześniej historia miała
 * na to osobne okienko i dwa miejsca pokazywały ten sam trening inaczej.
 *
 * Mapy mięśni tu nie ma. Partia ćwiczenia jest wolnym tekstem, więc własne
 * ćwiczenia z „inne" albo „Klata" nie świeciły, a do tego zaniżały skalę
 * pozostałym - rysunek bywał blady albo pusty mimo zrobionego treningu.
 *
 * Wykres tętna z zaznaczonymi seriami stoi na końcu: ciekawy, ale nie
 * pierwszy, po co się tu wchodzi.
 */
@Composable
fun SessionDetailScreen(summary: SessionSummary, onBack: () -> Unit) {
    // Systemowy wstecz robi to samo co strzałka - bez tego wychodził z aplikacji.
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val sessionRepo = remember { SessionRepository(context) }
    val exerciseRepo = remember { ExerciseRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }
    val gymRepo = remember { GymRepository(context) }
    val planRepo = remember { PlanRepository(context) }
    val reader = remember { ActivityDetail(context) }
    val goToTab = LocalGoToTab.current
    val scope = rememberCoroutineScope()

    // Propozycje z tego właśnie treningu. Trafia się na nie tutaj, patrząc,
    // co się zrobiło - i stąd ma być jedno kliknięcie do planu, a nie
    // szukanie po zakładkach.
    var fromHere by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    // Klucz na Refresh: propozycja zastosowana w Planie ma stąd zniknąć,
    // a nie wisieć do ponownego otwarcia treningu.
    LaunchedEffect(summary.sessionId, Refresh.version) {
        fromHere = withContext(Dispatchers.IO) {
            planRepo.itemsWithAdvice().filter { it.advice?.sessionId == summary.sessionId }
        }
    }

    var blocks by remember { mutableStateOf<List<StrengthAnalysis.Block>>(emptyList()) }
    var hr by remember { mutableStateOf<List<RunAnalysis.Sample>>(emptyList()) }
    var setTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var maxHr by remember { mutableStateOf(190.0) }
    var loading by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var exercises by remember { mutableStateOf<Map<Long, Exercise>>(emptyMap()) }
    var history by remember { mutableStateOf<Map<Long, List<SetEntry>>>(emptyMap()) }
    var records by remember { mutableStateOf<Map<Long, ProgressStats.Point>>(emptyMap()) }
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    // Numer, nie blok: po każdej poprawce bloki wczytują się od nowa
    // i trzymany obiekt pokazywałby serie sprzed zmiany.
    var openExercise by remember { mutableStateOf<Long?>(null) }

    /**
     * Serie i wszystko, co z nich wynika. Wołane także po każdej poprawce.
     *
     * Tętna nie czyta się tu ponownie: to kilkaset próbek z Health Connect,
     * a poprawienie liczby powtórzeń niczego w nich nie zmienia.
     */
    suspend fun reloadSets(samples: List<RunAnalysis.Sample>) {
        val loaded = withContext(Dispatchers.IO) {
            val sets = sessionRepo.setsFor(summary.sessionId)
            // Historia każdego ćwiczenia: z niej rekord (po poprawce serii może
            // się pojawić albo zniknąć) i poprzedni raz do porównania.
            val hist = sets.map { it.exerciseId }.distinct().associateWith { sessionRepo.workingSetsOf(it) }
            Loaded(sets, exerciseRepo.all().associateBy { it.id }, hist, ProgressStats.recordsIn(summary.sessionId, hist))
        }
        exercises = loaded.exercises
        history = loaded.history
        records = loaded.records
        blocks = StrengthAnalysis.blocks(loaded.sets, loaded.exercises.mapValues { it.value.name }, samples)
        setTimes = loaded.sets.map { it.doneAt }
    }

    /** Zmiana w seriach: zapis, przeładowanie i sygnał dla listy historii. */
    fun change(action: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            reloadSets(hr)
            // Karta w Historii pokazuje liczbę serii i objętość.
            Refresh.bump()
        }
    }

    LaunchedEffect(summary.sessionId) {
        val profile = withContext(Dispatchers.IO) { profileRepo.get() }
        profile.birthYear?.let { year ->
            val age = java.time.LocalDate.now().year - year
            if (age in 10..100) maxHr = RunAnalysis.maxHrFromAge(age)
        }
        // Tętno z okna treningu zegarka, a bez niego - z okna samego zapisu.
        //
        // Trening prowadzony pilotem nie uruchamia natywnego treningu i zegarek
        // nie ma czego skleić. Pomiar całodobowy leży jednak w Health Connect
        // i obejmuje także te godziny.
        val w = summary.watch
        val samples = if (w != null) reader.load(w.startedAt, w.endedAt).heartRate
        else reader.load(summary.startedAt, summary.endedAt).heartRate
        inventory = withContext(Dispatchers.IO) { gymRepo.inventory() }
        reloadSets(samples)
        hr = samples
        loading = false
    }

    // Liczby z serii, nie z podsumowania, z którym tu weszliśmy: tamto
    // powstało przed poprawką i po zmianie ciężaru pokazywałoby objętość
    // treningu, którego już nie ma. Póki serie się wczytują, zostaje
    // podsumowanie - inaczej kafelki mignęłyby zerem.
    val workingSets = if (loading) summary.workingSets else blocks.sumOf { it.sets.size }
    val tonnage = if (loading) summary.tonnage else blocks.sumOf { it.tonnage }

    // ---- Ćwiczenie otwarte na całym ekranie ----
    val openBlock = blocks.firstOrNull { it.exerciseId == openExercise }
    val inv = inventory
    if (openBlock != null && inv != null) {
        ScreenBackHandler { openExercise = null }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ExerciseReview(
                model = reviewModel(openBlock, exercises[openBlock.exerciseId], summary, history, records),
                inventory = inv,
                onBack = { openExercise = null },
                onAddSet = { w, r ->
                    // Godzina sekundę po ostatniej serii: prawdziwej nikt nie
                    // zna, a ta trzyma kolejność i nie dostawia kreski
                    // w przypadkowym miejscu wykresu tętna.
                    val last = openBlock.sets.last()
                    change {
                        sessionRepo.addSet(
                            last.copy(
                                id = 0,
                                setIndex = openBlock.sets.maxOf { it.setIndex } + 1,
                                weightKg = w,
                                reps = r,
                                rir = null,
                                doneAt = last.doneAt + 1_000
                            )
                        )
                    }
                },
                onDeleteSet = { id -> change { sessionRepo.deleteSet(id) } },
                onUpdateSet = { id, w, r, rir -> change { sessionRepo.updateSet(id, w, r, rir) } }
            )
        }
        return
    }
    // Usunięta ostatnia seria zabiera blok - wtedy wracamy na listę.
    // W efekcie, nie wprost w składaniu: zapis stanu w trakcie rysowania
    // wymusza drugie składanie i potrafi się zapętlić.
    val zniknal = openBlock == null && openExercise != null && !loading
    LaunchedEffect(zniknal) { if (zniknal) openExercise = null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SubScreenHeader(
            title = summary.planName ?: "Trening",
            subtitle = SimpleDateFormat("EEEE, d MMMM, HH:mm", Locale.getDefault()).format(Date(summary.startedAt)),
            onBack = onBack,
            trailing = {
                // Kasowanie w menu, nie przyciskiem na dole ekranu: to rzadka
                // decyzja i nie ma stać na drodze przewijania.
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Usuń trening", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = true }
                        )
                    }
                }
            }
        )

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            val w = summary.watch
            // Bez treningu z zegarka - średnia i szczyt z samych próbek.
            val hrAvg = w?.hrAvg ?: hr.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
            val hrMax = w?.hrMax ?: hr.maxOfOrNull { it.value }
            val tiles = buildList {
                add(sessionLength(summary.durationMin) to "czas")
                add("$workingSets" to "serie")
                // Same ćwiczenia z masą ciała mają zero kilogramów - wtedy
                // więcej mówią powtórzenia.
                if (tonnage > 0.0 || loading) add(tonnageLabel(tonnage) to "objętość")
                else add("${blocks.sumOf { it.totalReps }}" to "powtórzenia")
                hrAvg?.let { add("${it.toInt()}" to "tętno śr.") }
                hrMax?.let { add("${it.toInt()}" to "tętno maks.") }
                w?.energyKcal?.takeIf { it > 0 }?.let { add("${it.toInt()}" to "kcal") }
            }
            tiles.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (value, label) -> StatTile(value, label, null, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (loading) {
                Spacer(Modifier.height(18.dp))
                Text("Wczytuję…", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            // Propozycje zaraz pod liczbami: to jedyna rzecz tutaj, która
            // czeka na decyzję. Każda prowadzi do swojego ćwiczenia w jednostce.
            if (fromHere.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ProposalsCard(
                    unitName = summary.planName,
                    items = fromHere,
                    onOpen = { item ->
                        PlanJump.to(item.planId, item.id)
                        goToTab(PLAN_TAB)
                    }
                )
            }

            GroupLabel("Ćwiczenia")
            if (blocks.isEmpty()) {
                Text(
                    "Ten trening nie ma zapisanych serii.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                blocks.forEach { b ->
                    ExerciseCard(
                        block = b,
                        maxHr = maxHr,
                        record = records[b.exerciseId],
                        onClick = { openExercise = b.exerciseId }
                    )
                }
            }

            if (hr.size >= 4) {
                GroupLabel("Tętno w czasie treningu")
                AppCard {
                    Box(Modifier.padding(12.dp)) {
                        HrWithSets(RunAnalysis.downsample(hr, 180), setTimes, maxHr)
                    }
                }
                // Pomiar całodobowy chodzi co kilka minut, nie co sekundę.
                // Wykres jest wtedy prawdziwy, ale gładki - pojedyncza seria
                // się na nim nie odbije i lepiej to powiedzieć, niż kazać
                // szukać pików.
                val gapMs = (hr.last().at - hr.first().at) / (hr.size - 1).coerceAtLeast(1)
                if (summary.watch == null && gapMs > 60_000) {
                    Text(
                        "Zegarek mierzył tętno co ok. ${(gapMs / 60_000).coerceAtLeast(1)} min, " +
                            "więc pojedynczych serii nie widać - dokładniej będzie, gdy obok " +
                            "pilota włączysz na zegarku trening siłowy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
                    )
                }
            }

            if (hr.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                // Najpierw zgoda: bez niej żadne z poniższych wyjaśnień nie jest
                // prawdą, a to ona była przyczyną przez cały miesiąc po zmianie nazwy.
                HealthAccessBanner()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Brak tętna z czasu tego treningu. Tętno pochodzi z pomiaru " +
                        "całodobowego zegarka - włącz go w Zepp, " +
                        "w ustawieniach monitorowania tętna, najlepiej co minutę.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (deleting) {
        ActionSheet(
            onDismiss = { deleting = false },
            icon = Icons.Filled.DeleteOutline,
            tone = MaterialTheme.colorScheme.error,
            title = "Usunąć trening?",
            text = if (workingSets == 0) "Ten trening nie ma zapisanych serii - zniknie bez śladu."
            else "Zniknie razem z " + plural(workingSets, "serią", "seriami", "seriami") +
                ". Tego nie da się cofnąć.",
            primary = SheetAction("Usuń", danger = true) {
                deleting = false
                scope.launch {
                    withContext(Dispatchers.IO) { sessionRepo.deleteSession(summary.sessionId) }
                    // Historia w zakładce Trening kluczuje na tym sygnale.
                    Refresh.bump()
                    onBack()
                }
            },
            secondary = SheetAction("Wróć") { deleting = false }
        )
    }
}

/**
 * Ekran ćwiczenia z zapisanego treningu.
 *
 * Poprzedni raz po ćwiczeniu, nie po jednostce ([ProgressStats.previousTime]).
 * Ciężar liczy się, gdy sprzęt go ma albo gdy któraś seria go zapisała -
 * zero przy masie ciała to „bez obciążenia", nie pomyłka do poprawienia.
 */
private fun reviewModel(
    block: StrengthAnalysis.Block,
    exercise: Exercise?,
    summary: SessionSummary,
    history: Map<Long, List<SetEntry>>,
    records: Map<Long, ProgressStats.Point>
): ReviewModel {
    val equipment = exercise?.equipment ?: Equipment.MACHINE
    val before = ProgressStats.previousTime(
        history[block.exerciseId].orEmpty(), summary.sessionId, summary.startedAt
    )
    val last = block.sets.last()
    return ReviewModel(
        name = block.name,
        subtitle = plural(block.sets.size, "seria", "serie", "serii") +
            (summary.planName?.let { " · $it" } ?: ""),
        working = block.sets,
        before = before,
        beforeAt = before.firstOrNull()?.doneAt,
        withWeight = equipment != Equipment.BODYWEIGHT || block.sets.any { it.weightKg > 0.0 },
        // Sam szacowany maks. stoi już w kafelku - napis dokłada tylko to,
        // czego tam nie ma: ile było wcześniej.
        recordNote = records[block.exerciseId]?.let {
            "rekord - wcześniej najwyżej ${wholeKg(it.previousBest)} kg szac. maks."
        },
        equipment = equipment,
        step = stepOrDefault(exercise?.stepKg),
        nextWeight = last.weightKg,
        nextReps = last.reps
    )
}

// ---- Elementy ------------------------------------------------------------

/**
 * Szacowany maks. w całych kilogramach, jak w kafelku 1RM: z wzoru Epleya
 * wychodziło „59,22 (było 57,33)" - dokładność, której szacunek nie ma.
 */
private fun wholeKg(v: Double): String = Math.round(v).toString()

/** „1:12 h" zamiast „1 h 12 min" - musi się zmieścić w kafelku. */
private fun sessionLength(min: Long): String = when {
    min < 1 -> "<1 min"
    min < 60 -> "$min min"
    else -> "%d:%02d h".format(min / 60, min % 60)
}

/**
 * Serie w jednej linii: „100×8 · 100×8 · 100×7".
 *
 * Każda osobno, bo średnia gubiła to, co najciekawsze - gdzie seria siadła.
 * Przy samej masie ciała same powtórzenia, z jednym „powt." na końcu.
 */
private fun setsLine(sets: List<SetEntry>): String =
    if (sets.any { it.weightKg > 0.0 }) {
        sets.joinToString(" · ") { s ->
            if (s.weightKg > 0.0) "${Loading.fmt(s.weightKg)}×${s.reps}" else "${s.reps}"
        }
    } else sets.joinToString(" · ") { "${it.reps}" } + " powt."

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
    // Kolor kresek z motywu: biel z przezroczystością znikała na jasnym tle.
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        fun x(at: Long) = ((at - t0) / tSpan * size.width).toFloat()
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height * 0.84) - 12).toFloat()

        // Serie najpierw, pod krzywą - mają być tłem, nie bohaterem.
        setTimes.filter { it in t0..samples.last().at }.forEach {
            drawLine(tick, Offset(x(it), 0f), Offset(x(it), size.height), 2f)
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

/**
 * Karta ćwiczenia: nazwa, każda seria, objętość i tętno.
 *
 * Tętno w kolorze strefy zamiast osobnego podpisu „najwyższe tętno" -
 * najcięższe ćwiczenie i tak widać od razu po kolorze.
 */
@Composable
private fun ExerciseCard(
    block: StrengthAnalysis.Block,
    maxHr: Double,
    record: ProgressStats.Point?,
    onClick: () -> Unit
) {
    AppCard(Modifier.padding(bottom = 8.dp), onClick = onClick) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    block.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Ta sama złota plakietka co na karcie w Historii - tam widać,
                // ile rekordów padło, tu przy którym ćwiczeniu.
                if (record != null) {
                    TagPill("rekord", MaterialTheme.colorScheme.tertiary, Modifier.padding(start = 8.dp))
                }
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Cyfry równej szerokości, ale zwykła czcionka: krój o stałej
            // szerokości rozstrzeliwał „76,8×8 · 76,8×8" spacjami jak w terminalu.
            Text(
                setsLine(block.sets),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(top = 4.dp, end = 6.dp)
            )
            // Która seria i o ile: rekord to szacowany maks. z najlepszej
            // serii (jak na wykresie w Progresie), więc samo „rekord" bez
            // serii kazało zgadywać, czy chodzi o ciężar, czy o powtórzenia.
            if (record != null) {
                Text(
                    "${Loading.fmt(record.bestSet.weightKg)} kg × ${record.bestSet.reps} - " +
                        "szac. maks. ${wholeKg(record.e1rm)} kg (było ${wholeKg(record.previousBest)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (block.tonnage > 0.0 || block.hrAvg != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (block.tonnage > 0.0) tonnageLabel(block.tonnage) + " objętości" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    block.hrAvg?.let { avg ->
                        Text(
                            "${avg.toInt()} bpm",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = zoneTint(RunAnalysis.zoneOf(avg, maxHr))
                        )
                    }
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

/**
 * Propozycje progresji po tym treningu - każda osobno, z „było → będzie".
 *
 * Dotąd jeden niebieski prostokąt z trzema zdaniami drobnym drukiem i całość
 * prowadziła do pierwszego ćwiczenia. Teraz wiersz to ćwiczenie i dotknięcie
 * otwiera jednostkę właśnie na nim.
 */
@Composable
private fun ProposalsCard(unitName: String?, items: List<PlanItem>, onOpen: (PlanItem) -> Unit) {
    AppCard(accent = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                plural(items.size, "propozycja", "propozycje", "propozycji") + " progresji",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Wyliczone z tego treningu. Dotknij ćwiczenia, żeby zdecydować w jednostce" +
                    (unitName?.let { " $it" } ?: "") + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            items.forEach { item ->
                val a = item.advice ?: return@forEach
                val change = adviceChange(item, a)
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                        .tap(shape) { onOpen(item) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.exerciseName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            adviceTitle(a) + " · " +
                                (change?.let { it.first + " → " + it.second } ?: adviceText(item, a)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (a.kind == "WEIGHT_DOWN") MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Otwórz w planie",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
