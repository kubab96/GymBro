package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Plan

/**
 * Jednostki z pozostałych planów - za kafelkiem „Z innych planów".
 *
 * Każdy plan to karta, która rozwija swoje jednostki. Rozwinięty jest na
 * start ten, z którego trenowało się ostatnio: do jednorazowego treningu
 * z innego planu wraca się zwykle do tego samego. Wypisane wszystkie
 * naraz robiły z kilku planów długą listę bez punktu zaczepienia.
 *
 * Start pyta jak każda jednostka. Jednorazowy trening z innego planu nie
 * zmienia planu aktywnego: zegarek dalej dostaje jednostki tamtego.
 */
@Composable
internal fun OtherPlansScreen(
    programs: List<ProgramUnits>,
    onBack: () -> Unit,
    onStart: (Plan) -> Unit
) {
    ScreenBackHandler(onBack = onBack)
    val firstOpen = remember(programs) {
        programs.maxByOrNull { p -> p.units.maxOfOrNull { it.lastDoneAt ?: 0L } ?: 0L }?.program?.id
    }
    var open by rememberSaveable { mutableStateOf(listOfNotNull(firstOpen)) }
    var confirming by remember { mutableStateOf<PlanOption?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubScreenHeader("Z innych planów", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            programs.forEach { p ->
                val expanded = p.program.id in open
                AppCard(
                    accent = if (expanded) MaterialTheme.colorScheme.primary else null,
                    onClick = { open = if (expanded) open - p.program.id else open + p.program.id }
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.program.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    plural(p.units.size, "jednostka", "jednostki", "jednostek"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                                tint = if (expanded) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expanded) {
                            HorizontalDivider(
                                Modifier.padding(top = 12.dp, bottom = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            p.units.forEach { u -> UnitStartRow(u) { confirming = u } }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    confirming?.let { o ->
        StartConfirmDialog(o, onDismiss = { confirming = null }) {
            confirming = null
            onStart(o.plan)
        }
    }
}

/** Jednostka w rozwiniętym planie: nazwa, ćwiczenia i serie, kiedy ostatnio, start. */
@Composable
private fun UnitStartRow(o: PlanOption, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(o.plan.name, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    plural(o.exerciseCount, "ćwiczenie", "ćwiczenia", "ćwiczeń"),
                    o.sets.takeIf { it > 0 }?.let { plural(it, "seria", "serie", "serii") },
                    lastDoneLabel(o.lastDoneAt)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.PlayArrow, contentDescription = "Zacznij " + o.plan.name,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}
