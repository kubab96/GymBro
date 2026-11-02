package pl.gymbro.nutrition

import pl.gymbro.data.Food
import pl.gymbro.data.FoodSource
import pl.gymbro.data.Nutrients
import pl.gymbro.data.Recipe
import pl.gymbro.data.RecipeItem
import pl.gymbro.data.roundGram

/**
 * Przepis przeliczony na produkt.
 *
 * Projekt: `docs/kalorie.md`, rozdział 7.
 *
 * Cała trudność siedzi w jednej liczbie: **wadze po ugotowaniu**. 100 g
 * suchego ryżu to około 250 g ugotowanego, więc „100 g ryżu" w dzienniku
 * znaczy dwie zupełnie różne rzeczy i jedna z nich jest dwuipółkrotnie
 * większa od drugiej. Składniki waży się na surowo, bo tak je kupujesz;
 * danie waży się po ugotowaniu, bo tak je nakładasz.
 */
object Recipes {

    /** Suma gramatury składników - waga surowa. */
    fun rawWeight(items: List<RecipeItem>): Double =
        roundGram(items.sumOf { it.grams })

    /** Wartości odżywcze całego dania, zsumowane ze składników. */
    fun totals(items: List<RecipeItem>): Nutrients =
        items.fold(Nutrients.ZERO) { acc, item ->
            acc + item.per100.scaled(item.grams / 100.0)
        }

    /**
     * Waga, do której odnosimy wartości na 100 g.
     *
     * Podana waga po ugotowaniu, a gdy jej nie ma - suma składników. Brak
     * jest poprawnym stanem: sałatka ani koktajl nie zmieniają wagi, więc
     * wymuszanie tam ważenia gotowego dania byłoby uprzykrzaniem.
     */
    fun effectiveWeight(recipe: Recipe): Double =
        recipe.cookedG?.takeIf { it > 0.0 } ?: rawWeight(recipe.items)

    /**
     * Wartości na 100 g gotowego dania.
     *
     * null, gdy przepis jest pusty albo waga wychodzi zerowa - wtedy nie ma
     * czego dzielić i lepiej nie mieć wyniku niż mieć nieskończoność.
     */
    fun per100(recipe: Recipe): Nutrients? {
        if (recipe.items.isEmpty()) return null
        val weight = effectiveWeight(recipe)
        if (weight <= 0.0) return null
        return totals(recipe.items).scaled(100.0 / weight)
    }

    /**
     * Przepis jako wiersz katalogu.
     *
     * Dzięki temu wpisanie przepisu do dziennika jest tym samym, co wpisanie
     * produktu: jedna ścieżka zamiast dwóch do utrzymania. Wiersz przelicza
     * się przy każdym zapisie przepisu.
     *
     * [Food.packageG] ustawione na wagę gotowego dania nie jest sztuczką:
     * „całe opakowanie" to tutaj „całe danie", a zjedzenie całego garnka
     * jednym wpisem to najczęstszy przypadek przy gotowaniu na jeden raz.
     */
    fun asFood(recipe: Recipe): Food? {
        val per100 = per100(recipe) ?: return null
        return Food(
            id = recipe.foodId ?: 0,
            barcode = null,
            name = recipe.name,
            brand = null,
            per100 = per100,
            servingG = null,
            packageG = effectiveWeight(recipe),
            source = FoodSource.RECIPE
        )
    }
}
