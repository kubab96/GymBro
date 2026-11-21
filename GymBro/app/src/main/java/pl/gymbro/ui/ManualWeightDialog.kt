package pl.gymbro.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.gymbro.data.BodyComposition
import pl.gymbro.data.BodyMeasurement
import pl.gymbro.data.MeasurementSite
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Poprawianie zapisanego pomiaru: data, godzina, masa i obwody.
 *
 * Nowe pomiary - także te wstecz - idą przez kreator [MeasurementFlow], który
 * ma własny wybór daty. Osobny przycisk „Wpisz wstecz" obok „Zmierz" kazał
 * przed pomiarem zdecydować, którą z dwóch dróg do tego samego zapisu wybrać.
 * Parametr [existing] zostaje opcjonalny, bo okno umie oba przypadki.
 *
 * Obwody są tu razem z masą, bo jeden pomiar to jeden moment. Wcześniej dało się
 * je podać wyłącznie w kreatorze - więc wpis poprawiony po fakcie zostawał
 * bez centymetrów i nie dało się tego naprawić inaczej niż kasując pomiar
 * i robiąc go od nowa.
 *
 * Składu ciała się nie wpisuje: liczy się z obwodów. Ta waga nie oddaje
 * prawdziwej bioimpedancji - w rozgłoszeniu podaje stałą wartość zastępczą,
 * więc każda liczba przepisana z aplikacji producenta byłaby wzorem udającym
 * pomiar.
 *
 * Wybór daty i godziny przez systemowe okna Androida, a nie komponenty Material3:
 * tamte są wciąż oznaczone jako eksperymentalne i ich API zmienia się między
 * wersjami. Systemowe działają wszędzie tak samo.
 */
@Composable
fun ManualWeightDialog(
    /** Pomiar do poprawienia; null oznacza nowy wpis. */
    existing: BodyMeasurement? = null,
    /** Obwody zapisane przy tym pomiarze, do wypełnienia pól. */
    existingGirths: Map<MeasurementSite, Double> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (Double, Long, BodyComposition, Map<MeasurementSite, Double>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var whenMs by remember { mutableStateOf(existing?.measuredAt ?: System.currentTimeMillis()) }
    var weight by remember { mutableStateOf(existing?.let { fmtNum(it.weightKg) } ?: "") }

    // Usunięcie pomiaru jest nieodwracalne, więc wymaga drugiego dotknięcia.
    // Osobne okno potwierdzenia byłoby tu przesadą, a przypadkowe skasowanie
    // wpisu sprzed tygodnia - nie.
    var confirmDelete by remember { mutableStateOf(false) }

    val girths = remember {
        mutableStateMapOf<MeasurementSite, String>().apply {
            MeasurementSite.entries.forEach { site ->
                put(site, existingGirths[site]?.let { fmtNum(it) } ?: "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nowy pomiar" else "Popraw pomiar") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- Kiedy ----
                Text("Kiedy", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickDate(context, whenMs) { whenMs = it } },
                        modifier = Modifier.weight(1f)
                    ) { Text(dateOnly(whenMs)) }

                    OutlinedButton(
                        onClick = { pickTime(context, whenMs) { whenMs = it } },
                        modifier = Modifier.weight(1f)
                    ) { Text(timeOnly(whenMs)) }
                }
                Text(
                    if (existing == null) "Domyślnie teraz. Zmień, jeśli uzupełniasz pomiar wstecz."
                    else "Zmień, jeśli pomiar zapisał się ze złą datą albo godziną.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
                DecimalField(weight, { weight = it }, "Masa (kg)")

                Spacer(Modifier.height(14.dp))
                Text("Obwody", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Zostaw puste, jeśli nie mierzyłeś.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                MeasurementSite.entries.forEach { site ->
                    DecimalField(
                        value = girths[site].orEmpty(),
                        onChange = { girths[site] = it },
                        label = site.label + " (cm)"
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val kg = weight.num()
                if (kg != null && kg in 20.0..300.0) {
                    // Puste pole znaczy "nie mierzyłem", a nie "zero" - takie
                    // miejsce po prostu nie trafia do zapisu. Zakres odsiewa
                    // literówki w rodzaju 870 zamiast 87.
                    val parsed = girths.mapNotNull { (site, text) ->
                        text.num()?.takeIf { it in 10.0..250.0 }?.let { site to it }
                    }.toMap()
                    onSave(
                        kg,
                        whenMs,
                        // Przy edycji zachowujemy to, co pomiar miał wcześniej -
                        // dane z wersji sprzed liczenia tłuszczu z obwodów
                        // nie powinny znikać tylko dlatego, że nie ma już pól.
                        existing?.composition ?: BodyComposition.EMPTY,
                        parsed
                    )
                }
            }) { Text("Zapisz") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true }
                    ) {
                        Text(
                            if (confirmDelete) "Na pewno?" else "Usuń",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        }
    )
}

/** Przecinek jako separator dziesiętny jest w Polsce naturalniejszy niż kropka. */
private fun String.num(): Double? =
    trim().replace(',', '.').toDoubleOrNull()

/** Bez zbędnych zer na końcu - 79,15 zamiast 79,150000. */
private fun fmtNum(v: Double): String =
    if (kotlin.math.abs(v - Math.round(v)) < 0.001) Math.round(v).toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.', ',')

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun pickDate(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val c = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            onPicked(c.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).apply {
        // Pomiaru z przyszłości nie da się zrobić.
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

internal fun pickTime(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = current }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val c = Calendar.getInstance().apply {
                timeInMillis = current
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            onPicked(c.timeInMillis)
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        true
    ).show()
}

internal fun dateOnly(ms: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ms))

internal fun timeOnly(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
