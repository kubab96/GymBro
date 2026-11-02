package pl.gymbro.net

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Skanowanie kodów kreskowych.
 *
 * Skaner Google Play Services, a nie własny ekran na CameraX. Powód jest
 * praktyczny: **ta droga nie wymaga uprawnienia do kamery.** Skanuje okno
 * należące do Play Services, a aplikacja dostaje wyłącznie odczytany kod -
 * więc nie ma pytania o dostęp do aparatu, nie ma własnego podglądu do
 * utrzymania i nie ma kodu obsługi cyklu życia kamery.
 *
 * Cena: potrzebne są Google Play Services, a sam moduł skanera dociąga się
 * przy pierwszym użyciu. Pierwszy skan w życiu aplikacji może więc chwilę
 * poczekać - i dlatego [warmUp] prosi o moduł wcześniej, kiedy nikt nie stoi
 * przed półką.
 *
 * Gdyby kiedyś potrzebne było skanowanie seryjne, kilku produktów bez
 * zamykania okna, trzeba będzie wrócić do ML Kit i własnego podglądu.
 * Do jednego produktu naraz to byłby koszt bez pokrycia.
 */
object Barcodes {

    /** Wynik próby zeskanowania. */
    sealed interface Scan {
        data class Code(val value: String) : Scan

        /** Zamknięte przyciskiem wstecz. Nie jest błędem i nie zasługuje na komunikat. */
        data object Cancelled : Scan

        data class Failed(val reason: String) : Scan
    }

    /**
     * Formaty, których szukamy.
     *
     * Wyłącznie kody produktów spożywczych. Bez tego ograniczenia skaner
     * łapie pierwszy lepszy QR z opakowania - a te na żywności prowadzą
     * zwykle do strony producenta i nie mają nic wspólnego z kodem towaru.
     */
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E
        )
        // Od wersji 16.1.0: samo przybliża, więc kod z dalszej odległości
        // i mały kod na puszce łapią się bez podchodzenia.
        .enableAutoZoom()
        .build()

    /**
     * Otwiera skaner i czeka na kod.
     *
     * Wołać z wątku głównego - okno należy do Play Services, a my tylko
     * czekamy na wynik.
     */
    suspend fun scan(context: Context): Scan = suspendCancellableCoroutine { cont ->
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (cont.isActive) {
                    cont.resume(
                        if (value.isNullOrBlank()) Scan.Failed("kod nieczytelny")
                        else Scan.Code(value.trim())
                    )
                }
            }
            .addOnCanceledListener {
                if (cont.isActive) cont.resume(Scan.Cancelled)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resume(Scan.Failed(describe(e)))
            }
    }

    /**
     * Prosi Play Services o moduł skanera z wyprzedzeniem.
     *
     * Bez tego pierwszy skan w życiu aplikacji zaczyna się od pobierania,
     * a trafia to dokładnie w moment, w którym stoisz w sklepie z produktem
     * w ręce. Wywołanie jest ciche i bez skutków, gdy moduł już jest albo
     * gdy nie ma Play Services - nie ma po co o tym mówić.
     */
    fun warmUp(context: Context) {
        runCatching {
            ModuleInstall.getClient(context)
                .installModules(
                    ModuleInstallRequest.newBuilder()
                        .addApi(GmsBarcodeScanning.getClient(context, options))
                        .build()
                )
        }
    }

    /**
     * Komunikat po polsku zamiast kodu błędu.
     *
     * Najczęstszy przypadek to brak albo nieaktualne Google Play Services -
     * i wtedy trzeba powiedzieć, co z tym zrobić, a nie wypisać numer.
     */
    private fun describe(e: Exception): String {
        val text = e.message.orEmpty()
        return when {
            text.contains("UNAVAILABLE", true) ||
                text.contains("module", true) ->
                "Skaner wymaga Google Play Services - sprawdź, czy są zaktualizowane."
            text.contains("CAMERA", true) ->
                "Nie udało się otworzyć aparatu."
            text.isBlank() -> "Skanowanie się nie powiodło."
            else -> text
        }
    }
}
