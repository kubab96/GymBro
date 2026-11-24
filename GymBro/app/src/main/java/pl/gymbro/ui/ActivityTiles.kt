package pl.gymbro.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.IceSkating
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NordicWalking
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.RollerSkating
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.gymbro.data.Activity
import pl.gymbro.data.Plan
import pl.gymbro.gym.ActivityGroup
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.Timeline
import pl.gymbro.track.PhoneTracker

/**
 * Start aktywności z kafelka: zgoda na lokalizację, gdy aktywność ma trasę,
 * a potem [onReady] - ekran przygotowania ze „Start".
 *
 * Kafelki siedzą teraz na ekranie Treningu, a nie na osobnym ekranie „Nowy
 * trening" - do biegu czy treningu siłowego było o jeden ekran za daleko.
 * Zgoda i jej odmowa jadą razem z nimi, stąd osobny klocek.
 *
 * Zgoda na lokalizację dopiero przy pierwszej aktywności z trasą: kto nie
 * biega z telefonem, nie musi jej nigdy dawać. Samej przybliżonej nie
 * wystarczy - na trasę z dokładnością do kilometra nie ma co patrzeć.
 */
@Composable
internal fun rememberActivityStarter(onReady: (PhoneActivity) -> Unit): (PhoneActivity) -> Unit {
    val context = LocalContext.current
    val ready by rememberUpdatedState(onReady)
    var waitingFor by remember { mutableStateOf<PhoneActivity?>(null) }
    var denied by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val kind = waitingFor
        waitingFor = null
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true && kind != null) ready(kind)
        else denied = true
    }

    if (denied) {
        ActionSheet(
            onDismiss = { denied = false },
            icon = Icons.Filled.LocationOff,
            tone = MaterialTheme.colorScheme.tertiary,
            title = "Bez lokalizacji nie ma trasy",
            text = "Dystans i tempo telefon liczy z dokładnej lokalizacji. Zgodę " +
                "włączysz w ustawieniach systemu, w uprawnieniach GymBro.",
            primary = SheetAction("Rozumiem") { denied = false }
        )
    }

    return remember {
        { kind ->
            if (kind.gps && !PhoneTracker.hasLocationPermission(context)) {
                waitingFor = kind
                permissions.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else ready(kind)
        }
    }
}

/**
 * Kafelki aktywności, trening siłowy pierwszy. Kafelek to wybór, nie
 * decyzja: start rusza dopiero z ekranu przygotowania - stoper biegnący od
 * dotknięcia liczył sznurowanie butów, a GPS bez czasu na złapanie sygnału
 * gubił pierwsze sto metrów.
 */
@Composable
internal fun ActivityTiles(onPick: (PhoneActivity) -> Unit) {
    ActivityGrid(
        PhoneActivity.entries.filter { it.group != ActivityGroup.HIDDEN }
            .sortedBy { it.group != ActivityGroup.STRENGTH },
        onPick
    )
}


/**
 * Szeroki kafelek pod siatką: jednostki z pozostałych planów. Jako dziesiąty
 * w siatce trzy na trzy stałby sam w czwartym rzędzie. Lista jednostek
 * wypisana na ekranie Treningu ciągnęła go w dół o pół ekranu, a sięga się
 * po nią rzadko - stąd osobny ekran za kafelkiem.
 */
@Composable
internal fun OtherPlansTile(plans: Int, onClick: () -> Unit) {
    AppCard(Modifier.padding(top = 8.dp), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                "Z innych planów",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            Text(
                plural(plans, "plan", "plany", "planów"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Kafelki po trzy w rzędzie - dziewięć pozycji to równe trzy rzędy. */
@Composable
private fun ActivityGrid(kinds: List<PhoneActivity>, onPick: (PhoneActivity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    ActivityTile(kind, Modifier.weight(1f)) { onPick(kind) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActivityTile(kind: PhoneActivity, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier, onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    kindIcon(kind), contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    kind.label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (kind.gps) {
                Icon(
                    Icons.Filled.LocationOn, contentDescription = "z trasą",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(14.dp)
                )
            }
        }
    }
}

internal fun kindIcon(kind: PhoneActivity): ImageVector = when (kind) {
    PhoneActivity.RUN, PhoneActivity.TREADMILL -> Icons.AutoMirrored.Filled.DirectionsRun
    PhoneActivity.TRAIL -> Icons.Filled.Terrain
    PhoneActivity.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    PhoneActivity.HIKE -> Icons.Filled.Hiking
    PhoneActivity.NORDIC -> Icons.Filled.NordicWalking
    PhoneActivity.BIKE, PhoneActivity.MTB -> Icons.AutoMirrored.Filled.DirectionsBike
    PhoneActivity.BIKE_INDOOR -> Icons.Filled.PedalBike
    PhoneActivity.INLINE -> Icons.Filled.RollerSkating
    PhoneActivity.ELLIPTICAL -> Icons.Filled.Loop
    PhoneActivity.ROWING -> Icons.Filled.Rowing
    PhoneActivity.STAIRS -> Icons.Filled.Stairs
    PhoneActivity.SWIM -> Icons.Filled.Pool
    PhoneActivity.HIIT -> Icons.Filled.Bolt
    PhoneActivity.CROSSFIT -> Icons.Filled.Whatshot
    PhoneActivity.STRETCH -> Icons.Filled.SportsGymnastics
    PhoneActivity.YOGA, PhoneActivity.PILATES -> Icons.Filled.SelfImprovement
    PhoneActivity.DANCE -> Icons.Filled.MusicNote
    PhoneActivity.BOXING -> Icons.Filled.SportsMma
    PhoneActivity.MARTIAL -> Icons.Filled.SportsMartialArts
    PhoneActivity.CLIMB -> Icons.Filled.Landscape
    PhoneActivity.FOOTBALL -> Icons.Filled.SportsSoccer
    PhoneActivity.BASKETBALL -> Icons.Filled.SportsBasketball
    PhoneActivity.VOLLEYBALL -> Icons.Filled.SportsVolleyball
    PhoneActivity.TENNIS, PhoneActivity.PADEL, PhoneActivity.SQUASH,
    PhoneActivity.BADMINTON, PhoneActivity.TABLE_TENNIS -> Icons.Filled.SportsTennis
    PhoneActivity.GOLF -> Icons.Filled.GolfCourse
    PhoneActivity.XC_SKI -> Icons.Filled.NordicWalking
    PhoneActivity.SKI -> Icons.Filled.DownhillSkiing
    PhoneActivity.SNOWBOARD -> Icons.Filled.Snowboarding
    PhoneActivity.SKATE -> Icons.Filled.IceSkating
    PhoneActivity.KAYAK -> Icons.Filled.Kayaking
    PhoneActivity.SUP -> Icons.Filled.Surfing
    PhoneActivity.STRENGTH -> Icons.Filled.FitnessCenter
    PhoneActivity.OTHER -> Icons.Filled.Timer
}

/** Ikona pozycji historii - po nazwie typu, tej samej dla telefonu i zegarka. */
internal fun activityIcon(a: Activity): ImageVector = when (a) {
    is Activity.Strength -> Icons.Filled.FitnessCenter
    is Activity.Watch -> {
        val t = a.session.type
        val known = PhoneActivity.byType(t)
        when {
            Timeline.isStrength(a.session) -> Icons.Filled.FitnessCenter
            known != null -> kindIcon(known)
            t.startsWith("Bieg") -> Icons.AutoMirrored.Filled.DirectionsRun
            t.startsWith("Spacer") || t.startsWith("Marsz") -> Icons.AutoMirrored.Filled.DirectionsWalk
            t.startsWith("Trekking") -> Icons.Filled.Hiking
            t.startsWith("Rower") -> Icons.AutoMirrored.Filled.DirectionsBike
            t.startsWith("Pływanie") || t.startsWith("Plywanie") -> Icons.Filled.Pool
            t.startsWith("Rozciąganie") || t.startsWith("Joga") -> Icons.Filled.SelfImprovement
            t.startsWith("Wioślarz") || t.startsWith("Wioslarz") -> Icons.Filled.Rowing
            t.startsWith("HIIT") -> Icons.Filled.Bolt
            else -> Icons.Filled.Timer
        }
    }
}

