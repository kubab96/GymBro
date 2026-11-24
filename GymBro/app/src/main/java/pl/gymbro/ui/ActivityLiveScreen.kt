package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.LiveActivity
import pl.gymbro.data.restsMs
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.PhoneTracker
import pl.gymbro.ui.theme.ButtonShape
import java.util.Locale

/**
 * Trwająca aktywność nagrywana telefonem.
 *
 * Trzy układy pod jednym nagłówkiem i tymi samymi przyciskami: trasa z mapą,
 * trening siłowy z seriami i sam stoper. Duże liczby, bo ekran ogląda się
 * w biegu albo z ławki, z wyciągniętej ręki.
 *
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

    Column(Modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp)) {
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
        Spacer(Modifier.height(4.dp))
        StatusLine(snap)

        when {
            kind.sets -> StrengthBody(live, Modifier.weight(1f))
            kind.gps -> {
                Spacer(Modifier.height(12.dp))
                LiveRouteMap(snap, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(14.dp))
                GpsNumbers(snap)
                Spacer(Modifier.height(16.dp))
            }
            else -> {
                Spacer(Modifier.height(28.dp))
                Clock(live, big = true)
                Spacer(Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (live.paused) PhoneTracker.resume(context) else PhoneTracker.pause(context)
                },
                enabled = !saving,
                shape = ButtonShape,
                modifier = Modifier.weight(1f).height(60.dp),
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
                modifier = Modifier.weight(1f).height(60.dp)
            ) {
                Text("Zakończ", fontSize = 18.sp)
            }
        }
    }

    if (asking) {
        val now = System.currentTimeMillis()
        val moving = live.movingMs(now)
        val km = snap.distanceM / 1000.0
        ActionSheet(
            onDismiss = { asking = false },
            icon = kindIcon(kind),
            tone = MaterialTheme.colorScheme.secondary,
            title = "Zakończyć aktywność?",
            text = kind.label,
            primary = SheetAction("Zapisz") {
                asking = false
                saving = true
                scope.launch { onFinished(PhoneTracker.finish(context)) }
            },
            secondary = SheetAction("Wróć") { asking = false },
            extra = SheetAction("Odrzuć bez zapisu", danger = true) {
                asking = false
                discarding = true
            }
        ) {
            // Wynik przed decyzją: z liczbami widać, czy to trening do
            // zapisania, czy pomyłkowy start w szatni.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(clockText(moving), "czas", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                if (kind.sets) {
                    // Trwająca seria liczy się do wyniku - zapis i tak ją zamknie.
                    val all = live.endSet(now).sets
                    StatTile("${all.size}", "serie", null, Modifier.weight(1f))
                    val rests = restsMs(all)
                    StatTile(
                        if (rests.isEmpty()) "-" else clockText(rests.average().toLong()),
                        "śr. przerwa", null, Modifier.weight(1f)
                    )
                }
                if (kind.gps) {
                    StatTile(String.format(Locale.getDefault(), "%.2f", km), "km", null, Modifier.weight(1f))
                    val pace = if (km >= 0.05 && moving > 0) (moving / 60_000.0) / km else null
                    if (kind.maxSpeedMs > 10.0) StatTile(
                        pace?.let { String.format(Locale.getDefault(), "%.1f", 60.0 / it) } ?: "-",
                        "km/h", null, Modifier.weight(1f)
                    ) else StatTile(pace?.let { paceLabel(it) } ?: "-", "/km", null, Modifier.weight(1f))
                }
            }
        }
    }

    if (discarding) {
        ActionSheet(
            onDismiss = { discarding = false },
            icon = Icons.Filled.DeleteOutline,
            tone = MaterialTheme.colorScheme.error,
            title = "Odrzucić aktywność?",
            text = when {
                kind.gps -> "Czas i trasa przepadną. Tego nie da się cofnąć."
                kind.sets -> "Czas i serie przepadną. Tego nie da się cofnąć."
                else -> "Czas przepadnie. Tego nie da się cofnąć."
            },
            primary = SheetAction("Odrzuć", danger = true) {
                discarding = false
                PhoneTracker.discard(context)
                onFinished(null)
            },
            secondary = SheetAction("Wróć") { discarding = false }
        )
    }
}

/**
 * Trening siłowy bez ćwiczeń: licznik serii, czas serii albo odliczanie
 * przerwy, jeden duży przycisk.
 *
 * Nic do wpisywania - po to ten trening istnieje. Kto chce ćwiczeń
 * i ciężarów, ma plan i zegarek. Tu wystarczy dotknąć przy pierwszym
 * powtórzeniu i przy odłożeniu sztangi.
 *
 * Odliczanie przerwy wibruje na końcu, jak w treningu z planu: telefon
 * leży wtedy na ławce ekranem w dół.
 */
@Composable
private fun StrengthBody(live: LiveActivity, modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.paused) {
        while (true) {
            now = System.currentTimeMillis()
            if (live.paused) break
            delay(250)
        }
    }
    val lastEnd = live.sets.lastOrNull()?.endedAt
    val resting = !live.inSet && lastEnd != null && !live.paused
    // Klucz na końcu serii i długości przerwy: zmiana przerwy w trakcie
    // przesuwa wibrację, nowa seria ją odwołuje.
    LaunchedEffect(lastEnd, live.restSec, resting) {
        if (!resting || lastEnd == null) return@LaunchedEffect
        val left = lastEnd + live.restSec * 1000L - System.currentTimeMillis()
        if (left <= 0) return@LaunchedEffect
        delay(left)
        Haptics.restOver(context)
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(14.dp))
        // Licznik serii i czas treningu obok siebie - pod spodem musi się
        // zmieścić lista serii, a bez niej nie było wiadomo, jak szły.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${live.sets.size}",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    plural(live.sets.size, "seria", "serie", "serii").substringAfter(' '),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) { Clock(live, big = false) }
        }
        Spacer(Modifier.height(14.dp))

        // Jedna linia stanu pod licznikiem: czas serii albo odliczanie przerwy.
        // Przed pierwszą serią nic - stojące „01:30 przerwa" wyglądało jak
        // przerwa, która się nie odlicza, i powtarzało ustawienie pod spodem.
        val state = when {
            live.inSet -> Triple(
                "seria ${live.sets.size + 1}",
                clockText(now - live.setStartedAt!!),
                MaterialTheme.colorScheme.primary
            )
            lastEnd == null -> null
            else -> {
                val left = lastEnd + live.restSec * 1000L - now
                if (left > 0) Triple("przerwa", clockText(left + 999), MaterialTheme.colorScheme.tertiary)
                else Triple("przerwa minęła", "+" + clockText(-left), MaterialTheme.colorScheme.secondary)
            }
        }
        state?.let { (label, value, tone) ->
            Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = tone)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Długość przerwy zmienia się tylko między seriami - w trakcie
        // serii palec ma trafić w jeden duży przycisk, nie w minus obok.
        if (!live.inSet) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { PhoneTracker.setRest(context, live.restSec - 15) }, shape = ButtonShape) {
                    Text("-15 s")
                }
                Text(
                    "przerwa " + clockText(live.restSec * 1000L),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                FilledTonalButton(onClick = { PhoneTracker.setRest(context, live.restSec + 15) }, shape = ButtonShape) {
                    Text("+15 s")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SetHistory(live, now, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                Haptics.tick(context)
                if (live.inSet) PhoneTracker.endSet(context) else PhoneTracker.startSet(context)
            },
            enabled = !live.paused,
            shape = ButtonShape,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = if (live.inSet) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(
                when {
                    live.paused -> "Wstrzymany"
                    live.inSet -> "Koniec serii"
                    else -> "Zacznij serię"
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        // Pomyłkowe dotknięcie cofa się od ręki, bez pytania - to jedna seria.
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live.inSet || live.sets.isNotEmpty()) {
                TextButton(onClick = { PhoneTracker.dropSet(context) }) {
                    Text(if (live.inSet) "Anuluj tę serię" else "Usuń ostatnią serię")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Serie tego treningu, od najnowszej: ile trwała każda i ile po niej
 * odpoczywałeś. Sam licznik mówił tylko „5" - nie było wiadomo, czy
 * przerwy rosną, ani czy ostatnia seria była krótsza od pierwszej.
 *
 * Przerwa po ostatniej serii jeszcze trwa, więc liczy się na żywo.
 */
@Composable
private fun SetHistory(live: LiveActivity, now: Long, modifier: Modifier) {
    val sets = live.sets
    Column(modifier) {
        if (sets.isEmpty()) {
            Text(
                "Tu pojawi się każda seria - jak długo trwała i ile po niej odpoczywałeś.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        val context = LocalContext.current
        var removing by remember { mutableStateOf<Int?>(null) }
        val rests = restsMs(sets)
        val longest = sets.maxOf { it.ms }
        Text(
            "Dotknij serii, żeby ją usunąć",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        AppCard(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                items(sets.indices.reversed().toList(), key = { sets[it].startedAt }) { i ->
                    val rest = rests.getOrNull(i)
                        ?: if (!live.inSet && i == sets.lastIndex && !live.paused) now - sets[i].endedAt else null
                    PhoneSetRow(
                        number = i + 1,
                        setMs = sets[i].ms,
                        restMs = rest,
                        restRunning = i == sets.lastIndex && !live.inSet && !live.paused,
                        fraction = sets[i].ms.toFloat() / longest.coerceAtLeast(1),
                        onClick = { removing = i }
                    )
                }
            }
        }

        removing?.let { i ->
            RemoveSetSheet(i + 1, sets.getOrNull(i)?.ms ?: 0, onDismiss = { removing = null }) {
                removing = null
                PhoneTracker.dropSetAt(context, i)
            }
        }
    }
}

/**
 * Pytanie przed usunięciem serii. Jedna seria to mało, ale dotknięcie wiersza
 * w biegu łatwo pomylić z przewinięciem listy - bez pytania znikałaby od
 * samego otarcia palcem.
 */
@Composable
internal fun RemoveSetSheet(number: Int, setMs: Long, onDismiss: () -> Unit, onRemove: () -> Unit) {
    ActionSheet(
        onDismiss = onDismiss,
        icon = Icons.Filled.DeleteOutline,
        tone = MaterialTheme.colorScheme.error,
        title = "Usunąć serię $number?",
        text = "Trwała ${clockText(setMs)}. Przerwy przed nią i po niej połączą się w jedną.",
        primary = SheetAction("Usuń serię", danger = true, onClick = onRemove),
        secondary = SheetAction("Wróć", onClick = onDismiss)
    )
}

/**
 * Wiersz serii - wspólny dla trwającego treningu i szczegółów w historii.
 * Pasek to czas serii względem najdłuższej: krótsza seria pod koniec
 * treningu rzuca się w oczy bez czytania liczb.
 */
@Composable
internal fun PhoneSetRow(
    number: Int,
    setMs: Long,
    restMs: Long?,
    restRunning: Boolean,
    fraction: Float,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(clockText(setMs), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            Box(
                Modifier
                    .padding(top = 4.dp, end = 16.dp)
                    .fillMaxWidth(fraction.coerceIn(0.06f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                restMs?.let { clockText(it) } ?: "-",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = if (restRunning) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (restRunning) "przerwa trwa" else if (restMs != null) "przerwa" else "ostatnia",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Dystans, czas i tempo pod mapą. */
@Composable
private fun GpsNumbers(snap: PhoneTracker.Snapshot) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                String.format(Locale.getDefault(), "%.2f", snap.distanceM / 1000.0),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text("km", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) { Clock(snap.live, big = false) }
    }
    Spacer(Modifier.height(12.dp))
    Speeds(snap)
}

/**
 * Sam zegar tyka co sekundę i przerysowuje tylko siebie - jak pasek przerwy
 * w treningu siłowym. Cały ekran co sekundę to sześćdziesiąt zbędnych
 * przebudów na minutę.
 */
@Composable
private fun Clock(live: LiveActivity, big: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.paused) {
        while (true) {
            now = System.currentTimeMillis()
            if (live.paused) break
            delay(1000)
        }
    }
    Text(
        clockText(live.movingMs(now)),
        fontSize = if (big) 72.sp else 34.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = if (live.paused) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
    )
    Text(
        if (live.paused) "pauza" else "czas",
        style = MaterialTheme.typography.titleMedium,
        color = if (live.paused) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Speeds(snap: PhoneTracker.Snapshot) {
    val moving = snap.live.movingMs(snap.lastFixAt ?: System.currentTimeMillis())
    val km = snap.distanceM / 1000.0
    val avgPace = if (km >= 0.05 && moving > 0) (moving / 60_000.0) / km else null
    Row(Modifier.fillMaxWidth()) {
        if (snap.live.kind.maxSpeedMs > 10.0) {
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
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
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
            if (snap.live.kind.sets) "Dotknij na początku i na końcu każdej serii"
            else "Stoper - telefon mierzy sam czas",
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

internal fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
}
