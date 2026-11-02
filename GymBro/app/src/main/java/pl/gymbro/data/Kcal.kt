package pl.gymbro.data

/**
 * Zaokrąglanie wartości odżywczych - to samo, co [roundKg] dla ciężarów.
 *
 * Powód jest identyczny i sprawdzony: makro na porcję to `na100 * gramy / 100`,
 * a ten iloczyn prawie nigdy nie wychodzi równo. 36 g produktu o 4.7 g białka
 * na 100 g daje `1.6919999999999997`, i dokładnie tyle szłoby do bazy, a potem
 * wracało do każdej kolejnej sumy dnia - już jako cudza liczba. Przy
 * trzydziestu wpisach w tygodniu te ogony się dodają.
 *
 * Dwie różne dokładności, bo to dwie różne skale:
 * - kalorie do jednej dziesiątej; mniej nie ma znaczenia przy wartościach
 *   rzędu tysięcy, a etykiety podają je w liczbach całkowitych,
 * - gramy do jednej tysięcznej, bo sól na porcję naprawdę tam wypada:
 *   produkt z 0.107 g soli na 100 g daje w 30 g porcji 0.032 g. Przy dwóch
 *   miejscach po przecinku dzienna suma soli robiła się fikcją.
 */
fun roundKcal(value: Double): Double = Math.round(value * 10.0) / 10.0

/** Gramy makroskładnika - patrz uzasadnienie przy [roundKcal]. */
fun roundGram(value: Double): Double = Math.round(value * 1000.0) / 1000.0
