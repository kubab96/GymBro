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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.gymbro.data.ExternalSession
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.PhoneTracker
import pl.gymbro.ui.theme.ButtonShape
import java.util.Locale

/**
 * Trwająca aktywność nagrywana telefonem.
 *
 * Duże liczby i dwa przyciski: ekran ogląda się w biegu, z wyciągniętej ręki.
 * Zakończenie pyta, czy zapisać - przy pomyłkowym starcie lepiej odrzucić,
 * niż mieć w historii dwuminutowy „bieg" po szatni.
 */
@Composable
internal fun ActivityLiveScreen(snap: PhoneTracker.Snapshot, onFinished: (ExternalSession?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val live = snap.live
    val kind = live.kind

    Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                kindIcon(kind), contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)
            )
            Text(
                kind.label,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        StatusLine(snap)

        Spacer(Modifier.height(28.dp))
        Clock(snap)

        if (kind.gps) {
            Spacer(Modifier.height(22.dp))
            Text(
                String.format(Locale.getDefault(), "%.2f", snap.distanceM / 1000.0),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text("km", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp))
            Speeds(snap)
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (live.paused) PhoneTracker.resume(context) else PhoneTracker.pause(context)
                },
                enabled = !saving,
                shape = ButtonShape,
                modifier = Modifier.weight(1f).height(64.dp),
                colors = if (live.paused) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (live.paused) "Wznów" else "Pauza", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { asking = true },
                enabled = !saving,
                shape = ButtonShape,
                modifier = Modifier.weight(1f).height(64.dp)
            ) {
                Text("Zakończ", fontSize = 18.sp)
            }
        }
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Zakończyć " + kind.label.lowercase() + "?") },
            text = {
                Text(
                    if (kind.gps)
                        String.format(Locale.getDefault(), "%.2f km", snap.distanceM / 1000.0) +
                            " · " + clockText(live.movingMs(System.currentTimeMillis()))
                    else clockText(live.movingMs(System.currentTimeMillis()))
                )
            },
            confirmButton = {
                Button(onClick = {
                    asking = false
                    saving = true
                    scope.launch { onFinished(PhoneTracker.finish(context)) }
                }) { Text("Zapisz") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { asking = false; discarding = true }) {
                        Text("Odrzuć", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { asking = false }) { Text("Wróć") }
                }
            }
        )
    }

    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text("Odrzucić bez zapisu?") },
            text = { Text("Czas i trasa przepadną. Tego nie da się cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    PhoneTracker.discard(context)
                    onFinished(null)
                }) { Text("Odrzuć", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text("Wróć") } }
        )
    }
}

/**
 * Sam zegar tyka co sekundę i przerysowuje tylko siebie - jak pasek przerwy
 * w treningu siłowym. Cały ekran co sekundę to sześćdziesiąt zbędnych
 * przebudów na minutę.
 */
@Composable
private fun Clock(snap: PhoneTracker.Snapshot) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snap.live.paused) {
        while (true) {
            now = System.currentTimeMillis()
            if (snap.live.paused) break
            delay(1000)
        }
    }
    Text(
        clockText(snap.live.movingMs(now)),
        fontSize = if (snap.live.kind.gps) 40.sp else 72.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = if (snap.live.paused) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
    )
    Text(
        if (snap.live.paused) "pauza" else "czas",
        style = MaterialTheme.typography.titleMedium,
        color = if (snap.live.paused) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Speeds(snap: PhoneTracker.Snapshot) {
    val moving = snap.live.movingMs(snap.lastFixAt ?: System.currentTimeMillis())
    val km = snap.distanceM / 1000.0
    val avgPace = if (km >= 0.05 && moving > 0) (moving / 60_000.0) / km else null
    Row(Modifier.fillMaxWidth()) {
        if (snap.live.kind == PhoneActivity.BIKE) {
            Metric(
                Modifier.weight(1f), "śr. prędkość",
                avgPace?.let { String.format(Locale.getDefault(), "%.1f", 60.0 / it) } ?: "-", "km/h"
            )
            Metric(
                Modifier.weight(1f), "teraz",
                snap.recentPaceMinPerKm?.let { String.format(Locale.getDefault(), "%.1f", 60.0 / it) } ?: "-",
                "km/h"
            )
        } else {
            Metric(Modifier.weight(1f), "śr. tempo", avgPace?.let { paceLabel(it) } ?: "-", "/km")
            Metric(
                Modifier.weight(1f), "tempo teraz",
                snap.recentPaceMinPerKm?.takeIf { it < 60 }?.let { paceLabel(it) } ?: "-", "/km"
            )
        }
    }
}

@Composable
private fun Metric(modifier: Modifier, label: String, value: String, unit: String) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
            Text(
                " $unit", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Stan sygnału. Bez niego pierwsza minuta biegu wyglądała jak usterka:
 * dystans stoi na zerze, a to tylko telefon szuka satelitów.
 */
@Composable
private fun StatusLine(snap: PhoneTracker.Snapshot) {
    if (!snap.live.kind.gps) {
        Text(
            "Stoper - telefon mierzy sam czas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(3000); now = System.currentTimeMillis() } }
    val acc = snap.accuracyM
    val (text, color) = when {
        snap.live.paused -> "GPS wyłączony na czas pauzy" to MaterialTheme.colorScheme.onSurfaceVariant
        snap.lastFixAt == null -> "Szukam sygnału GPS…" to MaterialTheme.colorScheme.tertiary
        now - snap.lastFixAt > 15_000 -> "Brak sygnału GPS" to MaterialTheme.colorScheme.tertiary
        acc == null || acc > TrackMath.MAX_ACCURACY_M ->
            "Słaby sygnał GPS" + (acc?.takeIf { it < 1000 }?.let { " (±${it.toInt()} m)" } ?: "") to
                MaterialTheme.colorScheme.tertiary
        else -> "GPS ±${acc.toInt()} m" to MaterialTheme.colorScheme.secondary
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

private fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
}
