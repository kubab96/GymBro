package pl.gymbro.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Podnosi pilota po restarcie telefonu i po aktualizacji aplikacji.
 *
 * Bez tego usługa wstawała wyłącznie przy otwarciu aplikacji, więc po
 * restarcie telefonu zegarek nie miał z kim rozmawiać - a dowiadywało się
 * o tym dopiero na siłowni, przy pierwszej serii. Trening zaczyna się teraz
 * na zegarku, więc pilot musi stać, zanim ktokolwiek dotknie telefonu.
 *
 * `MY_PACKAGE_REPLACED` jest tu z tego samego powodu: instalacja nowej wersji
 * ubija proces razem z usługą.
 *
 * Typ `connectedDevice` wolno uruchomić z odbiornika startu systemu -
 * ograniczenie Androida 15 obejmuje `dataSync`, `camera`, `mediaPlayback`,
 * `phoneCall`, `mediaProjection` i `microphone`, i tylko te. Mimo to łapiemy
 * wyjątek: nieuruchomiony pilot to niedogodność, wywrócony telefon przy
 * starcie systemu to coś znacznie gorszego.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!WatchLinkService.always(context)) return

        runCatching { WatchLinkService.start(context) }
            .onFailure { Log.w(TAG, "nie udalo sie podniesc pilota po $action", it) }
    }

    private companion object {
        const val TAG = "GymBroBoot"
    }
}
