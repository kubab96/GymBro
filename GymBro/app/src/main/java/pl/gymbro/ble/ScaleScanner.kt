package pl.gymbro.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val LOG_TAG = "GymBroBLE"

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
    /** Surowa impedancja w omach, jesli waga ja podaje. Potrzebna do zapisu w bazie. */
    val impedanceOhm: Double? = null,
    /** Moment odczytu - czyni kazda emisje unikalna. */
    val at: Long = System.currentTimeMillis()
)

data class FoundDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val services: List<UUID>,
    val family: String?,
    val lastSeen: Long
)

/**
 * Uniwersalny sniffer wag BLE na potrzeby Fazy 0.
 *
 * Zamiast zakladac konkretny protokol, robi trzy rzeczy:
 *  1. skanuje otoczenie i rozpoznaje znane rodziny wag po nazwie i UUID serwisu,
 *  2. zrzuca do logu zawartosc rozgloszen (czesc tanich wag podaje mase
 *     w samym advertisement, bez laczenia),
 *  3. po polaczeniu subskrybuje KAZDA charakterystyke z notify/indicate
 *     i loguje kazda ramke razem z proba jej rozszyfrowania.
 *
 * Efekt: wchodzisz na wage, a aplikacja zapisuje wszystko, co ta waga wysyla.
 * Ten log wystarcza, zeby napisac docelowy parser.
 */
@SuppressLint("MissingPermission")
class ScaleScanner(private val context: Context) {

    private val manager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter

    private val _devices = MutableStateFlow<Map<String, FoundDevice>>(emptyMap())
    val devices: StateFlow<Map<String, FoundDevice>> = _devices.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _connected = MutableStateFlow<String?>(null)
    val connected: StateFlow<String?> = _connected.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Ostatni stabilny pomiar z wagi - to jest wlasciwy wynik Fazy 0. */
    private val _lastStable = MutableStateFlow<ScaleReading?>(null)
    val lastStable: StateFlow<ScaleReading?> = _lastStable.asStateFlow()

    /**
     * Dzielnik masy dla wag QN/Yolanda. Podaje go ramka 0x12 po polaczeniu;
     * do tego czasu zakladamy 100, bo tak ma wiekszosc tych wag.
     */
    private var qnDivisor: Int = 100

    /** Waga rozglasza kilka razy na sekunde - logujemy dopiero zmiane odczytu. */
    private var lastMeasurementKey: String? = null
    private var rawTraceCount = 0

    /** Ile razy z rzedu przyszla ta sama ramka pomiarowa. */
    private var repeatCount = 0

    /** Ile pakietow rozgloszeniowych w ogole doszlo. Zero = skanowanie nie dziala. */
    private val _packets = MutableStateFlow(0)
    val packets: StateFlow<Int> = _packets.asStateFlow()

    /** Deduplikacja tresci rozgloszen, zeby log nie urosl do nieskonczonosci. */
    private val seenPayloads = mutableSetOf<String>()

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
    private val descriptorQueue = ConcurrentLinkedQueue<BluetoothGattCharacteristic>()

    /**
     * Charakterystyki tozsamosci urzadzenia do odczytania po polaczeniu.
     *
     * Osobna kolejka od subskrypcji, bo stos BLE Androida obsluguje jedna
     * operacje GATT naraz: najpierw czytamy, kim urzadzenie jest, potem
     * zapisujemy sie na ramki z pomiarami.
     */
    private val readQueue = ConcurrentLinkedQueue<BluetoothGattCharacteristic>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /**
     * Log diagnostyczny.
     *
     * Trafia w dwa miejsca naraz. Na ekran, bo tam go oglada czlowiek bez
     * podlaczonego komputera. I do logcata, bo odczytywanie dlugiego logu przez
     * przewijanie listy na ekranie jest zawodne - latwo przy tym przegapic linie
     * albo wyjsc z ekranu i przerwac skanowanie.
     */
    private fun log(line: String) {
        val stamped = timeFmt.format(System.currentTimeMillis()) + "  " + line
        _log.value = (_log.value + stamped).takeLast(1500)
        // Poziom informacyjny, nie debug: R8 w konfiguracji "optimize" usuwa
        // wywolania Log.d i Log.v jako pozbawione skutkow ubocznych, wiec w
        // wariancie release log diagnostyczny po prostu znikal.
        android.util.Log.i(LOG_TAG, line)
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun logAsText(): String = _log.value.joinToString("\n")

    // ---- Skanowanie ------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        /**
         * Surowy podsluch jednego urzadzenia, z pominieciem calego odsiewania.
         *
         * Reszta tej klasy celowo wycisza powtorzenia, zeby log dalo sie czytac.
         * Przy diagnozowaniu "dlaczego nie widac wagi" to wyciszanie jest jednak
         * samo w sobie podejrzanym - nie da sie odroznic "pakiety nie przychodza"
         * od "przychodza, ale je odrzucamy". Ta sciezka loguje wszystko, co
         * przyjdzie od wskazanego urzadzenia, i tylko tak mozna to rozstrzygnac.
         */
        private fun traceRaw(result: ScanResult, name: String?) {
            val isScale = name?.startsWith("Yoda", ignoreCase = true) == true ||
                result.device.address.startsWith("A8:0B:6B", ignoreCase = true)
            if (!isScale) return
            rawTraceCount++
            val msd = result.scanRecord?.manufacturerSpecificData
            val payload = if (msd == null || msd.size() == 0) "(brak manufacturer data)"
            else (0 until msd.size()).joinToString("  ") { i ->
                "0x%04X: %s".format(msd.keyAt(i), ScaleProtocol.hex(msd.valueAt(i) ?: ByteArray(0)))
            }
            log("SUROWO #" + rawTraceCount + "  RSSI=" + result.rssi + "  " + payload)
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord
            val name = rec?.deviceName ?: result.device.name
            val services = rec?.serviceUuids?.map { it.uuid } ?: emptyList()

            traceRaw(result, name)

            // Wagi OKOK/Chipsea czesto nie maja sensownej nazwy ani serwisow w rozgloszeniu -
            // rozpoznajemy je po ID producenta w manufacturer data.
            val okOkId: Int? = rec?.manufacturerSpecificData?.let { msd ->
                (0 until msd.size())
                    .map { msd.keyAt(it) }
                    .firstOrNull { OkOkParser.looksLikeOkOk(it) }
            }

            val family = when {
                YolandaParser.matchesName(name) -> "Qingniu / Yolanda (QN-Scale)"
                okOkId != null -> "OKOK / Chipsea (ID 0x%04X, rozgloszenie)".format(okOkId)
                else -> ScaleProtocol.guessFamilyFromName(name)
                    ?: services.firstNotNullOfOrNull { ScaleProtocol.describeService(it) }
            }

            val addr = result.device.address
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    log("  polaczenia: " + if (result.isConnectable) "TAK" else "NIE (samo rozgloszenie)")
                }
                services.forEach {
                    log("  serwis: " + ScaleProtocol.short(it) + "  " + (ScaleProtocol.describeService(it) ?: ""))
                }
            }

            rec?.manufacturerSpecificData?.let { msd ->
                for (i in 0 until msd.size()) {
                    val id = msd.keyAt(i)
                    val bytes = msd.valueAt(i) ?: continue

                    // Wariant rozgloszeniowy Yolanda/QN - naglowek AA BB pod ID 0xFFFF.
                    val y = YolandaParser.parseBroadcast(id, bytes)
                    if (y != null) {
                        val key = "%s|%.2f|%b".format(y.source, y.weightKg, y.stable)
                        if (key != lastMeasurementKey) {
                            lastMeasurementKey = key
                            log("  ADV[0x%04X]: ".format(id) + ScaleProtocol.hex(bytes))
                            log(y.pretty())
                            if (y.stable) {
                                _lastStable.value =
                                    ScaleReading(y.weightKg, null, true, y.source)
                            }
                        }
                        continue
                    }

                    // Rodzina OKOK/Chipsea.
                    val m = OkOkParser.parse(id, bytes)
                    if (m != null) {
                        // Logujemy kazda ODMIENNA ramke, porownujac surowe bajty,
                        // a nie odczytane z nich wartosci.
                        //
                        // Wczesniej kluczem byla masa i impedancja po zdekodowaniu -
                        // przez co ramka, w ktorej zmienilo sie pole, ktorego jeszcze
                        // nie rozumiemy, nigdy nie trafiala do logu. Po ustabilizowaniu
                        // masy wszystko bylo wyciszane. Jesli waga dosyla sklad ciala
                        // osobna ramka, dopiero tak da sie go zobaczyc.
                        val hex = ScaleProtocol.hex(bytes)
                        val key = "%s|%s".format(m.variant, hex)
                        if (key != lastMeasurementKey) {
                            lastMeasurementKey = key
                            repeatCount = 0
                            log("  ADV[0x%04X]: ".format(id) + hex)
                            log("    pola 16-bit: " + ScaleProtocol.fields16(bytes))
                            log(m.pretty())
                            if (m.stable) {
                                _lastStable.value = ScaleReading(
                                    weightKg = m.weightKg,
                                    detail = m.impedanceOhm?.let { "impedancja %.1f Ohm".format(it) },
                                    stable = true,
                                    source = m.variant,
                                    impedanceOhm = m.impedanceOhm
                                )
                            }
                        } else {
                            // Bez tego licznika brak nowych linii w logu jest dwuznaczny:
                            // nie wiadomo, czy waga zamilkla, czy powtarza ten sam odczyt.
                            repeatCount++
                            if (repeatCount % 20 == 0) {
                                log("  (ta sama ramka powtorzona " + repeatCount + " razy - " +
                                    "waga nadaje, ale nie odswieza pomiaru)")
                            }
                        }
                        continue
                    }

                    // Nierozpoznane: logujemy kazda ODMIENNA tresc raz. Waga nadaje
                    // kilka razy na sekunde, ale zmienia payload przy zmianie odczytu,
                    // wiec wejscie na nia samo z siebie wyprodukuje nowe wpisy.
                    val noise = ScaleProtocol.isNoiseCompany(id)
                    val key = if (noise) addr + "|" + id else addr + "|" + id + "|" + ScaleProtocol.hex(bytes)
                    if (seenPayloads.add(key)) {
                        if (seenPayloads.size > 4000) seenPayloads.clear()
                        log("  ADV[0x%04X] ".format(id) + (name ?: addr) + ": " + ScaleProtocol.hex(bytes))
                        log(ScaleProtocol.annotate(null, bytes, deep = !noise))
                    }
                }
            }
            rec?.serviceData?.forEach { (uuid, bytes) ->
                val noise = ScaleProtocol.isNoiseService(uuid.uuid)
                val key = if (noise) addr + "|sd|" + uuid
                          else addr + "|sd|" + uuid + "|" + ScaleProtocol.hex(bytes)
                if (seenPayloads.add(key)) {
                    log("  ADV serviceData[" + ScaleProtocol.short(uuid.uuid) + "] " +
                        (name ?: addr) + ": " + ScaleProtocol.hex(bytes))
                    log(ScaleProtocol.annotate(uuid.uuid, bytes, deep = !noise))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            log("BLAD skanowania, kod=" + errorCode)
            _scanning.value = false
        }
    }

    fun startScan() {
        if (!isBluetoothOn()) {
            log("!!! Bluetooth jest wylaczony - wlacz go i sprobuj ponownie.")
            return
        }
        if (!isLocationOn()) {
            log("!!! USLUGI LOKALIZACJI SA WYLACZONE.")
            log("!!! Android w tym stanie nie zwroci ani jednego wyniku skanowania BLE,")
            log("!!! i nie zglosi zadnego bledu. Wlacz lokalizacje i skanuj ponownie.")
        }
        _devices.value = emptyMap()
        _packets.value = 0
        seenPayloads.clear()
        // Nowe skanowanie to nowy pomiar, nawet jesli waga pokaze dokladnie to samo
        // co poprzednio. Bez wyzerowania identyczne wejscie na wage byloby
        // potraktowane jako powtorzenie poprzedniej ramki i zignorowane.
        lastMeasurementKey = null
        rawTraceCount = 0
        repeatCount = 0
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        // Bez filtrow - chcemy zobaczyc wszystko, co jest w poblizu.
        adapter?.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        _scanning.value = true
        log("--- start skanowania (wejdz na wage, zeby ja obudzic) ---")
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _scanning.value = false
        log("--- stop skanowania ---")
        log("Podsumowanie: " + _devices.value.size + " urzadzen, " + _packets.value + " pakietow.")
        if (_packets.value == 0) {
            log("ZERO pakietow. To nie jest problem z rozpoznaniem wagi - skanowanie")
            log("w ogole nic nie zwraca. Sprawdz po kolei:")
            log("  1. uslugi lokalizacji wlaczone (lokalizacja ON = " + isLocationOn() + ")")
            log("  2. uprawnienie Lokalizacja przyznane aplikacji")
            log("  3. Bluetooth wlaczony (BT ON = " + isBluetoothOn() + ")")
            log("  4. na Xiaomi: Ustawienia > Aplikacje > GymBro > sprawdz uprawnienia")
        }
    }

    // ---- Polaczenie i nasluch -------------------------------------------

    fun connect(address: String) {
        stopScan()
        val device = adapter?.getRemoteDevice(address)
        if (device == null) {
            log("Nie znaleziono urzadzenia " + address)
            return
        }
        log("Lacze z " + address + " ...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connected.value = null
        log("Rozlaczono.")
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
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            log("=== Mapa GATT urzadzenia ===")
            descriptorQueue.clear()
            readQueue.clear()
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
                    if (notifiable) descriptorQueue.add(ch)

                    val readable = p and BluetoothGattCharacteristic.PROPERTY_READ != 0
                    if (readable && ScaleProtocol.short(ch.uuid) in DEVICE_INFO_NAMES) {
                        readQueue.add(ch)
                    }
                }
            }
            log("Subskrybuje " + descriptorQueue.size + " charakterystyk. Teraz wejdz na wage.")
            if (readQueue.isEmpty()) {
                log("Urzadzenie nie udostepnia uslugi informacyjnej - nazwy modelu nie poda.")
                subscribeNext(g)
            } else {
                log("=== Tozsamosc urzadzenia ===")
                readNext(g)
            }
        }

        /** Odczyty tez musza isc pojedynczo, jak zapisy deskryptorow. */
        private fun readNext(g: BluetoothGatt) {
            val ch = readQueue.poll()
            if (ch == null) {
                subscribeNext(g)
                return
            }
            if (!g.readCharacteristic(ch)) {
                log("   (nie udalo sie zlecic odczytu " + ScaleProtocol.short(ch.uuid) + ")")
                readNext(g)
            }
        }

        private fun logDeviceInfo(ch: BluetoothGattCharacteristic, value: ByteArray) {
            val key = ScaleProtocol.short(ch.uuid)
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

        /**
         * Zapisy deskryptorow musza isc pojedynczo - stos BLE w Androidzie
         * obsluguje jedna operacje GATT naraz. Stad kolejka.
         */
        private fun subscribeNext(g: BluetoothGatt) {
            val ch = descriptorQueue.poll()
            if (ch == null) {
                log("=== Subskrypcje gotowe, czekam na ramki ===")
                return
            }
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD)
            if (cccd == null) {
                log("   (brak CCCD dla " + ScaleProtocol.short(ch.uuid) + ", pomijam)")
                subscribeNext(g)
                return
            }
            val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            subscribeNext(g)
        }

        // Android 13+
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) logDeviceInfo(ch, value)
            readNext(g)
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
            readNext(g)
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleFrame(ch.uuid, value)
        }

        // Android 12 i starsze
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val v = ch.value ?: return
            handleFrame(ch.uuid, v)
        }

        private fun handleFrame(uuid: UUID, data: ByteArray) {
            log("RAMKA " + ScaleProtocol.short(uuid) + " (" + data.size + " B): " + ScaleProtocol.hex(data))

            // Ramka 0x12 ustala skale masy dla calej sesji - lapiemy ja pierwsza.
            YolandaParser.parseScaleFactor(data)?.let {
                qnDivisor = it
                log("  Yolanda/QN: dzielnik masy ustawiony na " + it)
            }

            val y = YolandaParser.parseGattFrame(data, qnDivisor)
            if (y != null) {
                log(y.pretty())
                if (y.stable) {
                    _lastStable.value = ScaleReading(
                        y.weightKg,
                        listOfNotNull(
                            y.resistance1?.takeIf { it > 0 }?.let { "R1=$it" },
                            y.resistance2?.takeIf { it > 0 }?.let { "R2=$it" }
                        ).joinToString("  ").ifEmpty { null },
                        true,
                        y.source
                    )
                }
                return
            }

            log(ScaleProtocol.annotate(uuid, data))
        }
    }
}
