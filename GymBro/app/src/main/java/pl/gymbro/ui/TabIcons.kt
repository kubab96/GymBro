package pl.gymbro.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ikony paska zakładek - jeden rysunek w dwóch wersjach: obrys, gdy zakładka
 * stoi w tle, i wypełnienie, gdy jest otwarta.
 *
 * Własne, a nie z `material-icons`: tam pięć ikon pochodziło z pięciu różnych
 * rodzin (sztanga pełna, lista prostokątna, waga z cyfrą), więc pasek wyglądał
 * na zszyty z przypadkowych kawałków, a „Restaurant" i „ListAlt" nie mają
 * wersji obrysowej, którą dałoby się odróżnić od pełnej. Tu wszystkie stoją
 * na tej samej siatce 24 × 24, z tą samą grubością linii i zaokrągleniem.
 *
 * Kolor nadaje `Icon(tint)`, więc czarny w ścieżkach nie ma znaczenia.
 * Wycięcia w wersji pełnej (okienko wagi, linie na podkładce) to drugi kontur
 * przy `EvenOdd` - osobna ścieżka w innym kolorze zniknęłaby pod tintem.
 */
internal object TabIcons {
    class Variants(val outline: ImageVector, val filled: ImageVector)

    private const val LINE = 1.8f

    /** Prostokąt o zaokrąglonych rogach jako kontur ścieżki. */
    private fun rr(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): String =
        "M${x0 + r},$y0 H${x1 - r} A$r,$r 0 0 1 $x1,${y0 + r} V${y1 - r} " +
            "A$r,$r 0 0 1 ${x1 - r},$y1 H${x0 + r} A$r,$r 0 0 1 $x0,${y1 - r} " +
            "V${y0 + r} A$r,$r 0 0 1 ${x0 + r},$y0 Z "

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},$cy A$r,$r 0 1 0 ${cx + r},$cy A$r,$r 0 1 0 ${cx - r},$cy Z "

    private fun icon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    private fun ImageVector.Builder.line(d: String) = addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = LINE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )

    private fun ImageVector.Builder.solid(d: String) = addPath(
        pathData = addPathNodes(d),
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd
    )

    // ---- Trening: hantel ----------------------------------------------------

    private val dumbbellPlates =
        rr(4.2f, 6.2f, 7.6f, 17.8f, 1.6f) + rr(16.4f, 6.2f, 19.8f, 17.8f, 1.6f)
    private val dumbbellEnds =
        rr(1.6f, 9.2f, 4.2f, 14.8f, 1.1f) + rr(19.8f, 9.2f, 22.4f, 14.8f, 1.1f)

    val training = Variants(
        outline = icon("TreningObrys") {
            line(dumbbellPlates)
            line(dumbbellEnds)
            line("M7.6,12 H16.4")
        },
        filled = icon("TreningPelny") {
            solid(dumbbellPlates)
            solid(dumbbellEnds)
            line("M7.6,12 H16.4")
        }
    )

    // ---- Kalorie: jabłko ------------------------------------------------------

    private const val APPLE =
        "M12,8 C10.4,6.7 8.4,6.4 6.8,7.1 C4.5,8.1 3.5,10.8 4.1,13.7 " +
            "C4.8,17.2 7.3,20.6 9.7,20.6 C10.7,20.6 11.1,20.1 12,20.1 " +
            "C12.9,20.1 13.3,20.6 14.3,20.6 C16.7,20.6 19.2,17.2 19.9,13.7 " +
            "C20.5,10.8 19.5,8.1 17.2,7.1 C15.6,6.4 13.6,6.7 12,8 Z"
    private const val LEAF =
        "M12.9,5.6 C13.6,3.8 15.3,2.9 17.3,3 C17.1,4.9 15.4,5.9 12.9,5.6 Z"

    val food = Variants(
        outline = icon("KalorieObrys") {
            line(APPLE)
            line(LEAF)
            line("M12,8 C11.9,6.4 11.4,5.2 10.6,4.2")
        },
        filled = icon("KaloriePelne") {
            solid(APPLE)
            solid(LEAF)
            line("M12,8 C11.9,6.4 11.4,5.2 10.6,4.2")
        }
    )

    // ---- Waga: waga łazienkowa ------------------------------------------------

    private val scaleBody = rr(3.2f, 3.2f, 20.8f, 20.8f, 4.6f)
    // Tarcza jako półkole: zaokrąglony prostokąt z kreską w środku czytał
    // się jak ogniwo łańcucha, nie jak waga.
    private const val DIAL = "M7.2,12 A4.8,4.8 0 0 1 16.8,12 Z"
    private const val NEEDLE = "M12,11.6 L14,8.6"

    val weight = Variants(
        outline = icon("WagaObrys") {
            line(scaleBody)
            line(DIAL)
            line(NEEDLE)
        },
        filled = icon("WagaPelna") {
            // Tarcza wycięta z korpusu, a wskazówka znów pełna w środku.
            solid(scaleBody + DIAL)
            line(NEEDLE)
        }
    )

    // ---- Plan: podkładka z listą ---------------------------------------------

    private val board = rr(4.6f, 4.4f, 19.4f, 21f, 2.6f)
    private val clip = rr(8.6f, 2.6f, 15.4f, 6.4f, 1.3f)

    val plan = Variants(
        outline = icon("PlanObrys") {
            line(board)
            solid(clip)
            line("M8.6,11 H15.4 M8.6,14.4 H15.4 M8.6,17.8 H12.6")
        },
        filled = icon("PlanPelny") {
            // Linie wycięte z podkładki; klips wystaje nad nią, więc rysuje
            // się osobno - w jednym konturze nakładka by się wydrążyła.
            solid(
                board +
                    rr(8f, 10.1f, 16f, 11.9f, 0.9f) +
                    rr(8f, 13.5f, 16f, 15.3f, 0.9f) +
                    rr(8f, 16.9f, 13.2f, 18.7f, 0.9f)
            )
            solid(clip)
        }
    )

    // ---- Profil: sylwetka -----------------------------------------------------

    val profile = Variants(
        outline = icon("ProfilObrys") {
            line(circle(12f, 8f, 3.8f))
            line("M4.6,20.4 C4.6,16.5 7.9,13.9 12,13.9 C16.1,13.9 19.4,16.5 19.4,20.4")
        },
        filled = icon("ProfilPelny") {
            solid(circle(12f, 8f, 4.2f))
            solid(
                "M3.8,20.4 C3.8,16.1 7.5,13.1 12,13.1 C16.5,13.1 20.2,16.1 20.2,20.4 " +
                    "C20.2,21 19.8,21.4 19.2,21.4 H4.8 C4.2,21.4 3.8,21 3.8,20.4 Z"
            )
        }
    )
}
