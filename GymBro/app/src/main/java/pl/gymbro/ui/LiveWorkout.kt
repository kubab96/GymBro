package pl.gymbro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.gymbro.data.Inventory
import pl.gymbro.data.PlanItem
import pl.gymbro.data.Session
import pl.gymbro.data.SetEntry
import pl.gymbro.data.roundKg
import pl.gymbro.gym.LiveSession
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.Loading
import pl.gymbro.gym.PlateCalculator
import pl.gymbro.gym.ProgressionEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trwający trening na telefonie: jedno ćwiczenie na ekran, jedna seria naraz.
 *
 * Tak samo jak na zegarku. Lista z jedną rozwiniętą kartą pokazywała
 * wszystko naraz, a liczby, które się w tej chwili zmienia - ciężar
 * i powtórzenia - były tej samej wielkości co reszta. Teraz ćwiczenie
 * zajmuje cały ekran, kolejne są obok (przesunięcie palcem albo kreski
 * pod nagłówkiem), a przerwa i zapas powtórzeń siedzą w panelu na dole.
 */


@Composable
internal fun ColumnScope.ActiveSession(
    session: Session,
    states: List<ExerciseState>,
    activeIndex: Int,
    inventory: Inventory,
    onSelect: (Int) -> Unit,
    onSaveSet: (ExerciseState, Double, Int, Int?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit,
    onPause: (Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val paused = session.pausedAt != null

    // Zegar sesji trzyma MINUTY, nie milisekundy - co sekundę przerysowywałby
    // cały ekran razem z ćwiczeniem, a zmienia się raz na minutę. Kluczem
    // jest cała sesja, nie numer: po wstrzymaniu przychodzi nowy obiekt
    // z inną chwilą pauzy, a pętla na starym liczyłaby dalej.
    var elapsedMin by remember { mutableLongStateOf(session.elapsedMs() / 60_000) }
    LaunchedEffect(session) {
        while (true) {
            val m = session.elapsedMs() / 60_000
            if (m != elapsedMin) elapsedMin = m
            delay(5_000)
        }
    }

    // Wstrzymany trening nie przyjmuje serii - ani zapisu, ani końca przerwy.
    // Seria we wstrzymanym treningu to prawie zawsze zapomniane wznowienie,
    // a zapisana szła do dziennika z czasem, który stał. Przeglądać wolno.
    var askResume by remember { mutableStateOf(false) }
    fun guarded(action: () -> Unit) {
        if (paused) askResume = true else action()
    }
    if (askResume) {
        AlertDialog(
            onDismissRequest = { askResume = false },
            title = { Text("Trening wstrzymany") },
            text = { Text("Czy chcesz wznowić trening?") },
            confirmButton = {
                TextButton(onClick = { askResume = false; onPause(false) }) { Text("Wznów") }
            },
            dismissButton = { TextButton(onClick = { askResume = false }) { Text("Anuluj") } }
        )
    }

    // Przerwa: chwila końca odliczania. Null - przerwy nie ma.
    var restEndsAt by remember { mutableStateOf<Long?>(null) }
    var restTotalSec by remember { mutableIntStateOf(0) }
    // Ćwiczenie, którego ostatnia seria czeka na zapas. Pytanie żyje osobno
    // od odliczania: znika po odpowiedzi, a przerwa liczy dalej.
    var rirItemId by remember { mutableStateOf<Long?>(null) }

    // Początek bieżącej serii - od niego liczy się czas serii. Na starcie
    // ostatnia zapisana seria w treningu (także z zegarka), a bez niej
    // początek treningu.
    val lastDoneAt = states.maxOfOrNull { st -> st.doneSets.maxOfOrNull { it.doneAt } ?: 0L } ?: 0L
    var setStartedAt by remember(session.id) { mutableLongStateOf(maxOf(session.startedAt, lastDoneAt)) }
    LaunchedEffect(lastDoneAt) {
        // Seria z zegarka też zaczyna liczenie od nowa.
        if (lastDoneAt > setStartedAt) setStartedAt = lastDoneAt
    }

    // Pauza zamraża oba zegary, a wznowienie przesuwa je o czas postoju -
    // także wtedy, gdy wstrzymano na zegarku.
    var pausedSeen by remember { mutableStateOf(session.pausedAt) }
    LaunchedEffect(session.pausedAt) {
        val was = pausedSeen
        if (was != null && session.pausedAt == null) {
            val d = System.currentTimeMillis() - was
            restEndsAt = restEndsAt?.plus(d)
            setStartedAt += d
        }
        pausedSeen = session.pausedAt
    }

    val allComplete = states.isNotEmpty() && states.all { it.complete }

    // Po ostatniej serii ekran sam przechodzi do podsumowania - bez tego nic
    // nie sygnalizowało końca i ekran zapraszał do dokładania serii.
    var dismissedSummary by remember { mutableStateOf(false) }
    LaunchedEffect(allComplete) { if (!allComplete) dismissedSummary = false }

    // Seria z zegarka znaczy, że trening trwa dalej - schodzimy z podsumowania,
    // żeby telefon pokazywał to samo co zegarek.
    var seenExternal by remember { mutableIntStateOf(ExternalSet.version) }
    LaunchedEffect(ExternalSet.version) {
        if (ExternalSet.version != seenExternal) {
            seenExternal = ExternalSet.version
            dismissedSummary = true
        }
    }

    // "Zakończ" prowadzi do podsumowania, a nie zamyka sesji od razu:
    // zamknięcie jest nieodwracalne, więc ostatnie dotknięcie pada na ekranie,
    // który pokazuje, co właściwie kończysz.
    var requestedFinish by remember { mutableStateOf(false) }
    val showSummary = requestedFinish || (allComplete && !dismissedSummary)

    // Wykrycie świeżo domkniętego ćwiczenia: po zbiorach, nie po liczbie,
    // żeby skasowana seria nie udawała domknięcia innego ćwiczenia.
    var justCompleted by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var completedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(states) {
        val nowComplete = states.filter { it.complete }.map { it.item.id }.toSet()
        val newly = if (initialized) (nowComplete - completedIds).firstOrNull() else null
        completedIds = nowComplete
        initialized = true
        if (newly != null && !allComplete) {
            val name = states.firstOrNull { it.item.id == newly }?.item?.exerciseName
            if (name != null) justCompleted = name to states.getOrNull(activeIndex)?.item?.exerciseName
        }
    }
    LaunchedEffect(justCompleted) {
        if (justCompleted != null) {
            delay(2200)
            justCompleted = null
        }
    }

    if (showSummary) {
        SessionDone(
            states = states,
            elapsedMin = elapsedMin,
            allComplete = allComplete,
            inventory = inventory,
            onAddSet = { st, w, r -> guarded { onSaveSet(st, w, r, null) } },
            onDeleteSet = onDeleteSet,
            onUpdateSet = onUpdateSet,
            onFinish = onFinish,
            onBack = {
                dismissedSummary = true
                requestedFinish = false
                // Przerwa po ostatniej serii minęła, zanim obejrzałeś
                // podsumowanie - wracasz bez wiszącego odliczania.
                restEndsAt = null
                rirItemId = null
            }
        )
        return
    }

    if (states.isEmpty()) {
        Text("Wczytuję…", Modifier.padding(top = 16.dp))
        return
    }

    // Ćwiczenie, z którego ekran właśnie odjeżdża po ostatniej serii. Do
    // końca przejścia jego Zapisz nic nie robi: w tej chwili przycisk był
    // jeszcze pod palcem i drugie naciśnięcie dopisywało serię ponad plan
    // do ćwiczenia, które przed chwilą się skończyło.
    var leaving by remember { mutableStateOf<Long?>(null) }
    val pager = rememberPagerState(initialPage = activeIndex.coerceIn(0, states.lastIndex)) { states.size }
    // Dwie drogi w jedną stronę: ekran idzie za aktywnym ćwiczeniem (po
    // domknięciu przeskakuje na następne), a przesunięcie palcem ustawia
    // aktywne. Porównanie przed każdym ruchem nie daje im się zapętlić.
    LaunchedEffect(activeIndex) {
        if (activeIndex in states.indices && pager.currentPage != activeIndex) {
            try {
                // Po domknięciu ćwiczenia chwila na zobaczenie potwierdzenia,
                // zanim ekran odjedzie - przeskok w tej samej klatce co zapis
                // wyglądał, jakby seria wylądowała w innym ćwiczeniu.
                if (states.getOrNull(pager.currentPage)?.complete == true) delay(700)
                pager.animateScrollToPage(activeIndex, animationSpec = tween(450, easing = FastOutSlowInEasing))
            } finally {
                leaving = null
            }
        }
    }
    // Zabezpieczenie: gdy przejścia nie było (nie ma już dokąd iść, trening
    // się skończył), blokada nie może wisieć.
    LaunchedEffect(leaving) {
        if (leaving != null) {
            delay(3000)
            leaving = null
        }
    }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != activeIndex) onSelect(pager.settledPage)
    }
    val scope = rememberCoroutineScope()
    val goTo: (Int) -> Unit = { i ->
        scope.launch { pager.animateScrollToPage(i, animationSpec = tween(450, easing = FastOutSlowInEasing)) }
    }

    // ---- Nagłówek: czas treningu, wstrzymanie, koniec ----
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (paused) "WSTRZYMANY" else "TRENING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                workoutLength(elapsedMin),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
        }
        OutlinedButton(
            onClick = { onPause(!paused) },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (paused) "Wznów" else "Wstrzymaj")
        }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = { requestedFinish = true }) { Text("Zakończ") }
    }
    // Poprzednie i następne nad kreskami, a nie na dole ćwiczenia: na dole
    // trzeba było je przewijać spod panelu przerwy, a przejście między
    // ćwiczeniami to coś, czego szuka się wzrokiem od góry, razem z postępem.
    ExerciseNav(
        prev = states.getOrNull(pager.currentPage - 1)?.item?.exerciseName,
        next = states.getOrNull(pager.currentPage + 1)?.item?.exerciseName,
        onPrev = { goTo(pager.currentPage - 1) },
        onNext = { goTo(pager.currentPage + 1) }
    )
    ExerciseStrip(states, pager.currentPage, onPick = goTo)

    val resting = restEndsAt != null
    val rirState = states.firstOrNull { it.item.id == rirItemId }
        ?.takeIf { it.doneSets.lastOrNull()?.rir == null }
    val rirSet = rirState?.doneSets?.lastOrNull()
    val panelShown = (resting || rirSet != null) && justCompleted == null

    Box(Modifier.fillMaxWidth().weight(1f)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { states[it].item.id },
            pageSpacing = 20.dp
        ) { page ->
            // Sąsiednie ćwiczenie wjeżdża przygaszone i trochę mniejsze - bez
            // tego przesunięcie wyglądało jak przewinięcie jednej długiej
            // strony i nie było widać, że to już inne ćwiczenie.
            val offset = kotlin.math.abs(
                (pager.currentPage - page) + pager.currentPageOffsetFraction
            ).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - 0.6f * offset
                    val s = 1f - 0.08f * offset
                    scaleX = s
                    scaleY = s
                }
            ) {
                ExercisePage(
                    state = states[page],
                    position = "ĆWICZENIE ${page + 1} Z ${states.size}",
                    inventory = inventory,
                    // Czas serii tylko na bieżącym ćwiczeniu i tylko poza przerwą -
                    // w przerwie liczy panel na dole.
                    clock = if (page == pager.currentPage && !resting && !states[page].complete) {
                        { SetClock(setStartedAt, session.pausedAt) }
                    } else null,
                    // Zapas pod panelem, żeby Zapisz dało się przewinąć nad niego.
                    bottomPad = if (panelShown) 190.dp else 24.dp,
                    locked = leaving == states[page].item.id,
                    // Dokąd prowadzi "Dalej" ze skończonego ćwiczenia - ta sama
                    // reguła, której ekran używa po ostatniej serii.
                    nextName = LiveSession.follow(states.map { it.complete }, page, page, false)
                        .takeIf { it != page && !states[it].complete }
                        ?.let { states[it].item.exerciseName },
                    onGoNext = {
                        val t = LiveSession.follow(states.map { it.complete }, page, page, false)
                        if (t != page) goTo(t)
                    },
                    onSummary = {
                        dismissedSummary = false
                        requestedFinish = true
                    },
                    onSave = { w, r ->
                        guarded {
                            val st = states[page]
                            if (leaving == st.item.id) return@guarded
                            // Ta seria domyka ćwiczenie, a jest jeszcze inne do zrobienia -
                            // blokada od razu, zanim baza zdąży odpowiedzieć.
                            val closes = !st.complete && st.doneSets.size + 1 >= st.item.sets
                            if (closes && states.any { it.item.id != st.item.id && !it.complete }) {
                                leaving = st.item.id
                            }
                            onSaveSet(st, w, r, null)
                            rirItemId = st.item.id
                            val now = System.currentTimeMillis()
                            if (st.item.hasRest) {
                                restTotalSec = st.item.restSec
                                restEndsAt = now + st.item.restSec * 1000L
                            } else {
                                // Bez przerwy następna seria zaczyna się od razu.
                                restEndsAt = null
                                setStartedAt = now
                            }
                        }
                    },
                    onDeleteSet = onDeleteSet,
                    onUpdateSet = onUpdateSet
                )
            }
        }

        val done = justCompleted
        DoneOverlay(
            exerciseName = done?.first,
            next = done?.second,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        )

        // Panel przerwy leży na ekranie, a nie w układzie: wchodząc w układ
        // spychał ćwiczenie w dół dokładnie wtedy, gdy patrzyłeś na serię.
        if (panelShown) {
            val restState = states.getOrNull(pager.currentPage)
            RestPanel(
                endsAt = restEndsAt,
                totalSec = restTotalSec,
                frozenAt = session.pausedAt,
                next = restState,
                inventory = inventory,
                rirSet = rirSet,
                onRir = { set, v ->
                    onUpdateSet(set.id, set.weightKg, set.reps, v)
                    // Pytanie znika od razu, nie dopiero po przeładowaniu z bazy.
                    rirItemId = null
                },
                onSkipRir = { rirItemId = null },
                onTimeUp = { at ->
                    restEndsAt = null
                    setStartedAt = at
                },
                onSkipRest = {
                    guarded {
                        restEndsAt = null
                        setStartedAt = System.currentTimeMillis()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

/**
 * Czas bieżącej serii, tykający co sekundę - osobno, żeby co sekundę
 * przerysowywał się sam napis, a nie całe ćwiczenie. Na pauzie stoi.
 */
@Composable
private fun SetClock(startedAt: Long, frozenAt: Long?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(frozenAt) {
        if (frozenAt != null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    val sec = (((frozenAt ?: now) - startedAt) / 1000).toInt().coerceAtLeast(0)
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Timer, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            clock(sec),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Dwa przyciski przejścia między ćwiczeniami. Przy pierwszym i ostatnim
 * ćwiczeniu brakujący przycisk zostaje jako puste miejsce, żeby drugi nie
 * skakał na środek.
 */
@Composable
private fun ExerciseNav(prev: String?, next: String?, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (prev != null) NavButton("Poprzednie", prev, forward = false, onPrev, Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
        if (next != null) NavButton("Następne", next, forward = true, onNext, Modifier.weight(1f))
        else Spacer(Modifier.weight(1f))
    }
}

/**
 * Przycisk z kierunkiem i nazwą ćwiczenia pod spodem. "Poprzednie ćwiczenie"
 * w jednej linii nie mieściło się w pół ekranu i ucinało się na "ćwicze…" -
 * a nazwa mówi więcej niż słowo "ćwiczenie".
 */
@Composable
private fun NavButton(label: String, name: String, forward: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        if (!forward) Icon(Icons.Filled.ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp))
        Column(
            Modifier.weight(1f),
            horizontalAlignment = if (forward) Alignment.End else Alignment.Start
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (forward) Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/**
 * Kreski ćwiczeń pod nagłówkiem: zrobione turkusowe, bieżące niebieskie,
 * reszta szara. Postęp całego treningu i skok do ćwiczenia jednym dotknięciem.
 */
@Composable
private fun ExerciseStrip(states: List<ExerciseState>, current: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        states.forEachIndexed { i, st ->
            val color = when {
                i == current -> MaterialTheme.colorScheme.primary
                st.complete -> MaterialTheme.colorScheme.secondary
                st.doneSets.isNotEmpty() -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            // Pole dotyku wyższe od samej kreski - kreska ma 5 dp, palec nie.
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .tap(RoundedCornerShape(6.dp)) { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(color)
                )
            }
        }
    }
}

/** Jedno ćwiczenie na cały ekran. */
@Composable
private fun ExercisePage(
    state: ExerciseState,
    position: String,
    clock: (@Composable () -> Unit)?,
    inventory: Inventory,
    bottomPad: androidx.compose.ui.unit.Dp,
    locked: Boolean,
    nextName: String?,
    onGoNext: () -> Unit,
    onSummary: () -> Unit,
    onSave: (Double, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit
) {
    val item = state.item
    val mode = LoadMode.of(item.equipment, inventory)

    // Ta sama reguła co na zegarku (LiveSession.nextWeight): ostatnia seria,
    // a przed pierwszą - ciężar z planu. Ekran liczył dotąd z propozycji
    // progresji i telefon z zegarkiem pokazywały przy tej samej serii
    // dwie różne liczby.
    var weight by remember(item.id, state.doneSets.size) { mutableStateOf(state.nextWeight) }
    var reps by remember(item.id, state.doneSets.size) { mutableStateOf(state.nextReps) }
    // Dotknięcie liczby otwiera wpisywanie: "w" ciężar, "r" powtórzenia.
    var typing by remember { mutableStateOf<String?>(null) }
    var editingSet by remember { mutableStateOf<SetEntry?>(null) }

    when (typing) {
        "w" -> NumberEntrySheet(
            title = item.exerciseName + " - ciężar",
            initial = weight,
            kind = NumberKind.WEIGHT,
            unit = item.equipment.weightUnitLabel,
            values = weightValues(mode, inventory, item.step),
            onDismiss = { typing = null },
            onPick = { weight = it; typing = null }
        )
        "r" -> NumberEntrySheet(
            title = item.exerciseName + " - powtórzenia",
            initial = reps.toDouble(),
            kind = NumberKind.COUNT,
            max = 100.0,
            onDismiss = { typing = null },
            onPick = { reps = it.toInt(); typing = null }
        )
    }

    val done = state.doneSets.size
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = bottomPad)
    ) {
        Text(
            position,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            item.exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            SetDots(planned = item.sets, done = done, current = !state.complete)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !state.complete -> "seria ${done + 1} z ${item.sets}"
                    else -> "zrobione"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            clock?.invoke()
        }
        // Skąd ten ciężar i co było ostatnio - tylko przed pierwszą serią,
        // potem liczy się to, co zrobiłeś przed chwilą.
        if (done == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                suggestionLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.suggestion.reason == ProgressionEngine.Reason.DELOAD ||
                    state.suggestion.reason == ProgressionEngine.Reason.STALLED
                ) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(14.dp))

        // Przy ćwiczeniu bez zadanego obciążenia ciężaru nie ma wcale -
        // seria zapisze się z zerem, bo ciężar nie jest tu informacją.
        if (item.hasTargetWeight) {
            BigStepper(
                caption = "CIĘŻAR · ${item.equipment.weightUnitLabel.uppercase()}",
                value = Loading.fmt(weight),
                onMinus = {
                    weight = if (mode != null) {
                        PlateCalculator.nextDown(weight, mode, inventory)?.totalKg ?: weight
                    } else roundKg((weight - item.step).coerceAtLeast(0.0))
                },
                onPlus = {
                    weight = if (mode != null) {
                        PlateCalculator.nextUp(weight, mode, inventory)?.totalKg ?: weight
                    } else roundKg(weight + item.step)
                },
                // Wpisany ręką ciężar dociąga się do tego, co da się złożyć
                // z talerzy - arkusz mówi, ile wyjdzie, zanim się zapisze.
                onValueClick = { typing = "w" }
            )
            Spacer(Modifier.height(10.dp))
        }

        BigStepper(
            caption = if (item.hasRepRange) "POWTÓRZENIA · ZAKRES ${item.repRange}" else "POWTÓRZENIA",
            value = "$reps",
            onMinus = { if (reps > 1) reps-- },
            onPlus = { reps++ },
            onValueClick = { typing = "r" }
        )

        Spacer(Modifier.height(14.dp))

        // Ćwiczenie z kompletem serii: główny przycisk prowadzi dalej, a seria
        // ponad plan to osobna, mniejsza decyzja. "Zapisz serię" na skończonym
        // ćwiczeniu dopisywało serie bez końca - na ostatnim ćwiczeniu i po
        // powrocie z podsumowania nie było dokąd przejść, więc nic go nie blokowało.
        if (state.complete && !locked) {
            Button(
                onClick = { if (nextName != null) onGoNext() else onSummary() },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (nextName != null) "Dalej: $nextName" else "Podsumowanie",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { onSave(weight, reps) }, modifier = Modifier.fillMaxWidth()) {
                Text("Dodaj serię ponad plan")
            }
        } else Button(
            onClick = { if (!locked) onSave(weight, reps) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(14.dp),
            colors = if (locked) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) else ButtonDefaults.buttonColors()
        ) {
            Text(
                if (locked) "Przechodzę dalej…" else "Zapisz serię",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.doneSets.isNotEmpty()) {
            GroupLabel("Zrobione")
            AppCard {
                state.doneSets.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Dotknięcie otwiera poprawkę. Kasowanie jednym dotknięciem
                    // zabierało całą serię za pomyłkę o jedno powtórzenie.
                    DoneSetRow(i + 1, s, item) { editingSet = s }
                }
            }
        } else if (state.lastTime.isNotEmpty()) {
            GroupLabel("Ostatnio")
            Text(
                state.lastTime.filter { !it.isWarmup }.joinToString("   ") {
                    if (item.hasTargetWeight) "${Loading.fmt(it.weightKg)} × ${it.reps}" else "${it.reps} powt."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    editingSet?.let { s ->
        EditSetDialog(
            entry = s,
            exerciseName = item.exerciseName,
            equipment = item.equipment,
            step = item.step,
            showWeight = item.hasTargetWeight,
            inventory = inventory,
            onDismiss = { editingSet = null },
            onSave = { w, r, v ->
                onUpdateSet(s.id, w, r, v)
                editingSet = null
            },
            onDelete = {
                onDeleteSet(s.id)
                editingSet = null
            }
        )
    }
}

@Composable
private fun DoneSetRow(nr: Int, s: SetEntry, item: PlanItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tap(RoundedCornerShape(0.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$nr",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(28.dp)
        )
        Text(
            if (item.hasTargetWeight) "${Loading.fmt(s.weightKg)} ${item.equipment.weightUnitLabel} × ${s.reps}"
            else "${s.reps} powt.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            s.rir?.let { "zapas ${rirLabel(it)}" } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Kropki serii: zrobione turkusowe, bieżąca w obwódce, reszta szara.
 * Serie ponad plan dochodzą jako kolejne turkusowe - tak samo jak na zegarku.
 */
@Composable
internal fun SetDots(planned: Int, done: Int, current: Boolean = false, size: Int = 10) {
    val total = maxOf(planned, done).coerceAtMost(12)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            val m = Modifier.size(size.dp).clip(CircleShape)
            when {
                i < done -> Box(m.background(MaterialTheme.colorScheme.secondary))
                current && i == done -> Box(
                    m.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
                else -> Box(m.background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

/**
 * Regulator z dużą liczbą w karcie: podpis, minus, liczba, plus.
 *
 * Liczba to jedyne, co się tu czyta z odległości wyciągniętej ręki, więc
 * jest największa na ekranie. Zwykły [Stepper] miał ją tej samej wielkości
 * co nazwę ćwiczenia.
 */
@Composable
internal fun BigStepper(
    caption: String,
    value: String,
    hint: String? = null,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: (() -> Unit)? = null
) {
    AppCard {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton(Icons.Filled.Remove, "Mniej", onMinus)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        value,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        // Lekkie tło mówi, że w liczbę da się dotknąć.
                        modifier = if (onValueClick != null) Modifier
                            .tap(RoundedCornerShape(12.dp), onClick = onValueClick)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp) else Modifier
                    )
                }
                StepButton(Icons.Filled.Add, "Więcej", onPlus)
            }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(60.dp)
            .tap(RoundedCornerShape(16.dp), onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
    }
}

/** Wybór zapasu: 0, 1, 2, 3+. Ponowne dotknięcie zdejmuje wybór, jak na zegarku. */
@Composable
internal fun RirPicker(value: Int?, onPick: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 1, 2, 3).forEach { v ->
            val m = Modifier.weight(1f).height(44.dp)
            if (value == v) {
                Button(
                    onClick = { onPick(null) }, modifier = m,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(rirLabel(v)) }
            } else {
                OutlinedButton(
                    onClick = { onPick(v) }, modifier = m,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(rirLabel(v)) }
            }
        }
    }
}

internal fun rirLabel(v: Int): String = if (v >= 3) "3+" else v.toString()

/**
 * Panel przy dolnej krawędzi: przerwa i pytanie o zapas.
 *
 * Nie okno: okno zasłaniało ćwiczenie, a w przerwie trzeba zobaczyć i ustawić
 * ciężar na następną serię. Dwie części żyją osobno. Odliczanie znika z końcem
 * przerwy - wtedy rusza czas serii. Zapas serii, którą właśnie skończyłeś,
 * znika po odpowiedzi; podaje się go tutaj, a nie przed zapisem, tak samo
 * jak na zegarku, bo przed serią nie wiadomo, ile zostanie w zapasie.
 *
 * Bez przycisków przesuwania przerwy: przerwa jest w planie, a w trakcie
 * kończy się ją przyciskiem Pomiń albo zapisem następnej serii.
 */
@Composable
private fun RestPanel(
    endsAt: Long?,
    totalSec: Int,
    frozenAt: Long?,
    next: ExerciseState?,
    inventory: Inventory,
    rirSet: SetEntry?,
    onRir: (SetEntry, Int?) -> Unit,
    onSkipRir: () -> Unit,
    onTimeUp: (Long) -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (endsAt != null) {
                RestCountdown(endsAt, totalSec, frozenAt, next, inventory, onTimeUp, onSkipRest)
            }
            if (rirSet != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = if (endsAt != null) 10.dp else 0.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ZAPAS W SERII ${rirSet.setIndex} · ${
                            if (rirSet.weightKg > 0.0) Loading.fmt(rirSet.weightKg) + " × " else ""
                        }${rirSet.reps}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // Bez przerwy panel trzyma się tylko na pytaniu - musi
                    // dać się zamknąć bez odpowiedzi.
                    if (endsAt == null) {
                        TextButton(
                            onClick = onSkipRir,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Pomiń") }
                    }
                }
                RirPicker(rirSet.rir) { onRir(rirSet, it) }
            }
        }
    }
}

/** Odliczanie przerwy - tyka samo, żeby nie przerysowywać ćwiczenia pod spodem. */
@Composable
private fun RestCountdown(
    endsAt: Long,
    totalSec: Int,
    frozenAt: Long?,
    next: ExerciseState?,
    inventory: Inventory,
    onTimeUp: (Long) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    fun leftAt(t: Long) = Math.floorDiv(endsAt - t, 1000L).toInt()
    var leftSec by remember(endsAt) { mutableIntStateOf(leftAt(frozenAt ?: System.currentTimeMillis())) }
    LaunchedEffect(endsAt, frozenAt) {
        if (frozenAt != null) {
            leftSec = leftAt(frozenAt)
            return@LaunchedEffect
        }
        while (true) {
            val left = leftAt(System.currentTimeMillis())
            if (left != leftSec) leftSec = left
            if (left < 0) {
                Haptics.restOver(context)
                onTimeUp(endsAt)
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    val fraction = if (totalSec > 0) (leftSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, tween(900), label = "przerwa")
    val ringColor = if (frozenAt != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val dalej: String? = next?.let { st ->
        val item = st.item
        when {
            st.complete -> "ćwiczenie zrobione"
            item.hasTargetWeight ->
                "dalej ${Loading.fmt(st.nextWeight)} ${item.equipment.weightUnitLabel} × ${st.nextReps}"
            else -> "dalej ${st.nextReps} powt."
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, -90f, 360f, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(ringColor, -90f, 360f * animated, false, Offset(inset, inset), arc,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "przerwa · " + clock(leftSec.coerceAtLeast(0)),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (frozenAt != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
            if (dalej != null) {
                Text(dalej, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onSkip) { Text("Pomiń") }
    }
}

private fun clock(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

/**
 * Jedna linia o tym, skąd ten ciężar - decyzja i punkt odniesienia.
 */
private fun suggestionLine(state: ExerciseState): String {
    val last = state.lastTime.maxOfOrNull { it.weightKg }
    val from = last?.let { " · ostatnio ${Loading.fmt(it)}" } ?: ""
    return when (state.suggestion.reason) {
        ProgressionEngine.Reason.FIRST_TIME -> "pierwszy raz - ciężar z planu"
        ProgressionEngine.Reason.HOLD -> "utrzymaj ciężar, dołóż powtórzeń$from"
        ProgressionEngine.Reason.INCREASE -> "↑ podbicie - komplet powtórzeń$from"
        ProgressionEngine.Reason.INCREASE_EASY -> "↑ podbicie - było za lekko$from"
        ProgressionEngine.Reason.AWAITING -> "podbicie czeka w planie - ten sam ciężar$from"
        ProgressionEngine.Reason.DELOAD -> "↓ cofnięcie - spadek poniżej zakresu$from"
        ProgressionEngine.Reason.STALLED -> "↓ cofnięcie - zastój$from"
        ProgressionEngine.Reason.KEEP -> "bez progresji - to samo, co poprzednio$from"
    }
}

// ---- Podsumowanie --------------------------------------------------------

/**
 * Podsumowanie przed zakończeniem: cztery kafelki i ćwiczenia z kropkami
 * serii - te same co na zegarku, żeby dwa ekrany tego samego treningu
 * mówiły tym samym językiem.
 *
 * Propozycji do planu tu nie ma: liczą się z zakończonego treningu i do
 * zakończenia mogą się jeszcze zmienić - pokazane wcześniej obiecywały coś,
 * czego zapis po dorobionej serii już nie dotrzymywał.
 *
 * Ćwiczenie otwiera się dotknięciem w szczegóły, gdzie da się też poprawić
 * serie. Zakończenie wymaga dotknięcia, bo zamyka sesję bezpowrotnie.
 */
@Composable
private fun ColumnScope.SessionDone(
    states: List<ExerciseState>,
    elapsedMin: Long,
    allComplete: Boolean,
    inventory: Inventory,
    onAddSet: (ExerciseState, Double, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onUpdateSet: (Long, Double, Int, Int?) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var openId by remember { mutableStateOf<Long?>(null) }
    val open = states.firstOrNull { it.item.id == openId }
    if (open != null) {
        ExerciseReview(
            state = open,
            inventory = inventory,
            onBack = { openId = null },
            onAddSet = { w, r -> onAddSet(open, w, r) },
            onDeleteSet = onDeleteSet,
            onUpdateSet = onUpdateSet
        )
        return
    }

    val working = states.map { st -> st.doneSets.filter { !it.isWarmup } }
    val sets = working.sumOf { it.size }
    val reps = working.sumOf { l -> l.sumOf { it.reps } }
    val tonnage = working.sumOf { l -> l.sumOf { it.tonnage } }

    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allComplete) {
                AnimatedCheck(Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column {
                // Przy wcześniejszym zakończeniu nie gratulujemy na wyrost.
                Text(
                    if (allComplete) "Trening ukończony" else "Podsumowanie",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${states.count { it.complete }} z ${states.size} ćwiczeń zrobionych",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(workoutLength(elapsedMin), "czas", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile("$sets", plural(sets, "seria", "serie", "serii").substringAfter(' '), null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(kg(tonnage), "kg tonażu", null, Modifier.weight(1f))
            StatTile("$reps", "powtórzeń", null, Modifier.weight(1f))
        }

        GroupLabel("Ćwiczenia")
        AppCard {
            states.forEachIndexed { i, st ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryExerciseRow(st) { openId = st.item.id }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
        shape = RoundedCornerShape(14.dp)
    ) { Text("Zakończ trening", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) { Text("Wróć do ćwiczeń") }
}

@Composable
internal fun StatTile(value: String, label: String, tone: Color?, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = tone ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Wiersz ćwiczenia w podsumowaniu: nazwa, tonaż i kropka na każdą serię.
 * Pominięte na bursztynowo z kreską - widać od razu, co zostało.
 */
@Composable
private fun SummaryExerciseRow(st: ExerciseState, onClick: () -> Unit) {
    val item = st.item
    val working = st.doneSets.filter { !it.isWarmup }
    val skipped = working.isEmpty()
    val tone = if (skipped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .tap(RoundedCornerShape(0.dp), onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.exerciseName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isRecord(st)) {
                    TagPill("rekord", MaterialTheme.colorScheme.tertiary, Modifier.padding(horizontal = 6.dp))
                }
                Text(
                    when {
                        skipped -> "-"
                        // Bez ciężaru tonaż wychodzi zero i nic nie mówi.
                        item.hasTargetWeight -> kg(working.sumOf { it.tonnage }) + " kg"
                        else -> "${working.sumOf { it.reps }} powt."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (skipped) tone else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            SetDots(planned = item.sets, done = working.size, size = 8)
        }
        Icon(
            Icons.Filled.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Rekord tą samą miarą co przycisk zapisu: ciężar ponad dotychczasowy najlepszy. */
internal fun isRecord(st: ExerciseState): Boolean {
    val top = st.doneSets.filter { !it.isWarmup }.maxOfOrNull { it.weightKg } ?: return false
    val best = st.bestWeight ?: return false
    return top > best + 0.001
}

internal fun kg(v: Double): String = "%,.0f".format(v).replace(',', ' ').replace(' ', ' ')

private fun workoutLength(min: Long): String =
    if (min < 60) "$min min" else "%d:%02d h".format(min / 60, min % 60)

// ---- Animacje ------------------------------------------------------------

/**
 * Ptaszek rysowany kreską, a nie wyświetlany jako gotowa ikona: oko łapie
 * ruch, zanim zdąży przeczytać tekst, a statyczna ikona zlewała się z resztą.
 */
@Composable
private fun AnimatedCheck(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val draw by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "ptaszek"
    )
    val ring by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "obwodka"
    )
    val color = MaterialTheme.colorScheme.secondary

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.1f
        drawCircle(
            color = color.copy(alpha = 0.25f * ring),
            radius = (w / 2 - stroke / 2) * ring,
            center = Offset(w / 2, h / 2),
            style = Stroke(width = stroke * 0.7f)
        )
        val a = Offset(0.24f * w, 0.52f * h)
        val b = Offset(0.43f * w, 0.72f * h)
        val c = Offset(0.78f * w, 0.30f * h)
        fun between(from: Offset, to: Offset, t: Float) =
            Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
        val first = (draw / 0.38f).coerceIn(0f, 1f)
        if (first > 0f) drawLine(color, a, between(a, b, first), strokeWidth = stroke, cap = StrokeCap.Round)
        if (draw > 0.38f) {
            val second = ((draw - 0.38f) / 0.62f).coerceIn(0f, 1f)
            drawLine(color, b, between(b, c, second), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * Potwierdzenie domknięcia ćwiczenia, w osobnej funkcji: wewnątrz `Box`
 * w `ColumnScope` widać dwa odbiorniki i Kotlin nie umie wybrać wariantu
 * `AnimatedVisibility`. Nazwa jest zapamiętana, bo w animacji wyjścia
 * wartość wejściowa jest już pusta.
 */
@Composable
private fun DoneOverlay(exerciseName: String?, next: String?, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableStateOf("") }
    var lastNext by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(exerciseName) {
        if (exerciseName != null) {
            lastShown = exerciseName
            lastNext = next
        }
    }
    AnimatedVisibility(
        visible = exerciseName != null,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        AppCard(accent = MaterialTheme.colorScheme.secondary) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedCheck(Modifier.size(38.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(lastShown, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "zrobione",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    // Dokąd jedzie ekran - przeskok na następne ćwiczenie
                    // bez zapowiedzi wyglądał jak zgubienie miejsca.
                    lastNext?.takeIf { it != lastShown }?.let {
                        Text(
                            "dalej: $it",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

internal fun setTime(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
