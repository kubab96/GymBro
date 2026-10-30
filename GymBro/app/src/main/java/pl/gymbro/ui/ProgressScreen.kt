package pl.gymbro.ui

import pl.gymbro.gym.MuscleMap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SessionSummary
import pl.gymbro.data.VolumeLandmarks
import pl.gymbro.gym.Loading
import pl.gymbro.gym.WatchMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wszystko, czego ekran potrzebuje, w jednym zejściu na wątek roboczy. */
private data class ProgressData(
    val thisWeek: Map<String, Int>,
    val lastWeek: Map<String, Int>,
    val exercises: List<Pair<Long, String>>
)

/**
 * Postęp: co już zrobiłeś i dokąd to zmierza.
 *
 * Objętość i siła dają się odczytać tylko z historii, nie z pojedynczej sesji,
 * i na jedno i drugie realnie wpływasz układając plan. Pod spodem leży sama
 * historia: treningi siłowe razem z tętnem, które zapamiętał zegarek, oraz
 * biegi - jedyna aktywność, o której aplikacja wie wyłącznie z zegarka.
 */
@Composable
fun ProgressScreen(onBack: () -> Unit) {
    // Systemowy wstecz robi to samo co „Wróć" - bez tego wychodził z aplikacji.
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }

    var thisWeek by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var lastWeek by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var exercises by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var selected by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var history by remember { mutableStateOf<List<Pair<Long, Double>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Busy.track {
            val now = System.currentTimeMillis()
            val week = 7 * 86_400_000L
            // Jeden skok na wątek roboczy zamiast trzech.
            val loaded = withContext(Dispatchers.IO) {
                ProgressData(
                    thisWeek = repo.workingSetsByMuscle(now - week, now),
                    lastWeek = repo.workingSetsByMuscle(now - 2 * week, now - week),
                    exercises = repo.exercisesWithHistory(),
                )
            }
            thisWeek = loaded.thisWeek
            lastWeek = loaded.lastWeek
            exercises = loaded.exercises
            selected = loaded.exercises.firstOrNull()
            loading = false
        }
    }

    LaunchedEffect(selected) {
        val id = selected?.first ?: return@LaunchedEffect
        history = withContext(Dispatchers.IO) { repo.e1rmHistory(id) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Postęp", style = MaterialTheme.typography.headlineMedium)
        }

        if (loading) {
            Text("Wczytuję…")
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {


            // ---- Mapa obciążonych partii ----
            item {
                Spacer(Modifier.height(22.dp))
                Text("Co obrobiłeś w tym tygodniu", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Z wykonanych ćwiczeń, nie z ruchu nadgarstka - więc dokładnie, " +
                        "a nie w przybliżeniu. Pełny kolor to " +
                        VolumeLandmarks.PRODUCTIVE_TO + " serii, czyli górna granica " +
                        "zakresu produktywnego.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                MuscleBody(MuscleMap.intensities(thisWeek, VolumeLandmarks.PRODUCTIVE_TO))
                Spacer(Modifier.height(10.dp))
                MuscleLegend("nietknięte", "${VolumeLandmarks.PRODUCTIVE_TO}+ serii")
            }
            // ---- Objętość ----
            item {
                Text("Objętość tygodniowa", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Serie robocze na grupę mięśniową z ostatnich 7 dni. " +
                        "Serie, nie tonaż - bo to one bliżej odpowiadają bodźcowi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            if (thisWeek.isEmpty()) {
                item {
                    EmptyBox("Brak serii w ostatnich 7 dniach. Zaloguj trening, " +
                        "a objętość policzy się sama.")
                }
            } else {
                items(thisWeek.entries.sortedByDescending { it.value }.toList()) { (muscle, sets) ->
                    MuscleRow(muscle, sets, lastWeek[muscle] ?: 0)
                }
            }

            // ---- Siła ----
            item {
                Spacer(Modifier.height(22.dp))
                Text("Siła w czasie", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Szacowany ciężar maksymalny z najlepszej serii każdej sesji. " +
                        "Pozwala porównać treningi o różnej liczbie powtórzeń.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            if (exercises.isEmpty()) {
                item { EmptyBox("Brak zapisanych serii. Wykres pojawi się po pierwszym treningu.") }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(exercises) { ex ->
                            ExerciseChip(
                                name = ex.second,
                                active = ex.first == selected?.first,
                                onClick = { selected = ex }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    E1rmChart(history)
                    Spacer(Modifier.height(8.dp))
                    StrengthSummary(history)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- Historia ------------------------------------------------------------


// ---- Objętość ------------------------------------------------------------

@Composable
private fun MuscleRow(muscle: String, sets: Int, previous: Int) {
    val level = VolumeLandmarks.level(sets)
    val color = when (level) {
        VolumeLandmarks.Level.NONE, VolumeLandmarks.Level.LOW -> MaterialTheme.colorScheme.tertiary
        VolumeLandmarks.Level.PRODUCTIVE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val diff = sets - previous

    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(muscle, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (previous > 0 && diff != 0) {
                    Text(
                        if (diff > 0) "+$diff " else "$diff ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("$sets", style = MaterialTheme.typography.titleMedium, color = color)
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = (sets.toFloat() / VolumeLandmarks.CHART_MAX).coerceIn(0.02f, 1f)
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }

        Text(
            VolumeLandmarks.describe(sets),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ---- Siła ----------------------------------------------------------------

@Composable
private fun ExerciseChip(name: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun E1rmChart(points: List<Pair<Long, Double>>) {
    if (points.size < 2) {
        EmptyBox("Za mało sesji na wykres - potrzebne co najmniej dwie.")
        return
    }

    val values = points.map { it.second }
    val span = (values.max() - values.min()).takeIf { it > 0.01 } ?: 1.0
    val lo = values.min() - span * 0.2
    val hi = values.max() + span * 0.2

    val minT = points.first().first
    val tSpan = (points.last().first - minT).takeIf { it > 0 } ?: 1L

    val line = MaterialTheme.colorScheme.primary
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f kg".format(hi), style = MaterialTheme.typography.labelSmall)
            Text("e1RM", style = MaterialTheme.typography.labelSmall, color = line)
        }

        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 4.dp)) {
            val w = size.width
            val h = size.height
            fun x(t: Long) = ((t - minT).toDouble() / tSpan * w).toFloat()
            fun y(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()

            for (i in 0..2) {
                val yy = h * i / 2f
                drawLine(grid, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
            }

            val path = Path()
            points.forEachIndexed { i, (t, v) ->
                if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
            }
            drawPath(path, line, style = Stroke(width = 4f))

            points.forEach { (t, v) ->
                drawCircle(dot, radius = 4f, center = Offset(x(t), y(v)), alpha = 0.7f)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f kg".format(lo), style = MaterialTheme.typography.labelSmall)
            Text(
                "${shortDate(minT)} - ${shortDate(points.last().first)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StrengthSummary(points: List<Pair<Long, Double>>) {
    if (points.size < 2) return

    val first = points.first().second
    val last = points.last().second
    val best = points.maxOf { it.second }
    val change = last - first
    val pct = if (first > 0) change / first * 100 else 0.0
    val days = ((points.last().first - points.first().first) / 86_400_000L).toInt().coerceAtLeast(1)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Zmiana przez $days dni", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "%+.1f kg (%+.1f%%)".format(change, pct),
                        fontSize = 22.sp,
                        color = if (change >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("rekord", style = MaterialTheme.typography.bodySmall)
                    Text("${Loading.fmt(best)} kg", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Zastój widać dopiero przez porównanie z rekordem: jeśli ostatnia
            // sesja wyraźnie od niego odstaje, to nie jest już progres.
            if (best - last > best * 0.03) {
                Text(
                    "Ostatnia sesja ${"%.1f".format(best - last)} kg poniżej rekordu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyBox(text: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun shortDate(ms: Long): String =
    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ms))
