package pl.gymbro.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log

/**
 * GPS na czas biegu, spaceru i roweru nagrywanego telefonem.
 *
 * Usługa pierwszoplanowa typu `location`, bo telefon leży w kieszeni
 * z wygaszonym ekranem - zwykły nasłuch z aktywności gaśnie razem z nią.
 * Startuje zawsze z widocznej aplikacji (przycisk startu albo wznowienia),
 * więc wystarcza zgoda na lokalizację „podczas używania" i nie trzeba
 * prosić o dostęp w tle.
 *
 * Sama niczego nie liczy - każdy odczyt oddaje [PhoneTracker].
 */
class TrackService : Service() {

    private var thread: HandlerThread? = null
    private var stopListening: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pl.gymbro.CrashLog.install(this)
        // Po ubiciu procesu system podnosi usługę sam (START_STICKY), zanim
        // ktokolwiek otworzy aplikację - stan trzeba wczytać tutaj.
        PhoneTracker.restore(applicationContext)
        val live = PhoneTracker.state.value?.live

        // Najpierw pierwszy plan, potem decyzja. Usługa zawołana przez
        // startForegroundService, która zgaśnie bez startForeground, wywraca
        // aplikację - także wtedy, gdy zgasła, bo nie miała nic do roboty.
        //
        // Zgodę da się cofnąć w ustawieniach w trakcie biegu - wtedy system
        // rzuca tu wyjątkiem. Lepiej zgasnąć po cichu niż wywrócić aplikację;
        // trasa do tej chwili jest już w bazie.
        val ok = runCatching {
            startForeground(
                NOTIFICATION_ID, notification(live?.kind?.label ?: "Aktywność"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }.onFailure { Log.w(TAG, "system nie pozwolil wystartowac GPS", it) }.isSuccess
        if (!ok || live == null || !live.kind.gps || live.paused) {
            if (ok) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (stopListening == null) listen()
        return START_STICKY
    }

    private fun listen() {
        val t = HandlerThread("gps").also { it.start() }
        thread = t
        // Źródło odczytów wybiera [LocationFeed] - sam GPS w budynku nie łapał
        // nic. Co sekundę: gęściej nic nie daje, bo odsiewanie i tak trzyma
        // co kilka metrów.
        stopListening = LocationFeed.listen(applicationContext, t.looper) { loc ->
            PhoneTracker.onLocation(applicationContext, loc)
        }
    }

    override fun onDestroy() {
        stopListening?.invoke()
        stopListening = null
        thread?.quitSafely()
        thread = null
        super.onDestroy()
    }

    private fun notification(label: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nagrywanie aktywności", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, PhoneTracker.openAppIntent(this), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(label)
            .setContentText("Nagrywam trasę - dotknij, żeby wrócić")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GymBroTrack"
        private const val CHANNEL = "track"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            if (!PhoneTracker.hasLocationPermission(context)) return
            context.startForegroundService(Intent(context, TrackService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackService::class.java))
        }
    }
}
