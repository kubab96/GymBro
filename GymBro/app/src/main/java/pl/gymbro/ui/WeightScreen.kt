package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.ScaleScreen
import pl.gymbro.data.BodyMeasurement
import pl.gymbro.data.BodyFat
import pl.gymbro.data.BodyMetric
import pl.gymbro.data.Cache
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.MetricsRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran masy ciała.
 *
 * Zastępuje surowy ekran diagnostyczny z fazy rozpoznania - ten jest nadal
 * dostępny pod przyciskiem, bo przydaje się, gdy waga zacznie się zachowywać
 * inaczej, niż zakładamy.
 *
 * Założenie, które rządzi tym ekranem: **pojedynczy pomiar nic nie znaczy**.
 * Masa ciała waha się w ciągu doby o ponad kilogram, więc na pierwszym planie
 * jest trend i tempo zmiany na tydzień, a nie ostatnia liczba z wagi.
 */
@Composable
fun WeightScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { BodyRepository(context) }
    val metricsRepo = remember { MetricsRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }

    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showMeasurements by rememberSaveable { mutableStateOf(false) }
    var showFlow by remember { mutableStateOf(false) }

    // Diagnostyka BLE nie jest osobnym ekranem z własnym uchwytem, więc wstecz
    // obsługujemy tutaj. Pomiar i obwody mają swoje i wygrywają, bo rejestrują
    // się później.
    ScreenBackHandler(enabled = showDiagnostics) { showDiagnostics = false }
    // Naciskiem w otwarta zakladke wracamy do glownego widoku wagi.
    // Rozpoczetego pomiaru to nie przerywa - wpisane wartosci przepadlyby.
    TabReselectHandler {
        showDiagnostics = false
        showMeasurements = false
    }
    var manualDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }

    var measurements by remember {
        mutableStateOf(Cache.peek<List<BodyMeasurement>>(Cache.BODY_RECENT) ?: emptyList())
    }
    var trend by remember {
        mutableStateOf(Cache.peek<List<Pair<Long, Double>>>(Cache.BODY_TREND) ?: emptyList())
    }
    var weekly by remember { mutableStateOf<Double?>(null) }
    var bodyFat by remember { mutableStateOf<Double?>(null) }
    var profile by remember { mutableStateOf(UserProfile()) }
    var status by remember { mutableStateOf<String?>(null) }

    var girthsByTime by remember { mutableStateOf<Map<Long, List<BodyMetric>>>(emptyMap()) }

    suspend fun reload() {
        val m = withContext(Dispatchers.IO) { repo.recent(120) }
        val t = withContext(Dispatchers.IO) { repo.trend() }
        val w = withContext(Dispatchers.IO) { repo.weeklyChangeKg() }
        val p = withContext(Dispatchers.IO) { profileRepo.get() }
        val girths = withContext(Dispatchers.IO) { metricsRepo.latestPerSite() }
        // Obwody zapisane w tych samych momentach co pomiary masy - żeby historia
        // pokazywała jeden pomiar, a nie osobno kilogramy i osobno centymetry.
        girthsByTime = withContext(Dispatchers.IO) { metricsRepo.forTimes(m.map { it.measuredAt }) }
        measurements = Cache.put(Cache.BODY_RECENT, m)
        trend = Cache.put(Cache.BODY_TREND, t)
        weekly = w
        profile = p
        // Tkanka tłuszczowa liczy się z obwodów, nie z masy - dlatego nie ma jej
        // już wśród pól wpisywanych ręcznie przy pomiarze.
        bodyFat = BodyFat.navy(
            sex = p.sex,
            heightCm = p.heightCm,
            waistCm = girths[MeasurementSite.WAIST]?.valueCm,
            neckCm = girths[MeasurementSite.NECK]?.valueCm,
            hipsCm = girths[MeasurementSite.HIPS]?.valueCm
        )
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

    if (showMeasurements) {
        MeasurementsScreen(onBack = { showMeasurements = false })
        return
    }

    if (showDiagnostics) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Diagnostyka BLE", style = MaterialTheme.typography.titleMedium)
            }
            ScaleScreen()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Masa ciała", style = MaterialTheme.typography.headlineMedium)
            Row {
                TextButton(onClick = { showMeasurements = true }) { Text("Obwody") }
                TextButton(onClick = { showDiagnostics = true }) { Text("Diagnostyka") }
            }
        }

        // ---- Podsumowanie ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                val latest = measurements.firstOrNull()
                val trendNow = trend.lastOrNull()?.second

                if (latest == null) {
                    Text("Brak pomiarów", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Naciśnij Zmierz i wejdź na wagę, albo wpisz masę ręcznie.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("%.2f kg".format(latest.weightKg), fontSize = 40.sp)
                    Text(
                        "ostatni pomiar · ${fullDate(latest.measuredAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (trendNow != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "trend: %.2f kg".format(trendNow),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    weekly?.let { w ->
                        val sign = if (w >= 0) "+" else ""
                        Text(
                            "$sign%.2f kg / tydzień".format(w),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            weeklyComment(w),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Tkanka tłuszczowa liczona z najświeższych obwodów.
                    bodyFat?.let { bf ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "%.1f %% tkanki tłuszczowej".format(bf),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "szacunek z obwodów · ${BodyFat.comment(profile.sex, bf)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Wykres ----
        WeightChart(
            raw = measurements.map { it.measuredAt to it.weightKg }.reversed(),
            trend = trend
        )

        Spacer(Modifier.height(8.dp))

        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Jedna ścieżka pomiaru: waga i obwody jako jedna sesja, zapisane
            // ze wspólnym znacznikiem czasu.
            Button(
                onClick = { status = null; showFlow = true },
                modifier = Modifier.weight(1f)
            ) { Text("Zmierz") }

            OutlinedButton(
                onClick = { manualDialog = true },
                modifier = Modifier.weight(1f)
            ) { Text("Wpisz wstecz") }
        }

        // ---- Historia ----
        Text("Historia", style = MaterialTheme.typography.titleMedium)
        Text(
            "Dotknij pozycji, żeby ją poprawić lub usunąć.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(measurements, key = { it.id }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editing = m }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("%.2f kg".format(m.weightKg), fontFamily = FontFamily.Monospace)
                        Text(
                            fullDate(m.measuredAt) + " · " + m.source +
                                (m.composition.bodyFatPct?.let { " · %.1f %% tł.".format(it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Obwody zmierzone w tym samym momencie co masa.
                        //
                        // Wpisywane są w jednym przebiegu i zapisywane z jednym
                        // znacznikiem czasu, więc rozdzielanie ich w historii na
                        // kilogramy tutaj i centymetry gdzie indziej rozbijało
                        // jeden pomiar na dwa niepowiązane fakty.
                        girthsByTime[m.measuredAt]
                            ?.sortedBy { it.site.ordinal }
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { list ->
                                Text(
                                    list.joinToString(" · ") {
                                        "%s %.1f".format(it.site.label.lowercase(), it.valueCm)
                                    } + " cm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                    }
                }
            }
        }
    }

    if (manualDialog) {
        ManualWeightDialog(
            onDismiss = { manualDialog = false },
            onSave = { kg, whenMs, composition, newGirths ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repo.record(
                            weightKg = kg,
                            impedanceOhm = null,
                            source = "ręcznie",
                            at = whenMs,
                            composition = composition
                        )
                        // Ten sam znacznik czasu co masa - jeden pomiar.
                        metricsRepo.replaceAt(whenMs, newGirths)
                    }
                    manualDialog = false
                    status = "Zapisano %.2f kg".format(kg)
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

/**
 * Komentarz do tempa zmiany masy.
 *
 * Progi nie są przypadkowe: około 0,5% masy ciała na tydzień to tempo, przy
 * którym na redukcji traci się głównie tłuszcz, a na masie przybiera się
 * z sensowną proporcją mięśni. Szybciej znaczy zwykle więcej strat po złej stronie.
 */
private fun weeklyComment(w: Double): String = when {
    w > 0.7 -> "szybki przyrost - przy budowaniu masy sporo z tego będzie tłuszczem"
    w > 0.15 -> "spokojny przyrost"
    w >= -0.15 -> "masa stabilna"
    w >= -0.9 -> "spokojna redukcja"
    else -> "szybka redukcja - przy takim tempie trudno utrzymać siłę"
}

private fun fullDate(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))


