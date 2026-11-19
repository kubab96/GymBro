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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import pl.gymbro.data.roundGram
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
/**
 * Produkt zaznaczony na liście, z ilością, z jaką pójdzie do dziennika.
 *
 * Zaznaczanie jest po to, żeby posiłek z pięciu rzeczy nie wymagał pięciu
 * wejść w produkt i pięciu okien porcji. Ilość podstawia się sama (ostatnia
 * gramatura albo pierwsza porcja), a gdy trzeba inną - dotknięcie produktu
 * ją zmienia.
 */
internal data class Picked(val food: Food, val grams: Double, val label: String) {
    val kcal: Double get() = Portions.nutrientsFor(food, grams).kcal
}

@Composable
internal fun FoodSearchView(
    meal: Meal,
    repos: NutritionRepos,
    onPick: (Food) -> Unit,
    onScan: () -> Unit,
    onQuickAdd: () -> Unit,
    onMyMeals: () -> Unit,
    onBack: () -> Unit,
    selected: Map<Long, Picked> = emptyMap(),
    onToggle: (Food) -> Unit = {},
    onAddSelected: () -> Unit = {},
    onClearSelected: () -> Unit = {}
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

        // Dwa wyjścia na WIERZCHU, nad polem szukania.
        //
        // Stały niżej, pod listą wyników, i wyglądało to porządnie - do
        // momentu wejścia na ekran. Klawiatura podnosi się tu sama i zasłania
        // dolne kilkaset pikseli, więc oba przyciski były niewidoczne przez
        // cały czas, kiedy się ich potrzebuje. Zrzut z telefonu nie zostawiał
        // tu wątpliwości: pod listą produktów zaczynała się od razu
        // klawiatura.
        //
        // Produkt, którego nie ma w katalogu, jest najczęstszym powodem
        // porzucenia dziennika - te przyciski nie mogą się chować ani za
        // brakiem wyników, ani za klawiaturą.
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onScan, modifier = Modifier.weight(1f)) { Text("Skanuj") }
            OutlinedButton(
                onClick = onMyMeals,
                modifier = Modifier.weight(1f)
            ) { Text("Dania") }
            OutlinedButton(
                onClick = onQuickAdd,
                modifier = Modifier.weight(1f)
            ) { Text("Kalorie") }
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

        // Zaznaczone - nad listą, nie pod nią. Pod listą przycisk leżałby
        // pod klawiaturą, która tu podnosi się sama.
        if (selected.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onAddSelected, modifier = Modifier.weight(1f)) {
                    Text(
                        "Dodaj " + plural(selected.size, "produkt", "produkty", "produktów") +
                            " · %.0f kcal".format(selected.values.sumOf { it.kcal })
                    )
                }
                TextButton(onClick = onClearSelected) { Text("Wyczyść") }
            }
        }

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
                FoodRow(
                    food = food,
                    picked = selected[food.id],
                    onClick = { onPick(food) },
                    onToggle = { onToggle(food) }
                )
            }
        }

    }
}

/**
 * Wiersz produktu: dotknięcie otwiera porcje, pole z prawej zaznacza.
 *
 * Zaznaczony pokazuje ilość, z jaką pójdzie do dziennika - bez tego
 * dodawało się "coś" i dopiero w dzienniku było widać, ile.
 */
@Composable
private fun FoodRow(food: Food, picked: Picked?, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(food.name, style = MaterialTheme.typography.bodyLarge)
            if (picked != null) {
                Text(
                    picked.label + " · %.0f kcal".format(picked.kcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    buildString {
                        food.brand?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                        append(sourceLabel(food.source))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "%.0f kcal/100 g".format(food.per100.kcal),
            style = MaterialTheme.typography.bodySmall
        )
        Checkbox(checked = picked != null, onCheckedChange = { onToggle() })
    }
}

private fun sourceLabel(source: FoodSource): String = when (source) {
    FoodSource.SEED -> "produkt podstawowy"
    FoodSource.OWN -> "mój produkt"
    FoodSource.RECIPE -> "przepis"
    FoodSource.SHOP -> "tabela sklepu"
    FoodSource.OFF -> "Open Food Facts"
}

// ---- Ilość ---------------------------------------------------------------

/** Jednostka, w której podaje się ilość. [gramsPerUnit] null = wpisujesz gramy. */
internal data class AmountUnit(val label: String, val gramsPerUnit: Double?)

/**
 * Jednostki produktu: gramy, porcje nazwane, porcja producenta, opakowanie.
 *
 * Jedna lista dla dodawania i dla poprawiania wpisu. Poprawka znała dotąd
 * tylko gramy - wpis dodany jako "2 kromki" dało się zmienić wyłącznie
 * przeliczając kromki na gramy w głowie.
 */
internal fun amountUnits(food: Food): List<AmountUnit> = buildList {
    add(AmountUnit("gramy", null))
    food.portions.forEach { add(AmountUnit(it.label, it.grams)) }
    food.servingG?.let { add(AmountUnit("porcja", it)) }
    food.packageG?.let { add(AmountUnit("opakowanie", it)) }
}

/**
 * Jednostka, w której najprościej opisać gramaturę: "2 kromki" zamiast "70 g".
 *
 * Porcja pasuje, gdy gramatura to jej wielokrotność co pół - tak, jak
 * wpisuje się porcje w oknie dodawania. Inaczej zostają gramy, bo "1,37
 * kromki" jest gorsze niż "48 g".
 */
internal fun bestUnitFor(units: List<AmountUnit>, grams: Double): Pair<AmountUnit, Double> {
    units.forEach { u ->
        val g = u.gramsPerUnit ?: return@forEach
        if (g <= 0.0) return@forEach
        val ile = grams / g
        val polowki = Math.round(ile * 2) / 2.0
        if (polowki in 0.5..20.0 && kotlin.math.abs(ile - polowki) * g < 0.6) return u to polowki
    }
    return units.first() to grams
}

/** Ilość słowami - "2 × kromka · 70 g" albo "180 g". */
internal fun amountLabel(units: List<AmountUnit>, grams: Double): String {
    val (u, ile) = bestUnitFor(units, grams)
    return if (u.gramsPerUnit == null) "${fmtG(grams)} g"
    else "${fmtG(ile)} × ${u.label} · ${fmtG(grams)} g"
}

/**
 * Wybór jednostki i pole na liczbę - wspólne dla dodawania i poprawki.
 *
 * Przy zmianie jednostki liczba przestaje mieć sens ("70" gramów to nie
 * "70" kromek), więc wraca do jednej sztuki albo stu gramów.
 */
@Composable
private fun AmountInput(
    units: List<AmountUnit>,
    unit: AmountUnit,
    text: String,
    onUnit: (AmountUnit) -> Unit,
    onText: (String) -> Unit
) {
    if (units.size > 1) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            units.forEach { u ->
                if (u.label == unit.label) {
                    Button(onClick = {}) { Text(u.label) }
                } else {
                    OutlinedButton(onClick = {
                        onUnit(u)
                        onText(if (u.gramsPerUnit == null) "100" else "1")
                    }) { Text(u.label) }
                }
            }
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text(if (unit.gramsPerUnit == null) "gramy" else "ile") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )
}

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
    onDismiss: () -> Unit,
    /** Ilość już wybrana - przy zmianie ilości zaznaczonego produktu. */
    startGrams: Double? = null,
    /** Napis na przycisku, gdy okno nie dodaje, tylko ustawia ilość. */
    confirmLabel: String? = null
) {
    val units = remember(food.id) { amountUnits(food) }

    // Ilość już wybrana - w tej jednostce, w której najprościej ją opisać.
    // Inaczej gramy, gdy wiadomo, ile wzięto ostatnio; w przeciwnym razie
    // pierwsza porcja nazwana, bo „dwie kromki" jest łatwiejsze niż „70 g".
    val start = remember(food.id) { startGrams?.let { bestUnitFor(units, it) } }
    var unit by remember(food.id) {
        mutableStateOf(
            start?.first
                ?: if (lastGrams != null || units.size == 1) units.first() else units[1]
        )
    }
    var text by remember(food.id) {
        mutableStateOf(
            when {
                start != null -> fmtG(start.second)
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

                AmountInput(units, unit, text, onUnit = { unit = it }, onText = { text = it })

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
            ) { Text(confirmLabel ?: "Dodaj do: ${meal.label.lowercase()}") }
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
    /**
     * Produkt z katalogu - tylko po to, żeby znać jego porcje. Makro liczy
     * się dalej z migawki wpisu. Null, gdy produktu już nie ma albo wpis
     * powstał bez niego.
     */
    food: Food?,
    onSave: (FoodLogEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val gramsMode = (entry.grams ?: 0.0) > 0.0
    val units = remember(entry.id, food?.id) {
        if (gramsMode && food != null) amountUnits(food) else listOf(AmountUnit("gramy", null))
    }
    // Wpis dodany jako "2 kromki" wraca jako dwie kromki, nie jako 70 g.
    val start = remember(entry.id, food?.id) {
        if (gramsMode) bestUnitFor(units, entry.grams!!) else null
    }
    var unit by remember(entry.id, food?.id) { mutableStateOf(start?.first ?: units.first()) }
    var text by remember(entry.id, food?.id) {
        mutableStateOf(
            if (start != null) fmtG(start.second) else fmtG(entry.nutrients.kcal)
        )
    }

    val value = text.replace(',', '.').toDoubleOrNull()
    val updated = value?.takeIf { it > 0.0 }?.let { v ->
        if (gramsMode) {
            val g = roundGram(v * (unit.gramsPerUnit ?: 1.0))
            entry.copy(
                grams = g,
                nutrients = entry.nutrients.scaled(g / entry.grams!!)
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
                if (gramsMode) {
                    AmountInput(units, unit, text, onUnit = { unit = it }, onText = { text = it })
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("kcal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                val n: Nutrients = updated?.nutrients ?: entry.nutrients
                Text("%.0f kcal".format(n.kcal), style = MaterialTheme.typography.titleLarge)
                Text(
                    buildString {
                        if (gramsMode) append("%.0f g · ".format(updated?.grams ?: entry.grams))
                        append("B %.1f · W %.1f · T %.1f".format(n.proteinG, n.carbsG, n.fatG))
                    },
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
    var serving by remember { mutableStateOf("") }
    var pack by remember { mutableStateOf("") }

    fun liczba(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    val kcalValue = liczba(kcal)
    val ok = name.isNotBlank() && kcalValue != null && kcalValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy produkt") },
        text = {
            // Przewijane: z polami porcji okno jest wyzsze niz to, co zostaje
            // nad klawiatura.
            Column(Modifier.verticalScroll(rememberScrollState())) {
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

                // Porcja z etykiety. Bez niej zeskanowana zupka 70 g dawala sie
                // wpisac tylko w gramach - a na opakowaniu stoi "1 porcja",
                // nie waga, i za kazdym razem trzeba bylo ja przepisywac.
                Text(
                    "Ile waży porcja i całe opakowanie - nieobowiązkowe. Potem " +
                        "wystarczy wybrać \"1 porcja\" zamiast wpisywać gramy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MacroField("porcja, g", serving, Modifier.weight(1f)) { serving = it }
                    MacroField("opakowanie, g", pack, Modifier.weight(1f)) { pack = it }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = ok,
                onClick = {
                    onSave(
                        Food(
                            servingG = liczba(serving)?.takeIf { it > 0.0 },
                            packageG = liczba(pack)?.takeIf { it > 0.0 },
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
