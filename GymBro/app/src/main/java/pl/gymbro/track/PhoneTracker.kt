package pl.gymbro.track

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.gymbro.CrashLog
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.ExternalSession
import pl.gymbro.data.LiveActivity
import pl.gymbro.data.TrackRepository
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.TrackMath
import pl.gymbro.health.ActivityDetail
import java.util.UUID

/**
 * Aktywność nagrywana telefonem: jedno miejsce, które ją prowadzi.
 *
 * Ekran i usługa GPS tylko tu zaglądają. Usługa podaje odczyty, ekran
 * naciska pauzę - stan, dystans i zapis są w jednym obiekcie, bo dwa
 * miejsca liczące dystans rozjechałyby się przy pierwszej pauzie.
 *
 * Wszystko, co trwałe, idzie od razu do bazy (stan do `app_state`, punkty do
 * `track_point`), więc proces ubity w kieszeni w połowie biegu wstaje przez
 * [restore] dokładnie tam, gdzie był.
 */
object PhoneTracker {

    /** Co widać na ekranie trwającej aktywności. */
    data class Snapshot(
        val live: LiveActivity,
        val distanceM: Double = 0.0,
        /** Dokładność ostatniego odczytu GPS, także odrzuconego - do paska sygnału. */
        val accuracyM: Float? = null,
        val lastFixAt: Long? = null,
        val recentPaceMinPerKm: Double? = null,
        /** Ile punktów ma trasa - mapa dociąga tylko nowe, przez [routeFrom]. */
        val routeSize: Int = 0,
        /** Ostatni odczyt, także odrzucony: kropka „tu jestem" na mapie. */
        val here: TrackMath.Fix? = null
    )

    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state

    private var accumulator: TrackMath.Accumulator? = null
    /** Ostatnie punkty trasy, do tempa „teraz" - bez czytania bazy co sekundę. */
    private val recent = ArrayDeque<TrackMath.Fix>()
    /**
     * Cała trasa w pamięci, do mapy na żywo. Mapa pyta o same nowe punkty,
     * a nie o kopię całości co sekundę - po godzinie biegu to trzy i pół
     * tysiąca punktów przy każdym odczycie.
     */
    private val route = ArrayList<TrackMath.Fix>()
    private var restored = false

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Wczytuje trwającą aktywność z bazy. Wołane przy starcie aplikacji i przy
     * podniesieniu usługi przez system - co zdarzy się pierwsze. Blokujące.
     */
    @Synchronized
    fun restore(context: Context) {
        if (restored) return
        restored = true
        val repo = TrackRepository(context)
        val live = repo.live() ?: return
        val acc = TrackMath.Accumulator(live.kind.maxSpeedMs)
        if (live.kind.gps) {
            val pts = repo.points(live.key)
            acc.restore(pts)
            recent.clear()
            recent.addAll(pts.takeLast(60))
            route.clear()
            route.addAll(pts)
        }
        accumulator = acc
        _state.value = Snapshot(live, distanceM = acc.distanceM, routeSize = route.size, here = route.lastOrNull())
        if (live.kind.gps && !live.paused) TrackService.start(context)
    }

    @Synchronized
    fun start(context: Context, kind: PhoneActivity) {
        if (_state.value != null) return
        val live = LiveActivity(
            key = "phone-" + UUID.randomUUID(),
            kind = kind,
            startedAt = System.currentTimeMillis()
        )
        TrackRepository(context).saveLive(live)
        accumulator = TrackMath.Accumulator(kind.maxSpeedMs)
        recent.clear()
        route.clear()
        _state.value = Snapshot(live)
        if (kind.gps) TrackService.start(context)
    }

    @Synchronized
    fun pause(context: Context) {
        val s = _state.value ?: return
        val live = s.live.pause(System.currentTimeMillis())
        TrackRepository(context).saveLive(live)
        _state.value = s.copy(live = live, recentPaceMinPerKm = null)
        // GPS w pauzie nie jest do niczego potrzebny, a zjada baterię
        // najbardziej ze wszystkiego, co telefon robi.
        if (live.kind.gps) TrackService.stop(context)
    }

    @Synchronized
    fun resume(context: Context) {
        val s = _state.value ?: return
        val live = s.live.resume(System.currentTimeMillis())
        TrackRepository(context).saveLive(live)
        _state.value = s.copy(live = live)
        if (live.kind.gps) TrackService.start(context)
    }

    /** Punkty trasy od [from] do końca - mapa dorysowuje tylko to, czego jeszcze nie ma. */
    @Synchronized
    fun routeFrom(from: Int): List<TrackMath.Fix> =
        if (from >= route.size) emptyList() else route.subList(from.coerceAtLeast(0), route.size).toList()

    // ---- Trening siłowy: serie i przerwy ----------------------------------

    @Synchronized
    fun startSet(context: Context) = change(context) { it.startSet(System.currentTimeMillis()) }

    @Synchronized
    fun endSet(context: Context) = change(context) { it.endSet(System.currentTimeMillis()) }

    @Synchronized
    fun dropSet(context: Context) = change(context) { it.dropSet() }

    @Synchronized
    fun dropSetAt(context: Context, index: Int) = change(context) { it.dropSetAt(index) }

    @Synchronized
    fun setRest(context: Context, sec: Int) = change(context) { it.copy(restSec = sec.coerceIn(15, 600)) }

    /** Zmiana stanu: od razu do bazy, potem na ekran - jak pauza. */
    private fun change(context: Context, f: (LiveActivity) -> LiveActivity) {
        val s = _state.value ?: return
        val live = f(s.live)
        if (live == s.live) return
        TrackRepository(context).saveLive(live)
        _state.value = s.copy(live = live)
    }

    /** Odczyt z GPS. Woła go usługa z własnego wątku. */
    @Synchronized
    fun onLocation(context: Context, loc: Location) {
        val s = _state.value ?: return
        if (s.live.paused) return
        val acc = accumulator ?: return
        val fix = TrackMath.Fix(
            at = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
            seg = s.live.seg
        )
        if (acc.offer(fix)) {
            runCatching { TrackRepository(context).add(s.live.key, fix) }
                .onFailure { CrashLog.note(context, "zapis punktu trasy", it) }
            recent.addLast(fix)
            route.add(fix)
            while (recent.size > 60) recent.removeFirst()
        }
        val now = System.currentTimeMillis()
        _state.value = s.copy(
            distanceM = acc.distanceM,
            accuracyM = fix.accuracyM,
            lastFixAt = now,
            recentPaceMinPerKm = TrackMath.recentPace(recent.toList(), fix.at),
            routeSize = route.size,
            here = fix
        )
    }

    /**
     * Zapisuje aktywność do historii. Blokujące - wołaj z wątku roboczego.
     *
     * Tętno doczytujemy z Health Connect za okno aktywności: zegarek mierzy je
     * cały dzień, więc bieg nagrany telefonem i tak ma swoje tętno, jeśli
     * zegarek był na ręce. Brak tętna nie zatrzymuje zapisu.
     *
     * @return zapisana aktywność albo null, gdy nic nie trwało
     */
    suspend fun finish(context: Context): ExternalSession? {
        val (s, end) = synchronized(this) {
            val snap = _state.value ?: return null
            snap to (snap.live.pausedAt ?: System.currentTimeMillis())
        }
        TrackService.stop(context)
        // Seria, która jeszcze trwa przy „Zakończ", to ostatnia seria
        // treningu - kończy się razem z nim, a nie przepada.
        val live = s.live.endSet(end)
        val weight = runCatching { BodyRepository(context).latest()?.weightKg }.getOrNull()
        val hr = runCatching {
            ActivityDetail(context).load(live.startedAt, end).heartRate.map { it.value }
        }.getOrDefault(emptyList())
        val session = ExternalSession(
            externalId = live.key,
            source = ExternalSession.PHONE_SOURCE,
            type = live.kind.type,
            startedAt = live.startedAt,
            endedAt = end,
            distanceM = s.distanceM.takeIf { live.kind.gps && it > 0 },
            energyKcal = TrackMath.energyKcal(live.kind, s.distanceM, live.movingMs(end), weight),
            hrAvg = hr.takeIf { it.isNotEmpty() }?.average(),
            hrMax = hr.maxOrNull()
        )
        TrackRepository(context).finish(session, live.sets)
        synchronized(this) {
            _state.value = null
            accumulator = null
            recent.clear()
            route.clear()
        }
        return session
    }

    /** Odrzuca trwającą aktywność razem z trasą. */
    @Synchronized
    fun discard(context: Context) {
        val s = _state.value ?: return
        TrackService.stop(context)
        val repo = TrackRepository(context)
        repo.delete(s.live.key)
        repo.saveLive(null)
        _state.value = null
        accumulator = null
        recent.clear()
        route.clear()
    }

    internal fun openAppIntent(context: Context): Intent =
        Intent(context, pl.gymbro.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
