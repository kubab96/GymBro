package pl.gymbro.data

/**
 * Model dziedzinowy aplikacji.
 *
 * Te typy nie wiedzą nic o SQLite ani o Compose - są czystym Kotlinem. Dzięki temu
 * warstwę przechowywania (dziś SQLite, kiedyś równie dobrze Room albo backend)
 * da się wymienić bez dotykania logiki i interfejsu.
 */

enum class Equipment {
    BARBELL,
    DUMBBELL_PAIR,
    DUMBBELL_SINGLE,
    CABLE,
    MACHINE,
    BODYWEIGHT,

    /**
     * Kettlebell - gotowy ciężar w jednej ręce, nie składany z talerzy.
     *
     * Osobno od hantla, bo hantel przy ograniczonych obciążeniach liczy się
     * z talerzy na uchwycie - a kettlebella nikt nie skręca z krążków.
     * Liczony jak maszyna: stały krok (4 kg, patrz [Seed.kettlebellSteps]).
     */
    KETTLEBELL,

    /** Gumy oporowe. Bez ciężaru w kilogramach - opór idzie z koloru gumy. */
    BAND;

    /** Czy ciężar tego sprzętu składamy z talerzy, czy bierzemy jako gotową liczbę. */
    val isPlateLoaded: Boolean
        get() = this == BARBELL || this == DUMBBELL_PAIR || this == DUMBBELL_SINGLE

    /** Jak opisać ciężar w interfejsie. */
    val weightUnitLabel: String
        get() = when (this) {
            DUMBBELL_PAIR, DUMBBELL_SINGLE -> "kg / hantel"
            BODYWEIGHT -> "masa ciała"
            else -> "kg"
        }

    companion object {
        fun fromName(s: String?): Equipment =
            entries.firstOrNull { it.name == s } ?: MACHINE
    }
}

data class Exercise(
    val id: Long = 0,
    val name: String,
    val equipment: Equipment,
    val muscleGroup: String,
    val isCustom: Boolean = false,

    /**
     * Krok ciężaru przy sprzęcie bez talerzy. null = [DEFAULT_STEP_KG].
     *
     * Przy sztandze i hantlach krok wynika z tego, co leży na siłowni, i liczy
     * go [pl.gymbro.gym.PlateCalculator]. Stos wyciągu i maszyna nie mają
     * talerzy, więc ich progi zna tylko tabliczka przy maszynie - i tylko
     * stamtąd da się je tu przepisać. Bez tego pola każdy wyciąg chodził
     * co 2,5 kg i ciężaru 28,4 kg nie dawało się ustawić w ogóle.
     *
     * Przy ćwiczeniu, a nie przy pozycji planu: ta sama maszyna ma ten sam
     * krok w każdej jednostce, w której się pojawia.
     */
    val stepKg: Double? = null
)

/**
 * Program treningowy - zbiór jednostek tworzących całość.
 *
 * Ten poziom istnieje, bo plan to nie pojedynczy trening, tylko schemat:
 * FBW składa się z A, B i C, PPL z Push, Pull i Legs. Bez tego rozróżnienia
 * jednostki z różnych schematów leżałyby w jednym worku, a przełączenie się
 * na inny program wymagałoby kasowania poprzedniego.
 *
 * Aktywny jest dokładnie jeden - jego jednostki dostaje zegarek i od niego
 * zaczyna ekran treningu, na którym da się wybrać też inny.
 *
 * W interfejsie nazywa się „plan treningowy"; patrz `ui/ProgramListScreen.kt`.
 */
data class Program(
    val id: Long = 0,
    val name: String,
    val note: String? = null,
    val position: Int = 0,
    val isActive: Boolean = false
)

/** Jednostka treningowa w rotacji - np. A, B, C albo Push, Pull, Legs. */
data class Plan(
    val id: Long = 0,
    val name: String,
    val position: Int,
    val programId: Long? = null,
    /** Dzień tygodnia 1-7; null, gdy jednostka nie ma stałego dnia. */
    val day: Int? = null
)

/** Ćwiczenie wewnątrz planu, razem z zadanymi seriami i ciężarem. */
/**
 * Propozycja progresji czekajaca przy pozycji planu.
 *
 * Trzymana w bazie, a nie liczona w locie: ma przetrwac zamkniecie aplikacji
 * i czekac na decyzje. Kolejny trening nadpisuje poprzednia, wiec zawsze jest
 * najwyzej jedna i zawsze dotyczy ostatniej sesji.
 */
data class StoredAdvice(
    /** Nazwa wariantu z ProgressionAdvisor.Kind. */
    val kind: String,
    val weightKg: Double? = null,
    val repsTo: Int? = null,
    val sets: Int? = null,
    val reason: String = "",
    /** Co zrobiles ostatnio, zeby bylo z czym porownac. */
    val last: String = "",
    val sessionId: Long = 0,
    /** Czy plakietka przy zakladce zostala juz zobaczona. */
    val seen: Boolean = false
)

data class PlanItem(
    val id: Long = 0,
    val planId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetKg: Double,
    val sets: Int,
    val repsFrom: Int,
    val repsTo: Int,
    val restSec: Int,
    // Denormalizowane dla wygody wyświetlania - wypełniane przy odczycie.
    val exerciseName: String = "",
    val equipment: Equipment = Equipment.MACHINE,

    /** null = dziedziczy z profilu. */
    val goal: TrainingGoal? = null,

    /** Wyciszenie propozycji progresji dla tej pozycji. */
    val noProgress: Boolean = false,

    /** Krok ciężaru z ćwiczenia - denormalizowany, patrz [Exercise.stepKg]. */
    val stepKg: Double? = null,

    /** Czekajaca propozycja zmiany. null = nie ma zadnej. */
    val advice: StoredAdvice? = null
) {
    /**
     * Ustawienia są opcjonalne. Zero oznacza „nieustawione", bo żadna z tych
     * wartości nie ma sensu jako zalecenie: nie ma ćwiczenia z zerowym ciężarem
     * ani zalecenia zerowej przerwy. Dzięki temu pola są opcjonalne bez zmiany
     * schematu bazy i bez ryzykownej migracji kolumn NOT NULL.
     */
    val hasTargetWeight: Boolean get() = targetKg > 0.0
    val hasRepRange: Boolean get() = repsFrom > 0 && repsTo > 0
    val hasRest: Boolean get() = restSec > 0

    /** Krok ciężaru do plusa i minusa, gdy talerze nie rozstrzygają. */
    val step: Double get() = stepOrDefault(stepKg)

    val repRange: String get() = if (repsFrom == repsTo) "$repsFrom" else "$repsFrom-$repsTo"

    /** Krótki opis zadania - pomija to, czego nie ustawiono. */
    val prescription: String
        get() = buildList {
            add("$sets ${if (sets == 1) "seria" else "serii"}")
            if (hasRepRange) add("$repRange powt.")
            if (hasRest) add("przerwa ${restSec / 60}:%02d".format(restSec % 60))
        }.joinToString(" · ")
}

/** Jedna odbyta sesja treningowa. */
data class Session(
    val id: Long = 0,
    val planId: Long?,
    val startedAt: Long,
    val endedAt: Long? = null,
    val note: String? = null,
    /** Chwila wstrzymania; null, gdy trening idzie. */
    val pausedAt: Long? = null,
    /** Suma już odstanych przerw. */
    val pausedMs: Long = 0,
    /**
     * Znacznik nadany przez zegarek, gdy trening zaczął się bez telefonu.
     * null dla sesji założonych tutaj.
     */
    val localId: String? = null,
    /** Czy policzono już dla tej sesji propozycje progresji. */
    val advised: Boolean = false
) {
    val isActive: Boolean get() = endedAt == null
    val isPaused: Boolean get() = pausedAt != null

    /**
     * Czas treningu bez przerw.
     *
     * Liczony od startu, pomniejszony o to, co już odstane, i o trwającą
     * przerwę. Bez tego wyjście z sali na kwadrans zawyżało każdy czas
     * i każde podsumowanie.
     */
    fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
        val end = endedAt ?: now
        val running = pausedAt?.let { now - it } ?: 0L
        return (end - startedAt - pausedMs - running).coerceAtLeast(0L)
    }
}

/** Pojedyncza seria. To jest najczęściej zapisywany rekord w całej aplikacji. */
data class SetEntry(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    /** Zapas powtórzeń w rezerwie. null = nie podano. */
    val rir: Int? = null,
    val isWarmup: Boolean = false,
    val doneAt: Long
) {
    /**
     * Szacowany ciężar maksymalny, wzór Epleya.
     * Przy jednym powtórzeniu zwraca sam ciężar, co jest zgodne z definicją.
     */
    val estimated1RM: Double
        get() = if (reps <= 1) weightKg else weightKg * (1.0 + reps / 30.0)

    val tonnage: Double get() = weightKg * reps
}

/**
 * Skład ciała. Każda wartość osobno opcjonalna, bo źródła bywają niekompletne.
 *
 * Nie liczymy tych wartości sami: wymagałoby to prawdziwej bioimpedancji, a waga
 * podaje ją jako stałą wartość zastępczą. To są liczby przepisane z aplikacji
 * producenta albo pobrane z Health Connect.
 */
data class BodyComposition(
    /** Tkanka tłuszczowa w procentach masy ciała. */
    val bodyFatPct: Double? = null,
    /** Zawartość wody w procentach. */
    val waterPct: Double? = null,
    val muscleMassKg: Double? = null,
    val boneMassKg: Double? = null,
    /** Tłuszcz trzewny - zwykle poziom w skali 1-30, nie jednostka fizyczna. */
    val visceralFat: Double? = null,
    /** Podstawowa przemiana materii w kcal. */
    val bmrKcal: Double? = null,
    val proteinPct: Double? = null,
    /** Wiek metaboliczny w latach. */
    val metabolicAge: Double? = null
) {
    val isEmpty: Boolean
        get() = bodyFatPct == null && waterPct == null && muscleMassKg == null &&
            boneMassKg == null && visceralFat == null && bmrKcal == null &&
            proteinPct == null && metabolicAge == null

    companion object {
        val EMPTY = BodyComposition()
    }
}

/** Pomiar masy ciała - z wagi po BLE, wpisany ręcznie albo z Health Connect. */
data class BodyMeasurement(
    val id: Long = 0,
    val measuredAt: Long,
    val weightKg: Double,
    val impedanceOhm: Double? = null,
    val source: String,
    val composition: BodyComposition = BodyComposition.EMPTY
)

/**
 * Miejsce pomiaru obwodu.
 *
 * Instrukcje nie są ozdobnikiem: przy obwodach cała wartość pomiaru leży
 * w powtarzalności. Dwa centymetry różnicy biorą się częściej ze zmiany
 * miejsca przyłożenia taśmy niż ze zmiany ciała.
 */
enum class MeasurementSite(
    val label: String,
    val howTo: String,
    /** Czy wartość jest potrzebna do oszacowania tkanki tłuszczowej. */
    val usedForBodyFat: Boolean = false
) {
    NECK(
        "Szyja",
        "Tuż pod krtanią, taśma lekko opada z przodu. Nie zaciskaj - ma przylegać, " +
            "nie wcinać się. Patrz prosto przed siebie, barki luźno.",
        usedForBodyFat = true
    ),
    CHEST(
        "Klatka",
        "Na wysokości sutków, taśma poziomo dookoła. Mierz na końcu normalnego " +
            "wydechu, bez nabierania powietrza.",
    ),
    WAIST(
        "Talia",
        "W najwęższym miejscu, zwykle tuż nad pępkiem. Nie wciągaj brzucha. " +
            "Mierz na końcu spokojnego wydechu, taśma poziomo.",
        usedForBodyFat = true
    ),
    HIPS(
        "Biodra",
        "W najszerszym miejscu pośladków, stopy złączone. Taśma poziomo, " +
            "sprawdź w lustrze, czy z tyłu nie opada.",
    ),
    ARM(
        "Ramię",
        "W połowie odległości między barkiem a łokciem, ramię zwieszone luźno " +
            "wzdłuż ciała. Nie napinaj. Mierz zawsze tę samą rękę.",
    ),
    THIGH(
        "Udo",
        "Tuż pod pośladkiem, w najgrubszym miejscu. Ciężar rozłożony równo " +
            "na obie nogi. Mierz zawsze tę samą nogę.",
    );
}

/** Pojedynczy pomiar obwodu. */
data class BodyMetric(
    val id: Long = 0,
    val measuredAt: Long,
    val site: MeasurementSite,
    val valueCm: Double
)

enum class Sex { MALE, FEMALE }

/**
 * Co ma rosnac - decyduje o wyborze miedzy ciezarem a objetoscia.
 *
 * Rozdzial jest dobrze udokumentowany: przy zrownanych seriach ciezkie
 * obciazenia daja wieksza sile, a umiarkowane wieksza hipertrofie
 * (Schoenfeld 2016). Dokladanie serii dziala mocno na mase, a na sile
 * praktycznie wcale - brak roznicy miedzy jedna a pieciu seriami
 * (Schoenfeld 2018).
 */
enum class TrainingGoal { STRENGTH, HYPERTROPHY, MIXED }

/**
 * Faza sylwetkowa - nie zmienia tego, co podnosic, tylko jak czytac zastoj.
 *
 * Na redukcji utrzymanie ciezaru jest sukcesem, wiec nie wolno go zglaszac
 * jako problemu ani dokladac objetosci. Zadne ze zrodel nie badalo progresji
 * w deficycie; to wniosek z ograniczonej regeneracji, nie cytat.
 */
enum class Phase { CUT, MAINTAIN, BULK }

/**
 * Profil użytkownika: dane do wzorów oraz to, czym dysponuje.
 *
 * Domyślnie **nic nie jest ograniczone** - zakładamy pełną siłownię i dowolne
 * obciążenia. Ograniczenia są świadomą decyzją, a nie stanem wyjściowym:
 * aplikacja nie ma prawa zakładać, że czegoś nie masz.
 */
data class UserProfile(
    val heightCm: Double? = null,
    val sex: Sex = Sex.MALE,
    val birthYear: Int? = null,

    /**
     * Czy sprzęt i obciążenia są dostosowane do tego, co faktycznie masz.
     *
     * false = pełna siłownia i dowolny ciężar. Jeden przełącznik zamiast
     * dwóch osobnych („mam wszystko" i „dowolny ciężar"): oba pytały o to
     * samo - czy trenujesz tam, gdzie jest wszystko - i dawało się ustawić
     * je sprzecznie. W bazie siedzi w kolumnie `limited_weights`, bo to ona
     * do tej pory decydowała o liczeniu z talerzy.
     */
    val customGear: Boolean = false,

    /**
     * Wybrany sprzęt. Liczy się tylko przy [customGear].
     *
     * Pusty zbiór znaczy „bez ograniczeń", nie „nic nie mam": inaczej
     * włączenie dostosowania, zanim cokolwiek się doda, chowałoby wszystkie
     * ćwiczenia. Zbiór zostaje w bazie także przy wyłączonym przełączniku,
     * żeby wyłączenie na próbę nie kasowało listy.
     */
    val ownedGear: Set<Gear> = emptySet(),

    /** Domyslny cel treningowy; pozycja planu moze go nadpisac. */
    val trainingGoal: TrainingGoal = TrainingGoal.MIXED,

    val phase: Phase = Phase.MAINTAIN,

    /**
     * Tempo zmiany masy ciała w procentach na tydzień.
     *
     * Procent, nie kilogramy: ten sam ubytek 0.5 kg na tydzień jest
     * umiarkowany przy 100 kg i agresywny przy 55 kg. null oznacza wartość
     * domyślną dla fazy - patrz `docs/kalorie.md`.
     */
    val dietRatePct: Double? = null,

    /**
     * Dzień, od którego liczymy oszacowanie wydatku od nowa.
     *
     * Pierwszy tydzień deficytu to ubytek wody, nie tłuszczu, i bez
     * odcięcia zawyżałby nachylenie trendu na osiem tygodni w przód.
     */
    val tdeeResetDay: Long? = null
) {
    val age: Int?
        get() = birthYear?.let { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - it }

    /**
     * Rodzaje ćwiczeń, które da się wykonać. null oznacza „wszystko dostępne".
     *
     * Masa ciała wchodzi zawsze - pompki nie wymagają niczego z listy.
     */
    val ownedEquipment: Set<Equipment>?
        get() = if (!customGear || ownedGear.isEmpty()) null
        else ownedGear.flatMapTo(mutableSetOf(Equipment.BODYWEIGHT)) { it.covers }

    /** Czy ten rodzaj sprzętu jest w zasięgu. */
    fun canDo(equipment: Equipment): Boolean =
        ownedEquipment?.contains(equipment) ?: true

    /**
     * Czy to konkretne ćwiczenie da się zrobić - rodzaj sprzętu plus to, czego
     * wymaga samo ćwiczenie ([Seed.needsOf]): stojaki przy przysiadzie, drążek
     * przy podciąganiu.
     *
     * Bez listy sprzętu zawsze tak - to ścieżka „mam dostęp do wszystkiego".
     */
    fun canDoExercise(name: String, equipment: Equipment): Boolean =
        ownedEquipment == null || (canDo(equipment) && Seed.needsMet(name, ownedGear))
}

/**
 * Trening zarejestrowany poza aplikacją - z zegarka, przez Health Connect.
 *
 * [externalId] pochodzi ze źródła i jest unikalny, dzięki czemu synchronizację
 * można powtarzać dowolnie często bez tworzenia duplikatów.
 */
data class ExternalSession(
    val id: Long = 0,
    val externalId: String,
    val source: String,
    val type: String,
    val title: String? = null,
    val startedAt: Long,
    val endedAt: Long,
    val distanceM: Double? = null,
    val energyKcal: Double? = null,
    val hrAvg: Double? = null,
    val hrMax: Double? = null
) {
    val durationMin: Long get() = (endedAt - startedAt) / 60_000

    /**
     * Czy sesja ma już dane z agregacji (dystans, kalorie, tętno).
     *
     * Decyduje o tym, czy przy kolejnej synchronizacji trzeba ją jeszcze raz
     * odpytywać - a każde takie pytanie zużywa przydział Health Connect.
     */
    val isEnriched: Boolean
        get() = distanceM != null || energyKcal != null || hrAvg != null

    /** Tempo w minutach na kilometr. Null, gdy nie ma dystansu. */
    val paceMinPerKm: Double?
        get() {
            val km = (distanceM ?: return null) / 1000.0
            if (km < 0.1) return null
            return durationMin / km
        }
}

/**
 * Zakończony trening w skrócie, razem z tym, co o nim wie zegarek.
 *
 * Łączy dwie połowy tego samego treningu: Twój zapis mówi, co i ile dźwignąłeś,
 * zegarek - jak długo to trwało i przy jakim tętnie. [watch] bywa puste, bo nie
 * każdy trening odbywa się z zegarkiem na ręce.
 */
data class SessionSummary(
    val sessionId: Long,
    val planName: String?,
    val startedAt: Long,
    val endedAt: Long,
    val workingSets: Int,
    val tonnage: Double,
    val watch: ExternalSession? = null,
    /**
     * Plan (FBW, PPL), do którego należy jednostka tego treningu.
     *
     * null, gdy jednostkę albo cały plan skasowano - trening zostaje wtedy
     * bez przynależności i historia pokazuje go przy każdym planie, bo
     * inaczej nie byłoby skąd go otworzyć.
     */
    val programId: Long? = null
) {
    /** Czas trwania wg zegarka, a w jego braku wg zapisu w aplikacji. */
    val durationMin: Long
        get() = watch?.durationMin ?: ((endedAt - startedAt) / 60_000)
}

/** Rodzaj metryki dziennej z zegarka. */
enum class MetricKind(val label: String, val unit: String) {
    HRV_RMSSD("HRV", "ms"),
    RESTING_HR("Tętno spoczynkowe", "bpm"),
    SLEEP_MINUTES("Sen", "min"),
    STEPS("Kroki", ""),
    VO2MAX("VO2max", "")
}

/** Jedna wartość dzienna. [day] to numer dnia od epoki, nie znacznik czasu. */
data class DailyMetric(
    val day: Long,
    val kind: MetricKind,
    val value: Double
)

data class Plate(
    val id: Long = 0,
    val kg: Double,
    val count: Int,
    val fitsBarbell: Boolean = true
)

/**
 * Sprzęt, jakim dysponujesz. Wszystko edytowalne, nic zaszyte w kodzie.
 *
 * [unlimited] oznacza, że obciążenie nie jest ograniczone talerzami - tak jest
 * domyślnie i tak działa siłownia komercyjna. Kalkulator załadunku jest wtedy
 * wyłączony, a ciężar zmienia się stałym krokiem. Tak samo jest przy
 * dostosowanym sprzęcie bez ani jednego talerza na liście: pusta lista to
 * „nie podałem", a nie „mam sam gryf".
 */
data class Inventory(
    val barKg: Double,
    val plates: List<Plate>,
    val unlimited: Boolean = false
)

/**
 * Jedna pozycja w historii aktywności.
 *
 * Siła i bieganie to dwie połowy tego samego treningu w skali tygodnia, więc
 * w historii nie ma powodu ich rozdzielać - dzielenie na dwie listy wymuszało
 * składanie kolejności w głowie. Źródła są jednak różne: siłę zapisujesz sam,
 * bieg przychodzi z zegarka, i tylko dlatego te dwa warianty w ogóle istnieją.
 */
sealed interface Activity {
    val at: Long

    /** Trening zapisany w aplikacji; [SessionSummary.watch] może dołożyć tętno. */
    data class Strength(val summary: SessionSummary) : Activity {
        override val at: Long get() = summary.startedAt
    }

    /**
     * Aktywność znana wyłącznie z zegarka.
     *
     * Bieg, marsz, rower, trening dowolny - wszystko, czego nie zapisujesz
     * ręcznie, bo zegarek zrobił to za Ciebie. Trafia tu także trening siłowy
     * z zegarka, jeśli nie udało się go skleić z żadnym Twoim zapisem: to
     * znaczy, że ćwiczyłeś, ale nie zalogowałeś serii.
     */
    data class Watch(val session: ExternalSession) : Activity {
        override val at: Long get() = session.startedAt
    }
}
