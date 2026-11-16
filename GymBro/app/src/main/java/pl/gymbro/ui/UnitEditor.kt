package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Cache
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanLibrary
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.Program
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.UserProfile
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator
import pl.gymbro.gym.ProgressionReview

// ---- Edytor jednostki ---------------------------------------------------------

/**
 * Ćwiczenia jednej jednostki z ich seriami, zakresem, ciężarem i przerwą.
 *
 * Wzorowane na Hevy: lista kart, każda ustawiana na miejscu, bez okien.
 * Poprzednio było tu „Edytuj" otwierające okno z ośmioma polami - żeby
 * zmienić ciężar o jeden próg, trzeba było wejść, przewinąć, zapisać i wyjść.
 * Teraz karta pokazuje skrót, a dotknięcie rozwija ją w regulatory.
 *
 * Każda zmiana zapisuje się od razu. Przycisku „Zapisz" nie ma, bo nie ma
 * tu szkicu do porzucenia - każda pojedyncza zmiana jest kompletna, a
 * zapomniany zapis kosztowałby więcej niż jego brak.
 *
 * Ten sam ekran służy w kreatorze nowego planu i przy zwykłej edycji - dwa
 * edytory rozjechałyby się przy pierwszej zmianie. Nagłówek i stopkę daje
 * miejsce wywołania: kreator ma tam numer kroku i „Gotowe", edycja - nazwę
 * jednostki i dzień.
 *
 * Propozycje progresji siedzą przy kartach, jak dotąd: to tu podejmuje się
 * decyzję, widząc obok siebie to, co w planie, i to, co z treningu wynika.
 */
@Composable
internal fun UnitEditorScreen(
    plan: Plan,
    profile: UserProfile,
    inventory: Inventory,
    highlight: Long? = null,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val planRepo = remember { PlanRepository(context) }

    var items by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var expanded by remember(plan.id) { mutableStateOf(highlight) }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        items = withContext(Dispatchers.IO) { planRepo.items(plan.id) }
        loaded = true
    }

    LaunchedEffect(plan.id) {
        reload()
        // Zajrzenie w jednostkę gasi plakietkę, ale propozycje zostają do decyzji.
        withContext(Dispatchers.IO) { planRepo.markAdviceSeen() }
        val i = items.indexOfFirst { it.id == highlight }
        if (i >= 0) listState.animateScrollToItem(i)
    }

    /**
     * Zapis jednej pozycji: od razu na ekranie, potem do bazy.
     *
     * Najpierw lista w pamięci, bo przy szybkim klikaniu plusa odczyt z bazy
     * po każdym zapisie cofałby liczbę o krok, zanim dojdzie następny.
     */
    fun change(updated: PlanItem) {
        items = items.map { if (it.id == updated.id) updated else it }
        scope.launch {
            withContext(Dispatchers.IO) { planRepo.upsertItem(updated) }
            Refresh.bump()
        }
    }

    fun mutate(action: suspend () -> Unit) {
        scope.launch {
            action()
            reload()
            Cache.clear()
            Refresh.bump()
        }
    }

    if (picking) {
        ExercisePickerScreen(
            profile = profile,
            alreadyIn = items.map { it.exerciseId }.toSet(),
            onBack = { picking = false },
            onAdd = { chosen ->
                mutate {
                    withContext(Dispatchers.IO) {
                        chosen.forEachIndexed { i, e ->
                            planRepo.upsertItem(
                                // Zakres i przerwa od razu ustawione: bez zakresu
                                // aplikacja nie ma z czego proponować progresji,
                                // a 8-12 to rozsądny środek, który łatwo przestawić.
                                // Ciężar pusty - nie znamy niczyjej siły.
                                PlanItem(
                                    planId = plan.id,
                                    exerciseId = e.id,
                                    position = items.size + i,
                                    targetKg = 0.0,
                                    sets = 3,
                                    repsFrom = 8,
                                    repsTo = 12,
                                    restSec = 120
                                )
                            )
                        }
                    }
                    picking = false
                }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        header()

        // Wszystkie propozycje naraz - po treningu bywa ich kilka, a klikanie
        // po kolei zniechęca do całego mechanizmu.
        val czekajace = items.count { it.advice != null }
        if (czekajace > 0) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        mutate {
                            withContext(Dispatchers.IO) {
                                items.filter { it.advice != null }.forEach {
                                    ProgressionReview.accept(planRepo, it)
                                    planRepo.setAdvice(it.id, null)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Zastosuj wszystkie ($czekajace)") }
                OutlinedButton(
                    onClick = {
                        mutate {
                            withContext(Dispatchers.IO) {
                                items.filter { it.advice != null }
                                    .forEach { planRepo.setAdvice(it.id, null) }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Pomiń wszystkie") }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                ExerciseCard(
                    item = item,
                    inventory = inventory,
                    expanded = expanded == item.id,
                    highlighted = highlight == item.id,
                    first = index == 0,
                    last = index == items.lastIndex,
                    onToggle = { expanded = if (expanded == item.id) null else item.id },
                    onChange = ::change,
                    onMove = { up ->
                        val other = items.getOrNull(if (up) index - 1 else index + 1)
                        if (other != null) mutate {
                            withContext(Dispatchers.IO) {
                                planRepo.upsertItem(item.copy(position = other.position))
                                planRepo.upsertItem(other.copy(position = item.position))
                            }
                        }
                    },
                    onDelete = {
                        mutate { withContext(Dispatchers.IO) { planRepo.deleteItem(item.id) } }
                    },
                    onAdvice = { accept ->
                        mutate {
                            withContext(Dispatchers.IO) {
                                if (accept) ProgressionReview.accept(planRepo, item)
                                planRepo.setAdvice(item.id, null)
                            }
                        }
                    }
                )
            }

            // Dopiero po wczytaniu. Obecny od początku był jedynym elementem
            // listy, a lista trzyma się pierwszego widocznego - po wczytaniu
            // ćwiczeń przewijała się do niego, czyli na sam dół, i pierwsze
            // ćwiczenie chowało się nad krawędzią.
            if (loaded) item(key = "dodaj") {
                if (items.isEmpty()) {
                    Text(
                        "Jednostka jest pusta. Dodaj ćwiczenia - zaznaczysz kilka naraz, " +
                            "a serie, powtórzenia i ciężar ustawisz potem na kartach.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                OutlinedButton(
                    onClick = { picking = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Text("+ Dodaj ćwiczenia", modifier = Modifier.padding(vertical = 6.dp)) }
                Spacer(Modifier.height(8.dp))
            }
        }

        footer()
    }
}

/** „3 serie · 8-12 powt. · 60 kg · przerwa 2:00" - pomija to, czego nie ustawiono. */
private fun summaryLine(item: PlanItem, inventory: Inventory): String =
    buildList {
        add(plural(item.sets, "seria", "serie", "serii"))
        if (item.hasRepRange) add("${item.repRange} powt.")
        if (item.hasTargetWeight) {
            val r = PlateCalculator.resolve(item.targetKg, item.equipment, inventory)
            add("${Loading.fmt(r.actualKg)} ${item.equipment.weightUnitLabel}")
        }
        if (item.hasRest) add("przerwa ${item.restSec / 60}:%02d".format(item.restSec % 60))
    }.joinToString(" · ")

/**
 * Karta ćwiczenia: zwinięta to skrót, rozwinięta - regulatory.
 *
 * Rozwinięta jest jedna naraz. Sześć rozwiniętych kart to kilka ekranów
 * przewijania, w których traci się, przy którym ćwiczeniu się jest.
 */
@Composable
private fun ExerciseCard(
    item: PlanItem,
    inventory: Inventory,
    expanded: Boolean,
    highlighted: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onChange: (PlanItem) -> Unit,
    onMove: (up: Boolean) -> Unit,
    onDelete: () -> Unit,
    onAdvice: (accept: Boolean) -> Unit
) {
    var typingWeight by remember { mutableStateOf(false) }
    val mode = LoadMode.of(item.equipment, inventory)

    if (typingWeight) {
        WeightDialog(
            initialKg = item.targetKg,
            title = item.exerciseName,
            onDismiss = { typingWeight = false },
            onPick = { onChange(item.copy(targetKg = it)); typingWeight = false }
        )
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        summaryLine(item, inventory),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.hasTargetWeight && mode != null) {
                        PlateCalculator.resolve(item.targetKg, item.equipment, inventory)
                            .loading?.let {
                                Text(
                                    it.describe(mode, inventory),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                    }
                }
                Text(
                    if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item.advice?.let { czeka ->
                AdviceRow(
                    item = item,
                    advice = czeka,
                    onAccept = { onAdvice(true) },
                    onSkip = { onAdvice(false) }
                )
            }

            if (!expanded) return@Column

            // ---- Serie ----
            ControlRow("Serie") {
                MiniStepper(
                    value = "${item.sets}",
                    onMinus = { if (item.sets > 1) onChange(item.copy(sets = item.sets - 1)) },
                    onPlus = { if (item.sets < 10) onChange(item.copy(sets = item.sets + 1)) }
                )
            }

            // ---- Powtórzenia ----
            FieldLabel("Powtórzenia")
            if (item.hasRepRange) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStepper(
                        value = "${item.repsFrom}",
                        onMinus = {
                            if (item.repsFrom > 1) onChange(item.copy(repsFrom = item.repsFrom - 1))
                        },
                        // Dolna granica pcha górną, zamiast stawać: zakres 12-12
                        // jest dozwolony, a odwrócony nie.
                        onPlus = {
                            val od = item.repsFrom + 1
                            onChange(item.copy(repsFrom = od, repsTo = maxOf(item.repsTo, od)))
                        }
                    )
                    Text("do", style = MaterialTheme.typography.bodyMedium)
                    MiniStepper(
                        value = "${item.repsTo}",
                        onMinus = {
                            val doo = item.repsTo - 1
                            if (doo >= 1) onChange(
                                item.copy(repsTo = doo, repsFrom = minOf(item.repsFrom, doo))
                            )
                        },
                        onPlus = {
                            if (item.repsTo < 50) onChange(item.copy(repsTo = item.repsTo + 1))
                        }
                    )
                }
                Text(
                    "Gdy wyrobisz górną granicę, aplikacja zaproponuje większy ciężar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedButton(onClick = { onChange(item.copy(repsFrom = 8, repsTo = 12)) }) {
                    Text("Ustaw zakres powtórzeń")
                }
            }

            // ---- Ciężar ----
            FieldLabel("Ciężar")
            if (item.hasTargetWeight) {
                Stepper(
                    label = "${Loading.fmt(item.targetKg)} ${item.equipment.weightUnitLabel}",
                    onMinus = {
                        val w = if (mode != null) {
                            PlateCalculator.nextDown(item.targetKg, mode, inventory)?.totalKg
                                ?: item.targetKg
                        } else roundKg((item.targetKg - item.step).coerceAtLeast(0.0))
                        onChange(item.copy(targetKg = w))
                    },
                    onPlus = {
                        val w = if (mode != null) {
                            PlateCalculator.nextUp(item.targetKg, mode, inventory)?.totalKg
                                ?: item.targetKg
                        } else roundKg(item.targetKg + item.step)
                        onChange(item.copy(targetKg = w))
                    },
                    // Przy talerzach wpisywania nie ma i nie ma go celowo:
                    // ciężar, którego nie da się nałożyć, nie jest ciężarem.
                    onLabelClick = if (mode == null) ({ typingWeight = true }) else null
                )
                TextButton(onClick = { onChange(item.copy(targetKg = 0.0)) }) {
                    Text("Bez zadanego ciężaru")
                }
            } else {
                Text(
                    "Bez ciężaru - zapytam o niego przy pierwszej serii. Przy masie ciała " +
                        "zostaw tak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    // Start od najmniejszego ciężaru, który da się nałożyć -
                    // przy sztandze to sam gryf, przy maszynie jeden krok.
                    val start = if (mode != null) {
                        PlateCalculator.nextUp(0.0, mode, inventory)?.totalKg ?: item.step
                    } else item.step
                    onChange(item.copy(targetKg = start))
                }) { Text("Ustaw ciężar") }
            }

            // ---- Przerwa ----
            FieldLabel("Przerwa")
            val przerwy = listOf(0 to "brak", 60 to "1:00", 90 to "1:30", 120 to "2:00", 180 to "3:00")
            val wlasna = item.restSec.takeIf { r -> przerwy.none { it.first == r } }
                ?.let { listOf(it to "${it / 60}:%02d".format(it % 60)) }.orEmpty()
            ChipRow(
                options = przerwy + wlasna,
                selected = item.restSec,
                onSelect = { onChange(item.copy(restSec = it ?: 0)) },
                allowNone = false
            )

            // ---- Progresja ----
            // Odznaczone domyślnie: milczenie kosztuje więcej niż propozycja,
            // więc wyciszenie ma być decyzją, a nie stanem wyjściowym.
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Proponuj progresję", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Wyłącz przy rozgrzewkowych i rehabilitacyjnych.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = !item.noProgress,
                    onCheckedChange = { onChange(item.copy(noProgress = !it)) }
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onMove(true) }, enabled = !first) { Text("↑ Wyżej") }
                TextButton(onClick = { onMove(false) }, enabled = !last) { Text("↓ Niżej") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ControlRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

// ---- Nowa jednostka -----------------------------------------------------------

private enum class UnitPhase { SETUP, LIBRARY, EXERCISES }

/**
 * Nowa jednostka w krokach: nazwa i dzień, potem ćwiczenia z obciążeniem.
 *
 * Z kreatora nowego planu i z listy jednostek - stąd [title], [stepOffset]
 * i [totalSteps]: w kreatorze to kroki 3 i 4 z czterech, przy dodawaniu do
 * istniejącego planu 1 i 2 z dwóch.
 *
 * Jednostka zapisuje się po pierwszym kroku, nie na końcu. Ekran ćwiczeń
 * pracuje na bazie (ten sam co przy zwykłej edycji), więc musi mieć do czego
 * pisać - a „Anuluj" na drugim kroku zostawia jednostkę bez ćwiczeń, którą
 * widać na liście i łatwo usunąć, zamiast zgubić to, co już wybrane.
 */
@Composable
internal fun NewUnitFlow(
    program: Program,
    title: String,
    stepOffset: Int,
    totalSteps: Int,
    profile: UserProfile,
    inventory: Inventory,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val planRepo = remember { PlanRepository(context) }
    val programRepo = remember { ProgramRepository(context) }

    var phase by remember { mutableStateOf(UnitPhase.SETUP) }
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Int?>(null) }
    var current by remember { mutableStateOf<Plan?>(null) }

    ScreenBackHandler {
        when (phase) {
            UnitPhase.SETUP -> onCancel()
            UnitPhase.LIBRARY -> phase = UnitPhase.SETUP
            UnitPhase.EXERCISES -> onDone()
        }
    }

    fun open(planId: Long) {
        scope.launch {
            current = withContext(Dispatchers.IO) { planRepo.planById(planId) }
            phase = UnitPhase.EXERCISES
            Refresh.bump()
        }
    }

    when (phase) {
        UnitPhase.SETUP -> Column(Modifier.fillMaxSize().imePadding()) {
            StepHeader(title, stepOffset, totalSteps, onCancel)
            StepColumn("Nowa jednostka", Modifier.weight(1f)) {
                Text(
                    "Jednostka to jeden trening z planu - na przykład A, Push albo Nogi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FieldLabel("Nazwa")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("np. A, Push, Nogi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FieldLabel("Dzień tygodnia (opcjonalnie)")
                DayChips(day) { day = it }
                Text(
                    if (day == null) "Bez dnia - jednostki robisz po kolei, a Trening " +
                        "podpowie, która jest następna."
                    else "Dzień $day w Twoim tygodniu. Dotknij jeszcze raz, żeby zdjąć.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                TextButton(
                    onClick = { phase = UnitPhase.LIBRARY },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Albo weź gotową jednostkę z bazy") }
            }
            Button(
                onClick = {
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            val n = planRepo.plansOf(program.id).size
                            planRepo.insertPlan(name.trim(), n, program.id, day)
                        }
                        open(id)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
            ) { Text("Dalej - ćwiczenia", modifier = Modifier.padding(vertical = 4.dp)) }
        }

        UnitPhase.LIBRARY -> Column(Modifier.fillMaxSize().imePadding()) {
            StepHeader(title, stepOffset, totalSteps, onCancel)
            UnitLibraryContent(
                programName = program.name,
                profile = profile,
                onPick = { unit ->
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            programRepo.addUnitFromLibrary(program.id, unit).also {
                                if (day != null) planRepo.setPlanDay(it, day)
                            }
                        }
                        open(id)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { phase = UnitPhase.SETUP },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) { Text("Wstecz") }
        }

        UnitPhase.EXERCISES -> {
            val plan = current ?: return
            UnitEditorScreen(
                plan = plan,
                profile = profile,
                inventory = inventory,
                header = {
                    StepHeader(title, stepOffset + 1, totalSteps, onDone)
                    Text(plan.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Ćwiczenia i obciążenie" + (plan.day?.let { " · dzień $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                },
                // Jeden pod drugim, nie obok: obok siebie „+ Kolejna jednostka"
                // łamało się w dwie linie i przycisk wyglądał na zepsuty.
                footer = {
                    TextButton(
                        onClick = {
                            name = ""
                            day = null
                            current = null
                            phase = UnitPhase.SETUP
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) { Text("+ Dodaj kolejną jednostkę") }
                    Button(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) { Text("Gotowe", modifier = Modifier.padding(vertical = 4.dp)) }
                }
            )
        }
    }
}

// ---- Gotowe jednostki ---------------------------------------------------------

/**
 * Gotowe jednostki z bazy - lista na ekranie, nie w oknie.
 *
 * Dotknięcie rozwija jednostkę w jej ćwiczenia, z przyciskiem „Weź tę
 * jednostkę". Bez zapytania idą pogrupowane po planach, a plany o podobnej
 * nazwie na górę - w planie „PPL" najpierw Push, Pull i Legs. Z zapytaniem
 * lista robi się płaska: przy stu kilkudziesięciu jednostkach liczy się to,
 * co pasuje, a nie skąd pochodzi (to zostaje w podpisie).
 */
@Composable
private fun UnitLibraryContent(
    programName: String,
    profile: UserProfile,
    onPick: (PlanLibrary.UnitTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<PlanLibrary.UnitTemplate?>(null) }
    val gear = GearMode(profile)

    val nazwa = programName.lowercase()
    val order = remember(nazwa) {
        PlanLibrary.PROGRAMS.sortedByDescending { t ->
            t.name.substringBefore(' ').lowercase() in nazwa
        }.map { it.name }
    }
    val found = remember(query, showAll, profile) {
        PlanLibrary.searchUnits(query, gear.fits(showAll))
    }

    Column(modifier.fillMaxWidth()) {
        Text("Gotowa jednostka", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        SearchField(query, { query = it }, "Szukaj: nogi, podciąganie, hantle")
        GearModeRow(gear = gear, showAll = showAll, onShowAll = { showAll = it }, what = "jednostki")

        val grouped = if (query.isBlank()) {
            found.groupBy { it.first.name }.toList()
                .sortedBy { (plan, _) -> order.indexOf(plan) }
        } else listOf("" to found)

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (found.isEmpty()) {
                Text(
                    "Nic nie pasuje. Spróbuj partii (plecy), ćwiczenia (przysiad) " +
                        "albo sprzętu (kettlebell).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            grouped.forEach { (planName, pairs) ->
                if (planName.isNotEmpty()) {
                    Text(
                        planName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                }
                pairs.forEach { (program, unit) ->
                    val rozwinieta = open == unit
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { open = if (rozwinieta) null else unit },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(unit.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (planName.isEmpty()) program.name + " · " else "") +
                                    unit.note + " · " +
                                    PlanLibrary.gearSummary(listOf(unit)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (rozwinieta) {
                                Spacer(Modifier.height(6.dp))
                                unit.items.forEach { i ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            i.exercise,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${i.sets} × ${i.repsFrom}-${i.repsTo}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onPick(unit) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) { Text("Weź tę jednostkę") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
