package pl.gymbro.ui

import pl.gymbro.ui.theme.ButtonShape
import pl.gymbro.data.SessionSummary
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.ProfileRepository
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.gym.ProgressionAdvisor
import pl.gymbro.gym.WatchMatch
import pl.gymbro.gym.Timeline
import pl.gymbro.data.HealthRepository
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import pl.gymbro.data.Activity
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Cache
import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.Program
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.Session
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SetEntry
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.net.WatchLinkService
import pl.gymbro.gym.LiveSession
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator
import pl.gymbro.gym.ProgressionEngine

/**
 * Ekran logowania treningu.
 *
 * Zasada porządkująca cały układ: **jedna rzecz naraz**. W danym momencie
 * wykonujesz jedno ćwiczenie, więc tylko ono jest rozwinięte i zajmuje ekran.
 * Pozostałe to jednolinijkowe wiersze z samą nazwą i postępem - wystarczy,
 * żeby wiedzieć, co zostało, i nie odciąga uwagi od tego, co robisz teraz.
 *
 * Rzeczy drugorzędne (RIR, usuwanie serii) są schowane, dopóki ich nie potrzebujesz.
 */

// Stan ćwiczeń liczy wspólny LiveSession - ten sam, z którego korzysta pilot
// na zegarku. Alias, żeby nie przepisywać dziewięciu użyć w tym pliku.
private typealias ExerciseState = LiveSession.ExerciseState

@Composable
fun WorkoutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val planRepo = remember { PlanRepository(context) }
    val sessionRepo = remember { SessionRepository(context) }
    val healthRepo = remember { HealthRepository(context) }
    val gymRepo = remember { GymRepository(context) }
    val programRepo = remember { ProgramRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }

    // Stan początkowy bierzemy z pamięci podręcznej, jeśli coś w niej jest.
    // Dzięki temu powrót do zakładki pokazuje poprzednią zawartość od razu,
    // zamiast migać napisem „Wczytuję…" przy każdym przełączeniu.
    var planOptions by remember {
        mutableStateOf(Cache.peek<List<PlanOption>>(Cache.PLAN_OPTIONS) ?: emptyList())
    }
    var activeProgram by remember {
        mutableStateOf(Cache.peek<Program>(Cache.ACTIVE_PROGRAM))
    }
    var inventory by remember {
        mutableStateOf(Cache.peek<Inventory>(Cache.INVENTORY))
    }
    var loading by remember { mutableStateOf(inventory == null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var states by remember { mutableStateOf<List<ExerciseState>>(emptyList()) }
    var activeIndex by remember { mutableStateOf(0) }
    var showProgress by rememberSaveable { mutableStateOf(false) }
    // Cała sesja, nie sam identyfikator: po odtworzeniu aktywności historia
    // jeszcze się wczytuje, więc nie byłoby w czym szukać.
    // Ktore treningi zostawily propozycje - do kropek na liscie historii.
    var adviceSessions by remember { mutableStateOf(emptySet<Long>()) }
    var ownDetail by rememberSaveable(stateSaver = SessionSummarySaver) {
        mutableStateOf<SessionSummary?>(null)
    }
    var detailOf by rememberSaveable(stateSaver = ExternalSessionSaver) {
        mutableStateOf<ExternalSession?>(null)
    }

    // Ekran świeci tylko w trakcie treningu i tylko na tej zakładce.
    //
    // Wcześniej efekt był kluczowany na Unit, a zakładki żyją w tle przez
    // cały czas działania aplikacji - więc ekran nie gasł nigdy: ani przy
    // wpisywaniu jedzenia, ani z telefonem odłożonym na stół bez żadnego
    // treningu. Flaga jest jedna na całe okno, stąd warunek na widoczność.
    val view = LocalView.current
    val tabShown = LocalTabShown.current
    val sessionRunning = session != null
    DisposableEffect(sessionRunning, tabShown) {
        view.keepScreenOn = sessionRunning && tabShown
        onDispose { view.keepScreenOn = false }
    }

    suspend fun reload(s: Session, inv: Inventory) {
        if (s.planId == null) return
        val loaded = withContext(Dispatchers.IO) {
            LiveSession.load(planRepo, sessionRepo, s, inv)
        }
        states = loaded
        activeIndex = LiveSession.firstUnfinished(loaded)
    }

    /**
     * Przeładowuje listę jednostek razem z datą ostatniego wykonania.
     *
     * Wywoływana także po zakończeniu treningu - bez tego podpowiedź „następny
     * w kolejce" opierałaby się na stanie sprzed sesji i wskazywała jednostkę,
     * którą przed chwilą skończyłeś.
     */
    suspend fun reloadPlanOptions() {
        val result = withContext(Dispatchers.IO) {
            // Ekran treningu pokazuje jednostki AKTYWNEGO programu. Bez tego
            // przy dwóch schematach naraz (np. FBW i PPL) wszystkie jednostki
            // leżałyby w jednej liście i rotacja przestałaby cokolwiek znaczyć.
            val program = programRepo.active()
            val plans = if (program != null) planRepo.plansOf(program.id) else emptyList()
            // Dwa zapytania zbiorcze zamiast dwóch na każdą jednostkę.
            val counts = sessionRepo.itemCounts()
            val lastDone = sessionRepo.lastFinishedAll()
            program to plans.map { plan ->
                PlanOption(
                    plan = plan,
                    exerciseCount = counts[plan.id] ?: 0,
                    lastDoneAt = lastDone[plan.id]
                )
            }
        }
        activeProgram = Cache.put(Cache.ACTIVE_PROGRAM, result.first)
        planOptions = Cache.put(Cache.PLAN_OPTIONS, result.second)
    }

    // Naciskiem w otwarta zakladke wracamy do glownego widoku treningu.
    TabReselectHandler {
        detailOf = null
        ownDetail = null
        showProgress = false
    }

    LaunchedEffect(Unit) {
        Busy.track {
            // Jeden skok na wątek roboczy zamiast trzech. Każdy kosztuje przełączenie
            // kontekstu, a przy szybkim przeskakiwaniu między zakładkami to się sumuje.
            val loadedInv = withContext(Dispatchers.IO) { gymRepo.inventory() }
            inventory = Cache.put(Cache.INVENTORY, loadedInv)
            reloadPlanOptions()
            val active = withContext(Dispatchers.IO) { sessionRepo.active() }
            session = active
            if (active != null) reload(active, loadedInv)
            adviceSessions = withContext(Dispatchers.IO) {
                planRepo.itemsWithAdvice().mapNotNull { it.advice?.sessionId }.toSet()
            }
            // Trening zamykany na zegarku nie przechodzi przez przycisk
            // w aplikacji, więc propozycji nie miał kto policzyć - a to
            // najczęstszy sposób kończenia. Szukamy ich więc także przy
            // wejściu na ekran, dla ostatnio zamkniętej sesji.
            loading = false
        }
    }


    // Plan edytuje się w innej zakładce, a ta żyje w tle i sama się nie
    // przeliczy - bez tego Trening pokazywałby skasowane jednostki i starą
    // nazwę programu. Pierwsze złożenie pomijamy, bo robi to wczytywanie wyżej.
    var seenRefresh by remember { mutableIntStateOf(Refresh.version) }
    LaunchedEffect(Refresh.version) {
        if (Refresh.version == seenRefresh) return@LaunchedEffect
        seenRefresh = Refresh.version
        reloadPlanOptions()
        // Trwający trening mógł zostać skasowany gdzie indziej (czyszczenie
        // historii). Bez tego dopisywalibyśmy serie do sesji, której już nie ma.
        // Czytamy sesję na nowo: mogła zostać wstrzymana z zegarka albo
        // skasowana gdzie indziej. Null oznacza, że już jej nie ma.
        if (session != null) {
            session = withContext(Dispatchers.IO) { sessionRepo.active() }
        }
        // Seria mogła przyjść z zegarka. Bez tego ekran telefonu pokazywałby
        // stan sprzed niej, mimo że w bazie już jest.
        val live = session
        val inv2 = inventory
        if (live != null && inv2 != null) reload(live, inv2)
    }

    // Pilot na zegarku: stan usługi uzgadniamy przy każdej zmianie sesji.
    //
    // Przy ustawieniu domyślnym pilot stoi cały czas i to wywołanie niczego
    // nie zmienia; liczy się, gdy w Profilu wyłączono pracę poza treningiem -
    // wtedy usługa wstaje z sesją i gaśnie razem z nią. Nasłuch przekłada
    // zapis z zegarka na odświeżenie ekranu - serwer woła z własnego wątku,
    // a Refresh to stan Compose, więc musi to trafić na wątek główny.
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    DisposableEffect(session?.id) {
        WatchLinkService.sync(context, sessionActive = session != null)
        val off = WatchLinkService.Changes.listen { reason ->
            mainHandler.post {
                if (reason == "set") ExternalSet.bump()
                Refresh.bump()
            }
        }
        onDispose { off() }
    }

    // Historia wczytywana osobno od reszty ekranu.
    //
    // Klucz na Refresh.version, bo zakładki żyją w tle: bez tego bieg
    // dociągnięty z zegarka po starcie aplikacji nie pojawiłby się tu nigdy.
    // Klucz na session, bo po zakończeniu treningu dochodzi nowa pozycja.
    var history by remember {
        mutableStateOf(Timeline.Split(emptyList(), emptyList(), emptyList()))
    }
    LaunchedEffect(Refresh.version, session) {
        history = withContext(Dispatchers.IO) {
            val watch = healthRepo.sessions(limit = 200)
            Timeline.build(
                strength = sessionRepo.historySummaries(20).map { s ->
                    s.copy(watch = WatchMatch.bestMatch(s.startedAt, s.endedAt, watch))
                },
                watch = watch
            )
        }
    }
    val inv = inventory

    val own = ownDetail
    if (own != null) {
        SessionDetailScreen(summary = own, onBack = { ownDetail = null })
        return
    }

    val opened = detailOf
    if (opened != null) {
        ActivityDetailScreen(session = opened, onBack = { detailOf = null })
        return
    }

    if (showProgress) {
        ProgressScreen(onBack = { showProgress = false })
        return
    }

    // Jeden odczyt na całe składanie. `session` to stan, który zegarek potrafi
    // wyzerować w każdej chwili (FINISH z pilota), więc `session!!` w gałęzi
    // niżej i w przyciskach było awarią czekającą na zbieg okoliczności.
    val current = session
    CompositionLocalProvider(LocalAdviceSessions provides adviceSessions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        when {
            loading -> Text("Wczytuję…", Modifier.padding(top = 16.dp))

            inv == null -> Text("Brak danych o sprzęcie.", Modifier.padding(top = 16.dp))

            current == null -> PlanPicker(
                programName = activeProgram?.name,
                options = planOptions,
                onProgress = { showProgress = true },
                history = history,
                onOpen = { picked ->
                    when (picked) {
                        is Activity.Watch -> detailOf = picked.session
                        is Activity.Strength -> ownDetail = picked.summary
                    }
                }
            ) { plan ->
                scope.launch {
                    val id = withContext(Dispatchers.IO) { sessionRepo.start(plan.id) }
                    val s = Session(id = id, planId = plan.id, startedAt = System.currentTimeMillis())
                    session = s
                    reload(s, inv)
                }
            }

            else -> ActiveSession(
                session = current,
                states = states,
                activeIndex = activeIndex,
                inventory = inv,
                onSelect = { activeIndex = it },
                onSaveSet = { st, w, r, rir ->
                    scope.launch {
                        // Trening mógł się skończyć na zegarku między dotknięciem
                        // a tym miejscem - wtedy nie ma do czego dopisać serii.
                        val s = session ?: return@launch
                        withContext(Dispatchers.IO) {
                            sessionRepo.addSet(
                                SetEntry(
                                    sessionId = s.id,
                                    exerciseId = st.item.exerciseId,
                                    setIndex = st.doneSets.size + 1,
                                    weightKg = w,
                                    reps = r,
                                    rir = rir,
                                    doneAt = System.currentTimeMillis()
                                )
                            )
                        }
                        Haptics.tick(context)
                        reload(s, inv)
                    }
                },
                onDeleteSet = { id ->
                    scope.launch {
                        val s = session ?: return@launch
                        withContext(Dispatchers.IO) { sessionRepo.deleteSet(id) }
                        reload(s, inv)
                    }
                },
                onUpdateSet = { id, w, r, rir ->
                    scope.launch {
                        val s = session ?: return@launch
                        withContext(Dispatchers.IO) { sessionRepo.updateSet(id, w, r, rir) }
                        reload(s, inv)
                    }
                },
                onFinish = {
                    scope.launch {
                        val done = session ?: return@launch
                        // Propozycje liczymy przed zamknięciem sesji, bo potem
                        // nie ma już czego czytać na ekranie.
                        withContext(Dispatchers.IO) {
                            // Propozycje lądują przy pozycjach planu;
                            // zakładka Plan pokaże plakietkę.
                            ProgressionReview.saveFor(
                                planRepo, sessionRepo, profileRepo, gymRepo, done
                            )
                            sessionRepo.finish(done.id)
                        }
                        session = null
                        states = emptyList()
                        // Dopiero teraz jednostka ma datę zakończenia, więc kolejka
                        // musi się przeliczyć - inaczej lista pokaże stan sprzed sesji.
                        reloadPlanOptions()
                    }
                }
            )
        }
    }
    }
}

// ---- Wybór planu ---------------------------------------------------------

/** Jednostka treningowa razem z kontekstem potrzebnym do wyboru. */
private data class PlanOption(
    val plan: Plan,
    val exerciseCount: Int,
    val lastDoneAt: Long?
)

/** Która z trzech zakładek Treningu jest otwarta. */
private enum class Stream(val label: String) {
    STRENGTH("Siłowy"), CARDIO("Kardio"), OTHER("Inne")
}

@Composable
private fun PlanPicker(
    programName: String?,
    options: List<PlanOption>,
    onProgress: () -> Unit,
    history: Timeline.Split,
    onOpen: (Activity) -> Unit,
    // Ostatni parametr, żeby dało się go przekazać jako lambdę końcową.
    onStart: (Plan) -> Unit
) {
    var confirming by remember { mutableStateOf<PlanOption?>(null) }
    var stream by rememberSaveable { mutableStateOf(Stream.STRENGTH) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Trening", style = MaterialTheme.typography.headlineLarge)
                Text(
                    programName ?: "Wybierz jednostkę",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Postęp czyta się między treningami, a nie w ich trakcie - stąd wejście
            // na ekranie wyboru, a nie w nawigacji głównej.
            OutlinedButton(onClick = onProgress) { Text("Postęp") }
        }

        // Trzy strumienie, bo siła działa inaczej niż reszta: ciężary wpisujesz,
        // biegi i resztę zegarek przynosi sam. Mieszanie ich na jednej liście
        // kazało za każdym razem oddzielać wzrokiem to, co robisz, od tego,
        // co się samo zapisało.
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stream.entries.forEach { s ->
                val count = when (s) {
                    Stream.STRENGTH -> history.strength.size
                    Stream.CARDIO -> history.cardio.size
                    Stream.OTHER -> history.other.size
                }
                StreamChip(
                    label = s.label,
                    count = count,
                    active = s == stream,
                    onClick = { stream = s }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when (stream) {
            Stream.STRENGTH -> {
                if (options.isEmpty()) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                if (programName == null) "Nie masz jeszcze programu"
                                else "Program jest pusty",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (programName == null)
                                    "Przejdź do zakładki Plan i utwórz program - na przykład " +
                                        "FBW z jednostkami A, B i C albo PPL z Push, Pull i Legs."
                                else
                                    "Program " + programName + " nie ma jeszcze jednostek. " +
                                        "Dodaj je w zakładce Plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Przy rotacji A/B/C najdawniej robiona jednostka jest następna
                    // w kolejce. Nigdy nierobiona wyprzedza wszystkie.
                    val suggested = options.minByOrNull { it.lastDoneAt ?: 0L }?.plan?.id
                    options.forEach { option ->
                        PlanCard(
                            option = option,
                            suggested = option.plan.id == suggested,
                            onClick = { confirming = option }
                        )
                    }
                }

                HistorySection(
                    title = "Ostatnio",
                    empty = "Pusto. Po pierwszym zapisanym treningu pojawi się tu historia.",
                    items = history.strength,
                    onOpen = onOpen
                )
            }

            Stream.CARDIO -> HistorySection(
                title = "Bieganie i chodzenie",
                empty = "Pusto. Biegi, spacery i wycieczki pojawią się same po " +
                    "synchronizacji z zegarkiem - nic nie musisz wpisywać.",
                items = history.cardio,
                onOpen = onOpen
            )

            Stream.OTHER -> HistorySection(
                title = "Pozostałe aktywności",
                empty = "Pusto. Trafi tu wszystko, co nagrasz zegarkiem poza siłownią " +
                    "i ruchem w terenie - na przykład trening dowolny czy joga.",
                items = history.other,
                onOpen = onOpen
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // Potwierdzenie startu.
    //
    // Karta jednostki jest duża i leży tam, gdzie się przewija - dotknięcie
    // przez pomyłkę zaczynało trening i uruchamiało licznik. Cofnięcie tego
    // wymagało zakończenia pustej sesji, czyli śmiecia w historii. Jedno
    // pytanie kosztuje dotknięcie, pomyłka kosztowała wpis.
    val toConfirm = confirming
    if (toConfirm != null) {
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Zacząć " + toConfirm.plan.name + "?") },
            text = {
                Text(
                    toConfirm.exerciseCount.toString() + " ćwiczeń · " +
                        lastDoneLabel(toConfirm.lastDoneAt) + "\n\n" +
                        "Licznik czasu ruszy od razu."
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirming = null
                    onStart(toConfirm.plan)
                }) { Text("Zacznij") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Anuluj") }
            }
        )
    }
}

/** Przełącznik strumienia z licznikiem - od razu widać, gdzie coś jest. */
@Composable
private fun StreamChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            if (count > 0) label + "  " + count else label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun HistorySection(
    title: String,
    empty: String,
    items: List<Activity>,
    onOpen: (Activity) -> Unit
) {
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    if (items.isEmpty()) {
        Text(
            empty,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        items.forEach { ActivityRow(it, onOpen) }
    }
}
/**
 * Czas trwania treningu.
 *
 * Zero minut byłoby mylące: sesja z dwudziestoma seriami zapisana w pół minuty
 * to ślad po przeklikaniu aplikacji, a nie trening, który trwał zero. Podajemy
 * więc, że trwała krócej niż minutę, zamiast twierdzić, że nie trwała wcale.
 */
private fun durationLabel(min: Long): String = if (min < 1) "<1 min" else "$min min"

/** Tempo jako minuty i sekundy, bo 5,5 min/km czyta się gorzej niż 5:30. */
private fun paceLabel(minPerKm: Double): String {
    val total = (minPerKm * 60).toInt()
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

/**
 * Statystyki aktywności z zegarka.
 *
 * Pokazujemy tylko to, co naprawdę przyszło. Zegarek wypełnia różne pola
 * zależnie od typu treningu - bieg ma dystans i tempo, trening dowolny zwykle
 * samo tętno. Wypisywanie pustych rubryk z kreską sugerowałoby, że czegoś
 * brakuje, podczas gdy po prostu nie ma czego mierzyć.
 */
private fun watchStats(s: pl.gymbro.data.ExternalSession): String =
    listOfNotNull(
        s.distanceM?.takeIf { it > 0 }
            ?.let { String.format(Locale.getDefault(), "%.2f km", it / 1000.0) },
        s.paceMinPerKm?.let { paceLabel(it) + " /km" },
        s.hrAvg?.toInt()?.let { avg ->
            val max = s.hrMax?.toInt()
            if (max != null && max > avg) "tętno $avg/$max" else "tętno $avg"
        },
        s.energyKcal?.takeIf { it > 0 }?.let { "${it.toInt()} kcal" }
    ).joinToString(" · ")

/** Jedna pozycja historii - Twój zapis albo aktywność z zegarka. */
@Composable
private fun ActivityRow(item: Activity, onOpen: (Activity) -> Unit) {
    val adviceSessions = LocalAdviceSessions.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                // Każdy wpis ma teraz co pokazać: aktywność z zegarka swoje
                // pomiary, własny trening - ćwiczenia złożone z tętnem.
                Modifier.clickable { onOpen(item) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                when (item) {
                    is Activity.Strength -> {
                        val s = item.summary
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.planName ?: "Trening",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Kropka przy treningu, po ktorym cos czeka -
                            // inaczej trzeba wchodzic w kazdy po kolei,
                            // zeby sprawdzic, czy cos z niego wynikło.
                            if (adviceSessions.contains(s.sessionId)) {
                                Badge(Modifier.padding(start = 6.dp))
                            }
                        }
                        Text(
                            buildString {
                                append(s.workingSets.toString() + " serii")
                                if (s.tonnage > 0) append(" · " + s.tonnage.toInt() + " kg")
                                s.watch?.hrAvg?.toInt()?.let { append(" · tętno " + it) }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    is Activity.Watch -> {
                        val w = item.session
                        Text(
                            w.type,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val stats = watchStats(w)
                        Text(
                            // Trening siłowy, który trafił tutaj, nie został
                            // przez Ciebie zapisany - warto to powiedzieć wprost,
                            // bo inaczej wygląda jak zgubione serie.
                            if (stats.isNotEmpty()) stats else "bez szczegółów",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (Timeline.isStrength(w)) {
                            Text(
                                "z zegarka, bez zapisanych serii",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    durationLabel(
                        when (item) {
                            is Activity.Strength -> item.summary.durationMin
                            is Activity.Watch -> item.session.durationMin
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(item.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun PlanCard(option: PlanOption, suggested: Boolean, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (suggested) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (suggested) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${option.exerciseCount} ćwiczeń · ${lastDoneLabel(option.lastDoneAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (suggested) {
                    Text(
                        "następny w kolejce",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text("▶", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Ile dni temu - czytelniejsze niż data przy rotacji co kilka dni. */
private fun lastDoneLabel(ms: Long?): String {
    if (ms == null) return "jeszcze nierobiony"
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when (days) {
        0 -> "dzisiaj"
        1 -> "wczoraj"
        in 2..6 -> "$days dni temu"
        in 7..13 -> "tydzień temu"
        else -> "$days dni temu"
    }
}

// ---- Trwająca sesja ------------------------------------------------------

@Composable
private fun ColumnScope.ActiveSession(
    session: Session,
    states: List<ExerciseState>,
    activeIndex: Int,
    inventory: Inventory,
    onSelect: (Int) -> Unit,
    onSaveSet: (ExerciseState, Double, Int, Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit,
    onFinish: () -> Unit
) {

    // Zegar sesji trzyma MINUTY, nie milisekundy.
    //
    // Wcześniej tykał co sekundę i każde tyknięcie przerysowywało cały ekran
    // razem z listą ćwiczeń - sześćdziesiąt przebudów na minutę, z czego
    // pięćdziesiąt dziewięć niczego nie zmieniało na ekranie. Teraz wartość
    // zmienia się raz na minutę i tylko wtedy wywołuje rekompozycję.
    var elapsedMin by remember { mutableStateOf(0L) }
    LaunchedEffect(session.id) {
        while (true) {
            val m = session.elapsedMs() / 60_000
            if (m != elapsedMin) elapsedMin = m
            delay(5_000)
        }
    }

    // Odliczanie przerwy żyje w pasku przerwy, a nie tutaj - tamten musi tykać
    // co sekundę, ale przerysowuje wtedy tylko siebie.
    var restEndsAt by remember { mutableStateOf<Long?>(null) }
    var restTotalSec by remember { mutableStateOf(0) }
    // Ciężar ostatnio zapisanej serii - to on wraca na sztangę po przerwie.
    var restWeight by remember { mutableStateOf<Double?>(null) }

    val done = states.count { it.complete }
    val allComplete = states.isNotEmpty() && states.all { it.complete }

    // Po ukończeniu ostatniej serii ekran sam przechodzi do podsumowania.
    // Zostawienie użytkownika na karcie ćwiczenia zapraszało do dokładania serii
    // w nieskończoność, bo nic nie sygnalizowało, że trening się skończył.
    var dismissedSummary by remember { mutableStateOf(false) }
    LaunchedEffect(allComplete) { if (!allComplete) dismissedSummary = false }

    // Seria z zegarka znaczy, że trening trwa dalej - schodzimy z podsumowania,
    // żeby telefon pokazywał to samo co zegarek, a nie liczby sprzed niej.
    var seenExternal by remember { mutableIntStateOf(ExternalSet.version) }
    LaunchedEffect(ExternalSet.version) {
        if (ExternalSet.version != seenExternal) {
            seenExternal = ExternalSet.version
            dismissedSummary = true
        }
    }

    // "Zakończ" w nagłówku prowadzi do podsumowania, a nie zamyka sesji od razu.
    // Zamknięcie jest nieodwracalne, więc ostatnie dotknięcie ma paść na ekranie,
    // który pokazuje, co właściwie kończysz.
    var requestedFinish by remember { mutableStateOf(false) }
    val showSummary = requestedFinish || (allComplete && !dismissedSummary)

    // ---- Wykrycie świeżo ukończonego ćwiczenia ----
    // Porównujemy zbiory ukończonych, a nie samą liczbę: dzięki temu wiemy, KTÓRE
    // ćwiczenie się domknęło, i nie fałszujemy sygnału po skasowaniu serii.
    // Flaga `initialized` chroni przed pokazaniem potwierdzenia dla wszystkiego,
    // co było ukończone już przy wejściu na ekran (np. po wznowieniu sesji).
    var justCompleted by remember { mutableStateOf<String?>(null) }
    var completedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(states) {
        val nowComplete = states.filter { it.complete }.map { it.item.id }.toSet()
        val newly = if (initialized) (nowComplete - completedIds).firstOrNull() else null
        completedIds = nowComplete
        initialized = true
        if (newly != null && !allComplete) {
            justCompleted = states.firstOrNull { it.item.id == newly }?.item?.exerciseName
        }
    }

    // Osobny efekt na zgaszenie: kluczowany wartością, więc zadziała zawsze,
    // nawet gdy poprzedni efekt zostanie przerwany przeładowaniem stanu.
    LaunchedEffect(justCompleted) {
        if (justCompleted != null) {
            delay(1600)
            justCompleted = null
        }
    }

    // Podsumowanie jest ekranem samodzielnym - nagłówek z przyciskiem "Zakończ"
    // dublowałby tam główną akcję, a czas sesji i tak jest jedną z kafelek.
    if (showSummary) {
        SessionDone(
            states = states,
            elapsedMin = elapsedMin,
            allComplete = allComplete,
            onFinish = onFinish,
            onBack = {
                dismissedSummary = true
                requestedFinish = false
                // Przerwa po ostatniej serii zdążyła już minąć, zanim obejrzałeś
                // podsumowanie. Wracając do treningu zaczynasz od nowa, a nie
                // od odliczania, które wisiało w tle.
                restEndsAt = null
            }
        )
        return
    }

    // ---- Nagłówek: czas sesji i postęp ----
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "$elapsedMin min",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "$done z ${states.size} ćwiczeń",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = { requestedFinish = true }) { Text("Zakończ") }
    }

    // Lista sama przewija się do aktywnego ćwiczenia - po zapisaniu ostatniej
    // serii uwaga przenosi się na kolejne bez szukania go wzrokiem.
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex in states.indices) listState.animateScrollToItem(activeIndex)
    }

    // Komunikaty leżą NA liście, a nie nad nią.
    //
    // Wcześniej baner przerwy wchodził w układ pionowy i spychał całą zawartość
    // w dół - ekran skakał dokładnie w chwili, gdy patrzyłeś na zapisaną serię.
    // Jako nakładka przy dolnej krawędzi nie rusza niczego, a przy okazji trafia
    // pod kciuk, więc "Pomiń" jest w zasięgu bez sięgania w górę ekranu.
    Box(Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // Zapas, żeby ostatnia pozycja nie chowała się pod nakładką.
            contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp)
        ) {
            itemsIndexed(states, key = { _, s -> s.item.id }) { index, st ->
                if (index == activeIndex) {
                    ActiveExercise(
                        state = st,
                        inventory = inventory,
                        onSave = { w, r, rir ->
                            onSaveSet(st, w, r, rir)
                            // Bez ustawionej przerwy nie odpalamy odliczania -
                            // okno z zerem na liczniku nie jest informacją.
                            if (st.item.hasRest) {
                                restTotalSec = st.item.restSec
                                restWeight = w
                                restEndsAt = System.currentTimeMillis() + st.item.restSec * 1000L
                            }
                        },
                        onDeleteSet = onDeleteSet,
                        onUpdateSet = onUpdateSet
                    )
                } else {
                    CollapsedExercise(st) { onSelect(index) }
                }
            }
        }

        // Potwierdzenie ukończenia ćwiczenia zostaje nakładką: trwa 1,6 sekundy
        // i nie wymaga reakcji, więc okno byłoby tu nachalne.
        DoneOverlay(
            exerciseName = justCompleted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )

        // Przerwa jako pasek przy dolnej krawędzi, nie okno.
        //
        // Okno zasłaniało kartę ćwiczenia, a wtedy w trakcie przerwy nie dało
        // się sprawdzić, ile nałożyć na następną serię - czyli dokładnie tego,
        // po co ta przerwa jest. Pasek zostawia kartę widoczną i sam powtarza
        // ciężar z rozkładem talerzy, bo lista bywa przewinięta.
        //
        // Pokazuje się dopiero po zniknięciu potwierdzenia, żeby jedno nie
        // przykrywało drugiego.
        if (restEndsAt != null && justCompleted == null) {
            RestBar(
                endsAt = restEndsAt!!,
                totalSec = restTotalSec,
                item = states.getOrNull(activeIndex)?.item,
                nextWeight = restWeight,
                inventory = inventory,
                onFinished = { restEndsAt = null },
                onSkip = { restEndsAt = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Przerwa jako wąski pasek nad dolną krawędzią.
 *
 * Była tu wcześniej jako okno na pół ekranu. Wyglądało to dobrze, ale zasłaniało
 * kartę ćwiczenia - a w trakcie przerwy potrzebujesz dokładnie jednej rzeczy,
 * której tam nie było widać: ile nałożyć na następną serię. Pasek niczego nie
 * zakrywa i sam podaje ciężar z rozkładem talerzy, bo lista bywa przewinięta
 * na inne ćwiczenie.
 *
 * Pierścień zostaje, tylko mały: z odległości czyta się go jako „ile zostało"
 * bez czytania cyfr.
 */
@Composable
private fun RestBar(
    endsAt: Long,
    totalSec: Int,
    item: PlanItem?,
    nextWeight: Double?,
    inventory: Inventory,
    onFinished: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Odliczanie tyka tutaj, a nie w ekranie nadrzędnym. Dzięki temu co sekundę
    // przerysowuje się sam pasek, a nie cała lista ćwiczeń pod spodem.
    var leftSec by remember(endsAt) {
        mutableStateOf(((endsAt - System.currentTimeMillis()) / 1000).toInt())
    }
    LaunchedEffect(endsAt) {
        while (true) {
            val left = ((endsAt - System.currentTimeMillis()) / 1000).toInt()
            if (left != leftSec) leftSec = left
            if (left <= 0) {
                Haptics.restOver(context)
                onFinished()
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    val fraction = if (totalSec > 0) (leftSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(900),
        label = "przerwa"
    )
    val ringColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    val mode = item?.let { LoadMode.of(it.equipment, inventory) }
    val plates = if (item != null && item.hasTargetWeight && nextWeight != null && mode != null) {
        PlateCalculator.nearest(nextWeight, mode, inventory)?.describe(mode, inventory)
    } else null

    Card(
        modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(
                        size.width - stroke,
                        size.height - stroke
                    )
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (animated > 0f) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animated,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    "%d:%02d".format(leftSec / 60, leftSec % 60),
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    if (nextWeight != null && item?.hasTargetWeight == true)
                        "Następnie ${Loading.fmt(nextWeight)} ${item.equipment.weightUnitLabel}"
                    else "Przerwa",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                plates?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 2
                    )
                }
            }

            TextButton(onClick = onSkip) { Text("Pomiń") }
        }
    }
}

// ---- Ćwiczenie zwinięte --------------------------------------------------

/**
 * Jedna linia: nazwa i postęp. Nic więcej - ćwiczenie, którego teraz nie robisz,
 * nie potrzebuje ani historii, ani propozycji ciężaru.
 */
@Composable
private fun CollapsedExercise(state: ExerciseState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            state.item.exerciseName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.complete) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (state.complete) "✓" else "${state.doneSets.size}/${state.item.sets}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.complete) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---- Ćwiczenie aktywne ---------------------------------------------------

@Composable
private fun ActiveExercise(
    state: ExerciseState,
    inventory: Inventory,
    onSave: (Double, Int, Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit
) {
    val item = state.item
    val mode = LoadMode.of(item.equipment, inventory)

    var weight by remember(item.id, state.doneSets.size) {
        mutableStateOf(state.doneSets.lastOrNull()?.weightKg ?: state.suggestion.weightKg)
    }
    var reps by remember(item.id, state.doneSets.size) {
        mutableStateOf(state.doneSets.lastOrNull()?.reps ?: state.suggestion.reps)
    }
    var rir by remember(item.id, state.doneSets.size) { mutableStateOf<Int?>(null) }
    var showRir by remember { mutableStateOf(false) }
    var typingWeight by remember { mutableStateOf(false) }

    if (typingWeight) {
        WeightDialog(
            initialKg = weight,
            title = item.exerciseName,
            onDismiss = { typingWeight = false },
            onPick = { weight = it; typingWeight = false }
        )
    }
    // Dotknięcie zapisanej serii otwiera jej poprawianie. Wcześniej kasowało
    // ją od razu, bez pytania - pomyłka o jedno powtórzenie kosztowała całą serię.
    var editingSet by remember { mutableStateOf<SetEntry?>(null) }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {

            // ---- Nagłówek ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${state.doneSets.size}/${item.sets}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Jedna linia kontekstu zamiast trzech: skąd ten ciężar i co było ostatnio.
            if (state.doneSets.isEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    suggestionLine(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.suggestion.reason == ProgressionEngine.Reason.DELOAD ||
                        state.suggestion.reason == ProgressionEngine.Reason.STALLED
                    ) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Ciężar ----
            // Przy ćwiczeniu bez zadanego obciążenia regulator w ogóle się nie
            // pokazuje: seria zapisze się z zerem, bo ciężar nie jest tu informacją.
            if (item.hasTargetWeight) {
                Stepper(
                    label = "${Loading.fmt(weight)} ${item.equipment.weightUnitLabel}",
                    onMinus = {
                        weight = if (mode != null) {
                            PlateCalculator.nextDown(weight, mode, inventory)?.totalKg ?: weight
                        } else roundKg((weight - item.step).coerceAtLeast(0.0))
                    },
                    onPlus = {
                        weight = if (mode != null) {
                            PlateCalculator.nextUp(weight, mode, inventory)?.totalKg ?: weight
                        } else roundKg(weight + item.step)
                    },
                    // Przy talerzach wpisywania nie ma i nie ma go celowo:
                    // ciężar, którego nie da się nałożyć, nie jest ciężarem.
                    // Przy wyciągu liczba z tabliczki bywa dowolna.
                    onLabelClick = if (mode == null) ({ typingWeight = true }) else null
                )

                if (mode != null) {
                    PlateCalculator.nearest(weight, mode, inventory)?.let {
                        Text(
                            it.describe(mode, inventory),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // ---- Powtórzenia ----
            Stepper(
                label = "$reps powt.",
                onMinus = { if (reps > 1) reps-- },
                onPlus = { reps++ }
            )
            if (item.hasRepRange) {
                Text(
                    "zakres ${item.repRange}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Zapis ----
            val isPr = state.bestWeight != null && weight > state.bestWeight + 0.001
            Button(
                onClick = { onSave(weight, reps, rir) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isPr) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(
                    if (isPr) "ZAPISZ - REKORD" else "ZAPISZ SERIĘ",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // ---- Rzeczy drugorzędne ----
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zrobione serie jako zwięzłe chipy - przegląd bez zajmowania ekranu.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.doneSets.forEach { s ->
                        SetChip(s) { editingSet = s }
                    }
                }
                TextButton(
                    onClick = { showRir = !showRir },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        if (rir != null) "RIR $rir" else "RIR",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // RIR schowany domyślnie: przydaje się, ale nie przy każdej serii.
            AnimatedVisibility(visible = showRir) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 1, 2, 3).forEach { v ->
                        if (rir == v) {
                            Button(
                                onClick = { rir = null },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) { Text(v.toString()) }
                        } else {
                            OutlinedButton(
                                onClick = { rir = v },
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) { Text(v.toString()) }
                        }
                    }
                }
            }
        }
    }

    editingSet?.let { s ->
        EditSetDialog(
            entry = s,
            item = item,
            inventory = inventory,
            onDismiss = { editingSet = null },
            onSave = { w, r, v ->
                onUpdateSet(s.id, w, r, v)
                editingSet = null
            },
            onDelete = {
                onDeleteSet(s.id)
                editingSet = null
            }
        )
    }
}

/**
 * Zwięzły opis zapisanej serii. Dotknięcie otwiera jej poprawianie.
 *
 * Wcześniej kafelek miał krzyżyk i kasował serię jednym dotknięciem, bez
 * pytania. To zła zamiana: pomyłki przy wpisywaniu zdarzają się stale,
 * a kasowanie trafiało się przypadkiem.
 */
@Composable
private fun SetChip(s: SetEntry, onEdit: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onEdit)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${Loading.fmt(s.weightKg)}×${s.reps}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Poprawianie zapisanej serii.
 *
 * Te same regulatory co przy zapisie, żeby nie uczyć się drugiego sposobu
 * wpisywania tego samego. Kasowanie zostaje tutaj, w jednym miejscu
 * z poprawianiem - ale jako osobny przycisk, a nie skutek dotknięcia kafelka.
 */
@Composable
private fun EditSetDialog(
    entry: SetEntry,
    item: PlanItem,
    inventory: Inventory,
    onDismiss: () -> Unit,
    onSave: (Double, Int, Int?) -> Unit,
    onDelete: () -> Unit
) {
    val mode = LoadMode.of(item.equipment, inventory)
    var weight by remember(entry.id) { mutableStateOf(entry.weightKg) }
    var reps by remember(entry.id) { mutableStateOf(entry.reps) }
    // Zapas wchodzi do poprawki z tym, co zapisano - inaczej okno milczało
    // o jedynej wartości, której po treningu nie da się odtworzyć z pamięci.
    var rir by remember(entry.id) { mutableStateOf(entry.rir) }
    var typingWeight by remember { mutableStateOf(false) }

    if (typingWeight) {
        WeightDialog(
            initialKg = weight,
            title = item.exerciseName,
            onDismiss = { typingWeight = false },
            onPick = { weight = it; typingWeight = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Popraw serię") },
        text = {
            Column {
                if (item.hasTargetWeight) {
                    Stepper(
                        label = "${Loading.fmt(weight)} ${item.equipment.weightUnitLabel}",
                        onMinus = {
                            weight = if (mode != null) {
                                PlateCalculator.nextDown(weight, mode, inventory)?.totalKg ?: weight
                            } else roundKg((weight - item.step).coerceAtLeast(0.0))
                        },
                        onPlus = {
                            weight = if (mode != null) {
                                PlateCalculator.nextUp(weight, mode, inventory)?.totalKg ?: weight
                            } else roundKg(weight + item.step)
                        },
                        onLabelClick = if (mode == null) ({ typingWeight = true }) else null
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Stepper(
                    label = "$reps powt.",
                    onMinus = { if (reps > 1) reps-- },
                    onPlus = { reps++ }
                )

                // Zapas widoczny od razu, nie pod przyciskiem jak przy
                // świeżej serii: tam się go podaje, tutaj przede wszystkim
                // sprawdza, co się wtedy podało.
                Spacer(Modifier.height(14.dp))
                Text(
                    if (rir != null) "Zapas powtórzeń: $rir" else "Zapas powtórzeń: nie podano",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rir != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 1, 2, 3).forEach { v ->
                        // Ponowne naciśnięcie zdejmuje zapas - tak samo jak
                        // przy świeżej serii i tak samo jak na zegarku.
                        if (rir == v) {
                            Button(
                                onClick = { rir = null },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (v >= 3) "3+" else v.toString()) }
                        } else {
                            OutlinedButton(
                                onClick = { rir = v },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (v >= 3) "3+" else v.toString()) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Usuń tę serię", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(weight, reps, rir) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/**
 * Jedna linia zamiast osobnej karty z propozycją.
 *
 * Wcześniej było to pole z etykietą, ciężarem i dwuzdaniowym uzasadnieniem -
 * informacja powtórzona trzy razy w różnych miejscach tego samego ekranu.
 * Tutaj zostaje sama decyzja i punkt odniesienia.
 */
private fun suggestionLine(state: ExerciseState): String {
    val last = state.lastTime.maxOfOrNull { it.weightKg }
    val from = last?.let { " · ostatnio ${Loading.fmt(it)}" } ?: ""
    return when (state.suggestion.reason) {
        ProgressionEngine.Reason.FIRST_TIME -> "pierwszy raz - ciężar z planu"
        ProgressionEngine.Reason.HOLD -> "utrzymaj ciężar, dołóż powtórzeń$from"
        ProgressionEngine.Reason.INCREASE -> "↑ podbicie - komplet powtórzeń$from"
        ProgressionEngine.Reason.INCREASE_EASY -> "↑ podbicie - było za lekko$from"
        ProgressionEngine.Reason.DELOAD -> "↓ cofnięcie - spadek poniżej zakresu$from"
        ProgressionEngine.Reason.STALLED -> "↓ cofnięcie - zastój$from"
    }
}

/**
 * Podsumowanie po ukończeniu wszystkich ćwiczeń.
 *
 * Pokazuje się samo, gdy ostatnia seria ostatniego ćwiczenia zostanie zapisana -
 * bez tego nic nie sygnalizowało końca treningu i ekran zapraszał do dokładania
 * kolejnych serii.
 *
 * Zakończenie wymaga jednego dotknięcia, a nie dzieje się automatycznie, bo
 * zamyka sesję bezpowrotnie. Droga powrotna istnieje, gdyby jednak zostało
 * coś do dorobienia.
 */
@Composable
private fun ColumnScope.SessionDone(
    states: List<ExerciseState>,
    elapsedMin: Long,
    allComplete: Boolean,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val sets = states.sumOf { it.doneSets.size }
    val tonnage = states.sumOf { st -> st.doneSets.sumOf { it.tonnage } }
    val reps = states.sumOf { st -> st.doneSets.sumOf { it.reps } }

    // Elementy wchodzą po kolei, a nie wszystkie naraz. Oko dostaje je w kolejności
    // ważności - najpierw fakt ukończenia, potem liczby - zamiast ściany danych.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val target = if (revealed) 1f else 0f
    val headline by animateFloatAsState(
        target, tween(360, 0, FastOutSlowInEasing), label = "naglowek"
    )
    val stat1 by animateFloatAsState(
        target, tween(360, 140, FastOutSlowInEasing), label = "kafelka1"
    )
    val stat2 by animateFloatAsState(
        target, tween(360, 220, FastOutSlowInEasing), label = "kafelka2"
    )
    val stat3 by animateFloatAsState(
        target, tween(360, 300, FastOutSlowInEasing), label = "kafelka3"
    )
    val tonnageAlpha by animateFloatAsState(
        target, tween(360, 400, FastOutSlowInEasing), label = "tonazAlfa"
    )

    // Tonaż dolicza się w górę - ruch podkreśla, że to wynik pracy, a nie etykieta.
    val tonnageShown by animateFloatAsState(
        targetValue = if (revealed) tonnage.toFloat() else 0f,
        animationSpec = tween(1100, delayMillis = 420, easing = FastOutSlowInEasing),
        label = "tonaz"
    )

    Spacer(Modifier.height(16.dp))

    Card(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = headline
                // Delikatne wejście od dołu zamiast skoku.
                translationY = (1f - headline) * 40f
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allComplete) {
                AnimatedCheck(Modifier.size(46.dp))
                Spacer(Modifier.size(14.dp))
            }
            Column {
                // Przy wcześniejszym zakończeniu nie twierdzimy, że wszystko zrobione -
                // podsumowanie ma odzwierciedlać stan, a nie gratulować na wyrost.
                Text(
                    if (allComplete) "Trening ukończony" else "Podsumowanie",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    if (allComplete) "Wszystkie ${states.size} ćwiczeń zrobione."
                    else "${states.count { it.complete }} z ${states.size} ćwiczeń zrobionych.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatBox("$elapsedMin", "minut", Modifier.weight(1f).statReveal(stat1))
        StatBox("$sets", "serii", Modifier.weight(1f).statReveal(stat2))
        StatBox("$reps", "powtórzeń", Modifier.weight(1f).statReveal(stat3))
    }

    Spacer(Modifier.height(10.dp))

    // Tonaż to suma ciężar × powtórzenia. Pojedyncza sesja niewiele mówi,
    // ale porównanie tygodnia do tygodnia pokazuje, czy objętość rośnie.
    Card(Modifier.fillMaxWidth().graphicsLayer { alpha = tonnageAlpha }) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Tonaż", style = MaterialTheme.typography.titleMedium)
                Text(
                    "suma ciężar × powtórzenia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "%,.0f kg".format(tonnageShown),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }

    Spacer(Modifier.weight(1f))

    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "ZAKOŃCZ TRENING",
            style = MaterialTheme.typography.labelLarge,
            fontSize = 17.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) { Text(if (allComplete) "Wróć - chcę dorobić serię" else "Wróć do treningu") }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Animacje ------------------------------------------------------------

/**
 * Ptaszek rysowany kreską, a nie wyświetlany jako gotowa ikona.
 *
 * Ruch niesie informację: oko łapie, że COŚ się właśnie domknęło, zanim zdąży
 * przeczytać tekst. Statyczna ikona w tym samym miejscu zlewałaby się z resztą.
 */
@Composable
private fun AnimatedCheck(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val draw by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "ptaszek"
    )
    val ring by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "obwodka"
    )

    val color = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f

        drawCircle(
            color = color.copy(alpha = 0.25f * ring),
            radius = (w / 2 - stroke / 2) * ring,
            center = Offset(w / 2, h / 2),
            style = Stroke(width = stroke * 0.7f)
        )

        // Dwa odcinki ptaszka rysowane po kolei: krótki w dół, długi w górę.
        val a = Offset(0.24f * w, 0.52f * h)
        val b = Offset(0.43f * w, 0.72f * h)
        val c = Offset(0.78f * w, 0.30f * h)

        fun between(from: Offset, to: Offset, t: Float) =
            Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)

        val first = (draw / 0.38f).coerceIn(0f, 1f)
        if (first > 0f) {
            drawLine(color, a, between(a, b, first), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        if (draw > 0.38f) {
            val second = ((draw - 0.38f) / 0.62f).coerceIn(0f, 1f)
            drawLine(color, b, between(b, c, second), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * Nakładka z potwierdzeniem, w osobnej funkcji z konkretnego powodu.
 *
 * `ActiveSession` jest rozszerzeniem `ColumnScope`, więc wewnątrz `Box` widoczne
 * są dwa odbiorniki naraz i Kotlin nie potrafi wybrać właściwego wariantu
 * `AnimatedVisibility` - tu, bez odbiornika kolumny, wybór jest jednoznaczny.
 *
 * Nazwa jest zapamiętywana, bo podczas animacji wyjścia wartość wejściowa
 * jest już pusta, a baner ma jeszcze przez chwilę pokazywać tekst.
 */
@Composable
private fun DoneOverlay(exerciseName: String?, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableStateOf("") }
    LaunchedEffect(exerciseName) {
        if (exerciseName != null) lastShown = exerciseName
    }

    AnimatedVisibility(
        visible = exerciseName != null,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        ExerciseDoneBanner(lastShown)
    }
}

/** Krótkie potwierdzenie domknięcia ćwiczenia. */
@Composable
private fun ExerciseDoneBanner(exerciseName: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheck(Modifier.size(38.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                Text("ćwiczenie gotowe", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Wspólne wejście kafelki: przygaszenie plus delikatne przesunięcie w górę. */
private fun Modifier.statReveal(progress: Float): Modifier =
    this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 28f
    }
