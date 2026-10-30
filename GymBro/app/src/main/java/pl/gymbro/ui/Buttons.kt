package pl.gymbro.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pl.gymbro.ui.theme.ButtonShape

/**
 * Przyciski aplikacji: prostokątne, nie kapsułkowe.
 *
 * Dlaczego opakowania, a nie motyw: Material bierze kształt przycisku z tokena
 * `CornerFull`, który w bibliotece jest zakodowany na sztywno jako `CircleShape`.
 * `MaterialTheme(shapes = …)` go nie dotyka - można to zmienić wyłącznie przy
 * każdym wywołaniu. Zamiast dopisywać `shape` w stu kilkunastu miejscach,
 * stoją tu trzy funkcje o tych samych nazwach co materiałowe.
 *
 * Działa to dlatego, że Kotlin rozstrzyga wywołania w kolejności: jawne importy,
 * potem własny pakiet. Pliki ekranów nie importują już przycisków z Material,
 * więc trafiają tutaj - bez zmiany ani jednego wywołania. Wewnątrz wołamy
 * materiałowe po pełnej ścieżce, żeby nie wpaść we własne funkcje.
 *
 * Parametry odpowiadają temu, czego ekrany faktycznie używają. Gdy zabraknie
 * któregoś, kompilator powie o tym od razu - i o to chodzi, bo wtedy decyzja
 * o jego dodaniu zapada tutaj, w jednym miejscu.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}
