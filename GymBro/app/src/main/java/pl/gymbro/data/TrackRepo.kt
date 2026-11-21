package pl.gymbro.data

import android.content.ContentValues
import android.content.Context
import org.json.JSONObject
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.TrackMath

/**
 * Trwająca aktywność nagrywana telefonem.
 *
 * Trzymana w `app_state`, a nie w `external_session`: wiersz w historii
 * powstaje dopiero na końcu. Wcześniejszy pokazywałby się na liście jako
 * zero minut i liczył do podsumowań, zanim cokolwiek się wydarzyło.
 *
 * Pauzy liczymy jak w treningu siłowym: [pausedMs] to suma zamkniętych,
 * [pausedAt] - otwarta.
 */
data class LiveActivity(
    val key: String,
    val kind: PhoneActivity,
    val startedAt: Long,
    val pausedAt: Long? = null,
    val pausedMs: Long = 0,
    /** Numer odcinka trasy; rośnie po każdej pauzie - patrz [TrackMath.Fix.seg]. */
    val seg: Int = 0
) {
    val paused: Boolean get() = pausedAt != null

    fun movingMs(now: Long): Long =
        (now - startedAt - pausedMs - (pausedAt?.let { now - it } ?: 0L)).coerceAtLeast(0L)

    fun pause(now: Long): LiveActivity = if (paused) this else copy(pausedAt = now)

    fun resume(now: Long): LiveActivity {
        val at = pausedAt ?: return this
        return copy(pausedAt = null, pausedMs = pausedMs + (now - at).coerceAtLeast(0), seg = seg + 1)
    }

    fun toJson(): String = JSONObject()
        .put("key", key).put("kind", kind.name).put("startedAt", startedAt)
        .put("pausedAt", pausedAt ?: -1L).put("pausedMs", pausedMs).put("seg", seg)
        .toString()

    companion object {
        fun fromJson(s: String): LiveActivity? = runCatching {
            val o = JSONObject(s)
            LiveActivity(
                key = o.getString("key"),
                kind = PhoneActivity.valueOf(o.getString("kind")),
                startedAt = o.getLong("startedAt"),
                pausedAt = o.getLong("pausedAt").takeIf { it >= 0 },
                pausedMs = o.getLong("pausedMs"),
                seg = o.getInt("seg")
            )
        }.getOrNull()
    }
}

class TrackRepository(context: Context) {
    private val db = Db.get(context)

    fun add(activity: String, f: TrackMath.Fix) {
        db.writable.insert("track_point", null, ContentValues().apply {
            put("activity", activity)
            put("at", f.at)
            put("lat", f.lat)
            put("lon", f.lon)
            put("accuracy", f.accuracyM.toDouble())
            put("seg", f.seg)
        })
    }

    fun points(activity: String): List<TrackMath.Fix> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM track_point WHERE activity = ? ORDER BY at",
            arrayOf(activity)
        ).mapAll {
            TrackMath.Fix(
                at = it.long("at"),
                lat = it.dbl("lat"),
                lon = it.dbl("lon"),
                accuracyM = it.dbl("accuracy").toFloat(),
                seg = it.int("seg")
            )
        }

    fun live(): LiveActivity? =
        db.readableDatabase.rawQuery(
            "SELECT value FROM app_state WHERE key = ?", arrayOf(LIVE_KEY)
        ).mapAll { it.str("value") }.firstOrNull()?.let(LiveActivity::fromJson)

    fun saveLive(l: LiveActivity?) {
        if (l == null) {
            db.writable.delete("app_state", "key = ?", arrayOf(LIVE_KEY))
        } else {
            db.writable.insertWithOnConflict(
                "app_state", null,
                ContentValues().apply { put("key", LIVE_KEY); put("value", l.toJson()) },
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    /**
     * Zamyka aktywność: wiersz w historii i koniec stanu trwającego, razem
     * albo wcale. Przerwane w połowie zostawiłoby bieg w historii i ten sam
     * bieg dalej „trwający" - po starcie aplikacji zapisałby się drugi raz.
     */
    fun finish(session: ExternalSession) {
        val w = db.writable
        w.beginTransaction()
        try {
            HealthRepository.insert(w, session)
            w.delete("app_state", "key = ?", arrayOf(LIVE_KEY))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Odrzucona aktywność albo skasowana z historii: znika razem z trasą. */
    fun delete(activity: String) {
        val w = db.writable
        w.beginTransaction()
        try {
            w.delete("track_point", "activity = ?", arrayOf(activity))
            w.delete("external_session", "external_id = ?", arrayOf(activity))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private companion object {
        const val LIVE_KEY = "phone_activity"
    }
}
