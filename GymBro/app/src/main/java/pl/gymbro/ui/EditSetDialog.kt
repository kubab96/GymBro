package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Equipment
import pl.gymbro.data.Inventory
import pl.gymbro.data.SetEntry
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator

/**
 * Poprawianie zapisanej serii.
 *
 * Te same regulatory co przy zapisie, żeby nie uczyć się drugiego sposobu
 * wpisywania tego samego. Kasowanie zostaje tutaj, w jednym miejscu
 * z poprawianiem - ale jako osobny przycisk, a nie skutek dotknięcia kafelka.
 *
 * Okno jest jedno dla trwającego treningu i dla historii. Dlatego bierze
 * nazwę, sprzęt i krok wprost, a nie pozycję planu: trening sprzed miesiąca
 * mógł iść według planu, którego już nie ma, a serię dalej trzeba umieć
 * poprawić.
 *
 * @param showWeight czy pokazać regulator ciężaru - przy ćwiczeniu z masą
 *   ciała, zapisanym z zerem, byłby liczbą, która nic nie znaczy.
 */
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
    // Zapas wchodzi do poprawki z tym, co zapisano - inaczej okno milczało
    // o jedynej wartości, której po treningu nie da się odtworzyć z pamięci.
    var rir by remember(entry.id) { mutableStateOf(entry.rir) }
    var typingWeight by remember { mutableStateOf(false) }

    if (typingWeight) {
        WeightDialog(
            initialKg = weight,
            title = exerciseName,
            onDismiss = { typingWeight = false },
            onPick = { weight = it; typingWeight = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Popraw serię") },
        text = {
            Column {
                if (showWeight) {
                    Stepper(
                        label = "${Loading.fmt(weight)} ${equipment.weightUnitLabel}",
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
                        onLabelClick = if (mode == null) ({ typingWeight = true }) else null
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Stepper(
                    label = "$reps powt.",
                    onMinus = { if (reps > 1) reps-- },
                    onPlus = { reps++ }
                )

                // Zapas widoczny od razu, nie pod przyciskiem jak przy
                // świeżej serii: tam się go podaje, tutaj przede wszystkim
                // sprawdza, co się wtedy podało.
                Spacer(Modifier.height(14.dp))
                Text(
                    if (rir != null) "Zapas powtórzeń: $rir" else "Zapas powtórzeń: nie podano",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rir != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 1, 2, 3).forEach { v ->
                        // Ponowne naciśnięcie zdejmuje zapas - tak samo jak
                        // przy świeżej serii i tak samo jak na zegarku.
                        if (rir == v) {
                            Button(
                                onClick = { rir = null },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (v >= 3) "3+" else v.toString()) }
                        } else {
                            OutlinedButton(
                                onClick = { rir = v },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (v >= 3) "3+" else v.toString()) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Usuń tę serię", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(weight, reps, rir) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
