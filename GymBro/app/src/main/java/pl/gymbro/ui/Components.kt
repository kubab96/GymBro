package pl.gymbro.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.data.roundKg
import pl.gymbro.gym.Loading

/**
 * Regulator wartości z dużymi przyciskami.
 *
 * Używany i przy logowaniu serii, i w edytorze planu - w obu miejscach obsługiwany
 * kciukiem jednej ręki, często w ruchu. Stąd 56 dp na przycisk: to minimum,
 * w które trafia się bez patrzenia.
 *
 * Co dokładnie znaczy krok, decyduje miejsce wywołania. Przy sprzęcie ładowanym
 * talerzami to następny próg możliwy do złożenia, przy wyciągu - stały przyrost.
 *
 * [onLabelClick] robi z samej liczby przycisk: dotknięcie otwiera wpisanie
 * wartości wprost. Potrzebne tam, gdzie krok nie trafia we wszystko -
 * stos wyciągu z progami co 5,67 kg mija każdą okrągłą liczbę, a do 28,4 kg
 * nie dało się dojść plusem z żadnej strony.
 */
@Composable
internal fun Stepper(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onLabelClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Domyślny contentPadding przycisku nie zmieściłby się w 56 dp i obciąłby znak.
        OutlinedButton(
            onClick = onMinus,
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("−", fontSize = 22.sp) }

        // Liczba w pudełku tej samej wysokości co przyciski i zajmującym
        // całą resztę wiersza. Bez tego regulator z dotykalną liczbą był
        // o te kilka pikseli wyższy od zwykłego i stojące jeden pod drugim
        // ciężar i powtórzenia wyglądały na przesunięte względem siebie.
        Box(
            Modifier.weight(1f).height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (onLabelClick == null) {
                Text(label, fontSize = 22.sp)
            } else {
                // Podkreślenie, bo inaczej nic nie mówi, że w liczbę da się
                // dotknąć. Pole tekstowe zamiast tego zabierałoby miejsce
                // przyciskom i przy wejściu w ekran wyskakiwałaby klawiatura.
                Text(
                    label,
                    fontSize = 22.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .tap(RoundedCornerShape(8.dp), onClick = onLabelClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        OutlinedButton(
            onClick = onPlus,
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("+", fontSize = 22.sp) }
    }
}

/**
 * Wpisanie ciężaru wprost, z klawiatury.
 *
 * Istnieje dla sprzętu, którego aplikacja nie składa z talerzy: tam liczba
 * na tabliczce bywa dowolna i żaden stały krok jej nie trafia. Przy sztandze
 * tego okna nie ma i nie ma go celowo - ciężar, którego nie da się nałożyć,
 * nie jest ciężarem, tylko życzeniem.
 *
 * Przecinek i kropka znaczą to samo, bo klawiatura numeryczna daje raz jedno,
 * raz drugie, zależnie od ustawień telefonu.
 */
@Composable
internal fun WeightDialog(
    initialKg: Double,
    title: String = "Ciężar",
    onDismiss: () -> Unit,
    onPick: (Double) -> Unit
) {
    var text by remember { mutableStateOf(Loading.fmt(initialKg)) }
    val parsed = text.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onPick(roundKg(it.coerceAtLeast(0.0))) } },
                enabled = parsed != null && parsed >= 0.0
            ) { Text("Ustaw") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}
