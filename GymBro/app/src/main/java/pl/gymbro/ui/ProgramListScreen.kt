package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pl.gymbro.ui.theme.ButtonShape
import pl.gymbro.data.Program

/**
 * Lista planów treningowych - w kodzie „program", na ekranie „plan".
 *
 * Program to schemat - FBW, PPL, góra/dół - a jednostki w środku to konkretne
 * treningi. Rozdzielenie tych poziomów pozwala trzymać kilka schematów naraz
 * i przełączać się między nimi bez kasowania poprzedniego.
 *
 * Aktywny jest dokładnie jeden: jego jednostki dostaje zegarek i od niego
 * zaczyna ekran treningu. Tam da się podejrzeć i zrobić także inny plan -
 * bez przestawiania aktywnego.
 *
 * Nazewnictwo: użytkownik mówi na to „plan treningowy", więc tak brzmi każdy
 * napis. W kodzie zostaje `Program`, bo `Plan` to już jednostka (A, B, Push)
 * i zamiana nazw w modelu, bazie i protokole zegarka nie jest warta tego,
 * co mogłaby zepsuć.
 */

/** Program razem z liczbą jednostek - do wyświetlenia na karcie. */
data class ProgramOption(val program: Program, val planCount: Int)

@Composable
fun ProgramList(
    programs: List<ProgramOption>,
    onOpen: (Program) -> Unit,
    onActivate: (Program) -> Unit,
    onRename: (Program) -> Unit,
    onDelete: (Program) -> Unit,
    onNew: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Text("Plan", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Plany treningowe",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))

        if (programs.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Nie masz jeszcze planu", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Plan to schemat treningu, a jednostki w środku to konkretne " +
                            "dni. Na przykład FBW z jednostkami A, B i C, albo PPL " +
                            "z Push, Pull i Legs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(programs, key = { it.program.id }) { option ->
                ProgramCard(
                    option = option,
                    onOpen = { onOpen(option.program) },
                    onActivate = { onActivate(option.program) },
                    onRename = { onRename(option.program) },
                    onDelete = { onDelete(option.program) }
                )
            }
        }

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) { Text("Stwórz nowy plan", modifier = Modifier.padding(vertical = 6.dp)) }
    }
}

@Composable
private fun ProgramCard(
    option: ProgramOption,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val active = option.program.isActive

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        option.program.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (option.planCount == 0) "brak jednostek"
                        else "${option.planCount} ${unitWord(option.planCount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (active) {
                        Text(
                            "aktywny",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text("▶", style = MaterialTheme.typography.titleLarge)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Aktywacja ma sens tylko dla programu, który aktywny nie jest.
                if (!active) {
                    TextButton(onClick = onActivate) { Text("Ustaw aktywny") }
                }
                TextButton(onClick = onRename) { Text("Nazwa") }
                TextButton(onClick = onDelete) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun unitWord(n: Int): String = when {
    n == 1 -> "jednostka"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "jednostki"
    else -> "jednostek"
}
