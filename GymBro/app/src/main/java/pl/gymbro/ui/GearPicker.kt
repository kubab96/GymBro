package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Gear
import pl.gymbro.data.GearGroup
import pl.gymbro.data.TextSearch

/**
 * Sprzęt w Profilu - cały ekran, jak „lokalizacja" w Fitbodzie.
 *
 * Gotowe zestawy na górze zaznaczają typowy komplet jednym dotknięciem,
 * a lista pod spodem pozwala go dopiąć. Zestaw dokłada pozycje, nie zastępuje
 * zaznaczonych: kto ma domową siłownię i do tego kettlebell, dotyka zestawu
 * i nie traci kettlebella.
 *
 * Wynik idzie do szkicu Profilu, nie do bazy - zapisuje go przycisk Zapisz,
 * jak każdą inną zmianę na tamtym ekranie.
 */
@Composable
internal fun GearPickerScreen(
    selected: Set<Gear>,
    onCancel: () -> Unit,
    onDone: (Set<Gear>) -> Unit
) {
    ScreenBackHandler(onBack = onCancel)
    var picked by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }

    val words = remember(query) { TextSearch.words(query) }
    val visible = Gear.entries.filter {
        TextSearch.matches(words, TextSearch.fold(it.label + " " + it.group.label))
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)) {
        StepHeader("Twój sprzęt", 0, 1, onCancel)
        SearchField(query, { query = it }, "Szukaj: ławka, drążek, suwnica")

        // Opis i zestawy przewijają się razem z listą. Stojąc na sztywno nad
        // polem szukania, przy otwartej klawiaturze zostawiały liście miejsce
        // na jeden wiersz - wyniki wpisanego słowa leżały pod klawiaturą.
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (query.isBlank()) item(key = "wstep") { GearIntro(picked) { picked = it } }
            GearGroup.entries.forEach { group ->
                val wGrupie = visible.filter { it.group == group }
                if (wGrupie.isEmpty()) return@forEach
                item(key = "g-" + group.name) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                    )
                }
                items(wGrupie, key = { it.name }) { gear ->
                    val on = gear in picked
                    Row(
                        // Cały wiersz, nie samo pole: kwadracik wielkości
                        // paznokcia to zły cel dla kciuka.
                        Modifier
                            .fillMaxWidth()
                            .clickable { picked = if (on) picked - gear else picked + gear },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(
                            gear.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "pusto") {
                    Text(
                        "Nic takiego na liście. Spróbuj innego słowa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { picked = emptySet() },
                enabled = picked.isNotEmpty()
            ) { Text("Wyczyść") }
            Button(onClick = { onDone(picked) }, modifier = Modifier.weight(1f)) {
                Text(
                    "Gotowe · " + plural(picked.size, "pozycja", "pozycje", "pozycji"),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** Wstęp nad listą: co robi ten ekran i gotowe zestawy. */
@Composable
private fun GearIntro(picked: Set<Gear>, onChange: (Set<Gear>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "Zaznacz, co masz. Plany i ćwiczenia dobiorę do tego - bez stojaków " +
                "nie zaproponuję przysiadu ze sztangą, bez drążka podciągania.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Text("Na start", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Gear.PRESETS.forEach { (name, gear) ->
                val zawarty = picked.containsAll(gear)
                if (zawarty) {
                    Button(
                        onClick = { onChange(picked - gear) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(name, style = MaterialTheme.typography.labelMedium) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(picked + gear) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(name, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
