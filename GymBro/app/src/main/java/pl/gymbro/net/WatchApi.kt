package pl.gymbro.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.gymbro.data.GymRepository
import pl.gymbro.data.roundKg
import pl.gymbro.data.PlanRepository
import pl.gymbro.data.BodyRepository
import pl.gymbro.data.ProfileRepository
import pl.gymbro.data.Sex
import pl.gymbro.data.ProgramRepository
import pl.gymbro.data.SessionRepository
import pl.gymbro.data.SetEntry
import pl.gymbro.gym.LiveSession
import pl.gymbro.gym.LoadMode
import pl.gymbro.gym.ProgressionReview
import pl.gymbro.gym.PlateCalculator
import pl.gymbro.gym.StaleSession
import pl.gymbro.gym.StepLadder

/**
 * Odpowiedzi dla pilota na zegarku.
 *
 * Zegarek jest cienkim klientem i nic nie liczy. Nie zna talerzy, progresji ani
 * tego, które ćwiczenie jest następne - pyta o to telefon. Dzięki temu ciężar
 * proponowany na zegarku jest dokładnie ten sam, co na ekranie, bo liczy go ten
 * sam kod ([[LiveSession]] i [PlateCalculator]). Gdyby zegarek liczył sam,
 * rozjechałoby się to przy pierwszej zmianie w progresji.
 *
 * Każda odpowiedź po zmianie zawiera pełny stan, więc zegarek nigdy nie musi
 * zgadywać, co się udało - po prostu rysuje to, co dostał.
 */
class WatchApi(context: Context) {

    private val app = context.applicationContext
    private val sessionRepo = SessionRepository(app)
    private val planRepo = PlanRepository(app)
    private val programRepo = ProgramRepository(app)
    private val gymRepo = GymRepository(app)
    private val profileRepo = ProfileRepository(app)
    private val bodyRepo = BodyRepository(app)

    /** Wołane po każdej zmianie, żeby ekran telefonu pokazał serię z zegarka. */
    var onChanged: ((String) -> Unit)? = null

    /**
     * Znaczniki polecen, ktore juz wykonalismy - z numerem serii, ktora
     * polecenie zalozylo (0, gdy nie zakladalo zadnej).
     *
     * Kolejnosc wstawiania ma znaczenie: przy przepelnieniu wypada najstarszy.
     * Numer serii jest tu po to, zeby zegarek mogl poprawic albo opisac zapasem
     * serie, ktorej numeru jeszcze nie poznal - wysyla wtedy znacznik polecenia
     * (`ref`), a my zamieniamy go na numer. Bez tego zapas podany po serii,
     * ktora jeszcze czekala w kolejce, trafial w "ostatnia serie cwiczenia",
     * czyli w cudza, gdy w kolejce stalo ich kilka.
     */
    private val seen = LinkedHashMap<String, Long>()

    /**
     * Ustawienia biezacej obslugi. Serwer obsluguje polaczenia po kolei,
     * jednym watkiem, wiec dwa polecenia nigdy nie dziela tych pol naraz.
     *
     * [lekki] - odpowiedz bez drabinek ciezarow. Zegarek ma je z poczatku
     * treningu, a w kazdej odpowiedzi na serie byly wiekszoscia ladunku -
     * kilka kilobajtow przez Bluetooth przy kazdym nacisnieciu.
     *
     * [potwierdzenie] - zamiast stanu samo "zrobione" z numerem sesji. Zegarek
     * liczy ekran treningu u siebie i odpowiedzi nie rysuje; nawet lekki stan
     * to byl kilobajt lub dwa przez Bluetooth, rozkladany na zegarku w tym
     * samym watku, ktory obsluguje palec - przy kazdej serii i kazdym zapasie.
     *
     * [cichy] - bez stanu w ogole: polecenia z paczki dostaja jeden wspolny
     * stan na koniec, zamiast liczyc go od nowa po kazdym.
     */
    private var lekki = false
    private var cichy = false
    private var potwierdzenie = false

    /** Numer serii zalozonej przez biezace polecenie. */
    private var nowaSeria = 0L

    /**
     * Sesja, do ktorej nalezy polecenie z zegarka.
     *
     * Zegarek dokleja do kazdego polecenia znacznik swojej sesji: nadany
     * przez siebie albo numer od telefonu. Sesja bywa juz zamknieta - zalegle
     * serie dojezdzaja po treningu - i wtedy tez ma je dostac.
     *
     * Znacznik zegarka, ktorego telefon nie zna, znaczy, ze START tej sesji
     * nie dojechal. Wczesniej polecenie szlo wtedy do [active], czyli do
     * jakiegokolwiek trwajacego treningu - i serie nowego treningu laczyly
     * sie ze starym w jeden. Lepiej odmowic jednego polecenia niz pomieszac
     * dwa treningi.
     */
    private fun sesja(body: JSONObject): pl.gymbro.data.Session? {
        val sid = body.optString("sid").takeIf { it.isNotBlank() }
            ?: return sessionRepo.active()
        sessionRepo.byLocalId(sid)?.let { return it }
        if (sid.all { it.isDigit() }) return sid.toLongOrNull()?.let { sessionRepo.sessionById(it) }
        return null
    }

    /** Numer serii z polecenia: wprost albo przez znacznik polecenia, ktore ja zalozylo. */
    private fun setIdOf(body: JSONObject): Long {
        val wprost = body.optLong("setId")
        if (wprost > 0) return wprost
        val ref = body.optString("ref").takeIf { it.isNotBlank() } ?: return 0
        return seen[ref] ?: 0
    }

    /** Odpowiedz na polecenie zmieniajace zapis. */
    private fun stan(): JSONObject = when {
        cichy -> JSONObject().put("ok", true)
        potwierdzenie -> ack()
        else -> state(lekki)
    }

    /**
     * Samo potwierdzenie: czy trening trwa i ktory.
     *
     * Tyle zegarek musi wiedziec po kazdym poleceniu - czy jego trening dalej
     * jest tym trwajacym na telefonie (numer i znacznik), czy telefon go juz
     * zamknal. Reszte zna sam.
     */
    private fun ack(): JSONObject {
        val session = sessionRepo.active()
            ?: return JSONObject().put("ok", true).put("active", false).put("next", nextUnit())
        // Suma kontrolna serii: ile ich jest i ile maja powtorzen. Zegarek
        // liczy to samo ze swoich potwierdzonych serii i przy rozjezdzie -
        // seria dopisana albo poprawiona tutaj, na telefonie - prosi raz
        // o pelny stan. Bez tego taka zmiana nie docieralaby na nadgarstek
        // do konca treningu.
        val serie = sessionRepo.setsBySessionGrouped(session.id).values.flatten()
        return JSONObject()
            .put("ok", true)
            .put("ack", true)
            .put("active", true)
            .put("sessionId", session.id)
            .put("localId", session.localId ?: "")
            .put("sets", serie.size)
            .put("reps", serie.sumOf { it.reps })
            // Po nim zegarek wie, ze wstrzymanie albo wznowienie juz doszlo.
            .put("paused", session.isPaused)
    }

    fun handle(request: LocalServer.Request): LocalServer.Response {
        val body = runCatching { JSONObject(request.body.ifBlank { "{}" }) }
            .getOrElse { JSONObject() }
        if (request.method == "POST" && request.path == "/batch") return batch(body)
        // Tryb 2 to samo potwierdzenie; starszy zegarek zna tylko 1.
        lekki = body.optInt("lite") >= 1
        potwierdzenie = body.optInt("lite") == 2
        cichy = false
        return wykonaj(request.method, request.path, body)
    }

    /**
     * Paczka polecen z kolejki zegarka, w kolejnosci nadania.
     *
     * Po treningu bez lacznosci w kolejce stoi kilkadziesiat polecen. Po
     * jednym to kilkadziesiat rund przez Bluetooth, kazda z pelnym stanem
     * w odpowiedzi - przez minute zegarek mielil odpowiedzi zamiast reagowac
     * na palec. Paczka to jedna runda i jeden stan na koniec.
     *
     * Kazde polecenie przechodzi te sama droge co pojedyncze, z tym samym
     * pilnowaniem powtorek. Wywrotka przerywa paczke: reszta wroci w nastepnej,
     * a kolejnosc zostaje zachowana.
     */
    private fun batch(body: JSONObject): LocalServer.Response {
        val items = body.optJSONArray("items")
            ?: return LocalServer.Response.error(400, "brak polecen")
        lekki = true
        cichy = true
        potwierdzenie = false
        val results = JSONArray()
        try {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: break
                val path = PATHS[item.optString("m")]
                if (path == null) {
                    results.put(JSONObject().put("error", "nieznane zadanie"))
                    continue
                }
                val p = item.optJSONObject("p") ?: JSONObject()
                val r = runCatching { wykonaj("POST", path, p) }.getOrNull() ?: break
                if (r.status >= 500) break
                val json = runCatching { JSONObject(r.json) }.getOrElse { JSONObject() }
                if (r.status == 200) {
                    val wynik = JSONObject().put("ok", true)
                    if (json.optLong("setId") > 0) wynik.put("setId", json.optLong("setId"))
                    results.put(wynik)
                } else {
                    results.put(JSONObject().put("error", json.optString("error", "odmowa")))
                }
            }
        } finally {
            cichy = false
        }
        lekki = body.optInt("lite") >= 1
        val koniec = if (body.optInt("lite") == 2) ack() else state(lekki)
        return LocalServer.Response.ok(
            JSONObject().put("results", results).put("state", koniec).toString()
        )
    }

    private fun wykonaj(method: String, path: String, body: JSONObject): LocalServer.Response {
        // Zegarek numeruje polecenia zmieniajace zapis i ponawia je, dopoki
        // nie dostanie odpowiedzi. Odpowiedz potrafi zginac juz po wykonaniu -
        // bez tego sprawdzenia ponowienie dopisaloby te sama serie drugi raz,
        // a historii treningu nie ma skad odtworzyc.
        val cid = body.optString("cid").takeIf { it.isNotBlank() }
        if (cid != null && method == "POST") {
            seen[cid]?.let { seria ->
                val st = stan()
                if (seria > 0) st.put("setId", seria)
                return LocalServer.Response.ok(st.toString())
            }
        }
        nowaSeria = 0
        val response = route(method, path, body)
        // Znacznik zapamietujemy dopiero po udanym wykonaniu. Wczesniej
        // trafial do pamieci przed nim, wiec odrzucony START ponowiony przez
        // zegarek dostawal "w porzadku" ze stanem CUDZEGO treningu - i zegarek
        // przyjmowal tamten trening za swoj.
        if (cid != null && method == "POST" && response.status == 200) {
            seen[cid] = nowaSeria
            while (seen.size > SEEN_MAX) {
                val it = seen.keys.iterator()
                it.next()
                it.remove()
            }
        }
        return response
    }

    private fun route(method: String, path: String, body: JSONObject): LocalServer.Response {
        return when (method to path) {
            "GET" to "/state" -> LocalServer.Response.ok(state().toString())
            "GET" to "/units" -> LocalServer.Response.ok(units().toString())

            "POST" to "/start" -> {
                // Trening mogl zaczac sie na zegarku, bez lacznosci. Wtedy
                // przychodzi ze swoim znacznikiem i wlasna godzina startu,
                // a tutaj tylko go dopisujemy. Ponowione zgloszenie trafia
                // na te sama sesje i nie zaklada drugiej.
                val localId = body.optString("localId").takeIf { it.isNotBlank() }
                val startedAt = body.optLong("startedAt")
                    .takeIf { it > 0 } ?: System.currentTimeMillis()
                val juzJest = localId?.let { sessionRepo.byLocalId(it) }
                // Trening sprzed wielu godzin to zaleglosc, nie nowy trening.
                // Dopisujemy go od razu zamknietego, obok tego, co ewentualnie
                // trwa teraz: odmowa znaczyla, ze kolejne polecenia z tamtej
                // kolejki dopisywalyby sie do dzisiejszego treningu, a przy
                // pustym telefonie - ze trening przepadal w calosci.
                val zaleglosc = localId != null &&
                    System.currentTimeMillis() - startedAt >= StaleSession.STALE_AFTER_MS
                val trwa = sessionRepo.active()
                // Trening, ktory trwa tu od pozniej niz ten zgloszony, jest
                // nowszy - zgloszenie to zaleglosc, ktora dojechala dopiero teraz.
                val starszy = localId != null && trwa != null && trwa.startedAt > startedAt
                when {
                    juzJest != null -> LocalServer.Response.ok(stan().toString())
                    zaleglosc || starszy -> {
                        sessionRepo.startClosed(
                            body.optLong("planId").takeIf { it > 0 }, startedAt, localId
                        )
                        changed("other")
                        LocalServer.Response.ok(
                            JSONObject().put("backfill", true).toString()
                        )
                    }
                    trwa != null && localId == null ->
                        LocalServer.Response.error(409, "trening juz trwa")
                    else -> {
                        // Zegarek zaczyna trening tylko wtedy, gdy u siebie zadnego
                        // nie ma - wiec ten, ktory tu jeszcze trwa, jest porzucony.
                        // Odmowa (409) konczyla sie tym, ze serie nowego treningu
                        // trafialy do starego: zegarek nie mial dokad ich wyslac
                        // i przyjmowal stan tamtego treningu za swoj.
                        trwa?.let { sessionRepo.closeAbandoned(it.id) }
                        sessionRepo.start(
                            body.optLong("planId").takeIf { it > 0 }, startedAt, localId
                        )
                        changed("other")
                        LocalServer.Response.ok(stan().toString())
                    }
                }
            }

            "POST" to "/pause", "POST" to "/resume" -> {
                val s = sesja(body)
                    ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
                sessionRepo.setPaused(s.id, path == "/pause")
                changed("pause")
                LocalServer.Response.ok(stan().toString())
            }

            "POST" to "/finish" -> {
                val s = sesja(body)
                    ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
                // Propozycje liczymy przed zamknieciem, bo potem sesja nie
                // jest juz biezaca i nie ma z czego ich wyliczyc.
                //
                // Blad w nich nie moze zatrzymac konca treningu: polecenie
                // wracalo z awaria, zegarek ponawial je w kolko, a trening na
                // telefonie trwal dalej, choc na nadgarstku byl juz zamkniety.
                runCatching {
                    ProgressionReview.saveFor(planRepo, sessionRepo, profileRepo, gymRepo, s)
                }.onFailure { pl.gymbro.CrashLog.note(app, "propozycje po treningu z zegarka", it) }
                // Trening konczy sie wtedy, gdy nacisnales koniec na zegarku,
                // a nie wtedy, gdy telefon o tym uslyszal - przy synchronizacji
                // po fakcie to dwie rozne godziny.
                val endedAt = body.optLong("endedAt").takeIf { it > 0 }
                if (endedAt != null) sessionRepo.finish(s.id, endedAt)
                else sessionRepo.finish(s.id)
                changed("other")
                // Od razu z odpowiedzia leci to, co wypada nastepnym razem -
                // inaczej po zakonczeniu zegarek musialby dopytywac osobno,
                // a to jest moment, w ktorym chcesz to wiedziec.
                LocalServer.Response.ok(
                    JSONObject().put("active", false).put("next", nextUnit()).toString()
                )
            }

            "POST" to "/set" -> addSet(body)
            "POST" to "/undo" -> undo(body)
            "POST" to "/rir" -> setRir(body)
            "POST" to "/delete" -> deleteSet(body)
            "POST" to "/edit" -> editSet(body)
            "POST" to "/weight" -> nextWeight(body)

            else -> LocalServer.Response.error(404, "nieznana sciezka")
        }
    }

    // ---- Zapis ------------------------------------------------------------

    private fun addSet(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val exerciseId = body.optLong("exerciseId")
        if (exerciseId <= 0) return LocalServer.Response.error(400, "brak exerciseId")

        val states = states(session) ?: return LocalServer.Response.error(404, "plan pusty")
        val st = states.firstOrNull { it.item.exerciseId == exerciseId }
            ?: return LocalServer.Response.error(404, "cwiczenia nie ma w tej jednostce")

        // Ciężar z zegarka przykładamy do tego, co faktycznie da się złożyć
        // z talerzy. Zegarek nie zna zawartości siłowni, więc wartość przychodzi
        // stamtąd luźna i to telefon ma ostatnie słowo.
        val requested = body.optDouble("weight", st.nextWeight)
        val weight = snap(exerciseId, requested, states)
        val reps = body.optInt("reps", st.nextReps).coerceAtLeast(1)

        // Zapas moze przyjechac razem z seria: zegarek dokleja go do serii,
        // ktora jeszcze czeka w kolejce, zamiast slac osobne polecenie.
        val rir = if (body.has("rir")) body.optInt("rir", -1).takeIf { it >= 0 }?.coerceAtMost(10)
        else null

        val id = sessionRepo.addSet(
            SetEntry(
                sessionId = session.id,
                exerciseId = exerciseId,
                setIndex = st.doneSets.size,
                weightKg = weight,
                reps = reps,
                rir = rir,
                // Seria zapisana bez lacznosci dojedzie pozniej, czasem
                // duzo pozniej - liczy sie chwila, w ktorej ja zrobiles.
                doneAt = body.optLong("doneAt").takeIf { it > 0 }
                    ?: System.currentTimeMillis()
            )
        )
        nowaSeria = id
        changed("set")
        // Numer nowej serii wraca wprost. Zegarek zgadywal go dotad z "ostatniej
        // serii cwiczenia" w stanie, a przy kilku seriach w kolejce to zgadywanie
        // trafialo w cudza.
        return LocalServer.Response.ok(stan().put("setId", id).toString())
    }

    /**
     * Zmiana zapisanej serii: ciężar, powtórzenia albo jedno i drugie.
     *
     * Ciężar przechodzi przez to samo przycięcie do osiągalnych wartości,
     * co przy zapisie - poprawka nie może wprowadzić do dziennika obciążenia,
     * którego nie da się złożyć.
     */
    private fun editSet(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val setId = setIdOf(body)
        if (setId <= 0) return LocalServer.Response.error(404, "nie ma takiej serii")

        val states = states(session) ?: return LocalServer.Response.error(404, "plan pusty")
        val st = states.firstOrNull { s -> s.doneSets.any { it.id == setId } }
            ?: return LocalServer.Response.error(404, "nie ma takiej serii w tym treningu")
        val set = st.doneSets.first { it.id == setId }

        val reps = body.optInt("reps", set.reps).coerceAtLeast(1)
        val requested = body.optDouble("weight", set.weightKg)
        val weight = snap(st.item.exerciseId, requested, states)
        // Zapas powtorzen przychodzi osobno, juz po zapisaniu serii - na
        // zegarku wybiera sie go w przerwie albo poprawia na ekranie serii.
        //
        // Ujemna wartosc znaczy "nie podano" i musi czyscic pole, a nie
        // ladowac jako zero. Zero to konkretny pomiar - seria do upadku -
        // i decyduje o progresji inaczej niz brak danych.
        val rir = when {
            !body.has("rir") -> set.rir
            body.optInt("rir") < 0 -> null
            else -> body.optInt("rir").coerceIn(0, 10)
        }

        sessionRepo.updateSet(setId, weight, reps, rir)
        changed("set")
        return LocalServer.Response.ok(stan().toString())
    }

    /**
     * Zapas powtorzen dla wskazanej serii.
     *
     * Zegarek pyta o zapas zaraz po serii, czesto zanim pozna jej numer -
     * wskazuje ja wtedy znacznikiem polecenia, ktore ja zalozylo. Dopiero
     * gdy i tego nie ma (starszy zegarek), zostaje ostatnia seria cwiczenia.
     */
    private fun setRir(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val exerciseId = body.optLong("exerciseId")
        val serie = sessionRepo.setsForExerciseInSession(session.id, exerciseId)
        val wskazana = setIdOf(body).takeIf { it > 0 }?.let { id ->
            sessionRepo.setsBySessionGrouped(session.id).values.flatten().firstOrNull { it.id == id }
        }
        val last = wskazana ?: serie.lastOrNull()
            ?: return LocalServer.Response.error(404, "nie ma takiej serii")
        val rir = body.optInt("rir", -1).takeIf { it >= 0 }?.coerceAtMost(10)
        sessionRepo.updateSet(last.id, last.weightKg, last.reps, rir)
        changed("set")
        return LocalServer.Response.ok(stan().toString())
    }

    /** Skasowanie jednej, wskazanej serii - z ekranu poprawki na zegarku. */
    private fun deleteSet(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val setId = setIdOf(body)
        val istnieje = sessionRepo.setsBySessionGrouped(session.id).values
            .flatten().any { it.id == setId }
        if (!istnieje) return LocalServer.Response.error(404, "nie ma takiej serii")
        sessionRepo.deleteSet(setId)
        changed("set")
        return LocalServer.Response.ok(stan().toString())
    }

    private fun undo(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val exerciseId = body.optLong("exerciseId")
        val last = sessionRepo.setsForExerciseInSession(session.id, exerciseId).lastOrNull()
            ?: return LocalServer.Response.error(404, "nie ma czego cofnac")
        sessionRepo.deleteSet(last.id)
        changed("set")
        return LocalServer.Response.ok(stan().toString())
    }

    /**
     * Następny możliwy ciężar w górę lub w dół.
     *
     * Robi to telefon, bo tylko on zna gryf i talerze. Zegarek przy każdym
     * naciśnięciu pyta o kolejną wartość - to jedno żądanie po pętli zwrotnej,
     * czyli ułamek milisekundy, a w zamian plusy i minusy na zegarku chodzą
     * dokładnie po tych samych progach co na telefonie.
     */
    private fun nextWeight(body: JSONObject): LocalServer.Response {
        val session = sesja(body)
            ?: return LocalServer.Response.error(404, "brak trwajacego treningu")
        val exerciseId = body.optLong("exerciseId")
        val states = states(session) ?: return LocalServer.Response.error(404, "plan pusty")
        val st = states.firstOrNull { it.item.exerciseId == exerciseId }
            ?: return LocalServer.Response.error(404, "cwiczenia nie ma w tej jednostce")

        val from = body.optDouble("from", st.nextWeight)
        val up = body.optInt("dir", 1) >= 0
        val mode = LoadMode.of(st.item.equipment, gymRepo.inventory())
        val next = if (mode != null) {
            if (up) PlateCalculator.nextUp(from, mode, gymRepo.inventory())?.totalKg
            else PlateCalculator.nextDown(from, mode, gymRepo.inventory())?.totalKg
        } else {
            StepLadder.next(from, st.item.stepKg, up)
        }
        return LocalServer.Response.ok("""{"weight":${round(next ?: from)}}""")
    }

    private fun snap(
        exerciseId: Long,
        requested: Double,
        states: List<LiveSession.ExerciseState>
    ): Double {
        val st = states.firstOrNull { it.item.exerciseId == exerciseId } ?: return requested
        if (!st.item.hasTargetWeight) return 0.0
        val mode = LoadMode.of(st.item.equipment, gymRepo.inventory()) ?: return requested
        return PlateCalculator.nearest(requested, mode, gymRepo.inventory())?.totalKg ?: requested
    }

    // ---- Odczyt -----------------------------------------------------------

    private fun states(session: pl.gymbro.data.Session): List<LiveSession.ExerciseState>? {
        val list = LiveSession.load(planRepo, sessionRepo, session, gymRepo.inventory())
        return list.ifEmpty { null }
    }

    /**
     * Cwiczenia jednostki w postaci, ktorej potrzebuje zegarek.
     *
     * Wspolne dla trwajacego treningu i dla podgladu jednostki jeszcze
     * nierozpoczetej. Zegarek musi miec to wszystko u siebie, zeby dalo
     * sie zaczac trening bez telefonu - lacznie z drabinka ciezarow,
     * ktorej sam nie policzy, bo nie zna zawartosci silowni.
     */
    private fun exercisesJson(
        states: List<LiveSession.ExerciseState>,
        lite: Boolean = false
    ): JSONArray {
        val inventory = gymRepo.inventory()
        val exercises = JSONArray()
        states.forEach { st ->
            if (lite) {
                // Bez drabinki i reszty rzeczy stalych w trakcie treningu -
                // zegarek dokleja je sobie z poprzedniej odpowiedzi.
                exercises.put(
                    JSONObject()
                        .put("id", st.item.exerciseId)
                        .put("name", st.item.exerciseName)
                        .put("done", st.doneSets.size)
                        .put("sets", st.item.sets)
                        .put("weight", round(st.nextWeight))
                        .put("reps", st.nextReps)
                        .put("entries", entries(st))
                        .put("restSec", st.item.restSec)
                        .put("hasWeight", st.item.hasTargetWeight)
                        .put("unit", st.item.equipment.weightUnitLabel)
                )
                return@forEach
            }
            // Krok ciężaru wyliczony tutaj, bo tylko telefon zna gryf i talerze.
            // Zegarek dodaje go sobie lokalnie, zamiast pytać przy każdym
            // naciśnięciu plusa - tamta droga przez Bluetooth była widocznie
            // wolna. Przy zapisie i tak przycinamy do wartości osiągalnej,
            // więc drobne rozjechanie się po kilku krokach nic nie psuje.
            val mode = LoadMode.of(st.item.equipment, inventory)
            val step = if (st.item.hasTargetWeight && mode != null) {
                val up = PlateCalculator.nextUp(st.nextWeight, mode, inventory)?.totalKg
                if (up != null && up > st.nextWeight) up - st.nextWeight else st.item.step
            } else st.item.step
            val ladder = ladder(st, mode, inventory)

            exercises.put(
                JSONObject()
                    .put("id", st.item.exerciseId)
                    .put("name", st.item.exerciseName)
                    .put("done", st.doneSets.size)
                    .put("sets", st.item.sets)
                    .put("weight", round(st.nextWeight))
                    .put("reps", st.nextReps)
                    // Zakres z planu, osobno od propozycji. Propozycja mowi,
                    // ile zrobiles ostatnio; zakres mowi, ile masz zrobic -
                    // i to drugie znika z glowy miedzy seriami.
                    .put("repsFrom", st.item.repsFrom)
                    .put("repsTo", st.item.repsTo)
                    .put("step", round(step))
                    .put("ladder", ladder.values)
                    .put("entries", entries(st))
                    .put("atMin", ladder.atMin)
                    .put("atMax", ladder.atMax)
                    .put("restSec", st.item.restSec)
                    .put("hasWeight", st.item.hasTargetWeight)
                    .put("unit", st.item.equipment.weightUnitLabel)
            )
        }
        return exercises
    }

    private fun state(lite: Boolean = false): JSONObject {
        // Nastepna jednostka takze bez treningu. Koniec treningu przychodzi
        // czesto w paczce z zaleglymi seriami, a paczka konczy sie wlasnie tym
        // stanem - bez tego pola zegarek dowiadywal sie o nastepnej jednostce
        // dopiero przy kolejnym pytaniu o jednostki, minute pozniej.
        val session = sessionRepo.active()
            ?: return JSONObject().put("active", false).put("next", nextUnit())
        val states = states(session) ?: return JSONObject()
            .put("active", true)
            .put("plan", "")
            .put("exercises", JSONArray())

        val plan = session.planId?.let { id -> planRepo.plans().firstOrNull { it.id == id } }
        val exercises = exercisesJson(states, lite)

        // Liczby na podsumowanie. Zegarek ma pokazać to samo co telefon po
        // ostatniej serii, a sam ich nie policzy - zna tylko liczbę serii,
        // nie powtórzenia i ciężary z całej sesji.
        val done = states.flatMap { it.doneSets }.filter { !it.isWarmup }
        val totals = JSONObject()
            .put("sets", done.size)
            .put("reps", done.sumOf { it.reps })
            .put("tonnage", round(done.sumOf { it.weightKg * it.reps }))
            .put("complete", states.isNotEmpty() && states.all { it.complete })

        val out = JSONObject()
            .put("active", true)
            // Zegarek odrozni po tym powrot do tego samego treningu od nowego.
            // Jest ubijany przy zgaszeniu ekranu, wiec musi umiec sprawdzic,
            // czy stan, ktory ma zapisany u siebie, dotyczy jeszcze tej sesji.
            .put("sessionId", session.id)
            // Znacznik zegarka, jesli trening zaczal sie tam. Po nim zegarek
            // poznaje, czy stan dotyczy jego treningu, czy innego, ktory akurat
            // trwa na telefonie - sam numer tego nie mowi, bo zegarek go nie zna,
            // dopoki START nie dojedzie.
            .put("localId", session.localId ?: "")
            .put("plan", plan?.name ?: "Trening")
            // Numer jednostki - zegarek zaznacza ja u siebie jako zrobiona
            // w chwili zakonczenia, bez czekania na telefon.
            .put("planId", session.planId ?: 0)
            .put("next", nextUnit())
            .put("elapsedMin", session.elapsedMs() / 60_000)
            // Sekundy, bo zegarek dolicza sobie resztę lokalnie: odpowiedzi
            // przychodzą tylko przy akcjach, a zegar treningu ma tykać też
            // wtedy, gdy między seriami nic nie leci przez Bluetooth.
            .put("elapsedSec", session.elapsedMs() / 1_000)
            .put("paused", session.isPaused)
            .put("current", LiveSession.firstUnfinished(states))
            .put("totals", totals)
            .put("exercises", exercises)
        if (!lite) out.put("hrZones", hrZones()).put("burn", burn())
        return out
    }

    /**
     * Progi tętna, policzone tu, bo tu jest rocznik.
     *
     * Wzór Tanaki (208 - 0.7 * wiek) zamiast starego 220 - wiek: ten drugi
     * zaniża próg u dorosłych i przy czterdziestce myli się o kilka uderzeń.
     * Bez rocznika nie zgadujemy - zegarek dostanie pustkę i pokaże tętno
     * bez kolorów, zamiast malować strefy wyssane z palca.
     */
    private fun hrZones(): JSONObject {
        val age = profileRepo.get().age ?: return JSONObject()
        if (age < 10 || age > 100) return JSONObject()
        val max = 208.0 - 0.7 * age
        return JSONObject()
            .put("max", Math.round(max))
            .put("easy", Math.round(max * 0.60))
            .put("aerobic", Math.round(max * 0.70))
            .put("tempo", Math.round(max * 0.80))
            .put("anaerobic", Math.round(max * 0.90))
    }

    /**
     * Spalanie jako funkcja tętna: kcal/min = k0 + k1 * HR.
     *
     * Czujnik kalorii na zegarku podaje dzienny licznik aktywności, który
     * napędzają głównie kroki. Przy sztandze kroków nie ma, więc po godzinie
     * ciężkiej pracy pokazywał kilkanaście kalorii, podczas gdy natywny
     * trening siłowy na tym samym zegarku naliczał kilkaset. Z tętna wychodzi
     * wartość zgodna z tamtą - i to jest ta sama droga, którą liczy zegarek
     * w trybie treningu.
     *
     * Wzór Keytela: osobny dla każdej płci, przeliczony z kilodżuli na
     * kilokalorie. Rozbijamy go tutaj na dwie stałe, żeby zegarek nie musiał
     * znać ani masy, ani wieku - mnoży tylko tętno.
     *
     * Bez masy albo wieku nie zgadujemy: zegarek dostanie pustkę i wróci do
     * czujnika, który bywa zaniżony, ale nie jest zmyślony.
     */
    private fun burn(): JSONObject {
        val profile = profileRepo.get()
        val age = profile.age ?: return JSONObject()
        val mass = bodyRepo.latest()?.weightKg ?: return JSONObject()
        if (age < 10 || age > 100 || mass < 30 || mass > 300) return JSONObject()

        val k0: Double
        val k1: Double
        if (profile.sex == Sex.FEMALE) {
            k0 = (-20.4022 - 0.1263 * mass + 0.074 * age) / KJ_PER_KCAL
            k1 = 0.4472 / KJ_PER_KCAL
        } else {
            k0 = (-55.0969 + 0.1988 * mass + 0.2017 * age) / KJ_PER_KCAL
            k1 = 0.6309 / KJ_PER_KCAL
        }
        // Wzor zaklada ciagly wysilek tlenowy, a trening silowy to praca
        // z przerwami, w ktorych tetno jeszcze nie opadlo. Stad stale zawyzal.
        // Wspolczynnik wyliczony z 21 treningow zapisanych przez sam zegarek:
        // stosunek jego wyniku do wzoru wychodzil 0.770 przy rozrzucie 0.04.
        return JSONObject()
            .put("k0", round(k0 * STRENGTH_FACTOR))
            .put("k1", Math.round(k1 * STRENGTH_FACTOR * 10000) / 10000.0)
    }

    /**
     * Która jednostka wypada teraz.
     *
     * Przy rotacji A/B/C wystarcza najdawniej robiona - ta sama reguła,
     * którą ekran treningu układa listę na telefonie. Nigdy nierobiona
     * idzie przed wszystkimi, bo plan zaczyna się od początku.
     */
    private fun nextPlanId(): Long? {
        val program = programRepo.active() ?: return null
        val plans = planRepo.plansOf(program.id)
        if (plans.isEmpty()) return null
        val last = sessionRepo.lastFinishedAll()
        return plans.minByOrNull { last[it.id] ?: Long.MIN_VALUE }?.id
    }

    private fun nextUnit(): JSONObject {
        val id = nextPlanId() ?: return JSONObject()
        val plan = planRepo.plans().firstOrNull { it.id == id } ?: return JSONObject()
        return JSONObject().put("id", plan.id).put("name", plan.name)
    }

    /** Jednostki aktywnego programu - zegarek musi wiedzieć, co może zacząć. */
    private fun units(): JSONObject {
        val program = programRepo.active()
        val plans = program?.let { planRepo.plansOf(it.id) }.orEmpty()
        val counts = sessionRepo.itemCounts()
        val last = sessionRepo.lastFinishedAll()
        val next = nextPlanId()
        val array = JSONArray()
        val teraz = System.currentTimeMillis()
        val inventory = gymRepo.inventory()
        plans.forEach {
            // Podglad jednostki tak, jakby trening wlasnie sie zaczal: zero
            // zrobionych serii, ciezary i drabinki jak na starcie. Zegarek
            // trzyma to u siebie i dzieki temu zaczyna trening bez telefonu.
            val podglad = pl.gymbro.data.Session(id = 0, planId = it.id, startedAt = teraz)
            val states = LiveSession.load(planRepo, sessionRepo, podglad, inventory)
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("exercises", counts[it.id] ?: 0)
                    .put("next", it.id == next)
                    // Kiedy jednostka byla ostatnio robiona - z tego zegarek
                    // sam wyznacza nastepna po zakonczeniu treningu, ta sama
                    // regula co [nextPlanId], takze bez lacznosci.
                    .put("lastDone", last[it.id] ?: 0L)
                    .put("plan", exercisesJson(states))
            )
        }
        return JSONObject()
            .put("program", program?.name ?: "")
            // Wspolczynniki spalania i progi tetna jada takze tutaj, nie tylko
            // ze stanem trwajacego treningu. Zegarek pyta o jednostki za kazdym
            // razem na ekranie wyboru, wiec to jedyna odpowiedz, ktora dostaje
            // PRZED pierwsza seria - a trening zaczety bez lacznosci nie dostanie
            // potem zadnej. Bez nich kalorie staly na kresce caly trening.
            .put("hrZones", hrZones())
            .put("burn", burn())
            .put("units", array)
    }

    /**
     * Ciężary osiągalne w okolicy propozycji, od najmniejszego.
     *
     * Zegarek dodawał sobie wcześniej stały krok policzony raz, od bieżącej
     * wartości. Przy hantlach ze stosu kolejne progi nie są równo oddalone,
     * więc po kilku naciśnięciach lądowało się pomiędzy nimi - i nie dało
     * się ustawić okrągłych dwudziestu kilogramów, mimo że taki hantel stoi
     * na stojaku. Z gotową listą zegarek chodzi po progach, które istnieją.
     *
     * Krótka, bo to tylko najbliższe otoczenie: dalej sięga się rzadko,
     * a cała siłownia w każdej odpowiedzi byłaby marnotrawstwem.
     */
    /**
     * Zapisane serie ćwiczenia, z numerami do poprawiania.
     *
     * Bez identyfikatorów zegarek umiałby tylko dopisywać i cofać ostatnią.
     * Pomyłka w powtórzeniach trzeciej serii oznaczała wtedy cofanie trzech
     * i wklepywanie od nowa, zwykle już po treningu i z pamięci.
     */
    private fun entries(st: LiveSession.ExerciseState): JSONArray {
        val out = JSONArray()
        st.doneSets.forEach { set ->
            out.put(
                JSONObject()
                    .put("id", set.id)
                    .put("weight", round(set.weightKg))
                    .put("reps", set.reps)
                    .put("rir", set.rir ?: -1)
                    .put("warmup", set.isWarmup)
            )
        }
        return out
    }

    /**
     * Drabinka osiągalnych ciężarów wokół propozycji, wraz z informacją,
     * czy jej końce są prawdziwymi granicami sprzętu.
     *
     * Te dwa bity są tu najważniejsze. Bez nich zegarek nie umiał odróżnić
     * "dalej nie ma czego założyć" od "skończyła mi się lista" - i zatrzymywał
     * się w przypadkowym miejscu, zwykle kilkanaście kilogramów pod ciężarem
     * roboczym. Serie rozgrzewkowe od pustego gryfu były przez to nie do
     * ustawienia.
     *
     * Lista jest krótka, bo leci w każdej odpowiedzi; dalekie zejście zdarza
     * się rzadko i wtedy zegarek dochodzi tam krokiem z brzegu, a przy zapisie
     * telefon i tak przycina wynik do tego, co da się złożyć.
     *
     * Lista zawiera wszystkie osiągalne progi. Przycinanie jej do wygodnych
     * należy do propozycji progresji, nie do wybierania ciężaru.
     */
    private fun ladder(
        st: LiveSession.ExerciseState,
        mode: LoadMode?,
        inventory: pl.gymbro.data.Inventory
    ): Rungs {
        val out = JSONArray()
        if (!st.item.hasTargetWeight) return Rungs(out, atMin = true, atMax = true)

        val from = st.nextWeight
        if (mode == null) {
            // Sprzęt nieładowany talerzami - równe progi są tu prawdą, a ich
            // odstęp bierze się z ćwiczenia, bo stos wyciągu zna tylko ten,
            // kto przy nim stał. Dołu pilnuje zero, góry nie zna nikt,
            // więc oba końce są miękkie.
            StepLadder.rungs(from, st.item.stepKg, LADDER_SPAN).forEach { out.put(it) }
            return Rungs(out, atMin = false, atMax = false)
        }

        // Wszystkie progi, jakie da sie zlozyc z posiadanych talerzy.
        //
        // Celowo wszystkie, nie tylko te wygodne: to jest lista do ustawiania
        // ciezaru i musi dac sie na niej ustawic wszystko, co da sie nalozyc.
        // Ograniczona do wygodnych nie pozwalala zejsc z 81,4 na rowne 80,
        // bo z tamtego kompletu talerzy to az cztery ruchy. Wygodne progi
        // sluza do PROPONOWANIA progresji, nie do wybierania ciezaru.
        val wszystkie = PlateCalculator.achievable(mode, inventory)
        if (wszystkie.isEmpty()) return Rungs(out, atMin = true, atMax = true)

        val srodek = wszystkie.indexOfFirst { it.totalKg >= from - 0.01 }
            .let { if (it < 0) wszystkie.lastIndex else it }
        val okno = wszystkie.subList(
            (srodek - LADDER_SPAN).coerceAtLeast(0),
            (srodek + LADDER_SPAN + 1).coerceAtMost(wszystkie.size)
        )

        // Konce drabinki sa twarde tylko wtedy, gdy nie ma juz czego zalozyc
        // ani czego zdjac. Jesli dalsze progi istnieja, tylko nie zmiescily
        // sie w okienku, koniec jest miekki i zegarek dochodzi tam krokiem.
        val atMax = wszystkie.none { it.totalKg > okno.last().totalKg + 0.01 }
        val atMin = wszystkie.none { it.totalKg < okno.first().totalKg - 0.01 }

        okno.forEach { out.put(round(it.totalKg)) }
        return Rungs(out, atMin = atMin, atMax = atMax)
    }

    private data class Rungs(val values: JSONArray, val atMin: Boolean, val atMax: Boolean)

    /**
     * Obcięcie ogonów z arytmetyki zmiennoprzecinkowej.
     *
     * Suma talerzy wychodzi jako 81.39999999999999 i zegarek rysował to
     * dosłownie, bo JSON przenosi dokładnie tę liczbę, a formatowanie po
     * drugiej stronie nic o niej nie wie. Przycinamy tutaj, żeby każde
     * wejście dostało tę samą, krótką wartość.
     */
    private fun round(value: Double): Double = roundKg(value)

    private fun changed(reason: String) = onChanged?.invoke(reason)

    private companion object {
        /** Polecenia z kolejki zegarka i ich sciezki - dla paczki. */
        val PATHS = mapOf(
            "SET" to "/set", "RIR" to "/rir", "DELETE" to "/delete",
            "UNDO" to "/undo", "EDIT" to "/edit", "START" to "/start",
            "PAUSE" to "/pause", "RESUME" to "/resume", "FINISH" to "/finish"
        )

        /** Ile ostatnich znacznikow polecen pamietamy. */
        const val SEEN_MAX = 200

        /** Kilodżule na kilokalorię - wzór Keytela liczy w kJ. */
        const val KJ_PER_KCAL = 4.184

        /** Dopasowanie wzoru do treningu silowego - patrz [burn]. */
        const val STRENGTH_FACTOR = 0.77

        /** Ile progów w każdą stronę mieści się w drabince ciężarów. */
        const val LADDER_SPAN = 20
    }
}
