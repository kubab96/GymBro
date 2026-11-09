package pl.gymbro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Food
import pl.gymbro.data.Recipe
import pl.gymbro.data.RecipeItem
import pl.gymbro.nutrition.NutritionRepos
import pl.gymbro.nutrition.Recipes

/**
 * Własne dania - całe posiłki złożone z produktów.
 *
 * Projekt: `docs/kalorie.md`, rozdział 7.
 *
 * Danie złożone z kilku produktów wpisuje się raz, a potem jada tygodniami.
 * Bez tego owsianka z pięcioma dodatkami to pięć wpisów przy każdym
 * śniadaniu - i to jest ta liczba dotknięć, przy której dziennik się
 * porzuca.
 *
 * Zapisane danie **nie jest osobnym bytem w dzienniku**: trafia do
 * katalogu jako zwykły wiersz (`FoodSource.RECIPE`) i stamtąd wchodzi do
 * dziennika tą samą drogą, co jogurt z kodem kreskowym. Dzięki temu nie ma
 * drugiej ścieżki do utrzymania - patrz [Recipes.asFood].
 */
@Composable
internal fun MyMealsView(
    repos: NutritionRepos,
    onEdit: (Recipe?) -> Unit,
    onBack: () -> Unit
) {
    var lista by remember { mutableStateOf<List<Recipe>>(emptyList()) }

    // Skladniki dociagane tutaj, razem z lista, a nie przy rysowaniu
    // wiersza. Przepisow jest kilkanascie, nie tysiace, wiec jedno wejscie
    // na watek bazy wystarcza - a odczyt w remember() wewnatrz wiersza
    // robilby go na watku rysujacym.
    LaunchedEffect(Unit) {
        lista = withContext(Dispatchers.IO) {
            repos.recipes.all().map { it.copy(items = repos.recipes.items(it.id)) }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Moje dania", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Zamknij") }
        }

        Button(
            onClick = { onEdit(null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("Nowe danie") }

        if (lista.isEmpty()) {
            Text(
                "Nie masz jeszcze własnych dań. Złóż jedno z produktów, " +
                    "a potem dopisuj go jednym dotknięciem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(lista, key = { "posilek-${it.id}" }) { recipe ->
                MealRow(recipe) { onEdit(recipe) }
            }
        }
    }
}

@Composable
private fun MealRow(recipe: Recipe, onClick: () -> Unit) {
    val per100 = Recipes.per100(recipe)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${recipe.items.size} ${skladnikow(recipe.items.size)} · " +
                    "%.0f g".format(Recipes.effectiveWeight(recipe)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (per100 == null) "-" else "%.0f kcal/100 g".format(per100.kcal),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Składanie dania.
 *
 * Waga po ugotowaniu jest tu polem osobnym i **celowo pustym domyślnie**.
 * Sałatka ani koktajl nie zmieniają wagi, więc wymuszanie tam ważenia
 * gotowego dania byłoby uprzykrzaniem; ryż i makaron bez tej liczby kłamią
 * o połowę, bo 100 g suchego ryżu to około 250 g ugotowanego. Puste pole
 * znaczy „suma składników" - patrz [Recipes.effectiveWeight].
 */
@Composable
internal fun MealEditorView(
    repos: NutritionRepos,
    startFrom: Recipe?,
    status: String?,
    onScan: suspend () -> Food?,
    onSave: (Recipe) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit
) {
    var nazwa by remember { mutableStateOf(startFrom?.name.orEmpty()) }
    var porcje by remember {
        mutableStateOf(startFrom?.portions?.takeIf { it > 0 }?.toString().orEmpty())
    }
    val skladniki = remember { startFrom?.items.orEmpty().toMutableStateList() }
    var szukanie by remember { mutableStateOf(false) }

    val podglad = Recipe(
        id = startFrom?.id ?: 0,
        name = nazwa,
        cookedG = startFrom?.cookedG,
        portions = porcje.trim().toIntOrNull()?.takeIf { it > 0 },
        foodId = startFrom?.foodId,
        items = skladniki.toList()
    )
    val per100 = Recipes.per100(podglad)
    val calosc = Recipes.totals(skladniki.toList())

    // imePadding, bo "Zapisz" stoi na dole, a klawiatura jest tu podniesiona
    // przez wiekszosc czasu - nazwa, gramatura i szukanie skladnika to trzy
    // pola tekstowe pod rzad. Bez tego przycisk konczacy cala robote jest
    // niewidoczny dokladnie wtedy, kiedy sie go potrzebuje; zrzut z telefonu
    // pokazal go schowanego za klawiatura razem z podsumowaniem dania.
    //
    // Dziala, bo MainActivity wola enableEdgeToEdge() - bez tego okno samo
    // przesuwa zawartosc i te wstawki sa zerowe.
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (startFrom == null) "Nowe danie" else "Danie",
                style = MaterialTheme.typography.headlineSmall
            )
            TextButton(onClick = onBack) { Text("Zamknij") }
        }

        // Wybieranie skladnika zajmuje CALY ekran - nazwa i waga dania
        // znikaja na ten czas.
        //
        // Stalo to nizej, pod obydwoma polami, i lista produktow miescila
        // wtedy dwie pozycje. Rachunek jest prosty: klawiatura zabiera
        // okolo 1150 pikseli z 2400, a tytul plus dwa pola plus naglowek
        // i wyszukiwarka zjadaja z reszty jeszcze 800. Na liste zostawalo
        // tyle, co nic - a przy wybieraniu produktu nazwa dania i tak nie
        // jest do niczego potrzebna.
        if (szukanie) {
            SkladnikPicker(
                repos = repos,
                status = status,
                onScan = onScan,
                onPick = { food, gramy ->
                    skladniki += RecipeItem(
                        foodId = food.id,
                        grams = gramy,
                        foodName = food.name,
                        per100 = food.per100
                    )
                    szukanie = false
                },
                onBack = { szukanie = false }
            )
            return@Column
        }

        OutlinedTextField(
            value = nazwa,
            onValueChange = { nazwa = it },
            label = { Text("Nazwa") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )

        // "Na ile porcji", nie "waga po ugotowaniu".
        //
        // Pytanie o wage gotowego dania brzmi sensownie, dopoki nie stanie sie
        // nad garnkiem: trzeba go zwazyc, odjac wage garnka i zrobic to przed
        // nalozeniem. "Wychodza z tego cztery obiady" wie sie od razu, bez
        // wagi kuchennej. Porcja to waga dania podzielona przez te liczbe -
        // patrz [Recipes.portionWeight].
        OutlinedTextField(
            value = porcje,
            onValueChange = { porcje = it.filter(Char::isDigit) },
            label = { Text("Na ile porcji (opcjonalnie)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )

        OutlinedButton(
            onClick = { szukanie = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Dodaj składnik") }

        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(skladniki.size, key = { "skladnik-$it" }) { i ->
                val item = skladniki[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.foodName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "%.0f g · %.0f kcal".format(
                                item.grams,
                                item.per100.scaled(item.grams / 100.0).kcal
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { skladniki.removeAt(i) }) { Text("Usuń") }
                }
            }
        }

        if (skladniki.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Całe danie: %.0f kcal · B %.0f · W %.0f · T %.0f".format(
                            calosc.kcal, calosc.proteinG, calosc.carbsG, calosc.fatG
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Porcja na wierzchu, nad wartosciami na sto gramow:
                    // jesli podzieliles danie, to wlasnie porcja jest tym,
                    // co faktycznie nakladasz, a sto gramow to przelicznik.
                    Recipes.portionWeight(podglad)?.let { waga ->
                        val porcja = per100?.scaled(waga / 100.0)
                        if (porcja != null) {
                            Text(
                                "Porcja (%.0f g): %.0f kcal · B %.0f · W %.0f · T %.0f".format(
                                    waga, porcja.kcal, porcja.proteinG,
                                    porcja.carbsG, porcja.fatG
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    per100?.let {
                        Text(
                            "Na 100 g: %.0f kcal · B %.0f · W %.0f · T %.0f".format(
                                it.kcal, it.proteinG, it.carbsG, it.fatG
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zapis zablokowany bez nazwy i bez skladnikow, bo jedno i drugie
            // daje wiersz, ktorego nie da sie potem znalezc ani policzyc.
            Button(
                onClick = { onSave(podglad.copy(name = nazwa.trim())) },
                enabled = nazwa.isNotBlank() && skladniki.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Zapisz") }

            if (startFrom != null && startFrom.id > 0) {
                OutlinedButton(
                    onClick = { onDelete(startFrom.id) },
                    modifier = Modifier.weight(1f)
                ) { Text("Usuń") }
            }
        }
    }
}

/**
 * Wybór składnika: produkt i jego gramatura.
 *
 * Osobna, mniejsza wyszukiwarka zamiast [FoodSearchView]: tamta zna posiłek
 * dnia, skanowanie i szybki wpis, a tutaj żadna z tych rzeczy nie ma sensu -
 * dokładasz pozycję do dania, nie do dziennika.
 */
@Composable
private fun ColumnScope.SkladnikPicker(
    repos: NutritionRepos,
    status: String?,
    onScan: suspend () -> Food?,
    onPick: (Food, Double) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var wyniki by remember { mutableStateOf<List<Food>>(emptyList()) }
    var ostatnie by remember { mutableStateOf<List<Food>>(emptyList()) }
    var wybrany by remember { mutableStateOf<Food?>(null) }
    var gramy by remember { mutableStateOf("100") }

    LaunchedEffect(Unit) {
        ostatnie = withContext(Dispatchers.IO) { repos.foods.recent(20) }
    }

    // To samo odczekanie, co przy szukaniu do dziennika - patrz
    // [FoodSearchView]. Bez niego kazda litera to zapytanie po katalogu.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            wyniki = emptyList()
            return@LaunchedEffect
        }
        delay(180)
        wyniki = withContext(Dispatchers.IO) { repos.foods.search(query, 30) }
    }

    val wybor = wybrany
    if (wybor != null) {
        Text(wybor.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "%.0f kcal/100 g".format(wybor.per100.kcal),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = gramy,
            onValueChange = { gramy = it },
            label = { Text("Ile gramów") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { wybrany = null }) { Text("Wróć") }
            Button(
                onClick = { gramy.liczba()?.let { onPick(wybor, it) } },
                enabled = (gramy.liczba() ?: 0.0) > 0.0,
                modifier = Modifier.weight(1f)
            ) { Text("Dodaj") }
        }
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Składnik", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack) { Text("Anuluj") }
    }

    // Skanowanie takze tutaj, nie tylko przy wpisie do dziennika.
    //
    // Danie sklada sie glownie z rzeczy, ktore stoja w szafce w opakowaniu -
    // platki, jogurt, maslo orzechowe. Szukanie ich po nazwie znaczy
    // zgadywanie, jak producent je nazwal, a kod kreskowy lezy na stole obok.
    Button(
        onClick = { scope.launch { onScan()?.let { wybrany = it } } },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("Skanuj kod") }

    status?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Szukaj produktu") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )

    // Lista bierze to, co zostalo, zamiast stalej wysokosci.
    //
    // Mialo tu heightIn(max = 420.dp) i wygladalo dobrze do momentu, w ktorym
    // imePadding() podnioslo caly ekran nad klawiature: na liscie zostawaly
    // dwie pozycje, a reszta miejsca stala pusta pod spodem. Dlatego ta
    // funkcja jest rozszerzeniem ColumnScope - inaczej weight() nie istnieje.
    val lista = if (query.isBlank()) ostatnie else wyniki
    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        items(lista, key = { "skladnik-produkt-${it.id}" }) { food ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { wybrany = food }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "%.0f kcal".format(food.per100.kcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Liczba z pola tekstowego; przecinek dziesiętny jak kropka. */
private fun String.liczba(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }

private fun skladnikow(n: Int): String = when {
    n == 1 -> "składnik"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "składniki"
    else -> "składników"
}
