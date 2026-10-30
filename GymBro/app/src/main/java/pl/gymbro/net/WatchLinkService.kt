package pl.gymbro.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import pl.gymbro.MainActivity
import java.security.SecureRandom

/**
 * Trzyma serwer przy życiu na czas treningu.
 *
 * Zwykłe gniazdo w tle nie przetrwa: telefon na siłowni leży w kieszeni
 * z wygaszonym ekranem, a aplikacja w tle traci prawo do pracy. Usługa
 * pierwszoplanowa z powiadomieniem to jedyny sposób, żeby zegarek mógł zapisać
 * serię dwadzieścia minut po tym, jak ostatni raz spojrzałeś na telefon.
 *
 * Powiadomienie nie jest przy okazji złe: widać z niego, że pilot jest włączony,
 * i można go stamtąd wyłączyć. Serwer działa tylko w trakcie treningu, więc poza
 * siłownią nie ma otwartego gniazda ani nic nie zjada baterii.
 */
class WatchLinkService : Service() {

    private var server: LocalServer? = null
    private var api: WatchApi? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Od Androida 14 rodzaj usługi trzeba podać przy starcie, nie tylko
        // w manifeście - inaczej system ubija ją wyjątkiem.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification("Zegarek może zapisywać serie"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification("Zegarek może zapisywać serie"))
        }

        if (server == null) {
            val token = tokenOf(this)
            val handler = WatchApi(this).also { api = it }
            handler.onChanged = { reason -> Changes.bump(reason) }
            val srv = LocalServer(PORT) { request ->
                if (request.token != token) {
                    Log.i(TAG, "odrzucone zadanie: zly token")
                    LocalServer.Response.error(401, "zly token")
                } else {
                    handler.handle(request)
                }
            }
            server = if (srv.start()) srv else null
            listening = server != null
            if (server == null) {
                // Port zajęty: bez serwera usługa nie ma czego pilnować.
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listening = false
        server?.stop()
        server = null
        api = null
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Pilot na zegarku",
                    // Cicho i bez wibracji: to stan, nie zdarzenie.
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WatchLinkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Pilot na zegarku")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Wyłącz", stop).build()
            )
            .build()
    }

    /** Sygnał dla ekranów, że dane zmieniły się spoza aplikacji. */
    object Changes {
        private val listeners = mutableListOf<(String) -> Unit>()

        @Synchronized
        fun listen(block: (String) -> Unit): () -> Unit {
            listeners += block
            return { unlisten(block) }
        }

        @Synchronized
        private fun unlisten(block: (String) -> Unit) {
            listeners -= block
        }

        @Synchronized
        fun bump(reason: String) = listeners.toList().forEach { it(reason) }
    }

    companion object {
        /** Stały port, bo zegarek musi go znać bez negocjacji. */
        const val PORT = 48627

        private const val TAG = "GymBroLink"
        private const val CHANNEL = "watch-link"
        private const val NOTIFICATION_ID = 7312
        private const val ACTION_STOP = "pl.gymbro.STOP_LINK"
        private const val PREFS = "watch-link"
        private const val KEY_TOKEN = "token"


        private const val KEY_ALWAYS = "always"

        /**
         * Czy pilot faktycznie nasluchuje.
         *
         * Nie wystarczy sprawdzic ustawienia: system potrafi ubic usluge,
         * a po restarcie telefonu nie wstaje ona sama, dopoki nie zadziala
         * [BootReceiver]. Profil pokazuje ten stan, zeby nie trzeba bylo
         * tego zgadywac dopiero na silowni.
         */
        @Volatile
        var listening: Boolean = false
            private set

        /**
         * Czy pilot ma stać także poza treningiem.
         *
         * Domyślnie tak, bo trening zaczyna się na zegarku: zanim sesja
         * istnieje, nie ma czego obudzić żądaniem, a serwer wstający razem
         * z sesją nie odebrałby tego pierwszego pytania o jednostki.
         * Wyłącznik jest w Profilu - wtedy pilot żyje tylko w czasie sesji.
         */
        fun always(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ALWAYS, true)

        fun setAlways(context: Context, value: Boolean) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ALWAYS, value).apply()
            if (value) start(context) else stop(context)
        }

        /** Stan zgodny z tym, co ma być: trwa trening albo pilot jest na stałe. */
        fun sync(context: Context, sessionActive: Boolean) {
            if (sessionActive || always(context)) start(context) else stop(context)
        }
        fun start(context: Context) {
            val intent = Intent(context, WatchLinkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchLinkService::class.java))
        }

        /**
         * Hasło, którym zegarek potwierdza, że to on.
         *
         * Serwer stoi na pętli zwrotnej, więc z sieci nikt się nie dobije - ale
         * każda inna aplikacja na tym telefonie może do niego zapukać. Token
         * sprawia, że musiałaby go najpierw poznać. Losowany raz i pokazywany
         * w Profilu, żeby dało się go wpisać w ustawieniach aplikacji zegarka.
         */
        fun tokenOf(context: Context): String {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val bytes = ByteArray(8)
            SecureRandom().nextBytes(bytes)
            val token = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_TOKEN, token).apply()
            return token
        }
    }
}
