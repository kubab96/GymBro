package pl.gymbro.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.gymbro.data.Activity
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.SessionSummary

/**
 * Historia siły dla jednego planu - [Timeline.build] z `programId`.
 *
 * Zgłoszone przy dwóch planach naraz: treningi z PPL stały na liście pod FBW.
 * Samo odsianie po planie to jedna linijka; pułapką jest zegarek - jego
 * połowa treningu z drugiego planu nie może wrócić na listę jako osobny wpis.
 */
class TimelineTest {

    private val FBW = 1L
    private val PPL = 2L

    private fun trening(id: Long, plan: Long?, at: Long, watch: ExternalSession? = null) =
        SessionSummary(
            sessionId = id, planName = "x", startedAt = at, endedAt = at + 3_600_000,
            workingSets = 10, tonnage = 1000.0, watch = watch, programId = plan
        )

    private fun zegarek(id: String, at: Long, type: String = "Trening siłowy") =
        ExternalSession(
            externalId = id, source = "zegarek", type = type,
            startedAt = at, endedAt = at + 3_600_000
        )

    private fun ids(lista: List<Activity>) =
        lista.filterIsInstance<Activity.Strength>().map { it.summary.sessionId }

    @Test
    fun `bez planu historia pokazuje wszystko`() {
        val split = Timeline.build(
            strength = listOf(trening(1, FBW, 100), trening(2, PPL, 200)),
            watch = emptyList()
        )
        assertEquals(listOf(2L, 1L), ids(split.strength))
    }

    @Test
    fun `historia jednego planu nie pokazuje treningow drugiego`() {
        val split = Timeline.build(
            strength = listOf(trening(1, FBW, 100), trening(2, PPL, 200), trening(3, FBW, 300)),
            watch = emptyList(),
            programId = FBW
        )
        assertEquals(listOf(3L, 1L), ids(split.strength))
    }

    /**
     * Skasowana jednostka zostawia trening bez planu. Schowany przy każdym
     * planie nie dałby się już otworzyć, poprawić ani usunąć.
     */
    @Test
    fun `trening bez planu zostaje przy kazdym planie`() {
        val split = Timeline.build(
            strength = listOf(trening(1, null, 100), trening(2, PPL, 200)),
            watch = emptyList(),
            programId = FBW
        )
        assertEquals(listOf(1L), ids(split.strength))
    }

    @Test
    fun `zegarkowa polowa treningu z innego planu nie wraca jako osobny wpis`() {
        val w = zegarek("w-ppl", 200)
        val split = Timeline.build(
            strength = listOf(trening(1, FBW, 100), trening(2, PPL, 200, watch = w)),
            watch = listOf(w),
            programId = FBW
        )
        assertEquals(listOf(1L), ids(split.strength))
        assertTrue(split.strength.none { it is Activity.Watch })
    }

    /**
     * Zobaczone na telefonie: pod świeżo założonym PPL stało szesnaście
     * treningów „z zegarka, bez zapisanych serii" sprzed miesięcy.
     */
    @Test
    fun `sila z zegarka bez zapisanych serii nie udaje historii planu`() {
        val split = Timeline.build(
            strength = listOf(trening(1, PPL, 100)),
            watch = listOf(zegarek("luzny", 500)),
            programId = FBW
        )
        assertTrue(split.strength.isEmpty())
        // Nie ginie - zegarek ją nagrał, więc jest do obejrzenia w „Inne".
        assertEquals(1, split.other.size)
    }

    @Test
    fun `bez wyboru planu sila z zegarka stoi obok zapisow jak dotad`() {
        val split = Timeline.build(
            strength = listOf(trening(1, PPL, 100)),
            watch = listOf(zegarek("luzny", 500))
        )
        assertEquals(2, split.strength.size)
        assertTrue(split.other.isEmpty())
    }

    @Test
    fun `wybor planu nie rusza biegow`() {
        val bieg = zegarek("bieg", 300, type = "Bieg")
        val split = Timeline.build(
            strength = listOf(trening(1, PPL, 100)),
            watch = listOf(bieg),
            programId = FBW
        )
        assertEquals(1, split.cardio.size)
    }

    @Test
    fun `bieg nagrany i telefonem, i zegarkiem jest jednym biegiem`() {
        val telefon = zegarek("tel", 1_000, type = "Bieg").copy(source = ExternalSession.PHONE_SOURCE)
        // Zegarek włączony dwie minuty później - pokrywa się prawie całkiem.
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(telefon, zegarek("zeg", 121_000, type = "Bieg"))
        )
        assertEquals(listOf("tel"), split.cardio.map { (it as Activity.Watch).session.externalId })
    }

    @Test
    fun `bieg z telefonu nie zjada spaceru z innej pory`() {
        val telefon = zegarek("tel", 1_000, type = "Bieg").copy(source = ExternalSession.PHONE_SOURCE)
        val split = Timeline.build(
            strength = emptyList(),
            watch = listOf(telefon, zegarek("spacer", 10_000_000, type = "Spacer"))
        )
        assertEquals(2, split.cardio.size)
    }
}
