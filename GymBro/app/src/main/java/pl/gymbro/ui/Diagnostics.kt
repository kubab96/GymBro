package pl.gymbro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.BuildConfig
import pl.gymbro.ble.ScaleScanner
import pl.gymbro.health.HealthReader

/**
 * Ekrany diagnostyczne: podsłuch BLE i surowy zrzut z Health Connect.
 *
 * Zostały z fazy rozpoznania, kiedy były całą aplikacją, i długo mieszkały
 * w MainActivity obok nawigacji. Nie są częścią tego, co aplikacja robi -
 * służą do rozgryzania wagi, której zwykły pomiar nie łapie, i do sprawdzania,
 * co zegarek faktycznie przysyła. Dlatego wejścia do nich pokazują się tylko
 * w wariancie debug ([DIAGNOSTICS]): w wydaniu przycisk „Diagnostyka" obok
 * „Obwodów" wyglądał jak funkcja, a prowadził do logu pakietów.
 *
 * Wartość pochodzi z BuildConfig, więc w wariancie release R8 wycina te ekrany
 * razem z całym kodem, do którego tylko one sięgają.
 */
val DIAGNOSTICS: Boolean = BuildConfig.DEBUG

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ============================================================ WAGA / BLE ===

@Composable
internal fun BleDiagnosticsScreen() {
    val context = LocalContext.current
    val scanner = remember { ScaleScanner(context) }
    val devices by scanner.devices.collectAsState()
    val log by scanner.log.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    val connected by scanner.connected.collectAsState()

    var hasPerms by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPerms = result.values.all { it } }

    LaunchedEffect(Unit) { permLauncher.launch(ScaleScanner.permissions()) }

    // Tanie wagi BLE obsługują naraz tylko jednego klienta. Skanowanie zostawione
    // w tle blokuje wagę dla aplikacji producenta - i właśnie tak się stało:
    // parowanie w OKOK nie przechodziło, dopóki ten ekran nie przestał nasłuchiwać.
    DisposableEffect(Unit) {
        onDispose {
            scanner.stopScan()
            scanner.disconnect()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // Wagi OKOK rozglaszaja pomiar w pakiecie reklamowym, wiec nie trzeba
        // sie z nimi laczyc. Wystarczy wlaczyc skanowanie i stanac na wadze.
        val stable by scanner.lastStable.collectAsState()
        if (stable != null) {
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("%.2f kg".format(stable!!.weightKg), fontSize = 34.sp)
                    stable!!.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stable!!.source,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Text(
            "Tu widać wszystko, co telefon słyszy - służy do rozgryzania wagi,\n" +
                "której zwykły pomiar nie łapie.\n\n" +
                "Krok 1: włącz skanowanie.\n" +
                "Krok 2: stań na wadze i stój, aż pokaże wynik.\n" +
                "Krok 3: jeśli masa nie wyskoczy sama, dotknij wagę na liście -\n" +
                "        część wag oddaje pomiar dopiero po połączeniu.\n" +
                "Krok 4: powtórz 2-3 razy i skopiuj log.",
            style = MaterialTheme.typography.bodySmall
        )

        val packets by scanner.packets.collectAsState()
        var locationOn by remember { mutableStateOf(scanner.isLocationOn()) }

        if (!locationOn) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Usługi lokalizacji są wyłączone. Android w tym stanie potrafi " +
                            "nie zwrócić ani jednego wyniku skanowania BLE i nie zgłosić błędu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    ) { Text("Włącz lokalizację") }
                }
            }
        }

        Text(
            "urządzenia: ${devices.size}    pakiety: $packets",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    locationOn = scanner.isLocationOn()
                    if (scanning) scanner.stopScan() else scanner.startScan()
                },
                enabled = hasPerms
            ) { Text(if (scanning) "Stop" else "Skanuj") }

            OutlinedButton(
                onClick = { scanner.disconnect() },
                enabled = connected != null
            ) { Text("Rozłącz") }

            OutlinedButton(onClick = {
                copyToClipboard(context, "GymBro BLE", scanner.logAsText())
            }) { Text("Kopiuj log") }

            OutlinedButton(onClick = { scanner.clearLog() }) { Text("Wyczyść") }
        }

        if (!hasPerms) {
            Text(
                "Brak uprawnień Bluetooth. Przyznaj je w ustawieniach aplikacji.",
                color = MaterialTheme.colorScheme.error
            )
        }

        // Kandydaci na wage - pokazujemy tylko te urzadzenia, ktore rozpoznalismy,
        // a ponizej wszystkie pozostale, gdyby waga rozglaszala sie nietypowo.
        val likely = devices.values.filter { it.family != null }.sortedByDescending { it.rssi }
        val rest = devices.values.filter { it.family == null }.sortedByDescending { it.rssi }

        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            if (likely.isNotEmpty()) {
                item { SectionLabel("Prawdopodobne wagi") }
                items(likely, key = { it.address }) { d -> DeviceRow(d.name, d.address, d.rssi, d.family) { scanner.connect(d.address) } }
            }
            if (rest.isNotEmpty()) {
                item { SectionLabel("Pozostałe urządzenia (${rest.size})") }
                items(rest.take(30), key = { it.address }) { d -> DeviceRow(d.name, d.address, d.rssi, null) { scanner.connect(d.address) } }
            }
        }

        SectionLabel("Log (${log.size} linii)")
        // weight() jest modyfikatorem ColumnScope, wiec przekazujemy go z tego miejsca.
        LogView(log, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun DeviceRow(
    name: String?,
    address: String,
    rssi: Int,
    family: String?,
    onClick: () -> Unit
) {
    // Card(onClick = ...) jest oznaczony jako eksperymentalny, wiec klikalnosc
    // dokladamy modyfikatorem - mniej ostrzezen przy kompilacji.
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name ?: "(bez nazwy)", style = MaterialTheme.typography.bodyLarge)
                Text("$rssi dBm", style = MaterialTheme.typography.bodySmall)
            }
            Text(address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            if (family != null) {
                Text(family, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LogView(lines: List<String>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) state.animateScrollToItem(lines.size - 1)
    }
    LazyColumn(modifier, state = state) {
        items(lines) { line ->
            Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

// ================================================== ZEGAREK / HEALTH CONNECT

@Composable
internal fun HealthDumpScreen() {
    val context = LocalContext.current
    val reader = remember { HealthReader(context) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(reader.availability()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dump by remember { mutableStateOf("Naciśnij \"Wczytaj dane\", żeby zobaczyć, co przyszło z zegarka.") }
    var busy by remember { mutableStateOf(false) }

    val hcLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result }

    LaunchedEffect(status) {
        if (status == HealthReader.Availability.OK) {
            granted = runCatching { reader.grantedPermissions() }.getOrDefault(emptySet())
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        when (status) {
            HealthReader.Availability.UNAVAILABLE -> {
                Text(
                    "Health Connect nie jest dostępny na tym urządzeniu.\n" +
                        "Na Androidzie 13 i starszym trzeba zainstalować aplikację Health Connect ze Sklepu Play.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                        )
                    }
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Otwórz w Sklepie Play") }
            }

            HealthReader.Availability.NEEDS_UPDATE -> {
                Text("Health Connect wymaga aktualizacji.", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(HealthReader.PLAY_STORE_HEALTH_CONNECT))
                        )
                    }
                    status = reader.availability()
                }, modifier = Modifier.padding(top = 8.dp)) { Text("Aktualizuj") }
            }

            HealthReader.Availability.OK -> {
                Text(
                    "Zanim wczytasz dane, w aplikacji Zepp wejdź w:\n" +
                        "Profil > Konta zewnętrzne > Health Connect i zezwól na zapis.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Przyznane uprawnienia: ${granted.size} / ${HealthReader.PERMISSIONS.size}",
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { hcLauncher.launch(HealthReader.PERMISSIONS) }) {
                        Text("Przyznaj dostęp")
                    }
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                dump = withContext(Dispatchers.IO) {
                                    runCatching { reader.dumpLastDays(30) }
                                        .getOrElse { "BŁĄD: ${it.message}" }
                                }
                                busy = false
                            }
                        }
                    ) { Text(if (busy) "Czytam..." else "Wczytaj dane") }

                    OutlinedButton(onClick = {
                        copyToClipboard(context, "GymBro HC", dump)
                    }) { Text("Kopiuj") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(dump.lines()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }
}

