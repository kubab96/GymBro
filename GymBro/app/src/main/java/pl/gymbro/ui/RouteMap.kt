package pl.gymbro.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import pl.gymbro.gym.TrackMath
import pl.gymbro.track.PhoneTracker
import kotlin.math.hypot

/**
 * Mapa trasy: wektorowa mapa OpenFreeMap rysowana przez MapLibre, linia
 * z GPS telefonu.
 *
 * Bez Map Google - te wymagają klucza przypiętego do konta i usług Google.
 * OpenFreeMap nie wymaga klucza ani konta i nie ma limitu odsłon. Mapa jest
 * wektorowa: ulice i napisy rysują się ostro przy każdym przybliżeniu.
 * Wcześniej osmdroid rozciągał kafelki-obrazki OpenStreetMap prawie
 * trzykrotnie do gęstości ekranu i wychodziła z tego rozmazana breja,
 * a w ciemnym motywie jeszcze odwracana filtrem. Ładniejsze style obrazkowe
 * (CARTO, Stadia) od 2025 roku chcą klucza - CARTO odsyła kafelek z napisem
 * „API KEY REQUIRED".
 *
 * Style: ciemny i jasny Positron - prawie same szarości, więc jedynym
 * kolorem na mapie zostaje trasa.
 *
 * Kafelki zostają w pamięci podręcznej MapLibre, więc stała trasa po osiedlu
 * po pierwszym razie działa i bez zasięgu. Bez kafelków linia trasy dalej
 * się rysuje, na gładkim tle stylu.
 *
 * Pobranie kafelka mówi serwerowi OpenFreeMap, jaki fragment mapy się
 * ogląda - to jedyne, co aplikacja wysyła w świat poza Open Food Facts.
 * Same punkty trasy nigdy nie opuszczają telefonu.
 */
private object MapStyle {
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
    const val LIGHT = "https://tiles.openfreemap.org/styles/positron"

    /** Wymagają go licencje OpenStreetMap i OpenMapTiles. */
    const val ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap"

    /** Ta czcionka musi być na serwerze stylu - inna nie narysuje się wcale. */
    val FONT = arrayOf("Noto Sans Bold")

    @Volatile private var ready = false

    fun init(context: Context) {
        if (ready) return
        MapLibre.getInstance(context.applicationContext)
        ready = true
    }
}

/**
 * Kolory tempa na mapie i w legendzie - te same w obu miejscach.
 *
 * Skala jak mapa ciepła: zieleń to zwykłe tempo tego biegu, im cieplej, tym
 * szybciej. Wolniejsze odcinki też są zielone - mapa ma pokazać, gdzie się
 * przyspieszyło, a nie wytykać zwolnienia. Wcześniej trzy kolory: zielony
 * szybciej, bursztyn równo, czerwony wolniej; czerwień kojarzyła się
 * z wysiłkiem, a znaczyła coś odwrotnego.
 */
internal object PaceColors {
    val normal = Color(0xFF4CC38A)
    val faster = Color(0xFFF2D14B)
    val muchFaster = Color(0xFFFF9B3D)
    val fastest = Color(0xFFFF5C5C)

    /** Od dołu skali do góry, z napisami do legendy. */
    val scale = listOf(
        normal to "normalnie",
        faster to "szybciej",
        muchFaster to "jeszcze szybciej",
        fastest to "najszybciej"
    )

    /**
     * Kolor każdego odcinka względem zwykłego tempa tej aktywności, czyli
     * mediany odcinków, nie średniej. Średnią ściągają w dół postoje: spacer
     * z kwadransem stania na końcu wychodził prawie cały czerwony, bo zwykły
     * marsz był o 20% szybszy od średniej. Mediana to tempo, którym szło się
     * przez większość trasy - i to ono ma być zielone.
     */
    fun colors(chunks: List<TrackMath.PaceChunk>): List<Color> {
        if (chunks.isEmpty()) return emptyList()
        val typical = chunks.map { it.ratio }.sorted()[chunks.size / 2]
        return chunks.map { of(if (typical > 0) it.ratio / typical else 1.0) }
    }

    /**
     * Progi to prędkość względem zwykłego tempa ([colors]). Do czterech procent
     * to dalej „normalnie" - przy węższym progu mapa migałaby kolorami od
     * samego szumu GPS. Dalej co sześć-osiem punktów procentowych: przy
     * zwykłym bieżku odcinki rozjeżdżają się o kilka do kilkunastu procent,
     * więc czerwień zostaje dla przyspieszeń, nie dla każdego zbiegu.
     */
    fun of(ratio: Double): Color = when {
        ratio >= 1.18 -> fastest
        ratio >= 1.10 -> muchFaster
        ratio >= 1.04 -> faster
        else -> normal
    }
}

private fun TrackMath.Fix.point(): Point = Point.fromLngLat(lon, lat)
private fun TrackMath.Fix.latLng() = LatLng(lat, lon)
private fun Color.hex() = String.format("#%06X", toArgb() and 0xFFFFFF)

/** Wygląd mapy zależny od motywu. */
private class MapLook(val dark: Boolean) {
    val style = if (dark) MapStyle.DARK else MapStyle.LIGHT

    /**
     * Kolor mapy, zanim styl się narysuje - ten sam co tło stylu. Domyślnie
     * MapLibre zalewa widok jasnoszarym i przy każdym otwarciu i zamknięciu
     * mapy na pełnym ekranie (oba tworzą mapę od nowa) w ciemnym motywie
     * błyskało białe mignięcie.
     */
    val loading = if (dark) 0xFF0C0C0C.toInt() else 0xFFF2F3F0.toInt()

    /**
     * Obwódka pod linią trasy. Bez niej trasa tam i z powrotem tą samą ulicą
     * zlewała się w jedną grubą kreskę, a linia ginęła na ulicach podobnego
     * odcienia. Na ciemnej mapie obwódka ciemniejsza od tła, na jasnej biała.
     */
    val casing = if (dark) "#050608" else "#FFFFFF"

    /** Obwódka kropek - kontrast do tła mapy, nie do kropki. */
    val ring = if (dark) "#FFFFFF" else "#1B1F24"
}

@Composable
private fun rememberMapLook(): MapLook {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) { MapLook(dark) }
}

/**
 * Widok mapy przypięty do cyklu życia ekranu. Bez onStop mapa dalej pobiera
 * kafelki w tle, a bez onDestroy trzyma wątek rysujący po wyjściu z ekranu.
 *
 * Tryb TextureView zamiast SurfaceView: powierzchnia leży wtedy w zwykłym
 * drzewie widoków i daje się przyciąć do zaokrąglonych rogów karty.
 * SurfaceView rysuje się w osobnym oknie, z kanciastymi rogami nad kartą.
 */
@Composable
private fun rememberMapView(look: MapLook): MapView {
    val context = LocalContext.current
    val map = remember {
        MapStyle.init(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .foregroundLoadColor(look.loading)
            // Podpis źródła rysujemy sami, mniejszy - przycisk „i" i logo
            // zasłaniały róg mapy na małej karcie.
            .attributionEnabled(false)
            .logoEnabled(false)
            .compassEnabled(false)
            // Mapa zawsze północą do góry i bez pochylenia: obrót drugim
            // palcem przy przybliżaniu zdarzał się przypadkiem, a po nim
            // trasa „odwracała się" bez widocznego powodu.
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .minZoomPreference(3.0)
            .maxZoomPreference(19.5)
        MapView(context, options).apply {
            // Tło widoku też: TextureView pokazuje je przez pierwszą klatkę,
            // zanim MapLibre w ogóle zacznie rysować.
            setBackgroundColor(look.loading)
            onCreate(null)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, map) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> map.onStart()
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                Lifecycle.Event.ON_STOP -> map.onStop()
                else -> {}
            }
        }
        map.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onStop()
            map.onDestroy()
        }
    }
    return map
}

/**
 * Mapa zabiera palec dla siebie, zanim ruszy go ktoś wyżej.
 *
 * Widok Androida w Compose dostaje zdarzenia dopiero po przodkach. Pierwszy
 * ruch palca zjadał ktoś nad mapą i do mapy szło `ACTION_CANCEL` - zmierzone
 * w logu mapy: DOWN, CANCEL, koniec. Mapa nie dawała się ani przesunąć, ani
 * przybliżyć, nawet na pełnym ekranie. `requestDisallowInterceptTouchEvent`
 * przy pierwszym dotknięciu przestawia Compose na oddawanie zdarzeń mapie
 * jako pierwszej, do podniesienia palców.
 *
 * [onPan] - palec przesunął mapę jednym palcem. Szczypanie się nie liczy:
 * przybliżenie w trakcie biegu nie ma wyłączać jazdy za kropką.
 */
@SuppressLint("ClickableViewAccessibility")
private fun MapView.claimTouches(onPan: () -> Unit = {}) {
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    var pinch = false
    var downX = 0f
    var downY = 0f
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                pinch = false
                downX = e.x
                downY = e.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> pinch = true
            MotionEvent.ACTION_MOVE ->
                if (!pinch && hypot(e.x - downX, e.y - downY) > slop) onPan()
        }
        false
    }
}

/** Kropka na mapie: start, meta, numer kilometra albo „tu jestem". */
private fun dot(at: Point, fill: String, radius: Float, label: String? = null) =
    Feature.fromGeometry(at).apply {
        addStringProperty("fill", fill)
        addNumberProperty("r", radius)
        if (label != null) addStringProperty("label", label)
    }

/**
 * Warstwy trasy, od spodu: obwódka, linia, cień kropek, kropki, numery.
 *
 * Linia w kolorze z właściwości odcinka (`c`) - jedna warstwa dla całej
 * trasy, ile by kawałków tempa nie miała. Numery kilometrów na wierzchu:
 * na pętli wokół bloku wypadają dokładnie na starcie i znikały pod jego
 * kropką.
 */
private fun Style.addRouteLayers(look: MapLook, routeSrc: String, lineSrc: String, lineColor: String?) {
    addLayer(LineLayer("casing", routeSrc).withProperties(
        PropertyFactory.lineColor(look.casing),
        PropertyFactory.lineOpacity(0.9f),
        PropertyFactory.lineWidth(9f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
    ))
    addLayer(LineLayer("line", lineSrc).withProperties(
        if (lineColor != null) PropertyFactory.lineColor(lineColor)
        else PropertyFactory.lineColor(Expression.toColor(Expression.get("c"))),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
    ))
    // Miękki cień pod kropką - odkleja ją od linii, na której leży.
    addLayer(CircleLayer("dot-shade", "dots").withProperties(
        PropertyFactory.circleColor("#000000"),
        PropertyFactory.circleOpacity(0.35f),
        PropertyFactory.circleBlur(0.6f),
        PropertyFactory.circleRadius(Expression.sum(Expression.get("r"), Expression.literal(3f))),
        PropertyFactory.circleTranslate(arrayOf(0f, 1f))
    ))
    addLayer(CircleLayer("dot", "dots").withProperties(
        PropertyFactory.circleColor(Expression.toColor(Expression.get("fill"))),
        PropertyFactory.circleRadius(Expression.get("r")),
        PropertyFactory.circleStrokeColor(look.ring),
        PropertyFactory.circleStrokeWidth(2f)
    ))
    addLayer(SymbolLayer("dot-label", "dots").withProperties(
        PropertyFactory.textField(Expression.get("label")),
        PropertyFactory.textFont(MapStyle.FONT),
        PropertyFactory.textSize(11f),
        PropertyFactory.textColor("#000000"),
        // Numer kilometra nie ustępuje nazwom ulic - to on jest tu treścią.
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true)
    ))
}

/** Okrągły przycisk na mapie, w języku reszty aplikacji (karta z obwódką). */
@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .tap(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * Przybliż, oddal i przycisk zależny od miejsca ([extra]: „do mnie" w trakcie
 * biegu, „cała trasa" w szczegółach). Szczypanie i podwójne dotknięcie działają
 * i bez nich, ale w biegu, jedną ręką, przycisk trafia się pewniej niż gest.
 */
@Composable
private fun MapControls(map: () -> MapLibreMap?, modifier: Modifier, extra: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        extra?.invoke()
        MapButton(Icons.Filled.Add, "Przybliż") { map()?.animateCamera(CameraUpdateFactory.zoomIn()) }
        MapButton(Icons.Filled.Remove, "Oddal") { map()?.animateCamera(CameraUpdateFactory.zoomOut()) }
    }
}

/**
 * Kształt karty mapy z obwódką jak [AppCard]. Ciemny styl mapy ma prawie
 * ten sam odcień co tło ekranu i bez obwódki karta nie miała krawędzi.
 */
@Composable
private fun Modifier.mapCard(): Modifier {
    val shape = RoundedCornerShape(16.dp)
    return clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

/** Podpis źródła mapy, w rogu drobnym drukiem. */
@Composable
private fun Attribution(modifier: Modifier) {
    Text(
        MapStyle.ATTRIBUTION,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/**
 * Odcinki nagrania jako jedna linia wieloczęściowa - przez pauzę nie idzie
 * kreska tam, gdzie telefon nie nagrywał.
 */
private fun segments(points: List<TrackMath.Fix>): List<List<Point>> {
    val out = mutableListOf<List<Point>>()
    var from = 0
    for (i in 1..points.size) {
        if (i == points.size || points[i].seg != points[from].seg) {
            if (i - from >= 2) out += points.subList(from, i).map { it.point() }
            from = i
        }
    }
    return out
}

/**
 * Mapa trwającej aktywności: trasa rośnie z każdym odczytem, mapa jedzie
 * za kropką. Przesunięcie palcem wyłącza jazdę - inaczej nie dało się
 * obejrzeć, którędy się biegło - a przycisk w rogu ją przywraca.
 *
 * Punkty dokładają się do listy o nowe odczyty, nie wczytują od zera: po
 * godzinie biegu przebudowa całości co sekundę to trzy i pół tysiąca punktów
 * na odczyt. Do mapy cała linia idzie tylko wtedy, gdy doszedł punkt.
 */
@Composable
internal fun LiveRouteMap(snap: PhoneTracker.Snapshot, modifier: Modifier = Modifier) {
    val look = rememberMapLook()
    val view = rememberMapView(look)
    val lineColor = MaterialTheme.colorScheme.secondary.hex()
    val hereColor = MaterialTheme.colorScheme.primary.hex()
    var follow by remember { mutableStateOf(true) }
    val state = remember {
        object {
            var map: MapLibreMap? = null
            var style: Style? = null
            var drawn = 0
            val segs = mutableListOf<MutableList<Point>>()
            var lastSeg = -1
            var dirty = true
            var centered = false
            var here: TrackMath.Fix? = null

            fun push() {
                val s = style ?: return
                if (dirty) {
                    s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(
                        Feature.fromGeometry(MultiLineString.fromLngLats(segs.filter { it.size >= 2 }))
                    )
                    dirty = false
                }
                val h = here
                s.getSourceAs<GeoJsonSource>("dots")?.setGeoJson(
                    FeatureCollection.fromFeatures(
                        if (h == null) emptyList() else listOf(dot(h.point(), hereColor, 8f))
                    )
                )
            }
        }
    }

    Box(modifier.mapCard()) {
        AndroidView(
            factory = {
                view.claimTouches { follow = false }
                view.getMapAsync { m ->
                    state.map = m
                    m.cameraPosition = CameraPosition.Builder().zoom(16.5).apply {
                        state.here?.let { target(it.latLng()) }
                    }.build()
                    m.setStyle(Style.Builder().fromUri(look.style)) { s ->
                        s.addSource(GeoJsonSource("route"))
                        s.addSource(GeoJsonSource("dots"))
                        s.addRouteLayers(look, "route", "route", lineColor)
                        state.style = s
                        state.dirty = true
                        state.push()
                    }
                }
                view
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                PhoneTracker.routeFrom(state.drawn).forEach { f ->
                    // Po pauzie nowy odcinek.
                    if (state.segs.isEmpty() || f.seg != state.lastSeg) {
                        state.segs += mutableListOf<Point>()
                        state.lastSeg = f.seg
                    }
                    state.segs.last() += f.point()
                    state.drawn++
                    state.dirty = true
                }
                state.here = snap.here
                state.push()
                val m = state.map
                val h = snap.here
                if (m != null && h != null && (follow || !state.centered)) {
                    if (state.centered) m.animateCamera(CameraUpdateFactory.newLatLng(h.latLng()), 600)
                    else m.moveCamera(CameraUpdateFactory.newLatLng(h.latLng()))
                    state.centered = true
                }
            }
        )
        MapControls({ state.map }, Modifier.align(Alignment.BottomEnd)) {
            if (!follow) {
                MapButton(Icons.Filled.MyLocation, "Wróć do mojej pozycji") {
                    follow = true
                    snap.here?.let { state.map?.animateCamera(CameraUpdateFactory.newLatLng(it.latLng())) }
                }
            }
        }
        Attribution(Modifier.align(Alignment.BottomStart))
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
@Composable
internal fun RouteMap(
    points: List<TrackMath.Fix>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onOpen: (() -> Unit)? = null
) {
    val look = rememberMapLook()
    val view = rememberMapView(look)
    val startColor = MaterialTheme.colorScheme.secondary.hex()
    val endColor = MaterialTheme.colorScheme.onSurface.hex()
    val bounds = remember(points) {
        if (points.size >= 2) LatLngBounds.Builder().includes(points.map { it.latLng() }).build() else null
    }
    val holder = remember { object { var map: MapLibreMap? = null } }

    // Cała trasa w kadrze, z marginesem na kropki i przyciski.
    fun fit(m: MapLibreMap, animate: Boolean) {
        val b = bounds ?: return
        val pad = (40 * view.resources.displayMetrics.density).toInt()
        val update = CameraUpdateFactory.newLatLngBounds(b, pad)
        if (animate) m.animateCamera(update) else m.moveCamera(update)
    }

    Box(modifier.mapCard()) {
        AndroidView(
            factory = {
                if (interactive) view.claimTouches()
                view.getMapAsync { m ->
                    holder.map = m
                    if (!interactive) m.uiSettings.setAllGesturesEnabled(false)
                    // Kadr dopiero po rozłożeniu widoku - wcześniej mapa nie
                    // zna swoich wymiarów i ląduje na zbliżeniu świata.
                    if (bounds != null) view.doOnLayout { fit(m, false) }
                    else points.firstOrNull()?.let {
                        m.cameraPosition = CameraPosition.Builder().target(it.latLng()).zoom(16.0).build()
                    }
                    m.setStyle(Style.Builder().fromUri(look.style)) { s ->
                        s.addSource(GeoJsonSource("route",
                            Feature.fromGeometry(MultiLineString.fromLngLats(segments(points)))))
                        val chunks = TrackMath.paceChunks(points)
                        // Za krótka trasa na odcinki tempa - jedna linia w kolorze
                        // startu, żeby było cokolwiek.
                        val pace = if (chunks.isEmpty()) {
                            segments(points).map { seg ->
                                Feature.fromGeometry(LineString.fromLngLats(seg)).apply { addStringProperty("c", startColor) }
                            }
                        } else chunks.zip(PaceColors.colors(chunks)) { ch, color ->
                            Feature.fromGeometry(LineString.fromLngLats(ch.points.map { it.point() })).apply {
                                addStringProperty("c", color.hex())
                            }
                        }
                        s.addSource(GeoJsonSource("pace", FeatureCollection.fromFeatures(pace)))
                        s.addSource(GeoJsonSource("dots", FeatureCollection.fromFeatures(buildList {
                            points.firstOrNull()?.let { add(dot(it.point(), startColor, 7f)) }
                            points.lastOrNull()?.let { add(dot(it.point(), endColor, 7f)) }
                            TrackMath.kmMarks(points).forEachIndexed { i, f ->
                                add(dot(f.point(), "#FFFFFF", 9f, "${i + 1}"))
                            }
                        })))
                        s.addRouteLayers(look, "route", "pace", null)
                    }
                }
                view
            },
            modifier = Modifier.fillMaxSize()
        )
        if (interactive) {
            MapControls({ holder.map }, Modifier.align(Alignment.BottomEnd)) {
                if (bounds != null) {
                    MapButton(Icons.Filled.ZoomOutMap, "Pokaż całą trasę") {
                        holder.map?.let { fit(it, true) }
                    }
                }
            }
        }
        Attribution(Modifier.align(Alignment.BottomStart))
        if (!interactive && onOpen != null) {
            Box(Modifier.fillMaxSize().tap(RoundedCornerShape(16.dp), onClick = onOpen))
        }
    }
}
