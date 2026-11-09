/*
 * Przejście przez aplikację zegarka bez zegarka.
 *
 * Ładuje prawdziwy `page/index.js`, podstawia zaślepki pod `@zos/*` i pod
 * telefon, a potem przechodzi scenariusze tak, jak robi to palec: naciśnij,
 * przesuń, wróć. Powstało po tym, jak dodatkowa seria po ukończeniu planu
 * zaczęła przeskakiwać na inne ćwiczenie i wyszło to dopiero na siłowni.
 *
 * Nie zastąpi oczu - nie powie, że napis się obcina ani że przejście mruga.
 * Pilnuje tego, co da się sprawdzić bez patrzenia: przepływu ekranów, licznika
 * serii, tego, na którym ćwiczeniu stoimy, i tego, że żaden klawisz nie
 * wypada do systemu.
 *
 * Uruchomienie: node tools/check.js
 */

const fs = require('fs')
const path = require('path')

// ---- Zaślepki systemu -----------------------------------------------------

const DEVICE_WIDTH = 480
const DEVICE_HEIGHT = 480

// Kolejka wywolan zwrotnych animacji. Na poziomie modulu, bo fabryka
// widgetow nie widzi zmiennych z loadPage().
const ANIMACJE = { wolne: false, czeka: [] }

function makeWidget(type, options) {
  const w = {
    type: type,
    attrs: Object.assign({}, options),
    onClick: options && options.click_func,
    writes: 0,
    visible: true,
    setProperty(prop, value) {
      this.writes += 1
      if (prop === 'VISIBLE') {
        this.visible = !!value
        return
      }
      if (prop === 'ANIM') {
        this.anim = value
        // Przejście animuje się natychmiast; pętla przewijania nazwy nie ma
        // końca, więc jej wywołania zwrotnego nie ma czego udawać.
        //
        // W trybie `wolneAnimacje` wywołanie zwrotne czeka w kolejce zamiast
        // odpalić się od razu. Natychmiastowe kończenie ukrywało całą klasę
        // błędów: na zegarku animacja trwa 240 ms i w tym czasie da się
        // machnąć palcem drugi raz, a tu nie dało się tego odtworzyć wcale.
        if (typeof value.anim_complete_func === 'function') {
          if (ANIMACJE.wolne) ANIMACJE.czeka.push(value.anim_complete_func)
          else value.anim_complete_func()
        }
        return
      }
      if (prop === 'ANIM_STATUS') {
        this.animStatus = value
        if (!value) this.anim = null
        return
      }
      if (prop === 'MORE') {
        Object.assign(this.attrs, value)
        return
      }
      this.attrs[prop] = value
    },
    getProperty(prop) {
      return this.attrs[prop]
    }
  }
  return w
}

const hmUI = {
  widget: { TEXT: 'TEXT', BUTTON: 'BUTTON', FILL_RECT: 'FILL_RECT' },
  prop: {
    MORE: 'MORE', VISIBLE: 'VISIBLE', ANIM: 'ANIM',
    X: 'X', Y: 'Y', W: 'W', H: 'H', ALPHA: 'ALPHA'
  },
  align: { CENTER_H: 'CH', CENTER_V: 'CV', LEFT: 'L', RIGHT: 'R' },
  text_style: { NONE: 'NONE', WRAP: 'WRAP', ELLIPSIS: 'ELLIPSIS' },
  createWidget: makeWidget,
  // Pomiar napisu, tak jak robi to system zegarka. Szerokosc znaku zalezy
  // od pisma, wiec scenariusz moze ja ustawic i sprawdzic obie drogi:
  // z pomiarem i bez niego, gdy system go nie udostepnia.
  getTextLayout(text, opcje) {
    if (hmUI.brakPomiaru) throw new Error('brak pomiaru')
    return {
      width: Math.ceil(String(text).length * hmUI.szerokoscZnaku),
      height: 30
    }
  },
  szerokoscZnaku: 9,
  brakPomiaru: false
}

const GESTURE_LEFT = 'left'
const GESTURE_RIGHT = 'right'
const GESTURE_UP = 'up'
const GESTURE_DOWN = 'down'
const KEY_HOME = 'home'
const KEY_BACK = 'back'
const KEY_SELECT = 'select'
const KEY_SHORTCUT = 'shortcut'
const KEY_EVENT_CLICK = 'click'

const VIBRATOR_SCENE_SHORT_LIGHT = 'light'
const VIBRATOR_SCENE_SHORT_MIDDLE = 'middle'
const VIBRATOR_SCENE_SHORT_STRONG = 'strong'
const VIBRATOR_SCENE_DURATION = 'duration'
const VIBRATOR_SCENE_DURATION_LONG = 'duration-long'
const VIBRATOR_SCENE_NOTIFICATION = 'notification'
const VIBRATOR_SCENE_STRONG_REMINDER = 'reminder'

const buzzes = []

class HeartRate {
  getCurrent() { return 120 }
  getLast() { return 120 }
  onCurrentChange() {}
  offCurrentChange() {}
}

class Calorie {
  getCurrent() { return Calorie.wartosc }
  onChange() {
    // Nie kazda wersja systemu musi to mieć - kod ma to przezyc.
    if (Calorie.bezNasluchu) throw new Error('brak nasluchu')
  }
  offChange() {}
}
Calorie.wartosc = 300
Calorie.bezNasluchu = false

const LICZNIK = { ile: 0, bajtow: 0, klucze: {} }
class LocalStorage {
  constructor() { this.dane = LocalStorage.dane }
  setItem(k, v) { const t = String(v); LICZNIK.ile++; LICZNIK.bajtow += t.length; LICZNIK.klucze[k] = (LICZNIK.klucze[k] || 0) + 1; this.dane[k] = t }
  getItem(k, d) { return this.dane[k] === undefined ? d : this.dane[k] }
  removeItem(k) { delete this.dane[k]; return true }
  clear() { Object.keys(this.dane).forEach((k) => delete this.dane[k]) }
}
LocalStorage.dane = {}

class Vibrator {
  start(option) { buzzes.push(option && option.mode) }
  stop() {}
}

// ---- Zaślepka telefonu ----------------------------------------------------

/**
 * Odpowiada dokładnie tak jak `WatchApi` na telefonie, z tą samą regułą
 * wyboru bieżącego ćwiczenia: pierwsze nieukończone, a gdy wszystkie są
 * zrobione - ostatnie. To właśnie ta reguła wywracała dodatkowe serie.
 */
function makePhone() {
  const plan = [
    { id: 1, name: 'Przysiad ze sztangą', sets: 2, weight: 80, reps: 8, step: 2.5, restSec: 180, repsFrom: 8, repsTo: 10 },
    { id: 2, name: 'Wyciskanie sztangi leżąc', sets: 2, weight: 70, reps: 10, step: 2.5, restSec: 180, repsFrom: 8, repsTo: 10 },
    { id: 3, name: 'OHP', sets: 2, weight: 36, reps: 10, step: 2.5, restSec: 105, repsFrom: 8, repsTo: 10 }
  ]
  // Najdluzsza nazwa, jaka ma sens w planie silowym - do sprawdzenia tarczy.
  // Osiem cwiczen, zeby sprawdzic, co robi lista dluzsza niz ekran.
  const dodatkowe = [4, 5, 6, 7, 8].map((id) => ({
    id: id, name: "Cwiczenie numer " + id, sets: 2, weight: 20, reps: 10,
    step: 2.5, restSec: 90, repsFrom: 8, repsTo: 10
  }))
  const DLUGA = 'Wyciskanie hantli nad glowa w siadzie z oparciem'
  const phone = {
    active: false,
    done: {},
    log: [],
    polecenia: [],
    seen: {},
    localIds: {},
    starty: [],
    gubiOdpowiedz: false,
    wstrzymane: false,
    zatrzymane: [],
    paused: false,
    wpisy: {},
    kolejnyId: 1,
    dluga: false,
    setDlugaNazwa(on) { phone.dluga = on },
    duzo: false,
    setDuzoCwiczen(on) { phone.duzo = on },
    glucha: false,
    twardyDol: false,
    setTwardyDol(on) { phone.twardyDol = on },
    ladderOf(ex) {
      const w = ex.weight
      const out = []
      for (let i = -3; i <= 3; i++) out.push(Math.round((w + i * 2.5) * 100) / 100)
      return out
    },
    setGlucha(on) { phone.glucha = on },
    // Odpowiedzi czekaja, zamiast wracac od razu. Tak zachowuje sie telefon,
    // gdy odpowiedz juz leci, a w miedzyczasie dzieje sie cos na zegarku.
    wstrzymajOdpowiedzi(on) { phone.wstrzymane = on },
    // Jedna, najstarsza. Objaw widac wlasnie POMIEDZY odpowiedziami,
    // wiec zwolnienie wszystkich naraz by go przykrylo.
    puscJedna() {
      const f = phone.zatrzymane.shift()
      if (f) f()
      return !!f
    },
    puscOdpowiedzi() {
      const czekajace = phone.zatrzymane.splice(0)
      czekajace.forEach((f) => f())
      return czekajace.length
    },
    // Telefon wykonuje polecenie, ale odpowiedz ginie po drodze.
    // Najgrozniejszy przypadek: zegarek nie wie, czy doszlo.
    setGubiOdpowiedz(on) { phone.gubiOdpowiedz = on },
    DLUGA: DLUGA,
    reset() {
      phone.active = false
      phone.done = {}
      phone.log = []
      phone.wpisy = {}
      phone.kolejnyId = 1
    },
    // Plan jednostki tak, jakby trening dopiero sie zaczynal.
    podglad() {
      return plan.map((e) => ({
        id: e.id, name: e.name, done: 0, sets: e.sets,
        weight: e.weight, reps: e.reps, step: e.step,
        repsFrom: e.repsFrom, repsTo: e.repsTo, entries: [],
        ladder: phone.ladderOf(e), atMin: false, atMax: false,
        restSec: e.restSec, hasWeight: true, unit: 'kg'
      }))
    },
    state() {
      if (!phone.active) return { active: false }
      const lista = phone.duzo ? plan.concat(dodatkowe) : plan
      const exercises = lista.map((e) => ({
        id: e.id, name: phone.dluga && e.id === 1 ? DLUGA : e.name,
        done: phone.done[e.id] || 0,
        sets: e.sets, weight: e.weight, reps: e.reps, step: e.step,
        repsFrom: e.repsFrom, repsTo: e.repsTo,
        entries: (phone.wpisy[e.id] || []).map((w) => ({
          id: w.id, weight: w.weight, reps: w.reps,
          rir: w.rir === undefined ? -1 : w.rir, warmup: false
        })),
        ladder: phone.ladderOf(e),
        atMin: phone.twardyDol,
        atMax: false,
        restSec: e.restSec, hasWeight: true, unit: 'kg'
      }))
      let current = exercises.findIndex((e) => e.done < e.sets)
      if (current < 0) current = exercises.length - 1
      const sets = exercises.reduce((n, e) => n + e.done, 0)
      return {
        active: true,
        plan: 'A - sztanga',
        elapsedMin: 10,
        elapsedSec: 600,
        paused: !!phone.paused,
        sessionId: 101,
        localId: phone.lokalny || '',
        next: { id: 2, name: 'B - hantle' },
        hrZones: { max: 187, easy: 112, aerobic: 131, tempo: 150, anaerobic: 168 },
        current: current,
        totals: {
          sets: sets, reps: sets * 8, tonnage: sets * 640,
          complete: exercises.every((e) => e.done >= e.sets)
        },
        exercises: exercises
      }
    },
    // Telefon sprzed paczek: BATCH to dla niego nieznane zadanie.
    starePaczki: false,
    paczki: 0,
    // Znaczniki polecen -> numer serii, ktora zalozyly. Jak w WatchApi.
    serieZnacznikow: {},
    setIdOf(params) {
      if (params.setId) return params.setId
      if (params.ref) return phone.serieZnacznikow[params.ref] || 0
      return 0
    },
    request(req) {
      if (req.method === 'BATCH') {
        if (phone.starePaczki) return { error: 'nieznana sciezka', reject: true }
        phone.paczki += 1
        const results = (req.params.items || []).map((it) => {
          const r = phone.wykonaj({ method: it.m, params: it.p || {} })
          if (r && r.error) return { error: r.error }
          const w = { ok: true }
          if (r && r.setId) w.setId = r.setId
          return w
        })
        return { results: results, state: phone.state() }
      }
      return phone.wykonaj(req)
    },
    wykonaj(req) {
      const cid = req.params && req.params.cid
      if (cid && phone.seen[cid]) {
        const st = phone.state()
        if (phone.serieZnacznikow[cid]) st.setId = phone.serieZnacznikow[cid]
        return st
      }
      const wynik = phone.obsluz(req)
      // Jak w WatchApi: znacznik zapamietany dopiero po udanym wykonaniu.
      if (cid && !(wynik && wynik.error)) {
        phone.seen[cid] = true
        if (wynik && wynik.setId) phone.serieZnacznikow[cid] = wynik.setId
      }
      return wynik
    },
    obsluz(req) {
      if (req.params && String(req.params.sid || '').indexOf('w-obcy') === 0) {
        return { error: 'brak trwajacego treningu', reject: true }
      }
      switch (req.method) {
        case 'STATE':
          return phone.state()
        case 'UNITS':
          // Jednostka niesie caly swoj plan: z tego zegarek sklada trening,
          // gdy telefonu nie ma w zasiegu.
          return {
            program: 'FBW',
            hrZones: { max: 187, easy: 112, aerobic: 131, tempo: 150, anaerobic: 168 },
            burn: { k0: -6.1, k1: 0.116 },
            units: [
              { id: 1, name: 'A - sztanga', exercises: 3, next: false, plan: phone.podglad() },
              { id: 2, name: 'B - hantle', exercises: 3, next: true, plan: phone.podglad() },
              { id: 3, name: 'C - wyciągi', exercises: 3, next: false, plan: phone.podglad() }
            ]
          }
        case 'START': {
          // Sesja moze przyjsc ze znacznikiem nadanym na zegarku. Powtorzone
          // zgloszenie trafia na te sama sesje i nie zaklada drugiej - tak
          // samo jak w telefonie, gdzie pilnuje tego indeks jednoznaczny.
          const lid = req.params.localId
          if (lid && phone.localIds[lid]) return phone.state()
          if (lid) phone.localIds[lid] = true
          phone.active = true
          phone.lokalny = lid || ''
          phone.done = {}
          phone.wpisy = {}
          phone.starty.push({
            localId: lid || null,
            startedAt: req.params.startedAt || null,
            planId: req.params.planId
          })
          return phone.state()
        }
        case 'SET': {
          const id = req.params.exerciseId
          phone.done[id] = (phone.done[id] || 0) + 1
          const wpis = {
            id: phone.kolejnyId++, weight: req.params.weight, reps: req.params.reps,
            rir: req.params.rir === undefined || req.params.rir < 0 ? undefined : req.params.rir
          }
          if (!phone.wpisy[id]) phone.wpisy[id] = []
          phone.wpisy[id].push(wpis)
          phone.log.push({
            id: id, weight: wpis.weight, reps: wpis.reps,
            doneAt: req.params.doneAt || null
          })
          const st = phone.state()
          st.setId = wpis.id
          return st
        }
        case 'EDIT': {
          const setId = phone.setIdOf(req.params)
          let trafiony = null
          Object.keys(phone.wpisy).forEach((k) => {
            phone.wpisy[k].forEach((w) => { if (w.id === setId) trafiony = w })
          })
          if (!trafiony) return { error: 'nie ma takiej serii' }
          if (req.params.weight !== undefined) trafiony.weight = req.params.weight
          if (req.params.reps !== undefined) trafiony.reps = req.params.reps
          if (req.params.rir !== undefined) trafiony.rir = req.params.rir
          phone.log.push({
            setId: setId, weight: trafiony.weight, reps: trafiony.reps,
            rir: trafiony.rir
          })
          return phone.state()
        }
        case 'RIR': {
          // Zapas dla ostatniej serii cwiczenia - bez numeru serii, tak jak
          // robi to telefon. Zegarek pyta o zapas zanim pozna ten numer.
          const eid = req.params.exerciseId
          const lista = phone.wpisy[eid] || []
          const wskazany = phone.setIdOf(req.params)
          const ost = wskazany ? lista.filter((w) => w.id === wskazany)[0] : lista[lista.length - 1]
          if (!ost) return { error: 'nie ma takiej serii' }
          ost.rir = req.params.rir
          phone.log.push({ setId: ost.id, weight: ost.weight, reps: ost.reps, rir: ost.rir })
          return phone.state()
        }
        case 'DELETE': {
          const sid = phone.setIdOf(req.params)
          Object.keys(phone.wpisy).forEach((k) => {
            const i = phone.wpisy[k].findIndex((w) => w.id === sid)
            if (i < 0) return
            phone.wpisy[k].splice(i, 1)
            phone.done[k] = Math.max(0, (phone.done[k] || 0) - 1)
          })
          return phone.state()
        }
        case 'UNDO': {
          const id = req.params.exerciseId
          phone.done[id] = Math.max(0, (phone.done[id] || 0) - 1)
          if (phone.wpisy[id]) phone.wpisy[id].pop()
          return phone.state()
        }
        case 'FINISH':
          phone.active = false
          phone.endedAt = req.params.endedAt || null
          return { active: false, next: { id: 2, name: 'B - hantle' } }
        case 'PAUSE':
          phone.paused = true
          phone.polecenia.push('PAUSE')
          return phone.state()
        case 'RESUME':
          phone.paused = false
          phone.polecenia.push('RESUME')
          return phone.state()
        default:
          return { error: 'nieznane zadanie' }
      }
    }
  }
  return phone
}

// ---- Ładowanie strony -----------------------------------------------------

/**
 * Usługa towarzysząca z `app-side/index.js`, z zaślepkami pod Zepp OS.
 *
 * Reszta harnessu zaczyna udawać telefon dopiero za mostem: `messageBuilder`
 * w [loadPage] odpowiada od razu, więc ten plik nie wykonywał się nigdy.
 * A to przez niego przechodzi każde polecenie z zegarka i to tutaj łatwo
 * o literówkę, której nic nie widzi aż do siłowni.
 */
function loadSide(odpowiedz) {
  const file = path.join(__dirname, '..', 'app-side', 'index.js')
  const body = fs.readFileSync(file, 'utf8')
    .replace(/^import[\s\S]*?from '[^']+'\s*$/gm, '')

  const wyslane = []
  const fetchStub = async (options) => {
    wyslane.push(options)
    if (odpowiedz) return odpowiedz(options)
    return { status: 200, body: JSON.stringify({ active: true }) }
  }

  let naZadanie = null
  const builder = {
    listen() {},
    on(nazwa, cb) { if (nazwa === 'request') naZadanie = cb },
    // Harness podaje ładunek już jako obiekt, więc nie ma czego rozpakowywać.
    buf2Json(payload) { return payload }
  }
  const MessageBuilder = function () { return builder }
  let service = null
  const AppSideService = (o) => { service = o }

  const names = ['MessageBuilder', 'PHONE_URL', 'TOKEN', 'AppSideService', 'fetch']
  const values = [
    MessageBuilder, 'http://127.0.0.1:48627', 'token-testowy', AppSideService, fetchStub
  ]
  // eslint-disable-next-line no-new-func
  new Function(names.join(','), body).apply(null, values)
  service.onInit()

  return {
    wyslane,
    async zadaj(method, params) {
      const odpowiedzi = []
      await naZadanie({
        request: { payload: { method: method, params: params } },
        response: (r) => odpowiedzi.push(r)
      })
      return odpowiedzi[0]
    }
  }
}

function loadPage(phone) {
  // STRONA=sciezka pozwala puscic te same scenariusze na starej wersji
  // strony - tak sprawdzamy, ze nowy test naprawde lapie stary blad.
  const file = process.env.STRONA || path.join(__dirname, '..', 'page', 'index.js')
  const source = fs.readFileSync(file, 'utf8')
  const body = source
    .replace(/^import[\s\S]*?from '[^']+'\s*$/gm, '')
    .replace(/^const \{ messageBuilder \} = getApp\(\)\._options\.globalData\s*$/m, '')

  let captured = null
  const handlers = {}
  const Page = (object) => { captured = object }

  const messageBuilder = {
    request(req) {
      handlers.zadania = (handlers.zadania || 0) + 1
      // Glucha strona nie odpowiada ani bledem - obietnica wisi.
      if (phone.glucha) return new Promise(() => {})
      // Zepp dziala, ale GymBro na telefonie nie slucha: usluga towarzyszaca
      // odpowiada bledem, niczego nie wykonawszy.
      if (phone.mostPadl) {
        return Promise.resolve({ data: { error: 'Otwórz GymBro na telefonie', retry: true } })
      }
      const wynik = phone.request(req)
      // Wykonane, ale odpowiedz nie wraca.
      if (phone.gubiOdpowiedz) return new Promise(() => {})
      // Odpowiedz juz policzona, ale jeszcze w drodze.
      if (phone.wstrzymane) {
        return new Promise((resolve) => {
          phone.zatrzymane.push(() => resolve({ data: wynik }))
        })
      }
      return Promise.resolve({ data: wynik })
    }
  }

  // Zegary nie mają tu tykać: scenariusz steruje czasem sam, a tykający
  // zegar serii trzymałby proces przy życiu po ostatnim sprawdzeniu.
  const zegary = []
  const setTimeout_ = (fn, ms) => {
    const wpis = { fn: fn, ms: ms, zywy: true }
    const id = zegary.push(wpis)
    // Zero znaczy "jak tylko watek sie zwolni". Takie zlecenie odpalamy
    // naprawde, zeby flush() je zlapal - na zegarku lapie je petla zdarzen.
    // Zegary z prawdziwym odstepem zostaja do odpalenia przez scenariusz.
    if (!ms) {
      setImmediate(() => {
        if (!wpis.zywy) return
        wpis.zywy = false
        fn()
      })
    }
    return id
  }
  const clearTimeout_ = (id) => { if (zegary[id - 1]) zegary[id - 1].zywy = false }
  handlers.zegary = zegary
  Object.defineProperty(handlers, 'wolneAnimacje', {
    get: () => ANIMACJE.wolne,
    set: (v) => { ANIMACJE.wolne = !!v; if (!v) ANIMACJE.czeka.length = 0 },
    configurable: true
  })
  // Pojedyncze wywolanie zwrotne, w kolejnosci zlecania. Tak przychodza
  // na zegarku: animacja wyprzedzona przez nastepna i tak sie konczy,
  // tylko juz w trakcie tej nastepnej.
  handlers.jednaAnimacja = () => {
    const fn = ANIMACJE.czeka.shift()
    if (fn) fn()
    return !!fn
  }
  // Ile bezpiecznikow przejscia jeszcze czuwa.
  handlers.bezpieczniki = () => zegary.filter((z) => z.zywy && z.ms > 200 && z.ms < 400).length
  // Dokonczenie wszystkiego, co wisi: najpierw animacje, potem bezpieczniki.
  handlers.dokoncz = () => {
    ANIMACJE.czeka.splice(0).forEach((fn) => fn())
    zegary.filter((z) => z.zywy && z.ms <= 400).forEach((z) => { z.zywy = false; z.fn() })
  }

  const names = [
    'hmUI', 'px', 'Page', 'messageBuilder',
    'GESTURE_LEFT', 'GESTURE_RIGHT', 'GESTURE_UP', 'GESTURE_DOWN',
    'onGesture', 'offGesture', 'onKey', 'offKey',
    'KEY_HOME', 'KEY_BACK', 'KEY_SELECT', 'KEY_EVENT_CLICK',
    'setPageBrightTime', 'resetPageBrightTime',
    'setWakeUpRelaunch', 'pauseDropWristScreenOff', 'resetDropWristScreenOff',
    'LocalStorage',
    'HeartRate', 'Calorie', 'Vibrator',
    'VIBRATOR_SCENE_SHORT_LIGHT', 'VIBRATOR_SCENE_SHORT_MIDDLE',
    'VIBRATOR_SCENE_SHORT_STRONG', 'VIBRATOR_SCENE_DURATION',
    'VIBRATOR_SCENE_DURATION_LONG', 'VIBRATOR_SCENE_NOTIFICATION',
    'VIBRATOR_SCENE_STRONG_REMINDER',
    'DEVICE_WIDTH', 'DEVICE_HEIGHT', 'setTimeout', 'clearTimeout'
  ]

  const values = [
    hmUI, (v) => v, Page, messageBuilder,
    GESTURE_LEFT, GESTURE_RIGHT, GESTURE_UP, GESTURE_DOWN,
    (o) => { handlers.gesture = o.callback }, () => {},
    (o) => { handlers.key = o.callback }, () => {},
    KEY_HOME, KEY_BACK, KEY_SELECT, KEY_EVENT_CLICK,
    () => {}, () => {},
    (o) => { handlers.relaunch = o }, (o) => { handlers.dropWrist = o }, () => {},
    LocalStorage,
    HeartRate, Calorie, Vibrator,
    VIBRATOR_SCENE_SHORT_LIGHT, VIBRATOR_SCENE_SHORT_MIDDLE,
    VIBRATOR_SCENE_SHORT_STRONG, VIBRATOR_SCENE_DURATION,
    VIBRATOR_SCENE_DURATION_LONG, VIBRATOR_SCENE_NOTIFICATION,
    VIBRATOR_SCENE_STRONG_REMINDER,
    DEVICE_WIDTH, DEVICE_HEIGHT, setTimeout_, clearTimeout_
  ]

  // eslint-disable-next-line no-new-func
  const run = new Function(names.join(','), body)
  run.apply(null, values)

  if (!captured) throw new Error('Page() nie zostało wywołane')
  captured.onInit()
  return { page: captured, handlers: handlers }
}

// ---- Narzędzia scenariusza ------------------------------------------------

const flush = () => new Promise((resolve) => setImmediate(resolve))

let failures = 0
let checks = 0

function ok(label, condition, detail) {
  checks += 1
  if (condition) {
    console.log('  OK   ' + label)
  } else {
    failures += 1
    console.log('  BLAD ' + label + (detail ? '  (' + detail + ')' : ''))
  }
}

function section(name) {
  console.log('\n' + name)
}

// Czas przesuwamy sami: blokada sekundy po zapisie inaczej zjadłaby
// naciśnięcia scenariusza, a przerwy trwają po trzy minuty.
const realNow = Date.now
let offset = 0
Date.now = () => realNow.call(Date) + offset
const jump = (ms) => { offset += ms }

/**
 * Jedna zapisana seria, niezależnie od tego, w której fazie zastaliśmy ekran.
 *
 * Przycisk działa naprzemiennie, więc w trakcie przerwy pierwsze naciśnięcie
 * ją tylko kończy. Scenariusz, który o tym zapomniał, sprawdzał potem serię
 * sprzed swojego naciśnięcia i pokazywał cudzy wynik.
 */
async function zapiszSerie(page) {
  jump(2000)
  if (page.state.phase === 'rest') page.press()
  jump(2000)
  page.press()
  await flush(); await flush()
}

const BUZZ_GUARD = 1200

/**
 * Widgety każdego widoku, żeby dało się sprawdzić, że sąsiedzi są schowani.
 *
 * Bez tego "rozwala ekran" jest nie do złapania z poziomu testu: kod nie
 * rzuca wyjątkiem, tylko zostawia na wierzchu dwa widoki naraz albo zasłonę
 * przejścia.
 */
const GRUPY = {
  0: ['hr', 'hrIcon', 'name', 'count', 'phase', 'timer', 'load', 'prev', 'next'],
  1: ['wLabel', 'wMinus', 'wValue', 'wPlus', 'rLabel', 'rMinus', 'rValue', 'rPlus'],
  2: ['doneTitle', 'doneTime', 'doneSets', 'doneTon', 'doneSkip', 'doneBtn',
    'doneHint', 'doneMore', 'doneNext'],
  3: ['ctrlTime', 'ctrlState', 'ctrlPause', 'ctrlSkip', 'ctrlEnd', 'ctrlHint'],
  4: ['startTitle'],
  5: ['statTime', 'statTimeLabel', 'statSets', 'statSetsLabel', 'statKcal',
    'statKcalLabel', 'statVolume', 'statSync'],
  6: ['heartLabel', 'heartValue', 'heartRange'],
  7: ['gapsTitle', 'gapsSub', 'gapsHint'],
  8: ['logTitle', 'logHint'],
  9: ['fixTitle', 'fixWLabel', 'fixWValue', 'fixRLabel', 'fixRValue',
    'fixWMinus', 'fixWPlus', 'fixRMinus', 'fixRPlus', 'fixRirLabel', 'fixSave', 'fixDelete'],
  10: ['tonTitle', 'tonSub', 'tonHint']
}

/** Czy na ekranie stoi dokładnie jeden widok i nic z niego nie zostało. */
function ekranCzysty(page) {
  const w = page.w
  const widok = page.state.view
  const bledy = []
  // Zaslony przejscia juz nie ma - zmiana ekranu jest natychmiastowa.
  if (w.slide) bledy.push('wrocila zaslona przejscia')
  if (w.flash) bledy.push('wrocil pelnoekranowy blysk')
  Object.keys(GRUPY).forEach((v) => {
    const chciane = Number(v) === widok && !page.state.message
    GRUPY[v].forEach((klucz) => {
      const widget = w[klucz]
      if (!widget) { bledy.push('brak widgetu ' + klucz); return }
      if (widget.visible !== chciane) {
        bledy.push(klucz + (chciane ? ' schowany' : ' widoczny') + ' na ' + nazwa(widok))
      }
    })
  })
  return bledy
}

const VIEW = {
  SETS: 0, EDIT: 1, DONE: 2, CONTROL: 3, START: 4, STATS: 5, HEART: 6, GAPS: 7,
  LOG: 8, FIX: 9, TONS: 10
}
const NAZWA = ['serie', 'edycja', 'podsumowanie', 'sterowanie', 'wybor', 'czas',
  'tetno', 'braki', 'dziennik', 'poprawka', 'tonaz']
const nazwa = (v) => NAZWA[v] || ('?' + v)

// ---- Scenariusze ----------------------------------------------------------

async function main() {
  const phone = makePhone()
  const { page, handlers } = loadPage(phone)
  // Odpalenie budzikow zadan, ktore nie doczekaly sie odpowiedzi.
  // Gluchy telefon nie odrzuca obietnicy sam - robi to dopiero zegar.
  const cisza = async () => {
    // Kilka obrotow: wysylka jest teraz odroczona, wiec budzik zadania
    // powstaje dopiero po tym, jak petla zdarzen ja odpali.
    for (let i = 0; i < 4; i++) {
      handlers.zegary.filter((z) => z.zywy && z.ms >= 5000)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }

  // Zapis stanu jest odlozony i czeka, az nic sie nie animuje - scenariusz
  // musi dac mu dojsc do skutku, tak jak zrobi to prawdziwa petla zdarzen.
  const zapis = async () => {
    for (let i = 0; i < 4; i++) {
      // Zapis czeka na cisze po ostatnim dotyku - tu ja odczekujemy.
      jump(1500)
      // Dokladnie zegar odlozonego zapisu (ZAPIS_MS). Szerszy zakres lapal
      // tez bezpieczniki animacji i scenariusz konczyl przejscie sam,
      // zamiast sprawdzic, co sie dzieje w jego trakcie.
      handlers.zegary.filter((z) => z.zywy && z.ms === 350)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }

  // Kilka sekund bez odpowiedzi na pytanie o zapas po ostatniej serii planu -
  // wtedy zegarek sam przechodzi do podsumowania.
  const poKoncu = async () => {
    handlers.zegary.filter((z) => z.zywy && z.ms === 4000).forEach((z) => { z.zywy = false; z.fn() })
    await flush(); await flush()
  }

  // Oproznienie kolejki. Wysylka nie dzieje sie w uchwycie klikniecia,
  // tylko za chwile - wiec potrzeba kilku obrotow petli zdarzen.
  const osusz = async () => {
    for (let i = 0; i < 12; i++) {
      // Zadanie, ktore wisi po gluchym telefonie, trzyma wysylke zajeta -
      // dopiero jego budzik ja zwalnia.
      handlers.zegary.filter((z) => z.zywy && z.ms >= 5000)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
      page.drain()
      await flush(); await flush()
      if (!(page.state.outbox || []).length) break
    }
  }

  section('Start bez trwajacego treningu')
  page.build()
  await flush(); await flush()
  ok('pokazuje wybor jednostki', page.state.view === VIEW.START, nazwa(page.state.view))
  ok('widzi trzy jednostki', (page.state.units || []).length === 3)
  ok('nazwa programu z telefonu', page.state.program === 'FBW')

  section('Start jednostki z zegarka')
  page.startUnit(0)
  await flush(); await flush()
  ok('wchodzi w serie', page.state.view === VIEW.SETS, nazwa(page.state.view))
  ok('stoi na pierwszym cwiczeniu', page.state.index === 0)
  ok('telefon ma trening', phone.active)

  section('Seria i przerwa na zmiane')
  jump(2000)
  page.press()
  await flush(); await flush()
  ok('zapisana jedna seria', phone.log.length === 1, 'jest ' + phone.log.length)
  ok('weszla przerwa', page.state.phase === 'rest')
  jump(2000)
  page.press()
  ok('koniec przerwy nie zapisuje', phone.log.length === 1, 'jest ' + phone.log.length)
  ok('wrocila seria', page.state.phase === 'set')

  section('Szybkie klikanie bez blokady')
  // Stala tu sekunda przerwy na wypadek przycisniecia przycisku dwa razy
  // przy odkladaniu sztangi. Drugie nacisniecie po prostu znikalo i to
  // wlasnie czulo sie jak zacinanie - a serie zapisana przez pomylke widac
  // w dzienniku i cofa sie ja jednym nacisnieciem.
  const before = phone.log.length
  page.state.phase = 'set'
  page.press()
  await osusz()
  page.state.phase = 'set'
  page.press()
  await osusz()
  ok('dwa nacisniecia pod rzad to dwie serie', phone.log.length === before + 2,
    'przybylo ' + (phone.log.length - before))

  section('Przejscie przez caly plan')
  let guard = 0
  while (!(page.state.data && page.state.data.totals && page.state.data.totals.complete) && guard < 40) {
    guard += 1
    jump(1500)
    if (page.state.phase === 'rest') { page.press(); continue }
    page.press()
    await flush(); await flush()
  }
  ok('plan wykonany', !!(page.state.data.totals && page.state.data.totals.complete))
  ok('szesc serii w dzienniku', phone.log.length === 6, 'jest ' + phone.log.length)
  // Po ostatniej serii pada jeszcze pytanie o zapas - podsumowanie czeka.
  ok('po ostatniej serii najpierw pytanie o zapas', page.state.view === VIEW.SETS &&
    page.askingRir() === true, nazwa(page.state.view))
  await poKoncu()
  ok('a po chwili bez odpowiedzi podsumowanie', page.state.view === VIEW.DONE, nazwa(page.state.view))

  section('Dodatkowa seria po ukonczeniu planu')
  page.sideButton()
  await flush(); await flush()
  ok('z podsumowania wraca na serie', page.state.view === VIEW.SETS, nazwa(page.state.view))

  page.state.index = 2
  page.move(-1)
  ok('mozna cofnac sie na cwiczenie 2', page.state.index === 1, 'index ' + page.state.index)

  await zapiszSerie(page)
  const dodana = phone.log[phone.log.length - 1]
  ok('seria trafila na cwiczenie 2', dodana && dodana.id === 2, dodana && ('id ' + dodana.id))
  ok('NIE przeskoczylo na inne cwiczenie', page.state.index === 1, 'index ' + page.state.index)

  await zapiszSerie(page)
  const druga = phone.log[phone.log.length - 1]
  ok('kolejna dodatkowa tez na cwiczeniu 2', druga && druga.id === 2, druga && ('id ' + druga.id))
  ok('kolejna dodatkowa tez zostaje', page.state.index === 1, 'index ' + page.state.index)
  ok('podsumowanie nie wraca samo', page.state.view === VIEW.SETS, nazwa(page.state.view))

  section('Pomijanie cwiczenia')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  ok('swiezy trening bez pominiec', Object.keys(page.state.skipped).length === 0)

  // Drugie cwiczenie zostawiamy na potem i robimy trzecie do konca.
  page.state.index = 1
  page.state.view = VIEW.CONTROL
  page.skipExercise()
  await flush(); await flush()
  ok('pominiete trafia na liste', page.isSkipped(2) === true)
  ok('po pominieciu wchodzi trzecie cwiczenie', page.state.index === 2, 'index ' + page.state.index)

  await zapiszSerie(page)
  await zapiszSerie(page)
  ok('trzecie cwiczenie zrobione', (phone.done[3] || 0) === 2, 'serii ' + (phone.done[3] || 0))
  ok('NIE wrocilo na pominiete', page.state.index !== 1, 'index ' + page.state.index)
  ok('pominiete nie dostalo serii', (phone.done[2] || 0) === 0)

  section('Pominiecie a podsumowanie')
  page.state.index = 0
  while ((phone.done[1] || 0) < 2) await zapiszSerie(page)
  ok('plan uznany za wykonany mimo pominiecia', page.localComplete() === true)
  // Wybor zapasu do ostatniej serii prowadzi do podsumowania od razu.
  page.setRir(2)
  ok('pokazalo podsumowanie zaraz po zapasie', page.state.view === VIEW.DONE, nazwa(page.state.view))

  section('Powrot do pominietego cwiczenia')
  page.sideButton()
  await flush()
  page.state.index = 1
  page.paintSets()
  ok('licznik mowi POMINIETE', page.w.count.attrs.text === 'POMINIĘTE', page.w.count.attrs.text)
  await zapiszSerie(page)
  ok('seria przywraca cwiczenie do planu', page.isSkipped(2) === false)
  ok('seria trafila na drugie cwiczenie', (phone.done[2] || 0) === 1, 'serii ' + (phone.done[2] || 0))
  ok('zostalo na drugim cwiczeniu', page.state.index === 1, 'index ' + page.state.index)

  section('Wracanie do planu z przeskoku, bez pominiecia')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  // Pierwsze cwiczenie zostawiamy nietkniete i przechodzimy strzalka na drugie.
  page.move(1)
  ok('strzalka przenosi na drugie', page.state.index === 1, 'index ' + page.state.index)
  await zapiszSerie(page)
  await zapiszSerie(page)
  ok('po domknieciu drugiego idzie na trzecie, nie wstecz',
    page.state.index === 2, 'index ' + page.state.index)

  section('Pomijanie z ekranu sterowania')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  handlers.gesture(GESTURE_RIGHT)
  ok('w prawo wchodzi sterowanie', page.state.view === VIEW.CONTROL, nazwa(page.state.view))
  ok('ekran sterowania czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))

  page.w.ctrlSkip.onClick()
  await flush(); await flush()
  ok('po pominieciu jest na seriach', page.state.view === VIEW.SETS, nazwa(page.state.view))
  ok('ekran po pominieciu czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))

  section('Tykanie zegara nie przerysowuje przyciskow')
  // setBtn wysyla komplet wlasciwosci, zeby przycisk wrocil po zaslonie.
  // Wolane co sekunde z zegara odbudowywaloby przyciski w kolko.
  for (const widok of [VIEW.SETS, VIEW.CONTROL, VIEW.STATS]) {
    page.state.view = widok
    page.applyVisibility()
    const przyciski = page.visibleButtons()
    const przed = przyciski.map((b) => b.writes)
    if (widok === VIEW.SETS) page.paintClock()
    else if (widok === VIEW.CONTROL) page.paintControlClock()
    else page.paintStats()
    const ruszone = przyciski.filter((b, i) => b.writes !== przed[i]).length
    ok('tyk na ' + nazwa(widok) + ' nie rusza przyciskow', ruszone === 0,
      ruszone + ' przerysowanych')
  }

  section('Ostatnia seria przy niezrobionym cwiczeniu')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  // Drugie zostawiamy nietkniete, idziemy strzalka na ostatnie i domykamy je.
  page.state.index = 2
  await zapiszSerie(page)
  await zapiszSerie(page)
  await poKoncu()
  ok('po ostatniej serii ostatniego cwiczenia jest podsumowanie',
    page.state.view === VIEW.DONE, nazwa(page.state.view))
  ok('NIE cofnelo na niezrobione', page.state.index === 2, 'index ' + page.state.index)
  ok('ekran podsumowania czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))

  section('Podsumowanie wymienia braki')
  ok('mowi o niezrobionym cwiczeniu',
    /Pomini|Brak|brak/.test(page.w.doneSkip.attrs.text), page.w.doneSkip.attrs.text)
  // Trzy zawiniete linijki weszly kiedys w tonaz i w przycisk. Ta linijka
  // musi zostac jedna, krotka i bez zawijania - stad limit znakow.
  ok('linijka o brakach jest krotka',
    page.w.doneSkip.attrs.text.length <= 34, page.w.doneSkip.attrs.text)
  ok('linijka o brakach sie nie zawija',
    page.w.doneSkip.attrs.text_style === 'NONE', page.w.doneSkip.attrs.text_style)
  ok('podsumowanie zaprasza na ekran brakow',
    /w gór/.test(page.w.doneMore.attrs.text), page.w.doneMore.attrs.text)

  section('Osobny ekran z pominietymi')
  handlers.gesture(GESTURE_UP)
  ok('w gore z podsumowania wchodzi lista', page.state.view === VIEW.GAPS,
    nazwa(page.state.view))
  ok('ekran brakow czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  const widoczne = page.w.gapsRows.filter((r) => r.visible)
  ok('lista wymienia nazwy', widoczne.length > 0, widoczne.length + ' wierszy')
  ok('kazdy wiersz ma tekst', widoczne.every((r) => (r.attrs.text || '').length > 0))
  ok('wiersze sa klikalne', widoczne.every((r) => typeof r.onClick === 'function'))

  // Klikniecie w brak ma przeniesc wprost na to cwiczenie.
  const szukana = widoczne[0].attrs.text
  const oczekiwany = page.state.data.exercises.findIndex((e) => e.name === szukana)
  widoczne[0].onClick()
  await flush()
  ok('klikniecie przenosi na cwiczenie', page.state.index === oczekiwany,
    'index ' + page.state.index + ', oczekiwany ' + oczekiwany)
  ok('po skoku jest na seriach', page.state.view === VIEW.SETS, nazwa(page.state.view))
  page.state.view = VIEW.GAPS
  page.applyVisibility()
  page.paintGaps()
  handlers.gesture(GESTURE_DOWN)
  ok('w dol wraca na podsumowanie', page.state.view === VIEW.DONE, nazwa(page.state.view))
  ok('podsumowanie po powrocie czyste', ekranCzysty(page).length === 0,
    ekranCzysty(page).join('; '))

  section('Lista brakow nie przelewa sie przez ekran')
  phone.setDuzoCwiczen(true)
  await page.send('STATE', {}, false)
  await flush(); await flush()
  page.state.index = 0
  page.state.view = VIEW.GAPS
  page.applyVisibility()
  page.paintGaps()
  const duzo = page.w.gapsRows.filter((r) => r.visible)
  ok('nie wiecej wierszy niz miejsca', duzo.length <= 6, duzo.length + ' wierszy')
  ok('ostatni wiersz mowi ile jeszcze',
    /i jeszcze/.test(duzo[duzo.length - 1].attrs.text), duzo[duzo.length - 1].attrs.text)
  phone.setDuzoCwiczen(false)
  await page.send('STATE', {}, false)
  await flush(); await flush()

  section('Dluga nazwa cwiczenia')
  phone.setDlugaNazwa(true)
  await page.send('STATE', {}, false)
  await flush(); await flush()
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.paintSets()
  ok('nazwa z telefonu doszla w calosci',
    page.w.name.attrs.text === phone.DLUGA, page.w.name.attrs.text)
  const szerokosc = page.w.name.attrs.w
  ok('pole nazwy rozciagniete pod dlugi tekst', szerokosc > 360, 'szerokosc ' + szerokosc)
  const anim = page.w.name.anim
  ok('dluga nazwa dostala przewijanie', !!anim, anim ? 'jest' : 'brak animacji')
  const kroki = (anim && anim.anim_steps) || []
  // Jeden krok naraz: dwa w jednym zleceniu z nieskonczonym powtarzaniem
  // dawaly skoki, odbicia i dwojacy sie napis.
  ok('jeden krok naraz', kroki.length === 1, kroki.length + ' krokow')
  ok('animacja sprzata po sobie', anim && anim.anim_auto_destroy === 1)
  ok('nie powtarza sie sama', anim && anim.anim_repeat === 0)
  ok('ma zawrotke', anim && typeof anim.anim_complete_func === 'function')
  ok('pierwszy przejazd idzie w lewo',
    kroki.length === 1 && kroki[0].anim_to < kroki[0].anim_from,
    kroki.length ? kroki[0].anim_from + ' -> ' + kroki[0].anim_to : '')

  phone.setDlugaNazwa(false)
  await page.send('STATE', {}, false)
  await flush(); await flush()
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.paintSets()
  ok('krotka nazwa bez przewijania', page.w.name.attrs.w <= 360, 'szerokosc ' + page.w.name.attrs.w)

  section('Powtorzenia na glownym ekranie')
  ok('widac podpisane powtorzenia', /pow\. \d/.test(page.w.load.attrs.text),
    page.w.load.attrs.text)
  page.stepReps(3)
  page.paintSets()
  ok('pokazuje liczbe ustawiona w edycji',
    page.w.load.attrs.text.indexOf('pow. ' + page.state.reps) >= 0,
    page.w.load.attrs.text + ' przy reps ' + page.state.reps)
  ok('nie ma juz surowego x ani celu',
    !/ x /.test(page.w.load.attrs.text) && !/cel/.test(page.w.load.attrs.text),
    page.w.load.attrs.text)

  section('Zapis z ekranu ciezaru')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  handlers.gesture(GESTURE_LEFT)
  ok('w lewo wchodzi edycja', page.state.view === VIEW.EDIT, nazwa(page.state.view))
  page.stepWeight(1)
  page.stepReps(1)
  const ciezar = page.state.weight
  const powt = page.state.reps
  jump(2000)
  const przed = phone.log.length
  const przejete = handlers.key(KEY_SHORTCUT, KEY_EVENT_CLICK)
  await flush(); await flush()
  ok('klawisz nie wypada do systemu', przejete === true)
  ok('zapisalo serie', phone.log.length === przed + 1, 'przybylo ' + (phone.log.length - przed))
  const ostatnia = phone.log[phone.log.length - 1]
  ok('z ustawionym ciezarem', ostatnia && ostatnia.weight === ciezar,
    ostatnia && (ostatnia.weight + ' zamiast ' + ciezar))
  ok('z ustawionymi powtorzeniami', ostatnia && ostatnia.reps === powt,
    ostatnia && (ostatnia.reps + ' zamiast ' + powt))
  ok('wrocilo na glowny ekran', page.state.view === VIEW.SETS, nazwa(page.state.view))

  section('Klawisz boczny na kazdym ekranie')
  const widoki = [VIEW.SETS, VIEW.EDIT, VIEW.DONE, VIEW.CONTROL, VIEW.START, VIEW.STATS, VIEW.HEART]
  let wszystkie = true
  for (const v of widoki) {
    page.state.view = v
    jump(2000)
    const r = handlers.key(KEY_SHORTCUT, KEY_EVENT_CLICK)
    await flush()
    if (r !== true) { wszystkie = false; console.log('       wypada na: ' + nazwa(v)) }
  }
  ok('zaden ekran nie oddaje klawisza systemowi', wszystkie)
  ok('klawisz domowy zostaje systemowi', handlers.key(KEY_HOME, KEY_EVENT_CLICK) === false)
  ok('wstecz zostaje systemowi', handlers.key(KEY_BACK, KEY_EVENT_CLICK) === false)

  section('Pionowa karuzela')
  page.state.view = VIEW.SETS
  handlers.gesture(GESTURE_UP)
  ok('w gore: czas treningu', page.state.view === VIEW.STATS, nazwa(page.state.view))
  handlers.gesture(GESTURE_UP)
  ok('w gore: tetno', page.state.view === VIEW.HEART, nazwa(page.state.view))
  ok('wyzej juz nic', handlers.gesture(GESTURE_UP) === false)
  handlers.gesture(GESTURE_DOWN)
  handlers.gesture(GESTURE_DOWN)
  ok('w dol wraca na serie', page.state.view === VIEW.SETS, nazwa(page.state.view))

  section('Zakonczenie treningu')
  page.state.view = VIEW.DONE
  await page.send('FINISH')
  await flush(); await flush(); await flush()
  ok('telefon zamknal trening', phone.active === false)
  ok('zegarek wrocil do wyboru jednostki', page.state.view === VIEW.START, nazwa(page.state.view))

  section('Ekran gasnie i aplikacja wraca')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  ok('prosi system o powrot po obudzeniu',
    handlers.relaunch && handlers.relaunch.relaunch === true,
    JSON.stringify(handlers.relaunch))
  ok('wylacza gaszenie przy opuszczeniu nadgarstka',
    handlers.dropWrist && handlers.dropWrist.duration === 0,
    JSON.stringify(handlers.dropWrist))

  // Zostawiamy pierwsze cwiczenie, idziemy na trzecie i tam cos robimy.
  page.state.index = 1
  page.state.view = VIEW.CONTROL
  page.skipExercise()
  await flush(); await flush()
  await zapiszSerie(page)
  const gdzieBylem = page.state.index
  const pominieteBylo = Object.keys(page.state.skipped).slice()
  page.onDestroy()

  // Tak wyglada ponowne uruchomienie: nowy obiekt strony, ta sama pamiec.
  const drugi = loadPage(phone)
  drugi.page.onInit()
  drugi.page.build()
  await flush(); await flush(); await flush()
  ok('po powrocie ten sam trening',
    drugi.page.state.sessionId === page.state.sessionId,
    drugi.page.state.sessionId + ' vs ' + page.state.sessionId)
  ok('wraca na to cwiczenie, na ktorym bylem',
    drugi.page.state.index === gdzieBylem,
    'index ' + drugi.page.state.index + ', bylo ' + gdzieBylem)
  ok('pamieta pominiete',
    JSON.stringify(Object.keys(drugi.page.state.skipped)) === JSON.stringify(pominieteBylo),
    Object.keys(drugi.page.state.skipped).join(','))
  ok('nie wita podsumowaniem', drugi.page.state.view === VIEW.SETS,
    nazwa(drugi.page.state.view))

  section('Drabinka ciezarow')
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.state.editedFor = null
  await page.send('STATE', {}, true)
  await flush(); await flush()
  const ex0 = page.current()
  ok('telefon przyslal osiagalne ciezary',
    Array.isArray(ex0.ladder) && ex0.ladder.length > 1, JSON.stringify(ex0.ladder))
  const start = page.state.weight
  page.stepWeight(1)
  ok('w gore laduje na progu', ex0.ladder.indexOf(page.state.weight) >= 0,
    page.state.weight + ' w ' + JSON.stringify(ex0.ladder))
  ok('w gore to wiecej', page.state.weight > start, start + ' -> ' + page.state.weight)
  page.stepWeight(-1)
  ok('w dol wraca tam, gdzie bylo', page.state.weight === start,
    page.state.weight + ' vs ' + start)

  section('Koniec drabinki to nie koniec silowni')
  // Miekki kraniec: lista sie skonczyla, ale sprzet pozwala isc nizej.
  phone.setTwardyDol(false)
  await page.send('STATE', {}, false)
  await flush(); await flush()
  page.state.editedFor = null
  let poprzednia = page.state.weight
  let ruchow = 0
  for (let i = 0; i < 40; i++) {
    page.stepWeight(-1)
    if (page.state.weight === poprzednia) break
    poprzednia = page.state.weight
    ruchow += 1
  }
  const najnizszy = phone.ladderOf(page.current())[0]
  ok('schodzi ponizej przyslanej listy', page.state.weight < najnizszy,
    page.state.weight + ' przy liscie od ' + najnizszy)
  ok('nie zatrzymuje sie po kilku krokach', ruchow > 10, ruchow + ' krokow')
  ok('nie schodzi ponizej zera', page.state.weight > 0, String(page.state.weight))

  // Powrot do gory nie moze przeskoczyc na dno listy jednym susem.
  const nisko = page.state.weight
  const odstep = Math.abs(phone.ladderOf(page.current())[1] - najnizszy)
  page.stepWeight(1)
  ok('powrot w gore idzie krokiem, nie susem',
    Math.abs(page.state.weight - (nisko + odstep)) < 0.01,
    nisko + ' -> ' + page.state.weight + ', krok ' + odstep)
  ok('nadal ponizej listy', page.state.weight < najnizszy,
    page.state.weight + ' vs ' + najnizszy)

  // Twardy kraniec: sam gryf, nizej nie ma czego zdjac.
  phone.setTwardyDol(true)
  page.state.editedFor = null
  await page.send('STATE', {}, false)
  await flush(); await flush()
  for (let i = 0; i < 40; i++) page.stepWeight(-1)
  const dol = phone.ladderOf(page.current())[0]
  ok('na prawdziwej granicy staje', page.state.weight === dol,
    page.state.weight + ' vs granica ' + dol)
  phone.setTwardyDol(false)

  section('Odpowiedz telefonu nie kasuje recznego ciezaru')
  page.stepWeight(1)
  const recznie = page.state.weight
  ok('edycja zapamietana dla cwiczenia', page.state.editedFor === ex0.id)
  await page.send('STATE', {}, false)
  await flush(); await flush()
  ok('ciezar po odswiezeniu stanu nie zmieniony', page.state.weight === recznie,
    page.state.weight + ' zamiast ' + recznie)
  await zapiszSerie(page)
  const zapisana = phone.log[phone.log.length - 1]
  ok('zapisalo recznie ustawiony ciezar', zapisana && zapisana.weight === recznie,
    zapisana && (zapisana.weight + ' zamiast ' + recznie))
  ok('po zapisie edycja wygasa', page.state.editedFor === null)

  section('Ekran ciezaru bez przyciskow od serii')
  // Byly tu "-1 seria" i "+1 seria". Kasowanie przenioslo sie do poprawki
  // konkretnej serii, gdzie widac, ktora sie rusza, a dokladanie robi sie
  // na ekranie cwiczenia. Tutaj ustawia sie ciezar i powtorzenia, i tyle.
  page.state.view = VIEW.EDIT
  page.applyVisibility()
  ok('nie ma juz przyciskow od serii', page.w.undo === undefined &&
    page.w.addSet === undefined,
    'undo: ' + page.w.undo + ', addSet: ' + page.w.addSet)
  ok('ekran ciezaru czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Strefy tetna')
  ok('telefon przysyla progi', !!page.state.zones, JSON.stringify(page.state.zones))
  const z = page.state.zones
  ok('progi rosna',
    z && z.easy < z.aerobic && z.aerobic < z.tempo && z.tempo < z.anaerobic &&
    z.anaerobic < z.max, JSON.stringify(z))
  const kolory = [40, z.easy + 1, z.aerobic + 1, z.tempo + 1, z.anaerobic + 1]
    .map((v) => page.hrColor(v))
  ok('kazda strefa ma swoj kolor', new Set(kolory).size === 5,
    kolory.map((c) => c.toString(16)).join(' '))
  ok('bez progow tetno zostaje biale', (function () {
    const zap = page.state.zones
    page.state.zones = null
    const c = page.hrColor(130)
    page.state.zones = zap
    return c === 0xffffff
  })())

  section('Zakres tetna z calej sesji')
  page.state.hrLog = []
  page.state.hrMin = 0
  page.state.hrMax = 0
  page.lastHrLog = 0
  const przebieg = [70, 150, 95, 140, 120]
  przebieg.forEach((v, i) => { page.lastHrLog = 0; page.noteHeart(v) })
  ok('minimum z calej sesji', page.state.hrMin === 70, 'jest ' + page.state.hrMin)
  ok('maksimum z calej sesji', page.state.hrMax === 150, 'jest ' + page.state.hrMax)
  // Okno wykresu krotsze niz sesja - podpis nie moze podawac go jako minimum.
  page.state.hrLog = [130, 140]
  page.state.view = VIEW.HEART
  page.paintHeart()
  ok('podpis mowi o sesji, nie o oknie',
    /70-150/.test(page.w.heartRange.attrs.text), page.w.heartRange.attrs.text)
  ok('podpis podaje dlugosc okna',
    /wykres/.test(page.w.heartRange.attrs.text), page.w.heartRange.attrs.text)

  section('Kalorie trafiaja na ekran')
  // Gole `NaN` zamiast wypisania liczby przeszlo przez build bez slowa,
  // bo to poprawne wyrazenie. Tylko odczyt tego, co widac, to wykrywa.
  // Czujnik rzadzi tylko wtedy, gdy nie znamy wspolczynnikow spalania -
  // a te przychodza teraz razem z jednostkami, wiec tu trzeba je zdjac.
  page.state.burn = null
  Calorie.wartosc = 1000
  page.state.kcalBase = null
  page.state.kcal = 0
  page.state.kcalSeen = false
  page.state.kcalFail = false
  page.readCalorie()
  Calorie.wartosc = 1120
  page.readCalorie()
  page.state.view = VIEW.STATS
  page.applyVisibility()
  page.paintStats()
  ok('spalone liczone od startu treningu', page.state.kcal === 120,
    'jest ' + page.state.kcal)
  ok('liczba jest na ekranie', page.w.statKcal.attrs.text === '120',
    JSON.stringify(page.w.statKcal.attrs.text))
  ok('podpis zwyczajny', page.w.statKcalLabel.attrs.text === 'Kalorie',
    page.w.statKcalLabel.attrs.text)

  section('Kalorie liczone z tetna')
  // Czujnik dzienny napedzaja kroki i przy sztandze pokazuje prawie zero.
  // Liczymy wiec z tetna i to ma rzadzic nad czujnikiem.
  page.state.burn = { k0: -6.1, k1: 0.116 }
  page.state.burnKcal = 0
  page.state.burnAt = 0
  // Dwa odstepy po 30 s, bo pojedyncza dziura dluzsza niz to jest przycinana.
  page.addBurn(120)
  jump(30000)
  page.addBurn(120)
  jump(30000)
  page.addBurn(120)
  const spodziewane = (-6.1 + 0.116 * 120) * 1
  ok('minuta przy tetnie 120 to okolo 8 kcal',
    Math.abs(page.state.burnKcal - spodziewane) < 0.2,
    page.state.burnKcal.toFixed(2) + ' vs ' + spodziewane.toFixed(2))
  page.paintStats()
  ok('wyliczenie z tetna bije czujnik',
    page.w.statKcal.attrs.text === String(Math.round(page.state.burnKcal)),
    page.w.statKcal.attrs.text)

  // Dziura po zgaszonym ekranie nie moze dolozyc kalorii z martwego czasu.
  const przedDziura = page.state.burnKcal
  jump(20 * 60000)
  page.addBurn(120)
  ok('dluga dziura doliczona tylko czesciowo',
    page.state.burnKcal - przedDziura < 5,
    (page.state.burnKcal - przedDziura).toFixed(2) + ' kcal')

  // Godzina przy tetnie roboczym ma dac wynik rzedu tego, co zegarek sam liczy.
  page.state.burnKcal = 0
  page.state.burnAt = 0
  page.addBurn(118)
  for (let i = 0; i < 120; i++) { jump(30000); page.addBurn(118) }
  ok('godzina przy 118 to kilkaset kcal',
    page.state.burnKcal > 350 && page.state.burnKcal < 600,
    Math.round(page.state.burnKcal) + ' kcal')
  page.state.burn = null

  section('Kalorie, gdy czujnik milczy')
  page.state.kcalFail = true
  page.paintStats()
  ok('nie udaje zera', page.w.statKcal.attrs.text !== '0',
    page.w.statKcal.attrs.text)
  ok('podpis mowi o braku zgody', /brak zgody/.test(page.w.statKcalLabel.attrs.text),
    page.w.statKcalLabel.attrs.text)
  page.state.kcalFail = false

  section('Kalorie odpytywane z zegara, nie tylko z nasluchu')
  // Czujnik bez dzialajacego onChange ma dalej liczyc.
  Calorie.bezNasluchu = true
  const trzeci = loadPage(phone)
  trzeci.page.onInit()
  Calorie.wartosc = 2000
  trzeci.page.startCalorie()
  ok('brak nasluchu nie zabija czujnika', trzeci.page.calorie !== null)
  Calorie.wartosc = 2075
  trzeci.page.readCalorie()
  ok('liczy mimo braku nasluchu', trzeci.page.state.kcal === 75,
    'jest ' + trzeci.page.state.kcal)
  Calorie.bezNasluchu = false

  section('Dziennik serii i poprawianie')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  await zapiszSerie(page)
  await zapiszSerie(page)
  // Dwie serie domykaja pierwsze cwiczenie i advance przenosi dalej.
  // Wracamy, bo dziennik dotyczy cwiczenia, na ktore patrzysz.
  page.state.index = 0
  ok('telefon oddaje zapisane serie z numerami',
    page.logRows().length === 2 && page.logRows().every((e) => e.id > 0),
    JSON.stringify(page.logRows()))

  page.state.view = VIEW.SETS
  handlers.gesture(GESTURE_DOWN)
  ok('w dol z serii wchodzi dziennik', page.state.view === VIEW.LOG,
    nazwa(page.state.view))
  ok('ekran dziennika czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  const wiersze = page.w.logRows.filter((r) => r.visible)
  ok('kazda seria ma swoj wiersz', wiersze.length === 2, wiersze.length + ' wierszy')
  ok('wiersz pokazuje numer, ciezar i powtorzenia',
    /^1\./.test(wiersze[0].attrs.text) && /x \d/.test(wiersze[0].attrs.text),
    wiersze[0].attrs.text)

  section('Poprawka trafia w te wlasciwa serie')
  // Mylka w pierwszej serii: bylo wiecej powtorzen niz zapisalem.
  const pierwsza = page.logRows()[0]
  const drugaSeria = page.logRows()[1]
  wiersze[0].onClick()
  ok('otwiera poprawke', page.state.view === VIEW.FIX, nazwa(page.state.view))
  ok('ekran poprawki czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  ok('poprawka dotyczy pierwszej serii', page.state.fix.id === pierwsza.id,
    page.state.fix.id + ' vs ' + pierwsza.id)

  page.fixStep('r', 1)
  page.fixStep('r', 1)
  const noweRepsy = page.state.fix.reps
  ok('powtorzenia rosna', noweRepsy === pierwsza.reps + 2,
    noweRepsy + ' z ' + pierwsza.reps)
  page.w.fixSave.onClick()
  await flush(); await flush()
  ok('po zapisie wraca do dziennika', page.state.view === VIEW.LOG,
    nazwa(page.state.view))

  const poPoprawce = phone.log.find((e) => e.setId === pierwsza.id)
  ok('telefon dostal poprawke tej serii', !!poPoprawce, JSON.stringify(poPoprawce))
  ok('z nowa liczba powtorzen', poPoprawce && poPoprawce.reps === noweRepsy,
    poPoprawce && poPoprawce.reps)
  ok('druga seria nietknieta',
    page.logRows()[1] && page.logRows()[1].reps === drugaSeria.reps,
    page.logRows()[1] && page.logRows()[1].reps)
  ok('liczba serii bez zmian', page.logRows().length === 2, page.logRows().length)

  section('Ciezar w poprawce chodzi po drabince')
  wiersze[0].onClick()
  const przedW = page.state.fix.weight
  const biezacy = page.state.weight
  page.fixStep('w', 1)
  ok('ciezar poprawki rosnie', page.state.fix.weight > przedW,
    przedW + ' -> ' + page.state.fix.weight)
  ok('laduje na progu', page.current().ladder.indexOf(page.state.fix.weight) >= 0,
    String(page.state.fix.weight))
  ok('nie rusza ciezaru biezacej serii', page.state.weight === biezacy,
    page.state.weight + ' vs ' + biezacy)
  // Przycisk anuluj zniknal - z poprawki wychodzi sie gestem, tak jak
  // z kazdego innego ekranu doraznego.
  const przedWyjsciem = phone.log.length
  handlers.gesture(GESTURE_DOWN)
  ok('gest wraca do dziennika', page.state.view === VIEW.LOG, nazwa(page.state.view))
  ok('wyjscie nic nie zapisalo', phone.log.length === przedWyjsciem,
    przedWyjsciem + ' -> ' + phone.log.length)

  section('Dlugi dziennik przewija sie w dol')
  // Zgloszone 02.11: lista serii byla mala i konczyla sie tam, gdzie
  // konczyly sie wiersze. Przy osmiu seriach dwoch ostatnich nie dalo sie
  // ani zobaczyc, ani poprawic - nie istnialy na tym ekranie.
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  for (let i = 0; i < 7; i++) {
    page.state.index = 0
    await zapiszSerie(page)
  }
  page.state.index = 0
  page.state.logAt = 0
  page.state.view = VIEW.SETS
  handlers.gesture(GESTURE_DOWN)
  ok('dziennik ma wszystkie siedem serii', page.logRows().length === 7,
    page.logRows().length + ' serii')

  // Liczone z liczby wierszy, a nie wpisane na sztywno: ile ich jest,
  // to decyzja o wielkosci pisma i moze sie jeszcze zmienic. Scenariusz
  // ma pilnowac przewijania, a nie konkretnej liczby.
  const ile = page.w.logRows.length
  const ogon = 7 - ile
  const okno1 = page.w.logRows.filter((r) => r.visible)
  ok('widac pelne okno', okno1.length === ile, okno1.length + ' z ' + ile)
  ok('numery od pierwszego', /^1\./.test(okno1[0].attrs.text), okno1[0].attrs.text)
  ok('podpis mowi, ile zostalo',
    page.w.logHint.attrs.text.indexOf('1-' + ile + ' z 7') === 0,
    page.w.logHint.attrs.text)

  handlers.gesture(GESTURE_DOWN)
  ok('gest w dol zostaje w dzienniku', page.state.view === VIEW.LOG,
    nazwa(page.state.view))
  const okno2 = page.w.logRows.filter((r) => r.visible)
  // Okno dosuwa sie do konca listy zamiast wyjechac poza nia: widac
  // pelen komplet wierszy, a nie dwa wiersze i reszta pustych miejsc.
  ok('widac ogon listy, bez dziur', okno2.length === ile, okno2.length + ' wierszy')
  ok('i numery sie zgadzaja',
    okno2[0].attrs.text.indexOf((ogon + 1) + '.') === 0, okno2[0].attrs.text)
  ok('podpis tez',
    page.w.logHint.attrs.text.indexOf((ogon + 1) + '-7 z 7') === 0,
    page.w.logHint.attrs.text)
  ok('ostatnia seria wreszcie widoczna',
    okno2[ile - 1].attrs.text.indexOf('7.') === 0, okno2[ile - 1].attrs.text)

  // Numer serii w poprawce musi byc prawdziwy, nie numerem wiersza:
  // po przewinieciu pierwszy wiersz nie jest pierwsza seria.
  const przewinieta = page.logRows()[ogon]
  okno2[0].onClick()
  ok('poprawka otwiera te serie, w ktora trafil palec',
    page.state.fix.id === przewinieta.id,
    page.state.fix.id + ' vs ' + przewinieta.id)
  ok('i tak sie podpisuje', page.state.fix.nr === ogon + 1,
    'Seria ' + page.state.fix.nr)
  handlers.gesture(GESTURE_DOWN)

  handlers.gesture(GESTURE_DOWN)
  ok('na koncu listy zawija do poczatku',
    page.w.logHint.attrs.text.indexOf('1-' + ile + ' z 7') === 0,
    page.w.logHint.attrs.text)
  handlers.gesture(GESTURE_UP)
  ok('w gore nadal wychodzi z dziennika', page.state.view === VIEW.SETS,
    nazwa(page.state.view))

  section('Wiersze dziennika mieszcza sie w tarczy')
  // Okragly ekran: wiersz na 50 i szerokosci 380 wychodzil rogami poza
  // okrag i wygladal na przyciety, bo byl. Sprawdzamy kazdy rog.
  const poza = page.w.logRows.filter((r) => {
    const a = r.attrs
    return [[a.x, a.y], [a.x + a.w, a.y], [a.x, a.y + a.h], [a.x + a.w, a.y + a.h]]
      .some(([x, y]) => Math.hypot(x - 240, y - 240) > 240)
  })
  ok('zaden wiersz nie wystaje poza okrag', poza.length === 0,
    poza.length + ' wierszy poza tarcza')

  section('Ciezar i powtorzenia stoja na srodku tarczy')
  // Blok zaczynal sie na 56 i konczyl na 316: 56 pikseli nad nim, 164 pod.
  // Na okraglej tarczy widac to od razu, bo dol swieci pustka.
  const blokGora = page.w.wLabel.attrs.y
  const blokDol = page.w.rMinus.attrs.y + page.w.rMinus.attrs.h
  ok('tyle samo miejsca nad blokiem co pod nim', blokGora === 480 - blokDol,
    blokGora + ' nad, ' + (480 - blokDol) + ' pod')
  // Liczba w pionie na srodku swoich przyciskow, nie dwa piksele nizej.
  const srodek = (widget) => widget.attrs.y + widget.attrs.h / 2
  ok('ciezar na srodku swoich przyciskow',
    srodek(page.w.wValue) === srodek(page.w.wMinus),
    srodek(page.w.wValue) + ' vs ' + srodek(page.w.wMinus))
  ok('powtorzenia tak samo',
    srodek(page.w.rValue) === srodek(page.w.rMinus),
    srodek(page.w.rValue) + ' vs ' + srodek(page.w.rMinus))

  section('Nastepna jednostka po treningu')
  page.state.view = VIEW.DONE
  await page.send('FINISH')
  await flush(); await flush(); await flush()
  ok('zegarek wie, co nastepnym razem', !!page.state.nextUnit, page.state.nextUnit)
  const oznaczona = (page.state.units || []).filter((u) => u.next)
  ok('wybor jednostki wyroznia kolejna', oznaczona.length === 1,
    oznaczona.map((u) => u.name).join(','))

  section('Zapas powtorzen w przerwie')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  page.state.view = VIEW.SETS
  page.applyVisibility()
  ok('przed seria pytamy o nic', page.askingRir() === false)
  ok('strzalki widoczne', page.w.prev.visible === true)

  await zapiszSerie(page)
  page.state.index = 0
  page.state.phase = 'rest'
  ok('po serii, w przerwie, pytamy o zapas', page.askingRir() === true)
  ok('strzalki ustepuja miejsca', page.w.prev.visible === false)
  ok('cztery przyciski zapasu', page.w.rirRow.filter((b) => b.visible).length === 4)
  ok('podpis pyta wprost', /zosta/.test(page.w.load.attrs.text), page.w.load.attrs.text)

  page.w.rirRow[2].onClick()
  await osusz()
  ok('zapas trafil do telefonu',
    phone.wpisy[1] && phone.wpisy[1][0].rir === 2,
    JSON.stringify(phone.wpisy[1]))
  ok('po wyborze przestajemy pytac', page.askingRir() === false)
  ok('strzalki wracaja', page.w.prev.visible === true)
  ok('rzad zapasu znika', page.w.rirRow.every((b) => !b.visible))

  section('Zapas widac w dzienniku serii')
  page.state.view = VIEW.LOG
  page.applyVisibility()
  page.paintLog()
  ok('wiersz pokazuje RIR', /RIR 2/.test(page.w.logRows[0].attrs.text),
    page.w.logRows[0].attrs.text)

  section('Pominiecie zapasu niczego nie psuje')
  await zapiszSerie(page)
  page.state.index = 0
  page.state.phase = 'set'
  page.applyVisibility()
  ok('w trakcie serii nie pytamy', page.askingRir() === false)
  ok('seria bez zapasu zapisana i tak', (phone.wpisy[1] || []).length >= 1)

  section('Zapas pokazuje sie bez odpowiedzi telefonu')
  // Zgloszone po treningu 02.11: rzad zapasu nie pojawial sie po serii.
  // Widocznosc zmieniala wylacznie odpowiedz telefonu, wiec z Bluetoothem
  // w kieszeni przychodzila z opoznieniem, a z telefonem w szatni nie
  // przychodzila wcale. Ten scenariusz celowo nie wola zadnego
  // przemalowania recznie - sprawdza to, co widzi palec po odlozeniu sztangi.
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  phone.setGlucha(true)
  jump(2000)
  if (page.state.phase === 'rest') page.press()
  jump(2000)
  page.press()
  ok('pytamy o zapas od razu', page.askingRir() === true)
  ok('cztery przyciski zapasu na ekranie',
    page.w.rirRow.filter((b) => b.visible).length === 4,
    page.w.rirRow.filter((b) => b.visible).length + ' widocznych')
  ok('strzalki ustapily od razu', page.w.prev.visible === false)
  ok('podpis pyta wprost, bez telefonu', /zosta/.test(page.w.load.attrs.text),
    page.w.load.attrs.text)
  phone.setGlucha(false)

  section('Bledy zgloszone po treningu 21.10')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  page.state.view = VIEW.SETS

  // 1. Szybkie klikniecie: rzad zapasu nie moze zostac na ekranie serii.
  await zapiszSerie(page)
  page.state.index = 0
  page.applyVisibility()
  ok('po serii pytamy o zapas', page.askingRir() === true)
  jump(2000)
  page.press()
  ok('po wznowieniu serii nie pytamy', page.askingRir() === false)
  ok('rzad zapasu zniknal z ekranu',
    page.w.rirRow.every((b) => !b.visible),
    page.w.rirRow.filter((b) => b.visible).length + ' widocznych')
  ok('strzalki wrocily', page.w.prev.visible === true)

  // 2. Ostatnia seria cwiczenia tez musi zapytac o zapas.
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  await zapiszSerie(page)
  await zapiszSerie(page)
  ok('po domknieciu cwiczenia przeszlismy dalej', page.state.index === 1,
    'index ' + page.state.index)
  ok('nadal pytamy o zapas ostatniej serii', page.askingRir() === true)
  // Pytanie dotyczy cwiczenia, na ktorym zapisalismy serie - a nie tego,
  // na ktore ekran wlasnie przeskoczyl po domknieciu poprzedniego.
  ok('i dotyczy cwiczenia z poprzedniej serii',
    !!page.state.rirFor && page.state.rirFor.ex === 1,
    'rirFor ' + JSON.stringify(page.state.rirFor) + ', index ' + page.state.index)
  ok('i tej konkretnej serii, po znaczniku jej polecenia',
    !!page.state.rirFor && (!!page.state.acked[page.state.rirFor.ref] ||
      (page.state.outbox || []).some((x) => x.m === 'SET' && x.cid === page.state.rirFor.ref)),
    JSON.stringify(page.state.rirFor))

  // 3. Ostatnia seria ostatniego cwiczenia ma dac podsumowanie.
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  for (let i = 0; i < 3; i++) {
    page.state.index = i
    await zapiszSerie(page)
    await zapiszSerie(page)
  }
  ok('caly plan zrobiony', phone.log.length === 6, phone.log.length + ' serii')
  ok('ostatnia seria tez pyta o zapas, zanim przyjdzie podsumowanie',
    page.state.view === VIEW.SETS && page.askingRir() === true, nazwa(page.state.view))
  // Klawisz glowny w tej chwili nie zaczyna nowej serii - pokazuje koniec.
  page.press()
  ok('po ostatniej serii jest podsumowanie', page.state.view === VIEW.DONE,
    nazwa(page.state.view))
  ok('przycisk niczego nie dopisal', phone.log.length === 6, phone.log.length + ' serii')

  section('Koniec planu bez telefonu')
  // Zgloszone 09.11: "zakonczenie ostatniego cwiczenia nie konczy treningu".
  // Podsumowanie pokazywala tylko odpowiedz telefonu, a z telefonem
  // w szatni ta nie przychodzila wcale.
  const naKoniec = async (zZapasem) => {
    phone.reset()
    phone.setGlucha(false)
    page.state.outbox = []
    page.askUnits()
    await flush(); await flush()
    phone.setGlucha(true)
    page.startUnit(0)
    await flush()
    let straz = 0
    while (!page.localComplete() && straz < 12) {
      straz += 1
      await zapiszSerie(page)
    }
    ok((zZapasem ? 'z zapasem' : 'bez zapasu') + ': plan zrobiony bez telefonu', page.localComplete(),
      straz + ' serii')
    if (zZapasem) page.setRir(1)
    else await poKoncu()
    ok((zZapasem ? 'z zapasem' : 'bez zapasu') + ': podsumowanie bez odpowiedzi telefonu',
      page.state.view === VIEW.DONE, nazwa(page.state.view))
    ok((zZapasem ? 'z zapasem' : 'bez zapasu') + ': podsumowanie liczy wszystkie serie',
      (page.sumy().sets || 0) === 6, JSON.stringify(page.sumy()))
    await cisza()
    phone.setGlucha(false)
    await osusz()
  }
  await naKoniec(true)
  await naKoniec(false)
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Powroty z ekranow bocznych i dolnych')
  phone.reset()
  page.startUnit(0)
  await flush(); await flush()
  await zapiszSerie(page)
  page.state.index = 0
  page.state.view = VIEW.SETS
  page.applyVisibility()

  handlers.gesture(GESTURE_DOWN)
  ok('w dol wchodzi dziennik', page.state.view === VIEW.LOG, nazwa(page.state.view))
  handlers.gesture(GESTURE_UP)
  ok('w gore wraca na serie', page.state.view === VIEW.SETS, nazwa(page.state.view))

  handlers.gesture(GESTURE_DOWN)
  page.w.logRows[0].onClick()
  ok('wchodzi poprawka', page.state.view === VIEW.FIX, nazwa(page.state.view))
  // Z poprawki ma byc wyjscie kazdym rozsadnym gestem.
  ok('w dol wraca z poprawki', (function () {
    handlers.gesture(GESTURE_DOWN)
    return page.state.view === VIEW.LOG
  })(), nazwa(page.state.view))
  page.w.logRows[0].onClick()
  ok('w gore tez wraca z poprawki', (function () {
    handlers.gesture(GESTURE_UP)
    return page.state.view === VIEW.LOG
  })(), nazwa(page.state.view))
  page.w.logRows[0].onClick()
  ok('w prawo wychodzi na serie', (function () {
    handlers.gesture(GESTURE_RIGHT)
    return page.state.view === VIEW.SETS
  })(), nazwa(page.state.view))

  section('Nic nie pisze do pamieci pod palcem')
  // Zapis do pamieci trwalej to zapis do pliku. Odlozony o 350 ms trafial
  // akurat w nastepne klikniecie - drugi plus, zapas zaraz po serii - i to
  // ono czekalo, az plik sie zapisze. Ma poczekac, az palec da spokoj.
  page.state.message = null
  page.state.rirFor = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  await zapis()

  page.dotyk()
  ok('zaraz po dotyku jest zajete', page.zajeteRysowaniem() === true)
  LICZNIK.ile = 0
  page.remember()
  page.rememberOutbox()
  for (let i = 0; i < 3; i++) {
    jump(300)
    handlers.zegary.filter((z) => z.zywy && z.ms === 350).forEach((z) => { z.zywy = false; z.fn() })
    await flush()
    page.dotyk()
  }
  ok('przy klikaniu co 300 ms nie zapisano nic', LICZNIK.ile === 0, LICZNIK.ile + ' zapisow')
  await zapis()
  ok('po chwili ciszy zapis wreszcie doszedl', LICZNIK.ile > 0, LICZNIK.ile + ' zapisow')

  // Blysk to teraz kolor tla licznika na chwile, nie animacja na caly ekran.
  page.state.view = VIEW.SETS
  page.applyVisibility()
  const zapisyLicznika = page.w.count.writes
  page.flash(0x1f6a46, 200)
  ok('blysk zmienia kolor licznika', page.w.count.attrs.normal_color === 0x1f6a46,
    String(page.w.count.attrs.normal_color))
  ok('blysk bez animacji', !page.w.count.anim)
  handlers.zegary.filter((z) => z.zywy && z.ms === 200).forEach((z) => { z.zywy = false; z.fn() })
  ok('i wraca do czerni sam', page.w.count.attrs.normal_color === 0x000000,
    String(page.w.count.attrs.normal_color))
  ok('blysk nie zostawia po sobie zegara', !page.flashTimer, String(page.flashTimer))
  ok('kosztuje dwa zapisy licznika', page.w.count.writes - zapisyLicznika === 2,
    (page.w.count.writes - zapisyLicznika) + ' zapisow')

  section('Przejscie miedzy ekranami ma byc tanie')
  // Kazdy zapis idzie do warstwy natywnej. Jedno przejscie robilo ich ponad
  // sto, z czego prawie wszystkie ustawialy to, co juz bylo - a przesuniecie
  // palcem zlozone w ich trakcie przepadalo, bo watek byl zajety.
  const zapisyWidgetow = () => Object.keys(page.w).reduce((n, k) => {
    const x = page.w[k]
    if (x && typeof x.writes === 'number') return n + x.writes
    if (Array.isArray(x)) return n + x.reduce((m, y) => m + ((y && y.writes) || 0), 0)
    return n
  }, 0)
  page.state.message = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  let wPrzed = zapisyWidgetow()
  page.showView(VIEW.STATS, -1, 'y')
  const kosztPrzejscia = zapisyWidgetow() - wPrzed
  ok('przejscie miesci sie w budzecie', kosztPrzejscia <= 40,
    kosztPrzejscia + ' zapisow do widgetow')

  wPrzed = zapisyWidgetow()
  page.applyVisibility()
  page.applyVisibility()
  ok('powtorzona widocznosc nie kosztuje nic', zapisyWidgetow() === wPrzed,
    (zapisyWidgetow() - wPrzed) + ' zapisow')

  wPrzed = zapisyWidgetow()
  page.setText(page.w.statVolume, 'to samo')
  page.setText(page.w.statVolume, 'to samo')
  ok('ten sam napis pisze sie raz', zapisyWidgetow() - wPrzed === 1,
    (zapisyWidgetow() - wPrzed) + ' zapisow')

  // W prawo z ekranu czasu ma wrocic na serie, a nie wyjsc z aplikacji.
  page.state.view = VIEW.STATS
  page.applyVisibility()
  ok('w prawo z czasu wraca na serie', handlers.gesture(GESTURE_RIGHT) === true &&
    page.state.view === VIEW.SETS, nazwa(page.state.view))
  page.state.view = VIEW.HEART
  page.applyVisibility()
  ok('w prawo z tetna tez', handlers.gesture(GESTURE_RIGHT) === true &&
    page.state.view === VIEW.SETS, nazwa(page.state.view))

  section('Klikniecie nie rozmawia z telefonem')
  // Rozmowa z telefonem idzie przez Bluetooth i przy nieosiagalnym telefonie
  // potrafi zajac watek na sekundy. Zrobiona wprost w uchwycie przycisku
  // trzymala go wcisnietym, dopoki nie wrocila - przycisk zostawal niebieski,
  // a klikanie wygladalo, jakby zegarek sie kleil. Wysylka ma byc odroczona
  // zawsze, nawet gdy telefon odpowiada od reki.
  page.state.view = VIEW.SETS
  page.applyVisibility()
  page.state.outbox = []
  page.drainWait = 0
  jump(3000)
  page.state.phase = 'set'
  handlers.zadania = 0
  page.press()
  ok('uchwyt klikniecia nie wysyla niczego na miejscu', handlers.zadania === 0,
    handlers.zadania + ' zadan')
  ok('ale polecenie juz czeka w kolejce', (page.state.outbox || []).length === 1,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  await flush(); await flush()
  ok('wysylka rusza zaraz potem', handlers.zadania > 0, handlers.zadania + ' zadan')
  await osusz()

  section('Milczacy telefon pyta sie coraz rzadziej')
  // Dobijanie sie co piec sekund przez caly trening obciaza zegarek, a telefon
  // i tak nie wroci szybciej, niz go wlaczysz.
  phone.setGlucha(true)
  page.state.outbox = []
  page.drainWait = 0
  jump(3000)
  page.state.phase = 'set'
  page.press()
  await cisza()
  const odstep1 = page.drainWait
  ok('po pierwszej nieudanej probie jest odstep', odstep1 >= 5000, String(odstep1))
  await cisza()
  const odstep2 = page.drainWait
  ok('kolejna nieudana proba wydluza odstep', odstep2 > odstep1,
    odstep1 + ' -> ' + odstep2)
  ok('odstep nie rosnie w nieskonczonosc', odstep2 <= 120000, String(odstep2))
  phone.setGlucha(false)
  page.drainWait = 0
  await osusz()
  ok('po powrocie lacznosci odstep znika', page.drainWait === 0, String(page.drainWait))

  section('Jedno nacisniecie nie moze kosztowac zapisu calego planu')
  // Zapis do pamieci trwalej to na zegarku zapis do pliku. Gdy stan treningu
  // trzymal w jednym kawalku wykres tetna i plan z drabinkami ciezarow,
  // kazde nacisniecie przycisku zapisywalo kilka kilobajtow - i wszystkie
  // przyciski zaczynaly sie kleic. Ciezkie rzeczy maja wlasny klucz
  // i wlasny, rzadszy rytm.
  const kosztem = (fn) => {
    LICZNIK.ile = 0
    LICZNIK.bajtow = 0
    LICZNIK.klucze = {}
    fn()
    return { ile: LICZNIK.ile, bajtow: LICZNIK.bajtow }
  }
  page.state.view = VIEW.SETS
  page.applyVisibility()

  const kNastepne = kosztem(() => page.move(1))
  page.move(-1)
  ok('przejscie na kolejne cwiczenie nie dotyka pamieci na miejscu', kNastepne.ile === 0,
    kNastepne.ile + ' zapisow, ' + kNastepne.bajtow + ' bajtow')
  await zapis()
  ok('zapis przychodzi dopiero po oddaniu watku', LICZNIK.ile > 0, String(LICZNIK.ile))

  page.state.view = VIEW.EDIT
  page.applyVisibility()
  const kCiezar = kosztem(() => page.stepWeight(1))
  page.stepWeight(-1)
  ok('zmiana ciezaru nie dotyka pamieci wcale', kCiezar.ile === 0,
    kCiezar.ile + ' zapisow')

  page.state.view = VIEW.SETS
  page.applyVisibility()
  jump(3000)
  page.state.phase = 'set'
  const kSeria = kosztem(() => page.press())
  await cisza()
  ok('zapis serii tez nie dotyka pamieci na miejscu', kSeria.ile === 0,
    kSeria.ile + ' zapisow, ' + kSeria.bajtow + ' bajtow')
  await zapis()
  ok('ale dojezdza chwile pozniej', LICZNIK.bajtow > 0, LICZNIK.bajtow + ' bajtow')

  section('Telefon, ktory nie odpowiada wcale')
  // Tak zachowuje sie zegarek, gdy aplikacja Zepp na telefonie nie dziala:
  // wiadomosc nie dociera nigdzie i nie wraca ani odpowiedz, ani blad.
  phone.setGlucha(true)
  page.state.message = null
  page.send('STATE', {}, true)
  await flush(); await flush()
  ok('najpierw nie zglasza niczego', page.state.message === null, page.state.message)

  const budziki = handlers.zegary.filter((z) => z.zywy && z.ms >= 5000)
  ok('zadanie ma wlasny budzik', budziki.length === 1, budziki.length + ' budzikow')
  budziki.forEach((z) => z.fn())
  await flush(); await flush()
  // Przy trwajacym treningu cisza telefonu nie moze zabrac ekranu.
  // Zegarek wie, na ktorym cwiczeniu stoi, i ma na nim zostac - wczesniej
  // jedna nieudana odpowiedz gasila wszystko w srodku serii.
  ok('zywy trening zostaje na ekranie', page.state.message === null, page.state.message)
  ok('zamiast tego znacznik niezsynchronizowania', page.state.offline === true)

  // Komunikat zostal juz tylko na jedna sytuacje: nie ma ani treningu,
  // ani zapamietanego planu, wiec nie ma czego pokazac. Z planem w pamieci
  // zegarek idzie na wybor jednostki, bo trening da sie zaczac bez telefonu.
  const zywy = page.state.data
  page.state.data = null
  page.state.message = null
  delete LocalStorage.dane['gymbro-units']
  page.state.units = null
  page.send('STATE', {}, true)
  await flush(); await flush()
  handlers.zegary.filter((z) => z.zywy && z.ms >= 5000).forEach((z) => { z.zywy = false; z.fn() })
  await flush(); await flush()
  ok('bez treningu mowi co otworzyc',
    page.state.message === 'Otwórz Zepp na telefonie', page.state.message)
  ok('ekran nie zostaje na Lacze',
    !/Łączę/.test(page.state.message || ''), page.state.message)
  page.state.data = zywy
  page.state.message = null
  phone.setGlucha(false)
  page.setOffline(false)

  section('Wibracje')
  ok('cos zawibrowalo przy zapisie', buzzes.length > 0, buzzes.length + ' razy')
  const inne = buzzes.filter((b) => b !== VIBRATOR_SCENE_DURATION_LONG)
  ok('w calej sesji wylacznie jeden ciagly tryb', inne.length === 0,
    'obce tryby: ' + inne.join(','))

  section('Blokada wibracji')
  buzzes.length = 0
  jump(BUZZ_GUARD + 100)
  page.buzz()
  page.buzz()
  page.buzz()
  ok('trzy wywolania pod rzad to jedno szarpniecie', buzzes.length === 1,
    buzzes.length + ' razy')
  jump(BUZZ_GUARD + 100)
  page.buzz()
  ok('po blokadzie znowu mozna', buzzes.length === 2, buzzes.length + ' razy')

  section('Koniec zaplanowanej przerwy')
  // Po zakonczeniu nie ma planu, a przerwa bierze swoj czas z cwiczenia.
  page.startUnit(0)
  await flush(); await flush()
  buzzes.length = 0
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.state.phase = 'rest'
  page.state.phaseSince = Date.now()
  page.restRang = false
  page.paintClock()
  ok('w trakcie przerwy cicho', buzzes.length === 0, buzzes.join(','))
  jump(200 * 1000)
  page.paintClock()
  ok('po uplywie przerwy jedna ciagla wibracja',
    buzzes.length === 1 && buzzes[0] === VIBRATOR_SCENE_DURATION_LONG, buzzes.join(','))
  page.paintClock()
  ok('nie powtarza sie co sekunde', buzzes.length === 1, buzzes.length + ' razy')

  section('Tetno na srodku')
  page.state.hr = 72
  page.paintHeartValue()
  const dwie = page.w.hrIcon.attrs.x + page.w.hrIcon.attrs.w
  page.state.hr = 148
  page.paintHeartValue()
  const trzy = page.w.hrIcon.attrs.x + page.w.hrIcon.attrs.w
  ok('liczba zajmuje cala szerokosc i jest wysrodkowana',
    page.w.hr.attrs.x === 0 && page.w.hr.attrs.w === 480)
  ok('serce odsuwa sie przy trzech cyfrach', trzy < dwie, dwie + ' -> ' + trzy)
  ok('serce nie wychodzi poza ekran', page.w.hrIcon.attrs.x > 0, 'x ' + page.w.hrIcon.attrs.x)
  ok('nie ma juz podpisu bpm', page.w.hrLabel === undefined)

  section('Zgubiona odpowiedz nie dubluje serii')
  // Najgrozniejszy przypadek kolejki: telefon zapisal serie, ale odpowiedz
  // nie wrocila. Zegarek nie wie, czy doszlo, wiec ponawia - i bez znacznika
  // polecenia ta sama seria wpadlaby do historii drugi raz. Odtworzyc tego
  // nie da sie recznie, bo nie widac roznicy miedzy zgubiona odpowiedzia
  // a zgubionym poleceniem.
  page.state.message = null
  page.state.offline = false
  page.state.outbox = []
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.applyVisibility()
  const cwDubel = page.current()
  const przedDubel = phone.done[cwDubel.id] || 0

  phone.setGubiOdpowiedz(true)
  page.state.phase = 'set'
  page.press()
  await flush(); await flush()
  ok('telefon zapisal serie', (phone.done[cwDubel.id] || 0) === przedDubel + 1,
    przedDubel + ' -> ' + (phone.done[cwDubel.id] || 0))
  await cisza()
  ok('zegarek nie dostal potwierdzenia, wiec trzyma w kolejce',
    (page.state.outbox || []).length === 1, JSON.stringify((page.state.outbox || []).map((x) => x.m)))

  phone.setGubiOdpowiedz(false)
  await osusz()
  ok('po ponowieniu seria nadal jedna', (phone.done[cwDubel.id] || 0) === przedDubel + 1,
    przedDubel + ' -> ' + (phone.done[cwDubel.id] || 0))
  ok('kolejka opruzniona', (page.state.outbox || []).length === 0)
  ok('zegarek liczy te serie raz, nie dwa', page.shownDone(cwDubel) === przedDubel + 1,
    przedDubel + ' -> ' + page.shownDone(cwDubel))
  ok('i nic nie czeka', page.pendingCount() === 0, String(page.pendingCount()))

  section('Zapas pytany od razu po serii')
  // Pytanie o zapas wisialo na danych od telefonu: zegarek szukal zapisanej
  // serii w jego odpowiedzi. Odpowiedz przychodzi z opoznieniem, a bez
  // lacznosci nie przychodzi wcale - wiec rzad pojawial sie pozno albo nigdy.
  phone.setGlucha(true)
  page.state.message = null
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.state.rirFor = null
  // Czyscimy zapisane serie tego cwiczenia w danych od telefonu. Bez tego
  // stara droga - szukanie serii w jego odpowiedzi - trafialaby na wpis
  // z wczesniejszej sekcji i test przechodzilby przypadkiem.
  const cwZapas = page.current()
  cwZapas.entries = []
  page.applyVisibility()
  jump(3000)
  page.state.phase = 'set'
  page.press()
  ok('pytanie o zapas jest natychmiast, bez odpowiedzi telefonu',
    page.askingRir() === true, String(page.askingRir()))
  page.applyVisibility()
  ok('rzad zapasu juz na ekranie', page.w.rirRow.filter((b) => b.visible).length === 4,
    String(page.w.rirRow.filter((b) => b.visible).length))
  ok('strzalki ustapily', page.w.prev.visible === false)

  // Odpowiedz tez idzie bez telefonu - laduje w kolejce.
  page.w.rirRow[1].onClick()
  ok('po wyborze przestajemy pytac', page.askingRir() === false)
  // Seria jeszcze nie wyszla, wiec zapas jedzie razem z nia - bez osobnego
  // polecenia, ktore telefon musialby dopasowac do "ostatniej serii".
  const seriaZZapasem = (page.state.outbox || []).filter((x) => x.m === 'SET').pop()
  ok('zapas doklejony do czekajacej serii', seriaZZapasem && seriaZZapasem.p.rir === 1,
    JSON.stringify((page.state.outbox || []).map((x) => x.m + ':' + x.p.rir)))
  const wDzienniku = page.nakladka().ex[seriaZZapasem.p.exerciseId].entries
  ok('i od razu widac go w dzienniku', wDzienniku.some((w) => w.rir === 1),
    JSON.stringify(wDzienniku))
  await cisza()
  phone.setGlucha(false)
  await osusz()
  const naTelefonie = (phone.wpisy[seriaZZapasem.p.exerciseId] || []).slice(-1)[0]
  ok('telefon dostal serie razem z zapasem', naTelefonie && naTelefonie.rir === 1,
    JSON.stringify(naTelefonie))

  section('Zapas powtorzen na ekranie poprawki')
  // Ten sam rzad co po serii: cztery przyciski, jedno nacisniecie.
  // Wczesniej byl tu jeden przycisk krecacy sie w kolko, czyli ta sama
  // rzecz wygladala inaczej zaleznie od tego, kiedy sie ja podaje.
  page.state.message = null
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.applyVisibility()
  page.showView(VIEW.LOG, 1)
  const doPoprawki = page.logRows()[0]
  page.openFix(0)
  ok('otwarta poprawka serii', page.state.view === VIEW.FIX, nazwa(page.state.view))
  ok('rzad zapasu ma cztery przyciski', page.w.fixRirRow.length === 4,
    String(page.w.fixRirRow.length))
  ok('wszystkie widoczne', page.w.fixRirRow.every((b) => b.visible))
  ok('ekran poprawki czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))

  // Przy serii bez zapasu podpis mowi to wprost. Sam kolor przyciskow
  // nie odroznial "nie podano" od zapasu zerowego - cztery szare pola
  // wygladaja tak samo, gdy nie wybrano nic i gdy wybrano zero.
  ok('podpis mowi, ze zapasu nie podano',
    /nie podano/.test(page.w.fixRirLabel.attrs.text), page.w.fixRirLabel.attrs.text)

  // Jedno nacisniecie ustawia, drugie to samo - zdejmuje.
  page.w.fixRirRow[1].onClick()
  ok('nacisniecie ustawia zapas', page.state.fix.rir === 1, String(page.state.fix.rir))
  ok('wybrany jest podswietlony', page.w.fixRirRow[1].props.normal_color !==
    page.w.fixRirRow[0].props.normal_color)
  ok('i podpis podaje liczbe', /zapas: 1/.test(page.w.fixRirLabel.attrs.text),
    page.w.fixRirLabel.attrs.text)
  page.w.fixRirRow[1].onClick()
  ok('to samo nacisniecie zdejmuje zapas', page.state.fix.rir === -1,
    String(page.state.fix.rir))
  page.w.fixRirRow[2].onClick()

  // Zapis niesie zapas razem z ciezarem i powtorzeniami.
  page.w.fixSave.onClick()
  await osusz()
  const wpis = phone.log.filter((e) => e.setId === doPoprawki.id).pop()
  ok('telefon dostal poprawke z zapasem', wpis && wpis.rir === 2,
    JSON.stringify(wpis))
  ok('po zapisie wraca do dziennika', page.state.view === VIEW.LOG, nazwa(page.state.view))

  section('Kasowanie serii na dwa nacisniecia')
  // Jedno przypadkowe dotkniecie nie moze zabrac serii.
  page.openFix(0)
  const doKasacji = page.state.fix.id
  const serieNaStarcie = page.logRows().length
  page.w.fixDelete.onClick()
  ok('pierwsze nacisniecie tylko pyta', page.state.fixConfirm === true)
  ok('podpis sie zmienil', /pewno/.test(page.w.fixDelete.props.text || ''),
    page.w.fixDelete.props.text)
  ok('nic jeszcze nie skasowane', page.state.view === VIEW.FIX, nazwa(page.state.view))

  page.w.fixDelete.onClick()
  await osusz()
  ok('drugie nacisniecie kasuje', page.state.view === VIEW.LOG, nazwa(page.state.view))
  ok('serii ubylo', page.logRows().length === serieNaStarcie - 1,
    serieNaStarcie + ' -> ' + page.logRows().length)
  ok('telefon tez jej nie ma', !phone.wpisy[page.current().id] ||
    !phone.wpisy[page.current().id].some((w) => w.id === doKasacji),
    String(doKasacji))

  // Uzbrojony przycisk sam sie wycofuje.
  page.openFix(0)
  page.w.fixDelete.onClick()
  ok('znowu uzbrojony', page.state.fixConfirm === true)
  handlers.zegary.filter((z) => z.zywy && z.ms === 4000)
    .forEach((z) => { z.zywy = false; z.fn() })
  ok('po chwili sam sie wycofuje', page.state.fixConfirm === false)
  ok('i podpis wraca', page.w.fixDelete.props.text === 'usuń', page.w.fixDelete.props.text)
  handlers.gesture(GESTURE_DOWN)
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Wstrzymanie dziala od razu i bez telefonu')
  page.state.message = null
  page.state.offline = false
  page.state.outbox = []
  page.state.pausedLocal = null
  phone.paused = false
  phone.polecenia.length = 0
  page.state.view = VIEW.CONTROL
  page.applyVisibility()

  // Bez czekania na telefon: przycisk ma zareagowac w tej samej chwili.
  phone.setGlucha(true)
  page.togglePause()
  ok('wstrzymane natychmiast, bez odpowiedzi telefonu', page.paused() === true)
  ok('napis na przycisku juz zmieniony', page.w.ctrlPause.props.text === 'WZNÓW',
    page.w.ctrlPause.props.text)
  ok('stan mowi wstrzymany', page.w.ctrlState.attrs.text === 'wstrzymany',
    page.w.ctrlState.attrs.text)

  // Czas stoi, dopoki wstrzymane.
  const czas0 = page.sessionSeconds()
  jump(30 * 1000)
  ok('czas nie leci na wstrzymaniu', page.sessionSeconds() === czas0,
    czas0 + ' -> ' + page.sessionSeconds())

  // Wznowienie tez bez telefonu - to bylo zepsute najbardziej: klikalo sie
  // i nic, a trening zostawal wstrzymany mimo cwiczenia dalej.
  page.togglePause()
  ok('wznowione mimo braku lacznosci', page.paused() === false)
  ok('stan mowi trwa', page.w.ctrlState.attrs.text === 'trwa', page.w.ctrlState.attrs.text)
  const czas1 = page.sessionSeconds()
  jump(10 * 1000)
  ok('po wznowieniu czas znowu leci', page.sessionSeconds() > czas1 + 9,
    czas1 + ' -> ' + page.sessionSeconds())

  await cisza()
  ok('ekran nie zostal zabrany przez komunikat', page.state.message === null, page.state.message)
  ok('oba polecenia czekaja w kolejce', (page.state.outbox || []).length === 2,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  ok('zegarek wie, ze cos nie dojechalo', page.state.offline === true)

  section('Kolejka dojezdza, gdy wraca lacznosc')
  phone.setGlucha(false)
  await osusz()
  ok('kolejka pusta', (page.state.outbox || []).length === 0,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  ok('telefon dostal oba polecenia w kolejnosci',
    phone.polecenia.join(',') === 'PAUSE,RESUME', phone.polecenia.join(','))
  ok('telefon nie jest wstrzymany', phone.paused === false)
  ok('zegarek oddal stery telefonowi', page.state.pausedLocal === null,
    String(page.state.pausedLocal))
  ok('znacznik niezsynchronizowania zdjety', page.state.offline === false)

  // Seria zapisana bez lacznosci tez ma dojechac.
  phone.setGlucha(true)
  page.state.view = VIEW.SETS
  page.state.index = 0
  page.applyVisibility()
  const zrobionePrzed = phone.log.length
  // press() w przerwie tylko zaczyna serie - zapis idzie z fazy cwiczenia.
  page.state.phase = 'set'
  page.press()
  await cisza()
  ok('seria czeka w kolejce', (page.state.outbox || []).length === 1,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  ok('ekran serii nadal stoi', page.state.message === null, page.state.message)
  phone.setGlucha(false)
  await osusz()
  ok('seria dojechala do telefonu', phone.log.length === zrobionePrzed + 1,
    zrobionePrzed + ' -> ' + phone.log.length)
  ok('kolejka znowu pusta', (page.state.outbox || []).length === 0)
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Godzina zamiast kropek')
  page.state.message = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  ok('zegar widoczny na ekranie serii', page.w.clock.visible)
  const teraz = page.w.clock.attrs.text
  ok('pokazuje godzine w formacie HH:MM', /^[0-2][0-9]:[0-5][0-9]$/.test(teraz || ''), teraz)

  page.state.view = VIEW.STATS
  page.applyVisibility()
  ok('zegar widoczny takze na ekranie czasu', page.w.clock.visible)
  page.state.view = VIEW.HEART
  page.applyVisibility()
  ok('zegar widoczny takze na tetnie', page.w.clock.visible)

  page.state.view = VIEW.CONTROL
  page.applyVisibility()
  ok('poza karuzela zegara nie ma', !page.w.clock.visible)
  page.state.view = VIEW.SETS
  page.applyVisibility()

  // Minuta sie nie zmienila - nie ma po co pisac do widgetu co sekunde.
  const zapisyZegara = page.w.clock.writes
  page.paintWallClock(); page.paintWallClock(); page.paintWallClock()
  ok('nie przepisuje tej samej minuty', page.w.clock.writes === zapisyZegara,
    zapisyZegara + ' -> ' + page.w.clock.writes)
  jump(60 * 1000)
  page.paintWallClock()
  ok('po minucie godzina sie zmienia', page.w.clock.attrs.text !== teraz,
    teraz + ' -> ' + page.w.clock.attrs.text)
  ok('nie ma juz kropek', page.w.dots === undefined)

  section('Szybkie gesty bez animacji')
  // Przejscie bylo czarna zaslona animowana w 60 klatkach na sekunde, a po
  // niej odbudowa przyciskow. Cwierc sekundy bez reakcji na palec przy
  // kazdej zmianie ekranu - i to bylo czuc jako lag. Ekran zmienia sie
  // teraz od razu i nic po zmianie nie zostaje do posprzatania.
  page.state.message = null
  page.state.rirFor = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  const animacjePrzed = ANIMACJE.czeka.length
  handlers.wolneAnimacje = true
  handlers.gesture(GESTURE_UP)
  handlers.gesture(GESTURE_UP)
  ok('dwa szybkie gesty to dwa ekrany dalej', page.state.view === VIEW.HEART, nazwa(page.state.view))
  ok('zmiana ekranu niczego nie animuje', ANIMACJE.czeka.length === animacjePrzed,
    (ANIMACJE.czeka.length - animacjePrzed) + ' animacji')
  ok('ekran tetna czysty od razu', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  handlers.gesture(GESTURE_DOWN)
  handlers.gesture(GESTURE_DOWN)
  ok('wrocilismy na serie', page.state.view === VIEW.SETS, nazwa(page.state.view))
  ok('ekran serii czysty od razu', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  handlers.wolneAnimacje = false
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Tonaz po cwiczeniach')
  // Podsumowanie pokazywalo tylko sume calego treningu. To nie mowi, czy
  // ciezka byla pierwsza czy ostatnia polowa - a przy porownaniu z poprzednim
  // razem liczy sie wlasnie rozbicie.
  page.state.message = null
  page.state.view = VIEW.DONE
  page.applyVisibility()

  const tonWiersze = page.tonRows()
  ok('sa wiersze dla cwiczen z seriami', tonWiersze.length > 0, String(tonWiersze.length))
  ok('kazdy wiersz ma nazwe, serie i tonaz',
    tonWiersze.every((r) => r.name && r.sets > 0 && r.ton > 0), JSON.stringify(tonWiersze))

  // Suma z wierszy musi sie zgadzac z tonazem policzonym recznie z dziennika.
  const tonRecznie = ((page.state.data && page.state.data.exercises) || []).reduce(
    (n, ex) => n + (ex.entries || []).filter((e) => !e.warmup)
      .reduce((m, e) => m + e.weight * e.reps, 0), 0)
  const zWierszy = tonWiersze.reduce((n, r) => n + r.ton, 0)
  ok('suma wierszy zgadza sie z dziennikiem', Math.abs(zWierszy - tonRecznie) <= tonWiersze.length,
    zWierszy + ' vs ' + Math.round(tonRecznie))

  // Wchodzi sie w dol z podsumowania, wychodzi w gore.
  handlers.gesture(GESTURE_DOWN)
  ok('w dol wchodzi tonaz', page.state.view === VIEW.TONS, nazwa(page.state.view))
  ok('ekran tonazu czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))
  ok('naglowek i suma na miejscu', page.w.tonTitle.attrs.text === 'Tonaż' &&
    /razem \d+ kg/.test(page.w.tonSub.attrs.text || ''),
    page.w.tonTitle.attrs.text + ' / ' + page.w.tonSub.attrs.text)
  ok('pierwszy wiersz niesie kilogramy', /\d+ kg$/.test(page.w.tonRows[0].attrs.text || ''),
    page.w.tonRows[0].attrs.text)
  handlers.gesture(GESTURE_UP)
  ok('w gore wraca do podsumowania', page.state.view === VIEW.DONE, nazwa(page.state.view))
  ok('podsumowanie mowi, gdzie szukac tonazu',
    /tonaż/.test(page.w.doneHint.attrs.text || ''), page.w.doneHint.attrs.text)
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Nazwa cwiczenia jezdzi tylko wtedy, gdy musi')
  // Szerokosc liczona po znakach zawsze wypadala w gore, wiec nazwy, ktore
  // spokojnie miesza sie na ekranie, i tak jezdzily na boki - a jezdzaca
  // nazwa nie jest wysrodkowana. Do tego animacja chodzila w kolko przez
  // caly trening i zjadala klatki wszystkim pozostalym.
  // 1100 ms to przerwa miedzy przejazdami nazwy (NAME_HOLD_MS w stronie).
  const petleNazwy = () => handlers.zegary.filter(
    (z) => z.zywy && z.ms === 1100
  ).length

  page.state.view = VIEW.SETS
  page.applyVisibility()
  page.lastName = null
  page.paintName('Przysiad')
  ok('krotka nazwa wysrodkowana', page.w.name.attrs.align_h === 'CH',
    String(page.w.name.attrs.align_h))
  ok('krotka nazwa w pudelku na caly ekran', page.w.name.attrs.w === 356,
    String(page.w.name.attrs.w))
  ok('krotka nazwa bez zadnej petli', petleNazwy() === 0, petleNazwy() + ' petli')

  // Nazwa, ktora po staremu by jezdzila: 30 znakow razy 12 to 360, czyli
  // ponad pudelko - a naprawde zajmuje 270 pikseli i miesci sie z zapasem.
  const mieszczaca = 'Uginanie supinowane hantli stojac'
  ok('po staremu uznana za za dluga', mieszczaca.length * 12 > 356,
    String(mieszczaca.length * 12))
  page.paintName(mieszczaca)
  ok('zmierzona naprawde - miesci sie', page.w.name.attrs.align_h === 'CH',
    String(page.w.name.attrs.align_h))
  ok('i nie jezdzi', petleNazwy() === 0, petleNazwy() + ' petli')

  // Nazwa naprawde za dluga nadal ma jezdzic - inaczej konca nie da sie przeczytac.
  const zaDluga = 'Wyciskanie sztangi leżąc waskim chwytem na lawce skosnej'
  page.paintName(zaDluga)
  ok('bardzo dluga nazwa jednak jezdzi', page.w.name.attrs.align_h === 'L',
    String(page.w.name.attrs.align_h))
  ok('i ma swoja petle', petleNazwy() === 1, petleNazwy() + ' petli')

  // To jest ten blad: powrot na krotka nazwe musi te petle zatrzymac.
  page.paintName('OHP')
  ok('po powrocie na krotka nazwe petla stoi', petleNazwy() === 0,
    petleNazwy() + ' petli')
  ok('i nazwa znowu wysrodkowana', page.w.name.attrs.align_h === 'CH',
    String(page.w.name.attrs.align_h))

  // Gdyby system nie umial zmierzyc napisu, zostaje stara arytmetyka.
  hmUI.brakPomiaru = true
  page.lastName = null
  page.paintName('Przysiad')
  ok('bez pomiaru krotka nazwa dalej wysrodkowana',
    page.w.name.attrs.align_h === 'CH', String(page.w.name.attrs.align_h))
  hmUI.brakPomiaru = false
  page.lastName = null

  section('Trening zaczyna sie bez telefonu')
  // Najwazniejsza obietnica pilota: telefon moze zostac w szatni.
  // Dotad sesje zakladal telefon i to on nadawal jej numer, wiec bez
  // lacznosci trening nie zaczynal sie wcale.
  page.state.message = null
  page.state.offline = false
  page.state.outbox = []
  page.state.data = null
  page.state.sessionId = null
  page.state.localId = null
  phone.active = false
  phone.starty.length = 0
  phone.localIds = {}
  phone.seen = {}
  phone.log.length = 0

  // Plan trafil do pamieci przy ktoryms wczesniejszym polaczeniu.
  page.askUnits()
  await flush(); await flush()
  ok('jednostki pobrane, gdy telefon slychac', (page.state.units || []).length === 3,
    String((page.state.units || []).length))

  // Od tej chwili telefon milczy.
  phone.setGlucha(true)
  page.state.units = null
  page.askUnits()
  await cisza()
  ok('jednostki wzielo z pamieci', (page.state.units || []).length === 3,
    String((page.state.units || []).length))
  ok('ekran wyboru, a nie komunikat', page.state.view === VIEW.START && page.state.message === null,
    nazwa(page.state.view) + ' / ' + page.state.message)
  ok('widac, ze telefon nie odpowiada', page.state.offline === true)

  // Start jednostki bez telefonu.
  page.startUnit(0)
  ok('trening ruszyl', page.state.view === VIEW.SETS, nazwa(page.state.view))
  ok('sesja ma wlasny znacznik', typeof page.state.localId === 'string' && page.state.localId.length > 1,
    String(page.state.localId))
  ok('jednostka zbudowana z pamieci', (page.current() || {}).name !== undefined,
    JSON.stringify(page.current() && page.current().name))
  ok('zero serii na starcie', page.shownDone(page.current()) === 0,
    String(page.shownDone(page.current())))
  ok('start czeka w kolejce', (page.state.outbox || []).length === 1,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))

  // Cwiczenie, na ktorym zaczynamy: po domknieciu go ekran sam przeskakuje
  // na kolejne, wiec current() juz go nie pokaze.
  const cwOffline = page.current()
  // press() w przerwie tylko zaczyna serie, wiec przed kazdym zapisem
  // ustawiamy faze robocza. Do tego sekunda przerwy: zapis ma blokade
  // przed podwojnym nacisnieciem i dwie serie w tej samej milisekundzie
  // liczylyby sie jako jedna.
  page.state.phase = 'set'
  page.press()
  await cisza()
  jump(2000)
  page.state.phase = 'set'
  page.press()
  await cisza()
  ok('serie policzone u siebie', page.shownDone(cwOffline) === 2,
    String(page.shownDone(cwOffline)))
  ok('wszystko czeka w kolejce', (page.state.outbox || []).length === 3,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  ok('telefon o niczym nie wie', phone.starty.length === 0 && phone.log.length === 0,
    phone.starty.length + ' startow, ' + phone.log.length + ' serii')

  section('Synchronizacja po odzyskaniu lacznosci')
  const stojacyNa = page.state.index
  phone.setGlucha(false)
  await osusz()
  ok('kolejka pusta', (page.state.outbox || []).length === 0,
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  ok('telefon zalozyl dokladnie jedna sesje', phone.starty.length === 1,
    JSON.stringify(phone.starty))
  ok('z naszym znacznikiem i nasza godzina startu',
    phone.starty[0] && phone.starty[0].localId && phone.starty[0].startedAt > 0,
    JSON.stringify(phone.starty[0]))
  ok('obie serie doszly', phone.log.length === 2, String(phone.log.length))
  ok('kazda ze swoja godzina wykonania', phone.log.every((w) => w.doneAt > 0),
    JSON.stringify(phone.log.map((w) => w.doneAt)))
  ok('zegarek przyjal numer od telefonu', page.state.sessionId === 101,
    String(page.state.sessionId))
  ok('znacznik juz niepotrzebny', page.state.localId === null, String(page.state.localId))
  ok('nie cofnelo nas na poczatek planu', page.state.index === stojacyNa,
    stojacyNa + ' -> ' + page.state.index)
  ok('ekran serii, bez komunikatu', page.state.view === VIEW.SETS && page.state.message === null,
    nazwa(page.state.view) + ' / ' + page.state.message)

  // Ponowione zgloszenie startu nie zaklada drugiej sesji.
  page.queue('START', { planId: 1, localId: phone.starty[0].localId, startedAt: Date.now() })
  await flush(); await flush(); await flush()
  ok('powtorzony start nie zalozyl drugiej sesji', phone.starty.length === 1,
    JSON.stringify(phone.starty))

  section('Koniec treningu tez bez telefonu')
  phone.setGlucha(true)
  phone.endedAt = null
  page.finishWorkout()
  await cisza()
  ok('zegarek wrocil do wyboru jednostki', page.state.view === VIEW.START,
    nazwa(page.state.view))
  ok('koniec czeka w kolejce', (page.state.outbox || []).some((x) => x.m === 'FINISH'),
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  phone.setGlucha(false)
  await osusz()
  ok('telefon zamknal trening', phone.active === false)
  ok('z godzina z zegarka, nie z chwili synchronizacji', phone.endedAt > 0,
    String(phone.endedAt))
  page.state.view = VIEW.SETS
  page.applyVisibility()

  section('Zgasniecie ekranu w treningu bez telefonu')
  // Zgasniecie ekranu ubija mini-aplikacje - to dzieje sie co chwile, przy
  // kazdym opuszczeniu nadgarstka. Trening zalozony na zegarku nie ma go
  // skad odtworzyc poza wlasna pamiecia, bo telefon jeszcze o nim nie wie.
  phone.setGlucha(false)
  phone.active = false
  phone.starty.length = 0
  phone.localIds = {}
  phone.seen = {}
  phone.log.length = 0
  page.state.message = null
  page.state.outbox = []
  page.state.data = null
  page.state.sessionId = null
  page.state.localId = null
  page.askUnits()
  await flush(); await flush()

  phone.setGlucha(true)
  page.startUnit(0)
  await cisza()
  jump(2000)
  page.state.phase = 'set'
  page.press()
  await cisza()
  const znacznikPrzed = page.state.localId
  const indeksPrzed = page.state.index
  const kolejkaPrzed = (page.state.outbox || []).map((x) => x.cid)
  const seriiPrzed = page.shownDone(page.current())
  ok('trening stoi przed zgasnieciem', page.state.view === VIEW.SETS,
    nazwa(page.state.view))

  // Wspolczynniki spalania przychodza wylacznie z telefonu, a ten milczy.
  // Zapisane raz, musza przezyc zgasniecie ekranu - inaczej po pierwszym
  // opuszczeniu nadgarstka licznik kalorii staje na reszte treningu.
  page.state.burn = { k0: -6.1, k1: 0.116 }
  page.state.burnKcal = 42
  page.remember()

  // Stan zapisuje sie z opoznieniem - przed zgasnieciem musi zdazyc.
  await zapis()
  // Zgasniecie ekranu: aplikacja startuje od zera, pamiec trwala zostaje.
  const drugie = loadPage(phone)
  const page2 = drugie.page
  page2.build()
  await flush(); await flush()
  await cisza()
  ok('po restarcie wracamy do treningu, a nie do komunikatu',
    page2.state.view === VIEW.SETS && page2.state.message === null,
    nazwa(page2.state.view) + ' / ' + page2.state.message)
  ok('to ta sama sesja', page2.state.localId === znacznikPrzed,
    znacznikPrzed + ' -> ' + page2.state.localId)
  ok('stoimy na tym samym cwiczeniu', page2.state.index === indeksPrzed,
    indeksPrzed + ' -> ' + page2.state.index)
  ok('plan jednostki odtworzony', ((page2.state.data || {}).exercises || []).length > 0,
    String(((page2.state.data || {}).exercises || []).length))
  ok('zapisana seria nie przepadla', page2.shownDone(page2.current()) === seriiPrzed,
    seriiPrzed + ' -> ' + page2.shownDone(page2.current()))
  // Dokladnie ta sama kolejka, nie "co najmniej". Luzne sprawdzenie
  // przepuszczalo podwajanie kolejki przy kazdym restarcie.
  ok('kolejka przezyla restart co do polecenia',
    JSON.stringify((page2.state.outbox || []).map((x) => x.cid)) === JSON.stringify(kolejkaPrzed),
    JSON.stringify((page2.state.outbox || []).map((x) => x.m)))
  ok('spalone kalorie przezyly restart', page2.state.burnKcal === 42,
    String(page2.state.burnKcal))
  ok('i wspolczynniki, z ktorych rosna',
    !!(page2.state.burn && page2.state.burn.k1), JSON.stringify(page2.state.burn))
  const kcalPrzed = page2.state.burnKcal
  page2.noteHeart(140)
  jump(60000)
  page2.noteHeart(140)
  ok('wiec licznik idzie dalej, bez telefonu', page2.state.burnKcal > kcalPrzed,
    kcalPrzed.toFixed(1) + ' -> ' + page2.state.burnKcal.toFixed(1))

  section('Pierwsze otwarcie bez telefonu')
  // Z zapamietanym planem da sie zaczac trening nawet wtedy, gdy telefon
  // milczy od samego poczatku - inaczej caly tryb offline jest nieosiagalny.
  const trzecie = loadPage(phone)
  const page3 = trzecie.page
  page3.state.message = null
  page3.forget()
  page3.build()
  await flush(); await flush()
  trzecie.handlers.zegary.filter((z) => z.zywy && z.ms >= 5000)
    .forEach((z) => { z.zywy = false; z.fn() })
  await flush(); await flush()
  ok('wyborem jednostki, nie komunikatem',
    page3.state.view === VIEW.START && page3.state.message === null,
    nazwa(page3.state.view) + ' / ' + page3.state.message)
  ok('jednostki z pamieci', (page3.state.units || []).length === 3,
    String((page3.state.units || []).length))
  phone.setGlucha(false)

  section('Spozniona odpowiedz nie wskrzesza skonczonego treningu')
  // Po zakonczeniu treningu ekran na chwile wracal do cwiczen i dopiero
  // potem na wybor jednostki. Odpowiedz telefonu wyslana PRZED zakonczeniem
  // wracala juz po nim, niosla "trening trwa" i przestawiala ekran z powrotem.
  phone.setGlucha(false)
  phone.active = true
  page.state.message = null
  page.state.outbox = []
  await osusz()
  page.state.view = VIEW.SETS
  page.applyVisibility()

  // Pytanie o stan rusza, ale odpowiedz zatrzymujemy w drodze.
  phone.wstrzymajOdpowiedzi(true)
  page.send('STATE', {}, true)
  await flush(); await flush()

  // W tym czasie konczymy trening na zegarku.
  page.finishWorkout()
  await flush(); await flush()
  ok('po zakonczeniu jestesmy na wyborze jednostki',
    page.state.view === VIEW.START, nazwa(page.state.view))

  // Dopiero teraz wraca tamta stara odpowiedz.
  phone.puscJedna()
  await flush(); await flush(); await flush()
  ok('spozniona odpowiedz nie przerzuca na cwiczenia',
    page.state.view === VIEW.START, nazwa(page.state.view))
  ok('i nie wskrzesza danych treningu', !page.state.data || !page.state.data.active,
    JSON.stringify(page.state.data && page.state.data.active))

  // Lista jednostek ma sie odswiezac, gdy stoimy na ekranie wyboru:
  // skasowany na telefonie trening przestawia kolejnosc, a zegarek
  // pokazywal to, co zapamietal przy ostatnim polaczeniu.
  phone.wstrzymajOdpowiedzi(false)
  phone.puscOdpowiedzi()
  await flush(); await flush()
  page.state.view = VIEW.START
  page.state.message = null
  page.applyVisibility()
  // Lancuch zegara dawno wygasl w tym scenariuszu - uruchamiamy go na nowo,
  // zeby bylo co tykac.
  page.startClock()
  page.tickCount = 0
  handlers.zadania = 0
  // Co dwie minuty, nie co pol: odpowiedz to plany wszystkich jednostek.
  for (let i = 0; i < 60; i++) {
    const t = handlers.zegary.filter((z) => z.zywy && z.ms === 1000).pop()
    if (!t) break
    t.zywy = false
    t.fn()
    await flush()
  }
  ok('po minucie jeszcze nie pyta', handlers.zadania === 0, handlers.zadania + ' zapytan')
  for (let i = 0; i < 61; i++) {
    const t = handlers.zegary.filter((z) => z.zywy && z.ms === 1000).pop()
    if (!t) break
    t.zywy = false
    t.fn()
    await flush()
  }
  ok('na ekranie wyboru zegarek sam dopytuje o liste', handlers.zadania > 0,
    handlers.zadania + ' zapytan')
  await osusz()

  // Te same trzy pomocniki, ale dla dowolnej strony - nowa strona ma wlasne
  // zegary i budziki, a sterowanie zegarami pierwszej strony tylko wygladalo
  // jak uplyw czasu.
  const ciszaNa = async (h) => {
    for (let i = 0; i < 4; i++) {
      h.zegary.filter((z) => z.zywy && z.ms >= 5000)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }
  const zapisNa = async (h) => {
    for (let i = 0; i < 4; i++) {
      h.zegary.filter((z) => z.zywy && z.ms === 350)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }
  const osuszNa = async (h, strona) => {
    for (let i = 0; i < 12; i++) {
      h.zegary.filter((z) => z.zywy && z.ms >= 5000)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
      strona.drain()
      await flush(); await flush()
      if (!strona.czeka()) break
    }
  }

  section('Trening bez telefonu liczy wszystko u siebie')
  // Zgloszenie z 7.11: "kalorie zepsute - nie liczy w ogole tak samo jak
  // i serii". Oba liczniki stały, bo oba czekały na telefon: serie
  // niepotwierdzone nie przeżywały zgaśnięcia ekranu, a współczynniki
  // spalania ginęły razem z zapisem poprzedniej sesji.
  phone.setGlucha(false)
  phone.active = false
  phone.starty.length = 0
  phone.localIds = {}
  phone.seen = {}
  phone.log.length = 0
  phone.endedAt = null
  LocalStorage.dane['gymbro-outbox'] = '[]'
  const czwarte = loadPage(phone)
  const p4 = czwarte.page
  p4.state.message = null
  p4.forget()
  p4.build()
  await flush(); await flush()
  ok('wspolczynniki spalania przychodza z jednostkami',
    !!(p4.state.burn && p4.state.burn.k1), JSON.stringify(p4.state.burn))
  ok('i progi tetna razem z nimi', !!(p4.state.zones && p4.state.zones.max))

  // Koniec treningu czysci zapis sesji - wspolczynniki musza to przezyc,
  // bo nastepny trening moze zaczac sie juz bez telefonu.
  p4.finishWorkout()
  await ciszaNa(czwarte.handlers)
  phone.setGlucha(true)
  p4.state.outbox = []
  p4.state.burn = null
  p4.state.zones = null
  p4.state.units = null
  p4.askUnits()
  await ciszaNa(czwarte.handlers)
  ok('po treningu wspolczynniki wracaja z pamieci',
    !!(p4.state.burn && p4.state.burn.k1), JSON.stringify(p4.state.burn))

  p4.startUnit(0)
  await ciszaNa(czwarte.handlers)
  const cw1 = (p4.state.data || {}).exercises[0]
  const kcal0 = p4.state.burnKcal
  p4.noteHeart(140)
  jump(60000)
  p4.noteHeart(140)
  ok('kalorie rosna od pierwszej minuty, bez telefonu', p4.state.burnKcal > kcal0,
    kcal0 + ' -> ' + p4.state.burnKcal)
  ok('i pokazuja liczbe, nie kreske', /^[0-9]+$/.test(p4.kcalText()), p4.kcalText())

  p4.state.phase = 'set'
  p4.press()
  await ciszaNa(czwarte.handlers)
  jump(2000)
  p4.state.phase = 'set'
  p4.press()
  await ciszaNa(czwarte.handlers)
  ok('dwie serie policzone u siebie', p4.shownDone(cw1) === 2, String(p4.shownDone(cw1)))
  const sumy = p4.sumy()
  ok('podsumowanie zna serie bez telefonu', sumy.sets === 2, JSON.stringify(sumy))
  ok('i powtorzenia z tonazem', sumy.reps > 0 && sumy.tonnage > 0, JSON.stringify(sumy))
  ok('tonaz po cwiczeniach tez cos pokazuje', p4.tonRows().length === 1,
    JSON.stringify(p4.tonRows()))
  ok('widac, ile czeka na wyslanie', /Czeka|Wysyłam/.test(p4.syncText()), p4.syncText())

  // Zgasniecie ekranu w srodku treningu bez lacznosci.
  await zapisNa(czwarte.handlers)
  const piate = loadPage(phone)
  const p5 = piate.page
  p5.build()
  await flush(); await flush()
  await ciszaNa(czwarte.handlers)
  const cw1po = ((p5.state.data || {}).exercises || [])[0] || {}
  ok('licznik serii przezyl zgasniecie ekranu', p5.shownDone(cw1po) === 2,
    String(p5.shownDone(cw1po)))
  ok('i sumy do podsumowania', p5.sumy().sets === 2, JSON.stringify(p5.sumy()))

  section('Zalegla kolejka nie wyrzuca treningu')
  // Tak wlasnie przepadl caly trening: telefon byl nieosiagalny, a nad ranem
  // zegarek wyrzucil z kolejki wszystko starsze niz osiem godzin. Baza na
  // telefonie jest jedyna kopia historii - zaleglosc musi dojechac.
  phone.setGlucha(false)
  phone.active = false
  phone.starty.length = 0
  phone.localIds = {}
  phone.seen = {}
  phone.log.length = 0
  phone.endedAt = null
  const dawno = Date.now() - 9 * 3600000
  LocalStorage.dane['gymbro-outbox'] = JSON.stringify([
    {
      m: 'START', cid: 'z1', at: dawno,
      p: { planId: 1, localId: 'w-stary', startedAt: dawno, sid: 'w-stary', cid: 'z1' }
    },
    {
      m: 'SET', cid: 'z2', at: dawno + 60000,
      p: { exerciseId: 1, weight: 80, reps: 8, doneAt: dawno + 60000, sid: 'w-stary', cid: 'z2' }
    }
  ])
  const szoste = loadPage(phone)
  const p6 = szoste.page
  p6.state.outbox = []
  p6.restoreOutbox()
  ok('zaleglosc zostaje w kolejce', p6.czeka() >= 2, String(p6.czeka()))
  const koniec = (p6.state.outbox || []).filter((x) => x.m === 'FINISH')[0]
  ok('porzuconemu treningowi dopisano koniec', !!koniec,
    JSON.stringify((p6.state.outbox || []).map((x) => x.m)))
  ok('na godzinie ostatniego polecenia', koniec && koniec.p.endedAt === dawno + 60000,
    JSON.stringify(koniec && koniec.p))

  jump(2000)
  const buzzPrzed = buzzes.length
  for (let i = 0; i < 12; i++) {
    szoste.handlers.zegary.filter((z) => z.zywy && z.ms >= 5000)
      .forEach((z) => { z.zywy = false; z.fn() })
    await flush(); await flush()
    p6.drain()
    await flush(); await flush()
    if (!p6.czeka()) break
  }
  ok('kolejka dojechala', p6.czeka() === 0, String(p6.czeka()))
  ok('telefon dostal wczorajszy trening', phone.starty.length === 1,
    JSON.stringify(phone.starty))
  ok('razem z jego seria', phone.log.length === 1, String(phone.log.length))
  ok('i z godzina zakonczenia', phone.endedAt === dawno + 60000, String(phone.endedAt))
  ok('wibracja mowi, ze wszystko dojechalo', buzzes.length > buzzPrzed,
    buzzPrzed + ' -> ' + buzzes.length)

  // ---- Bledy zgloszone po treningu 09.11 ---------------------------------
  //
  // Lagujace przyciski, ubita aplikacja przy koncu treningu, a po powrocie
  // nowy trening i za chwile stary. Dziennik z dwiema seriami zamiast trzech,
  // zapas podany do drugiej, ktorego nie bylo, poprawka serii wracajaca do
  // starych liczb. Kazdy scenariusz nizej zaczyna od czystej pamieci zegarka.
  const swieza = async () => {
    Object.keys(LocalStorage.dane).forEach((k) => delete LocalStorage.dane[k])
    phone.setGlucha(false)
    phone.mostPadl = false
    phone.setGubiOdpowiedz(false)
    phone.wstrzymajOdpowiedzi(false)
    phone.puscOdpowiedzi()
    phone.reset()
    phone.active = false
    phone.seen = {}
    phone.localIds = {}
    phone.starty.length = 0
    phone.lokalny = ''
    phone.serieZnacznikow = {}
    phone.setDuzoCwiczen(false)
    phone.setDlugaNazwa(false)
    const r = loadPage(phone)
    r.page.build()
    await flush(); await flush(); await flush()
    return r
  }
  const cichoW = async (h) => {
    for (let i = 0; i < 4; i++) {
      h.zegary.filter((z) => z.zywy && z.ms >= 5000).forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }
  const suszW = async (q, h) => {
    for (let i = 0; i < 15; i++) {
      h.zegary.filter((z) => z.zywy && z.ms >= 5000).forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
      q.drain()
      await flush(); await flush()
      if (!(q.state.outbox || []).length) break
    }
  }
  const zapisW = async (h) => {
    for (let i = 0; i < 4; i++) {
      jump(1500)
      h.zegary.filter((z) => z.zywy && z.ms === 350).forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
  }
  const seriaW = async (q) => {
    jump(2000)
    if (q.state.phase === 'rest') q.press()
    jump(2000)
    q.press()
    await flush(); await flush()
  }
  const kolejka = (q) => JSON.stringify((q.state.outbox || []).map((x) => x.m))
  const wpisyEx = (q, id) => {
    const lista = ((q.state.data && q.state.data.exercises) || []).filter((e) => e.id === id)[0]
    const stareWpisy = (lista && lista.entries) || []
    // Stara strona nie ma nakladki - tam dziennik to wpisy z odpowiedzi telefonu.
    if (typeof q.nakladka !== 'function') return stareWpisy
    const e = q.nakladka().ex[id]
    return (e && e.entries) || []
  }

  section('Restart bez telefonu nie podwaja kolejki')
  // Kolejka lezala w dwoch miejscach pamieci, a po restarcie obie kopie sie
  // sumowaly. Bez lacznosci kazde zgasniecie ekranu podwajalo ja: po kilku
  // opuszczeniach nadgarstka tysiac polecen, zapis pliku na kazdym kroku,
  // lagi coraz wieksze - i ubita aplikacja przy zakonczeniu.
  let r1 = await swieza()
  let q = r1.page
  let h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  ok('trening ma numer od telefonu', q.state.sessionId === 101, String(q.state.sessionId))
  phone.setGlucha(true)
  await seriaW(q)
  await seriaW(q)
  await cichoW(h)
  await zapisW(h)
  const kolejkaPrzedRestartami = (q.state.outbox || []).map((x) => x.cid)
  ok('serie czekaja w kolejce', kolejkaPrzedRestartami.length === 2, kolejka(q))
  for (let i = 0; i < 3; i++) {
    q.onDestroy()
    const r = loadPage(phone)
    r.page.build()
    await flush(); await flush()
    await cichoW(r.handlers)
    q = r.page
    h = r.handlers
  }
  ok('po trzech restartach dokladnie ta sama kolejka',
    JSON.stringify((q.state.outbox || []).map((x) => x.cid)) === JSON.stringify(kolejkaPrzedRestartami),
    kolejkaPrzedRestartami.length + ' -> ' + (q.state.outbox || []).length)
  ok('w pamieci zegarka tez',
    JSON.parse(LocalStorage.dane['gymbro-outbox'] || '[]').length === kolejkaPrzedRestartami.length,
    String(JSON.parse(LocalStorage.dane['gymbro-outbox'] || '[]').length))

  // To samo dla treningu zalozonego na zegarku - tam kolejka lezala tez
  // w zapisie sesji i stamtad sie dublowala.
  {
    const rL = await swieza()
    let qL = rL.page
    let hL = rL.handlers
    phone.setGlucha(true)
    qL.startUnit(0)
    await seriaW(qL)
    await seriaW(qL)
    await cichoW(hL)
    await zapisW(hL)
    const przedL = (qL.state.outbox || []).map((x) => x.cid)
    for (let i = 0; i < 3; i++) {
      qL.onDestroy()
      const r = loadPage(phone)
      r.page.build()
      await flush(); await flush()
      await cichoW(r.handlers)
      await zapisW(r.handlers)
      qL = r.page
      hL = r.handlers
    }
    ok('trening z zegarka: po trzech restartach ta sama kolejka',
      JSON.stringify((qL.state.outbox || []).map((x) => x.cid)) === JSON.stringify(przedL),
      przedL.length + ' -> ' + (qL.state.outbox || []).length)
    ok('trening z zegarka: w pamieci tez',
      JSON.parse(LocalStorage.dane['gymbro-outbox'] || '[]').length === przedL.length,
      przedL.length + ' -> ' + JSON.parse(LocalStorage.dane['gymbro-outbox'] || '[]').length)
  }
  phone.setGlucha(false)
  // Wracamy do treningu z numerem od telefonu z poczatku scenariusza.
  Object.keys(LocalStorage.dane).forEach((k) => delete LocalStorage.dane[k])
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  await seriaW(q)
  await seriaW(q)
  await cichoW(h)
  await zapisW(h)
  q.onDestroy()
  {
    const r = loadPage(phone)
    r.page.build()
    await flush(); await flush()
    await cichoW(r.handlers)
    q = r.page
    h = r.handlers
  }

  section('Trening z numerem od telefonu wraca bez telefonu')
  // Odtwarzal sie tylko trening zalozony na zegarku. Ten, ktory dostal juz
  // numer od telefonu, po restarcie bez lacznosci konczyl na wyborze
  // jednostki - a start z tego ekranu zakladal drugi trening obok.
  ok('po restarcie bez telefonu trening, nie wybor jednostki',
    q.state.view === VIEW.SETS && !q.state.message,
    nazwa(q.state.view) + ' / ' + q.state.message)
  ok('ten sam trening', q.state.sessionId === 101, String(q.state.sessionId))
  ok('zrobione serie widac', q.state.data && q.shownDone(q.state.data.exercises[0]) === 2,
    q.state.data ? String(q.shownDone(q.state.data.exercises[0])) : 'brak planu')
  phone.setGlucha(false)
  await suszW(q, h)
  ok('obie serie doszly do telefonu, bez dubli', (phone.wpisy[1] || []).length === 2,
    String((phone.wpisy[1] || []).length))

  section('Awaria mostu nie kasuje serii')
  // Zepp dziala, a GymBro na telefonie akurat nie slucha. Usluga towarzyszaca
  // odpowiadala bledem, a zegarek wyrzucal za to polecenie z kolejki -
  // seria po serii, choc na ekranie wszystkie byly "zrobione".
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.mostPadl = true
  await seriaW(q)
  await cichoW(h)
  ok('seria zostaje w kolejce', (q.state.outbox || []).some((x) => x.m === 'SET'), kolejka(q))
  phone.mostPadl = false
  await suszW(q, h)
  ok('gdy GymBro wroci, seria dojezdza', (phone.wpisy[1] || []).length === 1,
    String((phone.wpisy[1] || []).length))

  section('Odmowa jednego polecenia nie kasuje reszty')
  // "Brak trwajacego treningu" dla jednego polecenia czyscil cala kolejke.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  q.state.outbox = [{
    m: 'RIR', cid: 'obcy-1', at: Date.now(),
    p: { exerciseId: 1, rir: 1, sid: 'w-obcy', cid: 'obcy-1' }
  }].concat(q.state.outbox || [])
  await seriaW(q)
  await cichoW(h)
  phone.setGlucha(false)
  await suszW(q, h)
  ok('seria za odrzuconym poleceniem dojechala', (phone.wpisy[1] || []).length === 1,
    String((phone.wpisy[1] || []).length))

  section('Poprawka serii bez telefonu')
  // Zgloszone: "nie da sie edytowac serii, wraca do poprzedniego stanu".
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  await seriaW(q)
  q.state.index = 0
  q.showView(VIEW.LOG)
  ok('seria w dzienniku od razu, bez telefonu', q.logRows().length === 1, String(q.logRows().length))
  q.openFix(0)
  q.fixStep('r', 1)
  q.fixStep('r', 1)
  q.saveFix()
  ok('dziennik pokazuje poprawke od razu', (q.logRows()[0] || {}).reps === 10,
    JSON.stringify(q.logRows()[0]))
  ok('wiersz dziennika tez', /x 10/.test(q.w.logRows[0].attrs.text || ''), q.w.logRows[0].attrs.text)
  await cichoW(h)
  phone.setGlucha(false)
  await suszW(q, h)
  ok('telefon dostal poprawione powtorzenia', ((phone.wpisy[1] || [])[0] || {}).reps === 10,
    JSON.stringify(phone.wpisy[1]))
  ok('dziennik nie wrocil do starych liczb', (q.logRows()[0] || {}).reps === 10,
    JSON.stringify(q.logRows()[0]))

  section('Poprawka serii, ktora juz doszla')
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  await seriaW(q)
  await suszW(q, h)
  q.state.index = 0
  q.showView(VIEW.LOG)
  q.openFix(0)
  q.fixStep('r', -1)
  q.saveFix()
  ok('nowe powtorzenia na ekranie, zanim odpowie telefon', (q.logRows()[0] || {}).reps === 7,
    JSON.stringify(q.logRows()[0]))
  await suszW(q, h)
  ok('i na telefonie', ((phone.wpisy[1] || [])[0] || {}).reps === 7, JSON.stringify(phone.wpisy[1]))

  section('Serie i zapas bez telefonu, wszystkie w dzienniku')
  // Zgloszone: trzy serie zrobione, w dzienniku dwie, zapasu do drugiej
  // nie ma, a przy innych cwiczeniach dziennik pusty, choc licznik mowi
  // "zrobione". Dziennik czekal na telefon, licznik nie.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  await seriaW(q)
  await seriaW(q)
  q.setRir(2)
  await seriaW(q)
  const pierwsze = wpisyEx(q, 1)
  ok('dwie serie pierwszego cwiczenia w dzienniku', pierwsze.length === 2, JSON.stringify(pierwsze))
  ok('zapas przy drugiej', pierwsze[1] && pierwsze[1].rir === 2, JSON.stringify(pierwsze[1]))
  ok('seria drugiego cwiczenia tez', wpisyEx(q, 2).length === 1, JSON.stringify(wpisyEx(q, 2)))
  const sumyW = typeof q.sumy === 'function' ? q.sumy() : ((q.state.data && q.state.data.totals) || {})
  ok('podsumowanie liczy wszystkie trzy', (sumyW.sets || 0) === 3, JSON.stringify(sumyW))
  await cichoW(h)
  phone.setGlucha(false)
  await suszW(q, h)
  ok('telefon ma trzy serie', (phone.wpisy[1] || []).length + (phone.wpisy[2] || []).length === 3,
    JSON.stringify(phone.wpisy))
  ok('zapas trafil w druga serie, nie w ostatnia',
    ((phone.wpisy[1] || [])[1] || {}).rir === 2 && ((phone.wpisy[2] || [])[0] || {}).rir === undefined,
    JSON.stringify(phone.wpisy))

  section('Koniec treningu od razu w pamieci')
  // Aplikacja ginela przy zakonczeniu. Odlozony zapis kolejki nie zdazal,
  // zapis sesji byl juz skasowany - telefon nie dostawal zakonczenia.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  await seriaW(q)
  q.finishWorkout()
  const wPamieci = JSON.parse(LocalStorage.dane['gymbro-outbox'] || '[]')
  ok('zakonczenie w pamieci zaraz po nacisnieciu', wPamieci.some((x) => x.m === 'FINISH'),
    JSON.stringify(wPamieci.map((x) => x.m)))
  ok('razem z niewyslana seria', wPamieci.some((x) => x.m === 'SET'),
    JSON.stringify(wPamieci.map((x) => x.m)))

  section('Cudzy trening na telefonie nie przejmuje treningu z zegarka')
  // "Nowy trening, a za chwile ten stary": zegarek przyjmowal za swoj
  // kazdy trening, ktory akurat trwal na telefonie.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  phone.setGlucha(true)
  q.startUnit(0)
  await seriaW(q)
  const mojZnacznik = q.state.localId
  phone.setGlucha(false)
  phone.active = true
  phone.lokalny = 'w-inny'
  q.send('STATE', {}, false)
  await flush(); await flush()
  ok('zegarek zostal przy swoim treningu', !!mojZnacznik && q.state.sessionId === mojZnacznik,
    mojZnacznik + ' -> ' + q.state.sessionId)
  ok('seria nie zniknela', q.state.data && q.shownDone(q.state.data.exercises[0]) === 1,
    q.state.data ? String(q.shownDone(q.state.data.exercises[0])) : 'brak')

  section('Inna kolejnosc cwiczen w odpowiedzi telefonu')
  // Zegarek trzymal sie miejsca na liscie, nie cwiczenia. Gdy lista
  // przychodzila w innej kolejnosci, ekran pokazywal inne cwiczenie,
  // a kolejne serie szly pod cudza nazwa.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  q.state.index = 0
  const odwrotnie = phone.state()
  odwrotnie.exercises = odwrotnie.exercises.slice().reverse()
  q.apply(odwrotnie, false)
  ok('stoimy na tym samym cwiczeniu', (q.current() || {}).id === 1, JSON.stringify((q.current() || {}).id))

  section('Odpowiedz bez drabinki nie gubi drabinki')
  const lekka = phone.state()
  lekka.exercises.forEach((e) => { delete e.ladder; delete e.atMin; delete e.atMax; delete e.step })
  q.apply(lekka, false)
  ok('drabinka zostala z poprzedniego stanu', Array.isArray((q.current() || {}).ladder),
    JSON.stringify((q.current() || {}).ladder))

  section('Zaleglosc leci paczkami')
  // Po treningu bez lacznosci kazde polecenie to byla osobna runda przez
  // Bluetooth z pelnym stanem w odpowiedzi - przez minute zegarek mielil
  // odpowiedzi, zamiast reagowac na palec.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  q.startUnit(0)
  await flush(); await flush(); await flush()
  phone.setGlucha(true)
  for (let i = 0; i < 6; i++) {
    await seriaW(q)
    if (q.askingRir()) q.setRir(1)
  }
  await cichoW(h)
  const doWyslania = (q.state.outbox || []).length
  phone.setGlucha(false)
  h.zadania = 0
  await suszW(q, h)
  ok('cala zaleglosc doszla', (q.state.outbox || []).length === 0, kolejka(q))
  ok('w najwyzej dwoch rundach', h.zadania <= 2, doWyslania + ' polecen, ' + h.zadania + ' rund')
  const wszystkieSerie = Object.keys(phone.wpisy).reduce((n, k) => n + phone.wpisy[k].length, 0)
  ok('szesc serii na telefonie', wszystkieSerie === 6, String(wszystkieSerie))

  section('Telefon bez paczek dostaje po jednym')
  phone.starePaczki = true
  phone.setGlucha(true)
  await seriaW(q)
  await seriaW(q)
  await cichoW(h)
  phone.setGlucha(false)
  await suszW(q, h)
  phone.starePaczki = false
  ok('kolejka doszla i tak', (q.state.outbox || []).length === 0, kolejka(q))

  section('Ekran serii nie pisze na prozno')
  // Tetno przychodzi co sekunde przez caly trening. Kolor i polozenie
  // serca, kolor zegara - kazdy odczyt pisal je od nowa, choc sie nie
  // zmienialy. Tyle pracy co sekunde na watku, ktorego potrzebuje palec.
  q.state.view = VIEW.SETS
  q.state.message = null
  q.applyVisibility()
  q.onHeart()
  q.paintClock()
  const wszystkieZapisy = () => Object.keys(q.w).reduce((n, k) => {
    const x = q.w[k]
    if (x && typeof x.writes === 'number') return n + x.writes
    if (Array.isArray(x)) return n + x.reduce((m, y) => m + ((y && y.writes) || 0), 0)
    return n
  }, 0)
  let przedTetnem = wszystkieZapisy()
  for (let i = 0; i < 5; i++) q.onHeart()
  ok('piec odczytow tego samego tetna - zero zapisow', wszystkieZapisy() === przedTetnem,
    (wszystkieZapisy() - przedTetnem) + ' zapisow')
  przedTetnem = wszystkieZapisy()
  for (let i = 0; i < 5; i++) {
    jump(1000)
    q.paintClock()
  }
  ok('piec sekund zegara - po jednym zapisie na sekunde', wszystkieZapisy() - przedTetnem <= 5,
    (wszystkieZapisy() - przedTetnem) + ' zapisow')

  section('Dluga nazwa przejezdza raz')
  // Animacja nazwy chodzila w kolko przez caly czas cwiczenia - a dluga
  // nazwa to kazde cwiczenie z hantlami na lawce.
  r1 = await swieza()
  q = r1.page
  h = r1.handlers
  phone.setDlugaNazwa(true)
  q.startUnit(0)
  await flush(); await flush(); await flush()
  q.state.view = VIEW.SETS
  q.lastName = null
  q.paintSets()
  const animacjeNazwy = () => q.w.name.writes
  for (let i = 0; i < 6; i++) {
    h.zegary.filter((z) => z.zywy && z.ms === 1100).forEach((z) => { z.zywy = false; z.fn() })
    await flush()
  }
  const poPrzejazdach = animacjeNazwy()
  for (let i = 0; i < 6; i++) {
    h.zegary.filter((z) => z.zywy && z.ms === 1100).forEach((z) => { z.zywy = false; z.fn() })
    await flush()
  }
  ok('po przejezdzie tam i z powrotem nazwa stoi', animacjeNazwy() === poPrzejazdach,
    poPrzejazdach + ' -> ' + animacjeNazwy())
  phone.setDlugaNazwa(false)

  section('Most do telefonu przekazuje parametry')
  // Zgloszone 02.11: podany zapas wracal jako "nie podano". Dwa polecenia
  // siegaly w usludze towarzyszacej po gola nazwe `params` zamiast
  // `request.params`. Modul chodzi w trybie scislym, wiec bylo to odwolanie
  // do nieistniejacej zmiennej - wyjatek leciał prosto w `catch`, zegarek
  // dostawal "Otworz GymBro na telefonie" i kasowal polecenie z kolejki.
  // Reszta scenariuszy tego nie lapie, bo udaje telefon od razu za mostem.
  const most = loadSide()
  const polecenia = [
    ['SET', { exerciseId: 1, weight: 60, reps: 8, cid: 'c1' }],
    ['RIR', { exerciseId: 1, rir: 2, cid: 'c2' }],
    ['DELETE', { setId: 7, cid: 'c3' }],
    ['UNDO', { exerciseId: 1, cid: 'c4' }],
    ['EDIT', { setId: 7, reps: 9, cid: 'c5' }],
    ['WEIGHT', { exerciseId: 1, from: 60, dir: 1 }],
    ['START', { planId: 1, localId: 'w1', cid: 'c6' }],
    // Te trzy szly z pustym cialem: bez sesji, znacznika i godziny konca.
    ['PAUSE', { sid: '101', cid: 'c7' }],
    ['RESUME', { sid: '101', cid: 'c8' }],
    ['FINISH', { endedAt: 123456, sid: '101', cid: 'c9' }],
    ['BATCH', { lite: 1, items: [{ m: 'SET', p: { exerciseId: 1, cid: 'c10' } }] }]
  ]
  for (let i = 0; i < polecenia.length; i++) {
    const metoda = polecenia[i][0]
    const parametry = polecenia[i][1]
    most.wyslane.length = 0
    const odpowiedz = await most.zadaj(metoda, parametry)
    const zadanie = most.wyslane[0]
    ok(metoda + ': parametry docieraja do telefonu',
      !!zadanie && zadanie.body === JSON.stringify(parametry),
      JSON.stringify((odpowiedz || {}).data) + ' / ' + (zadanie && zadanie.body))
  }

  section('Most odroznia odmowe od awarii')
  // Odmowa telefonu dotyczy jednego polecenia; awaria po drodze - nie,
  // i wtedy polecenie musi zostac w kolejce zegarka.
  const zeStatusem = (status, cialo) => loadSide(() => ({ status: status, body: JSON.stringify(cialo) }))
  let odp = await zeStatusem(404, { error: 'nie ma takiej serii' }).zadaj('EDIT', { setId: 9 })
  ok('404 to odmowa', odp && odp.data && odp.data.reject === true && !odp.data.retry, JSON.stringify(odp))
  odp = await zeStatusem(500, { error: 'blad wewnetrzny' }).zadaj('SET', { exerciseId: 1 })
  ok('500 to awaria - ponowic', odp && odp.data && odp.data.retry === true, JSON.stringify(odp))
  odp = await zeStatusem(401, {}).zadaj('SET', { exerciseId: 1 })
  ok('zly token - ponowic, nie wyrzucac', odp && odp.data && odp.data.retry === true, JSON.stringify(odp))
  odp = await loadSide(() => { throw new Error('ECONNREFUSED') }).zadaj('SET', { exerciseId: 1 })
  ok('nikt nie slucha - ponowic', odp && odp.data && odp.data.retry === true, JSON.stringify(odp))
  odp = await zeStatusem(200, { active: true }).zadaj('SET', { exerciseId: 1 })
  ok('udane bez zadnej flagi', odp && odp.data && !odp.data.retry && !odp.data.reject, JSON.stringify(odp))

  console.log('\n' + (failures === 0
    ? 'Wszystko przeszlo: ' + checks + ' sprawdzen.'
    : failures + ' z ' + checks + ' sprawdzen nie przeszlo.'))
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\nHarness wywalil sie: ' + (e && e.stack ? e.stack : e))
  process.exit(2)
})
