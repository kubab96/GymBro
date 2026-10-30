package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.BodyFat
import pl.gymbro.data.BodyMetric
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.MetricsRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.Sex
import pl.gymbro.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Obwody ciała.
 *
 * Powstało po tym, jak okazało się, że waga nie mierzy bioimpedancji, tylko
 * liczy „skład ciała" ze wzoru opartego na masie. Obwód talii jest realnym
 * pomiarem tłuszczu brzusznego - masa ciała nie jest niczym takim.
 *
 * Przy obwodach cała wartość leży w powtarzalności, dlatego każde miejsce ma
 * rysunek i instrukcję. Dwa centymetry różnicy biorą się częściej ze zmiany
 * miejsca przyłożenia taśmy niż ze zmiany ciała.
 *
 * Ekran tylko pokazuje - wpisywać stąd nie można. Wcześniej dało się, i każdy
 * taki wpis dostawał własny znacznik czasu, więc nie łączył się z żadnym
 * ważeniem i nie pojawiał się w historii. Jeden pomiar to jeden moment, więc
 * jest jedno miejsce, w którym powstaje: kreator „Zmierz" albo poprawka wpisu.
 */
@Composable
fun MeasurementsScreen(onBack: () -> Unit) {
    // Systemowy wstecz robi to samo co „Wróć" - bez tego wychodził z aplikacji.
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metricsRepo = remember { MetricsRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }

    var latest by remember { mutableStateOf<Map<MeasurementSite, BodyMetric>>(emptyMap()) }
    var changes by remember { mutableStateOf<Map<MeasurementSite, Double>>(emptyMap()) }
    var profile by remember { mutableStateOf(UserProfile()) }
    var editProfile by remember { mutableStateOf(false) }

    suspend fun reload() {
        latest = withContext(Dispatchers.IO) { metricsRepo.latestPerSite() }
        changes = withContext(Dispatchers.IO) {
            MeasurementSite.entries.mapNotNull { s ->
                metricsRepo.changeCm(s)?.let { s to it }
            }.toMap()
        }
        profile = withContext(Dispatchers.IO) { profileRepo.get() }
    }

    LaunchedEffect(Unit) { reload() }

    val waist = latest[MeasurementSite.WAIST]?.valueCm
    val neck = latest[MeasurementSite.NECK]?.valueCm
    val hips = latest[MeasurementSite.HIPS]?.valueCm
    val bf = BodyFat.navy(profile.sex, profile.heightCm, waist, neck, hips)

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Obwody", style = MaterialTheme.typography.headlineMedium)
        }

        Text(
            "Podgląd i instrukcje. Obwody wpisujesz przy pomiarze - w kreatorze " +
                "„Zmierz” albo poprawiając wpis w historii.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Oszacowanie tkanki tłuszczowej ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(14.dp)) {
                if (bf != null) {
                    Text("%.1f %%".format(bf), fontSize = 32.sp)
                    Text(
                        "szacowana tkanka tłuszczowa · ${BodyFat.comment(profile.sex, bf)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Metoda US Navy, z obwodów. To oszacowanie z błędem rzędu " +
                            "±3-4 punktów, nie pomiar. Patrz na kierunek zmian, " +
                            "nie na samą liczbę.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val missing = BodyFat.missing(
                        profile.sex, profile.heightCm, waist, neck, hips
                    )
                    Text("Oszacowanie tkanki tłuszczowej", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Brakuje: " + missing.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { editProfile = true }) {
                    Text(
                        if (profile.heightCm == null) "Uzupełnij profil"
                        else "Profil: ${profile.heightCm?.toInt()} cm"
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Mierz rano, przed jedzeniem, zawsze o podobnej porze. Taśma ma " +
                "przylegać, ale nie wcinać się w skórę.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(MeasurementSite.entries.toList(), key = { it.name }) { site ->
                SiteCard(
                    site = site,
                    current = latest[site],
                    change = changes[site],
                )
            }
        }
    }

    if (editProfile) {
        ProfileDialog(
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
}

@Composable
private fun SiteCard(
    site: MeasurementSite,
    current: BodyMetric?,
    change: Double?,
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyDiagram(site, Modifier.size(74.dp, 112.dp))

            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(site.label, style = MaterialTheme.typography.titleMedium)
                    if (site.usedForBodyFat) {
                        Text(
                            "  · do oszacowania",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (current != null) {
                    Text("%.1f cm".format(current.valueCm), fontSize = 22.sp)
                    Text(
                        shortDate(current.measuredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Kierunek zmiany jest tu ważniejszy niż sama wartość.
                    change?.let { d ->
                        val sign = if (d >= 0) "+" else ""
                        Text(
                            "$sign%.1f cm przez 8 tygodni".format(d),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        "brak pomiaru",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    site.howTo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
        }
    }
}

@Composable
private fun ProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var height by remember { mutableStateOf(profile.heightCm?.let { "%.0f".format(it) } ?: "") }
    var sex by remember { mutableStateOf(profile.sex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil") },
        text = {
            Column {
                Text(
                    "Wzrost i płeć wchodzą do wzoru na oszacowanie tkanki tłuszczowej.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Wzrost (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { s ->
                        val label = if (s == Sex.MALE) "mężczyzna" else "kobieta"
                        if (s == sex) {
                            Button(onClick = { sex = s }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(onClick = { sex = s }, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    profile.copy(
                        heightCm = height.trim().replace(',', '.').toDoubleOrNull(),
                        sex = sex
                    )
                )
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

private fun shortDate(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))
