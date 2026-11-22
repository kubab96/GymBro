package pl.gymbro.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.gymbro.ble.ScaleReading
import pl.gymbro.ble.ScaleScanner
import pl.gymbro.data.BodyFat
import pl.gymbro.data.BodyMetric
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.MeasurementSite
import pl.gymbro.data.MetricsRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.UserProfile

/**
 * Kreator pomiaru: masa z wagi, potem obwody, potem zapis - jako jedna sesja.
 *
 * Wszystko zapisuje się ze **wspólnym znacznikiem czasu**, dzięki czemu masa
 * i obwody z tego samego poranka są jednym pomiarem, a nie trzema osobnymi
 * wpisami o zbliżonych godzinach. Przy porównywaniu zmian w czasie to różnica
 * między danymi, które da się zestawić, a zbiorem luźnych liczb.
 *
 * Składu ciała nie wpisuje się tutaj ręcznie - wynika z obwodów.
 */
private enum class FlowStep { SCALE, GIRTHS, SUMMARY }

@Composable
fun MeasurementFlow(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scanner = remember { ScaleScanner(context) }
    val bodyRepo = remember { BodyRepository(context) }
    val metricsRepo = remember { MetricsRepository(context) }
    val profileRepo = remember { ProfileRepository(context) }

    var step by remember { mutableStateOf(FlowStep.SCALE) }

    // Pomiar to kreator, więc wstecz cofa o krok, a nie zamyka wszystkiego -
    // wpisane obwody przepadłyby razem z nim.
    ScreenBackHandler {
        when (step) {
            FlowStep.SCALE -> onCancel()
            FlowStep.GIRTHS -> step = FlowStep.SCALE
            FlowStep.SUMMARY -> step = FlowStep.GIRTHS
        }
    }
    var weightKg by remember { mutableStateOf<Double?>(null) }
    var impedance by remember { mutableStateOf<Double?>(null) }
    var source by remember { mutableStateOf("waga BLE") }
    var measuredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    // Data wybrana ręcznie. null znaczy „dzisiaj, teraz" - i wtedy liczy się
    // chwila odczytu z wagi, nie chwila otwarcia kreatora.
    //
    // Pomiar wstecz miał osobne okno pod osobnym przyciskiem, bez wagi i bez
    // kroku z obwodami na rysunku. Teraz to ten sam kreator z inną datą.
    var pickedAt by remember { mutableStateOf<Long?>(null) }

    val girths = remember { mutableStateMapOf<MeasurementSite, String>() }
    var previous by remember { mutableStateOf<Map<MeasurementSite, BodyMetric>>(emptyMap()) }
    var profile by remember { mutableStateOf(UserProfile()) }

    val stable by scanner.lastStable.collectAsState()
    val scanning by scanner.scanning.collectAsState()
    val live by scanner.live.collectAsState()
    val scaleName by scanner.scaleName.collectAsState()

    // Czy ostatni odczyt potwierdziła waga, czy tylko zamilkła po nim.
    var certain by remember { mutableStateOf(true) }

    // Zgoda na Bluetooth była dotąd zakładana z góry: kreator wołał skan bez
    // pytania, więc na telefonie bez zgody kończył się wyjątkiem, a nie pomiarem.
    val perms = remember { ScaleScanner.permissions() }
    var granted by remember {
        mutableStateOf(
            perms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var bluetoothOn by remember { mutableStateOf(scanner.isBluetoothOn()) }

    fun listen(rejected: ScaleReading? = null) {
        bluetoothOn = scanner.isBluetoothOn()
        if (granted && bluetoothOn) scanner.startScan(rejected = rejected, autoConnect = true)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        listen()
    }

    LaunchedEffect(Unit) {
        // Skan przed bazą: waga nadaje ustalony wynik tylko kilka sekund,
        // a wczytanie profilu i obwodów potrafiło zjeść pierwszą z nich.
        if (granted) listen() else permLauncher.launch(perms)
        previous = withContext(Dispatchers.IO) { metricsRepo.latestPerSite() }
        profile = withContext(Dispatchers.IO) { profileRepo.get() }
    }

    // Nasłuch i połączenie muszą się skończyć razem z kreatorem - inaczej
    // blokują wagę dla aplikacji producenta i zjadają baterię.
    DisposableEffect(Unit) { onDispose { scanner.finish() } }

    // Odczyt tylko zatrzymuje nasłuch i czeka na potwierdzenie. Automatyczne
    // przejście dalej odbierało możliwość odrzucenia pomiaru, który wyszedł źle -
    // a przy wadze zdarza się stanąć nie na środku albo zejść za wcześnie.
    LaunchedEffect(stable) {
        val s = stable ?: return@LaunchedEffect
        if (step != FlowStep.SCALE) return@LaunchedEffect
        weightKg = s.weightKg
        impedance = s.impedanceOhm
        source = "waga BLE"
        certain = s.certain
        measuredAt = System.currentTimeMillis()
        scanner.stopScan()
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (step) {
                    FlowStep.SCALE -> "Pomiar · 1 z 3"
                    FlowStep.GIRTHS -> "Pomiar · 2 z 3"
                    FlowStep.SUMMARY -> "Pomiar · 3 z 3"
                },
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onCancel) { Text("Anuluj") }
        }

        StepBar(step)

        // Data nad krokami, nie w ostatnim: kto dopisuje pomiar sprzed
        // tygodnia, ten wie o tym od początku, a na podsumowaniu łatwo ją
        // przeoczyć i zapisać stary wynik z dzisiejszą datą.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Data pomiaru",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    pickedAt?.let { dateOnly(it) + ", " + timeOnly(it) } ?: "dzisiaj",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (pickedAt != null) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(onClick = {
                pickDate(context, pickedAt ?: System.currentTimeMillis()) { day ->
                    if (isToday(day)) {
                        // Dzisiejsza data to powrót do ustawienia domyślnego,
                        // nie „dzisiaj o godzinie, o której otwarto okno".
                        pickedAt = null
                    } else {
                        pickedAt = day
                        // Przy pomiarze wstecz godzina też jest do podania -
                        // bieżąca rzadko się zgadza. Anulowanie zostawia ją.
                        pickTime(context, day) { pickedAt = it }
                    }
                }
            }) { Text(if (pickedAt == null) "Wybierz datę" else "Zmień datę") }
        }
        Spacer(Modifier.height(6.dp))

        Crossfade(targetState = step, label = "krok") { current ->
            when (current) {
                FlowStep.SCALE -> ScaleStep(
                    scanning = scanning,
                    granted = granted,
                    bluetoothOn = bluetoothOn,
                    liveKg = live?.weightKg,
                    scaleName = scaleName,
                    certain = certain,
                    onGrant = { permLauncher.launch(perms) },
                    capturedKg = weightKg,
                    onManual = { kg ->
                        weightKg = kg
                        impedance = null
                        source = "ręcznie"
                        certain = true
                        measuredAt = System.currentTimeMillis()
                        scanner.stopScan()
                    },
                    onRetry = {
                        weightKg = null
                        impedance = null
                        // Odrzucony odczyt idzie do skanera: waga nadaje go jeszcze
                        // chwilę i bez tego wracał w pierwszej sekundzie nasłuchu.
                        listen(rejected = stable)
                    },
                    onAccept = { step = FlowStep.GIRTHS }
                )

                FlowStep.GIRTHS -> GirthsStep(
                    girths = girths,
                    previous = previous,
                    sex = profile.sex,
                    onNext = { step = FlowStep.SUMMARY }
                )

                FlowStep.SUMMARY -> SummaryStep(
                    weightKg = weightKg,
                    girths = girths,
                    profile = profile,
                    onSave = {
                        val kg = weightKg
                        val at = pickedAt ?: measuredAt
                        if (kg != null) scope.launch {
                            withContext(Dispatchers.IO) {
                                bodyRepo.record(
                                    weightKg = kg,
                                    impedanceOhm = impedance,
                                    source = source,
                                    at = at
                                )
                                // Ten sam znacznik czasu co masa - jeden pomiar.
                                girths.forEach { (site, text) ->
                                    text.trim().replace(',', '.').toDoubleOrNull()
                                        ?.takeIf { it in 10.0..250.0 }
                                        ?.let { metricsRepo.record(site, it, at) }
                                }
                            }
                            onDone()
                        }
                    }
                )
            }
        }
    }
}

/** Czy znacznik czasu wypada dzisiaj - według kalendarza telefonu, nie UTC. */
private fun isToday(ms: Long): Boolean {
    val a = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val b = java.util.Calendar.getInstance()
    return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
}

// ---- Pasek postępu -------------------------------------------------------

@Composable
private fun StepBar(step: FlowStep) {
    val index = when (step) {
        FlowStep.SCALE -> 0
        FlowStep.GIRTHS -> 1
        FlowStep.SUMMARY -> 2
    }
    // Kolory muszą być odczytane TUTAJ: lambda rysująca w Canvas nie jest
    // kontekstem kompozycji, więc MaterialTheme nie jest w niej dostępny.
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { i ->
            val color = if (i <= index) active else inactive
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = size.height
                    )
                }
            }
        }
    }
}

// ---- Krok 1: waga --------------------------------------------------------

@Composable
private fun ScaleStep(
    scanning: Boolean,
    granted: Boolean,
    bluetoothOn: Boolean,
    liveKg: Double?,
    scaleName: String?,
    certain: Boolean,
    onGrant: () -> Unit,
    capturedKg: Double?,
    onManual: (Double) -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit
) {
    // Odczyt złapany - czekamy na decyzję, nie idziemy dalej sami.
    if (capturedKg != null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("%.2f".format(capturedKg), fontSize = 54.sp)
                    Text(
                        "kilogramów",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Zgadza się?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            if (certain) {
                Text(
                    "Jeśli zszedłeś za wcześnie, zmierz jeszcze raz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Waga zgasła bez potwierdzenia wyniku: to ostatnia liczba, jaka
                // dotarła, i mogła nam umknąć końcowa korekta o jedną działkę.
                // Powód zostaje w komentarzu - na ekranie wystarczy, co zrobić.
                Text(
                    "Porównaj z wyświetlaczem wagi. Jeśli się różni, zmierz jeszcze raz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) { Text("Zmierz ponownie") }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) { Text("Dalej") }
            }
        }
        return
    }

    // Nasłuch.
    var manual by remember { mutableStateOf("") }

    // Przewijane i z imePadding: pole ręcznego wpisu stoi na samym dole,
    // czyli dokładnie tam, gdzie wyjeżdża klawiatura. Odkąd nad krokami jest
    // wiersz z datą, przy wpisywaniu wstecz to pole jest główną drogą,
    // a nie zapasową.
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        PulsingScale(active = scanning)
        Spacer(Modifier.height(20.dp))

        // Liczba z wagi idzie na ekran od pierwszej ramki, jeszcze zanim się
        // ustali. Pusty ekran aż do wyniku nie odróżniał "waga liczy" od
        // "telefon nic nie słyszy" - a to drugie zdarzało się częściej.
        val context = LocalContext.current
        val title = when {
            !granted -> "Brak zgody na Bluetooth"
            !bluetoothOn -> "Bluetooth jest wyłączony"
            !scanning -> "Nasłuch zatrzymany"
            liveKg != null -> "%.2f kg".format(liveKg)
            scaleName != null -> "Waga znaleziona"
            else -> "Stań na wadze"
        }
        val hint = when {
            !granted -> "Bez niej telefon nie usłyszy wagi."
            !bluetoothOn -> "Włącz go i naciśnij Szukaj wagi."
            !scanning -> "Naciśnij Szukaj wagi."
            liveKg != null -> "Stój nieruchomo."
            // Bez nazwy urządzenia: „Yoda0" nic nikomu nie mówi.
            scaleName != null -> "Stań na niej i stój nieruchomo."
            else -> "Wynik pojawi się tu sam."
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))
        when {
            !granted -> Button(onClick = onGrant) { Text("Zezwól") }
            !bluetoothOn -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                ) { Text("Ustawienia") }
                Button(onClick = onRetry) { Text("Szukaj wagi") }
            }
            !scanning -> Button(onClick = onRetry) { Text("Szukaj wagi") }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "albo wpisz masę ręcznie",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    manual.trim().replace(',', '.').toDoubleOrNull()
                        ?.takeIf { it in 20.0..300.0 }
                        ?.let(onManual)
                }
            ) { Text("Użyj") }
        }
    }
}

/**
 * Pulsujące kręgi - sygnał, że aplikacja nasłuchuje i nic nie zawiesiło się
 * w tle. Przy czekaniu na wagę nie da się pokazać postępu, bo nie wiadomo,
 * ile potrwa; można za to pokazać, że urządzenie żyje.
 */
@Composable
private fun PulsingScale(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "puls")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "faza"
    )

    val ring = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(Modifier.size(170.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension / 2

        if (active) {
            // Trzy fale przesunięte w czasie dają wrażenie ciągłego nasłuchu.
            repeat(3) { i ->
                val p = (phase + i / 3f) % 1f
                drawCircle(
                    color = ring.copy(alpha = (1f - p) * 0.45f),
                    radius = maxR * (0.35f + 0.65f * p),
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Schematyczna waga: płyta i wyświetlacz.
        val plate = maxR * 0.42f
        drawRoundRect(
            color = body.copy(alpha = 0.35f),
            topLeft = Offset(center.x - plate, center.y - plate * 0.72f),
            size = androidx.compose.ui.geometry.Size(plate * 2, plate * 1.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(plate * 0.22f)
        )
        drawRoundRect(
            color = ring.copy(alpha = 0.85f),
            topLeft = Offset(center.x - plate * 0.45f, center.y - plate * 0.18f),
            size = androidx.compose.ui.geometry.Size(plate * 0.9f, plate * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(plate * 0.08f)
        )
    }
}

// ---- Krok 2: obwody ------------------------------------------------------

@Composable
private fun GirthsStep(
    girths: MutableMap<MeasurementSite, String>,
    previous: Map<MeasurementSite, BodyMetric>,
    sex: pl.gymbro.data.Sex,
    onNext: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("Podaj obwody", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MeasurementSite.entries.forEach { site ->
                AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyDiagram(site, Modifier.size(62.dp, 94.dp))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(site.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                site.howTo(sex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = girths[site] ?: "",
                                onValueChange = { girths[site] = it },
                                label = { Text("cm") },
                                // Poprzedni wynik jako podpowiedź, a NIE jako wartość
                                // wstępna: wypełnione pole zbyt łatwo zapisać bez zmiany
                                // i zafałszować historię pomiarem, którego nie było.
                                placeholder = {
                                    previous[site]?.let {
                                        Text("ostatnio %.1f".format(it.valueCm))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Dalej") }
        }
    }
}

// ---- Krok 3: podsumowanie ------------------------------------------------

@Composable
private fun SummaryStep(
    weightKg: Double?,
    girths: Map<MeasurementSite, String>,
    profile: UserProfile,
    onSave: () -> Unit
) {
    fun cm(site: MeasurementSite): Double? =
        girths[site]?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 10.0..250.0 }

    val bf = BodyFat.navy(
        profile.sex,
        profile.heightCm,
        cm(MeasurementSite.WAIST),
        cm(MeasurementSite.NECK),
        cm(MeasurementSite.HIPS)
    )

    Column(Modifier.fillMaxSize()) {
        Text("Zapisz pomiar", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("%.2f kg".format(weightKg ?: 0.0), fontSize = 36.sp)
                    Text(
                        "masa ciała",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (bf != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "%.1f %%".format(bf),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "tkanka tłuszczowa · ${BodyFat.comment(profile.sex, bf)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val missing = BodyFat.missing(
                            profile.sex, profile.heightCm,
                            cm(MeasurementSite.WAIST), cm(MeasurementSite.NECK),
                            cm(MeasurementSite.HIPS)
                        )
                        if (missing.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Do tkanki tłuszczowej brakuje: " +
                                    missing.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val filled = MeasurementSite.entries.mapNotNull { s -> cm(s)?.let { s to it } }
            if (filled.isEmpty()) {
                Text(
                    "Bez obwodów.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                filled.forEach { (site, value) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(site.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f cm".format(value))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSave,
                enabled = weightKg != null,
                modifier = Modifier.weight(1f)
            ) { Text("Zapisz pomiar") }
        }
    }
}
