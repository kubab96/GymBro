package pl.gymbro.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Baza danych aplikacji.
 *
 * Świadomie bez Room: Room wymaga procesora adnotacji KSP, czyli kolejnej wtyczki
 * Gradle z własną macierzą zgodności wersji. Tutaj mamy zwykły SQLite z biblioteki
 * standardowej Androida - zero dodatkowych zależności i pełna kontrola nad SQL-em.
 *
 * Migracje: każda zmiana schematu podbija [VERSION] i dopisuje gałąź w [onUpgrade].
 * Nigdy nie kasujemy tabel z danymi użytkownika przy aktualizacji.
 */
class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "gymbro.db"

        /**
         * 1 - schemat początkowy
         * 2 - neutralna biblioteka ćwiczeń (dociągana też do istniejących instalacji)
         * 3 - skład ciała w pomiarach masy
         * 4 - obwody ciała i profil użytkownika
         * 5 - dane z zegarka: sesje zewnętrzne i metryki dzienne
         * 6 - posiadany sprzęt i tryb obciążeń w profilu
         * 7 - programy treningowe: poziom nad jednostkami (FBW → A/B/C, PPL → Push/Pull/Legs)
         * 8 - indeks pod wyszukiwanie ostatniego wykonania jednostki
         * 12 - cel treningowy i faza w profilu, wyciszanie progresji w planie
         * 13 - propozycja progresji zapisana przy pozycji planu
         * 14 - znacznik sesji zalozonej na zegarku, bez lacznosci
         * 15 - znacznik sesji, dla ktorej policzono juz propozycje
         */
        const val VERSION = 15

        /**
         * Bez tego indeksu zapytanie o ostatnie ukończenie jednostki skanowało
         * całą tabelę sesji. Przy rosnącej historii to z czasem robi się odczuwalne.
         */
        /**
         * Wejścia do silnika propozycji progresji.
         *
         * Cel treningowy decyduje, czy podnosić ciężar czy objętość - ciężar
         * buduje siłę, objętość masę, a literatura rozdziela to wyraźnie.
         * Faza nie zmienia tego, co podnosić; zmienia to, jak czytać brak
         * postępu, bo na redukcji utrzymanie ciężaru jest sukcesem.
         *
         * Cel w pozycji planu nadpisuje ten z profilu: w jednym treningu
         * przysiad bywa ćwiczeniem siłowym, a uginanie masowym.
         *
         * `no_progress` wycisza ćwiczenie - rozgrzewkowe i rehabilitacyjne
         * nie mają rosnąć, a cotygodniowa propozycja przy nich to szum,
         * który uczy ignorować całą kartę.
         */
        private val V12_COLUMNS = listOf(
            "ALTER TABLE user_profile ADD COLUMN training_goal TEXT",
            "ALTER TABLE user_profile ADD COLUMN phase TEXT",
            "ALTER TABLE plan_item ADD COLUMN goal TEXT",
            "ALTER TABLE plan_item ADD COLUMN no_progress INTEGER NOT NULL DEFAULT 0"
        )

        /**
         * Propozycja progresji przy pozycji planu.
         *
         * Zapisana, a nie policzona w locie: ma przetrwac zamkniecie aplikacji
         * i czekac, az ja przyjmiesz albo odrzucisz. Jedna na pozycje, bo
         * kolejny trening nadpisuje poprzednia - inaczej zbieralaby sie kolejka
         * nieaktualnych podpowiedzi sprzed tygodni.
         *
         * `adv_seen` dotyczy wylacznie plakietki przy zakladce: znika po
         * zajrzeniu, ale sama propozycja zostaje do decyzji.
         */
        private val V13_COLUMNS = listOf(
            "ALTER TABLE plan_item ADD COLUMN adv_kind TEXT",
            "ALTER TABLE plan_item ADD COLUMN adv_weight REAL",
            "ALTER TABLE plan_item ADD COLUMN adv_reps_to INTEGER",
            "ALTER TABLE plan_item ADD COLUMN adv_sets INTEGER",
            "ALTER TABLE plan_item ADD COLUMN adv_reason TEXT",
            "ALTER TABLE plan_item ADD COLUMN adv_last TEXT",
            "ALTER TABLE plan_item ADD COLUMN adv_session INTEGER",
            "ALTER TABLE plan_item ADD COLUMN adv_seen INTEGER NOT NULL DEFAULT 0"
        )

        /**
         * Znacznik sesji nadany przez zegarek.
         *
         * Trening da sie zaczac bez telefonu, wiec numer sesji powstaje
         * wtedy na zegarku. Telefon dopisuje ja przy synchronizacji, a ten
         * znacznik pilnuje, zeby ponowione zgloszenie nie zalozylo jej
         * drugi raz. Indeks jest czesciowy, bo sesje zakladane na telefonie
         * znacznika nie maja i NULL-e nie moga sie gryzc.
         */
        private val V14_STATEMENTS = listOf(
            "ALTER TABLE session ADD COLUMN local_id TEXT",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_session_local " +
                "ON session(local_id) WHERE local_id IS NOT NULL"
        )

        /**
         * Czy dla tej sesji policzono juz propozycje progresji.
         *
         * Liczymy je przy zakonczeniu treningu - ale trening nie zawsze
         * konczy sie nacisnieciem "Zakoncz": bywa porzucony i zamkniety
         * po osmiu godzinach, a wtedy nie liczyl ich nikt. Ten znacznik
         * pozwala je dolaczyc pozniej, i tylko raz, zeby odrzucone
         * propozycje nie wracaly same.
         */
        private val V15_STATEMENTS = listOf(
            "ALTER TABLE session ADD COLUMN advised INTEGER NOT NULL DEFAULT 0"
        )

        private val V8_STATEMENTS = listOf(
            "CREATE INDEX IF NOT EXISTS idx_session_plan ON session(plan_id, ended_at)"
        )

        /**
         * Wprowadzenie programów.
         *
         * Przy aktualizacji istniejące jednostki trzeba gdzieś przypiąć, więc
         * tworzymy dla nich program zbiorczy - ale TYLKO jeśli jakieś istnieją.
         * Na świeżej instalacji baza zostaje pusta, zgodnie z zasadą, że aplikacja
         * nie wymyśla za użytkownika treści.
         */
        private val V7_STATEMENTS = listOf(
            """
            CREATE TABLE program (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                name      TEXT    NOT NULL,
                note      TEXT,
                position  INTEGER NOT NULL DEFAULT 0,
                is_active INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            "ALTER TABLE plan ADD COLUMN program_id INTEGER",
            "CREATE INDEX idx_plan_program ON plan(program_id, position)",
            """
            INSERT INTO program (name, note, position, is_active)
            SELECT 'Mój plan', 'Jednostki sprzed wprowadzenia programów', 0, 1
            WHERE EXISTS (SELECT 1 FROM plan)
            """.trimIndent(),
            """
            UPDATE plan SET program_id = (SELECT id FROM program ORDER BY id LIMIT 1)
            WHERE program_id IS NULL
            """.trimIndent()
        )

        /** DDL z wersji 5 - wspólny dla CREATE i migracji. */
        private val V6_COLUMNS = listOf(
            "ALTER TABLE user_profile ADD COLUMN owned_equipment TEXT",
            "ALTER TABLE user_profile ADD COLUMN limited_weights INTEGER NOT NULL DEFAULT 0"
        )

        private val V5_TABLES = listOf(
            """
            CREATE TABLE external_session (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                external_id TEXT    NOT NULL UNIQUE,
                source      TEXT    NOT NULL,
                type        TEXT    NOT NULL,
                title       TEXT,
                started_at  INTEGER NOT NULL,
                ended_at    INTEGER NOT NULL,
                distance_m  REAL,
                energy_kcal REAL,
                hr_avg      REAL,
                hr_max      REAL
            )
            """.trimIndent(),
            "CREATE INDEX idx_ext_session_time ON external_session(started_at DESC)",
            // Klucz (dzień, rodzaj) pozwala synchronizować wielokrotnie bez duplikatów:
            // ponowny import tego samego dnia nadpisuje wartość zamiast ją dokładać.
            """
            CREATE TABLE daily_metric (
                id    INTEGER PRIMARY KEY AUTOINCREMENT,
                day   INTEGER NOT NULL,
                kind  TEXT    NOT NULL,
                value REAL    NOT NULL,
                UNIQUE(day, kind)
            )
            """.trimIndent(),
            "CREATE INDEX idx_daily_kind_day ON daily_metric(kind, day DESC)"
        )

        /** DDL obu tabel z wersji 4 - wspólny dla CREATE i migracji. */
        private val V4_TABLES = listOf(
            """
            CREATE TABLE body_metric (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                measured_at INTEGER NOT NULL,
                site        TEXT    NOT NULL,
                value_cm    REAL    NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX idx_metric_site_time ON body_metric(site, measured_at DESC)",
            """
            CREATE TABLE user_profile (
                id         INTEGER PRIMARY KEY CHECK (id = 1),
                height_cm  REAL,
                sex        TEXT NOT NULL DEFAULT 'MALE',
                birth_year INTEGER,
                training_goal TEXT,
                phase         TEXT
            )
            """.trimIndent(),
            "INSERT INTO user_profile (id, sex) VALUES (1, 'MALE')"
        )

        /** Kolumny składu ciała - wspólne dla CREATE i migracji, żeby się nie rozjechały. */
        private val COMPOSITION_COLUMNS = listOf(
            "body_fat_pct", "water_pct", "muscle_mass_kg", "bone_mass_kg",
            "visceral_fat", "bmr_kcal", "protein_pct", "metabolic_age"
        )


        /**
         * Sprzątanie po wadliwym kasowaniu programów.
         *
         * `plan.program_id` dodano przez ALTER TABLE, a SQLite nie potrafi
         * dorobić w ten sposób klucza obcego - żadna kaskada przy programie
         * nigdy nie działała. Usunięcie programu zostawiało jego jednostki
         * jako sieroty: z ekranów znikały, bo listy filtrują po programie,
         * ale dalej trzymały ćwiczenia i blokowały ich usunięcie z biblioteki.
         *
         * Ćwiczenia jednostek kasujemy jawnie, zamiast liczyć na kaskadę
         * plan_item → plan: ta jest prawdziwym kluczem obcym, ale zależy od
         * `PRAGMA foreign_keys`, a tu akurat nie ma po co ryzykować.
         */
        private val V9_STATEMENTS = listOf(
            """
            DELETE FROM plan_item
            WHERE plan_id IN (
                SELECT id FROM plan
                WHERE program_id IS NOT NULL
                  AND program_id NOT IN (SELECT id FROM program)
            )
            """.trimIndent(),
            """
            DELETE FROM plan
            WHERE program_id IS NOT NULL
              AND program_id NOT IN (SELECT id FROM program)
            """.trimIndent()
        )

        /**
         * Pauzy w zapisanych nazwach na krótkie myślniki.
         *
         * W kodzie poprawione raz, ale nazwy jednostek i programów wpisałeś
         * wcześniej - i one siedzą w bazie, nie w kodzie. Bez tego "A - sztanga"
         * w nowych tekstach sąsiadowałoby z "A - sztanga" ze starą pauzą.
         *
         * char(8212) to pauza, char(8211) półpauza. Podane numerem, bo wklejony
         * w SQL znak spoza ASCII łatwo zgubić przy kolejnej edycji pliku.
         */
        private val V10_STATEMENTS = listOf("program", "plan", "exercise")
            .map { table ->
                """
                UPDATE $table
                SET name = REPLACE(REPLACE(name, char(8212), '-'), char(8211), '-')
                WHERE name LIKE '%' || char(8212) || '%'
                   OR name LIKE '%' || char(8211) || '%'
                """.trimIndent()
            } + listOf(
            """
            UPDATE session
            SET note = REPLACE(REPLACE(note, char(8212), '-'), char(8211), '-')
            WHERE note IS NOT NULL
            """.trimIndent(),
            """
            UPDATE external_session
            SET type = REPLACE(REPLACE(type, char(8212), '-'), char(8211), '-'),
                title = REPLACE(REPLACE(title, char(8212), '-'), char(8211), '-')
            """.trimIndent()
        )

        /**
         * Wstrzymywanie treningu.
         *
         * `paused_at` to chwila wstrzymania (NULL = trening idzie), a
         * `paused_ms` to suma już odstanych przerw. Czas treningu liczy się
         * od startu minus te dwie rzeczy - inaczej wyjście na dziesięć minut
         * zawyżałoby każde podsumowanie.
         *
         * Serie mają własne znaczniki czasu, więc pauza nie rusza danych
         * treningowych. Dotyczy wyłącznie tego, co pokazuje zegar.
         */
        private val V11_STATEMENTS = listOf(
            "ALTER TABLE session ADD COLUMN paused_at INTEGER",
            "ALTER TABLE session ADD COLUMN paused_ms INTEGER NOT NULL DEFAULT 0"
        )
        @Volatile
        private var instance: Db? = null

        /** Jedna instancja na proces - SQLiteOpenHelper sam pilnuje wątków. */
        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        // Klucze obce nie są w SQLite domyślnie egzekwowane.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exercise (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                name          TEXT    NOT NULL,
                equipment     TEXT    NOT NULL,
                muscle_group  TEXT    NOT NULL DEFAULT '',
                is_custom     INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX idx_exercise_name ON exercise(name)")

        db.execSQL(
            """
            CREATE TABLE plan (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                name     TEXT    NOT NULL,
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE plan_item (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id     INTEGER NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
                exercise_id INTEGER NOT NULL REFERENCES exercise(id),
                position    INTEGER NOT NULL DEFAULT 0,
                target_kg   REAL    NOT NULL DEFAULT 0,
                sets        INTEGER NOT NULL DEFAULT 3,
                reps_from   INTEGER NOT NULL DEFAULT 8,
                reps_to     INTEGER NOT NULL DEFAULT 12,
                rest_sec    INTEGER NOT NULL DEFAULT 120,
                goal        TEXT,
                no_progress INTEGER NOT NULL DEFAULT 0,
                adv_kind    TEXT,
                adv_weight  REAL,
                adv_reps_to INTEGER,
                adv_sets    INTEGER,
                adv_reason  TEXT,
                adv_last    TEXT,
                adv_session INTEGER,
                adv_seen    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_plan_item_plan ON plan_item(plan_id, position)")

        db.execSQL(
            """
            CREATE TABLE session (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id    INTEGER REFERENCES plan(id) ON DELETE SET NULL,
                started_at INTEGER NOT NULL,
                ended_at   INTEGER,
                note       TEXT,
                paused_at  INTEGER,
                paused_ms  INTEGER NOT NULL DEFAULT 0,
                local_id   TEXT,
                advised    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_session_started ON session(started_at DESC)")
        db.execSQL(
            "CREATE UNIQUE INDEX idx_session_local ON session(local_id) " +
                "WHERE local_id IS NOT NULL"
        )

        db.execSQL(
            """
            CREATE TABLE set_entry (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id  INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE,
                exercise_id INTEGER NOT NULL REFERENCES exercise(id),
                set_index   INTEGER NOT NULL,
                weight_kg   REAL    NOT NULL,
                reps        INTEGER NOT NULL,
                rir         INTEGER,
                is_warmup   INTEGER NOT NULL DEFAULT 0,
                done_at     INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // Ten indeks obsługuje najczęstsze zapytanie w aplikacji:
        // "co podniosłem ostatnim razem w tym ćwiczeniu".
        db.execSQL("CREATE INDEX idx_set_exercise_time ON set_entry(exercise_id, done_at DESC)")
        db.execSQL("CREATE INDEX idx_set_session ON set_entry(session_id, set_index)")

        db.execSQL(
            """
            CREATE TABLE body_measurement (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                measured_at    INTEGER NOT NULL,
                weight_kg      REAL    NOT NULL,
                impedance_ohm  REAL,
                source         TEXT    NOT NULL,
                body_fat_pct   REAL,
                water_pct      REAL,
                muscle_mass_kg REAL,
                bone_mass_kg   REAL,
                visceral_fat   REAL,
                bmr_kcal       REAL,
                protein_pct    REAL,
                metabolic_age  REAL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_body_time ON body_measurement(measured_at DESC)")

        db.execSQL(
            """
            CREATE TABLE plate (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                kg            REAL    NOT NULL,
                count         INTEGER NOT NULL,
                fits_barbell  INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )

        // Tabela jednowierszowa na ustawienia sprzętu. Wymuszamy jeden wiersz
        // kluczem głównym o stałej wartości.
        db.execSQL(
            """
            CREATE TABLE gym_profile (
                id     INTEGER PRIMARY KEY CHECK (id = 1),
                bar_kg REAL NOT NULL
            )
            """.trimIndent()
        )

        V4_TABLES.forEach { db.execSQL(it) }
        V5_TABLES.forEach { db.execSQL(it) }
        V6_COLUMNS.forEach { db.execSQL(it) }
        V7_STATEMENTS.forEach { db.execSQL(it) }
        V8_STATEMENTS.forEach { db.execSQL(it) }

        Seed.populate(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migracje dopisujemy jako kolejne if-y, nigdy jako DROP TABLE.
        // Dane użytkownika muszą przetrwać każdą aktualizację aplikacji.

        if (oldVersion < 2) {
            // Biblioteka ćwiczeń trafia też do baz założonych wcześniej.
            // Wstawianie pomija konflikty nazw, więc własne ćwiczenia
            // użytkownika zostają nienaruszone.
            Seed.seedExerciseLibrary(db)
        }

        if (oldVersion < 3) {
            // ALTER TABLE ADD COLUMN dokłada kolumny bez ruszania istniejących
            // wierszy - dotychczasowe pomiary zostają, po prostu mają tu NULL.
            COMPOSITION_COLUMNS.forEach { col ->
                db.execSQL("ALTER TABLE body_measurement ADD COLUMN $col REAL")
            }
        }

        if (oldVersion < 4) {
            V4_TABLES.forEach { db.execSQL(it) }
        }

        if (oldVersion < 5) {
            V5_TABLES.forEach { db.execSQL(it) }
        }

        if (oldVersion < 6) {
            V6_COLUMNS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 7) {
            V7_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 8) {
            V8_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 9) {
            V9_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 10) {
            V10_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 11) {
            V11_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 12) {
            V12_COLUMNS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 13) {
            V13_COLUMNS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 14) {
            V14_STATEMENTS.forEach { db.execSQL(it) }
        }

        if (oldVersion < 15) {
            V15_STATEMENTS.forEach { db.execSQL(it) }
        }
    }
}
