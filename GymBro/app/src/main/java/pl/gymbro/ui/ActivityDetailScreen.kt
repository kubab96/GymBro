package pl.gymbro.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.HealthRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.gym.RunAnalysis
import pl.gymbro.gym.Timeline
import pl.gymbro.health.ActivityDetail
import pl.gymbro.data.TrackRepository
import pl.gymbro.gym.TrackMath
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import pl.gymbro.data.PhoneSet
import pl.gymbro.data.restsMs
import pl.gymbro.gym.RunRecords
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zapis sesji na czas odtworzenia aktywności Androida.
 *
 * Obrót ekranu niszczy aktywność i buduje ją od nowa. Przechowywanie samego
 * identyfikatora nie wystarczyło: po odtworzeniu historia wczytuje się
 * asynchronicznie, więc przez moment nie ma w czym szukać i ekran wracał na
 * listę. Cała sesja to kilka liczb i dwa teksty - taniej ją zapisać, niż
 * uzależniać powrót od tego, czy coś zdążyło się wczytać.
 */
val ExternalSessionSaver: Saver<ExternalSession?, Any> = listSaver(
    save = { s ->
        if (s == null) emptyList() else listOf(
            s.id, s.externalId, s.source, s.type, s.title ?: "",
            s.startedAt, s.endedAt,
            s.distanceM ?: -1.0, s.energyKcal ?: -1.0, s.hrAvg ?: -1.0, s.hrMax ?: -1.0
        )
    },
    restore = { v ->
        if (v.isEmpty()) null else ExternalSession(
            id = v[0] as Long,
            externalId = v[1] as String,
            source = v[2] as String,
            type = v[3] as String,
            title = (v[4] as String).ifEmpty { null },
            startedAt = v[5] as Long,
            endedAt = v[6] as Long,
            distanceM = (v[7] as Double).takeIf { it >= 0 },
            energyKcal = (v[8] as Double).takeIf { it >= 0 },
            hrAvg = (v[9] as Double).takeIf { it >= 0 },
            hrMax = (v[10] as Double).takeIf { it >= 0 }
        )
    }
)

/**
 * Szczegóły aktywności z zegarka.
 *
 * Lista odpowiada na pytanie „co robiłem", ten ekran na „jak to wyglądało".
 * Średnie tempo 6:10/km nie mówi nic o biegu, w którym kilometry szły
 * 5:27, 7:04 i 5:29 - a to jest cała różnica między równym wysiłkiem
 * a interwałami.
 *
 * Szeregi czasowe czytamy z Health Connect dopiero tutaj i nie zapisujemy
 * u siebie: są potrzebne wyłącznie w chwili patrzenia.
 */
@Composable
fun ActivityDetailScreen(
    session: ExternalSession,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack
) {
    // Systemowy wstecz robi to samo co „Wróć" - bez tego wychodził z aplikacji.
    ScreenBackHandler(onBack = onBack)
    val context = LocalContext.current
    val reader = remember { ActivityDetail(context) }
    val profileRepo = remember { ProfileRepository(context) }
    val trackRepo = remember { TrackRepository(context) }
    val scope = rememberCoroutineScope()

    var series by remember { mutableStateOf<ActivityDetail.Series?>(null) }
    var track by remember { mutableStateOf<List<TrackMath.Fix>>(emptyList()) }
    var sets by remember { mutableStateOf<List<PhoneSet>>(emptyList()) }
    var records by remember { mutableStateOf<List<RunRecords.DistanceRecord>>(emptyList()) }
    var mapOpen by remember { mutableStateOf(false) }
    var maxHr by remember { mutableStateOf(190.0) }
    var loading by remember { mutableStateOf(true) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(session.externalId) {
        profileRepo.get().birthYear?.let { year ->
            val age = java.time.LocalDate.now().year - year
            if (age in 10..100) maxHr = RunAnalysis.maxHrFromAge(age)
        }
        // Trasa z telefonu leży u nas - kilometry liczymy z niej, tak jak
        // zegarek liczy swoje okrążenia.
        if (session.fromPhone) {
            val (t, s, r) = withContext(Dispatchers.IO) {
                val t = trackRepo.points(session.externalId)
                // Rekordy z całej historii, żeby wiedzieć, czy ten bieg któryś
                // pobił. Liczone tylko dla biegu z trasą - inaczej nie ma czego.
                val r = if (t.size >= 2 && session.type in RunRecords.RUN_TYPES) {
                    val (all, tracks) = trackRepo.runHistory()
                    RunRecords.compute(all, tracks).heldBy(session.externalId)
                } else emptyList()
                Triple(t, trackRepo.sets(session.externalId), r)
            }
            track = t
            sets = s
            records = r
        }
        series = reader.load(session.startedAt, session.endedAt)
        loading = false
    }

    if (deleting) {
        ActionSheet(
            onDismiss = { deleting = false },
            icon = Icons.Filled.DeleteOutline,
            tone = MaterialTheme.colorScheme.error,
            title = "Usunąć tę aktywność?",
            text = when {
                track.isNotEmpty() -> "Zniknie z historii razem z trasą. Tego nie da się cofnąć."
                sets.isNotEmpty() -> "Zniknie z historii razem z seriami. Tego nie da się cofnąć."
                session.fromPhone -> "Zniknie z historii. Tego nie da się cofnąć."
                else -> "Zniknie z historii i nie wróci przy synchronizacji z zegarkiem. " +
                    "W aplikacji zegarka trening zostaje."
            },
            primary = SheetAction("Usuń", danger = true) {
                deleting = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (session.fromPhone) trackRepo.delete(session.externalId)
                        else HealthRepository(context).hide(session.externalId)
                    }
                    onDeleted()
                }
            },
            secondary = SheetAction("Wróć") { deleting = false }
        )
    }

    // Mapa na pełnym ekranie - tu da się ją przesuwać i przybliżać. W ekranie
    // szczegółów palec na mapie przewijałby mapę zamiast listy.
    if (mapOpen) {
        ScreenBackHandler { mapOpen = false }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            SubScreenHeader("Trasa", onBack = { mapOpen = false })
            RouteMap(track, Modifier.fillMaxWidth().weight(1f), interactive = true)
            Spacer(Modifier.height(10.dp))
            PaceLegend()
            Spacer(Modifier.height(16.dp))
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                SimpleDateFormat("EEEE, d MMMM, HH:mm", Locale.getDefault())
                    .format(Date(session.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Hero(session)

        Spacer(Modifier.height(18.dp))
        KeyStats(session, series?.steps)

        records.forEach { r ->
            Spacer(Modifier.height(10.dp))
            RecordBadge("Rekord: ${r.label} w ${clockText(r.best.ms)}")
        }

        if (sets.isNotEmpty()) {
            Section("Serie", "Czas każdej serii i przerwa przed następną. Dotknij serii, żeby ją usunąć.")
            PhoneSetTable(sets) { i ->
                scope.launch {
                    sets = withContext(Dispatchers.IO) {
                        trackRepo.deleteSet(session.externalId, i)
                        trackRepo.sets(session.externalId)
                    }
                }
            }
        }

        if (loading) {
            Spacer(Modifier.height(28.dp))
            Text("Wczytuję szczegóły z zegarka…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(60.dp))
            return@Column
        }

        val data = series
        val legs = if (session.fromPhone) TrackMath.splits(track) else data?.laps ?: emptyList()
        val laps = RunAnalysis.lapStats(legs, data?.heartRate ?: emptyList())
        val hr = RunAnalysis.downsample(data?.heartRate ?: emptyList(), 180)
        val zones = RunAnalysis.timeInZones(data?.heartRate ?: emptyList(), maxHr)

        if (track.size >= 2) {
            Section("Trasa", "Kolor to tempo względem średniej z tego biegu. Dotknij, żeby powiększyć.")
            RouteMap(track, Modifier.fillMaxWidth().height(260.dp), onOpen = { mapOpen = true })
            Spacer(Modifier.height(8.dp))
            PaceLegend()
        }

        if (laps.isNotEmpty() && Timeline.isCardio(session)) {
            Section(
                "Kilometry",
                if (session.fromPhone) "Z trasy nagranej telefonem, nie wyliczone ze średniej."
                else "Zmierzone przez zegarek na okrążeniach, nie wyliczone ze średniej."
            )
            LapTable(laps, maxHr)
        }

        if (hr.size >= 4) {
            Section("Tętno", "Kolor to strefa wysiłku, liczona od ${maxHr.toInt()} bpm (220 − wiek).")
            HeartRateChart(hr, maxHr)
        }


        if (zones.isNotEmpty()) {
            Section("Czas w strefach", null)
            val total = zones.values.sum().coerceAtLeast(1L)
            (5 downTo 1).forEach { z ->
                zones[z]?.takeIf { it > 0 }?.let { ZoneBar(z, it, it.toFloat() / total) }
            }
        }

        if (laps.isEmpty() && hr.size < 4 && zones.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                if (session.fromPhone)
                    "Bez tętna - zegarek nie zapisał go w czasie tej aktywności, " +
                        "albo nie było go na ręce."
                else "Zegarek nie przysłał dla tego treningu ani okrążeń, ani przebiegu " +
                    "tętna - zostają same podsumowania powyżej.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Usunąć da się każdą aktywność. Tę z zegarka import przynosił z
        // powrotem przy każdej synchronizacji, więc trafia na listę ukrytych
        // (HealthRepository.hide) zamiast znikać tylko na chwilę.
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = { deleting = true }) {
            Text("Usuń aktywność", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(36.dp))
    }
}

/** Legenda kolorów trasy - te same progi co na mapie ([PaceColors]). */
@Composable
internal fun PaceLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(PaceColors.fast to "szybciej", PaceColors.even to "równo", PaceColors.slow to "wolniej")
            .forEach { (c, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
    }
}

/** Plakietka rekordu. Bursztyn, nie zieleń - zieleń na tym ekranie to tempo. */
@Composable
private fun RecordBadge(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.EmojiEvents, contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/**
 * Serie treningu siłowego z telefonu: czas serii i przerwa po niej.
 *
 * Bez ćwiczeń i ciężarów - tyle wie telefon. Za to widać rytm treningu:
 * przerwy, które rosły pod koniec, mówią o zmęczeniu więcej niż suma czasu.
 */
@Composable
private fun PhoneSetTable(sets: List<PhoneSet>, onRemove: (Int) -> Unit) {
    var removing by remember { mutableStateOf<Int?>(null) }
    removing?.let { i ->
        RemoveSetSheet(i + 1, sets.getOrNull(i)?.ms ?: 0, onDismiss = { removing = null }) {
            removing = null
            onRemove(i)
        }
    }
    val rests = restsMs(sets)
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        listOf(
            "${sets.size}" to "serie",
            clockText(sets.sumOf { it.ms }) to "w seriach",
            (if (rests.isEmpty()) "-" else clockText(rests.average().toLong())) to "śr. przerwa"
        ).forEach { (value, label) ->
            Column(Modifier.weight(1f)) {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                Text(
                    label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val longest = sets.maxOf { it.ms }.coerceAtLeast(1)
    sets.forEachIndexed { i, s ->
        PhoneSetRow(
            number = i + 1,
            setMs = s.ms,
            restMs = rests.getOrNull(i),
            restRunning = false,
            fraction = s.ms.toFloat() / longest,
            onClick = { removing = i }
        )
    }
}

// ---- Nagłówek ------------------------------------------------------------

/**
 * Jedna liczba, która opisuje ten trening.
 *
 * Dla biegu to dystans, dla treningu bez dystansu - czas. Reszta jest
 * uzupełnieniem; przy sześciu równorzędnych rubrykach wzrok nie ma się
 * o co zaczepić.
 */
@Composable
private fun Hero(s: ExternalSession) {
    val km = (s.distanceM ?: 0.0) / 1000.0
    Column(Modifier.padding(top = 2.dp)) {
        Text(
            s.type,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            if (km > 0) {
                Text(
                    String.format(Locale.getDefault(), "%.2f", km),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " km",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                Text(
                    durationText(s.durationMin),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun KeyStats(s: ExternalSession, steps: Long?) {
    val km = (s.distanceM ?: 0.0) / 1000.0
    val cadence = steps?.let { RunAnalysis.cadence(it, s.durationMin) }
    val stride = steps?.let { RunAnalysis.strideCm(s.distanceM ?: 0.0, it) }

    // Przy treningu, który nie jest ruchem w terenie, zegarek mierzy tylko
    // z nadgarstka: czas, tętno i oszacowanie kalorii. Rytm i długość kroku
    // nie opisują wtedy niczego sensownego - kroki zliczone przy podnoszeniu
    // ciężarów to przejścia między stanowiskami, nie technika biegu.
    val cardio = Timeline.isCardio(s)

    val items = listOfNotNull(
        // Przy treningu bez dystansu czas jest już tą dużą liczbą u góry -
        // powtarzanie go w karcie wygląda jak usterka.
        if (cardio) "Czas" to durationText(s.durationMin) else null,
        if (cardio) s.paceMinPerKm?.let { "Tempo" to paceText(it) } else null,
        if (cardio && km > 0 && s.durationMin > 0)
            "Prędkość" to String.format(
                Locale.getDefault(), "%.1f", km / (s.durationMin / 60.0)
            ) + " km/h" else null,
        s.hrAvg?.let { "Tętno śr." to "${it.toInt()}" },
        s.hrMax?.let { "Tętno maks." to "${it.toInt()}" },
        s.energyKcal?.takeIf { it > 0 }?.let { "Energia" to "${it.toInt()} kcal" },
        if (cardio) cadence?.let { "Rytm" to "${it.toInt()} /min" } else null,
        if (cardio) stride?.let { "Krok" to "${it.toInt()} cm" } else null,
        if (cardio) steps?.let { "Kroki" to "$it" } else null
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 14.dp)) {
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(
                                value,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, note: String?) {
    Spacer(Modifier.height(26.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (note != null) {
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(10.dp))
}

// ---- Kilometry -----------------------------------------------------------

/**
 * Tempo każdego kilometra jako słupek, z tętnem obok.
 *
 * Skala zaczyna się od najszybszego kilometra, nie od zera: przy tempach
 * 5:27 i 7:04 słupki liczone od zera różniłyby się o jedną czwartą i wykres
 * nie pokazywałby niczego, czego nie widać w liczbach. Tak różnica rzuca się
 * w oczy od razu.
 *
 * Kolor słupka to strefa tętna na tym kilometrze - widać wtedy nie tylko, że
 * było wolniej, ale czy było wolniej dlatego, że odpuściłeś, czy dlatego, że
 * już nie dawałeś rady.
 */
@Composable
private fun LapTable(laps: List<RunAnalysis.Lap>, maxHr: Double) {
    val full = laps.filter { it.isFull && it.paceMinPerKm != null }
    if (full.isEmpty()) return
    val fastest = full.minOf { it.paceMinPerKm!! }
    val slowest = full.maxOf { it.paceMinPerKm!! }
    val span = (slowest - fastest).coerceAtLeast(0.01)
    // Kolory z motywu czyta się w kompozycji, nie w rysowaniu - DrawScope
    // nie jest kontekstem kompozycji.
    val fallback = MaterialTheme.colorScheme.primary

    laps.forEach { lap ->
        val pace = lap.paceMinPerKm
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (lap.isFull) "${lap.index}" else "·",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            Box(
                Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(7.dp))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.White.copy(alpha = 0.05f))
                    if (pace != null) {
                        // Najszybszy pełny słupek, najwolniejszy jedna trzecia.
                        val fill = if (lap.isFull)
                            1f - 0.66f * ((pace - fastest) / span).toFloat() else 0.25f
                        val color = lap.hrAvg?.let { zoneColor(RunAnalysis.zoneOf(it, maxHr)) }
                            ?: fallback
                        drawRoundRect(
                            color = color.copy(alpha = if (lap.isFull) 0.85f else 0.4f),
                            size = Size(size.width * fill.coerceIn(0.08f, 1f), size.height),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                    }
                }
                Text(
                    if (pace != null) paceText(pace) else "-",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
                )
                // Najszybszy kilometr nazwany wprost - przy podobnych słupkach
                // trzeba by go szukać po liczbach.
                if (lap.isFull && pace == fastest && full.size >= 2) {
                    Text(
                        "najszybszy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
                    )
                }
                if (!lap.isFull) {
                    Text(
                        String.format(Locale.getDefault(), "%.0f m", lap.meters),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)
                    )
                }
            }
            Text(
                lap.hrAvg?.let { "${it.toInt()}" } ?: "-",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp).padding(start = 10.dp)
            )
        }
    }
}

// ---- Tętno ---------------------------------------------------------------

@Composable
private fun HeartRateChart(samples: List<RunAnalysis.Sample>, maxHr: Double) {
    val lo = samples.minOf { it.value }
    val hi = samples.maxOf { it.value }
    val span = (hi - lo).coerceAtLeast(1.0)
    val t0 = samples.first().at
    val tSpan = (samples.last().at - t0).coerceAtLeast(1L).toDouble()
    val surface = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        fun x(at: Long) = ((at - t0) / tSpan * size.width).toFloat()
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height * 0.84) - 14).toFloat()

        listOf(0.6, 0.7, 0.8, 0.9).forEach { pct ->
            val v = maxHr * pct
            if (v in lo..hi) {
                drawLine(
                    Color.White.copy(alpha = 0.07f),
                    Offset(0f, y(v)), Offset(size.width, y(v)), 1f
                )
            }
        }

        // Wypełnienie pod krzywą - gradient robi różnicę między wykresem
        // a kreską, a przy ciemnym tle nic nie zasłania.
        val area = Path().apply {
            moveTo(x(samples.first().at), size.height)
            samples.forEach { lineTo(x(it.at), y(it.value)) }
            lineTo(x(samples.last().at), size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(Color(0xFFD9654B).copy(alpha = 0.28f), Color.Transparent)
            )
        )

        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            drawLine(
                zoneColor(RunAnalysis.zoneOf(a.value, maxHr)),
                Offset(x(a.at), y(a.value)), Offset(x(b.at), y(b.value)), 4.5f
            )
        }

        val avg = samples.sumOf { it.value } / samples.size
        val dashes = Path().apply {
            var px = 0f
            while (px < size.width) {
                moveTo(px, y(avg)); lineTo((px + 9f).coerceAtMost(size.width), y(avg)); px += 18f
            }
        }
        drawPath(dashes, surface.copy(alpha = 0.5f), style = Stroke(width = 2f))
    }
}


@Composable
private fun ZoneBar(zone: Int, seconds: Long, fraction: Float) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            zoneName(zone),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(zoneColor(zone), size = Size(size.width * fraction, size.height))
            }
        }
        Text(
            durationText(seconds / 60),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp).padding(start = 10.dp)
        )
    }
}

// ---- Wspólne -------------------------------------------------------------

/**
 * Nazwy stref zamiast numerów.
 *
 * „Tlenowa" mówi, co się działo z ciałem. „S3" wymaga pamiętania tabelki.
 */
private fun zoneName(zone: Int): String = when (zone) {
    1 -> "Regeneracja"
    2 -> "Spokojna"
    3 -> "Tlenowa"
    4 -> "Progowa"
    else -> "Maksymalna"
}

private fun zoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF5B8FD9)
    2 -> Color(0xFF4FA3A3)
    3 -> Color(0xFF7FB069)
    4 -> Color(0xFFE0A458)
    else -> Color(0xFFD9654B)
}

private fun paceText(minPerKm: Double): String {
    val total = (minPerKm * 60).toInt()
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

private fun durationText(min: Long): String =
    if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
