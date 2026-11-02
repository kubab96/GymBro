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
import pl.gymbro.data.TdeeMethod
import pl.gymbro.data.todayEpochDay
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
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        today = withContext(Dispatchers.IO) { NutritionReview.refresh(day, repos) }
    }

    LaunchedEffect(day, Refresh.version) { Busy.track { reload() } }

    // Naciśnięcie otwartej zakładki wraca do dzisiejszego dnia i zwija
    // szukanie - tak jak każda inna zakładka zwija się do widoku głównego.
    TabReselectHandler {
        searchFor = null
        day = todayEpochDay()
    }
    ScreenBackHandler(enabled = searchFor != null) { searchFor = null }

    val meal = searchFor
    if (meal != null) {
        FoodSearchView(
            meal = meal,
            repos = repos,
            onPick = { food ->
                scope.launch {
                    // Porcje i ostatnia gramatura dociągane dopiero tutaj:
                    // przy liście wyników byłoby to kilkadziesiąt zapytań
                    // na każde wpisane słowo.
                    val full = withContext(Dispatchers.IO) { repos.foods.byId(food.id) } ?: food
                    pickingLastGrams = withContext(Dispatchers.IO) {
                        repos.foods.lastGrams(food.id)
                    }
                    picking = full to meal
                }
            },
            onQuickAdd = { quickAddTo = meal },
            onBack = { searchFor = null }
        )
    } else {
        DayView(
            today = today,
            day = day,
            status = status,
            onDay = { day = it; status = null },
            onAdd = { m -> status = null; searchFor = m },
            onQuickAdd = { m -> status = null; quickAddTo = m },
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
}

// ---- Widok dnia ----------------------------------------------------------

@Composable
private fun DayView(
    today: NutritionReview.Today?,
    day: Long,
    status: String?,
    onDay: (Long) -> Unit,
    onAdd: (Meal) -> Unit,
    onQuickAdd: (Meal) -> Unit,
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

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Posiłek podstawiany z godziny, nie stały. O ósmej rano chodzi
            // o śniadanie i wybieranie go z listy przy każdym wpisie byłoby
            // dokładnie tym dodatkowym dotknięciem, którego tu unikamy.
            Button(
                onClick = { onAdd(defaultMeal()) },
                modifier = Modifier.weight(1f)
            ) { Text("Szukaj produktu") }
            OutlinedButton(
                onClick = { onQuickAdd(defaultMeal()) },
                modifier = Modifier.weight(1f)
            ) { Text("Szybki wpis") }
        }

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
                        kcal = today?.summary?.byMeal?.get(meal)?.kcal,
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
 * Nagłówek posiłku z sumą kalorii.
 *
 * Cały wiersz jest przyciskiem dodawania, nie tylko znak plus: posiłek bez
 * wpisów ma po sobie kilkadziesiąt pikseli wysokości, a celowanie w ikonę
 * wielkości paznokcia kciukiem jednej ręki jest dokładnie tym, czego ten
 * ekran ma nie wymagać.
 */
@Composable
private fun MealHeader(meal: Meal, kcal: Double?, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(meal.label, style = MaterialTheme.typography.titleMedium)
        Text(
            if (kcal == null || kcal <= 0.0) "+" else "%.0f kcal".format(kcal),
            style = MaterialTheme.typography.bodyMedium,
            color = if (kcal == null || kcal <= 0.0) {
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

/**
 * Posiłek, do którego najprawdopodobniej dopisujesz.
 *
 * Godziny są konwencją, nie badaniem - ale każda konwencja jest tu lepsza
 * od stałej wartości, bo trafia w kilku przypadkach z pięciu, a pomyłkę
 * poprawia się jednym dotknięciem w nagłówek właściwego posiłku.
 */
private fun defaultMeal(): Meal = when (java.time.LocalTime.now().hour) {
    in 0..9 -> Meal.BREAKFAST
    in 10..11 -> Meal.SECOND_BREAKFAST
    in 12..15 -> Meal.LUNCH
    in 16..17 -> Meal.AFTERNOON
    in 18..21 -> Meal.DINNER
    else -> Meal.SNACKS
}

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
