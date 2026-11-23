package pl.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.gymbro.data.Activity
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SetEntry
import pl.gymbro.data.VolumeLandmarks
import pl.gymbro.gym.MuscleMap
import pl.gymbro.gym.ProgressStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY = 86_400_000L

/** Wszystko, czego Progres potrzebuje z bazy, w jednym zejściu na wątek roboczy. */
private data class ProgresData(
    val exercises: List<Triple<Long, String, List<SetEntry>>>,
    val thisWeek: Map<String, Int>,
    val lastWeek: Map<String, Int>
)

/**
 * Progres: pulpit okresu.
 *
 * Kafelki na górze odpowiadają na „czy trenuję tyle, co zwykle", wykres
 * tygodni na „czy to się trzyma", a boje na „czy rosnę". Dotąd ekran zaczynał
 * od mapy mięśni i objętości, a siła - jedyna rzecz, o którą większość pyta -
 * leżała na samym dole za paskiem wyboru ćwiczenia.
 *
 * [activities] przychodzi gotowe z zakładki, tak jak do Historii - to ta sama
 * lista, więc liczby z obu podzakładek się zgadzają.
 */
@Composable
internal fun ProgresPage(activities: List<Activity>, onExercise: (Long, String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var data by remember { mutableStateOf<ProgresData?>(null) }
    var periodIndex by rememberSaveable { mutableStateOf(0) }
    var metricIndex by rememberSaveable { mutableStateOf(0) }
    val period = ProgressStats.Period.entries[periodIndex]
    val metric = ProgressStats.Metric.entries[metricIndex]

    // Klucz na liście aktywności: nowy trening zmienia ją, a razem z nią
    // serie i mapę mięśni.
    LaunchedEffect(activities) {
        val now = System.currentTimeMillis()
        val week = 7 * DAY
        data = withContext(Dispatchers.IO) {
            ProgresData(
                exercises = repo.workingSetsByExercise(),
                thisWeek = repo.workingSetsByMuscle(now - week, now),
                lastWeek = repo.workingSetsByMuscle(now - 2 * week, now - week)
            )
        }
    }

    val now = remember(activities) { System.currentTimeMillis() }
    val from = period.days?.let { now - it * DAY } ?: Long.MIN_VALUE
    val totals = remember(activities, period) { ProgressStats.totals(activities, from, now + 1) }
    val previous = remember(activities, period) {
        period.days?.let { ProgressStats.totals(activities, from - it * DAY, from) }
    }
    val weeks = if (period == ProgressStats.Period.WEEKS4 || period == ProgressStats.Period.MONTHS3) 12 else 52
    val weekly = remember(activities, metric, weeks) {
        ProgressStats.weekly(activities, metric, weeks, now)
    }
    val lifts = remember(data, period) {
        data?.let { ProgressStats.lifts(it.exercises, period, now) } ?: emptyList()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressStats.Period.entries.forEachIndexed { i, p ->
                    FilterChip(p.label, active = i == periodIndex) { periodIndex = i }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Modifier.weight(1f), "Treningi", "${totals.workouts}",
                    previous?.let { diffText(totals.workouts - it.workouts) })
                Tile(Modifier.weight(1f), "Czas", hoursShort(totals.minutes),
                    previous?.let { hoursDiff(totals.minutes - it.minutes) })
                Tile(Modifier.weight(1f), "Serie", "${totals.sets}",
                    previous?.takeIf { it.sets > 0 }?.let { pctText(totals.sets, it.sets) })
            }
            Spacer(Modifier.height(10.dp))
            WeeklyCard(weekly, metric, period, metricIndex) { metricIndex = it }
        }

        if (data != null && lifts.isEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                EmptyBox(
                    if (period == ProgressStats.Period.ALL) "Brak zapisanych serii. Boje pojawią się po pierwszym treningu."
                    else "W tym okresie nie ma zapisanych serii. Wybierz dłuższy okres."
                )
            }
        }

        if (lifts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                Text("Główne boje", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Szacowany ciężar maksymalny z najlepszej serii każdej sesji - pozwala " +
                        "porównać treningi o różnej liczbie powtórzeń. Zmiana względem " +
                        "poprzedniego okresu tej samej długości.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            items(lifts.take(3), key = { "l" + it.exerciseId }) { l ->
                LiftCard(l) { onExercise(l.exerciseId, l.name) }
            }
            if (lifts.size > 3) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Pozostałe ćwiczenia", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                }
                items(lifts.drop(3), key = { "r" + it.exerciseId }) { l ->
                    LiftRow(l) { onExercise(l.exerciseId, l.name) }
                }
            }
        }

        // ---- Partie mięśniowe ----
        val d = data
        if (d != null) {
            item {
                Spacer(Modifier.height(22.dp))
                Text("Partie w ostatnich 7 dniach", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Serie robocze na grupę mięśniową - serie, nie tonaż, bo to one bliżej " +
                        "odpowiadają bodźcowi. Pełny kolor to ${VolumeLandmarks.PRODUCTIVE_TO} " +
                        "serii, górna granica zakresu produktywnego.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                MuscleBody(MuscleMap.intensities(d.thisWeek, VolumeLandmarks.PRODUCTIVE_TO))
                Spacer(Modifier.height(10.dp))
                MuscleLegend("nietknięte", "${VolumeLandmarks.PRODUCTIVE_TO}+ serii")
                Spacer(Modifier.height(8.dp))
                if (d.thisWeek.isEmpty()) {
                    EmptyBox("Brak serii w ostatnich 7 dniach. Zaloguj trening, a objętość policzy się sama.")
                }
            }
            items(d.thisWeek.entries.sortedByDescending { it.value }.toList(), key = { "m" + it.key }) { (muscle, sets) ->
                MuscleRow(muscle, sets, d.lastWeek[muscle] ?: 0)
            }
        }
    }
}

// ---- Kafelki i tygodnie ----------------------------------------------------

@Composable
private fun Tile(modifier: Modifier, label: String, value: String, change: Pair<String, Boolean>?) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                change?.first ?: " ",
                style = MaterialTheme.typography.labelSmall,
                color = when (change?.second) {
                    true -> MaterialTheme.colorScheme.secondary
                    false -> MaterialTheme.colorScheme.tertiary
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Zmiana i to, czy jest w dobrą stronę. Zero to brak zmiany, nie porażka. */
private fun diffText(d: Int): Pair<String, Boolean>? =
    if (d == 0) "bez zmian" to true else (if (d > 0) "+$d" else "$d") + " vs poprz." to (d > 0)

private fun hoursDiff(min: Long): Pair<String, Boolean>? =
    if (min == 0L) null else (if (min > 0) "+" else "-") + hoursShort(kotlin.math.abs(min)) to (min > 0)

private fun pctText(now: Int, before: Int): Pair<String, Boolean> {
    val pct = ((now - before) * 100.0 / before).toInt()
    return (if (pct >= 0) "+$pct%" else "$pct%") to (pct >= 0)
}

private fun hoursShort(min: Long): String =
    if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"

@Composable
private fun WeeklyCard(
    weekly: List<Pair<Long, Double>>,
    metric: ProgressStats.Metric,
    period: ProgressStats.Period,
    metricIndex: Int,
    onMetric: (Int) -> Unit
) {
    val bar = MaterialTheme.colorScheme.primary
    val now = MaterialTheme.colorScheme.secondary
    val axis = MaterialTheme.colorScheme.outline
    val max = weekly.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    // Tygodnie z wybranego okresu w pełnym kolorze, starsze przygaszone -
    // widać od razu, które słupki składają się na kafelki wyżej.
    val inPeriod = (period.days ?: 10_000) / 7.0

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(metric.label + " na tydzień", style = MaterialTheme.typography.titleSmall)
                Row {
                    ProgressStats.Metric.entries.forEachIndexed { i, m ->
                        Text(
                            m.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (i == metricIndex) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (i == metricIndex) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.Transparent
                                )
                                .clickable { onMetric(i) }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Text(
                "najwięcej " + metricValue(metric, max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Canvas(Modifier.fillMaxWidth().height(100.dp).padding(top = 6.dp)) {
                val n = weekly.size
                val gap = if (n > 20) 2.dp.toPx() else 5.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                weekly.forEachIndexed { i, (_, v) ->
                    val h = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) 3f else 0f)
                    val fromEnd = n - 1 - i
                    val color = when {
                        fromEnd == 0 -> now
                        fromEnd < inPeriod -> bar
                        else -> bar.copy(alpha = 0.4f)
                    }
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
                drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(weekly.firstOrNull()?.let { shortDate(it.first) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ten tydzień", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun metricValue(m: ProgressStats.Metric, v: Double): String = when (m) {
    ProgressStats.Metric.SETS -> "${v.toInt()} serii"
    ProgressStats.Metric.TIME -> hoursLabel(v.toLong())
    ProgressStats.Metric.TONNAGE -> tonnageLabel(v)
}

// ---- Boje -----------------------------------------------------------------

@Composable
private fun LiftCard(l: ProgressStats.Lift, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    "szac. maks. · " + plural(l.sessionsInPeriod, "sesja", "sesje", "sesji"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Sparkline(
                l.trend,
                if (l.stalledWeeks != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                Modifier.width(72.dp).height(28.dp)
            )
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                Text(kgLabel(l.current) + " kg", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                ChangeLabel(l)
            }
        }
    }
}

@Composable
private fun LiftRow(l: ProgressStats.Lift, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                l.lastRecordAt?.let { "rekord " + lastDoneLabel(it) } ?: "bez rekordu od pierwszej sesji",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(kgLabel(l.current), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Box(Modifier.width(86.dp), contentAlignment = Alignment.CenterEnd) { ChangeLabel(l) }
    }
}

/** Zmiana albo zastój. Zastój ma pierwszeństwo, bo to on wymaga decyzji. */
@Composable
private fun ChangeLabel(l: ProgressStats.Lift) {
    val stalled = l.stalledWeeks
    val c = l.change
    val (text, color) = when {
        stalled != null -> "zastój $stalled tyg." to MaterialTheme.colorScheme.tertiary
        c == null -> "-" to MaterialTheme.colorScheme.onSurfaceVariant
        c >= 0.05 -> "+" + kgLabel(c) + " kg" to MaterialTheme.colorScheme.secondary
        c <= -0.05 -> "-" + kgLabel(-c) + " kg" to MaterialTheme.colorScheme.tertiary
        else -> "bez zmian" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
}

@Composable
private fun Sparkline(values: List<Double>, color: Color, modifier: Modifier) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier.padding(horizontal = 6.dp)) {
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0.01 } ?: 1.0
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1)
            val y = (size.height - (v - lo) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

// ---- Jedno ćwiczenie --------------------------------------------------------

private enum class ExerciseMetric(val label: String) { E1RM("Maks."), TOP("Ciężar"), VOLUME("Objętość") }

/**
 * Jedno ćwiczenie w szczegółach: wykres z zaznaczonymi rekordami, rekordy
 * na liczbę powtórzeń i ostatnie sesje.
 *
 * Trzy miary, bo każda odpowiada na inne pytanie: szacowany maks. - „czy
 * jestem silniejszy" niezależnie od powtórzeń, ciężar - „co było na
 * sztandze", objętość - „ile pracy zrobiłem".
 */
@Composable
internal fun ExerciseProgressScreen(exerciseId: Long, name: String, onBack: () -> Unit) {
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var sets by remember { mutableStateOf<List<SetEntry>?>(null) }
    var metricIndex by rememberSaveable { mutableStateOf(0) }
    val metric = ExerciseMetric.entries[metricIndex]

    LaunchedEffect(exerciseId) {
        sets = withContext(Dispatchers.IO) { repo.workingSetsOf(exerciseId) }
    }
    val points = remember(sets) { ProgressStats.points(sets.orEmpty()) }
    val records = remember(sets) { ProgressStats.repRecords(sets.orEmpty()) }
    fun valueOf(p: ProgressStats.Point) = when (metric) {
        ExerciseMetric.E1RM -> p.e1rm
        ExerciseMetric.TOP -> p.topKg
        ExerciseMetric.VOLUME -> p.volume
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                }
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        if (sets == null) return@LazyColumn
        if (points.isEmpty()) {
            item { EmptyBox("Brak zapisanych serii roboczych w tym ćwiczeniu.") }
            return@LazyColumn
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExerciseMetric.entries.forEachIndexed { i, m ->
                            FilterChip(m.label, active = i == metricIndex) { metricIndex = i }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val last = valueOf(points.last())
                    val since = System.currentTimeMillis() - 91 * DAY
                    val base = points.firstOrNull { it.at >= since }?.takeIf { it != points.last() }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (metric == ExerciseMetric.VOLUME) tonnageLabel(last) else kgLabel(last) + " kg",
                            fontSize = 34.sp, fontWeight = FontWeight.Bold
                        )
                        base?.let {
                            val d = last - valueOf(it)
                            Text(
                                "  " + (if (d >= 0) "+" else "-") +
                                    (if (metric == ExerciseMetric.VOLUME) tonnageLabel(kotlin.math.abs(d))
                                    else kgLabel(kotlin.math.abs(d)) + " kg") + " w 3 mies.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (d >= 0) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                    Text(
                        "ostatnia sesja · złote kropki to rekordy szacowanego maks.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LineChart(points.map { it.at to valueOf(it) }, points.map { it.record })
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Text("Rekordy na powtórzenia", style = MaterialTheme.typography.titleMedium)
            Text(
                "Najcięższy ciężar podniesiony co najmniej tyle razy w jednej serii.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                records.forEachIndexed { i, r ->
                    val reps = listOf(1, 3, 5, 10)[i]
                    Card(
                        Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("$reps powt.", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r?.let { kgLabel(it.kg) } ?: "-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(r?.let { shortDate(it.at) } ?: " ", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Ostatnie sesje", style = MaterialTheme.typography.titleMedium)
        }

        items(points.reversed().take(15), key = { it.at }) { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortDate(p.at), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(58.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        kgLabel(p.bestSet.weightKg) + " kg × " + p.bestSet.reps,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(plural(p.sets, "seria", "serie", "serii") + " · " + tonnageLabel(p.volume),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.record) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.tertiary))
                }
                Text(kgLabel(p.e1rm), style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(64.dp).padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LineChart(points: List<Pair<Long, Double>>, highlight: List<Boolean>) {
    if (points.size < 2) {
        EmptyBox("Za mało sesji na wykres - potrzebne co najmniej dwie.")
        return
    }
    val values = points.map { it.second }
    val span = (values.max() - values.min()).takeIf { it > 0.01 } ?: 1.0
    val lo = values.min() - span * 0.15
    val hi = values.max() + span * 0.15
    val minT = points.first().first
    val tSpan = (points.last().first - minT).takeIf { it > 0 } ?: 1L
    val line = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        fun x(t: Long) = ((t - minT).toDouble() / tSpan * size.width).toFloat()
        fun y(v: Double) = (size.height - ((v - lo) / (hi - lo) * size.height)).toFloat()
        for (i in 0..2) {
            val yy = size.height * i / 2f
            drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1f)
        }
        val path = Path()
        points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
        val area = Path().apply {
            addPath(path)
            lineTo(x(points.last().first), size.height)
            lineTo(x(minT), size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
        points.forEachIndexed { i, (t, v) ->
            if (highlight.getOrElse(i) { false }) drawCircle(gold, radius = 4.5.dp.toPx(), center = Offset(x(t), y(v)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(shortDate(minT), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shortDate(points.last().first), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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

@Composable
private fun EmptyBox(text: String) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/** Kilogramy z jedną cyfrą po przecinku, bez „,0" - 112,5 i 105, nie 105,0. */
private fun kgLabel(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString()
    else String.format(Locale.getDefault(), "%.1f", r)
}

private fun shortDate(ms: Long): String =
    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ms))
