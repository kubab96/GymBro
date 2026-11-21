package pl.gymbro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.BuildConfig
import pl.gymbro.health.HealthReader

/**
 * Ekran diagnostyczny: surowy zrzut z Health Connect.
 *
 * Został z fazy rozpoznania, kiedy był całą aplikacją. Nie jest częścią tego,
 * co aplikacja robi - służy do sprawdzania, co zegarek faktycznie przysyła.
 * Dlatego wejście do niego pokazuje się tylko w wariancie debug ([DIAGNOSTICS]).
 *
 * Podsłuch BLE wagi mieszkał tu obok i został wyrzucony: był do testów
 * w fazie rozgryzania wagi, a przycisk „Diagnostyka" obok „Obwodów" robił
 * z zakładki Waga ekran techniczny. Gdyby nowa waga wymagała rozpoznania,
 * log skanera dalej idzie do logcatu w wariancie debug (`GymBroBLE`).
 *
 * Wartość pochodzi z BuildConfig, więc w wariancie release R8 wycina ten ekran
 * razem z całym kodem, do którego tylko on sięga.
 */
val DIAGNOSTICS: Boolean = BuildConfig.DEBUG

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
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

