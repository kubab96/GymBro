package pl.gymbro.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.gymbro.BuildConfig
import pl.gymbro.data.BiaValue
import pl.gymbro.data.roundKg
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private const val LOG_TAG = "GymBroBLE"

/**
 * Czy prowadzic log diagnostyczny - tylko w wariancie debug.
 *
 * Log zapisuje adresy i dane rozgloszeniowe wszystkich urzadzen w poblizu,
 * nie tylko wagi. Na wlasnym telefonie przy rozgryzaniu protokolu to jest
 * dokladnie to, o co chodzi; w wydaniu to spis cudzych sluchawek i zegarkow
 * w logcacie, a do tego zgadywanie masy z kazdej nieznanej ramki, ktorego
 * wyniku nikt nie czyta.
 */
private val DIAG: Boolean = BuildConfig.DEBUG

private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/**
 * Usluga informacyjna GATT - tu urzadzenie podaje, czym jest.
 *
 * Numeru seryjnego celowo nie czytamy: nie mowi nic o modelu, a jest
 * identyfikatorem konkretnego egzemplarza.
 */
private val DEVICE_INFO_NAMES: Map<String, String> = mapOf(
    "2A29" to "Producent",
    "2A24" to "Model",
    "2A26" to "Firmware",
    "2A27" to "Wersja sprzetowa",
    "2A28" to "Oprogramowanie",
    "2A23" to "System ID"
)

/**
 * Wspolny typ odczytu, zeby interfejs nie musial znac rodziny wagi.
 *
 * Pole [at] nie jest ozdobnikiem, tylko warunkiem dzialania. ScaleReading jest
 * data class, a StateFlow pomija przypisanie wartosci ROWNEJ obecnej. Bez znacznika
 * czasu dwa identyczne wazenia (ta sama masa, ta sama impedancja) bylyby tym samym
 * obiektem i drugie nie wywolalo by zadnej reakcji ekranu. Decyzje "czy to nowy
 * pomiar" podejmuje repozytorium, a nie mechanizm powiadamiania.
 */
data class ScaleReading(
    val weightKg: Double,
    val detail: String?,
    val stable: Boolean,
    val source: String,
    /** Surowe opory, jesli waga je podaje. Ida do bazy bez przeliczania. */
    val bia: List<BiaValue> = emptyList(),
    /** Moment odczytu - czyni kazda emisje unikalna. */
    val at: Long = System.currentTimeMillis(),
    /**
     * Falsz, gdy waga zamilkla, zanim potwierdzila wynik, i proponujemy
     * ostatnia liczbe, jaka dotarla. Ekran prosi wtedy o porownanie
     * z wyswietlaczem zamiast udawac pewnosc.
     */
    val certain: Boolean = true
)

/** Liczba, ktora waga pokazuje w tej chwili - jeszcze nie wynik. */
data class LiveWeight(val weightKg: Double, val at: Long)

data class FoundDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val services: List<UUID>,
    val family: String?,
    val lastSeen: Long
)

/**
 * Nasluch wag BLE: skanowanie, rozpoznanie rodziny, odczyt na zywo i wynik.
 *
 * Robi to, co aplikacje producentow, w tej samej kolejnosci:
 *  1. skanuje otoczenie i rozpoznaje wage po tym, czym sie przedstawia,
 *  2. jesli waga nadaje pomiar w rozgloszeniu, czyta go bez laczenia,
 *  3. jesli wymaga polaczenia - laczy sie sama i subskrybuje pomiary,
 *  4. pokazuje liczbe od pierwszej ramki, a wynik oddaje, gdy sie ustali
 *     ([WeightSettler]).
 *
 * Obok tego prowadzi log diagnostyczny ze wszystkim, co slychac - bez niego
 * nieznanej wagi nie da sie rozgryzc, a "nie dziala" nie da sie zmierzyc.
 */
@SuppressLint("MissingPermission")
class ScaleScanner(private val context: Context) {

    companion object {
        /** Uprawnienia, bez ktorych skan konczy sie wyjatkiem albo cisza. */
        fun permissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        /** Po tylu milisekundach odrzucony odczyt moze wrocic jako nowy. */
        private const val REJECTED_HOLD_MS = 10_000L

        /** Nie dobijamy sie do tej samej wagi czesciej. */
        private const val RECONNECT_GAP_MS = 15_000L

        private const val TICK_MS = 500L
    }

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private val main = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<Map<String, FoundDevice>>(emptyMap())
    val devices: StateFlow<Map<String, FoundDevice>> = _devices.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _connected = MutableStateFlow<String?>(null)
    val connected: StateFlow<String?> = _connected.asStateFlow()

    /** Czy trwa nasluch w sensie ekranu - takze wtedy, gdy skan ustapil polaczeniu. */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Wynik pomiaru: ustalony przez wage albo przez [WeightSettler]. */
    private val _lastStable = MutableStateFlow<ScaleReading?>(null)
    val lastStable: StateFlow<ScaleReading?> = _lastStable.asStateFlow()

    /**
     * Biezaca liczba z wagi. Ekran pokazuje ja od pierwszej ramki: czekanie
     * z pustym ekranem na wynik koncowy nie odroznialo "waga liczy" od
     * "nic nie slychac".
     */
    private val _live = MutableStateFlow<LiveWeight?>(null)
    val live: StateFlow<LiveWeight?> = _live.asStateFlow()

    /** Nazwa uslyszanej wagi (pusta, gdy sie nie przedstawia); null, dopoki zadnej nie slychac. */
    private val _scaleName = MutableStateFlow<String?>(null)
    val scaleName: StateFlow<String?> = _scaleName.asStateFlow()

    /** Ile pakietow rozgloszeniowych w ogole doszlo. Zero = skanowanie nie dziala. */
    private val _packets = MutableStateFlow(0)
    val packets: StateFlow<Int> = _packets.asStateFlow()

    private val settler = WeightSettler()

    /** Czy pod spodem naprawde chodzi skan (na czas laczenia jest wstrzymany). */
    private var scanRunning = false
    private var autoConnect = false
    private var scanStartedAt = 0L
    private var scalePackets = 0

    /**
     * Odczyt odrzucony przyciskiem "Zmierz ponownie". Waga nadaje ustalony
     * wynik jeszcze kilka sekund po zejsciu - bez tej blokady nowy nasluch
     * lapal w pierwszej chwili dokladnie te liczbe, ktora wlasnie odrzucono.
     */
    private var rejectedKg: Double? = null
    private var rejectedUntil = 0L

    /**
     * Dzielnik masy dla wag QN/Yolanda. Podaje go ramka 0x12 po polaczeniu;
     * do tego czasu zakladamy 100, bo tak ma wiekszosc tych wag.
     */
    private var qnDivisor: Int = 100
    private var qnGreeted = false

    /** Waga rozglasza kilka razy na sekunde - logujemy dopiero zmiane odczytu. */
    private var lastFrameKey: String? = null
    private var rawTraceCount = 0

    /** Ile razy z rzedu przyszla ta sama ramka pomiarowa. */
    private var repeatCount = 0

    /** Deduplikacja tresci rozgloszen, zeby log nie urosl do nieskonczonosci. */
    private val seenPayloads = mutableSetOf<String>()

    private val connectTried = HashMap<String, Long>()

    /**
     * Android nie zwraca ZADNYCH wynikow skanowania BLE przy wylaczonych uslugach
     * lokalizacji - bez bledu, bez ostrzezenia, po prostu cisza. To najczestsza
     * przyczyna "skanuje i nic nie znajduje", wiec sprawdzamy to wprost.
     */
    fun isLocationOn(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isLocationEnabled
    }

    private var gatt: BluetoothGatt? = null
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /**
     * Log diagnostyczny.
     *
     * Trafia w dwa miejsca naraz. Na ekran, bo tam go oglada czlowiek bez
     * podlaczonego komputera. I do logcata, bo odczytywanie dlugiego logu przez
     * przewijanie listy na ekranie jest zawodne - latwo przy tym przegapic linie
     * albo wyjsc z ekranu i przerwac skanowanie.
     *
     * Tylko w wariancie debug - patrz [DIAG]. Wczesniej log celowo przezywal
     * takze w release (poziom informacyjny, bo Log.d i Log.v R8 wycina),
     * zeby dalo sie diagnozowac wage na wersji do pomiarow plynnosci. Odkad
     * release ma byc wydaniem, a nie druga wersja robocza, to sie odwraca:
     * do diagnozy sluzy debug, a ekran z tym logiem i tak jest tylko tam.
     */
    private fun log(line: String) {
        if (!DIAG) return
        val stamped = timeFmt.format(System.currentTimeMillis()) + "  " + line
        synchronized(_log) { _log.value = (_log.value + stamped).takeLast(1500) }
        android.util.Log.i(LOG_TAG, line)
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun logAsText(): String = _log.value.joinToString("\n")

    // ---- Skanowanie ------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            log("BLAD skanowania, kod=" + errorCode)
            scanRunning = false
            _scanning.value = false
        }
    }

    /**
     * Surowy podsluch jednego urzadzenia, z pominieciem calego odsiewania.
     *
     * Reszta tej klasy celowo wycisza powtorzenia, zeby log dalo sie czytac.
     * Przy diagnozowaniu "dlaczego nie widac wagi" to wyciszanie jest jednak
     * samo w sobie podejrzanym - nie da sie odroznic "pakiety nie przychodza"
     * od "przychodza, ale je odrzucamy". Ta sciezka loguje wszystko, co
     * przyjdzie od rozpoznanej wagi, i tylko tak mozna to rozstrzygnac.
     */
    private fun traceRaw(result: ScanResult) {
        rawTraceCount++
        val msd = result.scanRecord?.manufacturerSpecificData
        val payload = if (msd == null || msd.size() == 0) "(brak manufacturer data)"
        else (0 until msd.size()).joinToString("  ") { i ->
            "0x%04X: %s".format(msd.keyAt(i), ScaleProtocol.hex(msd.valueAt(i) ?: ByteArray(0)))
        }
        log(
            "SUROWO #" + rawTraceCount + "  +" + (System.currentTimeMillis() - scanStartedAt) +
                " ms  RSSI=" + result.rssi + "  " + payload
        )
    }

    private fun onResult(result: ScanResult) {
        val rec = result.scanRecord
        val name = rec?.deviceName ?: result.device.name
        val services = rec?.serviceUuids?.map { it.uuid } ?: emptyList()
        val addr = result.device.address

        val manufacturer = rec?.manufacturerSpecificData?.let { msd ->
            (0 until msd.size()).mapNotNull { i -> msd.valueAt(i)?.let { msd.keyAt(i) to it } }
        } ?: emptyList()
        val serviceData = rec?.serviceData?.mapNotNull { (uuid, bytes) ->
            if (uuid == null || bytes == null) null else uuid.uuid to bytes
        } ?: emptyList()

        val read = ScaleDecoders.fromAdvert(manufacturer, serviceData)
        val connectable = result.isConnectable
        val gattScale = read == null && ScaleDecoders.wantsConnection(name, services)

        val family = read?.family
            ?: (if (YolandaParser.matchesName(name)) "Qingniu / Yolanda (QN-Scale)" else null)
            ?: ScaleProtocol.guessFamilyFromName(name)
            ?: services.firstNotNullOfOrNull { ScaleProtocol.describeService(it) }

        val alreadyKnown = _devices.value.containsKey(addr)
        _devices.value = _devices.value + (addr to FoundDevice(
            address = addr,
            name = name,
            rssi = result.rssi,
            services = services,
            family = family,
            lastSeen = System.currentTimeMillis()
        ))
        _packets.value = _packets.value + 1

        // KAZDE nowe urzadzenie trafia do logu, nie tylko rozpoznane.
        // Poprzednia wersja milczala o wszystkim, czego nie znala - przez co
        // pusty log nie odrozmial "nic nie widac" od "widac, ale nie rozpoznaje".
        if (!alreadyKnown) {
            log("URZADZENIE: " + (name ?: "(bez nazwy)") + "  " + addr + "  RSSI=" + result.rssi)
            if (family != null) log("  >>> rodzina: " + family)
            // Czy urzadzenie w ogole przyjmuje polaczenia.
            //
            // Rozstrzyga to samo rozgloszenie, wiec nie trzeba czekac na
            // nieudane laczenie i zgadywac z kodu bledu. Waga, ktora
            // rozglasza sie jako niekonektowalna, nigdy nie poda modelu ani
            // firmware'u - te dane leza za polaczeniem GATT, ktorego nie bedzie.
            log("  polaczenia: " + if (result.isConnectable) "TAK" else "NIE (samo rozgloszenie)")
            services.forEach {
                log("  serwis: " + ScaleProtocol.short(it) + "  " + (ScaleProtocol.describeService(it) ?: ""))
            }
        }

        if (read != null) {
            onScaleAdvert(result, name, read, manufacturer, serviceData)
            return
        }
        if (gattScale && connectable) {
            _scaleName.value = name?.trim().orEmpty()
            if (autoConnect) tryAutoConnect(result.device, name)
        }

        // Nierozpoznane: logujemy kazda ODMIENNA tresc raz. Waga nadaje
        // kilka razy na sekunde, ale zmienia payload przy zmianie odczytu,
        // wiec wejscie na nia samo z siebie wyprodukuje nowe wpisy.
        for ((id, bytes) in manufacturer) {
            val noise = ScaleProtocol.isNoiseCompany(id)
            val key = if (noise) addr + "|" + id else addr + "|" + id + "|" + ScaleProtocol.hex(bytes)
            if (seenPayloads.add(key)) {
                if (seenPayloads.size > 4000) seenPayloads.clear()
                log("  ADV[0x%04X] ".format(id) + (name ?: addr) + ": " + ScaleProtocol.hex(bytes))
                if (DIAG) log(ScaleProtocol.annotate(null, bytes, deep = !noise))
            }
        }
        for ((uuid, bytes) in serviceData) {
            val noise = ScaleProtocol.isNoiseService(uuid)
            val key = if (noise) addr + "|sd|" + uuid
                      else addr + "|sd|" + uuid + "|" + ScaleProtocol.hex(bytes)
            if (seenPayloads.add(key)) {
                log("  ADV serviceData[" + ScaleProtocol.short(uuid) + "] " +
                    (name ?: addr) + ": " + ScaleProtocol.hex(bytes))
                if (DIAG) log(ScaleProtocol.annotate(uuid, bytes, deep = !noise))
            }
        }
    }

    /** Rozgloszenie rozpoznanej wagi - z masa albo bez. */
    private fun onScaleAdvert(
        result: ScanResult,
        name: String?,
        read: AdvertRead,
        manufacturer: List<Pair<Int, ByteArray>>,
        serviceData: List<Pair<UUID, ByteArray>>
    ) {
        scalePackets++
        if (scalePackets == 1) {
            log("WAGA uslyszana po " + (System.currentTimeMillis() - scanStartedAt) + " ms od startu skanu")
        }
        if (DIAG) traceRaw(result)
        _scaleName.value = name?.trim().orEmpty()

        // Logujemy kazda ODMIENNA ramke, porownujac surowe bajty, a nie
        // odczytane z nich wartosci.
        //
        // Wczesniej kluczem byla masa i impedancja po zdekodowaniu - przez co
        // ramka, w ktorej zmienilo sie pole, ktorego jeszcze nie rozumiemy,
        // nigdy nie trafiala do logu.
        val hex = manufacturer.joinToString(" | ") { "0x%04X: ".format(it.first) + ScaleProtocol.hex(it.second) } +
            serviceData.joinToString(" | ") { ScaleProtocol.short(it.first) + ": " + ScaleProtocol.hex(it.second) }
        val key = result.device.address + "|" + hex
        if (key != lastFrameKey) {
            lastFrameKey = key
            repeatCount = 0
            log("  ADV: " + hex)
            if (DIAG) manufacturer.firstOrNull()?.let { log("    pola 16-bit: " + ScaleProtocol.fields16(it.second)) }
            val s = read.sample
            if (s == null) {
                log("    waga czuwa, bez masy")
            } else {
                log(
                    (if (s.stable == true) ">>> POMIAR STABILNY: " else "    pomiar w trakcie: ") +
                        "%.2f kg".format(s.weightKg) +
                        (WeightSample.describe(s.bia)?.let { "   opor: $it" } ?: "") +
                        "   [" + s.source + "]"
                )
            }
        } else {
            // Bez tego licznika brak nowych linii w logu jest dwuznaczny:
            // nie wiadomo, czy waga zamilkla, czy powtarza ten sam odczyt.
            repeatCount++
            if (repeatCount % 20 == 0) {
                log("  (ta sama ramka powtorzona " + repeatCount + " razy)")
            }
        }

        val sample = read.sample
        if (sample == null) {
            // Pusta waga po odrzuconym pomiarze znaczy, ze zaczyna od nowa -
            // nastepna liczba jest juz nowym wazeniem, chocby byla ta sama.
            rejectedKg = null
            return
        }
        onSample(sample)
    }

    /**
     * Jedno miejsce, przez ktore przechodzi kazdy odczyt - z rozgloszenia
     * i z polaczenia. Powtorzonej ramki celowo NIE odsiewamy: dla stabilizacji
     * druga taka sama liczba jest wlasnie ta informacja, na ktora czekamy.
     */
    private fun onSample(sample: WeightSample) {
        if (!_scanning.value) return
        val now = System.currentTimeMillis()

        val rejected = rejectedKg
        if (rejected != null) {
            val same = abs(sample.weightKg - rejected) < 0.005 && sample.stable != false
            if (same && now < rejectedUntil) return
            rejectedKg = null
        }

        _live.value = LiveWeight(roundKg(sample.weightKg), now)
        settler.offer(sample, now)?.let { publish(it, now) }
    }

    private fun publish(verdict: WeightSettler.Verdict, now: Long) {
        val s = verdict.sample
        log(
            "WYNIK: %.2f kg".format(s.weightKg) + "  (" + verdict.reason + ")  po " +
                (now - scanStartedAt) + " ms od startu skanu, pakietow od wagi: " + scalePackets
        )
        _lastStable.value = ScaleReading(
            weightKg = roundKg(s.weightKg),
            detail = WeightSample.describe(s.bia)?.let { "opor: $it" },
            stable = true,
            source = s.source,
            bia = s.bia,
            at = now,
            certain = verdict.certain
        )
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!_scanning.value) return
            val now = System.currentTimeMillis()
            settler.tick(now)?.let { publish(it, now) }
            main.postDelayed(this, TICK_MS)
        }
    }

    /**
     * @param rejected odczyt, ktory uzytkownik wlasnie odrzucil - przez chwile
     *   nie przyjmiemy go ponownie, patrz [rejectedKg]
     * @param autoConnect czy laczyc sie samemu z waga, ktora tego wymaga.
     *   Kreator pomiaru tak; ekran diagnostyczny nie, bo tam laczy sie
     *   recznie, z wybranym urzadzeniem.
     */
    fun startScan(rejected: ScaleReading? = null, autoConnect: Boolean = false) {
        if (!isBluetoothOn()) {
            log("!!! Bluetooth jest wylaczony - wlacz go i sprobuj ponownie.")
            return
        }
        if (!isLocationOn()) {
            log("!!! USLUGI LOKALIZACJI SA WYLACZONE.")
            log("!!! Android w tym stanie nie zwroci ani jednego wyniku skanowania BLE,")
            log("!!! i nie zglosi zadnego bledu. Wlacz lokalizacje i skanuj ponownie.")
        }
        if (_scanning.value) stopScan()

        this.autoConnect = autoConnect
        _devices.value = emptyMap()
        _packets.value = 0
        _live.value = null
        _scaleName.value = null
        seenPayloads.clear()
        connectTried.clear()
        settler.reset()
        lastFrameKey = null
        rawTraceCount = 0
        repeatCount = 0
        scalePackets = 0
        rejectedKg = rejected?.weightKg
        scanStartedAt = System.currentTimeMillis()
        rejectedUntil = scanStartedAt + REJECTED_HOLD_MS

        _scanning.value = true
        log("--- start skanowania (wejdz na wage, zeby ja obudzic) ---")
        resumeScan()
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, TICK_MS)
    }

    /**
     * Wlasciwy start skanu.
     *
     * FILTR JEST TU KONIECZNY, choc niczego nie odsiewa. Zmierzone
     * 2026-11-13 na Redmi Note 11 Pro (HyperOS, Android 13), trzy powtorzenia
     * po 10 s w tym samym pokoju:
     *
     *   bez filtra         2 urzadzenia,  20-26 wynikow
     *   z pustym filtrem  11-15 urzadzen, 69-127 wynikow
     *
     * Skan bez filtra system traktuje jak podejrzany i dlawi; waga byla wtedy
     * slyszana dwa razy na dziesiec sekund albo wcale. Pusty filtr przepuszcza
     * wszystko, wiec nadal widzimy kazda wage, takze nieznana.
     */
    private fun resumeScan() {
        if (scanRunning || !_scanning.value) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        val filters = listOf(ScanFilter.Builder().build())
        try {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                log("!!! Brak skanera BLE - Bluetooth wylaczyl sie w trakcie.")
                _scanning.value = false
                return
            }
            scanner.startScan(filters, settings, scanCallback)
            scanRunning = true
        } catch (e: SecurityException) {
            // Bez zgody na Bluetooth start konczyl sie wyjatkiem i zamknieciem
            // aplikacji w srodku kreatora pomiaru.
            log("!!! Brak uprawnienia do skanowania: " + e.message)
            _scanning.value = false
        }
    }

    private fun pauseScan() {
        if (!scanRunning) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
        scanRunning = false
    }

    fun stopScan() {
        val wasScanning = _scanning.value
        pauseScan()
        _scanning.value = false
        main.removeCallbacks(ticker)
        if (!wasScanning) return
        log("--- stop skanowania ---")
        log(
            "Podsumowanie: " + _devices.value.size + " urzadzen, " + _packets.value +
                " pakietow, w tym od wagi: " + scalePackets + "."
        )
        if (_packets.value == 0) {
            log("ZERO pakietow. To nie jest problem z rozpoznaniem wagi - skanowanie")
            log("w ogole nic nie zwraca. Sprawdz po kolei:")
            log("  1. uslugi lokalizacji wlaczone (lokalizacja ON = " + isLocationOn() + ")")
            log("  2. uprawnienie Lokalizacja przyznane aplikacji")
            log("  3. Bluetooth wlaczony (BT ON = " + isBluetoothOn() + ")")
            log("  4. na Xiaomi: Ustawienia > Aplikacje > GymBro > sprawdz uprawnienia")
        }
    }

    /**
     * Koniec pracy z waga: nasluch i polaczenie. Tanie wagi obsluguja naraz
     * jednego klienta - zostawione polaczenie blokuje je dla aplikacji
     * producenta i trzyma wage wlaczona.
     */
    fun finish() {
        stopScan()
        if (gatt != null) disconnect()
    }

    // ---- Polaczenie i nasluch -------------------------------------------

    /** Reczne laczenie z ekranu diagnostycznego. */
    fun connect(address: String) {
        stopScan()
        val device = adapter?.getRemoteDevice(address)
        if (device == null) {
            log("Nie znaleziono urzadzenia " + address)
            return
        }
        open(device)
    }

    /**
     * Waga, ktora nie nadaje pomiaru w rozgloszeniu, oddaje go dopiero po
     * polaczeniu. Skan na ten czas ustepuje: laczenie w trakcie skanowania
     * na wielu telefonach konczy sie bledem 133.
     */
    private fun tryAutoConnect(device: BluetoothDevice, name: String?) {
        if (gatt != null) return
        val now = System.currentTimeMillis()
        val last = connectTried[device.address]
        if (last != null && now - last < RECONNECT_GAP_MS) return
        connectTried[device.address] = now
        log("Waga " + (name ?: device.address) + " wymaga polaczenia - lacze sam.")
        pauseScan()
        open(device)
    }

    private fun open(device: BluetoothDevice) {
        log("Lacze z " + device.address + " ...")
        synchronized(ops) {
            ops.clear()
            opRunning = false
        }
        qnGreeted = false
        qnDivisor = 100
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connected.value = null
        log("Rozlaczono.")
    }

    /**
     * Operacje GATT musza isc pojedynczo - stos BLE w Androidzie obsluguje
     * jedna naraz i kazda nastepna zlecona przed odpowiedzia po cichu przepada.
     * Stad jedna kolejka na odczyty, subskrypcje i zapisy.
     */
    private sealed class Op {
        class Read(val ch: BluetoothGattCharacteristic) : Op()
        class Subscribe(val ch: BluetoothGattCharacteristic) : Op()
        class Write(val ch: BluetoothGattCharacteristic, val bytes: ByteArray) : Op()
    }

    private val ops = ArrayDeque<Op>()
    private var opRunning = false

    private fun enqueue(g: BluetoothGatt, op: Op) {
        synchronized(ops) { ops.addLast(op) }
        pump(g)
    }

    private fun opDone(g: BluetoothGatt) {
        synchronized(ops) { opRunning = false }
        pump(g)
    }

    private fun pump(g: BluetoothGatt) {
        while (true) {
            val op = synchronized(ops) {
                if (opRunning) null else ops.removeFirstOrNull()?.also { opRunning = true }
            } ?: return
            if (begin(g, op)) return
            synchronized(ops) { opRunning = false }
        }
    }

    /** Falsz = operacji nie udalo sie zlecic, wiec odpowiedz nie przyjdzie. */
    private fun begin(g: BluetoothGatt, op: Op): Boolean = try {
        when (op) {
            is Op.Read -> g.readCharacteristic(op.ch)
            is Op.Subscribe -> subscribe(g, op.ch)
            is Op.Write -> write(g, op.ch, op.bytes)
        }
    } catch (e: SecurityException) {
        false
    }

    private fun subscribe(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(ch, true)
        val cccd = ch.getDescriptor(CCCD)
        if (cccd == null) {
            log("   (brak CCCD dla " + ScaleProtocol.short(ch.uuid) + ", pomijam)")
            return false
        }
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        val p = ch.properties
        val type = if (p and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 &&
            p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        log("ZAPIS " + ScaleProtocol.short(ch.uuid) + ": " + ScaleProtocol.hex(bytes))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            ch.writeType = type
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private fun findCharacteristic(g: BluetoothGatt, short: String): BluetoothGattCharacteristic? =
        g.services.asSequence().flatMap { it.characteristics.asSequence() }
            .firstOrNull { ScaleProtocol.short(it.uuid) == short }

    /**
     * Powitanie rodziny Qingniu: bez odpowiedzi na ramke 0x12 waga milczy
     * i rozlacza sie po kilku sekundach. Starsze wagi maja osobne
     * charakterystyki na jednostke (0xFFE3) i czas (0xFFE4), nowsze jedna
     * wspolna (0xFFF2).
     */
    private fun greetQn(g: BluetoothGatt, protocolType: Int) {
        if (qnGreeted) return
        qnGreeted = true
        val unit = findCharacteristic(g, "0xFFE3") ?: findCharacteristic(g, "0xFFF2")
        val time = findCharacteristic(g, "0xFFE4") ?: findCharacteristic(g, "0xFFF2")
        if (unit == null) {
            log("  Yolanda/QN: brak charakterystyki do zapisu - waga moze nie zaczac nadawac")
            return
        }
        enqueue(g, Op.Write(unit, YolandaParser.unitCommand(protocolType)))
        if (time != null) enqueue(g, Op.Write(time, YolandaParser.timeCommand(System.currentTimeMillis())))
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("POLACZONO (status=" + status + "). Odkrywam serwisy...")
                    _connected.value = g.device.address
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("ROZLACZONO (status=" + status + ")")
                    if (status == 255 || status == 133) {
                        log("  Status " + status + " bez wczesniejszego POLACZONO zwykle znaczy,")
                        log("  ze urzadzenie rozglasza sie jako niekonektowalne (ADV_NONCONN_IND).")
                        log("  Takiej wagi nie da sie polaczyc - dane bierzemy z rozgloszenia.")
                    }
                    _connected.value = null
                    main.post {
                        // Zamkniecie jest konieczne: bez niego kazda proba
                        // zostawia zajety slot klienta GATT, a po kilkunastu
                        // telefon przestaje laczyc sie z czymkolwiek.
                        if (gatt === g) {
                            g.close()
                            gatt = null
                        }
                        // Waga rozlaczyla sie przed wynikiem - sluchamy dalej.
                        resumeScan()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            log("=== Mapa GATT urzadzenia ===")
            val reads = mutableListOf<Op>()
            val subs = mutableListOf<Op>()
            for (service in g.services) {
                val desc = ScaleProtocol.describeService(service.uuid)
                log("SERWIS " + ScaleProtocol.short(service.uuid) + "  " + (desc ?: ""))
                for (ch in service.characteristics) {
                    val props = mutableListOf<String>()
                    val p = ch.properties
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) props += "READ"
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props += "WRITE"
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) props += "WRITE_NR"
                    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props += "NOTIFY"
                    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props += "INDICATE"
                    log("   char " + ScaleProtocol.short(ch.uuid) + "  [" + props.joinToString("|") + "]")

                    val notifiable = p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (notifiable) subs += Op.Subscribe(ch)

                    val readable = p and BluetoothGattCharacteristic.PROPERTY_READ != 0
                    if (readable && ScaleProtocol.short(ch.uuid).removePrefix("0x") in DEVICE_INFO_NAMES) {
                        reads += Op.Read(ch)
                    }
                }
            }
            // Subskrypcje przed tozsamoscia: waga zaczyna nadawac od razu po
            // polaczeniu, a model i firmware moga poczekac. W odwrotnej
            // kolejnosci pierwsze ramki pomiaru przepadaly.
            log("Subskrybuje " + subs.size + " charakterystyk. Teraz wejdz na wage.")
            subs.forEach { enqueue(g, it) }
            reads.forEach { enqueue(g, it) }
        }

        private fun logDeviceInfo(ch: BluetoothGattCharacteristic, value: ByteArray) {
            val key = ScaleProtocol.short(ch.uuid).removePrefix("0x")
            val label = DEVICE_INFO_NAMES[key] ?: key
            // Wartosci sa tekstem, ale tanie urzadzenia potrafia wstawic smieci -
            // stad odsiew znakow niedrukowalnych i podgląd w hex na wszelki wypadek.
            val text = value.toString(Charsets.UTF_8).filter { it.code in 32..126 }.trim()
            if (text.isEmpty()) {
                log("   " + label + ": (puste) " + ScaleProtocol.hex(value))
            } else {
                log("   " + label + ": " + text)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            opDone(g)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            opDone(g)
        }

        // Android 13+
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) logDeviceInfo(ch, value)
            opDone(g)
        }

        // Android 12 i starsze
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) ch.value?.let { logDeviceInfo(ch, it) }
            opDone(g)
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val uuid = ch.uuid
            val copy = value.copyOf()
            main.post { handleFrame(g, uuid, copy) }
        }

        // Android 12 i starsze
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val v = ch.value?.copyOf() ?: return
            val uuid = ch.uuid
            main.post { handleFrame(g, uuid, v) }
        }
    }

    /**
     * Ramka z polaczenia. Zawsze na watku glownym - odczyty z rozgloszen
     * przychodza tam samo, a [WeightSettler] nie jest pisany pod dwa watki.
     */
    private fun handleFrame(g: BluetoothGatt, uuid: UUID, data: ByteArray) {
        log("RAMKA " + ScaleProtocol.short(uuid) + " (" + data.size + " B): " + ScaleProtocol.hex(data))

        val short = ScaleProtocol.short(uuid)
        if (short == "0xFFE1" || short == "0xFFF1") {
            // Ramka 0x12 ustala skale masy dla calej sesji - lapiemy ja pierwsza.
            YolandaParser.parseScaleFactor(data)?.let {
                qnDivisor = it
                log("  Yolanda/QN: dzielnik masy ustawiony na " + it)
                greetQn(g, data[2].toInt() and 0xFF)
            }
        }

        val sample = ScaleDecoders.fromGatt(uuid, data, qnDivisor)
        if (sample == null) {
            if (DIAG) log(ScaleProtocol.annotate(uuid, data))
            return
        }
        log(
            (if (sample.stable == true) ">>> POMIAR STABILNY: " else "    pomiar w trakcie: ") +
                "%.2f kg   [".format(sample.weightKg) + sample.source + "]"
        )
        scalePackets++
        if (_scanning.value) {
            onSample(sample)
        } else if (sample.stable == true) {
            // Ekran diagnostyczny laczy sie recznie, po zatrzymaniu skanu -
            // wynik ma sie tam pokazac mimo to.
            publish(WeightSettler.Verdict(sample, WeightSettler.Reason.SCALE_SAID), System.currentTimeMillis())
        }
    }
}
