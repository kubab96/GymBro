package pl.gymbro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lista sprzętu z Profilu - [Gear] i to, co z niej wynika w [UserProfile].
 *
 * Najważniejsze jest tu to, czego nie widać na ekranie: profil zapisany
 * jeszcze jako sześć pól wyboru ma się przeczytać bez migracji, a pusta
 * lista nigdy nie może schować wszystkich ćwiczeń.
 */
class GearTest {

    /** Dokładnie to leżało w bazie na telefonie w dniu zmiany. */
    private val dawnyZapis = "BARBELL,DUMBBELL_PAIR,DUMBBELL_SINGLE,CABLE,MACHINE,BODYWEIGHT"

    @Test
    fun `dawny zapis szesciu pol czyta sie bez migracji`() {
        assertEquals(
            setOf(
                Gear.BARBELL, Gear.DUMBBELL_PAIR, Gear.DUMBBELL_SINGLE,
                Gear.CABLE, Gear.MACHINE
            ),
            Gear.parse(dawnyZapis)
        )
    }

    @Test
    fun `dawny zapis daje te same rodzaje cwiczen co przedtem`() {
        val profil = UserProfile(customGear = true, ownedGear = Gear.parse(dawnyZapis))
        assertEquals(
            setOf(
                Equipment.BARBELL, Equipment.DUMBBELL_PAIR, Equipment.DUMBBELL_SINGLE,
                Equipment.CABLE, Equipment.MACHINE, Equipment.BODYWEIGHT
            ),
            profil.ownedEquipment
        )
    }

    /**
     * Kettlebell i gumy doszły później. Kto ustawił listę sprzętu wcześniej,
     * ich nie zaznaczył - więc nie ma ich też w jego filtrze ćwiczeń.
     */
    @Test
    fun `nowe rodzaje sprzetu nie wchodza do starej listy same`() {
        val profil = UserProfile(customGear = true, ownedGear = Gear.parse(dawnyZapis))
        assertFalse(profil.canDo(Equipment.KETTLEBELL))
        assertFalse(profil.canDo(Equipment.BAND))
    }

    @Test
    fun `kettlebell i gumy z listy odblokowuja swoje cwiczenia`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.KETTLEBELL, Gear.BANDS))
        assertTrue(profil.canDo(Equipment.KETTLEBELL))
        assertTrue(profil.canDo(Equipment.BAND))
        assertFalse(profil.canDo(Equipment.DUMBBELL_SINGLE))
    }

    @Test
    fun `zapis i odczyt sa odwrotnoscia`() {
        val zestaw = setOf(Gear.KETTLEBELL, Gear.BARBELL, Gear.BENCH)
        assertEquals(zestaw, Gear.parse(Gear.join(zestaw)))
    }

    @Test
    fun `pusty zbior zapisuje sie jako brak wpisu`() {
        assertNull(Gear.join(emptySet()))
        assertTrue(Gear.parse(null).isEmpty())
        assertTrue(Gear.parse("").isEmpty())
    }

    @Test
    fun `nieznana nazwa przepada, reszta zostaje`() {
        assertEquals(setOf(Gear.CABLE), Gear.parse("CABLE,COS_Z_PRZYSZLOSCI"))
    }

    @Test
    fun `wylaczone dostosowanie znaczy pelna silownie mimo listy`() {
        val profil = UserProfile(customGear = false, ownedGear = setOf(Gear.DUMBBELL_PAIR))
        assertNull(profil.ownedEquipment)
        assertTrue(profil.canDo(Equipment.BARBELL))
    }

    /**
     * Włączenie przełącznika, zanim cokolwiek się doda, nie może schować
     * wszystkich ćwiczeń - inaczej pierwsze dotknięcie wyglądałoby na awarię.
     */
    @Test
    fun `dostosowanie bez sprzetu niczego nie ogranicza`() {
        val profil = UserProfile(customGear = true)
        assertNull(profil.ownedEquipment)
        assertTrue(profil.canDo(Equipment.MACHINE))
    }

    @Test
    fun `same hantle zamykaja sztange, ale nie mase ciala`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.DUMBBELL_PAIR))
        assertFalse(profil.canDo(Equipment.BARBELL))
        assertFalse(profil.canDo(Equipment.CABLE))
        assertTrue(profil.canDo(Equipment.DUMBBELL_PAIR))
        // Kto ma parę, ten ma i jeden.
        assertTrue(profil.canDo(Equipment.DUMBBELL_SINGLE))
        assertTrue(profil.canDo(Equipment.BODYWEIGHT))
    }

    @Test
    fun `sama lawka zostawia tylko mase ciala`() {
        val profil = UserProfile(customGear = true, ownedGear = setOf(Gear.BENCH))
        assertEquals(setOf(Equipment.BODYWEIGHT), profil.ownedEquipment)
    }

    /** Dwie pozycje o tej samej nazwie byłyby na liście nie do odróżnienia. */
    @Test
    fun `kazda pozycja ma wlasna etykiete`() {
        assertEquals(Gear.entries.size, Gear.entries.map { it.label }.toSet().size)
    }
}
