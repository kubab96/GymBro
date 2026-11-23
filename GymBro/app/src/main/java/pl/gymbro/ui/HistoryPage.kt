package pl.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.TrackRepository
import pl.gymbro.gym.ProgressStats
import pl.gymbro.gym.RunAnalysis
import pl.gymbro.gym.Timeline
import pl.gymbro.gym.TrackMath
import pl.gymbro.health.ActivityDetail
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---- Filtry -----------------------------------------------------------------

internal enum class HistoryKind(val label: String) {
    ALL("Wszystko"), STRENGTH("Siła"), CARDIO("Kardio"), OTHER("Inne")
}

internal fun kindOf(a: Activity): HistoryKind = when (a) {
    is Activity.Strength -> HistoryKind.STRENGTH
    is Activity.Watch -> when {
        // Siła z zegarka bez zapisanych serii to dalej siła - ćwiczyłeś,
        // tylko nie zalogowałeś. W „Inne" wyglądała jak zgubiony trening.
        Timeline.isStrength(a.session) -> HistoryKind.STRENGTH
        Timeline.isCardio(a.session) -> HistoryKind.CARDIO
        else -> HistoryKind.OTHER
    }
}

/** Nazwa, po której filtruje „Trening": jednostka planu albo rodzaj aktywności. */
private fun nameOf(a: Activity): String = when (a) {
    is Activity.Strength -> a.summary.planName ?: "Trening bez jednostki"
    is Activity.Watch -> a.session.type
}

/**
 * Okres jako klucz: „all", „week", „30" albo „m" i numer miesiąca.
 * Tekst, nie klasa - żeby przeżył obrót i ubicie aplikacji w rememberSaveable.
 */
private fun inPeriod(a: Activity, period: String, now: Long): Boolean = when {
    period == "all" -> true
    period == "week" -> a.at >= ProgressStats.weekStart(now)
    period == "30" -> a.at >= now - 30 * 86_400_000L
    period.startsWith("m") -> monthKey(a.at) == period.drop(1).toIntOrNull()
    else -> true
}

/**
 * Historia jako karty - każdy trening z trzema liczbami, najlepszymi
 * seriami i rekordami, bieg z trasą.
 *
 * Wiersz z samą nazwą i liczbą serii kazał wchodzić w każdy trening po
 * kolei, żeby sprawdzić, co w nim było. Karta odpowiada na to od razu.
 *
 * Trzy filtry: okres, rodzaj i konkretny trening („A - sztanga", „Bieg") -
 * żeby zobaczyć wszystkie A pod rząd albo tylko listopad.
 */
@Composable
internal fun HistoryPage(activities: List<Activity>, onOpen: (Activity) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(context) }
    var period by rememberSaveable { mutableStateOf("all") }
    var kind by rememberSaveable { mutableStateOf(HistoryKind.ALL) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var highlights by remember { mutableStateOf<Map<Long, ProgressStats.Highlights>>(emptyMap()) }
    // Tętno i trasy doczytywane dopiero dla kart, które wjechały na ekran,
    // i trzymane na czas życia zakładki - przewinięcie w górę nie pyta
    // Health Connect drugi raz.
    val hrCache = remember { mutableStateMapOf<Long, List<RunAnalysis.Sample>>() }
    val trackCache = remember { mutableStateMapOf<String, List<TrackMath.Fix>>() }

    LaunchedEffect(activities) {
        highlights = withContext(Dispatchers.IO) {
            ProgressStats.highlights(repo.workingSetsByExercise())
        }
    }

    val now = remember(activities) { System.currentTimeMillis() }
    val shown = remember(activities, period, kind, name) {
        activities.filter { a ->
            inPeriod(a, period, now) &&
                (kind == HistoryKind.ALL || kindOf(a) == kind) &&
                (name == null || nameOf(a) == name)
        }
    }
    val months = remember(shown) { shown.groupBy { monthKey(it.at) }.toList() }
    // Miesiące i treningi do wyboru liczone z całej historii, a nie z tego,
    // co już przefiltrowane - inaczej po wyborze listopada nie dałoby się
    // przeskoczyć na październik bez czyszczenia filtra.
    val monthOptions = remember(activities) { activities.map { monthKey(it.at) }.distinct() }
    val nameOptions = remember(activities, kind) {
        activities.filter { kind == HistoryKind.ALL || kindOf(it) == kind }
            .groupingBy(::nameOf).eachCount().entries.sortedByDescending { it.value }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DropChip(
                label = when {
                    period == "all" -> "Okres"
                    period == "week" -> "Ten tydzień"
                    period == "30" -> "30 dni"
                    else -> monthLabel(period.drop(1).toInt())
                },
                active = period != "all",
                options = listOf("all" to "Cała historia", "week" to "Ten tydzień", "30" to "Ostatnie 30 dni") +
                    monthOptions.map { "m$it" to monthLabel(it) },
                onPick = { period = it }
            )
            DropChip(
                label = if (kind == HistoryKind.ALL) "Rodzaj" else kind.label,
                active = kind != HistoryKind.ALL,
                options = HistoryKind.entries.map { it.name to it.label },
                onPick = { picked ->
                    kind = HistoryKind.valueOf(picked)
                    // Trening z innego rodzaju zostawiłby pustą listę bez powodu.
                    if (name != null && activities.none { kindOf(it) == kind && nameOf(it) == name }) name = null
                }
            )
            DropChip(
                label = name ?: "Trening",
                active = name != null,
                options = listOf("" to "Wszystkie treningi") +
                    nameOptions.map { (n, c) -> n to "$n  ·  $c" },
                onPick = { name = it.ifEmpty { null } }
            )
            // Ikona zamiast napisu „Wyczyść": przy trzech zapalonych filtrach
            // napis wypadał za krawędź ekranu i widać było samo „Wyczy".
            if (period != "all" || kind != HistoryKind.ALL || name != null) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { period = "all"; kind = HistoryKind.ALL; name = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Wyczyść filtry",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            Text(
                if (activities.isEmpty()) "Pusto. Po pierwszym zapisanym treningu pojawi się tu historia."
                else "Nic nie pasuje do wybranych filtrów.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            months.forEach { (key, list) ->
                item(key = "m$key") { MonthHeader(key, list) }
                items(list, key = { keyOf(it) }) { a ->
                    HistoryCard(
                        item = a,
                        highlights = (a as? Activity.Strength)?.let { highlights[it.summary.sessionId] },
                        hrCache = hrCache,
                        trackCache = trackCache,
                        onOpen = onOpen
                    )
                }
            }
        }
    }
}

/** Przycisk filtra z rozwijaną listą. Zapalony, gdy filtr coś zawęża. */
@Composable
private fun DropChip(
    label: String,
    active: Boolean,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { open = true }
                .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Icon(
                Icons.Filled.ArrowDropDown, contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { open = false; onPick(key) }
                )
            }
        }
    }
}

// ---- Karta ------------------------------------------------------------------

@Composable
private fun HistoryCard(
    item: Activity,
    highlights: ProgressStats.Highlights?,
    hrCache: SnapshotStateMap<Long, List<RunAnalysis.Sample>>,
    trackCache: SnapshotStateMap<String, List<TrackMath.Fix>>,
    onOpen: (Activity) -> Unit
) {
    val context = LocalContext.current
    val strength = kindOf(item) == HistoryKind.STRENGTH

    // Tętno do karty siłowej bez treningu z zegarka - z pomiaru całodobowego.
    // Z zegarkiem średnia i tak jest w podsumowaniu, a przebieg pokaże
    // dopiero ekran szczegółów.
    if (item is Activity.Strength) {
        val sid = item.summary.sessionId
        LaunchedEffect(sid) {
            if (sid !in hrCache) {
                val s = item.summary.watch
                hrCache[sid] = ActivityDetail(context).heartRate(
                    s?.startedAt ?: item.summary.startedAt, s?.endedAt ?: item.summary.endedAt
                )
            }
        }
    }
    if (item is Activity.Watch && item.session.fromPhone) {
        val id = item.session.externalId
        LaunchedEffect(id) {
            if (id !in trackCache) {
                trackCache[id] = withContext(Dispatchers.IO) { TrackRepository(context).points(id) }
            }
        }
    }

    Card(
        onClick = { onOpen(item) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            CardHeader(item, strength, highlights?.records ?: 0)
            Spacer(Modifier.height(12.dp))
            when (item) {
                is Activity.Strength -> StrengthBody(item, highlights, hrCache[item.summary.sessionId].orEmpty())
                is Activity.Watch -> WatchBody(item.session, trackCache[item.session.externalId].orEmpty())
            }
        }
    }
}

@Composable
private fun CardHeader(item: Activity, strength: Boolean, records: Int) {
    val adviceSessions = LocalAdviceSessions.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (strength) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                activityIcon(item), contentDescription = null,
                tint = if (strength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    nameOf(item),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Kropka przy treningu, po ktorym cos czeka -
                // inaczej trzeba wchodzic w kazdy po kolei,
                // zeby sprawdzic, czy cos z niego wynikło.
                if (item is Activity.Strength && adviceSessions.contains(item.summary.sessionId)) {
                    Box(
                        Modifier.padding(start = 6.dp).size(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
            Text(
                SimpleDateFormat("EEEE d.MM · HH:mm", Locale.getDefault()).format(Date(item.at)) +
                    when {
                        item is Activity.Watch && item.session.fromPhone -> " · z telefonu"
                        item is Activity.Watch && Timeline.isStrength(item.session) -> " · z zegarka, bez serii"
                        else -> ""
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (records > 0) {
            Text(
                if (records == 1) "rekord" else plural(records, "rekord", "rekordy", "rekordów"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun StrengthBody(
    item: Activity.Strength,
    highlights: ProgressStats.Highlights?,
    hr: List<RunAnalysis.Sample>
) {
    val s = item.summary
    val hrAvg = s.watch?.hrAvg ?: hr.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
    StatRow(
        listOf(
            "Czas" to durationLabel(s.durationMin),
            "Objętość" to tonnageLabel(s.tonnage),
            if (hrAvg != null) "Tętno śr." to "${hrAvg.toInt()}" else "Serie" to "${s.workingSets}"
        )
    )
    val lines = highlights?.exercises.orEmpty()
    if (lines.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        lines.take(3).forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    l.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    plural(l.sets, "seria", "serie", "serii") + " · " +
                        kgText(l.bestKg) + " kg × " + l.bestReps,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (lines.size > 3) {
            Text(
                "+ " + plural(lines.size - 3, "ćwiczenie", "ćwiczenia", "ćwiczeń"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (hr.size >= 4) {
        Spacer(Modifier.height(10.dp))
        HrSpark(RunAnalysis.downsample(hr, 90))
    }
}

@Composable
private fun WatchBody(s: ExternalSession, track: List<TrackMath.Fix>) {
    val km = (s.distanceM ?: 0.0) / 1000.0
    val stats = listOfNotNull(
        if (km >= 0.05) "Dystans" to String.format(Locale.getDefault(), "%.2f km", km) else null,
        s.paceMinPerKm?.takeIf { Timeline.isCardio(s) }?.let { "Tempo" to paceLabel(it) },
        "Czas" to durationLabel(s.durationMin),
        s.hrAvg?.let { "Tętno śr." to "${it.toInt()}" },
        s.energyKcal?.takeIf { it > 0 }?.let { "Energia" to "${it.toInt()} kcal" }
    ).take(4)
    if (track.size >= 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                stats.chunked(2).forEach { StatRow(it, columns = 2); Spacer(Modifier.height(6.dp)) }
            }
            MiniRoute(track, Modifier.width(110.dp).height(88.dp))
        }
    } else {
        StatRow(stats.take(3))
    }
}

@Composable
private fun StatRow(items: List<Pair<String, String>>, columns: Int = 3) {
    Row(Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun HrSpark(samples: List<RunAnalysis.Sample>) {
    val color = Color(0xFFD9654B)
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val lo = samples.minOf { it.value }
        val span = (samples.maxOf { it.value } - lo).coerceAtLeast(1.0)
        val t0 = samples.first().at
        val tSpan = (samples.last().at - t0).coerceAtLeast(1L).toDouble()
        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = ((s.at - t0) / tSpan * size.width).toFloat()
            val y = (size.height - (s.value - lo) / span * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** Szkic trasy na karcie - ten sam rzut co w szczegółach, tylko mniejszy. */
@Composable
private fun MiniRoute(points: List<TrackMath.Fix>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.secondary
    val start = MaterialTheme.colorScheme.primary
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val k = kotlin.math.cos(Math.toRadians(points.map { it.lat }.average()))
            val xs = points.map { it.lon * k }
            val ys = points.map { it.lat }
            val minX = xs.min(); val minY = ys.min()
            val span = maxOf(xs.max() - minX, ys.max() - minY).takeIf { it > 0 } ?: 1e-6
            val scale = minOf(size.width, size.height) / span
            val offX = (size.width - (xs.max() - minX) * scale) / 2
            val offY = (size.height - (ys.max() - minY) * scale) / 2
            fun at(i: Int) = Offset(
                (offX + (xs[i] - minX) * scale).toFloat(),
                (size.height - offY - (ys[i] - minY) * scale).toFloat()
            )
            val path = Path()
            points.indices.forEach { i ->
                if (i == 0 || points[i].seg != points[i - 1].seg) path.moveTo(at(i).x, at(i).y)
                else path.lineTo(at(i).x, at(i).y)
            }
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(start, radius = 3.5.dp.toPx(), center = at(0))
        }
    }
}

// ---- Miesiące i wspólne -------------------------------------------------------

private fun keyOf(a: Activity): String = when (a) {
    is Activity.Strength -> "s" + a.summary.sessionId
    is Activity.Watch -> "w" + a.session.externalId
}

/** Rok * 12 + miesiąc - klucz grupy i jednocześnie kolejność. */
internal fun monthKey(ms: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}

private val MONTHS = listOf(
    "Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
    "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień"
)

private fun monthLabel(key: Int): String {
    val year = key / 12
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    return MONTHS[key % 12] + if (year != thisYear) " $year" else ""
}

@Composable
private fun MonthHeader(key: Int, list: List<Activity>) {
    val minutes = list.sumOf {
        when (it) {
            is Activity.Strength -> it.summary.durationMin
            is Activity.Watch -> it.session.durationMin
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            monthLabel(key).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            plural(list.size, "trening", "treningi", "treningów") + " · " + hoursLabel(minutes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Kilogramy bez zbędnego „,0": 37,5 i 100, nie 100,0. */
private fun kgText(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.getDefault(), "%.1f", r)
}
