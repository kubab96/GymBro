package pl.gymbro.ui

import android.content.Intent
import android.os.Looper
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.LocationFeed

/**
 * Ekran przed startem aktywności: wybór już padł, czas jeszcze nie leci.
 *
 * Przy aktywności z trasą lokalizacja łapie się tu, zanim cokolwiek się
 * liczy - nasłuch żyje tylko tak długo, jak ten ekran, i po „Start" przejmuje
 * go usługa. Startu nie blokujemy brakiem sygnału: w bramie albo w lesie
 * czekanie mogłoby trwać minutami, a dystans i tak zacznie się liczyć od
 * pierwszego dobrego odczytu.
 *
 * Do samego napisu „Szukam sygnału" dochodzą kreski sygnału i liczba
 * widzianych satelitów. Bez nich nie dało się odróżnić czekania, które zaraz
 * się skończy, od czekania w budynku, które nie skończy się nigdy.
 */
@Composable
internal fun ActivityPrepScreen(kind: PhoneActivity, onBack: () -> Unit, onStart: () -> Unit) {
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    var accuracy by remember { mutableStateOf<Float?>(null) }
    var seen by remember { mutableIntStateOf(0) }
    var used by remember { mutableIntStateOf(0) }
    // Przełącznik lokalizacji w szufladzie - sprawdzany co chwilę, bo
    // włącza się go poza aplikacją i nic nas o tym nie zawiadamia.
    var enabled by remember { mutableStateOf(LocationFeed.enabled(context)) }

    if (kind.gps) {
        LaunchedEffect(Unit) {
            while (true) {
                enabled = LocationFeed.enabled(context)
                delay(1500)
            }
        }
        DisposableEffect(enabled) {
            if (!enabled) return@DisposableEffect onDispose { }
            val stopFix = LocationFeed.listen(context, Looper.getMainLooper()) { loc ->
                if (loc.hasAccuracy()) accuracy = loc.accuracy
            }
            val stopSat = LocationFeed.satellites(context) { s, u -> seen = s; used = u }
            onDispose {
                stopFix()
                stopSat()
            }
        }
    }

    val acc = accuracy
    val ready = !kind.gps || (acc != null && acc <= TrackMath.MAX_ACCURACY_M)

    Column(Modifier.fillMaxSize().padding(bottom = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(112.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    kindIcon(kind), contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(kind.label, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(28.dp))

        if (kind.gps) {
            if (!enabled) LocationOffCard()
            else GpsCard(acc, seen, used, ready)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = pl.gymbro.ui.theme.ButtonShape
        ) {
            Text("Start", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GpsCard(acc: Float?, seen: Int, used: Int, ready: Boolean) {
    val bars = when {
        acc == null -> 0
        acc <= 8f -> 4
        acc <= TrackMath.MAX_ACCURACY_M -> 3
        acc <= 60f -> 2
        else -> 1
    }
    val tone = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    AppCard {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(bars, tone)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        acc == null -> "Szukam sygnału GPS…"
                        ready -> "GPS gotowy"
                        else -> "Słaby sygnał GPS"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = tone
                )
                Text(
                    listOfNotNull(
                        acc?.takeIf { it < 1000 }?.let { "dokładność ±${it.toInt()} m" },
                        when {
                            seen == 0 -> null
                            used > 0 -> "satelity: $used z $seen"
                            else -> "widzę satelity: $seen"
                        }
                    ).joinToString(" · ").ifEmpty {
                        "Pod gołym niebem to kilkanaście sekund"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Kreski jak w pasku stanu telefonu - czyta się je bez czytania liczby.
 * Pełne cztery to dokładność, przy której trasa nie zygzakuje.
 */
@Composable
private fun SignalBars(level: Int, tone: Color) {
    Row(
        Modifier.height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (1..4).forEach { i ->
            Box(
                Modifier
                    .width(6.dp)
                    .height((6 + i * 5).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) tone else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun LocationOffCard() {
    val context = LocalContext.current
    AppCard(accent = MaterialTheme.colorScheme.tertiary) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(10.dp))
                Text("Lokalizacja jest wyłączona", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Włącz lokalizację") }
        }
    }
}
