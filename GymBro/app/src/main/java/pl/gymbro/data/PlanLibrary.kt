package pl.gymbro.data

/**
 * Baza gotowych planów, jednostek i ich ćwiczeń.
 *
 * Aplikacja startuje bez planu i dotąd jedyną drogą było złożenie go od zera:
 * nazwa, jednostki, osiemnaście ćwiczeń dodawanych po jednym, każde z zakresem
 * i przerwą. Kto wie, czego chce, ten dalej może tak zrobić. Kto nie wie,
 * dostaje punkt wyjścia - na siłownię, do domu z hantlami albo bez niczego.
 *
 * **To są wzorce, nie plany.** Dodanie wzorca przepisuje go do bazy jako
 * zwykły plan użytkownika - z tą chwilą nie różni się niczym od wpisanego
 * ręcznie i da się w nim zmienić wszystko. Wzorca nic nie łączy z kopią:
 * poprawka tutaj nie rusza planów już dodanych, i o to chodzi, bo tamte są
 * czyjeś.
 *
 * W kodzie, nie w bazie, z tego samego powodu co biblioteka ćwiczeń w [Seed]:
 * to zawartość aplikacji, taka sama dla każdego. Tabela wymagałaby migracji
 * przy każdej poprawce zakresu.
 *
 * **Ciężaru wzorzec nie podaje.** Nie zna niczyjej siły, a zgadnięty byłby
 * gorszy niż pusty - ćwiczenie bez ciężaru po prostu pyta o niego przy
 * pierwszej serii.
 *
 * **Liczby są konwencją**, nie wynikiem badania: serie, zakresy i przerwy
 * to typowe wartości wyjściowe. Przy ciężarach trzymają się granic z
 * `docs/progresja.md` - nie wyżej niż 15 powtórzeń, bo powyżej doradca nie
 * rozszerza zakresu. Przy masie ciała i gumach zakres sięga 20-25: nie ma
 * tam ciężaru do dołożenia, a progresja idzie trudniejszym wariantem, który
 * w bibliotece jest osobnym ćwiczeniem. Przerwa idzie za wielkością ruchu:
 * 180 s po ciężkim wielostawowym, 120 s po pomocniczym, 90 s po izolacji,
 * 60 s w obwodzie i przy brzuchu.
 *
 * Ćwiczenia wskazywane są **nazwą z biblioteki** [Seed]. Test pilnuje, żeby
 * każda nazwa tam była - literówka dawałaby ćwiczenie-widmo bez partii.
 *
 * Treść leży w dwóch plikach: `PlanCatalogGym.kt` i `PlanCatalogHome.kt`.
 */
object PlanLibrary {

    /** Jedno ćwiczenie we wzorcu jednostki. */
    data class Item(
        val exercise: String,
        val sets: Int,
        val repsFrom: Int,
        val repsTo: Int,
        val restSec: Int
    )

    /** Jednostka: jeden trening z planu. */
    data class UnitTemplate(
        val name: String,
        /** Co ta jednostka robi - jedno zdanie pod nazwą. */
        val note: String,
        val items: List<Item>
    )

    /** Dla kogo plan jest pomyślany. */
    enum class Level(val label: String) {
        BEGINNER("początkujący"),
        INTERMEDIATE("średniozaawansowany"),
        ADVANCED("zaawansowany")
    }

    /** Cały plan: schemat i jego jednostki. */
    data class ProgramTemplate(
        val name: String,
        /** O co w nim chodzi, krótko - widać to na karcie. */
        val summary: String,
        /** Dłuższy opis do podglądu: jak to układać w tygodniu. */
        val description: String,
        /** Ile treningów w tygodniu - czasem dwie możliwości, jak 3 albo 6 przy PPL. */
        val days: List<Int>,
        val level: Level,
        val units: List<UnitTemplate>
    ) {
        val exerciseCount: Int get() = units.sumOf { it.items.size }

        /** „3 albo 6 dni", „4 dni" - do karty. */
        val daysLabel: String
            get() = days.joinToString(" albo ") + if (days.last() == 1) " dzień" else " dni"
    }

    val PROGRAMS: List<ProgramTemplate> by lazy {
        GYM_PROGRAMS + MORE_GYM_PROGRAMS + HOME_PROGRAMS + MORE_HOME_PROGRAMS
    }

    /**
     * Czy każde ćwiczenie z jednostek przechodzi filtr.
     *
     * Po ćwiczeniu, nie po rodzaju sprzętu: plan ze sztangą bez stojaków
     * przechodził filtr „sztanga", choć przysiadu nie dało się w nim zrobić.
     * Ćwiczenie spoza biblioteki nie przechodzi - wzorzec z literówką nie
     * powinien udawać, że pasuje.
     */
    private fun unitsFit(units: List<UnitTemplate>, fits: ExerciseFilter): Boolean =
        units.all { u ->
            u.items.all { item ->
                val eq = Seed.libraryEntry(item.exercise)?.second ?: return@all false
                fits(item.exercise, eq)
            }
        }

    /**
     * Czego brakuje do planu albo jednostki przy danym sprzęcie - po ludzku,
     * do ostrzeżenia na karcie.
     *
     * Najpierw rodzaje sprzętu („kettlebell"), potem konkretne pozycje
     * („stojaki lub klatka", „ławka regulowana"). Z grupy zamienników
     * pokazujemy pierwszy - „drążek do podciągania" mówi więcej niż lista
     * wszystkich rzeczy, na których da się podciągnąć.
     */
    fun missingFor(units: List<UnitTemplate>, profile: UserProfile): List<String> {
        if (profile.ownedEquipment == null) return emptyList()
        val items = units.flatMap { it.items }
        val rodzaje = equipmentOf(units).filterNot { profile.canDo(it) }
            .map { if (it == Equipment.DUMBBELL_SINGLE) Equipment.DUMBBELL_PAIR else it }
            .distinct()
            .map { gearWord(it) }
        val pozycje = items.flatMap { Seed.missingNeeds(it.exercise, profile.ownedGear) }
            .map { group -> group.first().label.lowercase() }
            .distinct()
        return rodzaje + pozycje
    }

    /**
     * Rodzaje sprzętu, których wymaga plan albo jednostka.
     *
     * Na tym stoi cały dobór planów. Nie ma tu żadnego „domu" ani „siłowni":
     * pierwsza wersja zgadywała miejsce ze sprzętu (bez sztangi to dom, potem
     * bez wyciągu to dom) i za każdym razem trafiała obok kogoś - dom bywa
     * z samym drążkiem, a bywa ze sztangą i wyciągiem. Jedyne, co da się
     * powiedzieć na pewno, to czego plan wymaga; czy to masz, wie Profil.
     */
    fun equipmentOf(units: List<UnitTemplate>): Set<Equipment> =
        units.flatMap { it.items }
            .mapNotNull { Seed.libraryEntry(it.exercise)?.second }
            .toSet()

    /**
     * Sprzęt planu po ludzku: „sztanga, hantle, wyciąg".
     *
     * Para hantli i jeden hantel to tu jedno słowo - na karcie planu chodzi
     * o to, co trzeba mieć, a kto ma parę, ten ma i jeden.
     */
    fun gearSummary(units: List<UnitTemplate>): String = describeGear(equipmentOf(units))

    /** To samo dla dowolnego zestawu - np. sprzętu z Profilu w kreatorze. */
    fun describeGear(equipment: Set<Equipment>): String =
        equipment
            // Masa ciała na końcu: „kettlebell, masa ciała" mówi, co trzeba
            // mieć; „masa ciała, kettlebell" brzmiało, jakby kettlebell był
            // dodatkiem.
            .sortedBy { if (it == Equipment.BODYWEIGHT) Int.MAX_VALUE else it.ordinal }
            .map { if (it == Equipment.DUMBBELL_SINGLE) Equipment.DUMBBELL_PAIR else it }
            .distinct()
            .joinToString(", ") { gearWord(it) }

    private fun gearWord(e: Equipment): String = when (e) {
        Equipment.BARBELL -> "sztanga"
        Equipment.DUMBBELL_PAIR, Equipment.DUMBBELL_SINGLE -> "hantle"
        Equipment.CABLE -> "wyciąg"
        Equipment.MACHINE -> "maszyny"
        Equipment.BODYWEIGHT -> "masa ciała"
        Equipment.KETTLEBELL -> "kettlebell"
        Equipment.BAND -> "gumy"
    }

    // ---- Wyszukiwanie ------------------------------------------------------

    /**
     * Tekst, w którym szuka wyszukiwarka planów - złożony raz.
     *
     * Obejmuje nazwy ćwiczeń, ich partie i sprzęt, nie tylko nazwę planu: kto
     * wpisuje „podciąganie", „pośladki" albo „kettlebell", szuka planu, w którym
     * to jest. Plan na samą masę ciała dostaje jeszcze „bez sprzętu" - tak się
     * takiego szuka, choć żadne ćwiczenie się tak nie nazywa.
     */
    private val PROGRAM_INDEX: List<Pair<ProgramTemplate, String>> by lazy {
        PROGRAMS.map { p ->
            p to TextSearch.fold(
                listOf(p.name, p.summary, p.description, p.level.label, gearWords(p.units))
                    .plus(p.units.flatMap { unitWords(it) })
                    .joinToString(" ")
            )
        }
    }

    private val UNIT_INDEX: List<Triple<ProgramTemplate, UnitTemplate, String>> by lazy {
        PROGRAMS.flatMap { p ->
            p.units.map { u ->
                Triple(
                    p, u,
                    TextSearch.fold(
                        (listOf(p.name, gearWords(listOf(u))) + unitWords(u)).joinToString(" ")
                    )
                )
            }
        }
    }

    private fun gearWords(units: List<UnitTemplate>): String {
        val sprzet = equipmentOf(units)
        val bezSprzetu = if (sprzet.all { it == Equipment.BODYWEIGHT }) " bez sprzętu" else ""
        return gearSummary(units) + bezSprzetu
    }

    private fun unitWords(u: UnitTemplate): List<String> =
        listOf(u.name, u.note) + u.items.flatMap { item ->
            listOfNotNull(item.exercise, Seed.libraryEntry(item.exercise)?.third)
        }

    /**
     * Plany pasujące do zapytania i filtrów.
     *
     * @param fits warunek na każde ćwiczenie w planie - w praktyce
     *   `profile::canDoExercise`. null to ścieżka prosta: aplikacja zakłada dostęp do
     *   wszystkiego i niczego nie odsiewa.
     */
    fun search(
        query: String,
        days: Int? = null,
        level: Level? = null,
        fits: ExerciseFilter? = null
    ): List<ProgramTemplate> {
        val words = TextSearch.words(query)
        return PROGRAM_INDEX.filter { (p, text) ->
            (days == null || days in p.days) &&
                (level == null || p.level == level) &&
                (fits == null || unitsFit(p.units, fits)) &&
                TextSearch.matches(words, text)
        }.map { it.first }
    }

    /**
     * Wynik kreatora: plany i to, z czego trzeba było zrezygnować.
     *
     * @property droppedLevel poziom zdjęty, bo z nim nic nie pasowało
     * @property droppedDays liczba dni zdjęta, bo i bez poziomu nic nie pasowało
     */
    data class Match(
        val plans: List<ProgramTemplate>,
        val droppedLevel: Boolean = false,
        val droppedDays: Boolean = false
    )

    /**
     * Plany dla odpowiedzi z kreatora, z poluzowaniem zamiast pustej listy.
     *
     * Odpowiedzi zadaje się po kolei, nie widząc wyników - a nie każde
     * połączenie istnieje: pięć dni w tygodniu to w bazie same plany dla
     * zaawansowanych. Pusty ekran na końcu kreatora kazałby zgadywać, które
     * pytanie zawiniło i wracać po kolei. Zamiast tego zdejmujemy najpierw
     * poziom (to jak plan jest ułożony, liczy się bardziej niż to, jak jest
     * trudny - łatwiej zmniejszyć ciężar niż dorobić dzień w tygodniu), potem
     * dni. Sprzętu nie zdejmujemy nigdy: plan, którego nie da się zrobić,
     * nie jest bliską propozycją.
     */
    fun match(
        days: Int?,
        level: Level?,
        fits: ExerciseFilter?,
        query: String = ""
    ): Match {
        search(query, days, level, fits).let { if (it.isNotEmpty()) return Match(it) }
        if (level != null) {
            search(query, days, null, fits).let {
                if (it.isNotEmpty()) return Match(it, droppedLevel = true)
            }
        }
        if (days != null) {
            search(query, null, level, fits).let {
                if (it.isNotEmpty()) return Match(it, droppedDays = true)
            }
        }
        return Match(
            search(query, null, null, fits),
            droppedLevel = level != null,
            droppedDays = days != null
        )
    }

    /**
     * Jednostki pasujące do zapytania, każda razem z planem, z którego pochodzi.
     *
     * @param fits jak w [search], tylko dla sprzętu samej jednostki - z planu
     *   ze sztangą da się wziąć dzień, który jej nie wymaga.
     */
    fun searchUnits(
        query: String,
        fits: ExerciseFilter? = null
    ): List<Pair<ProgramTemplate, UnitTemplate>> {
        val words = TextSearch.words(query)
        return UNIT_INDEX.filter { (_, u, text) ->
            (fits == null || unitsFit(listOf(u), fits)) &&
                TextSearch.matches(words, text)
        }.map { it.first to it.second }
    }

    /**
     * Nazwa, której jeszcze nie ma na liście.
     *
     * Baza nie pilnuje unikalności nazw planów ani jednostek, więc drugi
     * dodany „PPL" byłby nie do odróżnienia od pierwszego - i na ekranie
     * treningu, i na zegarku. Numer dopisujemy od dwójki.
     */
    fun uniqueName(base: String, taken: Collection<String>): String {
        val zajete = taken.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in zajete) return base
        var n = 2
        while ("$base $n".lowercase() in zajete) n += 1
        return "$base $n"
    }
}

// ---- Skróty do pisania katalogu ----------------------------------------------
//
// Katalog ma kilkaset pozycji i ma się dać czytać jak rozpiska z kartki:
// nazwa, serie, zakres. Przerwę niesie nazwa skrótu, nie liczba w każdym
// wierszu - przy liczbach co trzecia była inna dla tego samego rodzaju ruchu.

/** Ciężki wielostawowy - 180 s. */
internal fun heavy(exercise: String, sets: Int, from: Int, to: Int) =
    PlanLibrary.Item(exercise, sets, from, to, 180)

/** Pomocniczy wielostawowy albo trudny wariant z masą ciała - 120 s. */
internal fun aux(exercise: String, sets: Int, from: Int, to: Int) =
    PlanLibrary.Item(exercise, sets, from, to, 120)

/** Izolacja i zwykłe ćwiczenia z masą ciała - 90 s. */
internal fun iso(exercise: String, sets: Int, from: Int, to: Int) =
    PlanLibrary.Item(exercise, sets, from, to, 90)

/** Brzuch i obwód - 60 s. */
internal fun core(exercise: String, sets: Int, from: Int, to: Int) =
    PlanLibrary.Item(exercise, sets, from, to, 60)

internal fun unit(name: String, note: String, vararg items: PlanLibrary.Item) =
    PlanLibrary.UnitTemplate(name, note, items.toList())

/**
 * Filtr ćwiczeń: nazwa i rodzaj sprzętu → czy da się zrobić.
 *
 * Nazwa, bo od niej zależą wymagania ([Seed.needsOf]); rodzaj, bo własne
 * ćwiczenia użytkownika nie mają wymagań i liczy się tylko on.
 */
typealias ExerciseFilter = (name: String, equipment: Equipment) -> Boolean
