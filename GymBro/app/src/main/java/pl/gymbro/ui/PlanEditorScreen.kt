package pl.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import pl.gymbro.data.Cache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.Program
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.UserProfile

/**
 * Zakładka Plan: plany → jednostki → ćwiczenia jednostki.
 *
 * Wszystko, co tworzy albo zmienia plan, dzieje się na pełnych ekranach -
 * kreator nowego planu ([PlanLibraryScreen]), nowa jednostka ([NewUnitFlow])
 * i edytor jednostki ([UnitEditorScreen]) z wyborem ćwiczeń na całym ekranie.
 * Okienka zostały tylko tam, gdzie są pytaniem, a nie miejscem pracy: zmiana
 * nazwy i potwierdzenie usunięcia.
 *
 * Przy ciężarze nie wpisujesz dowolnej liczby - wybierasz spośród progów, jakie
 * realnie da się złożyć z Twojego sprzętu. To zamyka drogę do zapisania ciężaru,
 * którego nie ma jak załadować.
 */
@Composable
fun PlanEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val planRepo = remember { PlanRepository(context) }
    val gymRepo = remember { GymRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }
    val programRepo = remember { ProgramRepository(context) }

    // Trzy poziomy nawigacji: plany → jednostki → ćwiczenia jednostki.
    var programs by remember { mutableStateOf<List<ProgramOption>>(emptyList()) }
    var openProgram by remember { mutableStateOf<Program?>(null) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var openPlan by remember { mutableStateOf<Plan?>(null) }

    // Pełnoekranowe przebiegi nad poziomami: kreator planu i nowa jednostka.
    // Mają własne uchwyty wstecz, zarejestrowane później, więc wygrywają.
    var wizardOpen by remember { mutableStateOf(false) }
    var newUnitOpen by remember { mutableStateOf(false) }
    // Plan świeżo wybrany z bazy - lista jednostek mówi wtedy, gdzie się
    // go dostosowuje. Bez tego trafiało się na suchą listę nazw.
    var justAdded by remember { mutableStateOf(false) }

    // Szkic otwartej jednostki - zmiany czekają na Zapisz, jak w Profilu.
    val draft = remember(openPlan?.id) { openPlan?.let { UnitDraft(it) } }
    // Przejście, które czeka na odpowiedź „zapisać czy nie".
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** Wyjście z jednostki: od razu, chyba że szkic ma niezapisane zmiany. */
    fun requestLeave(proceed: () -> Unit) {
        if (draft?.dirty == true) leaving = proceed else proceed()
    }

    // Wyjście z zakładki z niezapisaną jednostką pyta tak samo jak Profil.
    val guard = LocalLeaveGuard.current
    val draftNow by rememberUpdatedState(draft)
    DisposableEffect(guard) {
        guard.ask = { proceed ->
            if (draftNow?.dirty == true) {
                leaving = proceed
                true
            } else false
        }
        onDispose { guard.ask = null }
    }

    ScreenBackHandler(enabled = openPlan != null || openProgram != null) {
        if (openPlan != null) requestLeave { openPlan = null } else openProgram = null
    }
    // Naciśnięcie otwartej zakładki wraca na listę planów, a nie zostawia
    // w rozgrzebanej jednostce. Wstecz cofa o jeden poziom, pasek wraca na
    // samą górę - i te dwa zachowania nie są tym samym.
    TabReselectHandler {
        requestLeave {
            openPlan = null
            openProgram = null
            wizardOpen = false
            newUnitOpen = false
        }
    }

    // Ile propozycji czeka w której jednostce - żeby było widać, dokąd iść.
    var adviceByPlan by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    // Ćwiczenie, do którego skoczyliśmy z historii - rozwinięte i podświetlone.
    var highlight by remember { mutableStateOf<Long?>(null) }
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var profile by remember { mutableStateOf(UserProfile()) }
    var loading by remember { mutableStateOf(true) }

    var renameProgramDialog by remember { mutableStateOf<Program?>(null) }
    var deleteProgramDialog by remember { mutableStateOf<Program?>(null) }
    var renamePlanDialog by remember { mutableStateOf<Plan?>(null) }

    // Po każdej zmianie w planie idzie Refresh.bump(): zakładki żyją w tle
    // i Trening sam z siebie nie zobaczyłby, że jednostka zniknęła.
    suspend fun reloadPrograms() {
        programs = withContext(Dispatchers.IO) {
            programRepo.all().map { ProgramOption(it, programRepo.planCount(it.id)) }
        }
    }

    suspend fun reloadPlans() {
        val p = openProgram ?: return
        plans = withContext(Dispatchers.IO) { planRepo.plansOf(p.id) }
        adviceByPlan = withContext(Dispatchers.IO) { planRepo.adviceCountByPlan() }
    }

    // Skok z historii prosto do ćwiczenia z propozycją. Bez tego lądowało się
    // na liście planów i trzeba było zgadywać, w której jednostce ono leży.
    //
    // Klucz na liczniku skoków, nie tylko na liście planów: ta zakładka żyje
    // w tle, lista wczytuje się raz, więc każdy skok po pierwszym przepadał.
    LaunchedEffect(PlanJump.version, programs) {
        if (programs.isEmpty()) return@LaunchedEffect
        val cel = PlanJump.take() ?: return@LaunchedEffect
        val (planId, itemId) = cel
        val wszystkie = withContext(Dispatchers.IO) { planRepo.plans() }
        val plan = wszystkie.firstOrNull { it.id == planId } ?: return@LaunchedEffect
        wizardOpen = false
        newUnitOpen = false
        openProgram = programs.map { it.program }.firstOrNull { it.id == plan.programId }
        openPlan = plan
        highlight = itemId
    }

    /** Zapis szkicu jednostki; [then] po zapisie, np. wyjście z ekranu. */
    fun saveDraft(then: () -> Unit = {}) {
        val d = draft ?: return then()
        scope.launch {
            val (items, plan) = withContext(Dispatchers.IO) {
                d.save(planRepo)
                planRepo.items(d.planId) to planRepo.planById(d.planId)
            }
            d.saved(items, plan)
            if (plan != null && openPlan?.id == plan.id) openPlan = plan
            Cache.clear()
            reloadPlans()
            // Zegarek, Trening i kropki propozycji w Historii czytają plan z bazy.
            Refresh.bump()
            then()
        }
    }

    LaunchedEffect(Unit) {
        Busy.track {
            inventory = withContext(Dispatchers.IO) { gymRepo.inventory() }
            profile = withContext(Dispatchers.IO) { profileRepo.get() }
            reloadPrograms()
            loading = false
        }
    }

    // Sprzęt i talerze zmienia się w Profilu, a ta zakładka żyje w tle.
    // Bez tego filtr ćwiczeń i progi ciężaru szły ze stanu sprzed zmiany
    // aż do zamknięcia aplikacji. Pierwsze złożenie robi wczytywanie wyżej.
    var seenRefresh by remember { mutableIntStateOf(Refresh.version) }
    LaunchedEffect(Refresh.version) {
        if (Refresh.version == seenRefresh) return@LaunchedEffect
        seenRefresh = Refresh.version
        inventory = withContext(Dispatchers.IO) { gymRepo.inventory() }
        profile = withContext(Dispatchers.IO) { profileRepo.get() }
        // Szkic gotowego planu kasuje się w tle, gdy kreator znika bez
        // wyboru (np. przy przełączeniu zakładki) - lista ma go nie widzieć.
        reloadPrograms()
    }

    LaunchedEffect(openProgram?.id, openPlan?.id, newUnitOpen) { reloadPlans() }

    val inv = inventory

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        val program = openProgram
        val plan = openPlan

        when {
            loading || inv == null -> Text("Wczytuję…", Modifier.padding(top = 16.dp))

            wizardOpen -> PlanLibraryScreen(
                profile = profile,
                inventory = inv,
                onBack = {
                    wizardOpen = false
                    scope.launch { reloadPrograms() }
                },
                onFinished = { id, fromTemplate ->
                    scope.launch {
                        reloadPrograms()
                        wizardOpen = false
                        // Od razu do środka: plan dodaje się po to, żeby go
                        // obejrzeć i dostosować, a powrót na listę kazałby go
                        // tam najpierw odszukać.
                        openProgram = programs.map { it.program }.firstOrNull { it.id == id }
                        justAdded = fromTemplate
                        Refresh.bump()
                    }
                }
            )

            program == null -> ProgramList(
                programs = programs,
                onOpen = { openProgram = it; justAdded = false },
                onActivate = { p ->
                    scope.launch {
                        withContext(Dispatchers.IO) { programRepo.setActive(p.id) }
                        reloadPrograms()
                        Refresh.bump()
                    }
                },
                onRename = { renameProgramDialog = it },
                onDelete = { deleteProgramDialog = it },
                onNew = { wizardOpen = true }
            )

            newUnitOpen -> NewUnitFlow(
                program = program,
                title = "Nowa jednostka",
                stepOffset = 0,
                totalSteps = 2,
                profile = profile,
                inventory = inv,
                onCancel = { newUnitOpen = false; scope.launch { reloadPrograms() } },
                onDone = { newUnitOpen = false; scope.launch { reloadPrograms() } }
            )

            plan == null -> UnitList(
                program = program,
                onBack = { openProgram = null },
                plans = plans,
                adviceByPlan = adviceByPlan,
                justAdded = justAdded,
                onOpen = { openPlan = it; highlight = null },
                onRename = { renamePlanDialog = it },
                onDelete = { p ->
                    scope.launch {
                        withContext(Dispatchers.IO) { planRepo.deletePlan(p.id) }
                        reloadPlans()
                        reloadPrograms()
                        Refresh.bump()
                    }
                },
                onNew = { newUnitOpen = true; justAdded = false }
            )

            draft != null -> UnitEditorScreen(
                draft = draft,
                profile = profile,
                inventory = inv,
                highlight = highlight,
                header = {
                    UnitEditorHeader(
                        programName = program.name,
                        draft = draft,
                        onBack = { requestLeave { openPlan = null } },
                        onRename = { renamePlanDialog = plan },
                        onSave = { saveDraft() }
                    )
                }
            )
        }
    }

    leaving?.let { proceed ->
        UnsavedChangesDialog(
            onStay = { leaving = null },
            onSave = {
                leaving = null
                saveDraft(then = proceed)
            },
            onDiscard = {
                leaving = null
                // Szkic wraca do stanu z bazy. Zostawiony wyglądałby po
                // powrocie jak zapisany.
                draft?.discard()
                proceed()
            }
        )
    }

    // ---- Okna: zmiana nazwy i potwierdzenie usunięcia ----

    renameProgramDialog?.let { p ->
        TextPrompt(
            title = "Zmień nazwę planu",
            initial = p.name,
            onDismiss = { renameProgramDialog = null },
            onConfirm = { name ->
                scope.launch {
                    withContext(Dispatchers.IO) { programRepo.rename(p.id, name) }
                    renameProgramDialog = null
                    reloadPrograms()
                    Refresh.bump()
                    if (openProgram?.id == p.id) openProgram = openProgram?.copy(name = name)
                }
            }
        )
    }

    // Usunięcie planu kasuje wszystkie jego jednostki razem z ćwiczeniami,
    // więc wymaga potwierdzenia z podaną liczbą - inaczej łatwo stracić
    // kilkanaście godzin układania planu jednym dotknięciem.
    deleteProgramDialog?.let { p ->
        val count = programs.firstOrNull { it.program.id == p.id }?.planCount ?: 0
        AlertDialog(
            onDismissRequest = { deleteProgramDialog = null },
            title = { Text("Usunąć plan?") },
            text = {
                Text(
                    if (count == 0) "Plan ${p.name} nie ma jednostek."
                    else "Razem z planem ${p.name} zniknie " +
                        plural(count, "jednostka", "jednostki", "jednostek") +
                        " wraz z przypisanymi ćwiczeniami. Tego nie da się cofnąć."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { programRepo.delete(p.id) }
                        deleteProgramDialog = null
                        if (openProgram?.id == p.id) openProgram = null
                        reloadPrograms()
                        Refresh.bump()
                    }
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteProgramDialog = null }) { Text("Anuluj") }
            }
        )
    }

    renamePlanDialog?.let { p ->
        TextPrompt(
            title = "Zmień nazwę jednostki",
            initial = if (draft?.planId == p.id) draft.name else p.name,
            onDismiss = { renamePlanDialog = null },
            onConfirm = { name ->
                // W otwartej jednostce nazwa idzie do szkicu, jak każda inna
                // zmiana tam - zapisze ją przycisk Zapisz. Z listy jednostek
                // od razu, bo tam nie ma czego zapisywać.
                val d = draft
                if (d != null && d.planId == p.id) {
                    d.name = name
                    renamePlanDialog = null
                } else scope.launch {
                    withContext(Dispatchers.IO) { planRepo.renamePlan(p.id, name) }
                    renamePlanDialog = null
                    reloadPlans()
                    Refresh.bump()
                }
            }
        )
    }
}

/** Jednostki planu - poziom drugi. */
@Composable
private fun UnitList(
    program: Program,
    onBack: () -> Unit,
    plans: List<Plan>,
    adviceByPlan: Map<Long, Int>,
    justAdded: Boolean,
    onOpen: (Plan) -> Unit,
    onRename: (Plan) -> Unit,
    onDelete: (Plan) -> Unit,
    onNew: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć do planów")
            }
            Column {
                Text(
                    program.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Jednostki treningowe" + if (program.isActive) " · plan aktywny" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (justAdded) {
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    "Plan dodany. Dotknij jednostki, żeby dostosować ćwiczenia " +
                        "i ustawić ciężary.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        if (plans.isEmpty()) {
            Text(
                "Plan jest pusty. Dodaj jednostki - na przykład A, B i C przy FBW " +
                    "albo Push, Pull i Legs przy PPL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(plans, key = { it.id }) { p ->
                UnitListCard(
                    plan = p,
                    pending = adviceByPlan[p.id] ?: 0,
                    onOpen = { onOpen(p) },
                    onRename = { onRename(p) },
                    onDelete = { onDelete(p) }
                )
            }
        }

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) { Text("Nowa jednostka", modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

/**
 * Jednostka na liście planu - w stylu kart z Treningu.
 *
 * Nazwa i usuwanie schowane w menu pod trzema kropkami. Dotąd stały jako
 * dwa przyciski pod każdą jednostką i „Usuń" leżało w zasięgu kciuka przy
 * każdym przewinięciu - a kasowało jednostkę bez pytania.
 */
@Composable
private fun UnitListCard(
    plan: Plan,
    pending: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // Propozycje po treningu widać już na liście - żeby było
                // wiadomo, do której jednostki zajrzeć.
                if (pending > 0) {
                    Text(
                        plural(pending, "propozycja", "propozycje", "propozycji"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Zmień nazwę") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("Usuń jednostkę", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; confirm = true }
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Usunąć jednostkę ${plan.name}?") },
            text = { Text("Zniknie razem z ćwiczeniami. Zapisane treningi zostają w historii.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete() }) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } }
        )
    }
}

/** Zmiana nazwy - okno, bo to pytanie o jedno słowo, a nie miejsce pracy. */
@Composable
private fun TextPrompt(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text("Zapisz")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
