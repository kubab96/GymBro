package pl.gymbro.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import pl.gymbro.gym.StaleSession

/**
 * Repozytoria - jedyna droga do bazy. Reszta aplikacji nie widzi SQL-a.
 *
 * Wszystkie metody są synchroniczne i blokujące. Wywołuj je z wątku roboczego
 * (`withContext(Dispatchers.IO)`), nigdy z wątku interfejsu.
 */

// ---- Pomocnicze odczyty z kursora ---------------------------------------
//
// `internal`, nie `private`: tych samych pomocnikow potrzebuje FoodRepos.kt,
// a zduplikowane w drugim pliku z czasem przestaja byc takie same.

internal fun Cursor.str(col: String): String = getString(getColumnIndexOrThrow(col))
internal fun Cursor.strOrNull(col: String): String? {
    val i = getColumnIndexOrThrow(col)
    return if (isNull(i)) null else getString(i)
}

internal fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
internal fun Cursor.longOrNull(col: String): Long? {
    val i = getColumnIndexOrThrow(col)
    return if (isNull(i)) null else getLong(i)
}

internal fun Cursor.int(col: String): Int = getInt(getColumnIndexOrThrow(col))
internal fun Cursor.intOrNull(col: String): Int? {
    val i = getColumnIndexOrThrow(col)
    return if (isNull(i)) null else getInt(i)
}

internal fun Cursor.dbl(col: String): Double = getDouble(getColumnIndexOrThrow(col))
internal fun Cursor.dblOrNull(col: String): Double? {
    val i = getColumnIndexOrThrow(col)
    return if (isNull(i)) null else getDouble(i)
}

internal fun Cursor.bool(col: String): Boolean = getInt(getColumnIndexOrThrow(col)) != 0

/**
 * Dostęp do zapisu, który przy okazji unieważnia pamięć podręczną odczytów.
 *
 * Pojedynczy punkt wejścia zamiast wywołania Cache.clear() w każdej z kilkudziesięciu
 * metod zapisujących - przeoczenie choć jednej dawałoby nieaktualne dane na ekranie,
 * a taki błąd jest trudny do powiązania z przyczyną.
 */
internal val Db.writable: android.database.sqlite.SQLiteDatabase
    get() {
        Cache.clear()
        return writableDatabase
    }

/** Wpisuje wartość albo NULL - przy polach opcjonalnych to najczęstsza operacja. */
internal fun ContentValues.putOrNull(key: String, value: Double?) {
    if (value != null) put(key, value) else putNull(key)
}

internal inline fun <T> Cursor.mapAll(map: (Cursor) -> T): List<T> = use { c ->
    val out = ArrayList<T>()
    while (c.moveToNext()) out += map(c)
    out
}

// ---- Ćwiczenia -----------------------------------------------------------

class ExerciseRepository(context: Context) {
    private val db = Db.get(context)

    private fun fromCursor(c: Cursor) = Exercise(
        id = c.long("id"),
        name = c.str("name"),
        equipment = Equipment.fromName(c.str("equipment")),
        muscleGroup = c.str("muscle_group"),
        isCustom = c.bool("is_custom"),
        stepKg = c.dblOrNull("weight_step")
    )

    fun all(): List<Exercise> =
        db.readableDatabase.rawQuery("SELECT * FROM exercise ORDER BY name", null)
            .mapAll(::fromCursor)

    fun byId(id: Long): Exercise? =
        db.readableDatabase.rawQuery("SELECT * FROM exercise WHERE id = ?", arrayOf(id.toString()))
            .mapAll(::fromCursor).firstOrNull()

    fun insert(e: Exercise): Long =
        db.writable.insert("exercise", null, ContentValues().apply {
            put("name", e.name)
            put("equipment", e.equipment.name)
            put("muscle_group", e.muscleGroup)
            put("is_custom", if (e.isCustom) 1 else 0)
            putOrNull("weight_step", e.stepKg)
        })

    /**
     * Zmiana samego ćwiczenia: nazwa, sprzęt, partia, krok ciężaru.
     *
     * `is_custom` zostaje nietknięte celowo. Poprawienie kroku przy ćwiczeniu
     * z biblioteki nie czyni go nagle „własnym" - a to właśnie ta flaga
     * decyduje o tym, czy wstawka z [Seed] ma je kiedyś odtworzyć.
     */
    fun update(e: Exercise): Int =
        db.writable.update("exercise", ContentValues().apply {
            put("name", e.name)
            put("equipment", e.equipment.name)
            put("muscle_group", e.muscleGroup)
            putOrNull("weight_step", e.stepKg)
        }, "id = ?", arrayOf(e.id.toString()))

    /**
     * Ćwiczenia, do których nic nie prowadzi: nie ma ich w żadnym planie
     * ani w żadnej zapisanej serii.
     *
     * Tylko takie wolno skasować. Usunięcie ćwiczenia leżącego w historii
     * zostawiłoby serie bez nazwy i bez partii - objętość przestałaby się
     * zgadzać, a tego nie da się już odtworzyć.
     */
    fun unusedIds(): Set<Long> =
        db.readableDatabase.rawQuery(
            """
            SELECT e.id AS id FROM exercise e
            WHERE NOT EXISTS (SELECT 1 FROM plan_item p WHERE p.exercise_id = e.id)
              AND NOT EXISTS (SELECT 1 FROM set_entry s WHERE s.exercise_id = e.id)
            """.trimIndent(),
            null
        ).mapAll { it.long("id") }.toSet()

    fun delete(id: Long): Int =
        db.writable.delete("exercise", "id = ?", arrayOf(id.toString()))
}

// ---- Plany ---------------------------------------------------------------

class ProgramRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = Program(
        id = c.long("id"),
        name = c.str("name"),
        note = c.strOrNull("note"),
        position = c.int("position"),
        isActive = c.int("is_active") == 1
    )

    fun all(): List<Program> =
        db.readableDatabase.rawQuery("SELECT * FROM program ORDER BY position, id", null)
            .mapAll(::from)

    fun active(): Program? =
        db.readableDatabase.rawQuery("SELECT * FROM program WHERE is_active = 1 LIMIT 1", null)
            .mapAll(::from).firstOrNull()

    /**
     * Nowy program. Pierwszy utworzony staje się od razu aktywny - bez tego
     * ekran treningu byłby pusty mimo istniejącego planu.
     */
    fun insert(name: String, note: String? = null): Long {
        val count = db.readableDatabase.rawQuery("SELECT COUNT(*) AS n FROM program", null)
            .mapAll { it.int("n") }.firstOrNull() ?: 0
        val id = db.writable.insert("program", null, ContentValues().apply {
            put("name", name)
            if (note != null) put("note", note) else putNull("note")
            put("position", count)
            put("is_active", 0)
        })
        if (count == 0) setActive(id)
        return id
    }

    fun rename(id: Long, name: String): Int =
        db.writable.update(
            "program", ContentValues().apply { put("name", name) },
            "id = ?", arrayOf(id.toString())
        )

    /** Aktywny jest dokładnie jeden - zdejmujemy flagę ze wszystkich i nadajemy jednemu. */
    fun setActive(id: Long) {
        db.writable.beginTransaction()
        try {
            db.writable.execSQL("UPDATE program SET is_active = 0")
            db.writable.execSQL("UPDATE program SET is_active = 1 WHERE id = ?", arrayOf(id))
            db.writable.setTransactionSuccessful()
        } finally {
            db.writable.endTransaction()
        }
    }

    /**
     * Usuwa program razem z jego jednostkami.
     *
     * Jednostki kasujemy jawnie, bo `plan.program_id` dodano przez ALTER TABLE
     * i nie ma przy nim klucza obcego - SQLite nie potrafi go tak dorobić,
     * więc żadna kaskada tu nie zadziała. Wcześniej jednostki zostawały
     * sierotami: znikały z list, bo te filtrują po programie, ale dalej
     * trzymały ćwiczenia i blokowały ich usunięcie z biblioteki.
     *
     * Jeśli skasowano aktywny, aktywnym zostaje pierwszy pozostały - inaczej
     * ekran treningu zostałby bez źródła jednostek.
     */
    fun delete(id: Long): Int {
        val wasActive = active()?.id == id
        val db2 = db.writable
        val removed: Int
        db2.beginTransaction()
        try {
            val plans = db2.rawQuery(
                "SELECT id FROM plan WHERE program_id = ?", arrayOf(id.toString())
            ).mapAll { it.long("id") }
            plans.forEach {
                db2.delete("plan_item", "plan_id = ?", arrayOf(it.toString()))
            }
            db2.delete("plan", "program_id = ?", arrayOf(id.toString()))
            removed = db2.delete("program", "id = ?", arrayOf(id.toString()))
            db2.setTransactionSuccessful()
        } finally {
            db2.endTransaction()
        }
        if (wasActive) all().firstOrNull()?.let { setActive(it.id) }
        return removed
    }

    fun planCount(programId: Long): Int =
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS n FROM plan WHERE program_id = ?",
            arrayOf(programId.toString())
        ).mapAll { it.int("n") }.firstOrNull() ?: 0
}

class PlanRepository(context: Context) {
    private val db = Db.get(context)

    private fun planFrom(c: Cursor) = Plan(
        id = c.long("id"),
        name = c.str("name"),
        position = c.int("position"),
        programId = c.longOrNull("program_id")
    )

    /** Wszystkie jednostki, niezależnie od programu. */
    fun plans(): List<Plan> =
        db.readableDatabase.rawQuery("SELECT * FROM plan ORDER BY position", null)
            .mapAll(::planFrom)

    /** Jednostki jednego programu - to one trafiają na ekran treningu. */
    fun plansOf(programId: Long): List<Plan> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM plan WHERE program_id = ? ORDER BY position, id",
            arrayOf(programId.toString())
        ).mapAll(::planFrom)

    /** Ćwiczenia w planie, już z nazwą i sprzętem - jedno zapytanie zamiast N+1. */
    fun items(planId: Long): List<PlanItem> =
        db.readableDatabase.rawQuery(
            """
            SELECT pi.*, e.name AS ex_name, e.equipment AS ex_equipment,
                   e.weight_step AS ex_step
            FROM plan_item pi
            JOIN exercise e ON e.id = pi.exercise_id
            WHERE pi.plan_id = ?
            ORDER BY pi.position
            """.trimIndent(),
            arrayOf(planId.toString())
        ).mapAll(::itemFrom)

    private fun itemFrom(c: Cursor) = PlanItem(
        id = c.long("id"),
        planId = c.long("plan_id"),
        exerciseId = c.long("exercise_id"),
        position = c.int("position"),
        targetKg = c.dbl("target_kg"),
        sets = c.int("sets"),
        repsFrom = c.int("reps_from"),
        repsTo = c.int("reps_to"),
        restSec = c.int("rest_sec"),
        exerciseName = c.str("ex_name"),
        equipment = Equipment.fromName(c.str("ex_equipment")),
        goal = runCatching { TrainingGoal.valueOf(c.str("goal")) }.getOrNull(),
        noProgress = c.int("no_progress") == 1,
        stepKg = c.dblOrNull("ex_step"),
        advice = c.strOrNull("adv_kind")?.let { kind ->
            StoredAdvice(
                kind = kind,
                weightKg = c.dblOrNull("adv_weight"),
                repsTo = c.intOrNull("adv_reps_to"),
                sets = c.intOrNull("adv_sets"),
                reason = c.strOrNull("adv_reason").orEmpty(),
                last = c.strOrNull("adv_last").orEmpty(),
                sessionId = c.longOrNull("adv_session") ?: 0L,
                seen = c.int("adv_seen") == 1
            )
        }
    )

    fun insertPlan(name: String, position: Int, programId: Long? = null): Long =
        db.writable.insert("plan", null, ContentValues().apply {
            put("name", name)
            put("position", position)
            if (programId != null) put("program_id", programId) else putNull("program_id")
        })

    fun renamePlan(id: Long, name: String): Int =
        db.writable.update(
            "plan", ContentValues().apply { put("name", name) },
            "id = ?", arrayOf(id.toString())
        )

    fun deletePlan(id: Long): Int =
        db.writable.delete("plan", "id = ?", arrayOf(id.toString()))

    fun upsertItem(item: PlanItem): Long {
        val values = ContentValues().apply {
            put("plan_id", item.planId)
            put("exercise_id", item.exerciseId)
            put("position", item.position)
            put("target_kg", roundKg(item.targetKg))
            put("sets", item.sets)
            put("reps_from", item.repsFrom)
            put("reps_to", item.repsTo)
            put("rest_sec", item.restSec)
            if (item.goal != null) put("goal", item.goal.name) else putNull("goal")
            put("no_progress", if (item.noProgress) 1 else 0)
        }
        return if (item.id == 0L) {
            db.writable.insert("plan_item", null, values)
        } else {
            db.writable.update("plan_item", values, "id = ?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun deleteItem(id: Long): Int =
        db.writable.delete("plan_item", "id = ?", arrayOf(id.toString()))

    /**
     * Zapisanie propozycji przy pozycji planu.
     *
     * Osobno od [upsertItem], bo zwykła edycja pozycji nie może kasować
     * czekającej podpowiedzi ani odwrotnie - to dwa niezależne cykle życia.
     */
    fun setAdvice(itemId: Long, advice: StoredAdvice?): Int =
        db.writable.update(
            "plan_item",
            ContentValues().apply {
                if (advice == null) {
                    putNull("adv_kind")
                    putNull("adv_weight")
                    putNull("adv_reps_to")
                    putNull("adv_sets")
                    putNull("adv_reason")
                    putNull("adv_last")
                    putNull("adv_session")
                    put("adv_seen", 0)
                } else {
                    put("adv_kind", advice.kind)
                    if (advice.weightKg != null) put("adv_weight", advice.weightKg)
                    else putNull("adv_weight")
                    if (advice.repsTo != null) put("adv_reps_to", advice.repsTo)
                    else putNull("adv_reps_to")
                    if (advice.sets != null) put("adv_sets", advice.sets)
                    else putNull("adv_sets")
                    put("adv_reason", advice.reason)
                    put("adv_last", advice.last)
                    put("adv_session", advice.sessionId)
                    put("adv_seen", if (advice.seen) 1 else 0)
                }
            },
            "id = ?", arrayOf(itemId.toString())
        )

    /** Ile propozycji czeka i ilu z nich jeszcze nie widziałeś. */
    fun adviceCounts(): Pair<Int, Int> =
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS n, SUM(CASE WHEN adv_seen = 0 THEN 1 ELSE 0 END) AS u " +
                "FROM plan_item WHERE adv_kind IS NOT NULL",
            null
        ).mapAll { (it.int("n")) to (it.intOrNull("u") ?: 0) }.firstOrNull() ?: (0 to 0)

    /** Ile propozycji czeka w kazdej jednostce - do znacznikow na listach. */
    fun adviceCountByPlan(): Map<Long, Int> =
        db.readableDatabase.rawQuery(
            "SELECT plan_id, COUNT(*) AS n FROM plan_item " +
                "WHERE adv_kind IS NOT NULL GROUP BY plan_id",
            null
        ).mapAll { it.long("plan_id") to it.int("n") }.toMap()

    /** Po zajrzeniu plakietka gaśnie, ale propozycje zostają do decyzji. */
    fun markAdviceSeen(): Int =
        db.writable.update(
            "plan_item", ContentValues().apply { put("adv_seen", 1) },
            "adv_kind IS NOT NULL", null
        )

    /** Pozycje z czekającą propozycją, z nazwami ćwiczeń. */
    fun itemsWithAdvice(): List<PlanItem> =
        db.readableDatabase.rawQuery(
            """
            SELECT pi.*, e.name AS ex_name, e.equipment AS ex_equipment,
                   e.weight_step AS ex_step
            FROM plan_item pi JOIN exercise e ON e.id = pi.exercise_id
            WHERE pi.adv_kind IS NOT NULL
            ORDER BY pi.plan_id, pi.position
            """.trimIndent(),
            null
        ).mapAll(::itemFrom)

    /** Zmiana samego ciężaru - najczęstsza edycja, więc ma własną metodę. */
    fun setTargetWeight(itemId: Long, kg: Double): Int =
        db.writable.update(
            "plan_item", ContentValues().apply { put("target_kg", roundKg(kg)) },
            "id = ?", arrayOf(itemId.toString())
        )
}

// ---- Sesje i serie -------------------------------------------------------

class SessionRepository(context: Context) {
    private val db = Db.get(context)

    private fun sessionFrom(c: Cursor) = Session(
        id = c.long("id"),
        planId = c.longOrNull("plan_id"),
        startedAt = c.long("started_at"),
        endedAt = c.longOrNull("ended_at"),
        note = c.strOrNull("note"),
        pausedAt = c.longOrNull("paused_at"),
        pausedMs = c.longOrNull("paused_ms") ?: 0L,
        localId = c.strOrNull("local_id"),
        advised = (c.longOrNull("advised") ?: 0L) != 0L
    )

    /**
     * Wstrzymuje trening albo go wznawia.
     *
     * Przy wznowieniu doliczamy odstany czas do sumy i czyścimy znacznik.
     * Serie mają własne godziny zapisu, więc pauza nie dotyka danych
     * treningowych - zmienia wyłącznie to, co pokazuje zegar.
     */
    fun setPaused(id: Long, paused: Boolean, now: Long = System.currentTimeMillis()): Int {
        val s = db.readableDatabase.rawQuery(
            "SELECT * FROM session WHERE id = ?", arrayOf(id.toString())
        ).mapAll(::sessionFrom).firstOrNull() ?: return 0

        val values = ContentValues()
        if (paused) {
            if (s.pausedAt != null) return 0
            values.put("paused_at", now)
        } else {
            val since = s.pausedAt ?: return 0
            values.putNull("paused_at")
            values.put("paused_ms", s.pausedMs + (now - since).coerceAtLeast(0L))
        }
        return db.writable.update("session", values, "id = ?", arrayOf(id.toString()))
    }

    private fun setFrom(c: Cursor) = SetEntry(
        id = c.long("id"),
        sessionId = c.long("session_id"),
        exerciseId = c.long("exercise_id"),
        setIndex = c.int("set_index"),
        weightKg = c.dbl("weight_kg"),
        reps = c.int("reps"),
        rir = c.intOrNull("rir"),
        isWarmup = c.bool("is_warmup"),
        doneAt = c.long("done_at")
    )

    /**
     * @param localId znacznik nadany przez zegarek, gdy trening zaczął się
     *   bez łączności. Pilnuje go indeks jednoznaczny, więc ponowione
     *   zgłoszenie nie założy tej samej sesji drugi raz.
     */
    fun start(
        planId: Long?,
        at: Long = System.currentTimeMillis(),
        localId: String? = null
    ): Long =
        db.writable.insert("session", null, ContentValues().apply {
            if (planId != null) put("plan_id", planId) else putNull("plan_id")
            put("started_at", at)
            if (localId != null) put("local_id", localId) else putNull("local_id")
        })

    /**
     * Trening dopisany po fakcie, od razu zamkniety.
     *
     * Zegarek potrafi lezec z niewyslana kolejka pol dnia - telefon bywa
     * wyladowany albo aplikacja Zepp ubita. Taka sesja dojezdza z godzina
     * sprzed wielu godzin, a [active] uznaje ja wtedy za porzucona i kasuje,
     * zanim dojada jej serie. Dopisana od razu z czasem zakonczenia nigdy
     * nie jest "trwajaca", wiec nikt jej nie sprzata, a FINISH z kolejki
     * poprawia godzine na te prawdziwa.
     */
    fun startClosed(
        planId: Long?,
        at: Long,
        localId: String? = null
    ): Long =
        db.writable.insert("session", null, ContentValues().apply {
            if (planId != null) put("plan_id", planId) else putNull("plan_id")
            put("started_at", at)
            put("ended_at", at)
            if (localId != null) put("local_id", localId) else putNull("local_id")
        })

    /**
     * Ostatni zakonczony trening, dla ktorego nie policzono jeszcze propozycji.
     *
     * Tylko ostatni: starsze i tak sa nieaktualne, a wskrzeszanie propozycji
     * sprzed tygodni byloby gorsze niz ich brak.
     */
    fun lastUnadvised(): Session? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM session WHERE ended_at IS NOT NULL " +
                "ORDER BY ended_at DESC LIMIT 1", null
        ).mapAll(::sessionFrom).firstOrNull()?.takeIf { !it.advised }

    fun markAdvised(id: Long): Int =
        db.writable.update(
            "session", ContentValues().apply { put("advised", 1) },
            "id = ?", arrayOf(id.toString())
        )

    /** Sesja po numerze - takze zamknieta. */
    fun sessionById(id: Long): Session? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM session WHERE id = ? LIMIT 1", arrayOf(id.toString())
        ).mapAll(::sessionFrom).firstOrNull()

    /** Sesja o znaczniku z zegarka - niezaleznie od tego, czy jeszcze trwa. */
    fun byLocalId(localId: String): Session? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM session WHERE local_id = ? LIMIT 1", arrayOf(localId)
        ).mapAll(::sessionFrom).firstOrNull()

    /**
     * Niezakończona sesja, jeśli istnieje. Dzięki temu zabicie aplikacji
     * w środku treningu nie gubi postępu - po powrocie wracasz do tej samej sesji.
     */
    /** Czas ostatniej serii w sesji; null, gdy nie zapisano żadnej. */
    private fun lastSetAt(sessionId: Long): Long? =
        db.readableDatabase.rawQuery(
            "SELECT MAX(done_at) AS t FROM set_entry WHERE session_id = ?",
            arrayOf(sessionId.toString())
        ).mapAll { it.longOrNull("t") }.firstOrNull()

    private fun discard(id: Long): Int =
        db.writable.delete("session", "id = ?", arrayOf(id.toString()))

    /**
     * Domkniecie treningu, ktory ustepuje nowemu - tak samo jak porzuconego.
     *
     * Pusty znika, a ten z seriami konczy sie na ostatniej z nich. Zegarek
     * zaczyna nowy trening tylko wtedy, gdy uwaza, ze zaden nie trwa - a
     * odmowa znaczyla, ze serie nowego dopisywaly sie do starego.
     */
    fun closeAbandoned(id: Long, now: Long = System.currentTimeMillis()) {
        val last = lastSetAt(id)
        if (last == null) discard(id) else finish(id, minOf(last, now))
    }

    /**
     * Trwający trening - albo nic, jeśli poprzedni został porzucony.
     *
     * Sprzątanie dzieje się tutaj, bo to jedyne miejsce, przez które przechodzi
     * każde pytanie o trwającą sesję. Osobny mechanizm czyszczący w tle byłby
     * kolejną rzeczą, która może nie zadziałać, a efekt ten sam.
     */
    fun active(now: Long = System.currentTimeMillis()): Session? {
        val s = db.readableDatabase.rawQuery(
            "SELECT * FROM session WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1", null
        ).mapAll(::sessionFrom).firstOrNull() ?: return null

        return when (val v = StaleSession.verdict(s.startedAt, lastSetAt(s.id), now)) {
            StaleSession.Verdict.Active -> s
            StaleSession.Verdict.Discard -> { discard(s.id); null }
            is StaleSession.Verdict.Close -> { finish(s.id, v.endedAt); null }
        }
    }

    fun finish(id: Long, at: Long = System.currentTimeMillis()): Int =
        db.writable.update(
            "session", ContentValues().apply { put("ended_at", at) },
            "id = ?", arrayOf(id.toString())
        )

    /**
     * Kiedy ostatnio ukończyłeś ten plan. Null, jeśli jeszcze ani razu.
     *
     * Przy rotacji A/B/C to wystarcza, żeby podpowiedzieć, co wypada dzisiaj:
     * najdawniej robiona jednostka jest następna w kolejce.
     */
    fun lastFinishedFor(planId: Long): Long? =
        db.readableDatabase.rawQuery(
            "SELECT MAX(started_at) AS t FROM session WHERE plan_id = ? AND ended_at IS NOT NULL",
            arrayOf(planId.toString())
        ).mapAll { it.longOrNull("t") }.firstOrNull()

    fun recent(limit: Int = 30): List<Session> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM session ORDER BY started_at DESC LIMIT ?", arrayOf(limit.toString())
        ).mapAll(::sessionFrom)

    /**
     * Historia zakończonych treningów: data, plan, liczba serii roboczych i tonaż.
     *
     * Wszystko jednym zapytaniem z grupowaniem, nie pętlą po sesjach - przy
     * trzydziestu wpisach pętla oznaczałaby trzydzieści jeden odczytów z bazy.
     * Rozgrzewki nie wchodzą ani do liczby serii, ani do tonażu: to objętość
     * przygotowawcza, nie wykonana praca.
     */
    fun historySummaries(limit: Int = 30): List<SessionSummary> =
        db.readableDatabase.rawQuery(
            """
            SELECT s.id AS id, s.started_at AS started_at, s.ended_at AS ended_at,
                   p.name AS plan_name,
                   COUNT(CASE WHEN e.is_warmup = 0 THEN 1 END) AS sets,
                   COALESCE(
                       SUM(CASE WHEN e.is_warmup = 0 THEN e.weight_kg * e.reps END), 0
                   ) AS tonnage
            FROM session s
            LEFT JOIN plan p ON p.id = s.plan_id
            LEFT JOIN set_entry e ON e.session_id = s.id
            WHERE s.ended_at IS NOT NULL
            GROUP BY s.id
            ORDER BY s.started_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.toString())
        ).mapAll {
            SessionSummary(
                sessionId = it.long("id"),
                planName = it.strOrNull("plan_name"),
                startedAt = it.long("started_at"),
                endedAt = it.long("ended_at"),
                workingSets = it.int("sets"),
                tonnage = it.dbl("tonnage")
            )
        }

    fun addSet(s: SetEntry): Long =
        db.writable.insert("set_entry", null, ContentValues().apply {
            put("session_id", s.sessionId)
            put("exercise_id", s.exerciseId)
            put("set_index", s.setIndex)
            put("weight_kg", roundKg(s.weightKg))
            put("reps", s.reps)
            if (s.rir != null) put("rir", s.rir) else putNull("rir")
            put("is_warmup", if (s.isWarmup) 1 else 0)
            put("done_at", s.doneAt)
        })

    fun deleteSet(id: Long): Int =
        db.writable.delete("set_entry", "id = ?", arrayOf(id.toString()))

    /**
     * Poprawia zapisaną serię.
     *
     * Pomyłka o jedno powtórzenie zdarza się przy każdym treningu, a dotąd
     * jedynym wyjściem było skasowanie serii i wklepanie jej od nowa -
     * co gubiło też godzinę zapisu, a po niej idzie dopasowanie tętna.
     * Dlatego `done_at` zostaje nietknięty.
     */
    fun updateSet(id: Long, weightKg: Double, reps: Int, rir: Int?): Int =
        db.writable.update(
            "set_entry",
            ContentValues().apply {
                put("weight_kg", roundKg(weightKg))
                put("reps", reps)
                if (rir == null) putNull("rir") else put("rir", rir)
            },
            "id = ?", arrayOf(id.toString())
        )

    /**
     * Kasuje jeden trening razem z jego seriami.
     *
     * Serie jawnie, z tego samego powodu co w [wipeHistory]: kaskada
     * set_entry → session jest prawdziwym kluczem obcym, ale zależy od
     * `PRAGMA foreign_keys`, a osierocone serie nie dałyby się już znaleźć.
     */
    fun deleteSession(id: Long): Int {
        val db2 = db.writable
        db2.delete("set_entry", "session_id = ?", arrayOf(id.toString()))
        return db2.delete("session", "id = ?", arrayOf(id.toString()))
    }

    fun setsFor(sessionId: Long): List<SetEntry> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM set_entry WHERE session_id = ? ORDER BY id", arrayOf(sessionId.toString())
        ).mapAll(::setFrom)

    // ---- Zapytania zbiorcze -------------------------------------------------
    //
    // Ekran treningu potrzebuje tych samych danych dla wszystkich ćwiczeń naraz.
    // Pytanie o każde osobno dawało kilkadziesiąt zapytań na jedno przeładowanie,
    // a przeładowanie leci po KAŻDEJ zapisanej serii. Poniższe metody robią to
    // jednym zapytaniem i grupują w Kotlinie.

    /** Wszystkie serie sesji, pogrupowane po ćwiczeniu. Jedno zapytanie zamiast N. */
    fun setsBySessionGrouped(sessionId: Long): Map<Long, List<SetEntry>> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM set_entry WHERE session_id = ? ORDER BY set_index",
            arrayOf(sessionId.toString())
        ).mapAll(::setFrom).groupBy { it.exerciseId }

    /** Najcięższa seria robocza każdego z podanych ćwiczeń. */
    fun bestWeights(exerciseIds: List<Long>): Map<Long, Double> {
        if (exerciseIds.isEmpty()) return emptyMap()
        val placeholders = exerciseIds.joinToString(",") { "?" }
        return db.readableDatabase.rawQuery(
            """
            SELECT exercise_id, MAX(weight_kg) AS m
            FROM set_entry
            WHERE exercise_id IN ($placeholders) AND is_warmup = 0
            GROUP BY exercise_id
            """.trimIndent(),
            exerciseIds.map { it.toString() }.toTypedArray()
        ).mapAll { it.long("exercise_id") to (it.dblOrNull("m") ?: 0.0) }
            .filter { it.second > 0.0 }
            .toMap()
    }

    /**
     * Historia sesji dla wielu ćwiczeń naraz - wejście silnika progresji.
     *
     * Jedno zapytanie po wszystkie serie robocze tych ćwiczeń, potem grupowanie
     * w pamięci. Wersja pytająca osobno robiła do siedmiu zapytań na ćwiczenie.
     */
    fun recentSessionsForMany(
        exerciseIds: List<Long>,
        limitPerExercise: Int = 6,
        excludeSessionId: Long? = null
    ): Map<Long, List<List<SetEntry>>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        val placeholders = exerciseIds.joinToString(",") { "?" }
        val args = exerciseIds.map { it.toString() } + (excludeSessionId ?: -1L).toString()

        return db.readableDatabase.rawQuery(
            """
            SELECT * FROM set_entry
            WHERE exercise_id IN ($placeholders)
              AND is_warmup = 0
              AND session_id <> ?
            ORDER BY done_at DESC
            """.trimIndent(),
            args.toTypedArray()
        ).mapAll(::setFrom)
            .groupBy { it.exerciseId }
            .mapValues { (_, sets) ->
                sets.groupBy { it.sessionId }
                    .entries
                    .sortedByDescending { entry -> entry.value.maxOf { it.doneAt } }
                    .take(limitPerExercise)
                    .map { entry -> entry.value.sortedBy { it.setIndex } }
            }
    }

    /** Liczba ćwiczeń w każdej jednostce. Jedno zapytanie zamiast N. */
    fun itemCounts(): Map<Long, Int> =
        db.readableDatabase.rawQuery(
            "SELECT plan_id, COUNT(*) AS n FROM plan_item GROUP BY plan_id", null
        ).mapAll { it.long("plan_id") to it.int("n") }.toMap()

    /** Data ostatniego ukończenia każdej jednostki. Jedno zapytanie zamiast N. */
    fun lastFinishedAll(): Map<Long, Long> =
        db.readableDatabase.rawQuery(
            """
            SELECT plan_id, MAX(started_at) AS t
            FROM session
            WHERE ended_at IS NOT NULL AND plan_id IS NOT NULL
            GROUP BY plan_id
            """.trimIndent(),
            null
        ).mapAll { it.long("plan_id") to it.long("t") }.toMap()

    fun setsForExerciseInSession(sessionId: Long, exerciseId: Long): List<SetEntry> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM set_entry WHERE session_id = ? AND exercise_id = ? ORDER BY set_index",
            arrayOf(sessionId.toString(), exerciseId.toString())
        ).mapAll(::setFrom)

    /**
     * Serie z poprzedniej sesji, w której to ćwiczenie w ogóle wystąpiło.
     *
     * To jest najważniejsze zapytanie w całej aplikacji - odpowiada na pytanie
     * "ile podniosłem ostatnim razem", które chcesz widzieć przy każdej serii,
     * bez wchodzenia w historię.
     */
    fun lastSessionSets(exerciseId: Long, excludeSessionId: Long? = null): List<SetEntry> {
        val exclude = excludeSessionId ?: -1L
        return db.readableDatabase.rawQuery(
            """
            SELECT * FROM set_entry
            WHERE exercise_id = ?
              AND is_warmup = 0
              AND session_id = (
                  SELECT session_id FROM set_entry
                  WHERE exercise_id = ? AND is_warmup = 0 AND session_id <> ?
                  ORDER BY done_at DESC LIMIT 1
              )
            ORDER BY set_index
            """.trimIndent(),
            arrayOf(exerciseId.toString(), exerciseId.toString(), exclude.toString())
        ).mapAll(::setFrom)
    }

    /**
     * Ostatnie sesje z danym ćwiczeniem, od najnowszej - każda jako lista serii.
     *
     * Silnik progresji potrzebuje nie tylko poprzedniego treningu, ale kilku:
     * wykrycie zastoju polega na policzeniu, ile sesji z rzędu stoisz na tym samym
     * ciężarze. Jedno zapytanie zbiera identyfikatory sesji, potem dociągamy serie.
     */
    fun recentSessionsFor(
        exerciseId: Long,
        limit: Int = 6,
        excludeSessionId: Long? = null
    ): List<List<SetEntry>> {
        val exclude = excludeSessionId ?: -1L
        val sessionIds = db.readableDatabase.rawQuery(
            """
            SELECT session_id, MAX(done_at) AS t
            FROM set_entry
            WHERE exercise_id = ? AND is_warmup = 0 AND session_id <> ?
            GROUP BY session_id
            ORDER BY t DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(exerciseId.toString(), exclude.toString(), limit.toString())
        ).mapAll { it.long("session_id") }

        return sessionIds.map { setsForExerciseInSession(it, exerciseId) }
    }

    /** Najlepszy szacowany ciężar maksymalny. Liczymy w Kotlinie, bo wzór jest nasz. */
    fun bestEstimated1RM(exerciseId: Long): SetEntry? =
        db.readableDatabase.rawQuery(
            "SELECT * FROM set_entry WHERE exercise_id = ? AND is_warmup = 0",
            arrayOf(exerciseId.toString())
        ).mapAll(::setFrom).maxByOrNull { it.estimated1RM }

    /** Rekord ciężaru przy co najmniej [minReps] powtórzeniach - do wykrywania PR. */
    fun bestWeight(exerciseId: Long, minReps: Int = 1): Double? =
        db.readableDatabase.rawQuery(
            "SELECT MAX(weight_kg) AS m FROM set_entry WHERE exercise_id = ? AND reps >= ? AND is_warmup = 0",
            arrayOf(exerciseId.toString(), minReps.toString())
        ).mapAll { it.dblOrNull("m") }.firstOrNull()

    /** Tonaż na grupę mięśniową w zadanym oknie czasu - podstawa pod kontrolę objętości. */
    fun tonnageByMuscle(fromMs: Long, toMs: Long): Map<String, Double> =
        db.readableDatabase.rawQuery(
            """
            SELECT e.muscle_group AS mg, SUM(s.weight_kg * s.reps) AS tonnage
            FROM set_entry s
            JOIN exercise e ON e.id = s.exercise_id
            WHERE s.done_at BETWEEN ? AND ? AND s.is_warmup = 0
            GROUP BY e.muscle_group
            """.trimIndent(),
            arrayOf(fromMs.toString(), toMs.toString())
        ).mapAll { it.str("mg") to (it.dblOrNull("tonnage") ?: 0.0) }.toMap()

    /**
     * Najlepszy szacowany ciężar maksymalny w każdej sesji z tym ćwiczeniem.
     *
     * Liczone w Kotlinie, a nie w SQL, żeby korzystać z tego samego wzoru co reszta
     * aplikacji - przy jednym powtórzeniu e1RM musi równać się podniesionemu
     * ciężarowi, a wzór Epleya zapisany wprost w zapytaniu dałby tam o 3% za dużo.
     *
     * @return pary (czas sesji, e1RM), od najstarszej.
     */
    fun e1rmHistory(exerciseId: Long, limitSessions: Int = 40): List<Pair<Long, Double>> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM set_entry WHERE exercise_id = ? AND is_warmup = 0 ORDER BY done_at",
            arrayOf(exerciseId.toString())
        ).mapAll(::setFrom)
            .groupBy { it.sessionId }
            .mapNotNull { (_, sets) ->
                val best = sets.maxByOrNull { it.estimated1RM } ?: return@mapNotNull null
                best.doneAt to best.estimated1RM
            }
            .sortedBy { it.first }
            .takeLast(limitSessions)

    /** Ćwiczenia, które mają choć jedną zapisaną serię - tylko one mają historię. */
    fun exercisesWithHistory(): List<Pair<Long, String>> =
        db.readableDatabase.rawQuery(
            """
            SELECT e.id AS id, e.name AS name, MAX(s.done_at) AS t
            FROM set_entry s
            JOIN exercise e ON e.id = s.exercise_id
            WHERE s.is_warmup = 0
            GROUP BY e.id
            ORDER BY t DESC
            """.trimIndent(),
            null
        ).mapAll { it.long("id") to it.str("name") }

    /** Liczba serii roboczych na grupę mięśniową - to, co realnie steruje hipertrofią. */
    fun workingSetsByMuscle(fromMs: Long, toMs: Long): Map<String, Int> =
        db.readableDatabase.rawQuery(
            """
            SELECT e.muscle_group AS mg, COUNT(*) AS n
            FROM set_entry s
            JOIN exercise e ON e.id = s.exercise_id
            WHERE s.done_at BETWEEN ? AND ? AND s.is_warmup = 0
            GROUP BY e.muscle_group
            """.trimIndent(),
            arrayOf(fromMs.toString(), toMs.toString())
        ).mapAll { it.str("mg") to it.int("n") }.toMap()

    /** Ile sesji i serii leży w historii. Do potwierdzenia przed kasowaniem. */
    fun historyCounts(): Pair<Int, Int> {
        val sessions = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS n FROM session", null
        ).use { if (it.moveToFirst()) it.int("n") else 0 }
        val sets = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) AS n FROM set_entry", null
        ).use { if (it.moveToFirst()) it.int("n") else 0 }
        return sessions to sets
    }

    /**
     * Kasuje całą historię treningów: sesje i wszystkie zapisane serie.
     *
     * Plany, programy i ćwiczenia zostają - to nie są wyniki, tylko narzędzia.
     * Zostaje też wszystko, co dotyczy ciała: masa, obwody, profil.
     *
     * Serie kasujemy jawnie, zamiast polegać na kaskadzie z klucza obcego:
     * SQLite egzekwuje ją tylko przy włączonym `PRAGMA foreign_keys`, a to
     * ustawienie łatwo zgubić przy zmianie sposobu otwierania bazy. Osierocone
     * serie byłyby niewidoczne i liczyłyby się do objętości.
     *
     * @return ile sesji usunięto
     */
    fun wipeHistory(): Int {
        val db2 = db.writable
        db2.delete("set_entry", null, null)
        return db2.delete("session", null, null)
    }

    /**
     * Serie robocze na partię w obrębie jednej sesji.
     *
     * Osobno od wersji z zakresem czasu, bo sesja to nie przedział dat:
     * dwa treningi tego samego dnia mają się nie zlewać.
     */
    fun setsByMuscleInSession(sessionId: Long): Map<String, Int> =
        db.readableDatabase.rawQuery(
            """
            SELECT e.muscle_group AS mg, COUNT(*) AS n
            FROM set_entry s
            JOIN exercise e ON e.id = s.exercise_id
            WHERE s.session_id = ? AND s.is_warmup = 0
            GROUP BY e.muscle_group
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).mapAll { it.str("mg") to it.int("n") }.toMap()
}

// ---- Masa ciała ----------------------------------------------------------

class BodyRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = BodyMeasurement(
        id = c.long("id"),
        measuredAt = c.long("measured_at"),
        weightKg = c.dbl("weight_kg"),
        impedanceOhm = c.dblOrNull("impedance_ohm"),
        source = c.str("source"),
        composition = BodyComposition(
            bodyFatPct = c.dblOrNull("body_fat_pct"),
            waterPct = c.dblOrNull("water_pct"),
            muscleMassKg = c.dblOrNull("muscle_mass_kg"),
            boneMassKg = c.dblOrNull("bone_mass_kg"),
            visceralFat = c.dblOrNull("visceral_fat"),
            bmrKcal = c.dblOrNull("bmr_kcal"),
            proteinPct = c.dblOrNull("protein_pct"),
            metabolicAge = c.dblOrNull("metabolic_age")
        )
    )

    /**
     * Zapisuje pomiar, ale pomija duplikat: ta sama masa z tego samego źródła
     * w ciągu [dedupeWindowMs] to ta sama sesja ważenia, a nie nowy pomiar.
     * Waga rozgłasza odczyt wielokrotnie, więc bez tego baza puchłaby od powtórek.
     *
     * @return id zapisanego pomiaru albo null, jeśli uznano go za duplikat.
     */
    fun record(
        weightKg: Double,
        impedanceOhm: Double?,
        source: String,
        at: Long = System.currentTimeMillis(),
        composition: BodyComposition = BodyComposition.EMPTY,
        dedupeWindowMs: Long = 10 * 60 * 1000
    ): Long? {
        // Deduplikacja dotyczy tylko zapisów "na teraz". Przy uzupełnianiu historii
        // wstecz świadomie wpisujesz pomiar z przeszłości i nie chcemy go odrzucać
        // dlatego, że masa akurat się powtarza.
        val isBackfill = at < System.currentTimeMillis() - dedupeWindowMs
        if (!isBackfill) {
            val duplicate = db.readableDatabase.rawQuery(
                """
                SELECT COUNT(*) AS n FROM body_measurement
                WHERE source = ? AND ABS(weight_kg - ?) < 0.005 AND measured_at > ?
                """.trimIndent(),
                arrayOf(source, weightKg.toString(), (at - dedupeWindowMs).toString())
            ).mapAll { it.int("n") }.firstOrNull() ?: 0

            if (duplicate > 0) return null
        }

        return db.writable.insert("body_measurement", null, ContentValues().apply {
            put("measured_at", at)
            put("weight_kg", weightKg)
            if (impedanceOhm != null) put("impedance_ohm", impedanceOhm) else putNull("impedance_ohm")
            put("source", source)
            putOrNull("body_fat_pct", composition.bodyFatPct)
            putOrNull("water_pct", composition.waterPct)
            putOrNull("muscle_mass_kg", composition.muscleMassKg)
            putOrNull("bone_mass_kg", composition.boneMassKg)
            putOrNull("visceral_fat", composition.visceralFat)
            putOrNull("bmr_kcal", composition.bmrKcal)
            putOrNull("protein_pct", composition.proteinPct)
            putOrNull("metabolic_age", composition.metabolicAge)
        })
    }

    fun delete(id: Long): Int =
        db.writable.delete("body_measurement", "id = ?", arrayOf(id.toString()))

    /**
     * Poprawienie istniejącego pomiaru.
     *
     * Celowo bez deduplikacji: przy poprawianiu literówki masa często wraca do
     * wartości, która już gdzieś w bazie jest, i odrzucenie takiej zmiany byłoby
     * absurdem. Deduplikacja pilnuje wyłącznie wielokrotnych zapisów z wagi.
     */
    fun update(m: BodyMeasurement): Int =
        db.writable.update(
            "body_measurement",
            ContentValues().apply {
                put("measured_at", m.measuredAt)
                put("weight_kg", m.weightKg)
                putOrNull("impedance_ohm", m.impedanceOhm)
                put("source", m.source)
                putOrNull("body_fat_pct", m.composition.bodyFatPct)
                putOrNull("water_pct", m.composition.waterPct)
                putOrNull("muscle_mass_kg", m.composition.muscleMassKg)
                putOrNull("bone_mass_kg", m.composition.boneMassKg)
                putOrNull("visceral_fat", m.composition.visceralFat)
                putOrNull("bmr_kcal", m.composition.bmrKcal)
                putOrNull("protein_pct", m.composition.proteinPct)
                putOrNull("metabolic_age", m.composition.metabolicAge)
            },
            "id = ?",
            arrayOf(m.id.toString())
        )

    /** Ostatni pomiar, który ma cokolwiek ze składu ciała. */
    fun latestWithComposition(): BodyMeasurement? =
        db.readableDatabase.rawQuery(
            """
            SELECT * FROM body_measurement
            WHERE body_fat_pct IS NOT NULL OR muscle_mass_kg IS NOT NULL
               OR water_pct IS NOT NULL OR visceral_fat IS NOT NULL
            ORDER BY measured_at DESC LIMIT 1
            """.trimIndent(),
            null
        ).mapAll(::from).firstOrNull()

    fun recent(limit: Int = 90): List<BodyMeasurement> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM body_measurement ORDER BY measured_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).mapAll(::from)

    fun latest(): BodyMeasurement? = recent(1).firstOrNull()

    /**
     * Trend masy wygładzony średnią wykładniczą.
     *
     * Dzienna masa ciała waha się o ±1,5 kg z powodu nawodnienia i treści przewodu
     * pokarmowego - surowy wykres nie mówi nic o tym, czy chudniesz. Dopiero trend
     * pokazuje kierunek. [alpha] 0,1 odpowiada mniej więcej uśrednieniu z 10 dni.
     *
     * @return lista par (czas, masa wygładzona), od najstarszej.
     */
    fun trend(alpha: Double = 0.1, limit: Int = 180): List<Pair<Long, Double>> {
        val raw = db.readableDatabase.rawQuery(
            "SELECT * FROM body_measurement ORDER BY measured_at ASC LIMIT ?",
            arrayOf(limit.toString())
        ).mapAll(::from)

        if (raw.isEmpty()) return emptyList()

        var ema = raw.first().weightKg
        return raw.map { m ->
            ema = alpha * m.weightKg + (1 - alpha) * ema
            m.measuredAt to ema
        }
    }

    /**
     * Tempo zmiany masy w kilogramach na tydzień, policzone z trendu.
     * Dodatnia wartość to przyrost. Zwraca null, gdy danych jest za mało.
     */
    fun weeklyChangeKg(): Double? {
        val t = trend()
        if (t.size < 2) return null
        val (t0, w0) = t.first()
        val (t1, w1) = t.last()
        val days = (t1 - t0) / 86_400_000.0
        if (days < 1.0) return null
        return (w1 - w0) / days * 7.0
    }
}

// ---- Dane z zegarka ------------------------------------------------------

class HealthRepository(context: Context) {
    private val db = Db.get(context)

    private fun sessionFrom(c: Cursor) = ExternalSession(
        id = c.long("id"),
        externalId = c.str("external_id"),
        source = c.str("source"),
        type = c.str("type"),
        title = c.strOrNull("title"),
        startedAt = c.long("started_at"),
        endedAt = c.long("ended_at"),
        distanceM = c.dblOrNull("distance_m"),
        energyKcal = c.dblOrNull("energy_kcal"),
        hrAvg = c.dblOrNull("hr_avg"),
        hrMax = c.dblOrNull("hr_max")
    )

    /**
     * Wstawia albo nadpisuje sesję. CONFLICT_REPLACE na unikalnym [external_id]
     * sprawia, że synchronizację można powtarzać dowolnie często - ponowny import
     * tej samej sesji aktualizuje wiersz zamiast dokładać kopię.
     */
    fun upsertSession(s: ExternalSession): Long =
        db.writable.insertWithOnConflict(
            "external_session", null,
            ContentValues().apply {
                put("external_id", s.externalId)
                put("source", s.source)
                put("type", s.type)
                if (s.title != null) put("title", s.title) else putNull("title")
                put("started_at", s.startedAt)
                put("ended_at", s.endedAt)
                putOrNull("distance_m", s.distanceM)
                putOrNull("energy_kcal", s.energyKcal)
                putOrNull("hr_avg", s.hrAvg)
                putOrNull("hr_max", s.hrMax)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )

    fun sessions(limit: Int = 60): List<ExternalSession> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM external_session ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).mapAll(::sessionFrom)


    /**
     * Wszystkie zapisane sesje, po identyfikatorze zrodlowym.
     *
     * Import nadpisuje wiersze (CONFLICT_REPLACE), wiec przed zapisem trzeba
     * wiedziec, co juz w nich bylo. Bez tego sesja, ktorej w danym przebiegu
     * nie agregujemy, straciłaby wczesniej pobrane tetno i dystans.
     */
    fun allByExternalId(): Map<String, ExternalSession> =
        db.readableDatabase.rawQuery("SELECT * FROM external_session", null)
            .mapAll(::sessionFrom).associateBy { it.externalId }
    fun sessionCount(): Int =
        db.readableDatabase.rawQuery("SELECT COUNT(*) AS n FROM external_session", null)
            .mapAll { it.int("n") }.firstOrNull() ?: 0

    fun lastSyncedAt(): Long? =
        db.readableDatabase.rawQuery("SELECT MAX(started_at) AS t FROM external_session", null)
            .mapAll { it.longOrNull("t") }.firstOrNull()

    fun upsertDaily(m: DailyMetric): Long =
        db.writable.insertWithOnConflict(
            "daily_metric", null,
            ContentValues().apply {
                put("day", m.day)
                put("kind", m.kind.name)
                put("value", m.value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )

    fun daily(kind: MetricKind, limitDays: Int = 90): List<DailyMetric> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM daily_metric WHERE kind = ? ORDER BY day DESC LIMIT ?",
            arrayOf(kind.name, limitDays.toString())
        ).mapAll {
            DailyMetric(
                day = it.long("day"),
                kind = runCatching { MetricKind.valueOf(it.str("kind")) }
                    .getOrDefault(MetricKind.STEPS),
                value = it.dbl("value")
            )
        }

    /** Średnia z ostatnich [days] dni - podstawa pod porównanie z bieżącą wartością. */
    fun dailyAverage(kind: MetricKind, days: Int): Double? {
        val since = System.currentTimeMillis() / 86_400_000L - days
        return db.readableDatabase.rawQuery(
            "SELECT AVG(value) AS a FROM daily_metric WHERE kind = ? AND day >= ?",
            arrayOf(kind.name, since.toString())
        ).mapAll { it.dblOrNull("a") }.firstOrNull()
    }
}

// ---- Obwody ciała --------------------------------------------------------

class MetricsRepository(context: Context) {
    private val db = Db.get(context)

    private fun from(c: Cursor) = BodyMetric(
        id = c.long("id"),
        measuredAt = c.long("measured_at"),
        site = runCatching { MeasurementSite.valueOf(c.str("site")) }
            .getOrDefault(MeasurementSite.WAIST),
        valueCm = c.dbl("value_cm")
    )

    fun record(site: MeasurementSite, valueCm: Double, at: Long = System.currentTimeMillis()): Long =
        db.writable.insert("body_metric", null, ContentValues().apply {
            put("measured_at", at)
            put("site", site.name)
            put("value_cm", valueCm)
        })

    fun delete(id: Long): Int =
        db.writable.delete("body_metric", "id = ?", arrayOf(id.toString()))

    /** Najświeższy pomiar każdego miejsca - to, co pokazujemy jako stan obecny. */
    fun latestPerSite(): Map<MeasurementSite, BodyMetric> =
        db.readableDatabase.rawQuery(
            """
            SELECT m.* FROM body_metric m
            JOIN (
                SELECT site, MAX(measured_at) AS t FROM body_metric GROUP BY site
            ) last ON last.site = m.site AND last.t = m.measured_at
            """.trimIndent(),
            null
        ).mapAll(::from).associateBy { it.site }

    /**
     * Obwody zapisane dokladnie w podanych momentach, pogrupowane po czasie.
     *
     * Przebieg pomiaru zapisuje mase i wszystkie obwody z jednym `measured_at`,
     * wiec zestawienie po rownosci jest jednoznaczne - nie trzeba okna tolerancji.
     *
     * Jedno zapytanie na cala historie zamiast jednego na wpis: przy stu
     * dwudziestu pomiarach to roznica miedzy jednym odczytem a stu dwudziestoma.
     */
    fun forTimes(times: List<Long>): Map<Long, List<BodyMetric>> {
        if (times.isEmpty()) return emptyMap()
        val marks = times.joinToString(",") { "?" }
        return db.readableDatabase.rawQuery(
            "SELECT * FROM body_metric WHERE measured_at IN ($marks)",
            times.map { it.toString() }.toTypedArray()
        ).mapAll(::from).groupBy { it.measuredAt }
    }

    /**
     * Zastepuje komplet obwodow zapisanych w danym momencie.
     *
     * Przy poprawianiu pomiaru nie da sie aktualizowac wiersz po wierszu:
     * obwod mozna usunac (wyczyscic pole) albo dopisac taki, ktorego wczesniej
     * nie bylo. Kasowanie calosci i zapis od nowa jest prostsze i nie zostawia
     * sierot. `previousAt` obsluguje zmiane daty pomiaru - obwody maja isc za
     * masa, a nie zostac w starym terminie.
     */
    fun replaceAt(at: Long, values: Map<MeasurementSite, Double>, previousAt: Long = at) {
        val db2 = db.writable
        db2.delete("body_metric", "measured_at = ?", arrayOf(previousAt.toString()))
        if (previousAt != at) {
            db2.delete("body_metric", "measured_at = ?", arrayOf(at.toString()))
        }
        values.forEach { (site, cm) ->
            db2.insert("body_metric", null, ContentValues().apply {
                put("measured_at", at)
                put("site", site.name)
                put("value_cm", cm)
            })
        }
    }

    fun history(site: MeasurementSite, limit: Int = 60): List<BodyMetric> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM body_metric WHERE site = ? ORDER BY measured_at DESC LIMIT ?",
            arrayOf(site.name, limit.toString())
        ).mapAll(::from)

    /**
     * Zmiana obwodu względem najstarszego pomiaru w ostatnich [days] dniach.
     * To jest właściwa informacja przy obwodach - nie wartość, tylko kierunek.
     */
    fun changeCm(site: MeasurementSite, days: Int = 56): Double? {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val rows = db.readableDatabase.rawQuery(
            "SELECT * FROM body_metric WHERE site = ? AND measured_at >= ? ORDER BY measured_at",
            arrayOf(site.name, since.toString())
        ).mapAll(::from)
        if (rows.size < 2) return null
        return rows.last().valueCm - rows.first().valueCm
    }
}

// ---- Profil użytkownika --------------------------------------------------

class ProfileRepository(context: Context) {
    private val db = Db.get(context)

    fun get(): UserProfile =
        db.readableDatabase.rawQuery("SELECT * FROM user_profile WHERE id = 1", null)
            .mapAll {
                UserProfile(
                    heightCm = it.dblOrNull("height_cm"),
                    sex = runCatching { Sex.valueOf(it.str("sex")) }.getOrDefault(Sex.MALE),
                    birthYear = it.intOrNull("birth_year"),
                    trainingGoal = runCatching {
                        TrainingGoal.valueOf(it.str("training_goal"))
                    }.getOrDefault(TrainingGoal.MIXED),
                    phase = runCatching {
                        Phase.valueOf(it.str("phase"))
                    }.getOrDefault(Phase.MAINTAIN),
                    // Pusty wpis i brak wpisu znaczą to samo: brak ograniczeń.
                    // Gdyby zapisać pustą listę, użytkownik straciłby wszystkie
                    // ćwiczenia zamiast zobaczyć wszystkie.
                    ownedEquipment = it.strOrNull("owned_equipment")
                        ?.split(",")
                        ?.mapNotNull { name -> runCatching { Equipment.valueOf(name) }.getOrNull() }
                        ?.toSet()
                        ?.takeIf { set -> set.isNotEmpty() },
                    limitedWeights = it.intOrNull("limited_weights") == 1,
                    dietRatePct = it.dblOrNull("diet_rate_pct"),
                    tdeeResetDay = it.longOrNull("tdee_reset_day")
                )
            }
            .firstOrNull() ?: UserProfile()

    fun save(p: UserProfile) {
        db.writable.update(
            "user_profile",
            ContentValues().apply {
                putOrNull("height_cm", p.heightCm)
                put("sex", p.sex.name)
                if (p.birthYear != null) put("birth_year", p.birthYear) else putNull("birth_year")
                put("training_goal", p.trainingGoal.name)
                put("phase", p.phase.name)
                val owned = p.ownedEquipment
                if (owned.isNullOrEmpty()) putNull("owned_equipment")
                else put("owned_equipment", owned.joinToString(",") { e -> e.name })
                put("limited_weights", if (p.limitedWeights) 1 else 0)
                putOrNull("diet_rate_pct", p.dietRatePct)
                if (p.tdeeResetDay != null) put("tdee_reset_day", p.tdeeResetDay)
                else putNull("tdee_reset_day")
            },
            "id = ?",
            arrayOf("1")
        )
    }
}

// ---- Sprzęt --------------------------------------------------------------

class GymRepository(context: Context) {
    private val db = Db.get(context)

    fun inventory(): Inventory {
        val bar = db.readableDatabase
            .rawQuery("SELECT bar_kg FROM gym_profile WHERE id = 1", null)
            .mapAll { it.dbl("bar_kg") }
            .firstOrNull() ?: 20.0

        // Tryb obciążeń mieszka w profilu, bo to decyzja o tym, gdzie trenujesz,
        // a nie właściwość samych talerzy.
        val limited = db.readableDatabase
            .rawQuery("SELECT limited_weights FROM user_profile WHERE id = 1", null)
            .mapAll { it.intOrNull("limited_weights") == 1 }
            .firstOrNull() ?: false

        val plates = db.readableDatabase
            .rawQuery("SELECT * FROM plate ORDER BY kg DESC", null)
            .mapAll {
                Plate(
                    id = it.long("id"),
                    kg = it.dbl("kg"),
                    count = it.int("count"),
                    fitsBarbell = it.bool("fits_barbell")
                )
            }

        return Inventory(bar, plates, unlimited = !limited)
    }

    fun setBarWeight(kg: Double) {
        db.writable.update(
            "gym_profile", ContentValues().apply { put("bar_kg", kg) },
            "id = ?", arrayOf("1")
        )
    }

    fun upsertPlate(p: Plate): Long {
        val values = ContentValues().apply {
            put("kg", p.kg)
            put("count", p.count)
            put("fits_barbell", if (p.fitsBarbell) 1 else 0)
        }
        return if (p.id == 0L) {
            db.writable.insert("plate", null, values)
        } else {
            db.writable.update("plate", values, "id = ?", arrayOf(p.id.toString()))
            p.id
        }
    }

    fun deletePlate(id: Long): Int =
        db.writable.delete("plate", "id = ?", arrayOf(id.toString()))
}
