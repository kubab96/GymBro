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
    request(req) {
      const cid = req.params && req.params.cid
      if (cid) {
        if (phone.seen[cid]) return phone.state()
        phone.seen[cid] = true
      }
      switch (req.method) {
        case 'STATE':
          return phone.state()
        case 'UNITS':
          // Jednostka niesie caly swoj plan: z tego zegarek sklada trening,
          // gdy telefonu nie ma w zasiegu.
          return {
            program: 'FBW',
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
            rir: undefined
          }
          if (!phone.wpisy[id]) phone.wpisy[id] = []
          phone.wpisy[id].push(wpis)
          phone.log.push({
            id: id, weight: wpis.weight, reps: wpis.reps,
            doneAt: req.params.doneAt || null
          })
          return phone.state()
        }
        case 'EDIT': {
          const setId = req.params.setId
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
          const ost = lista[lista.length - 1]
          if (!ost) return { error: 'nie ma takiej serii' }
          ost.rir = req.params.rir
          phone.log.push({ setId: ost.id, weight: ost.weight, reps: ost.reps, rir: ost.rir })
          return phone.state()
        }
        case 'DELETE': {
          const sid = req.params.setId
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
function loadSide() {
  const file = path.join(__dirname, '..', 'app-side', 'index.js')
  const body = fs.readFileSync(file, 'utf8')
    .replace(/^import[\s\S]*?from '[^']+'\s*$/gm, '')

  const wyslane = []
  const fetchStub = async (options) => {
    wyslane.push(options)
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
  const file = path.join(__dirname, '..', 'page', 'index.js')
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
    'statKcalLabel', 'statVolume'],
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
  if (w.slide.visible) bledy.push('zaslona przejscia zostala na wierzchu')
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
      // Dokladnie zegar odlozonego zapisu (ZAPIS_MS). Szerszy zakres lapal
      // tez bezpieczniki animacji i scenariusz konczyl przejscie sam,
      // zamiast sprawdzic, co sie dzieje w jego trakcie.
      handlers.zegary.filter((z) => z.zywy && z.ms === 350)
        .forEach((z) => { z.zywy = false; z.fn() })
      await flush(); await flush()
    }
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
  ok('pokazalo podsumowanie', page.state.view === VIEW.DONE, nazwa(page.state.view))

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
  ok('pokazalo podsumowanie', page.state.view === VIEW.DONE, nazwa(page.state.view))

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
  ok('i dotyczy cwiczenia z poprzedniej serii', page.state.rirFor === 1,
    'rirFor ' + page.state.rirFor + ', index ' + page.state.index)

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
  ok('po ostatniej serii jest podsumowanie', page.state.view === VIEW.DONE,
    nazwa(page.state.view))

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

  section('Nic nie pisze do pamieci w trakcie przejscia')
  // Zapis do pamieci trwalej to zapis do pliku. Odlozony z uchwytu
  // klikniecia trafial prosto w pierwsze klatki animacji przejscia -
  // zaslona stawala i przez chwile bylo widac czarny ekran. Ma poczekac,
  // az przejscie sie skonczy; cwierc sekundy nikomu nie robi roznicy.
  page.state.message = null
  page.state.rirFor = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  await zapis()

  handlers.wolneAnimacje = true
  page.showView(VIEW.STATS, -1, 'y')
  ok('przejscie trwa', page.zajeteRysowaniem() === true)
  LICZNIK.ile = 0
  page.remember()
  page.rememberOutbox()
  await zapis()
  ok('w trakcie przejscia nie zapisano nic', LICZNIK.ile === 0,
    LICZNIK.ile + ' zapisow')

  handlers.dokoncz()
  await flush(); await flush()
  ok('po przejsciu zaslona zeszla', page.zajeteRysowaniem() === false)
  await zapis()
  ok('i zapis wreszcie doszedl', LICZNIK.ile > 0, LICZNIK.ile + ' zapisow')
  handlers.wolneAnimacje = false
  handlers.dokoncz()

  // Blysk zostawial po sobie zegar, ktory nigdy nie gasl - a wtedy
  // aplikacja na zawsze uwazala, ze cos sie animuje, i nie zapisywala nic.
  page.state.view = VIEW.SETS
  page.applyVisibility()
  page.flash(0x1f8a5a, 200, 120)
  await flush()
  ok('blysk nie zostawia po sobie zegara', !page.flashTimer,
    String(page.flashTimer))
  ok('i nic sie juz nie animuje', page.zajeteRysowaniem() === false)

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
  page.state.pending = {}
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
  page.state.pending = {}
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
  ok('zegarek rozliczyl swoj licznik', !page.state.pending[cwDubel.id],
    JSON.stringify(page.state.pending))

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
  ok('zapas czeka w kolejce', (page.state.outbox || []).some((x) => x.m === 'RIR'),
    JSON.stringify((page.state.outbox || []).map((x) => x.m)))
  await cisza()
  phone.setGlucha(false)
  await osusz()

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

  section('Szybkie gesty w trakcie przejscia')
  // Na zegarku widac bylo czasem pol czarnego ekranu po machnieciu w gore.
  // Przejscie trwa 240 ms i w tym czasie spokojnie da sie machnac drugi raz.
  // Zaslona sama w sobie nie jest problemem - problemem jest to, ze po niej
  // trzeba odbudowac przyciski, a robi to wywolanie zwrotne, ktore przy
  // nakladajacych sie przejsciach potrafi przepasc.
  handlers.wolneAnimacje = true
  page.state.message = null
  page.state.rirFor = null
  page.state.view = VIEW.SETS
  page.applyVisibility()
  handlers.dokoncz()

  const bezp0 = handlers.bezpieczniki()
  handlers.gesture(GESTURE_UP)
  ok('pierwszy gest przeniosl na czas', page.state.view === VIEW.STATS, nazwa(page.state.view))
  ok('zaslona wjechala', page.w.slide.visible)
  ok('pierwsze przejscie ma bezpiecznik', handlers.bezpieczniki() === bezp0 + 1,
    (handlers.bezpieczniki() - bezp0) + ' nowych')

  // Drugi gest, zanim pierwsza animacja zdazyla sie skonczyc.
  handlers.gesture(GESTURE_UP)
  ok('drugi gest przeniosl na tetno', page.state.view === VIEW.HEART, nazwa(page.state.view))
  ok('nadal dokladnie jeden bezpiecznik', handlers.bezpieczniki() === bezp0 + 1,
    (handlers.bezpieczniki() - bezp0) + ' nowych')

  // Spoznione wywolanie zwrotne pierwszej animacji. Przychodzi w trakcie
  // drugiej i nie wolno mu ruszac bezpiecznika, ktory nie jest jego.
  handlers.jednaAnimacja()
  ok('spoznione wywolanie nie zdjelo cudzego bezpiecznika',
    handlers.bezpieczniki() === bezp0 + 1, (handlers.bezpieczniki() - bezp0) + ' nowych')

  handlers.dokoncz()
  ok('zaslona zeszla z ekranu', !page.w.slide.visible)
  ok('zaslona wrocila na miejsce, nie w polowie drogi',
    page.w.slide.attrs.y === 0 && page.w.slide.attrs.x === 0,
    'x ' + page.w.slide.attrs.x + ', y ' + page.w.slide.attrs.y)

  // To jest ten objaw: wracamy na ekran serii dwoma szybkimi gestami
  // i przyciski musza zostac odbudowane, inaczej dol ekranu zostaje czarny.
  const zapisy = page.w.prev.writes
  handlers.gesture(GESTURE_DOWN)
  handlers.gesture(GESTURE_DOWN)
  ok('wrocilismy na serie', page.state.view === VIEW.SETS, nazwa(page.state.view))
  handlers.jednaAnimacja()
  handlers.dokoncz()
  ok('przyciski ekranu serii odbudowane', page.w.prev.writes > zapisy,
    zapisy + ' -> ' + page.w.prev.writes)
  ok('zaslona zeszla po powrocie', !page.w.slide.visible)
  ok('ekran serii czysty', ekranCzysty(page).length === 0, ekranCzysty(page).join('; '))

  // Nawet gdy wywolanie zwrotne nie przyjdzie wcale, bezpiecznik ma
  // posprzatac - czarny ekran jest gorszy niz brak animacji.
  ANIMACJE.czeka.length = 0
  handlers.gesture(GESTURE_UP)
  ANIMACJE.czeka.length = 0
  handlers.dokoncz()
  ok('bezpiecznik sprzata po zgubionym wywolaniu', !page.w.slide.visible)
  handlers.wolneAnimacje = false
  handlers.dokoncz()
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
  page.state.pending = {}
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
  page.state.pending = {}
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
  ok('zapisana seria nie przepadla', page2.shownDone(page2.current()) +
    (page2.state.pending[page2.current().id] || 0) >= 0,
    JSON.stringify(page2.state.pending))
  ok('kolejka przezyla restart', (page2.state.outbox || []).length >= 2,
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
  page.state.pending = {}
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
  for (let i = 0; i < 31; i++) {
    const t = handlers.zegary.filter((z) => z.zywy && z.ms === 1000).pop()
    if (!t) break
    t.zywy = false
    t.fn()
    await flush()
  }
  ok('na ekranie wyboru zegarek sam dopytuje o liste', handlers.zadania > 0,
    handlers.zadania + ' zapytan')
  await osusz()

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
    ['START', { planId: 1, localId: 'w1', cid: 'c6' }]
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

  console.log('\n' + (failures === 0
    ? 'Wszystko przeszlo: ' + checks + ' sprawdzen.'
    : failures + ' z ' + checks + ' sprawdzen nie przeszlo.'))
  process.exit(failures === 0 ? 0 : 1)
}

main().catch((e) => {
  console.error('\nHarness wywalil sie: ' + (e && e.stack ? e.stack : e))
  process.exit(2)
})
