package pl.gymbro.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import pl.gymbro.data.Inventory
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Co się wybiera - od tego zależy skala i to, jak pisać liczby. */
internal enum class NumberKind {
    /** Liczba całkowita: serie, powtórzenia. */
    COUNT,
    /** Ciężar - skala z [values], zwykle tylko to, co da się złożyć. */
    WEIGHT,
    /** Czas przerwy co 5 sekund. */
    TIME
}

/** Ile stopni łuku na jeden krok skali. Dwadzieścia kroków na pół koła. */
private const val STEP_DEG = 9f

/** Co który krok podpis liczbą - podpisy muszą mieć między sobą miejsce. */
private const val LABEL_EVERY = 4

/**
 * Wybór liczby pokrętłem - półkole ze skalą, którą przesuwa się palcem.
 *
 * Jak koronka zegarka: skala obraca się za palcem, liczba pod znacznikiem
 * na górze jest wyborem, a puszczona rozpędzona skala dojeżdża sama
 * i staje na najbliższej kresce. Każda kreska to jedno tyknięcie w dłoni.
 * Klawiatura z cyframi wymagała patrzenia na każdy klawisz - pokrętło
 * da się kręcić kciukiem między seriami, a plus i minus po bokach zostają
 * na pojedynczy krok.
 *
 * Skala ciężaru to tylko to, co da się złożyć z talerzy ([weightValues]) -
 * ciężar, którego nie da się nałożyć, nie jest ciężarem.
 *
 * @param values skala; bez niej - liczby od 1 do [max] albo czas co 5 s
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NumberEntrySheet(
    title: String,
    initial: Double,
    kind: NumberKind,
    unit: String = "",
    max: Double = 100.0,
    values: List<Double>? = null,
    onDismiss: () -> Unit,
    onPick: (Double) -> Unit
) {
    val scale = remember(values, kind, max) {
        values?.takeIf { it.isNotEmpty() } ?: when (kind) {
            NumberKind.TIME -> (0..120).map { it * 5.0 }
            else -> (1..max.toInt().coerceAtLeast(1)).map { it.toDouble() }
        }
    }
    val start = remember(scale) { scale.indices.minByOrNull { abs(scale[it] - initial) } ?: 0 }
    val pos = remember(scale) { Animatable(start.toFloat()) }
    val index by remember(scale) {
        derivedStateOf { pos.value.roundToInt().coerceIn(0, scale.lastIndex) }
    }
    val fmt: (Double) -> String = when (kind) {
        NumberKind.TIME -> { v -> "%d:%02d".format(v.toInt() / 60, v.toInt() % 60) }
        NumberKind.WEIGHT -> { v -> Loading.fmt(v) }
        NumberKind.COUNT -> { v -> v.toInt().toString() }
    }
    val scope = rememberCoroutineScope()
    fun stepBy(d: Int) {
        scope.launch {
            pos.animateTo((index + d).coerceIn(0, scale.lastIndex).toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Ruch po pokrętle nie zamyka arkusza: pokrętło zjada swoje przesunięcia
        // (consume), więc arkusz ich nie dostaje.
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            // Łuk stoi pionowo przy prawej krawędzi, a wybór jest po lewej -
            // kciuk prowadzi skalę góra-dół, jak koronkę zegarka, i nie
            // zasłania liczby. Plus nad liczbą, minus pod nią: w tę samą
            // stronę, w którą rośnie skala.
            Box(Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp)) {
                ArcDial(scale, pos, fmt)
                Column(
                    Modifier.align(Alignment.CenterStart),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StepKey(Icons.Filled.Add, "Więcej", Modifier) { stepBy(1) }
                    Text(
                        fmt(scale[index]),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            unit,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    } else Spacer(Modifier.height(10.dp))
                    StepKey(Icons.Filled.Remove, "Mniej", Modifier) { stepBy(-1) }
                }
            }
            Text(
                "Przesuń palcem w górę lub w dół po łuku",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = {
                    val v = scale[index]
                    onPick(if (kind == NumberKind.WEIGHT) roundKg(v) else v)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Ustaw", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StepKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(52.dp)
            .tap(RoundedCornerShape(16.dp), onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
    }
}

/**
 * Samo półkole, postawione pionowo przy prawej krawędzi: pas skali
 * z kreskami, podpisy po wewnętrznej stronie i znacznik w połowie wysokości.
 *
 * Wyższe liczby są wyżej. Skala idzie za palcem - ruch w dół zsuwa
 * wyższe liczby pod znacznik, jak przewijanie taśmy miarki. Liczy się
 * sam ruch w pionie, przeliczony na obrót łuku: palec nie musi trzymać
 * się pasa, a ruch w bok niczego nie przestawia.
 */
@Composable
private fun ArcDial(scale: List<Double>, pos: Animatable<Float, *>, fmt: (Double) -> String) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val last = scale.lastIndex.toFloat()

    // Tyknięcie w dłoni na każdej kresce, która przejdzie pod znacznikiem.
    LaunchedEffect(pos) {
        var first = true
        snapshotFlow { pos.value.roundToInt() }.distinctUntilChanged().collect {
            if (!first) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            first = false
        }
    }

    val band = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val labelStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(scale) {
                val tracker = VelocityTracker()
                val r = minOf(size.height / 2f - 23.dp.toPx() - 8.dp.toPx(), size.width * 0.40f)
                // Piksele ruchu w pionie na jeden krok skali - długość łuku jednego kroku.
                val pxPerStep = r * STEP_DEG * PI.toFloat() / 180f
                var target = pos.value
                detectDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        target = pos.value
                        scope.launch { pos.stop() }
                    },
                    onDragEnd = {
                        // Prędkość w krokach na sekundę - rozpędzona skala jedzie
                        // dalej sama i hamuje, a na końcu staje na kresce.
                        val v = tracker.calculateVelocity().x / 100f
                        scope.launch {
                            pos.updateBounds(0f, last)
                            pos.animateDecay(v, exponentialDecay(frictionMultiplier = 2.2f))
                            pos.animateTo(pos.value.roundToInt().toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = {
                        scope.launch { pos.animateTo(pos.value.roundToInt().toFloat()) }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        target = (target + drag.y / pxPerStep).coerceIn(0f, last)
                        tracker.addPosition(change.uptimeMillis, Offset(target * 100f, 0f))
                        scope.launch { pos.snapTo(target) }
                    }
                )
            }
    ) {
        val bandW = 46.dp.toPx()
        val r = minOf(size.height / 2f - 23.dp.toPx() - 8.dp.toPx(), size.width * 0.40f)
        // Odsunięty od krawędzi o miejsce na trójkąt znacznika.
        val center = Offset(size.width - r - bandW / 2 - 20.dp.toPx(), size.height / 2f)
        // Pas skali: prawa połowa koła.
        drawArc(
            color = band,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(bandW, cap = StrokeCap.Round)
        )
        val p = pos.value
        val from = (p - 20).toInt().coerceAtLeast(0)
        val to = (p + 20).toInt().coerceAtMost(scale.lastIndex)
        for (i in from..to) {
            // Wyższa liczba wyżej: kąt rośnie w dół, więc kreska i stoi pod
            // kątem (pos - i), a nie (i - pos).
            val deg = (p - i) * STEP_DEG
            if (deg < -88f || deg > 88f) continue
            val rad = deg * PI.toFloat() / 180f
            val major = i % LABEL_EVERY == 0
            // Im dalej od znacznika, tym bledziej - oko trzyma się środka.
            val fade = (1f - abs(deg) / 95f).coerceIn(0.15f, 1f)
            val dir = Offset(cos(rad), sin(rad))
            val outer = r + bandW / 2 - 8.dp.toPx()
            val len = if (major) 16.dp.toPx() else 9.dp.toPx()
            drawLine(
                tick.copy(alpha = fade),
                center + dir * outer,
                center + dir * (outer - len),
                strokeWidth = if (major) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            if (major) {
                val text = measurer.measure(fmt(scale[i]), labelStyle)
                val at = center + dir * (r - bandW / 2 - 6.dp.toPx() - text.size.width / 2f)
                drawText(
                    text,
                    topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f),
                    alpha = fade
                )
            }
        }
        // Znacznik wyboru: kreska w poprzek pasa i trójkąt na zewnątrz.
        val edge = center.x + r
        val tri = Path().apply {
            moveTo(edge + bandW / 2 + 8.dp.toPx(), center.y - 9.dp.toPx())
            lineTo(edge + bandW / 2 + 8.dp.toPx(), center.y + 9.dp.toPx())
            lineTo(edge + bandW / 2 - 4.dp.toPx(), center.y)
            close()
        }
        drawPath(tri, accent)
        drawLine(
            accent,
            Offset(edge - bandW / 2 + 4.dp.toPx(), center.y),
            Offset(edge + bandW / 2 - 4.dp.toPx(), center.y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Skala ciężaru dla pokrętła: przy talerzach wszystko, co da się złożyć,
 * a bez nich - równe kroki ćwiczenia, do 300 kg.
 */
internal fun weightValues(mode: LoadMode?, inventory: Inventory, step: Double): List<Double> {
    if (mode != null) {
        val all = PlateCalculator.achievable(mode, inventory).map { it.totalKg }.filter { it <= 400.0 }
        if (all.isNotEmpty()) return all
    }
    val s = if (step > 0.0) step else 2.5
    return (0..(300.0 / s).toInt()).map { roundKg(it * s) }
}
