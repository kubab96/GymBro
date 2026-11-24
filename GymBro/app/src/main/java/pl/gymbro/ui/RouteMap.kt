package pl.gymbro.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.PhoneTracker
import java.io.File

/**
 * Mapa trasy: kafelki z OpenStreetMap, linia z GPS telefonu.
 *
 * OpenStreetMap, nie Mapy Google - te wymagają klucza przypiętego do konta
 * i usług Google. Kafelki idą z sieci i zostają w pamięci podręcznej, więc
 * stała trasa po osiedlu po pierwszym razie działa i bez zasięgu. Bez
 * kafelków linia trasy dalej się rysuje, na szarym tle.
 *
 * Pobranie kafelka mówi serwerowi OpenStreetMap, jaki fragment mapy się
 * ogląda - to jedyne, co aplikacja wysyła w świat poza Open Food Facts.
 * Same punkty trasy nigdy nie opuszczają telefonu.
 */
internal object RouteMapSetup {
    @Volatile private var done = false

    /**
     * Zasady OpenStreetMap wymagają, żeby aplikacja przedstawiała się własną
     * nazwą - bez niej serwer kafelków potrafi odmówić. Pamięć podręczna
     * w katalogu podręcznym aplikacji: system może ją wyczyścić, a do zapisu
     * nie potrzeba zgody na pamięć zewnętrzną.
     */
    fun init(context: Context) {
        if (done) return
        val app = context.applicationContext
        Configuration.getInstance().apply {
            userAgentValue = app.packageName
            osmdroidBasePath = File(app.cacheDir, "osmdroid")
            osmdroidTileCache = File(app.cacheDir, "osmdroid/tiles")
        }
        done = true
    }
}

/** Kolory tempa na mapie i w legendzie - te same w obu miejscach. */
internal object PaceColors {
    val fast = Color(0xFF4CC38A)
    val even = Color(0xFFFFB357)
    val slow = Color(0xFFFF7A7A)

    /**
     * Cztery procent w każdą stronę to „równo". Przy węższym progu mapa
     * migałaby kolorami od samego szumu GPS, przy szerszym biegnący równo
     * z jednym podbiegiem nie zobaczyłby nic.
     */
    fun of(ratio: Double): Color = when {
        ratio >= 1.04 -> fast
        ratio <= 0.96 -> slow
        else -> even
    }
}

private fun TrackMath.Fix.geo() = GeoPoint(lat, lon)

/**
 * Ciemna mapa: kolory prawie bez nasycenia, odwrócone i przygaszone.
 * Zostaje układ ulic i budynków w szarościach, a jedynym kolorem na mapie
 * jest trasa.
 */
private val DARK_TILES = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0.15f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    -0.8f, 0f, 0f, 0f, 235f,
                    0f, -0.8f, 0f, 0f, 237f,
                    0f, 0f, -0.8f, 0f, 240f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
)

@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val map = remember {
        RouteMapSetup.init(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.0
            // W ciemnym motywie jasna mapa świeciła jak latarka. Same
            // odwrócone kolory (TilesOverlay.INVERT_COLORS) dawały granatowe
            // ulice i fioletowe trawniki, które biły się z linią trasy.
            if (dark) overlayManager.tilesOverlay.setColorFilter(DARK_TILES)
        }
    }
    // Mapa sama nie wie o cyklu życia ekranu: bez onPause dalej pobiera
    // kafelki w tle, a bez onDetach trzyma wątki po wyjściu z ekranu.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }
    return map
}

/**
 * Kropki na mapie: start, koniec, numery kilometrów i „tu jestem".
 *
 * Własna nakładka zamiast znaczników osmdroid - tamte otwierają po dotknięciu
 * dymek z opisem i wymagają obrazków. Tu wystarczy kółko z cyfrą.
 */
private class DotsOverlay(private val density: Float) : Overlay() {
    data class Dot(val at: GeoPoint, val fill: Int, val label: String? = null, val radiusDp: Float = 6f)

    var dots: List<Dot> = emptyList()
    private val p = Point()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = map.projection
        ring.strokeWidth = 2f * density
        text.textSize = 10f * density
        dots.forEach { d ->
            proj.toPixels(d.at, p)
            val r = d.radiusDp * density
            fill.color = d.fill
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, ring)
            d.label?.let { c.drawText(it, p.x.toFloat(), p.y - (text.descent() + text.ascent()) / 2, text) }
        }
    }
}

private fun line(map: MapView, color: Int, widthPx: Float) = Polyline(map).apply {
    outlinePaint.color = color
    outlinePaint.strokeWidth = widthPx
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
    // Dotknięcie linii otwierało pusty dymek z opisem odcinka.
    setOnClickListener { _, _, _ -> true }
}

/**
 * Mapa trwającej aktywności: trasa rośnie z każdym odczytem, mapa jedzie
 * za kropką. Przesunięcie palcem wyłącza jazdę - inaczej nie dało się
 * obejrzeć, którędy się biegło - a przycisk w rogu ją przywraca.
 *
 * Linia dorysowuje się o nowe punkty, nie od zera: po godzinie biegu
 * przebudowa całości co sekundę to trzy i pół tysiąca punktów na odczyt.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun LiveRouteMap(snap: PhoneTracker.Snapshot, modifier: Modifier = Modifier) {
    val map = rememberMapView()
    val density = LocalDensity.current.density
    val lineColor = MaterialTheme.colorScheme.secondary.toArgb()
    val hereColor = MaterialTheme.colorScheme.primary.toArgb()
    var follow by remember { mutableStateOf(true) }
    val state = remember {
        object {
            var drawn = 0
            var seg = -1
            var current: Polyline? = null
            var centered = false
            val dots = DotsOverlay(density).also { map.overlays.add(it) }
        }
    }

    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = {
                map.controller.setZoom(17.0)
                map.setOnTouchListener { _, e ->
                    if (e.action == MotionEvent.ACTION_MOVE) follow = false
                    false
                }
                map
            },
            modifier = Modifier.fillMaxSize(),
            update = { m ->
                PhoneTracker.routeFrom(state.drawn).forEach { f ->
                    // Po pauzie nowy odcinek - bez kreski przez miejsce,
                    // w którym telefon nie nagrywał.
                    if (state.current == null || f.seg != state.seg) {
                        state.current = line(m, lineColor, 5f * density).also {
                            m.overlays.add(m.overlays.size - 1, it)
                        }
                        state.seg = f.seg
                    }
                    state.current!!.addPoint(f.geo())
                    state.drawn++
                }
                snap.here?.let { h ->
                    state.dots.dots = listOf(DotsOverlay.Dot(h.geo(), hereColor, radiusDp = 8f))
                    if (follow || !state.centered) {
                        if (state.centered) m.controller.animateTo(h.geo()) else m.controller.setCenter(h.geo())
                        state.centered = true
                    }
                }
                m.invalidate()
            }
        )
        if (!follow) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .tap(CircleShape) {
                        follow = true
                        snap.here?.let { map.controller.animateTo(it.geo()) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Wróć do mojej pozycji")
            }
        }
    }
}

/**
 * Mapa zapisanej trasy: linia w kolorach tempa, start, meta i numery
 * kilometrów, kadr dopasowany do całej trasy.
 *
 * [interactive] false - mapa w przewijanym ekranie szczegółów. Palec na
 * niej przewijał mapę zamiast ekranu, więc tam jest nieruchoma, a dotknięcie
 * otwiera ją na pełnym ekranie ([onOpen]).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun RouteMap(
    points: List<TrackMath.Fix>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onOpen: (() -> Unit)? = null
) {
    val map = rememberMapView()
    val density = LocalDensity.current.density
    val startColor = MaterialTheme.colorScheme.secondary.toArgb()
    val endColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val kmColor = Color.White.toArgb()

    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = {
                val chunks = TrackMath.paceChunks(points)
                chunks.forEach { ch ->
                    map.overlays.add(line(map, PaceColors.of(ch.ratio).toArgb(), 5f * density).apply {
                        setPoints(ch.points.map { it.geo() })
                    })
                }
                // Za krótka trasa na odcinki tempa - jedna linia, żeby było cokolwiek.
                if (chunks.isEmpty() && points.size >= 2) {
                    map.overlays.add(line(map, startColor, 5f * density).apply {
                        setPoints(points.map { it.geo() })
                    })
                }
                val dots = DotsOverlay(density)
                // Numery kilometrów na wierzchu: na pętli wokół bloku
                // wypadają dokładnie na starcie i znikały pod jego kropką.
                dots.dots = buildList {
                    points.firstOrNull()?.let { add(DotsOverlay.Dot(it.geo(), startColor, radiusDp = 7f)) }
                    points.lastOrNull()?.let { add(DotsOverlay.Dot(it.geo(), endColor, radiusDp = 7f)) }
                    TrackMath.kmMarks(points).forEachIndexed { i, f ->
                        add(DotsOverlay.Dot(f.geo(), kmColor, "${i + 1}", radiusDp = 8f))
                    }
                }
                map.overlays.add(dots)
                if (!interactive) map.setOnTouchListener { _, _ -> true }
                if (points.size >= 2) {
                    val box = BoundingBox.fromGeoPointsSafe(points.map { it.geo() })
                    // Kadr dopiero po rozłożeniu widoku - wcześniej mapa
                    // nie zna swoich wymiarów i ląduje na zbliżeniu świata.
                    map.addOnFirstLayoutListener { _, _, _, _, _ ->
                        map.zoomToBoundingBox(box, false, (28 * density).toInt())
                    }
                } else points.firstOrNull()?.let {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(it.geo())
                }
                map
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!interactive && onOpen != null) {
            Box(Modifier.fillMaxSize().tap(RoundedCornerShape(16.dp), onClick = onOpen))
        }
    }
}
