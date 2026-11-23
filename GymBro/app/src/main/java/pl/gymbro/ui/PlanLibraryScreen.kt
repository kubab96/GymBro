package pl.gymbro.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Inventory
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.PlanLibrary
import pl.gymbro.data.PlanLibrary.Level
import pl.gymbro.data.Program
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.UserProfile
import pl.gymbro.gym.Loading

/** Kroki kreatora nowego planu, z obu ścieżek. */
private enum class WizardStep { START, NAME, UNITS, DAYS, LIST, DETAIL }

/** Odpowiedź „jeszcze nie wiem" przy liczbie dni. */
private const val ANY = 0

/**
 * Nowy plan jako kreator w krokach - jak pomiar w zakładce Waga.
 *
 * Dwie ścieżki od pierwszego pytania, obie po cztery kroki:
 *
 * - **własny**: nazwa planu → nowa jednostka (nazwa) →
 *   ćwiczenia i obciążenie, z „Kolejna jednostka" albo „Gotowe" na końcu,
 * - **gotowy**: ile razy w tygodniu → plany z filtrami → plan rozpisany na
 *   ćwiczenia, z „Wybierz ten plan".
 *
 * Bez okien po drodze. Poprzednia wersja otwierała ramkę z nazwą, potem
 * pusty plan, potem okno nowej jednostki, okno wyboru ćwiczenia i okno
 * edycji każdego z nich - pięć różnych okien, zanim plan dało się zrobić.
 *
 * Gotowy plan zapisuje się dopiero po „Wybierz ten plan" i od razu otwiera
 * do dostosowania - tym samym edytorem jednostki, którym robi się własny.
 * Dostosowanie przed zapisem wymagałoby drugiego edytora na kopii w pamięci,
 * a dwa edytory rozjechałyby się przy pierwszej zmianie.
 *
 * @param onFinished plan gotowy; fromTemplate mówi, czy z bazy - wtedy
 *   lista jednostek pokazuje, gdzie się go dostosowuje.
 */
@Composable
fun PlanLibraryScreen(
    profile: UserProfile,
    inventory: Inventory,
    onBack: () -> Unit,
    onFinished: (programId: Long, fromTemplate: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val programRepo = remember { ProgramRepository(context) }

    var step by rememberSaveable { mutableStateOf(WizardStep.START) }
    var planName by rememberSaveable { mutableStateOf("") }
    var created by remember { mutableStateOf<Program?>(null) }
    var days by rememberSaveable { mutableStateOf<Int?>(null) }
    var level by rememberSaveable { mutableStateOf<Level?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<PlanLibrary.ProgramTemplate?>(null) }

    // Szkic gotowego planu: zapisany przy pierwszym dotknięciu jednostki,
    // bo edytor ćwiczeń pracuje na bazie i musi mieć do czego pisać.
    // „Wybierz ten plan" go zatwierdza, każde inne wyjście kasuje - inaczej
    // samo obejrzenie planu zostawiałoby go na liście.
    val planRepo = remember { PlanRepository(context) }
    var draftId by remember { mutableStateOf<Long?>(null) }
    var editingUnit by remember { mutableStateOf<Plan?>(null) }
    var draftUnits by remember { mutableStateOf<List<Pair<Plan, List<PlanItem>>>>(emptyList()) }

    LaunchedEffect(draftId, editingUnit) {
        val id = draftId
        draftUnits = if (id == null) emptyList()
        else withContext(Dispatchers.IO) {
            planRepo.plansOf(id).map { it to planRepo.items(it.id) }
        }
    }

    // Wyjście z kreatora inną drogą niż przyciski - przełączenie zakładki,
    // ponowne dotknięcie Planu na pasku. Własny scope, bo ten z ekranu
    // umiera razem z nim i kasowanie mogłoby się nie wykonać.
    DisposableEffect(Unit) {
        onDispose { draftId?.let { deleteDraft(programRepo, it) } }
    }

    /** Kasuje szkic i dopiero potem robi [then] - lista planów ma go już nie widzieć. */
    fun discardDraft(then: () -> Unit) {
        val id = draftId
        draftId = null
        editingUnit = null
        if (id == null) return then()
        scope.launch {
            withContext(Dispatchers.IO) { programRepo.delete(id) }
            Refresh.bump()
            then()
        }
    }

    /** Otwiera jednostkę do edycji, zakładając szkic przy pierwszym razie. */
    fun openUnit(template: PlanLibrary.ProgramTemplate, index: Int) {
        scope.launch {
            val id = draftId ?: withContext(Dispatchers.IO) {
                programRepo.addFromLibrary(template)
            }.also { draftId = it }
            editingUnit = withContext(Dispatchers.IO) { planRepo.plansOf(id) }.getOrNull(index)
        }
    }

    val index = when (step) {
        WizardStep.START -> 0
        WizardStep.NAME, WizardStep.DAYS -> 1
        WizardStep.LIST, WizardStep.UNITS -> 2
        WizardStep.DETAIL -> 3
    }
    val title = when (step) {
        WizardStep.NAME, WizardStep.UNITS -> "Własny plan"
        WizardStep.DAYS, WizardStep.LIST, WizardStep.DETAIL -> "Gotowy plan"
        WizardStep.START -> "Nowy plan"
    }

    fun back() {
        if (editingUnit != null) { editingUnit = null; return }
        step = when (step) {
            WizardStep.START -> { onBack(); return }
            WizardStep.NAME, WizardStep.DAYS -> WizardStep.START
            WizardStep.LIST -> WizardStep.DAYS
            WizardStep.DETAIL -> { discardDraft { step = WizardStep.LIST }; return }
            WizardStep.UNITS -> WizardStep.NAME
        }
    }

    fun cancel() = discardDraft(onBack)
    // Krok jednostek ma własne cofanie (NewUnitFlow) - ten byłby pod nim.
    ScreenBackHandler(enabled = step != WizardStep.UNITS) { back() }

    /**
     * Wyjście z kroku jednostek.
     *
     * Plan bez ani jednej jednostki to śmieć na liście - powstał tylko dlatego,
     * że kreator zapisuje go przed pierwszą jednostką. Wtedy go kasujemy.
     */
    fun leaveUnits() {
        val p = created ?: return onBack()
        scope.launch {
            val puste = withContext(Dispatchers.IO) {
                (programRepo.planCount(p.id) == 0).also { if (it) programRepo.delete(p.id) }
            }
            Refresh.bump()
            if (puste) onBack() else onFinished(p.id, false)
        }
    }

    val p = created
    if (step == WizardStep.UNITS && p != null) {
        NewUnitFlow(
            program = p,
            title = "Własny plan",
            stepOffset = 2,
            totalSteps = 4,
            profile = profile,
            inventory = inventory,
            onCancel = ::leaveUnits,
            onDone = ::leaveUnits
        )
        return
    }

    // Jednostka gotowego planu w edycji - ten sam edytor co przy własnym
    // planie i przy zwykłym wejściu w jednostkę, tylko w kroku 4 z 4.
    val editing = editingUnit
    val t = detail
    if (step == WizardStep.DETAIL && editing != null && t != null) {
        val draft = rememberAutoSavedDraft(editing)
        UnitEditorScreen(
            draft = draft,
            profile = profile,
            inventory = inventory,
            header = {
                StepHeader(title, index, 4, ::cancel)
                Text(
                    t.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(editing.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Dodaj, usuń albo przestaw ćwiczenia i ustaw ciężary. Zmiany " +
                        "zostaną w planie, gdy go wybierzesz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            },
            footer = {
                Button(
                    // Zapis przed powrotem, a nie w tle przy zniknięciu ekranu:
                    // rozpisany plan wczytuje jednostkę od razu i pokazałby
                    // ją sprzed zmian.
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { if (draft.dirty) draft.commit(PlanRepository(context)) }
                            editingUnit = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                ) { Text("Wróć do planu", modifier = Modifier.padding(vertical = 4.dp)) }
            }
        )
        return
    }

    // imePadding: pole nazwy i pole szukania wywołują klawiaturę, a pod nią
    // lądowałyby przycisk „Dalej" i wyniki.
    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader(title, index, 4, ::cancel)

        Crossfade(targetState = step, label = "krok", modifier = Modifier.weight(1f)) { current ->
            when (current) {
                WizardStep.START -> StepColumn("Jak chcesz zacząć?") {
                    ChoiceCard(
                        title = "Gotowy plan",
                        subtitle = "Wybierz oraz dostosuj gotowy plan",
                        selected = false,
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        onClick = { step = WizardStep.DAYS }
                    )
                    ChoiceCard(
                        title = "Własny plan",
                        subtitle = "Stwórz oraz dostosuj własny plan",
                        selected = false,
                        icon = Icons.Filled.Edit,
                        onClick = { step = WizardStep.NAME }
                    )
                }

                WizardStep.NAME, WizardStep.UNITS -> Column(Modifier.fillMaxSize()) {
                    StepColumn("Jak nazwiesz plan?", Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = planName,
                            onValueChange = { planName = it },
                            placeholder = { Text("np. Mój FBW") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Plan to cały schemat. W następnym kroku dodasz jego " +
                                "pierwszą jednostkę - jeden trening z tego planu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                // Ponowne „Dalej" po cofnięciu nie zakłada
                                // drugiego planu - poprawia nazwę pierwszego.
                                val gotowy = created
                                created = withContext(Dispatchers.IO) {
                                    if (gotowy != null) {
                                        programRepo.rename(gotowy.id, planName.trim())
                                        gotowy.copy(name = planName.trim())
                                    } else {
                                        val id = programRepo.insert(planName.trim())
                                        programRepo.all().first { it.id == id }
                                    }
                                }
                                step = WizardStep.UNITS
                            }
                        },
                        enabled = planName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) { Text("Dalej", modifier = Modifier.padding(vertical = 4.dp)) }
                }

                WizardStep.DAYS -> StepColumn("Ile razy w tygodniu?") {
                    listOf(
                        2 to "całe ciało, minimum, które działa",
                        3 to "najczęstszy wybór - całe ciało albo PPL",
                        4 to "góra i dół albo pchanie i ciągnięcie",
                        5 to "podział na partie",
                        6 to "PPL dwa razy w tygodniu"
                    ).forEach { (n, opis) ->
                        ChoiceCard(
                            title = plural(n, "trening", "treningi", "treningów"),
                            subtitle = opis,
                            selected = days == n,
                            onClick = { days = n; step = WizardStep.LIST }
                        )
                    }
                    ChoiceCard(
                        title = "Jeszcze nie wiem",
                        subtitle = "Pokażę plany na każdą liczbę dni.",
                        selected = days == ANY,
                        onClick = { days = ANY; step = WizardStep.LIST }
                    )
                }

                WizardStep.LIST -> PlanListStep(
                    profile = profile,
                    days = days?.takeIf { it != ANY },
                    level = level,
                    onLevel = { level = it },
                    showAll = showAll,
                    onShowAll = { showAll = it },
                    query = query,
                    onQuery = { query = it },
                    onOpen = { detail = it; step = WizardStep.DETAIL },
                    onOwn = { step = WizardStep.NAME }
                )

                WizardStep.DETAIL -> {
                    val tpl = detail
                    if (tpl == null) {
                        Text("Wczytuję…")
                    } else {
                        PlanDetailStep(
                            template = tpl,
                            profile = profile,
                            draft = draftUnits.takeIf { draftId != null },
                            onUnit = { i -> openUnit(tpl, i) },
                            onPick = {
                                scope.launch {
                                    val id = draftId ?: withContext(Dispatchers.IO) {
                                        programRepo.addFromLibrary(tpl)
                                    }
                                    // Szkic staje się planem - nie wolno go już
                                    // skasować przy wyjściu z kreatora.
                                    draftId = null
                                    Refresh.bump()
                                    onFinished(id, true)
                                }
                            }
                        )
                    }
                }
            }
        }

        if (step != WizardStep.START) {
            TextButton(
                onClick = { back() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                // Cofnięcie ze szkicu kasuje zmiany w jednostkach - lepiej
                // powiedzieć to na przycisku niż po fakcie.
                Text(if (draftId != null) "Wstecz - odrzuć zmiany" else "Wstecz")
            }
        }
    }
}

/**
 * Kasuje szkic gotowego planu poza ekranem, który właśnie znika.
 *
 * Osobny scope, bo scope ekranu jest anulowany razem z nim - kasowanie
 * odpalone z onDispose mogłoby nie wystartować i szkic zostałby na liście.
 */
private fun deleteDraft(repo: ProgramRepository, id: Long) {
    CoroutineScope(Dispatchers.IO).launch {
        repo.delete(id)
        withContext(Dispatchers.Main) { Refresh.bump() }
    }
}

// ---- Krok: lista planów ---------------------------------------------------

/**
 * Plany na wybraną liczbę dni, z filtrami poziomu i sprzętu.
 *
 * Poziom jest tu filtrem, nie osobnym pytaniem: liczba dni zawęża już do
 * kilku planów, a przy kilku łatwiej spojrzeć niż odpowiadać.
 */
@Composable
private fun PlanListStep(
    profile: UserProfile,
    days: Int?,
    level: Level?,
    onLevel: (Level?) -> Unit,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (PlanLibrary.ProgramTemplate) -> Unit,
    onOwn: () -> Unit
) {
    val gear = GearMode(profile)
    val match = remember(days, level, showAll, query, profile) {
        PlanLibrary.match(days, level, gear.fits(showAll), query)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "naglowek") {
            Text(
                days?.let { "Plany na " + plural(it, "trening", "treningi", "treningów") }
                    ?: "Wszystkie plany",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                options = Level.entries.map { it to it.label },
                selected = level,
                onSelect = onLevel
            )
            GearModeRow(gear = gear, showAll = showAll, onShowAll = onShowAll, what = "plany")
            Spacer(Modifier.height(6.dp))
            SearchField(query, onQuery, "Zawęź: nazwa, ćwiczenie, partia")
            relaxNote(match, days, level)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (match.plans.isEmpty()) {
                Text(
                    "Nic nie pasuje do wpisanego tekstu. Wyczyść pole albo zmień filtry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        items(match.plans, key = { it.name }) { template ->
            ProgramTemplateCard(template, profile) { onOpen(template) }
        }

        item(key = "wlasny") {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOwn, modifier = Modifier.fillMaxWidth()) {
                Text("Żaden nie pasuje - stwórz własny")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Co kreator musiał poluzować - zdaniem, żeby nie wyglądało na błąd filtra. */
private fun relaxNote(match: PlanLibrary.Match, days: Int?, level: Level?): String? {
    val dni = days?.let { plural(it, "trening", "treningi", "treningów") }
    return when {
        match.droppedLevel && match.droppedDays ->
            "Nic nie pasuje do wszystkich warunków naraz - pokazuję plany bez " +
                "warunku dni i poziomu."
        match.droppedLevel ->
            "Na $dni nie ma planu dla poziomu „${level?.label}” - pokazuję " +
                "pozostałe poziomy."
        match.droppedDays ->
            "Dla poziomu „${level?.label}” nie ma planu na $dni - pokazuję " +
                "inną liczbę dni."
        else -> null
    }
}

// ---- Krok: plan rozpisany -------------------------------------------------

/** Jeden wiersz ćwiczenia na karcie jednostki: nazwa i „4 × 8-12 · 60 kg". */
private data class DetailRow(val name: String, val prescription: String)

/**
 * Plan z bazy na całym ekranie: jednostki i ich ćwiczenia.
 *
 * Dotknięcie jednostki otwiera od razu edytor jej ćwiczeń - samo wypisanie
 * kazało najpierw wybrać plan, potem szukać go na liście i dopiero tam
 * zmieniać, a kto chce wyrzucić jedno ćwiczenie, chce to zrobić tu.
 *
 * Póki nic nie zmieniono, karty pokazują wzorzec. Po pierwszej edycji -
 * szkic z bazy ([draft]), żeby po powrocie z edytora było widać zmiany.
 */
@Composable
private fun PlanDetailStep(
    template: PlanLibrary.ProgramTemplate,
    profile: UserProfile,
    draft: List<Pair<Plan, List<PlanItem>>>?,
    onUnit: (Int) -> Unit,
    onPick: () -> Unit
) {
    val units: List<Triple<String, String, List<DetailRow>>> =
        if (draft != null && draft.isNotEmpty()) {
            draft.mapIndexed { i, (plan, items) ->
                Triple(
                    plan.name,
                    template.units.getOrNull(i)?.note.orEmpty(),
                    items.map { item ->
                        DetailRow(
                            item.exerciseName,
                            buildString {
                                append("${item.sets} × ")
                                append(if (item.hasRepRange) item.repRange else "-")
                                if (item.hasTargetWeight) append(" · ${Loading.fmt(item.targetKg)} kg")
                            }
                        )
                    }
                )
            }
        } else {
            template.units.map { u ->
                Triple(
                    u.name,
                    u.note,
                    u.items.map { DetailRow(it.exercise, "${it.sets} × ${it.repsFrom}-${it.repsTo}") }
                )
            }
        }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(template.name, style = MaterialTheme.typography.headlineMedium)
            TemplatePills(template, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Text(template.description, style = MaterialTheme.typography.bodyMedium)
            MissingGear(template.units, profile)

            GroupLabel("Jednostki")
            units.forEachIndexed { i, (name, note, rows) ->
                AppCard(Modifier.padding(bottom = 8.dp), onClick = { onUnit(i) }) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "trening ${i + 1}" + if (note.isNotEmpty()) " · $note" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Filled.Edit, contentDescription = "Edytuj",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(top = 10.dp, bottom = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        if (rows.isEmpty()) {
                            Text(
                                "Bez ćwiczeń - dotknij, żeby dodać.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        rows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                TagPill(row.prescription)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            if (draft != null) "Zmiany w jednostkach zostaną w planie, gdy go wybierzesz."
            else "Dotknij jednostki, żeby od razu zmienić jej ćwiczenia i ciężary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Button(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("Wybierz ten plan", modifier = Modifier.padding(vertical = 4.dp)) }
    }
}

// ---- Elementy wspólne -----------------------------------------------------

/**
 * Dni, poziom i sprzęt w pigułkach - to, po czym porównuje się plany.
 *
 * Sprzęt wprost, zamiast zgadniętego „dom" albo „siłownia": tylko to da się
 * o planie powiedzieć na pewno. Pigułki zawijają się do drugiego rzędu, bo
 * lista sprzętu bywa długa, a ucięta mówiłaby mniej niż nic.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TemplatePills(t: PlanLibrary.ProgramTemplate, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TagPill(t.daysLabel, MaterialTheme.colorScheme.primary)
        TagPill(t.level.label, MaterialTheme.colorScheme.primary)
        TagPill(PlanLibrary.gearSummary(t.units))
    }
}

/**
 * Która ścieżka doboru sprzętu obowiązuje - wynika wyłącznie z Profilu.
 *
 * limited: czy jest lista sprzętu (przełącznik „Dostosuj sprzęt oraz
 * obciążenia" włączony i coś na liście). Bez niej aplikacja zakłada dostęp
 * do wszystkiego.
 */
internal class GearMode(private val profile: UserProfile) {
    val limited: Boolean = profile.ownedEquipment != null

    /** Filtr do wyszukiwarki; null znaczy „niczego nie odsiewaj". */
    fun fits(showAll: Boolean): pl.gymbro.data.ExerciseFilter? =
        if (limited && !showAll) profile::canDoExercise else null
}

/**
 * Przełącznik „pokaż wszystkie" przy liście sprzętu w Profilu.
 *
 * Na ścieżce prostej (bez listy) go nie ma - pokazuje się wszystko i nie
 * ma czego przełączać.
 */
@Composable
internal fun GearModeRow(
    gear: GearMode,
    showAll: Boolean,
    onShowAll: (Boolean) -> Unit,
    /** „plany", „jednostki", „ćwiczenia" - do zdania przy przełączniku. */
    what: String
) {
    if (!gear.limited) return
    // Jeden przycisk-filtr, jak poziomy obok. Był tu przełącznik z dwiema
    // linijkami objaśnienia, który zajmował więcej miejsca niż pierwszy plan
    // na liście. Włączony znaczy „tylko mój sprzęt", bo tak brzmi domyślny
    // stan, który widać po wejściu.
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        val napis = "Tylko mój sprzęt"
        if (!showAll) {
            Button(
                onClick = { onShowAll(true) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(napis, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            OutlinedButton(
                onClick = { onShowAll(false) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) { Text(napis, style = MaterialTheme.typography.labelLarge) }
        }
        Text(
            if (showAll) "wszystkie $what" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 10.dp)
        )
    }
}

@Composable
private fun ProgramTemplateCard(
    template: PlanLibrary.ProgramTemplate,
    profile: UserProfile,
    onClick: () -> Unit
) {
    AppCard(Modifier.padding(vertical = 4.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Bez opisu: na liście porównuje się dni, poziom i sprzęt,
                // a trzy linijki prozy na karcie spychały resztę planów
                // poza ekran. Opis czeka w szczegółach.
                TemplatePills(template, Modifier.padding(top = 6.dp))
                Text(
                    template.units.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                MissingGear(template.units, profile)
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Ostrzeżenie o sprzęcie, którego nie ma na liście w Profilu.
 *
 * Tylko przy dostosowanym sprzęcie - przy pełnej siłowni `canDo` zawsze
 * odpowiada „tak" i linijki nie ma. Ostrzega, nie blokuje: lista w Profilu
 * bywa niepełna, a plan da się po dodaniu przerobić.
 */
@Composable
private fun MissingGear(units: List<PlanLibrary.UnitTemplate>, profile: UserProfile) {
    val brak = PlanLibrary.missingFor(units, profile)
    if (brak.isEmpty()) return
    Text(
        "Brakuje Ci: " +
            brak.joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
