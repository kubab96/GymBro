package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wykres masy ciała: surowe pomiary jako punkty, trend jako linia.
 *
 * Rysowany ręcznie na Canvas, bez biblioteki wykresów - jedna zależność mniej
 * i pełna kontrola nad tym, co pokazujemy.
 *
 * Najważniejsza decyzja projektowa jest tutaj taka, że **linia trendu jest
 * grubsza i w kolorze akcentu, a surowe pomiary to blade kropki**. Dzienna masa
 * ciała waha się o ±1,5 kg z powodu nawodnienia i treści przewodu pokarmowego -
 * patrzenie na surowe punkty prowadzi do błędnych wniosków i niepotrzebnych
 * nerwów. Liczy się kierunek, nie pojedynczy odczyt, więc to kierunek ma być
 * tym, co widać najpierw.
 */
@Composable
fun WeightChart(
    raw: List<Pair<Long, Double>>,
    trend: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier
) {
    if (raw.size < 2) {
        Box(
            modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Za mało pomiarów na wykres.\nPotrzebne co najmniej dwa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val allValues = raw.map { it.second } + trend.map { it.second }
    val minV = allValues.min()
    val maxV = allValues.max()
    // Zapas, żeby skrajne punkty nie dotykały krawędzi. Przy płaskim wykresie
    // (wszystkie pomiary identyczne) zakres byłby zerowy i dzielilibyśmy przez zero.
    val span = (maxV - minV).takeIf { it > 0.01 } ?: 1.0
    val lo = minV - span * 0.15
    val hi = maxV + span * 0.15

    val minT = raw.first().first
    val maxT = raw.last().first
    val tSpan = (maxT - minT).takeIf { it > 0 } ?: 1L

    val pointColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trendColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("%.1f kg".format(hi), style = MaterialTheme.typography.labelSmall)
            Text("trend", style = MaterialTheme.typography.labelSmall, color = trendColor)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(vertical = 4.dp)
        ) {
            val w = size.width
            val h = size.height

            fun x(t: Long) = ((t - minT).toDouble() / tSpan * w).toFloat()
            fun y(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()

            // Siatka pozioma - trzy linie odniesienia wystarczą, więcej zaśmieca.
            for (i in 0..2) {
                val yy = h * i / 2f
                drawLine(gridColor, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
            }

            // Surowe pomiary - blade, drugoplanowe.
            raw.forEach { (t, v) ->
                drawCircle(pointColor, radius = 3f, center = Offset(x(t), y(v)), alpha = 0.45f)
            }

            // Trend - to jest właściwa informacja, więc wyraźny.
            if (trend.size >= 2) {
                val path = Path()
                trend.forEachIndexed { i, (t, v) ->
                    if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
                }
                drawPath(path, trendColor, style = Stroke(width = 4f))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("%.1f kg".format(lo), style = MaterialTheme.typography.labelSmall)
            Text(
                "${shortDate(minT)} - ${shortDate(maxT)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun shortDate(ms: Long): String =
    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(ms))
