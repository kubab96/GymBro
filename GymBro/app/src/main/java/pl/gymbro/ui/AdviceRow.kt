package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.data.PlanItem
import pl.gymbro.data.StoredAdvice

/**
 * Czekająca propozycja zmiany, pokazana przy ćwiczeniu w planie.
 *
 * Siedzi tutaj, a nie na osobnej karcie po treningu, bo tu podejmuje się
 * decyzję: widać obok siebie to, co w planie stoi dziś, co zrobiłeś ostatnio
 * i co z tego wynika.
 *
 * Najważniejsze jest „było → będzie" w dużym druku. Dotąd całość była
 * jednym zdaniem drobnym drukiem w kolorowym prostokącie i liczbę trzeba
 * było z niego wyłuskać. Pod spodem ostatni wynik i powód - bez nich
 * propozycji nie da się sprawdzić, a mechanizmu, którego nie da się
 * sprawdzić, nikt nie używa.
 *
 * Kolor mówi o kierunku: turkus to dokładanie, bursztyn zejście z ciężaru
 * (zejście kosztuje więcej niż spojrzenie, więc ma się wyróżniać), szarość
 * - sama wskazówka bez zmiany w planie.
 */
@Composable
fun AdviceRow(
    item: PlanItem,
    advice: StoredAdvice,
    onAccept: () -> Unit,
    onSkip: () -> Unit
) {
    val tone = adviceTone(advice)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tone.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(tone.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(adviceIcon(advice), contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
            }
            Text(
                adviceTitle(advice),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tone,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        val change = adviceChange(item, advice)
        if (change != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    change.first,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "na",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp).size(18.dp)
                )
                Text(
                    change.second,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                adviceText(item, advice),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (advice.last.isNotBlank()) {
            Text(
                "ostatnio " + advice.last,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (advice.reason.isNotBlank()) {
            Text(
                advice.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cel powtórzeń i „bez zmian" niczego w planie nie ruszają.
            // Zastosuj obok Pomiń sugerowałoby, że jedno coś zmienia, a drugie
            // nie - a oba robiłyby to samo.
            if (isInfoOnly(advice)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Rozumiem") }
            } else {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tone,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) { Text("Zastosuj", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onSkip, modifier = Modifier.width(110.dp)) { Text("Pomiń") }
            }
        }
    }
}

@Composable
private fun adviceTone(advice: StoredAdvice): Color = when {
    advice.kind == "WEIGHT_DOWN" -> MaterialTheme.colorScheme.tertiary
    isInfoOnly(advice) -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.secondary
}

private fun adviceIcon(advice: StoredAdvice): ImageVector = when (advice.kind) {
    "WEIGHT_UP" -> Icons.AutoMirrored.Filled.TrendingUp
    "WEIGHT_DOWN" -> Icons.AutoMirrored.Filled.TrendingDown
    "REPS_UP", "SET_UP" -> Icons.Filled.Add
    "REPS_GOAL" -> Icons.Filled.Flag
    else -> Icons.Filled.Remove
}

/** Nagłówek propozycji: jedno, dwa słowa o tym, co się zmienia. */
fun adviceTitle(advice: StoredAdvice): String = when (advice.kind) {
    "WEIGHT_UP" -> "Większy ciężar"
    "WEIGHT_DOWN" -> "Zejdź z ciężaru"
    "REPS_UP" -> "Szerszy zakres powtórzeń"
    "SET_UP" -> "Dodatkowa seria"
    "REPS_GOAL" -> "Cel na następny raz"
    else -> "Bez zmian"
}

/**
 * Było i będzie jako dwie krótkie wartości - do dużego „60 kg → 62,5 kg".
 * Null przy propozycji bez zmiany w planie; wtedy zostaje zdanie z [adviceText].
 */
fun adviceChange(item: PlanItem, advice: StoredAdvice): Pair<String, String>? = when (advice.kind) {
    "WEIGHT_UP", "WEIGHT_DOWN" -> {
        val reps = advice.repsTo?.takeIf { it != item.repsTo }
        (fmtKg(item.targetKg) + " kg" + (reps?.let { " · ${item.repsFrom}-${item.repsTo}" } ?: "")) to
            (fmtKg(advice.weightKg ?: 0.0) + " kg" + (reps?.let { " · ${item.repsFrom}-$it" } ?: ""))
    }
    "REPS_UP" -> "${item.repsFrom}-${item.repsTo} powt." to "${item.repsFrom}-${advice.repsTo} powt."
    "SET_UP" -> plural(item.sets, "seria", "serie", "serii") to
        plural(advice.sets ?: item.sets, "seria", "serie", "serii")
    else -> null
}

/** Zmiana zapisana jako „z czego na co" - inaczej liczba wisi w próżni. */
fun adviceText(item: PlanItem, advice: StoredAdvice): String = when (advice.kind) {
    "WEIGHT_UP", "WEIGHT_DOWN" ->
        "ciężar ${fmtKg(item.targetKg)} na ${fmtKg(advice.weightKg ?: 0.0)} kg" +
            // Skok przez przepaść cofa zakres wydłużony na czas przechodzenia.
            advice.repsTo?.takeIf { it != item.repsTo }
                ?.let { ", zakres ${item.repsFrom}-${item.repsTo} na ${item.repsFrom}-$it" }
                .orEmpty()

    "REPS_UP" ->
        "zakres ${item.repsFrom}-${item.repsTo} na ${item.repsFrom}-${advice.repsTo}"

    "SET_UP" ->
        "serie ${item.sets} na ${advice.sets}"

    "REPS_GOAL" ->
        "następnym razem: ${advice.repsTo ?: item.repsTo} powt. w serii" +
            if (item.hasTargetWeight) " przy ${fmtKg(item.targetKg)} kg" else ""

    else -> advice.repsTo?.let { "bez zmian, cel $it powt. w serii" } ?: "bez zmian"
}

/** Propozycja, która niczego w planie nie zmienia - tylko mówi, co dalej. */
fun isInfoOnly(advice: StoredAdvice): Boolean =
    advice.kind == "REPS_GOAL" || advice.kind == "HOLD"

private fun fmtKg(v: Double): String =
    if (kotlin.math.abs(v - Math.round(v)) < 0.01) Math.round(v).toString()
    else String.format(java.util.Locale.getDefault(), "%.1f", v)
