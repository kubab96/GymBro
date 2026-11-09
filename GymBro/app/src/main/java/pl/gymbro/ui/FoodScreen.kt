package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.data.Food
import pl.gymbro.data.FoodLogEntry
import pl.gymbro.data.Meal
import pl.gymbro.data.Nutrients
import pl.gymbro.data.Recipe
import pl.gymbro.data.TdeeMethod
import pl.gymbro.data.todayEpochDay
import pl.gymbro.net.Barcodes
import pl.gymbro.net.OpenFoodFacts
import pl.gymbro.nutrition.NutritionRepos
import pl.gymbro.nutrition.NutritionReview
import pl.gymbro.nutrition.Portions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Zakładka Jedzenie: dziennik i zapotrzebowanie.
 *
 * Projekt i uzasadnienie liczb: `docs/kalorie.md`.
 *
 * Założenie, które rządzi tym ekranem: **wpis musi kosztować trzy dotknięcia.**
 * Dziennik jedzenia porzuca się nie dlatego, że jest nudny, ale dlatego, że
 * jeden posiłek to dwunaste kliknięcie i szukanie produktu, którego i tak
 * nie ma. Stąd kolejność na ekranie: najpierw to, co zjadłeś dzisiaj, zaraz
 * pod tym jedno pole szukania, a lista ostatnio jedzonych jako pierwsza
 * propozycja - bo jada się kilkadziesiąt produktów, nie trzydzieści tysięcy.
 *
 * Drugie założenie: **liczba na górze nie udaje pewniejszej, niż jest.**
 * Dopóki oszacowanie wydatku stoi na wzorze, ekran mówi to wprost, razem
 * z tym, czego brakuje, żeby zaczął liczyć z Twoich danych.
 */
@Composable
fun FoodScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repos = remember { NutritionRepos(context) }

    var day by remember { mutableStateOf(todayEpochDay()) }
    var today by remember { mutableStateOf<NutritionReview.Today?>(null) }

    // Niepusty posiłek znaczy „jesteśmy w widoku szukania i dodajemy do niego".
    var searchFor by remember { mutableStateOf<Meal?>(null) }
    var quickAddTo by remember { mutableStateOf<Meal?>(null) }
    var picking by remember { mutableStateOf<Pair<Food, Meal>?>(null) }
    var pickingLastGrams by remember { mutableStateOf<Double?>(null) }
    var editing by remember { mutableStateOf<FoodLogEntry?>(null) }
    var newProduct by remember { mutableStateOf<NewProduct?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    // Wlasne posilki: lista i edytor. Dwa osobne stany, nie jeden, bo
    // z edytora wraca sie do listy, a z listy do szukania - gdyby byl
    // jeden, zamkniecie edytora wyrzucaloby az do dziennika.
    var myMeals by remember { mutableStateOf(false) }
    var editingRecipe by remember { mutableStateOf<Recipe?>(null) }
    var recipeNew by remember { mutableStateOf(false) }

    suspend fun reload() {
        today = withContext(Dispatchers.IO) { NutritionReview.refresh(day, repos) }
    }

    /** Otwiera wybór porcji dla produktu, dociągając porcje i ostatnią gramaturę. */
    suspend fun openPortion(food: Food, meal: Meal) {
        val full = withContext(Dispatchers.IO) { repos.foods.byId(food.id) } ?: food
        pickingLastGrams = withContext(Dispatchers.IO) { repos.foods.lastGrams(food.id) }
        picking = full to meal
    }

    /**
     * Co zrobić z odczytanym kodem.
     *
     * Kolejność jest tu całą rzeczą i jest **offline najpierw**: katalog
     * lokalny, dopiero potem sieć. Drugi skan tego samego jogurtu nie rusza
     * już Open Food Facts, więc w sklepie bez zasięgu produkty, które już
     * raz skanowałeś, działają normalnie.
     */
    suspend fun handleCode(code: String, meal: Meal) {
        val local = withContext(Dispatchers.IO) { repos.foods.byBarcode(code) }
        if (local != null) {
            status = null
            openPortion(local, meal)
            return
        }

        status = "Szukam w Open Food Facts…"
        when (val lookup = withContext(Dispatchers.IO) { OpenFoodFacts.fetch(code) }) {
            is OpenFoodFacts.Lookup.Found -> {
                val id = withContext(Dispatchers.IO) { repos.foods.upsertFromSource(lookup.food) }
                val saved = withContext(Dispatchers.IO) { repos.foods.byId(id) }
                status = null
                if (saved != null) openPortion(saved, meal)
                else status = "Nie udało się zapisać produktu."
            }

            OpenFoodFacts.Lookup.NotFound -> {
                status = null
                newProduct = NewProduct(code, null, null, meal)
            }

            // Produkt jest w bazie, ale bez danych, które mają znaczenie.
            // Nazwa i marka idą do formularza, żeby nie przepisywać ich
            // z opakowania - to i tak najwięcej pisania w całej ścieżce.
            is OpenFoodFacts.Lookup.Incomplete -> {
                status = "W Open Food Facts brakuje danych: ${lookup.reason}."
                newProduct = NewProduct(code, lookup.name, lookup.brand, meal)
            }

            // Brak zasięgu w sklepie to nie powód, żeby nie dało się
            // zapisać posiłku - formularz otwiera się mimo wszystko.
            is OpenFoodFacts.Lookup.Failed -> {
                status = "${lookup.reason}. Możesz wpisać produkt ręcznie."
                newProduct = NewProduct(code, null, null, meal)
            }
        }
    }

    /**
     * Skanowanie **składnika dania**, nie wpisu do dziennika.
     *
     * Różni się od [handleCode] końcem: tam odczytany produkt otwiera okno
     * porcji i ląduje w dzienniku, a tutaj ma po prostu wrócić do edytora
     * dania. Dlatego zwraca produkt, zamiast ustawiać stan ekranu.
     *
     * Produktu, którego nie ma ani lokalnie, ani w Open Food Facts, **nie
     * otwieramy tu do wpisania ręcznego**. Formularz nowego produktu
     * prowadzi do dziennika i wyprowadziłby z edytora w połowie składania
     * dania. Zostaje komunikat i szukanie po nazwie - rzadki przypadek
     * kosztuje jedno przejście więcej, zamiast gubić całą robotę.
     */
    suspend fun scanIngredient(): Food? {
        val wynik = Barcodes.scan(context)
        val code = when (wynik) {
            is Barcodes.Scan.Code -> wynik.value
            // Zamkniecie skanera to decyzja, nie usterka - bez komunikatu.
            Barcodes.Scan.Cancelled -> return null
            is Barcodes.Scan.Failed -> { status = wynik.reason; return null }
        }

        // Offline najpierw, tak samo jak przy wpisie do dziennika.
        withContext(Dispatchers.IO) { repos.foods.byBarcode(code) }?.let {
            status = null
            return it
        }

        status = "Szukam w Open Food Facts…"
        return when (val lookup = withContext(Dispatchers.IO) { OpenFoodFacts.fetch(code) }) {
            is OpenFoodFacts.Lookup.Found -> {
                val id = withContext(Dispatchers.IO) { repos.foods.upsertFromSource(lookup.food) }
                status = null
                withContext(Dispatchers.IO) { repos.foods.byId(id) }
            }

            OpenFoodFacts.Lookup.NotFound -> {
                status = "Nie ma tego kodu w Open Food Facts - poszukaj po nazwie."
                null
            }

            is OpenFoodFacts.Lookup.Incomplete -> {
                status = "W Open Food Facts brakuje danych: ${lookup.reason}."
                null
            }

            is OpenFoodFacts.Lookup.Failed -> {
                status = "${lookup.reason}. Poszukaj po nazwie."
                null
            }
        }
    }

    fun scan(meal: Meal) {
        scope.launch {
            when (val wynik = Barcodes.scan(context)) {
                is Barcodes.Scan.Code -> Busy.track { handleCode(wynik.value, meal) }
                // Zamknięcie skanera to decyzja, nie usterka - bez komunikatu.
                Barcodes.Scan.Cancelled -> Unit
                is Barcodes.Scan.Failed -> status = wynik.reason
            }
        }
    }

    LaunchedEffect(day, Refresh.version) { Busy.track { reload() } }

    // Moduł skanera dociąga się przy pierwszym użyciu, a to trafia dokładnie
    // w moment, w którym stoisz przed półką. Prosimy o niego wcześniej.
    LaunchedEffect(Unit) { Barcodes.warmUp(context) }

    // Naciśnięcie otwartej zakładki wraca do dzisiejszego dnia i zwija
    // szukanie - tak jak każda inna zakładka zwija się do widoku głównego.
    TabReselectHandler {
        searchFor = null
        myMeals = false
        recipeNew = false
        editingRecipe = null
        day = todayEpochDay()
    }
    // Wstecz cofa o JEDEN ekran, nie do dziennika. Edytor wraca do listy
    // posilkow, lista do szukania, szukanie do dnia - inaczej pomylkowe
    // wejscie w edytor kosztowaloby przejscie calej drogi od nowa.
    val gdzies = searchFor != null || myMeals || recipeNew || editingRecipe != null
    ScreenBackHandler(enabled = gdzies) {
        when {
            recipeNew || editingRecipe != null -> { recipeNew = false; editingRecipe = null }
            myMeals -> myMeals = false
            else -> searchFor = null
        }
    }

    val meal = searchFor
    if (recipeNew || editingRecipe != null) {
        MealEditorView(
            repos = repos,
            startFrom = editingRecipe,
            status = status,
            onScan = { Busy.track { scanIngredient() } },
            onSave = { recipe ->
                scope.launch {
                    Busy.track {
                        withContext(Dispatchers.IO) { repos.recipes.save(recipe) }
                        recipeNew = false
                        editingRecipe = null
                        status = "Zapisano: ${recipe.name}."
                        // Przepis jest wierszem katalogu, wiec zmiana moze
                        // dotknac wpisow juz widocznych w dzienniku.
                        Refresh.bump()
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    Busy.track {
                        withContext(Dispatchers.IO) { repos.recipes.delete(id) }
                        recipeNew = false
                        editingRecipe = null
                        status = "Danie usunięte."
                        Refresh.bump()
                    }
                }
            },
            onBack = { recipeNew = false; editingRecipe = null }
        )
    } else if (myMeals) {
        MyMealsView(
            repos = repos,
            onEdit = { r -> if (r == null) recipeNew = true else editingRecipe = r },
            onBack = { myMeals = false }
        )
    } else if (meal != null) {
        FoodSearchView(
            meal = meal,
            repos = repos,
            // Porcje i ostatnia gramatura dociągane dopiero przy wyborze:
            // przy liście wyników byłoby to kilkadziesiąt zapytań na każde
            // wpisane słowo.
            onPick = { food -> scope.launch { openPortion(food, meal) } },
            onScan = { scan(meal) },
            onQuickAdd = { quickAddTo = meal },
            onMyMeals = { myMeals = true },
            onBack = { searchFor = null }
        )
    } else {
        DayView(
            today = today,
            day = day,
            status = status,
            onDay = { day = it; status = null },
            onAdd = { m -> status = null; searchFor = m },
            onEntry = { editing = it }
        )
    }

    picking?.let { (food, m) ->
        PortionDialog(
            food = food,
            meal = m,
            lastGrams = pickingLastGrams,
            onDismiss = { picking = null },
            onAdd = { grams ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repos.diary.add(Portions.entry(day, m, food, grams))
                    }
                    picking = null
                    searchFor = null
                    status = "Dodano: ${food.name}"
                    reload()
                }
            }
        )
    }

    quickAddTo?.let { m ->
        QuickAddDialog(
            meal = m,
            onDismiss = { quickAddTo = null },
            onAdd = { name, kcal, protein, carbs, fat ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        repos.diary.add(
                            Portions.quickEntry(day, m, name, kcal, protein, carbs, fat)
                        )
                    }
                    quickAddTo = null
                    searchFor = null
                    status = "Dodano: $name"
                    reload()
                }
            }
        )
    }

    editing?.let { entry ->
        EntryEditDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { repos.diary.update(updated) }
                    editing = null
                    status = "Poprawiono wpis"
                    reload()
                }
            },
            onDelete = {
                scope.launch {
                    withContext(Dispatchers.IO) { repos.diary.delete(entry.id) }
                    editing = null
                    status = "Usunięto wpis"
                    reload()
                }
            }
        )
    }

    newProduct?.let { nowy ->
        OwnProductDialog(
            barcode = nowy.barcode,
            initialName = nowy.name,
            initialBrand = nowy.brand,
            onDismiss = { newProduct = null },
            onSave = { food ->
                scope.launch {
                    // Zapisany produkt od razu prowadzi do wyboru porcji -
                    // wpisywanie go po to, żeby wrócić do pustego ekranu
                    // i szukać od nowa, byłoby karą za to, że czegoś nie ma
                    // w bazie.
                    val id = withContext(Dispatchers.IO) { repos.foods.insert(food) }
                    val saved = withContext(Dispatchers.IO) { repos.foods.byId(id) }
                    newProduct = null
                    status = null
                    if (saved != null) openPortion(saved, nowy.meal)
                }
            }
        )
    }
}

/**
 * Produkt do wpisania ręcznie, razem z tym, co już o nim wiadomo.
 *
 * Powstaje po skanie, którego nie udało się rozwiązać: kodu nie ma w Open
 * Food Facts, brakuje mu danych albo nie było zasięgu. [name] i [brand] bywają
 * znane nawet wtedy, gdy wartości odżywczych nie ma.
 */
private data class NewProduct(
    val barcode: String?,
    val name: String?,
    val brand: String?,
    val meal: Meal
)

// ---- Widok dnia ----------------------------------------------------------

@Composable
private fun DayView(
    today: NutritionReview.Today?,
    day: Long,
    status: String?,
    onDay: (Long) -> Unit,
    onAdd: (Meal) -> Unit,
    onEntry: (FoodLogEntry) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Jedzenie", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDay(day - 1) }) { Text("‹") }
                Text(dayLabel(day), style = MaterialTheme.typography.bodyMedium)
                // W przyszłość nie ma po co iść: jedzenia, którego jeszcze
                // nie było, nie da się zapisać.
                TextButton(
                    onClick = { onDay(day + 1) },
                    enabled = day < todayEpochDay()
                ) { Text("›") }
            }
        }

        SummaryCard(today)

        // Tu nie ma przyciskow dodawania - i to jest zmiana wzgledem
        // pierwszej wersji, nie przeoczenie.
        //
        // Stal tu rzad "Skanuj / Szukaj / Wpisz", ktory zgadywal posilek
        // z godziny. Zgadywanie bylo w porzadku o osmej rano, ale o
        // jedenastej juz nie, a poprawianie go po fakcie kosztuje wiecej
        // niz jedno dotkniecie zaoszczedzone na wejsciu. Teraz droga jest
        // jedna: dotknij posilku, do ktorego dopisujesz. Skanowanie
        // i wpisywanie czeka tam, na gorze ekranu szukania.
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Wszystkie posiłki, także puste. Pusty posiłek to miejsce do
        // dotknięcia, a nie brak treści - ukrywanie go kazałoby szukać,
        // gdzie dopisać kolację.
        val byMeal = today?.entries.orEmpty().groupBy { it.meal }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            Meal.entries.forEach { meal ->
                item(key = "posilek-${meal.name}") {
                    MealHeader(
                        meal = meal,
                        suma = today?.summary?.byMeal?.get(meal),
                        onAdd = { onAdd(meal) }
                    )
                }
                items(byMeal[meal].orEmpty(), key = { "wpis-${it.id}" }) { entry ->
                    EntryRow(entry) { onEntry(entry) }
                }
            }
        }
    }
}

/** Jeden wpis w dzienniku. Dotknięcie otwiera poprawianie i usuwanie. */
@Composable
private fun EntryRow(entry: FoodLogEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    entry.grams?.let { append("%.0f g · ".format(it)) }
                    append(
                        "B %.0f · W %.0f · T %.0f".format(
                            entry.nutrients.proteinG,
                            entry.nutrients.carbsG,
                            entry.nutrients.fatG
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "%.0f kcal".format(entry.nutrients.kcal),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Nagłówek posiłku z sumą kalorii i makro.
 *
 * Cały wiersz jest przyciskiem dodawania, nie tylko znak plus: posiłek bez
 * wpisów ma po sobie kilkadziesiąt pikseli wysokości, a celowanie w ikonę
 * wielkości paznokcia kciukiem jednej ręki jest dokładnie tym, czego ten
 * ekran ma nie wymagać.
 *
 * Makro pod nazwą, nie obok kalorii: linijka „Obiad · 820 kcal · B 45 · W 70
 * · T 30" nie mieści się na szerokości telefonu i obcina się w losowym
 * miejscu. Pod spodem, mniejszą czcionką, mieści się zawsze.
 *
 * Pusty posiłek dostaje sam plus. Wypisywanie mu „0 kcal · B 0 · W 0 · T 0"
 * byłoby czterema liczbami, które nic nie mówią, w miejscu, gdzie całą
 * treścią jest zaproszenie do dotknięcia.
 */
@Composable
private fun MealHeader(meal: Meal, suma: Nutrients?, onAdd: () -> Unit) {
    val pusty = suma == null || suma.kcal <= 0.0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(meal.label, style = MaterialTheme.typography.titleMedium)
            if (!pusty) {
                Text(
                    "B %.0f · W %.0f · T %.0f".format(
                        suma!!.proteinG,
                        suma.carbsG,
                        suma.fatG
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            if (pusty) "+" else "%.0f kcal".format(suma!!.kcal),
            style = MaterialTheme.typography.bodyMedium,
            color = if (pusty) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Karta podsumowania: kalorie, makro i skąd się wzięła liczba wydatku. */
@Composable
private fun SummaryCard(today: NutritionReview.Today?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            if (today == null) {
                Text("Wczytuję…", style = MaterialTheme.typography.titleMedium)
                return@Column
            }

            val target = today.target
            val eaten = today.summary.total

            if (target == null) {
                Text("%.0f kcal".format(eaten.kcal), fontSize = 36.sp)
                Text(
                    today.missingProfile ?: "Brak celu - uzupełnij profil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val remaining = today.remaining!!

            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.0f".format(eaten.kcal), fontSize = 36.sp)
                Text(
                    " / %.0f kcal".format(target.kcal),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                if (remaining.overKcal) {
                    "ponad cel o %.0f kcal".format(-remaining.kcal)
                } else {
                    "zostało %.0f kcal".format(remaining.kcal)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining.overKcal) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(Modifier.height(10.dp))
            ProgressBar(
                fraction = (eaten.kcal / target.kcal).toFloat(),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(12.dp))
            MacroRow("Białko", eaten.proteinG, target.proteinG, MaterialTheme.colorScheme.primary)
            MacroRow("Węgle", eaten.carbsG, target.carbsG, MaterialTheme.colorScheme.tertiary)
            MacroRow("Tłuszcz", eaten.fatG, target.fatG, MaterialTheme.colorScheme.secondary)

            today.tdee?.let { tdee ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "wydatek %.0f kcal %s".format(tdee.kcal, methodLabel(tdee.method)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    tdee.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Błonnik osobno, bo nie wchodzi do bilansu kalorii, a brak danych
            // u części wpisów trzeba pokazać wprost - inaczej suma czytałaby
            // się jak pełna.
            //
            // Przy pustym dniu linijki nie ma wcale. „Błonnik brak danych"
            // w dniu, w którym nic nie zjadłeś, nie jest informacją o braku
            // danych - jest szumem w jedynym miejscu, gdzie szum najbardziej
            // przeszkadza, bo to pierwsze, co widać rano.
            today.fiberTargetG?.takeIf { !today.summary.isEmpty }?.let { cel ->
                val b = eaten.fiberG
                val brak = today.summary.missing.fiber
                Text(
                    buildString {
                        append("błonnik ")
                        append(if (b == null) "brak danych" else "%.0f / %.0f g".format(b, cel))
                        if (brak > 0 && b != null) append(" (bez danych: $brak ${wpisy(brak)})")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, eaten: Double, target: Double, color: Color) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "%.0f / %.0f g".format(eaten, target),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(3.dp))
        ProgressBar(
            fraction = if (target > 0) (eaten / target).toFloat() else 0f,
            color = color
        )
    }
}

/**
 * Belka postępu rysowana z dwóch prostokątów.
 *
 * Ręcznie, a nie przez `LinearProgressIndicator`, z tego samego powodu co
 * pasek ładowania w `MainActivity`: sygnatura tamtego zmieniała się między
 * wersjami Material3 i wymusza wybór wariantu.
 *
 * Przekroczenie celu nie wylewa się za krawędź - belka staje się czerwona
 * i pełna. Pasek dłuższy niż tło czytałby się jak usterka rysowania.
 */
@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val over = fraction > 1.0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(if (over) MaterialTheme.colorScheme.error else color)
        )
    }
}

private fun methodLabel(method: TdeeMethod): String = when (method) {
    TdeeMethod.FORMULA -> "(ze wzoru)"
    TdeeMethod.BLENDED -> "(wzór i pomiar)"
    TdeeMethod.MEASURED -> "(z pomiaru)"
}

private fun wpisy(n: Int): String = when {
    n == 1 -> "wpis"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "wpisy"
    else -> "wpisów"
}

// Bylo tu defaultMeal(): posilek zgadywany z godziny, pod przyciski
// "Skanuj / Szukaj / Wpisz" stojace nad lista. Przyciski przeniosly sie do
// wnetrza posilku, wiec nie ma juz czego zgadywac - dopisujesz tam, gdzie
// dotknales. Zgadywanie bylo zreszta slabsze, niz wygladalo: trafialo
// o osmej rano i mylilo sie o jedenastej, a poprawka kosztowala wiecej niz
// jedno dotkniecie, ktore mialo oszczedzic.

private fun dayLabel(day: Long): String {
    val dzis = todayEpochDay()
    return when (day) {
        dzis -> "dziś"
        dzis - 1 -> "wczoraj"
        dzis - 2 -> "przedwczoraj"
        else -> LocalDate.ofEpochDay(day)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}
