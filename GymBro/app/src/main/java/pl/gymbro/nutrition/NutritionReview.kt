package pl.gymbro.nutrition

import android.content.Context
import pl.gymbro.data.Bia
import pl.gymbro.data.BodyFat
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.DiaryRepository
import pl.gymbro.data.FoodRepository
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.MetricKind
import pl.gymbro.data.MetricsRepository
import pl.gymbro.data.NutritionTarget
import pl.gymbro.data.NutritionTargetRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.RecipeRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.StoredTdee
import pl.gymbro.data.TdeeRepository
import pl.gymbro.data.epochDayOf
import kotlin.math.abs

/**
 * Repozytoria, których potrzebuje zakładka Kalorie.
 *
 * Dziesięć osobnych parametrów w sygnaturze [NutritionReview.refresh] byłoby
 * nieczytelne, a sam ekran i tak musi je wszystkie utworzyć. Jeden uchwyt
 * zamiast dziesięciu - tworzony raz, przy pierwszym wejściu w zakładkę.
 */
class NutritionRepos(context: Context) {
    val foods = FoodRepository(context)
    val diary = DiaryRepository(context)
    val recipes = RecipeRepository(context)
    val targets = NutritionTargetRepository(context)
    val tdee = TdeeRepository(context)
    val body = BodyRepository(context)
    val metrics = MetricsRepository(context)
    val profile = ProfileRepository(context)
    val health = HealthRepository(context)
    val sessions = SessionRepository(context)
}

/**
 * Zbiera dane, których potrzebują [Bmr], [Tdee] i [Targets], i oddaje gotowy
 * obraz dnia do pokazania.
 *
 * Rozdzielone od samej logiki celowo, dokładnie jak [pl.gymbro.gym.ProgressionReview]
 * od doradcy progresji: tam reguły, tu ładowanie. Dzięki temu liczby
 * z `docs/kalorie.md` sprawdza się na wymyślonych danych, bez emulatora.
 *
 * Wołać z wątku roboczego.
 */
object NutritionReview {

    /**
     * O ile musi zmienić się cel, żeby warto było zapisać nowy.
     *
     * Oszacowanie wydatku drga o kilka kalorii z każdym nowym wpisem
     * w dzienniku. Bez tego progu historia celów zapełniłaby się wierszami
     * „2280 zamiast 2278" i przestałaby pokazywać to, po co istnieje:
     * momenty, w których cel faktycznie się zmienił.
     */
    private const val TARGET_KCAL_STEP = 20.0
    private const val TARGET_GRAM_STEP = 3.0

    /** Ile ostatnich sesji przeglądamy, licząc treningi w oknie. */
    private const val SESSION_LOOKBACK = 120

    /** Co wie aplikacja o dzisiejszym dniu. */
    data class Today(
        val day: Long,
        val summary: Portions.DaySummary,

        /**
         * Wpisy dnia, w kolejności posiłków.
         *
         * Oddawane razem z podsumowaniem, bo ekran potrzebuje jednego
         * i drugiego, a pobrane są tym samym zapytaniem. Drugi odczyt tych
         * samych wierszy po to, żeby je wypisać, byłby czystą stratą.
         */
        val entries: List<pl.gymbro.data.FoodLogEntry> = emptyList(),

        /** Cel obowiązujący w tym dniu; null, gdy nie ma z czego go policzyć. */
        val target: NutritionTarget?,

        /** Oszacowanie wydatku; null, gdy brakuje danych w profilu. */
        val tdee: Tdee.Estimate?,

        /** Którym wzorem policzono przemianę spoczynkową. */
        val restingSource: Bmr.Source?,

        val restingKcal: Double? = null,
        val fiberTargetG: Double? = null,
        val weightKg: Double? = null,
        val leanKg: Double? = null,

        /** Czego brakuje w profilu, żeby dało się cokolwiek policzyć. */
        val missingProfile: String? = null
    ) {
        val remaining: Portions.Remaining?
            get() = target?.let { Portions.remaining(summary, it) }
    }

    /**
     * Obraz dnia, razem z odświeżeniem oszacowania wydatku i celu.
     *
     * Nazwa mówi „refresh", a nie „load", bo ta funkcja **zapisuje**:
     * oszacowanie wydatku na dziś (żeby dało się narysować krzywą) i nowy cel,
     * jeśli zmienił się na tyle, że warto. Ukrywanie zapisu pod nazwą
     * sugerującą czysty odczyt byłoby pułapką dla następnego czytającego.
     */
    fun refresh(day: Long, repos: NutritionRepos): Today {
        val entries = repos.diary.entries(day)
        val summary = Portions.summarize(day, entries)

        val profile = repos.profile.get()
        val latest = repos.body.latest()
        val weightKg = latest?.weightKg

        // Tkanka tłuszczowa z obwodów, nie z wagi - tak liczy ją ekran Masy
        // ciała, a dwa ekrany pokazujące różny procent tłuszczu dla tego
        // samego dnia byłyby gorsze niż brak tej liczby w ogóle. Wartość
        // z wagi zostaje jako zapas, gdy obwodów nie ma.
        //
        // Z oporu bierzemy tylko pomiar ręka - stopa. Stopa - stopa liczy się
        // wzorem pożyczonym z innej drogi prądu: kierunek zmian jest prawdziwy,
        // ale przesunięcie wartości bezwzględnej szłoby prosto w przemianę
        // materii, a potem w cel kalorii. Wzór producenta wagi jest dalej
        // ostatni - nie wiemy, z czego go liczy.
        val girths = repos.metrics.latestPerSite()
        val bodyFatPct = BodyFat.navy(
            sex = profile.sex,
            heightCm = profile.heightCm,
            waistCm = girths[MeasurementSite.WAIST]?.valueCm,
            neckCm = girths[MeasurementSite.NECK]?.valueCm,
            hipsCm = girths[MeasurementSite.HIPS]?.valueCm
        ) ?: latest?.let { Bia.of(it, profile) }
            ?.takeIf { it.basis == Bia.Basis.DIRECT }?.fatPct
            ?: latest?.composition?.bodyFatPct

        val resting = Bmr.resting(
            weightKg = weightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            sex = profile.sex,
            bodyFatPct = bodyFatPct
        )

        if (resting == null || weightKg == null) {
            return Today(
                day = day,
                summary = summary,
                entries = entries,
                target = repos.targets.current(day),
                tdee = null,
                restingSource = null,
                weightKg = weightKg,
                missingProfile = missingFor(weightKg, profile.heightCm, profile.age)
            )
        }

        val leanKg = bodyFatPct?.let { Bmr.leanMass(weightKg, it) }

        val formula = Bmr.formulaTdee(
            restingKcal = resting.kcal,
            weightKg = weightKg,
            stepsPerDay = repos.health.dailyAverage(MetricKind.STEPS, Tdee.WINDOW_DAYS),
            sessionsPerWeek = sessionsPerWeek(repos.sessions, day)
        )

        val estimate = Tdee.estimate(
            formulaKcal = formula,
            weighIns = repos.body.recent(WEIGH_IN_LOOKBACK).map {
                Tdee.WeighIn(epochDayOf(it.measuredAt), it.weightKg)
            },
            intake = repos.diary.intakeDays(day - Tdee.WINDOW_DAYS + 1, day),
            today = day,
            fromDay = profile.tdeeResetDay
        )

        // Jeden wiersz na dzień - to z nich powstaje krzywa wydatku, czyli
        // jedyny sposób, żeby zobaczyć, że na dłuższej redukcji wydatek spada.
        repos.tdee.save(
            StoredTdee(
                day = day,
                kcal = estimate.kcal,
                method = estimate.method,
                sdKcal = estimate.sdKcal,
                trendKgPerWeek = estimate.trendKgPerWeek
            )
        )

        val target = resolveTarget(day, repos, estimate, weightKg, leanKg, profile)

        return Today(
            day = day,
            summary = summary,
            entries = entries,
            target = target,
            tdee = estimate,
            restingSource = resting.source,
            restingKcal = resting.kcal,
            fiberTargetG = target?.let { Targets.fiberTarget(it.kcal) },
            weightKg = weightKg,
            leanKg = leanKg
        )
    }

    /** Ile ostatnich ważeń bierzemy; okno i tak odfiltruje starsze. */
    private const val WEIGH_IN_LOOKBACK = 300

    /**
     * Cel na dziś.
     *
     * Cel wpisany ręcznie **wygrywa zawsze**: skoro go wpisałeś, to znaczy,
     * że wiesz coś, czego aplikacja nie wie. Nadpisywanie go kolejnym
     * oszacowaniem byłoby odbieraniem decyzji, którą już podjąłeś.
     */
    private fun resolveTarget(
        day: Long,
        repos: NutritionRepos,
        estimate: Tdee.Estimate,
        weightKg: Double,
        leanKg: Double?,
        profile: pl.gymbro.data.UserProfile
    ): NutritionTarget {
        val existing = repos.targets.current(day)
        if (existing != null && existing.manual) return existing

        val fresh = Targets.target(
            fromDay = day,
            tdeeKcal = estimate.kcal,
            weightKg = weightKg,
            phase = profile.phase,
            leanKg = leanKg,
            ratePct = Targets.effectiveRatePct(profile.phase, profile.dietRatePct)
        )

        if (existing != null && !worthSaving(existing, fresh)) return existing

        repos.targets.set(fresh)
        return fresh
    }

    /** Czy nowy cel różni się od starego na tyle, żeby zapisać go w historii. */
    private fun worthSaving(old: NutritionTarget, new: NutritionTarget): Boolean =
        abs(old.kcal - new.kcal) >= TARGET_KCAL_STEP ||
            abs(old.proteinG - new.proteinG) >= TARGET_GRAM_STEP ||
            abs(old.carbsG - new.carbsG) >= TARGET_GRAM_STEP ||
            abs(old.fatG - new.fatG) >= TARGET_GRAM_STEP

    /**
     * Treningi siłowe na tydzień, policzone z bazy.
     *
     * Z bazy, a nie z deklaracji w profilu: „trenuję cztery razy w tygodniu"
     * to plan, a sesje w bazie to fakt. W tygodniu z kontuzją albo wyjazdem
     * różnica sięga kilkuset kalorii na dobę.
     */
    private fun sessionsPerWeek(sessions: SessionRepository, today: Long): Double {
        val from = today - Tdee.WINDOW_DAYS + 1
        val done = sessions.recent(SESSION_LOOKBACK).count { s ->
            s.endedAt != null && epochDayOf(s.startedAt) in from..today
        }
        return done / (Tdee.WINDOW_DAYS / 7.0)
    }

    /**
     * Czego brakuje, żeby policzyć przemianę spoczynkową.
     *
     * Komunikat ma być instrukcją: „uzupełnij wzrost w Profilu" prowadzi do
     * działania, a „brak danych" tylko informuje o porażce.
     */
    private fun missingFor(weightKg: Double?, heightCm: Double?, age: Int?): String {
        val braki = buildList {
            if (weightKg == null) add("pomiar masy ciała")
            if (heightCm == null) add("wzrost w Profilu")
            if (age == null) add("rok urodzenia w Profilu")
        }
        return when {
            braki.isEmpty() -> "Uzupełnij profil, żeby policzyć zapotrzebowanie."
            braki.size == 1 -> "Brakuje: ${braki.first()}."
            else -> "Brakuje: " + braki.dropLast(1).joinToString(", ") +
                " i " + braki.last() + "."
        }
    }
}
