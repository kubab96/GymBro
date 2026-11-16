package pl.gymbro.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Dane startowe.
 *
 * Aplikacja startuje **bez planu** - plan to dane użytkownika, nie część aplikacji.
 * Wstawiamy natomiast neutralną bibliotekę ćwiczeń: same nazwy, partie i rodzaj
 * sprzętu, bez ciężarów, serii i powiązań z jakimkolwiek planem. To zawartość
 * aplikacji, taka sama dla każdego, a nie czyjkolwiek trening.
 *
 * Gotowe plany leżą osobno, w [PlanLibrary], i do bazy nie trafiają wcale,
 * dopóki ktoś sam któregoś nie doda - wtedy są już jego planem.
 *
 * Wiersz `gym_profile` istnieje, bo tabela z założenia ma dokładnie jeden wiersz
 * (wymusza to CHECK na kluczu), a zapis masy gryfu robi UPDATE - bez istniejącego
 * wiersza nie miałby czego zaktualizować.
 */
object Seed {

    /** Neutralna wartość startowa, nie sugestia. Zmienia się w edytorze sprzętu. */
    private const val DEFAULT_BAR_KG = 20.0

    fun populate(db: SQLiteDatabase) {
        db.insert("gym_profile", null, ContentValues().apply {
            put("id", 1)
            put("bar_kg", DEFAULT_BAR_KG)
        })
        seedExerciseLibrary(db)
        kettlebellSteps(db)
    }

    /**
     * Biblioteka ćwiczeń.
     *
     * Wywoływana także z migracji, dlatego wstawia z pominięciem konfliktów:
     * nazwa ćwiczenia ma indeks unikalny, a użytkownik mógł już utworzyć własne
     * ćwiczenie o tej samej nazwie. Jego wpis ma pierwszeństwo i zostaje nietknięty.
     */
    fun seedExerciseLibrary(db: SQLiteDatabase) {
        for ((name, equipment, muscle) in LIBRARY) {
            db.insertWithOnConflict(
                "exercise",
                null,
                ContentValues().apply {
                    put("name", name)
                    put("equipment", equipment.name)
                    put("muscle_group", muscle)
                    put("is_custom", 0)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    /**
     * Wpis z biblioteki po nazwie: nazwa, sprzęt, partia.
     *
     * Dla [PlanLibrary]: gotowy plan wskazuje ćwiczenia nazwą, a ćwiczenie,
     * którego w bazie już nie ma (dało się je skasować, póki nic go nie
     * trzymało), trzeba przy dodawaniu planu odtworzyć z właściwym sprzętem.
     */
    fun libraryEntry(name: String): Triple<String, Equipment, String>? = BY_NAME[name]

    // Mapa, nie przeszukiwanie listy: wyszukiwarka planów pyta o partię
    // każdego ćwiczenia z kilkuset jednostek przy każdej wpisanej literze.
    private val BY_NAME by lazy { LIBRARY.associateBy { it.first } }

    // ---- Wymagania ćwiczeń ---------------------------------------------------

    /**
     * Czego poza rodzajem sprzętu wymaga ćwiczenie z biblioteki.
     *
     * Lista grup: każda musi być spełniona, a grupę spełnia dowolna pozycja
     * z niej. Wyciskanie leżąc to „ławka (płaska albo regulowana)" i
     * „stojaki" - dwie grupy. Ćwiczenie bez wpisu wymaga tylko swojego
     * rodzaju sprzętu.
     *
     * Po nazwie, w kodzie, jak cała biblioteka - zmiana tu nie wymaga
     * migracji. Ćwiczenie przemianowane przez użytkownika traci wpis i wraca
     * do samego rodzaju sprzętu, co jest bezpieczną stroną: lepiej pokazać
     * o jedno za dużo, niż schować ćwiczenie, które ktoś sam poprawił.
     */
    fun needsOf(name: String): List<Set<Gear>> = NEEDS[name].orEmpty()

    /** Czy posiadany sprzęt spełnia wymagania ćwiczenia. */
    fun needsMet(name: String, owned: Set<Gear>): Boolean {
        val mam = withGymStations(owned)
        return needsOf(name).all { group -> group.any { it in mam } }
    }

    /** Grupy wymagań, których posiadany sprzęt nie spełnia. */
    fun missingNeeds(name: String, owned: Set<Gear>): List<Set<Gear>> {
        val mam = withGymStations(owned)
        return needsOf(name).filter { group -> group.none { it in mam } }
    }

    /**
     * „Komplet maszyn (siłownia)" to pełna siłownia, a w niej ławki, stojaki
     * i drążek stoją zawsze. Bez tego lista zapisana, zanim stojaki i ławki
     * dało się zaznaczyć osobno (sztanga plus komplet maszyn), po
     * aktualizacji gubiła przysiad i wyciskanie leżąc.
     */
    private val GYM_STATIONS = setOf(
        Gear.BENCH, Gear.ADJ_BENCH, Gear.RACK, Gear.BOX, Gear.PREACHER,
        Gear.ROMAN_CHAIR, Gear.PULL_UP_BAR, Gear.DIP_BARS
    )

    private fun withGymStations(owned: Set<Gear>): Set<Gear> =
        if (Gear.MACHINE in owned) owned + GYM_STATIONS else owned

    // Grupy powtarzające się w wielu ćwiczeniach.
    private val FLAT = setOf(Gear.BENCH, Gear.ADJ_BENCH)
    private val INCLINE = setOf(Gear.ADJ_BENCH)
    private val STEP = setOf(Gear.BOX, Gear.BENCH, Gear.ADJ_BENCH)
    private val RACK = setOf(Gear.RACK)
    private val PULL = setOf(Gear.PULL_UP_BAR, Gear.POWER_TOWER)
    private val DIP = setOf(Gear.DIP_BARS, Gear.POWER_TOWER)
    private val RINGS = setOf(Gear.SUSPENSION)
    private val LOW_BAR = setOf(Gear.RACK, Gear.SMITH, Gear.SUSPENSION)
    private val LOAD = setOf(Gear.DIP_BELT, Gear.WEIGHTED_VEST)

    /**
     * Maszyna: konkretna pozycja albo komplet maszyn, który obejmuje każdą.
     *
     * Komplet na końcu - ostrzeżenie o brakach pokazuje pierwszą pozycję
     * z grupy, a „brak: suwnica" mówi więcej niż „brak: komplet maszyn".
     */
    private fun machine(vararg g: Gear) = setOf(*g) + Gear.MACHINE

    internal val NEEDS: Map<String, List<Set<Gear>>> by lazy {
        mapOf(
            // ---- sztanga: stojaki i ławki ----
            "Przysiad ze sztangą" to listOf(RACK),
            "Przysiad przedni" to listOf(RACK),
            "Przysiad ze sztangą z pauzą" to listOf(RACK),
            "Przysiad do skrzyni ze sztangą" to listOf(RACK, STEP),
            "Wykroki ze sztangą" to listOf(RACK),
            "Skłony good morning" to listOf(RACK),
            "Wspięcia na palce stojąc ze sztangą" to listOf(RACK),
            "OHP" to listOf(RACK),
            "Wyciskanie z podrzutem" to listOf(RACK),
            "Wyciskanie sztangi siedząc" to listOf(RACK, INCLINE),
            "Wyciskanie sztangi leżąc" to listOf(FLAT, RACK),
            "Wyciskanie sztangi z pauzą" to listOf(FLAT, RACK),
            "Wyciskanie wąsko" to listOf(FLAT, RACK),
            "Wyciskanie sztangi skos" to listOf(INCLINE, RACK),
            "Wyciskanie sztangi skos w dół" to listOf(INCLINE, RACK),
            "Francuskie sztangą" to listOf(FLAT),
            "Francuskie sztangą łamaną leżąc" to listOf(FLAT),
            "Hip thrust" to listOf(FLAT),
            "Martwy ciąg z trap bar" to listOf(setOf(Gear.TRAP_BAR)),
            "Wiosłowanie T-sztangą" to listOf(setOf(Gear.LANDMINE)),
            "Wyciskanie landmine" to listOf(setOf(Gear.LANDMINE)),
            "Wiosłowanie landmine" to listOf(setOf(Gear.LANDMINE)),
            "Przysiad landmine" to listOf(setOf(Gear.LANDMINE)),

            // ---- hantle: ławki ----
            "Wyciskanie hantli leżąc" to listOf(FLAT),
            "Wyciskanie hantli neutralnym chwytem" to listOf(FLAT),
            "Wyciskanie hantli wąsko" to listOf(FLAT),
            "Rozpiętki na ławce" to listOf(FLAT),
            "Przenoszenie hantla za głowę" to listOf(FLAT),
            "Francuskie hantlami leżąc" to listOf(FLAT),
            "Wyciskanie hantli skos" to listOf(INCLINE),
            "Wyciskanie hantli skos w dół" to listOf(INCLINE),
            "Rozpiętki na ławce skośnej" to listOf(INCLINE),
            "Wiosłowanie hantlami na ławce skośnej" to listOf(INCLINE),
            "Uginanie hantli na ławce skośnej" to listOf(INCLINE),
            "Uginanie hantli przodem na ławce skośnej" to listOf(INCLINE),
            "Wyciskanie hantli siedząc" to listOf(FLAT),
            "Wejścia na skrzynię z hantlami" to listOf(STEP),
            "Uginanie hantla na modlitewniku" to listOf(setOf(Gear.PREACHER)),

            // ---- maszyny ----
            "Suwnica" to listOf(machine(Gear.LEG_PRESS)),
            "Suwnica jednonóż" to listOf(machine(Gear.LEG_PRESS)),
            "Wspięcia na palce na suwnicy" to listOf(machine(Gear.LEG_PRESS)),
            "Hack przysiad" to listOf(machine(Gear.HACK_SQUAT)),
            "Przysiad wahadłowy" to listOf(machine(Gear.HACK_SQUAT)),
            "Przysiad z pasem na maszynie" to listOf(machine(Gear.BELT_SQUAT)),
            "Przysiad na maszynie Smitha" to listOf(machine(Gear.SMITH)),
            "Wspięcia na palce na maszynie Smitha" to listOf(machine(Gear.SMITH)),
            "Hip thrust na maszynie Smitha" to listOf(machine(Gear.SMITH), FLAT),
            "Wyciskanie na maszynie Smitha" to listOf(machine(Gear.SMITH), FLAT),
            "Wyciskanie skos na maszynie Smitha" to listOf(machine(Gear.SMITH), INCLINE),
            "Wyprosty nóg na maszynie" to listOf(machine(Gear.LEG_EXT_CURL, Gear.MULTI_GYM)),
            "Uginanie nóg na maszynie" to listOf(machine(Gear.LEG_EXT_CURL, Gear.MULTI_GYM)),
            "Uginanie nóg siedząc na maszynie" to listOf(machine(Gear.LEG_EXT_CURL)),
            "Przywodzenie nóg na maszynie" to listOf(machine(Gear.ABDUCTOR)),
            "Odwodzenie nóg na maszynie" to listOf(machine(Gear.ABDUCTOR)),
            "Hip thrust na maszynie" to listOf(machine(Gear.GLUTE_MACHINE)),
            "Wspięcia na palce" to listOf(machine(Gear.CALF_MACHINE, Gear.SMITH, Gear.LEG_PRESS)),
            "Wspięcia na palce siedząc" to listOf(machine(Gear.CALF_MACHINE)),
            "Wyciskanie na maszynie" to listOf(machine(Gear.CHEST_PRESS, Gear.MULTI_GYM)),
            "Wyciskanie na maszynie skos" to listOf(machine(Gear.CHEST_PRESS)),
            "Butterfly" to listOf(machine(Gear.PEC_DECK, Gear.MULTI_GYM)),
            "Odwrotne rozpiętki na maszynie" to listOf(machine(Gear.PEC_DECK)),
            "Wyciskanie nad głowę na maszynie" to listOf(machine(Gear.SHOULDER_MACHINE)),
            "Unoszenie bokiem na maszynie" to listOf(machine(Gear.SHOULDER_MACHINE)),
            "Wiosłowanie na maszynie" to listOf(machine(Gear.ROW_MACHINE, Gear.MULTI_GYM)),
            "Wiosłowanie na maszynie T-bar" to listOf(machine(Gear.ROW_MACHINE)),
            "Ściąganie na maszynie" to listOf(machine(Gear.ROW_MACHINE)),
            "Podciąganie z asystą na maszynie" to listOf(machine(Gear.ASSISTED)),
            "Dipy z asystą na maszynie" to listOf(machine(Gear.ASSISTED)),
            "Uginanie na modlitewniku" to listOf(machine(Gear.PREACHER, Gear.ARM_MACHINE)),
            "Prostowanie na maszynie" to listOf(machine(Gear.ARM_MACHINE)),
            "Dipy na maszynie" to listOf(machine(Gear.ARM_MACHINE, Gear.ASSISTED)),
            "Spięcia brzucha na maszynie" to listOf(machine(Gear.AB_MACHINE)),
            "Wyprost bioder na ławce rzymskiej" to listOf(machine(Gear.ROMAN_CHAIR)),
            "Wyprosty tułowia na ławce rzymskiej" to listOf(machine(Gear.ROMAN_CHAIR)),

            // ---- drążek, poręcze, kółka ----
            "Podciąganie na drążku" to listOf(PULL),
            "Podciąganie podchwytem" to listOf(PULL),
            "Podciąganie podchwytem wąskim" to listOf(PULL),
            "Podciąganie neutralnym chwytem" to listOf(PULL),
            "Podciąganie szerokim chwytem" to listOf(PULL),
            "Podciąganie negatywne" to listOf(PULL),
            "Podciąganie łucznicze" to listOf(PULL),
            "Podciąganie z obciążeniem" to listOf(PULL, LOAD),
            "Podciąganie z gumą asekuracyjną" to listOf(PULL, setOf(Gear.BANDS)),
            "Ściąganie łopatek w zwisie" to listOf(PULL),
            "Wyjście siłowe na drążku" to listOf(PULL),
            "Zwis na drążku" to listOf(PULL),
            "Unoszenie nóg w zwisie" to listOf(PULL),
            "Unoszenie kolan w zwisie" to listOf(PULL),
            "Unoszenie nóg do drążka" to listOf(PULL),
            "Wycieraczki w zwisie" to listOf(PULL),
            "Wiosłowanie w pozycji front lever" to listOf(PULL + RINGS),
            "Dipy na poręczach" to listOf(DIP),
            "Dipy z obciążeniem" to listOf(DIP, LOAD),
            "Unoszenie nóg w podporze na poręczach" to listOf(DIP),
            "Podciąganie australijskie" to listOf(LOW_BAR),
            "Prostowanie ramion na niskim drążku" to listOf(LOW_BAR),
            "Pompki na pierścieniach" to listOf(RINGS),
            "Dipy na pierścieniach" to listOf(RINGS),
            "Rozpiętki na pierścieniach" to listOf(RINGS),
            "Wiosłowanie na pierścieniach" to listOf(RINGS),
            "Uginanie ramion na pierścieniach" to listOf(RINGS),
            "Wyjście siłowe na pierścieniach" to listOf(RINGS),
            "Pompki na paralletkach" to listOf(setOf(Gear.PARALLETTES)),
            "Rollout kółkiem" to listOf(setOf(Gear.AB_WHEEL)),
            "Skoki na skrzynię" to listOf(setOf(Gear.BOX)),
            "Przysiad jednonóż do skrzyni" to listOf(STEP),

            // ---- gumy i reszta ----
            "Mostek biodrowy z minibandem" to listOf(setOf(Gear.MINI_BANDS)),
            "Odwodzenie nóg z minibandem" to listOf(setOf(Gear.MINI_BANDS)),
            "Spacer boczny z minibandem" to listOf(setOf(Gear.MINI_BANDS)),
            "Pompki z kamizelką" to listOf(setOf(Gear.WEIGHTED_VEST)),
            "Rzuty piłką lekarską o ścianę" to listOf(setOf(Gear.MEDICINE_BALL)),
            "Rzuty piłką o podłogę" to listOf(setOf(Gear.SLAM_BALL, Gear.MEDICINE_BALL)),
            "Pchanie sanek" to listOf(setOf(Gear.SLED))
        )
    }

    /**
     * Krok 4 kg dla kettlebelli z biblioteki, tam gdzie nikt nie ustawił
     * własnego.
     *
     * Kettlebelle rosną co 4 kg (8, 12, 16, 20, 24), a domyślny krok 2,5 kg
     * trafiałby plusem w ciężary, których nie ma na żadnej siłowni. Osobnym
     * UPDATE, nie w [seedExerciseLibrary]: ta woła się też z najstarszej
     * migracji, sprzed kolumny `weight_step`.
     */
    fun kettlebellSteps(db: SQLiteDatabase) {
        db.execSQL(
            "UPDATE exercise SET weight_step = 4 " +
                "WHERE equipment = 'KETTLEBELL' AND weight_step IS NULL AND is_custom = 0"
        )
    }

    // Leniwie, bo sięga do V26 zadeklarowanego niżej - pola obiektu inicjalizują
    // się po kolei i zwykłe przypisanie widziałoby tam jeszcze null.
    internal val LIBRARY: List<Triple<String, Equipment, String>> by lazy { listOf(
        // --- nogi ---
        Triple("Przysiad ze sztangą", Equipment.BARBELL, "nogi"),
        Triple("Przysiad przedni", Equipment.BARBELL, "nogi"),
        Triple("Przysiad goblet", Equipment.DUMBBELL_SINGLE, "nogi"),
        Triple("Martwy ciąg", Equipment.BARBELL, "nogi"),
        Triple("Martwy ciąg rumuński", Equipment.BARBELL, "nogi"),
        Triple("Wykroki z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Bułgarskie przysiady", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Suwnica", Equipment.MACHINE, "nogi"),
        Triple("Wyprosty nóg na maszynie", Equipment.MACHINE, "nogi"),
        Triple("Uginanie nóg na maszynie", Equipment.MACHINE, "nogi"),
        Triple("Hip thrust", Equipment.BARBELL, "pośladki"),
        Triple("Wspięcia na palce", Equipment.MACHINE, "łydki"),

        // --- klatka ---
        Triple("Wyciskanie sztangi leżąc", Equipment.BARBELL, "klatka"),
        Triple("Wyciskanie sztangi skos", Equipment.BARBELL, "klatka"),
        Triple("Wyciskanie hantli leżąc", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Wyciskanie hantli skos", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Rozpiętki na ławce", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Rozpiętki na bramie", Equipment.CABLE, "klatka"),
        Triple("Pompki", Equipment.BODYWEIGHT, "klatka"),
        Triple("Dipy na poręczach", Equipment.BODYWEIGHT, "klatka"),

        // --- plecy ---
        Triple("Podciąganie na drążku", Equipment.BODYWEIGHT, "plecy"),
        Triple("Ściąganie drążka wyciągu górnego", Equipment.CABLE, "plecy"),
        Triple("Wiosłowanie sztangą", Equipment.BARBELL, "plecy"),
        Triple("Wiosłowanie jednorącz", Equipment.DUMBBELL_SINGLE, "plecy"),
        Triple("Wiosłowanie na wyciągu siedząc", Equipment.CABLE, "plecy"),
        Triple("Szrugsy z hantlami", Equipment.DUMBBELL_PAIR, "plecy"),

        // --- barki ---
        Triple("OHP", Equipment.BARBELL, "barki"),
        Triple("Wyciskanie hantli nad głowę", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Unoszenie hantli bokiem", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Unoszenie hantli w opadzie", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Unoszenie hantli przodem", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Face pull", Equipment.CABLE, "barki"),

        // --- biceps ---
        Triple("Uginanie sztangi stojąc", Equipment.BARBELL, "biceps"),
        Triple("Uginanie supinowane hantli", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie młotkowe", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie na modlitewniku", Equipment.MACHINE, "biceps"),
        Triple("Uginanie na wyciągu dolnym", Equipment.CABLE, "biceps"),

        // --- triceps ---
        Triple("Wyciskanie wąsko", Equipment.BARBELL, "triceps"),
        Triple("Francuskie sztangą", Equipment.BARBELL, "triceps"),
        Triple("Francuskie hantlem", Equipment.DUMBBELL_SINGLE, "triceps"),
        Triple("Prostowanie na wyciągu górnym", Equipment.CABLE, "triceps"),
        Triple("Prostowanie ramion nad głową na wyciągu", Equipment.CABLE, "triceps"),

        // --- brzuch i przedramiona ---
        Triple("Deska", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie nóg w zwisie", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Spięcia brzucha na wyciągu", Equipment.CABLE, "brzuch"),
        Triple("Rollout kółkiem", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Uginanie nadgarstków ze sztangą", Equipment.BARBELL, "przedramiona"),
        Triple("Zwis na drążku", Equipment.BODYWEIGHT, "przedramiona"),

        // --- dopisane w wersji 25 bazy, razem z gotowymi planami ---
        //
        // Głównie maszyny i wyciągi: pierwsza lista była pisana pod sztangę
        // i hantle, a na siłowni komercyjnej połowa sali to sprzęt, którego
        // w niej nie było. Na końcu, nie w swoich działach, żeby było widać,
        // co doszło - kolejność w tej liście nie ma znaczenia dla ekranu,
        // bo biblioteka wyświetla się alfabetycznie.
        Triple("Martwy ciąg rumuński z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Hack przysiad", Equipment.MACHINE, "nogi"),
        Triple("Przysiad na maszynie Smitha", Equipment.MACHINE, "nogi"),
        Triple("Przywodzenie nóg na maszynie", Equipment.MACHINE, "nogi"),
        Triple("Odwodzenie nóg na maszynie", Equipment.MACHINE, "pośladki"),
        Triple("Wspięcia na palce siedząc", Equipment.MACHINE, "łydki"),
        Triple("Wyciskanie na maszynie", Equipment.MACHINE, "klatka"),
        Triple("Butterfly", Equipment.MACHINE, "klatka"),
        Triple("Wiosłowanie na maszynie", Equipment.MACHINE, "plecy"),
        Triple("Ściąganie drążka wąskim chwytem", Equipment.CABLE, "plecy"),
        Triple("Ściąganie prostymi rękami na wyciągu", Equipment.CABLE, "plecy"),
        Triple("Podciąganie podchwytem", Equipment.BODYWEIGHT, "plecy"),
        Triple("Szrugsy ze sztangą", Equipment.BARBELL, "plecy"),
        Triple("Wyciskanie nad głowę na maszynie", Equipment.MACHINE, "barki"),
        Triple("Unoszenie bokiem na wyciągu", Equipment.CABLE, "barki"),
        Triple("Odwrotne rozpiętki na maszynie", Equipment.MACHINE, "barki"),
        Triple("Uginanie hantli na ławce skośnej", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie na bramie", Equipment.CABLE, "biceps"),
        Triple("Pompki diamentowe", Equipment.BODYWEIGHT, "triceps"),
        Triple("Spięcia brzucha", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Russian twist", Equipment.BODYWEIGHT, "brzuch")
    ) + V26 + V28 }

    /**
     * Dopisane w wersji 26 bazy: biblioteka na tyle duża, żeby dało się z niej
     * złożyć trening na każdym sprzęcie - także bez żadnego.
     *
     * Masa ciała ma tu tyle samo miejsca co sztanga. Kalistenika to nie
     * „pompki i przysiady", tylko drabinki trudności - pompka z rękami na
     * podwyższeniu, zwykła, łucznicza, na jednej ręce - i każdy szczebel
     * jest osobnym ćwiczeniem, bo progresja idzie tam zmianą wariantu,
     * a nie ciężaru.
     *
     * Ćwiczeń na czas (deska, L-sit, zwis) jest mało i nie są w gotowych
     * planach: dziennik liczy powtórzenia, a seria zapisana jako „30" nie
     * mówi, czy to sekundy.
     *
     * Partie wyłącznie z [pl.gymbro.gym.MuscleMap.GROUPS] - pilnuje tego test.
     */
    private val V26: List<Triple<String, Equipment, String>> = listOf(
        // ---- nogi i pośladki: sztanga ----
        Triple("Przysiad ze sztangą z pauzą", Equipment.BARBELL, "nogi"),
        Triple("Przysiad do skrzyni ze sztangą", Equipment.BARBELL, "nogi"),
        Triple("Przysiad Zerchera", Equipment.BARBELL, "nogi"),
        Triple("Wykroki ze sztangą", Equipment.BARBELL, "nogi"),
        Triple("Martwy ciąg sumo", Equipment.BARBELL, "nogi"),
        Triple("Martwy ciąg z trap bar", Equipment.BARBELL, "nogi"),
        Triple("Martwy ciąg na prostych nogach", Equipment.BARBELL, "nogi"),
        Triple("Skłony good morning", Equipment.BARBELL, "nogi"),
        Triple("Mostek biodrowy ze sztangą", Equipment.BARBELL, "pośladki"),
        Triple("Wspięcia na palce stojąc ze sztangą", Equipment.BARBELL, "łydki"),

        // ---- nogi i pośladki: hantle ----
        Triple("Przysiad z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Wejścia na skrzynię z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Wykroki w tył z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Wykroki chodzone z hantlami", Equipment.DUMBBELL_PAIR, "nogi"),
        Triple("Przysiad sumo z hantlem", Equipment.DUMBBELL_SINGLE, "nogi"),
        Triple("Martwy ciąg jednonóż z hantlem", Equipment.DUMBBELL_SINGLE, "nogi"),
        Triple("Wykroki boczne z hantlem", Equipment.DUMBBELL_SINGLE, "nogi"),
        Triple("Hip thrust z hantlem", Equipment.DUMBBELL_SINGLE, "pośladki"),
        Triple("Wspięcia na palce jednonóż z hantlem", Equipment.DUMBBELL_SINGLE, "łydki"),

        // ---- nogi i pośladki: maszyny i wyciągi ----
        Triple("Suwnica jednonóż", Equipment.MACHINE, "nogi"),
        Triple("Przysiad wahadłowy", Equipment.MACHINE, "nogi"),
        Triple("Przysiad z pasem na maszynie", Equipment.MACHINE, "nogi"),
        Triple("Uginanie nóg siedząc na maszynie", Equipment.MACHINE, "nogi"),
        Triple("Hip thrust na maszynie", Equipment.MACHINE, "pośladki"),
        Triple("Wyprost bioder na ławce rzymskiej", Equipment.MACHINE, "pośladki"),
        Triple("Wspięcia na palce na suwnicy", Equipment.MACHINE, "łydki"),
        Triple("Wspięcia na palce na maszynie Smitha", Equipment.MACHINE, "łydki"),
        Triple("Pull-through na wyciągu", Equipment.CABLE, "pośladki"),
        Triple("Odrzut nogi w tył na wyciągu", Equipment.CABLE, "pośladki"),
        Triple("Odwodzenie nogi na wyciągu", Equipment.CABLE, "pośladki"),

        // ---- nogi i pośladki: masa ciała ----
        Triple("Przysiad bez obciążenia", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad z wyskokiem", Equipment.BODYWEIGHT, "nogi"),
        Triple("Wykroki bez obciążenia", Equipment.BODYWEIGHT, "nogi"),
        Triple("Wykroki w tył bez obciążenia", Equipment.BODYWEIGHT, "nogi"),
        Triple("Wykroki z wyskokiem", Equipment.BODYWEIGHT, "nogi"),
        Triple("Bułgarskie przysiady bez obciążenia", Equipment.BODYWEIGHT, "nogi"),
        Triple("Wejścia na krzesło", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad jednonóż do skrzyni", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad pistolet", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad kozacki", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad krewetka", Equipment.BODYWEIGHT, "nogi"),
        Triple("Przysiad sissy", Equipment.BODYWEIGHT, "nogi"),
        Triple("Uginanie nordyckie", Equipment.BODYWEIGHT, "nogi"),
        Triple("Skoki na skrzynię", Equipment.BODYWEIGHT, "nogi"),
        Triple("Skoki w dal z miejsca", Equipment.BODYWEIGHT, "nogi"),
        Triple("Burpees", Equipment.BODYWEIGHT, "nogi"),
        Triple("Mostek biodrowy", Equipment.BODYWEIGHT, "pośladki"),
        Triple("Mostek biodrowy jednonóż", Equipment.BODYWEIGHT, "pośladki"),
        Triple("Hip thrust jednonóż na ławce", Equipment.BODYWEIGHT, "pośladki"),
        Triple("Odrzut nogi w klęku podpartym", Equipment.BODYWEIGHT, "pośladki"),
        Triple("Odwodzenie nogi w leżeniu bokiem", Equipment.BODYWEIGHT, "pośladki"),
        Triple("Wspięcia na palce bez obciążenia", Equipment.BODYWEIGHT, "łydki"),
        Triple("Wspięcia na palce jednonóż", Equipment.BODYWEIGHT, "łydki"),

        // ---- klatka ----
        Triple("Wyciskanie sztangi skos w dół", Equipment.BARBELL, "klatka"),
        Triple("Wyciskanie sztangi z pauzą", Equipment.BARBELL, "klatka"),
        Triple("Wyciskanie sztangi z podłogi", Equipment.BARBELL, "klatka"),
        Triple("Wyciskanie hantli skos w dół", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Wyciskanie hantli neutralnym chwytem", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Wyciskanie hantli z podłogi", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Rozpiętki na ławce skośnej", Equipment.DUMBBELL_PAIR, "klatka"),
        Triple("Przenoszenie hantla za głowę", Equipment.DUMBBELL_SINGLE, "klatka"),
        Triple("Wyciskanie na maszynie skos", Equipment.MACHINE, "klatka"),
        Triple("Wyciskanie na maszynie Smitha", Equipment.MACHINE, "klatka"),
        Triple("Wyciskanie skos na maszynie Smitha", Equipment.MACHINE, "klatka"),
        Triple("Dipy z asystą na maszynie", Equipment.MACHINE, "klatka"),
        Triple("Rozpiętki na bramie z dołu", Equipment.CABLE, "klatka"),
        Triple("Rozpiętki na bramie z góry", Equipment.CABLE, "klatka"),
        Triple("Wyciskanie na wyciągu stojąc", Equipment.CABLE, "klatka"),
        Triple("Pompki na kolanach", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki z rękami na podwyższeniu", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki z nogami na podwyższeniu", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki szerokie", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki łucznicze", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki na jednej ręce", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki z klaśnięciem", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki pseudo planche", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki na pierścieniach", Equipment.BODYWEIGHT, "klatka"),
        Triple("Dipy na pierścieniach", Equipment.BODYWEIGHT, "klatka"),
        Triple("Rozpiętki na pierścieniach", Equipment.BODYWEIGHT, "klatka"),

        // ---- plecy ----
        Triple("Wiosłowanie sztangą z podłogi", Equipment.BARBELL, "plecy"),
        Triple("Wiosłowanie sztangą podchwytem", Equipment.BARBELL, "plecy"),
        Triple("Wiosłowanie T-sztangą", Equipment.BARBELL, "plecy"),
        Triple("Martwy ciąg z podwyższenia", Equipment.BARBELL, "plecy"),
        Triple("Wiosłowanie hantlami w opadzie", Equipment.DUMBBELL_PAIR, "plecy"),
        Triple("Wiosłowanie hantlami na ławce skośnej", Equipment.DUMBBELL_PAIR, "plecy"),
        Triple("Ściąganie drążka podchwytem", Equipment.CABLE, "plecy"),
        Triple("Ściąganie drążka jednorącz", Equipment.CABLE, "plecy"),
        Triple("Wiosłowanie jednorącz na wyciągu", Equipment.CABLE, "plecy"),
        Triple("Wiosłowanie na wyciągu szerokim chwytem", Equipment.CABLE, "plecy"),
        Triple("Wiosłowanie na maszynie T-bar", Equipment.MACHINE, "plecy"),
        Triple("Ściąganie na maszynie", Equipment.MACHINE, "plecy"),
        Triple("Podciąganie z asystą na maszynie", Equipment.MACHINE, "plecy"),
        Triple("Wyprosty tułowia na ławce rzymskiej", Equipment.MACHINE, "plecy"),
        Triple("Podciąganie neutralnym chwytem", Equipment.BODYWEIGHT, "plecy"),
        Triple("Podciąganie szerokim chwytem", Equipment.BODYWEIGHT, "plecy"),
        Triple("Podciąganie negatywne", Equipment.BODYWEIGHT, "plecy"),
        Triple("Podciąganie łucznicze", Equipment.BODYWEIGHT, "plecy"),
        Triple("Podciąganie australijskie", Equipment.BODYWEIGHT, "plecy"),
        Triple("Wiosłowanie na pierścieniach", Equipment.BODYWEIGHT, "plecy"),
        Triple("Ściąganie łopatek w zwisie", Equipment.BODYWEIGHT, "plecy"),
        Triple("Wyjście siłowe na drążku", Equipment.BODYWEIGHT, "plecy"),
        Triple("Wyjście siłowe na pierścieniach", Equipment.BODYWEIGHT, "plecy"),
        Triple("Wiosłowanie w pozycji front lever", Equipment.BODYWEIGHT, "plecy"),
        Triple("Superman", Equipment.BODYWEIGHT, "plecy"),

        // ---- barki ----
        Triple("Wyciskanie sztangi siedząc", Equipment.BARBELL, "barki"),
        Triple("Wyciskanie z podrzutem", Equipment.BARBELL, "barki"),
        Triple("Wiosłowanie sztangą do brody", Equipment.BARBELL, "barki"),
        Triple("Wyciskanie Arnolda", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Wyciskanie hantli siedząc", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Unoszenie hantli bokiem siedząc", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Wiosłowanie hantlami do brody", Equipment.DUMBBELL_PAIR, "barki"),
        Triple("Wyciskanie hantla jednorącz stojąc", Equipment.DUMBBELL_SINGLE, "barki"),
        Triple("Unoszenie hantla bokiem jednorącz", Equipment.DUMBBELL_SINGLE, "barki"),
        Triple("Odwrotne rozpiętki na bramie", Equipment.CABLE, "barki"),
        Triple("Unoszenie przodem na wyciągu", Equipment.CABLE, "barki"),
        Triple("Wiosłowanie do brody na wyciągu", Equipment.CABLE, "barki"),
        Triple("Unoszenie bokiem na maszynie", Equipment.MACHINE, "barki"),
        Triple("Pompki w pozycji pike", Equipment.BODYWEIGHT, "barki"),
        Triple("Pompki pike z nogami na podwyższeniu", Equipment.BODYWEIGHT, "barki"),
        Triple("Pompki w staniu na rękach przy ścianie", Equipment.BODYWEIGHT, "barki"),
        Triple("Wchodzenie po ścianie do stania na rękach", Equipment.BODYWEIGHT, "barki"),
        Triple("Pompki hindu", Equipment.BODYWEIGHT, "barki"),
        Triple("Unoszenie Y-T-W leżąc", Equipment.BODYWEIGHT, "barki"),

        // ---- biceps i przedramiona ----
        Triple("Uginanie sztangi łamanej", Equipment.BARBELL, "biceps"),
        Triple("Uginanie sztangi nachwytem", Equipment.BARBELL, "przedramiona"),
        Triple("Odwrotne uginanie nadgarstków", Equipment.BARBELL, "przedramiona"),
        Triple("Uginanie koncentryczne", Equipment.DUMBBELL_SINGLE, "biceps"),
        Triple("Uginanie hantli naprzemienne", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie Zottmana", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie hantla na modlitewniku", Equipment.DUMBBELL_SINGLE, "biceps"),
        Triple("Uginanie hantli przodem na ławce skośnej", Equipment.DUMBBELL_PAIR, "biceps"),
        Triple("Uginanie nadgarstków z hantlem", Equipment.DUMBBELL_SINGLE, "przedramiona"),
        Triple("Uginanie na wyciągu górnym", Equipment.CABLE, "biceps"),
        Triple("Uginanie liną na wyciągu", Equipment.CABLE, "biceps"),
        Triple("Podciąganie podchwytem wąskim", Equipment.BODYWEIGHT, "biceps"),
        Triple("Uginanie ramion na pierścieniach", Equipment.BODYWEIGHT, "biceps"),

        // ---- triceps ----
        Triple("Francuskie sztangą łamaną leżąc", Equipment.BARBELL, "triceps"),
        Triple("Francuskie hantlami leżąc", Equipment.DUMBBELL_PAIR, "triceps"),
        Triple("Wyciskanie hantli wąsko", Equipment.DUMBBELL_PAIR, "triceps"),
        Triple("Prostowanie ramienia w opadzie", Equipment.DUMBBELL_SINGLE, "triceps"),
        Triple("Prostowanie liną na wyciągu", Equipment.CABLE, "triceps"),
        Triple("Prostowanie jednorącz na wyciągu", Equipment.CABLE, "triceps"),
        Triple("Prostowanie podchwytem na wyciągu", Equipment.CABLE, "triceps"),
        Triple("Prostowanie na maszynie", Equipment.MACHINE, "triceps"),
        Triple("Dipy na maszynie", Equipment.MACHINE, "triceps"),
        Triple("Dipy na ławce", Equipment.BODYWEIGHT, "triceps"),
        Triple("Pompki wąskie", Equipment.BODYWEIGHT, "triceps"),
        Triple("Pompki sfinks", Equipment.BODYWEIGHT, "triceps"),
        Triple("Prostowanie ramion na niskim drążku", Equipment.BODYWEIGHT, "triceps"),

        // ---- brzuch ----
        Triple("Brzuszki", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie nóg leżąc", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Martwy robak", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie ręki i nogi w klęku podpartym", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Scyzoryki", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Nożyce", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Rowerek", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Wspinaczka górska", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Kołyski w łódce", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Spięcia odwrotne", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Spięcia boczne", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie bioder w podporze bokiem", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie kolan w zwisie", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie nóg do drążka", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Wycieraczki w zwisie", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Smoczy sztandar", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Unoszenie nóg w podporze na poręczach", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Skłony boczne z hantlem", Equipment.DUMBBELL_SINGLE, "brzuch"),
        Triple("Drwal na wyciągu", Equipment.CABLE, "brzuch"),
        Triple("Wyciskanie Pallofa na wyciągu", Equipment.CABLE, "brzuch"),
        Triple("Spięcia brzucha na maszynie", Equipment.MACHINE, "brzuch"),

        // ---- kettlebell ----
        Triple("Swing kettlebell", Equipment.KETTLEBELL, "pośladki"),
        Triple("Swing jednorącz kettlebell", Equipment.KETTLEBELL, "pośladki"),
        Triple("Przysiad goblet z kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Przysiad przedni z dwoma kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Martwy ciąg z kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Martwy ciąg jednonóż z kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Wykroki z kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Thruster z kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Zarzut kettlebell", Equipment.KETTLEBELL, "nogi"),
        Triple("Rwanie kettlebell", Equipment.KETTLEBELL, "barki"),
        Triple("Wyciskanie kettlebell nad głowę", Equipment.KETTLEBELL, "barki"),
        Triple("Zarzut i wyciśnięcie kettlebell", Equipment.KETTLEBELL, "barki"),
        Triple("Halo z kettlebell", Equipment.KETTLEBELL, "barki"),
        Triple("Wyciskanie kettlebell leżąc", Equipment.KETTLEBELL, "klatka"),
        Triple("Wiosłowanie kettlebell", Equipment.KETTLEBELL, "plecy"),
        Triple("Wiosłowanie naprzemienne kettlebell", Equipment.KETTLEBELL, "plecy"),
        Triple("Uginanie ramion z kettlebell", Equipment.KETTLEBELL, "biceps"),
        Triple("Prostowanie ramion nad głową z kettlebell", Equipment.KETTLEBELL, "triceps"),
        Triple("Tureckie wstawanie", Equipment.KETTLEBELL, "brzuch"),
        Triple("Wiatrak z kettlebell", Equipment.KETTLEBELL, "brzuch"),
        Triple("Russian twist z kettlebell", Equipment.KETTLEBELL, "brzuch"),

        // ---- gumy oporowe ----
        Triple("Przysiad z gumą", Equipment.BAND, "nogi"),
        Triple("Martwy ciąg z gumą", Equipment.BAND, "nogi"),
        Triple("Mostek biodrowy z minibandem", Equipment.BAND, "pośladki"),
        Triple("Odwodzenie nóg z minibandem", Equipment.BAND, "pośladki"),
        Triple("Spacer boczny z minibandem", Equipment.BAND, "pośladki"),
        Triple("Wyciskanie z gumą stojąc", Equipment.BAND, "klatka"),
        Triple("Pompki z gumą", Equipment.BAND, "klatka"),
        Triple("Wiosłowanie z gumą", Equipment.BAND, "plecy"),
        Triple("Ściąganie gumy z góry", Equipment.BAND, "plecy"),
        Triple("Podciąganie z gumą asekuracyjną", Equipment.BAND, "plecy"),
        Triple("Rozciąganie gumy w poziomie", Equipment.BAND, "barki"),
        Triple("Face pull z gumą", Equipment.BAND, "barki"),
        Triple("Wyciskanie gumy nad głowę", Equipment.BAND, "barki"),
        Triple("Unoszenie bokiem z gumą", Equipment.BAND, "barki"),
        Triple("Uginanie ramion z gumą", Equipment.BAND, "biceps"),
        Triple("Prostowanie ramion z gumą", Equipment.BAND, "triceps"),
        Triple("Wyciskanie Pallofa z gumą", Equipment.BAND, "brzuch")
    )

    /**
     * Dopisane w wersji 28 bazy: ćwiczenia pod sprzęt, który doszedł do listy
     * w Profilu - landmine, kamizelka i pas, paralletki, piłki, sanki.
     * Bez nich te pozycje byłyby na liście tylko dla ozdoby.
     */
    private val V28: List<Triple<String, Equipment, String>> = listOf(
        Triple("Wyciskanie landmine", Equipment.BARBELL, "barki"),
        Triple("Wiosłowanie landmine", Equipment.BARBELL, "plecy"),
        Triple("Przysiad landmine", Equipment.BARBELL, "nogi"),
        Triple("Hip thrust na maszynie Smitha", Equipment.MACHINE, "pośladki"),
        Triple("Podciąganie z obciążeniem", Equipment.BODYWEIGHT, "plecy"),
        Triple("Dipy z obciążeniem", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki z kamizelką", Equipment.BODYWEIGHT, "klatka"),
        Triple("Pompki na paralletkach", Equipment.BODYWEIGHT, "klatka"),
        Triple("Rzuty piłką lekarską o ścianę", Equipment.BODYWEIGHT, "nogi"),
        Triple("Rzuty piłką o podłogę", Equipment.BODYWEIGHT, "brzuch"),
        Triple("Pchanie sanek", Equipment.BODYWEIGHT, "nogi")
    )
}
