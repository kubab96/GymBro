package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.SetEntry
import pl.gymbro.gym.Loading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Szczegóły jednego ćwiczenia z trwającego treningu - wejście z podsumowania.
 *
 * Podsumowanie mówi, ile zrobiłeś w sumie; tutaj widać, jak to szło: każda
 * seria z godziną i odstępem od poprzedniej, najlepsza seria, szacowany
 * maksymalny ciężar i porównanie z poprzednim razem. Przycisk Edytuj
 * otwiera serie do poprawki i pozwala dołożyć brakującą - po to, żeby przed
 * zakończeniem dało się jeszcze naprawić to, co poszło do dziennika źle.
 * Bez edycji wiersze nie reagują na dotyk: przeglądając nie da się
 * przypadkiem otworzyć poprawki.
 */
@Composable
internal fun ColumnScope.ExerciseReview(
    state: ExerciseState,
    inventory: Inventory,
    onBack: () -> Unit,
    onAddSet: (Double, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit
) {
    val item = state.item
    ExerciseReview(
        ReviewModel(
            name = item.exerciseName,
            subtitle = "${state.doneSets.count { !it.isWarmup }} z ${item.sets} serii" +
                (if (item.hasRepRange) " · zakres ${item.repRange}" else ""),
            working = state.doneSets.filter { !it.isWarmup },
            before = state.lastTime.filter { !it.isWarmup },
            beforeAt = null,
            withWeight = item.hasTargetWeight,
            recordNote = if (isRecord(state)) "rekord - najcięższa seria w historii tego ćwiczenia" else null,
            equipment = item.equipment,
            step = item.step,
            nextWeight = state.nextWeight,
            nextReps = state.nextReps
        ),
        inventory, onBack, onAddSet, onDeleteSet, onUpdateSet
    )
}

/**
 * Wszystko, czego ekran ćwiczenia potrzebuje - bez pozycji planu.
 *
 * Ten sam ekran otwiera się z podsumowania trwającego treningu i ze
 * szczegółów treningu w historii. Tam pozycji planu może już nie być
 * (ćwiczenie spoza planu, plan zmieniony po treningu), więc każde wejście
 * składa ten model ze swojego źródła. Dwa osobne ekrany rozjechały się
 * już raz: historia miała gołą listę serii w okienku, podsumowanie tabelę
 * z godziną i porównaniem.
 *
 * @param beforeAt kiedy był poprzedni raz; null - bez daty w nagłówku porównania
 * @param recordNote napis pod kafelkami, gdy padł rekord; null - bez rekordu
 */
internal class ReviewModel(
    val name: String,
    val subtitle: String,
    val working: List<SetEntry>,
    val before: List<SetEntry>,
    val beforeAt: Long?,
    val withWeight: Boolean,
    val recordNote: String?,
    val equipment: Equipment,
    val step: Double,
    val nextWeight: Double,
    val nextReps: Int
)

@Composable
internal fun ColumnScope.ExerciseReview(
    model: ReviewModel,
    inventory: Inventory,
    onBack: () -> Unit,
    onAddSet: (Double, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit
) {
    val working = model.working
    val before = model.before
    val withWeight = model.withWeight
    val record = model.recordNote != null
    var editing by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetEntry?>(null) }

    // ---- Nagłówek ze strzałką i przyciskiem edycji ----
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                model.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = { editing = !editing },
            shape = RoundedCornerShape(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            if (!editing) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(if (editing) "Gotowe" else "Edytuj")
        }
    }

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        val best = working.maxWithOrNull(compareBy<SetEntry>({ it.weightKg }, { it.reps }))
        val e1rm = working.maxOfOrNull { it.estimated1RM }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                if (withWeight) kg(working.sumOf { it.tonnage }) else "${working.sumOf { it.reps }}",
                if (withWeight) "kg objętości" else "powtórzeń",
                null, Modifier.weight(1f)
            )
            StatTile(
                best?.let { setText(it, withWeight) } ?: "-",
                "najlepsza seria",
                if (record) MaterialTheme.colorScheme.tertiary else null,
                Modifier.weight(1f)
            )
        }
        if (withWeight) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(e1rm?.let { Loading.fmt(Math.round(it).toDouble()) } ?: "-", "szac. maks. (1RM)", null, Modifier.weight(1f))
                StatTile("${working.sumOf { it.reps }}", "powtórzeń", null, Modifier.weight(1f))
            }
        }
        model.recordNote?.let {
            Spacer(Modifier.height(8.dp))
            TagPill(it, MaterialTheme.colorScheme.tertiary)
        }

        // ---- Serie ----
        GroupLabel("Serie")
        AppCard {
            SetTableHeader(withWeight)
            if (working.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Żadnej serii - ćwiczenie pominięte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            working.forEachIndexed { i, s ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Odstęp od końca poprzedniej serii: przerwa razem z samą
                // serią, bo zapisujemy tylko chwilę zakończenia.
                val gap = working.getOrNull(i - 1)?.let { ((s.doneAt - it.doneAt) / 1000).toInt() }
                SetTableRow(i + 1, s, gap, withWeight, onClick = if (editing) ({ editingSet = s }) else null)
            }
        }
        if (editing) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    // Dokładka powtarza ostatnią serię - najczęściej brakuje
                    // takiej samej, a poprawić ją można od razu w arkuszu.
                    val last = working.lastOrNull()
                    onAddSet(last?.weightKg ?: model.nextWeight, last?.reps ?: model.nextReps)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dodaj serię")
            }
            Text(
                "Dotknij serii, żeby ją poprawić albo usunąć.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp)
            )
        }

        // ---- Porównanie z poprzednim razem ----
        if (before.isNotEmpty() && working.isNotEmpty()) {
            GroupLabel(
                "Względem poprzedniego razu" +
                    (model.beforeAt?.let { " · " + SimpleDateFormat("d.MM", Locale.getDefault()).format(Date(it)) } ?: "")
            )
            AppCard {
                if (withWeight) {
                    CompareRow(
                        "objętość",
                        kg(before.sumOf { it.tonnage }) + " kg",
                        kg(working.sumOf { it.tonnage }) + " kg",
                        working.sumOf { it.tonnage } - before.sumOf { it.tonnage },
                        first = true
                    )
                    val bBest = before.maxWithOrNull(compareBy<SetEntry>({ it.weightKg }, { it.reps }))
                    CompareRow(
                        "najlepsza seria",
                        bBest?.let { setText(it, true) } ?: "-",
                        best?.let { setText(it, true) } ?: "-",
                        (best?.estimated1RM ?: 0.0) - (bBest?.estimated1RM ?: 0.0)
                    )
                } else {
                    CompareRow(
                        "powtórzenia",
                        "${before.sumOf { it.reps }}",
                        "${working.sumOf { it.reps }}",
                        (working.sumOf { it.reps } - before.sumOf { it.reps }).toDouble(),
                        first = true
                    )
                }
                CompareRow(
                    "serie",
                    "${before.size}",
                    "${working.size}",
                    (working.size - before.size).toDouble()
                )
            }
            Text(
                "Poprzednio: " + before.joinToString("   ") { setText(it, withWeight) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    editingSet?.let { s ->
        EditSetDialog(
            entry = s,
            exerciseName = model.name,
            equipment = model.equipment,
            step = model.step,
            showWeight = withWeight,
            inventory = inventory,
            onDismiss = { editingSet = null },
            onSave = { w, r, v ->
                onUpdateSet(s.id, w, r, v)
                editingSet = null
            },
            onDelete = {
                onDeleteSet(s.id)
                editingSet = null
            }
        )
    }
}

private fun setText(s: SetEntry, withWeight: Boolean): String =
    if (withWeight) "${Loading.fmt(s.weightKg)} × ${s.reps}" else "${s.reps} powt."

private val COLS = listOf(0.6f, 1.4f, 1f, 1f, 1.2f, 1.3f)

@Composable
private fun SetTableHeader(withWeight: Boolean) {
    val labels = listOf("#", if (withWeight) "KG" else "", "POWT.", "ZAPAS", "GODZ.", "ODSTĘP")
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        labels.forEachIndexed { i, t ->
            Text(
                t,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(COLS[i])
            )
        }
    }
}

@Composable
private fun SetTableRow(nr: Int, s: SetEntry, gapSec: Int?, withWeight: Boolean, onClick: (() -> Unit)?) {
    val cells = listOf(
        "$nr",
        if (withWeight) Loading.fmt(s.weightKg) else "",
        "${s.reps}",
        s.rir?.let { rirLabel(it) } ?: "-",
        setTime(s.doneAt),
        gapSec?.let { "%d:%02d".format(it / 60, it % 60) } ?: "-"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tap(RoundedCornerShape(0.dp), onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { i, t ->
            Text(
                t,
                style = if (i in 1..2) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (i in 1..2) FontWeight.Bold else FontWeight.Normal,
                color = when (i) {
                    0 -> MaterialTheme.colorScheme.secondary
                    in 1..2 -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(COLS[i])
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.Edit, contentDescription = "Popraw",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CompareRow(label: String, was: String, now: String, delta: Double, first: Boolean = false) {
    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    val tone: Color = when {
        delta > 0.001 -> MaterialTheme.colorScheme.secondary
        delta < -0.001 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(was, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  →  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(now, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = tone)
    }
}
