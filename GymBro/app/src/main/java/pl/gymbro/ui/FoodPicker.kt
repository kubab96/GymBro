package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pl.gymbro.data.Food
import pl.gymbro.data.FoodLogEntry
import pl.gymbro.data.FoodSource
import pl.gymbro.data.Meal
import pl.gymbro.data.Nutrients
import pl.gymbro.nutrition.NutritionRepos
import pl.gymbro.nutrition.Portions

/**
 * Wybieranie produktu i wpisywanie ilości.
 *
 * Oddzielone od [FoodScreen], bo to inny rodzaj pracy: tam czyta się dzień,
 * tutaj się go uzupełnia. Obie części są duże, a razem dawałyby plik, w którym
 * nie da się znaleźć niczego.
 */

// ---- Szukanie ------------------------------------------------------------

/**
 * Lista produktów do wyboru.
 *
 * Gdy nic nie wpisano, pokazuje **ostatnio jedzone** - i to jest tu
 * najważniejsze. Je się kilkadziesiąt produktów, nie trzydzieści tysięcy,
 * więc w większości przypadków właściwy produkt jest na tej liście jeszcze
 * przed pierwszą literą.
 */
@Composable
internal fun FoodSearchView(
    meal: Meal,
    repos: NutritionRepos,
    onPick: (Food) -> Unit,
    onScan: () -> Unit,
    onQuickAdd: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Food>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Food>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        recent = withContext(Dispatchers.IO) { repos.foods.recent(20) }
    }

    // Odczekanie po ostatnim znaku, zamiast zapytania na każdą literę.
    //
    // „kurczak" wpisane bez tego to siedem zapytań po katalogu, z których
    // sześć jest wyrzucane, a pierwsze - po literze „k" - jest najdroższe,
    // bo pasuje do wszystkiego. Korutyna jest tu anulowana przy każdej
    // zmianie tekstu, więc odliczanie zaczyna się od nowa samo.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(180)
        results = withContext(Dispatchers.IO) { repos.foods.search(query, 40) }
        searching = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(meal.label, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Zamknij") }
        }

        // Kursor w polu od razu po wejściu.
        //
        // Zmierzone na telefonie: bez tego otwarcie szukania kosztuje dwa
        // dotknięcia zamiast jednego - najpierw przycisk, potem samo pole,
        // żeby podnieść klawiaturę. Na ekranie, w który wchodzi się kilka
        // razy dziennie, to jest ta różnica, przez którą dziennik się
        // porzuca.
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Szukaj produktu") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focus)
        )

        val pusto = query.isBlank()
        val lista = if (pusto) recent else results

        Text(
            when {
                pusto && recent.isEmpty() -> "Zacznij pisać, żeby znaleźć produkt."
                pusto -> "Ostatnio jedzone"
                searching -> "Szukam…"
                lista.isEmpty() -> "Nic nie znaleziono."
                else -> "Wyniki"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(lista, key = { "produkt-${it.id}" }) { food ->
                FoodRow(food) { onPick(food) }
            }
        }

        // Dwa wyjścia, zawsze widoczne. Produkt, którego nie ma w katalogu,
        // jest najczęstszym powodem porzucenia dziennika - i dlatego te
        // przyciski nie chowają się za brakiem wyników.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Skanuj") }
            OutlinedButton(
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f)
            ) { Text("Same kalorie") }
        }
    }
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    food.brand?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                    append(sourceLabel(food.source))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "%.0f kcal/100 g".format(food.per100.kcal),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun sourceLabel(source: FoodSource): String = when (source) {
    FoodSource.SEED -> "produkt podstawowy"
    FoodSource.OWN -> "mój produkt"
    FoodSource.RECIPE -> "przepis"
    FoodSource.OFF -> "Open Food Facts"
}

// ---- Ilość ---------------------------------------------------------------

/** Jednostka, w której podaje się ilość. [gramsPerUnit] null = wpisujesz gramy. */
private data class AmountUnit(val label: String, val gramsPerUnit: Double?)

/**
 * Okno wpisywania ilości.
 *
 * Jednostki są tu po to, żeby nie trzeba było ważyć. Kolejność nie jest
 * przypadkowa: najpierw gramy (bo to jedyna jednostka, którą ma każdy
 * produkt), potem porcje nazwane, na końcu porcja producenta i opakowanie.
 *
 * Podstawiana wartość to **ostatnia gramatura tego produktu**. Jeśli zawsze
 * jesz 180 g ryżu, to podstawienie stu gramów byłoby poprawką do wykonania
 * przy każdym wpisie.
 */
@Composable
internal fun PortionDialog(
    food: Food,
    meal: Meal,
    lastGrams: Double?,
    onAdd: (grams: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val units = remember(food.id) {
        buildList {
            add(AmountUnit("gramy", null))
            food.portions.forEach { add(AmountUnit(it.label, it.grams)) }
            food.servingG?.let { add(AmountUnit("porcja", it)) }
            food.packageG?.let { add(AmountUnit("opakowanie", it)) }
        }
    }

    // Gramy, gdy wiadomo, ile wzięto ostatnio; w przeciwnym razie pierwsza
    // porcja nazwana, bo „dwie kromki" jest łatwiejsze niż „70 g".
    var unit by remember(food.id) {
        mutableStateOf(
            if (lastGrams != null || units.size == 1) units.first() else units[1]
        )
    }
    var text by remember(food.id) {
        mutableStateOf(
            when {
                unit.gramsPerUnit == null && lastGrams != null -> fmtG(lastGrams)
                unit.gramsPerUnit == null -> "100"
                else -> "1"
            }
        )
    }

    val value = text.replace(',', '.').toDoubleOrNull()
    val grams = value?.let { v ->
        Portions.gramsOf(food, Portions.Amount.Grams(v * (unit.gramsPerUnit ?: 1.0)))
    }
    val nutrients = grams?.let { Portions.nutrientsFor(food, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column {
                Text(
                    "na 100 g: %.0f kcal · B %.1f · W %.1f · T %.1f".format(
                        food.per100.kcal,
                        food.per100.proteinG,
                        food.per100.carbsG,
                        food.per100.fatG
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    units.forEach { u ->
                        val wybrana = u.label == unit.label
                        if (wybrana) {
                            Button(onClick = {}) { Text(u.label) }
                        } else {
                            OutlinedButton(onClick = {
                                unit = u
                                // Przy zmianie jednostki liczba przestaje mieć
                                // sens: „70" gramów nie jest „70" kromkami.
                                text = if (u.gramsPerUnit == null) "100" else "1"
                            }) { Text(u.label) }
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (unit.gramsPerUnit == null) "gramy" else "ile") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Spacer(Modifier.height(10.dp))

                if (nutrients == null) {
                    Text(
                        "Podaj ilość.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("%.0f kcal".format(nutrients.kcal), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "%.0f g · B %.1f · W %.1f · T %.1f".format(
                            grams, nutrients.proteinG, nutrients.carbsG, nutrients.fatG
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { grams?.let(onAdd) },
                enabled = grams != null && grams > 0.0
            ) { Text("Dodaj do: ${meal.label.lowercase()}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ---- Szybki wpis ---------------------------------------------------------

/**
 * Wpis samych kalorii.
 *
 * Istnieje, bo obiad w restauracji nie ma kodu kreskowego ani znanego składu.
 * Makro jest opcjonalne i puste pole znaczy zero, nie brak danych - tu nie ma
 * czego nie wiedzieć, to Ty podajesz liczby.
 */
@Composable
internal fun QuickAddDialog(
    meal: Meal,
    onAdd: (name: String, kcal: Double, proteinG: Double, carbsG: Double, fatG: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val kcalValue = kcal.replace(',', '.').toDoubleOrNull()
    val ok = name.isNotBlank() && kcalValue != null && kcalValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Szybki wpis") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Co to było") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Makro nieobowiązkowe - bez niego wpis policzy się tylko do kalorii.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroField("B", protein, Modifier.weight(1f)) { protein = it }
                    MacroField("W", carbs, Modifier.weight(1f)) { carbs = it }
                    MacroField("T", fat, Modifier.weight(1f)) { fat = it }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = ok,
                onClick = {
                    onAdd(
                        name.trim(),
                        kcalValue ?: 0.0,
                        protein.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        carbs.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        fat.replace(',', '.').toDoubleOrNull() ?: 0.0
                    )
                }
            ) { Text("Dodaj do: ${meal.label.lowercase()}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    modifier: Modifier,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

// ---- Poprawianie wpisu ---------------------------------------------------

/**
 * Poprawienie albo usunięcie wpisu.
 *
 * Zmiana gramatury **przelicza zapisane wartości**, a nie sięga po produkt
 * do katalogu. Wpis ma własną migawkę makro i to ona jest tu prawdą: produkt
 * mógł się od tamtego dnia zmienić albo zniknąć, a poprawka gramatury nie
 * jest powodem, żeby wtorkowe śniadanie dostało dzisiejsze dane.
 */
@Composable
internal fun EntryEditDialog(
    entry: FoodLogEntry,
    onSave: (FoodLogEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val gramsMode = (entry.grams ?: 0.0) > 0.0
    var text by remember(entry.id) {
        mutableStateOf(
            if (gramsMode) fmtG(entry.grams!!) else fmtG(entry.nutrients.kcal)
        )
    }

    val value = text.replace(',', '.').toDoubleOrNull()
    val updated = value?.takeIf { it > 0.0 }?.let { v ->
        if (gramsMode) {
            entry.copy(
                grams = v,
                nutrients = entry.nutrients.scaled(v / entry.grams!!)
            )
        } else {
            entry.copy(nutrients = entry.nutrients.copy(kcal = v))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (gramsMode) "gramy" else "kcal") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                val n: Nutrients = updated?.nutrients ?: entry.nutrients
                Text("%.0f kcal".format(n.kcal), style = MaterialTheme.typography.titleLarge)
                Text(
                    "B %.1f · W %.1f · T %.1f".format(n.proteinG, n.carbsG, n.fatG),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = updated != null,
                onClick = { updated?.let(onSave) }
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) { Text("Usuń") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

// ---- Własny produkt ------------------------------------------------------

/**
 * Wpisanie produktu, którego nie ma w żadnej bazie.
 *
 * Otwiera się po skanie, który się nie rozwiązał: kodu nie ma w Open Food
 * Facts, brakuje mu danych albo nie było zasięgu. Kod kreskowy zostaje
 * zapisany razem z produktem, więc **drugi skan tego samego opakowania
 * znajdzie go już lokalnie** - to jest cały sens wpisywania go tutaj,
 * zamiast szybkim wpisem samych kalorii.
 *
 * Nazwa i marka bywają znane z Open Food Facts nawet wtedy, gdy wartości
 * odżywczych tam nie ma - wtedy przychodzą wypełnione, bo przepisywanie
 * nazwy z opakowania to najwięcej pisania w całej tej ścieżce.
 */
@Composable
internal fun OwnProductDialog(
    barcode: String?,
    initialName: String?,
    initialBrand: String?,
    onSave: (Food) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName.orEmpty()) }
    var brand by remember { mutableStateOf(initialBrand.orEmpty()) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    fun liczba(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    val kcalValue = liczba(kcal)
    val ok = name.isNotBlank() && kcalValue != null && kcalValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy produkt") },
        text = {
            Column {
                if (barcode != null) {
                    Text(
                        "kod $barcode - zapamiętamy go, więc następnym razem " +
                            "wystarczy zeskanować",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marka (opcjonalnie)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )

                Text(
                    "Wartości z etykiety, na 100 g. Węglowodany bez błonnika, " +
                        "tak jak podaje je polska etykieta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )

                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("kcal / 100 g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroField("B", protein, Modifier.weight(1f)) { protein = it }
                    MacroField("W", carbs, Modifier.weight(1f)) { carbs = it }
                    MacroField("T", fat, Modifier.weight(1f)) { fat = it }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = ok,
                onClick = {
                    onSave(
                        Food(
                            barcode = barcode?.takeIf { it.isNotBlank() },
                            name = name.trim(),
                            brand = brand.trim().takeIf { it.isNotBlank() },
                            per100 = Nutrients(
                                kcal = kcalValue ?: 0.0,
                                proteinG = liczba(protein) ?: 0.0,
                                carbsG = liczba(carbs) ?: 0.0,
                                fatG = liczba(fat) ?: 0.0
                            ),
                            source = FoodSource.OWN,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("Zapisz i dodaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

/** Liczba bez zbędnego zera po przecinku - „70", nie „70.0". */
private fun fmtG(v: Double): String =
    if (kotlin.math.abs(v - Math.round(v)) < 0.05) Math.round(v).toString()
    else "%.1f".format(v)
