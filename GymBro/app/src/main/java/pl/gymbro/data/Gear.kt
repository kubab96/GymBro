package pl.gymbro.data

/** Dział listy sprzętu - żeby pięćdziesiąt pozycji dało się przejrzeć. */
enum class GearGroup(val label: String) {
    FREE_WEIGHTS("Wolne ciężary"),
    BENCHES("Ławki i stojaki"),
    CABLES("Wyciągi"),
    MACHINES("Maszyny"),
    BODYWEIGHT("Drążki, poręcze, taśmy"),
    BANDS("Gumy"),
    OTHER("Inne")
}

/**
 * Sprzęt, który można mieć - lista do wyboru w Profilu.
 *
 * Osobno od [Equipment] i celowo. Tamten wylicza osiem **sposobów liczenia
 * ciężaru** i wisi na nim załadunek talerzy, krok plusa i minusa oraz to, co
 * dostaje zegarek. Tutaj jest odpowiedź na pytanie „co stoi na Twojej
 * siłowni" - tak szczegółowo, jak w Fitbodzie: stojaki, ławka regulowana,
 * suwnica, drążek, a nie „maszyny" w ogóle.
 *
 * Działa to na dwóch poziomach:
 * - [covers] mówi, jakie rodzaje ćwiczeń pozycja odblokowuje (sztanga →
 *   ćwiczenia ze sztangą),
 * - wymagania pojedynczych ćwiczeń ([Seed.needsOf]) mówią, czego jeszcze
 *   trzeba: przysiad ze sztangą bez stojaków nie jest do zrobienia, choć
 *   sztanga jest, a podciąganie bez drążka - choć masa ciała jest zawsze.
 *
 * Bez drugiego poziomu dłuższa lista byłaby ozdobą: zaznaczenie drążka
 * niczego by nie zmieniało, a ktoś z samymi hantlami dostawał plany
 * z podciąganiem.
 *
 * Każda maszyna z osobna odblokowuje rodzaj [Equipment.MACHINE], a o tym, które
 * ćwiczenie na maszynie da się zrobić, decydują wymagania. [MACHINE] to
 * „komplet maszyn" - spełnia wymaganie każdej maszyny, tak jak na siłowni
 * komercyjnej.
 *
 * **Nazwy stałych to format zapisu** (kolumna `owned_equipment`), więc nie
 * wolno ich zmieniać. BARBELL, DUMBBELL_PAIR, DUMBBELL_SINGLE, CABLE i MACHINE
 * noszą nazwy dawnych wartości [Equipment] nieprzypadkowo: profil zapisany
 * przed tą zmianą czyta się bez migracji.
 *
 * Kilka pozycji z działu „Inne" (skakanka, liny) jeszcze niczego nie
 * odblokowuje - nie ma do nich ćwiczeń liczonych w powtórzeniach. Zostają,
 * bo opisują siłownię, a wpisane teraz nie przepadną, gdy ćwiczenia dojdą.
 */
enum class Gear(val label: String, val group: GearGroup, val covers: Set<Equipment>) {
    BARBELL("Sztanga prosta", GearGroup.FREE_WEIGHTS, setOf(Equipment.BARBELL)),
    EZ_BAR("Gryf łamany", GearGroup.FREE_WEIGHTS, setOf(Equipment.BARBELL)),
    TRAP_BAR("Trap bar", GearGroup.FREE_WEIGHTS, setOf(Equipment.BARBELL)),
    // Kto ma parę, ten ma i jeden - odwrotnie już nie.
    DUMBBELL_PAIR(
        "Hantle (para)", GearGroup.FREE_WEIGHTS,
        setOf(Equipment.DUMBBELL_PAIR, Equipment.DUMBBELL_SINGLE)
    ),
    DUMBBELL_SINGLE("Hantel (jeden)", GearGroup.FREE_WEIGHTS, setOf(Equipment.DUMBBELL_SINGLE)),
    KETTLEBELL("Kettlebell", GearGroup.FREE_WEIGHTS, setOf(Equipment.KETTLEBELL)),
    LANDMINE("Landmine (uchwyt na sztangę)", GearGroup.FREE_WEIGHTS, emptySet()),

    BENCH("Ławka płaska", GearGroup.BENCHES, emptySet()),
    ADJ_BENCH("Ławka regulowana (skos)", GearGroup.BENCHES, emptySet()),
    RACK("Stojaki lub klatka", GearGroup.BENCHES, emptySet()),
    BOX("Skrzynia do skoków", GearGroup.BENCHES, emptySet()),
    // Modlitewnik i ławka rzymska odblokowują rodzaj „maszyna", bo ćwiczenia
    // na nich są tak opisane - a to wymaganie decyduje, czy da się je zrobić.
    PREACHER("Modlitewnik", GearGroup.BENCHES, setOf(Equipment.MACHINE)),
    ROMAN_CHAIR("Ławka rzymska", GearGroup.BENCHES, setOf(Equipment.MACHINE)),

    CABLE("Wyciąg", GearGroup.CABLES, setOf(Equipment.CABLE)),
    CABLE_CROSSOVER("Brama (dwa wyciągi)", GearGroup.CABLES, setOf(Equipment.CABLE)),
    MULTI_GYM("Atlas", GearGroup.CABLES, setOf(Equipment.MACHINE, Equipment.CABLE)),

    MACHINE("Komplet maszyn (siłownia)", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    SMITH("Maszyna Smitha", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    LEG_PRESS("Suwnica do nóg", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    HACK_SQUAT("Hack przysiad", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    BELT_SQUAT("Przysiad z pasem", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    LEG_EXT_CURL("Wyprosty i uginanie nóg", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    ABDUCTOR("Odwodziciele i przywodziciele", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    GLUTE_MACHINE("Maszyna do hip thrustu", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    CALF_MACHINE("Maszyna do łydek", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    CHEST_PRESS("Wyciskanie na klatkę", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    PEC_DECK("Butterfly (pec deck)", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    SHOULDER_MACHINE("Maszyny na barki", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    ROW_MACHINE("Maszyny na plecy", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    ASSISTED("Podciąganie i dipy z asystą", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    ARM_MACHINE("Maszyny na ramiona", GearGroup.MACHINES, setOf(Equipment.MACHINE)),
    AB_MACHINE("Maszyna do brzucha", GearGroup.MACHINES, setOf(Equipment.MACHINE)),

    PULL_UP_BAR("Drążek do podciągania", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),
    DIP_BARS("Poręcze", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),
    POWER_TOWER("Stacja: drążek i poręcze", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),
    SUSPENSION("Taśmy TRX lub kółka", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),
    PARALLETTES("Paralletki", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),
    AB_WHEEL("Kółko do brzucha", GearGroup.BODYWEIGHT, setOf(Equipment.BODYWEIGHT)),

    BANDS("Gumy oporowe", GearGroup.BANDS, setOf(Equipment.BAND)),
    MINI_BANDS("Minibandy", GearGroup.BANDS, setOf(Equipment.BAND)),

    WEIGHTED_VEST("Kamizelka obciążeniowa", GearGroup.OTHER, setOf(Equipment.BODYWEIGHT)),
    DIP_BELT("Pas do obciążania", GearGroup.OTHER, setOf(Equipment.BODYWEIGHT)),
    MEDICINE_BALL("Piłka lekarska", GearGroup.OTHER, setOf(Equipment.BODYWEIGHT)),
    SLAM_BALL("Slam ball", GearGroup.OTHER, setOf(Equipment.BODYWEIGHT)),
    SLED("Sanki", GearGroup.OTHER, setOf(Equipment.BODYWEIGHT)),
    JUMP_ROPE("Skakanka", GearGroup.OTHER, emptySet()),
    BATTLE_ROPES("Liny treningowe", GearGroup.OTHER, emptySet());

    companion object {
        /**
         * Odczyt z kolumny `owned_equipment`.
         *
         * Nieznane nazwy przepadają po cichu. Jedną z nich jest dawne
         * `BODYWEIGHT`: było polem wyboru, a teraz masa ciała jest dostępna
         * zawsze, więc nie ma czego przenosić.
         */
        fun parse(csv: String?): Set<Gear> =
            csv.orEmpty().split(",")
                .mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }
                .toSet()

        /** Zapis do kolumny. null przy pustym zbiorze - tak jak przed zmianą. */
        fun join(gear: Set<Gear>): String? =
            gear.sortedBy { it.ordinal }.joinToString(",") { it.name }.ifEmpty { null }

        /**
         * Gotowe zestawy na start - jak „lokalizacje" w Fitbodzie.
         *
         * Zaznaczanie pięćdziesięciu pozycji po jednej zniechęca, zanim ktoś
         * dojdzie do końca listy. Zestaw zaznacza typowy komplet, a resztę
         * się dopina albo odznacza.
         */
        val PRESETS: List<Pair<String, Set<Gear>>> = listOf(
            "Domowa siłownia" to setOf(
                BARBELL, DUMBBELL_PAIR, BENCH, ADJ_BENCH, RACK, PULL_UP_BAR, DIP_BARS
            ),
            "Hantle i ławka" to setOf(DUMBBELL_PAIR, BENCH, ADJ_BENCH),
            "Drążek i poręcze" to setOf(PULL_UP_BAR, DIP_BARS, SUSPENSION, BANDS),
            "Kettlebell i gumy" to setOf(KETTLEBELL, BANDS, MINI_BANDS, PULL_UP_BAR),
            "Siłownia osiedlowa" to setOf(
                BARBELL, DUMBBELL_PAIR, BENCH, ADJ_BENCH, RACK, CABLE, MULTI_GYM,
                LEG_PRESS, PULL_UP_BAR, DIP_BARS
            )
        )
    }
}
