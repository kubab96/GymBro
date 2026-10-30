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

    private val LIBRARY: List<Triple<String, Equipment, String>> = listOf(
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
        Triple("Zwis na drążku", Equipment.BODYWEIGHT, "przedramiona")
    )
}
