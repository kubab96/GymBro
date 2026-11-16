package pl.gymbro.data

import pl.gymbro.data.PlanLibrary.Level
import pl.gymbro.data.PlanLibrary.ProgramTemplate

/**
 * Gotowe plany do domu i na samą masę ciała - patrz [PlanLibrary].
 *
 * Podział na dwa pliki jest tylko porządkiem w kodzie: tu plany bez wyciągów
 * i maszyn, w `PlanCatalogGym.kt` te z nimi. Aplikacja nie zgaduje z tego,
 * gdzie kto trenuje - dobiera plany do sprzętu z Profilu, patrz
 * [PlanLibrary.equipmentOf].
 *
 * Masa ciała ma tu tyle samo planów co hantle i kettlebell razem, i to
 * celowo: trening bez sprzętu to nie zapasowa wersja siłowni, tylko osobna
 * dziedzina z własną drabiną trudności. Plany kalisteniczne idą od pompek
 * z rękami na podwyższeniu i podciągania negatywnego do wyjścia siłowego
 * i pistoletu - każdy szczebel to inne ćwiczenie z biblioteki.
 *
 * Plany „bez sprzętu" naprawdę nie wymagają niczego poza podłogą, krzesłem
 * i ścianą. Kalistenika zakłada drążek i poręcze - mówi o tym opis.
 */
internal val HOME_PROGRAMS: List<ProgramTemplate> = listOf(
    // ---- sztanga i hantle ----
    //
    // Domowa siłownia: stojaki, sztanga z talerzami, hantle, ławka i drążek.
    // Plany na siłownię prawie zawsze dokładają coś na wyciągu albo maszynie,
    // więc bez tych czterech dom ze sztangą nie miał w bazie nic swojego.
    ProgramTemplate(
        name = "Sztanga i hantle - FBW 3 dni",
        summary = "Domowa siłownia: całe ciało, trzy różne treningi",
        description = "Sztanga na stojakach, hantle, ławka - bez wyciągów i maszyn. " +
            "Trzy treningi całego ciała z dniem przerwy między nimi. Drążek " +
            "do podciągania się przyda, ale da się go zastąpić wiosłowaniem.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Przysiad, wyciskanie leżąc, wiosłowanie hantlami",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                heavy("Wyciskanie sztangi leżąc", 3, 6, 10),
                aux("Wiosłowanie hantlami w opadzie", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 2, 10, 15),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "B", "Rumuński, wyciskanie nad głowę, podciąganie",
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                heavy("OHP", 3, 6, 10),
                aux("Podciąganie na drążku", 3, 5, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                iso("Francuskie hantlem", 3, 10, 15),
                iso("Wspięcia na palce jednonóż z hantlem", 3, 10, 15)
            ),
            unit(
                "C", "Przysiad przedni, wiosłowanie sztangą, wykroki",
                heavy("Przysiad przedni", 3, 6, 10),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                aux("Wyciskanie hantli leżąc", 3, 8, 12),
                aux("Wykroki z hantlami", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Unoszenie hantli w opadzie", 3, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Sztanga i hantle - 2 dni",
        summary = "Domowa siłownia, dwa treningi w tygodniu",
        description = "Dwa treningi całego ciała na sztangę i hantle, z co najmniej " +
            "dniem przerwy między nimi. Bez wyciągów, maszyn i drążka.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "A", "Przysiad, wyciskanie leżąc, wiosłowanie jednorącz",
                heavy("Przysiad ze sztangą", 3, 6, 10),
                heavy("Wyciskanie sztangi leżąc", 3, 6, 10),
                aux("Wiosłowanie jednorącz", 3, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Uginanie supinowane hantli", 2, 10, 15),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "B", "Martwy ciąg, wyciskanie nad głowę, bułgarskie",
                heavy("Martwy ciąg", 3, 4, 6),
                heavy("OHP", 3, 6, 10),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                aux("Bułgarskie przysiady", 3, 8, 12),
                iso("Francuskie hantlami leżąc", 3, 10, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Sztanga i hantle - Góra / Dół",
        summary = "Domowa siłownia, góra i dół na zmianę",
        description = "Cztery treningi bez wyciągów i maszyn: góra i dół ciężej ze " +
            "sztangą, potem lżej z hantlami. Drążek i poręcze do podciągania " +
            "i dipów.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra A", "Ciężej: wyciskanie i wiosłowanie sztangą",
                heavy("Wyciskanie sztangi leżąc", 4, 5, 8),
                heavy("Wiosłowanie sztangą", 4, 6, 10),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                aux("Podciąganie na drążku", 3, 5, 10),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Francuskie sztangą", 3, 8, 12)
            ),
            unit(
                "Dół A", "Ciężej: przysiad i rumuński",
                heavy("Przysiad ze sztangą", 4, 5, 8),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki z hantlami", 3, 8, 12),
                iso("Wspięcia na palce jednonóż z hantlem", 3, 10, 15),
                core("Kołyski w łódce", 3, 10, 15)
            ),
            unit(
                "Góra B", "Lżej: hantle, dipy, wyciskanie nad głowę",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wiosłowanie hantlami na ławce skośnej", 4, 8, 12),
                heavy("OHP", 3, 6, 10),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Dół B", "Martwy ciąg, przysiad przedni, hip thrust",
                heavy("Martwy ciąg", 3, 4, 6),
                aux("Przysiad przedni", 3, 6, 10),
                aux("Hip thrust", 3, 8, 12),
                aux("Bułgarskie przysiady", 3, 8, 12),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Sztanga i hantle - PPL",
        summary = "Domowa siłownia: pchanie, ciągnięcie, nogi",
        description = "PPL bez wyciągów i maszyn - tył barków i biceps na hantlach, " +
            "podciąganie na drążku. Raz w tygodniu każdy trening przy trzech " +
            "dniach, dwa razy przy sześciu.",
        days = listOf(3, 6),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push", "Wyciskanie leżąc i nad głowę, dipy",
                heavy("Wyciskanie sztangi leżąc", 4, 6, 10),
                heavy("OHP", 3, 6, 10),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                aux("Dipy na poręczach", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Francuskie sztangą łamaną leżąc", 3, 8, 12)
            ),
            unit(
                "Pull", "Martwy ciąg, podciąganie, wiosłowanie",
                heavy("Martwy ciąg", 3, 3, 5),
                aux("Podciąganie na drążku", 4, 5, 10),
                aux("Wiosłowanie sztangą", 3, 8, 12),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Uginanie sztangi stojąc", 3, 8, 12),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Nogi", "Przysiad, rumuński, wykroki, hip thrust",
                heavy("Przysiad ze sztangą", 4, 6, 10),
                heavy("Martwy ciąg rumuński", 3, 8, 12),
                aux("Wykroki chodzone z hantlami", 3, 10, 12),
                aux("Hip thrust", 3, 8, 12),
                iso("Wspięcia na palce stojąc ze sztangą", 4, 10, 15),
                iso("Unoszenie nóg w zwisie", 3, 8, 12)
            )
        )
    ),

    // ---- hantle ----
    ProgramTemplate(
        name = "Hantle w domu",
        summary = "Same hantle i masa ciała, dwa treningi na zmianę",
        description = "Dwa treningi całego ciała robione na zmianę: A, B, A w jednym " +
            "tygodniu i B, A, B w następnym. Wystarczą hantle i ławka albo " +
            "kawałek podłogi - ani sztangi, ani maszyn.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Dom A", "Przysiad, wyciskanie leżąc, wiosłowanie",
                aux("Przysiad goblet", 4, 10, 15),
                aux("Wyciskanie hantli leżąc", 4, 8, 12),
                aux("Wiosłowanie jednorącz", 4, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Uginanie supinowane hantli", 3, 10, 15),
                iso("Francuskie hantlem", 3, 10, 15)
            ),
            unit(
                "Dom B", "Jedna noga, tył uda, barki",
                aux("Bułgarskie przysiady", 3, 8, 12),
                aux("Martwy ciąg rumuński z hantlami", 3, 10, 15),
                aux("Pompki", 3, 8, 15),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Hantle - Góra / Dół",
        summary = "Cztery treningi z hantlami, góra i dół na zmianę",
        description = "Ten sam układ co góra/dół na siłowni, tylko z hantlami: " +
            "poniedziałek i wtorek, przerwa, czwartek i piątek. Przyda się " +
            "ławka - choćby składana.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra A", "Wyciskanie leżąc, wiosłowanie, nad głowę",
                aux("Wyciskanie hantli leżąc", 4, 8, 12),
                aux("Wiosłowanie hantlami w opadzie", 4, 8, 12),
                aux("Wyciskanie hantli nad głowę", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 3, 12, 15),
                iso("Uginanie supinowane hantli", 3, 10, 15),
                iso("Francuskie hantlami leżąc", 3, 10, 15)
            ),
            unit(
                "Dół A", "Goblet, rumuński, wykroki",
                aux("Przysiad goblet", 4, 8, 12),
                aux("Martwy ciąg rumuński z hantlami", 4, 8, 12),
                aux("Wykroki w tył z hantlami", 3, 8, 12),
                iso("Wspięcia na palce jednonóż z hantlem", 3, 10, 15),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "Góra B", "Skos, jednorącz, Arnoldki",
                aux("Wyciskanie hantli skos", 4, 8, 12),
                aux("Wiosłowanie jednorącz", 4, 8, 12),
                aux("Wyciskanie Arnolda", 3, 8, 12),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Uginanie młotkowe", 3, 10, 15),
                iso("Prostowanie ramienia w opadzie", 3, 10, 15)
            ),
            unit(
                "Dół B", "Bułgarskie, hip thrust, wejścia",
                aux("Bułgarskie przysiady", 4, 8, 12),
                aux("Hip thrust z hantlem", 3, 8, 12),
                aux("Wejścia na skrzynię z hantlami", 3, 8, 12),
                iso("Martwy ciąg jednonóż z hantlem", 3, 8, 12),
                core("Russian twist", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Hantle - PPL",
        summary = "Pchanie, ciągnięcie, nogi - z samymi hantlami",
        description = "Podział PPL bez sztangi i maszyn. Raz w tygodniu każdy trening " +
            "przy trzech dniach, dwa razy przy sześciu.",
        days = listOf(3, 6),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push", "Klatka, barki, triceps",
                aux("Wyciskanie hantli leżąc", 4, 8, 12),
                aux("Wyciskanie hantli siedząc", 3, 8, 12),
                aux("Wyciskanie hantli skos", 3, 8, 12),
                iso("Unoszenie hantli bokiem", 4, 12, 15),
                iso("Francuskie hantlem", 3, 10, 15),
                iso("Pompki diamentowe", 2, 8, 15)
            ),
            unit(
                "Pull", "Plecy, tył barków, biceps",
                aux("Wiosłowanie hantlami w opadzie", 4, 8, 12),
                aux("Wiosłowanie jednorącz", 3, 8, 12),
                iso("Unoszenie hantli w opadzie", 3, 12, 15),
                iso("Szrugsy z hantlami", 3, 10, 15),
                iso("Uginanie supinowane hantli", 3, 10, 15),
                iso("Uginanie młotkowe", 3, 10, 15)
            ),
            unit(
                "Nogi", "Przysiad, rumuński, wykroki, łydki",
                aux("Przysiad z hantlami", 4, 8, 12),
                aux("Martwy ciąg rumuński z hantlami", 4, 8, 12),
                aux("Wykroki chodzone z hantlami", 3, 10, 12),
                iso("Przysiad sumo z hantlem", 3, 10, 15),
                iso("Wspięcia na palce jednonóż z hantlem", 3, 10, 15)
            )
        )
    ),

    // ---- kettlebell ----
    ProgramTemplate(
        name = "Kettlebell - całe ciało",
        summary = "Jeden albo dwa kettlebelle, dwa treningi na zmianę",
        description = "Swing, goblet, wyciskanie i wiosłowanie - podstawa, z której " +
            "wyrasta reszta pracy z kettlebellem. Wystarczy jeden odważnik, " +
            "lepiej dwa różne: lżejszy do barków, cięższy do swingu.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Kettlebell A", "Swing, goblet, wyciskanie, wiosłowanie",
                aux("Swing kettlebell", 4, 10, 15),
                aux("Przysiad goblet z kettlebell", 3, 8, 12),
                aux("Wyciskanie kettlebell nad głowę", 3, 6, 10),
                aux("Wiosłowanie kettlebell", 3, 8, 12),
                core("Martwy robak", 3, 8, 12)
            ),
            unit(
                "Kettlebell B", "Martwy ciąg, wykroki, wyciskanie leżąc",
                aux("Martwy ciąg z kettlebell", 4, 8, 12),
                aux("Wykroki z kettlebell", 3, 8, 12),
                aux("Wyciskanie kettlebell leżąc", 3, 8, 12),
                aux("Swing jednorącz kettlebell", 3, 8, 12),
                iso("Halo z kettlebell", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Kettlebell - siła i kondycja",
        summary = "Zarzuty, rwania, wstawanie - kettlebell na poważnie",
        description = "Trzy treningi z ruchami balistycznymi: swing, zarzut, rwanie. " +
            "Wymaga opanowanego swingu - zacznij od planu na całe ciało, " +
            "jeśli rwanie to jeszcze nowość.",
        days = listOf(3, 4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Swing i wyciskanie", "Dużo swingów, zarzut z wyciśnięciem",
                aux("Swing kettlebell", 5, 10, 15),
                aux("Zarzut i wyciśnięcie kettlebell", 4, 5, 8),
                iso("Pompki", 3, 10, 15),
                core("Russian twist z kettlebell", 3, 10, 15)
            ),
            unit(
                "Przysiad i wiosłowanie", "Dwa kettlebelle z przodu, wiosłowania",
                aux("Przysiad przedni z dwoma kettlebell", 4, 6, 10),
                aux("Wiosłowanie naprzemienne kettlebell", 4, 8, 12),
                aux("Martwy ciąg jednonóż z kettlebell", 3, 6, 10),
                iso("Uginanie ramion z kettlebell", 3, 10, 15),
                iso("Prostowanie ramion nad głową z kettlebell", 3, 10, 15)
            ),
            unit(
                "Rwanie i wstawanie", "Rwanie, tureckie wstawanie, thruster",
                aux("Rwanie kettlebell", 4, 5, 8),
                aux("Tureckie wstawanie", 3, 3, 5),
                aux("Thruster z kettlebell", 3, 8, 12),
                aux("Swing jednorącz kettlebell", 3, 10, 15),
                iso("Wiatrak z kettlebell", 3, 5, 8)
            )
        )
    ),

    // ---- gumy ----
    ProgramTemplate(
        name = "Gumy oporowe - całe ciało",
        summary = "Komplet gum i drzwi - nic więcej",
        description = "Trening z gumami: długie i mini. Opór dobierasz kolorem gumy " +
            "i odległością od zaczepu - aplikacja nie zapisuje go w kilogramach, " +
            "więc progresja to więcej powtórzeń i mocniejsza guma.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Gumy A", "Przysiad, wiosłowanie, wyciskanie",
                iso("Przysiad z gumą", 3, 12, 20),
                iso("Wiosłowanie z gumą", 3, 12, 20),
                iso("Wyciskanie z gumą stojąc", 3, 12, 20),
                iso("Rozciąganie gumy w poziomie", 3, 15, 20),
                iso("Uginanie ramion z gumą", 3, 12, 20),
                core("Wyciskanie Pallofa z gumą", 3, 10, 15)
            ),
            unit(
                "Gumy B", "Martwy ciąg, ściąganie, pośladki",
                iso("Martwy ciąg z gumą", 3, 12, 20),
                iso("Ściąganie gumy z góry", 3, 12, 20),
                iso("Wyciskanie gumy nad głowę", 3, 10, 15),
                iso("Mostek biodrowy z minibandem", 3, 15, 20),
                iso("Prostowanie ramion z gumą", 3, 12, 20),
                iso("Spacer boczny z minibandem", 3, 12, 20)
            )
        )
    ),

    ProgramTemplate(
        name = "Pośladki w domu",
        summary = "Miniband i masa ciała, trzy razy w tygodniu",
        description = "Mostki, wykroki i odwodzenia z gumą na udach. Dwa treningi " +
            "na zmianę. Gdy powtórzeń robi się za dużo, weź mocniejszy miniband " +
            "albo przejdź na wersję jednonóż.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Mostki i wykroki", "Mostek z gumą, hip thrust jednonóż, wykroki",
                iso("Mostek biodrowy z minibandem", 4, 15, 20),
                iso("Hip thrust jednonóż na ławce", 3, 10, 15),
                iso("Wykroki w tył bez obciążenia", 3, 10, 15),
                iso("Odwodzenie nóg z minibandem", 3, 15, 20),
                iso("Odrzut nogi w klęku podpartym", 3, 12, 20)
            ),
            unit(
                "Odwodzenia i przysiady", "Przysiad z gumą, bułgarskie, spacer boczny",
                iso("Przysiad z gumą", 4, 12, 20),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                iso("Spacer boczny z minibandem", 3, 12, 20),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                iso("Odwodzenie nogi w leżeniu bokiem", 3, 15, 20),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    // ---- bez sprzętu ----
    ProgramTemplate(
        name = "Bez sprzętu - start",
        summary = "Pierwsze tygodnie, podłoga i krzesło",
        description = "Dla zaczynających od zera: przysiady, łatwiejsze pompki, mostki " +
            "i plecy na podłodze. Bez drążka - plecy pracują w leżeniu przodem. " +
            "Dwa treningi na zmianę.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Start A", "Przysiad, pompki przy podwyższeniu, mostek",
                iso("Przysiad bez obciążenia", 3, 12, 20),
                iso("Pompki z rękami na podwyższeniu", 3, 8, 15),
                iso("Mostek biodrowy", 3, 12, 20),
                iso("Superman", 3, 10, 15),
                core("Martwy robak", 3, 8, 12),
                core("Unoszenie ręki i nogi w klęku podpartym", 3, 8, 12)
            ),
            unit(
                "Start B", "Wykroki, pompki z kolan, wejścia",
                iso("Wykroki w tył bez obciążenia", 3, 8, 12),
                iso("Pompki na kolanach", 3, 8, 15),
                iso("Wejścia na krzesło", 3, 8, 12),
                iso("Unoszenie Y-T-W leżąc", 3, 8, 12),
                iso("Wspięcia na palce bez obciążenia", 3, 15, 20),
                core("Spięcia odwrotne", 3, 8, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Bez sprzętu - całe ciało",
        summary = "Trzy różne treningi bez niczego, mocniej niż na start",
        description = "Trzy treningi na tydzień: jedna noga, trudniejsze pompki, pompki " +
            "w pozycji pike na barki. Krzesło i ściana to cały sprzęt.",
        days = listOf(3),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "A", "Bułgarskie, pompki, pike",
                iso("Bułgarskie przysiady bez obciążenia", 4, 8, 12),
                iso("Pompki", 4, 10, 20),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                iso("Pompki w pozycji pike", 3, 6, 12),
                iso("Superman", 3, 12, 15),
                core("Kołyski w łódce", 3, 10, 15)
            ),
            unit(
                "B", "Wyskoki, pompki z nogami wyżej, kozacki",
                iso("Przysiad z wyskokiem", 3, 8, 12),
                iso("Pompki z nogami na podwyższeniu", 4, 8, 15),
                iso("Przysiad kozacki", 3, 6, 10),
                iso("Dipy na ławce", 3, 10, 15),
                iso("Unoszenie Y-T-W leżąc", 3, 10, 12),
                core("Scyzoryki", 3, 8, 12)
            ),
            unit(
                "C", "Wykroki z wyskokiem, wąskie pompki, jedna noga",
                iso("Wykroki z wyskokiem", 3, 8, 12),
                iso("Pompki wąskie", 3, 8, 15),
                aux("Przysiad jednonóż do skrzyni", 3, 5, 8),
                iso("Pompki hindu", 3, 8, 12),
                iso("Wspięcia na palce jednonóż", 3, 12, 20),
                core("Rowerek", 3, 12, 20)
            )
        )
    ),

    ProgramTemplate(
        name = "Obwód bez sprzętu",
        summary = "Kondycja: krótkie przerwy, całe ciało",
        description = "Ćwiczenia jedno po drugim z minutą przerwy - tętno ma zostać " +
            "wysoko. Dobre na dni, kiedy nie ma czasu ani sprzętu, i jako " +
            "dodatek do siłowni.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Obwód A", "Burpees, wyskoki, pompki, wspinaczka",
                core("Burpees", 3, 8, 12),
                core("Przysiad z wyskokiem", 3, 10, 15),
                core("Pompki", 3, 8, 15),
                core("Wspinaczka górska", 3, 15, 20),
                core("Wykroki z wyskokiem", 3, 8, 12)
            ),
            unit(
                "Obwód B", "Skoki w dal, pompki hindu, nożyce",
                core("Skoki w dal z miejsca", 3, 5, 8),
                core("Pompki hindu", 3, 8, 12),
                core("Przysiad bez obciążenia", 3, 15, 25),
                core("Nożyce", 3, 15, 20),
                core("Burpees", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Brzuch i core",
        summary = "Dodatek do każdego planu, dwa krótkie treningi",
        description = "Brzuch jako osobny, krótki trening - po głównym albo w dzień " +
            "wolny. Drugi trening wymaga drążka do zwisu.",
        days = listOf(2, 3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Brzuch A", "Na podłodze: martwy robak, spięcia odwrotne",
                core("Martwy robak", 3, 8, 12),
                core("Spięcia odwrotne", 3, 10, 15),
                core("Unoszenie bioder w podporze bokiem", 3, 8, 12),
                core("Kołyski w łódce", 3, 10, 15),
                core("Rowerek", 3, 15, 20)
            ),
            unit(
                "Brzuch B", "Zwis, scyzoryki, skosy",
                core("Unoszenie kolan w zwisie", 3, 8, 12),
                core("Scyzoryki", 3, 8, 12),
                core("Spięcia boczne", 3, 12, 15),
                core("Unoszenie ręki i nogi w klęku podpartym", 3, 8, 12),
                core("Brzuszki", 3, 15, 20)
            )
        )
    ),

    // ---- kalistenika ----
    ProgramTemplate(
        name = "Minimum - pompki i podciąganie",
        summary = "Dwa krótkie treningi na drążku i poręczach",
        description = "Cztery ćwiczenia na trening, dwa razy w tygodniu. Na czas, kiedy " +
            "na więcej nie ma miejsca - drążek w drzwiach i poręcze na podwórku " +
            "wystarczą.",
        days = listOf(2),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Minimum A", "Podciąganie, pompki, przysiad",
                aux("Podciąganie na drążku", 3, 3, 8),
                iso("Pompki", 3, 8, 20),
                iso("Przysiad bez obciążenia", 3, 15, 25),
                iso("Unoszenie kolan w zwisie", 3, 8, 12)
            ),
            unit(
                "Minimum B", "Australijskie, poręcze, wykroki",
                iso("Podciąganie australijskie", 3, 8, 15),
                aux("Dipy na poręczach", 3, 5, 10),
                iso("Wykroki bez obciążenia", 3, 10, 15),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Kalistenika - początkujący",
        summary = "Drążek i poręcze od zera, do pierwszych podciągnięć",
        description = "Dla tych, którzy jeszcze się nie podciągają: podciąganie " +
            "negatywne, australijskie, ściąganie łopatek. Potrzebny drążek i coś " +
            "do podparcia przy dipach - ławka albo krzesło. Dwa treningi na zmianę.",
        days = listOf(3),
        level = Level.BEGINNER,
        units = listOf(
            unit(
                "Kalistenika A", "Negatywy, pompki przy podwyższeniu, dipy na ławce",
                aux("Podciąganie negatywne", 4, 3, 6),
                iso("Pompki z rękami na podwyższeniu", 3, 8, 15),
                iso("Przysiad bez obciążenia", 3, 15, 20),
                iso("Dipy na ławce", 3, 8, 12),
                iso("Wspięcia na palce bez obciążenia", 3, 15, 20),
                iso("Unoszenie kolan w zwisie", 3, 8, 12)
            ),
            unit(
                "Kalistenika B", "Łopatki, australijskie, pompki",
                iso("Ściąganie łopatek w zwisie", 3, 8, 12),
                iso("Pompki", 3, 5, 12),
                iso("Wykroki bez obciążenia", 3, 8, 12),
                iso("Podciąganie australijskie", 4, 8, 12),
                iso("Mostek biodrowy", 3, 12, 20),
                core("Martwy robak", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Kalistenika - średniozaawansowany",
        summary = "Pchanie, ciągnięcie, nogi na drążku i poręczach",
        description = "Dla tych, którzy robią kilka czystych podciągnięć i dipów. " +
            "Trudniejsze warianty: pompki łucznicze, pike z nogami wyżej, " +
            "przysiad na jednej nodze do skrzyni.",
        days = listOf(3, 4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Pchanie", "Dipy, pompki łucznicze, pike",
                aux("Dipy na poręczach", 4, 6, 12),
                aux("Pompki łucznicze", 3, 5, 8),
                aux("Pompki pike z nogami na podwyższeniu", 3, 6, 10),
                iso("Pompki z klaśnięciem", 3, 5, 8),
                iso("Pompki diamentowe", 3, 8, 15)
            ),
            unit(
                "Ciągnięcie", "Podciąganie w trzech chwytach",
                aux("Podciąganie na drążku", 4, 5, 10),
                aux("Podciąganie neutralnym chwytem", 3, 5, 10),
                iso("Podciąganie australijskie", 3, 10, 15),
                aux("Podciąganie podchwytem wąskim", 3, 5, 10),
                iso("Ściąganie łopatek w zwisie", 3, 10, 15)
            ),
            unit(
                "Nogi i brzuch", "Jedna noga, mostki, unoszenie nóg",
                aux("Przysiad jednonóż do skrzyni", 4, 5, 10),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                iso("Wspięcia na palce jednonóż", 3, 12, 20),
                iso("Unoszenie nóg w zwisie", 3, 6, 12),
                core("Kołyski w łódce", 3, 10, 15)
            )
        )
    ),

    ProgramTemplate(
        name = "Kalistenika - zaawansowany",
        summary = "Wyjście siłowe, stanie na rękach, pistolet",
        description = "Cztery treningi pod umiejętności: wyjście siłowe, pompki w staniu " +
            "na rękach, front lever, pistolet. Zakłada drążek, poręcze " +
            "i pierścienie oraz pewne kilkanaście podciągnięć.",
        days = listOf(4),
        level = Level.ADVANCED,
        units = listOf(
            unit(
                "Siła ciągnięcia", "Wyjście siłowe, łucznicze, front lever",
                aux("Wyjście siłowe na drążku", 4, 3, 6),
                aux("Podciąganie łucznicze", 4, 3, 6),
                aux("Wiosłowanie w pozycji front lever", 3, 3, 6),
                aux("Podciąganie szerokim chwytem", 3, 6, 10),
                iso("Unoszenie nóg do drążka", 3, 6, 10)
            ),
            unit(
                "Siła pchania", "Stanie na rękach, pierścienie, planche",
                aux("Pompki w staniu na rękach przy ścianie", 4, 3, 6),
                aux("Dipy na pierścieniach", 4, 5, 10),
                aux("Pompki pseudo planche", 3, 5, 10),
                aux("Pompki na jednej ręce", 3, 3, 5),
                iso("Pompki sfinks", 3, 8, 12)
            ),
            unit(
                "Nogi", "Pistolet, nordyckie, krewetka",
                aux("Przysiad pistolet", 4, 3, 6),
                aux("Uginanie nordyckie", 3, 3, 5),
                aux("Przysiad krewetka", 3, 3, 6),
                iso("Skoki na skrzynię", 3, 5, 8),
                iso("Przysiad sissy", 3, 8, 12),
                iso("Wspięcia na palce jednonóż", 3, 15, 20)
            ),
            unit(
                "Umiejętności", "Wyjście na pierścieniach, ściana, smoczy sztandar",
                aux("Wyjście siłowe na pierścieniach", 4, 3, 5),
                aux("Wchodzenie po ścianie do stania na rękach", 3, 3, 5),
                aux("Smoczy sztandar", 3, 3, 6),
                iso("Wycieraczki w zwisie", 3, 6, 10),
                iso("Pompki łucznicze", 3, 6, 10)
            )
        )
    ),

    ProgramTemplate(
        name = "Kalistenika - PPL",
        summary = "Pchanie, ciągnięcie, nogi z masą ciała",
        description = "Podział PPL bez ciężarów: drążek, poręcze i podłoga. Raz w tygodniu " +
            "każdy trening przy trzech dniach, dwa razy przy sześciu.",
        days = listOf(3, 6),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Push", "Dipy, pompki, pike",
                aux("Dipy na poręczach", 4, 8, 12),
                iso("Pompki z nogami na podwyższeniu", 3, 10, 15),
                iso("Pompki w pozycji pike", 3, 8, 12),
                iso("Pompki diamentowe", 3, 8, 15),
                iso("Pompki sfinks", 3, 8, 12)
            ),
            unit(
                "Pull", "Podciąganie, australijskie, łopatki",
                aux("Podciąganie na drążku", 4, 5, 10),
                iso("Podciąganie australijskie", 4, 10, 15),
                aux("Podciąganie podchwytem", 3, 5, 10),
                iso("Ściąganie łopatek w zwisie", 3, 10, 15),
                iso("Unoszenie kolan w zwisie", 3, 10, 15)
            ),
            unit(
                "Nogi", "Bułgarskie, jedna noga, wyskoki",
                iso("Bułgarskie przysiady bez obciążenia", 4, 10, 15),
                aux("Przysiad jednonóż do skrzyni", 3, 6, 10),
                iso("Mostek biodrowy jednonóż", 3, 12, 15),
                iso("Wykroki z wyskokiem", 3, 8, 12),
                iso("Wspięcia na palce jednonóż", 4, 12, 20)
            )
        )
    ),

    ProgramTemplate(
        name = "Drążek i poręcze - Góra / Dół",
        summary = "Cztery treningi z masą ciała, góra i dół na zmianę",
        description = "Układ góra/dół bez ciężarów. Góra na drążku i poręczach, dół na " +
            "jednej nodze, bo na dwóch masa ciała szybko przestaje wystarczać.",
        days = listOf(4),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Góra A", "Podciąganie, dipy, australijskie",
                aux("Podciąganie na drążku", 4, 5, 10),
                aux("Dipy na poręczach", 4, 6, 12),
                iso("Podciąganie australijskie", 3, 10, 15),
                iso("Pompki w pozycji pike", 3, 6, 12),
                iso("Unoszenie nóg w podporze na poręczach", 3, 8, 12)
            ),
            unit(
                "Dół A", "Przysiad, bułgarskie, mostki",
                iso("Przysiad bez obciążenia", 4, 15, 25),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                iso("Wspięcia na palce jednonóż", 3, 12, 20),
                core("Martwy robak", 3, 10, 12)
            ),
            unit(
                "Góra B", "Neutralny chwyt, łucznicze, niski drążek",
                aux("Podciąganie neutralnym chwytem", 4, 5, 10),
                aux("Pompki łucznicze", 3, 5, 8),
                aux("Podciąganie podchwytem wąskim", 3, 5, 10),
                iso("Prostowanie ramion na niskim drążku", 3, 8, 12),
                iso("Unoszenie kolan w zwisie", 3, 10, 15)
            ),
            unit(
                "Dół B", "Jedna noga do skrzyni, kozacki, nordyckie",
                aux("Przysiad jednonóż do skrzyni", 4, 5, 10),
                iso("Przysiad kozacki", 3, 6, 10),
                iso("Wejścia na krzesło", 3, 10, 15),
                aux("Uginanie nordyckie", 3, 3, 6),
                core("Unoszenie bioder w podporze bokiem", 3, 8, 12)
            )
        )
    ),

    ProgramTemplate(
        name = "Pierścienie lub TRX",
        summary = "Całe ciało na taśmach albo kółkach gimnastycznych",
        description = "Wiosłowania, pompki i dipy na niestabilnym uchwycie - zawieszone " +
            "na drążku, w drzwiach albo na gałęzi. Trudność zmienia kąt ciała: " +
            "im bardziej poziomo, tym ciężej.",
        days = listOf(3),
        level = Level.INTERMEDIATE,
        units = listOf(
            unit(
                "Pierścienie A", "Wiosłowanie, pompki, uginanie",
                iso("Wiosłowanie na pierścieniach", 4, 8, 12),
                iso("Pompki na pierścieniach", 4, 6, 12),
                iso("Uginanie ramion na pierścieniach", 3, 8, 12),
                iso("Bułgarskie przysiady bez obciążenia", 3, 10, 15),
                core("Kołyski w łódce", 3, 10, 15)
            ),
            unit(
                "Pierścienie B", "Dipy, rozpiętki, kozacki",
                aux("Dipy na pierścieniach", 4, 5, 10),
                iso("Wiosłowanie na pierścieniach", 3, 10, 15),
                iso("Rozpiętki na pierścieniach", 3, 6, 10),
                iso("Przysiad kozacki", 3, 6, 10),
                iso("Mostek biodrowy jednonóż", 3, 10, 15),
                core("Spięcia odwrotne", 3, 10, 15)
            )
        )
    )
)
