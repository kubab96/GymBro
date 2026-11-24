package pl.gymbro.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.SetEntry
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator

/**
 * Poprawianie zapisanej serii - arkusz od dołu.
 *
 * Arkusz zamiast okna na środku: przyciski są pod kciukiem, a regulatory te
 * same co przy zapisie serii, żeby nie uczyć się drugiego sposobu wpisywania
 * tego samego. Kasowanie zostaje tutaj, w jednym miejscu z poprawianiem -
 * ale jako osobny przycisk, a nie skutek dotknięcia kafelka.
 *
 * Arkusz jest jeden dla trwającego treningu i dla historii. Dlatego bierze
 * nazwę, sprzęt i krok wprost, a nie pozycję planu: trening sprzed miesiąca
 * mógł iść według planu, którego już nie ma, a serię dalej trzeba umieć
 * poprawić.
 *
 * @param showWeight czy pokazać regulator ciężaru - przy ćwiczeniu z masą
 *   ciała, zapisanym z zerem, byłby liczbą, która nic nie znaczy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSetDialog(
    entry: SetEntry,
    exerciseName: String,
    equipment: Equipment,
    step: Double,
    showWeight: Boolean,
    inventory: Inventory,
    onDismiss: () -> Unit,
    onSave: (Double, Int, Int?) -> Unit,
    onDelete: () -> Unit
) {
    val mode = LoadMode.of(equipment, inventory)
    var weight by remember(entry.id) { mutableStateOf(entry.weightKg) }
    var reps by remember(entry.id) { mutableStateOf(entry.reps) }
    // Zapas wchodzi do poprawki z tym, co zapisano - inaczej arkusz milczał
    // o jedynej wartości, której po treningu nie da się odtworzyć z pamięci.
    var rir by remember(entry.id) { mutableStateOf(entry.rir) }
    var typing by remember { mutableStateOf<String?>(null) }
    when (typing) {
        "w" -> NumberEntrySheet(
            title = exerciseName + " - ciężar",
            initial = weight,
            kind = NumberKind.WEIGHT,
            unit = equipment.weightUnitLabel,
            values = weightValues(mode, inventory, step),
            onDismiss = { typing = null },
            onPick = { weight = it; typing = null }
        )
        "r" -> NumberEntrySheet(
            title = exerciseName + " - powtórzenia",
            initial = reps.toDouble(),
            kind = NumberKind.COUNT,
            max = 100.0,
            onDismiss = { typing = null },
            onPick = { reps = it.toInt(); typing = null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Od razu w całości: arkusz do połowy chował przycisk Zapisz pod
        // krawędzią ekranu, dokładnie tam, gdzie go szukasz.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                "Seria ${entry.setIndex} · $exerciseName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Text(
                "zapisana ${setTime(entry.doneAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (showWeight) {
                BigStepper(
                    caption = "CIĘŻAR · ${equipment.weightUnitLabel.uppercase()}",
                    value = Loading.fmt(weight),
                    onMinus = {
                        weight = if (mode != null) {
                            PlateCalculator.nextDown(weight, mode, inventory)?.totalKg ?: weight
                        } else roundKg((weight - step).coerceAtLeast(0.0))
                    },
                    onPlus = {
                        weight = if (mode != null) {
                            PlateCalculator.nextUp(weight, mode, inventory)?.totalKg ?: weight
                        } else roundKg(weight + step)
                    },
                    onValueClick = { typing = "w" }
                )
                Spacer(Modifier.height(10.dp))
            }
            BigStepper(
                caption = "POWTÓRZENIA",
                value = "$reps",
                onMinus = { if (reps > 1) reps-- },
                onPlus = { reps++ },
                onValueClick = { typing = "r" }
            )

            // Zapas widoczny od razu: przy poprawce przede wszystkim sprawdza
            // się, co się wtedy podało.
            GroupLabel(if (rir != null) "Zapas powtórzeń" else "Zapas powtórzeń - nie podano")
            RirPicker(rir) { rir = it }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(weight, reps, rir) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Zapisz", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Usuń tę serię", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
