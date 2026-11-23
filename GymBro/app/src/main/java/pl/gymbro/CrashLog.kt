package pl.gymbro

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ostatnie awarie aplikacji, zapisane do pliku.
 *
 * Telefon (HyperOS) nie zostawia po awarii aplikacji żadnego śladu w systemowej
 * skrzynce zrzutów, a logcat nadpisuje się w kilka godzin - po treningu, który
 * wywrócił aplikację, nie było już czego przeczytać i przyczynę dało się tylko
 * zgadywać. Plik leży w katalogu aplikacji i czyta się go przez
 * `adb exec-out run-as pl.gymbro cat files/awarie.txt`.
 *
 * Trzyma kilka ostatnich wpisów, żeby nie rósł bez końca.
 */
object CrashLog {

    private const val FILE = "awarie.txt"
    private const val MAX_BYTES = 64 * 1024

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { write(app, "awaria w watku ${thread.name}", error) }
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /**
     * Błąd złapany i obsłużony, ale wart zapamiętania - na przykład liczenie
     * propozycji, które nie może zatrzymać końca treningu.
     */
    fun note(context: Context, what: String, error: Throwable) {
        Log.w("GymBro", what, error)
        runCatching { write(context.applicationContext, what, error) }
    }

    /**
     * Zdarzenie bez wyjatku, ale z tej samej polki: cos przepadlo po drodze.
     *
     * Odmowa polecenia z zegarka jest tu najwazniejsza. Zegarek wyrzuca
     * odrzucone polecenie z kolejki, wiec bez tego wpisu seria, ktora nigdy
     * nie trafila do bazy, nie zostawiala sladu po zadnej stronie.
     */
    fun line(context: Context, what: String) {
        Log.w("GymBro", what)
        runCatching { write(context.applicationContext, what, null) }
    }

    @Synchronized
    private fun write(context: Context, what: String, error: Throwable?) {
        val file = File(context.filesDir, FILE)
        val stack = error?.let { e -> StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString() }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val entry = if (stack != null) "=== $time - $what\n$stack\n" else "=== $time - $what\n"
        val old = if (file.exists()) file.readText() else ""
        val joined = old + entry
        file.writeText(if (joined.length > MAX_BYTES) joined.takeLast(MAX_BYTES) else joined)
    }
}
