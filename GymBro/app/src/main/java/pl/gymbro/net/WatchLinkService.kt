package pl.gymbro.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import pl.gymbro.MainActivity
import java.security.SecureRandom

/**
 * Trzyma serwer przy życiu, gdy aplikacja jest w tle.
 *
 * Zwykłe gniazdo w tle nie przetrwa: telefon na siłowni leży w kieszeni
 * z wygaszonym ekranem, a aplikacja w tle traci prawo do pracy. Usługa
 * pierwszoplanowa z powiadomieniem to jedyny sposób, żeby zegarek mógł zapisać
 * serię dwadzieścia minut po tym, jak ostatni raz spojrzałeś na telefon.
 *
 * Powiadomienie nie jest przy okazji złe: widać z niego, że pilot jest włączony,
 * i można go stamtąd wyłączyć.
 *
 * Domyślnie stoi cały czas, nie tylko w trakcie treningu: trening zaczyna się
 * na zegarku, więc pierwsze pytanie przychodzi, zanim jakakolwiek sesja
 * istnieje - patrz [always]. Po wyłączeniu tego w Profilu usługa żyje tylko
 * tyle, co sesja.
 */
class WatchLinkService : Service() {

    private var server: LocalServer? = null
    private var api: WatchApi? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Usługa wstaje też bez aktywności (po restarcie telefonu, START_STICKY),
        // więc zapis awarii musi włączać się i tutaj.
        pl.gymbro.CrashLog.install(this)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Od Androida 14 rodzaj usługi trzeba podać przy starcie, nie tylko
        // w manifeście - inaczej system ubija ją wyjątkiem.
        //
        // Ten sam Android 14 rzuca tu SecurityException, gdy brakuje zgody
        // na urządzenia w pobliżu - patrz [canStart]. Normalnie bez zgody
        // do tego miejsca nie dochodzimy, bo [start] sprawdza ją wcześniej.
        // Zgodę da się jednak cofnąć w ustawieniach, a START_STICKY podnosi
        // usługę bez pytania nas o zdanie - wtedy lepiej zgasnąć po cichu,
        // niż wywrócić całą aplikację.
        val wystartowala = runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification("Zegarek może zapisywać serie"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        }.onFailure { Log.w(TAG, "system nie pozwolil wystartowac pilotowi", it) }.isSuccess
        if (!wystartowala) {
            listening = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (server == null) {
            val token = tokenOf(this)
            val handler = WatchApi(this).also { api = it }
            handler.onChanged = { reason ->
                Changes.bump(reason)
                // Powiadomienie mowi, kiedy zegarek ostatnio cokolwiek zapisal.
                // Dotad stalo na "Zegarek moze zapisywac serie" przez caly
                // trening, wiec po stronie telefonu nie bylo zadnego sladu, czy
                // synchronizacja sie dzieje - a to pierwsze miejsce, w ktore
                // zaglada sie po treningu bez lacznosci.
                pokazOstatni()
            }
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

    /**
     * Odswiezenie powiadomienia, nie czesciej niz raz na pol minuty.
     *
     * Przy szybkim dosylaniu zaleglej kolejki polecenia ida jedno za drugim
     * i kazde odrysowanie powiadomienia byloby praca na nic - godzina i tak
     * zmienia sie raz na minute.
     */
    private fun pokazOstatni() {
        val teraz = System.currentTimeMillis()
        if (teraz - ostatnieOdswiezenie < 30_000) return
        ostatnieOdswiezenie = teraz
        val godzina = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(teraz))
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("Ostatni zapis z zegarka: " + godzina))
        }
    }

    private var ostatnieOdswiezenie = 0L

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Pilot na zegarku",
                // Cicho i bez wibracji: to stan, nie zdarzenie.
                NotificationManager.IMPORTANCE_LOW
            )
        )
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

        /**
         * Czy system pozwoli tej usłudze wystartować.
         *
         * Od Androida 14 usługa pierwszoplanowa typu `connectedDevice` wymaga
         * przyznanej zgody na urządzenia w pobliżu - bez niej `startForeground`
         * rzuca wyjątkiem. Na świeżej instalacji zgody jeszcze nie ma, a pilot
         * wstaje razem z aplikacją, więc pierwsze uruchomienie kończyło się
         * awarią, zanim pokazał się jakikolwiek ekran. Na Androidzie 13
         * i starszych tego warunku nie ma i stąd nie było go widać.
         */
        fun canStart(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
            return NEARBY.any {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        }

        /**
         * Zgody, o które trzeba poprosić, żeby pilot działał i było go widać.
         *
         * Dwie różne sprawy w jednej liście, bo pyta się o nie w tym samym
         * momencie: bez urządzeń w pobliżu pilot na Androidzie 14+ w ogóle
         * nie wstanie, a bez powiadomień wstanie, ale po cichu - nie widać
         * wtedy ani tego, że działa, ani przycisku, którym się go wyłącza.
         * Pusta tablica znaczy, że nie ma o co pytać.
         */
        fun permissionsToAsk(context: Context): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (!canStart(context)) addAll(NEARBY)
        }.toTypedArray()

        /**
         * Zgoda na urządzenia w pobliżu - jedno okno systemowe, dwa uprawnienia.
         *
         * Oba istnieją od Androida 12, a aplikacja wstaje od 10. Lint ostrzega
         * przed tym słusznie, ale tu to tylko napisy: sięga po nie wyłącznie
         * [canStart], który poniżej Androida 14 odpowiada wcześniej.
         */
        @SuppressLint("InlinedApi")
        val NEARBY = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )

        fun start(context: Context) {
            // Bez tego sprawdzenia startForegroundService zobowiązuje usługę
            // do wejścia na pierwszy plan, a ona nie może tego zrobić - i tak
            // źle, i tak niedobrze. Nie startujemy więc wcale; Profil mówi,
            // czego brakuje.
            if (!canStart(context)) return
            val intent = Intent(context, WatchLinkService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchLinkService::class.java))
        }

        /**
         * Czy system obiecał nie ubijać tej aplikacji w tle.
         *
         * Usługa pierwszoplanowa ze `START_STICKY` wystarcza w czystym
         * Androidzie i nie wystarcza na HyperOS: bez tego wyjątku usługa
         * ginie po kilkunastu minutach z wygaszonym ekranem, czyli dokładnie
         * wtedy, gdy telefon leży w szatni. Trening 02.11 nie dojechał
         * właśnie dlatego - zegarek nadawał w próżnię, a telefon nie miał
         * jak powiedzieć, że przestał słuchać.
         */
        fun batteryUnrestricted(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return true
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        /**
         * Systemowe okno z pytaniem o ten wyjątek.
         *
         * Prosto do okna, a nie do listy ustawień: droga przez ustawienia
         * ma na tym telefonie pięć kroków i trzy różne nazwy tej samej opcji.
         * Gdyby producent okna nie miał, zostaje ekran z listą aplikacji.
         */
        fun askBattery(context: Context) {
            val wprost = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val zapasowy = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(wprost) }
                .recoverCatching { context.startActivity(zapasowy) }
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
