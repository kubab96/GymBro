package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Bia

/**
 * Skład ciała policzony z oporu - te same wiersze w kreatorze pomiaru
 * i na ekranie Masy, żeby dwie liczby dla tego samego pomiaru nie mogły
 * się rozjechać formatem ani zaokrągleniem.
 *
 * Bez nazw wzorów i marginesów błędu: zakładka Waga ma krótkie instrukcje,
 * a skąd liczby się biorą, mówi `docs/sklad-ciala.md`.
 */
@Composable
internal fun BiaRows(e: Bia.Estimate) {
    Column(Modifier.fillMaxWidth()) {
        BiaRow("Tkanka tłuszczowa", "%.1f %% · %.1f kg".format(e.fatPct, e.fatKg))
        BiaRow("Masa beztłuszczowa", "%.1f kg".format(e.leanKg))
        e.muscleKg?.let { BiaRow("Mięśnie szkieletowe", "%.1f kg".format(it)) }
        BiaRow("Woda", "%.1f kg".format(e.waterKg))
        e.asymmetry?.armsPct?.let { BiaRow("Ręce, lewa wobec prawej", sidePct(it)) }
        e.asymmetry?.legsPct?.let { BiaRow("Nogi, lewa wobec prawej", sidePct(it)) }
        if (e.basis == Bia.Basis.BORROWED) {
            Spacer(Modifier.height(6.dp))
            // Jedno zdanie, bo to jedyna rzecz, którą trzeba wiedzieć, żeby
            // liczby nie wprowadzały w błąd: waga z czterema elektrodami
            // widzi nogi, a wzór zakłada całe ciało.
            Text(
                "Waga mierzy przez stopy - patrz na kierunek zmian, nie na samą liczbę.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BiaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** Różnica poniżej procenta to szum pomiaru, nie strona silniejsza od drugiej. */
private fun sidePct(v: Double): String = when {
    kotlin.math.abs(v) < 1.0 -> "równo"
    v > 0 -> "+%.0f %%".format(v)
    else -> "%.0f %%".format(v)
}
