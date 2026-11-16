package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pl.gymbro.data.PlanItem
import pl.gymbro.data.StoredAdvice

/**
 * Czekająca propozycja zmiany, pokazana przy ćwiczeniu w planie.
 *
 * Siedzi tutaj, a nie na osobnej karcie po treningu, bo tu podejmuje się
 * decyzję: widać obok siebie to, co w planie stoi dziś, co zrobiłeś ostatnio
 * i co z tego wynika. Karta po treningu wyskakiwała raz i znikała, nawet gdy
 * akurat nie było kiedy się nad nią zastanawiać.
 *
 * Trzy linijki w stałej kolejności: ostatni wynik, proponowana zmiana, powód.
 * Sama liczba bez poprzedniej nic nie znaczy, a bez powodu nie da się jej
 * sprawdzić - a mechanizmu, którego nie da się sprawdzić, nikt nie używa.
 */
@Composable
fun AdviceRow(
    item: PlanItem,
    advice: StoredAdvice,
    onAccept: () -> Unit,
    onSkip: () -> Unit
) {
    val down = advice.kind == "WEIGHT_DOWN"
    val tint = if (down) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onTint = if (down) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tint)
            .padding(10.dp)
    ) {
        if (advice.last.isNotBlank()) {
            Text(
                "ostatnio: ${advice.last}",
                style = MaterialTheme.typography.bodySmall,
                color = onTint
            )
        }
        Text(
            adviceText(item, advice),
            style = MaterialTheme.typography.titleSmall,
            color = onTint
        )
        if (advice.reason.isNotBlank()) {
            Text(
                advice.reason,
                style = MaterialTheme.typography.bodySmall,
                color = onTint
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = onAccept,
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) { Text("Zastosuj") }
            TextButton(
                onClick = onSkip,
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) { Text("Pomiń") }
        }
    }
}

/** Zmiana zapisana jako „z czego na co" - inaczej liczba wisi w próżni. */
fun adviceText(item: PlanItem, advice: StoredAdvice): String = when (advice.kind) {
    "WEIGHT_UP", "WEIGHT_DOWN" ->
        "ciężar ${fmtKg(item.targetKg)} na ${fmtKg(advice.weightKg ?: 0.0)} kg"

    "REPS_UP" ->
        "zakres ${item.repsFrom}-${item.repsTo} na ${item.repsFrom}-${advice.repsTo}"

    "SET_UP" ->
        "serie ${item.sets} na ${advice.sets}"

    else -> "bez zmian"
}

private fun fmtKg(v: Double): String =
    if (kotlin.math.abs(v - Math.round(v)) < 0.01) Math.round(v).toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", v)
