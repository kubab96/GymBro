package pl.gymbro.ui

import android.annotation.SuppressLint
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.PhoneTracker

/**
 * Ekran przed startem aktywności: wybór już padł, czas jeszcze nie leci.
 *
 * Przy biegu i spacerze GPS łapie tu sygnał, zanim cokolwiek się liczy -
 * nasłuch żyje tylko tak długo, jak ten ekran, i po „Start" przejmuje go
 * usługa. Startu nie blokujemy brakiem sygnału: w bramie albo w lesie
 * czekanie mogłoby trwać minutami, a dystans i tak zacznie się liczyć
 * od pierwszego dobrego odczytu.
 */
@Composable
internal fun ActivityPrepScreen(kind: PhoneActivity, onBack: () -> Unit, onStart: () -> Unit) {
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    var accuracy by remember { mutableStateOf<Float?>(null) }

    if (kind.gps) {
        DisposableEffect(Unit) {
            val lm = context.getSystemService(LocationManager::class.java)
            val l = LocationListener { loc -> if (loc.hasAccuracy()) accuracy = loc.accuracy }
            @SuppressLint("MissingPermission") // ekran wyboru pyta o zgodę przed wejściem tutaj
            fun listen() {
                if (PhoneTracker.hasLocationPermission(context)) {
                    runCatching {
                        lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, l, Looper.getMainLooper())
                    }
                }
            }
            listen()
            onDispose { lm?.removeUpdates(l) }
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
            Text(kind.label, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    !kind.gps -> "Stoper - telefon mierzy sam czas"
                    acc == null -> "Szukam sygnału GPS…"
                    acc > TrackMath.MAX_ACCURACY_M ->
                        "Słaby sygnał GPS" + (acc.takeIf { it < 1000 }?.let { " (±${it.toInt()} m)" } ?: "")
                    else -> "GPS gotowy · ±${acc.toInt()} m"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (ready) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (kind.gps)
                    "Na otwartej przestrzeni sygnał łapie się w kilkanaście sekund. " +
                        "Możesz zacząć i bez niego - dystans ruszy od pierwszego dobrego " +
                        "odczytu. Telefon schowaj do kieszeni, trasa nagrywa się przy " +
                        "wygaszonym ekranie."
                else "Czas ruszy po naciśnięciu Start. Pauza i zakończenie są na następnym ekranie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
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
