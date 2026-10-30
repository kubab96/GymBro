package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Regulator wartości z dużymi przyciskami.
 *
 * Używany i przy logowaniu serii, i w edytorze planu - w obu miejscach obsługiwany
 * kciukiem jednej ręki, często w ruchu. Stąd 56 dp na przycisk: to minimum,
 * w które trafia się bez patrzenia.
 *
 * Co dokładnie znaczy krok, decyduje miejsce wywołania. Przy sprzęcie ładowanym
 * talerzami to następny próg możliwy do złożenia, przy wyciągu - stały przyrost.
 */
@Composable
internal fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
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

        Text(label, fontSize = 22.sp)

        OutlinedButton(
            onClick = onPlus,
            modifier = Modifier.size(56.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("+", fontSize = 22.sp) }
    }
}
