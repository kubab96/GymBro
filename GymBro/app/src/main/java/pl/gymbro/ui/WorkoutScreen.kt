package pl.gymbro.ui

import pl.gymbro.ui.theme.ButtonShape
import pl.gymbro.data.SessionSummary
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.ProfileRepository
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.CrashLog
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
import androidx.compose.ui.graphics.Color
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
import pl.gymbro.track.PhoneTracker
import pl.gymbro.gym.PhoneActivity
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState

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
internal typealias ExerciseState = LiveSession.ExerciseState

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
    // Pozostałe plany z jednostkami - do sekcji „Z innych planów".
    //
    // Zastępuje przełącznik planu pod tytułem: jednorazowy trening z innego
    // planu robi się teraz stamtąd, a Historia pokazuje wszystkie plany naraz,
    // więc oglądanie cudzej historii też nie potrzebuje przełączania.
    var otherPrograms by remember { mutableStateOf<List<ProgramUnits>>(emptyList()) }
    var inventory by remember {
        mutableStateOf(Cache.peek<Inventory>(Cache.INVENTORY))
    }
    var loading by remember { mutableStateOf(inventory == null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var states by remember { mutableStateOf<List<ExerciseState>>(emptyList()) }
    var activeIndex by remember { mutableStateOf(0) }
    // Sesja, z której pochodzą [states] - po niej poznajemy nowy trening.
    var loadedFor by remember { mutableStateOf<Long?>(null) }
    // Ktore treningi zostawily propozycje - do kropek na liscie historii.
    var adviceSessions by remember { mutableStateOf(emptySet<Long>()) }
    // Cała sesja, nie sam identyfikator: po odtworzeniu aktywności historia
    // jeszcze się wczytuje, więc nie byłoby w czym szukać.
    var ownDetail by rememberSaveable(stateSaver = SessionSummarySaver) {
        mutableStateOf<SessionSummary?>(null)
    }
    var detailOf by rememberSaveable(stateSaver = ExternalSessionSaver) {
        mutableStateOf<ExternalSession?>(null)
    }
    var exerciseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exerciseName by rememberSaveable { mutableStateOf("") }
    // Aktywność wybrana z kafelka, czekająca na ekranie przygotowania na „Start".
    var preparing by rememberSaveable { mutableStateOf<PhoneActivity?>(null) }
    // Lista jednostek z innych planów, otwarta kafelkiem „Z innych planów".
    var pickingOtherPlan by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(pageCount = { TRAINING_PAGES.size })
    val liveActivity by PhoneTracker.state.collectAsState()

    // Ekran świeci tylko w trakcie treningu i tylko na tej zakładce.
    //
    // Wcześniej efekt był kluczowany na Unit, a zakładki żyją w tle przez
    // cały czas działania aplikacji - więc ekran nie gasł nigdy: ani przy
    // wpisywaniu jedzenia, ani z telefonem odłożonym na stół bez żadnego
    // treningu. Flaga jest jedna na całe okno, stąd warunek na widoczność.
    // Bieg nagrywany telefonem ekranu nie trzyma: telefon jest w kieszeni.
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
        // Nowy trening poznajemy po numerze sesji - numer ćwiczenia z
        // poprzedniego treningu nie może przejść na następny.
        val fresh = loadedFor != s.id
        val since = if (fresh) Long.MAX_VALUE
        else states.maxOfOrNull { st -> st.doneSets.maxOfOrNull { it.doneAt } ?: 0L } ?: 0L
        states = loaded
        loadedFor = s.id
        activeIndex = LiveSession.follow(
            complete = loaded.map { it.complete },
            newest = LiveSession.newestAfter(loaded, since),
            current = activeIndex,
            fresh = fresh
        )
    }

    /**
     * Przeładowuje jednostki planu aktywnego i pozostałych planów razem
     * z datą ostatniego wykonania.
     *
     * Wywoływana także po zakończeniu treningu - bez tego podpowiedź „następna
     * w kolejce" opierałaby się na stanie sprzed sesji i wskazywała jednostkę,
     * którą przed chwilą skończyłeś.
     */
    suspend fun reloadPlanOptions() {
        val result = withContext(Dispatchers.IO) {
            val wszystkie = programRepo.all()
            val aktywny = wszystkie.firstOrNull { it.isActive }
            // Dwa zapytania zbiorcze zamiast dwóch na każdą jednostkę.
            val counts = sessionRepo.itemCounts()
            val lastDone = sessionRepo.lastFinishedAll()
            fun optionsOf(p: Program, details: Boolean) = planRepo.plansOf(p.id).map { plan ->
                // Ćwiczenia czytamy tylko dla planu aktywnego - karta „następna
                // w kolejce" je wypisuje, reszta ekranów pokazuje samą liczbę.
                val items = if (details) planRepo.items(plan.id) else emptyList()
                PlanOption(
                    plan = plan,
                    exerciseCount = counts[plan.id] ?: 0,
                    lastDoneAt = lastDone[plan.id],
                    exercises = items.map { it.exerciseName },
                    sets = items.sumOf { it.sets }
                )
            }
            Triple(
                aktywny,
                aktywny?.let { optionsOf(it, details = true) } ?: emptyList(),
                wszystkie.filter { !it.isActive }
                    .map { ProgramUnits(it, optionsOf(it, details = false)) }
                    .filter { it.units.isNotEmpty() }
            )
        }
        activeProgram = Cache.put(Cache.ACTIVE_PROGRAM, result.first)
        planOptions = Cache.put(Cache.PLAN_OPTIONS, result.second)
        otherPrograms = result.third
    }

    // Naciskiem w otwarta zakladke wracamy do glownego widoku treningu.
    TabReselectHandler {
        detailOf = null
        ownDetail = null
        exerciseId = null
        preparing = null
        pickingOtherPlan = false
        scope.launch { pager.scrollToPage(0) }
    }

    LaunchedEffect(Unit) {
        Busy.track {
            // Jeden skok na wątek roboczy zamiast trzech. Każdy kosztuje przełączenie
            // kontekstu, a przy szybkim przeskakiwaniu między zakładkami to się sumuje.
            val loadedInv = withContext(Dispatchers.IO) {
                // Bieg nagrywany telefonem przeżywa zamknięcie aplikacji -
                // po powrocie ekran ma go pokazać, a nie wybór jednostki.
                PhoneTracker.restore(context.applicationContext)
                gymRepo.inventory()
            }
            inventory = Cache.put(Cache.INVENTORY, loadedInv)
            reloadPlanOptions()
            val active = withContext(Dispatchers.IO) { sessionRepo.active() }
            session = active
            if (active != null) reload(active, loadedInv)
            loading = false
        }
    }

    // Kropki propozycji w Historii - osobno i na Refresh, a nie raz przy starcie.
    // Wczytane raz, wisiały przy treningu jeszcze długo po tym, jak propozycje
    // zastosowano w Planie, i wyglądało to, jakby zastosowanie nie działało.
    LaunchedEffect(Refresh.version) {
        adviceSessions = withContext(Dispatchers.IO) {
            planRepo.itemsWithAdvice().mapNotNull { it.advice?.sessionId }.toSet()
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
        // Talerze i gryf zmienia się w Profilu. Bez tego Trening liczył
        // załadunek ze starego zestawu aż do zamknięcia aplikacji.
        inventory = Cache.put(
            Cache.INVENTORY, withContext(Dispatchers.IO) { gymRepo.inventory() }
        )
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
    //
    // Seria i pauza odświeżają tylko trwający trening ([LiveRefresh]), reszta
    // wszystko - a kilka zgłoszeń z jednej paczki składa się w jedno.
    val watchChanges = remember {
        WatchChanges(android.os.Handler(android.os.Looper.getMainLooper()))
    }
    DisposableEffect(session?.id) {
        WatchLinkService.sync(context, sessionActive = session != null)
        val off = WatchLinkService.Changes.listen { reason -> watchChanges.post(reason) }
        onDispose { off() }
    }
    // Zebrane zgłoszenie przeżywa zmianę sesji - seria, która przyszła tuż
    // przed zakończeniem na telefonie, i tak ma odświeżyć ekran. Ginie dopiero
    // razem z ekranem.
    DisposableEffect(Unit) { onDispose { watchChanges.cancel() } }

    // Seria albo pauza z zegarka: tylko trwający trening, bez planów,
    // talerzy i historii. Pierwsze złożenie pomijamy - wczytuje start ekranu.
    var seenLive by remember { mutableIntStateOf(LiveRefresh.version) }
    LaunchedEffect(LiveRefresh.version) {
        if (LiveRefresh.version == seenLive) return@LaunchedEffect
        seenLive = LiveRefresh.version
        if (session == null) return@LaunchedEffect
        session = withContext(Dispatchers.IO) { sessionRepo.active() }
        val live = session
        val inv = inventory
        if (live != null && inv != null) reload(live, inv)
    }

    // Historia wczytywana osobno od reszty ekranu - jedna lista dla Historii
    // i Progresu, żeby liczby w obu podzakładkach się zgadzały.
    //
    // Klucz na Refresh.version, bo zakładki żyją w tle: bez tego bieg
    // dociągnięty z zegarka po starcie aplikacji nie pojawiłby się tu nigdy.
    // Klucz na session, bo po zakończeniu treningu dochodzi nowa pozycja.
    var activities by remember { mutableStateOf<List<Activity>>(emptyList()) }
    LaunchedEffect(Refresh.version, session) {
        activities = withContext(Dispatchers.IO) {
            val all = healthRepo.sessions(limit = 3000)
            // Tętno do treningu siłowego bierzemy tylko z zegarka. Bieg
            // nagrany telefonem w tym samym czasie to inna aktywność.
            val fromWatch = all.filterNot { it.fromPhone }
            val split = Timeline.build(
                strength = sessionRepo.historySummaries(3000).map { s ->
                    s.copy(watch = WatchMatch.bestMatch(s.startedAt, s.endedAt, fromWatch))
                },
                watch = all,
                limit = Int.MAX_VALUE
            )
            (split.strength + split.cardio + split.other).sortedByDescending { it.at }
        }
    }

    fun startPlan(plan: Plan) {
        val inv = inventory ?: return
        scope.launch {
            val id = withContext(Dispatchers.IO) { sessionRepo.start(plan.id) }
            val s = Session(id = id, planId = plan.id, startedAt = System.currentTimeMillis())
            pickingOtherPlan = false
            session = s
            reload(s, inv)
        }
    }

    // Kolejność ekranów: trwająca aktywność z telefonu wygrywa ze wszystkim,
    // bo tylko tu da się ją zakończyć. Trening siłowy prowadzi zegarek, więc
    // jego ekran może poczekać.
    val liveSnap = liveActivity
    if (liveSnap != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            ActivityLiveScreen(liveSnap) { saved ->
                // Prosto do szczegółów zapisanej aktywności - to pierwsze,
                // co chce się zobaczyć po biegu.
                if (saved != null) detailOf = saved
                Refresh.bump()
            }
        }
        return
    }

    val own = ownDetail
    if (own != null) {
        SessionDetailScreen(summary = own, onBack = { ownDetail = null })
        return
    }

    val opened = detailOf
    if (opened != null) {
        ActivityDetailScreen(
            session = opened,
            onBack = { detailOf = null },
            onDeleted = {
                detailOf = null
                Refresh.bump()
            }
        )
        return
    }

    val exId = exerciseId
    if (exId != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            ExerciseProgressScreen(exId, exerciseName, onBack = { exerciseId = null })
        }
        return
    }

    // Start nie rusza od dotknięcia kafelka - najpierw ekran przygotowania,
    // żeby GPS zdążył złapać sygnał, a stoper nie liczył sznurowania butów.
    if (pickingOtherPlan) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            OtherPlansScreen(
                programs = otherPrograms,
                onBack = { pickingOtherPlan = false },
                onStart = { plan -> startPlan(plan) }
            )
        }
        return
    }

    preparing?.let { kind ->
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            ActivityPrepScreen(
                kind = kind,
                onBack = { preparing = null },
                onStart = {
                    preparing = null
                    PhoneTracker.start(context.applicationContext, kind)
                }
            )
        }
        return
    }

    val inv = inventory

    // Jeden odczyt na całe składanie. `session` to stan, który zegarek potrafi
    // wyzerować w każdej chwili (FINISH z pilota), więc `session!!` w gałęzi
    // niżej i w przyciskach było awarią czekającą na zbieg okoliczności.
    val current = session
    CompositionLocalProvider(LocalAdviceSessions provides adviceSessions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        when {
            loading -> Text("Wczytuję…", Modifier.padding(top = 16.dp))

            inv == null -> Text("Brak danych o sprzęcie.", Modifier.padding(top = 16.dp))

            current == null -> TrainingHome(
                pager = pager,
                program = activeProgram,
                options = planOptions,
                activities = activities,
                otherPrograms = otherPrograms,
                onStart = { plan -> startPlan(plan) },
                onOtherPlans = { pickingOtherPlan = true },
                onPrepare = { preparing = it },
                onOpen = { picked ->
                    when (picked) {
                        is Activity.Watch -> detailOf = picked.session
                        is Activity.Strength -> ownDetail = picked.summary
                    }
                },
                onExercise = { id, name ->
                    exerciseName = name
                    exerciseId = id
                }
            )

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
                // Wstrzymanie z telefonu tą samą drogą co z zegarka - zegarek
                // dowie się przy najbliższej odpowiedzi (`paused` w potwierdzeniu).
                onPause = { pause ->
                    scope.launch {
                        val s = session ?: return@launch
                        session = withContext(Dispatchers.IO) {
                            sessionRepo.setPaused(s.id, pause)
                            sessionRepo.active()
                        }
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
                            //
                            // Osobno od zamknięcia sesji: błąd w liczeniu
                            // propozycji wywracał całą aplikację w chwili
                            // naciśnięcia "Zakończ", a trening zostawał
                            // otwarty. Propozycja to dodatek, koniec treningu
                            // musi przejść zawsze.
                            runCatching {
                                ProgressionReview.saveFor(
                                    planRepo, sessionRepo, profileRepo, gymRepo, done
                                )
                            }.onFailure { CrashLog.note(context, "propozycje po treningu", it) }
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

