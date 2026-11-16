package pl.gymbro.data

import pl.gymbro.data.PlanLibrary.Level
import pl.gymbro.data.PlanLibrary.ProgramTemplate

/**
 * Druga partia gotowych planów - patrz [PlanLibrary], tam są zasady.
 *
 * Pierwsza partia pokrywała typowe podziały. Ta celuje w konkretne zestawy
 * sprzętu, które da się teraz zaznaczyć w Profilu (sztanga w garażu, domowa
 * siłownia z wyciągiem, gumy z drążkiem, kalistenika z pasem) i w sytuacje,
 * z którymi przychodzi się po plan: powrót po przerwie, siedząca praca,
 * mało czasu, sport.
 */
internal val MORE_GYM_PROGRAMS: List<ProgramTemplate> = listOf(
    ProgramTemplate(
        name = "Góra / Dół - 2 dni",
        summary = "Góra i dół ciała, każde raz w tygodniu",
        description = "Dwa treningi: jeden na górę, jeden na dół, z co najmniej dniem " +
            "przerwy między nimi. Mało czasu, a każda partia dostaje swoje.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Góra", "Wyciskanie, wiosłowanie, barki, ramiona",
                heavy("Wyciskanie sztangi leżąc", 3, 6, 10),
                heavy("Wiosłowanie sztangą", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                iso("Uginanie młotkowe", 2, 10, 15),
                iso("Prostowanie liną na wyciągu", 2, 10, 15)
            ),
            unit(
                "Dół", "Przysiad, tył uda, łydki, brzuch",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki z hantlami", 2, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 3, 10, 15),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Powrót po przerwie - 2 dni",
        summary = "Lekko, na maszynach, dwie serie zamiast trzech",
        description = "Na pierwsze tygodnie po kilku miesiącach przerwy albo po chorobie. " +
            "Maszyny prowadzą ruch, serii jest mało, powtórzeń dużo - ciężar " +
            "rośnie, gdy wszystko idzie lekko. Potem przejdź na zwykły plan.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Powrót A", "Suwnica, wyciskanie, ściąganie",
                aux("Suwnica", 2, 12, 15),
                aux("Wyciskanie na maszynie", 2, 10, 15),
                aux("Ściąganie drążka wyciągu górnego", 2, 10, 15),
                iso("Unoszenie bokiem na maszynie", 2, 12, 15),
                core("Martwy robak", 2, 8, 12)
            ),
            unit(
                "Powrót B", "Goblet, wiosłowanie, barki",
                aux("Przysiad goblet", 2, 10, 15),
                aux("Wiosłowanie na maszynie", 2, 10, 15),
                aux("Wyciskanie hantli nad głowę", 2, 10, 15),
                iso("Uginanie nóg na maszynie", 2, 12, 15),
                core("Unoszenie ręki i nogi w klęku podpartym", 2, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Pięć ruchów ze sztangą - 3 dni",
        summary = "Przysiad, wyciskania, martwy ciąg, wiosłowanie",
        description = "Dwa treningi na zmianę, prawie same ruchy ze sztangą i mało " +
            "ćwiczeń. Ciężar ma rosnąć z treningu na trening, dopóki da się " +
            "robić czyste powtórzenia.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Przysiad, wyciskanie leżąc, martwy ciąg",
                heavy("Przysiad ze sztangą", 3, 4, 6),
                heavy("Wyciskanie sztangi leżąc", 3, 4, 6),
                heavy("Martwy ciąg", 2, 3, 5),
                aux("Podciąganie na drążku", 3, 3, 8),
                core("Unoszenie nóg w zwisie", 3, 6, 12)
            ),
            unit(
                "B", "Przysiad, wyciskanie nad głowę, wiosłowanie",
                heavy("Przysiad ze sztangą", 3, 4, 6),
                heavy("OHP", 3, 4, 6),
                heavy("Wiosłowanie sztangą z podłogi", 3, 4, 6),
                aux("Dipy na poręczach", 3, 5, 10),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Plecy i postawa - 3 dni",
        summary = "Dla pracujących przy biurku: tył ciała i stabilizacja",
        description = "Więcej ciągnięcia niż pchania, dużo tylnej części barków, pośladki " +
            "i brzuch w roli stabilizatora. Trzy treningi na zmianę, umiarkowane " +
            "ciężary.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Postawa A", "Wiosłowanie, face pull, tył uda",
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Face pull", 3, 12, 15),
                aux("Martwy ciąg rumuński z hantlami", 3, 10, 12),
                aux("Wyciskanie hantli neutralnym chwytem", 3, 8, 12),
                iso("Unoszenie Y-T-W leżąc", 3, 8, 12),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "Postawa B", "Ściąganie, odwrotne rozpiętki, pośladki",
                aux("Ściąganie drążka wąskim chwytem", 3, 10, 12),
                iso("Odwrotne rozpiętki na bramie", 3, 12, 15),
                aux("Hip thrust", 3, 8, 12),
                aux("Pompki", 3, 8, 15),
                core("Unoszenie ręki i nogi w klęku podpartym", 3, 8, 12),
                core("Wyciskanie Pallofa na wyciągu", 3, 10, 12)
            ),
            unit(
                "Postawa C", "Wiosłowanie z podparciem, prostowniki, jedna noga",
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 8, 12),
                iso("Wyprosty tułowia na ławce rzymskiej", 3, 10, 15),
                aux("Wejścia na skrzynię z hantlami", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Superman", 3, 10, 15),
                core("Unoszenie bioder w podporze bokiem", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Całe ciało - 5 krótkich treningów",
        summary = "Cztery ćwiczenia dziennie, pięć razy w tygodniu",
        description = "Krótkie treningi całego ciała w dni robocze, każdy w pół godziny. " +
            "Objętość tygodnia jak przy splicie, tylko rozłożona na więcej " +
            "krótszych wizyt - dobre, gdy siłownia jest po drodze.",
        days = listOf(5),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Dzień 1", "Przysiad, wyciskanie hantli, wiosłowanie",
                heavy("Przysiad ze sztangą", 3, 5, 8),
                aux("Wyciskanie hantli leżąc", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Unoszenie hantli bokiem", 2, 12, 15)
            ),
            unit(
                "Dzień 2", "Tył uda, wyciskanie nad głowę, podciąganie",
                heavy("Martwy ciąg rumuński", 3, 6, 10),
                heavy("OHP", 3, 5, 8),
                aux("Podciąganie na drążku", 3, 5, 10),
                iso("Uginanie młotkowe", 2, 10, 15)
            ),
            unit(
                "Dzień 3", "Suwnica, wyciskanie sztangi, wiosłowanie hantlami",
                aux("Suwnica", 3, 10, 15),
                heavy("Wyciskanie sztangi leżąc", 3, 5, 8),
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                iso("Prostowanie liną na wyciągu", 2, 10, 15)
            ),
            unit(
                "Dzień 4", "Jedna noga, skos, ściąganie",
                aux("Bułgarskie przysiady", 3, 8, 12),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                iso("Face pull", 2, 12, 15)
            ),
            unit(
                "Dzień 5", "Martwy ciąg, poręcze, brzuch",
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Dipy na poręczach", 3, 6, 12),
                aux("Wiosłowanie jednorącz", 3, 8, 12),
                core("Spięcia brzucha na wyciągu", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Pośladki i nogi - 4 dni",
        summary = "Dwa dni na pośladki i nogi, dwa na górę",
        description = "Rozwinięcie planu na trzy dni: pośladki i nogi dwa razy w tygodniu, " +
            "raz ciężej na hip thrust, raz na przysiad. Góra ciała w dwóch " +
            "lżejszych dniach, żeby nie zostawała w tyle.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Pośladki ciężko", "Hip thrust, rumuński, bułgarskie",
                heavy("Hip thrust", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 6, 10),
                aux("Bułgarskie przysiady", 3, 8, 12),
                iso("Odwodzenie nóg na maszynie", 3, 12, 15),
                iso("Wyprost bioder na ławce rzymskiej", 3, 10, 15)
            ),
            unit(
                "Góra A", "Ściąganie, skos, wiosłowanie",
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Nogi i pośladki", "Przysiad, suwnica, maszyny",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                aux("Suwnica", 3, 10, 15),
                aux("Hip thrust na maszynie", 3, 10, 12),
                iso("Odrzut nogi w tył na wyciągu", 3, 12, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 10, 15)
            ),
            unit(
                "Góra B", "Wiosłowanie, barki, ramiona",
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                aux("Pompki", 3, 8, 15),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie hantli naprzemienne", 3, 10, 15),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Atletyczny - 3 dni",
        summary = "Skoki, rzuty i siła - pod sport, nie pod lustro",
        description = "Każdy trening zaczyna się od ruchu szybkiego (skoki, rzuty, " +
            "pompki z klaśnięciem), potem idzie ciężko. Dla grających w piłkę, " +
            "biegających i trenujących sporty walki.",
        days = listOf(3),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Moc i nogi", "Skoki, przysiad, swing",
                iso("Skoki na skrzynię", 4, 3, 6),
                heavy("Przysiad ze sztangą", 4, 4, 6),
                aux("Swing kettlebell", 3, 10, 15),
                aux("Wykroki chodzone z hantlami", 3, 8, 12),
                core("Rzuty piłką o podłogę", 3, 8, 12)
            ),
            unit(
                "Moc i góra", "Pompki z klaśnięciem, podrzut, podciąganie",
                aux("Pompki z klaśnięciem", 4, 5, 8),
                heavy("Wyciskanie z podrzutem", 4, 4, 6),
                aux("Podciąganie na drążku", 4, 5, 10),
                aux("Wiosłowanie sztangą", 3, 6, 10),
                core("Rzuty piłką lekarską o ścianę", 3, 10, 15)
            ),
            unit(
                "Siła całego ciała", "Trap bar, wyciskanie, sanki",
                heavy("Martwy ciąg z trap bar", 4, 3, 6),
                heavy("Wyciskanie sztangi leżąc", 4, 4, 6),
                aux("Pchanie sanek", 4, 4, 8),
                aux("Zarzut kettlebell", 3, 5, 8),
                iso("Uginanie nordyckie", 3, 3, 6)
            )
        )
    ),

    ProgramTemplate(
        name = "Klasyczny podział - 6 dni",
        summary = "Klatka z plecami, barki z ramionami, nogi - dwa razy",
        description = "Trzy treningi powtarzane dwa razy w tygodniu, jeden dzień wolny. " +
            "Dużo serii i mało przerw między dniami - tylko dla tych, którzy " +
            "dobrze się regenerują i śpią.",
        days = listOf(6),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Klatka i plecy", "Wyciskanie i podciąganie na przemian",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("Podciąganie szerokim chwytem", 4, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                iso("Rozpiętki na ławce", 3, 10, 15),
                iso("Przenoszenie hantla za głowę", 3, 10, 15)
            ),
            unit(
                "Barki i ramiona", "Wyciskanie nad głowę, unoszenia, biceps, triceps",
                heavy("OHP", 4, 6, 10),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Francuskie sztangą łamaną leżąc", 3, 8, 12),
                iso("Uginanie koncentryczne", 2, 10, 15),
                aux("Dipy na poręczach", 2, 8, 12)
            ),
            unit(
                "Nogi", "Przysiad, tył uda, maszyny, łydki",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Suwnica", 3, 10, 15),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15),
                core("Unoszenie nóg w zwisie", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Siła i masa - 5 dni",
        summary = "Dwa dni ciężko, trzy na objętość, każda partia osobno",
        description = "Góra i dół w niskim zakresie na początku tygodnia, potem trzy dni " +
            "na objętość: plecy z barkami, nogi, klatka z ramionami. Dużo pracy - " +
            "dla zaawansowanych.",
        days = listOf(5),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Góra siła", "Wyciskanie, wiosłowanie, podciąganie z obciążeniem",
                heavy("Wyciskanie sztangi leżąc", 4, 3, 5),
                heavy("Wiosłowanie sztangą", 4, 4, 6),
                aux("Podciąganie z obciążeniem", 3, 4, 8),
                aux("OHP", 3, 5, 8),
                iso("Uginanie sztangi stojąc", 2, 6, 10),
                iso("Francuskie sztangą", 2, 6, 10)
            ),
            unit(
                "Dół siła", "Przysiad i martwy ciąg ciężko",
                heavy("Przysiad ze sztangą", 4, 3, 5),
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Suwnica", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 6, 10),
                iso("Wspięcia na palce", 4, 8, 12)
            ),
            unit(
                "Plecy i barki", "Objętość: wiosłowania i unoszenia",
                aux("Wiosłowanie T-sztangą", 4, 8, 12),
                aux("Ściąganie drążka wąskim chwytem", 3, 10, 12),
                aux("Wiosłowanie jednorącz na wyciągu", 3, 10, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Face pull", 3, 12, 15)
            ),
            unit(
                "Nogi objętość", "Hack, jedna noga, maszyny",
                aux("Hack przysiad", 4, 8, 12),
                aux("Martwy ciąg rumuński z hantlami", 3, 10, 12),
                aux("Bułgarskie przysiady", 3, 10, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 12, 15),
                iso("Wspięcia na palce siedząc", 4, 12, 15)
            ),
            unit(
                "Klatka i ramiona", "Objętość: skos, maszyna, wyciągi",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wyciskanie na maszynie", 3, 10, 12),
                iso("Rozpiętki na bramie z góry", 3, 12, 15),
                iso("Uginanie hantli przodem na ławce skośnej", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15),
                iso("Uginanie liną na wyciągu", 3, 10, 15)
            )
        )
    )
)

internal val MORE_HOME_PROGRAMS: List<ProgramTemplate> = listOf(
    ProgramTemplate(
        name = "Sztanga w garażu - 3 dni",
        summary = "Sztanga, stojaki i ławka - nic więcej",
        description = "Trzy treningi na samą sztangę: stojaki do przysiadu i wyciskania, " +
            "ławka i komplet talerzy. Bez hantli, wyciągu i drążka.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Przysiad, wyciskanie leżąc, wiosłowanie",
                heavy("Przysiad ze sztangą", 3, 5, 8),
                heavy("Wyciskanie sztangi leżąc", 3, 5, 8),
                heavy("Wiosłowanie sztangą", 3, 6, 10),
                iso("Uginanie sztangi stojąc", 2, 8, 12),
                iso("Francuskie sztangą", 2, 8, 12)
            ),
            unit(
                "B", "Martwy ciąg, wyciskanie nad głowę, wykroki",
                heavy("Martwy ciąg", 3, 3, 5),
                heavy("OHP", 3, 5, 8),
                aux("Wykroki ze sztangą", 3, 8, 12),
                aux("Hip thrust", 3, 8, 12),
                iso("Szrugsy ze sztangą", 3, 10, 15)
            ),
            unit(
                "C", "Przysiad przedni, wyciskanie wąsko, good morning",
                heavy("Przysiad przedni", 3, 6, 10),
                heavy("Wyciskanie wąsko", 3, 6, 10),
                aux("Wiosłowanie sztangą podchwytem", 3, 8, 12),
                aux("Skłony good morning", 3, 8, 12),
                iso("Wspięcia na palce stojąc ze sztangą", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Domowa siłownia z wyciągiem - 4 dni",
        summary = "Sztanga, hantle, ławka, drążek i wyciąg - bez maszyn",
        description = "Góra i dół na zmianę, z wyciągiem tam, gdzie wolny ciężar nie " +
            "wystarcza: prostowania, uginania, face pull, pull-through. Dla " +
            "domowej siłowni z atlasem albo samym wyciągiem.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra A", "Wyciskanie i wiosłowanie sztangą",
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                heavy("Wiosłowanie sztangą", 4, 6, 10),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Prostowanie liną na wyciągu", 3, 10, 15),
                iso("Uginanie na wyciągu dolnym", 3, 10, 15)
            ),
            unit(
                "Dół A", "Przysiad, rumuński, pull-through",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki z hantlami", 3, 8, 12),
                iso("Pull-through na wyciągu", 3, 12, 15),
                iso("Wspięcia na palce jednonóż z hantlem", 3, 10, 15)
            ),
            unit(
                "Góra B", "Skos, wyciągi, podciąganie",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 4, 10, 12),
                aux("Podciąganie na drążku", 3, 5, 10),
                iso("Rozpiętki na bramie", 3, 12, 15),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Dół B", "Martwy ciąg, przysiad przedni, hip thrust",
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Przysiad przedni", 3, 6, 10),
                aux("Hip thrust", 3, 8, 12),
                iso("Odrzut nogi w tył na wyciągu", 3, 12, 15),
                core("Spięcia brzucha na wyciągu", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Hantle - Push / Pull - 4 dni",
        summary = "Pchanie i ciągnięcie na hantlach, nogi w obu",
        description = "Cztery treningi z hantlami i ławką: dwa pchające, dwa ciągnące, " +
            "w każdym jedno ćwiczenie na nogi. Dla domu, gdzie sztangi nie ma.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push A", "Wyciskanie leżąc i siedząc",
                aux("Wyciskanie hantli leżąc", 4, 8, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                iso("Rozpiętki na ławce", 3, 10, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Francuskie hantlami leżąc", 3, 10, 15)
            ),
            unit(
                "Pull A", "Wiosłowanie, rumuński, biceps",
                aux("Wiosłowanie hantlami w opadzie", 4, 8, 12),
                aux("Martwy ciąg rumuński z hantlami", 3, 8, 12),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Uginanie supinowane hantli", 3, 10, 15),
                iso("Szrugsy z hantlami", 3, 10, 15)
            ),
            unit(
                "Push B", "Skos, Arnoldki, goblet",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wyciskanie Arnolda", 3, 8, 12),
                aux("Przysiad goblet", 3, 10, 15),
                iso("Prostowanie ramienia w opadzie", 3, 10, 15),
                iso("Pompki diamentowe", 2, 8, 15)
            ),
            unit(
                "Pull B", "Jednorącz, bułgarskie, hip thrust",
                aux("Wiosłowanie jednorącz", 4, 8, 12),
                aux("Bułgarskie przysiady", 3, 8, 12),
                aux("Hip thrust z hantlem", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Uginanie koncentryczne", 2, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Hantle i drążek - 3 dni",
        summary = "Hantle do nóg i pchania, drążek do pleców",
        description = "Drążek w drzwiach zastępuje cały wyciąg: podciąganie w trzech " +
            "odmianach i zwis na brzuch. Do tego hantle, bez ławki - wyciskanie " +
            "idzie z podłogi.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Goblet, podciąganie, wyciskanie z podłogi",
                aux("Przysiad goblet", 3, 10, 15),
                aux("Podciąganie na drążku", 3, 3, 8),
                aux("Wyciskanie hantli z podłogi", 3, 8, 12),
                aux("Wiosłowanie jednorącz", 3, 8, 12),
                core("Unoszenie kolan w zwisie", 3, 8, 12)
            ),
            unit(
                "B", "Rumuński, pompki, podchwyt",
                aux("Martwy ciąg rumuński z hantlami", 3, 8, 12),
                aux("Pompki", 3, 8, 15),
                aux("Podciąganie podchwytem", 3, 3, 8),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Uginanie młotkowe", 2, 10, 15)
            ),
            unit(
                "C", "Wykroki, negatywy, pompki z nogami wyżej",
                aux("Wykroki w tył z hantlami", 3, 8, 12),
                aux("Podciąganie negatywne", 3, 3, 6),
                iso("Pompki z nogami na podwyższeniu", 3, 8, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                core("Unoszenie nóg w zwisie", 3, 6, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Kettlebell - 2 dni",
        summary = "Minimum z jednym kettlebellem",
        description = "Dwa treningi po cztery ćwiczenia - swing, przysiad, wyciskanie, " +
            "tureckie wstawanie. Na tygodnie, gdy na więcej nie ma czasu.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "KB A", "Swing, goblet, wyciskanie, wiosłowanie",
                aux("Swing kettlebell", 4, 10, 15),
                aux("Przysiad goblet z kettlebell", 3, 8, 12),
                aux("Wyciskanie kettlebell nad głowę", 3, 6, 10),
                aux("Wiosłowanie kettlebell", 3, 8, 12)
            ),
            unit(
                "KB B", "Jedna noga, wykroki, pompki, wstawanie",
                aux("Martwy ciąg jednonóż z kettlebell", 3, 6, 10),
                aux("Wykroki z kettlebell", 3, 8, 12),
                aux("Pompki", 3, 8, 15),
                aux("Tureckie wstawanie", 3, 3, 5)
            )
        )
    ),

    ProgramTemplate(
        name = "Gumy i drążek - 3 dni",
        summary = "Gumy, minibandy i drążek w drzwiach",
        description = "Gumy dają opór tam, gdzie masa ciała nie wystarcza, a drążek " +
            "trzyma plecy. Podciąganie z gumą asekuracyjną prowadzi do pierwszych " +
            "pełnych powtórzeń.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Podciąganie z gumą, przysiad, wyciskanie",
                aux("Podciąganie z gumą asekuracyjną", 3, 5, 10),
                iso("Przysiad z gumą", 3, 12, 20),
                iso("Wyciskanie z gumą stojąc", 3, 12, 20),
                iso("Face pull z gumą", 3, 15, 20),
                core("Unoszenie kolan w zwisie", 3, 8, 12)
            ),
            unit(
                "B", "Martwy ciąg z gumą, wiosłowanie, pompki",
                iso("Martwy ciąg z gumą", 3, 12, 20),
                iso("Wiosłowanie z gumą", 3, 12, 20),
                aux("Pompki", 3, 8, 15),
                iso("Wyciskanie gumy nad głowę", 3, 10, 15),
                iso("Uginanie ramion z gumą", 3, 12, 20)
            ),
            unit(
                "C", "Negatywy, pośladki z minibandem",
                aux("Podciąganie negatywne", 3, 3, 6),
                iso("Mostek biodrowy z minibandem", 3, 15, 20),
                iso("Spacer boczny z minibandem", 3, 12, 20),
                iso("Prostowanie ramion z gumą", 3, 12, 20),
                core("Wyciskanie Pallofa z gumą", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Bez sprzętu - 20 minut",
        summary = "Krótko, codziennie, bez niczego",
        description = "Dwa krótkie treningi na zmianę, sześć razy w tygodniu, po " +
            "dwadzieścia minut. Dla tych, którym łatwiej o codzienny nawyk niż " +
            "o godzinę trzy razy w tygodniu.",
        days = listOf(6),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "20 minut A", "Przysiad, pompki, mostek, wspinaczka",
                core("Przysiad bez obciążenia", 3, 15, 25),
                core("Pompki", 3, 8, 15),
                core("Mostek biodrowy", 3, 15, 20),
                core("Wspinaczka górska", 3, 15, 20)
            ),
            unit(
                "20 minut B", "Wykroki, pike, superman, rowerek",
                core("Wykroki bez obciążenia", 3, 10, 15),
                core("Pompki w pozycji pike", 3, 6, 12),
                core("Superman", 3, 10, 15),
                core("Rowerek", 3, 15, 20)
            )
        )
    ),

    ProgramTemplate(
        name = "Bez sprzętu - nogi i pośladki",
        summary = "Dwa treningi dołu ciała bez ciężarów",
        description = "Jedna noga zamiast ciężaru: bułgarskie, kozacki, mostek jednonóż. " +
            "Jako dodatek do treningu góry albo samodzielnie, dwa razy w tygodniu.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Nogi A", "Przysiad, bułgarskie, mostek jednonóż",
                iso("Przysiad bez obciążenia", 3, 15, 25),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                iso("Wspięcia na palce jednonóż", 3, 12, 20),
                iso("Odwodzenie nogi w leżeniu bokiem", 3, 15, 20)
            ),
            unit(
                "Nogi B", "Wyskoki, kozacki, nordyckie",
                iso("Przysiad z wyskokiem", 3, 8, 12),
                iso("Wykroki w tył bez obciążenia", 3, 10, 15),
                iso("Przysiad kozacki", 3, 6, 10),
                iso("Odrzut nogi w klęku podpartym", 3, 12, 20),
                aux("Uginanie nordyckie", 3, 3, 6)
            )
        )
    ),

    ProgramTemplate(
        name = "Kalistenika z obciążeniem",
        summary = "Podciąganie i dipy z pasem albo kamizelką",
        description = "Dla tych, którym masa ciała już nie wystarcza: obciążenie na pasie " +
            "albo w kamizelce przy ruchach podstawowych, a do tego trudne warianty " +
            "na jednej nodze i stanie na rękach.",
        days = listOf(3),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Ciągnięcie", "Podciąganie z obciążeniem, pierścienie",
                aux("Podciąganie z obciążeniem", 4, 4, 8),
                aux("Podciąganie neutralnym chwytem", 3, 6, 10),
                aux("Wiosłowanie na pierścieniach", 3, 8, 12),
                iso("Unoszenie nóg do drążka", 3, 6, 10)
            ),
            unit(
                "Pchanie", "Dipy z obciążeniem, kamizelka, stanie na rękach",
                aux("Dipy z obciążeniem", 4, 4, 8),
                aux("Pompki z kamizelką", 3, 8, 12),
                aux("Pompki w staniu na rękach przy ścianie", 3, 3, 6),
                iso("Pompki sfinks", 3, 8, 12)
            ),
            unit(
                "Nogi", "Pistolet, krewetka, nordyckie",
                aux("Przysiad pistolet", 4, 3, 6),
                aux("Przysiad krewetka", 3, 3, 6),
                aux("Uginanie nordyckie", 3, 3, 6),
                iso("Skoki na skrzynię", 3, 5, 8),
                iso("Wspięcia na palce jednonóż", 3, 15, 20)
            )
        )
    ),

    ProgramTemplate(
        name = "Paralletki i kółko - 3 dni",
        summary = "Pompki na paralletkach, brzuch z kółkiem, drążek",
        description = "Mały sprzęt, który mieści się w szafie: paralletki do trudniejszych " +
            "pompek, kółko do brzucha i drążek do pleców.",
        days = listOf(3),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Pchanie", "Paralletki, pseudo planche, pike",
                aux("Pompki na paralletkach", 4, 8, 12),
                aux("Pompki pseudo planche", 3, 5, 10),
                aux("Pompki pike z nogami na podwyższeniu", 3, 6, 10),
                iso("Pompki diamentowe", 3, 8, 15)
            ),
            unit(
                "Brzuch i nogi", "Kółko, bułgarskie, mostek",
                core("Rollout kółkiem", 4, 6, 12),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                core("Kołyski w łódce", 3, 10, 15),
                iso("Mostek biodrowy jednonóż", 3, 10, 15)
            ),
            unit(
                "Ciągnięcie", "Podciąganie, superman, Y-T-W",
                aux("Podciąganie na drążku", 4, 5, 10),
                iso("Superman", 3, 10, 15),
                iso("Unoszenie Y-T-W leżąc", 3, 8, 12),
                core("Unoszenie kolan w zwisie", 3, 8, 12)
            )
        )
    )
)
