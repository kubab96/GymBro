/*
 * Obrazki zegarka do README - z prawdziwej strony, nie z makiety.
 *
 * Ładuje `page/index.js` na tych samych zaślepkach co `check.js`, przechodzi
 * trening palcem i po każdym kroku rysuje widoczne widgety do HTML-a. Edge
 * bez okna robi z niego PNG, a klatki skleja się w animowany PNG (APNG) -
 * GitHub pokazuje go w README jak GIF-a, a sklejenie nie wymaga
 * rozpakowywania obrazków, bo klatki APNG to te same dane co IDAT.
 *
 * Makieta rozjechałaby się z zegarkiem przy pierwszej zmianie ekranu.
 * Pismo jest systemowe z Windowsa, więc szerokości napisów są przybliżone.
 *
 * Uruchomienie: node tools/zrzuty.js <katalog wyjściowy>
 */

const fs = require('fs')
const path = require('path')
const os = require('os')
const zlib = require('zlib')
const { execFileSync } = require('child_process')
const h = require('./check.js')

const EDGE = process.env.EDGE || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const ASSETS = path.join(__dirname, '..', 'assets', 'common.r')
const BEZEL = 34
const SIZE = 480 + 2 * BEZEL

// Kolejność tworzenia to kolejność rysowania na zegarku - zapamiętujemy ją,
// bo `page.w` to słownik i porządku warstw nie zna.
let created = []
const createWidget = h.hmUI.createWidget
h.hmUI.createWidget = (type, options) => {
  const w = createWidget(type, options)
  created.push(w)
  return w
}

// Zaślepka mierzy napis stałymi 9 px na znak, bez względu na rozmiar - na
// obrazku liczby wchodziły na podpisy. Tu przybliżenie pisma Segoe UI.
h.hmUI.getTextLayout = (text, opcje) => ({
  width: Math.ceil([...String(text)].reduce((s, c) =>
    s + (/[0-9]/.test(c) ? 0.56 : (/[ .,:]/.test(c) ? 0.28 : 0.55)), 0) * ((opcje && opcje.text_size) || 30)),
  height: (opcje && opcje.text_size) || 30
})

const hex = (c) => '#' + Number(c || 0).toString(16).padStart(6, '0')
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/\n/g, '<br>')

function widgetHtml(w) {
  const a = w.attrs
  const box = `left:${a.x}px;top:${a.y}px;width:${a.w}px;height:${a.h}px;`
  if (w.type === 'FILL_RECT') {
    const rot = a.angle ? `transform:rotate(${a.angle}deg);` : ''
    return `<div style="position:absolute;${box}background:${hex(a.color)};border-radius:${a.radius || 0}px;${rot}"></div>`
  }
  if (w.type === 'TEXT' || w.type === 'BUTTON') {
    const just = a.align_h === 'L' ? 'flex-start' : (a.align_h === 'R' ? 'flex-end' : 'center')
    const wrap = a.text_style === 'WRAP' ? 'white-space:normal;' : 'white-space:nowrap;'
    const tlo = w.type === 'BUTTON'
      ? `background:${hex(a.normal_color)};border-radius:${a.radius || 0}px;`
      : ''
    const align = a.align_h === 'L' ? 'left' : (a.align_h === 'R' ? 'right' : 'center')
    return `<div style="position:absolute;${box}${tlo}display:flex;align-items:center;justify-content:${just};` +
      `color:${hex(a.color)};font-size:${a.text_size}px;line-height:1.15;text-align:${align};${wrap}overflow:visible">` +
      `<span>${esc(a.text || '')}</span></div>`
  }
  if (w.type === 'ARC') {
    if (a.start_angle === a.end_angle) return ''
    const lw = a.line_width || 1
    const r = a.w / 2 - lw / 2
    const cx = a.x + a.w / 2
    const cy = a.y + a.h / 2
    const p = (deg) => {
      const rad = (deg * Math.PI) / 180
      return [cx + r * Math.cos(rad), cy + r * Math.sin(rad)]
    }
    const span = a.end_angle - a.start_angle
    if (Math.abs(span) >= 359.9) {
      return `<svg style="position:absolute;left:0;top:0" width="480" height="480"><circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${hex(a.color)}" stroke-width="${lw}"/></svg>`
    }
    const [x1, y1] = p(a.start_angle)
    const [x2, y2] = p(a.end_angle)
    const large = Math.abs(span) > 180 ? 1 : 0
    const sweep = span > 0 ? 1 : 0
    return `<svg style="position:absolute;left:0;top:0" width="480" height="480"><path d="M${x1} ${y1} A${r} ${r} 0 ${large} ${sweep} ${x2} ${y2}" fill="none" stroke="${hex(a.color)}" stroke-width="${lw}" stroke-linecap="round"/></svg>`
  }
  if (w.type === 'IMG') {
    const src = 'file:///' + path.join(ASSETS, a.src).replace(/\\/g, '/')
    return `<img style="position:absolute;left:${a.x}px;top:${a.y}px" src="${src}">`
  }
  return ''
}

function frameHtml() {
  const body = created.filter((w) => w.visible).map(widgetHtml).join('\n')
  return `<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;background:transparent}
.bezel{width:${SIZE}px;height:${SIZE}px;border-radius:50%;background:radial-gradient(circle at 30% 25%,#5a5f66,#26292d 55%,#111 100%);position:relative}
.screen{position:absolute;left:${BEZEL}px;top:${BEZEL}px;width:480px;height:480px;border-radius:50%;background:#000;overflow:hidden;
font-family:"Segoe UI Variable Text","Segoe UI",Roboto,sans-serif;font-weight:500}
</style></head><body><div class="bezel"><div class="screen">${body}</div></div></body></html>`
}

function shoot(html, out) {
  const tmp = path.join(os.tmpdir(), 'gymbro-zrzut.html')
  fs.writeFileSync(tmp, html)
  execFileSync(EDGE, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    '--default-background-color=00000000',
    '--window-size=' + SIZE + ',' + SIZE, '--screenshot=' + out,
    'file:///' + tmp.replace(/\\/g, '/')
  ], { stdio: 'ignore' })
}

// ---- APNG -----------------------------------------------------------------

const crcT = []
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0 }
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
function chunk(type, data) {
  const d = Buffer.concat([Buffer.from(type), data])
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(d))
  return Buffer.concat([len, d, c])
}
function chunks(png) {
  const out = []
  let i = 8
  while (i < png.length) {
    const len = png.readUInt32BE(i)
    out.push({ type: png.toString('ascii', i + 4, i + 8), data: png.slice(i + 8, i + 8 + len) })
    i += 12 + len
  }
  return out
}

/** Klatki z Edge'a mają ten sam IHDR, więc IDAT każdej przechodzi do fdAT bez zmian. */
function apng(files, delaysMs, out) {
  const frames = files.map((f) => chunks(fs.readFileSync(f)))
  const ihdr = frames[0].find((c) => c.type === 'IHDR').data
  const W = ihdr.readUInt32BE(0)
  const H = ihdr.readUInt32BE(4)
  let seq = 0
  const parts = [Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr)]
  const actl = Buffer.alloc(8); actl.writeUInt32BE(frames.length, 0); actl.writeUInt32BE(0, 4)
  parts.push(chunk('acTL', actl))
  frames.forEach((fr, n) => {
    const fctl = Buffer.alloc(26)
    fctl.writeUInt32BE(seq++, 0)
    fctl.writeUInt32BE(W, 4); fctl.writeUInt32BE(H, 8)
    fctl.writeUInt32BE(0, 12); fctl.writeUInt32BE(0, 16)
    fctl.writeUInt16BE(delaysMs[n], 20); fctl.writeUInt16BE(1000, 22)
    fctl[24] = 0 // dispose: none
    fctl[25] = 0 // blend: source - klatka zastępuje całość, z przezroczystością
    parts.push(chunk('fcTL', fctl))
    fr.filter((c) => c.type === 'IDAT').forEach((c) => {
      if (n === 0) { parts.push(chunk('IDAT', c.data)); return }
      const s = Buffer.alloc(4); s.writeUInt32BE(seq++)
      parts.push(chunk('fdAT', Buffer.concat([s, c.data])))
    })
  })
  parts.push(chunk('IEND', Buffer.alloc(0)))
  fs.writeFileSync(out, Buffer.concat(parts))
}

// ---- Scenariusz -----------------------------------------------------------

async function main() {
  const outDir = path.resolve(process.argv[2] || 'zrzuty')
  fs.mkdirSync(outDir, { recursive: true })
  const klatkiDir = path.join(outDir, 'klatki')
  fs.mkdirSync(klatkiDir, { recursive: true })

  created = []
  const phone = h.makePhone()
  const { page, handlers } = h.loadPage(phone)
  const flush2 = async () => { await h.flush(); await h.flush() }
  const odswiez = () => { page.paintClock(); page.paintRing() }

  const klatki = []
  const opoznienia = []
  const klatka = (nazwa, ms) => {
    odswiez()
    const plik = path.join(klatkiDir, String(klatki.length).padStart(2, '0') + '-' + nazwa + '.png')
    shoot(frameHtml(), plik)
    klatki.push(plik)
    opoznienia.push(ms)
    console.log('  ' + path.basename(plik))
    return plik
  }
  const kopia = (plik, nazwa) => fs.copyFileSync(plik, path.join(outDir, nazwa))

  page.build()
  await flush2()
  kopia(klatka('wybor', 1800), 'zegarek-wybor.png')

  page.startUnit(0)
  await flush2()
  h.jump(38000)
  kopia(klatka('seria', 1600), 'zegarek-seria.png')

  // Seria zapisana - przerwa odlicza się na pierścieniu.
  page.press()
  await flush2()
  await h.puscWysylke(page, handlers)
  h.jump(4000)
  klatka('przerwa-1', 700)
  h.jump(45000)
  klatka('przerwa-2', 700)
  h.jump(50000)
  kopia(klatka('przerwa-3', 1400), 'zegarek-przerwa.png')

  // Druga seria, przerwa, i przejście do następnego ćwiczenia.
  page.press()
  await flush2()
  h.jump(41000)
  klatka('seria-2', 1200)
  page.press()
  await flush2()
  await h.puscWysylke(page, handlers)
  h.jump(3000)
  // Ostatnia seria ćwiczenia: zegarek sam przechodzi do następnego,
  // a w przerwie pyta o zapas.
  klatka('zapas', 1600)
  page.setRir(2)
  h.jump(120000)
  page.press()
  await flush2()
  h.jump(29000)
  kopia(klatka('nastepne', 1500), 'zegarek-seria-2.png')

  // Spalanie liczy zegarek z tętna, a tętno zaślepki stoi na 120 od
  // sekundy zero - podstawiamy wynik kwadransa treningu.
  page.state.burnKcal = 142
  if (!page.state.burn) { page.state.kcalSeen = true; page.state.kcal = 142 }
  page.showView(h.VIEW.STATS)
  await flush2()
  kopia(klatka('czas', 1700), 'zegarek-czas.png')

  page.move(-1)
  page.showView(h.VIEW.LOG)
  await flush2()
  kopia(klatka('dziennik', 1700), 'zegarek-dziennik.png')

  apng(klatki, opoznienia, path.join(outDir, 'zegarek-demo.png'))
  console.log('-> ' + path.join(outDir, 'zegarek-demo.png'))
}

main().catch((e) => {
  console.error(e && e.stack ? e.stack : e)
  process.exit(1)
})
