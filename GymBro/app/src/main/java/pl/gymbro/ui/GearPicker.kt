package pl.gymbro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
 * Wygląd z tych samych klocków co reszta Profilu: zestawy jako karty z ikoną,
 * działy jako grupy wierszy z ptaszkiem. Była tu lista pól wyboru na gołym
 * tle i rząd przycisków przewijany w bok - jedyny ekran Profilu, który
 * wyglądał jak z innej aplikacji.
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
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
            Text(
                "Twój sprzęt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { picked = emptySet() }, enabled = picked.isNotEmpty()) { Text("Wyczyść") }
        }
        SearchField(query, { query = it }, "Szukaj: ławka, drążek, suwnica")

        // Zestawy przewijają się razem z listą. Stojąc na sztywno nad polem
        // szukania, przy otwartej klawiaturze zostawiały liście miejsce na
        // jeden wiersz - wyniki wpisanego słowa leżały pod klawiaturą.
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (query.isBlank()) item(key = "zestawy") { Presets(picked) { picked = it } }
            GearGroup.entries.forEach { group ->
                val wGrupie = visible.filter { it.group == group }
                if (wGrupie.isEmpty()) return@forEach
                // Cała grupa jednym elementem listy: wiersze siedzą w jednej
                // karcie, a karty nie da się rozciąć między elementy listy.
                // Najdłuższy dział ma kilkanaście pozycji, więc nic nie traci
                // się na leniwym składaniu.
                item(key = "g-" + group.name) {
                    val ile = wGrupie.count { it in picked }
                    GroupLabel(group.label + if (ile > 0) " · $ile" else "")
                    SettingsGroup {
                        wGrupie.forEachIndexed { i, gear ->
                            val on = gear in picked
                            SettingsRow(
                                title = gear.label,
                                first = i == 0,
                                onClick = { picked = if (on) picked - gear else picked + gear }
                            ) {
                                if (on) {
                                    Icon(
                                        Icons.Filled.CheckCircle, contentDescription = "Zaznaczone",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Circle, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
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
            item(key = "dol") { Spacer(Modifier.height(8.dp)) }
        }

        Button(
            onClick = { onDone(picked) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
        ) {
            Text(
                "Gotowe · " + plural(picked.size, "pozycja", "pozycje", "pozycji"),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/** Ikona zestawu - po nazwie, bo zestawy to pary w [Gear.PRESETS]. */
private fun presetIcon(name: String): ImageVector = when {
    name.contains("Domowa") -> Icons.Filled.Home
    name.contains("osiedlowa") -> Icons.Filled.Apartment
    name.contains("Drążek") -> Icons.Filled.SportsGymnastics
    name.contains("Kettlebell") -> Icons.Filled.SportsMartialArts
    else -> Icons.Filled.FitnessCenter
}

/**
 * Gotowe zestawy, po dwa w rzędzie.
 *
 * Siatka z rzędów, nie przewijany pasek: przy pięciu zestawach w jednym
 * rzędzie dwa ostatnie leżały za krawędzią i nikt nie wiedział, że są.
 */
@Composable
private fun Presets(picked: Set<Gear>, onChange: (Set<Gear>) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Zaznacz, co masz. Plany i ćwiczenia dobiorę do tego - bez stojaków " +
                "nie zaproponuję przysiadu ze sztangą, bez drążka podciągania.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
        GroupLabel("Gotowe zestawy")
        Gear.PRESETS.chunked(2).forEach { rzad ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rzad.forEach { (name, gear) ->
                    val zawarty = picked.containsAll(gear)
                    AppCard(
                        Modifier.weight(1f).padding(vertical = 4.dp),
                        accent = if (zawarty) MaterialTheme.colorScheme.primary else null,
                        onClick = { onChange(if (zawarty) picked - gear else picked + gear) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(presetIcon(name), size = 32)
                                Spacer(Modifier.weight(1f))
                                if (zawarty) {
                                    Icon(
                                        Icons.Filled.CheckCircle, contentDescription = "Dodany",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                plural(gear.size, "pozycja", "pozycje", "pozycji"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Nieparzysty ostatni rząd: pusta połowa, żeby karta nie
                // rozciągała się na całą szerokość.
                if (rzad.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
