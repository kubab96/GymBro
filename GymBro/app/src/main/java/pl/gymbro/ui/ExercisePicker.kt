package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.DEFAULT_STEP_KG
import pl.gymbro.data.Equipment
import pl.gymbro.data.Exercise
import pl.gymbro.data.ExerciseRepository
import pl.gymbro.data.TextSearch
import pl.gymbro.data.UserProfile
import pl.gymbro.gym.Loading
import pl.gymbro.gym.MuscleMap

/**
 * Dodawanie ćwiczeń do jednostki - cały ekran, nie okno.
 *
 * Wzorowane na Hevy: zaznaczasz kilka ćwiczeń i dodajesz je jednym
 * przyciskiem. Okno dialogowe z poprzedniej wersji dodawało jedno ćwiczenie
 * na raz i zamykało się po każdym - sześć ćwiczeń to było sześć wejść, sześć
 * wyszukiwań i lista oglądana przez szparę na trzy wiersze.
 *
 * Kolejność zaznaczania jest kolejnością w jednostce: kto klika „przysiad,
 * potem wyciskanie", chce je w tej kolejności, a nie alfabetycznie.
 *
 * Sprzęt idzie za Profilem, jak wszędzie (patrz [GearMode]): przy liście
 * sprzętu widać tylko to, co da się zrobić, z przełącznikiem na wszystko.
 */
@Composable
internal fun ExercisePickerScreen(
    profile: UserProfile,
    /** Ćwiczenia, które już są w jednostce - do podpisu, nie do blokady. */
    alreadyIn: Set<Long>,
    onBack: () -> Unit,
    onAdd: (List<Exercise>) -> Unit
) {
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ExerciseRepository(context) }

    var all by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    var unused by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<Long>>(emptyList()) }
    // Formularz ćwiczenia: null - zamknięty; Exercise z id 0 - nowe.
    var form by remember { mutableStateOf<Exercise?>(null) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) { repo.all() to repo.unusedIds() }
        // Polski porządek alfabetu, nie bajtowy z SQLite: tam „Ściąganie"
        // i „Łydki" lądowały na samym końcu, za „Zwisem".
        val collator = java.text.Collator.getInstance(java.util.Locale("pl", "PL"))
        all = loaded.first.sortedWith { a, b -> collator.compare(a.name, b.name) }
        unused = loaded.second
    }
    LaunchedEffect(Unit) { reload() }

    val editing = form
    if (editing != null) {
        ExerciseFormScreen(
            existing = editing.takeIf { it.id != 0L },
            canDelete = editing.id in unused,
            onBack = { form = null },
            onSave = { saved ->
                scope.launch {
                    val id = withContext(Dispatchers.IO) {
                        if (saved.id == 0L) repo.insert(saved) else { repo.update(saved); saved.id }
                    }
                    reload()
                    // Świeżo utworzone od razu zaznaczone - po to się je tworzyło.
                    if (saved.id == 0L && id > 0 && id !in picked) picked = picked + id
                    form = null
                    Refresh.bump()
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.delete(editing.id) }
                    picked = picked - editing.id
                    reload()
                    form = null
                }
            }
        )
        return
    }

    val gear = GearMode(profile)
    val words = remember(query) { TextSearch.words(query) }
    val visible = all.filter { e ->
        (muscle == null || e.muscleGroup.equals(muscle, ignoreCase = true)) &&
            (!gear.limited || showAll || profile.canDoExercise(e.name, e.equipment)) &&
            TextSearch.matches(
                words,
                TextSearch.fold(e.name + " " + e.muscleGroup + " " + equipmentName(e.equipment))
            )
    }
    val byId = remember(all) { all.associateBy { it.id } }

    // imePadding: przy wpisywaniu klawiatura przykrywałaby dolny przycisk
    // „Dodaj" - dokładnie ten, którego się wtedy szuka.
    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader("Dodaj ćwiczenia", 0, 1, onBack)
        SearchField(
            value = query,
            onChange = { query = it },
            placeholder = "Szukaj: nazwa, partia, sprzęt"
        )
        ChipRow(
            options = MuscleMap.GROUPS.map { it to it },
            selected = muscle,
            onSelect = { muscle = it }
        )
        if (gear.limited) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showAll) "Wszystkie ćwiczenia, też spoza Twojego sprzętu"
                    else "Tylko na Twój sprzęt z Profilu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = showAll, onCheckedChange = { showAll = it })
            }
        }
        Text(
            plural(visible.size, "ćwiczenie", "ćwiczenia", "ćwiczeń") +
                if (picked.isNotEmpty()) " · zaznaczone: ${picked.size}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(visible, key = { it.id }) { e ->
                val order = picked.indexOf(e.id)
                ExerciseRow(
                    exercise = e,
                    order = order,
                    inUnit = e.id in alreadyIn,
                    onToggle = {
                        picked = if (order >= 0) picked - e.id else picked + e.id
                    },
                    onEdit = { form = e }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "Nic nie pasuje. Zmień szukanie albo utwórz własne ćwiczenie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { form = Exercise(name = "", equipment = Equipment.BARBELL, muscleGroup = "") },
                modifier = Modifier.weight(1f)
            ) { Text("+ Nowe ćwiczenie") }
            Button(
                onClick = { onAdd(picked.mapNotNull { byId[it] }) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(if (picked.isEmpty()) "Dodaj" else "Dodaj (${picked.size})") }
        }
    }
}

/**
 * Wiersz ćwiczenia z kółkiem wyboru.
 *
 * Kółko pokazuje numer zaznaczenia, nie sam ptaszek: od razu widać, w jakiej
 * kolejności ćwiczenia trafią do jednostki.
 */
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    order: Int,
    inUnit: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val selected = order >= 0
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    "${order + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                exercise.muscleGroup + " · " + equipmentName(exercise.equipment).lowercase() +
                    if (inUnit) " · już w tej jednostce" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (inUnit) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Edytuj", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Ćwiczenie w bibliotece: nowe albo poprawiane - cały ekran.
 *
 * Jeden formularz na oba przypadki, bo pola są te same, a dwa osobne
 * rozjechałyby się przy pierwszej zmianie. Poprawianie istnieje, bo literówka
 * w nazwie albo źle wybrany sprzęt wymagały dawniej skasowania - czego przy
 * ćwiczeniu z historią nie wolno zrobić.
 *
 * Kasować wolno wyłącznie to, czego nie trzyma żaden plan ani historia:
 * ćwiczenie usunięte spod zapisanych serii zostawiłoby je bez nazwy i partii.
 */
@Composable
internal fun ExerciseFormScreen(
    existing: Exercise?,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: (Exercise) -> Unit,
    onDelete: () -> Unit
) {
    ScreenBackHandler(onBack = onBack)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var muscle by remember { mutableStateOf(existing?.muscleGroup ?: "") }
    var equipment by remember { mutableStateOf(existing?.equipment ?: Equipment.BARBELL) }
    var step by remember { mutableStateOf(existing?.stepKg?.let { Loading.fmt(it) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader(if (existing == null) "Nowe ćwiczenie" else "Popraw ćwiczenie", 0, 1, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FieldLabel("Partia")
            OutlinedTextField(
                value = muscle,
                onValueChange = { muscle = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ChipRow(
                options = MuscleMap.GROUPS.map { it to it },
                selected = MuscleMap.GROUPS.firstOrNull { it.equals(muscle.trim(), true) },
                onSelect = { muscle = it ?: "" }
            )
            // Ostrzeżenie dopiero przy wpisanej, nieznanej partii - przy
            // pustym polu byłoby pouczaniem, zanim ktokolwiek coś zrobił.
            if (muscle.isNotBlank() && MuscleMap.regionsOf(muscle).isEmpty()) {
                Text(
                    "Tej partii nie ma na sylwetce - ćwiczenie się zapisze, " +
                        "ale nie zaświeci się na mapie mięśni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            FieldLabel("Sprzęt")
            Text(
                "Decyduje o tym, jak liczymy ciężar - przy sztandze i hantlach z talerzy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Equipment.entries.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pair.forEach { eq ->
                        if (eq == equipment) {
                            Button(
                                onClick = { equipment = eq },
                                modifier = Modifier.weight(1f)
                            ) { Text(equipmentName(eq), maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { equipment = eq },
                                modifier = Modifier.weight(1f)
                            ) { Text(equipmentName(eq), maxLines = 1) }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // Krok tylko tam, gdzie nie wynika z talerzy. Przy sztandze byłby
            // polem, które nic nie robi.
            if (!equipment.isPlateLoaded) {
                FieldLabel("Krok ciężaru (kg)")
                OutlinedTextField(
                    value = step,
                    onValueChange = { step = it },
                    placeholder = { Text(Loading.fmt(DEFAULT_STEP_KG)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "O tyle zmieniają ciężar plus i minus - tu i na zegarku. Przepisz z tabliczki " +
                        "przy maszynie. Puste pole to ${Loading.fmt(DEFAULT_STEP_KG)} kg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (existing != null && canDelete) {
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Usuń z biblioteki", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                onSave(
                    (existing ?: Exercise(name = "", equipment = equipment, muscleGroup = "", isCustom = true))
                        .copy(
                            name = name.trim(),
                            equipment = equipment,
                            muscleGroup = muscle.trim().ifEmpty { "inne" },
                            // Krok ma sens wyłącznie przy sprzęcie bez talerzy.
                            stepKg = if (equipment.isPlateLoaded) null
                            else step.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                        )
                )
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) { Text(if (existing == null) "Utwórz ćwiczenie" else "Zapisz zmiany") }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć ćwiczenie?") },
            text = {
                Text(
                    "${existing.name} zniknie z biblioteki. Nie ma go w żadnym planie " +
                        "ani w zapisanym treningu, więc nic poza tym nie ucierpi."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } }
        )
    }
}
