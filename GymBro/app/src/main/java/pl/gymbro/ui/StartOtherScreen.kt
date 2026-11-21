package pl.gymbro.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Activity
import pl.gymbro.data.Plan
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.Timeline
import pl.gymbro.track.PhoneTracker

/**
 * „Rozpocznij inny trening": jednostka z innego planu albo aktywność
 * nagrywana telefonem.
 *
 * Pełny ekran, nie okienko - jak każdy wybór w aplikacji, który ma więcej
 * niż dwie odpowiedzi. Jednorazowy trening z innego planu nie zmienia planu
 * aktywnego: zegarek dalej dostaje jednostki tamtego, a „następna w kolejce"
 * dalej liczy się z niego.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartOtherScreen(
    programs: List<ProgramUnits>,
    onBack: () -> Unit,
    onStartUnit: (Plan) -> Unit,
    onStartActivity: (PhoneActivity) -> Unit
) {
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    var confirming by remember { mutableStateOf<PlanOption?>(null) }
    var waitingFor by remember { mutableStateOf<PhoneActivity?>(null) }
    var denied by remember { mutableStateOf(false) }
    // Wybrana aktywność czeka na ekranie przygotowania na „Start".
    var preparing by remember { mutableStateOf<PhoneActivity?>(null) }

    // Zgoda na lokalizację dopiero tutaj, przy pierwszym biegu: kto nie
    // biega z telefonem, nie musi jej nigdy dawać. Samej przybliżonej nie
    // wystarczy - na trasę z dokładnością do kilometra nie ma co patrzeć.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val kind = waitingFor
        waitingFor = null
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true && kind != null) {
            preparing = kind
        } else {
            denied = true
        }
    }

    fun begin(kind: PhoneActivity) {
        if (kind.gps && !PhoneTracker.hasLocationPermission(context)) {
            waitingFor = kind
            permissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            preparing = kind
        }
    }

    // Start nie rusza od dotknięcia kafelka. Kafelek to wybór, a nie
    // decyzja: stoper biegnący od chwili wyboru liczył sznurowanie butów,
    // a GPS bez czasu na złapanie sygnału gubił pierwsze sto metrów.
    preparing?.let { kind ->
        ActivityPrepScreen(
            kind = kind,
            onBack = { preparing = null },
            onStart = {
                preparing = null
                onStartActivity(kind)
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
            }
            Text("Nowy trening", style = MaterialTheme.typography.headlineSmall)
        }

        if (programs.isNotEmpty()) {
            SectionLabel("Z INNYCH PLANÓW")
            Spacer(Modifier.height(6.dp))
            programs.forEach { p ->
                // Najdawniej robiona jednostka tego planu - gdyby wrócić
                // do niego na stałe, to od niej trzeba by zacząć.
                val next = p.units.minByOrNull { it.lastDoneAt ?: 0L }?.plan?.id
                val last = p.units.mapNotNull { it.lastDoneAt }.maxOrNull()
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(p.program.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (last == null) "nierobiony" else "ostatnio " + lastDoneLabel(last),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.units.forEach { u ->
                                UnitPill(u.plan.name, highlighted = u.plan.id == next) { confirming = u }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        SectionLabel("AKTYWNOŚCI")
        Text(
            "Czas, dystans i trasę mierzy telefon. Tętno, jeśli zegarek był na ręce, " +
                "dociągnie się samo po zakończeniu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        PhoneActivity.entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { kind ->
                    ActivityTile(kind, Modifier.weight(1f)) { begin(kind) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    confirming?.let { o ->
        StartConfirmDialog(o, onDismiss = { confirming = null }) {
            confirming = null
            onStartUnit(o.plan)
        }
    }

    if (denied) {
        AlertDialog(
            onDismissRequest = { denied = false },
            title = { Text("Bez lokalizacji nie ma trasy") },
            text = {
                Text(
                    "Do biegu, spaceru i roweru telefon potrzebuje dokładnej lokalizacji - " +
                        "z niej liczy dystans i tempo. Zgodę włączysz w ustawieniach " +
                        "systemu, w uprawnieniach GymBro."
                )
            },
            confirmButton = { TextButton(onClick = { denied = false }) { Text("Rozumiem") } }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ActivityTile(kind: PhoneActivity, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(
                kindIcon(kind), contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(kind.label, style = MaterialTheme.typography.titleSmall)
            Text(
                when (kind) {
                    PhoneActivity.RUN -> "czas · dystans · tempo"
                    PhoneActivity.WALK -> "spacer, marsz"
                    PhoneActivity.BIKE -> "czas · dystans · prędkość"
                    PhoneActivity.SWIM -> "sam czas, bez trasy"
                    PhoneActivity.STRETCH -> "mobilność, joga"
                    PhoneActivity.OTHER -> "sam czas"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnitPill(name: String, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun kindIcon(kind: PhoneActivity): ImageVector = when (kind) {
    PhoneActivity.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    PhoneActivity.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    PhoneActivity.BIKE -> Icons.AutoMirrored.Filled.DirectionsBike
    PhoneActivity.SWIM -> Icons.Filled.Pool
    PhoneActivity.STRETCH -> Icons.Filled.SelfImprovement
    PhoneActivity.OTHER -> Icons.Filled.Timer
}

/** Ikona pozycji historii - po nazwie typu, tej samej dla telefonu i zegarka. */
internal fun activityIcon(a: Activity): ImageVector = when (a) {
    is Activity.Strength -> Icons.Filled.FitnessCenter
    is Activity.Watch -> {
        val t = a.session.type
        when {
            Timeline.isStrength(a.session) -> Icons.Filled.FitnessCenter
            t.startsWith("Bieg") -> Icons.AutoMirrored.Filled.DirectionsRun
            t.startsWith("Spacer") || t.startsWith("Marsz") || t.startsWith("Trekking") ->
                Icons.AutoMirrored.Filled.DirectionsWalk
            t.startsWith("Rower") -> Icons.AutoMirrored.Filled.DirectionsBike
            t.startsWith("Pływanie") -> Icons.Filled.Pool
            t.startsWith("Rozciąganie") || t.startsWith("Joga") -> Icons.Filled.SelfImprovement
            else -> Icons.Filled.Timer
        }
    }
}
