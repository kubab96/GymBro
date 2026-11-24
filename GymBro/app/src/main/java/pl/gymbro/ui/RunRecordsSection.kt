package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.gym.RunRecords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rekordy biegowe i rowerowe w Progresie, pod siłą.
 *
 * Bez osobnej podzakładki: kto nie biega, nie widzi tu nic, bo sekcja
 * znika bez danych. Kto biega, dostaje jedną kartę zamiast nowego ekranu.
 */
@Composable
internal fun RunRecordsSection(records: RunRecords.Records, weekly: List<Pair<Long, Double>>) {
    Column {
        Spacer(Modifier.height(22.dp))
        Text("Bieganie i rower", style = MaterialTheme.typography.titleMedium)
        Text(
            "Najlepszy czas z dowolnego fragmentu biegu nagranego telefonem - " +
                "z zegarka przychodzi tylko całość, bez trasy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        AppCard {
            Column(Modifier.padding(vertical = 4.dp)) {
                records.distances.forEach { r ->
                    RecordRow(
                        if (r.meters <= 1000.0) Icons.Filled.Bolt else Icons.Filled.Flag,
                        r.label,
                        dayLabel(r.best.at) + if (r.latest != r.best) " · ostatnio " + clockText(r.latest.ms) else "",
                        clockText(r.best.ms)
                    )
                }
                records.longestRun?.let { s ->
                    RecordRow(Icons.Filled.Route, "Najdłuższy bieg", dayLabel(s.startedAt), kmLabel(s.distanceM!!))
                }
                records.longestRide?.let { s ->
                    RecordRow(
                        Icons.AutoMirrored.Filled.DirectionsBike, "Najdłuższa jazda",
                        dayLabel(s.startedAt), kmLabel(s.distanceM!!)
                    )
                }
                records.fastestRide?.let { (s, kmh) ->
                    RecordRow(
                        Icons.Filled.Speed, "Najszybsza jazda", dayLabel(s.startedAt) + " · średnio",
                        String.format(Locale.getDefault(), "%.1f km/h", kmh)
                    )
                }
            }
        }

        if (weekly.any { it.second > 0 }) {
            Spacer(Modifier.height(12.dp))
            WeeklyKm(weekly)
        }
    }
}

@Composable
private fun RecordRow(icon: ImageVector, title: String, sub: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, size = 36)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

/** Kilometry biegu w ostatnich tygodniach - słupki od zera, bo tu liczy się suma. */
@Composable
private fun WeeklyKm(weekly: List<Pair<Long, Double>>) {
    val bar = MaterialTheme.colorScheme.secondary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = weekly.maxOf { it.second }.coerceAtLeast(1.0)
    AppCard {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Kilometry biegu w tygodniu", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "ten tydzień: " + String.format(Locale.getDefault(), "%.1f km", weekly.last().second),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val n = weekly.size
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                weekly.forEachIndexed { i, (_, km) ->
                    // Tydzień bez biegu dostaje kreskę, nie dziurę - inaczej
                    // nie widać, ile tygodni obejmuje wykres.
                    val h = if (km > 0) (km / max * size.height).toFloat().coerceAtLeast(4f) else 3f
                    drawRoundRect(
                        color = if (km > 0) bar else empty,
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dayLabel(weekly.first().first), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("teraz", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dayLabel(ms: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))

private fun kmLabel(m: Double): String = String.format(Locale.getDefault(), "%.1f km", m / 1000.0)
