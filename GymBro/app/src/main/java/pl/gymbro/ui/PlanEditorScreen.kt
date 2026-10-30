package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.ui.theme.ButtonShape
import pl.gymbro.data.Cache
import pl.gymbro.data.Equipment
import pl.gymbro.data.Exercise
import pl.gymbro.data.ExerciseRepository
import pl.gymbro.data.GymRepository
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.Program
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.UserProfile
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator

/**
 * Edytor planów treningowych.
 *
 * Całość planu da się tu zbudować od zera: utworzyć jednostkę, dodać do niej
 * ćwiczenia z biblioteki albo własne, ustawić serie, zakres powtórzeń, przerwę
 * i ciężar. Nic nie jest zaszyte w kodzie.
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
    val exerciseRepo = remember { ExerciseRepository(context) }
    val gymRepo = remember { GymRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }
    val programRepo = remember { ProgramRepository(context) }

    // Trzy poziomy nawigacji: programy → jednostki → ćwiczenia.
    // Stan wyznaczają dwa pola: otwarty program i otwarta jednostka.
    var programs by remember { mutableStateOf<List<ProgramOption>>(emptyList()) }
    var openProgram by remember { mutableStateOf<Program?>(null) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var openPlan by remember { mutableStateOf<Plan?>(null) }

    // Plan ma trzy poziomy w jednym ekranie, więc wstecz cofa o jeden:
    // ćwiczenia → jednostki → programy. Na samej górze nie robi nic i wtedy
    // zajmuje się nim zakładka.
    ScreenBackHandler(enabled = openPlan != null || openProgram != null) {
        if (openPlan != null) openPlan = null else openProgram = null
    }
    // Naciskiem w otwarta zakladke wracamy na liste programow, a nie
    // zostajemy w rozgrzebanej jednostce. Wstecz cofa o jeden poziom,
    // pasek wraca na samą góre - i te dwa zachowania nie sa tym samym.
    TabReselectHandler {
        openPlan = null
        openProgram = null
    }
    var items by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    // Ile propozycji czeka w ktorej jednostce - zeby bylo widac, dokad isc,
    // zamiast przeklikiwac wszystkie plany po kolei.
    var adviceByPlan by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    // Cwiczenie, do ktorego skoczylismy z historii - podswietlone na chwile.
    var highlight by remember { mutableStateOf<Long?>(null) }
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var profile by remember { mutableStateOf(UserProfile()) }
    var loading by remember { mutableStateOf(true) }

    var newProgramDialog by remember { mutableStateOf(false) }
    var renameProgramDialog by remember { mutableStateOf<Program?>(null) }
    var deleteProgramDialog by remember { mutableStateOf<Program?>(null) }
    var newPlanDialog by remember { mutableStateOf(false) }
    var renamePlanDialog by remember { mutableStateOf<Plan?>(null) }
    var pickExercise by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<PlanItem?>(null) }

    // Po każdej zmianie w planie idzie Refresh.bump().
    //
    // Zakładki żyją w tle i nie składają się od nowa przy wejściu, więc Trening
    // sam z siebie nigdy by nie zobaczył, że jednostka zniknęła albo że program
    // nazywa się inaczej. Przeładowanie poniżej odświeża tylko ten ekran.
    suspend fun reloadPrograms() {
        programs = withContext(Dispatchers.IO) {
            programRepo.all().map { ProgramOption(it, programRepo.planCount(it.id)) }
        }
    }

    suspend fun reloadPlans() {
        val p = openProgram ?: return
        plans = withContext(Dispatchers.IO) { planRepo.plansOf(p.id) }
    }

    suspend fun reloadItems() {
        val p = openPlan ?: return
        items = withContext(Dispatchers.IO) { planRepo.items(p.id) }
        adviceByPlan = withContext(Dispatchers.IO) { planRepo.adviceCountByPlan() }
    }

    suspend fun reloadAdviceCounts() {
        adviceByPlan = withContext(Dispatchers.IO) { planRepo.adviceCountByPlan() }
    }

    /**
     * Przyjecie albo odrzucenie propozycji przy jednej pozycji.
     *
     * Odrzucenie tez kasuje wpis: propozycja ma zniknac po decyzji, a nie
     * wracac przy kazdym wejsciu w plan. Nowa pojawi sie po nastepnym treningu.
     */
    fun applyAdvice(item: PlanItem, accept: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (accept) ProgressionReview.accept(planRepo, item)
                planRepo.setAdvice(item.id, null)
            }
            reloadItems()
            Cache.clear()
        }
    }

    fun applyAll(accept: Boolean) {
        scope.launch {
            val lista = items.filter { it.advice != null }
            withContext(Dispatchers.IO) {
                lista.forEach {
                    if (accept) ProgressionReview.accept(planRepo, it)
                    planRepo.setAdvice(it.id, null)
                }
            }
            reloadItems()
            Cache.clear()
        }
    }

    // Zajrzenie w plan gasi plakietke, ale same propozycje zostaja do decyzji.
    LaunchedEffect(openPlan) {
        if (openPlan != null) withContext(Dispatchers.IO) { planRepo.markAdviceSeen() }
    }

    // Skok z historii prosto do cwiczenia z propozycja. Bez tego ladowalo sie
    // na liscie programow i trzeba bylo zgadywac, w ktorej jednostce ono lezy.
    LaunchedEffect(programs) {
        val cel = PlanJump.take() ?: return@LaunchedEffect
        val (planId, itemId) = cel
        val wszystkie = withContext(Dispatchers.IO) { planRepo.plans() }
        val plan = wszystkie.firstOrNull { it.id == planId } ?: return@LaunchedEffect
        openProgram = programs.map { it.program }.firstOrNull { it.id == plan.programId }
        plans = withContext(Dispatchers.IO) {
            plan.programId?.let { planRepo.plansOf(it) } ?: wszystkie
        }
        openPlan = plan
        reloadItems()
        highlight = itemId
    }

    LaunchedEffect(Unit) {
        Busy.track {
            inventory = withContext(Dispatchers.IO) { gymRepo.inventory() }
            profile = withContext(Dispatchers.IO) { profileRepo.get() }
            reloadPrograms()
            loading = false
        }
    }

    LaunchedEffect(openProgram?.id) { reloadPlans() }
    LaunchedEffect(openPlan?.id) { reloadItems() }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

        val program = openProgram
        val plan = openPlan

        if (program == null) {
            // ---- Poziom 1: programy ----
            if (loading) {
                Text("Wczytuję…", Modifier.padding(top = 16.dp))
            } else {
                ProgramList(
                    programs = programs,
                    onOpen = { openProgram = it },
                    onActivate = { p ->
                        scope.launch {
                            withContext(Dispatchers.IO) { programRepo.setActive(p.id) }
                            reloadPrograms()
                            Refresh.bump()
                        }
                    },
                    onRename = { renameProgramDialog = it },
                    onDelete = { deleteProgramDialog = it },
                    onNew = { newProgramDialog = true }
                )
            }
        } else if (plan == null) {
            // ---- Poziom 2: jednostki programu ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(program.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Jednostki treningowe",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (plans.isEmpty()) {
                EmptyState(
                    title = "Program jest pusty",
                    body = "Dodaj jednostki - na przykład A, B i C przy FBW, " +
                        "albo Push, Pull i Legs przy PPL."
                )
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(plans, key = { it.id }) { p ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { openPlan = p },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val czeka = adviceByPlan[p.id] ?: 0
                                if (czeka > 0) {
                                    Badge(Modifier.padding(end = 8.dp)) { Text("$czeka") }
                                }
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("▶", style = MaterialTheme.typography.titleLarge)
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                TextButton(onClick = { renamePlanDialog = p }) { Text("Nazwa") }
                                TextButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { planRepo.deletePlan(p.id) }
                                        reloadPlans()
                                        reloadPrograms()
                                        Refresh.bump()
                                    }
                                }) {
                                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { newPlanDialog = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) { Text("Nowa jednostka", modifier = Modifier.padding(vertical = 6.dp)) }

        } else {
            // ---- Szczegóły planu ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            val inv = inventory
            if (items.isEmpty()) {
                EmptyState(
                    title = "Plan jest pusty",
                    body = "Dodaj pierwsze ćwiczenie. Ciężar ustawisz spośród progów, " +
                        "jakie da się złożyć z Twojego sprzętu."
                )
            }

            // Przyjecie albo odrzucenie wszystkiego naraz. Po treningu
            // propozycji bywa kilka i klikanie ich po kolei jest robota,
            // ktora zniecheca do calego mechanizmu.
            val czekajace = items.count { it.advice != null }
            if (czekajace > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { applyAll(true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Zastosuj wszystkie ($czekajace)") }
                    OutlinedButton(
                        onClick = { applyAll(false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pomiń wszystkie") }
                }
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
                            // Opis pomija to, czego nie ustawiono - pusta przerwa
                            // czy zakres nie zajmują miejsca jako „0:00" ani „0-0".
                            Text(
                                item.prescription,
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (inv != null && item.hasTargetWeight) {
                                val r = PlateCalculator.resolve(item.targetKg, item.equipment, inv)
                                Text(
                                    "${Loading.fmt(r.actualKg)} ${item.equipment.weightUnitLabel}",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                val mode = LoadMode.of(item.equipment, inv)
                                if (r.loading != null && mode != null) {
                                    Text(
                                        r.loading.describe(mode, inv),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            val czeka = item.advice
                            if (czeka != null) {
                                AdviceRow(
                                    item = item,
                                    advice = czeka,
                                    onAccept = { applyAdvice(item, true) },
                                    onSkip = { applyAdvice(item, false) }
                                )
                            }

                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(
                                    onClick = { editItem = item },
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) { Text("Edytuj") }

                                TextButton(
                                    enabled = index > 0,
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    onClick = {
                                        scope.launch {
                                            swapPositions(planRepo, items[index], items[index - 1])
                                            reloadItems()
                                            Refresh.bump()
                                        }
                                    }
                                ) { Text("↑") }

                                TextButton(
                                    enabled = index < items.lastIndex,
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    onClick = {
                                        scope.launch {
                                            swapPositions(planRepo, items[index], items[index + 1])
                                            reloadItems()
                                            Refresh.bump()
                                        }
                                    }
                                ) { Text("↓") }

                                TextButton(
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { planRepo.deleteItem(item.id) }
                                            reloadItems()
                                            Refresh.bump()
                                        }
                                    }
                                ) { Text("Usuń") }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { pickExercise = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) { Text("Dodaj ćwiczenie", modifier = Modifier.padding(vertical = 6.dp)) }
        }
    }

    // ---- Okna dialogowe ----

    if (newProgramDialog) {
        TextPrompt(
            title = "Nowy program",
            label = "Nazwa (np. FBW, PPL, Góra/Dół)",
            initial = "",
            onDismiss = { newProgramDialog = false },
            onConfirm = { name ->
                scope.launch {
                    withContext(Dispatchers.IO) { programRepo.insert(name) }
                    newProgramDialog = false
                    reloadPrograms()
                    Refresh.bump()
                }
            }
        )
    }

    renameProgramDialog?.let { p ->
        TextPrompt(
            title = "Zmień nazwę programu",
            label = "Nazwa",
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

    // Usunięcie programu kasuje wszystkie jego jednostki razem z ćwiczeniami,
    // więc wymaga potwierdzenia z podaną liczbą - inaczej łatwo stracić
    // kilkanaście godzin układania planu jednym dotknięciem.
    deleteProgramDialog?.let { p ->
        val count = programs.firstOrNull { it.program.id == p.id }?.planCount ?: 0
        AlertDialog(
            onDismissRequest = { deleteProgramDialog = null },
            title = { Text("Usunąć program?") },
            text = {
                Text(
                    if (count == 0) "Program ${p.name} nie ma jednostek."
                    else "Razem z programem ${p.name} zniknie " +
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

    if (newPlanDialog) {
        TextPrompt(
            title = "Nowa jednostka",
            label = "Nazwa (np. A, Push, Nogi)",
            initial = "",
            onDismiss = { newPlanDialog = false },
            onConfirm = { name ->
                val p = openProgram
                if (p != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            planRepo.insertPlan(name, plans.size, p.id)
                        }
                        newPlanDialog = false
                        reloadPlans()
                        reloadPrograms()
                        Refresh.bump()
                    }
                }
            }
        )
    }

    renamePlanDialog?.let { p ->
        TextPrompt(
            title = "Zmień nazwę",
            label = "Nazwa planu",
            initial = p.name,
            onDismiss = { renamePlanDialog = null },
            onConfirm = { name ->
                scope.launch {
                    withContext(Dispatchers.IO) { planRepo.renamePlan(p.id, name) }
                    renamePlanDialog = null
                    reloadPlans()
                    Refresh.bump()
                    if (openPlan?.id == p.id) openPlan = openPlan?.copy(name = name)
                }
            }
        )
    }

    if (pickExercise) {
        ExercisePickerDialog(
            exerciseRepo = exerciseRepo,
            profile = profile,
            onDismiss = { pickExercise = false },
            onPicked = { exercise ->
                val p = openPlan
                if (p != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            planRepo.upsertItem(
                                // Tylko liczba serii. Ciężar, zakres powtórzeń
                                // i przerwa zostają nieustawione - uzupełnisz je,
                                // jeśli ich potrzebujesz, a jeśli nie, nie będą
                                // nigdzie zajmować miejsca.
                                PlanItem(
                                    planId = p.id,
                                    exerciseId = exercise.id,
                                    position = items.size,
                                    targetKg = 0.0,
                                    sets = 3,
                                    repsFrom = 0,
                                    repsTo = 0,
                                    restSec = 0
                                )
                            )
                        }
                        pickExercise = false
                        reloadItems()
                        Refresh.bump()
                    }
                }
            }
        )
    }

    editItem?.let { item ->
        val inv = inventory
        if (inv != null) {
            ItemEditorDialog(
                item = item,
                inventory = inv,
                onDismiss = { editItem = null },
                onSave = { updated ->
                    scope.launch {
                        withContext(Dispatchers.IO) { planRepo.upsertItem(updated) }
                        editItem = null
                        reloadItems()
                        Refresh.bump()
                    }
                }
            )
        }
    }
}

/** Zamiana kolejności dwóch pozycji planu. */
private suspend fun swapPositions(repo: PlanRepository, a: PlanItem, b: PlanItem) {
    withContext(Dispatchers.IO) {
        repo.upsertItem(a.copy(position = b.position))
        repo.upsertItem(b.copy(position = a.position))
    }
}

// ---- Elementy wspólne ----------------------------------------------------

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextPrompt(
    title: String,
    label: String,
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
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

// ---- Wybór ćwiczenia -----------------------------------------------------

@Composable
private fun ExercisePickerDialog(
    exerciseRepo: ExerciseRepository,
    profile: UserProfile,
    onDismiss: () -> Unit,
    onPicked: (Exercise) -> Unit
) {
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    // Biblioteka mogła tylko rosnąć: ćwiczenie dało się dodać, ale nigdy
    // usunąć, więc każda pomyłka i każdy test zostawały w niej na zawsze.
    // Kasować wolno wyłącznie to, czego nie trzyma żaden plan ani historia.
    var unused by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var toDelete by remember { mutableStateOf<Exercise?>(null) }

    // Filtr startuje od sprzętu z profilu, ale jest w pełni edytowalny -
    // możesz zaznaczyć dowolną kombinację, np. same hantle i wyciąg.
    // Pusty zbiór znaczy „bez filtra", a nie „nic nie pokazuj": ukrycie
    // wszystkiego po odznaczeniu ostatniego pola byłoby zaskoczeniem.
    var selectedEquipment by remember {
        mutableStateOf(profile.ownedEquipment ?: Equipment.entries.toSet())
    }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            exerciseRepo.all() to exerciseRepo.unusedIds()
        }
        all = loaded.first
        unused = loaded.second
    }
    LaunchedEffect(Unit) { reload() }

    val byName = all.filter { it.name.contains(query, ignoreCase = true) }
    val filterActive = selectedEquipment.isNotEmpty() &&
        selectedEquipment.size < Equipment.entries.size
    val filtered = if (selectedEquipment.isEmpty()) byName
    else byName.filter { it.equipment in selectedEquipment }
    val hiddenCount = byName.size - filtered.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz ćwiczenie") },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Szukaj") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (filterActive && hiddenCount > 0) "Sprzęt · ukryto $hiddenCount"
                        else "Sprzęt",
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(
                        onClick = {
                            selectedEquipment =
                                if (filterActive) Equipment.entries.toSet()
                                else profile.ownedEquipment ?: Equipment.entries.toSet()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            if (filterActive) "pokaż wszystkie" else "mój sprzęt",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Każdy rodzaj sprzętu jako przełącznik - zaznaczasz dowolną
                // kombinację, np. hantle i wyciąg naraz.
                Equipment.entries.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        row.forEach { eq ->
                            val on = eq in selectedEquipment
                            val toggle = {
                                selectedEquipment = selectedEquipment.toMutableSet().apply {
                                    if (on) remove(eq) else add(eq)
                                }
                            }
                            if (on) {
                                Button(
                                    onClick = toggle,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        equipmentName(eq),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = toggle,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        equipmentName(eq),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (all.isEmpty()) {
                    Text(
                        "Biblioteka jest pusta. Utwórz pierwsze ćwiczenie.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        "Nic nie pasuje. Odznacz filtr sprzętu albo zmień wyszukiwanie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // Stała maksymalna wysokość zamiast weight(): wewnątrz okna
                // dialogowego wysokość bywa nieograniczona, a wtedy weight() nic nie mierzy.
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name)
                                    Text(
                                        "${e.muscleGroup} · ${equipmentName(e.equipment)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (e.id in unused) {
                                    TextButton(onClick = { toDelete = e }) {
                                        Text(
                                            "Usuń",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                TextButton(onClick = { onPicked(e) }) { Text("Dodaj") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text("Nowe ćwiczenie") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } }
    )

    if (creating) {
        NewExerciseDialog(
            onDismiss = { creating = false },
            onCreate = { name, equipment, muscle ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        exerciseRepo.insert(
                            Exercise(
                                name = name,
                                equipment = equipment,
                                muscleGroup = muscle,
                                isCustom = true
                            )
                        )
                    }
                    creating = false
                    reload()
                }
            }
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunąć ćwiczenie?") },
            text = {
                Text(
                    "${e.name} zniknie z biblioteki. Nie ma go w żadnym planie " +
                        "ani w żadnym zapisanym treningu, więc nic poza tym nie ucierpi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { exerciseRepo.delete(e.id) }
                        toDelete = null
                        reload()
                    }
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Anuluj") }
            }
        )
    }
}


@Composable
private fun NewExerciseDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Equipment, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowe ćwiczenie") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = muscle,
                    onValueChange = { muscle = it },
                    label = { Text("Partia (np. nogi, klatka)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Sprzęt", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Decyduje o tym, jak liczymy załadunek z talerzy.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                // Dwie kolumny zamiast jednej: sześć opcji mieści się wtedy
                // w całości na ekranie i nie trzeba niczego przewijać.
                Equipment.entries.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pair.forEach { eq ->
                            if (eq == equipment) {
                                Button(
                                    onClick = { equipment = eq },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text(equipmentName(eq), maxLines = 1) }
                            } else {
                                OutlinedButton(
                                    onClick = { equipment = eq },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text(equipmentName(eq), maxLines = 1) }
                            }
                        }
                        // Dopełnienie, gdyby liczba opcji była nieparzysta.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onCreate(name.trim(), equipment, muscle.trim().ifEmpty { "inne" })
                }
            }) { Text("Utwórz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

// ---- Edycja pozycji planu ------------------------------------------------

@Composable
private fun ItemEditorDialog(
    item: PlanItem,
    inventory: Inventory,
    onDismiss: () -> Unit,
    onSave: (PlanItem) -> Unit
) {
    // Puste pole oznacza „nieustawione" i tak też wraca do bazy jako zero.
    var sets by remember { mutableStateOf(item.sets.toString()) }
    var repsFrom by remember { mutableStateOf(if (item.hasRepRange) item.repsFrom.toString() else "") }
    var repsTo by remember { mutableStateOf(if (item.hasRepRange) item.repsTo.toString() else "") }
    var restSec by remember { mutableStateOf(if (item.hasRest) item.restSec.toString() else "") }
    var noProgress by remember { mutableStateOf(item.noProgress) }
    var weight by remember { mutableStateOf(item.targetKg) }
    var useWeight by remember { mutableStateOf(item.hasTargetWeight) }

    val mode = LoadMode.of(item.equipment, inventory)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.exerciseName) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ciężar", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { useWeight = !useWeight }) {
                        Text(if (useWeight) "nie ustawiam" else "ustaw ciężar")
                    }
                }

                if (!useWeight) {
                    Text(
                        "Bez zadanego ciężaru - nie pojawi się ani w planie, ani przy logowaniu. " +
                            "Przydatne przy ćwiczeniach z masą własną.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (mode != null) {
                    // Krok to realny próg z posiadanych talerzy, nie dowolna liczba.
                    Stepper(
                        label = "${Loading.fmt(weight)} ${item.equipment.weightUnitLabel}",
                        onMinus = {
                            weight = PlateCalculator.nextDown(weight, mode, inventory)?.totalKg ?: weight
                        },
                        onPlus = {
                            weight = PlateCalculator.nextUp(weight, mode, inventory)?.totalKg ?: weight
                        }
                    )
                    PlateCalculator.nearest(weight, mode, inventory)?.let {
                        Text(
                            it.describe(mode, inventory),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    // Wyciąg i maszyna: stos ustawiasz ręcznie, więc dowolna liczba.
                    Stepper(
                        label = "${Loading.fmt(weight)} kg",
                        onMinus = { weight = (weight - 2.5).coerceAtLeast(0.0) },
                        onPlus = { weight += 2.5 }
                    )
                }

                Spacer(Modifier.height(12.dp))
                NumberField(sets, { sets = it }, "Serie")

                Spacer(Modifier.height(10.dp))
                Text(
                    "Poniższe pola możesz zostawić puste - czego nie ustawisz, " +
                        "tego aplikacja nigdzie nie pokaże.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { NumberField(repsFrom, { repsFrom = it }, "Powt. od") }
                    Column(Modifier.weight(1f)) { NumberField(repsTo, { repsTo = it }, "Powt. do") }
                }
                Spacer(Modifier.height(8.dp))
                NumberField(restSec, { restSec = it }, "Przerwa (sekundy)")
                Spacer(Modifier.height(10.dp))
                // Odznaczone domyslnie: milczenie kosztuje wiecej niz propozycja,
                // wiec wyciszenie ma byc decyzja, a nie stanem wyjsciowym.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noProgress, onCheckedChange = { noProgress = it })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text("Nie proponuj progresji")
                        Text(
                            "Dla rozgrzewkowych i rehabilitacyjnych, gdzie ciezar " +
                                "ma stac w miejscu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Puste pole zapisuje się jako zero, czyli „nieustawione".
                // Zakres wymaga obu końców - jeden bez drugiego nic nie znaczy.
                val from = repsFrom.trim().toIntOrNull()
                val to = repsTo.trim().toIntOrNull()
                val rangeOk = from != null && to != null && from > 0 && to > 0

                onSave(
                    item.copy(
                        targetKg = if (useWeight) weight else 0.0,
                        sets = sets.trim().toIntOrNull()?.coerceAtLeast(1) ?: item.sets,
                        repsFrom = if (rangeOk) minOf(from!!, to!!) else 0,
                        repsTo = if (rangeOk) maxOf(from!!, to!!) else 0,
                        restSec = restSec.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        noProgress = noProgress
                    )
                )
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
