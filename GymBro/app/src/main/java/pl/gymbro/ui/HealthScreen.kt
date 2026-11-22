package pl.gymbro.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.MetricKind
import pl.gymbro.health.HealthImporter
import pl.gymbro.health.HealthReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dane z zegarka.
 *
 * Nie pokazuje surowych liczb, tylko to, co z nich wynika: czy dzisiejsze HRV
 * i tętno spoczynkowe odstają od Twojej normy. Pojedynczy pomiar HRV nic nie
 * znaczy - dopiero odchylenie od własnej średniej mówi, czy organizm jest
 * wypoczęty. To jest fundament pod moduł gotowości z Fazy 3.
 */
@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reader = remember { HealthReader(context) }
    val importer = remember { HealthImporter(context) }
    val repo = remember { HealthRepository(context) }

    var availability by remember { mutableStateOf(reader.availability()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDump by remember { mutableStateOf(false) }

    // Wstecz schodzi o jeden poziom, a nie wychodzi z aplikacji: najpierw
    // zamyka surowy odczyt, dopiero potem cały ekran zegarka.
    ScreenBackHandler {
        if (showDump) showDump = false else onBack()
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var hrvNow by remember { mutableStateOf<Double?>(null) }
    var hrvAvg by remember { mutableStateOf<Double?>(null) }
    var rhrNow by remember { mutableStateOf<Double?>(null) }
    var rhrAvg by remember { mutableStateOf<Double?>(null) }
    var sleepNow by remember { mutableStateOf<Double?>(null) }
    var sleepAvg by remember { mutableStateOf<Double?>(null) }
    var vo2 by remember { mutableStateOf<Double?>(null) }

    // Po oknie zgody pytamy system od nowa, zamiast wierzyć temu, co zwróciło.
    //
    // Wynik kontraktu zawiera tylko zgody rozpatrzone w tym jednym oknie, nie
    // komplet. Po dołożeniu dwunastego uprawnienia ekran pokazał przez to
    // „Dostęp: 1 z 12", choć pozostałe jedenaście było przyznanych przez cały
    // czas - i dane normalnie się wczytywały. Fałszywe ostrzeżenie o braku
    // dostępu jest gorsze niż jego brak, bo każe szukać usterki tam, gdzie
    // jej nie ma.
    val permLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch {
            granted = runCatching { reader.grantedPermissions() }.getOrDefault(it)
        }
    }

    // Odczyt starszy niż to podawany jako dzisiejszy byłby zwykłym kłamstwem.
    val freshDays = 7

    // VO2max zmienia się przez tygodnie, nie z dnia na dzień - tydzień to dla
    // niego próg absurdalnie ostry. Zegarek wylicza go zresztą rzadko, bo
    // potrzebuje biegu; przy siedmiu dniach nie pokazałby się prawie nigdy.
    val freshDaysVo2 = 90

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            val today = System.currentTimeMillis() / 86_400_000L
            // Pojedynczy odczyt liczy się tylko wtedy, gdy jest świeży.
            //
            // `daily(kind, 1)` zwraca ostatni wpis niezależnie od tego, kiedy
            // powstał. Bez tego progu ekran pokazywał sen sprzed roku jako
            // wartość bieżącą - zegarek noszony wyłącznie na treningach nie
            // dostarcza już takich danych, a stary wpis nigdy nie znika.
            fun recent(kind: MetricKind): Double? =
                repo.daily(kind, 1).firstOrNull()
                    ?.takeIf { today - it.day <= freshDays }
                    ?.value

            hrvNow = recent(MetricKind.HRV_RMSSD)
            hrvAvg = repo.dailyAverage(MetricKind.HRV_RMSSD, 30)
            rhrNow = recent(MetricKind.RESTING_HR)
            rhrAvg = repo.dailyAverage(MetricKind.RESTING_HR, 30)
            sleepNow = recent(MetricKind.SLEEP_MINUTES)
            sleepAvg = repo.dailyAverage(MetricKind.SLEEP_MINUTES, 7)
            vo2 = repo.daily(MetricKind.VO2MAX, 1).firstOrNull()
                ?.takeIf { today - it.day <= freshDaysVo2 }
                ?.value
        }
    }

    LaunchedEffect(Unit) {
        Busy.track {
            if (availability == HealthReader.Availability.OK) {
                granted = runCatching { reader.grantedPermissions() }.getOrDefault(emptySet())
            }
            reload()
        }
    }

    if (DIAGNOSTICS && showDump) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Surowy odczyt", style = MaterialTheme.typography.titleMedium)
            }
            HealthDumpScreen()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SubScreenHeader("Zegarek", "Dane z Amazfit przez Health Connect", onBack)
            }
            // Tylko w wariancie debug - patrz DIAGNOSTICS.
            if (DIAGNOSTICS) {
                TextButton(onClick = { showDump = true }) { Text("Surowe") }
            }
        }

        when (availability) {
            HealthReader.Availability.UNAVAILABLE -> {
                Text(
                    "Health Connect nie jest dostępny. Na Androidzie 13 i starszym " +
                        "trzeba zainstalować go ze Sklepu Play.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Otwórz Sklep Play") }
            }

            HealthReader.Availability.NEEDS_UPDATE -> {
                Text("Health Connect wymaga aktualizacji.", color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                            )
                        }
                        availability = reader.availability()
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Aktualizuj") }
            }

            HealthReader.Availability.OK -> {
                if (granted.size < HealthReader.PERMISSIONS.size) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Dostęp: ${granted.size} z ${HealthReader.PERMISSIONS.size} uprawnień",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "W Zepp włącz Profil → Konta zewnętrzne → Health Connect, " +
                                    "a tutaj przyznaj odczyt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { permLauncher.launch(HealthReader.PERMISSIONS) }) {
                                Text("Przyznaj dostęp")
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            status = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    runCatching { importer.sync(90) }.getOrNull()
                                }
                                status = when {
                                    r == null -> "Synchronizacja nie powiodła się"
                                    r.total == 0 -> "Brak nowych danych - sprawdź Zepp"
                                    else -> {
                                        // Wymieniamy tylko to, co faktycznie
                                        // przyszło. Lista z zerami przy połowie
                                        // pozycji czyta się jak awaria, nawet
                                        // gdy wszystko zadziałało.
                                        val parts = listOfNotNull(
                                            r.sessions.takeIf { it > 0 }
                                                ?.let { plural(it, "trening", "treningi", "treningów") },
                                            r.hrvDays.takeIf { it > 0 }
                                                ?.let { plural(it, "dzień", "dni", "dni") + " HRV" },
                                            r.restingHrDays.takeIf { it > 0 }
                                                ?.let { plural(it, "dzień", "dni", "dni") + " tętna spocz." },
                                            r.sleepDays.takeIf { it > 0 }
                                                ?.let { plural(it, "noc", "noce", "nocy") + " snu" },
                                            r.stepsDays.takeIf { it > 0 }
                                                ?.let { plural(it, "dzień", "dni", "dni") + " kroków" },
                                            r.vo2maxDays.takeIf { it > 0 }
                                                ?.let { plural(it, "dzień", "dni", "dni") + " VO2max" }
                                        )
                                        "Zsynchronizowano: " + parts.joinToString(", ") +
                                            if (r.warnings.isEmpty()) ""
                                            else "\nUwagi: ${r.warnings.joinToString("; ")}"
                                    }
                                }
                                reload()
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (busy) "Synchronizuję…" else "Synchronizuj") }
                }

                status?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                // HRV i tętno spoczynkowe mają sens tylko jako odchylenie od własnej
                // normy - wartość bezwzględna nic nie mówi, bo różnice między ludźmi
                // są większe niż różnice między dniami u jednej osoby.
                // Karty pokazujemy tylko wtedy, gdy są dane. Zegarek noszony
                // wyłącznie na treningach nigdy nie dostarczy snu ani tętna
                // spoczynkowego - stałe "brak" nie jest informacją, tylko szumem.
                DeviationCard(
                    title = "HRV",
                    now = hrvNow,
                    avg = hrvAvg,
                    unit = "ms",
                    higherIsBetter = true,
                    note = "Mierzone przy treningu, nie w nocy - bo wtedy nosisz " +
                        "zegarek. To mniej miarodajne niż pomiar po przebudzeniu, " +
                        "więc traktuj jako wskazówkę, nie wyrok."
                )
                DeviationCard(
                    title = "Tętno spoczynkowe",
                    now = rhrNow,
                    avg = rhrAvg,
                    unit = "bpm",
                    higherIsBetter = false,
                    note = "Podwyższone o kilka uderzeń bywa pierwszym znakiem przemęczenia."
                )
                DeviationCard(
                    title = "Sen",
                    now = sleepNow?.div(60),
                    avg = sleepAvg?.div(60),
                    unit = "h",
                    higherIsBetter = true,
                    note = null
                )
                vo2?.let {
                    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VO2max", style = MaterialTheme.typography.titleMedium)
                            Text("%.1f".format(it), fontSize = 22.sp)
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun DeviationCard(
    title: String,
    now: Double?,
    avg: Double?,
    unit: String,
    higherIsBetter: Boolean,
    note: String?
) {
    // Brak danych oznacza tu zwykle, że zegarek nie był noszony - a nie że
    // coś poszło nie tak. Nie zajmujemy miejsca na komunikat o tym.
    if (now == null && avg == null) return

    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (now != null) "%.1f %s".format(now, unit) else "brak",
                    fontSize = 22.sp
                )
            }

            if (now != null && avg != null && avg > 0) {
                val diffPct = (now - avg) / avg * 100
                val good = if (higherIsBetter) diffPct >= -5 else diffPct <= 5
                Text(
                    "średnia: %.1f %s · odchylenie %+.0f%%".format(avg, unit, diffPct),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (abs(diffPct) < 5) MaterialTheme.colorScheme.onSurfaceVariant
                    else if (good) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
            }

            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionRow(s: ExternalSession) {
    AppCard(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.type, style = MaterialTheme.typography.titleMedium)
                Text("${s.durationMin} min")
            }
            Text(
                dateTime(s.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val details = buildList {
                s.distanceM?.takeIf { it > 50 }?.let { add("%.2f km".format(it / 1000)) }
                s.paceMinPerKm?.let {
                    val min = it.toInt()
                    val sec = ((it - min) * 60).roundToInt()
                    add("%d:%02d /km".format(min, sec))
                }
                s.hrAvg?.let { add("śr. %.0f bpm".format(it)) }
                s.hrMax?.let { add("max %.0f".format(it)) }
                s.energyKcal?.takeIf { it > 1 }?.let { add("%.0f kcal".format(it)) }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun dateTime(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
