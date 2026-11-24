package pl.gymbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Przycisk arkusza. [danger] - czerwony, dla tego, czego nie da się cofnąć. */
internal data class SheetAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * Pytanie o decyzję - arkusz od dołu zamiast okna na środku.
 *
 * Okno systemowe z trzema płaskimi napisami w rogu („Odrzuć", „Wróć",
 * „Zapisz") wyglądało jak komunikat o błędzie, a przyciski były małe i stały
 * obok siebie: przy zakończeniu biegu, spoconym palcem, „Odrzuć" leżało
 * tuż przy „Zapisz". Tu główna decyzja jest dużym przyciskiem pod kciukiem,
 * powrót osobnym pod nim, a to, co kasuje, najniżej i na czerwono.
 *
 * @param content dodatek pod tekstem - np. kafelki z wynikiem biegu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(
    onDismiss: () -> Unit,
    icon: ImageVector,
    tone: Color,
    title: String,
    text: String? = null,
    primary: SheetAction,
    secondary: SheetAction? = null,
    extra: SheetAction? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(tone.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (text != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (content != null) {
                Spacer(Modifier.height(18.dp))
                content()
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = primary.onClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = if (primary.danger) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(primary.label, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            if (secondary != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = secondary.onClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(secondary.label, fontSize = 16.sp) }
            }
            if (extra != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = extra.onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        extra.label,
                        color = if (extra.danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
