package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Plan na liście - w stylu kart z Treningu.
 *
 * Aktywny ma obwódkę w kolorze akcji i pigułkę „aktywny"; dotąd cała karta
 * była niebieska i przy dwóch planach wyglądała jak zaznaczony przycisk.
 * Nazwa, aktywacja i usuwanie w menu pod trzema kropkami zamiast trzech
 * napisów pod każdym planem.
 */
@Composable
private fun ProgramCard(
    option: ProgramOption,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val active = option.program.isActive
    var menu by remember { mutableStateOf(false) }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.program.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (active) {
                        Text(
                            "aktywny",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    if (option.planCount == 0) "brak jednostek"
                    else plural(option.planCount, "jednostka", "jednostki", "jednostek"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Aktywacja ma sens tylko dla programu, który aktywny nie jest.
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text("Ustaw jako aktywny") },
                            onClick = { menu = false; onActivate() }
                        )
                    }
                    DropdownMenuItem(text = { Text("Zmień nazwę") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("Usuń plan", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
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
