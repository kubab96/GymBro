package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Elementy kreatorów: nowy plan, nowa jednostka, wybór ćwiczeń.
 *
 * Wszystkie wyglądają jak pomiar w zakładce Waga - tytuł z numerem kroku,
 * pasek postępu pod nim, jedno pytanie na ekranie. Jeden plik, bo kreatorów
 * jest kilka, a różniący się nagłówek albo pasek w każdym z nich czytałby
 * się jak trzy różne aplikacje.
 */

/** „Nowy plan · 2 z 4" z „Anuluj" po prawej i paskiem postępu pod spodem. */
@Composable
internal fun StepHeader(title: String, index: Int, count: Int, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (count > 1) "$title · ${index + 1} z $count" else title,
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = onCancel) { Text("Anuluj") }
    }
    if (count > 1) StepProgress(index, count)
    Spacer(Modifier.height(14.dp))
}

/** Pasek postępu kreatora - ten sam wygląd co przy pomiarze w zakładce Waga. */
@Composable
internal fun StepProgress(index: Int, count: Int) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= index) active else inactive)
            )
        }
    }
}

/**
 * Jedna odpowiedź w kreatorze - cała karta jest przyciskiem.
 *
 * Zaznaczona ma obwódkę w kolorze akcji, żeby po cofnięciu było widać, co
 * się wybrało. Karta z [AppCard], nie szary prostokąt - kreator był ostatnim
 * miejscem w starym wyglądzie, ze strzałką „▶" zamiast ikony.
 */
@Composable
internal fun ChoiceCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    AppCard(
        Modifier.padding(vertical = 4.dp),
        accent = if (selected) MaterialTheme.colorScheme.primary else null,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                IconBadge(icon)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Pytanie kroku i odpowiedzi pod nim, przewijane na niskim ekranie. */
@Composable
internal fun StepColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        content()
        Spacer(Modifier.height(12.dp))
    }
}

/** Podpis nad polem w formularzu kroku. */
@Composable
internal fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

/**
 * Mały regulator: minus, liczba, plus.
 *
 * Mniejszy od [Stepper], bo w karcie ćwiczenia stoją dwa obok siebie
 * (powtórzenia od i do). Pełnowymiarowe zajęłyby całą szerokość każdy.
 */
@Composable
internal fun MiniStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onMinus,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("−", fontSize = 20.sp) }
        Text(
            value,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )
        OutlinedButton(
            onClick = onPlus,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp)
        ) { Text("+", fontSize = 20.sp) }
    }
}

/**
 * Rząd przycisków z jednym wyborem; ponowne dotknięcie wybranego go zdejmuje
 * tylko wtedy, gdy [allowNone].
 *
 * Przewijany w poziomie, bo poziomy zaawansowania nie mieszczą
 * się w jednej linii na węższym telefonie.
 */
@Composable
internal fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    allowNone: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            if (value == selected) {
                Button(
                    onClick = { if (allowNone) onSelect(null) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(value) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** Pole szukania z krzyżykiem - wyczyszczenie z klawiatury to kilka dotknięć. */
@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = if (value.isNotEmpty()) {
            { TextButton(onClick = { onChange("") }) { Text("✕") } }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )
}
