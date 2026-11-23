package pl.gymbro.ui

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
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

/** Plan z jednostkami - do ekranu „Rozpocznij inny trening". */
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
    onStart: (Plan) -> Unit,
    onOther: () -> Unit,
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
                0 -> TrainingPage(program, options, activities, onStart, onOther)
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
    activities: List<Activity>,
    onStart: (Plan) -> Unit,
    onOther: () -> Unit
) {
    var confirming by remember { mutableStateOf<PlanOption?>(null) }
    // Przy rotacji A/B/C najdawniej robiona jednostka jest następna
    // w kolejce. Nigdy nierobiona wyprzedza wszystkie.
    val suggested = options.minByOrNull { it.lastDoneAt ?: 0L }
    val rest = options.filter { it.plan.id != suggested?.plan?.id }

    // Ten tydzień: treningi siłowe od poniedziałku. Bez celu „z N": liczba
    // jednostek nie mówi, ile razy w tygodniu ktoś trenuje (PPL bywa i trzy,
    // i sześć razy), a dni tygodnia przy jednostkach już nie ma.
    val weekFrom = ProgressStats.weekStart(System.currentTimeMillis())
    val thisWeek = activities.count { it is Activity.Strength && it.at >= weekFrom }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                program?.name ?: "Bez planu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "ten tydzień",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$thisWeek",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HealthAccessBanner(Modifier.padding(bottom = 12.dp))

        if (suggested == null) {
            EmptyPlanCard(program)
        } else {
            NextUnitCard(suggested) { confirming = suggested }
            if (rest.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Pozostałe jednostki planu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                rest.forEach { o -> UnitRow(o) { confirming = o } }
            }
        }

        Spacer(Modifier.height(14.dp))
        OtherWorkoutButton(onOther)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zacząć " + option.plan.name + "?") },
        text = {
            Text(
                option.exerciseCount.toString() + " ćwiczeń · " +
                    lastDoneLabel(option.lastDoneAt) + "\n\n" +
                    "Licznik czasu ruszy od razu."
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Zacznij") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
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
 * Następna jednostka w dużej karcie z przyciskiem.
 *
 * Dotąd wszystkie jednostki były tej samej wielkości i podpowiedź „następny
 * w kolejce" była jedną linijką drobnego druku. W dziewięciu treningach na
 * dziesięć robi się właśnie ją, więc dostaje cały przycisk.
 */
@Composable
private fun NextUnitCard(o: PlanOption, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "NASTĘPNA W KOLEJCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    o.plan.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "ostatnio " + lastDoneLabel(o.lastDoneAt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
            if (o.exercises.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    o.exercises.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    "${o.exerciseCount} ćwiczeń",
                    o.sets.takeIf { it > 0 }?.let { "$it serii" }
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Zacznij " + o.plan.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun UnitRow(o: PlanOption, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(o.plan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${o.exerciseCount} ćwiczeń · ${lastDoneLabel(o.lastDoneAt)}",
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
private fun OtherWorkoutButton(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                )
            }
            .tap(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("Rozpocznij inny trening", style = MaterialTheme.typography.titleMedium)
            Text(
                "z innego planu, bieg, spacer, rower…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
