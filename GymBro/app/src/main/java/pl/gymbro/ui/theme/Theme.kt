package pl.gymbro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * System wizualny aplikacji.
 *
 * Założenia, z których wynika reszta:
 *
 *  - **Ciemny domyślnie.** Trenujesz wieczorami, ekran świeci przez cały trening
 *    (nie gasimy go w trakcie sesji), a na AMOLED czerń nie pobiera prądu.
 *  - **Dwa akcenty, nie jeden.** Limonka to siła, błękit to wytrzymałość.
 *    Aplikacja jest hybrydowa, więc kolor od razu mówi, o którą stronę chodzi -
 *    zamiast malować wszystko jednym kolorem marki.
 *  - **Duży kontrast i duże liczby.** Patrzysz na to z wyciągniętej ręki,
 *    spocony, między seriami. To nie jest ekran do czytania na kanapie.
 */

// ---- Barwy ---------------------------------------------------------------

/** Błękit - kolor główny: siła, akcje, wysiłek. Jasny na tyle, by nieść tekst na czerni. */
private val Blue = Color(0xFF6AA9FF)
private val BlueDark = Color(0xFF1B5FC4)

/**
 * Turkus - regeneracja, przerwy, dane z zegarka.
 *
 * Musiał odejść od błękitu po tym, jak kolor główny stał się niebieski:
 * dwa sąsiadujące odcienie niebieskiego przestają cokolwiek rozróżniać.
 * Turkus zostaje w chłodnej rodzinie, ale jest rozpoznawalny z odległości.
 */
private val Teal = Color(0xFF37D6C0)
private val TealDark = Color(0xFF00796B)

/** Bursztyn - rekordy i ostrzeżenia, które nie są błędami. */
private val Amber = Color(0xFFFFB357)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF06213F),
    primaryContainer = Color(0xFF11385E),
    onPrimaryContainer = Color(0xFFD3E6FF),

    secondary = Teal,
    onSecondary = Color(0xFF00322B),
    secondaryContainer = Color(0xFF0B4A42),
    onSecondaryContainer = Color(0xFFB9F5EB),

    tertiary = Amber,
    onTertiary = Color(0xFF3A2206),

    background = Color(0xFF0D0F11),
    onBackground = Color(0xFFE7EAED),
    surface = Color(0xFF14171A),
    onSurface = Color(0xFFE7EAED),
    surfaceVariant = Color(0xFF262B30),
    onSurfaceVariant = Color(0xFFAEB6BE),
    outline = Color(0xFF3B424A),
    outlineVariant = Color(0xFF2A3036),

    error = Color(0xFFFF7A7A),
    onError = Color(0xFF3A0A0A)
)

private val LightColors = lightColorScheme(
    primary = BlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF0A2746),

    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6F0E6),
    onSecondaryContainer = Color(0xFF00241F),

    tertiary = Color(0xFF8A5200),
    onTertiary = Color.White,

    background = Color(0xFFF7F8F9),
    onBackground = Color(0xFF14171A),
    surface = Color.White,
    onSurface = Color(0xFF14171A),
    surfaceVariant = Color(0xFFE7EAED),
    onSurfaceVariant = Color(0xFF454C53),
    outline = Color(0xFFC2C8CE),
    outlineVariant = Color(0xFFDDE2E6),

    error = Color(0xFFB3261E),
    onError = Color.White
)

// ---- Typografia ----------------------------------------------------------

/**
 * Celowo większe niż domyślne Material3. Ekran jest używany z dystansu
 * i w ruchu, więc drobny tekst po prostu nie działa.
 */
private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
)

// ---- Kształty ------------------------------------------------------------

/**
 * Narożniki ledwie ścięte, zamiast zaokrąglonych.
 *
 * Material domyślnie zaokrągla mocno - karty na 16 dp, okna na 28, a przyciski
 * robi kapsułkami. Przy ekranie gęsto wypełnionym kartami i przyciskami daje to
 * wrażenie rozmycia: nic nie ma wyraźnej krawędzi. Ścięcie zostaje tylko po to,
 * żeby róg nie kłuł.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

/**
 * Kształt przycisków.
 *
 * Osobno od [AppShapes], bo Material bierze go z tokena `CornerFull`, a ten
 * jest w bibliotece zakodowany na sztywno jako koło - motyw go nie dosięga.
 * Dlatego przyciski mają w tej aplikacji własne opakowania w `Buttons.kt`,
 * które ten kształt podstawiają.
 */
val ButtonShape = RoundedCornerShape(4.dp)

// ---- Motyw ---------------------------------------------------------------

@Composable
fun GymBroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Ikony paska stanu muszą kontrastować z tłem, inaczej znikają.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
