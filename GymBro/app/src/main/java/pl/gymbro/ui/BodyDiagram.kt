package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import pl.gymbro.data.MeasurementSite

/**
 * Schematyczna sylwetka z zaznaczonym miejscem pomiaru.
 *
 * Rysowana parametrycznie, a nie wczytywana z grafiki, z trzech powodów: skaluje
 * się bez utraty ostrości na każdy ekran, bierze kolory z motywu (więc działa
 * w trybie jasnym i ciemnym), i nie waży nic.
 *
 * Współrzędne są ułamkami wysokości i szerokości płótna, dzięki czemu rysunek
 * zachowuje proporcje niezależnie od rozmiaru.
 */

/** Gdzie na sylwetce biegnie taśma: wysokość oraz zakres poziomy. */
private data class Band(val y: Float, val x1: Float, val x2: Float)

private fun bandFor(site: MeasurementSite): Band = when (site) {
    MeasurementSite.NECK -> Band(0.175f, 0.43f, 0.57f)
    MeasurementSite.CHEST -> Band(0.285f, 0.25f, 0.75f)
    MeasurementSite.WAIST -> Band(0.450f, 0.30f, 0.70f)
    MeasurementSite.HIPS -> Band(0.560f, 0.27f, 0.73f)
    // Ramię i udo mierzy się na kończynie, więc pasek jest krótki i z boku.
    MeasurementSite.ARM -> Band(0.350f, 0.13f, 0.30f)
    MeasurementSite.THIGH -> Band(0.660f, 0.30f, 0.47f)
}

@Composable
fun BodyDiagram(
    site: MeasurementSite,
    modifier: Modifier = Modifier.size(110.dp, 170.dp)
) {
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        drawSilhouette(bodyColor.copy(alpha = 0.35f))
        drawBand(bandFor(site), accent)
    }
}

/**
 * Sylwetka złożona z prostych kształtów zamiast jednej skomplikowanej ścieżki.
 * Kończyny to grube linie z zaokrąglonymi końcami - przy schemacie wyglądają
 * tak samo dobrze jak wyrysowany kontur, a są odporne na błędy w geometrii.
 */
private fun DrawScope.drawSilhouette(color: Color) {
    val w = size.width
    val h = size.height
    fun p(x: Float, y: Float) = Offset(x * w, y * h)

    // Głowa
    drawCircle(color, radius = 0.062f * h, center = p(0.5f, 0.085f))

    // Szyja
    drawLine(
        color, p(0.5f, 0.145f), p(0.5f, 0.215f),
        strokeWidth = 0.075f * w, cap = StrokeCap.Round
    )

    // Tułów: barki → talia → biodra
    val torso = Path().apply {
        moveTo(0.29f * w, 0.215f * h)
        lineTo(0.71f * w, 0.215f * h)
        lineTo(0.675f * w, 0.455f * h)
        lineTo(0.71f * w, 0.575f * h)
        lineTo(0.29f * w, 0.575f * h)
        lineTo(0.325f * w, 0.455f * h)
        close()
    }
    drawPath(torso, color)

    // Ramiona
    val armWidth = 0.085f * w
    drawLine(color, p(0.31f, 0.235f), p(0.20f, 0.56f), strokeWidth = armWidth, cap = StrokeCap.Round)
    drawLine(color, p(0.69f, 0.235f), p(0.80f, 0.56f), strokeWidth = armWidth, cap = StrokeCap.Round)

    // Nogi
    val legWidth = 0.115f * w
    drawLine(color, p(0.40f, 0.575f), p(0.37f, 0.955f), strokeWidth = legWidth, cap = StrokeCap.Round)
    drawLine(color, p(0.60f, 0.575f), p(0.63f, 0.955f), strokeWidth = legWidth, cap = StrokeCap.Round)
}

/** Taśma: pozioma linia z pionowymi znacznikami na końcach. */
private fun DrawScope.drawBand(band: Band, color: Color) {
    val w = size.width
    val h = size.height
    val y = band.y * h
    val x1 = band.x1 * w
    val x2 = band.x2 * w
    val tick = 0.022f * h

    drawLine(
        color, Offset(x1, y), Offset(x2, y),
        strokeWidth = 0.013f * h, cap = StrokeCap.Round
    )
    drawLine(color, Offset(x1, y - tick), Offset(x1, y + tick), strokeWidth = 0.009f * h)
    drawLine(color, Offset(x2, y - tick), Offset(x2, y + tick), strokeWidth = 0.009f * h)

    // Delikatna obwódka wokół taśmy - dodaje głębi i oddziela ją od sylwetki.
    drawCircle(color, radius = 0.016f * h, center = Offset(x1, y), style = Stroke(width = 0.006f * h))
    drawCircle(color, radius = 0.016f * h, center = Offset(x2, y), style = Stroke(width = 0.006f * h))
}
