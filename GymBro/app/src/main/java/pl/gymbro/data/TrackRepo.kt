package pl.gymbro.data

import android.content.ContentValues
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.gymbro.gym.PhoneActivity
import pl.gymbro.gym.RunRecords
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
 *
 * Trening siłowy trzyma tu swoje serie ([sets], [setStartedAt]): jest ich
 * kilkanaście, więc mieszczą się w tym samym zapisie, a proces ubity
 * w szatni wstaje z tą samą liczbą serii i z biegnącą serią.
 */
data class LiveActivity(
    val key: String,
    val kind: PhoneActivity,
    val startedAt: Long,
    val pausedAt: Long? = null,
    val pausedMs: Long = 0,
    /** Numer odcinka trasy; rośnie po każdej pauzie - patrz [TrackMath.Fix.seg]. */
    val seg: Int = 0,
    /** Zakończone serie, od pierwszej. */
    val sets: List<PhoneSet> = emptyList(),
    /** Początek trwającej serii; null - przerwa. */
    val setStartedAt: Long? = null,
    /** Docelowa przerwa między seriami, w sekundach. */
    val restSec: Int = DEFAULT_REST_SEC
) {
    val paused: Boolean get() = pausedAt != null
    val inSet: Boolean get() = setStartedAt != null

    fun movingMs(now: Long): Long =
        (now - startedAt - pausedMs - (pausedAt?.let { now - it } ?: 0L)).coerceAtLeast(0L)

    /**
     * Pauza zamyka trwającą serię. Seria liczona przez pauzę miałaby
     * w historii dziesięć minut i zaniżała średnią przerwę.
     */
    fun pause(now: Long): LiveActivity =
        if (paused) this else endSet(now).copy(pausedAt = now)

    fun resume(now: Long): LiveActivity {
        val at = pausedAt ?: return this
        return copy(pausedAt = null, pausedMs = pausedMs + (now - at).coerceAtLeast(0), seg = seg + 1)
    }

    fun startSet(now: Long): LiveActivity =
        if (inSet || paused) this else copy(setStartedAt = now)

    fun endSet(now: Long): LiveActivity {
        val at = setStartedAt ?: return this
        return copy(sets = sets + PhoneSet(at, now.coerceAtLeast(at)), setStartedAt = null)
    }

    /** Pomyłkowe dotknięcie: trwająca seria znika, a bez niej - ostatnia zakończona. */
    fun dropSet(): LiveActivity =
        if (inSet) copy(setStartedAt = null) else copy(sets = sets.dropLast(1))

    /**
     * Usunięcie wybranej serii z listy - pomyłkowa albo podwójnie dotknięta
     * seria ze środka treningu. Przerwa wokół niej skleja się w jedną, bo
     * przerwy liczą się z sąsiednich serii.
     */
    fun dropSetAt(index: Int): LiveActivity =
        if (index !in sets.indices) this else copy(sets = sets.filterIndexed { i, _ -> i != index })

    fun toJson(): String = JSONObject()
        .put("key", key).put("kind", kind.name).put("startedAt", startedAt)
        .put("pausedAt", pausedAt ?: -1L).put("pausedMs", pausedMs).put("seg", seg)
        .put("sets", JSONArray().apply {
            sets.forEach { put(JSONArray().put(it.startedAt).put(it.endedAt)) }
        })
        .put("setStartedAt", setStartedAt ?: -1L)
        .put("restSec", restSec)
        .toString()

    companion object {
        /** Półtorej minuty - typowa przerwa w treningu na masę, łatwa do zmiany na ekranie. */
        const val DEFAULT_REST_SEC = 90

        fun fromJson(s: String): LiveActivity? = runCatching {
            val o = JSONObject(s)
            val arr = o.optJSONArray("sets")
            LiveActivity(
                key = o.getString("key"),
                kind = PhoneActivity.valueOf(o.getString("kind")),
                startedAt = o.getLong("startedAt"),
                pausedAt = o.getLong("pausedAt").takeIf { it >= 0 },
                pausedMs = o.getLong("pausedMs"),
                seg = o.getInt("seg"),
                // Zapis sprzed treningu siłowego nie ma tych pól - stąd opt.
                sets = (0 until (arr?.length() ?: 0)).map { i ->
                    val p = arr!!.getJSONArray(i)
                    PhoneSet(p.getLong(0), p.getLong(1))
                },
                setStartedAt = o.optLong("setStartedAt", -1L).takeIf { it >= 0 },
                restSec = o.optInt("restSec", DEFAULT_REST_SEC)
            )
        }.getOrNull()
    }
}

/** Seria treningu siłowego nagranego telefonem: sam czas, bez ćwiczenia i ciężaru. */
data class PhoneSet(val startedAt: Long, val endedAt: Long) {
    val ms: Long get() = endedAt - startedAt
}

/** Przerwy między kolejnymi seriami, w ms. */
fun restsMs(sets: List<PhoneSet>): List<Long> =
    sets.zipWithNext { a, b -> (b.startedAt - a.endedAt).coerceAtLeast(0) }

class TrackRepository(private val context: Context) {
    private val db = Db.get(context)

    /**
     * Biegi i jazdy z całej historii, z trasami biegów nagranych telefonem -
     * wszystko, czego potrzebują rekordy. Blokujące.
     */
    fun runHistory(): Pair<List<ExternalSession>, Map<String, List<TrackMath.Fix>>> {
        val sessions = HealthRepository(context).byTypes(RunRecords.RUN_TYPES + RunRecords.BIKE_TYPES)
        val tracks = sessions.filter { it.fromPhone && it.type in RunRecords.RUN_TYPES }
            .associate { it.externalId to points(it.externalId) }
        return sessions to tracks
    }

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

    /**
     * Usuwa jedną serię zapisanego treningu. Wszystkie serie tego treningu
     * zapisują się od nowa z kolejnymi numerami - przesuwanie numerów
     * pojedynczym UPDATE potrafi po drodze trafić na zajęty klucz.
     */
    fun deleteSet(activity: String, index: Int) {
        val left = sets(activity).filterIndexed { i, _ -> i != index }
        val w = db.writable
        w.beginTransaction()
        try {
            w.delete("phone_set", "activity = ?", arrayOf(activity))
            left.forEachIndexed { i, s ->
                w.insert("phone_set", null, ContentValues().apply {
                    put("activity", activity)
                    put("idx", i)
                    put("started_at", s.startedAt)
                    put("ended_at", s.endedAt)
                })
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Serie treningu siłowego nagranego telefonem, od pierwszej. */
    fun sets(activity: String): List<PhoneSet> =
        db.readableDatabase.rawQuery(
            "SELECT started_at, ended_at FROM phone_set WHERE activity = ? ORDER BY idx",
            arrayOf(activity)
        ).mapAll { PhoneSet(it.long("started_at"), it.long("ended_at")) }

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
    fun finish(session: ExternalSession, sets: List<PhoneSet> = emptyList()) {
        val w = db.writable
        w.beginTransaction()
        try {
            HealthRepository.insert(w, session)
            sets.forEachIndexed { i, s ->
                w.insert("phone_set", null, ContentValues().apply {
                    put("activity", session.externalId)
                    put("idx", i)
                    put("started_at", s.startedAt)
                    put("ended_at", s.endedAt)
                })
            }
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
            w.delete("phone_set", "activity = ?", arrayOf(activity))
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
