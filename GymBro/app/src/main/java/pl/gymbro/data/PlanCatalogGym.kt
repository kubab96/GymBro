package pl.gymbro.data

import pl.gymbro.data.PlanLibrary.Level
import pl.gymbro.data.PlanLibrary.ProgramTemplate

/**
 * Gotowe plany na siłownię - patrz [PlanLibrary], tam są zasady.
 *
 * Kolejność ma znaczenie: tak wyświetla się lista bez wyszukiwania. Na górze
 * to, od czego zaczyna większość - całe ciało, PPL, góra/dół - a niżej
 * podziały dla tych, którzy już wiedzą, czego szukają.
 */
internal val GYM_PROGRAMS: List<ProgramTemplate> = listOf(
    ProgramTemplate(
        name = "FBW - 3 dni",
        summary = "Całe ciało za każdym razem, trzy różne treningi",
        description = "Trzy różne treningi całego ciała, robione na zmianę z dniem " +
            "przerwy między nimi, na przykład poniedziałek, środa, piątek. " +
            "Dobry punkt wyjścia i przy małej liczbie dni w tygodniu.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Sztanga: przysiad, wyciskanie, wiosłowanie",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                heavy("Wyciskanie sztangi leżąc", 3, 6, 10),
                heavy("Wiosłowanie sztangą", 3, 8, 12),
                aux("OHP", 3, 8, 12),
                iso("Uginanie sztangi stojąc", 2, 10, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            ),
            unit(
                "B", "Hantle i wyciąg, tył uda zamiast przysiadu",
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15),
                iso("Wspięcia na palce", 3, 10, 15)
            ),
            unit(
                "C", "Lżej dla stawów: suwnica, hantle, wyciągi",
                aux("Suwnica", 3, 10, 15),
                aux("Wyciskanie hantli leżąc", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 15),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "FBW - 2 dni",
        summary = "Całe ciało dwa razy w tygodniu - minimum, które działa",
        description = "Dwa treningi całego ciała na zmianę, z co najmniej dniem przerwy " +
            "między nimi. Dla tych, którzy mają mało czasu, i na powrót po przerwie.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Przysiad, wyciskanie leżąc, ściąganie",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                heavy("Wyciskanie sztangi leżąc", 3, 6, 10),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            ),
            unit(
                "B", "Martwy ciąg, wyciskanie nad głowę, wiosłowanie",
                heavy("Martwy ciąg", 3, 4, 6),
                heavy("OHP", 3, 6, 10),
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                aux("Wykroki z hantlami", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Maszyny i wyciągi - 3 dni",
        summary = "Bez wolnych ciężarów - najprostszy start na siłowni",
        description = "Same maszyny i wyciągi: tor ruchu prowadzi sprzęt, więc technika " +
            "nie hamuje postępu w pierwszych tygodniach. Trzy treningi na zmianę.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Maszyny A", "Suwnica, wyciskanie, ściąganie",
                aux("Suwnica", 3, 10, 15),
                aux("Wyciskanie na maszynie", 3, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 10, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Unoszenie bokiem na maszynie", 3, 12, 15),
                iso("Spięcia brzucha na maszynie", 3, 10, 15)
            ),
            unit(
                "Maszyny B", "Smith, wiosłowanie, barki",
                aux("Przysiad na maszynie Smitha", 3, 8, 12),
                aux("Wiosłowanie na maszynie", 3, 10, 12),
                aux("Wyciskanie nad głowę na maszynie", 3, 8, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie na wyciągu dolnym", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Maszyny C", "Pośladki, klatka skośnie, tył barków",
                aux("Hip thrust na maszynie", 3, 8, 12),
                aux("Wyciskanie na maszynie skos", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Butterfly", 3, 10, 15),
                iso("Odwrotne rozpiętki na maszynie", 3, 12, 15),
                iso("Wspięcia na palce siedząc", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Łagodnie dla stawów - 2 dni",
        summary = "Umiarkowane ciężary, wyższe powtórzenia, bez sztangi",
        description = "Dla wracających po kontuzji i dla starszych: maszyny, wyciągi " +
            "i hantle w neutralnym chwycie, zakres 10-15 zamiast ciężkich serii.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Stawy A", "Suwnica, wyciskanie neutralnie, ściąganie",
                aux("Suwnica", 3, 12, 15),
                aux("Wyciskanie hantli neutralnym chwytem", 3, 10, 15),
                aux("Ściąganie drążka wąskim chwytem", 3, 10, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 10, 15),
                iso("Face pull", 3, 12, 15),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "Stawy B", "Wejścia, wiosłowanie z podparciem, wyciąg",
                aux("Wejścia na skrzynię z hantlami", 3, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 10, 12),
                aux("Wyciskanie na wyciągu stojąc", 3, 10, 15),
                iso("Odwodzenie nóg na maszynie", 3, 12, 15),
                iso("Unoszenie bokiem na wyciągu", 3, 12, 15),
                core("Wyciskanie Pallofa na wyciągu", 3, 10, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Krótko - 3 dni po 45 minut",
        summary = "Cztery ćwiczenia na trening, całe ciało",
        description = "Dla tych, którzy mają trzy kwadranse z przebraniem. Każdy trening " +
            "to cztery ćwiczenia - dwa duże, dwa pomocnicze - i wyjście z sali.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Krótko A", "Przysiad, wyciskanie, ściąganie",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                aux("Wyciskanie hantli leżąc", 3, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 2, 12, 15)
            ),
            unit(
                "Krótko B", "Tył uda, barki, wiosłowanie",
                heavy("Martwy ciąg rumuński", 3, 6, 10),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 8, 12),
                iso("Uginanie młotkowe", 2, 10, 15)
            ),
            unit(
                "Krótko C", "Suwnica, skos, wiosłowanie z podparciem",
                aux("Suwnica", 3, 10, 15),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 8, 12),
                iso("Prostowanie liną na wyciągu", 2, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Siła - 3 dni (5×5)",
        summary = "Pięć serii po pięć w ruchach głównych",
        description = "Dwa treningi na zmianę: A, B, A w jednym tygodniu i B, A, B " +
            "w następnym. Mało ćwiczeń, ciężko, długie przerwy - ciężar ma rosnąć " +
            "co tydzień, dopóki się da.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Siła A", "Przysiad, wyciskanie leżąc, wiosłowanie",
                heavy("Przysiad ze sztangą", 5, 4, 6),
                heavy("Wyciskanie sztangi leżąc", 5, 4, 6),
                heavy("Wiosłowanie sztangą z podłogi", 5, 4, 6),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            ),
            unit(
                "Siła B", "Przysiad, wyciskanie nad głowę, martwy ciąg",
                heavy("Przysiad ze sztangą", 5, 4, 6),
                heavy("OHP", 5, 4, 6),
                heavy("Martwy ciąg", 2, 3, 5),
                aux("Podciąganie na drążku", 3, 5, 8)
            )
        )
    ),

    ProgramTemplate(
        name = "FBW - 4 dni",
        summary = "Całe ciało cztery razy, każdy dzień z innym akcentem",
        description = "Cztery treningi całego ciała: dwa z naciskiem na przysiad " +
            "i wyciskanie, dwa na martwy ciąg i plecy. Dzień przerwy po drugim " +
            "i po czwartym.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "A", "Przysiad i wyciskanie leżąc",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie sztangi łamanej", 3, 8, 12)
            ),
            unit(
                "B", "Martwy ciąg i podciąganie",
                heavy("Martwy ciąg", 3, 4, 6),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                aux("Podciąganie na drążku", 3, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "C", "Przysiad przedni i wyciskanie nad głowę",
                heavy("Przysiad przedni", 3, 6, 10),
                heavy("OHP", 4, 5, 8),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            ),
            unit(
                "D", "Tył uda, skos, plecy z podparciem",
                heavy("Martwy ciąg rumuński", 3, 6, 10),
                aux("Wyciskanie sztangi skos", 3, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 8, 12),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "PPL",
        summary = "Pchanie, ciągnięcie, nogi",
        description = "Push to klatka, barki i triceps, Pull to plecy i biceps, Legs " +
            "to nogi. Raz w tygodniu każdy przy trzech dniach, dwa razy przy " +
            "sześciu - wtedy po trzecim treningu dzień przerwy.",
        days = listOf(3, 6),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push", "Klatka, barki, triceps",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15),
                iso("Francuskie hantlem", 3, 10, 15)
            ),
            unit(
                "Pull", "Plecy, tył barków, biceps",
                heavy("Podciąganie na drążku", 4, 6, 10),
                heavy("Wiosłowanie sztangą", 4, 8, 12),
                aux("Ściąganie drążka wyciągu górnego", 3, 10, 12),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Legs", "Przód i tył uda, łydki, brzuch",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Suwnica", 3, 10, 15),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "PPL - 6 dni",
        summary = "Pchanie, ciągnięcie, nogi - dwie różne wersje każdego",
        description = "Sześć treningów: pierwsza trójka ciężej, ze sztangą, druga " +
            "lżej, na hantlach i wyciągach. Jeden dzień wolny w tygodniu. Dużo " +
            "objętości - dla tych, którzy dobrze się regenerują.",
        days = listOf(6),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Push A", "Ciężko: wyciskanie leżąc i nad głowę",
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                aux("OHP", 3, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15),
                iso("Francuskie hantlem", 3, 10, 15)
            ),
            unit(
                "Pull A", "Ciężko: martwy ciąg i podciąganie",
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Podciąganie na drążku", 4, 6, 10),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Legs A", "Ciężko: przysiad i tył uda",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                aux("Bułgarskie przysiady", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            ),
            unit(
                "Push B", "Objętość: hantle, poręcze, wyciąg",
                aux("Wyciskanie hantli leżąc", 4, 8, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Rozpiętki na bramie z góry", 3, 12, 15),
                iso("Unoszenie bokiem na wyciągu", 3, 12, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Pull B", "Objętość: wyciągi i wiosłowania",
                aux("Ściąganie drążka wyciągu górnego", 4, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 8, 12),
                aux("Wiosłowanie jednorącz na wyciągu", 3, 10, 12),
                iso("Odwrotne rozpiętki na maszynie", 3, 12, 15),
                iso("Uginanie hantli na ławce skośnej", 3, 10, 15),
                iso("Uginanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Legs B", "Objętość: maszyny i pośladki",
                aux("Hack przysiad", 4, 8, 12),
                aux("Hip thrust", 3, 8, 12),
                aux("Suwnica jednonóż", 3, 10, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 10, 15),
                iso("Wspięcia na palce siedząc", 4, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Push / Pull - 4 dni",
        summary = "Pchanie z przodem uda, ciągnięcie z tyłem uda",
        description = "Nogi nie mają osobnego dnia: przysiad idzie z pchaniem, martwy " +
            "ciąg z ciągnięciem. Cztery treningi, każdy ruch dwa razy w tygodniu.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push A", "Przysiad i wyciskanie leżąc",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15)
            ),
            unit(
                "Pull A", "Martwy ciąg i podciąganie",
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Podciąganie na drążku", 4, 6, 10),
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12)
            ),
            unit(
                "Push B", "Suwnica i wyciskanie nad głowę",
                aux("Suwnica", 4, 10, 12),
                heavy("OHP", 4, 5, 8),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Wspięcia na palce", 4, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Pull B", "Tył uda, pośladki, wyciągi",
                heavy("Martwy ciąg rumuński", 3, 6, 10),
                aux("Wiosłowanie na wyciągu siedząc", 4, 8, 12),
                aux("Ściąganie drążka podchwytem", 3, 8, 12),
                aux("Hip thrust", 3, 8, 12),
                iso("Odwrotne rozpiętki na bramie", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Góra / Dół",
        summary = "Góra i dół ciała na zmianę",
        description = "Dwa treningi góry i dwa dołu, na przykład poniedziałek i wtorek, " +
            "przerwa, czwartek i piątek. Pierwsza para idzie ciężej i niżej " +
            "w powtórzeniach, druga lżej i wyżej.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra A", "Ciężej: sztanga, niski zakres",
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                heavy("Wiosłowanie sztangą", 4, 6, 10),
                aux("OHP", 3, 6, 10),
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                aux("Wyciskanie wąsko", 3, 8, 12)
            ),
            unit(
                "Dół A", "Ciężej: przysiad i tył uda",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki z hantlami", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15)
            ),
            unit(
                "Góra B", "Lżej: hantle i wyciągi",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 4, 10, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15)
            ),
            unit(
                "Dół B", "Martwy ciąg, maszyny, brzuch",
                heavy("Martwy ciąg", 3, 4, 6),
                aux("Suwnica", 3, 10, 15),
                aux("Hip thrust", 3, 8, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Siła i masa - 4 dni",
        summary = "Góra i dół: dwa dni ciężko, dwa dni na objętość",
        description = "Pierwsza para treningów buduje siłę w niskim zakresie, druga " +
            "masę w wyższym. Kolejność: góra siła, dół siła, przerwa, góra " +
            "objętość, dół objętość.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra siła", "Wyciskanie i wiosłowanie w niskim zakresie",
                heavy("Wyciskanie sztangi leżąc", 4, 3, 5),
                heavy("Wiosłowanie sztangą", 4, 5, 8),
                aux("Wyciskanie hantli nad głowę", 3, 6, 10),
                aux("Podciąganie na drążku", 3, 5, 8),
                iso("Uginanie sztangi stojąc", 2, 6, 10),
                iso("Francuskie sztangą", 2, 6, 10)
            ),
            unit(
                "Dół siła", "Przysiad i martwy ciąg ciężko",
                heavy("Przysiad ze sztangą", 4, 3, 5),
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Suwnica", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 6, 10),
                iso("Wspięcia na palce", 4, 6, 10)
            ),
            unit(
                "Góra objętość", "Hantle, wyciągi, ramiona",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 4, 8, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                iso("Rozpiętki na bramie", 3, 12, 15),
                aux("Ściąganie drążka wąskim chwytem", 3, 10, 12),
                iso("Uginanie hantli naprzemienne", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Dół objętość", "Przysiad przedni, wykroki, maszyny",
                aux("Przysiad przedni", 3, 8, 12),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki chodzone z hantlami", 3, 10, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 12, 15),
                iso("Wspięcia na palce siedząc", 4, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Split - 4 dni",
        summary = "Każda partia raz w tygodniu, za to dokładnie",
        description = "Cztery treningi, każdy na inne partie. Każdą partię ćwiczysz raz " +
            "w tygodniu. Między plecami a nogami zostaw dzień przerwy - " +
            "oba treningi obciążają dół pleców.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Klatka i triceps", "Wyciskania, rozpiętki, prostowania",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                iso("Butterfly", 3, 10, 15),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15),
                iso("Francuskie sztangą", 3, 8, 12)
            ),
            unit(
                "Plecy i biceps", "Podciąganie, wiosłowania, uginania",
                heavy("Podciąganie na drążku", 4, 6, 10),
                heavy("Wiosłowanie sztangą", 4, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Ściąganie prostymi rękami na wyciągu", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Uginanie hantli na ławce skośnej", 3, 10, 15)
            ),
            unit(
                "Nogi", "Przysiad, tył uda, maszyny, łydki",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Suwnica", 3, 10, 15),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15)
            ),
            unit(
                "Barki i brzuch", "Wyciskanie nad głowę, unoszenia, brzuch",
                heavy("OHP", 4, 6, 10),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Odwrotne rozpiętki na maszynie", 3, 12, 15),
                iso("Szrugsy z hantlami", 3, 10, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Split - 5 dni",
        summary = "Klatka, plecy, nogi, barki, ramiona - każdy dzień osobno",
        description = "Klasyczny podział kulturystyczny: jedna partia na trening, dużo " +
            "serii, każda partia raz w tygodniu. Dwa dni wolne, najlepiej po " +
            "nogach i po ramionach.",
        days = listOf(5),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Klatka", "Sztanga, hantle, maszyna, wyciąg",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wyciskanie na maszynie", 3, 8, 12),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Rozpiętki na bramie z dołu", 3, 12, 15),
                iso("Butterfly", 3, 12, 15)
            ),
            unit(
                "Plecy", "Martwy ciąg, podciąganie, wiosłowania",
                heavy("Martwy ciąg", 3, 4, 6),
                aux("Podciąganie szerokim chwytem", 4, 6, 10),
                aux("Wiosłowanie T-sztangą", 4, 8, 12),
                aux("Wiosłowanie jednorącz", 3, 8, 12),
                iso("Ściąganie prostymi rękami na wyciągu", 3, 12, 15),
                iso("Szrugsy ze sztangą", 3, 10, 15)
            ),
            unit(
                "Nogi", "Przysiad, suwnica, tył uda, łydki",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                aux("Suwnica", 4, 10, 12),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 5, 10, 15)
            ),
            unit(
                "Barki", "Wyciskanie nad głowę i wszystkie unoszenia",
                heavy("OHP", 4, 6, 10),
                aux("Wyciskanie Arnolda", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Unoszenie bokiem na wyciągu", 3, 12, 15),
                iso("Odwrotne rozpiętki na bramie", 3, 12, 15),
                iso("Face pull", 3, 12, 15)
            ),
            unit(
                "Ramiona", "Biceps, triceps, przedramiona",
                aux("Wyciskanie wąsko", 4, 6, 10),
                iso("Uginanie sztangi łamanej", 4, 8, 12),
                iso("Francuskie sztangą łamaną leżąc", 3, 8, 12),
                iso("Uginanie hantli na ławce skośnej", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Uginanie nadgarstków ze sztangą", 2, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Split antagonistyczny",
        summary = "Klatka z plecami, barki z ramionami, nogi z brzuchem",
        description = "Trzy treningi, w których mięśnie przeciwne idą razem: klatka " +
            "z plecami, biceps z tricepsem. Raz w tygodniu przy trzech dniach, " +
            "dwa razy przy sześciu.",
        days = listOf(3, 6),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Klatka i plecy", "Wyciskanie i wiosłowanie na zmianę",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                heavy("Wiosłowanie sztangą", 4, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Podciąganie na drążku", 3, 6, 10),
                iso("Rozpiętki na bramie", 3, 12, 15),
                iso("Ściąganie prostymi rękami na wyciągu", 3, 12, 15)
            ),
            unit(
                "Barki i ramiona", "Nad głowę, unoszenia, biceps z tricepsem",
                heavy("OHP", 4, 6, 10),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Odwrotne rozpiętki na maszynie", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Francuskie sztangą", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15)
            ),
            unit(
                "Nogi i brzuch", "Przysiad, tył uda, wykroki, brzuch",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki chodzone z hantlami", 3, 10, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15),
                iso("Unoszenie nóg w zwisie", 3, 8, 12),
                core("Drwal na wyciągu", 3, 10, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Góra, dół i PPL - 5 dni",
        summary = "Dwa dni na siłę, trzy na objętość",
        description = "Najpierw góra i dół ciężko, ze sztangą, potem przerwa i trzy " +
            "lżejsze treningi pchanie, ciągnięcie, nogi. Każda partia dwa razy " +
            "w tygodniu, raz ciężko, raz na objętość.",
        days = listOf(5),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Góra", "Ciężko: wyciskanie, wiosłowanie, nad głowę",
                heavy("Wyciskanie sztangi leżąc", 4, 4, 6),
                heavy("Wiosłowanie sztangą", 4, 5, 8),
                aux("OHP", 3, 6, 10),
                aux("Podciąganie na drążku", 3, 6, 10),
                iso("Uginanie sztangi stojąc", 2, 8, 12),
                aux("Wyciskanie wąsko", 2, 6, 10)
            ),
            unit(
                "Dół", "Ciężko: przysiad i martwy ciąg",
                heavy("Przysiad ze sztangą", 4, 4, 6),
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Wykroki ze sztangą", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 8, 12),
                iso("Wspięcia na palce", 4, 10, 15)
            ),
            unit(
                "Push", "Objętość: klatka, barki, triceps",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                iso("Rozpiętki na bramie z dołu", 3, 12, 15),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15),
                iso("Francuskie hantlami leżąc", 3, 10, 15)
            ),
            unit(
                "Pull", "Objętość: plecy, tył barków, biceps",
                aux("Ściąganie drążka wyciągu górnego", 4, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 3, 8, 12),
                aux("Wiosłowanie na wyciągu szerokim chwytem", 3, 10, 12),
                iso("Face pull", 3, 12, 15),
                iso("Uginanie hantli naprzemienne", 3, 10, 15),
                iso("Uginanie liną na wyciągu", 3, 10, 15)
            ),
            unit(
                "Legs", "Objętość: maszyny i jedna noga",
                aux("Hack przysiad", 4, 8, 12),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                aux("Suwnica jednonóż", 3, 10, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Uginanie nóg siedząc na maszynie", 3, 10, 15),
                iso("Wspięcia na palce siedząc", 4, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Trzy boje - 4 dni",
        summary = "Przysiad, wyciskanie leżąc i martwy ciąg jak najciężej",
        description = "Pod trójbój siłowy: każdy bój ma swój ciężki dzień, a czwarty " +
            "trening dokłada objętości wyciskaniu, które rośnie najwolniej. " +
            "Niskie zakresy, długie przerwy.",
        days = listOf(4),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Przysiad ciężko", "Przysiad i przysiad z pauzą",
                heavy("Przysiad ze sztangą", 5, 3, 5),
                heavy("Przysiad ze sztangą z pauzą", 3, 3, 5),
                aux("Martwy ciąg rumuński", 3, 6, 10),
                iso("Uginanie nóg na maszynie", 3, 8, 12),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            ),
            unit(
                "Wyciskanie ciężko", "Wyciskanie leżąc i z pauzą",
                heavy("Wyciskanie sztangi leżąc", 5, 3, 5),
                heavy("Wyciskanie sztangi z pauzą", 3, 3, 5),
                aux("Wiosłowanie sztangą", 4, 6, 10),
                aux("Wyciskanie wąsko", 3, 6, 10),
                iso("Face pull", 3, 12, 15)
            ),
            unit(
                "Martwy ciąg ciężko", "Martwy ciąg z podłogi i z podwyższenia",
                heavy("Martwy ciąg", 5, 3, 5),
                heavy("Martwy ciąg z podwyższenia", 3, 3, 5),
                aux("Przysiad przedni", 3, 6, 10),
                aux("Podciąganie na drążku", 3, 6, 10),
                iso("Wyprost bioder na ławce rzymskiej", 3, 10, 15)
            ),
            unit(
                "Wyciskanie objętość", "Lżejsze wyciskanie i barki",
                aux("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("OHP", 3, 6, 10),
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                iso("Prostowanie na wyciągu górnym", 3, 10, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie sztangi łamanej", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Pośladki i nogi - 3 dni",
        summary = "Dwa treningi na dół ciała, jeden na górę",
        description = "Akcent na pośladki i tył uda: hip thrust, martwy ciąg rumuński, " +
            "jedna noga, odwodzenia. Trzeci trening trzyma górę ciała, żeby " +
            "nie zostawała w tyle.",
        days = listOf(3),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Pośladki A", "Hip thrust, rumuński, bułgarskie",
                heavy("Hip thrust", 4, 6, 10),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                aux("Bułgarskie przysiady", 3, 8, 12),
                iso("Odwodzenie nóg na maszynie", 3, 12, 15),
                iso("Odrzut nogi w tył na wyciągu", 3, 12, 15)
            ),
            unit(
                "Pośladki B", "Przysiad, maszyna, wejścia na skrzynię",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                aux("Hip thrust na maszynie", 3, 8, 12),
                aux("Wejścia na skrzynię z hantlami", 3, 8, 12),
                iso("Pull-through na wyciągu", 3, 12, 15),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Odwodzenie nogi na wyciągu", 3, 12, 15)
            ),
            unit(
                "Góra ciała", "Plecy, klatka, barki",
                aux("Ściąganie drążka wyciągu górnego", 3, 8, 12),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Wiosłowanie na wyciągu siedząc", 3, 10, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Face pull", 3, 12, 15),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Góra / Dół z akcentem na ramiona",
        summary = "Barki i ramiona dostają po jednym całym treningu",
        description = "Układ góra/dół, w którym jedna góra jest pod barki, a druga pod " +
            "biceps i triceps. Na kilka tygodni, gdy ramiona zostają w tyle - " +
            "potem wróć do zwykłego planu.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra - barki", "Wyciskanie nad głowę i unoszenia",
                heavy("OHP", 4, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Podciąganie na drążku", 3, 6, 10),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Unoszenie bokiem na wyciągu", 3, 12, 15),
                iso("Odwrotne rozpiętki na bramie", 3, 12, 15)
            ),
            unit(
                "Dół A", "Przysiad i tył uda",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                aux("Martwy ciąg rumuński", 3, 8, 12),
                iso("Uginanie nóg na maszynie", 3, 10, 15),
                iso("Wspięcia na palce", 4, 10, 15)
            ),
            unit(
                "Góra - ramiona", "Wyciskanie, wiosłowanie, biceps i triceps",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                iso("Uginanie sztangi łamanej", 3, 8, 12),
                iso("Francuskie sztangą łamaną leżąc", 3, 8, 12),
                iso("Uginanie liną na wyciągu", 3, 10, 15),
                iso("Prostowanie liną na wyciągu", 3, 10, 15),
                iso("Uginanie Zottmana", 2, 10, 15)
            ),
            unit(
                "Dół B", "Martwy ciąg, suwnica, wykroki",
                heavy("Martwy ciąg", 3, 4, 6),
                aux("Suwnica", 3, 10, 15),
                aux("Wykroki chodzone z hantlami", 3, 10, 12),
                iso("Wyprosty nóg na maszynie", 3, 12, 15),
                iso("Spięcia brzucha na wyciągu", 3, 10, 15)
            )
        )
    )
)
