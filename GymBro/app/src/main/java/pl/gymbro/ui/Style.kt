package pl.gymbro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Wspólne klocki wyglądu - te same w Treningu, Planie, Kaloriach, Wadze
 * i Profilu.
 *
 * Język ustalony w Treningu: ciemna karta z cienką obwódką zamiast szarego
 * prostokąta, pigułki na krótkie fakty, ikona w zaokrąglonym kwadracie,
 * jeden kolor akcji. Każdy ekran składał go dotąd z ręki i po trzech
 * zakładkach odcienie i zaokrąglenia zaczęły się rozjeżdżać - stąd jedno
 * miejsce.
 */

/**
 * Dotyk z falą od palca, przyciętą do zaokrąglonego kształtu.
 *
 * Fala jest jedna w całej aplikacji - ta z kart wyboru w Celu, rozlewająca
 * się po całym elemencie. Goły `clickable` rysował ją jako kanciasty
 * prostokąt wystający za zaokrąglone rogi, a przed `padding` - tylko na
 * środku elementu. Stąd zasada: `tap` przed `padding`, a karta dostaje
 * dotyk przez `Card(onClick)`, nie przez modyfikator z zewnątrz.
 */
internal fun Modifier.tap(
    shape: Shape = RoundedCornerShape(10.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = clip(shape).clickable(enabled = enabled, onClick = onClick)

/** Karta: tło ekranu o ton jaśniejsze i obwódka. [accent] - obwódka w kolorze akcji. */
@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val m = modifier.fillMaxWidth()
    val shape = RoundedCornerShape(12.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = if (accent != null) BorderStroke(2.dp, accent)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(modifier = m, shape = shape, colors = colors, border = border, content = content)
    }
}

/**
 * Nagłówek ekranu w głębi zakładki: strzałka wstecz, tytuł i podpis.
 *
 * Zamiast „Zamknij" w prawym rogu, które na każdym ekranie stało gdzie
 * indziej - strzałka po lewej jest tam, gdzie kciuk szuka wyjścia, tak
 * samo jak w Planie i w szczegółach treningu.
 */
@Composable
internal fun SubScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Krótki fakt w pigułce: „4 × 8-12", „81,8 kg", „aktywny". */
@Composable
internal fun TagPill(text: String, tone: Color? = null, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = tone ?: MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tone?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Nagłówek grupy: wersaliki, drobno, przygaszone - jak „Z INNYCH PLANÓW". */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** Ikona w zaokrąglonym kwadracie - znak rozpoznawczy wiersza. */
@Composable
internal fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 36) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/**
 * Grupa ustawień: jedna karta, wiersze rozdzielone cienką kreską.
 *
 * Zamiast osobnej karty na każdą rzecz - przy dwudziestu ustawieniach Profilu
 * dwadzieścia ramek zlewało się w jedną szarą ścianę.
 */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier) { content() }
}

/**
 * Wiersz ustawień: ikona, tytuł z podpisem, a z prawej wartość, przełącznik
 * albo strzałka „w głąb". [first] - bez kreski nad sobą.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    value: String? = null,
    first: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconBadge(icon, iconTint, size = 34)
            Box(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        trailing?.invoke(this)
        if (chevron) {
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
