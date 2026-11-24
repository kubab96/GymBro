package pl.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
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
import pl.gymbro.data.ProgressMode
import pl.gymbro.data.UserProfile
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator

// ---- Edytor jednostki ---------------------------------------------------------

/**
 * Ćwiczenia jednej jednostki z ich seriami, zakresem, ciężarem i przerwą.
 *
 * Wzorowane na Hevy: lista kart, każda ustawiana na miejscu, bez okien.
 * Karta zwinięta to numer, nazwa i skrót w pigułkach; dotknięcie rozwija ją
 * w regulatory. Wygląd jak w Treningu - ciemna karta z obwódką zamiast
 * szarego prostokąta, żeby obie zakładki wyglądały na jedną aplikację.
 *
 * Zmiany idą do szkicu ([UnitDraft]), a do bazy dopiero przyciskiem Zapisz
 * - jak w Profilu. Przycisk i pytanie przy wyjściu daje miejsce wywołania:
 * edycja ma je w nagłówku, kreator zapisuje sam przy „Gotowe".
 *
 * Ten sam ekran służy w kreatorze nowego planu i przy zwykłej edycji - dwa
 * edytory rozjechałyby się przy pierwszej zmianie.
 *
 * Propozycje progresji siedzą przy kartach: to tu podejmuje się decyzję,
 * widząc obok siebie to, co w planie, i to, co z treningu wynika.
 */
@Composable
internal fun UnitEditorScreen(
    draft: UnitDraft,
    profile: UserProfile,
    inventory: Inventory,
    highlight: Long? = null,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val planRepo = remember { PlanRepository(context) }

    var expanded by remember(draft.planId, highlight) { mutableStateOf(highlight) }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(draft.planId) {
        val (items, plan) = withContext(Dispatchers.IO) {
            planRepo.items(draft.planId) to planRepo.planById(draft.planId)
        }
        if (plan != null) draft.load(items, plan)
        // Zajrzenie w jednostkę gasi plakietkę, ale propozycje zostają do decyzji.
        withContext(Dispatchers.IO) { planRepo.markAdviceSeen() }
    }
    // Skok z historii: do ćwiczenia z propozycją, gdy tylko lista jest gotowa.
    LaunchedEffect(highlight, draft.loaded) {
        val i = draft.items.indexOfFirst { it.id == highlight }
        if (i >= 0) listState.animateScrollToItem(i + 1)
    }

    if (picking) {
        ExercisePickerScreen(
            profile = profile,
            alreadyIn = draft.items.map { it.exerciseId }.toSet(),
            onBack = { picking = false },
            onAdd = { chosen ->
                draft.add(chosen)
                picking = false
            }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        header()

        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            // Wszystkie propozycje naraz - po treningu bywa ich kilka, a klikanie
            // po kolei zniechęca do całego mechanizmu.
            item(key = "propozycje") {
                val czeka = draft.pendingAdvice
                if (czeka > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f))
                            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            plural(czeka, "propozycja", "propozycje", "propozycji") + " po treningu",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { draft.acceptAll() }) { Text("Zastosuj wszystkie") }
                        TextButton(onClick = { draft.skipAll() }) {
                            Text("Pomiń", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            itemsIndexed(draft.items, key = { _, it -> it.id }) { index, item ->
                ExerciseCard(
                    item = item,
                    number = index + 1,
                    inventory = inventory,
                    expanded = expanded == item.id,
                    highlighted = highlight == item.id,
                    first = index == 0,
                    last = index == draft.items.lastIndex,
                    onToggle = { expanded = if (expanded == item.id) null else item.id },
                    onChange = draft::update,
                    onMove = { up -> draft.move(item, up) },
                    onDelete = { draft.remove(item) },
                    onAdvice = { accept -> if (accept) draft.accept(item) else draft.skip(item) }
                )
            }

            // Dopiero po wczytaniu. Obecny od początku był jedynym elementem
            // listy, a lista trzyma się pierwszego widocznego - po wczytaniu
            // ćwiczeń przewijała się do niego, czyli na sam dół, i pierwsze
            // ćwiczenie chowało się nad krawędzią.
            if (draft.loaded) item(key = "dodaj") {
                if (draft.items.isEmpty()) {
                    Text(
                        "Jednostka jest pusta. Dodaj ćwiczenia - zaznaczysz kilka naraz, " +
                            "a serie, powtórzenia i ciężar ustawisz potem na kartach.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
                AddExercisesButton { picking = true }
                Spacer(Modifier.height(12.dp))
            }
        }

        footer()
    }
}

/** Ten sam przerywany przycisk co „Rozpocznij inny trening" - dokładanie czegoś nowego. */
@Composable
private fun AddExercisesButton(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            .tap(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "Dodaj ćwiczenia",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Skrót karty w pigułkach: „3 × 8-12", „60 kg", „2:00" - pomija to, czego nie ustawiono. */
private fun summaryPills(item: PlanItem, inventory: Inventory): List<String> = buildList {
    add(if (item.hasRepRange) "${item.sets} × ${item.repRange}" else plural(item.sets, "seria", "serie", "serii"))
    if (item.hasTargetWeight) {
        val r = PlateCalculator.resolve(item.targetKg, item.equipment, inventory)
        add("${Loading.fmt(r.actualKg)} ${item.equipment.weightUnitLabel}")
    } else add("bez ciężaru")
    if (item.hasRest) add("${item.restSec / 60}:%02d".format(item.restSec % 60))
    if (item.progress != ProgressMode.ALL) add(item.progress.label.lowercase())
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * Karta ćwiczenia: zwinięta to skrót, rozwinięta - regulatory.
 *
 * Rozwinięta jest jedna naraz. Sześć rozwiniętych kart to kilka ekranów
 * przewijania, w których traci się, przy którym ćwiczeniu się jest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    item: PlanItem,
    number: Int,
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
    val mode = LoadMode.of(item.equipment, inventory)
    // Które pole się wpisuje: serie, powtórzenia od/do, ciężar, przerwa.
    var typing by remember { mutableStateOf<String?>(null) }
    val close = { typing = null }
    when (typing) {
        "sets" -> NumberEntrySheet(
            title = item.exerciseName + " - serie", initial = item.sets.toDouble(),
            kind = NumberKind.COUNT, max = 10.0, onDismiss = close,
            onPick = { onChange(item.copy(sets = it.toInt())); close() }
        )
        "from" -> NumberEntrySheet(
            title = item.exerciseName + " - powtórzenia od", initial = item.repsFrom.toDouble(),
            kind = NumberKind.COUNT, max = 50.0, onDismiss = close,
            // Jak przy plusie: dolna granica pcha górną, zakres się nie odwraca.
            onPick = { v ->
                val od = v.toInt()
                onChange(item.copy(repsFrom = od, repsTo = maxOf(item.repsTo, od)))
                close()
            }
        )
        "to" -> NumberEntrySheet(
            title = item.exerciseName + " - powtórzenia do", initial = item.repsTo.toDouble(),
            kind = NumberKind.COUNT, max = 50.0, onDismiss = close,
            onPick = { v ->
                val doo = v.toInt()
                onChange(item.copy(repsTo = doo, repsFrom = minOf(item.repsFrom, doo)))
                close()
            }
        )
        "kg" -> NumberEntrySheet(
            title = item.exerciseName + " - ciężar", initial = item.targetKg,
            kind = NumberKind.WEIGHT, unit = item.equipment.weightUnitLabel,
            values = weightValues(mode, inventory, item.step), onDismiss = close,
            onPick = { onChange(item.copy(targetKg = it)); close() }
        )
        "rest" -> NumberEntrySheet(
            title = item.exerciseName + " - przerwa", initial = item.restSec.toDouble(),
            kind = NumberKind.TIME, onDismiss = close,
            onPick = { onChange(item.copy(restSec = it.toInt())); close() }
        )
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Ćwiczenie, do którego skoczyliśmy z historii, ma obwódkę w kolorze
        // akcji - tło zmieniało całą kartę i gryzło się z propozycją w środku.
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().tap(RoundedCornerShape(8.dp), onClick = onToggle),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        item.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        summaryPills(item, inventory).forEach { Pill(it) }
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            HorizontalDivider(
                Modifier.padding(top = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // ---- Serie ----
            ControlRow("Serie") {
                MiniStepper(
                    value = "${item.sets}",
                    onMinus = { if (item.sets > 1) onChange(item.copy(sets = item.sets - 1)) },
                    onPlus = { if (item.sets < 10) onChange(item.copy(sets = item.sets + 1)) },
                    onValueClick = { typing = "sets" }
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
                        },
                        onValueClick = { typing = "from" }
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
                        },
                        onValueClick = { typing = "to" }
                    )
                }
                // Tylko tam, gdzie to prawda: przy ciężarze, który ma stać,
                // obietnica większego ciężaru kłóciła się z wyborem niżej.
                if (item.progress.weight) {
                    Text(
                        "Gdy wyrobisz górną granicę, aplikacja zaproponuje większy ciężar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
                    // Wpisany ciężar dociąga się do tego, co da się złożyć
                    // z talerzy - ciężar, którego nie da się nałożyć, nie jest ciężarem.
                    onLabelClick = { typing = "kg" }
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
                }, modifier = Modifier.padding(top = 6.dp)) { Text("Ustaw ciężar") }
            }

            // ---- Przerwa ----
            FieldLabel("Przerwa")
            val przerwy = listOf(0 to "brak", 60 to "1:00", 90 to "1:30", 120 to "2:00", 180 to "3:00")
            val wlasna = item.restSec.takeIf { r -> przerwy.none { it.first == r } }
                ?.let { listOf(it to "${it / 60}:%02d".format(it % 60)) }.orEmpty()
            // Ostatni przycisk otwiera wpisywanie - przerwa bywa i 45 s, i 2:30.
            ChipRow(
                options = przerwy + wlasna + listOf(-1 to "wpisz..."),
                selected = item.restSec,
                onSelect = { if (it == -1) typing = "rest" else onChange(item.copy(restSec = it ?: 0)) },
                allowNone = false
            )

            // ---- Progresja ----
            // Pełna domyślnie: milczenie kosztuje więcej niż propozycja, więc
            // każde ograniczenie ma być decyzją, a nie stanem wyjściowym.
            //
            // Cztery tryby zamiast przełącznika. Sam przełącznik znał tylko
            // "wszystko albo nic", a przy jednej parze hantli chce się
            // powtórzeń bez ciężaru, przy stałej liczbie powtórzeń - odwrotnie.
            FieldLabel("Progresja")
            ProgressPicker(item.progress) { onChange(item.copy(progress = it)) }
            Text(
                when (item.progress) {
                    ProgressMode.ALL -> "Aplikacja dokłada powtórzeń, a po wyrobieniu zakresu " +
                        "proponuje większy ciężar."
                    ProgressMode.NO_WEIGHT -> "Ciężar stoi. Rosną powtórzenia, a po dobiciu " +
                        "zakresu - sam zakres."
                    ProgressMode.NO_REPS -> "Powtórzenia stoją. Ciężar rośnie, gdy dobijesz " +
                        "górną granicę zakresu."
                    ProgressMode.OFF -> "Nic nie rośnie - dla rozgrzewkowych " +
                        "i rehabilitacyjnych."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            HorizontalDivider(
                Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMove(true) }, enabled = !first) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Wyżej")
                }
                IconButton(onClick = { onMove(false) }, enabled = !last) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Niżej")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Usuń z jednostki",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Cztery tryby progresji w dwóch rzędach po dwa.
 *
 * W jednym przewijanym rzędzie, jak przerwa, się nie mieszczą: czwarty
 * przycisk wychodził za krawędź karty i nic nie zdradzało, że rząd da się
 * przesunąć - „bez powtórzeń" po prostu nie było widać. W lewej kolumnie
 * całość (pełna, wyłączona), w prawej połówki.
 */
@Composable
private fun ProgressPicker(selected: ProgressMode, onSelect: (ProgressMode) -> Unit) {
    val rzedy = listOf(
        listOf(ProgressMode.ALL, ProgressMode.NO_WEIGHT),
        listOf(ProgressMode.OFF, ProgressMode.NO_REPS)
    )
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rzedy.forEach { rzad ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rzad.forEach { tryb ->
                    if (tryb == selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text(tryb.label, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect(tryb) },
                            modifier = Modifier.weight(1f)
                        ) { Text(tryb.label, style = MaterialTheme.typography.labelLarge) }
                    }
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

/**
 * Nagłówek edycji jednostki z przyciskiem Zapisz - jak w Profilu.
 *
 * Poza przewijaniem, bo przycisk dotyczy całej jednostki i musi być widać go
 * z każdego miejsca listy. Podtytuł mówi, czy coś czeka na zapis - zapalony
 * przycisk sam w sobie łatwo przeoczyć.
 */
@Composable
internal fun UnitEditorHeader(
    programName: String,
    draft: UnitDraft,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
        }
        Column(Modifier.weight(1f).tap(RoundedCornerShape(8.dp), onClick = onRename)) {
            Text(
                programName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(draft.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Button(onClick = onSave, enabled = draft.dirty) { Text("Zapisz") }
    }
    Text(
        if (draft.dirty) "Niezapisane zmiany" else "Dotknij nazwy, żeby ją zmienić",
        style = MaterialTheme.typography.bodySmall,
        color = if (draft.dirty) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 48.dp)
    )
    Spacer(Modifier.height(10.dp))
}

/**
 * Szkic jednostki w kreatorze, zapisywany sam przy zniknięciu ekranu.
 *
 * W kreatorze nie ma przycisku Zapisz: nowa jednostka powstaje po to, żeby
 * zostać, więc szkic zapisuje się przy każdym wyjściu - „Gotowe", kolejna
 * jednostka, wstecz, a nawet zmiana zakładki. Stąd zapis przy zniknięciu
 * ekranu, w zakresie, który przeżyje sam ekran.
 */
@Composable
internal fun rememberAutoSavedDraft(plan: Plan): UnitDraft {
    val context = LocalContext.current
    val planRepo = remember { PlanRepository(context) }
    val draft = remember(plan.id) { UnitDraft(plan) }
    DisposableEffect(draft) {
        onDispose {
            if (draft.dirty) {
                CoroutineScope(Dispatchers.IO).launch {
                    draft.commit(planRepo)
                    Cache.clear()
                    withContext(Dispatchers.Main) { Refresh.bump() }
                }
            }
        }
    }
    return draft
}

// ---- Nowa jednostka -----------------------------------------------------------

private enum class UnitPhase { SETUP, LIBRARY, EXERCISES }

/**
 * Nowa jednostka w krokach: nazwa, potem ćwiczenia z obciążeniem.
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
                            planRepo.insertPlan(name.trim(), n, program.id)
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
                            programRepo.addUnitFromLibrary(program.id, unit)
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
            val draft = rememberAutoSavedDraft(plan)
            UnitEditorScreen(
                draft = draft,
                profile = profile,
                inventory = inventory,
                header = {
                    StepHeader(title, stepOffset + 1, totalSteps, onDone)
                    Text(plan.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Ćwiczenia i obciążenie",
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
                        onClick = { open = if (rozwinieta) null else unit },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
