package pl.gymbro.data

/**
 * Ciężar obcięty do dekagrama.
 *
 * Suma talerzy liczona w `Double` nie wychodzi równo: 10 + 2 × (10+10+5+5+3,4+1,15+1,15)
 * daje `81.39999999999999`, bo 3,4 ani 1,15 nie mają dokładnej reprezentacji
 * binarnej. Na ekranie tego nie widać, bo formatowanie tnie do jednego miejsca -
 * ale do bazy szło dokładnie tyle, a stamtąd wracało przy każdym porównaniu
 * ciężaru i przy każdym kolejnym dodawaniu, już jako cudza liczba.
 *
 * Dwa miejsca po przecinku, bo najmniejszy talerz, jaki ktokolwiek produkuje,
 * to 0,25 kg - setne części kilograma są i tak poniżej rozdzielczości sprzętu.
 */
fun roundKg(value: Double): Double = Math.round(value * 100.0) / 100.0
