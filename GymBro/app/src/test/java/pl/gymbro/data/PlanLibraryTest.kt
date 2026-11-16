package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.PlanLibrary.Level
import pl.gymbro.gym.MuscleMap

/**
 * Sprawdzenie bazy gotowych planów i biblioteki ćwiczeń.
 *
 * Jak przy zestawie produktów: to test **danych wpisanych ręcznie**, nie
 * logiki. Kilkaset wierszy z nazwą i czterema liczbami czyta się uważnie
 * raz, a literówka w nazwie ćwiczenia nie wywraca niczego - po prostu po
 * cichu gubi pozycję z planu, który ktoś właśnie dodał.
 */
class PlanLibraryTest {

    private val plany = PlanLibrary.PROGRAMS
    private val wszystkieJednostki = plany.flatMap { it.units }
    private val wszystkiePozycje = wszystkieJednostki.flatMap { it.items }

    private fun sprzet(item: PlanLibrary.Item) = Seed.libraryEntry(item.exercise)?.second

    @Test
    fun `kazde cwiczenie z gotowego planu jest w bibliotece`() {
        wszystkiePozycje.forEach { item ->
            assertNotNull("brak w bibliotece: ${item.exercise}", sprzet(item))
        }
    }

    /** Baza ma być duża - spadek poniżej znaczyłby zgubiony plik katalogu. */
    @Test
    fun `baza ma dziesiatki planow i setki cwiczen`() {
        assertTrue("planów: ${plany.size}", plany.size >= 30)
        assertTrue("jednostek: ${wszystkieJednostki.size}", wszystkieJednostki.size >= 90)
        assertTrue("ćwiczeń: ${Seed.LIBRARY.size}", Seed.LIBRARY.size >= 250)
    }

    @Test
    fun `nazwy planow sa rozne, a jednostek - rozne w obrebie planu`() {
        assertEquals(plany.size, plany.map { it.name }.toSet().size)
        plany.forEach { p ->
            val jednostki = p.units.map { it.name }
            assertEquals("plan ${p.name}", jednostki.size, jednostki.toSet().size)
        }
    }

    @Test
    fun `cwiczenie nie powtarza sie w jednej jednostce`() {
        wszystkieJednostki.forEach { u ->
            val nazwy = u.items.map { it.exercise }
            assertEquals("jednostka ${u.name}", nazwy.size, nazwy.toSet().size)
        }
    }

    /**
     * Granice z docs/progresja.md przy ciężarach: nie wyżej niż 15, bo powyżej
     * doradca nie rozszerza zakresu. Masa ciała i gumy do 25 - nie ma tam
     * czego dołożyć, więc zakres jest jedyną drogą w górę przed trudniejszym
     * wariantem.
     */
    @Test
    fun `serie, zakresy i przerwy mieszcza sie w rozsadnych granicach`() {
        wszystkiePozycje.forEach { i ->
            val bezCiezaru = sprzet(i) == Equipment.BODYWEIGHT || sprzet(i) == Equipment.BAND
            val sufit = if (bezCiezaru) 25 else 15
            val opis = "${i.exercise} ${i.sets}×${i.repsFrom}-${i.repsTo}"
            assertTrue(opis, i.sets in 2..5)
            assertTrue(opis, i.repsFrom >= 3)
            assertTrue(opis, i.repsTo in (i.repsFrom + 2)..sufit)
            assertTrue(opis, i.restSec in listOf(60, 90, 120, 180))
        }
    }

    @Test
    fun `jednostka ma od czterech do osmiu cwiczen`() {
        wszystkieJednostki.forEach { u ->
            assertTrue("jednostka ${u.name}", u.items.size in 4..8)
        }
    }

    @Test
    fun `dni w tygodniu sa od jednego do szesciu`() {
        plany.forEach { p ->
            assertTrue(p.name, p.days.isNotEmpty() && p.days.all { it in 1..6 })
        }
    }

    // ---- Pokrycie: żaden filtr nie może zwrócić pustki ----

    @Test
    fun `kazda liczba dni od dwoch do szesciu ma swoje plany`() {
        (2..6).forEach { d -> assertTrue("$d dni", PlanLibrary.search("", days = d).isNotEmpty()) }
    }

    @Test
    fun `kazdy poziom ma swoje plany`() {
        Level.entries.forEach { assertTrue(it.label, PlanLibrary.search("", level = it).isNotEmpty()) }
    }

    private fun tylkoMasaCiala(p: PlanLibrary.ProgramTemplate) =
        PlanLibrary.equipmentOf(p.units).all { it == Equipment.BODYWEIGHT }

    /** Wprost z prośby: masa ciała nie może być dodatkiem na końcu listy. */
    @Test
    fun `planow na sama mase ciala jest co najmniej osiem, na kazdym poziomie`() {
        val bw = plany.filter(::tylkoMasaCiala)
        assertTrue("planów: ${bw.size}", bw.size >= 8)
        Level.entries.forEach { lvl -> assertTrue(lvl.label, bw.any { it.level == lvl }) }
    }

    // ---- Dwie ścieżki doboru sprzętu ----
    //
    // Zgłoszone z telefonu dwa razy: najpierw „w domu" chowało plany ze
    // sztangą, potem - po poprawce - plany z wyciągiem, który w domu też
    // bywa. Miejsca nie da się wyczytać ze sprzętu, więc go nie ma. Jest
    // ścieżka prosta (brak listy sprzętu, widać wszystko) i dokładna (lista
    // z Profilu zawęża, ale da się pokazać wszystko).

    /** Ktoś z domową siłownią: sztanga ze stojakami, hantle, ławki, drążek, poręcze. */
    private val domowa = UserProfile(
        customGear = true,
        ownedGear = setOf(
            Gear.BARBELL, Gear.DUMBBELL_PAIR, Gear.RACK, Gear.BENCH, Gear.ADJ_BENCH,
            Gear.PULL_UP_BAR, Gear.DIP_BARS
        )
    )

    /** To samo, co sprawdza filtr w aplikacji: każde ćwiczenie z osobna. */
    private fun wykonalne(profil: UserProfile, units: List<PlanLibrary.UnitTemplate>) =
        units.flatMap { it.items }.all { profil.canDoExercise(it.exercise, sprzet(it)!!) }

    @Test
    fun `bez listy sprzetu nic nie jest odsiewane`() {
        val prosty = UserProfile()
        assertEquals(null, prosty.ownedEquipment)
        assertEquals(plany.size, PlanLibrary.search("").size)
        assertEquals(
            wszystkieJednostki.size,
            PlanLibrary.searchUnits("").size
        )
    }

    @Test
    fun `z lista sprzetu zostaja tylko plany do zrobienia na nim`() {
        val wynik = PlanLibrary.search("", fits = domowa::canDoExercise)
        assertTrue(wynik.isNotEmpty())
        wynik.forEach { p ->
            assertTrue(p.name, wykonalne(domowa, p.units))
        }
        assertTrue(wynik.size < plany.size)
    }

    @Test
    fun `domowa silownia ze sztanga dostaje plany ze sztanga, takze dla poczatkujacych`() {
        val zeSztanga = PlanLibrary.search("", fits = domowa::canDoExercise)
            .filter { Equipment.BARBELL in PlanLibrary.equipmentOf(it.units) }
        assertTrue(zeSztanga.map { it.name }.toString(), zeSztanga.size >= 4)
        assertTrue(zeSztanga.any { it.level == Level.BEGINNER })
    }

    /** Wyciąg w domu ma się liczyć tak samo jak każdy inny sprzęt z listy. */
    @Test
    fun `wyciag z listy odblokowuje plany z wyciagiem`() {
        val zWyciagiem = domowa.copy(ownedGear = domowa.ownedGear + Gear.CABLE)
        val bez = PlanLibrary.search("", fits = domowa::canDoExercise)
        val z = PlanLibrary.search("", fits = zWyciagiem::canDoExercise)
        assertTrue(z.size > bez.size)
        assertTrue(z.any { Equipment.CABLE in PlanLibrary.equipmentOf(it.units) })
    }

    @Test
    fun `jednostka liczy sie po swoim sprzecie, nie calego planu`() {
        // „Push / Pull - 4 dni" ma wyciąg w Pull, ale nie w każdej jednostce.
        val jednostki = PlanLibrary.searchUnits("", fits = domowa::canDoExercise)
        jednostki.forEach { (_, u) ->
            assertTrue(u.name, wykonalne(domowa, listOf(u)))
        }
        val zPlanowNiedostepnych = jednostki.filter { (p, _) ->
            !wykonalne(domowa, p.units)
        }
        assertTrue(zPlanowNiedostepnych.isNotEmpty())
    }

    @Test
    fun `kalistenika wymaga wylacznie masy ciala`() {
        plany.filter { it.name.startsWith("Kalistenika") }.forEach {
            assertTrue(it.name, tylkoMasaCiala(it))
        }
    }

    @Test
    fun `opis sprzetu laczy hantle w jedno slowo`() {
        val dom = plany.single { it.name == "Hantle w domu" }
        assertEquals("hantle, masa ciała", PlanLibrary.gearSummary(dom.units))
    }

    @Test
    fun `plan ze sztanga ostrzega kogos, kto ma same hantle`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.DUMBBELL_PAIR))
        val fbw = plany.first()
        assertTrue(PlanLibrary.equipmentOf(fbw.units).any { !profil.canDo(it) })
        val dom = plany.single { it.name == "Hantle w domu" }
        assertTrue(PlanLibrary.equipmentOf(dom.units).all { profil.canDo(it) })
    }

    // ---- Sprzęt ćwiczenia, nie tylko rodzaj ----
    //
    // Sam rodzaj (sztanga, maszyna) nie wystarcza: sztanga bez stojaków nie
    // daje przysiadu, a „maszyna" to kilkanaście różnych urządzeń. Kto
    // zaznaczył samą suwnicę, nie powinien dostać planu z wyciskaniem na
    // maszynie.

    @Test
    fun `kazde wymaganie dotyczy cwiczenia z biblioteki`() {
        Seed.NEEDS.keys.forEach { assertNotNull(it, Seed.libraryEntry(it)) }
    }

    @Test
    fun `kazda grupa wymagan ma co najmniej jedna pozycje`() {
        Seed.NEEDS.forEach { (nazwa, grupy) ->
            assertTrue(nazwa, grupy.isNotEmpty() && grupy.all { it.isNotEmpty() })
        }
    }

    /**
     * Każde ćwiczenie na maszynie mówi, na której - a „Komplet maszyn" je
     * zawsze zastępuje, bo kto go zaznaczył, ma pełną siłownię.
     */
    @Test
    fun `cwiczenie na maszynie ma swoja maszyne i komplet ja zastepuje`() {
        val komplet = setOf(Gear.MACHINE)
        Seed.LIBRARY.filter { it.second == Equipment.MACHINE }.forEach { (nazwa, _, _) ->
            assertTrue("brak wymagań: $nazwa", Seed.needsOf(nazwa).isNotEmpty())
            assertTrue("komplet nie wystarcza: $nazwa", Seed.needsMet(nazwa, komplet))
        }
    }

    @Test
    fun `sztanga bez stojakow nie daje przysiadu, ze stojakami daje`() {
        val bezStojakow = UserProfile(customGear = true, ownedGear = setOf(Gear.BARBELL))
        assertTrue(!bezStojakow.canDoExercise("Przysiad ze sztangą", Equipment.BARBELL))
        assertTrue(bezStojakow.canDoExercise("Martwy ciąg", Equipment.BARBELL))
        val zeStojakami = bezStojakow.copy(ownedGear = setOf(Gear.BARBELL, Gear.RACK))
        assertTrue(zeStojakami.canDoExercise("Przysiad ze sztangą", Equipment.BARBELL))
    }

    /** Lista sprzed stojaków i ławek: sztanga plus komplet maszyn to pełna siłownia. */
    @Test
    fun `komplet maszyn obejmuje lawki, stojaki i drazek`() {
        val stara = UserProfile(
            customGear = true,
            ownedGear = setOf(Gear.BARBELL, Gear.DUMBBELL_PAIR, Gear.CABLE, Gear.MACHINE)
        )
        assertTrue(stara.canDoExercise("Przysiad ze sztangą", Equipment.BARBELL))
        assertTrue(stara.canDoExercise("Wyciskanie sztangi leżąc", Equipment.BARBELL))
        assertTrue(stara.canDoExercise("Podciąganie na drążku", Equipment.BODYWEIGHT))
        assertTrue(wykonalne(stara, plany.first().units))
    }

    @Test
    fun `sama suwnica nie odblokowuje innych maszyn`() {
        val suwnica = UserProfile(customGear = true, ownedGear = setOf(Gear.LEG_PRESS))
        assertTrue(suwnica.canDoExercise("Suwnica", Equipment.MACHINE))
        assertTrue(!suwnica.canDoExercise("Wyciskanie na maszynie", Equipment.MACHINE))
    }

    @Test
    fun `bez listy sprzetu wymagania nie maja znaczenia`() {
        assertTrue(UserProfile().canDoExercise("Przysiad ze sztangą", Equipment.BARBELL))
        assertTrue(PlanLibrary.missingFor(plany.first().units, UserProfile()).isEmpty())
    }

    @Test
    fun `brak podaje konkretne urzadzenie, nie komplet maszyn`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.BARBELL, Gear.RACK))
        val brak = PlanLibrary.missingFor(
            listOf(unit("X", "", aux("Suwnica", 3, 8, 12))),
            profil
        )
        assertTrue(brak.toString(), brak.any { it.contains("suwnica") })
        assertTrue(brak.toString(), brak.none { it.contains("komplet") })
    }

    @Test
    fun `gotowe zestawy sprzetu sa niepuste i rozne`() {
        assertTrue(Gear.PRESETS.isNotEmpty())
        Gear.PRESETS.forEach { (nazwa, zestaw) -> assertTrue(nazwa, zestaw.isNotEmpty()) }
        assertEquals(Gear.PRESETS.size, Gear.PRESETS.map { it.second }.toSet().size)
    }

    /** Każdy zestaw z Profilu ma dostać jakiś plan - inaczej kreator zaczyna od pustki. */
    @Test
    fun `kazdy gotowy zestaw sprzetu dostaje plany`() {
        Gear.PRESETS.forEach { (nazwa, zestaw) ->
            val profil = UserProfile(customGear = true, ownedGear = zestaw)
            val wynik = PlanLibrary.search("", fits = profil::canDoExercise)
            assertTrue("$nazwa: ${wynik.size}", wynik.size >= 3)
        }
    }

    /**
     * Plany drugiej partii pisane pod konkretny zestaw muszą na nim przejść -
     * inaczej użytkownik z tym sprzętem nigdy by ich nie zobaczył.
     */
    @Test
    fun `plany pod konkretny sprzet przechodza na tym sprzecie`() {
        fun na(nazwa: String, vararg gear: Gear) {
            val p = plany.single { it.name == nazwa }
            val profil = UserProfile(customGear = true, ownedGear = gear.toSet())
            assertTrue("$nazwa: ${PlanLibrary.missingFor(p.units, profil)}", wykonalne(profil, p.units))
        }
        na("Sztanga w garażu - 3 dni", Gear.BARBELL, Gear.RACK, Gear.BENCH)
        na("Domowa siłownia z wyciągiem - 4 dni",
            Gear.BARBELL, Gear.DUMBBELL_PAIR, Gear.RACK, Gear.BENCH, Gear.ADJ_BENCH,
            Gear.PULL_UP_BAR, Gear.CABLE)
        na("Hantle - Push / Pull - 4 dni", Gear.DUMBBELL_PAIR, Gear.BENCH, Gear.ADJ_BENCH)
        na("Hantle i drążek - 3 dni", Gear.DUMBBELL_PAIR, Gear.PULL_UP_BAR)
        na("Kettlebell - 2 dni", Gear.KETTLEBELL)
        na("Gumy i drążek - 3 dni", Gear.BANDS, Gear.MINI_BANDS, Gear.PULL_UP_BAR)
        na("Bez sprzętu - 20 minut")
        na("Kalistenika z obciążeniem",
            Gear.PULL_UP_BAR, Gear.DIP_BARS, Gear.DIP_BELT, Gear.WEIGHTED_VEST,
            Gear.SUSPENSION, Gear.BOX)
        na("Paralletki i kółko - 3 dni", Gear.PARALLETTES, Gear.AB_WHEEL, Gear.PULL_UP_BAR)
    }

    // ---- Wyszukiwanie ----

    @Test
    fun `szukanie bez polskich znakow znajduje plany z nimi`() {
        val wynik = PlanLibrary.search("posladki").map { it.name }
        assertTrue(wynik.toString(), "Pośladki i nogi - 3 dni" in wynik)
    }

    @Test
    fun `szukanie obejmuje cwiczenia w planie, nie tylko nazwe`() {
        val wynik = PlanLibrary.search("wyjscie silowe").map { it.name }
        assertTrue(wynik.toString(), "Kalistenika - zaawansowany" in wynik)
        assertTrue(wynik.toString(), "FBW - 3 dni" !in wynik)
    }

    @Test
    fun `szukanie po sprzecie i bez sprzetu`() {
        val kb = PlanLibrary.search("kettlebell")
        assertTrue(kb.isNotEmpty())
        assertTrue(kb.all { Equipment.KETTLEBELL in PlanLibrary.equipmentOf(it.units) })
        val bez = PlanLibrary.search("bez sprzetu")
        assertTrue(bez.size >= plany.count(::tylkoMasaCiala))
    }

    @Test
    fun `filtr mojego sprzetu odsiewa plany z kettlebellem`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.DUMBBELL_PAIR))
        val wynik = PlanLibrary.search("kettlebell", fits = profil::canDoExercise)
        assertTrue(wynik.map { it.name }.toString(), wynik.isEmpty())
    }

    @Test
    fun `jednostki znajduja sie po cwiczeniu i niosa swoj plan`() {
        val wynik = PlanLibrary.searchUnits("pistolet")
        assertTrue(wynik.isNotEmpty())
        assertTrue(wynik.all { (_, u) -> u.items.any { it.exercise == "Przysiad pistolet" } })
    }

    // ---- Kreator: poluzowanie zamiast pustego ekranu ----

    @Test
    fun `kreator niczego nie luzuje, gdy cos pasuje`() {
        val m = PlanLibrary.match(3, Level.BEGINNER, null)
        assertTrue(m.plans.isNotEmpty())
        assertTrue(!m.droppedLevel && !m.droppedDays)
        assertTrue(m.plans.all { 3 in it.days && it.level == Level.BEGINNER })
    }

    @Test
    fun `odpowiedzi obojetne nie sa poluzowaniem`() {
        val m = PlanLibrary.match(null, null, null)
        assertEquals(plany.size, m.plans.size)
        assertTrue(!m.droppedLevel && !m.droppedDays)
    }

    /**
     * Pięć dni to w bazie same plany dla zaawansowanych. Liczba dni mówi, jak
     * ułożyć tydzień, i liczy się bardziej niż poziom - więc schodzi poziom.
     */
    @Test
    fun `piec dni dla poczatkujacych zdejmuje poziom, nie dni`() {
        val m = PlanLibrary.match(5, Level.BEGINNER, null)
        assertTrue(m.plans.isNotEmpty())
        assertTrue(m.droppedLevel && !m.droppedDays)
        assertTrue(m.plans.all { 5 in it.days })
    }

    @Test
    fun `sprzetu kreator nie luzuje nigdy`() {
        val tylkoGumy = UserProfile(customGear = true, ownedGear = setOf(Gear.BANDS))
        val m = PlanLibrary.match(6, Level.ADVANCED, tylkoGumy::canDoExercise)
        m.plans.forEach { p ->
            assertTrue(p.name, wykonalne(tylkoGumy, p.units))
        }
    }

    // ---- Nazwa bez powtórki ----

    @Test
    fun `wolna nazwa zostaje bez zmian`() {
        assertEquals("PPL", PlanLibrary.uniqueName("PPL", listOf("FBW")))
    }

    @Test
    fun `zajeta nazwa dostaje numer od dwojki`() {
        assertEquals("PPL 2", PlanLibrary.uniqueName("PPL", listOf("FBW", "PPL")))
        assertEquals("PPL 3", PlanLibrary.uniqueName("PPL", listOf("PPL", "PPL 2")))
    }

    @Test
    fun `wielkosc liter i spacje nie robia z tej samej nazwy dwoch`() {
        assertEquals("PPL 2", PlanLibrary.uniqueName("PPL", listOf(" ppl ")))
    }

    // ---- Biblioteka ćwiczeń ----

    /** Nazwa ma w bazie indeks unikalny - powtórka wpadałaby cicho w CONFLICT_IGNORE. */
    @Test
    fun `nazwy w bibliotece cwiczen sa rozne`() {
        val nazwy = Seed.LIBRARY.map { it.first }
        assertEquals(
            nazwy.groupBy { it }.filter { it.value.size > 1 }.keys.toString(),
            nazwy.size, nazwy.toSet().size
        )
    }

    @Test
    fun `kazde cwiczenie z biblioteki ma partie, ktora swieci na sylwetce`() {
        Seed.LIBRARY.forEach { (nazwa, _, partia) ->
            assertTrue("$nazwa: $partia", partia in MuscleMap.GROUPS)
        }
    }

    @Test
    fun `kazdy rodzaj sprzetu ma cwiczenia w bibliotece`() {
        val sprzety = Seed.LIBRARY.map { it.second }.toSet()
        assertEquals(Equipment.entries.toSet(), sprzety)
    }

    @Test
    fun `masy ciala jest w bibliotece najwiecej`() {
        val ile = Seed.LIBRARY.groupingBy { it.second }.eachCount()
        assertEquals(Equipment.BODYWEIGHT, ile.maxByOrNull { it.value }?.key)
    }
}
