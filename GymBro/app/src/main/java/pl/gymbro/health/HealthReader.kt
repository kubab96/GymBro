package pl.gymbro.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Odczyt danych z Health Connect.
 *
 * Cel Fazy 0: udowodnic, ze dane z Amazfit Balance realnie docieraja do naszej
 * aplikacji, i zobaczyc, KTORE pola Zepp faktycznie zapisuje. Dlatego przy kazdym
 * rekordzie pokazujemy pakiet zrodlowy (dataOrigin) - to jest dowod, ze dana
 * pozycja przyszla z Zepp, a nie z Google Fit czy recznego wpisu.
 *
 * Uwaga: integracja Zepp <-> Health Connect jest jednokierunkowa. Zepp tylko
 * zapisuje. Nasza aplikacja jest konsumentem i wlasnym zrodlem prawdy.
 */
class HealthReader(private val context: Context) {

    companion object {
        /** Uprawnienia, o ktore prosimy. Musza miec odpowiedniki w manifescie. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class)
        )

        const val PLAY_STORE_HEALTH_CONNECT =
            "market://details?id=com.google.android.apps.healthdata"
    }

    enum class Availability { OK, NEEDS_UPDATE, UNAVAILABLE }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.OK
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_UPDATE
        else -> Availability.UNAVAILABLE
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    private val dateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())

    /**
     * Zrzuca podsumowanie ostatnich [days] dni.
     * Wynik to zwykly tekst - w spike'u chodzi o to, zeby zobaczyc dane,
     * nie o ladne wykresy.
     */
    suspend fun dumpLastDays(days: Long = 30): String {
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
        val range = TimeRangeFilter.between(start, end)
        val out = StringBuilder()

        out.appendLine("=== Health Connect: ostatnie $days dni ===")
        out.appendLine()

        // --- Treningi -----------------------------------------------------
        runCatching {
            val sessions = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range)
            ).records.sortedByDescending { it.startTime }

            out.appendLine("TRENINGI (${sessions.size}):")
            if (sessions.isEmpty()) {
                out.appendLine("  brak - sprawdz, czy w Zepp wlaczyles Profil > Konta zewnetrzne > Health Connect")
            }
            sessions.take(25).forEach { s ->
                val mins = Duration.between(s.startTime, s.endTime).toMinutes()
                out.appendLine(
                    "  ${dateFmt.format(s.startTime)}  ${exerciseName(s.exerciseType)}" +
                        "  ${mins} min" +
                        (s.title?.let { "  \"$it\"" } ?: "")
                )
                out.appendLine("      zrodlo: ${s.metadata.dataOrigin.packageName}")
            }
        }.onFailure { out.appendLine("  BLAD odczytu treningow: ${it.message}") }
        out.appendLine()

        // --- Tetno --------------------------------------------------------
        runCatching {
            val hr = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range)).records
            val samples = hr.sumOf { it.samples.size }
            val all = hr.flatMap { it.samples }.map { it.beatsPerMinute }
            out.appendLine("TETNO: ${hr.size} rekordow, $samples probek" +
                if (all.isNotEmpty()) "  (min ${all.min()}, max ${all.max()})" else "")
            hr.firstOrNull()?.let { out.appendLine("  zrodlo: ${it.metadata.dataOrigin.packageName}") }
        }.onFailure { out.appendLine("TETNO: blad - ${it.message}") }

        // --- HRV (klucz do modulu gotowosci) ------------------------------
        runCatching {
            val hrv = client.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, range)
            ).records.sortedByDescending { it.time }
            out.appendLine("HRV (RMSSD): ${hrv.size} pomiarow")
            hrv.take(7).forEach {
                out.appendLine("  ${dateFmt.format(it.time)}  %.1f ms".format(it.heartRateVariabilityMillis))
            }
        }.onFailure { out.appendLine("HRV: blad - ${it.message}") }

        // --- Tetno spoczynkowe --------------------------------------------
        runCatching {
            val rhr = client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, range)
            ).records.sortedByDescending { it.time }
            out.appendLine("TETNO SPOCZYNKOWE: ${rhr.size} pomiarow")
            rhr.take(7).forEach {
                out.appendLine("  ${dateFmt.format(it.time)}  ${it.beatsPerMinute} bpm")
            }
        }.onFailure { out.appendLine("TETNO SPOCZYNKOWE: blad - ${it.message}") }

        // --- Sen -----------------------------------------------------------
        runCatching {
            val sleep = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, range)
            ).records.sortedByDescending { it.startTime }
            out.appendLine("SEN: ${sleep.size} nocy")
            sleep.take(7).forEach { s ->
                val h = Duration.between(s.startTime, s.endTime).toMinutes() / 60.0
                out.appendLine("  ${dateFmt.format(s.startTime)}  %.1f h  (faz: ${s.stages.size})".format(h))
            }
            sleep.firstOrNull()?.let { out.appendLine("  zrodlo: ${it.metadata.dataOrigin.packageName}") }
        }.onFailure { out.appendLine("SEN: blad - ${it.message}") }

        // --- Masa ciala i tkanka tluszczowa --------------------------------
        runCatching {
            val w = client.readRecords(ReadRecordsRequest(WeightRecord::class, range))
                .records.sortedByDescending { it.time }
            out.appendLine("MASA CIALA: ${w.size} pomiarow")
            w.take(10).forEach {
                out.appendLine("  ${dateFmt.format(it.time)}  %.2f kg   [${it.metadata.dataOrigin.packageName}]"
                    .format(it.weight.inKilograms))
            }
        }.onFailure { out.appendLine("MASA CIALA: blad - ${it.message}") }

        runCatching {
            val bf = client.readRecords(ReadRecordsRequest(BodyFatRecord::class, range))
                .records.sortedByDescending { it.time }
            out.appendLine("TKANKA TLUSZCZOWA: ${bf.size} pomiarow")
            bf.take(5).forEach {
                out.appendLine("  ${dateFmt.format(it.time)}  %.1f %%".format(it.percentage.value))
            }
        }.onFailure { out.appendLine("TKANKA TLUSZCZOWA: blad - ${it.message}") }

        // --- VO2max -------------------------------------------------------
        runCatching {
            val vo2 = client.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, range))
                .records.sortedByDescending { it.time }
            out.appendLine("VO2MAX: ${vo2.size} pomiarow")
            vo2.take(3).forEach {
                out.appendLine("  ${dateFmt.format(it.time)}  %.1f".format(it.vo2MillilitersPerMinuteKilogram))
            }
        }.onFailure { out.appendLine("VO2MAX: blad - ${it.message}") }

        // --- Kroki i dystans ----------------------------------------------
        runCatching {
            val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, range)).records
            out.appendLine("KROKI: ${steps.sumOf { it.count }} lacznie w ${steps.size} rekordach")
        }.onFailure { out.appendLine("KROKI: blad - ${it.message}") }

        runCatching {
            val d = client.readRecords(ReadRecordsRequest(DistanceRecord::class, range)).records
            out.appendLine("DYSTANS: %.1f km lacznie".format(d.sumOf { it.distance.inKilometers }))
        }.onFailure { out.appendLine("DYSTANS: blad - ${it.message}") }

        out.appendLine()
        out.appendLine("--- Wniosek Fazy 0 ---")
        out.appendLine("Sprawdz, przy ktorych pozycjach widnieje pakiet Zepp.")
        out.appendLine("To mowi nam dokladnie, co da sie wziac z zegarka, a co musimy")
        out.appendLine("dociagnac ze Stravy albo z pliku FIT.")

        return out.toString()
    }

    /** Health Connect koduje typ aktywnosci jako int. Mapujemy te, ktore nas interesuja. */
    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Bieg"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Bieg (bieznia)"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marsz"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trekking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Rower"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Rower stacjonarny"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Plywanie (basen)"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Plywanie (otwarte)"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Trening silowy"
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Podnoszenie ciezarow"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Orbitrek"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Wioslarz"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Schody"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Joga"
        ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Inny trening"
        else -> "Typ $type"
    }
}
