package pl.gymbro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import pl.gymbro.gym.MuscleMap
import pl.gymbro.gym.MuscleMap.Region

/**
 * Mapa obciążonych partii: sylwetka z przodu i z tyłu.
 *
 * Rysowana parametrycznie, nie z grafiki - tak jak schemat przy obwodach.
 * Skaluje się bez utraty ostrości, bierze kolory z motywu i nic nie waży.
 *
 * Intensywność koloru niesie liczbę serii, a nie tylko fakt pracy. Trzy
 * sztywne poziomy, jak u producenta zegarka, gubią różnicę między partią
 * dobitą a liźniętą jedną serią - a to jest akurat najważniejsze, kiedy
 * patrzysz na tydzień i szukasz, co zaniedbałeś.
 */
@Composable
fun MuscleBody(
    intensities: Map<Region, Float>,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val hot = MaterialTheme.colorScheme.primary

    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(true, false).forEach { front ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(132.dp, 190.dp)) {
                    drawBody(front, intensities, base, hot)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (front) "przód" else "tył",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Skala: od ledwie zaznaczonego do pełnego koloru.
 *
 * Bez niej kolor jest nieczytelny - nie wiadomo, czy jasna klatka znaczy
 * mało pracy, czy po prostu taki odcień.
 */
@Composable
fun MuscleLegend(lowLabel: String, highLabel: String) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val hot = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            lowLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(8.dp)
                .padding()
        ) {
            val steps = 24
            val w = size.width / steps
            repeat(steps) { i ->
                val t = i / (steps - 1f)
                drawRect(
                    color = lerpColor(base, hot, t),
                    topLeft = Offset(i * w, 0f),
                    size = Size(w + 1f, size.height)
                )
            }
        }
        Text(
            highLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * k,
        green = from.green + (to.green - from.green) * k,
        blue = from.blue + (to.blue - from.blue) * k,
        alpha = from.alpha + (to.alpha - from.alpha) * k
    )
}

/**
 * Sylwetka: ciągły kontur pod spodem, mięśnie malowane na nim.
 *
 * Pierwsza wersja składała ciało z osobnych prostokątów i grubych kresek -
 * każdy mięsień był własnym klockiem, więc tam, gdzie klocki się nie stykały,
 * zostawały dziury i postać rozpadała się na części. Teraz najpierw idzie
 * pełny kontur w kolorze tła, a kształty mięśni kładą się na nim. Nieobciążona
 * partia niczego nie wycina: po prostu nie zmienia w tym miejscu koloru.
 *
 * Wszystkie kształty podane są w ułamkach rozmiaru płótna i symetrycznie -
 * [mirror] odbija je względem pionowej osi, więc jedna definicja wystarcza
 * na obie strony ciała.
 */
private fun DrawScope.drawBody(
    front: Boolean,
    intensities: Map<Region, Float>,
    base: Color,
    hot: Color
) {
    val fx: (Float) -> Float = { it * size.width }
    val fy: (Float) -> Float = { it * size.height }
    fun tint(r: Region) = lerpColor(base, hot, intensities[r] ?: 0f)

    // ---- Kontur ----
    // Rysowany raz i w całości, żeby postać trzymała się kupy niezależnie od
    // tego, które partie akurat świecą.
    drawCircle(base, radius = 0.050f * size.height, center = Offset(fx(0.5f), fy(0.068f)))
    drawPath(neck(fx, fy), base)
    drawPath(trunk(fx, fy), base)
    listOf(true, false).forEach { l ->
        drawPath(armOutline(fx, fy, l), base)
        drawPath(legOutline(fx, fy, l), base)
    }

    // ---- Mięśnie ----
    listOf(true, false).forEach { l ->
        if (front) {
            drawPath(deltoid(fx, fy, l), tint(Region.SHOULDERS))
            drawPath(pec(fx, fy, l), tint(Region.CHEST))
            drawPath(upperArm(fx, fy, l), tint(Region.BICEPS))
            drawPath(forearm(fx, fy, l), tint(Region.FOREARMS))
            drawPath(thigh(fx, fy, l), tint(Region.QUADS))
        } else {
            drawPath(deltoid(fx, fy, l), tint(Region.REAR_SHOULDERS))
            drawPath(glute(fx, fy, l), tint(Region.GLUTES))
            drawPath(upperArm(fx, fy, l), tint(Region.TRICEPS))
            drawPath(forearm(fx, fy, l), tint(Region.FOREARMS))
            drawPath(hamstring(fx, fy, l), tint(Region.HAMSTRINGS))
            drawPath(calf(fx, fy, l), tint(Region.CALVES))
        }
    }

    // Brzuch i plecy są pojedyncze, nie parzyste - rysowane po pętli, żeby
    // nie malować ich dwa razy jeden na drugim.
    if (front) drawPath(abs(fx, fy), tint(Region.ABS))
    else drawPath(back(fx, fy), tint(Region.BACK))
}

// ---- Kształty -------------------------------------------------------------

/** Odbicie względem pionowej osi. */
private fun mirror(left: Boolean, v: Float) = if (left) v else 1f - v

private fun path(build: Path.() -> Unit): Path = Path().apply { build(); close() }

/** Szyja - rysowana przed tułowiem, który ją przykrywa u dołu. */
private fun neck(x: (Float) -> Float, y: (Float) -> Float): Path = path {
    moveTo(x(0.456f), y(0.096f))
    lineTo(x(0.544f), y(0.096f))
    lineTo(x(0.552f), y(0.196f))
    lineTo(x(0.448f), y(0.196f))
}

/**
 * Tułów: spad czworobocznego od szyi do barku, talia, biodra.
 *
 * Górna krawędź nie jest prosta, bo ramiona nie wyrastają z poziomej deski -
 * kanciate barki były pierwszym, co psuło całą sylwetkę.
 */
private fun trunk(x: (Float) -> Float, y: (Float) -> Float): Path = path {
    moveTo(x(0.438f), y(0.150f))
    cubicTo(x(0.378f), y(0.164f), x(0.318f), y(0.190f), x(0.300f), y(0.232f))
    cubicTo(x(0.304f), y(0.318f), x(0.348f), y(0.392f), x(0.366f), y(0.470f))
    cubicTo(x(0.370f), y(0.512f), x(0.378f), y(0.534f), x(0.390f), y(0.558f))
    lineTo(x(0.610f), y(0.558f))
    cubicTo(x(0.622f), y(0.534f), x(0.630f), y(0.512f), x(0.634f), y(0.470f))
    cubicTo(x(0.652f), y(0.392f), x(0.696f), y(0.318f), x(0.700f), y(0.232f))
    cubicTo(x(0.682f), y(0.190f), x(0.622f), y(0.164f), x(0.562f), y(0.150f))
}

/** Całe ramię: bark → łokieć → nadgarstek, zwężające się ku dłoni. */
private fun armOutline(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.318f)), y(0.206f))
    cubicTo(
        x(mirror(l, 0.236f)), y(0.236f),
        x(mirror(l, 0.216f)), y(0.330f),
        x(mirror(l, 0.206f)), y(0.392f)
    )
    cubicTo(
        x(mirror(l, 0.198f)), y(0.462f),
        x(mirror(l, 0.190f)), y(0.512f),
        x(mirror(l, 0.188f)), y(0.552f)
    )
    lineTo(x(mirror(l, 0.250f)), y(0.552f))
    cubicTo(
        x(mirror(l, 0.254f)), y(0.508f),
        x(mirror(l, 0.262f)), y(0.456f),
        x(mirror(l, 0.270f)), y(0.392f)
    )
    cubicTo(
        x(mirror(l, 0.280f)), y(0.318f),
        x(mirror(l, 0.306f)), y(0.248f),
        x(mirror(l, 0.360f)), y(0.228f)
    )
}

/** Cała noga: biodro → kolano → kostka, z przewężeniem w kolanie. */
private fun legOutline(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.392f)), y(0.534f))
    cubicTo(
        x(mirror(l, 0.370f)), y(0.622f),
        x(mirror(l, 0.376f)), y(0.700f),
        x(mirror(l, 0.390f)), y(0.754f)
    )
    cubicTo(
        x(mirror(l, 0.374f)), y(0.822f),
        x(mirror(l, 0.382f)), y(0.902f),
        x(mirror(l, 0.394f)), y(0.950f)
    )
    lineTo(x(mirror(l, 0.466f)), y(0.950f))
    cubicTo(
        x(mirror(l, 0.474f)), y(0.902f),
        x(mirror(l, 0.476f)), y(0.822f),
        x(mirror(l, 0.466f)), y(0.754f)
    )
    cubicTo(
        x(mirror(l, 0.482f)), y(0.692f),
        x(mirror(l, 0.488f)), y(0.612f),
        x(mirror(l, 0.486f)), y(0.534f)
    )
}

/** Czepek naramienny - opada na ramię, dlatego nie jest kółkiem obok tułowia. */
private fun deltoid(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.352f)), y(0.198f))
    cubicTo(
        x(mirror(l, 0.278f)), y(0.206f),
        x(mirror(l, 0.234f)), y(0.248f),
        x(mirror(l, 0.226f)), y(0.312f)
    )
    cubicTo(
        x(mirror(l, 0.272f)), y(0.328f),
        x(mirror(l, 0.312f)), y(0.308f),
        x(mirror(l, 0.330f)), y(0.272f)
    )
    cubicTo(
        x(mirror(l, 0.342f)), y(0.244f),
        x(mirror(l, 0.350f)), y(0.220f),
        x(mirror(l, 0.352f)), y(0.198f)
    )
}

/** Połowa klatki: od mostka do pachy, dolna krawędź lekko wklęsła. */
private fun pec(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.494f)), y(0.214f))
    lineTo(x(mirror(l, 0.494f)), y(0.320f))
    cubicTo(
        x(mirror(l, 0.436f)), y(0.330f),
        x(mirror(l, 0.380f)), y(0.312f),
        x(mirror(l, 0.348f)), y(0.282f)
    )
    cubicTo(
        x(mirror(l, 0.340f)), y(0.242f),
        x(mirror(l, 0.356f)), y(0.214f),
        x(mirror(l, 0.388f)), y(0.202f)
    )
    cubicTo(
        x(mirror(l, 0.426f)), y(0.196f),
        x(mirror(l, 0.464f)), y(0.202f),
        x(mirror(l, 0.494f)), y(0.214f)
    )
}

/** Brzuch: wąski pas między żebrami a miednicą, zwężony ku dołowi. */
private fun abs(x: (Float) -> Float, y: (Float) -> Float): Path = path {
    moveTo(x(0.434f), y(0.332f))
    lineTo(x(0.566f), y(0.332f))
    cubicTo(x(0.564f), y(0.420f), x(0.552f), y(0.470f), x(0.500f), y(0.496f))
    cubicTo(x(0.448f), y(0.470f), x(0.436f), y(0.420f), x(0.434f), y(0.332f))
}

/** Plecy: najszersze pod pachami, zwężone nad miednicą - to czyta się jako plecy. */
private fun back(x: (Float) -> Float, y: (Float) -> Float): Path = path {
    moveTo(x(0.330f), y(0.214f))
    cubicTo(x(0.314f), y(0.300f), x(0.352f), y(0.382f), x(0.398f), y(0.458f))
    lineTo(x(0.602f), y(0.458f))
    cubicTo(x(0.648f), y(0.382f), x(0.686f), y(0.300f), x(0.670f), y(0.214f))
    cubicTo(x(0.600f), y(0.246f), x(0.400f), y(0.246f), x(0.330f), y(0.214f))
}

/** Ramię między barkiem a łokciem. */
private fun upperArm(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.252f)), y(0.284f))
    cubicTo(
        x(mirror(l, 0.224f)), y(0.314f),
        x(mirror(l, 0.216f)), y(0.352f),
        x(mirror(l, 0.212f)), y(0.388f)
    )
    lineTo(x(mirror(l, 0.270f)), y(0.388f))
    cubicTo(
        x(mirror(l, 0.274f)), y(0.352f),
        x(mirror(l, 0.284f)), y(0.314f),
        x(mirror(l, 0.300f)), y(0.288f)
    )
}

/** Przedramię: łokieć → nadgarstek. */
private fun forearm(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.210f)), y(0.404f))
    cubicTo(
        x(mirror(l, 0.200f)), y(0.454f),
        x(mirror(l, 0.192f)), y(0.506f),
        x(mirror(l, 0.190f)), y(0.544f)
    )
    lineTo(x(mirror(l, 0.248f)), y(0.544f))
    cubicTo(
        x(mirror(l, 0.252f)), y(0.504f),
        x(mirror(l, 0.260f)), y(0.452f),
        x(mirror(l, 0.268f)), y(0.404f)
    )
}

/** Czworogłowe: kropla najszersza u góry, zwężona nad kolanem. */
private fun thigh(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.398f)), y(0.560f))
    cubicTo(
        x(mirror(l, 0.378f)), y(0.622f),
        x(mirror(l, 0.384f)), y(0.690f),
        x(mirror(l, 0.398f)), y(0.738f)
    )
    lineTo(x(mirror(l, 0.460f)), y(0.738f))
    cubicTo(
        x(mirror(l, 0.472f)), y(0.688f),
        x(mirror(l, 0.478f)), y(0.620f),
        x(mirror(l, 0.476f)), y(0.560f)
    )
}

/** Dwugłowe uda - zaczynają się niżej niż czworogłowe, pod pośladkiem. */
private fun hamstring(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.390f)), y(0.608f))
    cubicTo(
        x(mirror(l, 0.378f)), y(0.656f),
        x(mirror(l, 0.382f)), y(0.702f),
        x(mirror(l, 0.396f)), y(0.740f)
    )
    lineTo(x(mirror(l, 0.462f)), y(0.740f))
    cubicTo(
        x(mirror(l, 0.474f)), y(0.702f),
        x(mirror(l, 0.480f)), y(0.656f),
        x(mirror(l, 0.478f)), y(0.608f)
    )
}

/** Pośladek: półkula na szczycie uda. */
private fun glute(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.492f)), y(0.506f))
    lineTo(x(mirror(l, 0.390f)), y(0.516f))
    cubicTo(
        x(mirror(l, 0.368f)), y(0.552f),
        x(mirror(l, 0.374f)), y(0.592f),
        x(mirror(l, 0.402f)), y(0.602f)
    )
    cubicTo(
        x(mirror(l, 0.452f)), y(0.608f),
        x(mirror(l, 0.488f)), y(0.586f),
        x(mirror(l, 0.492f)), y(0.542f)
    )
}

/** Łydka: brzusiec wysoko, ścięgno nisko. */
private fun calf(x: (Float) -> Float, y: (Float) -> Float, l: Boolean): Path = path {
    moveTo(x(mirror(l, 0.396f)), y(0.772f))
    cubicTo(
        x(mirror(l, 0.380f)), y(0.816f),
        x(mirror(l, 0.388f)), y(0.868f),
        x(mirror(l, 0.400f)), y(0.898f)
    )
    lineTo(x(mirror(l, 0.456f)), y(0.898f))
    cubicTo(
        x(mirror(l, 0.468f)), y(0.864f),
        x(mirror(l, 0.474f)), y(0.814f),
        x(mirror(l, 0.462f)), y(0.772f)
    )
}
