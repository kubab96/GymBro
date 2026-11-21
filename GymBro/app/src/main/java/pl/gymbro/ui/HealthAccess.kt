package pl.gymbro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.health.HealthImporter
import pl.gymbro.health.HealthReader

/**
 * Ostrzeżenie o braku dostępu do Health Connect, z przyciskiem do naprawy.
 *
 * Zgody Health Connect należą do nazwy pakietu. Po zmianie nazwy z HybridLog
 * na GymBro przepadły po cichu - import łapał wyjątek, zapisywał go w logu
 * i przez miesiąc z zegarka nie przyszło nic: ani tętno, ani biegi. Jedyny
 * przycisk do przyznania dostępu leżał w Profilu, trzy ekrany w głąb.
 * Dlatego brak zgody widać teraz tam, gdzie widać jego skutki.
 *
 * Nic nie rysuje, gdy dostęp jest albo gdy Health Connect na telefonie nie ma.
 */
@Composable
internal fun HealthAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reader = remember { HealthReader(context) }
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    suspend fun check() {
        missing = withContext(Dispatchers.IO) {
            if (reader.availability() != HealthReader.Availability.OK) return@withContext false
            val granted = runCatching { reader.grantedPermissions() }.getOrDefault(emptySet())
            ESSENTIAL.any { it !in granted }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch {
            check()
            if (!missing) {
                // Od razu po zgodzie, nie przy następnej automatycznej
                // synchronizacji za kilka godzin - użytkownik właśnie
                // chce zobaczyć swoje tętno.
                syncing = true
                withContext(Dispatchers.IO) { runCatching { HealthImporter(context).sync() } }
                syncing = false
                Refresh.bump()
            }
        }
    }

    LaunchedEffect(Unit) { check() }

    if (syncing) {
        Text(
            "Pobieram dane z zegarka…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = modifier
        )
        return
    }
    if (!missing) return

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Zegarek nie przesyła danych", style = MaterialTheme.typography.titleMedium)
            Text(
                "Brak zgody na odczyt z Health Connect, więc nie dociera tętno, biegi " +
                    "ani kroki. Health Connect oddaje dane najwyżej 30 dni wstecz od " +
                    "przyznania zgody - im wcześniej, tym mniej przepadnie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { launcher.launch(HealthReader.PERMISSIONS) }) {
                Text("Przyznaj dostęp")
            }
        }
    }
}

/** Bez tych dwóch ani tętna, ani biegów - reszta to dodatki. */
private val ESSENTIAL = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class)
)
