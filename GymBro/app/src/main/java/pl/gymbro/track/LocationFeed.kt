package pl.gymbro.track

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat

/**
 * Skąd biorą się odczyty lokalizacji - jedno miejsce dla ekranu
 * przygotowania i dla usługi nagrywającej trasę.
 *
 * Dawniej oba słuchały samego GPS_PROVIDER. Zmierzone na telefonie: półtorej
 * minuty nasłuchu w budynku i zero odczytów, a ekran do końca pisał
 * „Szukam sygnału". Sam odbiornik satelitarny w pomieszczeniu nie złapie
 * nic, a pod chmurą i przy zimnym starcie potrafi szukać minutami.
 *
 * Teraz pierwszeństwo ma lokalizacja łączona (fused) systemu, ta sama, której
 * używa Strava: satelity, gdy są, a do tego Wi-Fi i czujniki ruchu, więc
 * pierwszy odczyt przychodzi w sekundy, a pod gołym niebem i tak jest z GPS.
 * Skoki z sieci, przed którymi chronił sam GPS, odsiewa dalej
 * [pl.gymbro.gym.TrackMath.MAX_ACCURACY_M] - odczyt z masztu ma niepewność
 * w setkach metrów i do trasy nie wchodzi. Bez łączonej (Android poniżej 12
 * albo telefon bez usług Google) zostaje sam GPS.
 */
object LocationFeed {

    fun provider(lm: LocationManager): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            lm.hasProvider(LocationManager.FUSED_PROVIDER)
        ) LocationManager.FUSED_PROVIDER
        else LocationManager.GPS_PROVIDER

    /** Czy lokalizacja w telefonie jest w ogóle włączona - przełącznik w szufladzie. */
    fun enabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Odczyty co sekundę, z najwyższą dokładnością. Zwraca funkcję, która
     * kończy nasłuch. Bez zgody nie robi nic.
     */
    @SuppressLint("MissingPermission") // sprawdzone niżej
    fun listen(context: Context, looper: Looper, onFix: (Location) -> Unit): () -> Unit {
        val lm = context.getSystemService(LocationManager::class.java) ?: return {}
        if (!PhoneTracker.hasLocationPermission(context)) return {}
        val listener = LocationListenerCompat { onFix(it) }
        val request = LocationRequestCompat.Builder(1000L)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        val ok = runCatching {
            LocationManagerCompat.requestLocationUpdates(lm, provider(lm), request, listener, looper)
        }.isSuccess
        // Łączona bywa zgłaszana, a potem odmawia - wtedy sam GPS.
        if (!ok) runCatching {
            LocationManagerCompat.requestLocationUpdates(
                lm, LocationManager.GPS_PROVIDER, request, listener, looper
            )
        }
        return { runCatching { LocationManagerCompat.removeUpdates(lm, listener) } }
    }

    /**
     * Satelity widziane i użyte do pozycji. Na ekranie przygotowania to
     * jedyny znak życia, zanim przyjdzie pierwszy odczyt: „widzę 9" mówi,
     * że wystarczy chwilę poczekać, a „0" - że trzeba wyjść spod dachu.
     */
    @SuppressLint("MissingPermission")
    fun satellites(context: Context, onStatus: (seen: Int, used: Int) -> Unit): () -> Unit {
        val lm = context.getSystemService(LocationManager::class.java) ?: return {}
        if (!PhoneTracker.hasLocationPermission(context)) return {}
        val cb = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                onStatus(status.satelliteCount, used)
            }
        }
        val ok = runCatching {
            LocationManagerCompat.registerGnssStatusCallback(lm, cb, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        if (!ok) return {}
        return { runCatching { LocationManagerCompat.unregisterGnssStatusCallback(lm, cb) } }
    }
}
