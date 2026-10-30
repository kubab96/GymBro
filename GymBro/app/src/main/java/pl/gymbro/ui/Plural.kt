package pl.gymbro.ui

/**
 * Polska odmiana rzeczownika przez liczebnik.
 *
 * Polszczyzna ma trzy formy, nie dwie, więc angielskie „1 day / N days" tu nie
 * wystarcza. „1 dni VO2max" czyta się jak usterka, a komunikat po
 * synchronizacji jest jedynym miejscem, w którym widać, że import zadziałał -
 * szkoda psuć go literówką gramatyczną.
 *
 * @param one forma dla 1: dzień, noc, trening
 * @param few forma dla 2-4: dni, noce, treningi
 * @param many forma dla 0, 5 i więcej: dni, nocy, treningów
 */
fun plural(n: Int, one: String, few: String, many: String): String {
    val form = when {
        n == 1 -> one
        // Nastolatki są wyjątkiem: 12, 13, 14 biorą formę mnogą dopełniaczową,
        // choć kończą się na 2, 3, 4.
        n % 100 in 12..14 -> many
        n % 10 in 2..4 -> few
        else -> many
    }
    return "$n $form"
}
