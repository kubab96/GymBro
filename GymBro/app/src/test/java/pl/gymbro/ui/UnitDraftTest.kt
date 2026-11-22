package pl.gymbro.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Equipment
import pl.gymbro.data.Exercise
import pl.gymbro.data.Plan
import pl.gymbro.data.PlanItem
import pl.gymbro.data.ProgressMode
import pl.gymbro.data.StoredAdvice

/**
 * Szkic jednostki - to, co dzieje się przed naciśnięciem Zapisz.
 * Sam zapis wymaga bazy na Androidzie, więc tu tylko reguły szkicu.
 */
class UnitDraftTest {

    private val plan = Plan(id = 7, name = "A", position = 0, programId = 1)

    private fun item(id: Long, kg: Double = 60.0, advice: StoredAdvice? = null) = PlanItem(
        id = id, planId = 7, exerciseId = id, position = id.toInt(), targetKg = kg,
        sets = 3, repsFrom = 8, repsTo = 12, restSec = 120, exerciseName = "ćw$id",
        equipment = Equipment.BARBELL, advice = advice
    )

    private fun draft(vararg items: PlanItem) = UnitDraft(plan).apply { load(items.toList(), plan) }

    @Test
    fun `swiezo wczytany szkic nie jest zmieniony`() {
        assertFalse(draft(item(1), item(2)).dirty)
    }

    @Test
    fun `zmiana serii zapala Zapisz, a porzucenie gasi`() {
        val d = draft(item(1))
        d.update(d.items[0].copy(sets = 4))
        assertTrue(d.dirty)
        d.discard()
        assertFalse(d.dirty)
        assertEquals(3, d.items[0].sets)
    }

    @Test
    fun `przestawienie zmienia kolejnosc na liscie`() {
        val d = draft(item(1), item(2), item(3))
        d.move(d.items[2], up = true)
        assertEquals(listOf(1L, 3L, 2L), d.items.map { it.id })
        // Pierwszego wyżej się nie da - nic się nie dzieje.
        d.move(d.items[0], up = true)
        assertEquals(listOf(1L, 3L, 2L), d.items.map { it.id })
    }

    @Test
    fun `zastosowana propozycja zmienia ciezar i gasnie w szkicu`() {
        val d = draft(item(1, advice = StoredAdvice(kind = "WEIGHT_UP", weightKg = 62.5)))
        assertEquals(1, d.pendingAdvice)
        d.accept(d.items[0])
        assertEquals(62.5, d.items[0].targetKg, 0.001)
        assertNull(d.items[0].advice)
        assertEquals(0, d.pendingAdvice)
        assertTrue(d.dirty)
    }

    @Test
    fun `pominieta propozycja nie zmienia liczb`() {
        val d = draft(item(1, advice = StoredAdvice(kind = "WEIGHT_UP", weightKg = 62.5)))
        d.skip(d.items[0])
        assertEquals(60.0, d.items[0].targetKg, 0.001)
        assertNull(d.items[0].advice)
    }

    @Test
    fun `wylaczenie dokladania ciezaru zdejmuje propozycje wiekszego ciezaru`() {
        val d = draft(item(1, advice = StoredAdvice(kind = "WEIGHT_UP", weightKg = 62.5)))
        d.update(d.items[0].copy(progress = ProgressMode.NO_WEIGHT))
        assertNull(d.items[0].advice)
    }

    @Test
    fun `nowe cwiczenia dostaja ujemne numery i domyslny zakres`() {
        val d = draft(item(1))
        d.add(listOf(Exercise(id = 50, name = "Wiosłowanie", equipment = Equipment.BARBELL, muscleGroup = "Plecy")))
        val nowe = d.items.last()
        assertTrue(nowe.id < 0)
        assertEquals("8-12", nowe.repRange)
        assertTrue(d.dirty)
    }

    @Test
    fun `szkic ze zmianami nie daje sie nadpisac wczytaniem z bazy`() {
        val d = draft(item(1))
        d.update(d.items[0].copy(sets = 5))
        d.load(listOf(item(1)), plan)
        assertEquals(5, d.items[0].sets)
    }
}
