package pl.gymbro.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
                // Lekkie tło, jak przy każdej liczbie do wpisania - mówi, że
                // w nią da się dotknąć. Pole tekstowe zabierałoby miejsce
                // przyciskom i przy wejściu w ekran wyskakiwałaby klawiatura.
                Text(
                    label,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .tap(RoundedCornerShape(10.dp), onClick = onLabelClick)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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
