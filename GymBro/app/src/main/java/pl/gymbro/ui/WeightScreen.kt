package pl.gymbro.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.BodyMeasurement
import pl.gymbro.data.BodyMetric
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.Cache
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.MetricsRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zakładka Waga: trzy podzakładki - Masa, Obwody, Historia.
 *
 * Wcześniej wszystko stało na jednym ekranie jedno pod drugim: liczba,
 * wykres, przycisk i historia ściśnięta w resztę wysokości, a obwody
 * pod osobnym przyciskiem w nagłówku. Historia dostawała przez to kilka
 * wierszy, a obwody łatwo było przeoczyć.
 *
 * Napisy są krótkie i mówią, co zrobić - bez nazw metod, źródeł odczytu
 * i marginesów błędu. Te szczegóły są w komentarzach; na ekranie, przy
 * wadze w łazience, tylko zawadzały.
 *
 * Założenie, które rządzi Masą: **pojedynczy pomiar nic nie znaczy**.
 * Masa ciała waha się w ciągu doby o ponad kilogram, więc obok ostatniej
 * liczby stoi trend i tempo zmiany na tydzień.
 *
 * Dane wczytuje się raz, tutaj, a podzakładki tylko rysują. Gdyby każda
 * ładowała swoje, obwody po zapisaniu pomiaru zostawały stare, dopóki
 * strona nie wypadła z pamięci pagera.
 */
private val PAGES = listOf("Masa", "Obwody", "Historia")

@Composable
fun WeightScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { BodyRepository(context) }
    val metricsRepo = remember { MetricsRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }

    // Przed wczesnym powrotem kreatora: po pomiarze wraca się na tę samą stronę.
    val pager = rememberPagerState(pageCount = { PAGES.size })
    var showFlow by remember { mutableStateOf(false) }

    // Wstecz z Obwodów albo Historii wraca do Masy, a nie wychodzi z aplikacji.
    ScreenBackHandler(enabled = pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }
    // Naciśnięcie otwartej zakładki wraca do Masy. Rozpoczętego pomiaru to
    // nie przerywa - wpisane wartości przepadłyby.
    TabReselectHandler { scope.launch { pager.scrollToPage(0) } }

    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var editProfile by remember { mutableStateOf(false) }

    var measurements by remember {
        mutableStateOf(Cache.peek<List<BodyMeasurement>>(Cache.BODY_RECENT) ?: emptyList())
    }
    var trend by remember {
        mutableStateOf(Cache.peek<List<Pair<Long, Double>>>(Cache.BODY_TREND) ?: emptyList())
    }
    var weekly by remember { mutableStateOf<Double?>(null) }
    var profile by remember { mutableStateOf(UserProfile()) }
    var status by remember { mutableStateOf<String?>(null) }
    var girthsByTime by remember { mutableStateOf<Map<Long, List<BodyMetric>>>(emptyMap()) }
    var latestGirths by remember { mutableStateOf<Map<MeasurementSite, BodyMetric>>(emptyMap()) }
    var girthChanges by remember { mutableStateOf<Map<MeasurementSite, Double>>(emptyMap()) }
    // Czy baza już odpowiedziała. Bez tego pierwsze otwarcie po instalacji
    // przez sekundę mówiło „Brak pomiarów" - wyglądało to jak utrata historii,
    // a było tylko wczytywaniem.
    var loaded by remember { mutableStateOf(Cache.peek<List<BodyMeasurement>>(Cache.BODY_RECENT) != null) }

    suspend fun reload() {
        val m = withContext(Dispatchers.IO) { repo.recent(120) }
        val t = withContext(Dispatchers.IO) { repo.trend() }
        val w = withContext(Dispatchers.IO) { repo.weeklyChangeKg() }
        val p = withContext(Dispatchers.IO) { profileRepo.get() }
        latestGirths = withContext(Dispatchers.IO) { metricsRepo.latestPerSite() }
        girthChanges = withContext(Dispatchers.IO) {
            MeasurementSite.entries.mapNotNull { s -> metricsRepo.changeCm(s)?.let { s to it } }.toMap()
        }
        // Obwody zapisane w tych samych momentach co pomiary masy - żeby historia
        // pokazywała jeden pomiar, a nie osobno kilogramy i osobno centymetry.
        girthsByTime = withContext(Dispatchers.IO) { metricsRepo.forTimes(m.map { it.measuredAt }) }
        measurements = Cache.put(Cache.BODY_RECENT, m)
        trend = Cache.put(Cache.BODY_TREND, t)
        weekly = w
        profile = p
        loaded = true
    }

    LaunchedEffect(Unit) { Busy.track { reload() } }

    if (showFlow) {
        MeasurementFlow(
            onDone = {
                showFlow = false
                status = "Pomiar zapisany"
                scope.launch { reload() }
            },
            onCancel = { showFlow = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        // Nagłówek jak w Treningu - ten sam rozmiar i odstęp od góry, żeby
        // przeskok między zakładkami nie podskakiwał tytułem.
        Text(
            "Waga",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
        )

        SegmentedTabs(
            labels = PAGES,
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
        )

        Spacer(Modifier.height(12.dp))

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.Top,
            // Odstęp między stronami, żeby przy przesuwaniu karty nie sklejały się w jedną.
            pageSpacing = 14.dp
        ) { page ->
            // Przed odpowiedzią bazy strona zostaje pusta: nie ma czego
            // pokazać, a pusty stan („Brak pomiarów") byłby nieprawdą.
            if (!loaded) return@HorizontalPager
            when (page) {
                0 -> MassPage(
                    measurements = measurements,
                    trend = trend,
                    weekly = weekly,
                    status = status,
                    onNew = { status = null; showFlow = true },
                    onAll = { scope.launch { pager.animateScrollToPage(2) } }
                )
                1 -> GirthsPage(
                    latest = latestGirths,
                    changes = girthChanges,
                    profile = profile,
                    onEditProfile = { editProfile = true }
                )
                else -> HistoryPage(
                    measurements = measurements,
                    girthsByTime = girthsByTime,
                    onEdit = { editing = it }
                )
            }
        }
    }

    if (editProfile) {
        BodyProfileDialog(
            profile = profile,
            onDismiss = { editProfile = false },
            onSave = { p ->
                scope.launch {
                    withContext(Dispatchers.IO) { profileRepo.save(p) }
                    editProfile = false
                    reload()
                }
            }
        )
    }

    editing?.let { m ->
        ManualWeightDialog(
            existing = m,
            existingGirths = girthsByTime[m.measuredAt]
                ?.associate { it.site to it.valueCm }
                .orEmpty(),
            onDismiss = { editing = null },
            onSave = { kg, whenMs, composition, newGirths ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.update(
                            m.copy(
                                weightKg = kg,
                                measuredAt = whenMs,
                                composition = composition
                            )
                        )
                        // previousAt, bo przy zmianie daty obwody mają pójść
                        // za masą, a nie zostać w starym terminie.
                        metricsRepo.replaceAt(whenMs, newGirths, previousAt = m.measuredAt)
                    }
                    editing = null
                    status = "Poprawiono pomiar"
                    reload()
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.delete(m.id)
                        // Obwody bez masy byłyby osieroconym wpisem, którego
                        // nie da się już zobaczyć ani poprawić.
                        metricsRepo.replaceAt(m.measuredAt, emptyMap())
                    }
                    editing = null
                    status = "Usunięto pomiar"
                    reload()
                }
            }
        )
    }
}

// ---- Przełącznik podzakładek --------------------------------------------

/**
 * Przełącznik segmentowy. Nie systemowy `TabRow`: ten rysuje pasek na całą
 * szerokość z kreską pod spodem i wygląda jak drugi pasek zakładek zaraz pod
 * nagłówkiem, a to tylko trzy widoki jednej rzeczy.
 *
 * Narożniki jak przyciski „Siłowy / Kardio / Inne" w Treningu. Pierwsza
 * wersja była okrągłą pigułką i odstawała od reszty kanciastej aplikacji.
 */
@Composable
internal fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(8.dp)
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "tlo"
            )
            val fg by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "napis"
            )
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

// ---- 1. Masa --------------------------------------------------------------

@Composable
private fun MassPage(
    measurements: List<BodyMeasurement>,
    trend: List<Pair<Long, Double>>,
    weekly: Double?,
    status: String?,
    onNew: () -> Unit,
    onAll: () -> Unit
) {
    val latest = measurements.firstOrNull()
    val previous = measurements.getOrNull(1)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        // Liczba, zmiana i przycisk w jednej karcie: to, co się robi na tym
        // ekranie, stoi przy tym, na co się patrzy. Osobny przycisk na dole
        // spychał wykres i wyglądał jak podpis pod pustą kartą.
        item(key = "teraz") {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    if (latest == null) {
                        Text("Brak pomiarów", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Naciśnij Nowy pomiar i stań na wadze.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Ostatni pomiar · " + dayAndTime(latest.measuredAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            previous?.let { p ->
                                TagPill(
                                    signed(latest.weightKg - p.weightKg, "kg"),
                                    tone = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                kg2(latest.weightKg),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 60.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 64.sp
                            )
                            Text(
                                " kg",
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                    // Jedna ścieżka pomiaru: waga i obwody jako jedna sesja.
                    // Pomiar wstecz to ta sama ścieżka z inną datą, wybieraną
                    // już w kreatorze - nie osobny przycisk.
                    Button(
                        onClick = onNew,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp)
                    ) { Text("Nowy pomiar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        if (latest != null) {
            item(key = "kafelki") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Trend",
                        value = trend.lastOrNull()?.second?.let { "%.1f kg".format(it) } ?: "-",
                        note = "bez dziennych wahań",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "Na tydzień",
                        value = weekly?.let { signed(it, "kg") } ?: "-",
                        note = weekly?.let { weeklyComment(it) } ?: "za mało pomiarów",
                        modifier = Modifier.weight(1f)
                    )
                    val first = measurements.last()
                    StatTile(
                        label = "Od początku",
                        value = if (measurements.size > 1) signed(latest.weightKg - first.weightKg, "kg") else "-",
                        note = "od " + shortDate(first.measuredAt),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "wykres") {
            Spacer(Modifier.height(10.dp))
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text("Masa i trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    WeightChart(
                        raw = measurements.map { it.measuredAt to it.weightKg }.reversed(),
                        trend = trend
                    )
                }
            }
        }

        if (measurements.size > 1) {
            item(key = "ostatnie") {
                Spacer(Modifier.height(10.dp))
                AppCard {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ostatnie pomiary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onAll) { Text("wszystkie") }
                    }
                    measurements.take(3).forEachIndexed { i, m ->
                        val delta = measurements.getOrNull(i + 1)?.let { m.weightKg - it.weightKg }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dayAndTime(m.measuredAt),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(kg2(m.weightKg), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            delta?.let {
                                TagPill(signed(it, ""), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

private fun shortDate(ms: Long): String = SimpleDateFormat("d.MM", PL).format(Date(ms))

// ---- 3. Historia ----------------------------------------------------------

/** Wiersz listy historii: nagłówek miesiąca albo pomiar. */
private sealed interface HistoryRow {
    data class Month(val title: String) : HistoryRow
    data class Entry(val m: BodyMeasurement, val delta: Double?) : HistoryRow
}

@Composable
private fun HistoryPage(
    measurements: List<BodyMeasurement>,
    girthsByTime: Map<Long, List<BodyMetric>>,
    onEdit: (BodyMeasurement) -> Unit
) {
    if (measurements.isEmpty()) {
        Text(
            "Brak pomiarów.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    // Miesiące jako nagłówki: przy stu dwudziestu wpisach sama lista dat
    // przestaje mówić, gdzie się jest.
    val rows = remember(measurements) {
        buildList {
            var month: String? = null
            measurements.forEachIndexed { i, m ->
                val mm = monthTitle(m.measuredAt)
                if (mm != month) { add(HistoryRow.Month(mm)); month = mm }
                // Lista idzie od najnowszego, więc poprzedni pomiar to następny wiersz.
                val delta = measurements.getOrNull(i + 1)?.let { m.weightKg - it.weightKg }
                add(HistoryRow.Entry(m, delta))
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(rows, key = {
            when (it) {
                is HistoryRow.Month -> "m-" + it.title
                is HistoryRow.Entry -> it.m.id
            }
        }) { row ->
            when (row) {
                is HistoryRow.Month -> GroupLabel(row.title, Modifier.padding(top = 0.dp))
                is HistoryRow.Entry -> HistoryCard(
                    m = row.m,
                    delta = row.delta,
                    girths = girthsByTime[row.m.measuredAt].orEmpty(),
                    onClick = { onEdit(row.m) }
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    m: BodyMeasurement,
    delta: Double?,
    girths: List<BodyMetric>,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Filled.MonitorWeight, MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(dayAndTime(m.measuredAt), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                // Obwody zmierzone w tym samym momencie co masa - jeden pomiar,
                // więc jedna karta.
                if (girths.isNotEmpty()) {
                    Text(
                        girths.sortedBy { it.site.ordinal }.joinToString(" · ") {
                            "%s %.1f".format(it.site.label.lowercase(), it.valueCm)
                        } + " cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    kg2(m.weightKg) + " kg",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                delta?.let { TagPill(signed(it, ""), modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

// ---- Wspólne --------------------------------------------------------------

/**
 * Komentarz do tempa zmiany masy.
 *
 * Progi nie są przypadkowe: około 0,5% masy ciała na tydzień to tempo, przy
 * którym na redukcji traci się głównie tłuszcz, a na masie przybiera się
 * z sensowną proporcją mięśni. Szybciej znaczy zwykle więcej strat po złej stronie.
 * Na ekranie zostało samo nazwanie tempa - uzasadnienie tylko tutaj.
 */
private fun weeklyComment(w: Double): String = when {
    w > 0.7 -> "szybki przyrost"
    w > 0.15 -> "spokojny przyrost"
    w >= -0.15 -> "masa stabilna"
    w >= -0.9 -> "spokojna redukcja"
    else -> "szybka redukcja"
}

private val PL = Locale("pl", "PL")

private fun kg2(v: Double): String = "%.2f".format(v)

/** Zawsze ze znakiem: samo „0,40" nie mówi, czy przybyło, czy ubyło. */
private fun signed(v: Double, unit: String): String {
    val s = "%.2f".format(kotlin.math.abs(v))
    val sign = when {
        kotlin.math.abs(v) < 0.005 -> "±"
        v > 0 -> "+"
        else -> "-"
    }
    return if (unit.isEmpty()) "$sign$s" else "$sign$s $unit"
}

private fun longDate(ms: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", PL).format(Date(ms))

private fun dayAndTime(ms: Long): String =
    SimpleDateFormat("d MMMM, HH:mm", PL).format(Date(ms))

// LLLL to mianownik („listopad"), MMMM dałoby dopełniacz („listopada").
private fun monthTitle(ms: Long): String =
    SimpleDateFormat("LLLL yyyy", PL).format(Date(ms)).replaceFirstChar { it.uppercase() }
