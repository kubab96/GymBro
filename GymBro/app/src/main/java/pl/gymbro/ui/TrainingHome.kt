package pl.gymbro.ui

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.data.Activity
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.Plan
import pl.gymbro.data.Program
import pl.gymbro.gym.ProgressStats
import pl.gymbro.gym.Timeline
import pl.gymbro.ui.theme.ButtonShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Jednostka treningowa razem z kontekstem potrzebnym do wyboru. */
internal data class PlanOption(
    val plan: Plan,
    val exerciseCount: Int,
    val lastDoneAt: Long?,
    /** Nazwy ćwiczeń, w kolejności - tylko dla jednostek planu aktywnego. */
    val exercises: List<String> = emptyList(),
    val sets: Int = 0
)

/** Plan z jednostkami - do sekcji „Z innych planów" na ekranie Treningu. */
internal data class ProgramUnits(val program: Program, val units: List<PlanOption>)

internal val TRAINING_PAGES = listOf("Trening", "Progres", "Historia")

/**
 * Zakładka Trening poza trwającym treningiem: trzy podzakładki jak w Wadze.
 *
 * Progres i Historia czyta się między treningami, więc siedzą obok wyboru
 * jednostki, a nie w osobnym ekranie za przyciskiem - dotąd Postęp był
 * przyciskiem w rogu i mało kto do niego zaglądał.
 */
@Composable
internal fun TrainingHome(
    pager: PagerState,
    program: Program?,
    options: List<PlanOption>,
    activities: List<Activity>,
    otherPrograms: List<ProgramUnits>,
    onStart: (Plan) -> Unit,
    /** Kafelek „Z innych planów" - otwiera ich listę na pełnym ekranie. */
    onOtherPlans: () -> Unit,
    /** Wybrana aktywność z kafelka - czeka na ekranie przygotowania na „Start". */
    onPrepare: (PhoneActivity) -> Unit,
    onOpen: (Activity) -> Unit,
    onExercise: (Long, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    // Wstecz z Progresu albo Historii wraca do Treningu, a nie wychodzi z aplikacji.
    ScreenBackHandler(enabled = pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Trening",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
        )
        SegmentedTabs(
            labels = TRAINING_PAGES,
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
        )
        Spacer(Modifier.height(12.dp))
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top,
            pageSpacing = 14.dp
        ) { page ->
            when (page) {
                0 -> TrainingPage(program, options, otherPrograms, activities, onStart, onOtherPlans, onPrepare, onOpen)
                1 -> ProgresPage(activities, onExercise)
                else -> HistoryPage(activities, onOpen)
            }
        }
    }
}

// ---- 1. Trening -----------------------------------------------------------

@Composable
private fun TrainingPage(
    program: Program?,
    options: List<PlanOption>,
    otherPrograms: List<ProgramUnits>,
    activities: List<Activity>,
    onStart: (Plan) -> Unit,
    onOtherPlans: () -> Unit,
    onPrepare: (PhoneActivity) -> Unit,
    onOpen: (Activity) -> Unit
) {
    var confirming by remember { mutableStateOf<PlanOption?>(null) }
    // Przy rotacji A/B/C najdawniej robiona jednostka jest następna
    // w kolejce. Nigdy nierobiona wyprzedza wszystkie.
    val suggested = options.minByOrNull { it.lastDoneAt ?: 0L }
    val recent = remember(activities) { activities.sortedByDescending { it.at }.take(3) }
    val startActivity = rememberActivityStarter(onPrepare)

    // Wszystko, od czego zaczyna się trening, na jednym ekranie: jednostki
    // planu w jego kolejności z zaznaczoną następną, kafelki innych
    // treningów i kafelek z innymi planami. Osobny ekran „Nowy trening"
    // za przyciskiem chował bieg i trening siłowy o jeden ekran za daleko.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HealthAccessBanner(Modifier.padding(bottom = 4.dp))

        if (suggested == null) {
            Spacer(Modifier.height(8.dp))
            EmptyPlanCard(program)
        } else {
            GroupLabel("Jednostki w planie " + (program?.name ?: ""))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Kolejność z planu, nie od najdawniej robionej - A, B, C
                // stoją tam, gdzie się ich szuka, a następna jest zaznaczona.
                options.forEach { o ->
                    PlanUnitRow(o, next = o.plan.id == suggested.plan.id) { confirming = o }
                }
            }
        }

        GroupLabel("Inny trening")
        ActivityTiles(startActivity)
        if (otherPrograms.isNotEmpty()) OtherPlansTile(otherPrograms.size, onOtherPlans)

        if (recent.isNotEmpty()) {
            GroupLabel("Ostatnie treningi")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { a -> LastActivityRow(a) { onOpen(a) } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // Potwierdzenie startu.
    //
    // Karta jednostki jest duża i leży tam, gdzie się przewija - dotknięcie
    // przez pomyłkę zaczynało trening i uruchamiało licznik. Cofnięcie tego
    // wymagało zakończenia pustej sesji, czyli śmiecia w historii. Jedno
    // pytanie kosztuje dotknięcie, pomyłka kosztowała wpis.
    confirming?.let { o ->
        StartConfirmDialog(o, onDismiss = { confirming = null }) {
            confirming = null
            onStart(o.plan)
        }
    }
}

@Composable
internal fun StartConfirmDialog(option: PlanOption, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ActionSheet(
        onDismiss = onDismiss,
        icon = Icons.Filled.FitnessCenter,
        tone = MaterialTheme.colorScheme.primary,
        title = "Zacząć " + option.plan.name + "?",
        text = plural(option.exerciseCount, "ćwiczenie", "ćwiczenia", "ćwiczeń") + " · " +
            lastDoneLabel(option.lastDoneAt) + ". Licznik czasu ruszy od razu.",
        primary = SheetAction("Zacznij", onClick = onConfirm),
        secondary = SheetAction("Anuluj", onClick = onDismiss)
    )
}

@Composable
private fun EmptyPlanCard(program: Program?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (program == null) "Nie masz jeszcze planu" else "Plan jest pusty",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (program == null)
                    "Przejdź do zakładki Plan i stwórz plan - na przykład " +
                        "FBW z jednostkami A, B i C albo PPL z Push, Pull i Legs. " +
                        "Bieg albo spacer zaczniesz i bez planu, przyciskiem niżej."
                else
                    "Plan " + program.name + " nie ma jeszcze jednostek. " +
                        "Dodaj je w zakładce Plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Jednostka planu na ekranie Treningu: nazwa, liczba ćwiczeń i serii, kiedy
 * ostatnio. Następna w kolejce ma kolor i okrągły start zamiast strzałki -
 * w dziewięciu treningach na dziesięć robi się właśnie ją.
 *
 * Bez nazw ćwiczeń: wypisane zajmowały pół ekranu, a podgląd jest w Planie.
 * Dotknięcie wiersza i okrągły start prowadzą do tego samego pytania.
 */
@Composable
private fun PlanUnitRow(o: PlanOption, next: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (next) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (next) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        ),
        border = if (next) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                if (next) {
                    Text(
                        "NASTĘPNY TRENING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    o.plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (next) FontWeight.Bold else null,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        plural(o.exerciseCount, "ćwiczenie", "ćwiczenia", "ćwiczeń"),
                        o.sets.takeIf { it > 0 }?.let { plural(it, "seria", "serie", "serii") },
                        lastDoneLabel(o.lastDoneAt)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (next) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (next) {
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .tap(CircleShape, onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "Zacznij " + o.plan.name,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Ostatni trening jednym wierszem - otwiera jego szczegóły, jak w Historii. */
@Composable
private fun LastActivityRow(a: Activity, onClick: () -> Unit) {
    val (title, sub) = when (a) {
        is Activity.Strength -> (a.summary.planName ?: "Trening") to listOfNotNull(
            lastDoneLabel(a.at),
            durationLabel(a.summary.durationMin),
            a.summary.tonnage.takeIf { it > 0 }?.let { tonnageLabel(it) }
        ).joinToString(" · ")
        is Activity.Watch -> a.session.type to listOfNotNull(
            lastDoneLabel(a.at),
            durationLabel(a.session.durationMin),
            a.session.distanceM?.takeIf { it > 0 }?.let {
                String.format(java.util.Locale.getDefault(), "%.2f km", it / 1000.0)
            }
        ).joinToString(" · ")
    }
    AppCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(activityIcon(a), size = 32)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun UnitRow(o: PlanOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(o.plan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    plural(o.exerciseCount, "ćwiczenie", "ćwiczenia", "ćwiczeń") + " · " + lastDoneLabel(o.lastDoneAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Wspólne --------------------------------------------------------------

/**
 * Czas trwania treningu.
 *
 * Zero minut byłoby mylące: sesja z dwudziestoma seriami zapisana w pół minuty
 * to ślad po przeklikaniu aplikacji, a nie trening, który trwał zero. Podajemy
 * więc, że trwała krócej niż minutę, zamiast twierdzić, że nie trwała wcale.
 */
internal fun durationLabel(min: Long): String = when {
    min < 1 -> "<1 min"
    min >= 60 -> "${min / 60} h ${min % 60} min"
    else -> "$min min"
}

internal fun hoursLabel(min: Long): String =
    if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

/** Tempo jako minuty i sekundy, bo 5,5 min/km czyta się gorzej niż 5:30. */
internal fun paceLabel(minPerKm: Double): String {
    val total = (minPerKm * 60).toInt()
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

internal fun tonnageLabel(kg: Double): String =
    if (kg >= 1000) String.format(Locale.getDefault(), "%.1f t", kg / 1000.0) else "${kg.toInt()} kg"

/** Ile dni temu - czytelniejsze niż data przy rotacji co kilka dni. */
internal fun lastDoneLabel(ms: Long?): String {
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
