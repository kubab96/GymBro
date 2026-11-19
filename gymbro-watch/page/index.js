import * as hmUI from '@zos/ui'
import { px } from '@zos/utils'
import {
  onGesture, offGesture,
  GESTURE_LEFT, GESTURE_RIGHT, GESTURE_UP, GESTURE_DOWN,
  onKey, offKey, KEY_HOME, KEY_BACK, KEY_SELECT, KEY_EVENT_CLICK
} from '@zos/interaction'
import {
  setPageBrightTime, resetPageBrightTime,
  setWakeUpRelaunch, pauseDropWristScreenOff, resetDropWristScreenOff
} from '@zos/display'
import { LocalStorage } from '@zos/storage'
import {
  HeartRate, Calorie, Vibrator,
  VIBRATOR_SCENE_DURATION_LONG
} from '@zos/sensor'
import { DEVICE_WIDTH, DEVICE_HEIGHT } from '../utils/config/device'

const { messageBuilder } = getApp()._options.globalData

const FG = 0xffffff
const DIM = 0x9aa0a6
const ACCENT = 0x6aa9ff
const PENDING = 0x37d6c0
const AMBER = 0xffb357
const GREEN = 0x0b4a42
const GREY = 0x2a3036
const HEART = 0xff7a7a

// Blysk potwierdzenia: kolor mowi co, dlugosc mowi jak duzo.
const FLASH_SET_MS = 320
const FLASH_REST_MS = 320
const FLASH_DONE_MS = 900
// Kolory blysku - tlo licznika serii, ciemne, zeby napis zostal czytelny.
const FLASH_SET = 0x0b4a42
const FLASH_REST = 0x1d3a66
const FLASH_DONE = 0x1f6a46

// Strefy tętna. Progi liczy telefon z rocznika (wzór Tanaki), tu są same
// kolory: chłodne na lekkim wysiłku, ciepłe przy progu, czerwień u sufitu.
const ZONE_EASY = 0x8ab4f8
const ZONE_AEROBIC = 0x5bd68a
const ZONE_TEMPO = 0xf2d24b
const ZONE_ANAEROBIC = 0xffa23a
const ZONE_MAX = 0xff5c5c

const VIEW_SETS = 0
const VIEW_EDIT = 1
const VIEW_DONE = 2
const VIEW_CONTROL = 3
const VIEW_START = 4
const VIEW_STATS = 5
const VIEW_HEART = 6
const VIEW_GAPS = 7
const VIEW_LOG = 8
const VIEW_FIX = 9
// Tonaż po ćwiczeniach. Osobny ekran, bo na podsumowaniu nie ma już miejsca -
// lista nazw wyniosła się stamtąd kiedyś z tego samego powodu.
const VIEW_TONS = 10

/**
 * Dziennik serii: wiersze są przyciskami, więc trafia w nie kciuk.
 *
 * Cztery zamiast sześciu, za to każdy o dwadzieścia sześć pikseli wyższy
 * i o pięć punktów większym pismem. Sześć wierszy po 46 pikseli mieściło
 * wprawdzie całe ćwiczenie naraz, ale czytało się je z nadgarstka jak
 * regulamin, a trafienie w ten właściwy wymagało patrzenia. Czego nie
 * widać od razu, dobiera się gestem w dół - patrz [logFrom].
 *
 * Szerokość 340 przy x=70, a nie 380 przy x=50: przy tej wysokości wiersza
 * rogi górnego wychodziły poza okrąg tarczy i wyglądały na przycięte.
 */
const LOG_ROWS = 4
const LOG_ROW_H = 72
const LOG_ROW_GAP = 78
const LOG_TOP = 76

/**
 * Początek bloku z ciężarem i powtórzeniami.
 *
 * Blok ma 260 pikseli wysokości, ekran 480 - stąd 110 marginesu z góry
 * i tyle samo z dołu.
 */
const EDIT_TOP = 110

// Tyle nazw mieści się na ekranie braków; reszta idzie w "i jeszcze N".
const GAP_ROWS = 6
const TON_ROWS = 6

/**
 * Pionowa karuzela, jak w natywnym treningu siłowym: seria, czas, tętno.
 * Lewo i prawo zostaje na tym, co zmienia trening - edycję i sterowanie.
 */
const COLUMN = [VIEW_SETS, VIEW_STATS, VIEW_HEART]

// Znacznik ciszy po drugiej stronie, odrozniany od zwyklego bledu.
const TIMEOUT = 'timeout'

const PHASE_SET = 'set'
const PHASE_REST = 'rest'

// Wykres tętna: tyle słupków mieści się w okręgu na wysokości podstawy.
const BARS = 24
const BAR_W = 13
const BAR_GAP = 2
const BAR_X0 = 61
const BAR_BASE = 360
const BAR_MAX_H = 130
const BAR_EVERY_MS = 5000

// Przejscie miedzy widokami: animuje je system, nie pętla w JS.
// Tętno: rozmiar liczby i przybliżona szerokość cyfry w tym foncie.
// Szerokości tekstu nie da się tu zmierzyć, więc serce ustawiamy
// z wyliczenia - pomyłka przesuwa odstęp, nie samą liczbę.
const HR_SIZE = 92
const HR_DIGIT_W = 50
const HR_GAP = 18
const HR_ICON_W = 60

// Wibracja trwa sekunde, wiec blokujemy na troche dluzej.
// Nazwa ćwiczenia: pole, przybliżona szerokość znaku i postój na końcach
// przewijania, żeby dało się przeczytać początek i koniec.
const NAME_X = 62
const NAME_Y = 168
const NAME_H = 40
const NAME_BOX_W = 356
// Rozmiar pisma nazwy - potrzebny też do zmierzenia jej szerokości.
const NAME_SIZE = 23
// Zapasowa miara, gdy system nie umie zmierzyć napisu. Z grubsza i w górę,
// bo lepiej przewinąć nazwę, która by się zmieściła, niż uciąć tę, która nie.
const NAME_CHAR_W = 12
const NAME_HOLD_MS = 1100

// Ile czekamy na telefon, zanim uznamy, że nikt nie słucha. Droga wiedzie
// przez Bluetooth, więc sekunda to za mało, a dwadzieścia to już wiszenie.
// Ekran świeci długo, bo przerwa między seriami bywa trzyminutowa.
const BRIGHT_MS = 600000

// Klucz w pamięci zegarka. Jeden, bo i stan jest jeden.
const UNITS_KEY = 'gymbro-units'
// Kolejka ma wlasny klucz, nie wspolny ze stanem sesji: zakonczenie treningu
// czysci stan, a to, co jeszcze nie dojechalo do telefonu, ma przezyc.
const OUTBOX_KEY = 'gymbro-outbox'
// Ciezkie rzeczy osobno: wykres tetna rosnie przez caly trening, a plan
// jednostki niesie drabinki ciezarow dla kazdego cwiczenia. Trzymane razem
// z postepem zapisywaly sie przy kazdym nacisnieciu przycisku, a zapis do
// pamieci trwalej na zegarku to zapis do pliku - stad klejace sie przyciski.
const HEAVY_KEY = 'gymbro-session-big'
const STORE_KEY = 'session'

const REQUEST_TIMEOUT_MS = 8000
// Ponawianie wysylki: od pieciu sekund, z podwajaniem, do dwoch minut.
// Stale pytanie martwego telefonu co piec sekund obciaza zegarek do konca
// treningu, a telefon i tak nie wroci szybciej, niz go wlaczysz.
const DRAIN_MIN_MS = 5000
const DRAIN_MAX_MS = 120000
// Po ilu godzinach trening i niewyslane polecenia sa juz nieaktualne.
// Ta sama granica, ktora telefon stosuje do porzuconych sesji: po niej
// zamyka je na ostatniej serii, wiec dosylanie czegokolwiek nie ma sensu.
// Bez tej granicy zegarek wskrzeszal trening sprzed dni, a zalegle START
// zakladalo na telefonie pusta sesje-widmo.
const STALE_AFTER_MS = 8 * 3600000
// Po ilu milisekundach od prosby zapisujemy stan. Dluzej niz przejscie
// miedzy ekranami: zapis do pliku potrafi zjesc pierwsze klatki animacji,
// a wtedy zaslona stoi i widac czarny ekran.
const ZAPIS_MS = 350
// Ile czeka uzbrojony przycisk kasowania, zanim sam sie wycofa.
const FIX_CONFIRM_MS = 4000
// Ile po ostatniej serii planu czekamy na zapas, zanim pokaze sie
// podsumowanie. Wybor zapasu przechodzi do podsumowania od razu.
const KONIEC_MS = 4000
// Zapis do pamieci czeka, az palec da spokoj. 350 ms po kliknieciu to
// akurat chwila, w ktorej przychodzi nastepne - plus, minus, zapas po serii -
// i wtedy to ono czekalo na zapis pliku.
const CISZA_MS = 1200
// Ile polecen z kolejki leci jedna paczka. Po treningu bez lacznosci w kolejce
// stoi ich kilkadziesiat, a kazda runda przez Bluetooth to sekunda.
const PACZKA = 15
// Ile ciszy po ostatnim dotyku, zanim polecenie pojdzie do telefonu. Dluzej niz
// CISZA_MS, bo po serii zwykle pada jeszcze zapas - wtedy oba ida jednym
// poleceniem, a Bluetooth nie pracuje w chwili, w ktorej palec jest na ekranie.
const WYSYLKA_MS = 2500
// Ile numerow potwierdzonych serii pamietamy - tyle, ile da sie jeszcze
// poprawic albo opisac zapasem z ekranu, ktory je pokazuje.
const ACKED_MAX = 40

// Rząd przycisków zapasu powtórzeń. Mieści się w okręgu na tej wysokości.
const RIR_X0 = 88
const RIR_Y = 374
const RIR_W = 70
const RIR_H = 50
const RIR_GAP = 8

// Najdluzsza przerwa miedzy probkami, jaka jeszcze doliczamy do spalania.
const BURN_MAX_GAP_MS = 30000

const BUZZ_GUARD_MS = 1200

/**
 * Pilot treningu.
 *
 * Obsługa sprowadza się do jednego przycisku, naprzemiennie: koniec serii
 * zaczyna przerwę, koniec przerwy zaczyna kolejną serię. Zegar na środku
 * pokazuje, jak długo trwa to, co akurat robisz. Dzięki temu w trakcie
 * ćwiczenia nie trzeba patrzeć na ekran ani w niego celować.
 *
 * Zasady, które wyszły z używania tego na siłowni:
 *
 * **Ekran reaguje od razu**, bez czekania na telefon - licznik podbija się
 * lokalnie, żądanie leci w tle.
 *
 * **Niepotwierdzone serie liczone są osobno**, inaczej odpowiedź na starsze
 * żądanie cofa licznik.
 *
 * **Rysuje się tylko widoczny ekran** - przerysowywanie wszystkiego przy
 * każdym powtórzeniu było widocznie wolne.
 *
 * Układ mieści się w okręgu: przy promieniu 240 element na wysokości y ma do
 * dyspozycji sqrt(240^2 - (y-240)^2) w każdą stronę od środka.
 */
Page({
  state: {
    view: VIEW_SETS,
    data: null,
    index: 0,
    weight: 0,
    reps: 0,
    hr: 0,
    hrLog: [],
    phase: PHASE_SET,
    phaseSince: Date.now(),
    sessionSec: 0,
    sessionAt: Date.now(),
    kcalBase: null,
    kcal: 0,
    message: null,
    built: false,
    wasComplete: false,
    firstState: true,
    units: null,
    program: '',
    skipped: {},
    sessionId: null,
    hrMin: 0,
    hrMax: 0,
    kcalSeen: false,
    kcalFail: false,
    kcalTotal: 0,
    burn: null,
    burnKcal: 0,
    burnAt: 0,
    nextUnit: null,
    fix: null,
    // Czy przycisk kasowania czeka na potwierdzenie.
    fixConfirm: false,
    rirFor: null,
    /** Pierwsza widoczna seria w dzienniku - patrz [logFrom]. */
    logAt: 0,
    editedFor: null,
    zones: null,
    // Wstrzymanie trzymamy lokalnie. null znaczy "co powie telefon";
    // true/false to nasza decyzja, która czeka na potwierdzenie.
    pausedLocal: null,
    // Polecenia, które muszą dojść do telefonu. Trening ma iść dalej
    // także bez łączności, a to, co się w nim wydarzyło, ma dojechać
    // samo, kiedy połączenie wróci.
    outbox: [],
    offline: false,
    // Znacznik sesji zalozonej tutaj, bez telefonu. null, gdy sesja jest
    // telefonu albo gdy ten juz nadal jej swoj numer.
    localId: null,
    startedAt: 0,
    // Numery serii nadane przez telefon, po znaczniku polecenia SET. Z tego
    // zapas i poprawka trafiaja w te serie, ktora pokazuje ekran, nawet gdy
    // zegarek poznal jej numer dopiero po fakcie.
    acked: {},
    // Ile polecen czekalo w kolejce ostatnio. Z tego bierze sie pojedyncza
    // wibracja w chwili, gdy wszystko wreszcie dojechalo do telefonu.
    syncWas: 0,
    // Czy ostatnia kolejka doszla do konca - stad napis 'Wyslane'.
    syncDone: false
  },

  onInit() {
    // Ekran gaśnie - aplikacja ginie. System daje jej dziesięć sekund
    // i wraca na tarczę, więc samo wydłużenie podświetlenia nie wystarcza:
    // po opuszczeniu ręki trening znikał i trzeba go było otwierać od nowa.
    //
    // Trzy rzeczy naraz: świeć długo, nie gaś przy opuszczeniu nadgarstka,
    // a gdyby mimo to zgasło - wróć na tę samą stronę po obudzeniu zegarka.
    // To ostatnie jest ponownym uruchomieniem, nie wznowieniem, dlatego
    // stan trzymamy w pamięci trwałej i odtwarzamy go w [restore].
    setPageBrightTime({ brightTime: BRIGHT_MS })
    try { setWakeUpRelaunch({ relaunch: true }) } catch (e) {}
    try { pauseDropWristScreenOff({ duration: 0 }) } catch (e) {}

    try {
      this.store = new LocalStorage()
    } catch (e) {
      this.store = null
    }
    try {
      this.vibrator = new Vibrator()
    } catch (e) {
      this.vibrator = null
    }
  },

  // ---- Pamięć między uruchomieniami -------------------------------------

  /**
   * To, czego telefon o nas nie wie.
   *
   * Które ćwiczenie oglądasz, co pominąłeś, czy trwa przerwa, przebieg
   * tętna - telefon zna tylko zapisane serie. Po zgaszeniu ekranu aplikacja
   * startuje od zera, więc bez tego wracała na pierwsze ćwiczenie i gubiła
   * pominięcia, co wyglądało jak cofnięcie się treningu.
   *
   * Zapis jest przywiązany do numeru sesji: stan po cudzym treningu byłby
   * gorszy niż jego brak.
   */
  /**
   * Plan zapamietany na pozniej.
   *
   * Osobno od stanu treningu, bo potrzebny jest zanim jakikolwiek trening
   * sie zacznie - to z niego zegarek buduje sesje, gdy telefonu nie ma
   * w zasiegu. Odswiezany przy kazdej udanej odpowiedzi, wiec jest tak
   * swiezy, jak ostatnie polaczenie.
   */
  rememberUnits() {
    if (!this.store) return
    try {
      const tekst = JSON.stringify({
        program: this.state.program,
        units: this.state.units,
        // Wspolczynniki spalania i progi tetna lezaly dotad wylacznie w
        // zapisie sesji, a ten jest czyszczony na koncu treningu. Nastepny
        // trening bez lacznosci zaczynal wiec bez nich i kalorie staly na
        // kresce przez cale godziny. Licza sie dla kazdej sesji, nie dla
        // jednej, wiec ich miejsce jest tutaj.
        burn: this.state.burn,
        zones: this.state.zones
      })
      // Lista jednostek niesie plany z drabinkami - kilkanascie kilobajtow.
      // Odswiezana co chwile na ekranie wyboru, zapisywala sie za kazdym
      // razem do pliku, choc prawie zawsze byla ta sama.
      if (tekst === this.store.getItem(UNITS_KEY)) return
      this.store.setItem(UNITS_KEY, tekst)
    } catch (e) {
      this.store = null
    }
  },

  /** Plan z pamieci. Zwraca, czy sie udalo. */
  restoreUnits() {
    if (!this.store) return false
    let zapis = null
    try {
      const raw = this.store.getItem(UNITS_KEY)
      if (raw) zapis = JSON.parse(raw)
    } catch (e) {
      return false
    }
    const units = (zapis && zapis.units) || []
    if (!units.length) return false
    this.state.program = (zapis && zapis.program) || ''
    this.state.units = units
    if (!this.state.burn && zapis.burn && zapis.burn.k1) this.state.burn = zapis.burn
    if (!this.state.zones && zapis.zones && zapis.zones.max) this.state.zones = zapis.zones
    const kolejna = units.filter((u) => u.next)[0]
    if (kolejna) this.state.nextUnit = kolejna.name
    this.przeliczKolejna()
    return true
  },

  rememberOutbox() {
    this.kolejkaBrudna = true
    this.planujZapisKolejki()
  },

  planujZapisKolejki() {
    if (this.kolejkaTimer) return
    this.kolejkaTimer = setTimeout(() => {
      this.kolejkaTimer = null
      if (this.zajeteRysowaniem()) {
        this.planujZapisKolejki()
        return
      }
      if (this.kolejkaBrudna) this.rememberOutboxNow()
    }, ZAPIS_MS)
  },

  rememberOutboxNow() {
    this.kolejkaBrudna = false
    if (!this.store) return
    try {
      this.store.setItem(OUTBOX_KEY, JSON.stringify(this.state.outbox || []))
    } catch (e) {
      this.store = null
    }
  },

  restoreOutbox() {
    if (!this.store) return
    try {
      const raw = this.store.getItem(OUTBOX_KEY)
      const lista = raw ? JSON.parse(raw) : []
      // Zalegla kolejka NIE jest wyrzucana. Wczesniej odpadalo z niej
      // wszystko starsze niz osiem godzin i wlasnie tak przepadl caly
      // trening: telefon lezal bez lacznosci, a nad ranem zegarek wyrzucil
      // jedyny zapis tamtych serii. Baza na telefonie jest jedyna kopia
      // historii, wiec zaleglosc dojezdza niezaleznie od tego, ile czekala.
      //
      // Zostaje tylko jedno: trening bez zakonczenia, ktory lezy w kolejce
      // od osmiu godzin, jest treningiem porzuconym. Dopisujemy mu koniec
      // na godzinie ostatniego polecenia - dokladnie tak, jak robi to
      // telefon z porzucona sesja - bo inaczej dojechalby jako trwajacy
      // i zaslonilby ekran nastepnego dnia.
      const teraz = Date.now()
      let kolejka = lista || []
      const najstarsze = kolejka.length ? (kolejka[0].at || teraz) : teraz
      const maKoniec = kolejka.some((x) => x.m === 'FINISH')
      const maStart = kolejka.some((x) => x.m === 'START')
      if (kolejka.length && !maKoniec && maStart &&
        teraz - najstarsze >= STALE_AFTER_MS) {
        const ostatnie = kolejka[kolejka.length - 1]
        const koniec = {
          m: 'FINISH',
          p: { endedAt: ostatnie.at || najstarsze },
          cid: 'late-' + najstarsze,
          at: ostatnie.at || najstarsze
        }
        if (ostatnie.p && ostatnie.p.sid) koniec.p.sid = ostatnie.p.sid
        koniec.p.cid = koniec.cid
        kolejka = kolejka.concat([koniec])
      }
      // Bez powtorek. Kolejka lezala dotad takze w zapisie sesji i po kazdym
      // zgasnieciu ekranu oba zapisy skladaly sie w jeden - dwa razy dluzszy.
      // Bez lacznosci rosla tak przy kazdym opuszczeniu nadgarstka: dwadziescia
      // serii robilo sie tysiacem polecen, zapis pliku trwal coraz dluzej,
      // przyciski zaczynaly sie kleic, a na koncu zegarek ubijal aplikacje.
      const razem = this.bezPowtorek(kolejka.concat(this.state.outbox || []))
      this.state.outbox = razem
      if (razem.length !== (lista || []).length) this.rememberOutbox()
    } catch (e) {}
  },

  /** Kolejka bez polecen o tym samym znaczniku - zostaje pierwsze. */
  bezPowtorek(lista) {
    const byly = {}
    return (lista || []).filter((x) => {
      if (!x || !x.m) return false
      if (!x.cid) return true
      if (byly[x.cid]) return false
      byly[x.cid] = true
      return true
    })
  },

  /**
   * Wykres tetna i plan jednostki.
   *
   * Zapisywane rzadko, bo ciezkie: tetno rosnie przez caly trening, a plan
   * niesie drabinki ciezarow. Zmieniaja sie wolno, wiec nie maja czego
   * szukac przy kazdym nacisnieciu przycisku.
   *
   * Plan trafia tu dla kazdej sesji, nie tylko zalozonej na zegarku. Trening
   * z numerem od telefonu po zgasnieciu ekranu bez lacznosci nie mial skad
   * wrocic - zegarek pokazywal wybor jednostki w srodku treningu, a nowy
   * start z tego ekranu zakladal drugi trening obok trwajacego.
   */
  rememberHeavy() {
    this.ciezkieBrudne = false
    if (!this.store || !this.state.sessionId) return
    try {
      this.store.setItem(HEAVY_KEY, JSON.stringify({
        sessionId: this.state.sessionId,
        hrLog: this.state.hrLog,
        data: this.state.data
      }))
    } catch (e) {
      this.store = null
    }
  },

  /** Prosba o zapis planu i tetna - za chwile, w ciszy, razem z reszta. */
  rememberHeavyLater() {
    this.ciezkieBrudne = true
    this.planujZapis()
  },

  restoreHeavy(sessionId, zDanymi) {
    if (!this.store || !sessionId) return
    let zapis = null
    try {
      const raw = this.store.getItem(HEAVY_KEY)
      if (raw) zapis = JSON.parse(raw)
    } catch (e) {
      return
    }
    if (!zapis || zapis.sessionId !== sessionId) return
    this.state.hrLog = zapis.hrLog || []
    if (zDanymi && zapis.data && zapis.data.exercises) this.state.data = zapis.data
  },

  /** Czy w pamieci lezy plan tej sesji - bez niego nie ma czego pokazac. */
  maPlan(sessionId) {
    if (!this.store || !sessionId) return false
    try {
      const raw = this.store.getItem(HEAVY_KEY)
      const zapis = raw ? JSON.parse(raw) : null
      return !!(zapis && zapis.sessionId === sessionId && zapis.data &&
        (zapis.data.exercises || []).length)
    } catch (e) {
      return false
    }
  },

  /**
   * Prosba o zapis stanu - wykonana za chwile, nie tu.
   *
   * Zapis do pamieci trwalej to zapis do pliku i nie ma go po co robic
   * w uchwycie przycisku. Kilka prosb pod rzad sklada sie w jeden zapis,
   * bo i tak liczy sie tylko stan koncowy.
   */
  remember() {
    this.pamiecBrudna = true
    this.planujZapis()
  },

  /** Zapis za chwilę, i nigdy pod palcem. */
  planujZapis() {
    if (this.pamiecTimer) return
    this.pamiecTimer = setTimeout(() => {
      this.pamiecTimer = null
      if (this.zajeteRysowaniem()) {
        this.planujZapis()
        return
      }
      if (this.pamiecBrudna) this.rememberNow()
      if (this.ciezkieBrudne) this.rememberHeavy()
    }, ZAPIS_MS)
  },

  /**
   * Czy palec wlasnie pracuje i nie wolno mu zabierac watku.
   *
   * Dotad liczylo sie tylko to, czy cos sie animuje. Ale zapis pliku
   * puszczony 350 ms po kliknieciu trafial akurat w nastepne klikniecie -
   * drugi plus, zapas zaraz po serii - i to ono czekalo, az plik sie zapisze.
   */
  zajeteRysowaniem() {
    return !!(this.ostatniDotyk && Date.now() - this.ostatniDotyk < CISZA_MS)
  },

  /** Slad, ze palec cos zrobil - od tej chwili liczy sie cisza. */
  dotyk() {
    this.ostatniDotyk = Date.now()
  },

  rememberNow() {
    this.pamiecBrudna = false
    if (!this.store || !this.state.sessionId) return
    try {
      this.store.setItem(STORE_KEY, JSON.stringify({
        sessionId: this.state.sessionId,
        index: this.state.index,
        skipped: this.state.skipped,
        phase: this.state.phase,
        phaseSince: this.state.phaseSince,
        hrMin: this.state.hrMin,
        hrMax: this.state.hrMax,
        kcalBase: this.state.kcalBase,
        burnKcal: this.state.burnKcal,
        // Współczynniki spalania, nie tylko sam licznik. Przychodzą
        // wyłącznie z telefonu, a zgaszenie ekranu ubija mini-aplikację -
        // bez nich [addBurn] wychodziło na pierwszej linijce i kalorie
        // stały aż do następnej odpowiedzi. Z telefonem w szatni: cały trening.
        burn: this.state.burn,
        kcalSeen: this.state.kcalSeen,
        // Kolejki tu nie ma. Leżała dotąd i tutaj, i pod własnym kluczem,
        // a po restarcie obie kopie się sumowały - patrz [restoreOutbox].
        // Serie niepotwierdzone wynikają z kolejki, więc też ich tu nie ma.
        acked: this.state.acked,
        // Pytanie o zapas dotyczy serii sprzed chwili. Przerwa przeżywa
        // zgaszenie ekranu, więc pytanie musi przeżyć razem z nią.
        rirFor: this.state.rirFor,
        // Liczby ustawione na ekranie serii, o ile ktos je tam przytrzymal -
        // patrz [trzymajEkran]. Bez tego restart strony oddawal je telefonowi.
        pin: this.state.editedFor
          ? { ex: this.state.editedFor, weight: this.state.weight, reps: this.state.reps }
          : null,
        wasComplete: this.state.wasComplete,
        pausedLocal: this.state.pausedLocal,
        localId: this.state.localId,
        planId: this.state.planId,
        startedAt: this.state.startedAt,
        sessionSec: this.state.sessionSec,
        sessionAt: this.state.sessionAt
      }))
    } catch (e) {
      this.store = null
    }
  },

  /**
   * Powrót do treningu wyłącznie z własnej pamięci, bez pytania telefonu.
   *
   * Dotąd tylko dla treningu założonego na zegarku. Trening, który zdążył
   * dostać numer od telefonu, po zgaśnięciu ekranu bez łączności nie wracał:
   * zegarek czekał osiem sekund na odpowiedź, a potem pokazywał wybór
   * jednostki - w środku treningu. Start z tego ekranu zakładał drugi trening
   * obok trwającego i serie rozjeżdżały się między nimi.
   */
  restoreLocal() {
    if (!this.store) return false
    let zapis = null
    try {
      const raw = this.store.getItem(STORE_KEY)
      if (raw) zapis = JSON.parse(raw)
    } catch (e) {
      return false
    }
    if (!zapis || !zapis.sessionId) return false
    // Trening sprzed wielu godzin to trening zapomniany, nie przerwany.
    // Wskrzeszony pokazywal sie jako "wczesniej rozpoczety" i wysylal
    // polecenia do sesji, ktorej telefon juz dawno nie ma.
    const start = zapis.startedAt || 0
    if (!start || Date.now() - start > STALE_AFTER_MS) {
      if (zapis.localId || start) this.forget()
      return false
    }
    // Bez planu nie ma czego pokazać. Zapis sprzed tej zmiany nie trzymał
    // planu dla treningu z numerem od telefonu - wtedy zostaje telefon.
    if (!this.maPlan(zapis.sessionId)) return false
    this.state.sessionId = zapis.sessionId
    if (!this.restore(zapis.sessionId, true)) return false
    const lista = (this.state.data && this.state.data.exercises) || []
    if (!lista.length) return false
    this.state.firstState = false
    const ex = lista[Math.min(this.state.index, lista.length - 1)]
    if (ex && this.state.editedFor !== ex.id) {
      this.state.weight = ex.weight
      this.state.reps = ex.reps
    }
    return true
  },

  /** Odtworzenie stanu, o ile dotyczy tej samej sesji co odpowiedź telefonu. */
  restore(sessionId, zPlanem) {
    if (!this.store || !sessionId) return false
    let zapis = null
    try {
      const raw = this.store.getItem(STORE_KEY)
      if (raw) zapis = JSON.parse(raw)
    } catch (e) {
      return false
    }
    if (!zapis || zapis.sessionId !== sessionId) return false

    this.state.index = zapis.index || 0
    this.state.skipped = zapis.skipped || {}
    this.state.phase = zapis.phase === PHASE_REST ? PHASE_REST : PHASE_SET
    this.state.phaseSince = zapis.phaseSince || Date.now()
    this.state.hrMin = zapis.hrMin || 0
    this.state.hrMax = zapis.hrMax || 0
    this.state.kcalBase = zapis.kcalBase === undefined ? null : zapis.kcalBase
    this.state.burnKcal = zapis.burnKcal || 0
    // Tylko dodaje, nigdy nie zabiera: wspolczynniki moga pochodzic z zapisu
    // jednostek, ktory jest trwalszy od zapisu sesji.
    if (zapis.burn && zapis.burn.k1) this.state.burn = zapis.burn
    this.state.kcalSeen = !!zapis.kcalSeen
    this.state.acked = zapis.acked || {}
    this.state.rirFor = zapis.rirFor || null
    if (zapis.pin && zapis.pin.ex) {
      this.state.editedFor = zapis.pin.ex
      this.state.weight = zapis.pin.weight
      this.state.reps = zapis.pin.reps
    }
    this.state.wasComplete = !!zapis.wasComplete
    // Kolejka z zapisu sesji (wersje sprzed tej zmiany) tylko wtedy, gdy
    // wlasnej nie ma. Obie byly kopia tego samego, a zsumowane podwajaly
    // kolejke przy kazdym restarcie - patrz [restoreOutbox].
    if (zapis.outbox && zapis.outbox.length && !(this.state.outbox || []).length) {
      this.state.outbox = this.bezPowtorek(zapis.outbox)
      this.zmienione()
    }
    this.state.localId = zapis.localId || null
    this.state.planId = zapis.planId || null
    this.state.startedAt = zapis.startedAt || 0
    this.restoreHeavy(sessionId, zPlanem)
    const p = zapis.pausedLocal
    this.state.pausedLocal = (p === true || p === false) ? p : null
    // Czas treningu po restarcie. Gdy wstrzymanie jeszcze nie doszło do
    // telefonu, jego sekundy są nieaktualne. Przy sesji odtwarzanej z własnej
    // pamięci telefon nie odezwał się wcale, więc nasze są jedyne.
    if (this.state.pausedLocal !== null || this.state.localId || zPlanem) {
      this.state.sessionSec = zapis.sessionSec || 0
      this.state.sessionAt = zapis.sessionAt || Date.now()
    }
    this.zmienione()
    return true
  },

  forget() {
    if (!this.store) return
    try { this.store.removeItem(STORE_KEY) } catch (e) {}
    try { this.store.removeItem(HEAVY_KEY) } catch (e) {}
  },

  /**
   * Jedyna wibracja w całej aplikacji: jedno ciągłe szarpnięcie.
   *
   * Ma tylko potwierdzić, że zapis wszedł, przy sztandze w rękach i ekranie
   * poza polem widzenia. Lekkie potwierdzenia pod każdym przyciskiem
   * odpadły: kilka naciśnięć pod rząd zlewało się w serię impulsów i cały
   * sygnał przestawał cokolwiek znaczyć.
   *
   * Blokada sekundy z okładem pilnuje, że dwa zdarzenia blisko siebie nie
   * zrobią z jednego szarpnięcia dwóch - szarpnięcie trwa pełną sekundę,
   * więc kolejne nałożyłoby się na trwające.
   */
  buzz() {
    if (!this.vibrator) return
    const now = Date.now()
    if (this.lastBuzz && now - this.lastBuzz < BUZZ_GUARD_MS) return
    this.lastBuzz = now
    try {
      this.vibrator.start({ mode: VIBRATOR_SCENE_DURATION_LONG })
    } catch (e) {
      this.vibrator = null
    }
  },

  build() {
    // To, co nie dojechalo przed zgasnieciem ekranu, czeka w pamieci.
    this.restoreOutbox()
    // Zaleglosc z pamieci tez liczy sie jako "cos czeka": bez tego wibracja
    // po dojechaniu kolejki nalezala sie tylko temu, kto zdazyl zobaczyc, jak
    // rosnie - a przy zaleglosci z nocy nikt tego nie widzial.
    this.state.syncWas = this.czeka()
    onGesture({
      callback: (event) => {
        this.dotyk()
        const view = this.state.view
        const col = COLUMN.indexOf(view)

        // Pion przewija karuzelę.
        if (col >= 0 && !this.state.message) {
          if (event === GESTURE_UP && col < COLUMN.length - 1) {
            this.showView(COLUMN[col + 1])
            return true
          }
          if (event === GESTURE_DOWN && col > 0) {
            this.showView(COLUMN[col - 1])
            return true
          }
          // Pod głównym ekranem leży dziennik serii tego ćwiczenia.
          if (event === GESTURE_DOWN && view === VIEW_SETS) {
            // Zawsze od pierwszej serii: wejście w dziennik to pytanie
            // "co dzisiaj zrobiłem", a nie powrót tam, gdzie się skończyło.
            this.state.logAt = 0
            this.showView(VIEW_LOG)
            return true
          }
        }

        // Dziennik i poprawka leżą poza karuzelą, więc ich wyjścia muszą
        // leżeć poza jej blokiem. Zagnieżdżone w środku nie działały wcale:
        // do dziennika dało się wejść, bo wejście wychodzi z ekranu serii,
        // ale wyjścia już nikt nie sprawdzał i ekran był pułapką.
        if (!this.state.message) {
          if (event === GESTURE_UP && view === VIEW_LOG) {
            this.showView(VIEW_SETS)
            return true
          }
          // W dół kolejne serie, a gdy wszystkie się mieszczą - nic.
          // Przed tą linijką gest w dół w dzienniku nie robił nic nigdy.
          if (event === GESTURE_DOWN && view === VIEW_LOG) {
            return this.logScroll()
          }
          // Z poprawki wychodzi się każdym rozsądnym gestem - to ekran
          // doraźny, a nie miejsce w nawigacji.
          if (view === VIEW_FIX) {
            if (event === GESTURE_RIGHT) {
              this.showView(VIEW_SETS)
              return true
            }
            this.showView(VIEW_LOG)
            return true
          }
          if (event === GESTURE_RIGHT && view === VIEW_LOG) {
            this.showView(VIEW_SETS)
            return true
          }
        }

        if (event === GESTURE_LEFT && view === VIEW_SETS) {
          this.showView(VIEW_EDIT)
          return true
        }
        if (event === GESTURE_LEFT && view === VIEW_CONTROL) {
          this.showView(VIEW_SETS)
          return true
        }
        // Z podsumowania w górę na listę pominiętych - ale tylko wtedy,
        // gdy jest co pokazywać, żeby gest nie prowadził w pustkę.
        if (event === GESTURE_UP && view === VIEW_DONE && this.gaps().sets > 0) {
          this.showView(VIEW_GAPS)
          return true
        }
        // Tonaż leży pod podsumowaniem, braki nad nim.
        if (event === GESTURE_DOWN && view === VIEW_DONE && this.tonRows().length) {
          this.showView(VIEW_TONS)
          return true
        }
        if (event === GESTURE_UP && view === VIEW_TONS) {
          this.showView(VIEW_DONE)
          return true
        }
        if (event === GESTURE_DOWN && view === VIEW_GAPS) {
          this.showView(VIEW_DONE)
          return true
        }
        if (event === GESTURE_RIGHT && (view === VIEW_EDIT || view === VIEW_DONE || view === VIEW_GAPS)) {
          this.showView(VIEW_SETS)
          return true
        }
        // W prawo z głównego ekranu wychodziło z aplikacji. Teraz jest tam
        // sterowanie treningiem, tak jak w natywnym treningu siłowym.
        if (event === GESTURE_RIGHT && view === VIEW_SETS) {
          this.showView(VIEW_CONTROL)
          return true
        }
        // Z ekranu czasu i tętna w prawo wracamy na serie. Wcześniej gest
        // wracał bez obsługi, a wtedy system robi swoje - czyli wychodzi
        // z aplikacji w środku treningu.
        if (event === GESTURE_RIGHT && col > 0) {
          this.showView(VIEW_SETS)
          return true
        }
        return false
      }
    })

    // Każdy klawisz poza domowym, wstecz i wyborem obsługuje trening.
    // Na tym zegarku zostaje przez to dolny przycisk boczny.
    //
    // Zwracamy `true` niezależnie od widoku. Wcześniej obsługiwana była
    // tylko pionowa karuzela, a na edycji i na podsumowaniu `false`
    // oddawało klawisz systemowi - i ten wychodził z aplikacji w środku
    // treningu. Wyjście zostaje na klawiszu domowym, gdzie jego miejsce.
    onKey({
      callback: (key, keyEvent) => {
        if (keyEvent !== KEY_EVENT_CLICK) return false
        if (key === KEY_HOME || key === KEY_BACK || key === KEY_SELECT) return false
        this.dotyk()
        this.sideButton()
        return true
      }
    })

    this.startHeartRate()
    this.startCalorie()
    this.buildWidgets()
    this.startClock()

    // Trening wraca z własnej pamięci, bez pytania telefonu. Zgaśnięcie ekranu ubija
    // mini-aplikację przy każdym opuszczeniu nadgarstka, więc to nie jest
    // przypadek brzegowy, tylko zwykły bieg rzeczy. Czekanie na telefon
    // znaczyłoby, że bez niego trening ginie po pierwszym zgaśnięciu.
    const wrocil = this.restoreLocal()
    if (wrocil) {
      this.state.view = VIEW_SETS
      this.state.message = null
      this.applyVisibility()
      this.paint()
      this.scheduleDrain(0)
      // Ekran zgasl w tych kilku sekundach miedzy ostatnia seria planu
      // a podsumowaniem. Podsumowanie nalezy sie nadal.
      if (!this.state.wasComplete && this.localComplete()) this.zaplanujKoniec()
    } else {
      this.setMessage('Łączę z telefonem...')
    }
    // Po powrocie z pamieci zegarek wie, gdzie stoi - telefon nie przestawia.
    this.send('STATE', {}, !wrocil)
  },

  onDestroy() {
    // Ostatni moment na zapis: odlozony nie zdazylby sie wykonac.
    this.rememberNow()
    this.rememberOutboxNow()
    this.rememberHeavy()
    offGesture()
    offKey()
    if (this.clockTimer) clearTimeout(this.clockTimer)
    if (this.nameTimer) clearTimeout(this.nameTimer)
    if (this.koniecTimer) clearTimeout(this.koniecTimer)
    if (this.flashTimer) clearTimeout(this.flashTimer)
    this.nameRun = -1
    if (this.heart && this.onHeart) this.heart.offCurrentChange(this.onHeart)
    if (this.calorie && this.onCalorie) this.calorie.offChange(this.onCalorie)
    if (this.vibrator) {
      try { this.vibrator.stop() } catch (e) {}
    }
    try { resetDropWristScreenOff() } catch (e) {}
    resetPageBrightTime()
  },

  // ---- Tętno -------------------------------------------------------------

  /**
   * Tętno z czujnika zegarka, nie z telefonu.
   *
   * Uprawnienie `data:user.hd.heart_rate` musi być w app.json; zgody udziela
   * się przy instalacji. `getCurrent()` wolno wołać wyłącznie wewnątrz
   * wywołania zwrotnego, a wyłączenie nasłuchu wymaga tej samej referencji.
   */
  startHeartRate() {
    try {
      this.heart = new HeartRate()
      this.onHeart = () => {
        this.state.hr = this.heart.getCurrent() || 0
        this.noteHeart(this.state.hr)
        this.paintHeartValue()
      }
      this.state.hr = this.heart.getLast() || 0
      this.noteHeart(this.state.hr)
      this.heart.onCurrentChange(this.onHeart)
    } catch (e) {
      this.heart = null
    }
  },

  /**
   * Próbka do wykresu, nie częściej niż co pięć sekund.
   *
   * Czujnik potrafi odezwać się kilka razy na sekundę; bez przerzedzenia
   * dwadzieścia cztery słupki pokrywałyby pół minuty i wykres nie mówiłby
   * nic o przebiegu serii.
   */
  /**
   * Spalanie naliczane z tętna, kawałek po kawałku.
   *
   * Czujnik kalorii zegarka podaje dzienny licznik aktywności - napędzają
   * go kroki, a przy sztandze kroków nie ma. Po godzinie ciężkiej pracy
   * pokazywał kilkanaście kalorii, podczas gdy natywny trening na tym samym
   * zegarku naliczał kilkaset. Telefon przysyła więc prostą zależność od
   * tętna, a my mnożymy ją przez czas, który faktycznie upłynął.
   *
   * Mnożymy przez odstęp między próbkami, nie przez stałą: aplikacja bywa
   * ubita przy zgaszonym ekranie i wtedy próbki przychodzą rzadziej.
   */
  addBurn(bpm) {
    const b = this.state.burn
    const now = Date.now()
    if (!b) { this.state.burnAt = now; return }
    const poprzednio = this.state.burnAt
    this.state.burnAt = now
    if (!poprzednio) return

    // Dłuższe dziury to czas, w którym nikt nie patrzył - doliczanie ich
    // w całości zmyślałoby kalorie z martwego okresu.
    const minuty = Math.min(now - poprzednio, BURN_MAX_GAP_MS) / 60000
    const naMinute = b.k0 + b.k1 * bpm
    if (naMinute <= 0) return
    this.state.burnKcal += naMinute * minuty
  },

  noteHeart(bpm) {
    if (!bpm || bpm <= 0) return
    this.addBurn(bpm)
    const now = Date.now()
    if (this.lastHrLog && now - this.lastHrLog < BAR_EVERY_MS) return
    this.lastHrLog = now
    const log = this.state.hrLog
    log.push(bpm)
    if (log.length > BARS) log.shift()

    // Zakres liczony osobno, dla całej sesji. Wcześniej min i max brały
    // się z samego wykresu, czyli z ostatnich dwóch minut - i po serii
    // "minimum" wychodziło powyżej stu, choć w przerwie schodziło niżej.
    if (!this.state.hrMin || bpm < this.state.hrMin) this.state.hrMin = bpm
    if (bpm > this.state.hrMax) this.state.hrMax = bpm
    if (this.state.view === VIEW_HEART) this.paintHeart()
  },

  /**
   * Kalorie tej sesji, nie całego dnia.
   *
   * Czujnik podaje sumę od północy, więc zapamiętujemy odczyt z chwili,
   * w której zegarek zobaczył trening, i pokazujemy różnicę. Otwarcie
   * aplikacji w połowie treningu liczy dopiero od tamtego momentu -
   * wcześniejszych kalorii nie da się odzyskać, bo nikt ich wtedy nie
   * obserwował.
   */
  startCalorie() {
    // Osobne próby, bo to osobne ryzyka. Wcześniej jeden wspólny `try`
    // obejmował konstruktor i nasłuch; wywrotka nasłuchu zabijała czujnik
    // na dobre, choć sam odczyt mógł działać.
    try {
      this.calorie = new Calorie()
    } catch (e) {
      this.calorie = null
      this.state.kcalFail = true
      return
    }

    // Nasłuch jest dodatkiem, nie podstawą: odczyt i tak chodzi z zegara.
    // Opieranie się na samym wywołaniu zwrotnym znaczyło, że gdy nie przyjdzie,
    // licznik stoi na zerze i nie ma po czym poznać, że cokolwiek się zepsuło.
    try {
      this.onCalorie = () => this.readCalorie()
      this.calorie.onChange(this.onCalorie)
    } catch (e) {
      this.onCalorie = null
    }

    this.readCalorie()
  },

  readCalorie() {
    if (!this.calorie) return
    let total = 0
    try {
      total = this.calorie.getCurrent() || 0
    } catch (e) {
      this.state.kcalFail = true
      this.calorie = null
      return
    }
    // Po północy licznik dnia wraca do zera, więc baza nie może zostać
    // wyżej niż sam odczyt - inaczej trening po dwudziestej czwartej
    // pokazywałby wartość ujemną.
    if (this.state.kcalBase === null || total < this.state.kcalBase) {
      this.state.kcalBase = total
    }
    this.state.kcalTotal = total
    if (total > 0) this.state.kcalSeen = true
    this.state.kcal = total - this.state.kcalBase
    if (this.state.view === VIEW_STATS) this.paintStats()
  },

  // ---- Zegar serii i przerwy ---------------------------------------------

  /** Godzina nad karuzelą, w miejscu dawnych kropek. */
  paintWallClock() {
    if (!this.w || !this.w.clock) return
    const t = new Date(Date.now())
    const dwie = (n) => (n < 10 ? '0' : '') + n
    const teraz = dwie(t.getHours()) + ':' + dwie(t.getMinutes())
    // Pisanie do widgetu co sekundę, gdy minuta się nie zmieniła, to
    // tylko praca dla ekranu - a ten tyk chodzi przez cały trening.
    if (this.lastWallClock === teraz) return
    this.lastWallClock = teraz
    this.setText(this.w.clock, teraz)
  },

  /** Jedno tyknięcie na sekundę, odświeżające wyłącznie napisy z czasem. */
  startClock() {
    const tick = () => {
      if (this.state.built && !this.state.message) {
        if (COLUMN.indexOf(this.state.view) >= 0) this.paintWallClock()
        if (this.state.view === VIEW_SETS) this.paintClock()
        else if (this.state.view === VIEW_STATS) this.paintStats()
        else if (this.state.view === VIEW_CONTROL) this.paintControlClock()
      }
      // Zapis stanu rzadziej niz tyk: czesciej byloby pisaniem do pamieci
      // co sekunde bez powodu, a i tak zapisujemy przy kazdej akcji.
      this.tickCount = (this.tickCount || 0) + 1
      // Kalorie same z siebie nie przychodzą - czujnik odpytujemy.
      if (this.tickCount % 10 === 0) this.readCalorie()
      // Zapis stanu z zegara tylko przez kolejke zapisow, czyli w ciszy.
      // Dotad szedl wprost co piętnaście sekund, a tetno i plan co pol
      // minuty - niezaleznie od tego, czy palec akurat cos naciskal.
      if (this.tickCount % 30 === 0 && this.state.sessionId) this.remember()
      if (this.tickCount % 60 === 0 && this.state.sessionId) this.rememberHeavyLater()
      // Na ekranie wyboru odswiezamy liste jednostek. Plan zmienia sie na
      // telefonie - skasowany trening przestawia kolejnosc - a zegarek
      // pokazywal to, co zapamietal przy ostatnim polaczeniu. Co dwie
      // minuty, nie co pol: odpowiedz to kilkanascie kilobajtow.
      if (this.tickCount % 120 === 0 && this.state.view === VIEW_START &&
        !this.state.message) {
        this.askUnits()
      }
      // Zegar tylko pilnuje, żeby kolejka miała umówioną próbę. Samo
      // ponawianie chodzi własnym rytmem, z rosnącym odstępem.
      if (this.tickCount % 5 === 0 && (this.state.outbox || []).length) {
        this.scheduleDrain(this.drainWait || 0)
      }
      this.clockTimer = setTimeout(tick, 1000)
    }
    tick()
  },

  /**
   * Czas treningu liczony lokalnie od ostatniego odczytu.
   *
   * Telefon przysyła sekundy przy każdej odpowiedzi, ale odpowiedzi
   * przychodzą tylko przy akcjach - między seriami minuta potrafi stać
   * w miejscu. Doliczamy to, co upłynęło od tamtej chwili, a przy
   * wstrzymaniu nie doliczamy nic, bo telefon też wtedy nie liczy.
   */
  sessionSeconds() {
    if (this.paused()) return this.state.sessionSec
    return this.state.sessionSec + (Date.now() - this.state.sessionAt) / 1000
  },

  /**
   * Czy trening jest wstrzymany.
   *
   * Pierwszeństwo ma decyzja podjęta na zegarku. Wcześniej jedynym źródłem
   * był telefon, więc przycisk reagował dopiero po przyjściu odpowiedzi -
   * stąd zwłoka - a bez łączności nie reagował wcale i trening zostawał
   * wstrzymany na zawsze, mimo że szło się dalej ćwiczyć.
   */
  paused() {
    const lokalnie = this.state.pausedLocal
    if (lokalnie === true || lokalnie === false) return lokalnie
    return !!(this.state.data && this.state.data.paused)
  },

  /** Liczba bez ogona zmiennoprzecinkowego: 82.30000000000001 nie wchodzi. */
  num(value) {
    const v = Math.round(Number(value) * 100) / 100
    if (!isFinite(v)) return '0'
    return String(v)
  },

  clock(seconds) {
    const s = Math.max(0, Math.floor(seconds))
    const m = Math.floor(s / 60)
    const rest = s % 60
    return m + ':' + (rest < 10 ? '0' + rest : rest)
  },

  /**
   * Zegar fazy: osobno słowo, osobno czas.
   *
   * Jeden napis "PRZERWA 0:19 / 3:00" nie mieścił się w linijce i zawijał
   * na drugą, która wchodziła pod spód na ciężar. Słowo jest teraz małe
   * i nad czasem, więc sam czas może zostać duży.
   *
   * W trakcie serii słowa nie ma wcale: nad zegarem stoi już "SERIA 3 z 3",
   * więc powtarzanie go nic nie wnosi. Przy przerwie zostaje, bo tego
   * licznik nad spodem nie mówi.
   */
  paintClock() {
    const resting = this.state.phase === PHASE_REST
    const since = (Date.now() - this.state.phaseSince) / 1000
    const ex = this.current()

    const paused = this.paused()
    this.setText(this.w.phase, paused ? 'WSTRZYMANY' : (resting ? 'PRZERWA' : ''))

    let value = this.clock(since)
    if (resting && ex && ex.restSec > 0) {
      value += ' / ' + this.clock(ex.restSec)
      // Koniec zaplanowanej przerwy w nadgarstku - po to, żeby nie trzeba
      // było pilnować ekranu między seriami.
      if (!this.restRang && since >= ex.restSec) {
        this.restRang = true
        this.buzz()
      }
    }
    this.setText(this.w.timer, value)
    this.kolor(this.w.timer, resting ? AMBER : ACCENT)
  },

  // ---- Budowa, raz -------------------------------------------------------

  text(x, y, w, h, size, color, style, align) {
    return hmUI.createWidget(hmUI.widget.TEXT, {
      x: px(x), y: px(y), w: px(w), h: px(h),
      color: color,
      text_size: px(size),
      align_h: align === undefined ? hmUI.align.CENTER_H : align,
      align_v: hmUI.align.CENTER_V,
      text_style: style === undefined ? hmUI.text_style.WRAP : style,
      text: ''
    })
  },

  button(x, y, w, h, size, label, color, onClick) {
    // Komplet właściwości zostaje przy widgecie, bo przycisk trzeba czasem
    // odtworzyć w całości - patrz [setBtn].
    const props = {
      x: px(x), y: px(y), w: px(w), h: px(h),
      text: label,
      color: FG,
      text_size: px(size),
      radius: px(6),
      normal_color: color,
      press_color: ACCENT
    }
    const widget = hmUI.createWidget(
      hmUI.widget.BUTTON,
      Object.assign({ click_func: () => { this.dotyk(); onClick() } }, props)
    )
    widget.props = props
    return widget
  },

  /**
   * Zmiana przycisku po utworzeniu.
   *
   * Przycisk wymaga, żeby przy `setProperty` podać razem z nową wartością
   * także `x`, `y`, `w` i `h` - bez nich zmiana nie dochodzi. Kolor
   * tekstu nazywa się tu `color`. Wysyłamy więc za każdym razem komplet.
   */
  setBtn(widget, changes) {
    if (!widget || !widget.props) return
    // Bez zmiany - bez zapisu. Komplet wlasciwosci szedl dotad przy kazdym
    // malowaniu, a zapis przycisku to jego przerysowanie od nowa.
    let zmiana = false
    for (const key in changes) {
      if (widget.props[key] !== changes[key]) {
        widget.props[key] = changes[key]
        zmiana = true
      }
    }
    if (!zmiana) return
    widget.setProperty(hmUI.prop.MORE, widget.props)
  },

  /** Przyciski widoczne w tej chwili - tylko one wymagają odmalowania. */
  visibleButtons() {
    const w = this.w
    if (!w) return []
    if (this.state.message) return [w.retry]
    switch (this.state.view) {
      case VIEW_SETS: return this.askingRir()
        ? [w.count].concat(w.rirRow)
        : [w.count, w.prev, w.next]
      case VIEW_EDIT: return [w.wMinus, w.wPlus, w.rMinus, w.rPlus]
      case VIEW_DONE: return [w.doneBtn]
      case VIEW_GAPS: return w.gapsRows.slice(0, this.gapsRows().length)
      case VIEW_LOG:
        return w.logRows.slice(0, this.logRows().length - this.logFrom())
      case VIEW_FIX: return [w.fixWMinus, w.fixWPlus, w.fixRMinus, w.fixRPlus]
        .concat(w.fixRirRow).concat([w.fixSave, w.fixDelete])
      case VIEW_CONTROL: return [w.ctrlPause, w.ctrlSkip, w.ctrlEnd]
      case VIEW_START: return w.units.slice(0, (this.state.units || []).length)
      default: return []
    }
  },

  buildWidgets() {
    const w = {}

    w.message = this.text(60, 170, 360, 140, 30, DIM)
    w.retry = this.button(140, 330, 200, 72, 26, 'Odśwież', GREY, () => {
      this.setMessage('Łączę...')
      this.send('STATE', {}, true)
    })

    // ---- Godzina nad karuzelą ----
    // Były tu kropki pokazujące, który z trzech ekranów jest na wierzchu.
    // Przewija się je gestem i tak się je pamięta, a godziny w trakcie
    // treningu nie ma gdzie indziej sprawdzić bez wychodzenia z aplikacji.
    w.clock = this.text(170, 8, 140, 32, 25, DIM, hmUI.text_style.NONE)

    // ---- Widok: serie ----
    // Serce, tętno i jednostka w jednej linii. Wolne pole po dawnym
    // podpisie poszło w rozmiar liczby - to ją widać kątem oka.
    //
    // Glif serca ma wyższe wydłużenia niż litery, więc dostaje własne,
    // wyższe pole; w niskim obcinało mu rogi. Liczba jest dosunięta do
    // lewej, żeby nie skakała przy zmianie z dwóch cyfr na trzy, a cały
    // rząd ustawiony pod tętno trzycyfrowe, czyli to robocze.
    // Liczba zajmuje całą szerokość i jest wyśrodkowana, więc stoi
    // dokładnie na środku bez względu na to, czy ma dwie cyfry czy trzy.
    // Serce dosuwa się do niej z lewej - [placeHeart] liczy, gdzie ta
    // lewa krawędź wypadła. Glif serca ma wyższe wydłużenia niż cyfry,
    // więc dostaje własne, wyższe pole; w niskim obcinało mu rogi.
    w.hr = this.text(0, 42, 480, 108, HR_SIZE, FG, hmUI.text_style.NONE)
    w.hrIcon = this.text(60, 66, 60, 58, 32, HEART, hmUI.text_style.NONE, hmUI.align.RIGHT)
    w.name = this.text(NAME_X, NAME_Y, NAME_BOX_W, NAME_H, NAME_SIZE, DIM, hmUI.text_style.NONE)
    w.count = this.button(90, 212, 300, 54, 29, '', 0x000000, () => this.press())
    w.phase = this.text(120, 272, 240, 24, 17, AMBER, hmUI.text_style.NONE)
    w.timer = this.text(80, 294, 320, 52, 40, ACCENT, hmUI.text_style.NONE)
    w.load = this.text(80, 348, 320, 28, 20, DIM, hmUI.text_style.NONE)
    w.prev = this.button(120, 380, 90, 58, 28, '<', GREY, () => { this.move(-1) })
    w.next = this.button(270, 380, 90, 58, 28, '>', GREY, () => { this.move(1) })

    // Zapas powtórzeń pytamy w przerwie, w miejscu strzałek: wtedy jest
    // na to chwila, a seria jest jeszcze świeża w pamięci. Pominięcie
    // niczego nie psuje - reguły progresji działają też bez tego.
    w.rirRow = []
    const etykiety = ['0', '1', '2', '3+']
    for (let i = 0; i < 4; i++) {
      const btn = this.button(
        RIR_X0 + i * (RIR_W + RIR_GAP), RIR_Y, RIR_W, RIR_H, 24,
        etykiety[i], GREY, () => this.setRir(i)
      )
      btn.setProperty(hmUI.prop.VISIBLE, false)
      w.rirRow.push(btn)
    }

    // ---- Widok: czas treningu ----
    w.statTime = this.text(80, 74, 320, 76, 58, FG, hmUI.text_style.NONE)
    w.statTimeLabel = this.text(100, 152, 280, 26, 18, DIM, hmUI.text_style.NONE)
    w.statSets = this.text(80, 188, 320, 62, 46, FG, hmUI.text_style.NONE)
    w.statSetsLabel = this.text(100, 250, 280, 24, 17, DIM, hmUI.text_style.NONE)
    w.statKcal = this.text(80, 278, 320, 62, 46, FG, hmUI.text_style.NONE)
    w.statKcalLabel = this.text(100, 340, 280, 24, 17, DIM, hmUI.text_style.NONE)
    w.statVolume = this.text(70, 372, 340, 28, 19, DIM, hmUI.text_style.NONE)
    // Stan synchronizacji. Ekran czasu jest tym, na ktory zaglada sie
    // w przerwie, wiec tu ma stac odpowiedz na pytanie "czy to w ogole
    // dojezdza do telefonu".
    w.statSync = this.text(70, 404, 340, 26, 17, DIM, hmUI.text_style.NONE)

    // ---- Widok: tętno ----
    w.heartLabel = this.text(140, 68, 200, 34, 24, DIM, hmUI.text_style.NONE)
    w.heartValue = this.text(100, 104, 280, 88, 68, FG, hmUI.text_style.NONE)
    w.bars = []
    for (let i = 0; i < BARS; i++) {
      const bar = hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: px(BAR_X0 + i * (BAR_W + BAR_GAP)), y: px(BAR_BASE - 4),
        w: px(BAR_W), h: px(4), radius: px(2), color: HEART
      })
      bar.setProperty(hmUI.prop.VISIBLE, false)
      w.bars.push(bar)
    }
    w.heartRange = this.text(70, 372, 340, 28, 19, DIM, hmUI.text_style.NONE)

    // ---- Widok: ciężar i powtórzenia ----
    //
    // Cały blok wyśrodkowany w tarczy: od EDIT_TOP do EDIT_TOP + EDIT_H,
    // z tym samym marginesem u góry i u dołu. Wcześniej zaczynał się na
    // 56 i kończył na 316, więc nad nim zostawało 56 pikseli, a pod nim
    // 164 - na okrągłym ekranie widać to od razu, bo dół tarczy świeci pustką.
    w.wLabel = this.text(100, EDIT_TOP, 280, 34, 24, DIM)
    // Plusy i minusy bez wibracji: naciska się je po kilka razy z rzędu,
    // a wtedy potwierdzanie każdego kliknięcia robi się męczące.
    w.wMinus = this.button(86, EDIT_TOP + 40, 84, 84, 36, '-', GREY, () => this.stepWeight(-1))
    // Liczba w pionie na środku przycisków: 84 i 56 wysokości to różnica
    // 28, czyli 14 na stronę. Wcześniej stała dwa piksele niżej.
    w.wValue = this.text(170, EDIT_TOP + 54, 140, 56, 36, FG, hmUI.text_style.NONE)
    w.wPlus = this.button(310, EDIT_TOP + 40, 84, 84, 36, '+', GREY, () => this.stepWeight(1))

    w.rLabel = this.text(100, EDIT_TOP + 140, 280, 34, 24, DIM)
    w.rMinus = this.button(86, EDIT_TOP + 176, 84, 84, 36, '-', GREY, () => this.stepReps(-1))
    w.rValue = this.text(170, EDIT_TOP + 190, 140, 56, 36, FG, hmUI.text_style.NONE)
    w.rPlus = this.button(310, EDIT_TOP + 176, 84, 84, 36, '+', GREY, () => this.stepReps(1))

    // Bez przycisków od serii. Dokładanie serii robi się na ekranie ćwiczenia,
    // a kasowanie - w poprawce konkretnej serii, gdzie widać, którą się rusza.
    // Tutaj ustawia się ciężar i powtórzenia, i tyle.

    // ---- Widok: podsumowanie ----
    // Jedna linijka, bez zawijania. Lista nazw mieszkała tu wcześniej
    // i przy pięciu pominięciach rozpychała się na trzy wiersze, wchodząc
    // w tonaż i w przycisk. Nazwy są teraz na osobnym ekranie.
    w.doneTitle = this.text(90, 44, 300, 38, 27, FG, hmUI.text_style.NONE)
    w.doneTime = this.text(90, 84, 300, 46, 36, ACCENT, hmUI.text_style.NONE)
    w.doneSets = this.text(80, 134, 320, 30, 21, DIM, hmUI.text_style.NONE)
    w.doneTon = this.text(80, 166, 320, 30, 21, DIM, hmUI.text_style.NONE)
    w.doneSkip = this.text(60, 200, 360, 30, 19, AMBER, hmUI.text_style.NONE)
    w.doneBtn = this.button(80, 244, 320, 92, 29, 'ZAKOŃCZ', GREEN, () => this.finishWorkout())
    w.doneHint = this.text(90, 342, 300, 28, 17, DIM, hmUI.text_style.NONE)
    w.doneMore = this.text(90, 374, 300, 28, 17, AMBER, hmUI.text_style.NONE)
    w.doneNext = this.text(70, 402, 340, 28, 17, ACCENT, hmUI.text_style.NONE)

    // ---- Widok: zapisane serie ----
    w.logTitle = this.text(70, 30, 340, 34, 23, DIM, hmUI.text_style.NONE)
    w.logRows = []
    for (let i = 0; i < LOG_ROWS; i++) {
      // Węższe niż dotąd o dwadzieścia pikseli z każdej strony: przy 50
      // i szerokości 380 górny wiersz wychodził rogami poza okrąg tarczy
      // i wyglądał na przycięty, bo był.
      const row = this.button(
        70, LOG_TOP + i * LOG_ROW_GAP, 340, LOG_ROW_H, 26, '', 0x1c2127,
        () => this.openFix(i)
      )
      row.setProperty(hmUI.prop.VISIBLE, false)
      w.logRows.push(row)
    }
    w.logHint = this.text(80, 398, 320, 30, 17, DIM, hmUI.text_style.NONE)

    // ---- Widok: poprawka serii ----
    w.fixTitle = this.text(70, 20, 340, 30, 22, DIM, hmUI.text_style.NONE)
    w.fixWLabel = this.text(100, 52, 280, 24, 19, DIM, hmUI.text_style.NONE)
    w.fixWMinus = this.button(86, 76, 84, 64, 32, '-', GREY, () => this.fixStep('w', -1))
    w.fixWValue = this.text(170, 84, 140, 48, 32, FG, hmUI.text_style.NONE)
    w.fixWPlus = this.button(310, 76, 84, 64, 32, '+', GREY, () => this.fixStep('w', 1))
    w.fixRLabel = this.text(100, 146, 280, 24, 19, DIM, hmUI.text_style.NONE)
    w.fixRMinus = this.button(86, 170, 84, 64, 32, '-', GREY, () => this.fixStep('r', -1))
    w.fixRValue = this.text(170, 178, 140, 48, 32, FG, hmUI.text_style.NONE)
    w.fixRPlus = this.button(310, 170, 84, 64, 32, '+', GREY, () => this.fixStep('r', 1))

    // Zapas powtórzeń tym samym rzędem co po serii - ta sama rzecz ma
    // wyglądać tak samo, niezależnie od tego, kiedy się ją podaje.
    w.fixRirLabel = this.text(100, 240, 280, 22, 17, DIM, hmUI.text_style.NONE)
    w.fixRirRow = []
    const zapasy = ['0', '1', '2', '3+']
    for (let i = 0; i < 4; i++) {
      const btn = this.button(
        88 + i * 78, 266, 70, 50, 22, zapasy[i], GREY, () => this.fixSetRir(i)
      )
      btn.setProperty(hmUI.prop.VISIBLE, false)
      w.fixRirRow.push(btn)
    }
    // Zapas powtórzeń siedzi tu, a nie w osobnym rzędzie: na okrągłym
    // ekranie nie ma już miejsca w pionie, a i tak poprawia się go razem
    // z ciężarem i powtórzeniami tej samej serii. Jeden przycisk, który
    // kręci się w kółko - bez niego zapas dało się podać tylko w przerwie
    // tuż po serii, a przy poprawce zapisanej serii nie dało się wcale.

    w.fixSave = this.button(96, 328, 142, 60, 22, 'zapisz', GREEN, () => this.saveFix())
    // Kasowanie na dwa naciśnięcia. Jedno przypadkowe dotknięcie nie może
    // zabrać serii, a wyjście z ekranu i tak jest pod gestem.
    w.fixDelete = this.button(246, 328, 142, 60, 22, 'usuń', 0x3a1f1f, () => this.fixDelete())

    // ---- Widok: co pominięte ----
    w.gapsTitle = this.text(70, 30, 340, 36, 24, FG, hmUI.text_style.NONE)
    w.gapsSub = this.text(70, 66, 340, 28, 18, DIM, hmUI.text_style.NONE)
    // Wiersze są przyciskami: z listy braków skaczesz wprost na ćwiczenie,
    // zamiast wracać i szukać go strzałkami.
    w.gapsRows = []
    for (let i = 0; i < GAP_ROWS; i++) {
      const row = this.button(50, 100 + i * 48, 380, 42, 20, '', 0x1c2127, () => this.openGap(i))
      row.setProperty(hmUI.prop.VISIBLE, false)
      w.gapsRows.push(row)
    }
    w.gapsHint = this.text(90, 390, 300, 30, 17, DIM, hmUI.text_style.NONE)

    // ---- Widok: tonaż po ćwiczeniach ----
    w.tonTitle = this.text(70, 30, 340, 36, 24, FG, hmUI.text_style.NONE)
    w.tonSub = this.text(70, 66, 340, 28, 18, DIM, hmUI.text_style.NONE)
    w.tonRows = []
    for (let i = 0; i < TON_ROWS; i++) {
      w.tonRows.push(this.text(50, 100 + i * 48, 380, 42, 20, FG, hmUI.text_style.NONE))
    }
    w.tonHint = this.text(90, 390, 300, 30, 17, DIM, hmUI.text_style.NONE)

    // ---- Widok: sterowanie ----
    w.ctrlTime = this.text(90, 52, 300, 64, 44, FG, hmUI.text_style.NONE)
    w.ctrlState = this.text(90, 118, 300, 32, 21, DIM, hmUI.text_style.NONE)
    w.ctrlPause = this.button(100, 156, 280, 78, 27, 'WSTRZYMAJ', GREY, () => { this.togglePause() })
    w.ctrlSkip = this.button(100, 244, 280, 78, 27, 'POMIŃ ĆWICZENIE', GREY, () => { this.skipExercise() })
    w.ctrlEnd = this.button(100, 332, 280, 78, 27, 'KONIEC', GREEN, () => { this.showView(VIEW_DONE) })
    w.ctrlHint = this.text(110, 416, 260, 36, 19, DIM)

    // ---- Widok: start treningu ----
    w.startTitle = this.text(70, 44, 340, 44, 28, DIM)
    w.units = []
    for (let i = 0; i < 4; i++) {
      const btn = this.button(70, 104 + i * 80, 340, 72, 26, '', GREY, () => { this.startUnit(i) })
      btn.setProperty(hmUI.prop.VISIBLE, false)
      w.units.push(btn)
    }

    this.w = w
    this.state.built = true
    this.setText(w.hrIcon, '♥')
    this.paintHeartValue()
  },

  // ---- Widoczność i przejścia --------------------------------------------

  /**
   * Widoczność - ale tylko wtedy, gdy się zmienia.
   *
   * Każdy zapis idzie do warstwy natywnej. Jedno przejście między ekranami
   * robiło ich ponad sto, z czego prawie wszystkie ustawiały to, co już
   * było - a gest złożony w ich trakcie przepadał, bo wątek był zajęty.
   */
  setVisible(widget, visible) {
    if (!widget) return
    const chciane = !!visible
    if (widget.widoczny === chciane) return
    widget.widoczny = chciane
    widget.setProperty(hmUI.prop.VISIBLE, chciane)
  },

  /** Kolor napisu - tak samo, tylko gdy się zmienia. */
  kolor(widget, color) {
    if (!widget || widget.barwa === color) return
    widget.barwa = color
    widget.setProperty(hmUI.prop.MORE, { color: color })
  },

  /** Napis - tak samo, tylko gdy się zmienia. */
  setText(widget, value) {
    if (!widget) return
    const tekst = String(value)
    if (widget.tekst === tekst) return
    widget.tekst = tekst
    widget.setProperty(hmUI.prop.MORE, { text: tekst })
  },

  /**
   * Zmiana ekranu - od razu, bez animacji.
   *
   * Byla tu czarna zaslona zjezdzajaca z ekranu w szescdziesieciu klatkach
   * na sekunde. Pelnoekranowy prostokat w ruchu to przerysowanie calej
   * tarczy w kazdej klatce, a po nim jeszcze odbudowa wszystkich przyciskow -
   * cwierc sekundy, w ktorej palec nie byl slyszany. Przy kilku przejsciach
   * na serie to wlasnie bylo czuc jako lag.
   */
  showView(view) {
    this.state.view = view
    this.applyVisibility()
    this.paint()
    if (view === VIEW_DONE || view === VIEW_CONTROL) this.send('STATE', {}, false)
  },

  /**
   * Potwierdzenie kolorem: tlo licznika serii na chwile zmienia barwe.
   *
   * Byl tu blysk na caly ekran, gasnacy w animacji przezroczystosci. Ten sam
   * problem co z zaslona przejscia: kazda klatka to cala tarcza od nowa,
   * a dluzszy blysk trwal prawie sekunde - akurat wtedy, gdy przychodzi
   * kolejne klikniecie. Kolor wciaz mowi, co weszlo, a kosztuje dwa zapisy.
   */
  flash(color, ms) {
    const btn = this.w && this.w.count
    if (!btn) return
    if (this.flashTimer) clearTimeout(this.flashTimer)
    this.setBtn(btn, { normal_color: color })
    this.flashTimer = setTimeout(() => {
      this.flashTimer = null
      this.setBtn(btn, { normal_color: 0x000000 })
    }, ms)
  },

  setMessage(text) {
    this.state.message = text
    this.applyVisibility()
    if (text) this.setText(this.w.message, text)
  },

  applyVisibility() {
    const w = this.w
    if (!w) return
    const msg = !!this.state.message
    const view = this.state.view
    const sets = !msg && view === VIEW_SETS
    const edit = !msg && view === VIEW_EDIT
    const done = !msg && view === VIEW_DONE
    const ctrl = !msg && view === VIEW_CONTROL
    const start = !msg && view === VIEW_START
    const stats = !msg && view === VIEW_STATS
    const heart = !msg && view === VIEW_HEART
    const tons = !msg && view === VIEW_TONS
    const column = COLUMN.indexOf(view)

    this.setVisible(w.message, msg)
    this.setVisible(w.retry, msg)

    this.setVisible(w.clock, !msg && column >= 0)
    if (!msg && column >= 0) this.paintWallClock()

    // W przerwie strzałki ustępują miejsca pytaniu o zapas powtórzeń.
    const pytamy = sets && this.askingRir()
    ;[w.hr, w.hrIcon, w.name, w.count, w.phase, w.timer, w.load]
      .forEach((x) => this.setVisible(x, sets))
    ;[w.prev, w.next].forEach((x) => this.setVisible(x, sets && !pytamy))
    w.rirRow.forEach((x) => this.setVisible(x, pytamy))
    ;[w.statTime, w.statTimeLabel, w.statSets, w.statSetsLabel,
      w.statKcal, w.statKcalLabel, w.statVolume, w.statSync]
      .forEach((x) => this.setVisible(x, stats))
    ;[w.heartLabel, w.heartValue, w.heartRange]
      .forEach((x) => this.setVisible(x, heart))
    const samples = this.state.hrLog.length
    w.bars.forEach((bar, i) => this.setVisible(bar, heart && i < samples))
    ;[w.wLabel, w.wMinus, w.wValue, w.wPlus,
      w.rLabel, w.rMinus, w.rValue, w.rPlus].forEach((x) => this.setVisible(x, edit))
    ;[w.doneTitle, w.doneTime, w.doneSets, w.doneTon, w.doneSkip, w.doneBtn,
      w.doneHint, w.doneMore, w.doneNext].forEach((x) => this.setVisible(x, done))
    const gaps = !msg && view === VIEW_GAPS
    const log = !msg && view === VIEW_LOG
    const fix = !msg && view === VIEW_FIX
    ;[w.logTitle, w.logHint].forEach((x) => this.setVisible(x, log))
    const wiersze = this.logRows()
    const odWiersza = this.logFrom()
    w.logRows.forEach((row, i) =>
      this.setVisible(row, log && odWiersza + i < wiersze.length))
    ;[w.fixTitle, w.fixWLabel, w.fixWMinus, w.fixWValue, w.fixWPlus,
      w.fixRLabel, w.fixRMinus, w.fixRValue, w.fixRPlus,
      w.fixRirLabel, w.fixSave, w.fixDelete].forEach((x) => this.setVisible(x, fix))
    w.fixRirRow.forEach((x) => this.setVisible(x, fix))
    ;[w.gapsTitle, w.gapsSub, w.gapsHint].forEach((x) => this.setVisible(x, gaps))
    ;[w.tonTitle, w.tonSub, w.tonHint].forEach((x) => this.setVisible(x, tons))
    const wTon = this.tonRows()
    w.tonRows.forEach((row, i) => this.setVisible(row, tons && i < wTon.length))
    const rows = this.gapsRows()
    w.gapsRows.forEach((row, i) => this.setVisible(row, gaps && i < rows.length))
    ;[w.ctrlTime, w.ctrlState, w.ctrlPause, w.ctrlSkip, w.ctrlEnd, w.ctrlHint]
      .forEach((x) => this.setVisible(x, ctrl))
    this.setVisible(w.startTitle, start)
    const units = this.state.units || []
    w.units.forEach((btn, i) => this.setVisible(btn, start && i < units.length))
  },

  // ---- Stan lokalny ------------------------------------------------------

  current() {
    const list = (this.state.data && this.state.data.exercises) || []
    return list[this.state.index] || null
  },

  /**
   * Trening tak, jak go widac na zegarku: stan od telefonu plus wszystko,
   * co jeszcze czeka w kolejce.
   *
   * Dotad liczba serii brala sie z osobnego licznika niepotwierdzonych,
   * a dziennik serii - wylacznie z odpowiedzi telefonu. Te dwie rzeczy
   * rozjezdzaly sie przy kazdym opoznieniu: ekran cwiczenia mowil
   * "zrobione 3 z 3", a dziennik pokazywal dwie serie, bez zapasu podanego
   * do drugiej, a przy innych cwiczeniach - nic. Poprawka serii wracala do
   * starych liczb, bo dziennik czekal na telefon.
   *
   * Teraz jedno i drugie wynika z tego samego: z ostatniego stanu telefonu
   * i z polecen, ktore jeszcze do niego nie doszly. Liczone raz na zmiane
   * stanu albo kolejki, nie przy kazdym malowaniu.
   */
  nakladka() {
    const data = this.state.data
    const outbox = this.state.outbox || []
    const n = this.nakl
    if (n && n.d === data && n.o === outbox && n.v === (this.wersja || 0)) return n

    const ex = {}
    ;((data && data.exercises) || []).forEach((e) => {
      ex[e.id] = {
        done: e.done || 0,
        entries: (e.entries || []).map((w) => ({
          id: w.id, ref: w.ref, weight: w.weight, reps: w.reps,
          rir: w.rir === undefined || w.rir === null ? -1 : w.rir,
          warmup: !!w.warmup
        }))
      }
    })
    const sid = this.state.sessionId
    outbox.forEach((poz) => {
      const p = poz.p || {}
      // Zaleglosc z innego treningu nie nalezy do tego, ktory widac.
      if (p.sid && sid && String(p.sid) !== String(sid)) return
      this.nalozOp(ex, poz.m, p, poz.cid, null, true)
    })

    let sets = 0
    let reps = 0
    let ton = 0
    // Plan wykonany - liczony tutaj, nie brany z telefonu. Odpowiedz samym
    // potwierdzeniem flagi nie niesie, a stan sprzed niej by klamal.
    const lista = (data && data.exercises) || []
    const complete = lista.length > 0 &&
      lista.every((e) => ex[e.id] && ex[e.id].done >= (e.sets || 0))
    for (const id in ex) {
      ex[id].entries.forEach((w) => {
        if (w.warmup) return
        sets += 1
        reps += w.reps || 0
        ton += (w.weight || 0) * (w.reps || 0)
      })
    }
    const wynik = {
      d: data, o: outbox, v: this.wersja || 0, ex: ex,
      totals: {
        sets: sets, reps: reps, tonnage: ton,
        complete: complete
      }
    }
    this.nakl = wynik
    return wynik
  },

  /** Kolejka albo stan zmienione w miejscu - widok trzeba policzyc od nowa. */
  zmienione() {
    this.wersja = (this.wersja || 0) + 1
  },

  /**
   * Jedno polecenie nalozone na serie cwiczen.
   *
   * Ta sama funkcja liczy widok z kolejki i wpisuje do stanu polecenia juz
   * potwierdzone - dzieki temu seria wyglada tak samo przed wyslaniem i po.
   *
   * @param ex mapa id cwiczenia -> { done, entries }
   * @param setId numer od telefonu, gdy polecenie SET jest juz potwierdzone
   * @param czeka czy seria jeszcze nie doszla - wiersz dziennika to pokazuje
   */
  nalozOp(ex, m, p, cid, setId, czeka) {
    const znajdz = () => {
      for (const id in ex) {
        const lista = ex[id].entries
        for (let i = 0; i < lista.length; i++) {
          const w = lista[i]
          if ((p.setId && w.id === p.setId) || (p.ref && w.ref === p.ref)) {
            return { e: ex[id], i: i }
          }
        }
      }
      return null
    }
    if (m === 'SET') {
      const e = ex[p.exerciseId]
      if (!e) return
      const w = {
        weight: p.weight, reps: p.reps,
        rir: p.rir === undefined || p.rir === null ? -1 : p.rir,
        warmup: false
      }
      if (setId) w.id = setId
      else w.ref = cid
      if (czeka) w.czeka = true
      e.entries.push(w)
      e.done += 1
      return
    }
    if (m === 'EDIT') {
      const f = znajdz()
      if (!f) return
      const w = f.e.entries[f.i]
      if (p.weight !== undefined) w.weight = p.weight
      if (p.reps !== undefined) w.reps = p.reps
      if (p.rir !== undefined) w.rir = p.rir
      return
    }
    if (m === 'RIR') {
      let f = znajdz()
      if (!f) {
        // Starszy zapis bez wskazania serii - telefon wezmie ostatnia.
        const e = ex[p.exerciseId]
        if (!e || !e.entries.length) return
        f = { e: e, i: e.entries.length - 1 }
      }
      f.e.entries[f.i].rir = p.rir === undefined ? -1 : p.rir
      return
    }
    if (m === 'DELETE') {
      const f = znajdz()
      if (!f) return
      f.e.entries.splice(f.i, 1)
      f.e.done = Math.max(0, f.e.done - 1)
      return
    }
    if (m === 'UNDO') {
      const e = ex[p.exerciseId]
      if (!e || !e.entries.length) return
      e.entries.pop()
      e.done = Math.max(0, e.done - 1)
    }
  },

  /**
   * Potwierdzone polecenie wpisane na stale do stanu.
   *
   * Odpowiedz telefonu nie zawsze nadaje sie do pokazania - zaleglosc
   * z innego treningu, odpowiedz bez stanu. Polecenie znika jednak z kolejki
   * i bez tego wpisu seria znikalaby tez z ekranu, az do nastepnej odpowiedzi.
   */
  wpiszPotwierdzone(poz, setId) {
    const data = this.state.data
    if (!data || !data.exercises) return
    const p = poz.p || {}
    if (p.sid && this.state.sessionId && String(p.sid) !== String(this.state.sessionId)) return
    const ex = {}
    data.exercises.forEach((e) => {
      if (!e.entries) e.entries = []
      ex[e.id] = e
    })
    this.nalozOp(ex, poz.m, p, poz.cid, setId, false)
    this.zmienione()
  },

  /** Serie cwiczenia, z tymi jeszcze w kolejce. */
  shownDone(ex) {
    const e = this.nakladka().ex[ex.id]
    return e ? e.done : (ex.done || 0)
  },

  /**
   * Ile polecen zmieniajacych serie tego treningu czeka na telefon.
   * Kolor licznika mowi, czy to, co widac, juz jest w bazie.
   */
  pendingCount() {
    const sid = this.state.sessionId
    let n = 0
    ;(this.state.outbox || []).forEach((x) => {
      if (x.m !== 'SET' && x.m !== 'DELETE' && x.m !== 'UNDO') return
      if (x.p && x.p.sid && sid && String(x.p.sid) !== String(sid)) return
      n += 1
    })
    return n
  },

  /** Czy w kolejce czeka cokolwiek dla tej sesji. */
  czekaDla(sid) {
    if (!sid) return false
    return (this.state.outbox || []).some((x) => x.p && String(x.p.sid) === String(sid))
  },

  isSkipped(id) {
    return this.state.skipped[id] === true
  },

  /**
   * Czy plan jest wykonany - licząc po swojemu, nie po telefonie.
   *
   * Telefon nic nie wie o pominięciach: dla niego ćwiczenie bez serii
   * jest po prostu niezrobione, więc trening z pominiętym ćwiczeniem
   * nigdy nie pokazałby podsumowania.
   *
   * Patrzymy tylko w przód, tak samo jak [advance]. Ostatnia seria
   * ostatniego ćwiczenia kończy trening nawet wtedy, gdy coś po drodze
   * zostało - bo wtedy właśnie skończyłeś, a nie cofnąłeś się do planu.
   */
  localComplete() {
    const list = (this.state.data && this.state.data.exercises) || []
    if (!list.length) return false
    for (let i = this.state.index; i < list.length; i++) {
      const ex = list[i]
      if (!this.isSkipped(ex.id) && this.shownDone(ex) < ex.sets) return false
    }
    return true
  },

  /**
   * Czego w planie zabrakło: całe ćwiczenia i pojedyncze serie.
   *
   * Idzie na podsumowanie, bo po treningu trudno odtworzyć z pamięci,
   * czy ćwiczenie się ominęło świadomie, czy po prostu wypadło.
   */
  gaps() {
    const list = (this.state.data && this.state.data.exercises) || []
    const names = []
    let sets = 0
    list.forEach((ex) => {
      const done = this.shownDone(ex)
      if (done >= ex.sets) return
      sets += ex.sets - done
      if (done === 0) names.push(ex.name)
    })
    return { names: names, sets: sets }
  },

  /**
   * Pominięcie bieżącego ćwiczenia, odwracalne.
   *
   * Bez tego jedyną drogą obok ćwiczenia było przejście strzałką, a wtedy
   * [advance] i tak ściągał z powrotem - bo szukał pierwszego
   * niezrobionego od początku planu.
   */
  skipExercise() {
    const ex = this.current()
    if (!ex) return
    if (this.isSkipped(ex.id)) {
      delete this.state.skipped[ex.id]
      this.showView(VIEW_SETS)
      return
    }
    this.state.skipped[ex.id] = true
    this.advance()
    this.remember()
    this.state.phase = PHASE_SET
    this.state.phaseSince = Date.now()
    this.showView(this.localComplete() ? VIEW_DONE : VIEW_SETS)
  },

  /**
   * Czy pytamy teraz o zapas powtórzeń.
   *
   * Tylko w przerwie i tylko gdy jest co opisać - czyli gdy telefon zdążył
   * potwierdzić ostatnią serię i nie ma ona jeszcze wpisanego zapasu.
   * Po wybraniu rząd znika, żeby strzałki wróciły na swoje miejsce.
   */
  /**
   * Czy pytamy teraz o zapas powtórzeń.
   *
   * Wyłącznie ze stanu zegarka. Wcześniej trzeba było znaleźć zapisaną serię
   * w danych od telefonu - a te przychodzą dopiero z odpowiedzią, więc rząd
   * pojawiał się z opóźnieniem, a bez łączności nie pojawiał się wcale.
   */
  askingRir() {
    return this.state.phase === PHASE_REST && !!this.state.rirFor
  },

  /**
   * Zapas powtórzeń dla serii, którą właśnie skończyłeś.
   *
   * Czwarty przycisk znaczy "trzy albo więcej". Dokładniejsza skala nie ma
   * sensu: ludzie i tak zaniżają zapas o mniej więcej jedno powtórzenie,
   * a cztery pola trafia się kciukiem bez patrzenia.
   */
  setRir(value) {
    const r = this.state.rirFor
    if (!r) return
    this.buzz()
    this.state.rirFor = null
    this.applyVisibility()
    this.paintSets()
    this.remember()
    // Zapis sprzed tej zmiany trzymal tu sam numer cwiczenia.
    if (typeof r !== 'object') {
      this.queue('RIR', { exerciseId: r, rir: value })
      return
    }
    this.opiszSerie(r.ref, r.ex, { rir: value })
    // Zapas do ostatniej serii planu - to byla ostatnia rzecz przed koncem.
    if (this.koniecTimer) this.pokazKoniec()
  },

  /**
   * Koniec planu, rozpoznany na zegarku.
   *
   * Podsumowanie pokazywala dotad wylacznie odpowiedz telefonu na ostatnia
   * serie. Z telefonem w szatni ta odpowiedz nie przychodzila wcale - ostatnia
   * seria ostatniego cwiczenia nie konczyla treningu i ekran stal na serii.
   * Zegarek wie sam, ze plan jest zrobiony: liczy serie z kolejki.
   *
   * Nie od razu: po ostatniej serii pada jeszcze pytanie o zapas. Wybor
   * zapasu albo kilka sekund ciszy - i wtedy podsumowanie.
   */
  zaplanujKoniec() {
    if (this.koniecTimer) clearTimeout(this.koniecTimer)
    this.koniecTimer = setTimeout(() => this.pokazKoniec(), KONIEC_MS)
  },

  pokazKoniec() {
    if (this.koniecTimer) {
      clearTimeout(this.koniecTimer)
      this.koniecTimer = null
    }
    if (this.state.wasComplete || !this.localComplete()) return
    // Podsumowanie wskakuje tylko w miejsce ekranow treningu - z dziennika
    // czy poprawki nie wyrywamy, tam sie wlasnie cos robi.
    if (COLUMN.indexOf(this.state.view) < 0 || this.state.message) return
    this.state.wasComplete = true
    this.remember()
    this.showView(VIEW_DONE)
  },

  /**
   * Zmiana serii wskazanej znacznikiem polecenia, ktore ja zalozylo.
   *
   * Seria, ktora jeszcze nie wyszla z zegarka, dostaje zmiane wprost
   * w swoim poleceniu - bez osobnego polecenia i bez zgadywania, ktora to.
   * Wczesniej zapas lecial jako "dla ostatniej serii cwiczenia" i gdy
   * w kolejce czekalo kilka serii, trafial w zla - albo w nic.
   *
   * Seria juz wyslana dostaje polecenie z jej numerem, a gdy numeru jeszcze
   * nie znamy - ze znacznikiem, ktory telefon sam zamieni na numer.
   */
  opiszSerie(ref, exerciseId, zmiany) {
    const poz = this.czekajacaSeria(ref)
    if (poz) {
      for (const k in zmiany) poz.p[k] = zmiany[k]
      this.zmienione()
      this.rememberOutbox()
      return
    }
    const setId = this.state.acked[ref]
    const p = Object.assign({ exerciseId: exerciseId }, zmiany)
    if (setId) p.setId = setId
    else p.ref = ref
    this.queue(zmiany.weight !== undefined || zmiany.reps !== undefined ? 'EDIT' : 'RIR', p)
  },

  /**
   * Polecenie SET o tym znaczniku, o ile jeszcze ani razu nie wyszlo.
   *
   * Tylko takie wolno zmieniac w miejscu. Wyslane moglo juz zostac wykonane
   * bez odpowiedzi - ponowione trafiloby wtedy na telefonie w pamiec powtorek
   * i zmiana przepadlaby po cichu.
   */
  czekajacaSeria(ref) {
    if (!ref) return null
    const lista = this.state.outbox || []
    for (let i = 0; i < lista.length; i++) {
      const x = lista[i]
      if (x.m === 'SET' && x.cid === ref) return x.s ? null : x
    }
    return null
  },

  /**
   * Sumy do pokazania - z tego samego widoku co serie i dziennik.
   *
   * Licza sie z wpisow, nie z liczb przyslanych przez telefon, bo tamte
   * dotycza tylko tego, co juz dojechalo.
   */
  sumy() {
    return this.nakladka().totals
  },

  /**
   * Przepisanie kolejki na numer, ktory nadal telefon.
   *
   * Polecenia wyslane przed polaczeniem niosa nasz wlasny znacznik sesji.
   * Od chwili, gdy telefon nada jej numer, to ta sama sesja - bez tego
   * reszta kolejki wygladala jak zaleglosc z innego treningu i jej
   * odpowiedzi przestawaly odswiezac ekran.
   */
  przepiszKolejke(stary, nowy) {
    if (!stary || !nowy) return
    let zmiana = false
    ;(this.state.outbox || []).forEach((x) => {
      if (x.p && x.p.sid === stary) { x.p.sid = String(nowy); zmiana = true }
    })
    if (zmiana) { this.zmienione(); this.rememberOutbox() }
  },

  /** Ile polecen czeka na telefon. */
  czeka() {
    return (this.state.outbox || []).length
  },

  /**
   * Jedna linijka o tym, co jeszcze nie dojechalo.
   *
   * Dotad nie bylo tego nigdzie: kolejka pracowala w ciszy, a jedynym
   * znakiem byla zolta godzina, ktora mowi o braku odpowiedzi, nie
   * o zaleglosci. Trening mogl wiec lezec w zegarku cala noc i nic na
   * ekranie tego nie zdradzalo.
   */
  syncText() {
    const n = this.czeka()
    if (!n) return this.state.syncDone ? 'Wysłane do telefonu' : 'Nic do wysłania'
    const co = n === 1 ? '1 rzecz' : n + ' rzeczy'
    return (this.state.offline ? 'Czeka ' : 'Wysyłam ') + co
  },

  paintSync() {
    const w = this.w
    if (!w || !w.statSync) return
    const n = this.czeka()
    this.setText(w.statSync, this.syncText())
    this.kolor(w.statSync, n ? (this.state.offline ? AMBER : PENDING) : DIM)
  },

  /**
   * Zmiana dlugosci kolejki - jedyne miejsce, ktore o niej mowi na glos.
   *
   * Opustoszenie kolejki to wydarzenie: wlasnie wtedy trening jest juz
   * w bazie na telefonie i mozna odlozyc zegarek. Dlatego jedna wibracja,
   * tylko przy przejsciu z "cos czeka" na "nic nie czeka".
   */
  zmianaKolejki() {
    const n = this.czeka()
    const bylo = this.state.syncWas || 0
    this.state.syncWas = n
    if (bylo && !n) {
      this.state.syncDone = true
      this.buzz()
    }
    if (n) this.state.syncDone = false
    const view = this.state.view
    if (this.state.built && !this.state.message) {
      if (view === VIEW_STATS) this.paintSync()
      else if (view === VIEW_START) this.paintStart()
      else if (view === VIEW_DONE) this.paintDone()
      // Odpowiedz telefonu nie przemalowuje juz ekranu serii - kolor
      // licznika (czeka / w bazie) zmienia sie wiec tutaj.
      else if (view === VIEW_SETS && this.state.data) this.paintCount()
    }
  },

  /** Serie policzone lokalnie, razem z tymi jeszcze niepotwierdzonymi. */
  shownTotalSets() {
    const list = (this.state.data && this.state.data.exercises) || []
    let n = 0
    list.forEach((ex) => { n += this.shownDone(ex) })
    return n
  },

  // ---- Rysowanie ---------------------------------------------------------

  paint() {
    if (!this.state.built || this.state.message) return
    if (!this.state.data && this.state.view !== VIEW_START) return
    switch (this.state.view) {
      case VIEW_SETS: this.paintSets(); break
      case VIEW_EDIT: this.paintEdit(); break
      case VIEW_DONE: this.paintDone(); break
      case VIEW_CONTROL: this.paintControl(); break
      case VIEW_START: this.paintStart(); break
      case VIEW_STATS: this.paintStats(); break
      case VIEW_HEART: this.paintHeart(); break
      case VIEW_GAPS: this.paintGaps(); break
      case VIEW_TONS: this.paintTons(); break
      case VIEW_LOG: this.paintLog(); break
      case VIEW_FIX: this.paintFix(); break
    }
  },

  paintSets() {
    const w = this.w
    const ex = this.current()
    if (!ex) return

    this.paintName(ex.name)
    this.paintCount()
    // Ciężar i powtórzenia podpisane, bez surowego "x". To są te same
    // wartości, które pójdą do dziennika po naciśnięciu - czyli to, co
    // ustawiłeś na ekranie obok, a nie zakres z planu.
    if (this.askingRir()) {
      this.setText(w.load, 'ile powtórzeń zostało?')
    } else {
      this.setText(
        w.load,
        (ex.hasWeight
          ? this.num(this.state.weight) + ' ' + ex.unit + '   '
          : '') + 'pow. ' + this.state.reps
      )
    }
    this.paintClock()
  },

  /**
   * Licznik serii i jego kolor - turkusowy, dopoki cos czeka na telefon.
   *
   * Osobno od reszty ekranu, bo potwierdzenie od telefonu zmienia wylacznie
   * kolor: nie ma po co przemalowywac nazwy ani ciezaru.
   */
  paintCount() {
    const ex = this.current()
    if (!ex || !this.w || !this.w.count) return
    const done = this.shownDone(ex)
    this.setBtn(this.w.count, {
      text: this.isSkipped(ex.id)
        ? 'POMINIĘTE'
        : (done >= ex.sets
          ? 'ZROBIONE ' + done + ' z ' + ex.sets
          : 'SERIA ' + (done + 1) + ' z ' + ex.sets),
      color: this.pendingCount() > 0 ? PENDING : ACCENT
    })
  },

  /**
   * Nazwa ćwiczenia, a gdy nie mieści się w tarczy - przewijana.
   *
   * "Wyciskanie hantli nad głową w siadzie z oparciem" nie wejdzie
   * w okrąg w żadnym rozmiarze, w jakim dałoby się to jeszcze czytać.
   * Zawijanie na dwie linijki zjadało miejsce licznikowi serii, więc
   * nazwa jedzie w lewo i wraca - tak, żeby dało się przeczytać całość,
   * nie patrząc na nią dłużej niż chwilę.
   *
   * Szerokość liczymy z przybliżenia, bo tekstu nie da się tu zmierzyć.
   * Pomyłka w jedną stronę zostawia margines, w drugą - urywa ogon,
   * więc liczymy z zapasem.
   */
  /**
   * Szerokość napisu w pikselach.
   *
   * Liczenie po znakach było z grubsza i zawsze w górę: w nazwie z wąskimi
   * literami myliło się o kilkadziesiąt pikseli i nazwa mieszcząca się na
   * ekranie i tak jeździła na boki. System umie to zmierzyć naprawdę,
   * a stara arytmetyka zostaje na wypadek, gdyby nie umiał.
   */
  textWidth(text) {
    try {
      const wymiar = hmUI.getTextLayout(String(text), {
        text_size: px(NAME_SIZE), text_width: 0, wrapped: 0
      })
      if (wymiar && wymiar.width > 0) return wymiar.width
    } catch (e) {}
    return Math.ceil(String(text).length * NAME_CHAR_W)
  },

  paintName(text) {
    const w = this.w.name
    if (this.lastName === text) return
    this.lastName = text
    this.setText(w, text)

    // Każda zmiana nazwy unieważnia poprzedni przejazd. Bez tego przejście
    // z długiej nazwy na krótką zostawiało animację chodzącą w kółko przez
    // resztę treningu - a ta zjada klatki i wszystko zaczyna się kleić.
    this.nameRun = (this.nameRun || 0) + 1
    if (this.nameTimer) {
      clearTimeout(this.nameTimer)
      this.nameTimer = null
    }

    const szer = this.textWidth(text)
    if (szer <= NAME_BOX_W) {
      w.setProperty(hmUI.prop.MORE, {
        x: px(NAME_X), y: px(NAME_Y), w: px(NAME_BOX_W), h: px(NAME_H),
        align_h: hmUI.align.CENTER_H
      })
      return
    }

    const nadmiar = szer - NAME_BOX_W
    w.setProperty(hmUI.prop.MORE, {
      x: px(NAME_X), y: px(NAME_Y), w: px(szer), h: px(NAME_H),
      align_h: hmUI.align.LEFT
    })
    this.slideName(nadmiar, -1)
  },

  /**
   * Jeden przejazd nazwy, po którym sam zawraca.
   *
   * Dwa kroki w jednym zleceniu z `anim_repeat: -1` zachowywały się dziwnie:
   * napis raz skakał w prawo i wyłaniał się z lewej, raz odbijał w pół drogi,
   * a czasem dwoił się na ekranie. Z `anim_auto_destroy: 0` kolejne wywołanie
   * dokładało animację do poprzedniej, zamiast ją zastąpić - stąd dwa napisy.
   *
   * Teraz leci jeden krok, sprząta się po sobie, a zawrotkę zleca dopiero
   * jego wywołanie zwrotne. Znacznik `nameRun` unieważnia starą pętlę, gdy
   * ćwiczenie zmieni się w trakcie przejazdu.
   */
  slideName(nadmiar, kierunek) {
    const w = this.w.name
    if (!w || nadmiar <= 0) return

    const bieg = (this.nameRun || 0) + 1
    this.nameRun = bieg

    const czas = Math.max(900, nadmiar * 14)
    const od = kierunek < 0 ? NAME_X : NAME_X - nadmiar
    const doo = kierunek < 0 ? NAME_X - nadmiar : NAME_X

    const zawroc = () => {
      if (this.nameRun !== bieg) return
      // Jeden przejazd tam i z powrotem, potem nazwa stoi od poczatku.
      // Chodzila dotad w kolko przez caly czas cwiczenia - a dluga nazwa
      // to kazde cwiczenie z hantlami na lawce - i animacja w tle zjadala
      // klatki, ktorych potrzebowaly przyciski.
      if (kierunek > 0 || this.state.view !== VIEW_SETS) return
      this.nameTimer = setTimeout(() => {
        if (this.nameRun !== bieg) return
        this.slideName(nadmiar, -kierunek)
      }, NAME_HOLD_MS)
    }

    try {
      w.setProperty(hmUI.prop.ANIM, {
        anim_steps: [{
          anim_prop: hmUI.prop.X,
          anim_from: od,
          anim_to: doo,
          anim_rate: 'linear',
          anim_duration: czas,
          anim_offset: 0
        }],
        anim_fps: 30,
        anim_auto_start: 1,
        anim_auto_destroy: 1,
        anim_repeat: 0,
        anim_complete_func: zawroc
      })
    } catch (e) {
      // Bez animacji zostaje nazwa dosunięta do lewej - ucięta, ale
      // czytelna od początku, a początek niesie najwięcej.
    }
  },

  paintStats() {
    const w = this.w
    const data = this.state.data || {}
    const totals = data.totals || {}
    const paused = !!data.paused
    this.setText(w.statTime, this.clock(this.sessionSeconds()))
    this.setText(w.statTimeLabel, paused ? 'Wstrzymany' : 'Czas treningu')
    this.kolor(w.statTimeLabel, paused ? AMBER : DIM)
    this.setText(w.statSets, String(this.shownTotalSets()))
    this.setText(w.statSetsLabel, 'Serie')
    this.setText(w.statKcal, this.kcalText())
    this.setText(w.statKcalLabel, this.state.kcalFail ? 'Kalorie - brak zgody' : 'Kalorie')
    this.kolor(w.statKcalLabel, this.state.kcalFail ? AMBER : DIM)
    this.setText(w.statVolume, Math.round(this.sumy().tonnage || 0) + ' kg objętości')
    this.paintSync()
  },

  /**
   * Kolor dla tętna: od razu widać, czy to jeszcze rozgrzewka, czy już próg.
   *
   * Bez progów z telefonu zostaje biel. Malowanie stref z wieku zgadniętego
   * na oko dawałoby kolory, które kłamią - a kolor czyta się szybciej niż
   * liczbę i właśnie dlatego musi być prawdziwy.
   */
  hrColor(bpm) {
    const z = this.state.zones
    if (!z || !bpm) return FG
    if (bpm < z.easy) return ZONE_EASY
    if (bpm < z.aerobic) return ZONE_AEROBIC
    if (bpm < z.tempo) return ZONE_TEMPO
    if (bpm < z.anaerobic) return ZONE_ANAEROBIC
    return ZONE_MAX
  },

  hrZoneName(bpm) {
    const z = this.state.zones
    if (!z || !bpm) return ''
    if (bpm < z.easy) return 'spokój'
    if (bpm < z.aerobic) return 'rozgrzewka'
    if (bpm < z.tempo) return 'tlenowa'
    if (bpm < z.anaerobic) return 'beztlenowa'
    return 'maksymalna'
  },

  /**
   * Kalorie albo powód, dla którego ich nie ma.
   *
   * Zero znaczyło dotąd dwie zupełnie różne rzeczy: "nic jeszcze nie
   * spaliłeś" i "czujnik milczy". Pierwsze jest prawdą przez pierwszą
   * minutę treningu, drugie trzeba naprawić - więc muszą wyglądać inaczej.
   */
  kcalText() {
    // Wyliczenie z tętna jest tu lepsze od czujnika i to ono rządzi;
    // czujnik zostaje jako wyjście awaryjne, gdy nie znamy ani masy,
    // ani wieku i nie ma z czego liczyć.
    if (this.state.burn) return String(Math.round(this.state.burnKcal))
    if (this.state.kcalFail) return 'x'
    if (!this.state.kcalSeen) return '-'
    return String(Math.round(this.state.kcal))
  },

  /** Sama liczba, bez wykresu - woła to czujnik kilka razy na sekundę. */
  paintHeartValue() {
    if (!this.state.built) return
    // Tętno przychodzi co sekundę przez cały trening. Malowanie go na
    // ekranie, którego nie widać, to praca za darmo na wątku, którego
    // potrzebują przyciski.
    const widac = this.state.view === VIEW_SETS || this.state.view === VIEW_HEART
    if (!widac || this.state.message) return
    const value = this.state.hr > 0 ? String(this.state.hr) : '--'
    const kolor = this.hrColor(this.state.hr)
    // Tetno przychodzi co sekunde przez caly trening. Kolor i polozenie
    // serca pisaly sie przy kazdym odczycie - trzy zapisy do widgetow
    // na sekunde, prawie zawsze z ta sama wartoscia.
    this.setText(this.w.hr, value)
    this.kolor(this.w.hr, kolor)
    this.kolor(this.w.hrIcon, kolor)
    this.placeHeart(value)
    if (this.state.view === VIEW_HEART) {
      this.setText(this.w.heartValue, value)
      this.kolor(this.w.heartValue, kolor)
      this.setText(this.w.heartLabel, this.hrZoneName(this.state.hr) || 'Tętno')
    }
  },

  /**
   * Serce przy lewej krawędzi liczby.
   *
   * Liczba stoi na środku ekranu, a jej szerokość zależy od tego, czy
   * tętno ma dwie cyfry czy trzy. Stałe miejsce na serce oznaczałoby
   * więc raz dziurę, raz nachodzenie - dosuwamy je do wyliczonej
   * krawędzi, zamiast przesuwać liczbę ze środka.
   */
  placeHeart(value) {
    if (this.w.hrIcon.cyfr === value.length) return
    this.w.hrIcon.cyfr = value.length
    const half = (value.length * HR_DIGIT_W) / 2
    const right = Math.round(DEVICE_WIDTH / 2 - half - HR_GAP)
    this.w.hrIcon.setProperty(hmUI.prop.MORE, {
      x: px(right - HR_ICON_W), y: px(66), w: px(HR_ICON_W), h: px(58)
    })
  },

  /**
   * Tętno z przebiegiem z tej sesji.
   *
   * Skala dopasowuje się do zebranych próbek, bo stały zakres 50-190 spłaszcza
   * wszystko do kilku pikseli - przy pracy na 120-140 nie widać wtedy ani
   * wejścia w serię, ani zejścia w przerwie.
   */
  paintHeart() {
    const w = this.w
    const log = this.state.hrLog
    this.setText(w.heartLabel, this.hrZoneName(this.state.hr) || 'Tętno')
    this.setText(w.heartValue, this.state.hr > 0 ? this.state.hr : '--')
    this.kolor(w.heartValue, this.hrColor(this.state.hr))

    if (!log.length) {
      this.setText(w.heartRange, 'zbieram pomiary...')
      w.bars.forEach((bar) => this.setVisible(bar, false))
      return
    }

    let lo = log[0]
    let hi = log[0]
    for (let i = 1; i < log.length; i++) {
      if (log[i] < lo) lo = log[i]
      if (log[i] > hi) hi = log[i]
    }
    // Podpis mówi o całej sesji, słupki o ostatnich minutach - inaczej
    // "minimum" znaczyłoby minimum z dwóch minut i wychodziło absurdalnie
    // wysoko zaraz po serii.
    const okno = Math.round((log.length * BAR_EVERY_MS) / 60000)
    this.setText(
      w.heartRange,
      'sesja ' + (this.state.hrMin || lo) + '-' + (this.state.hrMax || hi) +
        '     wykres ' + okno + ' min'
    )

    const floor = lo - 5
    const span = Math.max(hi - floor, 20)
    w.bars.forEach((bar, i) => {
      if (i >= log.length) {
        this.setVisible(bar, false)
        return
      }
      let frac = (log[i] - floor) / span
      if (frac < 0.06) frac = 0.06
      if (frac > 1) frac = 1
      const h = Math.round(frac * BAR_MAX_H)
      bar.setProperty(hmUI.prop.MORE, {
        x: px(BAR_X0 + i * (BAR_W + BAR_GAP)), y: px(BAR_BASE - h),
        w: px(BAR_W), h: px(h), radius: px(2), color: HEART
      })
      this.setVisible(bar, true)
    })
  },

  paintEdit() {
    const w = this.w
    const ex = this.current()
    if (!ex) return
    this.setText(w.wLabel, 'Ciężar')
    this.setText(w.wValue, ex.hasWeight ? this.num(this.state.weight) : '-')
    this.setText(w.rLabel, 'Powtórzenia')
    this.setText(w.rValue, String(this.state.reps))
  },

  paintDone() {
    const w = this.w
    const data = this.state.data || {}
    const totals = data.totals || {}
    this.setText(w.doneTitle, 'Podsumowanie')
    this.setText(w.doneTime, this.clock(this.sessionSeconds()))
    const sumy = this.sumy()
    this.setText(w.doneSets, (sumy.sets || 0) + ' serii,  ' + (sumy.reps || 0) + ' powt.')
    this.setText(w.doneSkip, this.gapsText())
    const g = this.gaps()
    this.setText(w.doneMore, g.sets ? 'w górę: co pominięte' : '')
    this.setText(
      w.doneTon,
      Math.round(sumy.tonnage || 0) + ' kg,  ' + this.kcalText() + ' kcal'
    )
    this.setText(w.doneHint, this.tonRows().length ? 'w dół: tonaż po ćwiczeniach' : 'w prawo: wróć do serii')
    // Po treningu to jedyny moment, w ktorym widac, czy cokolwiek zostalo
    // do wyslania. Wczesniej nie bylo tego nigdzie i trening lezal
    // w kolejce bez sladu na ekranie.
    const czeka = this.czeka()
    this.setText(
      w.doneNext,
      czeka ? this.syncText() : (this.state.nextUnit ? 'następny: ' + this.state.nextUnit : '')
    )
    this.kolor(w.doneNext, czeka ? AMBER : DIM)
  },

  /** Nazwy na ekran braków, z ogonem "i jeszcze N", gdy się nie mieszczą. */
  gapsRows() {
    const names = this.gaps().names
    if (names.length <= GAP_ROWS) return names
    const widoczne = names.slice(0, GAP_ROWS - 1)
    widoczne.push('i jeszcze ' + (names.length - (GAP_ROWS - 1)))
    return widoczne
  },

  /**
   * Skok z listy braków wprost na to ćwiczenie.
   *
   * Ostatnia pozycja bywa podsumowaniem "i jeszcze N" i nie wskazuje
   * niczego konkretnego - wtedy nie ma dokąd skakać.
   */
  openGap(i) {
    const names = this.gaps().names
    const rows = this.gapsRows()
    if (i >= rows.length) return
    const szukana = rows[i]
    if (names.indexOf(szukana) < 0) return
    const list = (this.state.data && this.state.data.exercises) || []
    const idx = list.findIndex((ex) => ex.name === szukana)
    if (idx < 0) return
    this.state.index = idx
    this.ustawPropozycje(list[idx])
    this.state.editedFor = null
    this.state.phase = PHASE_SET
    this.state.phaseSince = Date.now()
    this.remember()
    this.showView(VIEW_SETS)
  },

  /** Zapisane serie bieżącego ćwiczenia, od pierwszej. */
  logRows() {
    const ex = this.current()
    if (!ex) return []
    const e = this.nakladka().ex[ex.id]
    return (e && e.entries) || []
  },

  /**
   * Od której serii zaczyna się to, co widać.
   *
   * Dotąd widać było sześć pierwszych i koniec: siódma seria nie istniała
   * na tym ekranie, a przy ćwiczeniu z ośmioma nie dało się poprawić
   * żadnej z ostatnich dwóch. Okno przesuwa się gestem w dół i zawija.
   */
  logFrom() {
    const lista = this.logRows()
    if (lista.length <= LOG_ROWS) return 0
    const max = lista.length - LOG_ROWS
    return Math.min(this.state.logAt || 0, max)
  },

  /**
   * Gest w dół w dzienniku: kolejne serie.
   *
   * W górę wychodzi z ekranu i tak zostaje - wyjście jest ważniejsze od
   * przewijania w drugą stronę, a okno i tak zawija się na końcu listy.
   */
  logScroll() {
    const lista = this.logRows()
    if (lista.length <= LOG_ROWS) return false
    const max = lista.length - LOG_ROWS
    const teraz = this.logFrom()
    this.state.logAt = teraz >= max ? 0 : Math.min(teraz + LOG_ROWS, max)
    this.applyVisibility()
    this.paintLog()
    return true
  },

  paintLog() {
    const w = this.w
    const ex = this.current()
    if (!ex) return
    this.setText(w.logTitle, ex.name)
    const lista = this.logRows()
    const od = this.logFrom()
    lista.slice(od, od + LOG_ROWS).forEach((e, i) => {
      this.setBtn(w.logRows[i], {
        text: (od + i + 1) + '.  ' + this.num(e.weight) + ' ' + ex.unit + '  x ' + e.reps +
          (e.rir >= 0 ? '   RIR ' + (e.rir >= 3 ? '3+' : e.rir) : ''),
        color: FG
      })
    })
    // Podpis mówi, ile zostało poza oknem - inaczej nic nie zdradza,
    // że gest w dół w ogóle coś da.
    this.setText(
      w.logHint,
      lista.length === 0 ? 'brak zapisanych serii'
        : lista.length <= LOG_ROWS ? 'dotknij, by poprawić'
          : (od + 1) + '-' + Math.min(od + LOG_ROWS, lista.length) +
            ' z ' + lista.length + ' · w dół po resztę'
    )
  },

  /**
   * Poprawka zapisanej serii.
   *
   * Pomyłka w powtórzeniach trzeciej serii oznaczała dotąd cofanie trzech
   * i wpisywanie od nowa, zwykle już po treningu i z pamięci. Tu zmienia
   * się dokładnie tę jedną, po jej numerze.
   */
  openFix(i) {
    const lista = this.logRows()
    const wpis = lista[this.logFrom() + i]
    if (!wpis) return
    // Zapas z dziennika; -1 znaczy "nie podano" i tak też wraca na ekran.
    const zapas = (wpis.rir === undefined || wpis.rir === null) ? -1 : wpis.rir
    this.state.fix = {
      id: wpis.id, ref: wpis.ref, nr: this.logFrom() + i + 1,
      weight: wpis.weight, reps: wpis.reps, rir: zapas,
      exerciseId: (this.current() || {}).id
    }
    this.state.fixConfirm = false
    this.showView(VIEW_FIX)
  },

  paintFix() {
    const w = this.w
    const fix = this.state.fix
    const ex = this.current()
    if (!fix || !ex) return
    this.setText(w.fixTitle, 'Seria ' + fix.nr)
    this.setText(w.fixWLabel, 'Ciężar')
    this.setText(w.fixWValue, ex.hasWeight ? this.num(fix.weight) : '-')
    this.setText(w.fixRLabel, 'Powtórzenia')
    this.setText(w.fixRValue, String(fix.reps))
    // Zapas napisany słowem, nie tylko podświetlony. Sam kolor przycisku
    // nie wystarczał: cztery szare pola i jedno zielone na małej tarczy
    // czyta się jak "wybierz", a nie jak "wtedy podałeś dwa" - a przy
    // serii bez zapasu nie różniło się to niczym od zapasu zerowego.
    const wybrany = (fix.rir === undefined || fix.rir === null) ? -1 : fix.rir
    this.setText(
      w.fixRirLabel,
      wybrany < 0 ? 'zapas: nie podano'
        : 'zapas: ' + (wybrany >= 3 ? '3+' : wybrany)
    )
    w.fixRirRow.forEach((btn, i) => {
      this.setBtn(btn, { normal_color: i === wybrany ? GREEN : GREY })
    })
    this.setBtn(w.fixDelete, {
      text: this.state.fixConfirm ? 'na pewno?' : 'usuń',
      normal_color: this.state.fixConfirm ? 0x8a1f1f : 0x3a1f1f
    })
  },

  /** Zapas w poprawce: jedno naciśnięcie, ta sama skala co po serii. */
  fixSetRir(value) {
    const fix = this.state.fix
    if (!fix) return
    this.buzz()
    // Ponowne naciśnięcie tego samego zdejmuje zapas - inaczej raz podanego
    // nie dałoby się cofnąć, a pomyłka jest tu równie łatwa jak gdzie indziej.
    fix.rir = fix.rir === value ? -1 : value
    this.paintFix()
  },

  /**
   * Skasowanie serii - na dwa naciśnięcia.
   *
   * Pierwsze zmienia podpis na pytanie, drugie kasuje. Po chwili pytanie
   * samo się wycofuje, żeby przycisk nie został uzbrojony na później.
   */
  fixDelete() {
    const fix = this.state.fix
    if (!fix) return
    if (!this.state.fixConfirm) {
      this.state.fixConfirm = true
      this.buzz()
      this.paintFix()
      if (this.fixTimer) clearTimeout(this.fixTimer)
      this.fixTimer = setTimeout(() => {
        this.fixTimer = null
        this.state.fixConfirm = false
        if (this.state.view === VIEW_FIX) this.paintFix()
      }, FIX_CONFIRM_MS)
      return
    }
    if (this.fixTimer) {
      clearTimeout(this.fixTimer)
      this.fixTimer = null
    }
    this.state.fixConfirm = false
    this.buzz()
    // Seria, ktora jeszcze nie wyszla z zegarka, znika razem z poleceniem -
    // telefon nigdy sie o niej nie dowie. Wyslana dostaje polecenie kasowania.
    // Dziennik i licznik licza sie z kolejki, wiec seria znika z ekranu od razu.
    const czeka = fix.ref ? this.czekajacaSeria(fix.ref) : null
    if (czeka) {
      this.state.outbox = (this.state.outbox || []).filter((x) =>
        x !== czeka && !(x.p && x.p.ref === fix.ref))
      this.rememberOutbox()
      this.zmianaKolejki()
    } else if (fix.id) {
      this.queue('DELETE', { setId: fix.id })
    } else if (fix.ref) {
      const setId = this.state.acked[fix.ref]
      this.queue('DELETE', setId ? { setId: setId } : { ref: fix.ref })
    }
    const r = this.state.rirFor
    if (r && typeof r === 'object' && fix.ref && r.ref === fix.ref) this.state.rirFor = null
    this.trzymajEkran(fix.exerciseId)
    this.showView(VIEW_LOG)
  },

  /**
   * Poprawka dotyczy zapisu, nie nastepnej serii.
   *
   * Telefon podstawia na nastepna serie to, co stoi w ostatniej zapisanej.
   * Po poprawce ostatniej serii jego odpowiedz niosla wiec poprawione liczby
   * i ekran serii przestawial sie na nie: seria poprawiona z osmiu na szesc
   * powtorzen zmieniala tez cel kolejnej, a skasowana - cofala go do tej
   * sprzed niej. Trzymamy to, co stoi na ekranie, tak samo jak po recznej
   * zmianie plusem: do nastepnej zapisanej serii albo do zmiany cwiczenia.
   */
  trzymajEkran(exerciseId) {
    const ex = this.current()
    if (!ex || ex.id !== exerciseId) return
    this.state.editedFor = ex.id
    this.remember()
  },

  fixStep(co, dir) {
    const fix = this.state.fix
    const ex = this.current()
    if (!fix || !ex) return
    if (co === 'r') {
      const v = fix.reps + dir
      if (v < 1) return
      fix.reps = v
    } else {
      if (!ex.hasWeight) return
      // Ta sama drabinka co przy zapisie - poprawka nie może wprowadzić
      // ciężaru, którego nie da się złożyć z talerzy. Bez ruszania ciężaru
      // na nastepna serie: szlo to dotad przez ten sam przycisk co edycja,
      // wiec poprawka starej serii przestawiala tez propozycje na nowa.
      const nowa = this.krokCiezaru(ex, fix.weight, dir)
      if (nowa === null) return
      fix.weight = nowa
    }
    this.paintFix()
  },

  /**
   * Zapis poprawki - najpierw do kolejki, potem na ekran.
   *
   * Odwrotna kolejnosc to byl ten blad, w ktorym poprawka "wracala do
   * poprzedniego stanu": dziennik malowal sie od razu, ze starych liczb
   * z ostatniej odpowiedzi telefonu, i stal tak, dopoki poprawka nie
   * dojechala - bez lacznosci do konca treningu.
   */
  saveFix() {
    const fix = this.state.fix
    if (!fix) return
    this.state.fixConfirm = false
    this.buzz()
    const zmiany = { weight: fix.weight, reps: fix.reps, rir: fix.rir }
    if (fix.id) this.queue('EDIT', Object.assign({ setId: fix.id }, zmiany))
    else if (fix.ref) this.opiszSerie(fix.ref, fix.exerciseId, zmiany)
    this.trzymajEkran(fix.exerciseId)
    this.showView(VIEW_LOG)
  },

  /**
   * Tonaż każdego ćwiczenia z osobna.
   *
   * Liczony tutaj, nie na telefonie: zegarek ma zapisane serie każdego
   * ćwiczenia, więc nie ma po co pytać - a po treningu i tak chce się to
   * zobaczyć od razu, nie po synchronizacji.
   *
   * Serie rozgrzewkowe pomijamy, tak samo jak w sumie całkowitej.
   */
  tonRows() {
    const lista = (this.state.data && this.state.data.exercises) || []
    const out = []
    const widok = this.nakladka().ex
    lista.forEach((ex) => {
      // Z widoku, nie z samej odpowiedzi telefonu: bez lacznosci telefon
      // nie przysyla nic, a serie i tak sa - czekaja w kolejce.
      const e = widok[ex.id]
      const wpisy = ((e && e.entries) || []).filter((w) => !w.warmup)
      if (!wpisy.length) return
      const ton = wpisy.reduce((suma, e) => suma + e.weight * e.reps, 0)
      out.push({ name: ex.name, sets: wpisy.length, ton: Math.round(ton) })
    })
    return out
  },

  paintTons() {
    const w = this.w
    const wiersze = this.tonRows()
    const suma = wiersze.reduce((n, r) => n + r.ton, 0)
    this.setText(w.tonTitle, 'Tonaż')
    this.setText(w.tonSub, 'razem ' + suma + ' kg')
    w.tonRows.forEach((row, i) => {
      if (i >= wiersze.length) return
      const r = wiersze[i]
      // Nazwa skrócona, bo liczba po prawej jest tu ważniejsza niż
      // ostatnie litery nazwy, którą i tak się zna.
      const nazwa = r.name.length > 16 ? r.name.slice(0, 15) + "…" : r.name
      this.setText(row, nazwa + '   ' + r.ton + ' kg')
    })
    this.setText(w.tonHint, 'w górę: wróć do podsumowania')
  },

  paintGaps() {
    const w = this.w
    const g = this.gaps()
    this.setText(w.gapsTitle, 'Co pominięte')
    this.setText(w.gapsSub, g.sets + ' ' + this.serie(g.sets) + ' mniej')
    const rows = this.gapsRows()
    w.gapsRows.forEach((row, i) => {
      if (i < rows.length) this.setBtn(row, { text: rows[i], color: AMBER })
    })
    this.setText(w.gapsHint, 'w dół: wróć do podsumowania')
  },

  /** Jedna linijka o tym, czego w planie zabrakło. */
  gapsText() {
    const g = this.gaps()
    if (!g.sets) return 'Plan wykonany w całości'
    const braki = g.sets + ' ' + this.serie(g.sets)
    if (!g.names.length) return 'Brakuje ' + braki
    return 'Pominięte: ' + g.names.length + ' ćw.,  ' + braki
  },

  serie(n) {
    if (n === 1) return 'seria'
    const d = n % 10
    const s = n % 100
    if (d >= 2 && d <= 4 && (s < 12 || s > 14)) return 'serie'
    return 'serii'
  },

  /**
   * Co tyka na ekranie sterowania - wyłącznie napisy.
   *
   * Przyciski są tu celowo pominięte. [setBtn] wysyła komplet właściwości,
   * żeby wymusić przerysowanie po zasłonie przejścia, a wołane co sekundę
   * z zegara odbudowywało oba przyciski w kółko i rozsypywało ekran.
   */
  paintControlClock() {
    const paused = this.paused()
    this.setText(this.w.ctrlTime, this.clock(this.sessionSeconds()))
    this.setText(this.w.ctrlState, paused ? 'wstrzymany' : 'trwa')
  },

  paintControl() {
    const w = this.w
    const paused = this.paused()
    this.paintControlClock()
    this.setText(w.ctrlHint, 'przesuń w lewo, by wrócić')
    this.setBtn(w.ctrlPause, { text: paused ? 'WZNÓW' : 'WSTRZYMAJ' })
    const ex = this.current()
    this.setBtn(w.ctrlSkip, {
      text: ex && this.isSkipped(ex.id) ? 'PRZYWRÓĆ ĆWICZENIE' : 'POMIŃ ĆWICZENIE'
    })
  },

  paintStart() {
    const w = this.w
    const units = this.state.units || []
    // Zaleglosc wyprzedza nazwe programu: po treningu to wazniejsza
    // wiadomosc niz to, jak nazywa sie plan.
    const czeka = this.czeka()
    this.setText(w.startTitle, czeka
      ? this.syncText()
      : (this.state.program || 'Wybierz jednostkę'))
    this.kolor(w.startTitle, czeka ? AMBER : DIM)
    // Jednostka, która wypada w rotacji, jest podświetlona - po treningu
    // nie trzeba pamiętać, na czym się skończyło.
    units.slice(0, w.units.length).forEach((unit, i) => {
      this.setBtn(w.units[i], {
        text: unit.name + '   ' + unit.exercises + ' ćw.',
        normal_color: unit.next ? GREEN : GREY
      })
    })
  },

  // ---- Działania ---------------------------------------------------------

  /**
   * Dolny przycisk boczny - jedyny, którego trzeba szukać palcem.
   *
   * Na każdym ekranie robi to, co w tym miejscu ma sens, i nigdy nie
   * oddaje klawisza systemowi: z podsumowania i ze sterowania wraca do
   * serii, przy zerwanym połączeniu ponawia pytanie, a na wyborze
   * jednostki nie robi nic, bo trening trzeba wskazać świadomie.
   */
  sideButton() {
    if (this.state.message) {
      this.setMessage('Łączę...')
      this.send('STATE', {}, true)
      return
    }
    const view = this.state.view
    if (view === VIEW_START) return
    if (view === VIEW_FIX) {
      this.saveFix()
      return
    }
    if (view === VIEW_LOG) {
      this.showView(VIEW_SETS)
      return
    }
    if (view === VIEW_GAPS) {
      this.showView(VIEW_DONE)
      return
    }
    if (view === VIEW_DONE || view === VIEW_CONTROL) {
      this.showView(VIEW_SETS)
      return
    }
    this.press()
  },

  /**
   * Jeden przycisk, naprzemiennie.
   *
   * W trakcie serii kończy ją i zaczyna przerwę, w trakcie przerwy kończy
   * przerwę i zaczyna kolejną serię. Zapisujemy tylko przy tym pierwszym -
   * koniec przerwy niczego nie dodaje.
   *
   * Działa też z pozostałych ekranów karuzeli i z edycji ciężaru, więc
   * wracamy wtedy na główny: seria zapisana bez śladu na widoku wygląda
   * jak seria niezapisana. Z edycji leci dokładnie to, co tam ustawiłeś.
   */
  press() {
    const view = this.state.view
    if (view === VIEW_EDIT) {
      // Z edycji zawsze zapis, nawet w trakcie przerwy. Po to się tam wchodzi:
      // ustawić ciężar i powtórzenia serii, która właśnie idzie do dziennika.
      this.showView(VIEW_SETS)
      this.saveSet()
      return
    }
    if (view !== VIEW_SETS) this.showView(VIEW_SETS)

    // Plan zrobiony i czeka na podsumowanie - przycisk go nie odwleka,
    // tylko je pokazuje. Nowa seria po koncu planu i tak zaczyna sie
    // z podsumowania jednym gestem.
    if (this.koniecTimer && view === VIEW_SETS) {
      this.pokazKoniec()
      return
    }
    if (this.state.phase === PHASE_REST) {
      // Start serii też szarpie: to sygnał "bierz sztangę", tak samo
      // potrzebny bez patrzenia na ekran jak potwierdzenie zapisu.
      this.buzz()
      this.flash(FLASH_REST, FLASH_REST_MS)
      this.state.phase = PHASE_SET
      this.state.phaseSince = Date.now()
      // Rząd zapasu znika razem z przerwą, a strzałki wracają na miejsce.
      // Bez tego zostawał na ekranie już w trakcie kolejnej serii.
      this.applyVisibility()
      this.paintSets()
      return
    }
    this.saveSet()
  },

  saveSet() {
    const ex = this.current()
    if (!ex) return

    // Bez blokady ponownego naciśnięcia. Stała tu sekunda przerwy na
    // wypadek przyciśnięcia przycisku dwa razy przy odkładaniu sztangi,
    // ale drugie naciśnięcie po prostu znikało i to właśnie czuło się
    // jak zacinanie. Serię zapisaną przez pomyłkę widać w dzienniku
    // i cofa się ją jednym naciśnięciem; naciśnięcie, które nic nie robi,
    // jest gorsze, bo nie wiadomo, czy w ogóle weszło.
    const now = Date.now()
    // Jeden ciągły impuls, nie seria krótkich: przy sztandze w rękach
    // kilka szarpnięć zlewa się w szum, a jedno długie czuć na pewno.
    this.buzz()
    this.restRang = false

    const id = ex.id
    const weight = this.state.weight
    const reps = this.state.reps

    // Seria na pominiętym ćwiczeniu znaczy, że jednak je robisz.
    if (this.isSkipped(id)) delete this.state.skipped[id]
    // Najpierw do kolejki: licznik, dziennik i sumy licza sie z niej.
    const cid = this.queue('SET', {
      exerciseId: id, weight: weight, reps: reps, doneAt: now
    })
    const domkniete = this.shownDone(ex) >= ex.sets
    // Przeskok na kolejne ćwiczenie robimy sami, bez czekania na telefon -
    // inaczej szybkie klikanie pozwalało dobić 4/3 i 5/3.
    if (domkniete) this.advance()

    this.state.phase = PHASE_REST
    this.state.phaseSince = now

    // Bez podążania za telefonem. Odpowiedź niesie jego wskaźnik bieżącego
    // ćwiczenia, a ten przy ukończonym planie pokazuje ostatnie ćwiczenie -
    // dodatkowa seria na drugim przerzucała więc ekran na trzecie. Na którym
    // ćwiczeniu stoimy, wie zegarek: sam tu przyszedł strzałkami albo przez
    // [advance]. Telefon rozstrzyga ciężary, nie miejsce w planie.
    this.state.editedFor = null
    // O zapas pytamy dla tej serii, nie dla ćwiczenia, na które za chwilę
    // przeskoczy ekran po domknięciu poprzedniego.
    this.state.rirFor = { ex: id, ref: cid }
    this.remember()

    // Rząd zapasu pojawia się tutaj, a nie dopiero po odpowiedzi telefonu.
    // Widoczność zmienia wyłącznie [applyVisibility], a na tej ścieżce nikt
    // jej nie wołał: pytanie wisiało do pierwszej odpowiedzi z Bluetootha,
    // a bez zasięgu nie pokazywało się wcale. Malowanie dopiero po
    // ustawieniu [rirFor], bo podpis pod licznikiem zmienia się razem z nim.
    this.applyVisibility()
    this.paintSets()

    // Domknięcie całego ćwiczenia dostaje osobny, dłuższy i zielony błysk -
    // to inny rodzaj wydarzenia niż kolejna seria i ma się tak czuć.
    if (domkniete && !this.state.wasComplete && this.localComplete()) this.zaplanujKoniec()
    if (domkniete) this.flash(FLASH_DONE, FLASH_DONE_MS)
    else this.flash(FLASH_SET, FLASH_SET_MS)
  },

  /**
   * Następne ćwiczenie do zrobienia, zawsze w przód i nigdy wstecz.
   *
   * Najpierw szukało od początku planu, potem z zawinięciem na końcu -
   * obie wersje cofały na ćwiczenie zostawione świadomie, i to zwykle
   * przy ostatniej serii treningu. Koniec listy jest końcem: to, co
   * pominięte, zostaje do dobrania strzałką, jeśli naprawdę chcesz.
   */
  advance() {
    const list = (this.state.data && this.state.data.exercises) || []
    for (let i = this.state.index + 1; i < list.length; i++) {
      const ex = list[i]
      if (this.isSkipped(ex.id) || this.shownDone(ex) >= ex.sets) continue
      this.state.index = i
      this.ustawPropozycje(ex)
      this.state.editedFor = null
      return
    }
  },

  move(delta) {
    const list = (this.state.data && this.state.data.exercises) || []
    const next = this.state.index + delta
    if (next < 0 || next >= list.length) return
    this.state.index = next
    const ex = list[next]
    this.ustawPropozycje(ex)
    this.state.editedFor = null
    this.remember()
    this.paintSets()
  },

  /**
   * Ciezar i powtorzenia na nastepna serie cwiczenia - ta sama regula co
   * na telefonie (LiveSession.nextWeight): to, co stoi w ostatniej serii,
   * a przed pierwsza - plan.
   *
   * Dotad przychodzilo to z odpowiedzia telefonu po kazdej serii. Odkad
   * telefon odpowiada samym potwierdzeniem, zegarek liczy to sam - z serii
   * w kolejce i potwierdzonych, wiec tak samo z telefonem i bez niego.
   */
  ustawPropozycje(ex) {
    const e = this.nakladka().ex[ex.id]
    const wpisy = ((e && e.entries) || []).filter((w) => !w.warmup)
    const ost = wpisy[wpisy.length - 1]
    this.state.weight = ost && ost.weight !== undefined ? ost.weight : ex.weight
    this.state.reps = ost && ost.reps ? ost.reps : ex.reps
  },

  stepReps(delta) {
    const value = this.state.reps + delta
    if (value < 1) return
    this.state.reps = value
    const ex = this.current()
    if (ex) this.state.editedFor = ex.id
    this.paintEdit()
  },

  /**
   * Ciężar zmieniany lokalnie, krokiem policzonym przez telefon.
   *
   * Pytanie telefonu o następną możliwą wartość przy każdym naciśnięciu
   * oznaczało czekanie na Bluetooth. Telefon przysyła teraz krok razem ze
   * stanem, a przy zapisie i tak przycina wynik do tego, co da się złożyć.
   */
  stepWeight(dir) {
    const ex = this.current()
    if (!ex || !ex.hasWeight) return
    const nowa = this.krokCiezaru(ex, this.state.weight, dir)
    if (nowa === null) return
    this.state.weight = nowa
    this.state.editedFor = ex.id
    this.paintEdit()
  },

  /**
   * Nastepny ciezar od podanego, po progach, ktore naprawde istnieja na tej
   * silowni. Zwraca null, gdy dalej nie ma czego zalozyc albo zdjac.
   *
   * Dodawanie stalego kroku policzonego raz ladowalo pomiedzy progami -
   * przy hantlach nie dawalo sie ustawic okraglych dwudziestu kilogramow,
   * choc taki hantel stoi na stojaku.
   */
  krokCiezaru(ex, waga, dir) {
    const drabinka = ex.ladder
    if (!drabinka || !drabinka.length) {
      const next = waga + dir * (ex.step || 2.5)
      if (next < 0) return null
      return Math.round(next * 100) / 100
    }

    const dol = drabinka[0]
    const gora = drabinka[drabinka.length - 1]

    // Poza drabinką chodzimy krokiem z jej brzegu - ale wracając, wchodzimy
    // na nią zamiast ją przeskoczyć. Inaczej plus po zejściu dziesięć kilo
    // pod listę skakał od razu na jej dno.
    if (waga < dol - 0.01 || waga > gora + 0.01) {
      const ponizej = waga < dol
      const twarde = ponizej ? (dir < 0 && ex.atMin) : (dir > 0 && ex.atMax)
      if (twarde) return null
      const krok = this.edgeGap(drabinka, ponizej ? -1 : 1) || ex.step || 2.5
      let next = waga + dir * krok
      if (ponizej && dir > 0 && next > dol) next = dol
      if (!ponizej && dir < 0 && next < gora) next = gora
      if (next <= 0) return null
      return Math.round(next * 100) / 100
    }

    let i = this.nearestRung(drabinka, waga)
    // Gdy stoimy dokładnie na progu, ruszamy na sąsiedni; gdy pomiędzy,
    // najbliższy próg w stronę ruchu jest już krokiem.
    if (Math.abs(drabinka[i] - waga) < 0.01) i += dir
    else if (dir > 0 && drabinka[i] < waga) i += 1
    else if (dir < 0 && drabinka[i] > waga) i -= 1

    if (i >= 0 && i < drabinka.length) return drabinka[i]

    // Koniec listy to nie koniec siłowni. Telefon mówi osobno, czy to
    // prawdziwa granica sprzętu - wtedy stoimy - czy tylko kraniec
    // okienka, które przysłał; wtedy idziemy dalej krokiem z brzegu.
    // Bez tego nie dało się zejść do rozgrzewkowych serii na gryfie.
    const twarde = i < 0 ? ex.atMin : ex.atMax
    if (twarde) return null
    const krok = this.edgeGap(drabinka, dir) || ex.step || 2.5
    const next = waga + dir * krok
    if (next <= 0) return null
    return Math.round(next * 100) / 100
  },

  /** Odstęp między dwoma ostatnimi progami po tej stronie drabinki. */
  edgeGap(drabinka, dir) {
    if (drabinka.length < 2) return 0
    const a = dir < 0 ? drabinka[0] : drabinka[drabinka.length - 1]
    const b = dir < 0 ? drabinka[1] : drabinka[drabinka.length - 2]
    return Math.abs(a - b)
  },

  nearestRung(drabinka, waga) {
    let best = 0
    for (let i = 1; i < drabinka.length; i++) {
      if (Math.abs(drabinka[i] - waga) < Math.abs(drabinka[best] - waga)) best = i
    }
    return best
  },

  /**
   * Koniec treningu, takze bez telefonu.
   *
   * Podsumowanie juz widziales, wiec czekanie na odpowiedz zostawialoby
   * ekran w zawieszeniu. Zamykamy u siebie, a telefon dowiaduje sie z
   * kolejki - razem z godzina, o ktorej naprawde skonczyles.
   */
  finishWorkout() {
    this.buzz()
    if (this.koniecTimer) { clearTimeout(this.koniecTimer); this.koniecTimer = null }
    // Od tej chwili wszystko, co wyszło wcześniej, jest nieaktualne.
    this.nowyBieg()
    const teraz = Date.now()
    const jednostka = this.jednostkaTreningu()
    // Numer jednostki jedzie z zakonczeniem tylko dla zegarka - po nim
    // wie, ze ta jednostka jest juz zrobiona, zanim telefon to potwierdzi.
    const koniec = { endedAt: teraz }
    if (jednostka) koniec.planId = jednostka.id
    this.queue('FINISH', koniec)
    // Data jak na telefonie: poczatek treningu. Zostaje w zapisie jednostek,
    // wiec nastepna jednostka zgadza sie takze po restarcie bez lacznosci.
    if (jednostka) jednostka.lastDone = Math.max(jednostka.lastDone || 0, this.state.startedAt || teraz)
    // Zakonczenie do pliku od razu, nie za chwile. Gdy aplikacja ginie
    // w tej chwili - a zegarek potrafil ja ubic wlasnie przy koncu treningu -
    // odlozony zapis nie zdazyl, zapis sesji byl juz skasowany i trening
    // na telefonie trwal dalej. Po powrocie zegarek widzial go jako swiezy.
    this.rememberOutboxNow()
    this.state.localId = null
    this.state.sessionId = null
    this.state.data = null
    this.state.acked = {}
    this.state.wasComplete = false
    this.state.firstState = true
    this.state.skipped = {}
    this.state.index = 0
    this.state.editedFor = null
    this.state.rirFor = null
    this.state.pausedLocal = null
    this.state.hrMin = 0
    this.state.hrMax = 0
    this.state.startedAt = 0
    this.state.planId = null
    this.forget()
    // Kolejna jednostka od razu, z wlasnych danych. Pytanie telefonu poleci
    // dopiero, gdy ten przyjmie zakonczenie - patrz [koniecDojechal].
    const znane = (this.state.units || []).length > 0
    if (znane) {
      this.przeliczKolejna()
      this.rememberUnits()
    }
    this.state.view = VIEW_START
    this.applyVisibility()
    this.paint()
    if (!znane) this.askUnits()
  },

  /** Jednostka trwajacego treningu z listy jednostek - po numerze, a gdy go nie ma, po nazwie. */
  jednostkaTreningu() {
    const units = this.state.units || []
    const id = this.state.planId || (this.state.data && this.state.data.planId)
    if (id) {
      const u = units.filter((x) => x.id === id)[0]
      if (u) return u
    }
    const nazwa = this.state.data && this.state.data.plan
    return nazwa ? units.filter((x) => x.name === nazwa)[0] || null : null
  },

  togglePause() {
    const chce = !this.paused()
    // Licznik zamrażamy albo odmrażamy tu, na zegarku. Telefon przyśle
    // swoją wersję przy najbliższej odpowiedzi i wtedy się zrównamy.
    this.state.sessionSec = this.sessionSeconds()
    this.state.sessionAt = Date.now()
    this.state.pausedLocal = chce
    this.paintControl()
    this.paintControlClock()
    this.remember()
    this.queue(chce ? 'PAUSE' : 'RESUME', {})
  },

  // ---- Rozmowa z telefonem -----------------------------------------------

  /**
   * Jednostki aktywnego programu.
   *
   * Odpowiedź nie jest stanem treningu, więc nie przechodzi przez [apply] -
   * ta uznałaby brak pola `active` za koniec treningu i zapętliła pytanie.
   */
  askUnits() {
    const bieg = this.bierzBieg()
    this.ask('UNITS', {})
      .then((reply) => {
        if (this.bierzBieg() !== bieg) return
        const data = (reply && reply.data) || reply || {}
        // Awaria po drodze - tak samo jak cisza: plan z pamieci.
        if (data.retry) throw TIMEOUT
        const units = data.units || []
        if (!units.length) {
          this.setMessage('Brak jednostek w programie')
          return
        }
        this.state.program = data.program || ''
        this.state.units = units
        // Wspolczynniki spalania i progi tetna jada razem z jednostkami,
        // nie tylko ze stanem treningu. Stan przychodzi dopiero wtedy, gdy
        // trening trwa - a kalorie maja liczyc sie od pierwszej serii, takze
        // w treningu zaczetym bez lacznosci.
        if (data.burn && data.burn.k1) this.state.burn = data.burn
        if (data.hrZones && data.hrZones.max) this.state.zones = data.hrZones
        const kolejna = units.filter((u) => u.next)[0]
        if (kolejna) this.state.nextUnit = kolejna.name
        // Zakonczenie, ktore jeszcze czeka w kolejce, telefonowi jest obce.
        this.przeliczKolejna()
        this.rememberUnits()
        this.setOffline(false)
        this.state.message = null
        this.state.view = VIEW_START
        this.applyVisibility()
        this.paint()
      })
      .catch((e) => {
        // Bez telefonu bierzemy plan z pamieci. Wybor jednostki to ostatnia
        // rzecz, ktora dzieli od rozpoczecia treningu - a ten ma sie dac
        // zaczac takze wtedy, gdy telefon zostal w szatni.
        if (this.restoreUnits()) {
          this.setOffline(true)
          this.state.message = null
          this.state.view = VIEW_START
          this.applyVisibility()
          this.paint()
          return
        }
        this.setMessage(this.whyFailed(e))
      })
  },

  startUnit(i) {
    const unit = (this.state.units || [])[i]
    if (!unit) return
    // Nowy trening - odpowiedzi dotyczące poprzedniego już nie wchodzą.
    this.nowyBieg()
    if (this.koniecTimer) { clearTimeout(this.koniecTimer); this.koniecTimer = null }
    this.state.view = VIEW_SETS
    this.state.phase = PHASE_SET
    this.state.phaseSince = Date.now()
    this.state.hrLog = []
    this.state.skipped = {}
    this.state.kcalBase = null
    this.state.kcal = 0
    this.state.burnKcal = 0
    this.state.burnAt = 0
    this.state.acked = {}
    this.readCalorie()

    // Sesje zaklada zegarek, a telefon ja tylko dopisuje, gdy uslyszy.
    // Wczesniej trzeba bylo czekac na jego odpowiedz, wiec bez lacznosci
    // trening nie zaczynal sie wcale.
    const plan = unit.plan || []
    if (!plan.length) {
      // Pamiec sprzed tej zmiany albo starszy telefon - nie ma z czego
      // zbudowac jednostki u siebie, wiec po staremu, z czekaniem.
      this.setMessage('Zaczynam ' + unit.name + '...')
      this.queue('START', { planId: unit.id }, true)
      return
    }

    const znacznik = 'w' + Date.now() + '-' + Math.floor(Math.random() * 100000)
    this.state.planId = unit.id
    this.state.localId = znacznik
    this.state.sessionId = znacznik
    this.state.startedAt = Date.now()
    this.state.sessionSec = 0
    this.state.sessionAt = Date.now()
    this.state.pausedLocal = null
    this.state.wasComplete = false
    this.state.firstState = false
    this.state.index = 0
    this.state.editedFor = null
    this.state.rirFor = null
    this.state.data = {
      active: true,
      sessionId: znacznik,
      plan: unit.name,
      paused: false,
      elapsedSec: 0,
      exercises: plan,
      totals: { sets: 0, reps: 0, tonnage: 0, complete: false }
    }
    const pierwsze = plan[0]
    if (pierwsze) {
      this.state.weight = pierwsze.weight
      this.state.reps = pierwsze.reps
    }
    this.state.message = null
    this.applyVisibility()
    this.paint()
    this.queue('START', {
      planId: unit.id, localId: znacznik, startedAt: this.state.startedAt
    })
    // Poczatek treningu tez od razu do pliku - z tego samego powodu co koniec.
    this.rememberOutboxNow()
    this.rememberNow()
    this.rememberHeavy()
  },

  /**
   * Żądanie do telefonu z własnym budzikiem.
   *
   * Obietnica z ZML nie ma własnego limitu: gdy aplikacja Zepp na
   * telefonie nie działa, wiadomość nie dociera nigdzie i nie wraca ani
   * odpowiedź, ani błąd. Ekran zostawał wtedy na "Łączę z telefonem..."
   * w nieskończoność, bez śladu, czego brakuje.
   */
  ask(method, params) {
    return new Promise((resolve, reject) => {
      let zamkniete = false
      const budzik = setTimeout(() => {
        if (zamkniete) return
        zamkniete = true
        reject(TIMEOUT)
      }, REQUEST_TIMEOUT_MS)

      const koniec = (fn) => (wynik) => {
        if (zamkniete) return
        zamkniete = true
        clearTimeout(budzik)
        fn(wynik)
      }

      messageBuilder
        .request({ method: method, params: params || {} })
        .then(koniec(resolve))
        .catch(koniec(reject))
    })
  },

  /**
   * Czego brakuje, powiedziane tak, żeby dało się z tym coś zrobić.
   *
   * Cisza znaczy, że wiadomość nie doszła nawet do telefonu - czyli
   * aplikacja Zepp nie działa albo zegarek stracił z nią łączność.
   * To zupełnie co innego niż odmowa, która przychodzi z odpowiedzią.
   */
  whyFailed(powod) {
    return powod === TIMEOUT ? 'Otwórz Zepp na telefonie' : 'Brak łączności z Zepp'
  },

  /**
   * Polecenie, które musi dojść do telefonu.
   *
   * Ląduje w kolejce i od razu próbuje wyjść. Jeśli nie wyjdzie, zostaje
   * i poleci przy następnej próbie, a trening toczy się dalej - zegarek
   * zna swój stan sam. Wcześniej każde takie polecenie szło wprost, więc
   * zerwana łączność gasiła ekran w środku serii i gubiła to, co zrobione.
   */
  queue(method, params, follow) {
    // Znacznik polecenia. Odpowiedź potrafi zginąć po tym, jak telefon już
    // ją wykonał - wtedy ponowienie zapisałoby tę samą serię drugi raz.
    // Telefon pamięta ostatnie znaczniki i powtórkę pomija.
    this.cidSeq = (this.cidSeq || 0) + 1
    const cid = (this.state.sessionId || 0) + '-' + Date.now() + '-' + this.cidSeq
    const poz = { m: method, p: params || {}, cid: cid, at: Date.now() }
    poz.p.cid = cid
    // Do ktorej sesji to polecenie nalezy. Bez tego zalegla seria sprzed
    // dwoch dni dopisalaby sie do treningu, ktory akurat trwa.
    if (this.state.sessionId) poz.p.sid = String(this.state.sessionId)
    // Tylko start jednostki podaza za telefonem: on wie, od ktorego
    // cwiczenia zaczac. Przy reszcie to zegarek wie lepiej, gdzie stoi.
    if (follow === true) poz.f = true
    // Poczatek i koniec treningu nie czekaja na cisze. Po nich palec zwykle
    // odpoczywa, ale aplikacje zamyka sie wlasnie wtedy - a telefon ma sie
    // dowiedziec o koncu treningu od razu, nie przy nastepnym otwarciu.
    if (method === 'START' || method === 'FINISH') this.pilne = true
    this.state.outbox = (this.state.outbox || []).concat([poz])
    // Sam stan treningu zapisuje ten, kto go zmienil - tutaj wystarczy
    // kolejka. Dwa zapisy na jedno nacisniecie to dwa razy za duzo.
    this.rememberOutbox()
    this.zmianaKolejki()
    // Pilne nie czeka na budzik ustawiony pod cisze po dotyku.
    if (this.pilne && this.drainTimer && !this.draining) {
      clearTimeout(this.drainTimer)
      this.drainTimer = null
    }
    this.scheduleDrain(0)
    return cid
  },

  /**
   * Wysyłka za chwilę, nigdy w uchwycie kliknięcia.
   *
   * Rozmowa z telefonem idzie przez Bluetooth i przy nieosiągalnym telefonie
   * potrafi zająć wątek na sekundy. Zrobiona wprost pod przyciskiem trzymała
   * go wciśniętym, dopóki nie wróciła - przycisk zostawał niebieski, a całe
   * klikanie wyglądało, jakby zegarek się kleił.
   *
   * @param za po ilu milisekundach; 0 znaczy "jak tylko wątek się zwolni".
   */
  scheduleDrain(za) {
    if (this.draining || this.drainTimer) return
    this.drainTimer = setTimeout(() => {
      this.drainTimer = null
      this.drain()
    }, za || 0)
  },

  /**
   * Wysyłka kolejki w kolejności nadania - paczkami, gdy telefon to umie.
   *
   * Trzy rodzaje odpowiedzi i każdy znaczy co innego:
   *
   * - wykonane: polecenie znika z kolejki,
   * - odmowa telefonu (`reject`): to jedno polecenie nie ma szans przejść,
   *   więc też znika - ale tylko ono,
   * - awaria po drodze (`retry`, brak odpowiedzi): polecenie zostaje.
   *
   * Dotąd ostatnie dwa wyglądały tak samo. Każdy błąd wyrzucał polecenie,
   * a "brak trwającego treningu" - całą kolejkę. Wystarczyło, że GymBro na
   * telefonie akurat nie słuchał, i serie znikały jedna po drugiej, choć
   * zegarek pokazywał je jako zrobione.
   */
  drain() {
    if (this.draining) return
    const kolejka = this.state.outbox || []
    if (!kolejka.length) {
      this.drainWait = 0
      this.setOffline(false)
      this.zmianaKolejki()
      return
    }
    // Palec pracuje - wysylka poczeka. Rozmowa przez Bluetooth to praca
    // w tym samym watku, ktory obsluguje dotyk: wyslanie zadania, odbior
    // i rozlozenie odpowiedzi. Puszczana zaraz po nacisnieciu trafiala
    // w nastepne nacisniecie - zapas po serii, plus przy ciezarze - i to
    // ono czekalo. Ekran i tak nie czeka na telefon, wiec nic nie traci.
    const odDotyku = this.ostatniDotyk ? Date.now() - this.ostatniDotyk : WYSYLKA_MS
    if (!this.pilne && odDotyku < WYSYLKA_MS) {
      this.scheduleDrain(WYSYLKA_MS - odDotyku)
      return
    }
    this.pilne = false
    this.draining = true
    // Cos czeka - choc paczka moze oproznic kolejke jednym zadaniem, jedna
    // wibracja po dojechaniu wszystkiego nadal sie nalezy.
    if (!this.state.syncWas) this.state.syncWas = kolejka.length
    const bieg = this.bierzBieg()
    const paczka = kolejka.slice(0, this.bezPaczek ? 1 : PACZKA)
    // Start w paczce znaczy, ze telefon dopiero pozna ten trening - stan
    // na koniec musi byc pelny, z drabinkami, bo innego jeszcze nie mamy.
    const pelny = paczka.some((x) => x.m === 'START')
    paczka.forEach((x) => { x.s = 1 })
    const pojedyncze = paczka.length === 1
    // Bez startu w paczce wystarczy samo potwierdzenie (lite 2): ekran liczy
    // sie z kolejki i z potwierdzonych serii, a stan od telefonu byl tylko
    // kilobajtem do rozlozenia przy kazdej serii. Telefon sprzed tej zmiany
    // zna tylko 1 i odpowie lekkim stanem - to tez dziala.
    const prosba = pojedyncze
      ? this.ask(paczka[0].m, pelny ? paczka[0].p : Object.assign({ lite: 2 }, paczka[0].p))
      : this.ask('BATCH', {
        lite: pelny ? 0 : 2,
        items: paczka.map((x) => ({ m: x.m, p: x.p }))
      })
    prosba
      .then((reply) => {
        this.draining = false
        const data = (reply && reply.data) || reply || {}
        if (data.retry) {
          this.niePoszlo()
          return
        }
        let wyniki
        let stan
        if (pojedyncze) {
          wyniki = [data.error ? { error: data.error } : { ok: true, setId: data.setId }]
          stan = data.error ? null : data
        } else if (data.results) {
          wyniki = data.results
          stan = data.state || null
        } else if (data.reject || /nieznan/.test(String(data.error || ''))) {
          // Telefon sprzed tej zmiany nie zna paczek - po jednym, jak dawniej.
          this.bezPaczek = true
          this.scheduleDrain(0)
          return
        } else {
          this.niePoszlo()
          return
        }
        this.przyjmij(paczka.slice(0, wyniki.length), wyniki, stan, bieg)
      })
      .catch(() => {
        this.draining = false
        this.niePoszlo()
      })
  },

  /** Telefon milczy - pytamy rzadziej, zamiast dobijać się w kółko. */
  niePoszlo() {
    this.setOffline(true)
    this.drainWait = Math.min(
      Math.max(DRAIN_MIN_MS, (this.drainWait || 0) * 2), DRAIN_MAX_MS
    )
    this.scheduleDrain(this.drainWait)
  },

  /** Wyniki od telefonu: kolejka krótsza, stan aktualny. */
  przyjmij(zalatwione, wyniki, stan, bieg) {
    const usun = {}
    zalatwione.forEach((poz, i) => {
      usun[poz.cid] = true
      const wynik = wyniki[i] || {}
      if (!wynik.ok) {
        // Odmowa dotyczy tego jednego polecenia. Reszta kolejki leci dalej.
        this.state.odrzucone = (this.state.odrzucone || 0) + 1
        return
      }
      let setId = wynik.setId || 0
      if (poz.m === 'SET') {
        // Telefon sprzed tej zmiany nie podaje numeru - wtedy to ostatnia
        // seria tego ćwiczenia w stanie, który właśnie przyszedł.
        if (!setId && stan) setId = this.ostatniaSeria(stan, poz.p.exerciseId)
        if (setId) this.potwierdzSerie(poz.cid, setId)
      }
      this.wpiszPotwierdzone(poz, setId)
    })
    this.state.outbox = (this.state.outbox || []).filter((x) => !usun[x.cid])
    this.rememberOutbox()
    this.zmianaKolejki()
    this.remember()
    this.drainWait = 0
    this.setOffline(false)

    const ostatnie = zalatwione[zalatwione.length - 1]
    const dalej = () => {
      if ((this.state.outbox || []).length) this.scheduleDrain(0)
    }
    // Serie potwierdzone samym "zrobione" zyja juz tylko w stanie zegarka -
    // z kolejki wlasnie zniknely. Bez zapisu planu restart strony cofalby
    // licznik do tego, co bylo w pamieci przed nimi.
    if (zalatwione.some((poz, i) => poz.m !== 'START' && poz.m !== 'FINISH' &&
      (wyniki[i] || {}).ok)) {
      this.rememberHeavyLater()
    }
    // Koniec treningu dojechal: dopiero teraz telefon zna nowa kolejnosc
    // jednostek. Pytanie zadane od razu po nacisnieciu "Zakoncz" wyprzedzalo
    // samo zakonczenie i wracalo z jednostka, ktora wlasnie zrobiles.
    if (zalatwione.some((poz, i) => poz.m === 'FINISH' && (wyniki[i] || {}).ok)) {
      this.koniecDojechal()
    }
    // Polecenie doszło, ale trening zdążył się skończyć albo zacząć nowy -
    // stan z tej odpowiedzi dotyczy już czegoś innego.
    if (!stan || this.bierzBieg() !== bieg) return dalej()
    // Samo potwierdzenie - nie ma czego rysowac, ekran juz to pokazuje.
    if (stan.ack) {
      this.przyjmijPotwierdzenie(stan)
      return dalej()
    }
    // Zaleglosc z innej sesji: dojezdza wczorajszy trening, a na ekranie
    // stoi dzisiejszy. Odpowiedz opisuje nie to, co widac, wiec wolno jej
    // zapisac historie, ale nie przestawic ekranu.
    const sid = ostatnie && ostatnie.p && ostatnie.p.sid
    if (sid && String(this.state.sessionId || '') !== String(sid)) return dalej()
    this.apply(stan, !!(ostatnie && ostatnie.f === true))
    dalej()
  },

  /**
   * Potwierdzenie bez stanu: telefon mowi tylko, ktory trening u niego trwa.
   *
   * Jedyne, co z tego wynika dla zegarka, to numer nadany treningowi zaczetemu
   * tutaj - po nim kolejne polecenia trafiaja do wlasciwej sesji. Trening
   * cudzy (inny znacznik) nie zmienia niczego.
   */
  przyjmijPotwierdzenie(stan) {
    if (this.state.localId && stan.localId === this.state.localId && stan.sessionId) {
      const stary = this.state.localId
      this.state.sessionId = stan.sessionId
      this.state.localId = null
      if (this.state.data) this.state.data.sessionId = stan.sessionId
      this.przepiszKolejke(stary, stan.sessionId)
      this.remember()
      this.rememberHeavyLater()
    }
    if (String(stan.sessionId) !== String(this.state.sessionId || '')) return
    // Telefon dogonil wstrzymanie ustawione na zegarku - jak w [apply].
    if (this.state.pausedLocal !== null && stan.paused !== undefined &&
      !!stan.paused === this.state.pausedLocal) {
      this.state.pausedLocal = null
      this.remember()
    }
    // Suma kontrolna: telefon zna serie, ktorych zegarek nie zna - dopisane
    // albo poprawione na telefonie w trakcie treningu. Wtedy raz pelny stan.
    if (stan.sets === undefined) return
    const moje = this.potwierdzoneSerie()
    if (moje.sets === stan.sets && moje.reps === stan.reps) return
    const teraz = Date.now()
    if (this.pelnyStanAt && teraz - this.pelnyStanAt < 10000) return
    this.pelnyStanAt = teraz
    this.send('STATE', {}, false)
  },

  /** Serie, ktore telefon juz potwierdzil - bez tych, ktore czekaja w kolejce. */
  potwierdzoneSerie() {
    let sets = 0
    let reps = 0
    ;((this.state.data && this.state.data.exercises) || []).forEach((e) => {
      ;(e.entries || []).forEach((w) => {
        if (!w.id) return
        sets += 1
        reps += w.reps || 0
      })
    })
    return { sets: sets, reps: reps }
  },

  /**
   * Telefon przyjal koniec treningu. Ekran wyboru dostaje swieza liste
   * jednostek - o ile wlasnie na nim stoimy, a nie w nowym treningu.
   */
  koniecDojechal() {
    if (this.state.data && this.state.data.active) return
    if (this.state.view !== VIEW_START) return
    this.askUnits()
  },

  /**
   * Ktora jednostka wypada teraz - ta sama regula co na telefonie: najdawniej
   * robiona, a nigdy nierobiona przed wszystkimi.
   *
   * Liczone na zegarku z dat, ktore przyslal telefon, plus treningow
   * zakonczonych tutaj, o ktorych telefon jeszcze nie wie. Dotad nastepna
   * jednostka przychodzila wylacznie od telefonu, a ten dowiadywal sie
   * o koncu treningu dopiero z kolejki - wiec po zakonczeniu B zegarek przez
   * minute proponowal B, a bez lacznosci az do powrotu telefonu.
   */
  przeliczKolejna() {
    const units = this.state.units || []
    // Telefon sprzed tej zmiany nie podaje dat - zostaje jego wybor.
    if (!units.length || !units.some((u) => u.lastDone !== undefined)) return
    const konce = {}
    ;(this.state.outbox || []).forEach((x) => {
      if (x.m === 'FINISH' && x.p && x.p.planId) {
        konce[x.p.planId] = Math.max(konce[x.p.planId] || 0, x.p.endedAt || x.at || 1)
      }
    })
    const ostatnio = (u) => Math.max(u.lastDone || 0, konce[u.id] || 0)
    let kolejna = units[0]
    units.forEach((u) => { if (ostatnio(u) < ostatnio(kolejna)) kolejna = u })
    units.forEach((u) => { u.next = u === kolejna })
    this.state.nextUnit = kolejna.name
  },

  /** Numer najnowszej serii ćwiczenia w stanie od telefonu. */
  ostatniaSeria(stan, exerciseId) {
    const ex = ((stan && stan.exercises) || []).filter((e) => e.id === exerciseId)[0]
    const wpisy = (ex && ex.entries) || []
    let max = 0
    wpisy.forEach((w) => { if (w.id > max) max = w.id })
    return max
  },

  /**
   * Seria dostała numer. Polecenia, które czekają na nią po znaczniku,
   * dostają go wprost - wtedy dojdą także do telefonu, który w międzyczasie
   * zapomniał znaczniki, bo na przykład się zrestartował.
   */
  potwierdzSerie(cid, setId) {
    const acked = this.state.acked || {}
    acked[cid] = setId
    const klucze = Object.keys(acked)
    if (klucze.length > ACKED_MAX) {
      klucze.slice(0, klucze.length - ACKED_MAX).forEach((k) => { delete acked[k] })
    }
    this.state.acked = acked
    ;(this.state.outbox || []).forEach((x) => {
      if (x.p && x.p.ref === cid) {
        x.p.setId = setId
        delete x.p.ref
      }
    })
  },

  /**
   * Znacznik "coś jeszcze nie dojechało".
   *
   * Celowo nie komunikat na cały ekran. Tamten gasił trening i jedynym
   * wyjściem był przycisk Odśwież - a przecież ćwiczyć dalej można.
   * Żółta godzina mówi tyle, ile trzeba, i nie zabiera miejsca.
   */
  setOffline(czy) {
    if (this.state.offline === !!czy) return
    this.state.offline = !!czy
    if (this.w && this.w.clock) {
      this.kolor(this.w.clock, czy ? AMBER : DIM)
    }
    if (this.state.built && !this.state.message) {
      if (this.state.view === VIEW_STATS) this.paintSync()
      else if (this.state.view === VIEW_START) this.paintStart()
    }
  },

  /**
   * Numer bieżącej sesji na zegarku.
   *
   * Rośnie przy każdym zaczęciu i zakończeniu treningu. Odpowiedź telefonu
   * wysłana przed tą chwilą jest już nieaktualna, choćby wróciła sekundę
   * później - a wracała, niosła "trening trwa" i przerzucała ekran z wyboru
   * jednostki z powrotem na ćwiczenia. Stąd to mignięcie po zakończeniu.
   */
  bierzBieg() {
    return this.sesjaGen || 0
  },

  nowyBieg() {
    this.sesjaGen = (this.sesjaGen || 0) + 1
  },

  send(method, params, follow) {
    const bieg = this.bierzBieg()
    this.ask(method, params)
      .then((reply) => {
        const data = (reply && reply.data) || reply || {}
        // Awaria po drodze to to samo co brak odpowiedzi.
        if (data.retry) throw TIMEOUT
        // Odpowiedź dotyczy treningu, którego już nie ma.
        if (this.bierzBieg() !== bieg) return

        if (data.error) {
          // W trakcie treningu odmowa nie zabiera ekranu - trening idzie dalej.
          if (this.state.data && this.state.data.active) return
          this.setMessage(data.error)
          return
        }
        this.apply(data, follow === true)
      })
      .catch((e) => {
        // Przy trwającym treningu nie zabieramy ekranu. Jedna nieudana
        // odpowiedź nie znaczy, że nie wiadomo, co się dzieje.
        if (this.state.data && this.state.data.active) {
          this.setOffline(true)
          return
        }
        // Bez treningu, ale z zapamiętanym planem jest co pokazać: wybór
        // jednostki. Inaczej cały tryb bez telefonu jest nieosiągalny, bo
        // droga do niego wiodła przez udaną odpowiedź telefonu.
        if (this.restoreUnits()) {
          this.setOffline(true)
          this.state.message = null
          this.state.view = VIEW_START
          this.applyVisibility()
          this.paint()
          return
        }
        this.setMessage(this.whyFailed(e))
      })
  },

  apply(data, follow) {
    // Zaleglosc dopisana na telefonie od razu jako zamknieta - bez stanu.
    if (!data || data.backfill) return
    if (!data.active) {
      // Telefon nie zna trwajacego treningu. Gdy zegarek ma swoj, a w kolejce
      // czeka cos dla niego - START, ktory go dopiero zalozy, albo FINISH -
      // telefon po prostu jeszcze nie wie. Taka odpowiedz konczyla dotad
      // trening na zegarku w polowie serii.
      if (this.state.sessionId && this.czekaDla(this.state.sessionId)) return
      this.state.data = data
      // Czyścimy ślady po zakończonym treningu, żeby kolejny zaczynał się
      // od zera także bez zamykania aplikacji.
      this.state.wasComplete = false
      this.state.firstState = true
      this.state.skipped = {}
      this.state.index = 0
      this.state.sessionId = null
      this.state.localId = null
      this.state.startedAt = 0
      this.state.acked = {}
      this.state.rirFor = null
      this.state.editedFor = null
      if (data.next && data.next.name) this.state.nextUnit = data.next.name
      this.state.hrMin = 0
      this.state.hrMax = 0
      this.forget()
      this.state.view = VIEW_START
      // Zegarek sam zaczyna trening - inaczej "klikam tylko na zegarku"
      // kończyłoby się sięganiem po telefon przy pierwszej serii.
      this.askUnits()
      return
    }
    const list = data.exercises || []
    if (!list.length) {
      this.setMessage('Jednostka bez ćwiczeń')
      return
    }

    // Czyj to trening. Telefon pokazuje ten, ktory u niego trwa - a to nie
    // zawsze nasz. Trening zaczety na zegarku, ktorego START jeszcze nie
    // dojechal albo dojechal jako zaleglosc, ma na telefonie inny numer
    // niz trening, ktory tam akurat trwa. Przyjecie tamtego za swoj
    // przerzucalo zegarek na stary trening - i jego serie szly tam.
    const sid = data.sessionId || null
    if (this.state.sessionId && this.state.sessionId !== sid) {
      if (this.state.localId) {
        if (data.localId !== undefined && data.localId !== this.state.localId) return
      } else if (this.czekaDla(this.state.sessionId)) {
        return
      }
    }

    // Odpowiedz bez drabinek i reszty stalych rzeczy - dokladamy je
    // z poprzedniego stanu tego samego cwiczenia.
    const prev = this.state.data
    const byla = this.current()
    const poId = {}
    ;((prev && prev.exercises) || []).forEach((e) => { poId[e.id] = e })
    data.exercises = list.map((e) => (poId[e.id] ? Object.assign({}, poId[e.id], e) : e))
    if (prev) {
      ;['hrZones', 'burn', 'next', 'plan'].forEach((k) => {
        if (data[k] === undefined && prev[k] !== undefined) data[k] = prev[k]
      })
    }
    this.state.data = data
    this.zmienione()
    this.rememberHeavyLater()

    // Telefon dogonił to, co ustawiliśmy na zegarku - od tej chwili znowu
    // on jest źródłem. Dopóki nie dogonił, jego sekundy są nieaktualne.
    if (this.state.pausedLocal !== null && !!data.paused === this.state.pausedLocal) {
      this.state.pausedLocal = null
    }
    if (this.state.pausedLocal === null) {
      this.state.sessionSec = data.elapsedSec || 0
      this.state.sessionAt = Date.now()
    }
    // Odpowiedz, ktora ich nie niesie - a taka jest kazda odpowiedz na
    // zakonczenie treningu - nie moze ich zabierac. Wyzerowane tutaj,
    // znikaly na cale nastepne treningi i kalorie staly na kresce.
    if (data.hrZones && data.hrZones.max) this.state.zones = data.hrZones
    if (data.burn && data.burn.k1) {
      const stare = this.state.burn
      this.state.burn = data.burn
      // Do zapisu jednostek, bo ten przezywa koniec treningu. Tylko przy
      // zmianie: zapis do pamieci trwalej to zapis do pliku.
      if (!stare || stare.k0 !== data.burn.k0 || stare.k1 !== data.burn.k1) {
        this.rememberUnits()
      }
    }
    // Nastepna jednostka jedzie w kazdej odpowiedzi, nie tylko przy
    // zakonczeniu: trening zamkniety na telefonie nie daje zegarkowi
    // odpowiedzi z FINISH, wiec podsumowanie pokazywalo wartosc sprzed sesji.
    if (data.next && data.next.name) this.state.nextUnit = data.next.name
    if (data.planId) this.state.planId = data.planId

    // Zgaszenie ekranu ubija aplikację, więc pierwsza odpowiedź po starcie
    // może dotyczyć treningu, który trwa od godziny. Odtwarzamy wtedy to,
    // co o nim wiemy, i nie dajemy telefonowi przestawić nas na początek
    // planu - on zna tylko zapisane serie, nie to, gdzie staliśmy.
    let trzymaj = true
    if (this.state.sessionId !== sid) {
      // Nasza sesja wlasnie dostala numer od telefonu. To wciaz ta sama
      // sesja, wiec nie wolno zaczynac od poczatku planu: zegarek wie,
      // na ktorym cwiczeniu stoi, telefon dopiero sie tego uczy.
      const nasza = !!this.state.localId
      const staryZnacznik = this.state.localId
      this.state.sessionId = sid
      if (nasza) {
        this.state.localId = null
        follow = false
        this.przepiszKolejke(staryZnacznik, sid)
        this.remember()
      } else if (this.state.firstState && this.restore(sid, false)) {
        follow = false
      } else {
        this.state.skipped = {}
        this.state.index = 0
        trzymaj = false
      }
      // Godzina startu do pilnowania osmiu godzin - z czasu, ktory podaje
      // telefon, bo trening zaczety tam nie ma jej na zegarku.
      if (!this.state.startedAt) {
        this.state.startedAt = Date.now() - (data.elapsedSec || 0) * 1000
      }
      this.remember()
    }

    // Na tym samym cwiczeniu, nie na tym samym miejscu listy. Gdy kolejnosc
    // w odpowiedzi rozni sie od tej na zegarku - plan zmieniony na telefonie,
    // plan z pamieci sprzed zmiany - ten sam numer pozycji wskazywal inne
    // cwiczenie, a serie zapisywaly sie pod cudza nazwa.
    if (trzymaj && byla && !follow) {
      const i = data.exercises.findIndex((e) => e.id === byla.id)
      if (i >= 0) this.state.index = i
    }

    const settled = this.pendingCount() === 0
    if (follow && settled && data.current !== undefined) {
      this.state.index = Math.min(data.current, list.length - 1)
    }
    if (this.state.index >= list.length) this.state.index = list.length - 1

    const ex = data.exercises[this.state.index]
    // Propozycja telefonu tylko wtedy, gdy sam niczego nie ustawiłeś.
    // Wcześniej każda odpowiedź kasowała ręcznie wybrany ciężar: wpisane
    // 19.8 wracało do 20 przy pierwszym lepszym odświeżeniu stanu.
    if (this.state.editedFor !== ex.id) {
      this.state.weight = ex.weight
      this.state.reps = ex.reps
    }
    this.state.message = null
    if (this.state.view === VIEW_START) this.state.view = VIEW_SETS

    const complete = this.localComplete()
    // Pierwszy odczyt po otwarciu tylko zapamiętuje stan. Bez tego wejście
    // w trening, który już ma plan wykonany, witało ekranem podsumowania -
    // a to właśnie wyglądało jak podsumowanie na dzień dobry.
    const justCompleted = complete && !this.state.wasComplete && !this.state.firstState
    // Pytanie o zapas do ostatniej serii jeszcze wisi - podsumowanie pokaze
    // zegarek sam, po odpowiedzi albo po chwili. Odpowiedz telefonu zabierala
    // je dotad sprzed palca w pol sekundy.
    if (justCompleted && settled && this.koniecTimer) {
      this.state.firstState = false
      this.applyVisibility()
      this.paint()
      return
    }
    if (settled) {
      this.state.wasComplete = complete
      this.state.firstState = false
    }

    if (justCompleted && settled && COLUMN.indexOf(this.state.view) >= 0) {
      this.showView(VIEW_DONE)
      return
    }
    this.applyVisibility()
    this.paint()
  }
})
