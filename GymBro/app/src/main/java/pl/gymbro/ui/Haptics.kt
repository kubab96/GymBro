package pl.gymbro.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Wibracja na koniec przerwy między seriami.
 *
 * Telefon leży wtedy zwykle na ławce ekranem w dół albo w kieszeni, więc sygnał
 * musi być wyczuwalny bez patrzenia. Stąd podwójny impuls zamiast jednego -
 * pojedynczy krótki gubi się wśród powiadomień.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Krótkie potwierdzenie - zapisana seria. */
    fun tick(context: Context) {
        val v = vibrator(context) ?: return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Koniec przerwy - wyraźny, dwuimpulsowy. */
    fun restOver(context: Context) {
        val v = vibrator(context) ?: return
        runCatching {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 250, 150, 250),
                    -1 // bez powtarzania
                )
            )
        }
    }
}
