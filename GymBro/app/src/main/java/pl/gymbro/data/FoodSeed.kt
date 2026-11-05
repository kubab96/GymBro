package pl.gymbro.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Produkty podstawowe dostarczone z aplikacją.
 *
 * Projekt: `docs/kalorie.md`, rozdział 7.
 *
 * Dlaczego to w ogóle istnieje, skoro jest Open Food Facts: tamta baza jest
 * zorientowana na **opakowania**. Ma kod kreskowy jogurtu konkretnej mleczarni,
 * ale nie ma „piersi z kurczaka" ani „ryżu białego", bo te nie mają producenta
 * ani etykiety. A to z nich składa się większość dnia.
 *
 * Kurowane, nie ogromne. Tych pozycji jest około stu pięćdziesięciu i to
 * pokrywa prawie wszystko, co się naprawdę je. Trzydzieści tysięcy wpisów
 * z sieci nie pomoże, jeśli wśród nich nie ma kurczaka.
 *
 * ## Dwie konwencje, których nie wolno pomylić
 *
 * **Węglowodany nie zawierają błonnika.** Tak liczy się je na etykietach
 * w Unii i tak podaje je Open Food Facts dla produktów europejskich -
 * `węglowodany` i `błonnik` to dwie osobne pozycje deklaracji. W Stanach jest
 * odwrotnie: `Total Carbohydrate` obejmuje błonnik. Pomylenie tego podwaja
 * węglowodany w migdałach i w awokado, a przy liczeniu kalorii z makro daje
 * rozjazd kilkunastu procent. Wartości tutaj są **europejskie**.
 *
 * **Wszystko na 100 g produktu surowego**, o ile nazwa nie mówi inaczej.
 * Ryż, makarony i kasze są suche - tak się je waży przed gotowaniem, a wagę
 * po ugotowaniu obsługują przepisy ([Recipe]).
 *
 * Liczby są typowe, nie zmierzone: z tablic wartości odżywczych i z etykiet,
 * zaokrąglone do czytelnej dokładności. Dla produktu, który jesz codziennie,
 * warto je potem podmienić na te z konkretnego opakowania - dlatego zestaw
 * nie jest nigdy nadpisywany danymi z sieci.
 */
object FoodSeed {

    /**
     * Pozycja zestawu.
     *
     * Czysty Kotlin, bez Androida - dzięki temu całą tablicę sprawdza test
     * jednostkowy. Przy stu pięćdziesięciu wierszach wpisywanych ręcznie to
     * nie ostrożność, to konieczność: literówka w jednej liczbie przekłamuje
     * potem każdy dzień, w którym ten produkt wystąpi.
     */
    data class Item(
        val name: String,
        val kcal: Double,
        val proteinG: Double,

        /** Bez błonnika - patrz konwencja w opisie [FoodSeed]. */
        val carbsG: Double,

        val fatG: Double,
        val fiberG: Double? = null,

        /**
         * Czy kalorie pochodzą w większości z alkoholu.
         *
         * Alkohol ma 7 kcal na gram i **nie jest makroskładnikiem**, więc dla
         * tych pozycji kalorie nie wynikają z białka, węgli i tłuszczu.
         * Znacznik istnieje, żeby sprawdzanie zgodności nie zgłaszało ich
         * jako błędu danych - bo nim nie są.
         */
        val alcohol: Boolean = false,

        val portions: List<Portion> = emptyList()
    )

    data class Portion(val label: String, val grams: Double)

    private fun p(label: String, grams: Double) = Portion(label, grams)

    val ITEMS: List<Item> = listOf(

        // ---- Mięso, drób, wędliny ------------------------------------------
        Item(
            "Pierś z kurczaka, surowa", 110.0, 23.0, 0.0, 1.5,
            portions = listOf(p("sztuka", 180.0))
        ),
        Item("Udko z kurczaka bez skóry", 120.0, 19.0, 0.0, 4.5),
        Item("Pierś z indyka, surowa", 105.0, 22.0, 0.0, 1.0),
        Item("Wołowina, ligawa", 130.0, 21.0, 0.0, 4.5),
        Item("Wołowina mielona 10%", 180.0, 19.0, 0.0, 11.0),
        Item("Polędwica wołowa", 130.0, 21.0, 0.0, 5.0),
        Item("Schab środkowy", 140.0, 21.0, 0.0, 6.0),
        Item("Karkówka", 220.0, 17.0, 0.0, 17.0),
        Item("Boczek wędzony", 420.0, 13.0, 0.0, 40.0),
        Item("Kiełbasa śląska", 300.0, 13.0, 1.0, 27.0),
        Item("Parówki drobiowe", 240.0, 11.0, 2.0, 20.0),
        Item(
            "Szynka z indyka", 100.0, 18.0, 1.5, 2.5,
            portions = listOf(p("plaster", 20.0))
        ),
        Item("Wątróbka drobiowa", 130.0, 20.0, 1.0, 5.0),

        // ---- Jaja ----------------------------------------------------------
        Item(
            "Jajo kurze całe", 140.0, 12.5, 0.7, 9.5,
            portions = listOf(p("sztuka L", 55.0), p("sztuka M", 48.0))
        ),
        Item("Białko jaja", 50.0, 11.0, 0.7, 0.2, portions = listOf(p("sztuka", 33.0))),
        Item("Żółtko jaja", 320.0, 16.0, 0.3, 28.0, portions = listOf(p("sztuka", 18.0))),

        // ---- Ryby i owoce morza --------------------------------------------
        Item("Łosoś świeży", 200.0, 20.0, 0.0, 13.0),
        Item("Łosoś wędzony", 180.0, 22.0, 0.0, 10.0),
        Item("Dorsz", 80.0, 18.0, 0.0, 0.7),
        Item("Mintaj", 75.0, 17.0, 0.0, 0.6),
        Item(
            "Tuńczyk w sosie własnym", 100.0, 23.0, 0.0, 1.0,
            portions = listOf(p("puszka odsączona", 120.0))
        ),
        Item("Makrela wędzona", 300.0, 20.0, 0.0, 24.0),
        Item("Śledź w oleju", 280.0, 16.0, 0.0, 24.0),
        Item("Krewetki", 85.0, 18.0, 0.0, 1.0),

        // ---- Nabiał --------------------------------------------------------
        Item("Mleko 0,5%", 37.0, 3.4, 4.9, 0.5, portions = listOf(p("szklanka", 250.0))),
        Item("Mleko 2%", 50.0, 3.4, 4.8, 2.0, portions = listOf(p("szklanka", 250.0))),
        Item("Mleko 3,2%", 62.0, 3.3, 4.7, 3.2, portions = listOf(p("szklanka", 250.0))),
        Item(
            "Jogurt naturalny 2%", 60.0, 4.3, 6.0, 2.0,
            portions = listOf(p("kubek", 150.0), p("duży kubek", 400.0))
        ),
        Item("Jogurt grecki 10%", 130.0, 5.5, 3.5, 10.0, portions = listOf(p("kubek", 150.0))),
        Item("Skyr naturalny", 65.0, 11.0, 4.0, 0.2, portions = listOf(p("kubek", 150.0))),
        Item("Kefir 2%", 50.0, 3.3, 4.5, 2.0, portions = listOf(p("szklanka", 250.0))),
        Item("Maślanka", 40.0, 3.3, 4.7, 1.0, portions = listOf(p("szklanka", 250.0))),
        Item(
            "Twaróg chudy", 95.0, 19.0, 3.5, 0.5,
            portions = listOf(p("kostka", 250.0), p("porcja", 100.0))
        ),
        Item("Twaróg półtłusty", 130.0, 17.0, 3.0, 5.0, portions = listOf(p("kostka", 250.0))),
        Item("Serek wiejski", 100.0, 12.0, 3.0, 4.5, portions = listOf(p("opakowanie", 200.0))),
        Item("Serek homogenizowany naturalny", 130.0, 9.0, 8.0, 7.0),
        Item("Serek kanapkowy", 230.0, 6.0, 4.0, 21.0, portions = listOf(p("łyżka", 15.0))),
        Item("Ser gouda", 350.0, 25.0, 0.0, 27.0, portions = listOf(p("plaster", 20.0))),
        Item("Mozzarella", 250.0, 18.0, 2.0, 19.0, portions = listOf(p("kulka", 125.0))),
        Item("Parmezan", 400.0, 33.0, 0.0, 29.0, portions = listOf(p("łyżka startego", 8.0))),
        Item("Feta", 260.0, 14.0, 1.0, 21.0),
        Item("Śmietana 18%", 180.0, 2.5, 3.5, 18.0, portions = listOf(p("łyżka", 15.0))),
        Item("Śmietana 30%", 290.0, 2.3, 3.0, 30.0, portions = listOf(p("łyżka", 15.0))),
        Item(
            "Masło", 740.0, 0.7, 0.7, 82.0,
            portions = listOf(p("łyżeczka", 5.0), p("na kromkę", 8.0))
        ),

        // ---- Pieczywo ------------------------------------------------------
        Item(
            "Chleb żytni razowy", 220.0, 6.0, 43.0, 1.5, fiberG = 7.0,
            portions = listOf(p("kromka", 35.0))
        ),
        Item(
            "Chleb pszenny", 260.0, 8.0, 50.0, 2.5, fiberG = 3.0,
            portions = listOf(p("kromka", 30.0))
        ),
        Item(
            "Chleb graham", 240.0, 8.0, 45.0, 2.0, fiberG = 5.0,
            portions = listOf(p("kromka", 35.0))
        ),
        Item(
            "Chleb tostowy", 270.0, 8.0, 50.0, 4.0, fiberG = 2.5,
            portions = listOf(p("kromka", 25.0))
        ),
        Item(
            "Bułka kajzerka", 290.0, 9.0, 56.0, 2.5, fiberG = 2.5,
            portions = listOf(p("sztuka", 55.0))
        ),
        Item(
            "Pieczywo chrupkie żytnie", 330.0, 10.0, 65.0, 1.5, fiberG = 16.0,
            portions = listOf(p("kromka", 10.0))
        ),
        Item(
            "Tortilla pszenna", 310.0, 8.0, 50.0, 8.0, fiberG = 3.0,
            portions = listOf(p("sztuka", 60.0))
        ),
        Item("Bułka tarta", 360.0, 11.0, 70.0, 3.0, fiberG = 4.0),

        // ---- Zboża, kasze, makarony ----------------------------------------
        Item(
            "Płatki owsiane", 370.0, 13.0, 60.0, 7.0, fiberG = 10.0,
            portions = listOf(p("porcja", 50.0), p("łyżka", 10.0))
        ),
        Item("Płatki owsiane górskie", 360.0, 12.0, 62.0, 6.0, fiberG = 9.0),
        Item(
            "Musli bez cukru", 360.0, 10.0, 60.0, 8.0, fiberG = 9.0,
            portions = listOf(p("porcja", 50.0))
        ),
        // Otręby to skrajny przypadek: 43 g błonnika na 100 g. Przy wzorze
        // europejskim (błonnik po 2 kcal) wychodzi z tego 277 kcal, a etykiety
        // podają około 250 - dlatego ta pozycja ma największy rozjazd w całym
        // zestawie i to nie jest błąd wpisu.
        Item(
            "Otręby pszenne", 253.0, 16.0, 22.0, 4.3, fiberG = 43.0,
            portions = listOf(p("łyżka", 8.0))
        ),
        Item(
            "Ryż biały, suchy", 350.0, 7.0, 78.0, 1.0, fiberG = 1.4,
            portions = listOf(p("porcja", 60.0), p("szklanka", 180.0))
        ),
        Item(
            "Ryż brązowy, suchy", 350.0, 8.0, 73.0, 2.5, fiberG = 4.0,
            portions = listOf(p("porcja", 60.0))
        ),
        Item(
            "Kasza gryczana, sucha", 340.0, 12.0, 66.0, 3.0, fiberG = 6.0,
            portions = listOf(p("porcja", 60.0))
        ),
        Item(
            "Kasza jaglana, sucha", 350.0, 10.0, 70.0, 3.0, fiberG = 3.0,
            portions = listOf(p("porcja", 60.0))
        ),
        Item("Kasza jęczmienna, sucha", 340.0, 9.0, 70.0, 2.0, fiberG = 6.0),
        Item(
            "Makaron pszenny, suchy", 360.0, 12.0, 71.0, 1.5, fiberG = 3.0,
            portions = listOf(p("porcja", 80.0))
        ),
        Item(
            "Makaron pełnoziarnisty, suchy", 340.0, 13.0, 64.0, 2.5, fiberG = 8.0,
            portions = listOf(p("porcja", 80.0))
        ),
        Item("Kuskus, suchy", 360.0, 12.0, 72.0, 1.5, fiberG = 5.0),
        Item("Mąka pszenna", 350.0, 10.0, 72.0, 1.0, fiberG = 3.0, portions = listOf(p("łyżka", 10.0))),
        Item("Mąka żytnia", 330.0, 8.0, 70.0, 1.5, fiberG = 6.0),

        // ---- Warzywa -------------------------------------------------------
        Item(
            "Ziemniaki", 75.0, 2.0, 15.0, 0.1, fiberG = 1.5,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item("Bataty", 90.0, 1.6, 17.0, 0.1, fiberG = 3.0),
        Item("Pomidor", 18.0, 0.9, 2.6, 0.2, fiberG = 1.2, portions = listOf(p("sztuka", 120.0))),
        Item("Ogórek świeży", 12.0, 0.7, 1.8, 0.1, fiberG = 0.7, portions = listOf(p("sztuka", 250.0))),
        Item("Papryka czerwona", 30.0, 1.0, 4.6, 0.3, fiberG = 2.0, portions = listOf(p("sztuka", 150.0))),
        Item("Cebula", 40.0, 1.2, 6.3, 0.1, fiberG = 1.7, portions = listOf(p("sztuka", 100.0))),
        Item("Marchew", 35.0, 0.9, 6.0, 0.2, fiberG = 2.8, portions = listOf(p("sztuka", 80.0))),
        Item("Brokuł", 34.0, 2.8, 2.5, 0.4, fiberG = 2.6),
        Item("Kalafior", 25.0, 1.9, 2.3, 0.3, fiberG = 2.0),
        Item("Kapusta biała", 25.0, 1.3, 3.4, 0.1, fiberG = 2.5),
        Item("Kapusta kiszona", 20.0, 1.0, 1.2, 0.1, fiberG = 2.0),
        Item("Szpinak", 23.0, 2.9, 1.4, 0.4, fiberG = 2.2),
        Item("Sałata lodowa", 15.0, 1.4, 1.5, 0.2, fiberG = 1.2),
        Item("Cukinia", 17.0, 1.2, 2.1, 0.3, fiberG = 1.0),
        Item("Buraki", 43.0, 1.6, 8.0, 0.2, fiberG = 2.8),
        Item("Pieczarki", 22.0, 3.1, 1.0, 0.3, fiberG = 1.0),
        Item("Fasolka szparagowa", 31.0, 1.8, 4.0, 0.2, fiberG = 3.0),
        Item("Groszek zielony", 80.0, 5.0, 11.0, 0.4, fiberG = 5.0),
        Item("Kukurydza konserwowa", 85.0, 3.0, 14.0, 1.2, fiberG = 3.0),
        Item("Ogórek kwaszony", 12.0, 0.6, 1.5, 0.1, fiberG = 0.8),
        Item("Pomidory z puszki", 25.0, 1.2, 3.5, 0.2, fiberG = 1.2),
        // Awokado to podręcznikowy przykład pomylenia konwencji: po amerykańsku
        // ma 9 g węglowodanów, po europejsku 1 g, bo pozostałe 7 to błonnik.
        Item("Awokado", 160.0, 2.0, 1.0, 15.0, fiberG = 7.0, portions = listOf(p("sztuka", 150.0))),

        // ---- Owoce ---------------------------------------------------------
        Item("Banan", 90.0, 1.1, 20.0, 0.3, fiberG = 2.6, portions = listOf(p("sztuka", 120.0))),
        Item("Jabłko", 52.0, 0.3, 12.0, 0.2, fiberG = 2.4, portions = listOf(p("sztuka", 180.0))),
        Item("Gruszka", 57.0, 0.4, 12.0, 0.1, fiberG = 3.0, portions = listOf(p("sztuka", 170.0))),
        Item("Truskawki", 32.0, 0.7, 6.0, 0.3, fiberG = 2.0),
        Item("Maliny", 52.0, 1.2, 5.0, 0.7, fiberG = 6.5),
        Item("Borówki", 57.0, 0.7, 12.0, 0.3, fiberG = 2.4),
        Item("Winogrona", 70.0, 0.7, 17.0, 0.2, fiberG = 0.9),
        Item("Pomarańcza", 47.0, 0.9, 9.0, 0.1, fiberG = 2.4, portions = listOf(p("sztuka", 180.0))),
        Item("Mandarynka", 53.0, 0.8, 11.0, 0.3, fiberG = 1.8, portions = listOf(p("sztuka", 80.0))),
        Item("Arbuz", 30.0, 0.6, 7.5, 0.2, fiberG = 0.4),
        Item("Ananas", 50.0, 0.5, 12.0, 0.1, fiberG = 1.4),
        Item("Kiwi", 61.0, 1.1, 11.0, 0.5, fiberG = 3.0, portions = listOf(p("sztuka", 75.0))),
        Item("Brzoskwinia", 39.0, 0.9, 8.0, 0.2, fiberG = 1.5),
        Item("Śliwki", 46.0, 0.7, 10.0, 0.3, fiberG = 1.4),
        Item("Rodzynki", 300.0, 3.0, 75.0, 0.5, fiberG = 4.0, portions = listOf(p("łyżka", 15.0))),
        Item("Daktyle suszone", 280.0, 2.5, 68.0, 0.4, fiberG = 8.0, portions = listOf(p("sztuka", 8.0))),
        Item("Morele suszone", 240.0, 3.4, 53.0, 0.5, fiberG = 7.0),

        // ---- Orzechy i nasiona ---------------------------------------------
        Item("Orzechy włoskie", 650.0, 15.0, 7.0, 65.0, fiberG = 7.0, portions = listOf(p("garść", 30.0))),
        Item("Migdały", 580.0, 21.0, 9.0, 50.0, fiberG = 12.0, portions = listOf(p("garść", 30.0))),
        Item("Nerkowce", 550.0, 18.0, 27.0, 44.0, fiberG = 3.0, portions = listOf(p("garść", 30.0))),
        Item("Orzeszki ziemne", 570.0, 26.0, 8.0, 49.0, fiberG = 8.0, portions = listOf(p("garść", 30.0))),
        Item(
            "Masło orzechowe 100%", 600.0, 25.0, 12.0, 50.0, fiberG = 6.0,
            portions = listOf(p("łyżka", 15.0))
        ),
        Item("Siemię lniane", 530.0, 18.0, 2.0, 42.0, fiberG = 27.0, portions = listOf(p("łyżka", 10.0))),
        Item("Nasiona chia", 480.0, 17.0, 8.0, 31.0, fiberG = 34.0, portions = listOf(p("łyżka", 12.0))),
        Item("Pestki dyni", 560.0, 30.0, 11.0, 49.0, fiberG = 6.0, portions = listOf(p("garść", 30.0))),
        Item("Słonecznik", 580.0, 21.0, 11.0, 51.0, fiberG = 9.0, portions = listOf(p("garść", 30.0))),
        Item("Wiórki kokosowe", 600.0, 7.0, 7.0, 62.0, fiberG = 16.0),

        // ---- Tłuszcze ------------------------------------------------------
        Item(
            "Oliwa z oliwek", 880.0, 0.0, 0.0, 100.0,
            portions = listOf(p("łyżka", 10.0), p("łyżeczka", 5.0))
        ),
        Item("Olej rzepakowy", 880.0, 0.0, 0.0, 100.0, portions = listOf(p("łyżka", 10.0))),
        Item("Smalec", 890.0, 0.0, 0.0, 99.0, portions = listOf(p("łyżka", 15.0))),
        Item("Majonez", 700.0, 1.0, 2.0, 77.0, portions = listOf(p("łyżka", 15.0))),

        // ---- Strączkowe ----------------------------------------------------
        Item(
            "Soczewica czerwona, sucha", 340.0, 24.0, 50.0, 1.5, fiberG = 10.0,
            portions = listOf(p("porcja", 60.0))
        ),
        Item("Ciecierzyca, sucha", 360.0, 19.0, 45.0, 6.0, fiberG = 15.0),
        Item(
            "Ciecierzyca z puszki", 120.0, 7.0, 14.0, 2.5, fiberG = 6.0,
            portions = listOf(p("puszka odsączona", 240.0))
        ),
        Item(
            "Fasola czerwona z puszki", 100.0, 7.0, 11.0, 0.5, fiberG = 6.0,
            portions = listOf(p("puszka odsączona", 240.0))
        ),
        Item("Fasola biała, sucha", 330.0, 21.0, 46.0, 1.5, fiberG = 15.0),
        Item("Tofu naturalne", 120.0, 13.0, 2.0, 7.0, fiberG = 1.0),
        Item("Hummus", 230.0, 7.0, 15.0, 16.0, fiberG = 5.0, portions = listOf(p("łyżka", 25.0))),

        // ---- Słodkie i dodatki ---------------------------------------------
        Item("Miód", 320.0, 0.3, 80.0, 0.0, portions = listOf(p("łyżeczka", 8.0))),
        Item("Cukier", 400.0, 0.0, 100.0, 0.0, portions = listOf(p("łyżeczka", 5.0))),
        Item(
            "Czekolada gorzka 70%", 570.0, 8.0, 32.0, 42.0, fiberG = 10.0,
            portions = listOf(p("kostka", 8.0), p("tabliczka", 100.0))
        ),
        Item(
            "Czekolada mleczna", 540.0, 7.0, 58.0, 31.0, fiberG = 2.5,
            portions = listOf(p("kostka", 8.0))
        ),
        Item(
            "Dżem niskosłodzony", 150.0, 0.4, 36.0, 0.1, fiberG = 1.0,
            portions = listOf(p("łyżeczka", 10.0))
        ),
        Item("Lody waniliowe", 200.0, 3.5, 24.0, 10.0),
        Item("Ciastka kruche", 470.0, 6.0, 65.0, 20.0, fiberG = 2.0),
        Item("Chipsy ziemniaczane", 530.0, 6.0, 52.0, 33.0, fiberG = 4.0),
        Item("Ketchup", 100.0, 1.2, 23.0, 0.2, fiberG = 1.0, portions = listOf(p("łyżka", 15.0))),
        Item("Musztarda", 80.0, 5.0, 6.0, 4.0, portions = listOf(p("łyżeczka", 8.0))),
        Item("Sos sojowy", 60.0, 6.0, 6.0, 0.5, portions = listOf(p("łyżka", 15.0))),

        // ---- Suplementy i inne ---------------------------------------------
        Item(
            "Odżywka białkowa WPC", 390.0, 78.0, 6.0, 6.0,
            portions = listOf(p("miarka", 30.0))
        ),
        Item("Żelatyna", 340.0, 85.0, 0.0, 0.0),
        Item("Drożdże piekarskie", 100.0, 12.0, 8.0, 1.5, fiberG = 2.0),

        // ---- Napoje --------------------------------------------------------
        Item("Kawa czarna bez cukru", 2.0, 0.1, 0.0, 0.0, portions = listOf(p("kubek", 250.0))),
        Item("Sok pomarańczowy", 45.0, 0.7, 10.0, 0.2, portions = listOf(p("szklanka", 250.0))),
        Item("Napój cola", 42.0, 0.0, 10.6, 0.0, portions = listOf(p("puszka", 330.0))),
        Item("Napój cola zero", 0.3, 0.0, 0.0, 0.0, portions = listOf(p("puszka", 330.0))),
        Item(
            "Piwo jasne", 43.0, 0.5, 3.5, 0.0, alcohol = true,
            portions = listOf(p("butelka", 500.0))
        ),
        Item(
            "Wino czerwone", 85.0, 0.1, 2.6, 0.0, alcohol = true,
            portions = listOf(p("kieliszek", 150.0))
        ),
        Item(
            "Wódka 40%", 230.0, 0.0, 0.0, 0.0, alcohol = true,
            portions = listOf(p("kieliszek", 50.0))
        ),

        // ====================================================================
        // POTRAWY PO OBRÓBCE
        //
        // Powyżej są surowce, tutaj to, co faktycznie leży na talerzu.
        // Rozdział jest konieczny, bo 100 g suchego ryżu to około 250 g
        // ugotowanego - „ryż" bez dopisku znaczy więc dwie rzeczy różniące
        // się dwuipółkrotnie, a waży się to, co się nakłada.
        //
        // Dwie konwencje dla tłuszczu, bo nie da się ich zmierzyć z zewnątrz:
        // smażenie dokłada około 5-7 g tłuszczu na 100 g gotowego dania,
        // panierowanie 10-13 g. To są wartości typowe, nie pomiary - zależą
        // od patelni, ilości oleju i tego, czy odsączasz. Danie, które jesz
        // co tydzień, lepiej złożyć z przepisu ze składników.
        // ====================================================================

        // ---- Ziemniaki i dodatki skrobiowe ---------------------------------
        Item(
            "Ziemniaki gotowane", 80.0, 2.0, 17.0, 0.1, fiberG = 1.5,
            portions = listOf(p("sztuka", 80.0), p("porcja", 200.0))
        ),
        Item(
            "Ziemniaki smażone", 150.0, 2.2, 18.0, 7.5, fiberG = 1.6,
            portions = listOf(p("porcja", 200.0))
        ),
        Item("Frytki", 290.0, 3.5, 36.0, 14.0, fiberG = 3.0, portions = listOf(p("porcja", 150.0))),
        Item(
            "Puree ziemniaczane", 110.0, 2.0, 15.0, 4.5, fiberG = 1.3,
            portions = listOf(p("porcja", 200.0))
        ),
        Item(
            "Placki ziemniaczane", 220.0, 3.5, 25.0, 11.0, fiberG = 2.0,
            portions = listOf(p("sztuka", 50.0))
        ),
        Item("Kopytka", 160.0, 4.0, 30.0, 2.5, fiberG = 1.5, portions = listOf(p("porcja", 200.0))),
        Item(
            "Kluski śląskie", 160.0, 3.5, 33.0, 1.0, fiberG = 1.2,
            portions = listOf(p("sztuka", 30.0), p("porcja", 180.0))
        ),
        Item(
            "Ryż biały, ugotowany", 130.0, 2.6, 28.0, 0.3, fiberG = 0.5,
            portions = listOf(p("porcja", 150.0))
        ),
        Item(
            "Ryż brązowy, ugotowany", 120.0, 2.7, 24.0, 0.9, fiberG = 1.5,
            portions = listOf(p("porcja", 150.0))
        ),
        Item(
            "Makaron, ugotowany", 150.0, 5.0, 29.0, 0.9, fiberG = 1.5,
            portions = listOf(p("porcja", 200.0))
        ),
        Item(
            "Makaron pełnoziarnisty, ugotowany", 135.0, 5.5, 25.0, 1.0, fiberG = 3.5,
            portions = listOf(p("porcja", 200.0))
        ),
        Item(
            "Kasza gryczana, ugotowana", 110.0, 4.0, 20.0, 1.0, fiberG = 2.0,
            portions = listOf(p("porcja", 150.0))
        ),
        Item(
            "Kasza jaglana, ugotowana", 115.0, 3.5, 23.0, 1.0, fiberG = 1.0,
            portions = listOf(p("porcja", 150.0))
        ),
        Item("Soczewica, ugotowana", 115.0, 9.0, 15.0, 0.4, fiberG = 4.0),
        Item("Ciecierzyca, ugotowana", 140.0, 8.0, 18.0, 2.5, fiberG = 6.0),

        // ---- Mięso i ryby po obróbce ---------------------------------------
        Item(
            "Kotlet schabowy panierowany", 290.0, 20.0, 12.0, 18.0, fiberG = 0.7,
            portions = listOf(p("sztuka", 120.0))
        ),
        Item("Schab pieczony", 190.0, 28.0, 0.0, 8.5, portions = listOf(p("plaster", 50.0))),
        Item(
            "Kotlet mielony", 250.0, 17.0, 8.0, 16.0, fiberG = 0.5,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item(
            "Pierś z kurczaka grillowana", 165.0, 31.0, 0.0, 3.6,
            portions = listOf(p("sztuka", 150.0))
        ),
        Item("Pierś z kurczaka panierowana", 250.0, 22.0, 12.0, 13.0, fiberG = 0.5),
        Item("Udko z kurczaka pieczone", 215.0, 26.0, 0.0, 12.0, portions = listOf(p("sztuka", 120.0))),
        Item("Karkówka z grilla", 280.0, 25.0, 0.0, 20.0, portions = listOf(p("sztuka", 150.0))),
        Item("Gulasz wołowy", 180.0, 16.0, 4.0, 11.0, fiberG = 0.5, portions = listOf(p("porcja", 200.0))),
        Item("Wątróbka smażona", 190.0, 23.0, 4.0, 9.0),
        Item("Ryba smażona panierowana", 200.0, 17.0, 10.0, 10.0, fiberG = 0.4),
        Item("Łosoś pieczony", 230.0, 25.0, 0.0, 14.0, portions = listOf(p("porcja", 150.0))),

        // ---- Jajka po obróbce ----------------------------------------------
        Item("Jajko gotowane", 145.0, 13.0, 0.7, 10.0, portions = listOf(p("sztuka", 50.0))),
        Item("Jajko sadzone", 195.0, 12.0, 0.8, 16.0, portions = listOf(p("sztuka", 55.0))),
        Item("Jajecznica na maśle", 200.0, 12.0, 1.0, 17.0, portions = listOf(p("porcja", 150.0))),
        Item("Omlet", 180.0, 11.0, 2.0, 14.0, portions = listOf(p("sztuka", 150.0))),

        // ---- Dania mączne ---------------------------------------------------
        Item(
            "Pierogi ruskie", 200.0, 6.0, 30.0, 6.0, fiberG = 1.5,
            portions = listOf(p("sztuka", 35.0), p("porcja", 250.0))
        ),
        Item(
            "Pierogi z mięsem", 220.0, 9.0, 28.0, 8.0, fiberG = 1.3,
            portions = listOf(p("sztuka", 35.0))
        ),
        Item("Naleśniki", 185.0, 6.0, 26.0, 6.0, fiberG = 0.8, portions = listOf(p("sztuka", 80.0))),
        Item(
            "Naleśniki z serem", 210.0, 8.0, 28.0, 7.0, fiberG = 1.0,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item(
            "Makaron z sosem bolońskim", 150.0, 7.0, 17.0, 5.5, fiberG = 1.5,
            portions = listOf(p("porcja", 350.0))
        ),
        Item("Lasagne", 170.0, 9.0, 15.0, 8.0, fiberG = 1.2, portions = listOf(p("porcja", 300.0))),
        Item("Pizza", 260.0, 11.0, 30.0, 10.0, fiberG = 2.0, portions = listOf(p("kawałek", 100.0))),

        // ---- Zupy ------------------------------------------------------------
        Item("Rosół", 35.0, 2.0, 2.5, 1.8, fiberG = 0.3, portions = listOf(p("talerz", 250.0))),
        Item(
            "Zupa pomidorowa z ryżem", 65.0, 1.8, 9.0, 2.3, fiberG = 0.7,
            portions = listOf(p("talerz", 250.0))
        ),
        Item("Żurek", 70.0, 2.5, 7.0, 3.5, fiberG = 0.6, portions = listOf(p("talerz", 250.0))),
        Item("Zupa ogórkowa", 50.0, 1.5, 6.0, 2.0, fiberG = 0.8, portions = listOf(p("talerz", 250.0))),
        Item("Barszcz czerwony", 35.0, 1.0, 6.0, 0.6, fiberG = 0.8, portions = listOf(p("talerz", 250.0))),
        Item("Krem z dyni", 60.0, 1.5, 7.0, 2.8, fiberG = 1.2, portions = listOf(p("talerz", 250.0))),
        Item("Grochówka", 90.0, 5.0, 11.0, 2.5, fiberG = 2.5, portions = listOf(p("talerz", 250.0))),

        // ---- Dania jednogarnkowe ---------------------------------------------
        Item("Bigos", 110.0, 6.0, 5.0, 7.0, fiberG = 2.5, portions = listOf(p("porcja", 250.0))),
        Item(
            "Gołąbki", 120.0, 7.0, 11.0, 4.5, fiberG = 1.5,
            portions = listOf(p("sztuka", 150.0))
        ),
        Item("Leczo", 80.0, 4.0, 6.0, 4.0, fiberG = 1.5, portions = listOf(p("porcja", 250.0))),
        Item(
            "Fasolka po bretońsku", 110.0, 6.0, 12.0, 3.5, fiberG = 3.5,
            portions = listOf(p("porcja", 250.0))
        ),
        Item("Risotto", 150.0, 4.0, 22.0, 4.5, fiberG = 0.8, portions = listOf(p("porcja", 300.0))),
        Item("Chili con carne", 130.0, 9.0, 10.0, 5.5, fiberG = 3.0, portions = listOf(p("porcja", 300.0))),

        // ---- Surówki i dodatki warzywne ---------------------------------------
        Item("Mizeria", 45.0, 1.0, 3.0, 3.0, fiberG = 0.6, portions = listOf(p("porcja", 100.0))),
        Item("Surówka z marchewki", 70.0, 0.8, 9.0, 3.0, fiberG = 2.5, portions = listOf(p("porcja", 100.0))),
        Item("Surówka z kapusty kiszonej", 60.0, 1.0, 4.0, 4.0, fiberG = 2.2, portions = listOf(p("porcja", 100.0))),
        Item("Sałatka jarzynowa", 180.0, 2.5, 9.0, 14.0, fiberG = 2.0, portions = listOf(p("porcja", 150.0))),
        Item("Buraczki zasmażane", 85.0, 1.5, 11.0, 3.5, fiberG = 2.5, portions = listOf(p("porcja", 100.0))),
        Item("Kapusta zasmażana", 90.0, 2.0, 6.0, 6.0, fiberG = 2.5, portions = listOf(p("porcja", 150.0))),
        Item("Warzywa gotowane na parze", 40.0, 2.5, 5.0, 0.4, fiberG = 2.5, portions = listOf(p("porcja", 200.0))),
        Item("Warzywa z patelni", 75.0, 2.5, 6.0, 4.5, fiberG = 2.5, portions = listOf(p("porcja", 200.0))),

        // ---- Śniadaniowe i kanapkowe -------------------------------------------
        Item("Owsianka na mleku", 95.0, 4.0, 14.0, 2.5, fiberG = 1.8, portions = listOf(p("porcja", 300.0))),
        Item("Owsianka na wodzie", 70.0, 2.5, 12.0, 1.4, fiberG = 1.7, portions = listOf(p("porcja", 300.0))),
        Item(
            "Kanapka z szynką i serem", 250.0, 13.0, 25.0, 10.0, fiberG = 1.8,
            portions = listOf(p("sztuka", 90.0))
        ),
        Item("Tost z serem", 280.0, 12.0, 28.0, 13.0, fiberG = 1.5, portions = listOf(p("sztuka", 70.0))),

        // ====================================================================
        // PIECZYWO Z WŁASNEGO WYPIEKU
        //
        // Osobny dział, bo tego nie da się wziąć z żadnej bazy produktów.
        // Bułki i chleby pieczone na miejscu w Biedronce, Lidlu czy piekarni
        // sprzedaje się **na wagę, bez kodu kreskowego** - a Open Food Facts
        // jest indeksowane kodami. Taka bułka fizycznie nie może tam trafić.
        //
        // Bazy społecznościowe przeczą sobie nawzajem - ta sama kajzerka raz
        // 262 kcal na 100 g, raz 297. Niektóre sieci publikują jednak własne
        // tabele i wtedy warto je wziąć: niżej jest dział Biedronki
        // z prawdziwymi wartościami.
        //
        // Ten dział zostaje dla reszty: piekarni za rogiem, innych sieci
        // i sytuacji, w której nie wiadomo, skąd bułka pochodzi.
        //
        // To produkty rodzajowe, jak „ziemniaki smażone". Różnica
        // między kajzerką z Biedronki, z Lidla i z piekarni za rogiem jest
        // rzędu kilku procent, a **masa bułki waha się od 50 do 80 gramów**,
        // czyli dwa razy bardziej. Dlatego przy każdej pozycji stoi typowa
        // gramatura - to ona decyduje o wyniku, nie receptura.
        // ====================================================================

        // ---- Bułki ------------------------------------------------------------
        Item(
            "Bułka pszenna zwykła", 285.0, 9.0, 55.0, 2.5, fiberG = 2.5,
            portions = listOf(p("sztuka", 55.0))
        ),
        Item(
            "Bułka grahamka", 265.0, 9.0, 48.0, 2.0, fiberG = 5.0,
            portions = listOf(p("sztuka", 60.0))
        ),
        Item(
            "Bułka maślana", 320.0, 8.0, 52.0, 8.0, fiberG = 2.0,
            portions = listOf(p("sztuka", 60.0))
        ),
        Item(
            "Bułka wieloziarnista", 290.0, 10.0, 45.0, 7.0, fiberG = 5.0,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item(
            "Bułka orkiszowa", 270.0, 9.0, 50.0, 3.0, fiberG = 4.0,
            portions = listOf(p("sztuka", 65.0))
        ),
        Item(
            "Bułka hamburgerowa", 280.0, 8.5, 50.0, 5.0, fiberG = 2.5,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item(
            "Bułka hot-dogowa", 280.0, 8.5, 50.0, 5.0, fiberG = 2.5,
            portions = listOf(p("sztuka", 60.0))
        ),

        // ---- Pozostałe pieczywo drobne -----------------------------------------
        Item(
            "Rogal maślany", 400.0, 7.5, 45.0, 21.0, fiberG = 2.0,
            portions = listOf(p("sztuka", 65.0))
        ),
        Item(
            "Bagietka", 275.0, 9.0, 54.0, 1.5, fiberG = 3.0,
            portions = listOf(p("kawałek", 60.0), p("sztuka", 250.0))
        ),
        Item("Ciabatta", 270.0, 9.0, 52.0, 2.5, fiberG = 2.5, portions = listOf(p("sztuka", 100.0))),
        Item(
            "Chałka", 330.0, 8.0, 58.0, 7.0, fiberG = 2.0,
            portions = listOf(p("kromka", 40.0))
        ),
        Item("Bajgiel", 280.0, 10.0, 53.0, 2.0, fiberG = 3.0, portions = listOf(p("sztuka", 90.0))),
        Item(
            "Obwarzanek krakowski", 290.0, 9.0, 57.0, 2.5, fiberG = 2.5,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item("Pita", 275.0, 9.0, 53.0, 1.5, fiberG = 2.5, portions = listOf(p("sztuka", 60.0))),
        Item("Cebularz", 300.0, 8.0, 50.0, 7.5, fiberG = 2.5, portions = listOf(p("sztuka", 120.0))),

        // ---- Chleby ------------------------------------------------------------
        Item(
            "Chleb pszenno-żytni", 240.0, 7.0, 46.0, 1.5, fiberG = 4.0,
            portions = listOf(p("kromka", 35.0))
        ),
        Item(
            "Chleb orkiszowy", 250.0, 8.5, 45.0, 2.0, fiberG = 6.0,
            portions = listOf(p("kromka", 35.0))
        ),
        Item(
            "Chleb wieloziarnisty", 265.0, 9.0, 42.0, 6.0, fiberG = 7.0,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb na zakwasie", 230.0, 7.0, 45.0, 1.0, fiberG = 4.0,
            portions = listOf(p("kromka", 40.0))
        ),

        // ---- Słodkie z piekarni -------------------------------------------------
        Item(
            "Drożdżówka z serem", 300.0, 7.0, 45.0, 9.5, fiberG = 1.5,
            portions = listOf(p("sztuka", 90.0))
        ),
        Item(
            "Jagodzianka", 290.0, 6.5, 48.0, 8.0, fiberG = 2.0,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item("Pączek", 390.0, 6.0, 47.0, 19.0, fiberG = 1.5, portions = listOf(p("sztuka", 70.0))),
        Item(
            "Donut lukrowany", 420.0, 6.0, 53.0, 20.0, fiberG = 1.5,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item("Muffin", 400.0, 5.5, 50.0, 19.0, fiberG = 1.5, portions = listOf(p("sztuka", 90.0))),

        // ====================================================================
        // PIECZYWO BIEDRONKI
        //
        // Wartosci z biedronka.pl/pl/pieczywo?page=skladniki - sieć publikuje
        // je dla całego pieczywa razem z gramaturą sztuki. To **prawdziwe
        // liczby z etykiety**, nie konwencje jak w dziale wyżej.
        //
        // Dział wyżej zostaje, bo pieczywo kupuje się też gdzie indziej.
        // Tutaj masz dokładność, tam uniwersalność.
        //
        // Ten sam produkt bywa u kilku producentów i wtedy wartości się
        // różnią - rogal croissant ma cztery warianty od 425 do 437 kcal.
        // Bierzemy jeden, bo przy półce i tak nie wiadomo, który leży
        // w koszyku, a różnica mieści się w grubości skórki.
        // ====================================================================

        // ---- Bułki -------------------------------------------------------------
        Item(
            "Bułka poranna, Biedronka", 289.0, 9.0, 56.0, 2.6, fiberG = 2.9,
            portions = listOf(p("sztuka", 65.0))
        ),
        Item(
            "Bułka poznańska, Biedronka", 274.0, 8.7, 54.0, 2.1, fiberG = 2.5,
            portions = listOf(p("sztuka", 75.0))
        ),
        Item(
            "Bułka codzienna, Biedronka", 272.0, 9.0, 53.0, 1.9, fiberG = 2.7,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item(
            "Bułka grahamka, Biedronka", 260.0, 10.9, 48.0, 1.4, fiberG = 6.9,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item(
            "Bułka pasterska, Biedronka", 265.0, 9.3, 52.0, 1.5, fiberG = 2.6,
            portions = listOf(p("sztuka", 90.0))
        ),
        Item(
            "Bułka śniadaniowa, Biedronka", 293.0, 8.7, 57.0, 2.4, fiberG = 3.2,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka śniadaniowa z ziarnami, Biedronka", 310.0, 11.0, 49.0, 6.9, fiberG = 3.2,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka śniadaniowa pełnoziarnista, Biedronka", 282.0, 9.3, 51.0, 2.7, fiberG = 7.7,
            portions = listOf(p("sztuka", 73.0))
        ),
        Item(
            "Bułka wysokobiałkowa, Biedronka", 282.0, 18.0, 35.0, 6.4, fiberG = 6.2,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka razowa z mąki żytniej, Biedronka", 240.0, 13.0, 33.0, 4.5, fiberG = 7.5,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka z pestkami dyni, Biedronka", 300.0, 12.0, 49.0, 5.8, fiberG = 2.7,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item(
            "Bułka z żółtym serem, Biedronka", 286.0, 11.0, 48.0, 5.1,
            portions = listOf(p("sztuka", 70.0))
        ),
        Item(
            "Bułka fala ciemna z ziarnami, Biedronka", 283.0, 10.0, 46.0, 5.6, fiberG = 3.7,
            portions = listOf(p("sztuka", 110.0))
        ),
        Item(
            "Bułka poranna fitness, Biedronka", 291.0, 11.0, 49.0, 4.6, fiberG = 6.6,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka samo ziarno, Biedronka", 309.0, 14.0, 19.0, 17.0, fiberG = 8.5,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Bułka paryska, Biedronka", 276.0, 9.8, 52.0, 2.8, fiberG = 2.4,
            portions = listOf(p("sztuka", 240.0))
        ),
        Item(
            "Bułka żytnia niski IG, Biedronka", 253.0, 11.0, 44.0, 2.7, fiberG = 4.1,
            portions = listOf(p("sztuka", 55.0))
        ),
        Item(
            "BIO bułka z chia i siemieniem, Biedronka", 282.0, 8.8, 52.0, 3.2, fiberG = 5.2,
            portions = listOf(p("sztuka", 75.0))
        ),
        Item(
            "BIO bułka z dynią, Biedronka", 329.0, 11.0, 52.0, 7.6, fiberG = 4.4,
            portions = listOf(p("sztuka", 70.0))
        ),

        // ---- Bagietki ------------------------------------------------------------
        Item(
            "Bagietka duża, Biedronka", 272.0, 9.1, 53.0, 2.1, fiberG = 2.5,
            portions = listOf(p("sztuka", 405.0), p("kawałek", 100.0))
        ),
        Item(
            "Półbagietka, Biedronka", 282.0, 9.5, 57.0, 1.2, fiberG = 3.0,
            portions = listOf(p("sztuka", 110.0))
        ),
        Item(
            "Półbagietka pełnoziarnista, Biedronka", 253.0, 11.0, 46.0, 1.4, fiberG = 6.6,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item(
            "Bagietka czosnkowa, Biedronka", 289.0, 8.1, 47.0, 7.0, fiberG = 2.9,
            portions = listOf(p("sztuka", 165.0))
        ),
        Item(
            "Ciabatta, Biedronka", 249.0, 8.7, 49.0, 1.4, fiberG = 2.4,
            portions = listOf(p("sztuka", 80.0))
        ),

        // ---- Chleby ----------------------------------------------------------------
        Item(
            "Chleb pasterski, Biedronka", 260.0, 9.1, 51.0, 1.4, fiberG = 2.7,
            portions = listOf(p("kromka", 40.0), p("bochenek", 500.0))
        ),
        Item(
            "Chleb rodzinny, Biedronka", 250.0, 8.5, 50.0, 1.0, fiberG = 3.3,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb słowiański, Biedronka", 264.0, 11.0, 51.0, 1.4, fiberG = 2.9,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb Nowodworski z makiem, Biedronka", 257.0, 9.0, 49.0, 1.8, fiberG = 3.7,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb fitness, Biedronka", 290.0, 12.0, 44.0, 6.4, fiberG = 4.1,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb orkiszowy z ziarnami, Biedronka", 292.0, 11.2, 46.0, 6.1, fiberG = 3.8,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb słonecznikowy, Biedronka", 292.0, 8.5, 43.0, 8.5, fiberG = 5.0,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb całe ziarno, Biedronka", 322.0, 12.0, 29.0, 15.0, fiberG = 9.7,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb multi-ziarno, Biedronka", 300.0, 10.0, 46.0, 7.3, fiberG = 5.0,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb żytni 100% bez drożdży, Biedronka", 210.0, 4.2, 43.0, 1.2, fiberG = 4.9,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb w stylu włoskim, Biedronka", 257.0, 9.0, 51.0, 1.4, fiberG = 2.4,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Chleb śniadaniowy, Biedronka", 242.0, 7.0, 50.0, 1.0,
            portions = listOf(p("kromka", 40.0))
        ),

        // ---- Słodkie z piekarni Biedronki -------------------------------------------
        Item(
            "Rogal croissant z masłem, Biedronka", 435.0, 7.5, 44.0, 25.0, fiberG = 2.0,
            portions = listOf(p("sztuka", 60.0))
        ),
        Item(
            "Croissant orzechowo-kakaowy, Biedronka", 414.0, 8.5, 45.4, 21.4, fiberG = 2.8,
            portions = listOf(p("sztuka", 85.0))
        ),
        Item(
            "Chałka z kruszonką, Biedronka", 347.0, 7.7, 59.0, 8.5, fiberG = 1.9,
            portions = listOf(p("kromka", 50.0))
        ),
        Item(
            "Rożek z jabłkiem, Biedronka", 352.0, 4.0, 45.0, 17.0, fiberG = 1.4,
            portions = listOf(p("sztuka", 95.0))
        ),
        Item(
            "Bajgiel z ziarnami, Biedronka", 351.0, 11.0, 48.0, 12.0, fiberG = 4.5,
            portions = listOf(p("sztuka", 80.0))
        ),
        Item(
            "Cynamonka, Biedronka", 410.0, 6.9, 53.0, 19.0, fiberG = 2.9,
            portions = listOf(p("sztuka", 100.0))
        ),
        Item(
            "Ciacho z kruszonką, Biedronka", 319.0, 7.0, 51.0, 9.3, fiberG = 1.6,
            portions = listOf(p("kawałek", 80.0))
        ),
        Item(
            "Chlebek bananowy, Biedronka", 356.0, 5.6, 42.0, 18.0, fiberG = 1.7,
            portions = listOf(p("sztuka", 170.0))
        ),
        Item(
            "Twist musli, Biedronka", 335.0, 11.0, 46.0, 11.0, fiberG = 4.0,
            portions = listOf(p("sztuka", 95.0))
        ),

        // ---- Przekąski z lady --------------------------------------------------------
        Item(
            "Zapiekanka z pieczarkami, Biedronka", 288.0, 10.1, 25.9, 15.3,
            portions = listOf(p("sztuka", 150.0))
        ),
        Item(
            "Hot dog z berlinką, Biedronka", 352.0, 9.6, 24.2, 23.8,
            portions = listOf(p("sztuka", 120.0))
        ),
        Item(
            "Tost z szynką i serem, Biedronka", 259.0, 9.2, 33.4, 9.4, fiberG = 2.0,
            portions = listOf(p("sztuka", 120.0))
        ),
        Item(
            "Mini pizza z salami, Biedronka", 272.0, 10.31, 35.3, 9.02,
            portions = listOf(p("sztuka", 130.0))
        ),
        Item(
            "Chleb z pomidorami i oliwkami, Biedronka", 245.0, 8.7, 44.0, 3.2, fiberG = 2.9,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "BIO chleb żytni z płatkami, Biedronka", 234.0, 4.7, 49.0, 0.8, fiberG = 6.1,
            portions = listOf(p("kromka", 40.0))
        ),
        Item(
            "Ciastko Duet truskawkowy, Biedronka", 359.0, 5.3, 40.0, 20.0, fiberG = 1.6,
            portions = listOf(p("sztuka", 94.0))
        ),
        Item(
            "Pinsa capriciosa, Biedronka", 235.0, 9.0, 32.0, 6.1,
            portions = listOf(p("sztuka", 150.0))
        ),
        Item(
            "Przekąska a'la cheeseburger, Biedronka", 243.0, 9.1, 26.9, 10.6, fiberG = 1.8,
            portions = listOf(p("sztuka", 125.0))
        ),
        Item(
            "Flauto z pomidorami i mozzarellą, Biedronka", 201.0, 8.4, 32.0, 4.4,
            portions = listOf(p("sztuka", 120.0))
        ),
        Item(
            "Borek ze szpinakiem i serem, Biedronka", 324.0, 6.2, 34.0, 18.0, fiberG = 2.1,
            portions = listOf(p("sztuka", 75.0))
        ),

        // ====================================================================
        // SIECI RESTAURACJI
        //
        // Jedyny dzial, w ktorym liczby nie sa konwencja, tylko konkretna
        // wartoscia opublikowana przez producenta. Dlatego pochodza wylacznie
        // z oficjalnych tabel wartosci odzywczych, a nie z baz
        // spolecznosciowych - te podawaly dla tego samego Twistera raz 579
        // kcal za porcje, raz 239 kcal za 100 g, co roznilo sie o 15 %.
        //
        // Zrodla:
        // - McDonald's Polska, tabela wartosci odzywczych z 8.11.2023,
        //   mcdonalds.pl/tabela-wartosci-odzywczych
        // - KFC Polska, tabela z 29.09.2026, kfc.pl/kcal-wartosci-odzywcze
        //
        // DWIE RZECZY, KTORE TRZEBA WIEDZIEC:
        //
        // 1. Tabela McDonald's ma ponad dwa lata. Receptury i gramatury sie
        //    zmieniaja, wiec przy produkcie jedzonym czesto warto raz
        //    sprawdzic liczbe w aplikacji sieci i poprawic wlasnym produktem.
        //
        // 2. Bialko jest WYLICZONE z bilansu energii, nie przepisane.
        //    Kolumna bialka w obu PDF-ach jest przy ekstrakcji przesunieta
        //    wzgledem reszty tabeli i nie da sie jej przypisac do wiersza
        //    bez zgadywania. Pozostale kolumny - gramatura porcji, kalorie,
        //    tluszcz, weglowodany, blonnik - sa odczytane wprost.
        //    Sprawdzenie metody: dla Big Maca wychodzi 27 g, czyli dokladnie
        //    tyle, ile podaja zrodla niezalezne.
        //
        //    Konsekwencja dla testu: sprawdzian zgodnosci kalorii ze skladem
        //    jest dla tych wierszy spelniony z definicji i nic nie dowodzi.
        //    Weryfikuja je gramatura porcji i pozostale kolumny.
        // ====================================================================

        // ---- McDonald's ------------------------------------------------------
        Item(
            "Hamburger, McDonald's", 243.0, 12.7, 27.0, 8.9, fiberG = 2.1,
            portions = listOf(p("sztuka", 106.0))
        ),
        Item(
            "Cheeseburger, McDonald's", 254.0, 12.8, 25.0, 11.0, fiberG = 1.9,
            portions = listOf(p("sztuka", 120.0))
        ),
        Item(
            "Big Mac, McDonald's", 231.0, 11.9, 18.0, 12.0, fiberG = 1.7,
            portions = listOf(p("sztuka", 235.0))
        ),
        Item(
            "McRoyal, McDonald's", 256.0, 16.1, 18.0, 13.0, fiberG = 1.4,
            portions = listOf(p("sztuka", 214.0))
        ),
        Item(
            "WieśMac, McDonald's", 261.0, 13.7, 15.0, 16.0, fiberG = 1.2,
            portions = listOf(p("sztuka", 233.0))
        ),
        Item(
            "McChicken, McDonald's", 229.0, 11.3, 25.0, 9.0, fiberG = 1.4,
            portions = listOf(p("sztuka", 189.0))
        ),
        Item(
            "McDouble, McDonald's", 252.0, 14.1, 19.0, 13.0, fiberG = 1.4,
            portions = listOf(p("sztuka", 162.0))
        ),
        Item(
            "McNuggets, McDonald's", 241.0, 16.1, 19.0, 11.0, fiberG = 0.9,
            portions = listOf(p("6 sztuk", 109.0), p("9 sztuk", 163.0), p("4 sztuki", 73.0))
        ),
        Item(
            "Frytki małe, McDonald's", 287.0, 2.5, 36.0, 14.0, fiberG = 3.6,
            portions = listOf(p("porcja", 80.0))
        ),
        Item(
            "Frytki średnie, McDonald's", 287.0, 2.5, 36.0, 14.0, fiberG = 3.6,
            portions = listOf(p("porcja", 114.0))
        ),
        Item(
            "Frytki duże, McDonald's", 287.0, 2.5, 36.0, 14.0, fiberG = 3.6,
            portions = listOf(p("porcja", 150.0))
        ),

        // ---- KFC ---------------------------------------------------------------
        // Tabela KFC nie ma kolumny blonnika, stad brak danych zamiast zera.
        Item(
            "Hot Wings, KFC", 320.0, 18.8, 14.0, 21.0,
            portions = listOf(p("sztuka", 36.0), p("5 sztuk", 180.0))
        ),
        Item(
            "Hot & Spicy Strips, KFC", 288.0, 19.4, 9.9, 19.0,
            portions = listOf(p("sztuka", 32.0), p("3 sztuki", 96.0))
        ),
        Item(
            "Zinger Burger, KFC", 262.0, 15.3, 21.0, 13.0,
            portions = listOf(p("sztuka", 170.0))
        ),
        Item("Zinger Max, KFC", 246.0, 17.5, 17.0, 12.0, portions = listOf(p("sztuka", 240.0))),
        Item(
            "Zinger Ser i Bekon, KFC", 262.0, 16.3, 20.0, 13.0,
            portions = listOf(p("sztuka", 200.0))
        ),
        Item("Grander Burger, KFC", 252.0, 13.8, 20.0, 13.0, portions = listOf(p("sztuka", 300.0))),
        Item(
            "Grander Kentucky Gold, KFC", 263.0, 10.3, 24.0, 14.0,
            portions = listOf(p("sztuka", 320.0))
        ),
        Item("Longer, KFC", 244.0, 12.6, 34.0, 6.4, portions = listOf(p("sztuka", 127.0))),
        Item("Cheeseburger, KFC", 247.0, 11.8, 32.0, 8.0, portions = listOf(p("sztuka", 125.0))),
        Item("Twister, KFC", 256.0, 9.8, 25.0, 13.0, portions = listOf(p("sztuka", 226.0))),
        Item(
            "Ryż i Strips Sweet Chilli, KFC", 268.0, 7.5, 46.0, 6.0,
            portions = listOf(p("standard", 260.0), p("grande", 330.0))
        )
    )

    /**
     * Dopisuje brakujące pozycje zestawu.
     *
     * Wywoływane i z [Db.onCreate], i z migracji do wersji 16, a później
     * z każdej migracji, która ten zestaw rozszerza. Dlatego **idempotentne
     * i rozszerzalne**: pomija pozycje, które już są, więc dodanie dziesięciu
     * nowych produktów w kolejnej wersji dociągnie wyłącznie te dziesięć.
     *
     * Rozpoznawanie po `name_key`, nie po nazwie: inaczej poprawienie
     * wielkości litery w nazwie zakładało drugi wiersz tego samego produktu.
     *
     * Produkty użytkownika o tej samej nazwie zostają nietknięte - porównujemy
     * tylko w obrębie `source = SEED`. Jeśli sam wpisałeś „Masło", dostaniesz
     * dwa wpisy i to jest poprawne: Twój ma wartości z Twojego opakowania.
     */
    fun populate(db: SQLiteDatabase) {
        val existing = HashSet<String>()
        db.rawQuery(
            "SELECT name_key FROM food WHERE source = ?",
            arrayOf(FoodSource.SEED.name)
        ).use { c ->
            while (c.moveToNext()) existing += c.getString(0)
        }

        for (item in ITEMS) {
            val key = searchKey(item.name)
            if (key in existing) continue

            val id = db.insert("food", null, ContentValues().apply {
                putNull("barcode")
                put("name", item.name)
                put("name_key", key)
                putNull("brand")
                put("kcal_100", item.kcal)
                put("protein_100", item.proteinG)
                put("carbs_100", item.carbsG)
                put("fat_100", item.fatG)
                if (item.fiberG != null) put("fiber_100", item.fiberG) else putNull("fiber_100")
                putNull("sugars_100")
                putNull("saturated_100")
                putNull("salt_100")
                putNull("serving_g")
                putNull("package_g")
                put("source", FoodSource.SEED.name)
                put("updated_at", 0)
            })
            if (id <= 0) continue

            item.portions.forEachIndexed { i, portion ->
                db.insert("food_portion", null, ContentValues().apply {
                    put("food_id", id)
                    put("label", portion.label)
                    put("grams", portion.grams)
                    put("position", i)
                })
            }
        }
    }
}
