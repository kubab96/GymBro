package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.data.BodyFat
import pl.gymbro.data.BodyMetric
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.Sex
import pl.gymbro.data.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Podzakładka Obwody: najświeższa wartość każdego obwodu i jego zmiana.
 *
 * Powstało po tym, jak okazało się, że waga nie mierzy bioimpedancji, tylko
 * liczy „skład ciała" ze wzoru opartego na masie. Obwód talii jest realnym
 * pomiarem tłuszczu brzusznego - masa ciała nie jest niczym takim.
 *
 * Strona tylko pokazuje - wpisywać stąd nie można. Wcześniej dało się, i każdy
 * taki wpis dostawał własny znacznik czasu, więc nie łączył się z żadnym
 * ważeniem i nie pojawiał się w historii. Jeden pomiar to jeden moment, więc
 * jest jedno miejsce, w którym powstaje: Nowy pomiar albo poprawka w Historii.
 *
 * Instrukcje przykładania taśmy są w kreatorze, przy polu do wpisania - tutaj
 * zasłaniały liczby, po które się na tę stronę przychodzi.
 */
@Composable
internal fun GirthsPage(
    latest: Map<MeasurementSite, BodyMetric>,
    changes: Map<MeasurementSite, Double>,
    profile: UserProfile,
    onEditProfile: () -> Unit
) {
    val bf = BodyFat.navy(
        profile.sex,
        profile.heightCm,
        latest[MeasurementSite.WAIST]?.valueCm,
        latest[MeasurementSite.NECK]?.valueCm,
        latest[MeasurementSite.HIPS]?.valueCm
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BodyFatCard(bf = bf, latest = latest, profile = profile, onEditProfile = onEditProfile)
        }

        if (latest.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Brak obwodów. Wpiszesz je przy Nowym pomiarze.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        items(MeasurementSite.entries.toList(), key = { it.name }) { site ->
            GirthTile(site = site, current = latest[site], change = changes[site])
        }
    }
}

@Composable
private fun BodyFatCard(
    bf: Double?,
    latest: Map<MeasurementSite, BodyMetric>,
    profile: UserProfile,
    onEditProfile: () -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Tkanka tłuszczowa",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bf != null) {
                    Text(
                        "%.1f %%".format(bf),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        BodyFat.comment(profile.sex, bf),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val missing = BodyFat.missing(
                        profile.sex,
                        profile.heightCm,
                        latest[MeasurementSite.WAIST]?.valueCm,
                        latest[MeasurementSite.NECK]?.valueCm,
                        latest[MeasurementSite.HIPS]?.valueCm
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Brakuje: " + missing.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            TextButton(onClick = onEditProfile) {
                Text(if (profile.heightCm == null) "Podaj wzrost" else "wzrost ${profile.heightCm?.toInt()} cm")
            }
        }
    }
}

@Composable
private fun GirthTile(site: MeasurementSite, current: BodyMetric?, change: Double?) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BodyDiagram(site, Modifier.size(38.dp, 58.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    site.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (current != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "%.1f".format(current.valueCm),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            " cm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    // Kierunek zmiany jest tu ważniejszy niż sama wartość.
                    Text(
                        change?.let { d ->
                            (if (d >= 0) "+" else "") + "%.1f cm / 8 tyg.".format(d)
                        } ?: shortDate(current.measuredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (change != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("-", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "brak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Wzrost i płeć - bez nich tkanki tłuszczowej z obwodów nie da się policzyć. */
@Composable
internal fun BodyProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var height by remember { mutableStateOf(profile.heightCm?.let { "%.0f".format(it) } ?: "") }
    var sex by remember { mutableStateOf(profile.sex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wzrost i płeć") },
        text = {
            Column {
                Text(
                    "Potrzebne do tkanki tłuszczowej.",
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
    SimpleDateFormat("d.MM.yyyy", Locale.getDefault()).format(Date(ms))
