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

/**
 * Krok ciężaru dla sprzętu, którego nie składa się z talerzy.
 *
 * Stos wyciągu i maszyna mają własne progi, których aplikacja nie ma skąd
 * znać - i to jest konwencja, nie pomiar. Dwa i pół kilograma pasuje do
 * wielu stosów i do żadnego dokładnie; stos w funtach ma progi co 5,67 kg
 * (12,5 funta), a wtedy każda liczba z tej drabinki jest obok. Dlatego to
 * jest wartość **startowa**: prawdziwy krok ustawia się przy ćwiczeniu
 * ([Exercise.stepKg]), raz, patrząc na tabliczkę przy maszynie.
 */
const val DEFAULT_STEP_KG = 2.5

/** Krok ciężaru ćwiczenia; null i wartości bez sensu schodzą na konwencję. */
fun stepOrDefault(stepKg: Double?): Double =
    stepKg?.takeIf { it > 0.0 } ?: DEFAULT_STEP_KG
